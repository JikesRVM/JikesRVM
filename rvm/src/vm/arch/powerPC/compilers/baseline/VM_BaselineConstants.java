/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Registers used by baseline compiler code.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public interface VM_BaselineConstants extends VM_Constants {

  // Dedicated registers
  static final int FP   = FRAME_POINTER; 
  static final int JTOC = JTOC_POINTER;

  // Scratch general purpose registers
  static final int S0   = FIRST_SCRATCH_GPR;
  static final int S1   = FIRST_SCRATCH_GPR+1;

  // Temporary general purpose registers 
  static final int T0   = FIRST_VOLATILE_GPR;
  static final int T1   = FIRST_VOLATILE_GPR+1;
  static final int T2   = FIRST_VOLATILE_GPR+2;
  static final int T3   = FIRST_VOLATILE_GPR+3;
  static final int T4   = FIRST_VOLATILE_GPR+4;
  
  // Temporary floating-point registers;
  static final int F0   = FIRST_VOLATILE_FPR;
  static final int F1   = FIRST_VOLATILE_FPR+1;
  static final int F2   = FIRST_VOLATILE_FPR+2;
  static final int F3   = FIRST_VOLATILE_FPR+3;

  static final int VOLATILE_GPRS = LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR + 1;
  static final int VOLATILE_FPRS = LAST_VOLATILE_FPR - FIRST_VOLATILE_FPR + 1;
  static final int MIN_PARAM_REGISTERS = (VOLATILE_GPRS < VOLATILE_FPRS ? VOLATILE_GPRS : VOLATILE_FPRS);
}
