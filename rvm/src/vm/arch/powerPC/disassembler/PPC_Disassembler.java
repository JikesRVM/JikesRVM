/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Disassembler for Rios instruction set.
 * @author Ton Ngo
 *
 * Defined: disasm(inst, addr, buf, reg)
 *          INSTRUCTION inst; ADDRESS addr;  CHAR *buf; CHAR reg[4][10];
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
 *  mnemonic   	opcode  extop   key    	form    format  Example  
 *  --------   	------  ---    	--- 	----    ------  --------
 *  dcbf	31	86	172	X	6	7C0218AC
 *  dcbi	31	470	940	X	6	7C021BAC
 *  dcbst	31	54	108	X	6	7C02186C
 *  dcbt	31	278	556	X	6	7C021A2C
 *  dcbtst	31	246	492	X	6	7C0219EC
 *  dcbz	31	1014	2028	X	6	7C021FEC
 *  divw	31	491	982	XO	1	7C221BD6
 *  divw.	31	491	983	XO	1	7C221BD7
 *  divwo	31	491	2006	XO	1	7C221FD6
 *  divwo.	31	491	2007	XO	1	7C221FD7
 *  divwu	31	459	918	XO	1	7C221B96
 *  divwu.	31	459	919	XO	1	7C221B97
 *  divwuo	31	459	1942	XO	1	7C221F96
 *  divwuo.	31	459	1943	XO	1	7C221F97
 *  eieio	31	854	1708	X	1	7C0006AC
 *  extsb	31	954	1908	X	0	7C220774
 *  extsb.	31	954	1909	X	0	7C220775
 *  icbi	31	982	1964	X	6	7C021FAC
 *  lwarx	31	20	40	X	7	7C221828
 *  mfsrin	31	659	1318	X	4	7C201526
 *  mulhw	31	75	150	XO	1	7C221896
 *  mulhw.	31	75	151	XO	1	7C221897
 *  mulhwu	31	11	22	XO	1	7C221816
 *  mulhwu.	31	11	23	XO	1	7C221817
 *  stwcx.	31	150	301	X	7	7C22192D
 *  subf	31	40	80	XO	1	7C221850
 *  subf.	31	40	81	XO	1	7C221851
 *  subfo	31	40	1104	XO	1	7C221C50
 *  subfo.	31	40	1105	XO	1	7C221C51
 *  							    
 *  fadds	59	21	42	A	0	EC22182A
 *  fadds.	59	21	43	A	0	EC22182B
 *  fdivs	59	18	36	A	0	EC221824
 *  fdivs.	59	18	37	A	0	EC221825
 *  fmadds	59	29	58	A	2	EC22193A
 *  fmadds.	59	29	59	A	2	EC22193B
 *  fmsubs	59	28	56	A	2	EC221938
 *  fmsubs.	59	28	57	A	2	EC221939
 *  fmuls	59	25	50	A	1	EC2200F2
 *  fmuls.	59	25	51	A	1	EC2200F3
 *  fnmadds	59	31	62	A	2	EC22193E
 *  fnmadds.	59	31	63	A	2	EC22193F
 *  fnmsubs	59	30	60	A	2	EC22193C
 *  fnmsubs.	59	30	61	A	2	EC22193D
 *  fsubs	59	20	40	A	0	EC221828
 *  fsubs.	59	20	41	A	0	EC221829
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
 */
class PPC_Disassembler implements VM_Constants {
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

    int instr = Integer.parseInt(args[0],16);
    int addr = Integer.parseInt(args[1],16);
    System.out.println("instr = "+intAsHexString(instr)+" addr = "+
       intAsHexString(addr));
    System.out.println("result --> "+disasm(instr, addr));
    
  }
  
  static final int bits (int x, int n, int m) {
    return ((x >> (31-m)) & ((1 << (m-n+1)) - 1));
  }
  
  static final int signed (int x, int n, int m) {
    return ((x << n) >> 31-m+n);
  }

  static String rname(int n) {
    String rvmName;
    if (n>=0 && n<=31)
      rvmName = GPR_NAMES[n];
    else if (n>=128 && n<=135)
      rvmName = FPR_NAMES[n-128];
    else 
      switch (n) {
      case IAR:  rvmName = "IAR"; break;
      case MSR:  rvmName = "MSR"; break;
      case CR:   rvmName = "CR"; break;
      case LR:   rvmName = "LR"; break;
      case CTR:  rvmName = "CTR"; break;
      case XER:  rvmName = "XER"; break;
      case MQ:   rvmName = "MQ"; break;
      case TID:  rvmName = "TID"; break;
      case FPSCR:   rvmName = "FPSCR"; break;
      case FPINFO:  rvmName = "FPINFO"; break;
      case FPSCRX:  rvmName = "FPSCRX"; break;
      default: rvmName = "r" + n; break;
      }
    return rvmName;
  }

  
  static final int INST_SZ = 4;
  
  /* Special register fields */
  static final int SPR_MQ  = 0;
  static final int SPR_XER  = 1;
  static final int SPR_LR  = 8;
  static final int SPR_CTR  = 9;
  static final int SPR_TID  = 17;
  static final int SPR_DSISR= 18;
  static final int SPR_DAR  = 19;
  static final int SPR_RTCU = 20;
  static final int SPR_RTCL = 21;
  static final int SPR_DEC  = 22;
  static final int SPR_SDR0 = 24;
  static final int SPR_SDR1 = 25;
  static final int SPR_SRR0 = 26;
  static final int SPR_SRR1 = 27;
  
  /* Trap Options */
  static final int TO_LT=16;
  static final int TO_GT= 8;
  static final int TO_EQ= 4;
  static final int TO_LLT=   2;
  static final int TO_LGT= 1;
  
  /* Different instruction formats */
  
  static final int  D_FORM    = 0;
  static final int  B_FORM    = 1;
  static final int  I_FORM=     2;
  static final int  SC_FORM =   3;
  static final int  X_FORM   =  4;
  static final int  XL_FORM   = 5;
  static final int  XFX_FORM  = 6;
  static final int  XFL_FORM  = 7;
  static final int  XO_FORM   = 8;
  static final int  A_FORM    = 9;
  static final int  M_FORM    =10;
  static final int  EXTENDED  =11;
  static final int  INVALID_OP=12;

  static final int OPCODE31_SZ  =  187;
  static final int OPCODE63_SZ  =  23;
  static final int AFORM_SZ     =  16;
  
  /* Condition register fields */
  static final int CR_LT=8;
  static final int CR_GT=4;
  static final int CR_EQ=2;
  static final int CR_SO=1;
  
  static final int X=9; // place holder
  
  /*
   * Instruction decoder.
   */
  static int destination;

  static
  int opcode_to_form[] = {
    /* table to convert opcode into */
    /* instruction formats          */

    /* FORM */
    INVALID_OP, /* OPCODE 00 */
    INVALID_OP, /* OPCODE 01 */
    INVALID_OP, /* OPCODE 02 */
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
    D_FORM,     /* OPCODE 30 */
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
    INVALID_OP, /* OPCODE 58 */
    A_FORM,     /* OPCODE 59 */
    INVALID_OP, /* OPCODE 60 */
    INVALID_OP, /* OPCODE 61 */
    INVALID_OP, /* OPCODE 62 */
    EXTENDED    /* OPCODE 63 */
  };
  
  static opcode_tab Dform[] = {
    
    /* Table for the D instruction format */
    
    /*   OPCD     EO                                format      mnemonic   */
    /*   ----     --                                ------      --------   */
    /*    0,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    1,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    2,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    3,      XXX,     */  new opcode_tab (      3,      "twi"     ), 
    /*    4,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
       
    /*    5,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    6,      XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    7,      XXX,     */  new opcode_tab (      0,      "mulli"   ),
    /*    8,      XXX,     */  new opcode_tab (      0,      "subfic"  ),
    /*    9,      XXX,     */  new opcode_tab (      0,      "dozi"    ),
    
    /*    10,     XXX,     */  new opcode_tab (      4,      "cmpli"   ),
    /*    11,     XXX,     */  new opcode_tab (      4,      "cmpi"    ),
    /*    12,     XXX,     */  new opcode_tab (      0,      "addic"   ),
    /*    13,     XXX,     */  new opcode_tab (      0,      "addic."  ),
    /*    14,     XXX,     */  new opcode_tab (      2,      "addi"    ),
       
    /*    15,     XXX,     */  new opcode_tab (      0,      "addis"   ),
    /*    16,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    17,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    18,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    19,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    
    /*    20,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    21,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    22,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    23,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    24,     XXX,     */  new opcode_tab (      1,      "ori"     ),
    
    /*    25,     XXX,     */  new opcode_tab (      1,      "oris"    ),
    /*    26,     XXX,     */  new opcode_tab (      1,      "xori"    ),
    /*    27,     XXX,     */  new opcode_tab (      1,      "xoris"   ),
    /*    28,     XXX,     */  new opcode_tab (      1,      "andi. "  ),
    /*    29,     XXX,     */  new opcode_tab (      1,      "andis."  ),
    
    /*    30,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    31,     XXX,     */  new opcode_tab (      X,      "RESERVED"),
    /*    32,     XXX,     */  new opcode_tab (      2,      "lwz"     ),
    /*    33,     XXX,     */  new opcode_tab (      2,      "lwzu"    ),
    /*    34,     XXX,     */  new opcode_tab (      2,      "lbz"     ),
    
    /*    35,     XXX,     */  new opcode_tab (      2,      "lbzu"    ),
    /*    36,     XXX,     */  new opcode_tab (      2,      "stw"     ),
    /*    37,     XXX,     */  new opcode_tab (      2,      "stwu"    ),
    /*    38,     XXX,     */  new opcode_tab (      2,      "stb"     ),
    /*    39,     XXX,     */  new opcode_tab (      2,      "stbu"    ),
    
    /*    40,     XXX,     */  new opcode_tab (      2,      "lhz"     ),
    /*    41,     XXX,     */  new opcode_tab (      2,      "lhzu"    ),
    /*    42,     XXX,     */  new opcode_tab (      2,      "lha"     ),
    /*    43,     XXX,     */  new opcode_tab (      2,      "lhau"    ),
    /*    44,     XXX,     */  new opcode_tab (      2,      "sth"     ),
    
    /*    45,     XXX,     */  new opcode_tab (      2,      "sthu"    ),
    /*    46,     XXX,     */  new opcode_tab (      2,      "lmw"     ),
    /*    47,     XXX,     */  new opcode_tab (      2,      "stmw"    ),
    /*    48,     XXX,     */  new opcode_tab (      5,      "lfs"     ),
    /*    49,     XXX,     */  new opcode_tab (      5,      "lfsu"    ),
    
    /*    50,     XXX,     */  new opcode_tab (      5,      "lfd"     ),
    /*    51,     XXX,     */  new opcode_tab (      5,      "lfdu"    ),
    /*    52,     XXX,     */  new opcode_tab (      5,      "stfs"    ),
    /*    53,     XXX,     */  new opcode_tab (      5,      "stfsu"   ),
    /*    54,     XXX,     */  new opcode_tab (      5,      "stfd"    ),
    
    /*    55,     XXX,     */  new opcode_tab (      5,      "stfdu"   )
  };
  
  
  static
  opcode_tab XLform[] = {
    
    /* Table for the XL instruction format */
    
    /*   OPCD      EO                 format     mnemonic      */
    /*   ----      --                 ------     --------      */ 
    /*    19,      0,      */  new opcode_tab(        2,        "mcrf"   ),
    /*    19,      16,     */  new opcode_tab(        1,        "bclr or bclrl"),
    /*    19,      33,     */  new opcode_tab(        3,        "crnor"   ),
    /*    19,      50,     */  new opcode_tab(        0,        "rfi"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    
    /*    19,      82,     */  new opcode_tab(        0,        "rfsvc"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      129,    */  new opcode_tab(        3,        "crandc"   ),
    /*    19,      150,    */  new opcode_tab(        0,        "isync"   ),
    
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      193,    */  new opcode_tab(        3,        "crxor"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      225,    */  new opcode_tab(        3,        "crnand"   ),
    
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      257,    */  new opcode_tab(        3,        "crand"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      289,    */  new opcode_tab(        3,        "creqv"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      417,    */  new opcode_tab(        3,        "crorc"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      449,    */  new opcode_tab(        3,        "cror"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      XXX,    */  new opcode_tab(        X,        "RESERVED"   ),
    /*    19,      528,    */  new opcode_tab(        1,        "bcctr or bcctrl")
  };
  
  
  
  /**
   *  Opcode 31 table: 
   *  The key is bits 21 through 31 of the instruction. 
   *  "Form" is the instruction format: 
   *      I, B, SC, D, DS, X, XL, XFX, XFL, XO, A, M
   *  "format" is how the instruction should be printed (specific to the disassembler) 
   */
  
  
  
  static
  opcodeXX opcode31[] = {
    
    /*                  key        form     format       mnemonic       */
    /*                  ---        ----     ------       --------       */
    new opcodeXX(        0,         4,        22,         "cmp"), 
    new opcodeXX(        8,         4,        24,         "tw"),
    new opcodeXX(       16,         8,         1,         "subfc"),
    new opcodeXX(       17,         8,         1,         "subfc."),
    new opcodeXX(       20,         8,         1,         "addc"),
    new opcodeXX(       21,         8,         1,         "addc."),
    new opcodeXX(       38,         4,         2,         "mfcr"),
    new opcodeXX(       46,         4,         7,         "lwzx"),
    new opcodeXX(       48,         4,         8,         "slw"),
    new opcodeXX(       49,         4,         8,         "slw."),
    new opcodeXX(       52,         4,         9,         "cntlzw"),
    new opcodeXX(       53,         4,         9,         "cntlzw."),
    new opcodeXX(       56,         4,         8,         "and"),
    new opcodeXX(       57,         4,         8,         "and."),
    new opcodeXX(       58,         4,         8,         "maskg"),
    new opcodeXX(       59,         4,         8,         "maskg."),
    new opcodeXX(       64,         4,        22,         "cmpl"),
    new opcodeXX(      110,         4,         7,         "lwzux"),
    new opcodeXX(      120,         4,         8,         "andc"),
    new opcodeXX(      121,         4,         8,         "andc."),
    new opcodeXX(      166,         4,         2,         "mfmsr"),
    new opcodeXX(      174,         4,         7,         "lbzx"),
    new opcodeXX(      208,         8,         0,         "neg"),
    new opcodeXX(      209,         8,         0,         "neg."),
    new opcodeXX(      214,         8,         1,         "mul"),
    new opcodeXX(      215,         8,         1,         "mul."),
    new opcodeXX(      236,         4,         6,         "clf"),
    new opcodeXX(      238,         4,         7,         "lbzux"),
    new opcodeXX(      248,         4,         8,         "nor"),
    new opcodeXX(      249,         4,         8,         "nor."),
    new opcodeXX(      272,         8,         1,         "subfe"),
    new opcodeXX(      273,         8,         1,         "subfe."),
    new opcodeXX(      276,         8,         1,         "adde"),
    new opcodeXX(      277,         8,         1,         "adde."),
    new opcodeXX(      288,         6,         9,         "mtcrf"),
    new opcodeXX(      292,         4,         2,         "mtmsr"),
    new opcodeXX(      302,         4,         7,         "stwx"),
    new opcodeXX(      304,         4,         8,         "slq"),
    new opcodeXX(      305,         4,         8,         "slq."),
    new opcodeXX(      306,         4,         8,         "sle"),
    new opcodeXX(      307,         4,         8,         "sle."),
    new opcodeXX(      366,         4,         7,         "stwux"),
    new opcodeXX(      368,         4,        11,         "sliq"),
    new opcodeXX(      369,         4,        11,         "sliq."),
    new opcodeXX(      400,         8,         0,         "subfze"),
    new opcodeXX(      401,         8,         0,         "subfze."),
    new opcodeXX(      404,         8,         0,         "addze"),
    new opcodeXX(      405,         8,         0,         "addze."),
    new opcodeXX(      420,         4,        17,         "mtsr"),
    new opcodeXX(      430,         4,         7,         "stbx"),
    new opcodeXX(      432,         4,         8,         "sllq"),
    new opcodeXX(      433,         4,         8,         "sllq."),
    new opcodeXX(      434,         4,         8,         "sleq"),
    new opcodeXX(      435,         4,         8,         "sleq."),
    new opcodeXX(      464,         8,         0,         "subfme"),
    new opcodeXX(      465,         8,         0,         "subfme."),
    new opcodeXX(      468,         8,         0,         "addme"),
    new opcodeXX(      469,         8,         0,         "addme."),
    new opcodeXX(      470,         8,         1,         "mullw"),
    new opcodeXX(      471,         8,         1,         "mullw."),
    new opcodeXX(      484,         4,         7,         "mtsrin"),
    new opcodeXX(      494,         4,         7,         "stbux"),
    new opcodeXX(      496,         4,        11,         "slliq"),
    new opcodeXX(      497,         4,        11,         "slliq"),
    new opcodeXX(      528,         8,         1,         "doz"),
    new opcodeXX(      529,         8,         1,         "doz."),
    new opcodeXX(      532,         8,         1,         "add"),
    new opcodeXX(      533,         8,         1,         "add."),
    new opcodeXX(      554,         4,        25,         "lscbx"),
    new opcodeXX(      555,         4,        25,         "lscbx."),
    new opcodeXX(      558,         4,         7,         "lhzx"),
    new opcodeXX(      568,         4,         8,         "eqv"),
    new opcodeXX(      569,         4,         8,         "eqv."),
    new opcodeXX(      612,         4,         6,         "tlbie"),
    new opcodeXX(      622,         4,         7,         "lhzux"),
    new opcodeXX(      632,         4,         8,         "xor"),
    new opcodeXX(      633,         4,         8,         "xor."),
    new opcodeXX(      662,         8,         1,         "div"),
    new opcodeXX(      663,         8,         1,         "div."),
    new opcodeXX(      678,         4,         3,         "mfspr"),
    new opcodeXX(      686,         4,         7,         "lhax"),
    new opcodeXX(      720,         8,         0,         "abs"),
    new opcodeXX(      721,         8,         0,         "abs."),
    new opcodeXX(      726,         8,         1,         "divs"),
    new opcodeXX(      727,         8,         1,         "divs."),
    new opcodeXX(      750,         4,         7,         "lhaux"),
    new opcodeXX(      814,         4,         7,         "sthx"),
    new opcodeXX(      824,         4,         8,         "orc"),
    new opcodeXX(      825,         4,         8,         "orc."),
    new opcodeXX(      878,         4,         7,         "sthux"),
    new opcodeXX(      888,         4,         8,         "or"),
    new opcodeXX(      889,         4,         8,         "or."),
    new opcodeXX(      934,         4,         3,         "mtspr"),
    new opcodeXX(      952,         4,         8,         "nand"),
    new opcodeXX(      953,         4,         8,         "nand."),
    new opcodeXX(      976,         8,         0,         "nabs"),
    new opcodeXX(      977,         8,         0,         "nabs."),
    new opcodeXX(     1004,         4,         6,         "cli"),
    new opcodeXX(     1024,         4,        23,         "mcrxr"),
    new opcodeXX(     1040,         8,         1,         "subfco"),
    new opcodeXX(     1041,         8,         1,         "subfco."),
    new opcodeXX(     1044,         8,         1,         "addco"),
    new opcodeXX(     1045,         8,         1,         "addco."),
    new opcodeXX(     1062,         4,         5,         "clcs"),
    new opcodeXX(     1066,         4,         7,         "lswx"),
    new opcodeXX(     1068,         4,         7,         "lwbrx"),
    new opcodeXX(     1070,         4,        12,         "lfsx"),
    new opcodeXX(     1072,         4,         8,         "srw"),
    new opcodeXX(     1073,         4,         8,         "srw."),
    new opcodeXX(     1074,         4,         8,         "rrib"),
    new opcodeXX(     1075,         4,         8,         "rrib."),
    new opcodeXX(     1082,         4,         8,         "maskir"),
    new opcodeXX(     1083,         4,         8,        "maskir."),
    new opcodeXX(     1134,         4,        12,         "lfsux"),
    new opcodeXX(     1190,         4,        18,         "mfsr"),
    new opcodeXX(     1194,         4,        10,         "lswi"),
    new opcodeXX(     1196,         4,         1,         "sync"),
    new opcodeXX(     1198,         4,        12,         "lfdx"),
    new opcodeXX(     1232,         8,         0,         "nego"),
    new opcodeXX(     1233,         8,         0,         "nego."),
    new opcodeXX(     1238,         8,         1,         "mulo"),
    new opcodeXX(     1239,         8,         1,         "mulo."),
    new opcodeXX(     1254,         4,         7,         "mfsri"),
    new opcodeXX(     1260,         4,         6,         "dclst"),
    new opcodeXX(     1262,         4,        12,         "lfdux"),
    new opcodeXX(     1296,         8,         1,         "subfeo"),
    new opcodeXX(     1297,         8,         1,         "subfeo."),
    new opcodeXX(     1300,         8,         1,         "addeo"),
    new opcodeXX(     1301,         8,         1,         "addeo."),
    new opcodeXX(     1322,         4,         7,         "stswx"),
    new opcodeXX(     1324,         4,         7,         "stwbrx"),
    new opcodeXX(     1326,         4,        12,         "stfsx"),
    new opcodeXX(     1328,         4,         8,         "srq"),
    new opcodeXX(     1329,         4,         8,         "srq."),
    new opcodeXX(     1330,         4,         8,         "sre"),
    new opcodeXX(     1331,         4,         8,         "sre."),
    new opcodeXX(     1390,         4,        12,         "stfsux"),
    new opcodeXX(     1392,         4,        11,         "sriq"),
    new opcodeXX(     1393,         4,        11,         "sriq."),
    new opcodeXX(     1424,         8,         0,         "subfzeo"),
    new opcodeXX(     1425,         8,         0,         "subfzeo."),
    new opcodeXX(     1428,         8,         0,         "addzeo."),
    new opcodeXX(     1429,         8,         0,         "addzeo."),
    new opcodeXX(     1450,         4,        10,         "stswi"), 
    new opcodeXX(     1454,         4,        12,         "stfdx"),
    new opcodeXX(     1456,         4,         8,         "srlq"),
    new opcodeXX(     1457,         4,         8,         "srlq."),
    new opcodeXX(     1458,         4,         8,         "sreq"),
    new opcodeXX(     1459,         4,         8,         "sreq."),
    new opcodeXX(     1488,         8,         0,         "subfmeo."),
    new opcodeXX(     1489,         8,         0,         "subfmeo."),
    new opcodeXX(     1492,         8,         0,         "addmeo"),
    new opcodeXX(     1493,         8,         0,         "addmeo."),
    new opcodeXX(     1494,         8,         1,         "mullwo."),
    new opcodeXX(     1495,         8,         1,         "mullwo."),
    new opcodeXX(     1518,         4,        12,         "stfdux"),
    new opcodeXX(     1520,         4,        11,         "srliq"),
    new opcodeXX(     1521,         4,        11,         "srliq."),
    new opcodeXX(     1552,         8,         1,         "dozo."),
    new opcodeXX(     1553,         8,         1,         "dozo."),
    new opcodeXX(     1556,         8,         1,         "addo"),
    new opcodeXX(     1557,         8,         1,         "addo."),
    new opcodeXX(     1580,         4,         7,         "lhbrx"),
    new opcodeXX(     1584,         4,         8,         "sraw"),
    new opcodeXX(     1585,         4,         8,         "sraw."),
    new opcodeXX(     1636,         4,        25,         "rac"),
    new opcodeXX(     1637,         4,        25,         "rac."),
    new opcodeXX(     1648,         4,        11,         "srawi"),
    new opcodeXX(     1649,         4,        11,         "srawi."),
    new opcodeXX(     1686,         8,         1,         "divo"),
    new opcodeXX(     1687,         8,         1,         "divo."),
    new opcodeXX(     1744,         8,         0,         "abso"),
    new opcodeXX(     1745,         8,         0,         "abso."),
    new opcodeXX(     1750,         8,         1,         "divso"),
    new opcodeXX(     1751,         8,         1,         "divso."),
    new opcodeXX(     1836,         4,         7,         "sthbrx"),
    new opcodeXX(     1840,         4,         8,         "sraq"),
    new opcodeXX(     1841,         4,         8,         "sraq."),
    new opcodeXX(     1842,         4,         8,         "srea"),
    new opcodeXX(     1843,         4,         8,         "srea."),
    new opcodeXX(     1844,         4,         9,         "extsh"),
    new opcodeXX(     1845,         4,         9,         "extsh."),
    new opcodeXX(     1904,         4,        11,         "sraiq"),
    new opcodeXX(     1905,         4,        11,         "sraiq."),
    new opcodeXX(     2000,         8,         0,         "nabso"),
    new opcodeXX(     2001,         8,         0,         "nabso."),
    new opcodeXX(     2028,         4,         6,         "dcbz"),

    // these are the addition for the PowerPC
    new opcodeXX(	172,	4,	6,	 "dcbf"),    
    new opcodeXX(	940,	4,	6,	 "dcbi"),    
    new opcodeXX(	108,	4,	6,	 "dcbst"),   
    new opcodeXX(	556,	4,	6,	 "dcbt"),    
    new opcodeXX(	492,	4,	6,	 "dcbtst"),  
    new opcodeXX(	2028,	4,	6,	 "dcbz"),    
    new opcodeXX(	982,	8,	1,	 "divw"),   
    new opcodeXX(	983,	8,	1,	 "divw."),   
    new opcodeXX(	2006,	8,	1,	 "divwo"),   
    new opcodeXX(	2007,	8,	1,	 "divwo."),  
    new opcodeXX(	918,	8,	1,	 "divwu"),   
    new opcodeXX(	919,	8,	1,	 "divwu."),  
    new opcodeXX(	1942,	8,	1,	 "divwuo"),  
    new opcodeXX(	1943,	8,	1,	 "divwuo."), 
    new opcodeXX(	1708,	4,	1,	 "eieio"),  
    new opcodeXX(	1908,	4,	0,	 "extsb"),  
    new opcodeXX(	1909,	4,	0,	 "extsb."),  
    new opcodeXX(	1964,	4,	6,	 "icbi"),    
    new opcodeXX(	40,	4,	7,	 "lwarx"),   
    new opcodeXX(	1318,	4,	4,	 "mfsrin"),  
    new opcodeXX(	150,	8,	1,	 "mulhw"),   
    new opcodeXX(	151,	8,	1,	 "mulhw."),  
    new opcodeXX(	22,	8,	1,	 "mulhwu"),  
    new opcodeXX(	23,	8,	1,	 "mulhwu."), 
    new opcodeXX(	301,	4,	7,	 "stwcx."), 
    new opcodeXX(	80,	8,	1,	 "subf"),  
    new opcodeXX(	81,	8,	1,	 "subf."),  
    new opcodeXX(	1104,	8,	1,	 "subfo"),   
    new opcodeXX(	1105,	8,	1,	 "subfo.")
  };
  
  
/*  Opcode 63 table: The key is computed by taking 
 *  bits 21 through 31 of the instruction. "Form" is
 *  the instruction format and "format" is how the
 *  instruction should be printed.  */

  static
  opcodeXX opcode63[] = {

    /*                  key        form     format       mnemonic       */
    /*                  ---        ----     ------       --------       */
    new opcodeXX(        0,         4,        19,         "fcmpu"),
    new opcodeXX(       24,         4,        21,         "frsp"),
    new opcodeXX(       25,         4,        21,         "frsp."),
    new opcodeXX(       30,         4,        21,         "fctiwz"),  // PowerPC only
    new opcodeXX(       31,         4,        21,         "fctiwz."), // PowerPC only
    new opcodeXX(       64,         4,        19,         "fcmpo"),
    new opcodeXX(       76,         4,        16,         "mtfsb1"),
    new opcodeXX(       77,         4,        16,         "mtfsb1."),
    new opcodeXX(       80,         4,        21,         "fneg"),
    new opcodeXX(       81,         4,        21,         "fneg."),
    new opcodeXX(      128,         4,        14,         "mcrfs"),
    new opcodeXX(      140,         4,        16,         "mtfsb0"),
    new opcodeXX(      141,         4,        16,         "mtfsb0."),
    new opcodeXX(      144,         4,        21,         "fmr"),
    new opcodeXX(      145,         4,        21,         "fmr."),
    new opcodeXX(      268,         4,        15,         "mtfsfi"),
    new opcodeXX(      269,         4,        15,         "mtfsfi."),
    new opcodeXX(      272,         4,        21,         "fnabs"),
    new opcodeXX(      273,         4,        21,         "fnabs."),
    new opcodeXX(      528,         4,        21,         "fabs"),
    new opcodeXX(      529,         4,        21,         "fabs."),
    new opcodeXX(     1166,         4,        13,         "mffs"),
    new opcodeXX(     1167,         4,        13,         "mffs."),
    new opcodeXX(     1422,         7,         9,         "mtfsf"),
    new opcodeXX(     1423,         7,         9,         "mtfsf.")
  };

  static
  opcodeXX opcode59[] = {
    
    /*  opcode59 table: These are the addition for the PowerPC set
     *  Key is  bits 26 through 31 of the instruction. 
     *  "Form" is the instruction format and "format" is how the
     *  instruction should be printed (the enumeration is specific 
     *  to each decode method)
     */
    
    /*                  key        form     format       mnemonic       */
    new opcodeXX(       42,         9,          0,      "fadds"),
    new opcodeXX(       43,         9,          0,      "fadds."),
    new opcodeXX(       36,	    9,	  	0,	"fdivs"),   
    new opcodeXX(       37,	    9,	  	0,	"fdivs."),  
    new opcodeXX(       58,	    9,	  	2,	"fmadds"),  
    new opcodeXX(       59,	    9,	  	2,	"fmadds."), 
    new opcodeXX(       56,	    9,	  	2,	"fmsubs"),  
    new opcodeXX(       57,	    9,	  	2,	"fmsubs."), 
    new opcodeXX(       50,	    9,	  	1,	"fmuls"),  
    new opcodeXX(       51,	    9,	  	1,	"fmuls."),  
    new opcodeXX(       62,	    9,	  	2,	"fnmadds"), 
    new opcodeXX(       63,	    9,	  	2,	"fnmadds."),
    new opcodeXX(       60,	    9,	  	2,	"fnmsubs"),
    new opcodeXX(       61,	    9,	  	2,	"fnmsubs."),
    new opcodeXX(       40,	    9,	  	0,	"fsubs"),  
    new opcodeXX(       41,	    9,	  	0,	"fsubs.")  
  };

  static
  opcodeXX Aform[] = {
    
    /*  Aform table: The key is computed by taking 
     *  bits 26 through 31 of the instruction. "Form" is
     *  the instruction format and "format" is how the
     *  instruction should be printed.  */
    
    /*                  key        form     format       mnemonic       */
    new opcodeXX(       36,         9,         0,         "fdiv"),
    new opcodeXX(       37,         9,         0,         "fdiv."),
    new opcodeXX(       40,         9,         0,         "fsub"),
    new opcodeXX(       41,         9,         0,         "fsub."),
    new opcodeXX(       42,         9,         0,         "fadd"),
    new opcodeXX(       43,         9,         0,         "fadd."),
    new opcodeXX(       50,         9,         1,         "fm"),
    new opcodeXX(       51,         9,         1,         "fm."),
    new opcodeXX(       56,         9,         2,         "fmsub"),
    new opcodeXX(       57,         9,         2,         "fmsub."),
    new opcodeXX(       58,         9,         2,         "fmadd"),
    new opcodeXX(       59,         9,         2,         "fmadd."),
    new opcodeXX(       60,         9,         2,         "fnmsub"),
    new opcodeXX(       61,         9,         2,         "fnmsub."),
    new opcodeXX(       62,         9,         2,         "fnmadd"),
    new opcodeXX(       63,         9,         2,         "fnmadd.")
  };
  
  /* 
   *  SPR_name - common special purpose register names options   
   */
  static String SPR_name(int SPR)
  {
    switch (SPR) {
    case SPR_MQ:      return("mq");
    case SPR_XER:     return("xer");
    case SPR_LR:      return("lr");
    case SPR_CTR:     return("ctr");
    case SPR_TID:     return("tid");
    case SPR_DSISR:   return("dsisr");
    case SPR_DAR:     return("dar");
    case SPR_RTCU:    return("rtcu");
    case SPR_RTCL:    return("rtcu");
    case SPR_DEC:     return("dec");
    case SPR_SDR0:    return("sdr0");
    case SPR_SDR1:    return("sdr1");
    case SPR_SRR0:    return("srr0");
    case SPR_SRR1:    return("srr1");
    default: return null;
    }
  }
  
  /* 
   *  TO_ext - common trap options   
   */
  static String TO_ext(int TO)
  {
    switch (TO) {
    case TO_LT:             return("lt");
    case (TO_LT | TO_EQ):   return("le");
    case (TO_LT | TO_GT):   return("ne");
    case TO_GT:             return("gt");
    case (TO_GT | TO_EQ):   return("ge");
    case TO_LLT:            return("llt");
    case (TO_LLT | TO_EQ):  return("lle");
    case (TO_LLT | TO_LGT): return("lne");
    case TO_LGT:            return("lgt");
    case (TO_LGT | TO_EQ):  return("lge");
    case TO_EQ:             return("eq");
    default:return null;
    }
  }
  
  /*
   *  Translate an instruction from its
   *  numeric form into something more 
   *  readable.
   */
  
  static String disasm(int inst, int addr)
  {
    int opcode;
    int form;
    
    opcode = bits(inst,0,5);                /* Determine opcode */
    form = opcode_to_form[opcode];           /* Determine instruction format */
    
    switch(form)     /* decode known instruction format */
      {
      case D_FORM:
	return decode_Dform(inst, opcode);
      case B_FORM:
	return decode_Bform(addr, inst, opcode);
      case I_FORM:
	return decode_Iform(addr,inst, opcode);
      case SC_FORM:
	return decode_SCform(inst, opcode);
      case XL_FORM:
	return decode_XLform(inst, opcode);
      case M_FORM:
	return decode_Mform(inst, opcode);
      case A_FORM:
	return decode_opcode59(inst);
      case EXTENDED:      /* More work to do... */
	switch(opcode)   /* Switch off of opcode and process from there */
	  {
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
  
  static String decode_Dform(int inst, int opcode)
  {
    int rt, RA, TO, BF, FRT,ufield;
    int sfield;
    opcode_tab opcode_info;
    String datafield, mnemonic, asm_mnemonic, common_opt;
    
    rt = TO = FRT = bits(inst, 6, 10);
    RA = bits(inst, 11, 15);
    BF = bits(inst,6,8);
    ufield = inst & 0xffff;
    sfield = (inst << 16) >> 16;
    if (sfield < 0) {
      datafield = ""+sfield;
    } else {
      datafield = intAsHexString(sfield);
    }
    opcode_info = Dform[opcode];
    mnemonic = opcode_info.mnemonic;
    
    switch(opcode_info.format)
      {
      case 0:
	if (opcode != 15) {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+rname(rt)+","+rname(RA)+","+datafield;
	} else {
	  if (RA != 0) {
	    return "        ".substring(mnemonic.length()) + mnemonic +
	      "   "+rname(rt)+","+rname(RA)+","+intAsHexString(ufield);
	  } else {
	    return "     liu   "+
	      rname(rt)+","+intAsHexString(ufield);
	  }
	}
      case 1:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(rt)+","+intAsHexString(ufield);
      case 2:
	if ((opcode == 14) && (RA == 0)) {
	  return "     lil" +
	    "   "+rname(rt)+","+datafield;
	} else {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+rname(rt)+","+datafield+"("+rname(RA)+")";
	}
      case 3: /* Trap immediate */
	common_opt = TO_ext(TO);
	asm_mnemonic = "t"+common_opt+"i";
	if (common_opt!=null) {
	  return "        ".substring(asm_mnemonic.length()) + asm_mnemonic +
	    "   "+rname(RA)+","+datafield;
	} else {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+TO+","+rname(RA)+","+datafield;
	}
      case 4:
	if (opcode == 11) {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   cr"+BF+","+rname(RA)+","+datafield;
	} else {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   cr"+BF+","+rname(RA)+","+ufield;
	}
      case 5:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT+","+rname(RA)+","+datafield;
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Decode the B instruction format */
  
  static String decode_Bform(int addr, int inst, int opcode)
  {
    int AA, LK, BO, BI; 
    String mnemonic;
    int cr_field;
    int target;
    
    AA = bits(inst,30,30);
    LK = bits(inst,31,31);
    BO = bits(inst,6,10);
    BI = bits(inst,11,15);
    target = (((inst) & 0xfffc) << 16) >> 16;
    if (AA == 0)
      target += addr;
    mnemonic = build_branch_op(BO,1 << (3 - (BI&3)),LK,AA,0);
    destination = target;
    cr_field = (BI>>2);
    /* Build disassembly without target, added on later... */
    if (cr_field != 0) {/* Not CR 0 ? */
      return "        ".substring(mnemonic.length()) + mnemonic +
	"   "+cr_field + " " + Integer.toHexString(destination);
    } else {
      return "        ".substring(mnemonic.length()) + mnemonic +
	"   " + Integer.toHexString(destination);
    }
  }
  
  /* Decode the I instruction format */
  
  static String decode_Iform(int addr, int inst, int opcode)
  {
    int target;
    int AA, LK; 
    String mnemonic;
    
    AA = bits(inst,30,30);
    LK = bits(inst,31,31);
    target = (((inst) & ~3) << 6) >> 6;
    if (AA!=0) {
      mnemonic = (LK!=0) ? "bla" : "ba";
    } else {
      target += addr;
      mnemonic = (LK!=0) ? "bl" : "b";
    }
    destination = target;
    return "        ".substring(mnemonic.length()) + mnemonic + "   " + Integer.toHexString(destination);
  }
  
  /* Decode the SC instruction format */
  
  static String decode_SCform(int inst, int opcode)
  {
    int SA, LK, LEV, FL1, FL2, SV; 
    
    SA = bits(inst,30,30);
    LK = bits(inst,31,31);
    if (SA != 0)
      {
	SV = bits(inst,16,29);
	String mnemonic = (LK!=0) ? "svcla" : "svca";
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+intAsHexString(SV);
      }
    else
      {
	LEV = bits(inst,20,26);
	FL1 = bits(inst,16,19);
	FL2 = bits(inst,27,29);
	String mnemonic = (LK!=0) ? "svcl" : "svc";
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+intAsHexString(LEV)+","+intAsHexString(FL1)+","+
	  intAsHexString(FL2);
      }
  }
  
  /* Decode the XL instruction format */
  
  static String decode_XLform(int inst, int opcode)
  {
    String mnemonic;
    int ext_opcode;
    int LK, BO,  BI, BB; 
    int BF, BFA, BT, BA; 
    opcode_tab opcode_info;
    int cr_field;
    String branch_name;
    
    ext_opcode = bits(inst,21,30);
    opcode_info = XLform[ext_opcode >> 4]; /* shift to get XL table index */
    mnemonic = opcode_info.mnemonic;
    switch(opcode_info.format)
      {
      case 0:
	return "        ".substring(mnemonic.length()) + mnemonic;
      case 1:
	BO = bits(inst,6,10);
	BI = bits(inst,11,15);
	LK = bits(inst,31,31);
	cr_field = (byte) (BI>>2);
	branch_name = build_branch_op(BO,1 << (3 - (BI&3)),LK,0,ext_opcode);
	if (cr_field!=0) {/* Not CR 0 ? */
	  return "        ".substring(branch_name.length()) + branch_name +
	    "   "+cr_field;
	} else {
	  return "        ".substring(branch_name.length()) + branch_name;
	}
      case 2:
	BF  = bits(inst,6,10);
	BFA = bits(inst,11,13);
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+","+intAsHexString(BFA);
      case 3:
	BT = bits(inst,6,10);
	BA = bits(inst,11,15);
	BB = bits(inst,16,20);
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+intAsHexString(BT)+","+intAsHexString(BA)+","+
	  intAsHexString(BB);
      default:
	return "    Invalid opcode";
      }
  }
  
  static String Mforms[]  = { "rlimi", "rlimi.", "rlinm", "rlinm.",
      "rlmi", "rlmi.", "rlnm", "rlnm."};
  
  /* Decode the M instruction format */
  
  static String decode_Mform(int inst, int opcode)
  {
    int RS, RA, RB, SH;
    int MB, ME, Rc;
    int SH_RB;
    String asm_mnemonic;
    
    RS = bits(inst,6,10);
    RA = bits(inst,11,15);
    SH_RB = RB = SH = bits(inst,16,20);
    MB = bits(inst,21,25);
    ME = bits(inst,26,30);
    Rc = inst & 1;
    
    asm_mnemonic = Mforms[(opcode - 20) * 2 + Rc];
    if ((opcode < 20) || ((opcode >>> 23)!=0)) {
      return "    Invalid opcode";
    } else {
      if (opcode == 21) { /* sri and sli are special forms of rlmni */
	if ((ME == 32) && (MB == (31-SH_RB))) {
	  String mnemonic = (Rc!=0) ? "sri." : "sri";
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+rname(RA)+","+rname(RS)+","+intAsHexString(MB);
	} else if ((MB == 0) && (SH_RB == (31-ME))) {
	  String mnemonic = (Rc!=0) ? "sli." : "sli";
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+rname(RA)+","+rname(RS)+","+intAsHexString(SH_RB);
	} else {
	  return "        ".substring(asm_mnemonic.length()) + asm_mnemonic +
	    "   "+rname(RA)+","+rname(RS)+","+intAsHexString(SH_RB)+","+
	    intAsHexString(MB)+","+intAsHexString(ME) ;
	}
      } else {
	return "        ".substring(asm_mnemonic.length()) + asm_mnemonic +
	  "   "+rname(RA)+","+rname(RS)+","+intAsHexString(SH_RB)+","+
	  intAsHexString(MB)+","+intAsHexString(ME) ;
      }
    }
  }
  
  static opcodeXX searchXX(int key, opcodeXX[] where) {
    
    for (int i=0; i<where.length; ++i) {
      opcodeXX opxx = where[i];
      if (opxx.key == key) return opxx;
    }
    
    return null;
    
  }
  
  /* Decode opcode 31 and then the relevent format */
  
  static String decode_opcode31(int inst)
  {
    opcodeXX search_results;
    int format;
    String mnemonic;
    
    
    int testkey = bits(inst,21,31);
    search_results = searchXX(testkey, opcode31);
    
    if (search_results == null) {
      return "    Invalid opcode";
    }
    
    mnemonic = search_results.mnemonic;
    format = search_results.format;
    switch(search_results.form) 
      {
      case 4:
	return decode_Xform(inst,mnemonic,format,testkey);
      case 6:
	return decode_XFXform(inst);
      case 8:
	return decode_XOform(inst,mnemonic,format);
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Decode the X instruction format */
  
  static String decode_Xform(int inst, String mnemonic, int format, int ext_op)
  {
    int rt,RA,RB,NB,SH,FRS,SPR,FRT,FXM;
    int BF,BFA,I,BT,SR,FRA,FRB,TO;
    String asm_mnemonic, common_opt, mn;
    
    FRS = FRT = TO = BT = rt = bits(inst,6,10);
    FRB = NB  = SH = RB = I = bits(inst,16,20);
    FRA = SPR = SR = RA = bits(inst,11,15);
    BFA = bits(inst,11,13);
    I = bits(inst,16,19);
    BF = bits(inst,6,8);
    
    switch(format) 
      {
      case 0:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(rt);
      case 1:
	return "        ".substring(mnemonic.length()) + mnemonic;
      case 2:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt);
      case 3:
	common_opt = SPR_name(SPR);
	if (common_opt != null) {
	  asm_mnemonic = mnemonic.substring(0, 2) + common_opt;
	  return "        ".substring(asm_mnemonic.length()) + asm_mnemonic +
	    "   "+rname(rt);
	} else {/* reserved register? */
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+intAsHexString(SPR)+","+rname(rt);
	}
      case 4:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RB);
      case 5:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA);
      case 6:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(RB);
      case 7:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA)+","+rname(RB);
      case 8:
	if ((ext_op == 888) || (ext_op == 889)) {
	  if (rt == RB) {
	    String mne = (ext_op == 889) ? "mr." : "mr";
	    return "        ".substring(mne.length()) + mne +
	      "   "+rname(RA)+","+rname(rt);
	  }
	}
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(rt)+","+rname(RB);
      case 9:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(rt);
      case 10:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA)+","+intAsHexString(NB);
      case 11:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(RA)+","+rname(rt)+","+intAsHexString(SH);
      case 12:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRS+","+rname(RA)+","+rname(RB);
      case 13:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT;
      case 14:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+","+intAsHexString(BFA);
      case 15:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+","+intAsHexString(I);
      case 16:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+intAsHexString(BT);
      case 17:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+intAsHexString(SR)+","+rname(rt);
      case 18:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+intAsHexString(SR);
      case 19:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+",fr"+FRA+",fr"+FRB;
      case 20:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+",fr"+FRB;
      case 21:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT+",fr"+FRB;
      case 22:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF+","+rname(RA)+","+rname(RB);
      case 23:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   cr"+BF;
      case 24:
	common_opt = TO_ext(TO);
	if (common_opt != null) {
	  asm_mnemonic = "t" + common_opt;
	  return "        ".substring(asm_mnemonic.length()) + asm_mnemonic +
	    "   "+rname(RA)+","+rname(RB);
	} else {
	  return "        ".substring(mnemonic.length()) + mnemonic +
	    "   "+TO+","+rname(RA)+","+rname(RB);
	}
      case 25:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA)+","+rname(RB);
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Decode the XFX instruction format */
  
  static String decode_XFXform(int inst)
  {
    int rt,FXM;
    
    rt = bits(inst,6,10);
    FXM = bits(inst,12,19);
    if (FXM == 0xff) {
      return "    mtcr   "+rname(rt);
    } else {
      return "   mtcrf   "+intAsHexString(FXM)+","+rname(rt);
    }
  }
  
  /* Decode the XO instruction format */
  
  static String decode_XOform(int inst, String mnemonic, int format)
  {
    int rt,RA,RB;
    
    rt = bits(inst,6,10);
    RA = bits(inst,11,15);
    switch(format)
      {
      case 0:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA);
      case 1:
	RB = bits(inst,16,20);
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   "+rname(rt)+","+rname(RA)+","+rname(RB);
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Decode opcode 59 and then the relevent format */
  
  static String decode_opcode59(int inst)
  {
    opcodeXX opcode, search_results;
    String mnemonic;
    
    int testkey = bits(inst,26,31);
    
    search_results = searchXX(testkey, opcode59);

    if (search_results == null) {
      return "    Invalid opcode";
    }
    
    mnemonic = search_results.mnemonic;
    int format = search_results.format;

    // All opcode 59 are in A form
    switch(search_results.form) 
      {
      case 9:
	return decode_Aform(inst,mnemonic,format);
      default:
	return "    Invalid opcode";
      }
    }

  /* Decode opcode 63 and then the relevent format */
  
  static String decode_opcode63(int inst)
  {
    opcodeXX opcode, search_results;
    String mnemonic;
    
    int testkey = bits(inst,21,31);
    
    search_results = searchXX(testkey, opcode63);
    
    if (search_results == null)
      {
	testkey = bits(inst,26,31);
	search_results = searchXX(testkey, Aform);
      }
    
    if (search_results == null) {
      return "    Invalid opcode";
    }
    
    mnemonic = search_results.mnemonic;
    int format = search_results.format;
    switch(search_results.form) 
      {
      case 4:
	return decode_Xform(inst,mnemonic,format,testkey);
      case 7:
	return decode_XFLform(inst);
      case 9:
	return decode_Aform(inst,mnemonic,format);
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Decode the XFL instruction format */
  
  static String decode_XFLform(int inst)
  {
    int FLM, FRB, Rc;
    
    Rc  = bits(inst,31,31);
    FLM = bits(inst,7,14);
    FRB = bits(inst,16,20);
    if (FLM == 0xff) {
      String mnemonic = (Rc!=0) ? "mtfs." : "mtfs";
      return "        ".substring(mnemonic.length()) + mnemonic +
	"   fr"+FRB;
    } else {
      String mnemonic = (Rc!=0) ? "mtfsf." : "mtfsf";
      return "        ".substring(mnemonic.length()) + mnemonic +
	"   "+intAsHexString(FLM)+",fr"+FRB;
    }
  }
  
  /* Decode the A instruction format */
  
  static String decode_Aform(int inst,String mnemonic,int format)
  {
    int FRT,FRA,FRB,FRC;
    
    FRT = bits(inst, 6,10);
    FRA = bits(inst,11,15);
    FRB = bits(inst,16,20);
    FRC = bits(inst,21,25);
    switch(format) 
      {

      case 0:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT+",fr"+FRA+",fr"+FRB;
      case 1:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT+",fr"+FRA+",fr"+FRC;
      case 2:
	return "        ".substring(mnemonic.length()) + mnemonic +
	  "   fr"+FRT+",fr"+FRA+",fr"+FRC+",fr"+FRB;
      default:
	return "    Invalid opcode";
      }
  }
  
  /* Construct an assembler-like branch instruction */
  static
  String build_branch_op(int br_opt, int cr_bit, int update_link, int absolute, int ext_op)
  {
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
      if ((br_opt & 2)!=0) {
	ctr_zero = 1;
      } else if ((br_opt & 8) != 0) {
	pos_cond = 1;
      } 
    }
    if (dec_ctr!=0) {
      c += 'd';
      c += (ctr_zero!=0)?'z' : 'n';
    }
    if (uncond==0) {
      if (pos_cond!=0) {
	switch(cr_bit) {
	case CR_LT:  c += "lt"; break;
	case CR_GT:  c += "gt"; break;
	case CR_EQ:  c += "eq"; break;
	case CR_SO:  c += "so"; break;
	}
      } else {
	switch(cr_bit) {
	case CR_LT:  c += "ge"; break;
	case CR_GT:  c += "le"; break;
	case CR_EQ:  c += "ne"; break;
	case CR_SO:  c += "ns"; break;
	}
      }
    }
    if (ext_op == 16) {
      c += 'r';
    } else if (ext_op == 528)  {
      c += 'c';
      if (uncond!=0) {/* Can't confuse with br conditional */
	c += "tr";
      }
    }
    if (update_link!=0)
      c += "l";
    if (absolute!=0)
      c += "a";
    
    return c;
  }
  
  /*
   * Simply return whether an instruction is a branch_with_link
   */
  /* static int branch_link(inst) */
  static boolean isBranchAndLink(int inst)
  {
    int opcode, ext_op;
    int link;
    
    opcode = bits(inst,0,5);
    link = bits(inst,31,31);
    switch (opcode) {
    case 16: /* unconditional branch */
    case 18: /* conditional branch */
      break;
    case 19: /* possibly branch register */
      ext_op = bits(inst,21,30);
      if ((ext_op != 16) && (ext_op != 528)) {
	link = 0;
      } 
      break;
    default: /* definitely not a branch */
      link = 0;
    }
    
    if (link==0)
      return false;
    else
      return true;
  }

  /*
   * Return whether an instruction is a branch for yieldpoint
   *  used by OPT compiler
   */
  /* static int branch_for_yieldpoint(inst) */
  static boolean isBranchForYieldpoint(int inst)
  {
    int opcode, ext_op;
    int link;
    
    opcode = bits(inst,0,5);
    link = bits(inst,31,31);
    switch (opcode) {
    case 16: /* unconditional branch */
      if (link==1)
	return true;
      else
	return false;
    }
    
    return false;
  }
  
  
  static String intAsHexString(int x) {
    return "0x"+Integer.toHexString(x);
  }
  
}



