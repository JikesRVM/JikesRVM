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
   * This method is identical to the yieldpointFromPrologue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_yieldpointFromPrologue() {
    VM_Thread.yieldpoint(VM_Thread.PROLOGUE);
  }

  /**
   * Handle timer interrupt taken in method epilogue.
   * This method is identical to the yieldpointFromEpilogue() 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_yieldpointFromEpilogue() {
    VM_Thread.yieldpoint(VM_Thread.EPILOGUE);
  }

  /**
   * Handle timer interrupt taken on loop backedge.
   * This method is identical to the yieldpointFromBackedge() method used 
   * method used by the baseline compiler, except in the OPT compiler world, 
   * we also save the volatile registers.
   */         
  public static void OPT_yieldpointFromBackedge() {
    VM_Thread.yieldpoint(VM_Thread.BACKEDGE);
  }

  /**
   * Handle timer interrupt taken in the prologue of a native method.
   */         
  public static void OPT_yieldpointFromNativePrologue() {
    // VM.sysWriteln(123);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // VM_Thread.yieldpoint(VM_Thread.NATIVE_PROLOGUE);
  }

  /**
   * Handle timer interrupt taken in the epilogue of a native method.
   */         
  public static void OPT_yieldpointFromNativeEpilogue() {
    // VM.sysWriteln(321);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // VM_Thread.yieldpoint(VM_Thread.NATIVE_EPILOGUE);
  }

  //-#if RVM_WITH_OSR
  public static void OPT_yieldpointFromOsrOpt() 
    throws UninterruptiblePragma {
    VM_Processor.getCurrentProcessor().yieldToOSRRequested = true;
    VM_Thread.yieldpoint(VM_Thread.OSROPT);
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
