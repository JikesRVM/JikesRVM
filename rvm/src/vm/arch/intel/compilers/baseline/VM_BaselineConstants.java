/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

/**
 * Registers used by baseline compiler implementation of virtual machine.
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
public interface VM_BaselineConstants extends VM_Constants {
  
  static final int    WORDSIZE = 4; // bytes
  static final int LG_WORDSIZE = 2; 

  // Dedicated registers.
  //
  static final byte JTOC = EDI;
  static final byte SP   = ESP;
  static final byte PR   = PROCESSOR_REGISTER;

  // Volatile (parameter) registers.
  //
  static final byte T0   =  EAX;  // DO NOT CHANGE THIS ASSIGNMENT
  static final byte T1   =  EDX; 
  
  // scratch register
  static final byte S0  =  ECX;

  // Mnemonics corresponding to the above constants.
  // These are some alternate names that can be used in the debugger
  //
  static final String[] RVM_GPR_NAMES =
     {
     "eax", "ecx", "edx", "ebx", "esp", "ebp", "PR", "JT"
     };

  // Constants describing baseline compiler conventions for
  // saving registers in stackframes.
  // 
  static final int STACKFRAME_REG_SAVE_OFFSET          = STACKFRAME_BODY_OFFSET;
                                        // offset from FP of the saved registers.  
                                        // Some registers are saved in all baseline
                                        // frames, and most register as saved in the
                                        // dynamic bridge frames.
  static final int STACKFRAME_FIRST_PARAMETER_OFFSET  = STACKFRAME_REG_SAVE_OFFSET -8;
  // bridge frames save 3 additional GPRs
  static final int BRIDGE_FRAME_EXTRA_SIZE             = FPU_STATE_SIZE + 8;

  static final int SAVED_GPRS       = 2; // EDI(JTOC) and EBX are nonvolatile registers used by baseline compiler
  static final int JTOC_SAVE_OFFSET = STACKFRAME_REG_SAVE_OFFSET;
  static final int EBX_SAVE_OFFSET  = STACKFRAME_REG_SAVE_OFFSET - 4;
  static final int T0_SAVE_OFFSET   = STACKFRAME_FIRST_PARAMETER_OFFSET ;
  static final int T1_SAVE_OFFSET   = STACKFRAME_FIRST_PARAMETER_OFFSET - 4;
  static final int FPU_SAVE_OFFSET  = T1_SAVE_OFFSET - FPU_STATE_SIZE;

}

