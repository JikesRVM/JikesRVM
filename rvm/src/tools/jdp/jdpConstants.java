/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Ton Ngo
 */

interface jdpConstants {
  static final int PRINTNONE = 0;
  static final int PRINTSOURCE = 1;
  static final int PRINTASSEMBLY = 2;

  static final int PROLOGMARKER = 0x4ffffb82;     // mark end of prolog section

  static final int BKPT_INSTRUCTION =  0x7d821008;  // instruction for setting breakpoints

  static final int NATIVE_METHOD_ID = -1;           // value returned for method ID if the stack frame is native

  static final int MAX_INSTRUCTION_SIZE = 3;   // for Intel, the largest size for one instruction, as number of 4-byte words
}  
