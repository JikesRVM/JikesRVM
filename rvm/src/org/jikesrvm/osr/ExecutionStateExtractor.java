/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.PrintContainer;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A ExecutionStateExtractor extracts a runtime state (VM scope descriptor)
 * of a method activation. The implementation depends on compilers and
 * hardware architectures
 * @see org.jikesrvm.ArchitectureSpecificOpt.BaselineExecutionStateExtractor
 * @see org.jikesrvm.ArchitectureSpecificOpt.OptExecutionStateExtractor
 *
 * It returns a compiler and architecture neutered runtime state
 * ExecutionState.
 */

public abstract class ExecutionStateExtractor implements Constants {
  /**
   * Returns a VM scope descriptor (ExecutionState) for a compiled method
   * on the top of a thread stack, (or a list of descriptors for an inlined
   * method).
   *
   * @param thread a suspended RVM thread
   * @param tsFromFPoff the frame pointer offset of the threadSwitchFrom method
   * @param ypTakenFPoff the frame pointer offset of the real method where
   *                      yield point was taken. tsFrom is the callee of ypTaken
   * @param cmid the compiled method id of ypTaken
   */
  public abstract ExecutionState extractState(RVMThread thread, Offset tsFromFPoff, Offset ypTakenFPoff, int cmid);

  public static void printStackTraces(int[] stack, Offset osrFPoff) {

    VM.disableGC();

    Address fp = Magic.objectAsAddress(stack).plus(osrFPoff);
    Address ip = Magic.getReturnAddressUnchecked(fp);
    fp = Magic.getCallerFramePointer(fp);
    while (Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
      int cmid = Magic.getCompiledMethodID(fp);

      if (cmid == ArchitectureSpecific.StackframeLayoutConstants.INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
        Offset instrOff = cm.getInstructionOffset(ip);
        cm.printStackTrace(instrOff, PrintContainer.get(System.out));

        if (cm.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
          fp = RuntimeEntrypoints.unwindNativeStackFrame(fp);
        }
      }

      ip = Magic.getReturnAddressUnchecked(fp);
      fp = Magic.getCallerFramePointer(fp);
    }

    VM.enableGC();
  }
}
