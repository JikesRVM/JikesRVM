/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class holds constants that describe IA32 physical register set.
 *
 * @author Stephen Fink
 */
interface OPT_PhysicalRegisterConstants extends VM_RegisterConstants {

  // Types of values stored in physical registers; 
  // These affect instruction selection for accessing
  // the data
  static final byte INT_VALUE= 0;
  static final byte DOUBLE_VALUE = 1;
  static final byte FLOAT_VALUE = 2;
  static final byte CONDITION_VALUE = 3;
  
  // There are different types of hardware registers, so we define
  // the following register classes:
  // NOTE: they must be in consecutive ordering
  // TODO: Kill this?
  static final byte INT_REG = 0;
  static final byte DOUBLE_REG = 1;
  static final byte SPECIAL_REG = 2;
  static final byte NUMBER_TYPE = 3;

  // Derived constants for use by the register pool.
  // In the register pool, the physical registers are assigned integers
  // based on these constants.
  static final int FIRST_INT = 0;
  static final int FIRST_DOUBLE = NUM_GPRS;
  static final int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS;

  // special intel registers or register sub-fields.
  static final int NUM_SPECIALS = 10;
  static final int AF = FIRST_SPECIAL + 0;      // AF bit of EFLAGS
  static final int CF = FIRST_SPECIAL + 1;      // CF bit of EFLAGS
  static final int OF = FIRST_SPECIAL + 2;      // OF bit of EFLAGS
  static final int PF = FIRST_SPECIAL + 3;      // PF bit of EFLAGS
  static final int SF = FIRST_SPECIAL + 4;      // SF bit of EFLAGS
  static final int ZF = FIRST_SPECIAL + 5;      // ZF bit of EFLAGS
  static final int C0 = FIRST_SPECIAL + 6;      // FP status bit
  static final int C1 = FIRST_SPECIAL + 7;      // FP status bit
  static final int C2 = FIRST_SPECIAL + 8;      // FP status bit
  static final int C3 = FIRST_SPECIAL + 9;      // FP status bit
}
