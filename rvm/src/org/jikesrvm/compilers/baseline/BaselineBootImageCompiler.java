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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.ArchitectureSpecific.BaselineCompilerImpl;
import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.BootImageCompiler;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * Use baseline compiler to build virtual machine boot image.
 */
public final class BaselineBootImageCompiler extends BootImageCompiler {

  @Override
  protected void initCompiler(String[] args) {
    BaselineCompiler.initOptions();
    // Process arguments specified by the user.
    for (int i = 0, n = args.length; i < n; i++) {
      String arg = args[i];
      if (!BaselineCompilerImpl.options.processAsOption("-X:bc:", arg)) {
        VM.sysWrite("BootImageCompiler(baseline): Unrecognized argument " + arg + "; ignoring\n");
      }
    }
  }

  @Override
  protected CompiledMethod compileMethod(NormalMethod method, TypeReference[] params) {
    CompiledMethod cm;
    Callbacks.notifyMethodCompile(method, CompiledMethod.BASELINE);
    cm = BaselineCompiler.compile(method);

    if (VM.BuildForAdaptiveSystem) {
      /* We can't accurately measure compilation time on Host JVM, so just approximate with DNA */
      cm.setCompilationTime((float)CompilerDNA.estimateCompileTime(CompilerDNA.BASELINE, method));
    }
    return cm;
  }
}
