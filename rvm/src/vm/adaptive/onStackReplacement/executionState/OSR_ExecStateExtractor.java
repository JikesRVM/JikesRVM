/*
 * (C) Copyright IBM Corp 2002, 2003
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import java.io.*;

import org.vmmagic.unboxed.*;

/**
 * A OSR_ExecStateExtractor extracts a runtime state (JVM scope descriptor) 
 * of a method activation. The implementation depends on compilers and 
 * hardware architectures
 * @see OSR_BaselineExecStateExtractor
 * @see OSR_OptExecStateExtractor
 * 
 * It returns a compiler and architecture neutered runtime state 
 * OSR_ExecutionState.
 *
 * @author Feng Qian
 * @modified Steven Augart
 */

public abstract class OSR_ExecStateExtractor implements VM_Constants{
  /** 
   * Returns a JVM scope descriptor (OSR_ExecutionState) for a compiled method
   * on the top of a thread stack, (or a list of descriptors for an inlined
   * method).  
   *
   * @param thread a suspended RVM thread
   * @param tsFromFPoff the frame pointer offset of the threadSwitchFrom method
   * @param ypTakenFPoff the frame pointer offset of the real method where 
   *                      yield point was taken. tsFrom is the callee of ypTaken
   * @param cmid the compiled method id of ypTaken
   */
  public abstract OSR_ExecutionState extractState(VM_Thread thread, 
                                           int tsFromFPoff,
                                           int ypTakenFPoff,
                                           int cmid);

  public static void printStackTraces(int[] stack, int osrFPoff) {

    VM.disableGC();

    Address fp = VM_Magic.objectAsAddress(stack).add(osrFPoff);
    Address ip = VM_Magic.getReturnAddress(fp);
    fp = VM_Magic.getCallerFramePointer(fp);
    while (VM_Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP) ){
      int cmid = VM_Magic.getCompiledMethodID(fp);

      if (cmid == INVISIBLE_METHOD_ID) {
        VM.sysWriteln(" invisible method ");
      } else {
        VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
        int instrOff = cm.getInstructionOffset(ip);
        cm.printStackTrace(instrOff, PrintContainer.get(System.out));

        if (cm.getMethod().getDeclaringClass().isBridgeFromNative()) {
          fp = VM_Runtime.unwindNativeStackFrame(fp);
        }
      }
      
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
    }

    VM.enableGC();
  }
}
