/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */
//$Id$

package com.ibm.JikesRVM;

import java.util.List;
import com.ibm.JikesRVM.adaptive.VM_Controller;

/**
 * Utilities for providing compiler advice.  Advice files provided
 * at run time allow compilers to be specified for particular methods
 * <p>
 * <i>Run time</i> advice is given by identifying an advice file
 * through a command line option:
 * <code>-X:aos:cafi=path-to-advice-file</code>.
 *
 * @author Xianglong Huang
 * @author <a href="http://www.cs.utexas.edu/users/xlhuang">Xianglong Huang</a>
 * @version $Revision$
 * @date    $Date$ 
 *
 * @see VM_CompilerAdviceAttribute
 * @see VM_CompilerAdviceInfoReader
 * @see VM_RuntimeCompiler 
 */
class VM_CompilerAdvice {
  
  /**
   *  Read a list of compiler advice annotations from a file
   */
  public static void postBoot() {
    VM_CompilerAdviceAttribute.postBoot();
    String compilerAdviceFileName = VM_Controller.options.COMPILER_ADVICE_FILE_INPUT;
    if (compilerAdviceFileName != null) {
      VM.sysWrite("Compiler advice file name ");
      VM.sysWriteln(compilerAdviceFileName);
      List compilerAdviceInfoList = 
        VM_CompilerAdviceInfoReader.readCompilerAdviceFile(
          compilerAdviceFileName);
      // register these sites so that when a compilation is done,
      // these sites use compiler advice
      VM_CompilerAdviceAttribute.registerCompilerAdvice(compilerAdviceInfoList);
    }
    String dynamicCallFileName = VM_Controller.options.DYNAMIC_CALL_FILE_INPUT; 
    if (dynamicCallFileName != null) {
      VM.sysWrite("Dynamic call file name ");
      VM.sysWriteln(dynamicCallFileName);
      //List dynamicCallInfoList = 
        VM_DynamicCallFileInfoReader.readDynamicCallFile(
          dynamicCallFileName, false);
      // register these sites so that when a compilation is done,
      // these sites use compiler advice
      //VM_DynamicCallAttribute.registerDynamicCall(dynamicCallInfoList);
    }
  }

}




