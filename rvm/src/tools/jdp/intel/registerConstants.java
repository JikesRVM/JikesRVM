/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Constants for Intel registers
 * @author Ton Ngo (3/5/01)
 */

interface registerConstants extends VM_RegisterConstants{

  // temp fix until VM_Constants.STACKFRAME_NEXT_INSTRUCTION_OFFSET is fixed
  // need to fix up in memory.java, breakpointList.java and register.java
  static final int STACKFRAME_NEXT_INSTRUCTION_OFFSET = 4;

  // general purpose registers, all 32-bit
  static final int GPR0	 =  0;
  // static final int GPR1	 =  1;
  // static final int GPR2	 =  2;
  // static final int GPR3	 =  3;
  // static final int GPR4	 =  4;
  // static final int GPR5	 =  5;
  // static final int GPR6	 =  6;
  static final int GPR7	 =  GPR0 + NUM_GPRS - 1;

  // static final int EAX = 0;
  // static final int ECX = 1;
  // static final int EDX = 2;
  // static final int EBX = 3;
  // static final int ESP = 4;
  // static final int EBP = 5;
  // static final int ESI = 6;
  // static final int EDI = 7;
  static final int EIP = 8;

  // register index for RVM convention
  static final int IAR	  = EIP;	         /* instruction address register*/
  // static final int SP	  = STACK_POINTER;       /* stack pointer		*/
  // static final int FP	  = FRAME_POINTER;	 /* Frame pointer 		*/
  // static final int PR	  = PROCESSOR_REGISTER;	 /* Processor register 		*/
  // static final int JTOC	  = EDI;	 /* JTOC for baseline 		*/
  // static final int TH	  = EBX;	 /* Thread ID 		        */

  static final int LR	  = 0;	  	         /* link register		*/

  static final int FPR0	 =  0;
  static final int FPR1	 =  1;
  static final int FPR2	 =  2;
  static final int FPR3	 =  3;
  static final int FPR4	 =  4;
  static final int FPR5	 =  5;
  static final int FPR6	 =  6;
  static final int FPR7	 =  7;


}
