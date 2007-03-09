/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ppc;

import org.jikesrvm.ArchitectureSpecific.VM_RegisterConstants;

/**
 * This class holds constants that describe PowerPC register set.
 *
 * @author Mauricio J. Serrano
 * @author Stephen Fink
 * @modified Vivek Sarkar
 * @see OPT_RegisterAllocator
 */
public interface OPT_PhysicalRegisterConstants extends VM_RegisterConstants {
  
  // Types of values stored in physical registers; 
  // These affect instruction selection for accessing
  // the data
  byte INT_VALUE= 0;
  byte DOUBLE_VALUE = 1;
  byte FLOAT_VALUE = 2;
  byte CONDITION_VALUE = 3;
  
  // There are different types of hardware registers, so we define
  // the following register classes:
  // NOTE: they must be in consecutive ordering
  // TODO: Kill this?
  byte INT_REG = 0;
  byte DOUBLE_REG = 1;
  byte CONDITION_REG = 2;
  byte SPECIAL_REG = 3;
  byte NUMBER_TYPE = 4;

  // Derived constants for use by the register pool.
  // In the register pool, the physical registers are assigned integers
  // based on these constants.
  int FIRST_INT = 0;
  int FIRST_DOUBLE = NUM_GPRS;
  int FIRST_CONDITION = NUM_GPRS + NUM_FPRS;
  int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS + NUM_CRS;

  // Derived constants for use by the register pool.
  int NUMBER_INT_NONVOLAT = LAST_NONVOLATILE_GPR
                                         - FIRST_NONVOLATILE_GPR + 1;
  int NUMBER_DOUBLE_NONVOLAT = LAST_NONVOLATILE_FPR
                                            - FIRST_NONVOLATILE_FPR + 1;
                                           
  
  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for parameters
  int FIRST_INT_PARAM = FIRST_VOLATILE_GPR + FIRST_INT;
  int NUMBER_INT_PARAM = LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR
                                        + 1;
  int FIRST_DOUBLE_PARAM = FIRST_VOLATILE_FPR + FIRST_DOUBLE;
  int NUMBER_DOUBLE_PARAM = LAST_VOLATILE_FPR - FIRST_VOLATILE_FPR
                                        + 1;
  
  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for caller saved registers 
  // (or volatile registers, preserved across function calls).
  // NOTE: the order is used by the register allocator 
  // TODO: fix this.
  int FIRST_INT_RETURN = FIRST_VOLATILE_GPR + FIRST_INT;
  int NUMBER_INT_RETURN = 2;
  int FIRST_DOUBLE_RETURN = FIRST_VOLATILE_FPR + FIRST_DOUBLE;
  int NUMBER_DOUBLE_RETURN = 1;

  // special PowerPC registers 
  int XER = FIRST_SPECIAL + 0;     // extended register
  int LR = FIRST_SPECIAL + 1;      // link register
  int CTR = FIRST_SPECIAL + 2;     // count register
  int CR = FIRST_SPECIAL + 3;      // condition register
  int TU = FIRST_SPECIAL + 4;      // time upper
  int TL = FIRST_SPECIAL + 5;      // time lower

}
