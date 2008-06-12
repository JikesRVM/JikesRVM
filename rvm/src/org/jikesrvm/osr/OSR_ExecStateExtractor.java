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
package org.jikesrvm.osr;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.VM_PrintContainer;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A OSR_ExecStateExtractor extracts a runtime state (VM scope descriptor)
 * of a method activation. The implementation depends on compilers and
 * hardware architectures
 * @see org.jikesrvm.ArchitectureSpecific.OSR_BaselineExecStateExtractor
 * @see org.jikesrvm.ArchitectureSpecific.OSR_OptExecStateExtractor
 *
 * It returns a compiler and architecture neutered runtime state
 * OSR_ExecutionState.
 */

public abstract class OSR_ExecStateExtractor implements VM_Constants {
  /**
   * Returns a VM scope descriptor (OSR_ExecutionState) for a compiled method
   * on the top of a thread stack, (or a list of descriptors for an inlined
   * method).
   *
   * @param thread a suspended RVM thread
   * @param tsFromFPoff the frame pointer offset of the threadSwitchFrom method
   * @param ypTakenFPoff the frame pointer offset of the real method where
   *                      yield point was taken. tsFrom is the callee of ypTaken
   * @param cmid the compiled method id of ypTaken
   */
  public abstract OSR_ExecutionState extractState(RVMThread thread, Offset tsFromFPoff, Offset ypTakenFPoff, int cmid);

  public static void printStackTraces(int[] stack, Offset osrFPoff) {

    VM.disableGC();

    Address fp = VM_Magic.objectAsAddress(stack).plus(osrFPoff);
    Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    while (VM_Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.VM_StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
      int cmid = VM_Magic.getCompiledMethodID(fp);

      if (cmid == ArchitectureSpecific.VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
        Offset instrOff = cm.getInstructionOffset(ip);
        cm.printStackTrace(instrOff, VM_PrintContainer.get(System.out));

        if (cm.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
          fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
        }
      }

      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }

    VM.enableGC();
  }
}
