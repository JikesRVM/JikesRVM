/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.classloader.VM_NativeMethod;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * Use baseline compiler to compile methods at runtime. 
 *
 * @author Stephen Fink
 * @author David Grove
 */
public class VM_RuntimeCompiler extends VM_RuntimeCompilerInfrastructure {
  static void boot() {
    if (VM.MeasureCompilation) {
      VM_Callbacks.addExitMonitor(new VM_RuntimeCompilerInfrastructure());
    }
  }

  static void processCommandLineArg(String arg) {
    VM_BaselineCompiler.processCommandLineArg("-X:irc",arg);
  }

  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    return baselineCompile(method);
  }
  
  public static VM_CompiledMethod compile(VM_NativeMethod method) {
    return jniCompile(method);
  }
  
  public static void detailedCompilationReport(boolean explain) {
  }
  
  public static VM_GCMapIterator createGCMapIterator(VM_WordArray registerLocations) {
    return new VM_BaselineGCMapIterator(registerLocations);
  }
}
