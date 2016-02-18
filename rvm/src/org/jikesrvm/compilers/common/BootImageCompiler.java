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

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NativeMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.BaselineBootImageCompiler;
import org.jikesrvm.runtime.Callbacks;

/**
 * Abstract superclass to interface bootimage compiler to the rest of the VM.
 * Individual compilers provide concrete implementations, one of which is
 * instantiated by BootImageCompiler.init.
 */
public abstract class BootImageCompiler {

  protected static final BootImageCompiler baseCompiler;
  protected static final BootImageCompiler optCompiler;
  protected static final BootImageCompiler compiler;

  static {
    baseCompiler = new BaselineBootImageCompiler();
    optCompiler = VM.BuildForAdaptiveSystem ? new org.jikesrvm.compilers.opt.driver.OptimizingBootImageCompiler() : null;
    compiler = VM.BuildWithBaseBootImageCompiler ? baseCompiler : optCompiler;
  }

  /**
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  protected abstract void initCompiler(String[] args);

  /**
   * Compiles a method with bytecodes.
   * @param method the method to compile
   * @param params the specialized types of the method's parameters.
   *  This will be {@code null} if the types from the method's signature are used.
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
      if (VM.BuildForAdaptiveSystem && VM.BuildWithBaseBootImageCompiler) {
        // We have to use the opt compiler to compile the org.jikesrvm.compiler.opt.OptSaveVolatile class,
        // so if we're building a baseline compiled configuration that includes AOS, we also need to init
        // the optimizing bootimage compiler so it can be invoked to compile this class.
        optCompiler.initCompiler(args);
      }
    } catch (Throwable e) {
      while (e != null) {
        e.printStackTrace();
        e = e.getCause();
      }
    }
  }

  public static CompiledMethod compile(NormalMethod method, TypeReference[] params) {
    try {
      if (VM.BuildForAdaptiveSystem && VM.BuildWithBaseBootImageCompiler && method.getDeclaringClass().hasSaveVolatileAnnotation()) {
        // Force opt compilation of SaveVolatile methods.
        return optCompiler.compileMethod(method, params);
      } else {
        return compiler.compileMethod(method, params);
      }
    } catch (Exception e) {
      throw new Error("Exception during compilation of " + method, e);
    }
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

    if (VM.BuildForIA32) {
      return org.jikesrvm.jni.ia32.JNICompiler.compile(method);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.jni.ppc.JNICompiler.compile(method);
    }
  }
}
