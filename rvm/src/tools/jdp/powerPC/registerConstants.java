/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Constants for AIX registers, copied from /usr/include/sys/reg.h
 * @author Ton Ngo
 */

interface registerConstants {

  // general purpose registers, all 32-bit, sequential no's 
  static final int GPR0	 =  0;
  static final int GPR1	 =  1;
  static final int GPR2	 =  2;
  static final int GPR3	 =  3;
  static final int GPR4	 =  4;
  static final int GPR5	 =  5;
  static final int GPR6	 =  6;
  static final int GPR7	 =  7;
  static final int GPR8	 =  8;
  static final int GPR9	 =  9;
  static final int GPR10 = 10;
  static final int GPR11 = 11;
  static final int GPR12 = 12;
  static final int GPR13 = 13;
  static final int GPR14 = 14;
  static final int GPR15 = 15;
  static final int GPR16 = 16;
  static final int GPR17 = 17;
  static final int GPR18 = 18;
  static final int GPR19 = 19;
  static final int GPR20 = 20;
  static final int GPR21 = 21;
  static final int GPR22 = 22;
  static final int GPR23 = 23;
  static final int GPR24 = 24;
  static final int GPR25 = 25;
  static final int GPR26 = 26;
  static final int GPR27 = 27;
  static final int GPR28 = 28;
  static final int GPR29 = 29;
  static final int GPR30 = 30;
  static final int GPR31 = 31;

  // miscellaneous special purpose registers - 32-bit, sequential no's 
  static final int IAR	  = 128;	  /* instruction address register*/
  static final int MSR	  = 129;	  /* machine state register	*/
  static final int CR	  = 130;	  /* condition register		*/
  static final int LR	  = 131;	  /* link register		*/
  static final int CTR	  = 132;	  /* count register		*/
  static final int XER	  = 133;	  /* fixed point exception	*/
  static final int MQ	  = 134;	  /* multiply/quotient register	*/
  static final int TID	  = 135;	  /* tid register		*/
  static final int FPSCR  = 136;	  /* floating point status reg	*/
  static final int FPINFO = 138;	  /* floating point info reg	*/
  static final int FPSCRX = 148;	  /* floating point sreg ext.    */

  // macro defines for floating point registers - 64-bit, sequential no's
  static final int FPR0	 = 256;
  static final int FPR1	 = 257;
  static final int FPR2	 = 258;
  static final int FPR3	 = 259;
  static final int FPR4	 = 260;
  static final int FPR5	 = 261;
  static final int FPR6	 = 262;
  static final int FPR7	 = 263;
  static final int FPR8	 = 264;
  static final int FPR9	 = 265;
  static final int FPR10 = 266;
  static final int FPR11 = 267;
  static final int FPR12 = 268;
  static final int FPR13 = 269;
  static final int FPR14 = 270;
  static final int FPR15 = 271;
  static final int FPR16 = 272;
  static final int FPR17 = 273;
  static final int FPR18 = 274;
  static final int FPR19 = 275;
  static final int FPR20 = 276;
  static final int FPR21 = 277;
  static final int FPR22 = 278;
  static final int FPR23 = 279;
  static final int FPR24 = 280;
  static final int FPR25 = 281;
  static final int FPR26 = 282;
  static final int FPR27 = 283;
  static final int FPR28 = 284;
  static final int FPR29 = 285;
  static final int FPR30 = 286;
  static final int FPR31 = 287;

}
