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
import org.jikesrvm.compilers.common.CodeArray;
import org.jikesrvm.compilers.common.LazyCompilationTrampoline;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.ReflectionBase;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.util.HashMapRVM;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.RuntimePure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

import static org.jikesrvm.classloader.TypeReference.baseReflectionClass;
import static org.jikesrvm.classloader.BytecodeConstants.*;
import static org.jikesrvm.classloader.ConstantPool.*;
import static org.jikesrvm.classloader.ClassLoaderConstants.*;


/**
 * A method of a java class corresponding to a method_info structure
 * in the class file. A method is read from a class file using the
 * {@link ClassFileReader#readMethod} method.
 */
public abstract class RVMMethod extends RVMMember {

  /**
   * current compiled method for this method
   */
  protected CompiledMethod currentCompiledMethod;
  /**
   * exceptions this method might throw (null --&gt; none)
   */
  private static final ImmutableEntryHashMapRVM<RVMMethod, TypeReference[]> exceptionTypes =
    new ImmutableEntryHashMapRVM<RVMMethod, TypeReference[]>();
  /**
   * Method parameter annotations from the class file that are
   * described as runtime visible. These annotations are available to
   * the reflection API.
   */
  private static final ImmutableEntryHashMapRVM<RVMMethod, RVMAnnotation[][]> parameterAnnotations =
    new ImmutableEntryHashMapRVM<RVMMethod, RVMAnnotation[][]>();
  /**
   * A table mapping to values present in the method info tables of annotation
   * types. It represents the default result from an annotation method.
   */
  private static final HashMapRVM<RVMMethod, Object> annotationDefaults =
    new HashMapRVM<RVMMethod, Object>();
  /**
   * The offsets of virtual methods in the JTOC, if it's been placed
   * there by constant propagation.
   */
  private static final ImmutableEntryHashMapRVM<RVMMethod, Integer> jtocOffsets =
    new ImmutableEntryHashMapRVM<RVMMethod, Integer>();

  /** Cache of arrays of declared parameter annotations. */
  private static final ImmutableEntryHashMapRVM<RVMMethod, Annotation[][]> declaredParameterAnnotations =
    new ImmutableEntryHashMapRVM<RVMMethod, Annotation[][]>();

  /** Reference to method designated to replace this one when compiled */
  private RVMMethod replacementMethod = null;

  /** All method annotations */
  protected final MethodAnnotations methodAnnotations;

  /**
   * Construct a read method
   *
   * @param declaringClass the RVMClass object of the class that declared this field
   * @param memRef the canonical memberReference for this method.
   * @param modifiers modifiers associated with this method.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param methodAnnotations all method annotations
   */
  protected RVMMethod(TypeReference declaringClass, MemberReference memRef, short modifiers,
                      TypeReference[] exceptionTypes, Atom signature, MethodAnnotations methodAnnotations) {
    super(declaringClass, memRef, (short) (modifiers & APPLICABLE_TO_METHODS), signature, methodAnnotations);
    this.methodAnnotations = methodAnnotations;
    if (parameterAnnotations != null) {
      synchronized (RVMMethod.parameterAnnotations) {
        RVMMethod.parameterAnnotations.put(this, methodAnnotations.getParameterAnnotations());
      }
    }
    if (exceptionTypes != null) {
      synchronized (RVMMethod.exceptionTypes) {
        RVMMethod.exceptionTypes.put(this, exceptionTypes);
      }
    }
    Object methodAnnotationDefaults = methodAnnotations.getAnnotationDefaults();
    if (methodAnnotationDefaults != null) {
      synchronized (annotationDefaults) {
        annotationDefaults.put(this, methodAnnotationDefaults);
      }
    }
  }

  /**
   * @return the parameter annotations for this method
   */
  @Pure
  private RVMAnnotation[][] getParameterAnnotations() {
    synchronized (parameterAnnotations) {
      return parameterAnnotations.get(this);
    }
  }

  /**
   * @return the annotation default value for an annotation method
   */
  @Pure
  public Object getAnnotationDefault() {
    synchronized (annotationDefaults) {
      Object value = annotationDefaults.get(this);
      if (value instanceof TypeReference || value instanceof Object[]) {
        value = RVMAnnotation.firstUse(value);
        annotationDefaults.put(this, value);
      }
      return value;
    }
  }

  /**
   * @return {@code true} if this method is a class initializer
   */
  @Uninterruptible
  public final boolean isClassInitializer() {
    return getName() == RVMClassLoader.StandardClassInitializerMethodName;
  }

  /**
   * @return {@code true} if this method is an object initializer
   */
  @Uninterruptible
  public final boolean isObjectInitializer() {
    return getName() == RVMClassLoader.StandardObjectInitializerMethodName;
  }

  /**
   * @return {@code true} if this method is a a compiler-generated
   *  object initializer helper
   */
  @Uninterruptible
  public final boolean isObjectInitializerHelper() {
    return getName() == RVMClassLoader.StandardObjectInitializerHelperMethodName;
  }

  @Uninterruptible
  public final TypeReference getReturnType() {
    return memRef.asMethodReference().getReturnType();
  }

  /**
   * @return the types of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public final TypeReference[] getParameterTypes() {
    return memRef.asMethodReference().getParameterTypes();
  }

  /**
   * @return space required by this method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public final int getParameterWords() {
    return memRef.asMethodReference().getParameterWords();
  }

  /**
   * @return {@code true} if machine code has been generated
   * for this method's bytecodes
   */
  public final boolean isCompiled() {
    return currentCompiledMethod != null;
  }

  /**
   * Get the current compiled method for this method.
   * Will return null if there is no current compiled method!
   * <p>
   * We make this method Unpreemptible to avoid a race-condition
   * in Reflection.invoke.
   * @return compiled method or {@code null} if none exists
   */
  @Unpreemptible
  public final synchronized CompiledMethod getCurrentCompiledMethod() {
    return currentCompiledMethod;
  }

  /**
   * @return {@code true} if this method is declared as statically
   *  dispatched
   */
  @Uninterruptible
  public final boolean isStatic() {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * @return {@code true} if this method is declared as non-overridable
   *  by subclasses
   */
  @Uninterruptible
  public final boolean isFinal() {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * @return {@code true} if this method is guarded by
   *  monitorenter/monitorexit
   */
  @Uninterruptible
  public final boolean isSynchronized() {
    return (modifiers & ACC_SYNCHRONIZED) != 0;
  }

  /**
   * @return {@code true} if this method is not implemented in java
   */
  @Uninterruptible
  public final boolean isNative() {
    return (modifiers & ACC_NATIVE) != 0;
  }

  /**
   * @return {@code true} if IEEE 754 rules need to be strictly
   *  enforced for this method
   */
  public final boolean isStrictFP() {
    return (modifiers & ACC_STRICT) != 0;
  }

  /**
   * @return {@code true} if this is a method that's not implemented in Java
   *  and use C not JNI calling convention
   * @see org.jikesrvm.runtime.SysCall
   */
  public final boolean isSysCall() {
    return isNative() && isStatic() && isAnnotationDeclared(TypeReference.SysCall);
  }

  /**
   * @return {@code true} if this is a specialized method invoke
   * @see SpecializedMethod
   * @see SpecializedMethodManager
   */
  public final boolean isSpecializedInvoke() {
    return isAnnotationDeclared(TypeReference.SpecializedMethodInvoke);
  }

  /**
   * @return {@code true} if this method needs to be implemented by subclasses
   */
  @Uninterruptible
  public final boolean isAbstract() {
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * @return {@code true} if this method is not present in the source code file
   *  (e.g. because it has been added by a Java compiler like javac)
   */
  public boolean isSynthetic() {
    return (modifiers & ACC_SYNTHETIC) != 0;
  }

  /**
   * @return {@code true} if this method is a bridge method. Bridge methods are
   *  generated in some cases of generics and inheritance.
   */
  public boolean isBridge() {
    return (modifiers & BRIDGE) != 0;
  }

  /**
   * @return {@code true} if ts this a varargs method taking a variable number
   *  of arguments
   */
  public boolean isVarArgs() {
    return (modifiers & VARARGS) != 0;
  }

  /**
   * Exceptions thrown by this method -
   * something like <code>{ "java/lang/IOException", "java/lang/EOFException" }</code>
   * @return exception info or {@code null} if this method doesn't throw any
   *  exceptions
   */
  @Pure
  public final TypeReference[] getExceptionTypes() {
    synchronized (exceptionTypes) {
      return exceptionTypes.get(this);
    }
  }

  /**
   * Is this method interruptible?
   * In other words, should the compiler insert yieldpoints
   * in method prologue, epilogue, and backwards branches.
   * Also, only methods that are Interruptible have stackoverflow checks
   * in the method prologue (since there is no mechanism for handling a stackoverflow
   * that doesn't violate the uninterruptiblity of the method).
   * To determine if a method is interruptible, the following conditions
   * are checked (<em>in order</em>):
   * <ul>
   * <li> If it is a <code>&lt;clinit&gt;</code> or <code>&lt;init&gt;</code> method then it is interruptible.
   * <li> If is the synthetic 'this' method used by jikes to
   *      factor out default initializers for <code>&lt;init&gt;</code> methods then it is interruptible.
   * <li> If it is annotated with <CODE>&#064;Interruptible</CODE> it is interruptible.
   * <li> If it is annotated with <CODE>&#064;Preemptible</CODE> it is interruptible.
   * <li> If it is annotated with <CODE>&#064;Uninterruptible</CODE> it is not interruptible.
   * <li> If it is annotated with <CODE>&#064;UninterruptibleNoWarn</CODE> it is not interruptible.
   * <li> If it is annotated with <CODE>&#064;Unpreemptible</CODE> it is not interruptible.
   * <li> If its declaring class is annotated with <CODE>&#064;Uninterruptible</CODE>
   *      or <CODE>&#064;Unpreemptible</CODE> it is not interruptible.
   * </ul>
   *
   * @return {@code true} if and only if this method is interruptible
   */
  public final boolean isInterruptible() {
    if (isClassInitializer() || isObjectInitializer()) return true;
    if (isObjectInitializerHelper()) return true;
    if (hasInterruptibleAnnotation()) return true;
    if (hasPreemptibleAnnotation()) return true;
    if (hasUninterruptibleNoWarnAnnotation()) return false;
    if (hasUninterruptibleAnnotation()) return false;
    if (hasUnpreemptibleAnnotation()) return false;
    if (hasUnpreemptibleNoWarnAnnotation()) return false;
    if (getDeclaringClass().hasUnpreemptibleAnnotation()) return false;
    return !getDeclaringClass().hasUninterruptibleAnnotation();
  }

  /**
   * Is the method Unpreemptible? See the comment in {@link #isInterruptible}
   *
   * @return {@code true} if and only if this method is unpreemptible
   */
  public final boolean isUnpreemptible() {
    if (isClassInitializer() || isObjectInitializer()) return false;
    if (isObjectInitializerHelper()) return false;
    if (hasInterruptibleAnnotation()) return false;
    if (hasPreemptibleAnnotation()) return false;
    if (hasUninterruptibleAnnotation()) return false;
    if (hasUninterruptibleNoWarnAnnotation()) return false;
    if (hasUnpreemptibleAnnotation()) return true;
    if (hasUnpreemptibleNoWarnAnnotation()) return true;
    return getDeclaringClass().hasUnpreemptibleAnnotation();
  }

  /**
   * Is the method Uninterruptible? See the comment in {@link #isInterruptible}
   *
   * @return {@code true} if and only if this method is uninterruptible
   */
  public final boolean isUninterruptible() {
    if (isClassInitializer() || isObjectInitializer()) return false;
    if (isObjectInitializerHelper()) return false;
    if (hasInterruptibleAnnotation()) return false;
    if (hasPreemptibleAnnotation()) return false;
    if (hasUnpreemptibleAnnotation()) return false;
    if (hasUnpreemptibleNoWarnAnnotation()) return false;
    if (hasUninterruptibleAnnotation()) return true;
    if (hasUninterruptibleNoWarnAnnotation()) return true;
    return getDeclaringClass().hasUninterruptibleAnnotation();
  }

  /**
   * Is the method Pure? That is would it, without any side effects, return the
   * same value given the same arguments?
   *
   * @return whether the method has a pure annotation
   */
  public final boolean isPure() {
    return hasPureAnnotation() || hasRuntimePureAnnotation();
  }

  /**
   * Is the method RuntimePure? This is the same as Pure at runtime but has a
   * special return value at boot image writing time
   *
   * @return whether the method has a pure annotation
   */
  public final boolean isRuntimePure() {
   return hasRuntimePureAnnotation();
  }

  /**
   * Has this method been marked as forbidden to inline?
   * ie., it is marked with the <CODE>NoInline</CODE> annotation or
   * the <CODE>NoOptCompile</CODE> annotation?
   *
   * @return {@code true} if this method must not be inlined
   */
  public final boolean hasNoInlinePragma() {
    return (hasNoInlineAnnotation() || hasNoOptCompileAnnotation());
  }

  /**
   * @param field the field whose write access is supposed to be checked
   * @return {@code true} if the method may write to a given field
   */
  public boolean mayWrite(RVMField field) {
    return true; // be conservative.  native methods can write to anything
  }

  /**
   * @return {@code true} if the method is the implementation of a runtime service
   * that is called "under the covers" from the generated code and thus is not subject to
   * inlining via the normal mechanisms.
   */
  public boolean isRuntimeServiceMethod() {
    return false; // only NormalMethods can be runtime service impls in Jikes RVM and they override this method
  }

  /**
   * @return {@code true} if all allocation from this method must go to
   *  a non-moving space
   *
   * @see org.vmmagic.pragma.NonMovingAllocation
   */
  public boolean isNonMovingAllocation() {
    return hasNonMovingAllocationAnnotation();
  }

  //------------------------------------------------------------------//
  //                        Section 2.                                //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * Get the code array that corresponds to the entry point (prologue)
   * for the method.
   *
   * @return the code array for the method
   */
  public final synchronized CodeArray getCurrentEntryCodeArray() {
    RVMClass declaringClass = getDeclaringClass();
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) {
      return currentCompiledMethod.getEntryCodeArray();
    } else if (!VM.writingBootImage || isNative()) {
      if (!isStatic() && !isObjectInitializer() && !isPrivate()) {
        // A non-private virtual method.
        if (declaringClass.isJavaLangObjectType() ||
            declaringClass.getSuperClass().findVirtualMethod(getName(), getDescriptor()) == null) {
          // The root method of a virtual method family can use the lazy method invoker directly.
          return Entrypoints.lazyMethodInvokerMethod.getCurrentEntryCodeArray();
        } else {
          // All other virtual methods in the family must use unique stubs to
          // ensure correct operation of the method test (guarded inlining of virtual calls).
          // It is TIBs job to marshall between the actual trampoline and this marker.
          return LazyCompilationTrampoline.getInstructions();
        }
      } else {
        // We'll never do a method test against this method.
        // Therefore we can use the lazy method invoker directly.
        return Entrypoints.lazyMethodInvokerMethod.getCurrentEntryCodeArray();
      }
    } else {
      compile();
      return currentCompiledMethod.getEntryCodeArray();
    }
  }

  /**
   * Generate machine code for this method if valid
   * machine code doesn't already exist.
   */
  public final synchronized void compile() {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isResolved());
    if (isCompiled()) return;

    if (VM.TraceClassLoading && VM.runningVM && VM.fullyBooted) VM.sysWriteln("RVMMethod: (begin) compiling " + this);

    CompiledMethod cm;

    // Compile the replacement method if one has been set
    if (replacementMethod != null) {
      replacementMethod.compile();
      cm = replacementMethod.getCurrentCompiledMethod();
    } else {
      cm = genCode();
    }

    // Ensure that cm wasn't invalidated while it was being compiled.
    synchronized (cm) {
      if (cm.isInvalid()) {
        CompiledMethods.setCompiledMethodObsolete(cm);
      } else {
        currentCompiledMethod = cm;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM && VM.fullyBooted) VM.sysWriteln("RVMMethod: (end)   compiling " + this);
  }

  /**
   * Generates the code for this method.
   *
   * @return an object representing the compiled method
   */
  protected abstract CompiledMethod genCode();

  /**
   * Set a replacement RVMMethod to be compiled in place of this one
   */
  protected void setReplacementMethod(RVMMethod m) {
    replacementMethod = m;
    // invalidate currently compiled method if target class has been instantiated already
    if (getDeclaringClass().isInstantiated())
      replaceCompiledMethod(null);
  }

  //----------------------------------------------------------------//
  //                        Section 3.                              //
  // The following are available after the declaring class has been //
  // "instantiated".                                                //
  //----------------------------------------------------------------//

  /**
   * Change machine code that will be used by future executions of this method
   * (ie. optimized &lt;-&gt; non-optimized)<p>
   *
   * Side effect: updates JTOC or method dispatch tables
   * ("type information blocks")
   *              for this class and its subclasses
   *
   * @param compiledMethod new machine code
   *
   */
  public final synchronized void replaceCompiledMethod(CompiledMethod compiledMethod) {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isInstantiated());
    // If we're replacing with a non-null compiledMethod, ensure that is still valid!
    if (compiledMethod != null) {
      synchronized (compiledMethod) {
        if (compiledMethod.isInvalid()) return;
      }
    }

    // Grab version that is being replaced
    CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Install the new method in JTOC/TIB. If virtual, will also replace in
    // all subclasses that inherited the method.
    getDeclaringClass().updateMethod(this);

    // Replace constant-ified virtual method in JTOC if necessary
    Offset jtocOffset = getJtocOffset();
    if (jtocOffset.NE(Offset.zero())) {
      Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
    }

    // Now that we've updated the JTOC/TIB, old version is obsolete
    if (oldCompiledMethod != null) {
      CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * Invalidates the given compiled method if it is the current compiled code
   * for this method.
   *
   * @param cm the compiled method to try to invalidate
   */
  public final synchronized void invalidateCompiledMethod(CompiledMethod cm) {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isInstantiated());
    if (currentCompiledMethod == cm) {
      replaceCompiledMethod(null);
    }
  }

  /**
   * Gets the offset used to hold a JTOC addressable version of the current entry
   * code array.
   *
   * @return the JTOC offset
   */
  @Pure
  private Offset getJtocOffset()  {
    Integer offAsInt;
    synchronized (jtocOffsets) {
      offAsInt = jtocOffsets.get(this);
    }
    if (offAsInt == null) {
      return Offset.zero();
    } else {
      return Offset.fromIntSignExtend(offAsInt.intValue());
    }
  }

  /**
   * Finds or create a JTOC offset for this method.
   *
   * @return the JTOC offset
   */
  public final synchronized Offset findOrCreateJtocOffset() {
    if (VM.VerifyAssertions) VM._assert(!isStatic() && !isObjectInitializer());
    Offset jtocOffset = getJtocOffset();;
    if (jtocOffset.EQ(Offset.zero())) {
      jtocOffset = Statics.allocateReferenceSlot(true);
      Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
      synchronized (jtocOffsets) {
        jtocOffsets.put(this, Integer.valueOf(jtocOffset.toInt()));
      }
    }
    return jtocOffset;
  }

  /**
   * @return the parameter annotations for this method.
   */
  @Pure
  public final Annotation[][] getDeclaredParameterAnnotations() {
    Annotation[][] result;
    synchronized (declaredParameterAnnotations) {
      result = declaredParameterAnnotations.get(this);
    }
    if (result == null) {
      RVMAnnotation[][] parameterAnnotations = getParameterAnnotations();
      if (parameterAnnotations == null) {
        result = new Annotation[getParameterTypes().length][];
        for (int a = 0; a  < result.length; a++) {
          result[a] = new Annotation[0];
        }
      } else {
        result = new Annotation[parameterAnnotations.length][];
        for (int a = 0; a < result.length; ++a) {
          result[a] = toAnnotations(parameterAnnotations[a]);
        }
        synchronized (declaredParameterAnnotations) {
          declaredParameterAnnotations.put(this, result);
        }
      }
    }
    return result;
  }

  /** Map from a method to a reflective method capable of invoking it */
  private static final ImmutableEntryHashMapRVM<RVMMethod, ReflectionBase> invokeMethods =
    Reflection.bytecodeReflection || Reflection.cacheInvokerInJavaLangReflect ?
      new ImmutableEntryHashMapRVM<RVMMethod, ReflectionBase>(30) : null;

  /**
   * @return an instance of an object capable of reflectively invoking this method
   */
  @RuntimePure
  @SuppressWarnings("unchecked")
  public synchronized ReflectionBase getInvoker() {
    if (!VM.runningVM) {
      return null;
    }
    ReflectionBase invoker;
    if (invokeMethods != null) {
      synchronized (RVMMethod.class) {
        invoker = invokeMethods.get(this);
      }
    } else {
      invoker = null;
    }
    if (invoker == null) {
      Class<ReflectionBase> reflectionClass = (Class<ReflectionBase>)RVMClass.createReflectionClass(this);
      if (reflectionClass != null) {
        try {
          invoker = reflectionClass.newInstance();
        } catch (Throwable e) {
          throw new Error(e);
        }
      } else {
        invoker = ReflectionBase.nullInvoker;
      }
      if (invokeMethods != null) {
        synchronized (RVMMethod.class) {
          invokeMethods.put(this, invoker);
        }
      }
    }
    return invoker;
  }

  /**
   * Create a method to act as a default constructor (just return)
   * @param klass class for method
   * @param memRef reference for default constructor
   * @return method normal (bytecode containing) method that just returns
   */
  static RVMMethod createDefaultConstructor(TypeReference klass, MemberReference memRef) {
    return new NormalMethod(klass,
                               memRef,
                               (short) (ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC),
                               null,
                               (short) 1,
                               (short) 0,
                               new byte[]{(byte)JBC_return},
                               null,
                               null,
                               null,
                               new int[0],
                               null,
                               MethodAnnotations.noMethodAnnotations());
  }

  /**
   * Create a method for reflectively invoking this method
   *
   * @param reflectionClass the class this method will belong to
   * @param constantPool for the class
   * @param memRef the member reference corresponding to this method
   * @return the created method
   */
  RVMMethod createReflectionMethod(TypeReference reflectionClass, int[] constantPool,
                                   MethodReference memRef) {
    TypeReference[] parameters = getParameterTypes();
    int numParams = parameters.length;
    byte[] bytecodes;
    boolean interfaceCall = false;
    int curBC = 0;
    if (!isStatic()) {
      if (!getDeclaringClass().isInterface()) {
        // virtual call
        bytecodes = new byte[8 * numParams + 8];
      } else {
        // interface call
        bytecodes = new byte[8 * numParams + 10];
        interfaceCall = true;
      }
      bytecodes[curBC] = JBC_aload_1;
      curBC++;
    } else {
      // static call
      bytecodes = new byte[8 * numParams + 7];
    }
    for (int i = 0; i < numParams; i++) {
      if (parameters[i].isVoidType()) {
        bytecodes[curBC] =
          bytecodes[curBC + 1] =
          bytecodes[curBC + 2] =
          bytecodes[curBC + 3] =
          bytecodes[curBC + 4] =
          bytecodes[curBC + 5] =
          bytecodes[curBC + 6] =
          bytecodes[curBC + 7] =
            (byte)JBC_nop;
        continue;
      }
      bytecodes[curBC] = (byte)JBC_aload_2;
      bytecodes[curBC + 1] = (byte)JBC_sipush;
      bytecodes[curBC + 2] = (byte)(i >>> 8);
      bytecodes[curBC + 3] = (byte)i;
      bytecodes[curBC + 4] = (byte)JBC_aaload;
      if (!parameters[i].isPrimitiveType()) {
        bytecodes[curBC + 5] = (byte)JBC_checkcast;
        if (VM.VerifyAssertions) VM._assert(parameters[i].getId() != 0);
        constantPool[i + 1] = ConstantPool.packCPEntry(CP_CLASS, parameters[i].getId());
        bytecodes[curBC + 6] = (byte)((i + 1) >>> 8);
        bytecodes[curBC + 7] = (byte)(i + 1);
      } else if (parameters[i].isWordLikeType()) {
        bytecodes[curBC + 5] =
          bytecodes[curBC + 6] =
          bytecodes[curBC + 7] =
            (byte)JBC_nop;
      } else {
        bytecodes[curBC + 5] = (byte)JBC_invokestatic;
        MemberReference unboxMethod;
        if (parameters[i].isBooleanType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsBoolean"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)Z"));
        } else if (parameters[i].isByteType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsByte"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)B"));
        } else if (parameters[i].isShortType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsShort"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)S"));
        } else if (parameters[i].isCharType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsChar"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)C"));
        } else if (parameters[i].isIntType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsInt"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)I"));
        } else if (parameters[i].isLongType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsLong"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)J"));
        } else if (parameters[i].isFloatType()) {
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsFloat"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)F"));
        } else {
          if (VM.VerifyAssertions) VM._assert(parameters[i].isDoubleType());
          unboxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                        Atom.findOrCreateUnicodeAtom("unboxAsDouble"),
                                                        Atom.findOrCreateUnicodeAtom("(Ljava/lang/Object;)D"));
        }
        constantPool[i + 1] = ConstantPool.packCPEntry(CP_MEMBER, unboxMethod.getId());
        bytecodes[curBC + 6] = (byte)((i + 1) >>> 8);
        bytecodes[curBC + 7] = (byte)(i + 1);
      }
      curBC += 8;
    }
    if (isStatic()) {
      bytecodes[curBC] = (byte)JBC_invokestatic;
    } else if (isObjectInitializer() || isPrivate()) {
      bytecodes[curBC] = (byte)JBC_invokespecial;
    } else if (interfaceCall) {
      bytecodes[curBC] = (byte)JBC_invokeinterface;
    } else {
      bytecodes[curBC] = (byte)JBC_invokevirtual;
    }
    constantPool[numParams + 1] = ConstantPool.packCPEntry(CP_MEMBER, getId());
    bytecodes[curBC + 1] = (byte)((numParams + 1) >>> 8);
    bytecodes[curBC + 2] = (byte)(numParams + 1);
    if (interfaceCall) {
      // invokeinterface bytecodes are historically longer than others
      curBC += 2;
    }
    TypeReference returnType = getReturnType();
    if (!returnType.isPrimitiveType() || returnType.isWordLikeType()) {
      bytecodes[curBC + 3] = (byte)JBC_nop;
      bytecodes[curBC + 4] = (byte)JBC_nop;
      bytecodes[curBC + 5] = (byte)JBC_nop;
    } else if (returnType.isVoidType()) {
      bytecodes[curBC + 3] = (byte)JBC_aconst_null;
      bytecodes[curBC + 4] = (byte)JBC_nop;
      bytecodes[curBC + 5] = (byte)JBC_nop;
    } else {
      MemberReference boxMethod;
      if (returnType.isBooleanType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsBoolean"),
                                                    Atom.findOrCreateUnicodeAtom("(Z)Ljava/lang/Object;"));
      } else if (returnType.isByteType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsByte"),
                                                    Atom.findOrCreateUnicodeAtom("(B)Ljava/lang/Object;"));
      } else if (returnType.isShortType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsShort"),
                                                    Atom.findOrCreateUnicodeAtom("(S)Ljava/lang/Object;"));
      } else if (returnType.isCharType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsChar"),
                                                    Atom.findOrCreateUnicodeAtom("(C)Ljava/lang/Object;"));
      } else if (returnType.isIntType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsInt"),
                                                    Atom.findOrCreateUnicodeAtom("(I)Ljava/lang/Object;"));
      } else if (returnType.isLongType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsLong"),
                                                    Atom.findOrCreateUnicodeAtom("(J)Ljava/lang/Object;"));
      } else if (returnType.isFloatType()) {
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsFloat"),
                                                    Atom.findOrCreateUnicodeAtom("(F)Ljava/lang/Object;"));
      } else {
        if (VM.VerifyAssertions) VM._assert(returnType.isDoubleType());
        boxMethod = MethodReference.findOrCreate(baseReflectionClass,
                                                    Atom.findOrCreateUnicodeAtom("boxAsDouble"),
                                                    Atom.findOrCreateUnicodeAtom("(D)Ljava/lang/Object;"));
      }
      constantPool[numParams + 2] = ConstantPool.packCPEntry(CP_MEMBER, boxMethod.getId());
      bytecodes[curBC + 3] = (byte)JBC_invokestatic;
      bytecodes[curBC + 4] = (byte)((numParams + 2) >>> 8);
      bytecodes[curBC + 5] = (byte)(numParams + 2);
    }
    bytecodes[curBC + 6] = (byte)JBC_areturn;
    return new NormalMethod(reflectionClass,
                               memRef,
                               (short) (ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC),
                               null,
                               (short) 3,
                               (short) (getParameterWords() + 2),
                               bytecodes,
                               null,
                               null,
                               null,
                               constantPool,
                               null,
                               MethodAnnotations.noMethodAnnotations());
  }
}
