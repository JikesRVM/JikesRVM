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

import org.jikesrvm.VM_Constants;
import org.vmmagic.unboxed.Offset;

/**
 * Registers used by baseline compiler implementation of virtual machine.
 */
public interface VM_BaselineConstants extends VM_Constants, VM_ArchConstants {

  int WORDSIZE = 4; // bytes
  int LG_WORDSIZE = 2;

  // Dedicated registers.
  //
  byte JTOC = EDI;
  byte SP = ESP;
  byte PR = PROCESSOR_REGISTER;

  // Volatile (parameter) registers.
  //
  byte T0 = EAX;  // DO NOT CHANGE THIS ASSIGNMENT
  byte T1 = EDX;

  // scratch register
  byte S0 = ECX;

  // Mnemonics corresponding to the above constants.
  // These are some alternate names that can be used in the debugger
  //
  String[] RVM_GPR_NAMES = {"eax", "ecx", "edx", "ebx", "esp", "ebp", "PR", "JT"};

  // Constants describing baseline compiler conventions for
  // saving registers in stackframes.
  //
  int STACKFRAME_REG_SAVE_OFFSET = STACKFRAME_BODY_OFFSET;
  // offset from FP of the saved registers.
  // Some registers are saved in all baseline
  // frames, and most register as saved in the
  // dynamic bridge frames.
  int STACKFRAME_FIRST_PARAMETER_OFFSET = STACKFRAME_REG_SAVE_OFFSET - 8;
  // bridge frames save 3 additional GPRs
  int BRIDGE_FRAME_EXTRA_SIZE = (SSE2_FULL ? XMM_STATE_SIZE : FPU_STATE_SIZE) + 8;

  int SAVED_GPRS = 2; // EDI(JTOC) and EBX are nonvolatile registers used by baseline compiler
  Offset JTOC_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_REG_SAVE_OFFSET);
  Offset EBX_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_REG_SAVE_OFFSET).minus(4);
  Offset T0_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_FIRST_PARAMETER_OFFSET);
  Offset T1_SAVE_OFFSET = Offset.fromIntSignExtend(STACKFRAME_FIRST_PARAMETER_OFFSET).minus(4);
  Offset FPU_SAVE_OFFSET = T1_SAVE_OFFSET.minus(FPU_STATE_SIZE);
  Offset XMM_SAVE_OFFSET = T1_SAVE_OFFSET.minus(XMM_STATE_SIZE);

}

