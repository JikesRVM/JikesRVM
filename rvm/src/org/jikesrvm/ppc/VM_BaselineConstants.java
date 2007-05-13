/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

import org.jikesrvm.VM_Constants;

/**
 * Registers used by baseline compiler code.
 *
 */
public interface VM_BaselineConstants extends VM_Constants, VM_ArchConstants {

  // Dedicated registers
  int FP   = FRAME_POINTER;
  int JTOC = JTOC_POINTER;

  // Scratch general purpose registers
  int S0   = FIRST_SCRATCH_GPR;
  int S1   = FIRST_SCRATCH_GPR+1;

  // Temporary general purpose registers 
  int T0   = FIRST_VOLATILE_GPR;
  int T1   = FIRST_VOLATILE_GPR+1;
  int T2   = FIRST_VOLATILE_GPR+2;
  int T3   = FIRST_VOLATILE_GPR+3;
  int T4   = FIRST_VOLATILE_GPR+4;
  int T5   = FIRST_VOLATILE_GPR+5;
  int T6   = FIRST_VOLATILE_GPR+6;

  // Temporary floating-point registers;
  int F0   = FIRST_VOLATILE_FPR;
  int F1   = FIRST_VOLATILE_FPR+1;
  int F2   = FIRST_VOLATILE_FPR+2;
  int F3   = FIRST_VOLATILE_FPR+3;

  int VOLATILE_GPRS = LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR + 1;
  int VOLATILE_FPRS = LAST_VOLATILE_FPR - FIRST_VOLATILE_FPR + 1;
  int MIN_PARAM_REGISTERS = (VOLATILE_GPRS < VOLATILE_FPRS ? VOLATILE_GPRS : VOLATILE_FPRS);

  int FIRST_FIXED_LOCAL_REGISTER = FIRST_NONVOLATILE_GPR;
  int LAST_FIXED_LOCAL_REGISTER = LAST_NONVOLATILE_GPR;
  int LAST_FIXED_STACK_REGISTER = LAST_NONVOLATILE_GPR;
  
  int FIRST_FLOAT_LOCAL_REGISTER = FIRST_NONVOLATILE_FPR;
  int LAST_FLOAT_LOCAL_REGISTER = LAST_NONVOLATILE_FPR;
  int LAST_FLOAT_STACK_REGISTER = LAST_NONVOLATILE_FPR;
}
