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
package org.jikesrvm.adaptive.util;

import java.util.List;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;

/**
 * Utilities for providing compiler advice.  Advice files provided
 * at run time allow compilers to be specified for particular methods
 * <p>
 * <i>Run time</i> advice is given by identifying an advice file
 * through a command line option:
 * <code>-X:aos:cafi=path-to-advice-file</code>.
 *
 *
 * @see CompilerAdviceAttribute
 * @see CompilerAdviceInfoReader
 * @see org.jikesrvm.compilers.common.RuntimeCompiler
 */
public class CompilerAdvice {

  /**
   *  Read a list of compiler advice annotations from a file
   */
  public static void postBoot() {
    CompilerAdviceAttribute.postBoot();
  }
  public static void readCompilerAdvice() {
    String compilerAdviceFileName = Controller.options.COMPILER_ADVICE_FILE_INPUT;
    if (compilerAdviceFileName != null) {
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("Loading compiler advice file: ", compilerAdviceFileName, " "); }
      List<CompilerAdviceAttribute> compilerAdviceInfoList =
          CompilerAdviceInfoReader.readCompilerAdviceFile(compilerAdviceFileName);
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWriteln(); }
      // register these sites so that when a compilation is done,
      // these sites use compiler advice
      CompilerAdviceAttribute.registerCompilerAdvice(compilerAdviceInfoList);
    }
    String dynamicCallFileName = Controller.options.DYNAMIC_CALL_FILE_INPUT;
    if (dynamicCallFileName != null) {
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWrite("Loading dynamic call file: ", dynamicCallFileName, " "); }
      //List dynamicCallInfoList =
      DynamicCallFileInfoReader.readDynamicCallFile(dynamicCallFileName, false);
      if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) { VM.sysWriteln(); }
     // register these sites so that when a compilation is done,
      // these sites use compiler advice
    }
  }
}




