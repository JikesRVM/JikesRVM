/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Contains routines that must be compiled with special prologues and eplilogues that
 * save/restore all registers (both volatile and non-volatile).
 * TODO: Instead of VM_SaveVolatile, make this class implement
 * VM_DynamicBridge...will allow us to kill support for VM_SaveVolatile!.
 * ISSUE: GCMapping for dynamic bridge assumes that it is being used for
 *        lazy method compilation.  Need to generalize to support 
 *        opt's use for other purposes. 
 * 
 * @see OPT_Compiler (hooks to recognize & specially compile this class)
 * @see special treatment of VM_OptCompilerInfo.saveVolatile
 * 
 * @author Mauricio Serrano
 * @author Dave Grove
 */
class VM_OptSaveVolatile implements VM_SaveVolatile, VM_Uninterruptible {
 
  /**
   * Suspend execution of current thread and place it on tail of system
   * thread's ready queue because of a timer interrupt caught in a 
   * method prologue.
   * This method is identical to the threadSwitchFromPrologue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromPrologue() {
    VM_Thread.threadSwitch(VM_Thread.PROLOGUE);
  }

  /**
   * Suspend execution of current thread and place it on tail of system
   * thread's ready queue because of a timer interrupt caught in a 
   * method epilogue.
   * This method is identical to the threadSwitchFromEpilogue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromEpilogue() {
    VM_Thread.threadSwitch(VM_Thread.EPILOGUE);
  }

  /**
   * Suspend execution of current thread and place it on tail of system
   * thread's ready queue because of a timer interrupt caught on a 
   * loop backedge.
   * This method is identical to the threadSwitchFromBackedge() method used 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromBackedge() {
    VM_Thread.threadSwitch(VM_Thread.BACKEDGE);
  }

  /**
   * Wrapper to save/restore volatile registers when a class needs to be
   * dynamically loaded/resolved/etc.
   */
  public static void OPT_resolve()  throws VM_ResolutionException {
    VM.disableGC();
    // (1) Get the compiled method & compilerInfo for the (opt) 
    // compiled method that called OPT_resolve
    VM_Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    int cmid = VM_Magic.getCompiledMethodID(fp);
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    VM_OptCompilerInfo info = (VM_OptCompilerInfo)cm.getCompilerInfo();
    // (2) Get the return address 
    VM_Address ip = VM_Magic.getReturnAddress(VM_Magic.getFramePointer());
    VM_Address methodStartAddress = VM_Magic.objectAsAddress(cm.getInstructions());
    int offset = ip.diff(methodStartAddress);
    VM.enableGC();
    // (3) Call the routine in VM_OptLinker that does all the real work.
    VM_OptLinker.resolveDynamicLink(info, offset);
  }
}
