/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.quick.*;

/**
 * Use quick compiler to build virtual machine boot image.
 * 
 * @author Chris Hoffmann
 */
public class VM_BootImageCompiler {

  // If excludePattern is null, all methods are quick-compiled (or attempted).
  // Otherwise, methods that match the pattern are not quick-compiled.
  //
  private static String excludePattern; 
  private static boolean match(VM_Method method) {
    if (excludePattern == null) return true;
    VM_Class cls = method.getDeclaringClass();
    String clsName = cls.toString();
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
      options = new VM_QuickOptions();
      VM.sysWrite("VM_BootImageCompiler: init (quick compiler)\n");
         
      // Writing a boot image is a little bit special.  We're not really 
      // concerned about compile time, but we do care a lot about the quality
      // and stability of the generated code.  Set the options accordingly.
      VM_QuickCompiler.setBootOptions(options);

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

      VM_QuickCompiler.init(options);


    } catch (VM_QuickCompilerException e) {
      String msg = "VM_BootImageCompiler: VM_QuickCompiler failed during initialization: "+e+"\n";
      if (e.isFatal) {
        // An unexpected error when building the quick boot image should be fatal
        e.printStackTrace();
        System.exit(VM.exitStatusOptCompilerFailed);
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
    VM_CompiledMethod cm = null;
    VM_QuickCompilerException escape =  new VM_QuickCompilerException(false);
    try {
      VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.QUICK);
      boolean include = match(method);
      if (!include)
        throw escape;
      long start = System.currentTimeMillis();
      cm = VM_QuickCompiler.compile(method);
      if (VM.BuildForAdaptiveSystem) {
        long stop = System.currentTimeMillis();
        long compileTime = stop - start;
        cm.setCompilationTime((float)compileTime);
      }
      return cm;
    } catch (VM_QuickCompilerException e) {
      if (e.isFatal) {
        // An unexpected error when building the quick boot image should be fatal
        e.printStackTrace();
        System.exit(VM.exitStatusOptCompilerFailed);
      } else {
        boolean printMsg = false;
        if (e == escape) 
          printMsg = false;
        if (printMsg) {
          if (e.toString().indexOf("method excluded") >= 0) {
            String msg = "VM_BootImageCompiler: " + method + " excluded from quick-compilation\n"; 
            VM.sysWrite(msg);
          } else {
            String msg = "VM_BootImageCompiler: can't quick-compile \"" + method + "\" (error was: " + e + ")\n"; 
            VM.sysWrite(msg);
          }
        }
      }
      return baselineCompile(method);
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
  private static VM_QuickOptions options;
}
