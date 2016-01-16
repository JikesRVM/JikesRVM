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
package org.jikesrvm.compilers.opt.regalloc.ppc;

import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.NUM_CRS;
import static org.jikesrvm.ppc.RegisterConstants.NUM_FPRS;
import static org.jikesrvm.ppc.RegisterConstants.NUM_GPRS;

/**
 * This class holds constants that describe PowerPC register set.
 *
 * @see org.jikesrvm.compilers.opt.regalloc.RegisterAllocator
 */
public final class PhysicalRegisterConstants {

  // There are different types of hardware registers, so we define
  // the following register classes:
  // NOTE: they must be in consecutive ordering
  // TODO: Kill this?
  public static final byte INT_REG = 0;
  public static final byte DOUBLE_REG = 1;
  public static final byte CONDITION_REG = 2;
  public static final byte SPECIAL_REG = 3;
  public static final byte NUMBER_TYPE = 4;

  // Derived constants for use by the register pool.
  // In the register pool, the physical registers are assigned integers
  // based on these constants.
  public static final int FIRST_INT = 0;
  public static final int FIRST_DOUBLE = NUM_GPRS;
  public static final int FIRST_CONDITION = NUM_GPRS + NUM_FPRS;
  public static final int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS + NUM_CRS;

  // Derived constants for use by the register pool.
  public static final int NUMBER_INT_NONVOLAT = LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1;
  public static final int NUMBER_DOUBLE_NONVOLAT = LAST_NONVOLATILE_FPR.value() - FIRST_NONVOLATILE_FPR.value() + 1;

  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for parameters
  public static final int FIRST_INT_PARAM = FIRST_VOLATILE_GPR.value() + FIRST_INT;
  public static final int NUMBER_INT_PARAM = LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1;
  public static final int FIRST_DOUBLE_PARAM = FIRST_VOLATILE_FPR.value() + FIRST_DOUBLE;
  public static final int NUMBER_DOUBLE_PARAM = LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1;

  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for caller saved registers
  // (or volatile registers, preserved across function calls).
  // NOTE: the order is used by the register allocator
  // TODO: fix this.
  public static final int FIRST_INT_RETURN = FIRST_VOLATILE_GPR.value() + FIRST_INT;
  public static final int NUMBER_INT_RETURN = 2;
  public static final int FIRST_DOUBLE_RETURN = FIRST_VOLATILE_FPR.value() + FIRST_DOUBLE;
  public static final int NUMBER_DOUBLE_RETURN = 1;

  // special PowerPC registers
  public static final int XER = FIRST_SPECIAL + 0;     // extended register
  public static final int LR = FIRST_SPECIAL + 1;      // link register
  public static final int CTR = FIRST_SPECIAL + 2;     // count register
  public static final int CR = FIRST_SPECIAL + 3;      // condition register
  public static final int TU = FIRST_SPECIAL + 4;      // time upper
  public static final int TL = FIRST_SPECIAL + 5;      // time lower

  private PhysicalRegisterConstants() {
    // prevent instantiation
  }

}
