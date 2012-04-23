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
package org.jikesrvm.compilers.opt.driver;

import org.jikesrvm.Callbacks;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.MagicNotImplementedException;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.specialization.InvokeeThreadLocalContext;
import org.jikesrvm.compilers.opt.specialization.SpecializationDatabase;

/**
 * <p> The main driver of the Compiler.
 * <p> External drivers are responsible for providing the policies; the
 * role of this class is simply to take a CompilationPlan
 * and execute it.
 *
 * Currently, this class is invoked from four clients:
 * <ul>
 *  <li> (1) Command line: ExecuteOptCode
 *  <li> (2) BootImageWriting: BootImageCompiler.compile (optimizing version)
 *  <li> (3) RuntimeCompiler: RuntimeCompiler.compile (optimizing version)
 *  <li> (4) AOS: Compilation threads execute controller plans by invoking
 *      the opt compiler.
 * </ul>
 *
 * <p> Clients are responsible for ensuring that:
 *  <ul>
 *  <li> (1) the VM has been initialized
 *  <li> (2) Compiler.init has been called before the first opt compilation
 *  </ul>
 *
 * <p> This class is not meant to be instantiated.
 */
public final class OptimizingCompiler implements Callbacks.StartupMonitor {

  ////////////////////////////////////////////
  // Initialization
  ////////////////////////////////////////////
  /**
   * Prepare compiler for use.
   * @param options options to use for compilations during initialization
   */
  public static void init(OptOptions options) {
    try {
      if (!(VM.writingBootImage || VM.runningTool || VM.runningVM)) {
        // Caller failed to ensure that the VM was initialized.
        throw new OptimizingCompilerException("VM not initialized", true);
      }
      // Make a local copy so that some options can be forced off just for the
      // duration of this initialization step.
      options = options.dup();
      options.ESCAPE_SIMPLE_IPA = false;

      initializeStatics();

      // want to be notified when VM boot is done and ready to start application
      Callbacks.addStartupMonitor(new OptimizingCompiler());
      isInitialized = true;
    } catch (OptimizingCompilerException e) {
      // failures during initialization can't be ignored
      e.isFatal = true;
      throw e;
    } catch (Throwable e) {
      VM.sysWriteln(e.toString());
      throw new OptimizingCompilerException("Compiler",
                                                "untrapped failure during init, " +
                                                " Converting to OptimizingCompilerException");
    }
  }

  /*
   * callback when application is about to start.
   */
  @Override
  public void notifyStartup() {
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("Compiler got notified of app ready to begin");
    }
    setAppStarted();
  }

  /**
   * indicate when the application has started
   */
  private static boolean appStarted = false;

  public static synchronized boolean getAppStarted() { return appStarted; }

  public static synchronized void setAppStarted() { appStarted = true; }

  /**
   * Set up option used while compiling the boot image
   * @param options the options to set
   */
  public static void setBootOptions(OptOptions options) {
    // Only do guarded inlining if we can use code patches.
    // Early speculation with method test/class test can result in
    // bad code that we can't recover from later.
    options.INLINE_GUARDED = options.guardWithCodePatch();

    // Compute summaries of bootimage methods if we haven't encountered them yet.
    // Does not handle unimplemented magics very well; disable until
    // we can get a chance to either implement them on IA32 or fix the
    // analysis to not be so brittle.
    // options.SIMPLE_ESCAPE_IPA = true;
  }

  /**
   * Call the static init functions for the Compiler subsystems
   */
  private static void initializeStatics() {
    InvokeeThreadLocalContext.init();
  }

  /**
   * Prevent instantiation by clients
   */
  private OptimizingCompiler() {
  }

  /**
   * Has the optimizing compiler been initialized?
   */
  private static boolean isInitialized = false;

  /**
   * Has the optimizing compiler been initialized?
   */
  public static boolean isInitialized() {
    return isInitialized;
  }

  /**
   * Reset the optimizing compiler
   */
  static void reset() {
    isInitialized = false;
  }

  ////////////////////////////////////////////
  // Public interface for compiling a method
  ////////////////////////////////////////////
  /**
   * Invoke the opt compiler to execute a compilation plan.
   *
   * @param cp the compilation plan to be executed
   * @return the CompiledMethod object that is the result of compilation
   */
  public static CompiledMethod compile(CompilationPlan cp) {
    NormalMethod method = cp.method;
    OptOptions options = cp.options;
    checkSupported(method, options);
    try {
      printMethodMessage(method, options);
      IR ir = cp.execute();
      // if doing analysis only, don't try to return an object
      if (cp.analyzeOnly || cp.irGeneration) {
        return null;
      }
      // now that we're done compiling, give the specialization
      // system a chance to eagerly compile any specialized version
      // that are pending.  TODO: use lazy compilation with specialization.
      SpecializationDatabase.doDeferredSpecializations();
      ir.compiledMethod.compileComplete(ir.MIRInfo.machinecode);
      return ir.compiledMethod;
    } catch (OptimizingCompilerException e) {
      throw e;
    } catch (Throwable e) {
      fail(e, method);
      return null;
    }
  }

  /**
   * Debugging aid.
   * @param what a string message to print
   */
  public static void report(String what) {
    VM.sysWrite(what + '\n');
  }

  /**
   * Debugging aid.
   * @param what a string message to print
   * @param time a timestamp to print
   */
  public static void report(String what, long time) {
    VM.sysWrite(what);
    if (what.length() < 8) {
      VM.sysWrite('\t');
    }
    if (what.length() < 16) {
      VM.sysWrite('\t');
    }
    VM.sysWrite('\t' + time + " ms");
  }

  /**
   * Debugging aid to be called before printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void header(String what, NormalMethod method) {
    System.out.println("********* START OF:  " + what + "   FOR " + method);
  }

  /**
   * Debugging aid to be called after printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void bottom(String what, NormalMethod method) {
    System.out.println("*********   END OF:  " + what + "   FOR " + method);
  }

  /**
   * Print the IR along with a message
   * @param ir
   * @param message
   */
  public static void printInstructions(IR ir, String message) {
    header(message, ir.method);
    ir.printInstructions();
    bottom(message, ir.method);
  }

  /**
   * Print a message of a method name
   * @param method
   * @param options
   */
  private static void printMethodMessage(NormalMethod method, OptOptions options) {
    if (options.PRINT_METHOD || options.PRINT_INLINE_REPORT) {
      VM.sysWrite("-methodOpt " +
                  method.getDeclaringClass() +
                  ' ' +
                  method.getName() +
                  ' ' +
                  method.getDescriptor() +
                  " \n");
    }
  }

  /**
   * Abort a compilation with an error.
   * @param e The exception thrown by a compiler phase
   * @param method The method being compiled
   */
  private static void fail(Throwable e, NormalMethod method) {
    OptimizingCompilerException optExn =
        new OptimizingCompilerException("Compiler", "failure during compilation of", method.toString());
    if (e instanceof OutOfMemoryError) {
      VM.sysWriteln("Compiler ran out of memory during compilation of ", method.toString());
      optExn.isFatal = false;
    } else {
      VM.sysWriteln("Compiler failure during compilation of ", method.toString());
      e.printStackTrace();
    }
    throw optExn;
  }

  /**
   * Check whether opt compilation of a particular method is supported.
   * If not, throw a non-fatal run-time exception.
   */
  private static void checkSupported(NormalMethod method, OptOptions options) {
    if (method.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      String msg = "Dynamic Bridge register save protocol not implemented";
      throw MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      String msg = "Native Bridge prologue not implemented";
      throw MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.hasNoOptCompileAnnotation()) {
      String msg = "Method throws NoOptCompilePragma";
      throw MagicNotImplementedException.EXPECTED(msg);
    }
    if (options.hasDRIVER_EXCLUDE()) {
      String name = method.getDeclaringClass().toString() + "." + method.getName();
      if (options.fuzzyMatchDRIVER_EXCLUDE(name)) {
        if (!method.getDeclaringClass().hasSaveVolatileAnnotation()) {
          throw new OptimizingCompilerException("method excluded", false);
        }
      }
    }
  }
}
