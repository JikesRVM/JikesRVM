/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import java.util.List;
import java.util.Iterator;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.OPT_CompilationPlan;
/**
 * Utilities for providing compiler advice.  Advice files provided
 * at run time allow compilers to be specified for particular methods
 * <p>
 * <i>Run time</i> advice is given by identifying an advice file
 * through a command line option:
 * <code>-X:aos:compiler_advice_file=path-to-advice-file</code>.
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
public class VM_PreCompile implements VM_Callbacks.StartupMonitor {
  
  public static void init() {
    VM_Callbacks.addStartupMonitor(new VM_PreCompile());
  }

  public void notifyStartup() {
    if (VM_Controller.options.ENABLE_PRECOMPILE) {
      VM.sysWrite("Start precompiling");
      // precompile the methods
      compileAllMethods();
      VM.sysWrite("Finish precompiling");
    } 
  }

  /**
   * Compile all methods in the advice file
   *
   */
  public static void compileAllMethods() {
    //Collection allMethodsSet = attribMap.values();
    Iterator it = VM_CompilerAdviceAttribute.getEntries();
    VM.sysWriteln("Start precompile");
    while (it.hasNext()) {
      //while (allMethods.hasNext()) {
      //VM.sysWriteln("checking one");
      VM_CompilerAdviceAttribute value =
        (VM_CompilerAdviceAttribute)it.next();

      VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_ClassLoader.getApplicationClassLoader(), value.getClassName());
      VM_Class cls = (VM_Class)tRef.peekResolvedType();
      if (cls == null) {
        try {
          cls = tRef.resolve().asClass();
          cls.resolve();
          cls.instantiate();
          cls.initialize();
        } catch (NoClassDefFoundError cnf) {
          VM.sysWriteln("Bad entry in the advice file");
        }
      }
      
      if (cls != null) {
        // Find the method
        VM_Method method = cls.findDeclaredMethod(value.getMethodName(),value.getMethodSig());
        
        // If found, compile it
        if ((method != null) &&
            ! method.hasNoOptCompilePragma() &&
            (method instanceof com.ibm.JikesRVM.classloader.VM_NormalMethod)) {
          // if user's requirement is higher than advice
          if ((VM_RuntimeCompiler.options.getOptLevel() > value.getOptLevel())
              || (VM_Controller.options.MAX_OPT_LEVEL < value.getOptLevel())) {
            ((VM_NormalMethod)method).compile();
          } else {
            // otherwise, follow the advice...
            // VM.sysWrite("Compiler advice for ");
            // VM.sysWriteln(value.methodName);
            OPT_CompilationPlan compPlan;
            if (VM_Controller.options.counters()) {
              // for invocation counter, we only use one optimization level
              compPlan = VM_InvocationCounts.createCompilationPlan((VM_NormalMethod)method);
              if (VM.LogAOSEvents) VM_AOSLogging.recompilationStarted(compPlan); 
              VM_RuntimeCompiler.recompileWithOpt(compPlan);
              if (VM.LogAOSEvents) VM_AOSLogging.recompilationCompleted(compPlan); 
            } else if (VM_Controller.options.sampling()) {
              // Create our set of standard optimization plans.
              compPlan = VM_Controller.recompilationStrategy.createCompilationPlan((VM_NormalMethod)method, value.getOptLevel(), null);
              if (VM.LogAOSEvents) VM_AOSLogging.recompilationStarted(compPlan); 
              VM_RuntimeCompiler.recompileWithOpt(compPlan);
              if (VM.LogAOSEvents) VM_AOSLogging.recompilationCompleted(compPlan); 
            } else {
              VM.sysWriteln("Compiler advice file is not followed  ");
              ((VM_NormalMethod)method).compile();
            }
          }
        }
      }
    }
    if (VM.LogAOSEvents) VM_AOSLogging.compileAllMethodsCompleted(); 

  }


}




