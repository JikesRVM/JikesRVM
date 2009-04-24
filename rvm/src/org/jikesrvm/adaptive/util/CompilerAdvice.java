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
    String compilerAdviceFileName = Controller.options.COMPILER_ADVICE_FILE_INPUT;
    if (compilerAdviceFileName != null) {
      VM.sysWrite("Compiler advice file name ");
      VM.sysWriteln(compilerAdviceFileName);
      List<CompilerAdviceAttribute> compilerAdviceInfoList =
          CompilerAdviceInfoReader.readCompilerAdviceFile(compilerAdviceFileName);
      // register these sites so that when a compilation is done,
      // these sites use compiler advice
      CompilerAdviceAttribute.registerCompilerAdvice(compilerAdviceInfoList);
    }
    String dynamicCallFileName = Controller.options.DYNAMIC_CALL_FILE_INPUT;
    if (dynamicCallFileName != null) {
      VM.sysWrite("Dynamic call file name ");
      VM.sysWriteln(dynamicCallFileName);
      //List dynamicCallInfoList =
      DynamicCallFileInfoReader.readDynamicCallFile(dynamicCallFileName, false);
      // register these sites so that when a compilation is done,
      // these sites use compiler advice
    }
  }
}




