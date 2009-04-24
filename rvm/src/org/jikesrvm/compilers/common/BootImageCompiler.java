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
package org.jikesrvm.compilers.common;

import org.jikesrvm.ArchitectureSpecific.JNICompiler;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.BaselineBootImageCompiler;

/**
 * Abstract superclass to interface bootimage compiler to the rest of the VM.
 * Individual compilers provide concrete implementations, one of which is
 * instantiated by BootImageCompiler.init.
 */
public abstract class BootImageCompiler {

  private static BootImageCompiler compiler =
      VM.BuildWithBaseBootImageCompiler ? new BaselineBootImageCompiler() : new org.jikesrvm.compilers.opt.driver.OptimizingBootImageCompiler();

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
  protected abstract CompiledMethod compileMethod(NormalMethod method, TypeReference[] params);

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

  public static CompiledMethod compile(NormalMethod method, TypeReference[] params) {
    return compiler.compileMethod(method, params);
  }

  public static CompiledMethod compile(NormalMethod method) {
    return compile(method, null);
  }

  /**
   * Compile a native method.
   * @param method the method to compile
   * @return the compiled method
   */
  public static CompiledMethod compile(NativeMethod method) {
    Callbacks.notifyMethodCompile(method, CompiledMethod.JNI);
    return JNICompiler.compile(method);
  }
}
