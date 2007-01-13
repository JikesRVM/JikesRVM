/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.ppc;

import com.ibm.jikesrvm.VM_Constants;
import com.ibm.jikesrvm.VM_MachineSpecific;
import com.ibm.jikesrvm.VM_Memory;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_Assembler;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_Registers;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Wrappers around PowerPC-specific code common to both 32 & 64 bit
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public abstract class VM_MachineSpecificPowerPC extends VM_MachineSpecific implements VM_Constants {

  /**
   * Wrappers around PPC32-specific code (32-bit specific)
   */
  public static final class PPC32 extends VM_MachineSpecificPowerPC {
    public static final PPC32 singleton = new PPC32();
  }
  
  /**
   * Wrappers around PPC64-specific code (64-bit specific)
   */
  public static final class PPC64 extends VM_MachineSpecificPowerPC {
    public static final PPC64 singleton = new PPC64();
  }
 
  
  /* 
   * Generic (32/64 neutral) PowerPC support
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
  public final void baselineEmitLoadTIB(VM_Assembler asm, int dest, int object, Offset tibOffset) { 
    asm.emitLAddrOffset(dest, object, tibOffset);
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
  public final void initializeStack(VM_Registers contextRegisters, Address ip, Address sp) {
    Address fp;
   // align stack frame
    int INITIAL_FRAME_SIZE = STACKFRAME_HEADER_SIZE;
    fp = VM_Memory.alignDown(sp.minus(INITIAL_FRAME_SIZE), STACKFRAME_ALIGNMENT);
    fp.plus(STACKFRAME_FRAME_POINTER_OFFSET).store(STACKFRAME_SENTINEL_FP);
    fp.plus(STACKFRAME_NEXT_INSTRUCTION_OFFSET).store(ip); // need to fix
    fp.plus(STACKFRAME_METHOD_ID_OFFSET).store(INVISIBLE_METHOD_ID);
        
    contextRegisters.gprs.set(FRAME_POINTER, fp.toWord());
    contextRegisters.ip  = ip;
  }
    /* unique to PowerPC */

}
