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

import org.jikesrvm.Constants;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.VM;

/**
 * Registers used by baseline compiler implementation of virtual machine.
 */
public interface BaselineConstants extends Constants, ArchConstants {

  int WORDSIZE = VM.BuildFor64Addr ? 8 : 4; // bytes
  int LG_WORDSIZE = VM.BuildFor64Addr ? 3 : 2;

  // Dedicated registers.
  //
  RegisterConstants.GPR SP = ESP;
  RegisterConstants.GPR TR = THREAD_REGISTER;

  // Volatile (parameter) registers.
  //
  RegisterConstants.GPR T0 = EAX;  // DO NOT CHANGE THIS ASSIGNMENT
  RegisterConstants.GPR T1 = EDX;

  // scratch register
  RegisterConstants.GPR S0 = ECX;
  RegisterConstants.GPR S1 = EDI;

  // Constants describing baseline compiler conventions for
  // saving registers in stackframes.
  //
  int STACKFRAME_REG_SAVE_OFFSET = STACKFRAME_BODY_OFFSET;
  // offset from FP of the saved registers.
  // Some registers are saved in all baseline
  // frames, and most register as saved in the
  // dynamic bridge frames.
  int STACKFRAME_FIRST_PARAMETER_OFFSET = STACKFRAME_REG_SAVE_OFFSET - (2 * WORDSIZE);
  // bridge frames save 2 additional GPRs
  int BRIDGE_FRAME_EXTRA_SIZE = (SSE2_FULL ? XMM_STATE_SIZE : FPU_STATE_SIZE) + (2 * WORDSIZE);

  int SAVED_GPRS = 2; // EDI and EBX are nonvolatile registers used by baseline compiler
  int SAVED_GPRS_FOR_SAVE_LS_REGISTERS = 3; // save all non-volatiles
  Offset EDI_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_REG_SAVE_OFFSET);
  Offset EBX_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_REG_SAVE_OFFSET).minus(WORDSIZE);
  Offset EBP_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_REG_SAVE_OFFSET).minus(WORDSIZE*2);
  Offset T0_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_FIRST_PARAMETER_OFFSET);
  Offset T1_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_FIRST_PARAMETER_OFFSET).minus(WORDSIZE);
  Offset FPU_SAVE_OFFSET = T1_SAVE_OFFSET.minus(FPU_STATE_SIZE);
  Offset XMM_SAVE_OFFSET = T1_SAVE_OFFSET.minus(XMM_STATE_SIZE);
}

