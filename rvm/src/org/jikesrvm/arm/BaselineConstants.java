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
package org.jikesrvm.arm;

import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_DPR;

import org.jikesrvm.arm.RegisterConstants.GPR;
import org.jikesrvm.arm.RegisterConstants.FPR;
import org.jikesrvm.arm.RegisterConstants.DPR;
import org.jikesrvm.arm.RegisterConstants.QPR;

/**
 * Registers used by baseline compiler code.
 */
public final class BaselineConstants {

  // Temporary general purpose registers
  public static final GPR T0 = FIRST_VOLATILE_GPR;
  public static final GPR T1 = T0.nextGPR();
  public static final GPR T2 = T1.nextGPR();
  public static final GPR T3 = T2.nextGPR(); // = LAST_VOLATILE_GPR

  // Temporary floating-point registers;
  public static final FPR F0 = FIRST_VOLATILE_FPR;
  public static final FPR F1 = F0.nextFPR();
  public static final FPR F2 = F1.nextFPR();
  public static final FPR F3 = F2.nextFPR();
  public static final FPR F4 = F3.nextFPR();
  public static final FPR F5 = F4.nextFPR();
  public static final FPR F6 = F5.nextFPR();

  public static final DPR F0and1 = FIRST_VOLATILE_DPR;
  public static final DPR F2and3 = F0and1.nextDPR();
  public static final DPR F4and5 = F2and3.nextDPR();
  public static final DPR F6and7 = F4and5.nextDPR();

  public static final QPR F0and1and2and3 = QPR.Q0;

  private BaselineConstants() {
      // prevent instantiation
  }
}
