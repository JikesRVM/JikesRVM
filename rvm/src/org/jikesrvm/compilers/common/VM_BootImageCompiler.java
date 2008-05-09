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
package org.jikesrvm.compilers.common;

import org.jikesrvm.ArchitectureSpecific.VM_JNICompiler;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.classloader.VM_NativeMethod;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineBootImageCompiler;

/**
 * Abstract superclass to interface bootimage compiler to the rest of the VM.
 * Individual compilers provide concrete implementations, one of which is
 * instantiated by VM_BootImageCompiler.init.
 */
public abstract class VM_BootImageCompiler {

  private static VM_BootImageCompiler compiler =
      VM.BuildWithBaseBootImageCompiler ? new VM_BaselineBootImageCompiler() : new org.jikesrvm.compilers.opt.driver.VM_OptimizingBootImageCompiler();

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
  protected abstract VM_CompiledMethod compileMethod(VM_NormalMethod method, VM_TypeReference[] params);

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
        e = e.getCause();
      }
    }
  }

  public static VM_CompiledMethod compile(VM_NormalMethod method, VM_TypeReference[] params) {
    return compiler.compileMethod(method, params);
  }

  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    return compile(method, null);
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
