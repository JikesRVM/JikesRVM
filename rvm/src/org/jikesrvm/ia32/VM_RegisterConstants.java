/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ia32;

public interface VM_RegisterConstants {
  //---------------------------------------------------------------------------------------//
  //               RVM register usage conventions - Intel version.                         //
  //---------------------------------------------------------------------------------------//

  byte LG_INSTRUCTION_WIDTH = 0;             // log2 of instruction width in bytes
  int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  // Symbolic values for fixed-point registers.
  // These values are used to assemble instructions and as indices into:
  //    VM_Registers.gprs[]
  //    VM_Registers.fprs[]
  //    VM_GCMapIterator.registerLocations[]
  //    VM_RegisterConstants.GPR_NAMES[]
  //
  byte EAX = 0x0;
  byte ECX = 0x1;
  byte EDX = 0x2;
  byte EBX = 0x3;
  byte ESP = 0x4;
  byte EBP = 0x5;
  byte ESI = 0x6;
  byte EDI = 0x7;

  // Mnemonics corresponding to the above constants.
  String[] GPR_NAMES = {"eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"};

  byte FP0 = 0x0;
  byte FP1 = 0x1;
  byte FP2 = 0x2;
  byte FP3 = 0x3;
  byte FP4 = 0x4;
  byte FP5 = 0x5;
  byte FP6 = 0x6;
  byte FP7 = 0x7;

  String[] FPR_NAMES = {"FP0", "FP1", "FP2", "FP3", "FP4", "FP5", "FP6", "FP7"};

  // Register sets (``range'' is a misnomer for the alphabet soup of
  // of intel registers)
  //

  // Note: the order here is important.  The opt-compiler allocates
  // the volatile registers in the order they appear here.
  byte[] VOLATILE_GPRS = {EAX, EDX, ECX};
  int NUM_VOLATILE_GPRS = VOLATILE_GPRS.length;

  // Note: the order here is very important.  The opt-compiler allocates
  // the nonvolatile registers in the reverse of order they appear here.
  // EBX must be last, because it is the only non-volatile that can
  // be used in instructions that are using r8 and we must ensure that
  // opt doesn't skip over another nonvol while looking for an r8 nonvol.
  byte[] NONVOLATILE_GPRS = {EBP, EDI, EBX};
  int NUM_NONVOLATILE_GPRS = NONVOLATILE_GPRS.length;

  byte[] VOLATILE_FPRS = {FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7};
  int NUM_VOLATILE_FPRS = VOLATILE_FPRS.length;

  byte[] NONVOLATILE_FPRS = {};
  int NUM_NONVOLATILE_FPRS = NONVOLATILE_FPRS.length;

  /*
   * These constants represent the number of volatile registers used
   * to pass parameters in registers.  They are defined to mean that
   * the first n registers in the corresponding set of volatile
   * registers are used to pass parameters.
   */ int NUM_PARAMETER_GPRS = 2;
  int NUM_PARAMETER_FPRS = 4;
  int NUM_RETURN_GPRS = 2;
  int NUM_RETURN_FPRS = 1;

  // Dedicated registers.
  //
  byte STACK_POINTER = ESP;
  byte PROCESSOR_REGISTER = ESI;

  byte NUM_GPRS = 8;
  byte NUM_FPRS = 8;
}
