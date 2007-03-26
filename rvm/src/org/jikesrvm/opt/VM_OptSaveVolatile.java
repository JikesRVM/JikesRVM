/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt;

import org.jikesrvm.*;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.VM_Processor;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Contains routines that must be compiled with special prologues and eplilogues that
 * save/restore all registers (both volatile and non-volatile).
 * TODO: Instead of SaveVolatile, make this class implement
 * DynamicBridge...will allow us to kill support for SaveVolatile!.
 * ISSUE: GCMapping for dynamic bridge assumes that it is being used for
 *        lazy method compilation.  Need to generalize to support 
 *        opt's use for other purposes. 
 * 
 * @see OPT_Compiler (hooks to recognize & specially compile this class)
 * 
 * @author Mauricio Serrano
 * @author Dave Grove
 */
@SaveVolatile
@Uninterruptible
public class VM_OptSaveVolatile {

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

  /**
   * OSR invalidation being initiated.
   */
  @Uninterruptible
  public static void OPT_yieldpointFromOsrOpt() { 
    VM_Processor.getCurrentProcessor().yieldToOSRRequested = true;
    VM_Thread.yieldpoint(VM_Thread.OSROPT);
  }

  /**
   * Wrapper to save/restore volatile registers when a class needs to be
   * dynamically loaded/resolved/etc.
   */
  @Interruptible
  public static void OPT_resolve() throws NoClassDefFoundError { 
    VM.disableGC();
    // (1) Get the compiled method & compilerInfo for the (opt) 
    // compiled method that called OPT_resolve
    Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    int cmid = VM_Magic.getCompiledMethodID(fp);
    VM_OptCompiledMethod cm = (VM_OptCompiledMethod)VM_CompiledMethods.getCompiledMethod(cmid);
    // (2) Get the return address 
    Address ip = VM_Magic.getReturnAddress(VM_Magic.getFramePointer());
    Offset offset = cm.getInstructionOffset(ip);
    VM.enableGC();
    // (3) Call the routine in VM_OptLinker that does all the real work.
    VM_OptLinker.resolveDynamicLink(cm, offset);
  }
}
