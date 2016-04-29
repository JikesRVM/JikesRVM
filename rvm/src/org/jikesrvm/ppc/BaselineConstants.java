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

import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_SCRATCH_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FRAME_POINTER;
import static org.jikesrvm.ppc.RegisterConstants.JTOC_POINTER;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;

import org.jikesrvm.ppc.RegisterConstants.FPR;
import org.jikesrvm.ppc.RegisterConstants.GPR;

/**
 * Registers used by baseline compiler code.
 */
public final class BaselineConstants {

  // Dedicated registers
  public static final GPR FP = FRAME_POINTER;
  public static final GPR JTOC = JTOC_POINTER;

  // Scratch general purpose registers
  public static final GPR S0 = FIRST_SCRATCH_GPR;
  public static final GPR S1 = GPR.lookup(FIRST_SCRATCH_GPR.value() + 1);

  // Temporary general purpose registers
  public static final GPR T0 = FIRST_VOLATILE_GPR;
  public static final GPR T1 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 1);
  public static final GPR T2 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 2);
  public static final GPR T3 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 3);
  public static final GPR T4 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 4);
  public static final GPR T5 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 5);
  public static final GPR T6 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 6);
  public static final GPR T7 = GPR.lookup(FIRST_VOLATILE_GPR.value() + 7);

  // Temporary floating-point registers;
  public static final FPR F0 = FIRST_VOLATILE_FPR;
  public static final FPR F1 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 1);
  public static final FPR F2 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 2);
  public static final FPR F3 = FPR.lookup(FIRST_VOLATILE_FPR.value() + 3);

  public static final int VOLATILE_GPRS = LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1;
  public static final int VOLATILE_FPRS = LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1;
  public static final int MIN_PARAM_REGISTERS = (VOLATILE_GPRS < VOLATILE_FPRS ? VOLATILE_GPRS : VOLATILE_FPRS);

  public static final GPR FIRST_FIXED_LOCAL_REGISTER = FIRST_NONVOLATILE_GPR;
  public static final GPR LAST_FIXED_LOCAL_REGISTER = LAST_NONVOLATILE_GPR;
  public static final GPR LAST_FIXED_STACK_REGISTER = LAST_NONVOLATILE_GPR;

  public static final FPR FIRST_FLOAT_LOCAL_REGISTER = FIRST_NONVOLATILE_FPR;
  public static final FPR LAST_FLOAT_LOCAL_REGISTER = LAST_NONVOLATILE_FPR;
  public static final FPR LAST_FLOAT_STACK_REGISTER = LAST_NONVOLATILE_FPR;

  private BaselineConstants() {
    // prevent instantiation
  }
}
