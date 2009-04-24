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
package org.jikesrvm.adaptive.recompilation;

import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.adaptive.util.CompilerAdviceAttribute;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;

/**
 * Utilities for providing compiler advice.  Advice files provided
 * at run time allow compilers to be specified for particular methods
 * <p>
 * <i>Run time</i> advice is given by identifying an advice file
 * through a command line option:
 * <code>-X:aos:compiler_advice_file=path-to-advice-file</code>.
 *
 *
 * @see org.jikesrvm.adaptive.util.CompilerAdviceAttribute
 * @see org.jikesrvm.adaptive.util.CompilerAdviceInfoReader
 * @see org.jikesrvm.compilers.common.RuntimeCompiler
 */
public class PreCompile implements Callbacks.StartupMonitor {

  public static void init() {
    Callbacks.addStartupMonitor(new PreCompile());
  }

  public void notifyStartup() {
    if (Controller.options.ENABLE_PRECOMPILE) {
      VM.sysWrite("Start precompiling");
      // precompile the methods
      compileAllMethods();
      VM.sysWrite("Finish precompiling");
    }
  }

  /**
   * Compile all methods in the advice file
   */
  public static void compileAllMethods() {
    //Collection allMethodsSet = attribMap.values();
    VM.sysWriteln("Start precompile");
    for (CompilerAdviceAttribute value : CompilerAdviceAttribute.values()) {
      //while (allMethods.hasNext()) {
      //VM.sysWriteln("checking one");

      TypeReference tRef =
          TypeReference.findOrCreate(RVMClassLoader.getApplicationClassLoader(), value.getClassName());
      RVMClass cls = (RVMClass) tRef.peekType();
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
        RVMMethod method = cls.findDeclaredMethod(value.getMethodName(), value.getMethodSig());

        // If found, compile it
        if ((method != null) &&
            !method.hasNoOptCompileAnnotation() &&
            (method instanceof org.jikesrvm.classloader.NormalMethod)) {
          // if user's requirement is higher than advice
          if ((((org.jikesrvm.compilers.opt.OptOptions) RuntimeCompiler.options).getOptLevel() >
               value.getOptLevel()) || (Controller.options.DERIVED_MAX_OPT_LEVEL < value.getOptLevel())) {
            method.compile();
          } else {
            // otherwise, follow the advice...
            // VM.sysWrite("Compiler advice for ");
            // VM.sysWriteln(value.methodName);
            CompilationPlan compPlan;
            if (Controller.options.counters()) {
              // for invocation counter, we only use one optimization level
              compPlan = InvocationCounts.createCompilationPlan((NormalMethod) method);
              AOSLogging.logger.recompilationStarted(compPlan);
              RuntimeCompiler.recompileWithOpt(compPlan);
              AOSLogging.logger.recompilationCompleted(compPlan);
            } else if (Controller.options.sampling()) {
              // Create our set of standard optimization plans.
              compPlan =
                  Controller.recompilationStrategy.createCompilationPlan((NormalMethod) method,
                                                                            value.getOptLevel(),
                                                                            null);
              AOSLogging.logger.recompilationStarted(compPlan);
              RuntimeCompiler.recompileWithOpt(compPlan);
              AOSLogging.logger.recompilationCompleted(compPlan);
            } else {
              VM.sysWriteln("Compiler advice file is not followed  ");
              method.compile();
            }
          }
        }
      }
    }
    AOSLogging.logger.compileAllMethodsCompleted();
  }
}
