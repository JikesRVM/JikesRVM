/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_GCMapIterator;

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

  static VM_CompiledMethod compile(VM_Method method) {
    return method.isNative() ? jniCompile(method) : baselineCompile(method);
  }
  
  static void detailedCompilationReport(boolean explain) {
  }
  
  public static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_BaselineGCMapIterator(registerLocations);
  }
}
