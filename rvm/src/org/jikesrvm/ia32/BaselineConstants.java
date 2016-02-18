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

import static org.jikesrvm.ia32.ArchConstants.SSE2_FULL;
import static org.jikesrvm.ia32.RegisterConstants.EAX;
import static org.jikesrvm.ia32.RegisterConstants.ECX;
import static org.jikesrvm.ia32.RegisterConstants.EDI;
import static org.jikesrvm.ia32.RegisterConstants.EDX;
import static org.jikesrvm.ia32.RegisterConstants.ESP;
import static org.jikesrvm.ia32.RegisterConstants.THREAD_REGISTER;
import static org.jikesrvm.ia32.StackframeLayoutConstants.FPU_STATE_SIZE;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_BODY_OFFSET;
import static org.jikesrvm.ia32.StackframeLayoutConstants.XMM_STATE_SIZE;

import org.jikesrvm.VM;
import org.jikesrvm.ia32.RegisterConstants.GPR;
import org.vmmagic.unboxed.Offset;

/**
 * Registers used by baseline compiler implementation of virtual machine.
 */
public final class BaselineConstants {

  public static final int WORDSIZE = VM.BuildFor64Addr ? 8 : 4; // bytes
  public static final int LG_WORDSIZE = VM.BuildFor64Addr ? 3 : 2;

  // Dedicated registers.
  //
  public static final GPR SP = ESP;
  public static final GPR TR = THREAD_REGISTER;

  // Volatile (parameter) registers.
  //
  public static final GPR T0 = EAX;  // DO NOT CHANGE THIS ASSIGNMENT
  public static final GPR T1 = EDX;

  // scratch register
  public static final GPR S0 = ECX;
  public static final GPR S1 = EDI;

  // Constants describing baseline compiler conventions for
  // saving registers in stackframes.

  public static final Offset STACKFRAME_REG_SAVE_OFFSET = STACKFRAME_BODY_OFFSET;
  /** offset from FP of the saved registers. Some registers are saved in all baseline
    * frames, and most register as saved in the  dynamic bridge frames. */
  public static final Offset STACKFRAME_FIRST_PARAMETER_OFFSET = STACKFRAME_REG_SAVE_OFFSET.minus(2 * WORDSIZE);
  /** bridge frames save 2 additional GPRs **/
  public static final int BRIDGE_FRAME_EXTRA_SIZE = (SSE2_FULL ? XMM_STATE_SIZE : FPU_STATE_SIZE) + (2 * WORDSIZE);

  /** EDI and EBX are nonvolatile registers used by baseline compiler **/
  public static final int SAVED_GPRS = 2;
  /** save all non-volatiles **/
  public static final int SAVED_GPRS_FOR_SAVE_LS_REGISTERS = 3;

  public static final Offset EDI_SAVE_OFFSET = STACKFRAME_REG_SAVE_OFFSET;
  public static final Offset EBX_SAVE_OFFSET = STACKFRAME_REG_SAVE_OFFSET.minus(WORDSIZE);
  public static final Offset EBP_SAVE_OFFSET = STACKFRAME_REG_SAVE_OFFSET.minus(WORDSIZE * 2);
  public static final Offset T0_SAVE_OFFSET = STACKFRAME_FIRST_PARAMETER_OFFSET;
  public static final Offset T1_SAVE_OFFSET = STACKFRAME_FIRST_PARAMETER_OFFSET.minus(WORDSIZE);
  public static final Offset FPU_SAVE_OFFSET = T1_SAVE_OFFSET.minus(FPU_STATE_SIZE);
  public static final Offset XMM_SAVE_OFFSET = T1_SAVE_OFFSET.minus(XMM_STATE_SIZE);

  private BaselineConstants() {
    // prevent instantation
  }
}

