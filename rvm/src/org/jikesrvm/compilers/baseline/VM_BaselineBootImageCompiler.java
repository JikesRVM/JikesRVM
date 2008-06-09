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

import org.jikesrvm.ArchitectureSpecific.VM_BaselineCompilerImpl;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Callbacks;
import org.jikesrvm.adaptive.recompilation.VM_CompilerDNA;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;
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
      if (!VM_BaselineCompilerImpl.options.processAsOption("-X:bc:", arg)) {
        VM.sysWrite("VM_BootImageCompiler(baseline): Unrecognized argument " + arg + "; ignoring\n");
      }
    }
  }

  /**
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  protected VM_CompiledMethod compileMethod(VM_NormalMethod method, VM_TypeReference[] params) {
    VM_CompiledMethod cm;
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    cm = VM_BaselineCompiler.compile(method);

    if (VM.BuildForAdaptiveSystem) {
      /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
      cm.setCompilationTime((float)VM_CompilerDNA.estimateCompileTime(VM_CompilerDNA.BASELINE, method));
    }
    return cm;
  }
}
