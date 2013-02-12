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

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;

/**
 * Register Usage Conventions for PowerPC.
 */
public interface RegisterConstants extends SizeConstants {
  // Machine instructions.
  //

  /** log2 of instruction width in bytes, powerPC */
  int LG_INSTRUCTION_WIDTH = 2;
  /** instruction width in bytes, powerPC */
  int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  // OS register convention (for mapping parameters in JNI calls)
  // These constants encode conventions for AIX, OSX, and Linux.
  int FIRST_OS_PARAMETER_GPR = 3;
  int LAST_OS_PARAMETER_GPR = 10;
  int FIRST_OS_VOLATILE_GPR = 3;
  int LAST_OS_VOLATILE_GPR = 12;
  int FIRST_OS_NONVOLATILE_GPR = (VM.BuildForAix && VM.BuildFor64Addr) ? 14 : 13;
  int LAST_OS_NONVOLATILE_GPR = 31;
  int FIRST_OS_PARAMETER_FPR = 1;
  int LAST_OS_PARAMETER_FPR = VM.BuildForLinux ? 8 : 13;
  int FIRST_OS_VOLATILE_FPR = 1;
  int LAST_OS_VOLATILE_FPR = 13;
  int FIRST_OS_NONVOLATILE_FPR = 14;
  int LAST_OS_NONVOLATILE_FPR = 31;
  int LAST_OS_VARARG_PARAMETER_FPR = VM.BuildForAix ? 6 : 8;

  // Jikes RVM's general purpose register usage (32 or 64 bits wide based on VM.BuildFor64Addr).
  //
  /** special instruction semantics on this register */
  int REGISTER_ZERO = 0;
  /** same as AIX/OSX/Linux */
  int FRAME_POINTER = 1;
  int FIRST_VOLATILE_GPR = FIRST_OS_PARAMETER_GPR;
  //                                            ...
  int LAST_VOLATILE_GPR = LAST_OS_PARAMETER_GPR;
  int FIRST_SCRATCH_GPR = LAST_VOLATILE_GPR + 1;
  int LAST_SCRATCH_GPR = LAST_OS_VOLATILE_GPR;
  // AIX 64 bit ABI reserves R13 for use by libpthread; therefore Jikes RVM doesn't touch it.
  int FIRST_RVM_RESERVED_NV_GPR = VM.BuildFor64Addr ? 14 : 13;
  int THREAD_REGISTER = FIRST_RVM_RESERVED_NV_GPR;

  // 2 is used by Linux for thread context, on AIX it's the toc and on OS X it's a scratch.
  int JTOC_POINTER = (VM.BuildForLinux && VM.BuildFor32Addr) ? THREAD_REGISTER + 1 : 2;
  int KLUDGE_TI_REG = THREAD_REGISTER + ((VM.BuildForLinux && VM.BuildFor32Addr) ? 2 : 1);

  int LAST_RVM_RESERVED_NV_GPR = KLUDGE_TI_REG; // will become PR when KLUDGE_TI dies.
  int FIRST_NONVOLATILE_GPR = LAST_RVM_RESERVED_NV_GPR + 1;
  //                                            ...
  int LAST_NONVOLATILE_GPR = LAST_OS_NONVOLATILE_GPR;
  int NUM_GPRS = 32;

  // Floating point register usage. (FPR's are 64 bits wide).

  /** AIX/OSX/Linux is 0 */
  int FIRST_SCRATCH_FPR = 0;
  /** AIX/OSX/Linux is 0 */
  int LAST_SCRATCH_FPR = 0;
  int FIRST_VOLATILE_FPR = FIRST_OS_VOLATILE_FPR;
  //
  int LAST_VOLATILE_FPR = LAST_OS_VOLATILE_FPR;
  int FIRST_NONVOLATILE_FPR = FIRST_OS_NONVOLATILE_FPR;
  //                                            ...
  int LAST_NONVOLATILE_FPR = LAST_OS_NONVOLATILE_FPR;
  int NUM_FPRS = 32;

  int NUM_NONVOLATILE_GPRS = LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1;
  int NUM_NONVOLATILE_FPRS = LAST_NONVOLATILE_FPR - FIRST_NONVOLATILE_FPR + 1;

  // condition registers
  // TODO: fill table
  int NUM_CRS = 8;

  /** number of special registers (user visible) */
  int NUM_SPECIALS = 8;

  // Register mnemonics (for use by debugger/machine code printers).
  //
  String[] GPR_NAMES = RegisterConstantsHelper.gprNames();

  String[] FPR_NAMES =
      {"F0",
       "F1",
       "F2",
       "F3",
       "F4",
       "F5",
       "F6",
       "F7",
       "F8",
       "F9",
       "F10",
       "F11",
       "F12",
       "F13",
       "F14",
       "F15",
       "F16",
       "F17",
       "F18",
       "F19",
       "F20",
       "F21",
       "F22",
       "F23",
       "F24",
       "F25",
       "F26",
       "F27",
       "F28",
       "F29",
       "F30",
       "F31"};

  /**
   * This class exists only to kludge around the fact that we can't
   * put static clinit blocks in interfaces.  As a result,
   * it is awkward to write 'nice' code to initialize the register names
   * based on the values of the constants.
   */
  class RegisterConstantsHelper {
    static String[] gprNames() {
      String[] names =
          {"R0",
           "R1",
           "R2",
           "R3",
           "R4",
           "R5",
           "R6",
           "R7",
           "R8",
           "R9",
           "R10",
           "R11",
           "R12",
           "R13",
           "R14",
           "R15",
           "R16",
           "R17",
           "R18",
           "R19",
           "R20",
           "R21",
           "R22",
           "R23",
           "R24",
           "R25",
           "R26",
           "R27",
           "R28",
           "R29",
           "R30",
           "R31"};
      names[FRAME_POINTER] = "FP";
      names[JTOC_POINTER] = "JT";
      names[THREAD_REGISTER] = "TR";
      return names;
    }
  }
}

