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
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.VM_MachineSpecific;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Wrappers around IA32-specific code common to both 32 & 64 bit
 */
public abstract class VM_MachineSpecificIA extends VM_MachineSpecific implements VM_ArchConstants {

  /**
   * A well-known memory location used to manipulate the FPU control word.
   */
  static int FPUControlWord;

  /**
   * Wrappers around IA32-specific code (32-bit specific)
   */
  public static final class IA32 extends VM_MachineSpecificIA {
    public static final IA32 singleton = new IA32();
  }

  /**
   * Wrappers around EMT64-specific code (64-bit specific)
   */
  public static final class EM64T extends VM_MachineSpecificIA {
    public static final EM64T singleton = new EM64T();
  }

  /*
  * Generic (32/64 neutral) IA support
  */

  /* common to all ISAs */

  /**
   * The following method will emit code that moves a reference to an
   * object's TIB into a destination register.
   *
   * @param asm the assembler object to emit code with
   * @param dest the number of the destination register
   * @param object the number of the register holding the object reference
   * @param tibOffset the offset of the tib from the object header
   */
  @Interruptible
  public final void baselineEmitLoadTIB(ArchitectureSpecific.VM_Assembler asm, int dest, int object, Offset tibOffset) {
    asm.emitMOV_Reg_RegDisp(GPR.lookup(dest), GPR.lookup(object), tibOffset);
  }

  /**
   * The following method initializes a thread stack as if
   * "startoff" method had been called by an empty baseline-compiled
   *  "sentinel" frame with one local variable
   *
   * @param contextRegisters The context registers for this thread
   * @param ip The instruction pointer for the "startoff" method
   * @param sp The base of the stack
   */
  @Uninterruptible
  public final void initializeStack(ArchitectureSpecific.VM_Registers contextRegisters, Address ip, Address sp) {
    Address fp;
    sp = sp.minus(STACKFRAME_HEADER_SIZE);                   // last word of header
    fp = sp.minus(VM_SizeConstants.BYTES_IN_ADDRESS + STACKFRAME_BODY_OFFSET);
    VM_Magic.setCallerFramePointer(fp, STACKFRAME_SENTINEL_FP);
    VM_Magic.setCompiledMethodID(fp, INVISIBLE_METHOD_ID);

    sp = sp.minus(VM_SizeConstants.BYTES_IN_ADDRESS);                                 // allow for one local
    contextRegisters.gprs.set(ESP.value(), sp.toWord());
    contextRegisters.gprs.set(VM_BaselineConstants.JTOC.value(), VM_Magic.objectAsAddress(VM_Magic.getJTOC()).toWord());
    contextRegisters.fp = fp;
    contextRegisters.ip = ip;
  }

  /* unique to IA */

  /**
   * A thread's stack has been moved or resized.
   * Adjust the ESP register to reflect new position.
   *
   * @param registers The registers for this thread
   * @param delta The displacement to be applied
   * @param traceAdjustments Log all adjustments to stderr if true
   */
  @Uninterruptible
  @Override
  public final void adjustESP(ArchitectureSpecific.VM_Registers registers, Offset delta, boolean traceAdjustments) {
    Word old = registers.gprs.get(ESP.value());
    registers.gprs.set(ESP.value(), old.plus(delta));
    if (traceAdjustments) {
      VM.sysWrite(" esp =");
      VM.sysWrite(registers.gprs.get(ESP.value()));
    }
  }
}
