/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

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
 * 
 * @author Mauricio Serrano
 * @author Dave Grove
 */
public class VM_OptSaveVolatile implements VM_SaveVolatile {
 
  /**
   * Suspend execution of current thread and place it on tail of system
   * thread's ready queue because of a timer interrupt caught in a 
   * method prologue.
   * This method is identical to the threadSwitchFromPrologue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromPrologue() throws VM_PragmaUninterruptible {
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
  public static void OPT_threadSwitchFromEpilogue() throws VM_PragmaUninterruptible {
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
  public static void OPT_threadSwitchFromBackedge() throws VM_PragmaUninterruptible {
    VM_Thread.threadSwitch(VM_Thread.BACKEDGE);
  }

  //-#if RVM_WITH_OSR
  public static void OPT_threadSwitchFromOsrOpt() 
    throws VM_PragmaUninterruptible {
    VM_Thread.threadSwitch(VM_Thread.OSROPT);
  }
  //-#endif 

  /**
   * Wrapper to save/restore volatile registers when a class needs to be
   * dynamically loaded/resolved/etc.
   */
  public static void OPT_resolve() throws ClassNotFoundException {
    VM.disableGC();
    // (1) Get the compiled method & compilerInfo for the (opt) 
    // compiled method that called OPT_resolve
    VM_Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    int cmid = VM_Magic.getCompiledMethodID(fp);
    VM_OptCompiledMethod cm = (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);
    // (2) Get the return address 
    VM_Address ip = VM_Magic.getReturnAddress(VM_Magic.getFramePointer());
    VM_Address methodStartAddress = VM_Magic.objectAsAddress(cm.getInstructions());
    int offset = ip.diff(methodStartAddress).toInt();
    VM.enableGC();
    // (3) Call the routine in VM_OptLinker that does all the real work.
    VM_OptLinker.resolveDynamicLink(cm, offset);
  }
}
