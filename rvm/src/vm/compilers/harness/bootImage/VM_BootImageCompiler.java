/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2006
 */
//$Id$
package com.ibm.jikesrvm;

import com.ibm.jikesrvm.classloader.*;

/**
 * Abstract superclass to interface bootimage compiler to the rest of the VM.
 * Individual compilers provide concrete implementations, one of which is
 * instantiated by VM_BootImageCompiler.init.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public abstract class VM_BootImageCompiler {

  private static VM_BootImageCompiler compiler = 
    //-#if RVM_WITH_BASE_BOOTIMAGE_COMPILER
    new VM_BaselineBootImageCompiler();
    //-#elif RVM_WITH_QUICK_BOOTIMAGE_COMPILER
    new VM_QuickBootImageCompiler();
    //-#elif RVM_WITH_OPT_BOOTIMAGE_COMPILER
    new VM_OptimizingBootImageCompiler();
    //-#endif

  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  protected abstract void initCompiler(String[] args);

  /** 
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  protected abstract VM_CompiledMethod compileMethod(VM_NormalMethod method);
  
  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  static void init(String[] args) {
    compiler.initCompiler(args);
  }

  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    return compiler.compileMethod(method);
  }
  
  /** 
   * Compile a native method.
   * @param method the method to compile
   * @return the compiled method
   */
  public static VM_CompiledMethod compile(VM_NativeMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
    return com.ibm.jikesrvm.jni.VM_JNICompiler.compile(method);
  }
}
