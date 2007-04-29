/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package org.jikesrvm;

import org.jikesrvm.ArchitectureSpecific.VM_Assembler;
import org.jikesrvm.ArchitectureSpecific.VM_Registers;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Wrappers around machine specific code
 * 
 *
 * @author Steve Blackburn
 */
public abstract class VM_MachineSpecific {

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
  public abstract void baselineEmitLoadTIB(VM_Assembler asm, int dest, int object, Offset tibOffset);
  
  /**
   * The following method initializes a thread stack as if
   * "startoff" method had been called by an empty baseline-compiled
   * "sentinel" frame with one local variable.
   * 
   * @param contextRegisters The context registers for this thread
   * @param ip The instruction pointer for the "startoff" method
   * @param sp The base of the stack
   */
  public abstract void initializeStack(VM_Registers contextRegisters, Address ip, Address sp);
  
  /* unique to IA */
  /**
   * A thread's stack has been moved or resized.
   * Adjust the ESP register to reflect new position.
   * 
   * @param VM_Registers The registers for this thread
   * @param delta The displacement to be applied
   * @param traceAdjustments Log all adjustments to stderr if true
   */
  @Uninterruptible
  public void adjustESP(VM_Registers registers, Offset delta, boolean traceAdjustments) {
    if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
  }

  /* unique to PowerPC */

}
