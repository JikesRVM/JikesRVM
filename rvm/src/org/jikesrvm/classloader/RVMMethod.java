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

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.ArchitectureSpecific.LazyCompilationTrampoline;
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

/**
 * A method of a java class corresponding to a method_info structure
 * in the class file. A method is read from a class file using the
 * {@link #readMethod} method.
 */
public abstract class RVMMethod extends RVMMember implements BytecodeConstants {

  /**
   * current compiled method for this method
   */
  protected CompiledMethod currentCompiledMethod;
  /**
   * exceptions this method might throw (null --> none)
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
   * The offsets of virtual methods in the jtoc, if it's been placed
   * there by constant propagation.
   */
  private static final ImmutableEntryHashMapRVM<RVMMethod, Integer> jtocOffsets =
    new ImmutableEntryHashMapRVM<RVMMethod, Integer>();

  /** Cache of arrays of declared parameter annotations. */
  private static final ImmutableEntryHashMapRVM<RVMMethod, Annotation[][]> declaredParameterAnnotations =
    new ImmutableEntryHashMapRVM<RVMMethod, Annotation[][]>();

  /**
   * Construct a read method
   *
   * @param declaringClass the RVMClass object of the class that declared this field
   * @param memRef the canonical memberReference for this method.
   * @param modifiers modifiers associated with this method.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  protected RVMMethod(TypeReference declaringClass, MemberReference memRef, short modifiers,
                      TypeReference[] exceptionTypes, Atom signature, RVMAnnotation[] annotations,
                      RVMAnnotation[][] parameterAnnotations, Object annotationDefault) {
    super(declaringClass, memRef, (short) (modifiers & APPLICABLE_TO_METHODS), signature, annotations);
    if (parameterAnnotations != null) {
      synchronized(RVMMethod.parameterAnnotations) {
        RVMMethod.parameterAnnotations.put(this, parameterAnnotations);
      }
    }
    if (exceptionTypes != null) {
      synchronized(RVMMethod.exceptionTypes) {
        RVMMethod.exceptionTypes.put(this, exceptionTypes);
      }
    }
    if (annotationDefault != null) {
      synchronized(annotationDefaults) {
        annotationDefaults.put(this, annotationDefault);
      }
    }
  }

  /**
   * Get the parameter annotations for this method
   */
  @Pure
  private RVMAnnotation[][] getParameterAnnotations() {
    synchronized(parameterAnnotations) {
      return parameterAnnotations.get(this);
    }
  }

  /**
   * Get the annotation default value for an annotation method
   */
  @Pure
  public Object getAnnotationDefault() {
    synchronized(annotationDefaults) {
      Object value = annotationDefaults.get(this);
      if (value instanceof TypeReference || value instanceof Object[]) {
        value = RVMAnnotation.firstUse(value);
        annotationDefaults.put(this, value);
      }
      return value;
    }
  }

  /**
   * Called from {@link ClassFileReader#readClass(TypeReference,DataInputStream)} to create an
   * instance of a RVMMethod by reading the relevant data from the argument bytecode stream.
   *
   * @param declaringClass the TypeReference of the class being loaded
   * @param constantPool the constantPool of the RVMClass object that's being constructed
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the method's attributes from
   */
  static RVMMethod readMethod(TypeReference declaringClass, int[] constantPool, MemberReference memRef,
                              short modifiers, DataInputStream input) throws IOException {
    short tmp_localWords = 0;
    short tmp_operandWords = 0;
    byte[] tmp_bytecodes = null;
    ExceptionHandlerMap tmp_exceptionHandlerMap = null;
    TypeReference[] tmp_exceptionTypes = null;
    int[] tmp_lineNumberMap = null;
    LocalVariableTable tmp_localVariableTable = null;
    Atom tmp_signature = null;
    RVMAnnotation[] annotations = null;
    RVMAnnotation[][] parameterAnnotations = null;
    Object tmp_annotationDefault = null;

    // Read the attributes
    for (int i = 0, n = input.readUnsignedShort(); i < n; i++) {
      Atom attName = ClassFileReader.getUtf(constantPool, input.readUnsignedShort());
      int attLength = input.readInt();

      // Only bother to interpret non-boring Method attributes
      if (attName == RVMClassLoader.codeAttributeName) {
        tmp_operandWords = input.readShort();
        tmp_localWords = input.readShort();
        tmp_bytecodes = new byte[input.readInt()];
        input.readFully(tmp_bytecodes);
        tmp_exceptionHandlerMap = ExceptionHandlerMap.readExceptionHandlerMap(input, constantPool);

        // Read the attributes portion of the code attribute
        for (int j = 0, n2 = input.readUnsignedShort(); j < n2; j++) {
          attName = ClassFileReader.getUtf(constantPool, input.readUnsignedShort());
          attLength = input.readInt();

          if (attName == RVMClassLoader.lineNumberTableAttributeName) {
            int cnt = input.readUnsignedShort();
            if (cnt != 0) {
              tmp_lineNumberMap = new int[cnt];
              for (int k = 0; k < cnt; k++) {
                int startPC = input.readUnsignedShort();
                int lineNumber = input.readUnsignedShort();
                tmp_lineNumberMap[k] = (lineNumber << BITS_IN_SHORT) | startPC;
              }
            }
          } else if (attName == RVMClassLoader.localVariableTableAttributeName) {
            tmp_localVariableTable = LocalVariableTable.readLocalVariableTable(input, constantPool);
          } else {
            // All other entries in the attribute portion of the code attribute are boring.
            int skippedAmount = input.skipBytes(attLength);
            if (skippedAmount != attLength) {
              throw new IOException("Unexpected short skip");
            }
          }
        }
      } else if (attName == RVMClassLoader.exceptionsAttributeName) {
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionTypes = new TypeReference[cnt];
          for (int j = 0, m = tmp_exceptionTypes.length; j < m; ++j) {
            tmp_exceptionTypes[j] = ClassFileReader.getTypeRef(constantPool, input.readUnsignedShort());
          }
        }
      } else if (attName == RVMClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == RVMClassLoader.signatureAttributeName) {
        tmp_signature = ClassFileReader.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == RVMClassLoader.runtimeVisibleAnnotationsAttributeName) {
        annotations = AnnotatedElement.readAnnotations(constantPool, input, declaringClass.getClassLoader());
      } else if (attName == RVMClassLoader.runtimeVisibleParameterAnnotationsAttributeName) {
        int numParameters = input.readByte() & 0xFF;
        parameterAnnotations = new RVMAnnotation[numParameters][];
        for (int a = 0; a < numParameters; ++a) {
          parameterAnnotations[a] = AnnotatedElement.readAnnotations(constantPool, input, declaringClass.getClassLoader());
        }
      } else if (attName == RVMClassLoader.annotationDefaultAttributeName) {
        try {
          tmp_annotationDefault = RVMAnnotation.readValue(memRef.asMethodReference().getReturnType(), constantPool, input, declaringClass.getClassLoader());
        } catch (ClassNotFoundException e) {
          throw new Error(e);
        }
      } else {
        // all other method attributes are boring
        int skippedAmount = input.skipBytes(attLength);
        if (skippedAmount != attLength) {
          throw new IOException("Unexpected short skip");
        }
      }
    }
    RVMMethod method;
    if ((modifiers & ACC_NATIVE) != 0) {
      method =
          new NativeMethod(declaringClass,
                              memRef,
                              modifiers,
                              tmp_exceptionTypes,
                              tmp_signature,
                              annotations,
                              parameterAnnotations,
                              tmp_annotationDefault);
    } else if ((modifiers & ACC_ABSTRACT) != 0) {
      method =
          new AbstractMethod(declaringClass,
                                memRef,
                                modifiers,
                                tmp_exceptionTypes,
                                tmp_signature,
                                annotations,
                                parameterAnnotations,
                                tmp_annotationDefault);

    } else {
      method =
          new NormalMethod(declaringClass,
                              memRef,
                              modifiers,
                              tmp_exceptionTypes,
                              tmp_localWords,
                              tmp_operandWords,
                              tmp_bytecodes,
                              tmp_exceptionHandlerMap,
                              tmp_lineNumberMap,
                              tmp_localVariableTable,
                              constantPool,
                              tmp_signature,
                              annotations,
                              parameterAnnotations,
                              tmp_annotationDefault);
    }
    return method;
  }

  /**
   * Create a copy of the method that occurs in the annotation
   * interface. The method body will contain a read of the field at
   * the constant pool index specified.
   *
   * @param annotationClass the class this method belongs to
   * @param constantPool for the class
   * @param memRef the member reference corresponding to this method
   * @param interfaceMethod the interface method that will copied to
   * produce the annotation method
   * @param constantPoolIndex the index of the field that will be
   * returned by this method
   * @return the created method
   */
  static RVMMethod createAnnotationMethod(TypeReference annotationClass, int[] constantPool,
                                          MemberReference memRef, RVMMethod interfaceMethod,
                                          int constantPoolIndex) {
    byte[] bytecodes =
        new byte[]{
        (byte) JBC_aload_0,
        (byte) JBC_getfield, (byte) (constantPoolIndex >>> 8), (byte) constantPoolIndex,
        // Xreturn
        (byte) typeRefToReturnBytecode(interfaceMethod.getReturnType())};
    return new NormalMethod(annotationClass,
                               memRef,
                               (short) (ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC),
                               null,
                               (short) 1,
                               (short) 2,
                               bytecodes,
                               null,
                               null,
                               null,
                               constantPool,
                               null,
                               null,
                               null,
                               null);
  }

  /**
   * Create a method to initialise the annotation class
   *
   * @param aClass the class this method belongs to
   * @param constantPool for the class
   * @param memRef the member reference corresponding to this method
   * @param objectInitIndex an index into the constant pool for a
   * method reference to java.lang.Object.<init>
   * @param aFields
   * @param aMethods
   * @return the created method
   */
  static RVMMethod createAnnotationInit(TypeReference aClass, int[] constantPool, MemberReference memRef,
                                        int objectInitIndex, RVMField[] aFields, RVMMethod[] aMethods,
                                        int[] defaultConstants) {
    byte[] bytecode = new byte[6 + (defaultConstants.length * 7)];
    bytecode[0] = (byte) JBC_aload_0; // stack[0] = this
    bytecode[1] = (byte) JBC_aload_1; // stack[1] = instanceof RVMAnnotation
    bytecode[2] = (byte) JBC_invokespecial;
    bytecode[3] = (byte) (objectInitIndex >>> 8);
    bytecode[4] = (byte) objectInitIndex;
    for (int i = 0, j = 0; i < aMethods.length; i++) {
      Object value = aMethods[i].getAnnotationDefault();
      if (value != null) {
        bytecode[(j * 7) + 5 + 0] = (byte) JBC_aload_0;    // stack[0] = this
        byte literalType = ClassFileReader.getLiteralDescription(constantPool, defaultConstants[j]);
        if (literalType != CP_LONG && literalType != CP_DOUBLE) {
          bytecode[(j * 7) + 5 + 1] = (byte) JBC_ldc_w; // stack[1] = value
        } else {
          bytecode[(j * 7) + 5 + 1] = (byte) JBC_ldc2_w;// stack[1&2] = value
        }
        bytecode[(j * 7) + 5 + 2] = (byte) (defaultConstants[j] >>> 8);
        bytecode[(j * 7) + 5 + 3] = (byte) defaultConstants[j];
        bytecode[(j * 7) + 5 + 4] = (byte) JBC_putfield;
        bytecode[(j * 7) + 5 + 5] = (byte) (i >>> 8);
        bytecode[(j * 7) + 5 + 6] = (byte) i;
        j++;
      }
    }
    bytecode[bytecode.length - 1] = (byte) JBC_return;
    return new NormalMethod(aClass,
                               memRef,
                               (short) (ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC),
                               null,
                               (short) 2,
                               (short) 3,
                               bytecode,
                               null,
                               null,
                               null,
                               constantPool,
                               null,
                               null,
                               null,
                               null);
  }

  /**
   * What would be the appropriate return bytecode for the given type
   * reference?
   */
  private static int typeRefToReturnBytecode(TypeReference tr) {
    if (!tr.isPrimitiveType()) {
      return JBC_areturn;
    } else {
      Primitive pt = (Primitive) tr.peekType();
      if ((pt == RVMType.BooleanType) ||
          (pt == RVMType.ByteType) ||
          (pt == RVMType.ShortType) ||
          (pt == RVMType.CharType) ||
          (pt == RVMType.IntType)) {
        return JBC_ireturn;
      } else if (pt == RVMType.LongType) {
        return JBC_lreturn;
      } else if (pt == RVMType.FloatType) {
        return JBC_freturn;
      } else if (pt == RVMType.DoubleType) {
        return JBC_dreturn;
      } else {
        VM._assert(false);
        return -1;
      }
    }
  }

  /**
   * Is this method a class initializer?
   */
  @Uninterruptible
  public final boolean isClassInitializer() {
    return getName() == RVMClassLoader.StandardClassInitializerMethodName;
  }

  /**
   * Is this method an object initializer?
   */
  @Uninterruptible
  public final boolean isObjectInitializer() {
    return getName() == RVMClassLoader.StandardObjectInitializerMethodName;
  }

  /**
   * Is this method a compiler-generated object initializer helper?
   */
  @Uninterruptible
  public final boolean isObjectInitializerHelper() {
    return getName() == RVMClassLoader.StandardObjectInitializerHelperMethodName;
  }

  /**
   * Type of this method's return value.
   */
  @Uninterruptible
  public final TypeReference getReturnType() {
    return memRef.asMethodReference().getReturnType();
  }

  /**
   * Type of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public final TypeReference[] getParameterTypes() {
    return memRef.asMethodReference().getParameterTypes();
  }

  /**
   * Space required by this method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public final int getParameterWords() {
    return memRef.asMethodReference().getParameterWords();
  }

  /**
   * Has machine code been generated for this method's bytecodes?
   */
  public final boolean isCompiled() {
    return currentCompiledMethod != null;
  }

  /**
   * Get the current compiled method for this method.
   * Will return null if there is no current compiled method!
   *
   * We make this method Unpreemptible to avoid a race-condition
   * in Reflection.invoke.
   * @return compiled method
   */
  @Unpreemptible
  public final synchronized CompiledMethod getCurrentCompiledMethod() {
    return currentCompiledMethod;
  }

  /**
   * Declared as statically dispatched?
   */
  @Uninterruptible
  public final boolean isStatic() {
    return (modifiers & ACC_STATIC) != 0;
  }

  /**
   * Declared as non-overridable by subclasses?
   */
  @Uninterruptible
  public final boolean isFinal() {
    return (modifiers & ACC_FINAL) != 0;
  }

  /**
   * Guarded by monitorenter/monitorexit?
   */
  @Uninterruptible
  public final boolean isSynchronized() {
    return (modifiers & ACC_SYNCHRONIZED) != 0;
  }

  /**
   * Not implemented in java?
   */
  @Uninterruptible
  public final boolean isNative() {
    return (modifiers & ACC_NATIVE) != 0;
  }

  /**
   * Strict enforcement of IEEE 754 rules?
   */
  public final boolean isStrictFP() {
    return (modifiers & ACC_STRICT) != 0;
  }

  /**
   * Not implemented in Java and use C not JNI calling convention
   */
  public final boolean isSysCall() {
    return isNative() && isStatic() && isAnnotationDeclared(TypeReference.SysCall);
  }

  /**
   * Not implemented in Java and use C not JNI calling convention
   */
  public final boolean isSpecializedInvoke() {
    return isAnnotationDeclared(TypeReference.SpecializedMethodInvoke);
  }

  /**
   * Implemented in subclass?
   */
  @Uninterruptible
  public final boolean isAbstract() {
    return (modifiers & ACC_ABSTRACT) != 0;
  }

  /**
   * Not present in source code file?
   */
  public boolean isSynthetic() {
    return (modifiers & ACC_SYNTHETIC) != 0;
  }

  /**
   * Is this method a bridge method? Bridge methods are generated in some cases
   * of generics and inheritance.
   */
  public boolean isBridge() {
    return (modifiers & BRIDGE) != 0;
  }

  /**
   * Is this a varargs method taking a variable number of arguments?
   */
  public boolean isVarArgs() {
    return (modifiers & VARARGS) != 0;
  }

  /**
   * Exceptions thrown by this method -
   * something like { "java/lang/IOException", "java/lang/EOFException" }
   * @return info (null --> method doesn't throw any exceptions)
   */
  @Pure
  public final TypeReference[] getExceptionTypes() {
    synchronized(exceptionTypes) {
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
   * <li> If it is a <clinit> or <init> method then it is interruptible.
   * <li> If is the synthetic 'this' method used by jikes to
   *      factor out default initializers for <init> methods then it is interruptible.
   * <li> If it is annotated with <CODE>Interruptible</CODE> it is interruptible.
   * <li> If it is annotated with <CODE>Preemptible</CODE> it is interruptible.
   * <li> If it is annotated with <CODE>Uninterruptible</CODE> it is not interruptible.
   * <li> If it is annotated with <CODE>UninterruptibleNoWarn</CODE> it is not interruptible.
   * <li> If it is annotated with <CODE>Unpreemptible</CODE> it is not interruptible.
   * <li> If its declaring class is annotated with <CODE>Uninterruptible</CODE>
   *      or <CODE>Unpreemptible</CODE> it is not interruptible.
   * </ul>
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
   */
  public final boolean hasNoInlinePragma() {
    return (hasNoInlineAnnotation() || hasNoOptCompileAnnotation());
  }

  /**
   * @return true if the method may write to a given field
   */
  public boolean mayWrite(RVMField field) {
    return true; // be conservative.  native methods can write to anything
  }

  /**
   * @return true if the method is the implementation of a runtime service
   * that is called "under the covers" from the generated code and thus is not subject to
   * inlining via the normal mechanisms.
   */
  public boolean isRuntimeServiceMethod() {
    return false; // only NormalMethods can be runtime service impls in Jikes RVM and they override this method
  }

  /**
   * Should all allocation from this method go to a non-moving space?
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
   * Get the code array that corresponds to the entry point (prologue) for the method.
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
          return LazyCompilationTrampoline.instructions;
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
   * Return the resulting CompiledMethod object.
   */
  public final synchronized void compile() {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isResolved());
    if (isCompiled()) return;

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("RVMMethod: (begin) compiling " + this + "\n");

    CompiledMethod cm = genCode();

    // Ensure that cm wasn't invalidated while it was being compiled.
    synchronized (cm) {
      if (cm.isInvalid()) {
        CompiledMethods.setCompiledMethodObsolete(cm);
      } else {
        currentCompiledMethod = cm;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM) VM.sysWrite("RVMMethod: (end)   compiling " + this + "\n");
  }

  protected abstract CompiledMethod genCode();

  //----------------------------------------------------------------//
  //                        Section 3.                              //
  // The following are available after the declaring class has been //
  // "instantiated".                                                //
  //----------------------------------------------------------------//

  /**
   * Change machine code that will be used by future executions of this method
   * (ie. optimized <-> non-optimized)
   * @param compiledMethod new machine code
   * Side effect: updates jtoc or method dispatch tables
   * ("type information blocks")
   *              for this class and its subclasses
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

    // Install the new method in jtoc/tib. If virtual, will also replace in
    // all subclasses that inherited the method.
    getDeclaringClass().updateMethod(this);

    // Replace constant-ified virtual method in JTOC if necessary
    Offset jtocOffset = getJtocOffset();
    if (jtocOffset.NE(Offset.zero())) {
      Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
    }

    // Now that we've updated the jtoc/tib, old version is obsolete
    if (oldCompiledMethod != null) {
      CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * If CM is the current compiled code for this, then invaldiate it.
   */
  public final synchronized void invalidateCompiledMethod(CompiledMethod cm) {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isInstantiated());
    if (currentCompiledMethod == cm) {
      replaceCompiledMethod(null);
    }
  }

  /**
   * Get the offset used to hold a JTOC addressable version of the current entry
   * code array
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
   * Find or create a jtoc offset for this method
   */
  public final synchronized Offset findOrCreateJtocOffset() {
    if (VM.VerifyAssertions) VM._assert(!isStatic() && !isObjectInitializer());
    Offset jtocOffset = getJtocOffset();;
    if (jtocOffset.EQ(Offset.zero())) {
      jtocOffset = Statics.allocateReferenceSlot(true);
      Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
      synchronized(jtocOffsets) {
        jtocOffsets.put(this, Integer.valueOf(jtocOffset.toInt()));
      }
    }
    return jtocOffset;
  }

  /**
   * Returns the parameter annotations for this method.
   */
  @Pure
  public final Annotation[][] getDeclaredParameterAnnotations() {
    Annotation[][] result;
    synchronized(declaredParameterAnnotations) {
      result = declaredParameterAnnotations.get(this);
    }
    if (result == null) {
      RVMAnnotation[][] parameterAnnotations = getParameterAnnotations();
      result = new Annotation[parameterAnnotations.length][];
      for (int a = 0; a < result.length; ++a) {
        result[a] = toAnnotations(parameterAnnotations[a]);
      }
      synchronized(declaredParameterAnnotations) {
        declaredParameterAnnotations.put(this, result);
      }
    }
    return result;
  }

  /** Map from a method to a reflective method capable of invoking it */
  private static final ImmutableEntryHashMapRVM<RVMMethod, ReflectionBase> invokeMethods =
    Reflection.bytecodeReflection || Reflection.cacheInvokerInJavaLangReflect ?
      new ImmutableEntryHashMapRVM<RVMMethod, ReflectionBase>(30) : null;

  /**
   * Get an instance of an object capable of reflectively invoking this method
   */
  @RuntimePure
  @SuppressWarnings("unchecked")
  public synchronized ReflectionBase getInvoker() {
    if (!VM.runningVM) {
      return null;
    }
    ReflectionBase invoker;
    if (invokeMethods != null) {
      synchronized(RVMMethod.class) {
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
        synchronized(RVMMethod.class) {
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
                               null,
                               null,
                               null);
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
    int curBC = 0;
    if (!isStatic()) {
      bytecodes = new byte[8 * numParams + 8];
      bytecodes[curBC] = JBC_aload_1;
      curBC++;
    } else {
      bytecodes = new byte[8 * numParams + 7];
    }
    for (int i=0; i < numParams; i++) {
      if (parameters[i].isVoidType()) {
        bytecodes[curBC] =
          bytecodes[curBC+1] =
          bytecodes[curBC+2] =
          bytecodes[curBC+3] =
          bytecodes[curBC+4] =
          bytecodes[curBC+5] =
          bytecodes[curBC+6] =
          bytecodes[curBC+7] =
            (byte)JBC_nop;
        continue;
      }
      bytecodes[curBC] = (byte)JBC_aload_2;
      bytecodes[curBC+1] = (byte)JBC_sipush;
      bytecodes[curBC+2] = (byte)(i >>> 8);
      bytecodes[curBC+3] = (byte)i;
      bytecodes[curBC+4] = (byte)JBC_aaload;
      if (!parameters[i].isPrimitiveType()) {
        bytecodes[curBC+5] = (byte)JBC_checkcast;
        if (VM.VerifyAssertions) VM._assert(parameters[i].getId() != 0);
        constantPool[i+1] = ClassFileReader.packCPEntry(CP_CLASS, parameters[i].getId());
        bytecodes[curBC+6] = (byte)((i+1) >>> 8);
        bytecodes[curBC+7] = (byte)(i+1);
      } else if (parameters[i].isWordType()) {
        bytecodes[curBC+5] =
          bytecodes[curBC+6] =
          bytecodes[curBC+7] =
            (byte)JBC_nop;
      } else {
        bytecodes[curBC+5] = (byte)JBC_invokestatic;
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
        constantPool[i+1] = ClassFileReader.packCPEntry(CP_MEMBER, unboxMethod.getId());
        bytecodes[curBC+6] = (byte)((i+1) >>> 8);
        bytecodes[curBC+7] = (byte)(i+1);
      }
      curBC+=8;
    }
    if (isStatic()) {
      bytecodes[curBC] = (byte)JBC_invokestatic;
    } else if (isObjectInitializer() || isPrivate()) {
      bytecodes[curBC] = (byte)JBC_invokespecial;
    } else {
      bytecodes[curBC] = (byte)JBC_invokevirtual;
    }
    constantPool[numParams+1] = ClassFileReader.packCPEntry(CP_MEMBER, getId());
    bytecodes[curBC+1] = (byte)((numParams+1) >>> 8);
    bytecodes[curBC+2] = (byte)(numParams+1);
    TypeReference returnType = getReturnType();
    if (!returnType.isPrimitiveType() || returnType.isWordType()) {
      bytecodes[curBC+3] = (byte)JBC_nop;
      bytecodes[curBC+4] = (byte)JBC_nop;
      bytecodes[curBC+5] = (byte)JBC_nop;
    } else if (returnType.isVoidType()) {
      bytecodes[curBC+3] = (byte)JBC_aconst_null;
      bytecodes[curBC+4] = (byte)JBC_nop;
      bytecodes[curBC+5] = (byte)JBC_nop;
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
      constantPool[numParams+2] = ClassFileReader.packCPEntry(CP_MEMBER, boxMethod.getId());
      bytecodes[curBC+3] = (byte)JBC_invokestatic;
      bytecodes[curBC+4] = (byte)((numParams+2) >>> 8);
      bytecodes[curBC+5] = (byte)(numParams+2);
    }
    bytecodes[curBC+6] = (byte)JBC_areturn;
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
                               null,
                               null,
                               null);
  }
}
