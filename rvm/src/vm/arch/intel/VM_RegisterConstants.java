/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

/**
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author Dave Grove
 */
public interface VM_RegisterConstants {
  //---------------------------------------------------------------------------------------//
  //               RVM register usage conventions - Intel version.                         //
  //---------------------------------------------------------------------------------------//
    
  static final byte LG_INSTRUCTION_WIDTH = 0;             // log2 of instruction width in bytes
  static final int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH; 
    
  // Symbolic values for fixed-point registers.
  // These values are used to assemble instructions and as indices into:
  //    VM_Registers.gprs[]
  //    VM_Registers.fprs[]
  //    VM_GCMapIterator.registerLocations[]
  //    VM_RegisterConstants.GPR_NAMES[]
  //
  static final byte EAX = 0x0;
  static final byte ECX = 0x1;
  static final byte EDX = 0x2;
  static final byte EBX = 0x3;
  static final byte ESP = 0x4;
  static final byte EBP = 0x5;
  static final byte ESI = 0x6;
  static final byte EDI = 0x7;

  // Mnemonics corresponding to the above constants.
  static final String[] GPR_NAMES = {
    "eax", "ecx", "edx", "ebx", "esp", "ebp", "esi", "edi"
  };

  static final byte FP0 = 0x0;
  static final byte FP1 = 0x1;
  static final byte FP2 = 0x2;
  static final byte FP3 = 0x3;
  static final byte FP4 = 0x4;
  static final byte FP5 = 0x5;
  static final byte FP6 = 0x6;
  static final byte FP7 = 0x7;
  
  static final String [] FPR_NAMES = {
    "FP0", "FP1", "FP2", "FP3", "FP4", "FP5", "FP6", "FP7"
  };
    
  // Register sets (``range'' is a misnomer for the alphabet soup of
  // of intel registers)
  //

  // Note: the order here is important.  The opt-compiler allocates
  // the volatile registers in the order they appear here.
  static final byte[]  VOLATILE_GPRS = { EAX, EDX, ECX };
  static final int NUM_VOLATILE_GPRS = VOLATILE_GPRS.length;
    
  // Note: the order here is very important.  The opt-compiler allocates
  // the nonvolatile registers in the reverse of order they appear here.
  // EBX must be last, because it is the only non-volatile that can
  // be used in instructions that are using r8 and we must ensure that
  // opt doesn't skip over another nonvol while looking for an r8 nonvol.
  static final byte[]  NONVOLATILE_GPRS = { EBP, EDI, EBX};
  static final int NUM_NONVOLATILE_GPRS = NONVOLATILE_GPRS.length;
    
  static final byte[]  VOLATILE_FPRS = { FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7 }; 
  static final int NUM_VOLATILE_FPRS = VOLATILE_FPRS.length;
    
  static final byte[]  NONVOLATILE_FPRS = {  }; 
  static final int NUM_NONVOLATILE_FPRS = NONVOLATILE_FPRS.length;

  /*
   * These constants represent the number of volatile registers used
   * to pass parameters in registers.  They are defined to mean that
   * the first n registers in the corresponding set of volatile
   * registers are used to pass parameters.
   */
  static final int NUM_PARAMETER_GPRS = 2;
  static final int NUM_PARAMETER_FPRS = 4;
  static final int NUM_RETURN_GPRS = 2;
  static final int NUM_RETURN_FPRS = 1;


  // Dedicated registers.
  //
  static final byte STACK_POINTER              = ESP;
  static final byte PROCESSOR_REGISTER         = ESI;
   
  static final byte NUM_GPRS                   =  8;
  static final byte NUM_FPRS                   =  8;
}
