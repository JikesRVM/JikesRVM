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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.VM_Compiler;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.adaptive.recompilation.VM_CompilerDNA;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_BootImageCompiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;

/**
 * Use baseline compiler to build virtual machine boot image.
 */
public final class VM_BaselineBootImageCompiler extends VM_BootImageCompiler {

  /**
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  protected void initCompiler(String[] args) {
    VM_BaselineCompiler.initOptions();
    // Process arguments specified by the user.
    for (int i = 0, n = args.length; i < n; i++) {
      String arg = args[i];
      if (!VM_Compiler.options.processAsOption("-X:bc:", arg)) {
        VM.sysWrite("VM_BootImageCompiler(baseline): Unrecognized argument " + arg + "; ignoring\n");
      }
    }
  }

  /**
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  protected VM_CompiledMethod compileMethod(VM_NormalMethod method) {
    VM_CompiledMethod cm;
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    cm = VM_BaselineCompiler.compile(method);

    if (VM.BuildForAdaptiveSystem) {
      // Must estimate compilation time by using offline ratios.
      // It is tempting to time via System.currentTimeMillis()
      // but 1 millisecond granularity isn't good enough because the
      // the baseline compiler is just too fast.
      // TODO: Try Using System.nanoTime() instead
      double compileTime = method.getBytecodeLength() / VM_CompilerDNA.getBaselineCompilationRate();
      cm.setCompilationTime(compileTime);
    }
    return cm;
  }
}
