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
package org.jikesrvm.compilers.opt.regalloc.ia32;

import org.jikesrvm.ia32.RegisterConstants;

/**
 * This class holds constants that describe IA32 physical register set.
 */
public interface PhysicalRegisterConstants extends RegisterConstants {

  /*
   * Types of values stored in physical registers;
   * These affect instruction selection for accessing
   * the data
   */
  byte INT_VALUE = 0;
  byte DOUBLE_VALUE = 1;
  byte FLOAT_VALUE = 2;
  byte CONDITION_VALUE = 3;

  /*
   * There are different types of hardware registers, so we define
   * the following register classes:
   * NOTE: they must be in consecutive ordering
   * TODO: Kill this?
   */
  byte INT_REG = 0;
  byte DOUBLE_REG = 1;
  byte SPECIAL_REG = 2;
  byte NUMBER_TYPE = 3;

  /*
   * Derived constants for use by the register pool.
   * In the register pool, the physical registers are assigned integers
   * based on these constants.
   */
  int FIRST_INT = 0;
  int FIRST_DOUBLE = NUM_GPRS;
  int FIRST_SPECIAL = NUM_GPRS + NUM_FPRS;

  /** special intel registers or register sub-fields. */
  int NUM_SPECIALS = 12;
  /** AF bit of EFLAGS */
  int AF = FIRST_SPECIAL + 0;
  /** CF bit of EFLAGS */
  int CF = FIRST_SPECIAL + 1;
  /** OF bit of EFLAGS */
  int OF = FIRST_SPECIAL + 2;
  /** PF bit of EFLAGS */
  int PF = FIRST_SPECIAL + 3;
  /** SF bit of EFLAGS */
  int SF = FIRST_SPECIAL + 4;
  /** ZF bit of EFLAGS */
  int ZF = FIRST_SPECIAL + 5;
  /** C0 bit of EFLAGS */
  int C0 = FIRST_SPECIAL + 6;
  /** C1 bit of EFLAGS */
  int C1 = FIRST_SPECIAL + 7;
  /** C2 bit of EFLAGS */
  int C2 = FIRST_SPECIAL + 8;
  /** C3 bit of EFLAGS */
  int C3 = FIRST_SPECIAL + 9;
  /** ST0 - top of FP stack (for SSE2) */
  int ST0 = FIRST_SPECIAL + 10;
  /** ST1 - below top of FP stack (for SSE2) */
  int ST1 = FIRST_SPECIAL + 11;
}
