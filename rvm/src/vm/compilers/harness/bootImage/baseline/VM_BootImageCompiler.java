/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;

/**
 * Use baseline compiler to build virtual machine boot image.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_BootImageCompiler {
  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  static void init(String[] args) { 
    VM_BaselineCompiler.initOptions();
    // Process arguments specified by the user.
    for (int i = 0, n = args.length; i < n; i++) {
      String arg = args[i];
      if (!VM_Compiler.options.processAsOption("-X:bc:", arg)) {
	VM.sysWrite("VM_BootImageCompiler(baseline): Unrecognized argument "+arg+"; ignoring\n");
      }
    }
  }

  /** 
   * Compile a method.
   * @param method the method to compile
   * @return the compiled method
   */
  public static VM_CompiledMethod compile(VM_Method method) {
    VM_CompiledMethod cm;
    if (method.isNative()) {
      VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
      cm = VM_JNICompiler.compile(method);
    } else { 
      VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
      cm = VM_BaselineCompiler.compile(method);
    }

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // Must estimate compilation time by using offline ratios.
    // It is tempting to time via System.currentTimeMillis()
    // but 1 millisecond granularity isn't good enough because the 
    // the baseline compiler is just too fast.
    if (!method.isNative()) {
      double compileTime = method.getRawBytecodes().length / com.ibm.JikesRVM.adaptive.VM_CompilerDNA.getBaselineCompilationRate();
      cm.setCompilationTime(compileTime);
    }
    //-#endif
    return cm;
  }
  
  /**
   * Create stackframe mapper appropriate for this compiler.
   */
  public static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_BaselineGCMapIterator(registerLocations);
  }
}
