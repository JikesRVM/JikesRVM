/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;

/**
 * Use optimizing compiler to build virtual machine boot image.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_BootImageCompiler {

  // If excludePattern is null, all methods are opt-compiled (or attempted).
  // Otherwise, methods that match the pattern are not opt-compiled.
  // In any case, the class VM_OptSaveVolatile is always opt-compiled.
  //
  private static String excludePattern; 
  private static boolean match(VM_Method method) {
    if (excludePattern == null) return true;
    VM_Class cls = method.getDeclaringClass();
    String clsName = cls.toString();
    if (clsName.compareTo("com.ibm.JikesRVM.opt.VM_OptSaveVolatile") == 0) return true;
    String methodName = method.getName().toString();
    String fullName = clsName + "." + methodName;
    return (fullName.indexOf(excludePattern)) < 0;
  }

  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  public static void init(String[] args) {
    try {
      VM_BaselineCompiler.initOptions();
      options = new OPT_Options();
      VM.sysWrite("VM_BootImageCompiler: init (opt compiler)\n");
         
      // Writing a boot image is a little bit special.  We're not really 
      // concerned about compile time, but we do care a lot about the quality
      // and stability of the generated code.  Set the options accordingly.
      OPT_Compiler.setBootOptions(options);

      // Allow further customization by the user.
      for (int i = 0, n = args.length; i < n; i++) {
        String arg = args[i];
        if (!options.processAsOption("-X:bc:", arg)) {
          if (arg.startsWith("exclude=")) 
            excludePattern = arg.substring(8);
          else
            VM.sysWrite("VM_BootImageCompiler: Unrecognized argument "+arg+"; ignoring\n");
        }
      }

      OPT_Compiler.init(options);
      optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);

    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_BootImageCompiler: OPT_Compiler failed during initialization: "+e+"\n";
      if (e.isFatal) {
        // An unexpected error when building the opt boot image should be fatal
        e.printStackTrace();
        System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
      } else {
        VM.sysWrite(msg);
      }
    }
  }


  /** 
   * Compile a method with bytecodes.
   * @param method the method to compile
   * @return the compiled method
   */
  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    if (method.hasNoOptCompilePragma()) {
      return baselineCompile(method);
    } else {
      VM_CompiledMethod cm = null;
      OPT_OptimizingCompilerException escape =  new OPT_OptimizingCompilerException(false);
      try {
        VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.OPT);
        boolean include = match(method);
        if (!include)
          throw escape;
        long start = System.currentTimeMillis();
        OPT_CompilationPlan cp = new OPT_CompilationPlan(method, optimizationPlan, null, options);
        cm = OPT_Compiler.compile(cp);
        if (VM.BuildForAdaptiveSystem) {
          long stop = System.currentTimeMillis();
          long compileTime = stop - start;
          cm.setCompilationTime((float)compileTime);
        }
        return cm;
      } catch (OPT_OptimizingCompilerException e) {
        if (e.isFatal) {
          // An unexpected error when building the opt boot image should be fatal
          e.printStackTrace();
          System.exit(VM.EXIT_STATUS_OPT_COMPILER_FAILED);
        } else {
          boolean printMsg = true;
          if (e instanceof OPT_MagicNotImplementedException) 
            printMsg = !((OPT_MagicNotImplementedException)e).isExpected;
          if (e == escape) 
            printMsg = false;
          if (printMsg) {
            if (e.toString().indexOf("method excluded") >= 0) {
              String msg = "VM_BootImageCompiler: " + method + " excluded from opt-compilation\n"; 
              VM.sysWrite(msg);
            } else {
              String msg = "VM_BootImageCompiler: can't optimize \"" + method + "\" (error was: " + e + ")\n"; 
              VM.sysWrite(msg);
            }
          }
        }
        return baselineCompile(method);
      }
    }
  }

  /** 
   * Compile a native method.
   * @param method the method to compile
   * @return the compiled method
   */
  public static VM_CompiledMethod compile(VM_NativeMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
    return com.ibm.JikesRVM.jni.VM_JNICompiler.compile(method);
  }

  private static VM_CompiledMethod baselineCompile(VM_NormalMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    VM_CompiledMethod cm = VM_BaselineCompiler.compile(method);
    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // Must estimate compilation time by using offline ratios.
    // It is tempting to time via System.currentTimeMillis()
    // but 1 millisecond granularity isn't good enough because the 
    // the baseline compiler is just too fast.
    double compileTime = method.getBytecodeLength() / com.ibm.JikesRVM.adaptive.VM_CompilerDNA.getBaselineCompilationRate();
    cm.setCompilationTime(compileTime);
    //-#endif
    return cm;
  }

  // Cache objects needed to cons up compilation plans
  private static OPT_OptimizationPlanElement[] optimizationPlan;
  private static OPT_Options options;
}
