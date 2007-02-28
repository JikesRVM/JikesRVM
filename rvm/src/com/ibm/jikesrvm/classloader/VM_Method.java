/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002
 */
package com.ibm.jikesrvm.classloader;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_LazyCompilationTrampolineGenerator;

import java.io.DataInputStream;
import java.io.IOException;

import org.vmmagic.unboxed.Offset;
import org.vmmagic.pragma.*;

/**
 * A method of a java class corresponding to a method_info structure
 * in the class file. A method is read from a class file using the
 * {@link #readMethod} method.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Ian Rogers
 */
public abstract class VM_Method extends VM_Member implements VM_BytecodeConstants {

  /**
   * current compiled method for this method
   */
  protected VM_CompiledMethod currentCompiledMethod;
  /**
   * exceptions this method might throw (null --> none)
   */
  protected final VM_TypeReference[] exceptionTypes;
  /**
   * Method paramter annotations from the class file that are
   * described as runtime visible. These annotations are available to
   * the reflection API.
   */
  protected final VM_Annotation[] parameterAnnotations;
  /**
   * A value present in the method info tables of annotation types. It
   * represents the default result from an annotation method.
   */
  protected final Object annotationDefault;
  /**
   * The offset of this virtual method in the jtoc if it's been placed
   * there by constant propagation, otherwise 0.
   */
  private Offset jtocOffset;

  /**
   * Construct a read method
   *
   * @param declaringClass the VM_Class object of the class that declared this field
   * @param memRef the canonical memberReference for this method.
   * @param modifiers modifiers associated with this method.
   * @param exceptionTypes exceptions thrown by this method.
   * @param signature generic type of this method.
   * @param annotations array of runtime visible annotations
   * @param parameterAnnotations array of runtime visible parameter annotations
   * @param annotationDefault value for this annotation that appears
   */
  protected VM_Method(VM_TypeReference declaringClass, VM_MemberReference memRef,
                      int modifiers, VM_TypeReference[] exceptionTypes, VM_Atom signature,
                      VM_Annotation[] annotations,
                      VM_Annotation[] parameterAnnotations,
                      Object annotationDefault)
  {
    super(declaringClass, memRef, modifiers & APPLICABLE_TO_METHODS, signature, annotations);
    this.parameterAnnotations = parameterAnnotations;
    this.annotationDefault = annotationDefault;
    memRef.asMethodReference().setResolvedMember(this);
    this.exceptionTypes = exceptionTypes;
    this.jtocOffset = Offset.fromIntSignExtend(-1);
  }
  
  /**
   * Called from {@link VM_Class#readClass(VM_TypeReference, DataInputStream)} to create an
   * instance of a VM_Method by reading the relevant data from the argument bytecode stream.
   * 
   * @param declaringClass the VM_TypeReference of the class being loaded
   * @param constantPool the constantPool of the VM_Class object that's being constructed
   * @param memRef the canonical memberReference for this member.
   * @param modifiers modifiers associated with this member.
   * @param input the DataInputStream to read the method's attributes from
   */
  static VM_Method readMethod(VM_TypeReference declaringClass, int[] constantPool, VM_MemberReference memRef,
                              int modifiers, DataInputStream input) throws IOException {
    int tmp_localWords = 0;
    int tmp_operandWords = 0;      
    byte[] tmp_bytecodes = null;       
    VM_ExceptionHandlerMap tmp_exceptionHandlerMap = null;
    VM_TypeReference[] tmp_exceptionTypes = null;
    int[] tmp_lineNumberMap = null;      
    VM_Atom tmp_signature = null;
    VM_Annotation[] annotations = null;
    VM_Annotation[] parameterAnnotations = null;
    Object tmp_annotationDefault = null;

    // Read the attributes
    for (int i = 0, n = input.readUnsignedShort(); i<n; i++) {
      VM_Atom attName   = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      int     attLength = input.readInt();

      // Only bother to interpret non-boring Method attributes
      if (attName == VM_ClassLoader.codeAttributeName) {
        tmp_operandWords = input.readUnsignedShort();
        tmp_localWords   = input.readUnsignedShort();
        tmp_bytecodes = new byte[input.readInt()];
        input.readFully(tmp_bytecodes);
        tmp_exceptionHandlerMap = VM_ExceptionHandlerMap.readExceptionHandlerMap(input, constantPool);

        // Read the attributes portion of the code attribute
        for (int j = 0, n2 = input.readUnsignedShort(); j<n2; j++) {
          attName   = VM_Class.getUtf(constantPool, input.readUnsignedShort());
          attLength = input.readInt();

          if (attName == VM_ClassLoader.lineNumberTableAttributeName) {
            int cnt = input.readUnsignedShort();
            if (cnt != 0) {
              tmp_lineNumberMap = new int[cnt];
              for (int k = 0; k<cnt; k++) {
                int startPC = input.readUnsignedShort();
                int lineNumber = input.readUnsignedShort();
                tmp_lineNumberMap[k] = (lineNumber << BITS_IN_SHORT) | startPC;
              }
            }
          } else {
            // All other entries in the attribute portion of the code attribute are boring.
            input.skipBytes(attLength);
          }
        }
      } else if (attName == VM_ClassLoader.exceptionsAttributeName) {
        int cnt = input.readUnsignedShort();
        if (cnt != 0) {
          tmp_exceptionTypes = new VM_TypeReference[cnt];
          for (int j = 0, m = tmp_exceptionTypes.length; j < m; ++j) {
            tmp_exceptionTypes[j] = VM_Class.getTypeRef(constantPool, input.readUnsignedShort());
          }
        }
      } else if (attName == VM_ClassLoader.syntheticAttributeName) {
        modifiers |= ACC_SYNTHETIC;
      } else if (attName == VM_ClassLoader.signatureAttributeName) {
        tmp_signature = VM_Class.getUtf(constantPool, input.readUnsignedShort());
      } else if (attName == VM_ClassLoader.runtimeVisibleAnnotationsAttributeName) {
        annotations = VM_AnnotatedElement.readAnnotations(constantPool, input, 2,
                                                                            declaringClass.getClassLoader());
      } else if (attName == VM_ClassLoader.runtimeVisibleParameterAnnotationsAttributeName) {
        parameterAnnotations = VM_AnnotatedElement.readAnnotations(constantPool, input, 1,
                                                                                     declaringClass.getClassLoader());
      } else if (attName == VM_ClassLoader.annotationDefaultAttributeName) {
        try {
          tmp_annotationDefault = VM_Annotation.readValue(constantPool, input, declaringClass.getClassLoader());
        }
        catch (ClassNotFoundException e){
          throw new Error(e);
        }
      } else {
        // all other method attributes are boring
        input.skipBytes(attLength);
      }
    }
    VM_Method method;
    if ((modifiers & ACC_NATIVE) != 0) {
      method = new VM_NativeMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes, tmp_signature,
                                   annotations, parameterAnnotations, tmp_annotationDefault);
    } else if ((modifiers & ACC_ABSTRACT) != 0) {
      method = new VM_AbstractMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes, tmp_signature,
                                     annotations, parameterAnnotations, tmp_annotationDefault);

    } else {
      method = new VM_NormalMethod(declaringClass, memRef, modifiers, tmp_exceptionTypes,
                                   tmp_localWords, tmp_operandWords, tmp_bytecodes, 
                                   tmp_exceptionHandlerMap, tmp_lineNumberMap,
                                   constantPool, tmp_signature,
                                   annotations, parameterAnnotations, tmp_annotationDefault);
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
  static VM_Method createAnnotationMethod(VM_TypeReference annotationClass, int[] constantPool,
                                          VM_MemberReference memRef, VM_Method interfaceMethod,
                                          int constantPoolIndex) {
    byte[] bytecodes = new byte[] {
      (byte)JBC_aload_0,
      (byte)JBC_getfield,
      (byte)(constantPoolIndex >>> 8),
      (byte)constantPoolIndex,
      // Xreturn
      (byte)typeRefToReturnBytecode(interfaceMethod.getReturnType())
    };
    return new VM_NormalMethod(annotationClass, memRef, ACC_PUBLIC|ACC_FINAL|ACC_SYNTHETIC, null,
                               1, 2, bytecodes,
                               null, null,
                               constantPool,
                               null, null, null, null);
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
  static VM_Method createAnnotationInit(VM_TypeReference aClass, int[] constantPool,
                                        VM_MemberReference memRef, int objectInitIndex,
                                        VM_Field[] aFields, VM_Method[] aMethods,
                                        int[] defaultConstants) {
    byte[] bytecode = new byte[6+(defaultConstants.length*7)];
    bytecode[0] =   (byte)JBC_aload_0; // stack[0] = this
    bytecode[1] =   (byte)JBC_aload_1; // stack[1] = instanceof VM_Annotation
    bytecode[2] = (byte)JBC_invokespecial;
    bytecode[3] = (byte)(objectInitIndex >>> 8);
    bytecode[4] = (byte)objectInitIndex;
    for(int i=0, j=0; i < aMethods.length; i++) {
      if(aMethods[i].annotationDefault != null) {
        bytecode[(j*7)+5+0] = (byte)JBC_aload_0;    // stack[0] = this
        if(VM_Class.getLiteralSize(constantPool, defaultConstants[j]) == BYTES_IN_INT) {
          bytecode[(j*7)+5+1] = (byte)JBC_ldc_w; // stack[1] = value
        }
        else {
          bytecode[(j*7)+5+1] = (byte)JBC_ldc2_w;// stack[1&2] = value
        }
        bytecode[(j*7)+5+2] = (byte)(defaultConstants[j] >>> 8);        
        bytecode[(j*7)+5+3] = (byte)defaultConstants[j];        
        bytecode[(j*7)+5+4] = (byte)JBC_putfield;
        bytecode[(j*7)+5+5] = (byte)(i >>> 8);        
        bytecode[(j*7)+5+6] = (byte)i;
        j++;
      }
    }
    bytecode[bytecode.length-1] = (byte)JBC_return;
    return new VM_NormalMethod(aClass, memRef, ACC_PUBLIC|ACC_FINAL|ACC_SYNTHETIC, null,
                               2, 3, bytecode,
                               null, null,
                               constantPool,
                               null, null, null, null);
  }

  /**
   * What would be the appropriate return bytecode for the given type
   * reference?
   */
  private static int typeRefToReturnBytecode(VM_TypeReference tr) {
    if(!tr.isPrimitiveType()) {
      return JBC_areturn;
    } else {
      VM_Primitive pt = (VM_Primitive)tr.peekResolvedType();
      if((pt == VM_Type.BooleanType)||(pt == VM_Type.ByteType)||(pt == VM_Type.ShortType)||
              (pt == VM_Type.CharType)||(pt == VM_Type.IntType)) {
        return JBC_ireturn;
      }
      else if(pt == VM_Type.LongType) {
        return JBC_lreturn;
      }
      else if(pt == VM_Type.FloatType) {
        return JBC_freturn;
      }
      else if(pt == VM_Type.DoubleType) {
        return JBC_dreturn;
      }
      else {
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
    return getName() == VM_ClassLoader.StandardClassInitializerMethodName;  
  }

  /**
   * Is this method an object initializer?
   */
  @Uninterruptible
  public final boolean isObjectInitializer() { 
    return getName() == VM_ClassLoader.StandardObjectInitializerMethodName; 
  }

  /**
   * Is this method a compiler-generated object initializer helper?
   */
  @Uninterruptible
  public final boolean isObjectInitializerHelper() { 
    return getName() == VM_ClassLoader.StandardObjectInitializerHelperMethodName; 
  }

  /**
   * Type of this method's return value.
   */
  @Uninterruptible
  public final VM_TypeReference getReturnType() { 
    return memRef.asMethodReference().getReturnType();
  }

  /**
   * Type of this method's parameters.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public final VM_TypeReference[] getParameterTypes() { 
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
   * in VM_Reflection.invoke.
   * @return compiled method
   */ 
  @Unpreemptible
  public final synchronized VM_CompiledMethod getCurrentCompiledMethod() { 
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
   * Exceptions thrown by this method - 
   * something like { "java/lang/IOException", "java/lang/EOFException" }
   * @return info (null --> method doesn't throw any exceptions)
   */
  @Uninterruptible
  public final VM_TypeReference[] getExceptionTypes() { 
    return exceptionTypes;
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
    if (isAnnotationPresent(Interruptible.class)) return true;
    if (isAnnotationPresent(Preemptible.class)) return true;
    if (isAnnotationPresent(UninterruptibleNoWarn.class)) return false;
    if (isAnnotationPresent(Uninterruptible.class)) return false;
    if (isAnnotationPresent(Unpreemptible.class)) return false;
    if (getDeclaringClass().isUnpreemptible()) return false;
    if (getDeclaringClass().isUninterruptible()) return false;
    return true;
  }

  /**
   * Is the method Unpreemptible? See the comment in {@link #isInterruptible}
   */
  public final boolean isUnpreemptible() {
    if (isClassInitializer() || isObjectInitializer()) return false;
    if (isObjectInitializerHelper()) return false;
    if (isAnnotationPresent(Interruptible.class)) return false;
    if (isAnnotationPresent(Preemptible.class)) return false;
    if (isAnnotationPresent(Uninterruptible.class)) return false;
    if (isAnnotationPresent(UninterruptibleNoWarn.class)) return false;
    if (isAnnotationPresent(Unpreemptible.class)) return true;
    if (getDeclaringClass().isUnpreemptible()) return true;
    return false;
  }

  /**
   * Is the method Uninterruptible? See the comment in {@link #isInterruptible}
   */
  public final boolean isUninterruptible() {
    if (isClassInitializer() || isObjectInitializer()) return false;
    if (isObjectInitializerHelper()) return false;
    if (isAnnotationPresent(Interruptible.class)) return false;
    if (isAnnotationPresent(Preemptible.class)) return false;
    if (isAnnotationPresent(Unpreemptible.class)) return false;
    if (isAnnotationPresent(Uninterruptible.class)) return true;
    if (isAnnotationPresent(UninterruptibleNoWarn.class)) return true;
    if (getDeclaringClass().isUninterruptible()) return true;
    return false;
  }

  /**
   * Has this method been marked as mandatory to inline?
   * ie., it implements the <CODE>Inline</CODE> exception?
   */
  public final boolean hasInlinePragma() {
    return isAnnotationPresent(Inline.class);
  }
    
  /**
   * Has this method been marked as forbidden to inline?
   * ie., it is marked with the <CODE>NoInline</CODE> annotation or
   * the <CODE>NoOptCompile</CODE> annotation?
   */
  public final boolean hasNoInlinePragma() {
    return (isAnnotationPresent(NoInline.class) || isAnnotationPresent(NoOptCompile.class));
  }
    
  /**
   * Has this method been marked as no opt compile?
   * ie., it is marked with the <CODE>NoOptCompile</CODE> annotation?
   */
  public final boolean hasNoOptCompilePragma() {
    return isAnnotationPresent(NoOptCompile.class);
  }
    
  /**
   * @return true if the method may write to a given field
   */
  public boolean mayWrite(VM_Field field) {
    return true; // be conservative.  native methods can write to anything
  }

  /**
   * @return true if the method is the implementation of a runtime service
   * that is called "under the covers" from the generated code and thus is not subject to
   * inlining via the normal mechanisms.
   */
  public boolean isRuntimeServiceMethod() {
    return false; // only VM_NormalMethods can be runtime service impls in Jikes RVM and they override this method
  }

  //------------------------------------------------------------------//
  //                        Section 2.                                //
  // The following are available after the declaring class has been   //
  // "resolved".                                                      //
  //------------------------------------------------------------------//

  /**
   * Get the code array that corresponds to the entry point (prologue) for the method.
   */
  public final synchronized VM_CodeArray getCurrentEntryCodeArray() {
    VM_Class declaringClass = getDeclaringClass();
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    if (isCompiled()) {
      return currentCompiledMethod.getEntryCodeArray();
    } else if (!VM.writingBootImage || isNative()) {
      if (!isStatic() && !isObjectInitializer() && !isPrivate()) {
        // A non-private virtual method.
        if (declaringClass.isJavaLangObjectType() ||
            declaringClass.getSuperClass().findVirtualMethod(getName(), getDescriptor()) == null) {
          // The root method of a virtual method family can use the lazy method invoker directly.
          return VM_Entrypoints.lazyMethodInvokerMethod.getCurrentEntryCodeArray();
        } else {
          // All other virtual methods in the family must generate unique stubs to
          // ensure correct operation of the method test (guarded inlining of virtual calls).
          return (VM_CodeArray) VM_LazyCompilationTrampolineGenerator.getTrampoline();
        }
      } else {
        // We'll never do a method test against this method.
        // Therefore we can use the lazy method invoker directly.
        return VM_Entrypoints.lazyMethodInvokerMethod.getCurrentEntryCodeArray();
      }
    } else {
      compile(); 
      return currentCompiledMethod.getEntryCodeArray();
    }
  }

  /**
   * Generate machine code for this method if valid
   * machine code doesn't already exist. 
   * Return the resulting VM_CompiledMethod object.
   */
  public final synchronized void compile() {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isResolved());
    if (isCompiled()) return;

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (begin) compiling " + this + "\n");

    VM_CompiledMethod cm = genCode();

    // Ensure that cm wasn't invalidated while it was being compiled.
    synchronized(cm) {
      if (cm.isInvalid()) {
        VM_CompiledMethods.setCompiledMethodObsolete(cm);
      } else {
        currentCompiledMethod = cm;
      }
    }

    if (VM.TraceClassLoading && VM.runningVM)  VM.sysWrite("VM_Method: (end)   compiling " + this + "\n");
  }

  protected abstract VM_CompiledMethod genCode();

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
  public final synchronized void replaceCompiledMethod(VM_CompiledMethod compiledMethod) {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isInstantiated());
    // If we're replacing with a non-null compiledMethod, ensure that is still valid!
    if (compiledMethod != null) {
      synchronized(compiledMethod) {
        if (compiledMethod.isInvalid()) return;
      }
    }
      
    // Grab version that is being replaced
    VM_CompiledMethod oldCompiledMethod = currentCompiledMethod;
    currentCompiledMethod = compiledMethod;

    // Install the new method in jtoc/tib. If virtual, will also replace in
    // all subclasses that inherited the method.
    getDeclaringClass().updateMethod(this);

    // Replace constant-ified virtual method in JTOC if necessary
    if(jtocOffset.toInt() != -1) {
      VM_Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
    }

    // Now that we've updated the jtoc/tib, old version is obsolete
    if (oldCompiledMethod != null) {
      VM_CompiledMethods.setCompiledMethodObsolete(oldCompiledMethod);
    }
  }

  /**
   * If CM is the current compiled code for this, then invaldiate it. 
   */
  public final synchronized void invalidateCompiledMethod(VM_CompiledMethod cm) {
    if (VM.VerifyAssertions) VM._assert(getDeclaringClass().isInstantiated());
    if (currentCompiledMethod == cm) {
      replaceCompiledMethod(null);
    }
  }

  /**
   * Find or create a jtoc offset for this method
   */
  public final synchronized Offset findOrCreateJtocOffset() {
    if (VM.VerifyAssertions) VM._assert(!isStatic() && !isObjectInitializer());
    if(jtocOffset.EQ(Offset.zero())) {
      jtocOffset = VM_Statics.allocateReferenceSlot();
      VM_Statics.setSlotContents(jtocOffset, getCurrentEntryCodeArray());
    }
    return jtocOffset;
  }
}
