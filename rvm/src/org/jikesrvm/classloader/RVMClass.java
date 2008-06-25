/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Array;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.Constants;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.inlining.ClassLoadingDependencyManager;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.FieldLayoutContext;
import org.jikesrvm.objectmodel.IMT;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.jikesrvm.util.LinkedListRVM;
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
 */
@NonMoving
public final class RVMClass extends RVMType implements Constants, ClassLoaderConstants {

  /** Flag for closed world testing */
  public static boolean classLoadingDisabled = false;

  /** Constant pool entry for a UTF-8 encoded atom */
  public static final byte CP_UTF = 0;

  /** Constant pool entry for int literal */
  public static final byte CP_INT = 1;

  /** Constant pool entry for long literal */
  public static final byte CP_LONG = 2;

  /** Constant pool entry for float literal */
  public static final byte CP_FLOAT = 3;

  /** Constant pool entry for double literal */
  public static final byte CP_DOUBLE = 4;

  /** Constant pool entry for string literal (for annotations, may be other objects) */
  public static final byte CP_STRING = 5;

  /** Constant pool entry for member (field or method) reference */
  public static final byte CP_MEMBER = 6;

  /** Constant pool entry for type reference or class literal */
  public static final byte CP_CLASS = 7;

  /**
   * The constant pool holds constants used by the class and the Java
   * bytecodes in the methods associated with this class. This
   * constant pool isn't that from the class file, instead it has been
   * processed during class loading (see {@link #readClass}). The loaded
   * class' constant pool has 3 bits of type information (such as
   * (see {@link #CP_INT})), the rest of the int holds data as follows:
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

  /** offsets of reference-containing instance fields */
  private int[] referenceOffsets;

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
   * method that overrides java.lang.Object.finalize()
   * null => class does not have a finalizer
   */
  private RVMMethod finalizeMethod;

  /** type and virtual method dispatch table for class */
  private TIB typeInformationBlock;

  // --- Annotation support --- //

  /**
   * Map from interfaces of annotations to the classes that implement them
   */
  private static final ImmutableEntryHashMapRVM<RVMClass, RVMClass> annotationClasses =
    new ImmutableEntryHashMapRVM<RVMClass, RVMClass>();

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
  private final LinkedListRVM<Object> objectCache;

  /** The imt for this class **/
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
   * Stack space requirement in words.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getStackWords() {
    return 1;
  }

  /**
   * Space required in memory in bytes.
   */
  @Override
  @Pure
  @Uninterruptible
  public int getMemoryBytes() {
    return BYTES_IN_ADDRESS;
  }

  /**
   * If class is an annotation (which means its actually an
   * interface), get the class that implements it
   */
  RVMClass getAnnotationClass() {
    if (VM.VerifyAssertions) VM._assert(this.isAnnotation());
    return annotationClasses.get(this);
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
    return (enclosingClass != null) && (enclosingMethod == null);
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
    for (int i = 0, n = declaredFields.length; i < n; ++i) {
      RVMField field = declaredFields[i];
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
    for (int i = 0, n = declaredFields.length; i < n; ++i) {
      RVMField field = declaredFields[i];
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
    for (int i = 0, n = declaredMethods.length; i < n; ++i) {
      RVMMethod method = declaredMethods[i];
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
    for (int i = 0, n = declaredMethods.length; i < n; ++i) {
      RVMMethod method = declaredMethods[i];
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
    objectCache.add(o);
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

  @Uninterruptible
  private static int packCPEntry(byte type, int value) {
    return (type << 29) | (value & 0x1fffffff);
  }

  @Uninterruptible
  private static byte unpackCPType(int cpValue) {
    return (byte) (cpValue >>> 29);
  }

  @Uninterruptible
  private static int unpackSignedCPValue(int cpValue) {
    return (cpValue << 3) >> 3;
  }

  @Uninterruptible
  private static int unpackUnsignedCPValue(int cpValue) {
    return cpValue & 0x1fffffff;
  }

  @Uninterruptible
  private static boolean packedCPTypeIsClassType(int cpValue) {
    return (cpValue & (7 << 29)) == (CP_CLASS << 29);
  }

  @Uninterruptible
  private static int packTempCPEntry(int index1, int index2) {
    return (index1 << 16) | (index2 & 0xffff);
  }

  @Uninterruptible
  private static int unpackTempCPIndex1(int cpValue) {
    return cpValue >>> 16;
  }

  @Uninterruptible
  private static int unpackTempCPIndex2(int cpValue) {
    return cpValue & 0xffff;
  }

  static int getLiteralSize(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    switch (unpackCPType(cpValue)) {
      case CP_INT:
      case CP_FLOAT:
        return BYTES_IN_INT;
      case CP_LONG:
      case CP_DOUBLE:
        return BYTES_IN_LONG;
      case CP_CLASS:
      case CP_STRING:
        return BYTES_IN_ADDRESS;
      default:
        VM._assert(NOT_REACHED);
        return 0;
    }
  }

  /**
   * Get offset of a literal constant, in bytes.
   * Offset is with respect to virtual machine's "table of contents" (jtoc).
   */
  public Offset getLiteralOffset(int constantPoolIndex) {
    return getLiteralOffset(this.constantPool, constantPoolIndex);
  }

  /**
   * Get offset of a literal constant, in bytes.
   * Offset is with respect to virtual machine's "table of contents" (jtoc).
   */
  static Offset getLiteralOffset(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) {
      int value = unpackSignedCPValue(cpValue);
      byte type = unpackCPType(cpValue);
      switch (type) {
        case CP_INT:
        case CP_FLOAT:
        case CP_LONG:
        case CP_DOUBLE:
        case CP_STRING:
          return Offset.fromIntSignExtend(value);
        case CP_CLASS: {
          int typeId = unpackUnsignedCPValue(cpValue);
          Class<?> literalAsClass = TypeReference.getTypeRef(typeId).resolve().getClassForType();
          return Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(literalAsClass));
        }
        default:
          VM._assert(NOT_REACHED);
          return Offset.fromIntSignExtend(0xebad0ff5);
      }
    } else {
      if (packedCPTypeIsClassType(cpValue)) {
        int typeId = unpackUnsignedCPValue(cpValue);
        Class<?> literalAsClass = TypeReference.getTypeRef(typeId).resolve().getClassForType();
        return Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(literalAsClass));
      } else {
        int value = unpackSignedCPValue(cpValue);
        return Offset.fromIntSignExtend(value);
      }
    }
  }

  /**
   * Get description of a literal constant.
   */
  static byte getLiteralDescription(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    byte type = unpackCPType(cpValue);
    return type;
  }

  /**
   * Get description of a literal constant.
   */
  public byte getLiteralDescription(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    byte type = unpackCPType(cpValue);
    return type;
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  @Uninterruptible
  public TypeReference getTypeRef(int constantPoolIndex) {
    return getTypeRef(constantPool, constantPoolIndex);
  }

  /**
   * Get contents of a "typeRef" constant pool entry.
   * @return type that was referenced
   */
  @Uninterruptible
  static TypeReference getTypeRef(int[] constantPool, int constantPoolIndex) {
    if (constantPoolIndex != 0) {
      int cpValue = constantPool[constantPoolIndex];
      if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_CLASS);
      return TypeReference.getTypeRef(unpackUnsignedCPValue(cpValue));
    } else {
      return null;
    }
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   */
  @Uninterruptible
  public MethodReference getMethodRef(int constantPoolIndex) {
    return getMethodRef(constantPool, constantPoolIndex);
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   */
  @Uninterruptible
  static MethodReference getMethodRef(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_MEMBER);
    return (MethodReference) MemberReference.getMemberRef(unpackUnsignedCPValue(cpValue));
  }

  /**
   * Get contents of a "fieldRef" constant pool entry.
   */
  @Uninterruptible
  public FieldReference getFieldRef(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_MEMBER);
    return (FieldReference) MemberReference.getMemberRef(unpackUnsignedCPValue(cpValue));
  }

  /**
   * Get contents of a "methodRef" constant pool entry.
   */
  @Uninterruptible
  static FieldReference getFieldRef(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_MEMBER);
    return (FieldReference) MemberReference.getMemberRef(unpackUnsignedCPValue(cpValue));
  }

  /**
   * Get contents of a "utf" constant pool entry.
   */
  @Uninterruptible
  Atom getUtf(int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_UTF);
    return Atom.getAtom(unpackUnsignedCPValue(cpValue));
  }

  /**
   * Get contents of a "utf" from a constant pool entry.
   */
  @Uninterruptible
  static Atom getUtf(int[] constantPool, int constantPoolIndex) {
    int cpValue = constantPool[constantPoolIndex];
    if (VM.VerifyAssertions) VM._assert(unpackCPType(cpValue) == CP_UTF);
    return Atom.getAtom(unpackUnsignedCPValue(cpValue));
  }

  /**
   * Return true if the SynchronizedObject annotation is present.
   * @see org.vmmagic.pragma.SynchronizedObject
   */
  boolean hasSynchronizedObjectAnnotation() {
    return isAnnotationDeclared(TypeReference.SynchronizedObject);
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
    return (finalizeMethod != null);
  }

  /**
   * Get finalize method that overrides java.lang.Object.finalize(),
   * if one exists
   */
  @Uninterruptible
  public RVMMethod getFinalizer() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return finalizeMethod;
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
   * Offsets of reference-containing instance fields of this class type.
   * Offsets are with respect to object pointer -- see RVMField.getOffset().
   */
  @Uninterruptible
  public int[] getReferenceOffsets() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return referenceOffsets;
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
    RVMMethod[] methods = getStaticMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      RVMMethod method = methods[i];
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
    RVMMethod[] methods = getConstructorMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      RVMMethod method = methods[i];
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
  private RVMClass(TypeReference typeRef, int[] constantPool, short modifiers, RVMClass superClass,
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
    this.objectCache = new LinkedListRVM<Object>();

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
   * Create an instance of a RVMClass.
   * @param typeRef the cannonical type reference for this type.
   * @param input the data stream from which to read the class's description.
   */
  static RVMClass readClass(TypeReference typeRef, DataInputStream input) throws ClassFormatError, IOException {

    if (classLoadingDisabled) {
      throw new RuntimeException("ClassLoading Disabled : " + typeRef);
    }

    if (VM.TraceClassLoading && VM.runningVM) {
      VM.sysWrite("RVMClass: (begin) load file " + typeRef.getName() + "\n");
    }

    int magic = input.readInt();
    if (magic != 0xCAFEBABE) {
      throw new ClassFormatError("bad magic number " + Integer.toHexString(magic));
    }

    // Get the class file version number and check to see if it is a version
    // that we support.
    int minor = input.readUnsignedShort();
    int major = input.readUnsignedShort();
    switch (major) {
      case 45:
      case 46:
      case 47:
      case 48:
      case 49: // we support all variants of these major versions so the minor number doesn't matter.
        break;
      case 50: // we only support up to 50.0 (ie Java 1.6.0)
        if (minor == 0) break;
      default:
        throw new UnsupportedClassVersionError("unsupported class file version " + major + "." + minor);
    }

    //
    // pass 1: read constant pool
    //
    int[] constantPool = new int[input.readUnsignedShort()];
    byte[] tmpTags = new byte[constantPool.length];

    // note: slot 0 is unused
    for (int i = 1; i < constantPool.length; i++) {
      tmpTags[i] = input.readByte();
      switch (tmpTags[i]) {
        case TAG_UTF: {
          byte[] utf = new byte[input.readUnsignedShort()];
          input.readFully(utf);
          int atomId = Atom.findOrCreateUtf8Atom(utf).getId();
          constantPool[i] = packCPEntry(CP_UTF, atomId);
          break;
        }
        case TAG_UNUSED:
          if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
          break;

        case TAG_INT: {
          int literal = input.readInt();
          int offset = Statics.findOrCreateIntSizeLiteral(literal);
          constantPool[i] = packCPEntry(CP_INT, offset);
          break;
        }
        case TAG_FLOAT: {
          int literal = input.readInt();
          int offset = Statics.findOrCreateIntSizeLiteral(literal);
          constantPool[i] = packCPEntry(CP_FLOAT, offset);
          break;
        }
        case TAG_LONG: {
          long literal = input.readLong();
          int offset = Statics.findOrCreateLongSizeLiteral(literal);
          constantPool[i] = packCPEntry(CP_LONG, offset);
          i++;
          break;
        }
        case TAG_DOUBLE: {
          long literal = input.readLong();
          int offset = Statics.findOrCreateLongSizeLiteral(literal);
          constantPool[i] = packCPEntry(CP_DOUBLE, offset);
          i++;
          break;
        }
        case TAG_TYPEREF:
          constantPool[i] = input.readUnsignedShort();
          break;

        case TAG_STRING:
          constantPool[i] = input.readUnsignedShort();
          break;

        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF: {
          int classDescriptorIndex = input.readUnsignedShort();
          int memberNameAndDescriptorIndex = input.readUnsignedShort();
          constantPool[i] = packTempCPEntry(classDescriptorIndex, memberNameAndDescriptorIndex);
          break;
        }

        case TAG_MEMBERNAME_AND_DESCRIPTOR: {
          int memberNameIndex = input.readUnsignedShort();
          int descriptorIndex = input.readUnsignedShort();
          constantPool[i] = packTempCPEntry(memberNameIndex, descriptorIndex);
          break;
        }

        default:
          throw new ClassFormatError("bad constant pool");
      }
    }

    //
    // pass 2: post-process type and string constant pool entries
    // (we must do this in a second pass because of forward references)
    //
    try {
      for (int i = 1; i < constantPool.length; i++) {
        switch (tmpTags[i]) {
          case TAG_LONG:
          case TAG_DOUBLE:
            ++i;
            break;

          case TAG_TYPEREF: { // in: utf index
            Atom typeName = getUtf(constantPool, constantPool[i]);
            int typeRefId =
                TypeReference.findOrCreate(typeRef.getClassLoader(), typeName.descriptorFromClassName()).getId();
            constantPool[i] = packCPEntry(CP_CLASS, typeRefId);
            break;
          } // out: type reference id

          case TAG_STRING: { // in: utf index
            Atom literal = getUtf(constantPool, constantPool[i]);
            int offset = literal.getStringLiteralOffset();
            constantPool[i] = packCPEntry(CP_STRING, offset);
            break;
          } // out: jtoc slot number
        }
      }
    } catch (java.io.UTFDataFormatException x) {
      throw new ClassFormatError(x.toString());
    }

    //
    // pass 3: post-process type field and method constant pool entries
    //
    for (int i = 1; i < constantPool.length; i++) {
      switch (tmpTags[i]) {
        case TAG_LONG:
        case TAG_DOUBLE:
          ++i;
          break;

        case TAG_FIELDREF:
        case TAG_METHODREF:
        case TAG_INTERFACE_METHODREF: { // in: classname+membername+memberdescriptor indices
          int bits = constantPool[i];
          int classNameIndex = unpackTempCPIndex1(bits);
          int memberNameAndDescriptorIndex = unpackTempCPIndex2(bits);
          int memberNameAndDescriptorBits = constantPool[memberNameAndDescriptorIndex];
          int memberNameIndex = unpackTempCPIndex1(memberNameAndDescriptorBits);
          int memberDescriptorIndex = unpackTempCPIndex2(memberNameAndDescriptorBits);

          TypeReference tref = getTypeRef(constantPool, classNameIndex);
          Atom memberName = getUtf(constantPool, memberNameIndex);
          Atom memberDescriptor = getUtf(constantPool, memberDescriptorIndex);
          MemberReference mr = MemberReference.findOrCreate(tref, memberName, memberDescriptor);
          int mrId = mr.getId();
          constantPool[i] = packCPEntry(CP_MEMBER, mrId);
          break;
        } // out: MemberReference id
      }
    }

    short modifiers = input.readShort();
    int myTypeIndex = input.readUnsignedShort();
    TypeReference myTypeRef = getTypeRef(constantPool, myTypeIndex);
    if (myTypeRef != typeRef) {
      // eg. file contains a different class than would be
      // expected from its .class file name
      if (!VM.VerifyAssertions) {
        throw new ClassFormatError("expected class \"" +
                                   typeRef.getName() +
                                   "\" but found \"" +
                                   myTypeRef.getName() +
                                   "\"");
      } else {
        throw new ClassFormatError("expected class \"" +
                                   typeRef.getName() +
                                   "\" but found \"" +
                                   myTypeRef.getName() +
                                   "\"\n" + typeRef + " != " + myTypeRef);
      }
    }

    TypeReference superType = getTypeRef(constantPool, input.readUnsignedShort()); // possibly null
    RVMClass superClass = null;
    if (((modifiers & ACC_INTERFACE) == 0) && (superType != null)) {
      superClass = superType.resolve().asClass();
    }

    int numInterfaces = input.readUnsignedShort();
    RVMClass[] declaredInterfaces;
    if (numInterfaces == 0) {
      declaredInterfaces = emptyVMClass;
    } else {
      declaredInterfaces = new RVMClass[numInterfaces];
      for (int i = 0; i < numInterfaces; ++i) {
        TypeReference inTR = getTypeRef(constantPool, input.readUnsignedShort());
        declaredInterfaces[i] = inTR.resolve().asClass();
      }
    }

    int numFields = input.readUnsignedShort();
    RVMField[] declaredFields;
    if (numFields == 0) {
      declaredFields = emptyVMField;
    } else {
      declaredFields = new RVMField[numFields];
      for (int i = 0; i < numFields; i++) {
        short fmodifiers = input.readShort();
        Atom fieldName = getUtf(constantPool, input.readUnsignedShort());
        Atom fieldDescriptor = getUtf(constantPool, input.readUnsignedShort());
        MemberReference memRef = MemberReference.findOrCreate(typeRef, fieldName, fieldDescriptor);
        declaredFields[i] = RVMField.readField(typeRef, constantPool, memRef, fmodifiers, input);
      }
    }

    int numMethods = input.readUnsignedShort();
    RVMMethod[] declaredMethods;
    RVMMethod classInitializerMethod = null;
    if (numMethods == 0) {
      declaredMethods = emptyVMMethod;
    } else {
      declaredMethods = new RVMMethod[numMethods];
      for (int i = 0; i < numMethods; i++) {
        short mmodifiers = input.readShort();
        Atom methodName = getUtf(constantPool, input.readUnsignedShort());
        Atom methodDescriptor = getUtf(constantPool, input.readUnsignedShort());
        MemberReference memRef = MemberReference.findOrCreate(typeRef, methodName, methodDescriptor);
        RVMMethod method = RVMMethod.readMethod(typeRef, constantPool, memRef, mmodifiers, input);
        declaredMethods[i] = method;
        if (method.isClassInitializer()) {
          classInitializerMethod = method;
        }
      }
    }
    TypeReference[] declaredClasses = null;
    Atom sourceName = null;
    TypeReference declaringClass = null;
    Atom signature = null;
    RVMAnnotation[] annotations = null;
    TypeReference enclosingClass = null;
    MethodReference enclosingMethod = null;
    // Read attributes.
    for (int i = 0, n = input.readUnsignedShort(); i < n; ++i) {
      Atom attName = getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();

      // Class attributes
      if (attName == RVMClassLoader.sourceFileAttributeName && attLength == 2) {
        sourceName = getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.innerClassesAttributeName) {
        // Parse InnerClasses attribute, and use the information to populate
        // the list of declared member classes.  We do this so we can
        // support the java.lang.Class.getDeclaredClasses()
        // and java.lang.Class.getDeclaredClass methods.

        int numberOfClasses = input.readUnsignedShort();
        declaredClasses = new TypeReference[numberOfClasses];

        for (int j = 0; j < numberOfClasses; ++j) {
          int innerClassInfoIndex = input.readUnsignedShort();
          int outerClassInfoIndex = input.readUnsignedShort();
          int innerNameIndex = input.readUnsignedShort();
          int innerClassAccessFlags = input.readUnsignedShort();

          if (innerClassInfoIndex != 0 && outerClassInfoIndex == myTypeIndex && innerNameIndex != 0) {
            // This looks like a declared inner class.
            declaredClasses[j] = getTypeRef(constantPool, innerClassInfoIndex);
          }

          if (innerClassInfoIndex == myTypeIndex) {
            if (outerClassInfoIndex != 0) {
              declaringClass = getTypeRef(constantPool, outerClassInfoIndex);
              if (enclosingClass == null) {
                // TODO: is this the null test necessary?
                enclosingClass = declaringClass;
              }
            }
            if ((innerClassAccessFlags & (ACC_PRIVATE | ACC_PROTECTED)) != 0) {
              modifiers &= ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);
            }
            modifiers |= innerClassAccessFlags;
          }
        }
      } else if (attName == RVMClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == RVMClassLoader.enclosingMethodAttributeName) {
        int enclosingClassIndex = input.readUnsignedShort();
        enclosingClass = getTypeRef(constantPool, enclosingClassIndex);

        int enclosingMethodIndex = input.readUnsignedShort();
        if (enclosingMethodIndex != 0) {
          int memberNameIndex = constantPool[enclosingMethodIndex] >>> BITS_IN_SHORT;
          int memberDescriptorIndex = constantPool[enclosingMethodIndex] & ((1 << BITS_IN_SHORT) - 1);
          Atom memberName = getUtf(constantPool, memberNameIndex);
          Atom memberDescriptor = getUtf(constantPool, memberDescriptorIndex);
          enclosingMethod =
              MemberReference.findOrCreate(enclosingClass, memberName, memberDescriptor).asMethodReference();
        }
      } else if (attName == RVMClassLoader.signatureAttributeName) {
        signature = RVMClass.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.runtimeVisibleAnnotationsAttributeName) {
        annotations = AnnotatedElement.readAnnotations(constantPool, input, typeRef.getClassLoader());
      } else {
        int skippedAmount = input.skipBytes(attLength);
        if (skippedAmount != attLength) {
          throw new IOException("Unexpected short skip");
        }
      }
    }

    return new RVMClass(typeRef,
                        constantPool,
                        modifiers,
                        superClass,
                        declaredInterfaces,
                        declaredFields,
                        declaredMethods,
                        declaredClasses,
                        declaringClass,
                        enclosingClass,
                        enclosingMethod,
                        sourceName,
                        classInitializerMethod,
                        signature,
                        annotations);
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

    if (hasSynchronizedObjectAnnotation() || this == RVMType.JavaLangClassType) {
      ObjectModel.allocateThinLock(this);
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
        RVMField[] fields = superClass.getInstanceFields();
        for (int i = 0, n = fields.length; i < n; ++i) {
          instanceFields.addElement(fields[i]);
        }

        RVMMethod[] methods = superClass.getVirtualMethods();
        for (int i = 0, n = methods.length; i < n; ++i) {
          virtualMethods.addElement(methods[i]);
        }
      }

      // append fields defined by this class
      //
      RVMField[] fields = getDeclaredFields();
      for (int i = 0, n = fields.length; i < n; ++i) {
        RVMField field = fields[i];
        if (field.isStatic()) {
          staticFields.addElement(field);
        } else {
          instanceFields.addElement(field);
        }
      }

      // append/overlay methods defined by this class
      //
      RVMMethod[] methods = getDeclaredMethods();
      for (int i = 0, n = methods.length; i < n; ++i) {
        RVMMethod method = methods[i];

        if (VM.VerifyUnint) {
          if (method.isUninterruptible() && method.isSynchronized()) {
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
    for (int i = 0, n = staticFields.length; i < n; ++i) {
      RVMField field = staticFields[i];
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
    for (int i = 0, n = instanceFields.length; i < n; ++i) {
      RVMField field = instanceFields[i];
      if (field.isReferenceType() && !field.isUntraced()) {
        referenceFieldCount += 1;
      }
    }

    // record offsets of those instance fields that contain references
    //
    if (typeRef.isRuntimeTable()) {
      referenceOffsets = MM_Interface.newNonMovingIntArray(0);
    } else {
      referenceOffsets = MM_Interface.newNonMovingIntArray(referenceFieldCount);
      for (int i = 0, j = 0, n = instanceFields.length; i < n; ++i) {
        RVMField field = instanceFields[i];
        if (field.isReferenceType() && !field.isUntraced()) {
          referenceOffsets[j++] = field.getOffset().toInt();
        }
      }
    }

    // Allocate space for <init> method pointers
    //
    for (int i = 0, n = constructorMethods.length; i < n; ++i) {
      RVMMethod method = constructorMethods[i];
      method.setOffset(Statics.allocateReferenceSlot(true));
    }

    // Allocate space for static method pointers
    //
    for (int i = 0, n = staticMethods.length; i < n; ++i) {
      RVMMethod method = staticMethods[i];
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
      allocatedTib = MM_Interface.newTIB(0);
    } else {
      allocatedTib = MM_Interface.newTIB(virtualMethods.length);
    }

    superclassIds = DynamicTypeCheck.buildSuperclassIds(this);
    doesImplement = DynamicTypeCheck.buildDoesImplement(this);

    // can't move this beyond "finalize" code block
    publishResolved(allocatedTib, superclassIds, doesImplement);

    // TODO: Make this into a more general listener interface
    if (VM.BuildForOptCompiler && VM.writingBootImage) {
      classLoadListener.classInitialized(this, true);
    }

    Callbacks.notifyClassResolved(this);
    MM_Interface.notifyClassResolved(this);

    // check for a "finalize" method that overrides the one in java.lang.Object
    //
    finalizeMethod = null;
    if (!isInterface()) {
      final RVMMethod method =
          findVirtualMethod(RVMClassLoader.StandardObjectFinalizerMethodName,
                            RVMClassLoader.StandardObjectFinalizerMethodDescriptor);
      if (!method.getDeclaringClass().isJavaLangObjectType()) {
        finalizeMethod = method;
      }
    }

    // Check if this was an annotation, if so create the class that
    // will implement the annotation interface
    //
    if (isAnnotation()) {
      createAnnotationClass(this);
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


  // RCGC: A reference to class is acyclic if the class is acyclic and
  // final (otherwise the reference could be to a subsequently loaded
  // cyclic subclass).
  //
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
      for (int i = 0, n = constructorMethods.length; i < n; ++i) {
        RVMMethod method = constructorMethods[i];
        Statics.setSlotContents(method.getOffset(), method.getCurrentEntryCodeArray());
      }

      // compile static methods and put their addresses into jtoc
      for (int i = 0, n = staticMethods.length; i < n; ++i) {
        // don't bother compiling <clinit> here;
        // compile it right before we invoke it in initialize.
        // This also avoids putting <clinit>s in the bootimage.
        RVMMethod method = staticMethods[i];
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
   * Execute this class's static initializer, <clinit>.
   * Side effects: superclasses are initialized, static fields receive
   * initial values.
   */
  @Override
  public synchronized void initialize()
    // Doesn't really need declaring.
      throws ExceptionInInitializerError {
    if (isInitialized()) {
      return;
    }

    if (state == CLASS_INITIALIZING) {
      return;
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
        throw e;
      } catch (Throwable t) {
        ExceptionInInitializerError eieio = new ExceptionInInitializerError("While initializing " + this);
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

  //------------------------------------------------------------//
  // Additional methods for annotation                          //
  //------------------------------------------------------------//
  /**
   * Method to create a class representing an implementation of an
   * annotation interface ({@link RVMAnnotation}). The created class
   * must have:
   * <ul>
   * <li>a method for each in the interface</li>
   * <li>a field backing store for the values to be returned by the
   * methods</li>
   * <li>a constructor that assigns default annotation values to each
   * of the field backing store values (if they are given)</li>
   * <li>an implementation of: annotationType, equals, hashCode and
   * toString</li>
   * </ul>
   *
   * @param annotationInterface the annotation interface this class
   * will implement
   * @return the implementing class
   */
  private static void createAnnotationClass(RVMClass annotationInterface) {
    // Compute name of class based on the name of the annotation interface
    Atom annotationClassName = annotationInterface.getDescriptor().annotationInterfaceToAnnotationClass();

    // Create a handle to the new synthetic type
    TypeReference annotationClass =
        TypeReference.findOrCreateInternal(annotationInterface.getClassLoader(), annotationClassName);

    if (VM.TraceClassLoading && VM.runningVM) {
      VM.sysWrite("RVMClass: (begin) create (load) annotation " + annotationClass.getName() + "\n");
    }

    // Count the number of default values for this class
    int numDefaultFields = 0;
    for (RVMMethod declaredMethod : annotationInterface.declaredMethods) {
      if (declaredMethod.getAnnotationDefault() != null) {
        numDefaultFields++;
      }
    }
    // The constant pool that will be used by bytecodes in our
    // synthetic methods. The constant pool is laid out as:
    // * 1 - the fields holding the annotation values
    // * 2 - the methods implementing those in the interface
    // * 3 - the default values to initialise the class fields to
    // * 4 - the object initialiser method
    int numFields = annotationInterface.declaredMethods.length;
    int numMethods = annotationInterface.declaredMethods.length + 1;
    int constantPoolSize = numFields + numMethods + numDefaultFields;
    int[] constantPool = new int[constantPoolSize];

    // Create fields for class
    RVMField[] annotationFields = new RVMField[numFields];
    for (int i = 0; i < numFields; i++) {
      RVMMethod currentAnnotationValue = annotationInterface.declaredMethods[i];
      Atom newFieldName = Atom.findOrCreateAsciiAtom(currentAnnotationValue.getName().toString() + "_field");
      Atom newFieldDescriptor = currentAnnotationValue.getReturnType().getName();
      MemberReference newFieldRef =
          MemberReference.findOrCreate(annotationClass, newFieldName, newFieldDescriptor);
      annotationFields[i] = RVMField.createAnnotationField(annotationClass, newFieldRef);
      constantPool[i] = packCPEntry(CP_MEMBER, newFieldRef.getId());
    }

    // Create copy of methods from the annotation
    RVMMethod[] annotationMethods = new RVMMethod[numMethods];
    for (int i = 0; i < annotationInterface.declaredMethods.length; i++) {
      RVMMethod currentAnnotationValue = annotationInterface.declaredMethods[i];
      Atom newMethodName = currentAnnotationValue.getName();
      Atom newMethodDescriptor = currentAnnotationValue.getDescriptor();
      MemberReference newMethodRef =
          MemberReference.findOrCreate(annotationClass, newMethodName, newMethodDescriptor);
      annotationMethods[i] =
          RVMMethod.createAnnotationMethod(annotationClass,
                                           constantPool,
                                           newMethodRef,
                                           annotationInterface.declaredMethods[i],
                                           i);
      constantPool[numFields + i] = packCPEntry(CP_MEMBER, annotationMethods[i].getMemberRef().getId());
    }
    // Create default value constants
    int nextFreeConstantPoolSlot = numFields + annotationInterface.declaredMethods.length;
    int[] defaultConstants = new int[numDefaultFields];
    for (int i = 0, j = 0; i < annotationInterface.declaredMethods.length; i++) {
      Object value = annotationInterface.declaredMethods[i].getAnnotationDefault();
      if (value != null) {
        if (value instanceof Object[]) {
          // Special case of 0 length arrays that can't have their type resolved early
          if (VM.VerifyAssertions) VM._assert(((Object[])value).length == 0);
          value = Array.newInstance(annotationInterface.declaredMethods[i].getReturnType().resolve().getClassForType(), 0);
        }
        if (value instanceof Integer) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_INT, Statics.findOrCreateIntSizeLiteral((Integer) value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Boolean) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_INT, Statics.findOrCreateIntSizeLiteral((Boolean) value ? 1 : 0));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Byte) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_INT, Statics.findOrCreateIntSizeLiteral((Byte) value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Short) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_INT, Statics.findOrCreateIntSizeLiteral((Short) value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Character) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_INT, Statics.findOrCreateIntSizeLiteral((Character) value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Float) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_FLOAT, Statics.findOrCreateIntSizeLiteral(Float.floatToIntBits(((Float)value).floatValue())));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Double) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_DOUBLE, Statics.findOrCreateLongSizeLiteral(Double.doubleToLongBits(((Double)value).doubleValue())));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof Long) {
          constantPool[nextFreeConstantPoolSlot] =
              packCPEntry(CP_LONG, Statics.findOrCreateLongSizeLiteral((Long)value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else if (value instanceof String) {
          try {
            constantPool[nextFreeConstantPoolSlot] =
                packCPEntry(CP_STRING,
                            Atom.findOrCreateUnicodeAtom((String) value).getStringLiteralOffset());
          } catch (UTFDataFormatException e) {
            throw new Error(e);
          }
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        } else { // Array/Enum/Annotation
          constantPool[nextFreeConstantPoolSlot] =
            packCPEntry(CP_STRING,
                Statics.findOrCreateObjectLiteral(value));
          defaultConstants[j] = nextFreeConstantPoolSlot;
          j++;
          nextFreeConstantPoolSlot++;
        }
      }
    }
    // Create initialiser
    int objectInitIndex = nextFreeConstantPoolSlot;
    MethodReference baInitMemRef = RVMAnnotation.getBaseAnnotationInitMemberReference();
    constantPool[objectInitIndex] = packCPEntry(CP_MEMBER, baInitMemRef.getId());

    MemberReference initMethodRef =
        MemberReference.findOrCreate(annotationClass, baInitMemRef.getName(), baInitMemRef.getDescriptor());

    annotationMethods[annotationInterface.declaredMethods.length] =
        RVMMethod.createAnnotationInit(annotationClass,
                                       constantPool,
                                       initMethodRef,
                                       objectInitIndex,
                                       annotationFields,
                                       annotationInterface.declaredMethods,
                                       defaultConstants);

    // Create class
    RVMClass klass =
        new RVMClass(annotationClass, constantPool, (short) (ACC_SYNTHETIC | ACC_PUBLIC | ACC_FINAL), // modifiers
                     baInitMemRef.resolveMember().getDeclaringClass(), // superClass
                     new RVMClass[]{annotationInterface}, // declaredInterfaces
                     annotationFields, annotationMethods, null, null, null, null, null, null, null, null);
    annotationClass.setType(klass);
    annotationClasses.put(annotationInterface, klass);
    // Now the class is set up, try to resolve any RVMAnnotation constants to Annotation constants
    for (int cpSlot : defaultConstants) {
      if (unpackCPType(constantPool[cpSlot]) == CP_STRING) {
        Offset off = getLiteralOffset(constantPool, cpSlot);
        Object value = Statics.getSlotContentsAsObject(off);
        if (value instanceof RVMAnnotation) {
          value = ((RVMAnnotation)value).getValue();
          Statics.setSlotContents(off, value);
        }
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

  /**
   * Resolution status.
   */
  @Override
  @Uninterruptible
  public boolean isResolved() {
    return state >= CLASS_RESOLVED;
  }

  /**
   * Instantiation status.
   */
  @Override
  @Uninterruptible
  public boolean isInstantiated() {
    return state >= CLASS_INSTANTIATED;
  }

  /**
   * Initialization status.
   */
  @Override
  @Uninterruptible
  public boolean isInitialized() {
    return state == CLASS_INITIALIZED;
  }

  /**
   * Only intended to be used by the BootImageWriter
   */
  @Override
  public void markAsBootImageClass() {
    inBootImage = true;
  }

  /**
   * Is this class part of the virtual machine's boot image?
   */
  @Override
  @Uninterruptible
  public boolean isInBootImage() {
    return inBootImage;
  }

  /**
   * Get the offset in instances of this type assigned to the thin lock word.
   * Offset.max() if instances of this type do not have thin lock words.
   * Is only known after class has been resolved.
   */
  @Override
  @Uninterruptible
  public Offset getThinLockOffset() {
    if (VM.VerifyAssertions) VM._assert(isResolved());
    return thinLockOffset;
  }

 /**
   * Set the thin lock offset for instances of this type. Can be called at most once.
   * and is invoked from ObjectModel.allocateThinLock (in object models which
   * do not allocate thin locks for all scalar object types).
   */
  public void setThinLockOffset(Offset offset) {
    if (VM.VerifyAssertions) VM._assert(thinLockOffset.isMax());
    if (VM.VerifyAssertions) VM._assert(!offset.isMax());
    thinLockOffset = offset;
  }

  /**
   * get number of superclasses to Object
   */
  @Override
  @Pure
  @Uninterruptible
  public int getTypeDepth() {
    return depth;
  }

  /**
   * Whether or not this is an instance of RVMClass?
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isClassType() {
    return true;
  }

  /**
   * Whether or not this is an instance of RVMArray?
   * @return true
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isArrayType() {
    return false;
  }

  /**
   * Whether or not this is a primitive type
   * @return false
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isPrimitiveType() {
    return false;
  }

  /**
   * @return whether or not this is a reference (ie non-primitive) type.
   */
  @Override
  @Pure
  @Uninterruptible
  public boolean isReferenceType() {
    return true;
  }
}
