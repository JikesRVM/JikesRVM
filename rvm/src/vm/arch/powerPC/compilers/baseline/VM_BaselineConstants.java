/*
 * (C) Copyright IBM Corp. 2001
 */
// Registers used by virtual machine.
//
interface VM_BaselineConstants extends VM_Constants {

  // Dedicated registers
  static final int FP   = FRAME_POINTER; 
  static final int JTOC = JTOC_POINTER;
  static final int TI   = THREAD_ID_REGISTER;

  // Scratch general purpose registers
  static final int S0   = FIRST_SCRATCH_GPR;
  static final int SP   = FIRST_SCRATCH_GPR+1;

  // Temporary general purpose registers 
  static final int T0   = FIRST_VOLATILE_GPR;
  static final int T1   = FIRST_VOLATILE_GPR+1;
  static final int T2   = FIRST_VOLATILE_GPR+2;
  static final int T3   = FIRST_VOLATILE_GPR+3;
  
  // Temporary floating-point registers;
  static final int F0   = FIRST_VOLATILE_FPR;
  static final int F1   = FIRST_VOLATILE_FPR+1;
  static final int F2   = FIRST_VOLATILE_FPR+2;
  static final int F3   = FIRST_VOLATILE_FPR+3;

  static final int VOLATILE_GPRS = LAST_VOLATILE_GPR - FIRST_VOLATILE_GPR + 1;
  static final int VOLATILE_FPRS = LAST_VOLATILE_FPR - FIRST_VOLATILE_FPR + 1;
  static final int MIN_PARAM_REGISTERS = (VOLATILE_GPRS < VOLATILE_FPRS ? VOLATILE_GPRS : VOLATILE_FPRS);

  // Register mnemonics (for use by debugger).
  //
  static final String [] GPR_NAMES =
     {
     "00", "FP", "JT", "T0", "T1", "T2", "T3", "V4",
     "V5", "V6", "V7", "V8", "V9", "S0", "SP", "TI",
     "PR", "NE", "ND", "NC", "NB", "NA", "N9", "N8",
     "N7", "N6", "N5", "N4", "N3", "N2", "N1", "N0"
     };

  static final String [] FPR_NAMES =
     {
     "F00",  "FV0",  "FV1",  "FV2",  "FV3",  "FV4",  "FN19", "FN18",
     "FN17", "FN16", "FN15", "FN14", "FN13", "FN12", "FN11", "FN10",
     "FNF",  "FNE",  "FND",  "FNC",  "FNB",  "FNA",  "FN9",  "FN8",
     "FN7",  "FN6",  "FN5",  "FN4",  "FN3",  "FN2",  "FN1",  "FN0"
     };
}                         
