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

/**
 * Registers used by baseline compiler code.
 */
public interface BaselineConstants extends ArchConstants {

  // Dedicated registers
  GPR FP = FRAME_POINTER;
  GPR JTOC = JTOC_POINTER;

  // Scratch general purpose registers
  GPR S0 = FIRST_SCRATCH_GPR;
  GPR S1 = GPR.lookup(FIRST_SCRATCH_GPR.value() + 1);

  // Temporary general purpose registers
  GPR T0 = FIRST_VOLATILE_GPR;
  GPR T1 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 1);
  GPR T2 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 2);
  GPR T3 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 3);
  GPR T4 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 4);
  GPR T5 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 5);
  GPR T6 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 6);

  // Temporary floating-point registers;
  FPR F0 = FIRST_VOLATILE_FPR;
  FPR F1 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 1);
  FPR F2 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 2);
  FPR F3 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 3);

  int VOLATILE_GPRS = LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1;
  int VOLATILE_FPRS = LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1;
  int MIN_PARAM_REGISTERS = (VOLATILE_GPRS < VOLATILE_FPRS ? VOLATILE_GPRS : VOLATILE_FPRS);

  GPR FIRST_FIXED_LOCAL_REGISTER = FIRST_NONVOLATILE_GPR;
  GPR LAST_FIXED_LOCAL_REGISTER = LAST_NONVOLATILE_GPR;
  GPR LAST_FIXED_STACK_REGISTER = LAST_NONVOLATILE_GPR;

  FPR FIRST_FLOAT_LOCAL_REGISTER = FIRST_NONVOLATILE_FPR;
  FPR LAST_FLOAT_LOCAL_REGISTER = LAST_NONVOLATILE_FPR;
  FPR LAST_FLOAT_STACK_REGISTER = LAST_NONVOLATILE_FPR;
}
