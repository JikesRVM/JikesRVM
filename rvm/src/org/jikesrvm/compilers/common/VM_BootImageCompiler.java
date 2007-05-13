/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2006
 */
package org.jikesrvm.compilers.common;

import org.jikesrvm.ArchitectureSpecific.VM_JNICompiler;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.classloader.VM_NativeMethod;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.baseline.VM_BaselineBootImageCompiler;

/**
 * Abstract superclass to interface bootimage compiler to the rest of the VM.
 * Individual compilers provide concrete implementations, one of which is
 * instantiated by VM_BootImageCompiler.init.
 */
public abstract class VM_BootImageCompiler {

  private static VM_BootImageCompiler compiler = VM.BuildWithBaseBootImageCompiler ? new VM_BaselineBootImageCompiler() : new org.jikesrvm.compilers.opt.VM_OptimizingBootImageCompiler();

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
  public static void init(String[] args) {
    try {
      compiler.initCompiler(args);
    } catch (Throwable e) {
      while (e != null) {
        e.printStackTrace();
        e= e.getCause();
      }
    }
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
    return VM_JNICompiler.compile(method);
  }
}
