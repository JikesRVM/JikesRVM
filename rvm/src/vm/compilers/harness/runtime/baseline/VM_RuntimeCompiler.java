/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Use baseline compiler to compile methods at runtime. 
 *
 * @author Stephen Fink
 * @author David Grove
 */
class VM_RuntimeCompiler extends VM_RuntimeCompilerInfrastructure {
  public static final int COMPILER_TYPE = VM_CompiledMethod.BASELINE;

  static void boot() {
  }

  static void initializeMeasureCompilation() {
    VM_Callbacks.addExitMonitor(new VM_RuntimeCompilerInfrastructure());
  }

  static void processCommandLineArg(String arg) {
    VM_BaselineCompiler.processCommandLineArg("-X:irc",arg);
  }

  static VM_CompiledMethod compile(VM_Method method) {
    VM_Callbacks.notifyMethodCompile(method, COMPILER_TYPE);
    return baselineCompile(method);
  }
  
  static void detailedCompilationReport(boolean explain) {
  }
  
  static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_BaselineGCMapIterator(registerLocations);
  }
}
