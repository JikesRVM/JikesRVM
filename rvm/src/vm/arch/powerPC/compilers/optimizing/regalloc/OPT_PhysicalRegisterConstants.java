/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

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
  static final byte INT_VALUE= 0;
  static final byte DOUBLE_VALUE = 1;
  static final byte FLOAT_VALUE = 2;
  static final byte CONDITION_VALUE = 3;
  static final byte LONG_VALUE = 4;
  
  // There are different types of hardware registers, so we define
  // the following register classes:
  // NOTE: they must be in consecutive ordering
  // TODO: Kill this?
  static final byte INT_REG = 0;
  static final byte DOUBLE_REG = 1;
  static final byte CONDITION_REG = 2;
  static final byte SPECIAL_REG = 3;
  static final byte NUMBER_TYPE = 4;

  // Derived constants for use by the register pool.
  // In the register pool, the physical registers are assigned integers
  // based on these constants.
  static final int FIRST_INT = 0;
  static final int FIRST_DOUBLE = NUM_GPRS;
  static final int FIRST_CONDITION = NUM_GPRS + NUM_FPRS;
  static final int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS + NUM_CRS;

  // Derived constants for use by the register pool.
  static final int NUMBER_INT_NONVOLAT = LAST_NONVOLATILE_GPR 
                                         - FIRST_NONVOLATILE_GPR + 1;
  static final int NUMBER_DOUBLE_NONVOLAT = LAST_NONVOLATILE_FPR 
                                            - FIRST_NONVOLATILE_FPR + 1;
                                           
  
  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for parameters
  static final int FIRST_INT_PARAM = FIRST_VOLATILE_GPR + FIRST_INT;
  static final int NUMBER_INT_PARAM = LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR
                                        + 1;
  static final int FIRST_DOUBLE_PARAM = FIRST_VOLATILE_FPR + FIRST_DOUBLE;
  static final int NUMBER_DOUBLE_PARAM = LAST_VOLATILE_FPR - FIRST_VOLATILE_FPR
                                        + 1;
  
  // Derived constants for use by the register pool.
  // These constants give the register pool numbers for caller saved registers 
  // (or volatile registers, preserved across function calls).
  // NOTE: the order is used by the register allocator 
  // TODO: fix this.
  static final int FIRST_INT_RETURN = FIRST_VOLATILE_GPR + FIRST_INT;
  static final int NUMBER_INT_RETURN = 2;
  static final int FIRST_DOUBLE_RETURN = FIRST_VOLATILE_FPR + FIRST_DOUBLE;
  static final int NUMBER_DOUBLE_RETURN = 1;

  // special PowerPC registers 
  static final int XER = FIRST_SPECIAL + 0;     // extended register
  static final int LR = FIRST_SPECIAL + 1;      // link register
  static final int CTR = FIRST_SPECIAL + 2;     // count register
  static final int CR = FIRST_SPECIAL + 3;      // condition register
  static final int TU = FIRST_SPECIAL + 4;      // time upper
  static final int TL = FIRST_SPECIAL + 5;      // time lower

}
