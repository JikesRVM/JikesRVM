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

/**
 * Disassembler for Rios instruction set.
 *
 * Defined: disasm(inst, addr, buf, reg)
 *          INSTRUCTION inst; Address addr;  CHAR *buf; CHAR reg[4][10];
 *
 * 31 Jul 1990 Derek Lieber.
 *      Borrowed from libdbx (opcode.c, decode.c).
 *
 * 30 Jan 1998 Ton Ngo:
 *      adapted for Java debugger jdp
 *
 *  7 Aug 1998 John Whaley:
 *      rewritten in Java
 *
 *  26 Jan 1999 Ton Ngo:
 *    New instruction for PowerPC that are not in the POWER instruction set
 *    opcode is bit 0-5 of the instruction
 *    extop is the extended opcode as listed in the manual
 *    key is extop plus additional bits for variation on an opcode (. or o)
 *    form is the instruction format: I, B, SC, D, DS, X, XL, XFX, XFL, XO, A, M
 *    format is a local enumeration to specify how to print the instruction
 *    (this is specific to each decode_?? method).
 *
 * 18 Dec 2000 Dave Grove:
 *    Changed the mnemonics for POWER instructions that have
 *    different names on the PowerPC to use the PowerPC mnemonic.
 *    (Applied changes from Appendix F of PPC Architecture book).
 *
 *  mnemonic    opcode  extop   key     form    format  Example
 *  --------    ------  ---     ---     ----    ------  --------
 *  dcbf        31      86      172     X       6       7C0218AC
 *  dcbi        31      470     940     X       6       7C021BAC
 *  dcbst       31      54      108     X       6       7C02186C
 *  dcbt        31      278     556     X       6       7C021A2C
 *  dcbtst      31      246     492     X       6       7C0219EC
 *  dcbz        31      1014    2028    X       6       7C021FEC
 *  divw        31      491     982     XO      1       7C221BD6
 *  divw.       31      491     983     XO      1       7C221BD7
 *  divwo       31      491     2006    XO      1       7C221FD6
 *  divwo.      31      491     2007    XO      1       7C221FD7
 *  divwu       31      459     918     XO      1       7C221B96
 *  divwu.      31      459     919     XO      1       7C221B97
 *  divwuo      31      459     1942    XO      1       7C221F96
 *  divwuo.     31      459     1943    XO      1       7C221F97
 *  eieio       31      854     1708    X       1       7C0006AC
 *  extsb       31      954     1908    X       0       7C220774
 *  extsb.      31      954     1909    X       0       7C220775
 *  icbi        31      982     1964    X       6       7C021FAC
 *  lwarx       31      20      40      X       7       7C221828
 *  mfsrin      31      659     1318    X       4       7C201526
 *  mulhw       31      75      150     XO      1       7C221896
 *  mulhw.      31      75      151     XO      1       7C221897
 *  mulhwu      31      11      22      XO      1       7C221816
 *  mulhwu.     31      11      23      XO      1       7C221817
 *  stwcx.      31      150     301     X       7       7C22192D
 *  subf        31      40      80      XO      1       7C221850
 *  subf.       31      40      81      XO      1       7C221851
 *  subfo       31      40      1104    XO      1       7C221C50
 *  subfo.      31      40      1105    XO      1       7C221C51
 *
 *  fadds       59      21      42      A       0       EC22182A
 *  fadds.      59      21      43      A       0       EC22182B
 *  fdivs       59      18      36      A       0       EC221824
 *  fdivs.      59      18      37      A       0       EC221825
 *  fmadds      59      29      58      A       2       EC22193A
 *  fmadds.     59      29      59      A       2       EC22193B
 *  fmsubs      59      28      56      A       2       EC221938
 *  fmsubs.     59      28      57      A       2       EC221939
 *  fmuls       59      25      50      A       1       EC2200F2
 *  fmuls.      59      25      51      A       1       EC2200F3
 *  fnmadds     59      31      62      A       2       EC22193E
 *  fnmadds.    59      31      63      A       2       EC22193F
 *  fnmsubs     59      30      60      A       2       EC22193C
 *  fnmsubs.    59      30      61      A       2       EC22193D
 *  fsubs       59      20      40      A       0       EC221828
 *  fsubs.      59      20      41      A       0       EC221829
 *  mfear
 *  mfpvw
 *  mfsprg
 *  mtear
 *  mtsprg
 *  mfdbatl
 *  mfdbatu
 *  mtdbatl
 *  mtdbatu
 *  mttb
 *  mttbu
 *  mftb
 *  mftbu
 *  mfibatl
 *  mfibatu
 *  mtibatl
 *  mtibatu
 *
 * 23 Apr 2003 Kris Venstermans:
 *    Added instruction decoding for 64 bit architecture.
 *    Use of constant definitions instead of integers.
 *    Cleaned up some old power instructions.
 */

public class Disassembler implements ArchConstants {
  // special register name copied from /usr/include/sys/reg.h
  static final int IAR = 128;
  static final int MSR = 129;
  static final int CR = 130;
  static final int LR = 131;
  static final int CTR = 132;
  static final int XER = 133;
  static final int MQ = 134;
  static final int TID = 135;
  static final int FPSCR = 136;
  static final int FPINFO = 138;
  static final int FPSCRX = 148;

  // for testing purposes
  public static void main(String[] args) {

    int instr = Integer.parseInt(args[0], 16);
    int addr = Integer.parseInt(args[1], 16);
    System.out.println("instr = " + intAsHexString(instr) + " addr = " + intAsHexString(addr));
    System.out.println("result --> " + disasm(instr, addr));

  }

  static int bits(int x, int n, int m) {
    return ((x >> (31 - m)) & ((1 << (m - n + 1)) - 1));
  }

  static int signed(int x, int n, int m) {
    return ((x << n) >> 31 - m + n);
  }

  static String rname(int n) {
    String rvmName;
    if (n >= 0 && n <= 31) {
      rvmName = GPR_NAMES[n];
    } else if (n >= 128 && n <= 135) {
      rvmName = FPR_NAMES[n - 128];
    } else {
      switch (n) {
        case IAR:
          rvmName = "IAR";
          break;
        case MSR:
          rvmName = "MSR";
          break;
        case CR:
          rvmName = "CR";
          break;
        case LR:
          rvmName = "LR";
          break;
        case CTR:
          rvmName = "CTR";
          break;
        case XER:
          rvmName = "XER";
          break;
        case MQ:
          rvmName = "MQ";
          break;
        case TID:
          rvmName = "TID";
          break;
        case FPSCR:
          rvmName = "FPSCR";
          break;
        case FPINFO:
          rvmName = "FPINFO";
          break;
        case FPSCRX:
          rvmName = "FPSCRX";
          break;
        default:
          rvmName = "r" + n;
          break;
      }
    }
    return rvmName;
  }

  static final int INST_SZ = 4;

  /* Special register fields */
  static final int SPR_MQ = 0;
  static final int SPR_XER = 1;
  static final int SPR_LR = 8;
  static final int SPR_CTR = 9;
  static final int SPR_TID = 17;
  static final int SPR_DSISR = 18;
  static final int SPR_DAR = 19;
  static final int SPR_RTCU = 20;
  static final int SPR_RTCL = 21;
  static final int SPR_DEC = 22;
  static final int SPR_SDR0 = 24;
  static final int SPR_SDR1 = 25;
  static final int SPR_SRR0 = 26;
  static final int SPR_SRR1 = 27;

  /* Trap Options */
  static final int TO_LT = 16;
  static final int TO_GT = 8;
  static final int TO_EQ = 4;
  static final int TO_LLT = 2;
  static final int TO_LGT = 1;

  /* Different instruction formats */

  static final int D_FORM = 0;
  static final int B_FORM = 1;
  static final int I_FORM = 2;
  static final int SC_FORM = 3;
  static final int X_FORM = 4;
  static final int XL_FORM = 5;
  static final int XFX_FORM = 6;
  static final int XFL_FORM = 7;
  static final int XO_FORM = 8;
  static final int A_FORM = 9;
  static final int M_FORM = 10;
  static final int DS_FORM = 30;
  static final int XS_FORM = 31;
  static final int MD_FORM = 32;
  static final int MDS_FORM = 33;

  static final int EXTENDED = 11;
  static final int INVALID_OP = 12;

  /* Condition register fields */
  static final int CR_LT = 8;
  static final int CR_GT = 4;
  static final int CR_EQ = 2;
  static final int CR_SO = 1;

  static final int X = 9; // place holder

  /*
   * Instruction decoder.
   */
  static int destination;

  static int[] opcode_to_form = {
      /* table to convert opcode into */
      /* instruction formats          */

      /* FORM */
      INVALID_OP, /* OPCODE 00 */
      INVALID_OP, /* OPCODE 01 */
      D_FORM,     /* OPCODE 02 */
      D_FORM,     /* OPCODE 03 */
      D_FORM,     /* OPCODE 04 */
      D_FORM,     /* OPCODE 05 */
      D_FORM,     /* OPCODE 06 */
      D_FORM,     /* OPCODE 07 */
      D_FORM,     /* OPCODE 08 */
      D_FORM,     /* OPCODE 09 */
      D_FORM,     /* OPCODE 10 */
      D_FORM,     /* OPCODE 11 */
      D_FORM,     /* OPCODE 12 */
      D_FORM,     /* OPCODE 13 */
      D_FORM,     /* OPCODE 14 */
      D_FORM,     /* OPCODE 15 */
      B_FORM,     /* OPCODE 16 */
      SC_FORM,    /* OPCODE 17 */
      I_FORM,     /* OPCODE 18 */
      XL_FORM,    /* OPCODE 19 */
      M_FORM,     /* OPCODE 20 */
      M_FORM,     /* OPCODE 21 */
      M_FORM,     /* OPCODE 22 */
      M_FORM,     /* OPCODE 23 */
      D_FORM,     /* OPCODE 24 */
      D_FORM,     /* OPCODE 25 */
      D_FORM,     /* OPCODE 26 */
      D_FORM,     /* OPCODE 27 */
      D_FORM,     /* OPCODE 28 */
      D_FORM,     /* OPCODE 29 */
      EXTENDED,   /* OPCODE 30 */
      EXTENDED,   /* OPCODE 31 */
      D_FORM,     /* OPCODE 32 */
      D_FORM,     /* OPCODE 33 */
      D_FORM,     /* OPCODE 34 */
      D_FORM,     /* OPCODE 35 */
      D_FORM,     /* OPCODE 36 */
      D_FORM,     /* OPCODE 37 */
      D_FORM,     /* OPCODE 38 */
      D_FORM,     /* OPCODE 39 */
      D_FORM,     /* OPCODE 40 */
      D_FORM,     /* OPCODE 41 */
      D_FORM,     /* OPCODE 42 */
      D_FORM,     /* OPCODE 43 */
      D_FORM,     /* OPCODE 44 */
      D_FORM,     /* OPCODE 45 */
      D_FORM,     /* OPCODE 46 */
      D_FORM,     /* OPCODE 47 */
      D_FORM,     /* OPCODE 48 */
      D_FORM,     /* OPCODE 49 */
      D_FORM,     /* OPCODE 50 */
      D_FORM,     /* OPCODE 51 */
      D_FORM,     /* OPCODE 52 */
      D_FORM,     /* OPCODE 53 */
      D_FORM,     /* OPCODE 54 */
      D_FORM,     /* OPCODE 55 */
      INVALID_OP, /* OPCODE 56 */
      INVALID_OP, /* OPCODE 57 */
      DS_FORM,    /* OPCODE 58 */
      A_FORM,     /* OPCODE 59 */
      INVALID_OP, /* OPCODE 60 */
      INVALID_OP, /* OPCODE 61 */
      DS_FORM,    /* OPCODE 62 */
      EXTENDED    /* OPCODE 63 */};

  static OpcodeTab[] Dform = {

      /* Table for the D instruction format */

      /*   OPCD     EO                                format      mnemonic   */
      /*   ----     --                                ------      --------   */
      /*    0,      XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    1,      XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    2,      XXX,     */  new OpcodeTab(3, "tdi"),
      /*    3,      XXX,     */  new OpcodeTab(3, "twi"),
      /*    4,      XXX,     */  new OpcodeTab(X, "RESERVED"),

      /*    5,      XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    6,      XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    7,      XXX,     */  new OpcodeTab(0, "mulli"),
      /*    8,      XXX,     */  new OpcodeTab(0, "subfic"),
      /*    9,      XXX,     */  new OpcodeTab(X, "RESERVED"),

      /*    10,     XXX,     */  new OpcodeTab(4, "cmpli"),
      /*    11,     XXX,     */  new OpcodeTab(4, "cmpi"),
      /*    12,     XXX,     */  new OpcodeTab(0, "addic"),
      /*    13,     XXX,     */  new OpcodeTab(0, "addic."),
      /*    14,     XXX,     */  new OpcodeTab(2, "addi"),

      /*    15,     XXX,     */  new OpcodeTab(0, "addis"),
      /*    16,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    17,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    18,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    19,     XXX,     */  new OpcodeTab(X, "RESERVED"),

      /*    20,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    21,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    22,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    23,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    24,     XXX,     */  new OpcodeTab(1, "ori"),

      /*    25,     XXX,     */  new OpcodeTab(1, "oris"),
      /*    26,     XXX,     */  new OpcodeTab(1, "xori"),
      /*    27,     XXX,     */  new OpcodeTab(1, "xoris"),
      /*    28,     XXX,     */  new OpcodeTab(1, "andi. "),
      /*    29,     XXX,     */  new OpcodeTab(1, "andis."),

      /*    30,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    31,     XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    32,     XXX,     */  new OpcodeTab(2, "lwz"),
      /*    33,     XXX,     */  new OpcodeTab(2, "lwzu"),
      /*    34,     XXX,     */  new OpcodeTab(2, "lbz"),

      /*    35,     XXX,     */  new OpcodeTab(2, "lbzu"),
      /*    36,     XXX,     */  new OpcodeTab(2, "stw"),
      /*    37,     XXX,     */  new OpcodeTab(2, "stwu"),
      /*    38,     XXX,     */  new OpcodeTab(2, "stb"),
      /*    39,     XXX,     */  new OpcodeTab(2, "stbu"),

      /*    40,     XXX,     */  new OpcodeTab(2, "lhz"),
      /*    41,     XXX,     */  new OpcodeTab(2, "lhzu"),
      /*    42,     XXX,     */  new OpcodeTab(2, "lha"),
      /*    43,     XXX,     */  new OpcodeTab(2, "lhau"),
      /*    44,     XXX,     */  new OpcodeTab(2, "sth"),

      /*    45,     XXX,     */  new OpcodeTab(2, "sthu"),
      /*    46,     XXX,     */  new OpcodeTab(2, "lmw"),
      /*    47,     XXX,     */  new OpcodeTab(2, "stmw"),
      /*    48,     XXX,     */  new OpcodeTab(5, "lfs"),
      /*    49,     XXX,     */  new OpcodeTab(5, "lfsu"),

      /*    50,     XXX,     */  new OpcodeTab(5, "lfd"),
      /*    51,     XXX,     */  new OpcodeTab(5, "lfdu"),
      /*    52,     XXX,     */  new OpcodeTab(5, "stfs"),
      /*    53,     XXX,     */  new OpcodeTab(5, "stfsu"),
      /*    54,     XXX,     */  new OpcodeTab(5, "stfd"),

      /*    55,     XXX,     */  new OpcodeTab(5, "stfdu")};

  static OpcodeTab[] XLform = {

      /* Table for the XL instruction format */

      /*   OPCD      EO                            format     mnemonic      */
      /*   ----      --                            ------     --------      */
      /*    19,      0,      */  new OpcodeTab(2, "mcrf"),
      /*    19,      16,     */  new OpcodeTab(1, "bclr or bclrl"),
      /*    19,      33,     */  new OpcodeTab(3, "crnor"),
      /*    19,      50,     */  new OpcodeTab(0, "rfi"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),

      /*    19,      XXX,     */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      129,    */  new OpcodeTab(3, "crandc"),
      /*    19,      150,    */  new OpcodeTab(0, "isync"),

      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      193,    */  new OpcodeTab(3, "crxor"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      225,    */  new OpcodeTab(3, "crnand"),

      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      257,    */  new OpcodeTab(3, "crand"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      289,    */  new OpcodeTab(3, "creqv"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),

      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),

      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      417,    */  new OpcodeTab(3, "crorc"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      449,    */  new OpcodeTab(3, "cror"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),

      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      XXX,    */  new OpcodeTab(X, "RESERVED"),
      /*    19,      528,    */  new OpcodeTab(1, "bcctr or bcctrl")};

  /**
   *  Opcode 30 table:
   *  The key is bits 27 through 30 of the instruction.
   *  "Form" is the instruction format:
   *      I, B, SC, D, DS, X, XL, XFX, XFL, XO, A, M, MDS, MD
   *  "format" is how the instruction should be printed (specific to the disassembler)
   */

  static OpcodeXX[] opcode30 = {

      /*               key        form     format       mnemonic       */
      /*               ---        ----     ------       --------       */
      new OpcodeXX(0, MD_FORM, 0, "rldicl"),
      new OpcodeXX(1, MD_FORM, 0, "rldicl"),
      new OpcodeXX(2, MD_FORM, 1, "rldicr"),
      new OpcodeXX(3, MD_FORM, 1, "rldicr"),
      new OpcodeXX(4, MD_FORM, 0, "rldic"),
      new OpcodeXX(5, MD_FORM, 0, "rldic"),
      new OpcodeXX(6, MD_FORM, 0, "rldimi"),
      new OpcodeXX(7, MD_FORM, 0, "rldimi"),
      new OpcodeXX(8, MDS_FORM, 0, "rldcl"),
      new OpcodeXX(9, MDS_FORM, 1, "rldcr")};

  /**
   *  Opcode 31 table:
   *  The key is bits 21 through 31 of the instruction.
   *  "Form" is the instruction format:
   *      I, B, SC, D, DS, X, XL, XFX, XFL, XO, A, M
   *  "format" is how the instruction should be printed (specific to the disassembler)
   */

  static OpcodeXX[] opcode31 = {

      /*                  key        form     format       mnemonic       */
      /*                  ---        ----     ------       --------       */
      new OpcodeXX(0, X_FORM, 22, "cmp"),
      new OpcodeXX(8, X_FORM, 24, "tw"),
      new OpcodeXX(16, XO_FORM, 1, "subfc"),
      new OpcodeXX(17, XO_FORM, 1, "subfc."),
      new OpcodeXX(20, XO_FORM, 1, "addc"),
      new OpcodeXX(21, XO_FORM, 1, "addc."),
      new OpcodeXX(38, X_FORM, 2, "mfcr"),
      new OpcodeXX(46, X_FORM, 7, "lwzx"),
      new OpcodeXX(48, X_FORM, 8, "slw"),
      new OpcodeXX(49, X_FORM, 8, "slw."),
      new OpcodeXX(52, X_FORM, 9, "cntlzw"),
      new OpcodeXX(53, X_FORM, 9, "cntlzw."),
      new OpcodeXX(56, X_FORM, 8, "and"),
      new OpcodeXX(57, X_FORM, 8, "and."),
      new OpcodeXX(64, X_FORM, 22, "cmpl"),
      new OpcodeXX(110, X_FORM, 7, "lwzux"),
      new OpcodeXX(120, X_FORM, 8, "andc"),
      new OpcodeXX(121, X_FORM, 8, "andc."),
      new OpcodeXX(166, X_FORM, 2, "mfmsr"),
      new OpcodeXX(174, X_FORM, 7, "lbzx"),
      new OpcodeXX(208, XO_FORM, 0, "neg"),
      new OpcodeXX(209, XO_FORM, 0, "neg."),
      new OpcodeXX(238, X_FORM, 7, "lbzux"),
      new OpcodeXX(248, X_FORM, 8, "nor"),
      new OpcodeXX(249, X_FORM, 8, "nor."),
      new OpcodeXX(272, XO_FORM, 1, "subfe"),
      new OpcodeXX(273, XO_FORM, 1, "subfe."),
      new OpcodeXX(276, XO_FORM, 1, "adde"),
      new OpcodeXX(277, XO_FORM, 1, "adde."),
      new OpcodeXX(288, XFX_FORM, 9, "mtcrf"),
      new OpcodeXX(292, X_FORM, 2, "mtmsr"),
      new OpcodeXX(302, X_FORM, 7, "stwx"),
      new OpcodeXX(366, X_FORM, 7, "stwux"),
      new OpcodeXX(400, XO_FORM, 0, "subfze"),
      new OpcodeXX(401, XO_FORM, 0, "subfze."),
      new OpcodeXX(404, XO_FORM, 0, "addze"),
      new OpcodeXX(405, XO_FORM, 0, "addze."),
      new OpcodeXX(430, X_FORM, 7, "stbx"),
      new OpcodeXX(464, XO_FORM, 0, "subfme"),
      new OpcodeXX(465, XO_FORM, 0, "subfme."),
      new OpcodeXX(468, XO_FORM, 0, "addme"),
      new OpcodeXX(469, XO_FORM, 0, "addme."),
      new OpcodeXX(470, XO_FORM, 1, "mullw"),
      new OpcodeXX(471, XO_FORM, 1, "mullw."),
      new OpcodeXX(494, X_FORM, 7, "stbux"),
      new OpcodeXX(532, XO_FORM, 1, "add"),
      new OpcodeXX(533, XO_FORM, 1, "add."),
      new OpcodeXX(558, X_FORM, 7, "lhzx"),
      new OpcodeXX(568, X_FORM, 8, "eqv"),
      new OpcodeXX(569, X_FORM, 8, "eqv."),
      new OpcodeXX(612, X_FORM, 6, "tlbie"),
      new OpcodeXX(622, X_FORM, 7, "lhzux"),
      new OpcodeXX(632, X_FORM, 8, "xor"),
      new OpcodeXX(633, X_FORM, 8, "xor."),
      new OpcodeXX(678, X_FORM, 3, "mfspr"),
      new OpcodeXX(686, X_FORM, 7, "lhax"),
      new OpcodeXX(750, X_FORM, 7, "lhaux"),
      new OpcodeXX(814, X_FORM, 7, "sthx"),
      new OpcodeXX(824, X_FORM, 8, "orc"),
      new OpcodeXX(825, X_FORM, 8, "orc."),
      new OpcodeXX(878, X_FORM, 7, "sthux"),
      new OpcodeXX(888, X_FORM, 8, "or"),
      new OpcodeXX(889, X_FORM, 8, "or."),
      new OpcodeXX(934, X_FORM, 3, "mtspr"),
      new OpcodeXX(952, X_FORM, 8, "nand"),
      new OpcodeXX(953, X_FORM, 8, "nand."),
      new OpcodeXX(1024, X_FORM, 23, "mcrxr"),
      new OpcodeXX(1040, XO_FORM, 1, "subfco"),
      new OpcodeXX(1041, XO_FORM, 1, "subfco."),
      new OpcodeXX(1044, XO_FORM, 1, "addco"),
      new OpcodeXX(1045, XO_FORM, 1, "addco."),
      new OpcodeXX(1066, X_FORM, 7, "lswx"),
      new OpcodeXX(1068, X_FORM, 7, "lwbrx"),
      new OpcodeXX(1070, X_FORM, 12, "lfsx"),
      new OpcodeXX(1072, X_FORM, 8, "srw"),
      new OpcodeXX(1073, X_FORM, 8, "srw."),
      new OpcodeXX(1134, X_FORM, 12, "lfsux"),
      new OpcodeXX(1194, X_FORM, 10, "lswi"),
      new OpcodeXX(1196, X_FORM, 1, "sync"),
      new OpcodeXX(1198, X_FORM, 12, "lfdx"),
      new OpcodeXX(1262, X_FORM, 12, "lfdux"),
      new OpcodeXX(1232, XO_FORM, 0, "nego"),
      new OpcodeXX(1233, XO_FORM, 0, "nego."),
      new OpcodeXX(1296, XO_FORM, 1, "subfeo"),
      new OpcodeXX(1297, XO_FORM, 1, "subfeo."),
      new OpcodeXX(1300, XO_FORM, 1, "addeo"),
      new OpcodeXX(1301, XO_FORM, 1, "addeo."),
      new OpcodeXX(1322, X_FORM, 7, "stswx"),
      new OpcodeXX(1324, X_FORM, 7, "stwbrx"),
      new OpcodeXX(1326, X_FORM, 12, "stfsx"),
      new OpcodeXX(1390, X_FORM, 12, "stfsux"),
      new OpcodeXX(1424, XO_FORM, 0, "subfzeo"),
      new OpcodeXX(1425, XO_FORM, 0, "subfzeo."),
      new OpcodeXX(1428, XO_FORM, 0, "addzeo."),
      new OpcodeXX(1429, XO_FORM, 0, "addzeo."),
      new OpcodeXX(1450, X_FORM, 10, "stswi"),
      new OpcodeXX(1454, X_FORM, 12, "stfdx"),
      new OpcodeXX(1488, XO_FORM, 0, "subfmeo."),
      new OpcodeXX(1489, XO_FORM, 0, "subfmeo."),
      new OpcodeXX(1492, XO_FORM, 0, "addmeo"),
      new OpcodeXX(1493, XO_FORM, 0, "addmeo."),
      new OpcodeXX(1494, XO_FORM, 1, "mullwo."),
      new OpcodeXX(1495, XO_FORM, 1, "mullwo."),
      new OpcodeXX(1518, X_FORM, 12, "stfdux"),
      new OpcodeXX(1556, XO_FORM, 1, "addo"),
      new OpcodeXX(1557, XO_FORM, 1, "addo."),
      new OpcodeXX(1580, X_FORM, 7, "lhbrx"),
      new OpcodeXX(1584, X_FORM, 8, "sraw"),
      new OpcodeXX(1585, X_FORM, 8, "sraw."),
      new OpcodeXX(1648, X_FORM, 11, "srawi"),
      new OpcodeXX(1649, X_FORM, 11, "srawi."),
      new OpcodeXX(1836, X_FORM, 7, "sthbrx"),
      new OpcodeXX(1844, X_FORM, 9, "extsh"),
      new OpcodeXX(1845, X_FORM, 9, "extsh."),
      new OpcodeXX(2028, X_FORM, 6, "dcbz"),

      // these are the addition for the PowerPC
      new OpcodeXX(172, X_FORM, 6, "dcbf"),
      new OpcodeXX(940, X_FORM, 6, "dcbi"),
      new OpcodeXX(108, X_FORM, 6, "dcbst"),
      new OpcodeXX(556, X_FORM, 6, "dcbt"),
      new OpcodeXX(492, X_FORM, 6, "dcbtst"),
      new OpcodeXX(982, XO_FORM, 1, "divw"),
      new OpcodeXX(983, XO_FORM, 1, "divw."),
      new OpcodeXX(2006, XO_FORM, 1, "divwo"),
      new OpcodeXX(2007, XO_FORM, 1, "divwo."),
      new OpcodeXX(918, XO_FORM, 1, "divwu"),
      new OpcodeXX(919, XO_FORM, 1, "divwu."),
      new OpcodeXX(1942, XO_FORM, 1, "divwuo"),
      new OpcodeXX(1943, XO_FORM, 1, "divwuo."),
      new OpcodeXX(1708, X_FORM, 1, "eieio"),
      new OpcodeXX(1908, X_FORM, 0, "extsb"),
      new OpcodeXX(1909, X_FORM, 0, "extsb."),
      new OpcodeXX(1964, X_FORM, 6, "icbi"),
      new OpcodeXX(40, X_FORM, 7, "lwarx"),
      new OpcodeXX(150, XO_FORM, 1, "mulhw"),
      new OpcodeXX(151, XO_FORM, 1, "mulhw."),
      new OpcodeXX(22, XO_FORM, 1, "mulhwu"),
      new OpcodeXX(23, XO_FORM, 1, "mulhwu."),
      new OpcodeXX(301, X_FORM, 7, "stwcx."),
      new OpcodeXX(80, XO_FORM, 1, "subf"),
      new OpcodeXX(81, XO_FORM, 1, "subf."),
      new OpcodeXX(1104, XO_FORM, 1, "subfo"),
      new OpcodeXX(1105, XO_FORM, 1, "subfo."),

// these are only valid for 32 bit architecture
new OpcodeXX(420, X_FORM, 17, "mtsr"),
new OpcodeXX(484, X_FORM, 7, "mtsrin"),
new OpcodeXX(1190, X_FORM, 18, "mfsr"),
new OpcodeXX(1318, X_FORM, 4, "mfsrin"),

// these are the addition for the 64 bit specific instructions
new OpcodeXX(18, XO_FORM, 2, "mulhdu"),
new OpcodeXX(19, XO_FORM, 2, "mulhdu."),
new OpcodeXX(42, X_FORM, 7, "ldx"),
new OpcodeXX(43, X_FORM, 7, "ldx."),
new OpcodeXX(54, X_FORM, 8, "sld"),
new OpcodeXX(55, X_FORM, 8, "sld."),
new OpcodeXX(106, X_FORM, 7, "ldux"),
new OpcodeXX(107, X_FORM, 7, "ldux."),
new OpcodeXX(116, X_FORM, 9, "cntlzd"),
new OpcodeXX(117, X_FORM, 9, "cntlzd."),
new OpcodeXX(136, X_FORM, 24, "td"),
new OpcodeXX(146, XO_FORM, 1, "mulhd"),
new OpcodeXX(151, XO_FORM, 1, "mulhd."),
new OpcodeXX(168, X_FORM, 7, "ldarx"),
new OpcodeXX(298, X_FORM, 7, "stdx"),
new OpcodeXX(362, X_FORM, 7, "stdux"),
new OpcodeXX(429, X_FORM, 7, "stdcx."),
new OpcodeXX(466, XO_FORM, 1, "mulld"),
new OpcodeXX(467, XO_FORM, 1, "mulld."),
new OpcodeXX(682, X_FORM, 7, "lwax"),
new OpcodeXX(746, X_FORM, 7, "lwaux"),
new OpcodeXX(1652, XS_FORM, 0, "sradi"),
new OpcodeXX(1653, XS_FORM, 0, "sradi."),
new OpcodeXX(1654, XS_FORM, 0, "sradi"),
new OpcodeXX(1655, XS_FORM, 0, "sradi."),
new OpcodeXX(868, X_FORM, 6, "slbie"),
new OpcodeXX(914, XO_FORM, 1, "divdu"),
new OpcodeXX(915, XO_FORM, 1, "divdu."),
new OpcodeXX(978, XO_FORM, 1, "divd"),
new OpcodeXX(979, XO_FORM, 1, "divd."),
new OpcodeXX(996, X_FORM, 1, "slbia"),
new OpcodeXX(1078, X_FORM, 8, "srd"),
new OpcodeXX(1079, X_FORM, 8, "srd."),
new OpcodeXX(1588, X_FORM, 8, "srad"),
new OpcodeXX(1589, X_FORM, 8, "srad."),
new OpcodeXX(1972, X_FORM, 9, "extsw"),
new OpcodeXX(1973, X_FORM, 9, "extsw.")};

  static OpcodeTab[] opcode58 = {
      /* Table for the instruction format of opcode 58*/

      /*    EO                            format      mnemonic   */
      /*   ----                           ------      --------   */
      /*    0,    */  new OpcodeTab(0, "ld"),
      /*    1,    */  new OpcodeTab(0, "ldu"),
      /*    2,    */  new OpcodeTab(0, "lwa"),
      /*    3,    */  new OpcodeTab(X, "RESERVED")};

  static OpcodeTab[] opcode62 = {
      /* Table for the instruction format of opcode 58*/

      /*    EO                            format      mnemonic   */
      /*   ----                           ------      --------   */
      /*    0,    */  new OpcodeTab(1, "std"),
      /*    1,    */  new OpcodeTab(1, "stdu"),
      /*    2,    */  new OpcodeTab(X, "RESERVED"),
      /*    3,    */  new OpcodeTab(X, "RESERVED")};

/*  Opcode 63 table: The key is computed by taking
 *  bits 21 through 31 of the instruction. "Form" is
 *  the instruction format and "format" is how the
 *  instruction should be printed.  */

  static OpcodeXX[] opcode63 = {

      /*                  key        form     format       mnemonic       */
      /*                  ---        ----     ------       --------       */
      new OpcodeXX(0, X_FORM, 19, "fcmpu"),
      new OpcodeXX(24, X_FORM, 21, "frsp"),
      new OpcodeXX(25, X_FORM, 21, "frsp."),
      new OpcodeXX(28, X_FORM, 21, "fctiw"),
      new OpcodeXX(29, X_FORM, 21, "fctiw."),
      new OpcodeXX(30, X_FORM, 21, "fctiwz"),
      new OpcodeXX(31, X_FORM, 21, "fctiwz."),
      new OpcodeXX(64, X_FORM, 19, "fcmpo"),
      new OpcodeXX(76, X_FORM, 16, "mtfsb1"),
      new OpcodeXX(77, X_FORM, 16, "mtfsb1."),
      new OpcodeXX(80, X_FORM, 21, "fneg"),
      new OpcodeXX(81, X_FORM, 21, "fneg."),
      new OpcodeXX(128, X_FORM, 14, "mcrfs"),
      new OpcodeXX(140, X_FORM, 16, "mtfsb0"),
      new OpcodeXX(141, X_FORM, 16, "mtfsb0."),
      new OpcodeXX(144, X_FORM, 21, "fmr"),
      new OpcodeXX(145, X_FORM, 21, "fmr."),
      new OpcodeXX(268, X_FORM, 15, "mtfsfi"),
      new OpcodeXX(269, X_FORM, 15, "mtfsfi."),
      new OpcodeXX(272, X_FORM, 21, "fnabs"),
      new OpcodeXX(273, X_FORM, 21, "fnabs."),
      new OpcodeXX(528, X_FORM, 21, "fabs"),
      new OpcodeXX(529, X_FORM, 21, "fabs."),
      new OpcodeXX(1166, X_FORM, 13, "mffs"),
      new OpcodeXX(1167, X_FORM, 13, "mffs."),
      new OpcodeXX(1422, XFL_FORM, 9, "mtfsf"),
      new OpcodeXX(1423, XFL_FORM, 9, "mtfsf."),

      // these are only valid for 32 bit architecture
      new OpcodeXX(1628, X_FORM, 21, "fctid"),
      new OpcodeXX(1629, X_FORM, 21, "fctid."),
      new OpcodeXX(1630, X_FORM, 21, "fctidz"),
      new OpcodeXX(1631, X_FORM, 21, "fctidz."),
      new OpcodeXX(1692, X_FORM, 21, "fcfid"),
      new OpcodeXX(1693, X_FORM, 21, "fcfid.")};

  static OpcodeXX[] opcode59 = {

      /*  opcode59 table: These are the addition for the PowerPC set
      *  Key is  bits 26 through 31 of the instruction.
      *  "Form" is the instruction format and "format" is how the
      *  instruction should be printed (the enumeration is specific
      *  to each decode method)
      */

      /*                  key        form     format       mnemonic       */
      new OpcodeXX(42, A_FORM, 0, "fadds"),
      new OpcodeXX(43, A_FORM, 0, "fadds."),
      new OpcodeXX(36, A_FORM, 0, "fdivs"),
      new OpcodeXX(37, A_FORM, 0, "fdivs."),
      new OpcodeXX(58, A_FORM, 2, "fmadds"),
      new OpcodeXX(59, A_FORM, 2, "fmadds."),
      new OpcodeXX(56, A_FORM, 2, "fmsubs"),
      new OpcodeXX(57, A_FORM, 2, "fmsubs."),
      new OpcodeXX(50, A_FORM, 1, "fmuls"),
      new OpcodeXX(51, A_FORM, 1, "fmuls."),
      new OpcodeXX(62, A_FORM, 2, "fnmadds"),
      new OpcodeXX(63, A_FORM, 2, "fnmadds."),
      new OpcodeXX(60, A_FORM, 2, "fnmsubs"),
      new OpcodeXX(61, A_FORM, 2, "fnmsubs."),
      new OpcodeXX(40, A_FORM, 0, "fsubs"),
      new OpcodeXX(41, A_FORM, 0, "fsubs.")};

  static OpcodeXX[] Aform = {

      /*  Aform table: The key is computed by taking
*  bits 26 through 31 of the instruction. "Form" is
*  the instruction format and "format" is how the
*  instruction should be printed.  */

      /*                  key        form     format       mnemonic       */
      new OpcodeXX(36, A_FORM, 0, "fdiv"),
      new OpcodeXX(37, A_FORM, 0, "fdiv."),
      new OpcodeXX(40, A_FORM, 0, "fsub"),
      new OpcodeXX(41, A_FORM, 0, "fsub."),
      new OpcodeXX(42, A_FORM, 0, "fadd"),
      new OpcodeXX(43, A_FORM, 0, "fadd."),
      new OpcodeXX(50, A_FORM, 1, "fm"),
      new OpcodeXX(51, A_FORM, 1, "fm."),
      new OpcodeXX(56, A_FORM, 2, "fmsub"),
      new OpcodeXX(57, A_FORM, 2, "fmsub."),
      new OpcodeXX(58, A_FORM, 2, "fmadd"),
      new OpcodeXX(59, A_FORM, 2, "fmadd."),
      new OpcodeXX(60, A_FORM, 2, "fnmsub"),
      new OpcodeXX(61, A_FORM, 2, "fnmsub."),
      new OpcodeXX(62, A_FORM, 2, "fnmadd"),
      new OpcodeXX(63, A_FORM, 2, "fnmadd.")};

  /*
   *  SPR_name - common special purpose register names options
   */
  static String SPR_name(int SPR) {
    switch (SPR) {
      case SPR_MQ:
        return ("mq");
      case SPR_XER:
        return ("xer");
      case SPR_LR:
        return ("lr");
      case SPR_CTR:
        return ("ctr");
      case SPR_TID:
        return ("tid");
      case SPR_DSISR:
        return ("dsisr");
      case SPR_DAR:
        return ("dar");
      case SPR_RTCU:
        return ("rtcu");
      case SPR_RTCL:
        return ("rtcu");
      case SPR_DEC:
        return ("dec");
      case SPR_SDR0:
        return ("sdr0");
      case SPR_SDR1:
        return ("sdr1");
      case SPR_SRR0:
        return ("srr0");
      case SPR_SRR1:
        return ("srr1");
      default:
        return null;
    }
  }

  /*
   *  TO_ext - common trap options
   */
  static String TO_ext(int TO) {
    switch (TO) {
      case TO_LT:
        return ("lt");
      case(TO_LT | TO_EQ):
        return ("le");
      case(TO_LT | TO_GT):
        return ("ne");
      case TO_GT:
        return ("gt");
      case(TO_GT | TO_EQ):
        return ("ge");
      case TO_LLT:
        return ("llt");
      case(TO_LLT | TO_EQ):
        return ("lle");
      case(TO_LLT | TO_LGT):
        return ("lne");
      case TO_LGT:
        return ("lgt");
      case(TO_LGT | TO_EQ):
        return ("lge");
      case TO_EQ:
        return ("eq");
      default:
        return null;
    }
  }

  /*
   *  Translate an instruction from its
   *  numeric form into something more
   *  readable.
   */

  public static String disasm(int inst, int addr) {
    int opcode;
    int form;

    opcode = bits(inst, 0, 5);                /* Determine opcode */
    form = opcode_to_form[opcode];           /* Determine instruction format */

    switch (form)     /* decode known instruction format */ {
      case D_FORM:
        return decode_Dform(inst, opcode);
      case DS_FORM:
        return decode_DSform(inst, opcode);
      case B_FORM:
        return decode_Bform(addr, inst, opcode);
      case I_FORM:
        return decode_Iform(addr, inst, opcode);
      case SC_FORM:
        return decode_SCform(inst, opcode);
      case XL_FORM:
        return decode_XLform(inst, opcode);
      case M_FORM:
        return decode_Mform(inst, opcode);
      case A_FORM:
        return decode_opcode59(inst);
      case EXTENDED:      /* More work to do... */
        switch (opcode)   /* Switch off of opcode and process from there */ {
          case 30:
            return decode_opcode30(inst);
          case 31:
            return decode_opcode31(inst);
          case 63:
            return decode_opcode63(inst);
          default:
            return "    Invalid opcode";
        }
      case INVALID_OP:      /* More work to do... */
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the D instruction format */

  static String decode_Dform(int inst, int opcode) {
    int rt, RA, TO, BF, FRT, ufield;
    int sfield;
    OpcodeTab opcode_info;
    String datafield, mnemonic, asm_mnemonic, common_opt;

    rt = TO = FRT = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    BF = bits(inst, 6, 8);
    ufield = inst & 0xffff;
    sfield = (inst << 16) >> 16;
    if (sfield < 0) {
      datafield = "" + sfield;
    } else {
      datafield = intAsHexString(sfield);
    }
    opcode_info = Dform[opcode];
    mnemonic = opcode_info.mnemonic;

    switch (opcode_info.format) {
      case 0:
        if (opcode != 15) {
          return "        ".substring(mnemonic.length()) +
                 mnemonic +
                 "   " +
                 rname(rt) +
                 "," +
                 rname(RA) +
                 "," +
                 datafield;
        } else {
          if (RA != 0) {
            return "        ".substring(mnemonic.length()) +
                   mnemonic +
                   "   " +
                   rname(rt) +
                   "," +
                   rname(RA) +
                   "," +
                   intAsHexString(ufield);
          } else {
            return "     liu   " + rname(rt) + "," + intAsHexString(ufield);
          }
        }
      case 1:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(RA) +
               "," +
               rname(rt) +
               "," +
               intAsHexString(ufield);
      case 2:
        if ((opcode == 14) && (RA == 0)) {
          return "     lil" + "   " + rname(rt) + "," + datafield;
        } else {
          return "        ".substring(mnemonic.length()) +
                 mnemonic +
                 "   " +
                 rname(rt) +
                 "," +
                 datafield +
                 "(" +
                 rname(RA) +
                 ")";
        }
      case 3: /* Trap immediate */
        common_opt = TO_ext(TO);
        asm_mnemonic = ((opcode == 2) ? "td" : "tw") + common_opt + "i";
        if (common_opt != null) {
          return "        ".substring(asm_mnemonic.length()) + asm_mnemonic + "   " + rname(RA) + "," + datafield;
        } else {
          return "        ".substring(mnemonic.length()) + mnemonic + "   " + TO + "," + rname(RA) + "," + datafield;
        }
      case 4:
        int L = inst & 0x00200000;
        if (opcode == 11) {
          return "       ".substring(mnemonic.length()) +
                 mnemonic +
                 ((L == 0) ? "W" : "D") +
                 "   cr" +
                 BF +
                 "," +
                 rname(RA) +
                 "," +
                 datafield;
        } else {
          return "       ".substring(mnemonic.length()) +
                 mnemonic +
                 ((L == 0) ? "W" : "D") +
                 "   cr" +
                 BF +
                 "," +
                 rname(RA) +
                 "," +
                 ufield;
        }
      case 5:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRT + "," + rname(RA) + "," + datafield;
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the DS instruction format */

  static String decode_DSform(int inst, int opcode) {
    int XO, RT, RA, sfield;
    String datafield, mnemonic;

    XO = bits(inst, 30, 31);
    RT = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    sfield = (((inst) & 0xfffc) << 16) >> 16;
    datafield = intAsHexString(sfield);

    switch (opcode) {
      case 58:
        mnemonic = opcode58[XO].mnemonic;
        if (mnemonic == "RESERVED") return "    Invalid opcode";
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(RT) +
               ", " +
               datafield +
               "(" +
               rname(RA) +
               ")";
      case 62:
        mnemonic = opcode62[XO].mnemonic;
        if (mnemonic == "RESERVED") return "    Invalid opcode";
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(RT) +
               ", " +
               datafield +
               "(" +
               rname(RA) +
               ")";
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the B instruction format */

  static String decode_Bform(int addr, int inst, int opcode) {
    int AA, LK, BO, BI;
    String mnemonic;
    int cr_field;
    int target;

    AA = bits(inst, 30, 30);
    LK = bits(inst, 31, 31);
    BO = bits(inst, 6, 10);
    BI = bits(inst, 11, 15);
    target = (((inst) & 0xfffc) << 16) >> 16;
    if (AA == 0) target += addr;
    mnemonic = build_branch_op(BO, 1 << (3 - (BI & 3)), LK, AA, 0);
    destination = target;
    cr_field = (BI >> 2);
    /* Build disassembly without target, added on later... */
    if (cr_field != 0) {/* Not CR 0 ? */
      return "        ".substring(mnemonic.length()) +
             mnemonic +
             "   " +
             cr_field +
             " " +
             Integer.toHexString(destination);
    } else {
      return "        ".substring(mnemonic.length()) + mnemonic + "   " + Integer.toHexString(destination);
    }
  }

  /* Decode the I instruction format */

  static String decode_Iform(int addr, int inst, int opcode) {
    int target;
    int AA, LK;
    String mnemonic;

    AA = bits(inst, 30, 30);
    LK = bits(inst, 31, 31);
    target = (((inst) & ~3) << 6) >> 6;
    if (AA != 0) {
      mnemonic = (LK != 0) ? "bla" : "ba";
    } else {
      target += addr;
      mnemonic = (LK != 0) ? "bl" : "b";
    }
    destination = target;
    return "        ".substring(mnemonic.length()) + mnemonic + "   " + Integer.toHexString(destination);
  }

  /* Decode the SC instruction format */

  static String decode_SCform(int inst, int opcode) {
    int SA, LK, LEV, FL1, FL2, SV;

    SA = bits(inst, 30, 30);
    LK = bits(inst, 31, 31);
    if (SA != 0) {
      SV = bits(inst, 16, 29);
      String mnemonic = (LK != 0) ? "svcla" : "svca";
      return "        ".substring(mnemonic.length()) + mnemonic + "   " + intAsHexString(SV);
    } else {
      LEV = bits(inst, 20, 26);
      FL1 = bits(inst, 16, 19);
      FL2 = bits(inst, 27, 29);
      String mnemonic = (LK != 0) ? "svcl" : "svc";
      return "        ".substring(mnemonic.length()) +
             mnemonic +
             "   " +
             intAsHexString(LEV) +
             "," +
             intAsHexString(FL1) +
             "," +
             intAsHexString(FL2);
    }
  }

  /* Decode the XL instruction format */

  static String decode_XLform(int inst, int opcode) {
    String mnemonic;
    int ext_opcode;
    int LK, BO, BI, BB;
    int BF, BFA, BT, BA;
    OpcodeTab opcode_info;
    int cr_field;
    String branch_name;

    ext_opcode = bits(inst, 21, 30);
    opcode_info = XLform[ext_opcode >> 4]; /* shift to get XL table index */
    mnemonic = opcode_info.mnemonic;
    switch (opcode_info.format) {
      case 0:
        return "        ".substring(mnemonic.length()) + mnemonic;
      case 1:
        BO = bits(inst, 6, 10);
        BI = bits(inst, 11, 15);
        LK = bits(inst, 31, 31);
        cr_field = (byte) (BI >> 2);
        branch_name = build_branch_op(BO, 1 << (3 - (BI & 3)), LK, 0, ext_opcode);
        if (cr_field != 0) {/* Not CR 0 ? */
          return "        ".substring(branch_name.length()) + branch_name + "   " + cr_field;
        } else {
          return "        ".substring(branch_name.length()) + branch_name;
        }
      case 2:
        BF = bits(inst, 6, 10);
        BFA = bits(inst, 11, 13);
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF + "," + intAsHexString(BFA);
      case 3:
        BT = bits(inst, 6, 10);
        BA = bits(inst, 11, 15);
        BB = bits(inst, 16, 20);
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               intAsHexString(BT) +
               "," +
               intAsHexString(BA) +
               "," +
               intAsHexString(BB);
      default:
        return "    Invalid opcode";
    }
  }

  static String[] Mforms = {"rlimi", "rlimi.", "rlinm", "rlinm.", "rlmi", "rlmi.", "rlnm", "rlnm."};

  /* Decode the M instruction format */

  static String decode_Mform(int inst, int opcode) {
    int RS, RA;
    int MB, ME, Rc;
    int SH_RB;
    String asm_mnemonic;

    RS = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    SH_RB = bits(inst, 16, 20);
    MB = bits(inst, 21, 25);
    ME = bits(inst, 26, 30);
    Rc = inst & 1;

    asm_mnemonic = Mforms[(opcode - 20) * 2 + Rc];
    if ((opcode < 20) || ((opcode >>> 23) != 0)) {
      return "    Invalid opcode";
    } else {
      if (opcode == 21) { /* sri and sli are special forms of rlmni */
        if ((ME == 32) && (MB == (31 - SH_RB))) {
          String mnemonic = (Rc != 0) ? "sri." : "sri";
          return "        ".substring(mnemonic.length()) +
                 mnemonic +
                 "   " +
                 rname(RA) +
                 "," +
                 rname(RS) +
                 "," +
                 intAsHexString(MB);
        } else if ((MB == 0) && (SH_RB == (31 - ME))) {
          String mnemonic = (Rc != 0) ? "sli." : "sli";
          return "        ".substring(mnemonic.length()) +
                 mnemonic +
                 "   " +
                 rname(RA) +
                 "," +
                 rname(RS) +
                 "," +
                 intAsHexString(SH_RB);
        } else {
          return "        ".substring(asm_mnemonic.length()) +
                 asm_mnemonic +
                 "   " +
                 rname(RA) +
                 "," +
                 rname(RS) +
                 "," +
                 intAsHexString(SH_RB) +
                 "," +
                 intAsHexString(MB) +
                 "," +
                 intAsHexString(ME);
        }
      } else {
        return "        ".substring(asm_mnemonic.length()) +
               asm_mnemonic +
               "   " +
               rname(RA) +
               "," +
               rname(RS) +
               "," +
               intAsHexString(SH_RB) +
               "," +
               intAsHexString(MB) +
               "," +
               intAsHexString(ME);
      }
    }
  }

  static OpcodeXX searchXX(int key, OpcodeXX[] where) {

    for (OpcodeXX opxx : where) {
      if (opxx.key == key) return opxx;
    }

    return null;

  }

  /* Decode opcode 30 and then the relevent format */

  static String decode_opcode30(int inst) {
    OpcodeXX search_results;
    String mnemonic;

    int testkey = bits(inst, 27, 30);
    search_results = searchXX(testkey, opcode30);

    if (search_results == null) {
      return "    Invalid opcode";
    }

    mnemonic = search_results.mnemonic;

    switch (search_results.form) {
      case MDS_FORM:
        return decode_MDSform(inst, mnemonic);
      case MD_FORM:
        return decode_MDform(inst, mnemonic);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the MD instruction format */
  static String decode_MDform(int inst, String mnemonic) {
    int RS, RA, SH, MB;

    RS = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    SH = ((inst & 0x2) >> 4) | bits(inst, 16, 20);
    MB = (inst & 0x20) | bits(inst, 21, 25);
    return "        ".substring(mnemonic.length()) + mnemonic + rname(RA) + ", " + rname(RS) + ", " + SH + ", " + MB;
  }

  /* Decode the MDS instruction format */
  static String decode_MDSform(int inst, String mnemonic) {
    int RS, RA, RB, MB;

    RS = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    RB = bits(inst, 16, 20);
    MB = (inst & 0x20) | bits(inst, 21, 25);
    return "        ".substring(mnemonic.length()) +
           mnemonic +
           rname(RA) +
           ", " +
           rname(RS) +
           ", " +
           rname(RB) +
           ", " +
           MB;
  }

  /* Decode the A instruction format */
  /* Decode opcode 31 and then the relevent format */

  static String decode_opcode31(int inst) {
    OpcodeXX search_results;
    int format;
    String mnemonic;

    int testkey = bits(inst, 21, 31);
    search_results = searchXX(testkey, opcode31);

    if (search_results == null) {
      return "    Invalid opcode";
    }

    mnemonic = search_results.mnemonic;
    format = search_results.format;
    switch (search_results.form) {
      case X_FORM:
        return decode_Xform(inst, mnemonic, format, testkey);
      case XFX_FORM:
        return decode_XFXform(inst);
      case XO_FORM:
        return decode_XOform(inst, mnemonic, format);
      case XS_FORM:
        return decode_XSform(inst, mnemonic, format);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the X instruction format */

  static String decode_Xform(int inst, String mnemonic, int format, int ext_op) {
    int rt, RA, RB, NB, SH, FRS, SPR, FRT;
    int BF, BFA, I, BT, SR, FRA, FRB, TO;
    String asm_mnemonic, common_opt;

    FRS = FRT = TO = BT = rt = bits(inst, 6, 10);
    FRB = NB = SH = RB = I = bits(inst, 16, 20);
    FRA = SPR = SR = RA = bits(inst, 11, 15);
    BFA = bits(inst, 11, 13);
    I = bits(inst, 16, 19);
    BF = bits(inst, 6, 8);

    switch (format) {
      case 0:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(RA) + "," + rname(rt);
      case 1:
        return "        ".substring(mnemonic.length()) + mnemonic;
      case 2:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(rt);
      case 3:
        common_opt = SPR_name(SPR);
        if (common_opt != null) {
          asm_mnemonic = mnemonic.substring(0, 2) + common_opt;
          return "        ".substring(asm_mnemonic.length()) + asm_mnemonic + "   " + rname(rt);
        } else {/* reserved register? */
          return "        ".substring(mnemonic.length()) + mnemonic + "   " + intAsHexString(SPR) + "," + rname(rt);
        }
      case 4:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(rt) + "," + rname(RB);
      case 5:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(rt) + "," + rname(RA);
      case 6:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(RA) + "," + rname(RB);
      case 7:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(rt) +
               "," +
               rname(RA) +
               "," +
               rname(RB);
      case 8:
        if ((ext_op == 888) || (ext_op == 889)) {
          if (rt == RB) {
            String mne = (ext_op == 889) ? "mr." : "mr";
            return "        ".substring(mne.length()) + mne + "   " + rname(RA) + "," + rname(rt);
          }
        }
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(RA) +
               "," +
               rname(rt) +
               "," +
               rname(RB);
      case 9:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(RA) + "," + rname(rt);
      case 10:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(rt) +
               "," +
               rname(RA) +
               "," +
               intAsHexString(NB);
      case 11:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(RA) +
               "," +
               rname(rt) +
               "," +
               intAsHexString(SH);
      case 12:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRS + "," + rname(RA) + "," + rname(RB);
      case 13:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRT;
      case 14:
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF + "," + intAsHexString(BFA);
      case 15:
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF + "," + intAsHexString(I);
      case 16:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + intAsHexString(BT);
      case 17:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + intAsHexString(SR) + "," + rname(rt);
      case 18:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(rt) + "," + intAsHexString(SR);
      case 19:
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF + ",fr" + FRA + ",fr" + FRB;
      case 20:
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF + ",fr" + FRB;
      case 21:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRT + ",fr" + FRB;
      case 22:
        int L = inst & 0x00200000;
        return "       ".substring(mnemonic.length()) +
               mnemonic +
               ((L == 0) ? "W" : "D") +
               "   cr" +
               BF +
               "," +
               rname(RA) +
               "," +
               rname(RB);
      case 23:
        return "        ".substring(mnemonic.length()) + mnemonic + "   cr" + BF;
      case 24:
        common_opt = TO_ext(TO);
        if (common_opt != null) {
          asm_mnemonic = "td" + common_opt;
          return "        ".substring(asm_mnemonic.length()) + asm_mnemonic + "   " + rname(RA) + "," + rname(RB);
        } else {
          return "        ".substring(mnemonic.length()) + mnemonic + "   " + TO + "," + rname(RA) + "," + rname(RB);
        }
      case 25:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(rt) +
               "," +
               rname(RA) +
               "," +
               rname(RB);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the XFX instruction format */

  static String decode_XFXform(int inst) {
    int rt, FXM;

    rt = bits(inst, 6, 10);
    FXM = bits(inst, 12, 19);
    if (FXM == 0xff) {
      return "    mtcr   " + rname(rt);
    } else {
      return "   mtcrf   " + intAsHexString(FXM) + "," + rname(rt);
    }
  }

  /* Decode the XO instruction format */

  static String decode_XOform(int inst, String mnemonic, int format) {
    int rt, RA, RB;

    rt = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    switch (format) {
      case 0:
        return "        ".substring(mnemonic.length()) + mnemonic + "   " + rname(rt) + "," + rname(RA);
      case 1:
      case 2:
        RB = bits(inst, 16, 20);
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   " +
               rname(rt) +
               "," +
               rname(RA) +
               "," +
               rname(RB);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the XS instruction format */
  static String decode_XSform(int inst, String mnemonic, int format) {
    int RS, RA, SH;

    RS = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    SH = ((inst & 0x2) >> 4) | bits(inst, 16, 20);
    return "        ".substring(mnemonic.length()) + mnemonic + rname(RA) + ", " + rname(RS) + ", " + SH;
  }

  /* Decode opcode 59 and then the relevent format */

  static String decode_opcode59(int inst) {
    OpcodeXX search_results;
    String mnemonic;

    int testkey = bits(inst, 26, 31);

    search_results = searchXX(testkey, opcode59);

    if (search_results == null) {
      return "    Invalid opcode";
    }

    mnemonic = search_results.mnemonic;
    int format = search_results.format;

    // All opcode 59 are in A form
    switch (search_results.form) {
      case A_FORM:
        return decode_Aform(inst, mnemonic, format);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode opcode 63 and then the relevent format */

  static String decode_opcode63(int inst) {
    OpcodeXX search_results;
    String mnemonic;

    int testkey = bits(inst, 21, 31);

    search_results = searchXX(testkey, opcode63);

    if (search_results == null) {
      testkey = bits(inst, 26, 31);
      search_results = searchXX(testkey, Aform);
    }

    if (search_results == null) {
      return "    Invalid opcode";
    }

    mnemonic = search_results.mnemonic;
    int format = search_results.format;
    switch (search_results.form) {
      case X_FORM:
        return decode_Xform(inst, mnemonic, format, testkey);
      case XFL_FORM:
        return decode_XFLform(inst);
      case A_FORM:
        return decode_Aform(inst, mnemonic, format);
      default:
        return "    Invalid opcode";
    }
  }

  /* Decode the XFL instruction format */

  static String decode_XFLform(int inst) {
    int FLM, FRB, Rc;

    Rc = bits(inst, 31, 31);
    FLM = bits(inst, 7, 14);
    FRB = bits(inst, 16, 20);
    if (FLM == 0xff) {
      String mnemonic = (Rc != 0) ? "mtfs." : "mtfs";
      return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRB;
    } else {
      String mnemonic = (Rc != 0) ? "mtfsf." : "mtfsf";
      return "        ".substring(mnemonic.length()) + mnemonic + "   " + intAsHexString(FLM) + ",fr" + FRB;
    }
  }

  /* Decode the A instruction format */

  static String decode_Aform(int inst, String mnemonic, int format) {
    int FRT, FRA, FRB, FRC;

    FRT = bits(inst, 6, 10);
    FRA = bits(inst, 11, 15);
    FRB = bits(inst, 16, 20);
    FRC = bits(inst, 21, 25);
    switch (format) {

      case 0:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRT + ",fr" + FRA + ",fr" + FRB;
      case 1:
        return "        ".substring(mnemonic.length()) + mnemonic + "   fr" + FRT + ",fr" + FRA + ",fr" + FRC;
      case 2:
        return "        ".substring(mnemonic.length()) +
               mnemonic +
               "   fr" +
               FRT +
               ",fr" +
               FRA +
               ",fr" +
               FRC +
               ",fr" +
               FRB;
      default:
        return "    Invalid opcode";
    }
  }

  /* Construct an assembler-like branch instruction */
  static String build_branch_op(int br_opt, int cr_bit, int update_link, int absolute, int ext_op) {
    String c;
    int uncond = 0;/* Unconditional br to count reg */
    int pos_cond = 0;/* Branch if condition is positive */
    int ctr_zero = 0;/* Branch if count register = 0 */
    int dec_ctr = 0;/* Decrement count register */

    c = "b";
    if ((br_opt & 4) != 0) {/* Don't decrement count register */
      if ((br_opt & 16) != 0) {
        uncond = 1;
      } else if ((br_opt & 8) != 0) {
        pos_cond = 1;
      }
    } else {/* Decrement count register */
      dec_ctr = 1;
      if ((br_opt & 2) != 0) {
        ctr_zero = 1;
      } else if ((br_opt & 8) != 0) {
        pos_cond = 1;
      }
    }
    if (dec_ctr != 0) {
      c += 'd';
      c += (ctr_zero != 0) ? 'z' : 'n';
    }
    if (uncond == 0) {
      if (pos_cond != 0) {
        switch (cr_bit) {
          case CR_LT:
            c += "lt";
            break;
          case CR_GT:
            c += "gt";
            break;
          case CR_EQ:
            c += "eq";
            break;
          case CR_SO:
            c += "so";
            break;
        }
      } else {
        switch (cr_bit) {
          case CR_LT:
            c += "ge";
            break;
          case CR_GT:
            c += "le";
            break;
          case CR_EQ:
            c += "ne";
            break;
          case CR_SO:
            c += "ns";
            break;
        }
      }
    }
    if (ext_op == 16) {
      c += 'r';
    } else if (ext_op == 528) {
      c += 'c';
      if (uncond != 0) {/* Can't confuse with br conditional */
        c += "tr";
      }
    }
    if (update_link != 0) c += "l";
    if (absolute != 0) c += "a";

    return c;
  }

  /*
   * Simply return whether an instruction is a branch_with_link
   */
  /* static int branch_link(inst) */
  public static boolean isBranchAndLink(int inst) {
    int opcode, ext_op;
    int link;

    opcode = bits(inst, 0, 5);
    link = bits(inst, 31, 31);
    switch (opcode) {
      case 16: /* unconditional branch */
      case 18: /* conditional branch */
        break;
      case 19: /* possibly branch register */
        ext_op = bits(inst, 21, 30);
        if ((ext_op != 16) && (ext_op != 528)) {
          link = 0;
        }
        break;
      default: /* definitely not a branch */
        link = 0;
    }

    return link != 0;
  }

  /*
   * Return whether an instruction is a branch for yieldpoint
   *  used by OPT compiler
   */
  /* static int branch_for_yieldpoint(inst) */
  public static boolean isBranchForYieldpoint(int inst) {
    int opcode;
    int link;

    opcode = bits(inst, 0, 5);
    link = bits(inst, 31, 31);
    switch (opcode) {
      case 16: /* unconditional branch */
        return link == 1;
    }

    return false;
  }

  static String intAsHexString(int x) {
    return "0x" + Integer.toHexString(x);
  }

}



