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
  public static final int COMPILER_TYPE = VM_CompilerInfo.BASELINE;

  static void boot() {
  }

  static void initializeMeasureCompilation() {
    VM_Callbacks.addExitMonitor(new VM_RuntimeCompilerInfrastructure());
  }

  static void processCommandLineArg(String arg) {
    if (VM_Compiler.setUpOptions != null) {
      if (VM_Compiler.setUpOptions.processAsOption("-X:irc", arg)) {
	return;
      } else {
	VM.sysWrite("VM_RuntimeCompiler(baseline): Ignoring unrecognized argument \""+ arg + "\"\n");
      }
    } else {
	VM.sysWrite("VM_RuntimeCompiler(baseline): Compiler setUpOptions not enabled; Ignoring argument \""+ arg + "\"\n");
    }
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
