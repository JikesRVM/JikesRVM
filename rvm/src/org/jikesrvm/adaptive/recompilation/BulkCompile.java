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
import org.jikesrvm.adaptive.util.CompilerAdvice;
import org.jikesrvm.adaptive.util.CompilerAdviceAttribute;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;

/**
 * Utilities for providing compiler advice.  Advice files provided
 * at run time allow compilers to be specified for particular methods
 * <p>
 * <i>Run time</i> advice is given by identifying an advice file
 * through a command line option:
 * <code>-X:aos:cafi=path-to-advice-file</code>.
 * This file specifies which methods should be optimized, and at
 * what level.
 *
 * Optionally, a dynamic call graph and edge counts may also
 * be provided in advice files, at the command line.
 * <code>-X:aos:dcfi=path-to-dynamic-call-graph-file</code>.
 * <code>-X:vm:edgeCounterFile=path-to-edge-count-file</code>.
 * These provide synthetic profile data to the compiler that would
 * otherwise be gathered by the AOS at run time.  These are therefore
 * strictly an optimization, so they are options.
 *
 * @see org.jikesrvm.adaptive.util.CompilerAdviceAttribute
 * @see org.jikesrvm.adaptive.util.CompilerAdviceInfoReader
 * @see org.jikesrvm.compilers.common.RuntimeCompiler
 */
public class BulkCompile implements Callbacks.StartupMonitor {

  public static void init() {
    Callbacks.addStartupMonitor(new BulkCompile());
  }

  @Override
  public void notifyStartup() {
    if (Controller.options.ENABLE_PRECOMPILE) {
      compileAllMethods();
    }
  }

  /**
   * Compile all methods nominated in the compiler advice,
   * which should have been provided in a .ca advice file.
   *
   * This method will be called at boot time (via notifyStartup())
   * if ENABLE_PRECOMPILE is true.  For replay compilation, this
   * method needs to be called explicitly from within the application
   * or benchmark harness. Typical usage in a benchmarking context
   * would be to call this method at the end of the first iteration
   * of the benchmark so that all/most classes were loaded, and
   * compilation could occur prior to the second iteration.
   */
  public static void compileAllMethods() {
    if (!(Controller.options.ENABLE_BULK_COMPILE || Controller.options.ENABLE_PRECOMPILE)) {
      /* should not be here */
      VM.sysFail("Attempt to perform bulk compilation without setting either -X:aos:enable_bulk_compile=true or -X:aos:enable_precompile=true");
    }

    EdgeCounts.loadCountsFromFileIfAvailable(VM.EdgeCounterFile);
    CompilerAdvice.readCompilerAdvice();
    if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1)
      VM.sysWriteln(Controller.options.ENABLE_PRECOMPILE ? "Start precompile" : "Start bulk compile");

    for (CompilerAdviceAttribute value : CompilerAdviceAttribute.values()) {
      if (value.getOptLevel() == -1) {
        if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
          VM.sysWrite("Skipping base method: "); VM.sysWriteln(value.toString());
        } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
          VM.sysWrite(".");
        }
        continue;
      }

      ClassLoader cl = RVMClassLoader.findWorkableClassloader(value.getClassName());
      if (cl == null)
        continue;

      TypeReference tRef = TypeReference.findOrCreate(cl, value.getClassName());
      RVMClass cls = (RVMClass) tRef.peekType();

      if (cls != null) {
        // Ensure the class is properly loaded
        if (!cls.isInstantiated()) {
          if (!cls.isResolved()) {
            if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
              VM.sysWriteln("Resolving class: ", cls.toString());
            } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
              VM.sysWrite("R");
            }
            cls.resolve();
          }
          if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
            VM.sysWriteln("Instantiating class: ", cls.toString());
          } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
            VM.sysWrite("I");
          }
          cls.instantiate();
        }

        // Find the method
        RVMMethod method = cls.findDeclaredMethod(value.getMethodName(), value.getMethodSig());


        // If found, compile it
        if ((method != null) &&
            !method.hasNoOptCompileAnnotation() &&
            (method instanceof org.jikesrvm.classloader.NormalMethod)) {
          // if user's requirement is higher than advice
          if (value.getOptLevel() > Controller.options.DERIVED_MAX_OPT_LEVEL) {
            if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
              VM.sysWrite("Replay advice overriden by default opt levels.  Wanted "); VM.sysWrite(value.getOptLevel()); VM.sysWrite(", but Controller.options.DERIVED_MAX_OPT_LEVEL: ");
              VM.sysWrite(Controller.options.DERIVED_MAX_OPT_LEVEL); VM.sysWrite(" "); VM.sysWriteln(value.toString());
            } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
              VM.sysWrite(value.getOptLevel(), "!");
            }
            method.compile();
          } else {
            CompilationPlan compPlan;
            if (Controller.options.counters()) {
              // for invocation counter, we only use one optimization level
              compPlan = InvocationCounts.createCompilationPlan((NormalMethod) method);
              AOSLogging.logger.recompilationStarted(compPlan);
              if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) { VM.sysWrite("Bulk compiling for counters "); VM.sysWriteln(value.toString()); }
              RuntimeCompiler.recompileWithOpt(compPlan);
              AOSLogging.logger.recompilationCompleted(compPlan);
            } else if (Controller.options.sampling()) {
              // Create our set of standard optimization plans.
              compPlan = Controller.recompilationStrategy.createCompilationPlan((NormalMethod) method, value.getOptLevel(), null);
              if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) { VM.sysWrite("Bulk compiling for sampling "); VM.sysWriteln(value.toString()); }
              if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) { VM.sysWrite(value.getOptLevel()); }
              AOSLogging.logger.recompilationStarted(compPlan);
              RuntimeCompiler.recompileWithOpt(compPlan);
              AOSLogging.logger.recompilationCompleted(compPlan);
            } else {
              if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) { VM.sysWrite("Compiler advice file overridden "); VM.sysWriteln(value.toString()); }
              method.compile();
            }
          }
        } else {
          if (Controller.options.BULK_COMPILATION_VERBOSITY > 1) {
            VM.sysWrite("Replay failed for "); VM.sysWrite(value.toString()); VM.sysWrite(" "); VM.sysWriteln(cl.toString());
          } else if (Controller.options.BULK_COMPILATION_VERBOSITY == 1) {
            VM.sysWrite("*");
          }
        }
      }
    }
    AOSLogging.logger.compileAllMethodsCompleted();
    if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) VM.sysWriteln();
    if (Controller.options.BULK_COMPILATION_VERBOSITY >= 1) VM.sysWriteln("Recompilation complete");
  }
}
