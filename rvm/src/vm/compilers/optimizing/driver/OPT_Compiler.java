/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;

import org.vmmagic.pragma.*;

/**
 * <p> The main driver of the OPT_Compiler. 
 * <p> External drivers are responsible for providing the policies; the 
 * role of this class is simply to take a OPT_CompilationPlan
 * and execute it.
 *
 * Currently, this class is invoked from four clients:
 * <ul>
 *  <li> (1) Command line: ExecuteOptCode
 *  <li> (2) BootImageWriting: VM_BootImageCompiler.compile (optimizing version)
 *  <li> (3) RuntimeCompiler: VM_RuntimeCompiler.compile (optimizing version)
 *  <li> (4) AOS: Compilation threads execute controller plans by invoking 
 *      the opt compiler.
 * </ul>
 *
 * <p> Clients are responsible for ensuring that:
 *  <ul>
 *  <li> (1) the VM has been initialized
 *  <li> (2) OPT_Compiler.init has been called before the first opt compilation
 *  </ul>
 *
 * <p> This class is not meant to be instantiated.
 *
 * @author Brian Cooper
 * @author Vassily Litvinov
 * @author Dave Grove
 * @author Michael Hind
 * @author Stephen Fink
 *
 */
public class OPT_Compiler implements VM_Callbacks.StartupMonitor {

  ////////////////////////////////////////////
  // Initialization
  ////////////////////////////////////////////
  /**
   * Prepare compiler for use.
   * @param options options to use for compilations during initialization
   */
  public static void init (OPT_Options options) {
    try {
      if (!(VM.writingBootImage || VM.runningTool || VM.runningVM)) {
        // Caller failed to ensure that the VM was initialized.
        throw new OPT_OptimizingCompilerException("VM not initialized", true);
      }
      // Make a local copy so that some options can be forced off just for the
      // duration of this initialization step.
      options = (OPT_Options)options.clone();
      options.SIMPLE_ESCAPE_IPA = false;

      initializeStatics();
      if (VM.runningVM) {
        // Make sure that VM_OptSaveVolatile.java is opt 
        // compiled (to get special prologues/epilogues)
        // TODO: This could be phased out as the new DynamicBridge 
        // magic comes on line.
        loadSpecialClass("Lcom/ibm/JikesRVM/opt/VM_OptSaveVolatile;", options);

      }
      // want to be notified when VM boot is done and ready to start application
      VM_Callbacks.addStartupMonitor(new OPT_Compiler());
      isInitialized = true;
    } catch (OPT_OptimizingCompilerException e) {
      // failures during initialization can't be ignored
      e.isFatal = true;
      throw e;
    } catch (Throwable e) {
        VM.sysWriteln( e.toString() );
        throw new OPT_OptimizingCompilerException("OPT_Compiler", 
                                                  "untrapped failure during init, "
                                                  + " Converting to OPT_OptimizingCompilerException");
    }
  }

  /*
   * callback when application is about to start.
   */
  public void notifyStartup() {
  //-#if RVM_WITH_OSR
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("OPT_Compiler got notified of app ready to begin");
    }
  //-#endif
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
  public static void setBootOptions(OPT_Options options) {
    // Pick an optimization level
    options.setOptLevel(2); 

    // Only do guarded inlining if we can use code patches.
    // Early speculation with method test/class test can result in
    // bad code that we can't recover from later.
    options.GUARDED_INLINE = options.guardWithCodePatch();

    // Compute summaries of bootimage methods if we haven't encountered them yet.
    // Does not handle unimplemented magics very well; disable until
    // we can get a chance to either implement them on IA32 or fix the 
    // analysis to not be so brittle.
    // options.SIMPLE_ESCAPE_IPA = true;

    // Static inlining controls. 
    // Be slightly more aggressive when building the boot image then we are normally.
    options.IC_MAX_TARGET_SIZE = 5*VM_NormalMethod.CALL_COST;
    options.IC_MAX_INLINE_DEPTH = 6;
    OPT_InlineOracleDictionary.registerDefault(new OPT_StaticInlineOracle());
  }

  /**
   * Load a class which must be compiled by the opt compiler in a special way
   * @param klassName the class to load
   * @param options compiler options for compiling the class
   */
  private static void loadSpecialClass (String klassName, OPT_Options options) {
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
                                                          VM_Atom.findOrCreateAsciiAtom(klassName));
    VM_Class klass = (VM_Class)tRef.peekResolvedType();
    VM_Method[] methods = klass.getDeclaredMethods();
    for (int j = 0; j < methods.length; j++) {
      VM_Method meth = methods[j];
      if (meth.isClassInitializer())
        continue;
      if (!meth.isCompiled() || 
          meth.getCurrentCompiledMethod().getCompilerType() != VM_CompiledMethod.OPT) {
        OPT_CompilationPlan cp = 
          new OPT_CompilationPlan((VM_NormalMethod)meth, 
                                  OPT_OptimizationPlanner.createOptimizationPlan(options), 
                                  null, options);
        meth.replaceCompiledMethod(compile(cp));
      }
    }
  }

  public static void preloadSpecialClass(OPT_Options options) {
    String klassName = "L"+options.PRELOAD_CLASS+";";

    if (options.PRELOAD_AS_BOOT ) {
      setBootOptions(options);
      // Make a local copy so that some options can be altered to mimic options
      // during boot build
      options = (OPT_Options)options.clone();
    }

    try {
      loadSpecialClass(klassName, options);
    } catch (Throwable e) {
      e.printStackTrace();
      VM.sysWrite("Ignoring failure of preloadSpecialClass of "+klassName+"\n");
    }
  }

  /**
   * Call the static init functions for the OPT_Compiler subsystems
   */
  private static void initializeStatics () {
    OPT_InlineOracleDictionary.registerDefault(new OPT_StaticInlineOracle());
    OPT_InvokeeThreadLocalContext.init();
  }

  /**
   * Prevent instantiation by clients
   */
  private OPT_Compiler () {
  }

  /**
   * Has the optimizing compiler been initialized?
   */
  private static boolean isInitialized = false;

  /**
   * Has the optimizing compiler been initialized?
   */
  public static boolean isInitialized () {
    return isInitialized;
  }

  /**
   * Reset the optimizing compiler
   */
  static void reset () {
    isInitialized = false;
  }

  ////////////////////////////////////////////
  // Public interface for compiling a method
  ////////////////////////////////////////////
  /**
   * Invoke the opt compiler to execute a compilation plan.
   * 
   * @param cp the compilation plan to be executed
   * @return the VM_CompiledMethod object that is the result of compilation
   */
  public static VM_CompiledMethod compile (OPT_CompilationPlan cp) {
    VM_NormalMethod method = cp.method;
    OPT_Options options = cp.options;
    checkSupported(method, options);
    try {
      printMethodMessage(method, options);
      OPT_IR ir = cp.execute();
      // if doing analysis only, don't try to return an object
      if (cp.analyzeOnly || cp.irGeneration)
        return null;
      // now that we're done compiling, give the specialization
      // system a chance to eagerly compile any specialized version
      // that are pending.  TODO: use lazy compilation with specialization.
      OPT_SpecializationDatabase.doDeferredSpecializations();
      ir.compiledMethod.compileComplete(ir.MIRInfo.machinecode);
      return ir.compiledMethod;
    } catch (OPT_OptimizingCompilerException e) {
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
  public static void report (String what) {
    VM.sysWrite(what + '\n');
  }

  /**
   * Debugging aid.
   * @param what a string message to print
   * @param time a timestamp to print
   */
  public static void report (String what, long time) {
    VM.sysWrite(what);
    if (what.length() < 8)
      VM.sysWrite('\t');
    if (what.length() < 16)
      VM.sysWrite('\t');
    VM.sysWrite('\t' + time + " ms");
  }

  /**
   * Debugging aid to be called before printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void header (String what, VM_NormalMethod method) {
    System.out.println("********* START OF:  " + what + "   FOR " + method);
  }

  /**
   * Debugging aid to be called after printing the IR
   * @param what a string message to print
   * @param method the method being compiled
   */
  public static void bottom (String what, VM_NormalMethod method) {
    System.out.println("*********   END OF:  " + what + "   FOR " + method);
  }

  /**
   * Print the IR along with a message
   * @param ir
   * @param message
   */
  public static void printInstructions (OPT_IR ir, String message) {
    header(message, ir.method);
    ir.printInstructions();
    bottom(message, ir.method);
  }

  /**
   * Print a message of a method name
   * @param method
   * @param options
   */
  private static void printMethodMessage (VM_NormalMethod method, 
                                          OPT_Options options) {
    if (options.PRINT_METHOD || options.PRINT_INLINE_REPORT)
      VM.sysWrite("-methodOpt "+ method.getDeclaringClass() + ' ' 
                  + method.getName() + ' ' 
                  + method.getDescriptor() + " \n");
  }

  /**
   * Abort a compilation with an error.
   * @param e The exception thrown by a compiler phase
   * @param method The method being compiled
   */
  private static void fail (Throwable e, VM_NormalMethod method) {
    OPT_OptimizingCompilerException optExn = new OPT_OptimizingCompilerException("OPT_Compiler", 
                                              "failure during compilation of", method.toString());
    if (e instanceof OutOfMemoryError) {
        VM.sysWriteln("OPT_Compiler ran out of memory during compilation of ",
                      method.toString());
        optExn.isFatal = false;
    }
    else {
        VM.sysWriteln("OPT_Compiler failure during compilation of ",
                      method.toString());
        e.printStackTrace();
    }
    throw optExn;
  }

  /**
   * Check whether opt compilation of a particular method is supported.
   * If not, throw a non-fatal run-time exception.
   */
  private static void checkSupported (VM_NormalMethod method, OPT_Options options) {
    if (method.getDeclaringClass().isDynamicBridge()) {
      String msg = "Dynamic Bridge register save protocol not implemented";
      throw OPT_MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.getDeclaringClass().isBridgeFromNative()) {
      String msg = "Native Bridge prologue not implemented";
      throw OPT_MagicNotImplementedException.EXPECTED(msg);
    }
    if (method.hasNoOptCompilePragma()) {
      String msg = "Method throws NoOptCompilePragma";
      throw OPT_MagicNotImplementedException.EXPECTED(msg);
    }
    if (options.hasEXCLUDE()) {
      String name = method.getDeclaringClass().toString() + "." + method.getName();
      if (options.fuzzyMatchEXCLUDE(name)) {
        if (!method.getDeclaringClass().isSaveVolatile()) {
          throw new OPT_OptimizingCompilerException("method excluded", false);
        }
      }
    }
  }
}
