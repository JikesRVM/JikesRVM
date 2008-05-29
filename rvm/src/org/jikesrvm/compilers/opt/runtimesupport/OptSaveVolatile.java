/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.runtimesupport;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.scheduler.greenthreads.VM_GreenThread;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.SaveVolatile;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Contains routines that must be compiled with special prologues and eplilogues that
 * save/restore all registers (both volatile and non-volatile).
 * TODO: Instead of SaveVolatile, make this class implement
 * DynamicBridge...will allow us to kill support for SaveVolatile!.
 * ISSUE: GCMapping for dynamic bridge assumes that it is being used for
 *        lazy method compilation.  Need to generalize to support
 *        opt's use for other purposes.
 *
 * @see org.jikesrvm.compilers.opt.driver.OptimizingCompiler (hooks to recognize & specially compile this class)
 */
@SaveVolatile
@Uninterruptible
public class OptSaveVolatile {

  /**
   * Handle timer interrupt taken in method prologue.
   * This method is identical to the yieldpointFromPrologue()
   * method used by the baseline compiler, except in the OPT compiler world,
   * we also save the volatile registers.
   */
  @Entrypoint
  public static void yieldpointFromPrologue() {
    Address fp = VM_Magic.getFramePointer();
    VM_GreenThread.yieldpoint(VM_Thread.PROLOGUE, fp);
  }

  /**
   * Handle timer interrupt taken in method epilogue.
   * This method is identical to the yieldpointFromEpilogue()
   * method used by the baseline compiler, except in the OPT compiler world,
   * we also save the volatile registers.
   */
  @Entrypoint
  public static void yieldpointFromEpilogue() {
    Address fp = VM_Magic.getFramePointer();
    VM_GreenThread.yieldpoint(VM_Thread.EPILOGUE, fp);
  }

  /**
   * Handle timer interrupt taken on loop backedge.
   * This method is identical to the yieldpointFromBackedge() method used
   * method used by the baseline compiler, except in the OPT compiler world,
   * we also save the volatile registers.
   */
  @Entrypoint
  public static void yieldpointFromBackedge() {
    Address fp = VM_Magic.getFramePointer();
    VM_GreenThread.yieldpoint(VM_Thread.BACKEDGE, fp);
  }

  /**
   * Handle timer interrupt taken in the prologue of a native method.
   */
  @Entrypoint
  public static void yieldpointFromNativePrologue() {
    // VM.sysWriteln(123);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // Address fp = VM_Magic.getFramePointer();
    // VM_Thread.yieldpoint(VM_Thread.NATIVE_PROLOGUE, fp);
  }

  /**
   * Handle timer interrupt taken in the epilogue of a native method.
   */
  @Entrypoint
  public static void yieldpointFromNativeEpilogue() {
    // VM.sysWriteln(321);
    // VM.sysWriteln(VM_Magic.getFramePointer());
    // VM.sysWriteln(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    // System.gc();
    // VM.sysWriteln("Survived GC");
    // Address fp = VM_Magic.getFramePointer();
    // VM_Thread.yieldpoint(VM_Thread.NATIVE_EPILOGUE, fp);
  }

  /**
   * OSR invalidation being initiated.
   */
  @Entrypoint
  public static void yieldpointFromOsrOpt() {
    Address fp = VM_Magic.getFramePointer();
    VM_Processor.getCurrentProcessor().yieldToOSRRequested = true;
    VM_GreenThread.yieldpoint(VM_Thread.OSROPT, fp);
  }

  /**
   * Wrapper to save/restore volatile registers when a class needs to be
   * dynamically loaded/resolved/etc.
   */
  @Interruptible
  public static void resolve() throws NoClassDefFoundError {
    VM.disableGC();
    // (1) Get the compiled method & compilerInfo for the (opt)
    // compiled method that called resolve
    Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    int cmid = VM_Magic.getCompiledMethodID(fp);
    OptCompiledMethod cm = (OptCompiledMethod) VM_CompiledMethods.getCompiledMethod(cmid);
    // (2) Get the return address
    Address ip = VM_Magic.getReturnAddress(VM_Magic.getFramePointer());
    Offset offset = cm.getInstructionOffset(ip);
    VM.enableGC();
    // (3) Call the routine in OptLinker that does all the real work.
    OptLinker.resolveDynamicLink(cm, offset);
  }
}
