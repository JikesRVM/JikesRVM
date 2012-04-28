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
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.MachineSpecific;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Wrappers around IA32-specific code common to both 32 & 64 bit
 */
public abstract class MachineSpecificIA extends MachineSpecific implements ArchConstants {

  /**
   * A well-known memory location used to manipulate the FPU control word.
   */
  static int FPUControlWord;

  /**
   * Wrappers around IA32-specific code (32-bit specific)
   */
  public static final class IA32 extends MachineSpecificIA {
    public static final IA32 singleton = new IA32();
  }

  /**
   * Wrappers around EMT64-specific code (64-bit specific)
   */
  public static final class EM64T extends MachineSpecificIA {
    public static final EM64T singleton = new EM64T();
  }

  /*
  * Generic (32/64 neutral) IA support
  */

  /* common to all ISAs */

  @Override
  @Interruptible
  public final void baselineEmitLoadTIB(ArchitectureSpecific.Assembler asm, int dest, int object, Offset tibOffset) {
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(GPR.lookup(dest), GPR.lookup(object), tibOffset);
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(GPR.lookup(dest), GPR.lookup(object), tibOffset);
    }
  }

  @Override
  @Uninterruptible
  public final void initializeStack(ArchitectureSpecific.Registers contextRegisters, Address ip, Address sp) {
    Address fp;
    sp = sp.minus(STACKFRAME_HEADER_SIZE);                   // last word of header
    fp = sp.minus(SizeConstants.BYTES_IN_ADDRESS + STACKFRAME_BODY_OFFSET);
    Magic.setCallerFramePointer(fp, STACKFRAME_SENTINEL_FP);
    Magic.setCompiledMethodID(fp, INVISIBLE_METHOD_ID);

    sp = sp.minus(SizeConstants.BYTES_IN_ADDRESS);                                 // allow for one local
    contextRegisters.gprs.set(ESP.value(), sp.toWord());
    contextRegisters.fp = fp;
    contextRegisters.ip = ip;
  }

  /* unique to IA */

  @Uninterruptible
  @Override
  public final void adjustESP(ArchitectureSpecific.Registers registers, Offset delta, boolean traceAdjustments) {
    Word old = registers.gprs.get(ESP.value());
    registers.gprs.set(ESP.value(), old.plus(delta));
    if (traceAdjustments) {
      VM.sysWrite(" esp =");
      VM.sysWrite(registers.gprs.get(ESP.value()));
    }
  }
}
