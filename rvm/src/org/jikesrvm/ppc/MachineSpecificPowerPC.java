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
package org.jikesrvm.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecific.Registers;
import org.jikesrvm.MachineSpecific;
import org.jikesrvm.runtime.Memory;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Wrappers around PowerPC-specific code common to both 32 & 64 bit
 */
public abstract class MachineSpecificPowerPC extends MachineSpecific implements ArchConstants {

  /**
   * Wrappers around PPC32-specific code (32-bit specific)
   */
  public static final class PPC32 extends MachineSpecificPowerPC {
    public static final PPC32 singleton = new PPC32();
  }

  /**
   * Wrappers around PPC64-specific code (64-bit specific)
   */
  public static final class PPC64 extends MachineSpecificPowerPC {
    public static final PPC64 singleton = new PPC64();
  }

  /*
  * Generic (32/64 neutral) PowerPC support
  */

  /* common to all ISAs */

  @Override
  @Interruptible
  public final void baselineEmitLoadTIB(ArchitectureSpecific.Assembler asm, int dest, int object, Offset tibOffset) {
    asm.emitLAddrOffset(dest, object, tibOffset);
  }

  @Override
  @Uninterruptible
  public final void initializeStack(Registers contextRegisters, Address ip, Address sp) {
    Address fp;
    // align stack frame
    int INITIAL_FRAME_SIZE = STACKFRAME_HEADER_SIZE;
    fp = Memory.alignDown(sp.minus(INITIAL_FRAME_SIZE), STACKFRAME_ALIGNMENT);
    fp.plus(STACKFRAME_FRAME_POINTER_OFFSET).store(STACKFRAME_SENTINEL_FP);
    fp.plus(STACKFRAME_NEXT_INSTRUCTION_OFFSET).store(ip); // need to fix
    fp.plus(STACKFRAME_METHOD_ID_OFFSET).store(INVISIBLE_METHOD_ID);

    contextRegisters.gprs.set(FRAME_POINTER, fp.toWord());
    contextRegisters.ip = ip;
  }
  /* unique to PowerPC */

}
