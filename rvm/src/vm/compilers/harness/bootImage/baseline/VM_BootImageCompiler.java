/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Use baseline compiler to build virtual machine boot image.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_BootImageCompiler {
  /** Identity. */
  public static final int COMPILER_TYPE = VM_CompilerInfo.BASELINE;

  /** 
   * Initialize boot image compiler.
   * @param args command line arguments to the bootimage compiler
   */
  static void init(String[] args) { 
    VM_Compiler.init(); 
  }

  /** 
   * Compile a method.
   * @param method the method to compile
   * @return the compiled method
   */
  static VM_CompiledMethod compile(VM_Method method) {
    VM_Callbacks.notifyMethodCompile(method, COMPILER_TYPE);
    return VM_Compiler.compile(method);
  }
  
  /**
   * Create stackframe mapper appropriate for this compiler.
   */
  static VM_GCMapIterator createGCMapIterator(int[] registerLocations) {
    return new VM_BaselineGCMapIterator(registerLocations);
  }
}
