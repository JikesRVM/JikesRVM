/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
public class VM_OptSaveVolatile implements VM_SaveVolatile,
                                           Uninterruptible {
 
  /**
   * Handle timer interrupt taken in method prologue.
   * This method is identical to the threadSwitchFromPrologue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromPrologue() {
    VM_Thread.threadSwitch(VM_Thread.PROLOGUE);
  }

  /**
   * Handle timer interrupt taken in method epilogue.
   * This method is identical to the threadSwitchFromEpilogue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromEpilogue() {
    VM_Thread.threadSwitch(VM_Thread.EPILOGUE);
  }

  /**
   * Handle timer interrupt taken on loop backedge.
   * This method is identical to the threadSwitchFromBackedge() method used 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_threadSwitchFromBackedge() {
    VM_Thread.threadSwitch(VM_Thread.BACKEDGE);
  }

  /**
   * Handle timer interrupt taken in the prologue of a native method.
   */         
  public static void OPT_threadSwitchFromNativePrologue() {
    // VM.sysWriteln(123);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // VM_Thread.threadSwitch(VM_Thread.NATIVE_PROLOGUE);
  }

  /**
   * Handle timer interrupt taken in the epilogue of a native method.
   */         
  public static void OPT_threadSwitchFromNativeEpilogue() {
    // VM.sysWriteln(321);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // VM_Thread.threadSwitch(VM_Thread.NATIVE_EPILOGUE);
  }

  //-#if RVM_WITH_OSR
  public static void OPT_threadSwitchFromOsrOpt() 
    throws UninterruptiblePragma {
    VM_Thread.threadSwitch(VM_Thread.OSROPT);
  }
  //-#endif 

  /**
   * Wrapper to save/restore volatile registers when a class needs to be
   * dynamically loaded/resolved/etc.
   */
  public static void OPT_resolve() throws NoClassDefFoundError,
                                          InterruptiblePragma {
    VM.disableGC();
    // (1) Get the compiled method & compilerInfo for the (opt) 
    // compiled method that called OPT_resolve
    Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    int cmid = VM_Magic.getCompiledMethodID(fp);
    VM_OptCompiledMethod cm = (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);
    // (2) Get the return address 
    Address ip = VM_Magic.getReturnAddress(VM_Magic.getFramePointer());
    int offset = cm.getInstructionOffset(ip);
    VM.enableGC();
    // (3) Call the routine in VM_OptLinker that does all the real work.
    VM_OptLinker.resolveDynamicLink(cm, offset);
  }
}
