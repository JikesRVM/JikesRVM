/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;

import org.jikesrvm.Callbacks;
import org.jikesrvm.Constants;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.inlining.ClassLoadingDependencyManager;
import org.jikesrvm.mm.mminterface.HandInlinedScanning;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.FieldLayoutContext;
import org.jikesrvm.objectmodel.IMT;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * Description of a java "class" type.<br/>
 *
 * This description is read from a ".class" file as classes/field/methods
 * referenced by the running program need to be bound in to the running image.
 *
 * @see RVMType
 * @see RVMArray
 * @see Primitive
 * @see UnboxedType
 */
@NonMoving
public final class RVMClass extends RVMType implements Constants, ClassLoaderConstants {

  /** Flag for closed world testing */
  public static boolean classLoadingDisabled = false;

  /**
   * The constant pool holds constants used by the class and the Java
   * bytecodes in the methods associated with this class. This
   * constant pool isn't that from the class file, instead it has been
   * processed during class loading (see {@link ClassFileReader#readClass}). The loaded
   * class' constant pool has 3 bits of type information (such as
   * (see {@link ClassLoaderConstants#CP_INT})), the rest of the int holds data as follows:
   *
   * <ul>
   * <li>utf: value is a UTF atom identifier</li>
   * <li>int, long, float, double, string: value is an offset in the
   *     JTOC</li>
   * <li>member: value is a member reference identifier</li>
   * <li>class: value is a type reference identifier. NB this means
   *     that class literal bytecodes must first convert the identifier
   *     in to a JTOC offset.</li>
   * </ul>
   */
  private final int[] constantPool;
  /** {@link ClassLoaderConstants} */
  private final short modifiers;
  /** Super class of this class */
  private final RVMClass superClass;
  /**
   * Non-final list of sub-classes. Classes added as sub-classes are
   * loaded.
   */
  private RVMClass[] subClasses;
  /** Interfaces supported by this class */
  private final RVMClass[] declaredInterfaces;
  /** Fields of this class */
  private final RVMField[] declaredFields;
  /** Methods of this class */
  private final RVMMethod[] declaredMethods;
  /** Declared inner classes, may be null */
  private final TypeReference[] declaredClasses;
  /** The outer class, or null if this is not a inner/nested class */
  private final TypeReference declaringClass;
  /** The enclosing class if this is a local class */
  private final TypeReference enclosingClass;
  /** The enclosing method if this is a local class */
  private final MethodReference enclosingMethod;
  /** Name of file .class file was compiled from, may be null */
  private final Atom sourceName;
  /**
   * The signature is a string representing the generic type for this
   * class declaration, may be null
   */
  private final Atom signature;
  /**
   * Class initializer method, null if no method or if class is
   * initialized (ie class initializer method has been run)
   */
  private RVMMethod classInitializerMethod;

  /**
   * current class-loading stage (loaded, resolved, instantiated,
   * initializing or initialized)
   */
  private byte state;

  //
  // The following are valid only when "state >= CLASS_RESOLVED".
  //

  // --- Field size and offset information --- //

  /** fields shared by all instances of class  */
  private RVMField[] staticFields;

  /** fields distinct for each instance of class */
  private RVMField[] instanceFields;

  /** Total size of per-instance data, in bytes  */
  private int instanceSize;

  /** The desired alignment for instances of this type. */
  private int alignment;

  /**
   * A field layout helper - used to keep context regarding field layouts.
   * Managed by the field layout objects in the ObjectModel.
   */
  private FieldLayoutContext fieldLayoutContext = null;

  // --- Method-dispatching information --- //

  /** static methods of class */
  private RVMMethod[] staticMethods;

  /** constructor methods of class  */
  private RVMMethod[] constructorMethods;

  /** virtual methods of class */
  private RVMMethod[] virtualMethods;

  /**
   * Do objects of this class have a finalizer method?
   */
  private boolean hasFinalizer;

  /** type and virtual method dispatch table for class */
  private TIB typeInformationBlock;

  // --- Memory manager support --- //

  /**
   * Is this class type in the bootimage? Types in the boot image can
   * be initialized prior to execution (therefore removing runtime
   * resolution).
   */
  private boolean inBootImage;

  /**
   * At what offset is the thin lock word to be found in instances of
   * objects of this type?  A value of -1 indicates that the instances of
   * this type do not have inline thin locks.
   */
  private Offset thinLockOffset;

  /** Reference Count GC: is this type acyclic?  */
  private boolean acyclic;

  /** Cached set of inherited and declared annotations. */
  private Annotation[] annotations;

  /** Set of objects that are cached here to ensure they are not collected by GC **/
  private Object[] objectCache;

  /** The imt for this class **/
  @SuppressWarnings("unused")
  private IMT imt;

  // --- Assertion support --- //
  /**
   * Are assertions enabled on this class?
   */
  private final boolean desiredAssertionStatus;

  // --- General purpose functions --- //

  /**
   * Name - something like "java.lang.String".
   */
  @Override
  @Pure
  public String toString() {
    return getDescriptor().classNameFromDescriptor();
  }

  /**
   * Package name - something like "java.lang".
   * Returns the empty string if the class is a member of the unnamed package.
   */
  public String getPackageName() {
    String className = toString();
    int lastDot = className.lastIndexOf(".");
    return (lastDot >= 0) ? className.substring(0, lastDot) : "";
  }

  /**
   * @return 1
   */
  @Override
  @Pure
  @Uninterruptible
  public int getStackWords() {
    return 1;
  }

  @Override
  @Pure
  @Uninterruptible
  public int getMemoryBytes() {
    return BYTES_IN_ADDRESS;
  }

  /**
   * An "interface" description rather than a "class" description?
   */
  @Uninterruptible
  public boolean isInterface() {
    return (modifiers & ACC_INTERFACE) != 0;
  }

  /**
   * Usable from other packages?
   */
  @Uninterruptible
  public boolean isPublic() {
    return (modifiers & ACC_PUBLIC) != 0;
  }

  /**
   * Non-subclassable?
   */
  @Uninterruptible
  public boolean isFinal() {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Non-instantiable?
   */
  @Uninterruptible
  public boolean isAbstract() {
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * Use new-style "invokespecial" semantics for method calls in this class?
   */
  @Uninterruptible
  public boolean isSpecial() {
    return (modifiers & ACC_SUPER) != 0;
  }

  /**
   * Not present in source code file?
   */
  public boolean isSynthetic() {
    return (modifiers & ACC_SYNTHETIC) != 0;
  }

  /**
   * Is enumeration?
   */
  public boolean isEnum() {
    return (modifiers & ACC_ENUM) != 0;
  }

  /**
   * Annotation type
   */
  public boolean isAnnotation() {
    return (modifiers & ACC_ANNOTATION) != 0;
  }

  /**
   * @return true if this is a representation of an anonymous class
   */
  public boolean isAnonymousClass() {
    if (enclosingClass == null || enclosingClass.peekType() == null) return false;
    for(TypeReference t: enclosingClass.peekType().asClass().getDeclaredClasses()) {
      if (t == typeRef) return false;
    }
    return true;
  }

  /**
   * @return true if this is a representation of a local class, ie
   * local to a block of code.
   */
  public boolean isLocalClass() {
    return enclosingMethod != null;
  }

  /**
   * @return true if this is a representation of a member class
   */
  public boolean isMemberClass() {
    return ((declaringClass != null) && ((modifiers & ACC_STATIC) == 0));
  }
  /**
   * @return true if this an object of this class could be assigned to Throwable
   */
  public boolean isThrowable() {
    return (getTypeRef() == TypeReference.JavaLangThrowable) ||
    RuntimeEntrypoints.isAssignableWith(TypeReference.JavaLangThrowable.resolve(), this);
  }
  /**
   * Get the modifiers associated with this class {@link
   * ClassLoaderConstants}.
   */
  public int getModifiers() {
    return modifiers & APPLICABLE_TO_CLASSES;
  }

  /**
   * Generic type information for class
   */
  public Atom getSignature() {
    return signature;
  }

  /**
   * Name of source file from which class was compiled -
   * something like "c:\java\src\java\lang\Object.java".
   * (null --> "unknown - wasn't recorded by compiler").
   */
  public Atom getSourceName() {
    return sourceName;
  }

  /**
   * Superclass of this class (null means "no superclass",
   * ie. class is "java/lang/Object").
   */
  @Uninterruptible
  public RVMClass getSuperClass() {
    return superClass;
  }

  /**
   * Currently loaded classes that "extend" this class.
   */
  @Uninterruptible
  public RVMClass[] getSubClasses() {
    return subClasses;
  }

  /**
   * Interfaces implemented directly by this class
   * (ie. not including superclasses).
   */
  @Uninterruptible
  public RVMClass[] getDeclaredInterfaces() {
    return declaredInterfaces;
  }

  /**
   * Fields defined directly by this class (ie. not including superclasses).
   */
  @Uninterruptible
  public RVMField[] getDeclaredFields() {
    return declaredFields;
  }

  /**
   * Does this class directly define a final instance field (has implications for JMM).
   */
  public boolean declaresFinalInstanceField() {
    for (RVMField f : declaredFields) {
      if (f.isFinal() && !f.isStatic()) return true;
    }
    return false;
  }

  /**
   * Methods defined directly by this class (ie. not including superclasses).
   */
  @Uninterruptible
  public RVMMethod[] getDeclaredMethods() {
    return declaredMethods;
  }

  /**
   * Declared inner and static member classes.
   */
  public TypeReference[] getDeclaredClasses() {
    return declaredClasses;
  }

  /**
   * Class that declared this class, or null if this is not an
   * inner/nested class.
   */
  public TypeReference getDeclaringClass() {
    return declaringClass;
  }

  /**
   * Class that immediately encloses this class, or null if this is not an
   * inner/nested class.
   */
  public TypeReference getEnclosingClass() {
    return enclosingClass;
  }

  /**
   * Set the resolvedMember in all declared members.
   */
  void setResolvedMembers() {
    for(RVMField field: declaredFields) {
      /* Make all declared fields appear resolved */
      field.getMemberRef().asFieldReference().setResolvedMember(field);
    }
    for(RVMMethod method: declaredMethods) {
      /* Make all declared methods appear resolved */
      method.getMemberRef().asMethodReference().setResolvedMember(method);
    }
    if (virtualMethods != null) {
      /* Possibly created Miranda methods */
      for(RVMMethod method: virtualMethods) {
        if (method.getDeclaringClass() == this) {
          method.getMemberRef().asMethodReference().setResolvedMember(method);
        }
      }
    }
  }

  /**
   * Static initializer method for this class (null -> no static initializer
   *  or initializer already been run).
   */
  @Uninterruptible
  public RVMMethod getClassInitializerMethod() {
    return classInitializerMethod;
  }

  @Override
  Annotation[] getAnnotationsInternal() {
    final RVMClass parent = getSuperClass();
    if (parent == null) {
      return super.getAnnotationsInternal();
    }
    if (annotations == null) {
      final Annotation[] declared = getDeclaredAnnotations();
      // Not synchronized as it does not matter if occasionally we create two cached copies
      final Annotation[] parentAnnotations = parent.getAnnotations();
      int rejected = 0;
      for (int i = 0; i < parentAnnotations.length; i++) {
        final Annotation pa = parentAnnotations[i];
        final Class<? extends Annotation> paType = pa.annotationType();
        if (!paType.isAnnotationPresent(Inherited.class)) {
          parentAnnotations[i] = null;
          rejected++;
        } else {
          for (final Annotation a : declared) {
            if (a.annotationType().equals(paType)) {
              parentAnnotations[i] = null;
              rejected++;
              break;
            }
          }
        }
      }
      final Annotation[] cache = new Annotation[declared.length + parentAnnotations.length - rejected];
      System.arraycopy(declared, 0, cache, 0, declared.length);
      int index = declared.length;
      for (final Annotation pa : parentAnnotations) {
        if (pa != null) cache[index++] = pa;
      }
      annotations = cache;
    }
    return annotations;
  }

  /**
   * Find description of a field of this class.
   * @param fieldName field name - something like "foo"
   * @param fieldDescriptor field descriptor - something like "I"
   * @return description (null --> not found)
   */
  public RVMField findDeclaredField(Atom fieldName, Atom fieldDescriptor) {
    for (RVMField field : declaredFields) {
      if (field.getName() == fieldName && field.getDescriptor() == fieldDescriptor) {
        return field;
      }
    }
    return null;
  }

  /**
   * Find description of a field of this class. NB. ignores descriptor.
   * @param fieldName field name - something like "foo"
   * @return description (null --> not found)
   */
  public RVMField findDeclaredField(Atom fieldName) {
    for (RVMField field : declaredFields) {
      if (field.getName() == fieldName) {
        return field;
      }
    }
    return null;
  }

  /**
   * Find description of a method of this class.
   * @param methodName method name - something like "foo"
   * @param methodDescriptor method descriptor - something like "()I"
   * @return description (null --> not found)
   */
  public RVMMethod findDeclaredMethod(Atom methodName, Atom methodDescriptor) {
    for (RVMMethod method : declaredMethods) {
      if (method.getName() == methodName && method.getDescriptor() == methodDescriptor) {
        return method;
      }
    }
    return null;
  }

  /**
   * Find the first description of a method of this class.
   * @param methodName method name - something like "foo"
   * @return description (null --> not found)
   */
  public RVMMethod findDeclaredMethod(Atom methodName) {
    for (RVMMethod method : declaredMethods) {
      if (method.getName() == methodName) {
        return method;
      }
    }
    return null;
  }

  /**
   * Find description of "public static void main(String[])"
   * method of this class.
   * @return description (null --> not found)
   */
  public RVMMethod findMainMethod() {
    Atom mainName = Atom.findOrCreateAsciiAtom(("main"));
    Atom mainDescriptor = Atom.findOrCreateAsciiAtom(("([Ljava/lang/String;)V"));
    RVMMethod mainMethod = this.findDeclaredMethod(mainName, mainDescriptor);

    if (mainMethod == null || !mainMethod.isPublic() || !mainMethod.isStatic()) {
      // no such method
      return null;
    }
    return mainMethod;
  }

  /**
   * Add the given cached object.
   */
  public synchronized void addCachedObject(Object o) {
    Object[] newObjectCache;
    if (objectCache == null) {
      newObjectCache = new Object[1];
    } else {
      newObjectCache = new Object[objectCache.length + 1];
      for (int i=0; i < objectCache.length; i++) {
        newObjectCache[i] = objectCache[i];
      }
    }
    newObjectCache[newObjectCache.length - 1] = o;
    objectCache = newObjectCache;
  }

  /**
   * Set the imt object.
   */
  public void setIMT(IMT imt) {
    this.imt = imt;
  }

  //
  // Constant pool accessors.
  //
  // The constant pool holds literals and external references used by
  // the bytecodes of this class's methods.
  // Items are fetched by specifying their "constant pool index".
  //

  /**
   * Get offset of a literal constant, in bytes.
   * Offset is with respect to virtual machine's "table of contents" (jtoc).
   */
  public Offset getLiteralOffset(int constantPoolIndex) {
    return ClassFileReader.getLiteralOffset(this.constantPool, constantPoolIndex);
  }

  /**
   * Get description of a literal constant.
   */
  public byte getLiteralDescription(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    byte type = ClassFileReader.unpackCPType(cpValue);
    return type;
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  @Uninterruptible
  public TypeReference getTypeRef(int constantPoolIndex) {
    return ClassFileReader.getTypeRef(constantPool, constantPoolIndex);
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   */
  @Uninterruptible
  public MethodReference getMethodRef(int constantPoolIndex) {
    return ClassFileReader.getMethodRef(constantPool, constantPoolIndex);
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   */
  @Uninterruptible
  public FieldReference getFieldRef(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(ClassFileReader.unpackCPType(cpValue) == CP_MEMBER);
    return (FieldReference) MemberReference.getMemberRef(ClassFileReader.unpackUnsignedCPValue(cpValue));
  }

  /**
   * Get contents of a "utf" constant pool entry.
   */
  @Uninterruptible
  Atom getUtf(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(ClassFileReader.unpackCPType(cpValue) == CP_UTF);
    return Atom.getAtom(ClassFileReader.unpackUnsignedCPValue(cpValue));
  }

  /**
   * Should the methods of this class be compiled with special
   * register save/restore logic?
   * @see org.vmmagic.pragma.DynamicBridge
   */
  @Uninterruptible
  public boolean hasDynamicBridgeAnnotation() {
    return isAnnotationDeclared(TypeReference.DynamicBridge);
  }

  /**
   * The methods of this class are only called from native code,
   * they are compiled with
   * a special prolog to interface with the native stack frame.
   */
  @Uninterruptible
  public boolean hasBridgeFromNativeAnnotation() {
    return isAnnotationDeclared(TypeReference.NativeBridge);
  }

  /**
   * Should the methods of this class save incoming registers ?
   * @see org.vmmagic.pragma.SaveVolatile
   */
  public boolean hasSaveVolatileAnnotation() {
    return isAnnotationDeclared(TypeReference.SaveVolatile);
  }

  //--------------------------------------------------------------------//
  // The following are available after the class has been "resolved".   //
  //--------------------------------------------------------------------//

  /**
   * Does this class override java.lang.Object.finalize()?
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean hasFinalizer() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return hasFinalizer;
  }

  /**
   * Static fields of this class.
   * Values in these fields are shared by all class instances.
   */
  @Override
  @Pure
  public RVMField[] getStaticFields() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return staticFields;
  }

  /**
   * Non-static fields of this class (composed with supertypes, if any).
   * Values in these fields are distinct for each class instance.
   */
  @Override
  @Pure
  public RVMField[] getInstanceFields() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return instanceFields;
  }

  /**
   * Statically dispatched methods of this class.
   */
  @Override
  @Pure
  public RVMMethod[] getStaticMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return staticMethods;
  }

  /**
   * Constructors (<init>) methods of this class.
   */
  @Pure
  public RVMMethod[] getConstructorMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved(), "Error class " + this + " is not resolved but " + state);
    return constructorMethods;
  }

  /**
   * Virtually dispatched methods of this class
   * (composed with supertypes, if any).
   */
  @Override
  @Pure
  public RVMMethod[] getVirtualMethods() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return virtualMethods;
  }

  /**
   * @return All of the interfaces implemented by this class either
   * directly or by inheritance from superclass and superinterfaces
   * recursively.
   */
  @Pure
  public RVMClass[] getAllImplementedInterfaces() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    int count = 0;
    int[] doesImplement = getDoesImplement();
    for (int mask : doesImplement) {
      while (mask != 0) {
        count++;
        mask &= (mask - 1); // clear lsb 1 bit
      }
    }
    if (count == 0) return emptyVMClass;
    RVMClass[] ans = new RVMClass[count];
    for (int i = 0, idx = 0; i < doesImplement.length; i++) {
      int mask = doesImplement[i];
      if (mask != 0) {
        for (int j = 0; j < 32; j++) {
          if ((mask & (1 << j)) != 0) {
            int id = 32 * i + j;
            ans[idx++] = RVMClass.getInterface(id);
          }
        }
      }
    }
    return ans;
  }

  /**
   * Total size, in bytes, of an instance of this class
   * (including object header).
   */
  @Uninterruptible
  public int getInstanceSize() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return instanceSize;
  }

  /**
   * Total size, in bytes, of an instance of this class (including
   * object header). Doesn't perform any verification.
   */
  @Uninterruptible
  public int getInstanceSizeInternal() {
    return instanceSize;
  }

  /**
   * Set the size of the instance. Only meant to be called from
   * ObjectModel et al. must be called when lock on class object
   * is already held (ie from resolve).
   */
  @Uninterruptible
  public void setInstanceSizeInternal(int size) {
    instanceSize = size;
  }

  /**
   * @return number of fields that are non-final
   */
  public int getNumberOfNonFinalReferences() {
    int count = 0;
    for(RVMField field: declaredFields) {
      if (!field.isFinal()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Set object representing available holes in the field layout
   */
  public FieldLayoutContext getFieldLayoutContext() {
    return fieldLayoutContext;
  }

  /**
   * Set object representing available holes in the field layout
   */
  public void setFieldLayoutContext(FieldLayoutContext newLayout) {
    fieldLayoutContext = isFinal() ? null : newLayout;
  }

  /**
   * @return alignment for instances of this class type
   */
  @Uninterruptible
  public int getAlignment() {
    if (BYTES_IN_ADDRESS == BYTES_IN_DOUBLE) {
      return BYTES_IN_ADDRESS;
    } else {
      return alignment;
    }
  }

  /**
   * Set the alignment for instances of this class type
   */
  public void setAlignment(int align) {
    if (BYTES_IN_ADDRESS != BYTES_IN_DOUBLE) {
      if (VM.VerifyAssertions) VM._assert(align >= alignment);
      alignment = align;
    }
  }

  /**
   * Find specified static method description.
   * @param memberName method name - something like "foo"
   * @param memberDescriptor method descriptor - something like "I" or "()I"
   * @return method description (null --> not found)
   */
  @Pure
  public RVMMethod findStaticMethod(Atom memberName, Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    for (RVMMethod method : getStaticMethods()) {
      if (method.getName() == memberName && method.getDescriptor() == memberDescriptor) {
        return method;
      }
    }
    return null;
  }

  /**
   * Find specified initializer method description.
   * @param  memberDescriptor  init method descriptor - something like "(I)V"
   * @return method description (null --> not found)
   */
  @Pure
  public RVMMethod findInitializerMethod(Atom memberDescriptor) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    for (RVMMethod method : getConstructorMethods()) {
      if (method.getDescriptor() == memberDescriptor) {
        return method;
      }
    }
    return null;
  }

  /**
   * Runtime type information for this class type.
   */
  @Override
  @Uninterruptible
  public TIB getTypeInformationBlock() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return typeInformationBlock;
  }

  //--------------------------------------------------------------------//
  //                     Miscellaneous queries.                         //
  //---------------------------------------------------------------------//

  /**
   * Support for user-written class loaders:
   * It's required to find the classloader of the class
   * whose method requires another class to be loaded;
   * the initiating loader of the required class is the
   * defining loader of the requiring class.
   *
   *
   * @param skip specifies the number of frames back from the
   *             caller to the method whose class's loader is required
   */
  public static ClassLoader getClassLoaderFromStackFrame(int skip) {
    skip++; // account for stack frame of this function
    StackBrowser browser = new StackBrowser();
    VM.disableGC();
    browser.init();
    while (skip-- > 0) browser.up();
    VM.enableGC();
    return browser.getClassLoader();
  }

  /**
   * Used for accessibility checks in reflection code.
   * Find the class of the method that corresponds to the requested frame.
   *
   * @param skip   Specifies the number of frames back from the
   *               caller to the method whose class is required
   */
  public static RVMClass getClassFromStackFrame(int skip) {
    skip++; // account for stack frame of this function
    StackBrowser browser = new StackBrowser();
    VM.disableGC();
    browser.init();
    while (skip-- > 0) browser.up();
    VM.enableGC();
    return browser.getCurrentClass();
  }

  /**
   * Should assertions be enabled on this type?
   * @return whether or not assertions should be enabled
   */
  @Override
  @Pure
  public boolean getDesiredAssertionStatus() {
    return desiredAssertionStatus;
  }

  //--------------------------------------------------------------------//
  //      Load, Resolve, Instantiate, and Initialize                    //
  //--------------------------------------------------------------------//

  /**
   * Construct a class from its constituent loaded parts
   *
   * @param typeRef the type reference that was resolved to this class
   * @param constantPool array of ints encoding constant value
   * @param modifiers {@link org.jikesrvm.classloader.ClassLoaderConstants}
   * @param superClass parent of this class
   * @param declaredInterfaces array of interfaces this class implements
   * @param declaredFields fields of the class
   * @param declaredMethods methods of the class
   * @param declaredClasses declared inner classes
   * @param declaringClass outer class if an inner class
   * @param sourceName source file name
   * @param classInitializerMethod handle to class initializer method
   * @param signature the generic type name for this class
   * @param annotations array of runtime visible annotations
   */
  RVMClass(TypeReference typeRef, int[] constantPool, short modifiers, RVMClass superClass,
           RVMClass[] declaredInterfaces, RVMField[] declaredFields, RVMMethod[] declaredMethods,
           TypeReference[] declaredClasses, TypeReference declaringClass, TypeReference enclosingClass,
           MethodReference enclosingMethod, Atom sourceName, RVMMethod classInitializerMethod,
           Atom signature, RVMAnnotation[] annotations) {
    super(typeRef, 0, annotations);
    if (VM.VerifyAssertions) VM._assert(!getTypeRef().isUnboxedType());
    if (VM.VerifyAssertions && null != superClass) VM._assert(!superClass.getTypeRef().isUnboxedType());

    // final fields
    this.constantPool = constantPool;
    this.modifiers = modifiers;
    this.superClass = superClass;
    this.declaredInterfaces = declaredInterfaces;
    this.declaredFields = declaredFields;
    this.declaredMethods = declaredMethods;
    this.declaredClasses = declaredClasses;
    this.declaringClass = declaringClass;
    this.enclosingClass = enclosingClass;
    this.enclosingMethod = enclosingMethod;
    this.sourceName = sourceName;
    this.classInitializerMethod = classInitializerMethod;
    this.signature = signature;

    // non-final fields
    this.subClasses = emptyVMClass;
    state = CLASS_LOADED;

    // we're about to leak a reference to 'this' force memory to be
    // consistent
    Magic.sync();

    if (superClass != null) {
      // MUST wait until end of constructor to 'publish' the subclass link.
      // If we do this earlier, then people can see an incomplete RVMClass object
      // by traversing the subclasses of our superclass!
      superClass.addSubClass(this);
    }

    this.desiredAssertionStatus = RVMClassLoader.getDesiredAssertionStatus(this);

    Callbacks.notifyClassLoaded(this);

    if (VM.TraceClassLoading && VM.runningVM) {
      VM.sysWriteln("RVMClass: (end)   load file " + typeRef.getName());
    }
    if (VM.verboseClassLoading) VM.sysWrite("[Loaded " + toString() + "]\n");
  }

  /**
   * Generate size and offset information for members of this class and
   * allocate space in jtoc for static fields, static methods, and virtual
   * method table.
   * Side effects: superclasses and superinterfaces are resolved.
   */
  @Override
  public synchronized void resolve() {
    if (isResolved()) return;
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (begin) resolve " + this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_LOADED);

    // Resolve superclass and super interfaces
    //
    if (superClass != null) {
      superClass.resolve();
    }
    for (RVMClass declaredInterface : declaredInterfaces) {
      declaredInterface.resolve();
    }

    if (isInterface()) {
      if (VM.VerifyAssertions) VM._assert(superClass == null);
      depth = 1;
      thinLockOffset = Offset.max();
    } else if (superClass == null) {
      if (VM.VerifyAssertions) VM._assert(isJavaLangObjectType());
      instanceSize = ObjectModel.computeScalarHeaderSize(this);
      alignment = BYTES_IN_ADDRESS;
      thinLockOffset = ObjectModel.defaultThinLockOffset();
      depth=0;
    } else {
      depth = superClass.depth + 1;
      thinLockOffset = superClass.thinLockOffset;
      instanceSize = superClass.instanceSize;
      fieldLayoutContext = superClass.fieldLayoutContext;
      alignment = superClass.alignment;
    }

    if (this == RVMType.JavaLangClassType) {
      ObjectModel.allocateThinLock(this);
    }

    if (superClass != null && superClass.isNonMoving() && !isNonMoving()) {
      VM.sysWriteln("WARNING: movable " + this + " extends non-moving " + superClass);
    }

    if (VM.verboseClassLoading) VM.sysWrite("[Preparing " + this + "]\n");

    // build field and method lists for this class
    //
    {
      FieldVector staticFields = new FieldVector();
      FieldVector instanceFields = new FieldVector();
      MethodVector staticMethods = new MethodVector();
      MethodVector constructorMethods = new MethodVector();
      MethodVector virtualMethods = new MethodVector();

      // start with fields and methods of superclass
      //
      if (superClass != null) {
        for (RVMField field : superClass.getInstanceFields()) {
          instanceFields.addElement(field);
        }

        for (RVMMethod method : superClass.getVirtualMethods()) {
          virtualMethods.addElement(method);
        }
      }

      // append fields defined by this class
      //
      for (RVMField field : getDeclaredFields()) {
        if (field.isStatic()) {
          staticFields.addElement(field);
        } else {
          instanceFields.addElement(field);
        }
      }

      // append/overlay methods defined by this class
      //
      for (RVMMethod method : getDeclaredMethods()) {
        if (VM.VerifyUnint) {
          if (method.isSynchronized() && method.isUninterruptible()) {
            if (VM.ParanoidVerifyUnint || !method.hasLogicallyUninterruptibleAnnotation()) {
              VM.sysWriteln("WARNING: " + method + " cannot be both uninterruptible and synchronized");
            }
          }
        }

        if (method.isObjectInitializer()) {
          Callbacks.notifyMethodOverride(method, null);
          constructorMethods.addElement(method);
        } else if (method.isStatic()) {
          if (!method.isClassInitializer()) {
            Callbacks.notifyMethodOverride(method, null);
            staticMethods.addElement(method);
          }
        } else { // Virtual method

          if (method.isSynchronized()) {
            ObjectModel.allocateThinLock(this);
          }

          // method could override something in superclass - check for it
          //
          int superclassMethodIndex = -1;
          for (int j = 0, m = virtualMethods.size(); j < m; ++j) {
            RVMMethod alreadyDefinedMethod = virtualMethods.elementAt(j);
            if (alreadyDefinedMethod.getName() == method.getName() &&
                alreadyDefinedMethod.getDescriptor() == method.getDescriptor()) {
              // method already defined in superclass
              superclassMethodIndex = j;
              break;
            }
          }

          if (superclassMethodIndex == -1) {
            Callbacks.notifyMethodOverride(method, null);
            virtualMethods.addElement(method);                          // append
          } else {
            RVMMethod superc = virtualMethods.elementAt(superclassMethodIndex);
            if (VM.VerifyUnint) {
              if (!superc.isInterruptible() && method.isInterruptible()) {
                VM.sysWriteln("WARNING: interruptible " + method + " overrides uninterruptible " + superc);
              }
            }
            Callbacks.notifyMethodOverride(method, superc);
            virtualMethods.setElementAt(method, superclassMethodIndex); // override
          }
        }
      }

      // Deal with Miranda methods.
      // If this is an abstract class, then for each
      // interface that this class implements, ensure that a corresponding virtual
      // method is declared.  If one is not, then create an abstract method to fill the void.
      if (!isInterface() && isAbstract()) {
        for (RVMClass I : declaredInterfaces) {
          RVMMethod[] iMeths = I.getVirtualMethods();
          outer:
          for (RVMMethod iMeth : iMeths) {
            Atom iName = iMeth.getName();
            Atom iDesc = iMeth.getDescriptor();
            for (int k = 0; k < virtualMethods.size(); k++) {
              RVMMethod vMeth = virtualMethods.elementAt(k);
              if (vMeth.getName() == iName && vMeth.getDescriptor() == iDesc) continue outer;
            }
            MemberReference mRef = MemberReference.findOrCreate(typeRef, iName, iDesc);
            virtualMethods.addElement(new AbstractMethod(getTypeRef(),
                                                            mRef,
                                                            (short) (ACC_ABSTRACT | ACC_PUBLIC),
                                                            iMeth.getExceptionTypes(),
                                                            null,
                                                            null,
                                                            null,
                                                            null));
          }
        }
      }

      // If this is an interface, inherit methods from its superinterfaces
      if (isInterface()) {
        for (RVMClass declaredInterface : declaredInterfaces) {
          RVMMethod[] meths = declaredInterface.getVirtualMethods();
          for (RVMMethod meth : meths) {
            virtualMethods.addUniqueElement(meth);
          }
        }
      }

      this.staticFields = staticFields.finish();
      this.instanceFields = instanceFields.finish();
      this.staticMethods = staticMethods.finish();
      this.constructorMethods = constructorMethods.finish();
      this.virtualMethods = virtualMethods.finish();
    }

    // allocate space for class fields
    //
    for (RVMField field : staticFields) {
      if (field.isReferenceType()) {
        field.setOffset(Statics.allocateReferenceSlot(true));
      } else if (field.getSize() <= BYTES_IN_INT) {
        field.setOffset(Statics.allocateNumericSlot(BYTES_IN_INT, true));
      } else {
        field.setOffset(Statics.allocateNumericSlot(BYTES_IN_LONG, true));
      }

      // (SJF): Serialization nastily accesses even final private static
      //           fields via pseudo-reflection! So, we must shove the
      //           values of final static fields into the JTOC.  Now
      //           seems to be a good time.
      if (field.isFinal()) {
        setFinalStaticJTOCEntry(field, field.getOffset());
      }
    }

    // lay out instance fields
    //
    ObjectModel.layoutInstanceFields(this);

    // count reference fields
    int referenceFieldCount = 0;
    for (RVMField field : instanceFields) {
      if (field.isTraced()) {
        referenceFieldCount += 1;
      }
    }

    // record offsets of those instance fields that contain references
    //
    if (typeRef.isRuntimeTable()) {
      referenceOffsets = MemoryManager.newNonMovingIntArray(0);
    } else {
      referenceOffsets = MemoryManager.newNonMovingIntArray(referenceFieldCount);
      int j = 0;
      for (RVMField field : instanceFields) {
        if (field.isTraced()) {
          referenceOffsets[j++] = field.getOffset().toInt();
        }
      }
    }

    // Allocate space for <init> method pointers
    //
    for (RVMMethod method : constructorMethods) {
      method.setOffset(Statics.allocateReferenceSlot(true));
    }

    // Allocate space for static method pointers
    //
    for (RVMMethod method : staticMethods) {
      if (method.isClassInitializer()) {
        method.setOffset(Offset.fromIntZeroExtend(0xebad0ff5)); // should never be used.
      } else {
        method.setOffset(Statics.allocateReferenceSlot(true));
      }
    }

    if (!isInterface()) {
      // lay out virtual method section of type information block
      // (to be filled in by instantiate)
      for (int i = 0, n = virtualMethods.length; i < n; ++i) {
        RVMMethod method = virtualMethods[i];
        method.setOffset(TIB.getVirtualMethodOffset(i));
      }
    }

    // RCGC: Determine if class is inherently acyclic
    acyclic = false;    // must initially be false for recursive types
    boolean foundCyclic = false;
    for (RVMField instanceField : instanceFields) {
      TypeReference ft = instanceField.getType();
      if (!ft.isResolved() || !ft.peekType().isAcyclicReference()) {
        foundCyclic = true;
        break;
      }
    }
    if (!foundCyclic) {
      acyclic = true;
    }

    // allocate "type information block"
    TIB allocatedTib;
    if (isInterface()) {
      allocatedTib = MemoryManager.newTIB(0, AlignmentEncoding.ALIGN_CODE_NONE);
    } else if (isAnnotationDeclared(TypeReference.ReferenceFieldsVary)) {
      allocatedTib = MemoryManager.newTIB(virtualMethods.length, HandInlinedScanning.fallback());
    } else {
      allocatedTib = MemoryManager.newTIB(virtualMethods.length, HandInlinedScanning.scalar(referenceOffsets));
    }

    superclassIds = DynamicTypeCheck.buildSuperclassIds(this);
    doesImplement = DynamicTypeCheck.buildDoesImplement(this);

    // can't move this beyond "finalize" code block as findVirtualMethod
    // assumes state >= RESOLVED, no allocation occurs until
    // state >= CLASS_INITIALIZING
    publishResolved(allocatedTib, superclassIds, doesImplement);

    // TODO: Make this into a more general listener interface
    if (VM.BuildForOptCompiler && VM.writingBootImage) {
      classLoadListener.classInitialized(this, true);
    }

    Callbacks.notifyClassResolved(this);
    MemoryManager.notifyClassResolved(this);

    // check for a "finalize" method that overrides the one in java.lang.Object
    //
    if (!isInterface()) {
      final RVMMethod method =
          findVirtualMethod(RVMClassLoader.StandardObjectFinalizerMethodName,
                            RVMClassLoader.StandardObjectFinalizerMethodDescriptor);
      if (!method.getDeclaringClass().isJavaLangObjectType()) {
        hasFinalizer = true;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (end)   resolve " + this);
  }

  /**
   * Atomically initialize the important parts of the TIB and let the world know this type is
   * resolved.
   *
   * @param allocatedTib The TIB that has been allocated for this type
   * @param superclassIds The calculated superclass ids array
   * @param doesImplement The calculated does implement array
   */
  @Uninterruptible
  private void publishResolved(TIB allocatedTib, short[] superclassIds, int[] doesImplement) {
    Statics.setSlotContents(getTibOffset(), allocatedTib);
    allocatedTib.setType(this);
    allocatedTib.setSuperclassIds(superclassIds);
    allocatedTib.setDoesImplement(doesImplement);
    typeInformationBlock = allocatedTib;
    state = CLASS_RESOLVED;
  }

  @Override
  public void allBootImageTypesResolved() {
    for (RVMMethod method : declaredMethods) {
      if (method instanceof NormalMethod) {
        ((NormalMethod)method).recomputeSummary(constantPool);
      }
    }
  }


  /**
   * @return <code>true</code> if the class is acyclic and
   * final (otherwise the reference could be to a subsequently loaded
   * cyclic subclass)
   */
  @Override
  @Uninterruptible
  public boolean isAcyclicReference() {
    return acyclic && isFinal();
  }

  /**
   * Insert the value of a final static field into the JTOC
   */
  private void setFinalStaticJTOCEntry(RVMField field, Offset fieldOffset) {
    if (!field.isFinal()) return;

    // value Index: index into the classes constant pool.
    int valueIndex = field.getConstantValueIndex();

    // if there's no value in the constant pool, bail out
    if (valueIndex <= 0) return;

    Offset literalOffset = field.getDeclaringClass().getLiteralOffset(valueIndex);

    if (Statics.isReference(Statics.offsetAsSlot(fieldOffset))) {
      Object obj = Statics.getSlotContentsAsObject(literalOffset);
      Statics.setSlotContents(fieldOffset, obj);
    } else if (field.getSize() <= BYTES_IN_INT) {
      // copy one word from constant pool to JTOC
      int value = Statics.getSlotContentsAsInt(literalOffset);
      Statics.setSlotContents(fieldOffset, value);
    } else {
      // copy two words from constant pool to JTOC
      long value = Statics.getSlotContentsAsLong(literalOffset);
      Statics.setSlotContents(fieldOffset, value);
    }
  }

  /**
   * Compile this class's methods, build type information block, populate jtoc.
   * Side effects: superclasses are instantiated.
   */
  @Override
  public synchronized void instantiate() {
    if (isInstantiated()) {
      return;
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (begin) instantiate " + this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_RESOLVED);

    // instantiate superclass
    //
    if (superClass != null) {
      superClass.instantiate();
    }
    if (VM.runningVM) {
      // can't instantiate if building bootimage, since this can cause
      // class initializer to be lost (when interface is not included in bootimage).
      // since we don't need to instantiate/initialize for the purposes of
      // dynamic type checking and interface invocation, defer it until runtime
      // and the class actually refers to a static field of the interface.
      for (RVMClass declaredInterface : declaredInterfaces) {
        declaredInterface.instantiate();
      }
    }

    if (!isInterface()) {
      // Create the internal lazy method invoker trampoline
      typeInformationBlock.initializeInternalLazyCompilationTrampoline();

      // Initialize slots in the TIB for virtual methods
      for(int i=0; i < virtualMethods.length; i++) {
        RVMMethod method = virtualMethods[i];
        if (method.isPrivate() && method.getDeclaringClass() != this) {
          typeInformationBlock.setVirtualMethod(i, null); // an inherited private method....will never be invoked via this TIB
        } else {
          typeInformationBlock.setVirtualMethod(i, method.getCurrentEntryCodeArray());
        }
      }

      // compile <init> methods and put their addresses into jtoc
      for (RVMMethod method : constructorMethods) {
        Statics.setSlotContents(method.getOffset(), method.getCurrentEntryCodeArray());
      }

      // compile static methods and put their addresses into jtoc
      for (RVMMethod method : staticMethods) {
        // don't bother compiling <clinit> here;
        // compile it right before we invoke it in initialize.
        // This also avoids putting <clinit>s in the bootimage.
        if (!method.isClassInitializer()) {
          Statics.setSlotContents(method.getOffset(), method.getCurrentEntryCodeArray());
        }
      }
    }

    InterfaceInvocation.initializeDispatchStructures(this);
    SpecializedMethodManager.notifyTypeInstantiated(this);

    if (VM.writingBootImage) {
      state = CLASS_INITIALIZED;
      // Mark final fields as literals as class initializer won't have been called
      markFinalFieldsAsLiterals();
    } else {
      state = CLASS_INSTANTIATED;
    }

    Callbacks.notifyClassInstantiated(this);
    if (VM.writingBootImage) {
      Callbacks.notifyClassInitialized(this);
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (end)   instantiate " + this);
  }

  /**
   * Make the passed field a traced field by garbage collection. Also affects all
   * subclasses.
   */
  public void makeFieldTraced(RVMField field) {
    int[] oldOffsets = referenceOffsets;
    int fieldOffset = field.getOffset().toInt();
    referenceOffsets = MemoryManager.newNonMovingIntArray(oldOffsets.length + 1);
    int i;
    for(i=0; i < oldOffsets.length && oldOffsets[i] < fieldOffset; i++) {
      referenceOffsets[i] = oldOffsets[i];
    }
    referenceOffsets[i++] = fieldOffset;
    while(i < referenceOffsets.length) {
      referenceOffsets[i] = oldOffsets[i-1];
      i++;
    }
    SpecializedMethodManager.refreshSpecializedMethods(this);

    for(RVMClass klass: subClasses) {
      klass.makeFieldTraced(field);
    }
  }

  /**
   * Execute this class's static initializer, <clinit>.
   * Side effects: superclasses are initialized, static fields receive
   * initial values.
   */
  @Override
  public synchronized void initialize() throws ExceptionInInitializerError {
    if (isInitialized()) {
      return;
    }

    if (state == CLASS_INITIALIZING) {
      return;
    }

    if (state == CLASS_INITIALIZER_FAILED) {
      throw new NoClassDefFoundError(this+" (initialization failure)");
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (begin) initialize " + this);
    if (VM.VerifyAssertions) VM._assert(state == CLASS_INSTANTIATED);
    state = CLASS_INITIALIZING;
    if (VM.verboseClassLoading) VM.sysWrite("[Initializing " + this + "]\n");

    // run super <clinit>
    //
    if (superClass != null) {
      superClass.initialize();
    }

    // run <clinit>
    //
    if (classInitializerMethod != null) {
      CompiledMethod cm = classInitializerMethod.getCurrentCompiledMethod();
      while (cm == null) {
        classInitializerMethod.compile();
        cm = classInitializerMethod.getCurrentCompiledMethod();
      }

      if (VM.verboseClassLoading) VM.sysWrite("[Running static initializer for " + this + "]\n");

      try {
        Magic.invokeClassInitializer(cm.getEntryCodeArray());
      } catch (Error e) {
        state = CLASS_INITIALIZER_FAILED;
        throw e;
      } catch (Throwable t) {
        ExceptionInInitializerError eieio = new ExceptionInInitializerError("While initializing " + this);
        eieio.initCause(t);
        state = CLASS_INITIALIZER_FAILED;
        if (VM.verboseClassLoading) {
          VM.sysWriteln("[Exception in initializer error caused by:");
          t.printStackTrace();
          VM.sysWriteln("]");
        }
        throw eieio;
      }

      // <clinit> is no longer needed: reclaim space by removing references to it
      classInitializerMethod.invalidateCompiledMethod(cm);
      classInitializerMethod = null;
    }

    if (VM.BuildForOptCompiler) {
      // report that a class is about to be marked initialized to
      // the opt compiler so it can invalidate speculative CHA optimizations
      // before an instance of this class could actually be created.
      classLoadListener.classInitialized(this, false);
    }

    state = CLASS_INITIALIZED;

    Callbacks.notifyClassInitialized(this);

    markFinalFieldsAsLiterals();

    if (VM.verboseClassLoading) VM.sysWrite("[Initialized " + this + "]\n");
    if (VM.TraceClassLoading && VM.runningVM) VM.sysWriteln("RVMClass: (end)   initialize " + this);
  }

  /**
   * Mark final fields as being available as literals
   */
  private void markFinalFieldsAsLiterals() {
    for (RVMField f : getStaticFields()) {
      if (f.isFinal()) {
        Offset fieldOffset = f.getOffset();
        if (Statics.isReference(Statics.offsetAsSlot(fieldOffset))) {
          Statics.markAsReferenceLiteral(fieldOffset);
        } else {
          Statics.markAsNumericLiteral(f.getSize(), fieldOffset);
        }
      }
    }
  }

  /**
   * Copy the values of all static final fields into
   * the JTOC.  Note: This method should only be run AFTER
   * the class initializer has run.
   */
  public void setAllFinalStaticJTOCEntries() {
    if (VM.VerifyAssertions) VM._assert(isInitialized());
    for (RVMField f : getStaticFields()) {
      if (f.isFinal()) {
        setFinalStaticJTOCEntry(f, f.getOffset());
      }
    }
  }

  void resolveNativeMethods() {
    if (VM.VerifyAssertions) VM._assert(isInitialized());
    resolveNativeMethodsInternal(getStaticMethods());
    resolveNativeMethodsInternal(getVirtualMethods());
  }

  private void resolveNativeMethodsInternal(RVMMethod[] methods) {
    for (RVMMethod m : methods) {
      if (m.isNative()) {
        m.replaceCompiledMethod(null);
      }
    }
  }

  /**
   * Unregisters all native methods
   */
  public void unregisterNativeMethods() {
    if (VM.VerifyAssertions) VM._assert(isInitialized());
    for (RVMMethod m : declaredMethods) {
      if (m.isNative()) {
        NativeMethod nm = (NativeMethod) m;
        nm.unregisterNativeSymbol();
        m.replaceCompiledMethod(null);
      }
    }
  }

  /**
   * Add to list of classes that derive from this one.
   */
  private void addSubClass(RVMClass sub) {
    int n = subClasses.length;
    RVMClass[] tmp = new RVMClass[n + 1];

    for (int i = 0; i < n; ++i) {
      tmp[i] = subClasses[i];
    }
    tmp[n] = sub;

    subClasses = tmp;
  }

  //------------------------------------------------------------//
  // Support for speculative optimizations that may need to
  // invalidate compiled code when new classes are loaded.
  //
  // TODO: Make this into a more general listener API
  //------------------------------------------------------------//
  public static final ClassLoadingListener classLoadListener =
      VM.BuildForOptCompiler ? new ClassLoadingDependencyManager() : null;

  /**
   * Given a method declared by this class, update all
   * dispatching tables to refer to the current compiled
   * code for the method.
   */
  public void updateMethod(RVMMethod m) {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    if (VM.VerifyAssertions) VM._assert(m.getDeclaringClass() == this);
    if (m.isClassInitializer()) return; // we never put this method in the jtoc anyways!

    if (m.isStatic() || m.isObjectInitializer()) {
      updateJTOCEntry(m);
    } else {
      updateVirtualMethod(m);
    }
  }

  /**
   * Update the JTOC slot for the given static method to point to
   * the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateJTOCEntry(RVMMethod m) {
    if (VM.VerifyAssertions) VM._assert(m.getDeclaringClass() == this);
    if (VM.VerifyAssertions) VM._assert(isResolved());
    if (VM.VerifyAssertions) VM._assert(m.isStatic() || m.isObjectInitializer());
    Statics.setSlotContents(m.getOffset(), m.getCurrentEntryCodeArray());
  }

  /**
   * Update this class's TIB entry for the given method to point to
   * the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateTIBEntry(RVMMethod m) {
    if (VM.VerifyAssertions) {
      RVMMethod vm = findVirtualMethod(m.getName(), m.getDescriptor());
      VM._assert(vm == m);
    }
    typeInformationBlock.setVirtualMethod(m.getOffset(), m.getCurrentEntryCodeArray());
    InterfaceInvocation.updateTIBEntry(this, m);
  }

  /**
   * Update the TIB entry's for all classes that inherit the given method
   * to point to the current compiled code for the given method.
   * NOTE: This method is intentionally not synchronized to avoid deadlocks.
   *       We instead rely on the fact that we are always updating the JTOC with
   *       the most recent instructions for the method.
   */
  public void updateVirtualMethod(RVMMethod m) {
    RVMMethod dm = findDeclaredMethod(m.getName(), m.getDescriptor());
    if (dm != null && dm != m) return;  // this method got overridden
    updateTIBEntry(m);
    if (m.isPrivate()) return; // can't override
    for (RVMClass sc : getSubClasses()) {
      if (sc.isResolved()) {
        sc.updateVirtualMethod(m);
      }
    }
  }

  //------------------------------------------------------------//
  // Additional fields and methods for Interfaces               //
  //------------------------------------------------------------//

  private static final Object interfaceCountLock = new Object();
  private static int interfaceCount = 0;
  private static RVMClass[] interfaces;
  private int interfaceId = -1;
  RVMMethod[] noIMTConflictMap; // used by InterfaceInvocation to support resetTIB

  /**
   * Classes used as Interfaces get assigned an interface id.
   *   If the class is not an interface, attempting to use this
   *   id will cause an IncompatibleClassChangeError to be thrown
   */
  public int getInterfaceId() {
    if (interfaceId == -1) {
      assignInterfaceId();
    }
    return interfaceId;
  }

  public int getDoesImplementIndex() {
    return getInterfaceId() >>> 5;
  }

  public int getDoesImplementBitMask() {
    return 1 << (getInterfaceId() & 31);
  }

  public static RVMClass getInterface(int id) {
    return interfaces[id];
  }

  private synchronized void assignInterfaceId() {
    if (interfaceId == -1) {
      if (interfaceCountLock != null && interfaces != null) {
        synchronized (interfaceCountLock) {
          interfaceId = interfaceCount++;
          if (interfaceId == interfaces.length) {
            RVMClass[] tmp = new RVMClass[interfaces.length * 2];
            System.arraycopy(interfaces, 0, tmp, 0, interfaces.length);
            interfaces = tmp;
          }
          interfaces[interfaceId] = this;
        }
      } else {
        interfaceId = interfaceCount++;
        if (interfaces == null) {
          interfaces = new RVMClass[200];
        }
        interfaces[interfaceId] = this;
      }
    }
  }

  /**
   * Number of [ in descriptor for arrays; -1 for primitives; 0 for
   * classes
   * @return 0
   */
  @Override
  @Pure
  @Uninterruptible
  public int getDimensionality() {
    return 0;
  }

  @Override
  @Uninterruptible
  public boolean isResolved() {
    return state >= CLASS_RESOLVED;
  }

  @Override
  @Uninterruptible
  public boolean isInstantiated() {
    return state >= CLASS_INSTANTIATED;
  }

  @Override
  @Uninterruptible
  public boolean isInitialized() {
    return state == CLASS_INITIALIZED;
  }

  @Override
  public void markAsBootImageClass() {
    inBootImage = true;
  }

  @Override
  @Uninterruptible
  public boolean isInBootImage() {
    return inBootImage;
  }

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * Is only known after class has been resolved.
   */
  @Override
  @Uninterruptible
  public Offset getThinLockOffset() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return thinLockOffset;
  }

 /**
   * Set the thin lock offset for instances of this type. Can be called at most once
   * and is invoked from ObjectModel.allocateThinLock (in object models which
   * do not allocate thin locks for all scalar object types).
   */
  public void setThinLockOffset(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(thinLockOffset.isMax());
    if (VM.VerifyAssertions) VM._assert(!offset.isMax());
    thinLockOffset = offset;
  }

  /**
   * Get number of superclasses to Object.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getTypeDepth() {
    return depth;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isClassType() {
    return true;
  }

  /**
   * @return <code>false</code>
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isArrayType() {
    return false;
  }

  /**
   * @return <code>false</code>
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isPrimitiveType() {
    return false;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isReferenceType() {
    return true;
  }

  /**
   * @return <code>false</code>
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isUnboxedType() {
    return false;
  }

  /**
   * Create a synthetic class that extends ReflectionBase and invokes the given method
   * @param methodToCall the method we wish to call reflectively
   * @return the synthetic class
   */
  static Class<?> createReflectionClass(RVMMethod methodToCall) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    if (DynamicTypeCheck.instanceOfResolved(TypeReference.baseReflectionClass.resolve(), methodToCall.getDeclaringClass())) {
      // Avoid reflection on reflection base class
      return null;
    }
    int[] constantPool = new int[methodToCall.getParameterTypes().length+3];
    String reflectionClassName = "Lorg/jikesrvm/classloader/ReflectionBase$$Reflect"+methodToCall.getMemberRef().getId()+";";
    TypeReference reflectionClass = TypeReference.findOrCreate(reflectionClassName);
    RVMType klass = reflectionClass.peekType();
    if (klass == null) {
      MethodReference reflectionMethodRef = MethodReference.findOrCreate(reflectionClass,
          Atom.findOrCreateUnicodeAtom("invokeInternal"),
          Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")
      ).asMethodReference();
      MethodReference constructorMethodRef = MethodReference.findOrCreate(reflectionClass,
          Atom.findOrCreateUnicodeAtom("<init>"),
          Atom.findOrCreateUnicodeAtom("()V")
      ).asMethodReference();

      RVMMethod[] reflectionMethods = new RVMMethod[]{
          methodToCall.createReflectionMethod(reflectionClass, constantPool, reflectionMethodRef),
          RVMMethod.createDefaultConstructor(reflectionClass, constructorMethodRef)};
      klass =
        new RVMClass(reflectionClass, constantPool, (short) (ACC_SYNTHETIC | ACC_PUBLIC | ACC_FINAL), // modifiers
            TypeReference.baseReflectionClass.resolve().asClass(), // superClass
            emptyVMClass, // declaredInterfaces
            emptyVMField, reflectionMethods,
            null, null, null, null, null, null, null, null);
      reflectionClass.setType(klass);
      RuntimeEntrypoints.initializeClassForDynamicLink(klass.asClass());
    }
    return klass.getClassForType();
  }
}
