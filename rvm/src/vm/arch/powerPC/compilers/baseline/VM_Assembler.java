/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Derek Lieber
 */
class VM_Assembler implements VM_BaselineConstants {

  /* Machine code generators:

     Corresponding to a PowerPC assembler instruction of the form

       xx A,B,C

     there will be the following methods
       
       int XX (int A, int B, int C), and

       private void emitXX (int A, int B, int C).

     The XX method returns the machine code instruction as an integer; the
     emitXX method appends this instruction to an VM_MachineCode object.
     The name of a method for generating assembler instruction with the record
     bit set (say xx.) will be end in a lower-case r (XXr and emitXXr).

     mIP will be incremented to point to the next machine instruction.

     Note: since most of the calls on these methods will have the same
     constant arguments (e.g. SP, the stack pointer), a method call overhead
     per instruction could be avoided by generating the machine code
     explicitly inline.  Alternately, a Java-to-Java preprocessor might
     achieve the same affect by aggressive constant propagation.

  */

  VM_Assembler (int length) {
    mc = new VM_MachineCode();
    b2m = new int[length];
    mIP = 0;
  }

  /* machine code */
  VM_MachineCode mc;
  private int    mIP    = 0; // current machine code instruction
  private int    bIP    = 0; // bytecode instruction pointer

  /* map from bytecode indeces to  machine code indeces */
  private int[]  b2m;                // 0, if bcodes[n] not an instruction

  /* assembler stuff:

     labels and comments can be added to the an assembler instruction by
     calling the label and comment menthods with the appropriate Strings
     BEFORE calling the emit method that generates the instruction.

  */

  final static boolean fits (int val, int bits) {
    val = val >> bits-1;
    return (val == 0 || val == -1);
  }

  final static String hex (int i) {
    if (i == 0) return "0x0";
    String s = VM.intAsHexString(i).substring(2);
    while (s.substring(0,1).equals("0")) 
      s = s.substring(1);
    return "0x" + s;
  }

  private final static String signedHex (int i) {
    if (i > 0) return hex(i);
    if (i < 0) return "-" + hex(-i);
    return "0x0";
  }

  private final static String left (String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(0,w);
    for (int i=n; i<w; i++) {
      s = s + " ";
    }
    return s; 
  }

  private final static String right (String s, int w) {
    int n = s.length();
    if (w < n) return s.substring(n-w);
    for (int i=n; i<w; i++) {
      s = " " + s;
    } 
    return s; 
  }

  String binstr = null;
  void noteBytecode (String bi) {
    binstr = bi;
    if (VM.TraceAssembler) 
      System.out.println(right(""+bIP,6) + " " + bi + " ");
  }

  String comment = null;
  void comment (String c) {
    if (!VM.TraceAssembler) return;
    if (comment == null) comment = "";
    comment += "# " + c;
  }

  private final void label (int inum, int ifrom, int bfrom) {
    String instr = right(hex(inum<<2),6) + "| " + right("",8);
    instr += " " + left("from",6) + left(hex(ifrom<<2),8);
    instr += "# <<< bi " + bfrom + " ";
    System.out.println(instr);
  }

  private final void label (int inum, int ifrom, int bfrom, int c) {
    String instr = right(hex(inum<<2),6) + "| " + right("",8);
    instr += " " + left("from",6) + left(hex(ifrom<<2),8);
    instr += "# <-- bi " + bfrom + " case " + c + " ";
    System.out.println(instr);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, String args) {
    String instr = right(hex(inum<<2),6) + "| " + right(hex((int)mi),8);
    instr += " " + left(opcode,6) + left(args,20);
    if (comment != null) {
      instr += comment;
      comment = null;
    }
    System.out.println(instr);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode) {
    String args = "";
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT) {
    String args = right(""+RT,2);
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, String s) {
    String args = right(""+RT,2) + right(s,7);
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, int RA) {
    String args = right(""+RT,2) + right(""+RA,7);
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, String D, int RA) {
    String args = right(""+RT,2) + right(D,7) + right("("+RA,3) + ")";
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, int RA, int RB) {
    String args = right(""+RT,2) + right(" "+RA,3) + ", " + right(""+RB,2);
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, int RA, String V){
    String args = right(""+RT,2) + right(" "+RA,3) + ", " + left(V,7);
    asm (inum, mi, opcode, args);
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, int RT, int RA, int RC, int RB) {
    String args = right(""+RT,2) + right(""+RA,7) 
                                 + ", " + right(""+RC,2) 
                                 + ", " + right(""+RB,2);
    asm (inum, mi, opcode, args);
  }

  /* Handling backward branch references */

  int relativeMachineAddress (int bci) {
    return b2m[bci] - mIP;
  }

  int currentInstructionOffset () {
    return mIP;
  }

  /* Handling forward branch references */

  VM_ForwardReference forwardReferenceQ = null;

  /* call before emiting code for the branch */
  final void reserveForwardBranch (int where) {
    VM_ForwardReference fr = new VM_UnconditionalBranch(mIP, where);
    if (forwardReferenceQ == null) {
      forwardReferenceQ = fr;
    } else {
      forwardReferenceQ = forwardReferenceQ.add(fr);
    }
  }

  /* call before emiting code for the branch */
  final void reserveForwardConditionalBranch (int where) {
    emitNOP();
    VM_ForwardReference fr = new VM_ConditionalBranch(mIP, where);
    if (forwardReferenceQ == null) {
      forwardReferenceQ = fr;
    } else {
      forwardReferenceQ = forwardReferenceQ.add(fr);
    }
  }

  /* call before emiting data for the case branch */
  final void reserveForwardCase (int where) {
    VM_ForwardReference fr = new VM_SwitchCase(mIP, where);
    if (forwardReferenceQ == null) {
      forwardReferenceQ = fr;
    } else {
      forwardReferenceQ = forwardReferenceQ.add(fr);
    }
  }

  /* call before emiting code for the target */
  final void resolveForwardReferences (int bindex) {
    bIP = bindex;
    b2m[bIP] = mIP;
    if (forwardReferenceQ != null && 
	forwardReferenceQ.targetBytecodeIndex == bIP) {
      forwardReferenceQ = forwardReferenceQ.resolveUpdate(mc, mIP);
    }
  }

  /* machine instructions */

  static final int Atemplate = 31<<26 | 10<<1;

  static final INSTRUCTION A (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 10<<1;
  }

  final void emitA (int RT, int RA, int RB) {
    INSTRUCTION mi = Atemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "a", RT,  RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int AEtemplate = 31<<26 | 138<<1;

  static final INSTRUCTION AE (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 138<<1;
  }

  final void emitAE (int RT, int RA, int RB) {
    INSTRUCTION mi = AEtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "ae", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int AIrtemplate = 13<<26;

  static final INSTRUCTION AIr (int RT, int RA, int SI) {
    return 13<<26 | RT<<21 | RA<<16 | SI;
  }

  final void emitAIr (int RT, int RA, int SI) {
    if (VM.VerifyAssertions) VM.assert(fits(SI, 16));
    INSTRUCTION mi = AIrtemplate | RT<<21 | RA<<16 | (SI & 0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "ai.", RT, RA, signedHex(SI));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDtemplate = 31<<26 | 28<<1;

  static final INSTRUCTION AND (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 28<<1;
  }

  final void emitAND (int RA, int RS, int RB) {
    INSTRUCTION mi = ANDtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "and", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDItemplate = 28<<26;

  static final INSTRUCTION ANDI (int RA, int RS, int U) {
    return 28<<26 | RS<<21 | RA<<16 | U;
  }

  final void emitANDI (int RA, int RS, int U) {
    if (VM.VerifyAssertions) VM.assert((U>>>16) == 0);
    INSTRUCTION mi = ANDItemplate | RS<<21 | RA<<16 | U;
    if (VM.TraceAssembler)
      asm(mIP, mi, "andi.", RA, RS, hex(U));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Btemplate = 18<<26;

  static final INSTRUCTION B (int relative_address) {
    return 18<<26 | (relative_address&0xFFFFFF)<<2;
  }

  final void emitB (int relative_address) {
    if (VM.VerifyAssertions) VM.assert(fits(relative_address,24));
    INSTRUCTION mi = Btemplate | (relative_address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "b", signedHex(relative_address<<2));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLAtemplate = 18<<26 | 3;

  static final INSTRUCTION BLA (int address) {
    return 18<<26 | (address&0xFFFFFF)<<2 | 3;
  }

  final void emitBLA (int address) {
    if (VM.VerifyAssertions) VM.assert(fits(address,24));
    INSTRUCTION mi = BLAtemplate | (address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bla", hex(address));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLtemplate = 18<<26 | 1;

  static final INSTRUCTION BL (int relative_address) {
    return 18<<26 | (relative_address&0xFFFFFF)<<2 | 1;
  }

  final void emitBL (int relative_address) {
    if (VM.VerifyAssertions) VM.assert(fits(relative_address,24));
    INSTRUCTION mi = BLtemplate | (relative_address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bl", signedHex(relative_address<<2));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCtemplate = 16<<26;

  static final INSTRUCTION BC (int B0, int B1, int rel) {
    return 16<<26 | B0<<21 | B1<<16 | (rel&0x3FFF)<<2;
  }

  private static final int lt = 0;
  private static final int gt = 1;
  private static final int eq = 2;
  private static final int yea = 0xC; // branch if condition is true
  private static final int nea = 0x4; // branch if condition is false

  static final int BLTtemplate = 16<<26 | yea<<21 | lt<<16;
  static final int BGTtemplate = 16<<26 | yea<<21 | gt<<16;
  static final int BEQtemplate = 16<<26 | yea<<21 | eq<<16;
  static final int BLEtemplate = 16<<26 | nea<<21 | gt<<16;
  static final int BGEtemplate = 16<<26 | nea<<21 | lt<<16;
  static final int BNEtemplate = 16<<26 | nea<<21 | eq<<16;

  static final INSTRUCTION BLT (int relative_address) {
    return 16<<26 | 0xC<<21 | 0<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBLT (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BLTtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "blt", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BGEtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bge", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final INSTRUCTION BGT (int relative_address) {
    return 16<<26 | 0xC<<21 | 1<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBGT (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BGTtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bgt", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BLEtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "ble", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final INSTRUCTION BEQ (int relative_address) {
    return 16<<26 | 0xC<<21 | 2<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBEQ (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BEQtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "beq", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BNEtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bne", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final INSTRUCTION BLE (int relative_address) {
    return 16<<26 | 0x4<<21 | 1<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBLE (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BLEtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "ble", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BGTtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bgt", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final INSTRUCTION BGE (int relative_address) {
    return 16<<26 | 0x4<<21 | 0<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBGE (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BGEtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bge", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BLTtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "blt", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final INSTRUCTION BNE (int relative_address) {
    return 16<<26 | 0x4<<21 | 2<<16 | (relative_address&0x3FFF)<<2;
  }

  final void emitBNE (int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BNEtemplate | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler)
	asm(mIP, mi, "bne", signedHex(relative_address<<2));
      mIP++;
      mc.addInstruction(mi);
    } else {
      INSTRUCTION mi = BEQtemplate | 8;
      if (VM.TraceAssembler)
	asm(mIP, mi, "beq", signedHex(8));
      mIP++;
      mc.addInstruction(mi);
      emitB(relative_address-1);
    }
  }

  static final int BLRtemplate = 19<<26 | 0x14<<21 | 16<<1;

  static final INSTRUCTION BLR () {
    return 19<<26 | 0x14<<21 | 16<<1;
  }

  final void emitBLR () {
    INSTRUCTION mi = BLRtemplate;
    // comment("bclr 0x14,0");
    if (VM.TraceAssembler)
      asm(mIP, mi, "blr");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLRLtemplate = 19<<26 | 0x14<<21 | 16<<1 | 1;

  static final INSTRUCTION BLRL () {
    return 19<<26 | 0x14<<21 | 16<<1 | 1;
  }

  final void emitBLRL () {
    INSTRUCTION mi = BLRLtemplate;
    // comment("bclrl 0x14,0");
    if (VM.TraceAssembler)
      asm(mIP, mi, "blrl");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCTRtemplate = 19<<26 | 0x14<<21 | 528<<1;

  static final INSTRUCTION BCTR () {
    return 19<<26 | 0x14<<21 | 528<<1 ;
  }

  final void emitBCTR () {
    INSTRUCTION mi = BCTRtemplate;
    // comment("bctr 0x14,0");
    if (VM.TraceAssembler)
      asm(mIP, mi, "bctr");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCTRLtemplate = 19<<26 | 0x14<<21 | 528<<1 | 1;

  static final INSTRUCTION BCTRL () {
    return 19<<26 | 0x14<<21 | 528<<1 | 1;
  }

  final void emitBCTRL () {
    INSTRUCTION mi = BCTRLtemplate;
    // comment("bctrl 0x14,0");
    if (VM.TraceAssembler)
      asm(mIP, mi, "bctrl");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CALtemplate = 14<<26;

  static final INSTRUCTION CAL (int RT, int D, int RA) {
    return 14<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitCAL (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = CALtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "cal", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CAUtemplate = 15<<26;

  static final INSTRUCTION CAU (int RT, int RA, int UI) {
    return  15<<26 | RT<<21 | RA<<16 | (UI&0xFFFF);
  }

  final void emitCAU (int RT, int RA, int UI) {
    if (VM.VerifyAssertions) VM.assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = CAUtemplate | RT<<21 | RA<<16 | UI;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cau", RT, RA, hex(UI));
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCAU (int RT, int UI) {
    if (VM.VerifyAssertions) VM.assert(UI == (UI&0xFFFF));
    INSTRUCTION mi = CAUtemplate | RT<<21 | UI;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cau", RT, 0, hex(UI));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPtemplate = 31<<26;

  static final INSTRUCTION CMP (int BF, int RA, int RB) {
    return 31<<26 | BF<<23 | RA<<16 | RB<<11;
  }

  final void emitCMP (int BF, int RA, int RB) {
    INSTRUCTION mi = CMPtemplate | BF<<23 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmp", BF, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCMP (int RA, int RB) {
    INSTRUCTION mi = CMPtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmp", 0, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPItemplate = 11<<26;

  static final INSTRUCTION CMPI (int BF, int RA, int V) {
    return 11<<26 | BF<<23 | RA<<16 | (V&0xFFFF);
  }

  final void emitCMPI (int BF, int RA, int V) {
    if (VM.VerifyAssertions) VM.assert(fits(V, 16));
    INSTRUCTION mi = CMPItemplate | BF<<23 | RA<<16 | (V&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmpi", BF, RA, signedHex(V));
    mIP++;
    mc.addInstruction(mi);
  }

  static final INSTRUCTION CMPI (int RA, int V) {
    return 11<<26 | RA<<16 | (V&0xFFFF);
  }

  final void emitCMPI (int RA, int V) {
    if (VM.VerifyAssertions) VM.assert(fits(V, 16));
    INSTRUCTION mi = CMPItemplate | RA<<16 | (V&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmpi", 0, RA, signedHex(V));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPLtemplate = 31<<26 | 32<<1;

  static final INSTRUCTION CMPL (int BF, int RA, int RB) {
    return 31<<26 | BF<<23 | RA<<16 | RB<<11 | 32<<1;
  }

  final void emitCMPL (int BF, int RA, int RB) {
    INSTRUCTION mi = CMPLtemplate | BF<<23 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmpl", BF, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitCMPL (int RA, int RB) {
    INSTRUCTION mi = CMPLtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmpl", 0, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDtemplate = 19<<26 | 257<<1;

  static final INSTRUCTION CRAND (int BT, int BA, int BB) {
    return  19<<26 | BT<<21 | BA<<16 | BB<<11 | 257<<1;
  }

  final void emitCRAND (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crand", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDCtemplate = 19<<26 | 129<<1;

  static final INSTRUCTION CRANDC (int BT, int BA, int BB) {
    return  19<<26 | BT<<21 | BA<<16 | BB<<11 | 129<<1;
  }

  final void emitCRANDC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDCtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crandc", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORtemplate = 19<<26 | 449<<1;

  static final INSTRUCTION CROR (int BT, int BA, int BB) {
    return  19<<26 | BT<<21 | BA<<16 | BB<<11 | 449<<1;
  }

  final void emitCROR (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cror", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORCtemplate = 19<<26 | 417<<1;

  static final INSTRUCTION CRORC (int BT, int BA, int BB) {
    return  19<<26 | BT<<21 | BA<<16 | BB<<11 | 417<<1;
  }

  final void emitCRORC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORCtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crorc", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FAtemplate = 63<<26 | 21<<1;

  static final INSTRUCTION FA (int FRT, int FRA, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 21<<1;
  }

  final void emitFA (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FAtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fa", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FAstemplate = 59<<26 | 21<<1; // single-percision add

  static final INSTRUCTION FAs (int FRT, int FRA, int FRB) {
    return 59<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 21<<1;
  }

  final void emitFAs (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FAstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "faS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FABStemplate = 63<<26 | 264<<1;

  static final INSTRUCTION FABS (int FRT, int FRB) {
    return 63<<26 | FRT<<21 | FRB<<11 | 264<<1;
  }

  final void emitFABS (int FRT, int FRB) {
    INSTRUCTION mi = FABStemplate | FRT<<21 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fabs", FRT, FRB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCMPUtemplate = 63<<26;

  static final INSTRUCTION FCMPU (int FRA, int FRB) {
    return 63<<26 | 0<<23 | FRA<<16 | FRB<<11;
  }

  final void emitFCMPU (int FRA,int FRB) {
    INSTRUCTION mi = FCMPUtemplate | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fcmpu", FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDtemplate = 63<<26 | 18<<1;

  static final INSTRUCTION FD (int FRT, int FRA, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 18<<1;
  }

  final void emitFD (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fd", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDstemplate = 59<<26 | 18<<1; // single-precision divide

  static final INSTRUCTION FDs (int FRT, int FRA, int FRB) {
    return 59<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 18<<1;
  }

  final void emitFDs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fdS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMtemplate = 63<<26 | 25<<1;

  static final INSTRUCTION FM (int FRT, int FRA, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<6 | 25<<1;
  }

  final void emitFM (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMtemplate | FRT<<21 | FRA<<16 | FRB<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fm", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMstemplate = 59<<26 | 25<<1; // single-precision fm

  static final INSTRUCTION FMs (int FRT, int FRA, int FRB) {
    return 59<<26 | FRT<<21 | FRA<<16 | FRB<<6 | 25<<1;
  }

  final void emitFMs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMstemplate | FRT<<21 | FRA<<16 | FRB<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fmS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMAtemplate = 63<<26 | 29<<1;

  static final INSTRUCTION FMA (int FRT, int FRA, int FRC, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6 | 29<<1;
  }

  final void emitFMA (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FMAtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fma", FRT, FRA, FRC, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNMStemplate = 63<<26 | 30<<1;

  static final INSTRUCTION FNMS (int FRT, int FRA, int FRC, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6 | 30<<1;
  }

  final void emitFNMS (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FNMStemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fnms", FRT, FRA, FRC, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNEGtemplate = 63<<26 | 40<<1;

  static final INSTRUCTION FNEG (int FRT, int FRB) {
    return 63<<26 | FRT<<21 | FRB<<11 | 40<<1;
  }

  final void emitFNEG (int FRT, int FRB) {
    INSTRUCTION mi = FNEGtemplate | FRT<<21 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fneg", FRT, FRB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FStemplate = 63<<26 | 20<<1;

  static final INSTRUCTION FS (int FRT, int FRA, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 20<<1;
  }

  final void emitFS (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fs", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSstemplate = 59<<26 | 20<<1;

  static final INSTRUCTION FSs (int FRT, int FRA, int FRB) {
    return 59<<26 | FRT<<21 | FRA<<16 | FRB<<11 | 20<<1;
  }

  final void emitFSs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FSstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "FSs", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSELtemplate = 63<<26 | 23<<1;

  static final INSTRUCTION FSEL (int FRT, int FRA, int FRC, int FRB) {
    return 63<<26 | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6 | 20<<1;
  }

  final void emitFSEL (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FSELtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fsel", FRT, FRA, FRB, FRC );
    mIP++;
    mc.addInstruction(mi);
  }

  // LOAD/ STORE MULTIPLE

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambituous) 
  //
  final void emitLM(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = (46<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lm", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambituous) 
  //
  final void emitSTM(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = (47<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lm", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }


  static final int Ltemplate = 32<<26;

  static final INSTRUCTION L (int RT, int D, int RA) {
    return 32<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitL (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = Ltemplate  | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "l", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZtemplate = 34<<26;

  static final INSTRUCTION LBZ (int RT, int D, int RA) {
    return 34<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLBZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LBZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lbz", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZXtemplate = 31<<26 | 87<<1;

  static final INSTRUCTION LBZX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 87<<1;
  }

  final void emitLBZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LBZXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lbzx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAtemplate = 42<<26;

  static final INSTRUCTION LHA (int RT, int D, int RA) {
    return 42<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLHA (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LHAtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lha", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZtemplate = 40<<26;

  static final INSTRUCTION LHZ (int RT, int D, int RA) {
    return 40<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLHZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LHZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhz", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDtemplate = 50<<26;

  static final INSTRUCTION LFD (int FRT, int D, int RA) {
    return 50<<26 | FRT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLFD (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFDtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfd", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDUtemplate = 51<<26;

  static final INSTRUCTION LFDU (int FRT, int D, int RA) {
    return 51<<26 | FRT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLFDU (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFDUtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfdu", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDXtemplate = 31<<26 | 599<<1;

  static final INSTRUCTION LFDX (int FRT, int RA, int RB) {
    return 31<<26 | FRT<<21 | RA<<16 | RB<<11 | 599<<1;
  }

  final void emitLFDX (int FRT, int RA, int RB) {
    INSTRUCTION mi = LFDXtemplate | FRT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfdx", FRT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFStemplate = 48<<26;

  static final INSTRUCTION LFS (int FRT, int D, int RA) {
    return 48<<26 | FRT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLFS (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFStemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfs", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAXtemplate = 31<<26 | 343<<1;

  static final INSTRUCTION LHAX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 343<<1;
  }

  final void emitLHAX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHAXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhax", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZXtemplate = 31<<26 | 279<<1;

  static final INSTRUCTION LHZX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 279<<1;
  }

  final void emitLHZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHZXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhzx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final INSTRUCTION LIL(int RT, int D) {
      return CALtemplate | RT<<21 | (D&0xFFFF);
  }

  final void emitLIL (int RT, int D) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = CALtemplate | RT<<21 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lil", RT, signedHex(D));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LUtemplate = 33<<26;

  static final INSTRUCTION LU (int RT, int D, int RA) {
    return 33<<26 | RT<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitLU (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LUtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lu", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LXtemplate = 31<<26 | 23<<1;

  static final INSTRUCTION LX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 23<<1;
  }

  final void emitLX (int RT, int RA, int RB) {
    INSTRUCTION mi = LXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LUXtemplate = 31<<26 | 55<<1;

  static final INSTRUCTION LUX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 55<<1;
  }

  final void emitLUX (int RT, int RA, int RB) {
    INSTRUCTION mi = LUXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lux", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  
  static final int LWARXtemplate = 31<<26 | 20<<1;

  static final INSTRUCTION LWARX (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 20<<1;
  }

  final void emitLWARX (int RT, int RA, int RB) {
    INSTRUCTION mi = LWARXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lwarx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFLRtemplate = 31<<26 | 0x08<<16 | 339<<1;

  static final INSTRUCTION MFLR (int RT) {
    return 31<<26 | RT<<21 | 0x08<<16 | 339<<1;
  }

  final void emitMFLR (int RT) {
    INSTRUCTION mi = MFLRtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mflr", RT);
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int MFSPRtemplate = 31<<26 | 339<<1;

  static final INSTRUCTION MFSPR (int RT, int SPR) {
    return 31<<26 | RT<<21 | SPR<<16 | 339<<1;
  }

  final void emitMFSPR (int RT, int SPR) {
    INSTRUCTION mi = MFSPRtemplate | RT<<21 | SPR<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mfspr", RT, SPR);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTLRtemplate = 31<<26 | 0x08<<16 | 467<<1;

  static final INSTRUCTION MTLR (int RS) {
    return 31<<26 | RS<<21 | 0x08<<16 | 467<<1;
  }

  final void emitMTLR (int RS) {
    INSTRUCTION mi = MTLRtemplate | RS<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mtlr", RS);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTCTRtemplate = 31<<26 | 0x09<<16 | 467<<1;

  static final INSTRUCTION MTCTR (int RS) {
    return 31<<26 | RS<<21 | 0x09<<16 | 467<<1;
  }

  final void emitMTCTR (int RS) {
    INSTRUCTION mi = MTCTRtemplate | RS<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mtctr", RS);
    mIP++;
    mc.addInstruction(mi);
  }
 
  static final int MULHWUtemplate = 31<<26 | 11<<1;

  static final INSTRUCTION MULHWU (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 11<<1;
  }

  final void emitMULHWU (int RT, int RA, int RB) {
    INSTRUCTION mi = MULHWUtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mulhwu", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DIVtemplate = 31<<26 | 491<<1;

  static final INSTRUCTION DIV (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 491<<1;
  }

  final void emitDIV (int RT, int RA, int RB) {
    INSTRUCTION mi = DIVtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "div", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULStemplate = 31<<26 | 235<<1;

  static final INSTRUCTION MULS (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 235<<1;
  }

  final void emitMULS (int RT, int RA, int RB) {
    INSTRUCTION mi = MULStemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "muls", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int NEGtemplate = 31<<26 | 104<<1;

  static final INSTRUCTION NEG (int RT, int RA) {
    return 31<<26 | RT<<21 | RA<<16 | 104<<1;
  }

  final void emitNEG (int RT, int RA) {
    INSTRUCTION mi = NEGtemplate | RT<<21 | RA<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "neg", RT, RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORtemplate = 31<<26 | 444<<1;

  static final INSTRUCTION OR (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 444<<1;
  }

  final void emitOR (int RA, int RS, int RB) {
    INSTRUCTION mi = ORtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "or", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFrtemplate = 31<<26 | 8<<1 | 1;

  static final INSTRUCTION SFr (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 8<<1 | 1;
  }

  final void emitSFr (int RT, int RA, int RB) {
    INSTRUCTION mi = SFrtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sf.", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFtemplate = 31<<26 | 8<<1;

  static final INSTRUCTION SF (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 8<<1;
  }

  final void emitSF (int RT, int RA, int RB) {
    INSTRUCTION mi = SFtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sf", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFItemplate = 8<<26;

  static final INSTRUCTION SFI (int RA, int RS, int S) {
    return 8<<26 | RS<<21 | RA<<16 | S;
  }

  final void emitSFI (int RA, int RS, int S) {
    if (VM.VerifyAssertions) VM.assert(fits(S,16));
    INSTRUCTION mi = SFItemplate | RS<<21 | RA<<16 | S;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfi", RA, RS, signedHex(S));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFErtemplate = 31<<26 | 136<<1 | 1;

  static final INSTRUCTION SFEr (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 136<<1 | 1;
  }

  final void emitSFEr (int RT, int RA, int RB) {
    INSTRUCTION mi = SFErtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfe.", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFEtemplate = 31<<26 | 136<<1;

  static final INSTRUCTION SFE (int RT, int RA, int RB) {
    return 31<<26 | RT<<21 | RA<<16 | RB<<11 | 136<<1;
  }

  final void emitSFE (int RT, int RA, int RB) {
    INSTRUCTION mi = SFEtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfe", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFZEtemplate = 31<<26 | 200<<1;

  static final INSTRUCTION SFZE (int RT, int RA) {
    return 31<<26 | RT<<21 | RA<<16 | 200<<1;
  }

  final void emitSFZE (int RT, int RA) {
    INSTRUCTION mi = SFZEtemplate | RT<<21 | RA<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfze", RT, RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLtemplate = 31<<26 | 24<<1;

  static final INSTRUCTION SL (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 24<<1;
  }

  final void emitSL (int RA, int RS, int RB) {
    INSTRUCTION mi = SLtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sl", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLItemplate = 21<<26;

  static final INSTRUCTION SLI (int RA, int RS, int N) {
    return 21<<26 | RS<<21 | RA<<16 | N<<11 | (31-N)<<1;
  }

  final void emitSLI (int RA, int RS, int N) {
    INSTRUCTION mi = SLItemplate | RS<<21 | RA<<16 | N<<11 | (31-N)<<1;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sli", RA, RS, signedHex(N));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRtemplate = 31<<26 | 536<<1;

  static final INSTRUCTION SR (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 536<<1;
  }

  final void emitSR (int RA, int RS, int RB) {
    INSTRUCTION mi = SRtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sr", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAtemplate = 31<<26 | 792<<1;

  static final INSTRUCTION SRA (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 792<<1;
  }

  final void emitSRA (int RA, int RS, int RB) {
    INSTRUCTION mi = SRAtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sra", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAItemplate = 31<<26 | 824<<1;

  static final INSTRUCTION SRAI (int RA, int RS, int SH) {
    return 31<<26 | RS<<21 | RA<<16 | SH<<11 | 824<<1;
  }

  final void emitSRAI (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAItemplate | RS<<21 | RA<<16 | SH<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "srai", RA, RS, signedHex(SH));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAIrtemplate = 31<<26 | 824<<1 | 1;

  static final INSTRUCTION SRAIr (int RA, int RS, int SH) {
    return 31<<26 | RS<<21 | RA<<16 | SH<<11 | 824<<1 | 1;
  }

  final void emitSRAIr (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAIrtemplate | RS<<21 | RA<<16 | SH<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "srai.", RA, RS, signedHex(SH));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STtemplate = 36<<26;

  static final INSTRUCTION ST (int RS, int D, int RA) {
    return 36<<26 | RS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitST (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "st", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBtemplate = 38<<26;

  static final INSTRUCTION STB (int RS, int D, int RA) {
    return 38<<26 | RS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTB (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STBtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stb", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBXtemplate = 31<<26 | 215<<1;

  static final INSTRUCTION STBX (int RS, int RA, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 215<<1;
  }

  final void emitSTBX (int RS, int RA, int RB) {
    INSTRUCTION mi = STBXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stbx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STHXtemplate = 31<<26 | 407<<1;

  static final INSTRUCTION STHX (int RS, int RA, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 407<<1;
  }

  final void emitSTHX (int RS, int RA, int RB) {
    INSTRUCTION mi = STHXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sthx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STXtemplate = 31<<26 | 151<<1;

  static final INSTRUCTION STX (int RS, int RA, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 151<<1;
  }

  final void emitSTX (int RS, int RA, int RB) {
    INSTRUCTION mi = STXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDtemplate = 54<<26;

  static final INSTRUCTION STFD (int FRS, int D, int RA) {
    return 54<<26 | FRS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTFD (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFDtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfd", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDUtemplate = 55<<26;

  static final INSTRUCTION STFDU (int FRS, int D, int RA) {
    return 55<<26 | FRS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTFDU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFDUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfdu", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDXtemplate = 31<<26 | 727<<1;

  static final INSTRUCTION STFDX(int FRS, int RA, int RB) {
    return 31<<26 | FRS<<21 | RA<<16 | RB<<11 | 727<<1;
  }

  final void emitSTFDX (int FRS, int RA, int RB) {
    INSTRUCTION mi = STFDXtemplate | FRS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfdx", FRS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFStemplate = 52<<26;

  static final INSTRUCTION STFS (int FRS, int D, int RA) {
    return 52<<26 | FRS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTFS (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFStemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfs", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFSUtemplate = 53<<26;

  static final INSTRUCTION STFSU (int FRS, int D, int RA) {
    return 53<<26 | FRS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTFSU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFSUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfsu", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STUtemplate = 37<<26;

  static final INSTRUCTION STU (int RS, int D, int RA) {
    return 37<<26 | RS<<21 | RA<<16 | (D&0xFFFF);
  }

  final void emitSTU (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STUtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stu", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STUXtemplate = 31<<26 | 183<<1;

  static final INSTRUCTION STUX (int RS, int RA, int RB) {
    return 37<<26 | RS<<21 | RA<<16 | RB<<11 | 183<<1;
  }

  final void emitSTUX (int RS, int RA, int RB) {
    INSTRUCTION mi = STUXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stux", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWCXrtemplate = 31<<26 | 150<<1 | 1;

  static final INSTRUCTION STWCXr (int RS, int RA, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 150<<1 | 1;
  }

  final void emitSTWCXr (int RS, int RA, int RB) {
    INSTRUCTION mi = STWCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stwcx.", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Ttemplate = 31<<26 | 4<<1;

  static final INSTRUCTION T (int TO, int RA, int RB) {
    return 31<<26 | TO<<21 | RA<<16 | RB<<11 | 4<<1;
  }

/**********UNUSED*****
* 
* final void emitT (int TO, int RA, int RB) {
*   INSTRUCTION mi = Ttemplate | TO<<21 | RA<<16 | RB<<11;
*   if (VM.TraceAssembler)
*     asm(mIP, mi, "t", TO, RA, RB);
*   mIP++;
*   mc.addInstruction(mi);
* }
*
* static final int TGEtemplate = 31<<26 | 0xC<<21 | 4<<1;
*
* final void emitTGE (int RA, int RB) {
*   INSTRUCTION mi = TGEtemplate | RA<<16 | RB<<11;
*   if (VM.TraceAssembler)
*     asm(mIP, mi, "tge", RA, RB);
*   mIP++;
*   mc.addInstruction(mi);
* }
*
* static final int TLGEtemplate = 31<<26 | 0x5<<21 | 4<<1;
*
* final void emitTLGE (int RA, int RB) {
*   INSTRUCTION mi = TLGEtemplate | RA<<16 | RB<<11;
*   if (VM.TraceAssembler)
*     asm(mIP, mi, "tlge", RA, RB);
*   mIP++;
*   mc.addInstruction(mi);
* }
***/

  static final int TItemplate = 3<<26;

  static final INSTRUCTION TI (int TO, int RA, int SI) {
    return 3<<26 | TO<<21 | RA<<16 | SI&0xFFFF;
  }

  final void emitTI (int TO, int RA, int SI) {
    INSTRUCTION mi = TItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    if (VM.TraceAssembler)
      asm(mIP, mi, "ti", TO, RA, signedHex(SI));
    mIP++;
    mc.addInstruction(mi);
  }
  
/***
* static final int TLTItemplate = 3<<26 | 0x10<<21;
*
* final void emitTLT0 (int RA) {
*   INSTRUCTION mi = TLTItemplate | RA<<16;
*   if (VM.TraceAssembler)
*     asm(mIP, mi, "tlti", RA, "0");
*   mIP++;
*   mc.addInstruction(mi);
* }
*************/

  static final int TLEtemplate = 31<<26 | 0x14<<21 | 4<<1;

  final void emitTLE (int RA, int RB) {
    INSTRUCTION mi = TLEtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "tle", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TLTtemplate = 31<<26 | 0x10<<21 | 4<<1;

  final void emitTLT (int RA, int RB) {
    INSTRUCTION mi = TLTtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "tlt", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TLLEtemplate = 31<<26 | 0x6<<21 | 4<<1;

  final void emitTLLE (int RA, int RB) {
    INSTRUCTION mi = TLLEtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "tlle", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TEQItemplate = 3<<26 | 0x4<<21;

  final void emitTEQ0 (int RA) {
    INSTRUCTION mi = TEQItemplate | RA<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "teqi", RA, "0");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWItemplate = 3<<26 | 0x3EC<<16;	// RA == 12

  final void emitTWI (int imm) {
    INSTRUCTION mi = TWItemplate | imm;
    if (VM.TraceAssembler)
      asm(mIP, mi, "twi", imm);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORtemplate = 31<<26 | 316<<1;

  static final INSTRUCTION XOR (int RA, int RS, int RB) {
    return 31<<26 | RS<<21 | RA<<16 | RB<<11 | 316<<1;
  }

  final void emitXOR (int RA, int RS, int RB) {
    INSTRUCTION mi = XORtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "xor", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORItemplate = 26<<26;

  static final INSTRUCTION XORI (int RA, int RS, int V) {
    return 26<<26 | RS<<21 | RA<<16  | V&0xFFFF;
  }

  final void emitXORI (int RA, int RS, int V) {
    if (VM.VerifyAssertions) VM.assert(fits(V, 16));
    INSTRUCTION mi = XORItemplate |  RS<<21 | RA<<16  | V&0xFFFF;
    if (VM.TraceAssembler)
      asm(mIP, mi, "xori", RA, RS, V);
    mIP++;
    mc.addInstruction(mi);
  }

  /* macro instructions */

  static final int NOPtemplate = 19<<26 | 449<<1;

  static final INSTRUCTION NOP () {
    return CROR(0, 0, 0);
  }

  final void emitNOP () {
    INSTRUCTION mi = NOPtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "nop");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SENTINALtemplate = 19<<26 | 0x1F<<21 | 0x1F<<16 | 0x1F<<11 | 449<<1;

  final void emitSENTINAL () {
    INSTRUCTION mi = SENTINALtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "end", "prologue");
    mIP++;
    mc.addInstruction(mi);
  }

  // branch conditional -- don't thread switch
  static final int BNTStemplate = 16<<26 | nea<<21 | THREAD_SWITCH_BIT<<16;
  final void emitBNTS (int relative_address) {
    if (VM.VerifyAssertions) VM.assert(fits(relative_address, 14));
    INSTRUCTION mi = BNTStemplate | (relative_address&0x3FFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "BC", nea, THREAD_SWITCH_BIT, signedHex(relative_address<<2));
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitLtoc (int RT, int offset) {
    if (fits(offset, 16)) {
      emitL(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU(RT, JTOC, offset>>16);
      emitL  (RT, offset&0xFFFF, RT);
    } else {
      emitCAU(RT, JTOC, (offset>>16)+1);
      emitL  (RT, offset|0xFFFF0000, RT);
    }
  }

  // A fixed size (2 instruction) load from JTOC
  final void emitLtoc2 (int RT, int offset) {
    if (0 == (offset&0x8000)) {
      emitCAU(RT, JTOC, offset>>16);
      emitL  (RT, offset&0xFFFF, RT);
    } else {
      emitCAU(RT, JTOC, (offset>>16)+1);
      emitL  (RT, offset|0xFFFF0000, RT);
    }
  }

  final void emitSTtoc (int RT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitST(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU(Rz, JTOC, offset>>16);
      emitST (RT, offset&0xFFFF, Rz);
    } else {
      emitCAU(Rz, JTOC, (offset>>16)+1);
      emitST (RT, offset|0xFFFF0000, Rz);
    }
  }

  // A fixed size (2 instruction) store into JTOC
  final void emitSTtoc2 (int RT, int offset, int Rz) {
    if (0 == (offset&0x8000)) {
      emitCAU(Rz, JTOC, offset>>16);
      emitST (RT, offset&0xFFFF, Rz);
    } else {
      emitCAU(Rz, JTOC, (offset>>16)+1);
      emitST (RT, offset|0xFFFF0000, Rz);
    }
  }
  
  final void emitCALtoc (int RT, int offset) {
    if (fits(offset, 16)) {
      emitCAL(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU(RT, JTOC, offset>>16);
      emitCAL(RT, offset&0xFFFF, RT);
    } else {
      emitCAU(RT, JTOC, (offset>>16)+1);
      emitCAL(RT, offset|0xFFFF0000, RT);
    }
  }

  final void emitLFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU( Rz, JTOC, offset>>16);
      emitLFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitCAU( Rz, JTOC, (offset>>16)+1);
      emitLFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitSTFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU ( Rz, JTOC, offset>>16);
      emitSTFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitCAU ( Rz, JTOC, (offset>>16)+1);
      emitSTFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitLFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU( Rz, JTOC, offset>>16);
      emitLFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitCAU( Rz, JTOC, (offset>>16)+1);
      emitLFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitSTFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitCAU ( Rz, JTOC, offset>>16);
      emitSTFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitCAU ( Rz, JTOC, (offset>>16)+1);
      emitSTFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  final void emitLVAL (int RT, int val) {
    if (VM.TraceAssembler && (comment == null)) comment("" + val);
    if (fits(val, 16)) { 
      emitLIL(RT, val);
    } else if ((val&0x8000) == 0) {
      emitLIL(RT, val&0xFFFF);
      emitCAU(RT, RT,  val>>>16);
    } else {// top half of RT is 0xFFFF
      emitLIL(RT, val|0xFFFF0000);
      emitCAU(RT, RT, (val>>>16)+1);
    }
  }

  final void emitDATA (int i) {
    INSTRUCTION mi = i;
    if (VM.TraceAssembler)
      asm(mIP, mi, "DATA", "" + i);
    mIP++;
    mc.addInstruction(mi);
  }

  // Convert generated machine code into final form.
  //
  VM_MachineCode makeMachineCode () {
    mc.setBytecodeMap(b2m);
    mc.finish();
    return mc;
  }

   /**
    * Append an array of INSTRUCTION to the current machine code
    * @see VM_Compiler.storeParametersForAIX()
    */
   void
   appendInstructions (INSTRUCTION[] instructionSegment) {
     
     for (int i=0; i<instructionSegment.length; i++) {
       mc.addInstruction(instructionSegment[i]);
     }

   }

  // new PowerPC instuctions

  static final int SYNCtemplate = 31<<26 | 598<<1;
  
  static final INSTRUCTION SYNC () {
    return 31<<26 | 598<<1;
  }

  final void emitSYNC () {
    INSTRUCTION mi = SYNCtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sync");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ICBItemplate = 31<<26 | 982<<1;
  
  static final INSTRUCTION ICBI (int RA, int RB) {
    return 31<<26 | RA<<16 | RB<<11 | 982<<1;
  }

  final void emitICBI (int RA, int RB) {
    INSTRUCTION mi = ICBItemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "icbi", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ISYNCtemplate = 19<<26 | 150<<1;
  
  static final INSTRUCTION ISYNC () {
    return 19<<26 | 150<<1;
  }

  final void emitISYNC () {
    INSTRUCTION mi = ISYNCtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "isync");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBFtemplate = 31<<26 | 86<<1;
  
  static final INSTRUCTION DCBF (int RA, int RB) {
    return 31<<26 | RA<<16 | RB<<11 | 86<<1;
  }

  final void emitDCBF (int RA, int RB) {
    INSTRUCTION mi = DCBFtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "dcbf", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBSTtemplate = 31<<26 | 54<<1;
  
  static final INSTRUCTION DCBST (int RA, int RB) {
    return 31<<26 | RA<<16 | RB<<11 | 54<<1;
  }

  final void emitDCBST (int RA, int RB) {
    INSTRUCTION mi = DCBSTtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "dcbst", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBtemplate = 31<<26 | 392<<11 | 371<<1;
  
  static final INSTRUCTION MFTB (int RT) {
    return 31<<26 | RT<<21 | 392<<11 | 371<<1;
  }

  final void emitMFTB (int RT) {
    INSTRUCTION mi = MFTBtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mftb", RT);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBUtemplate = 31<<26 | 424<<11 | 371<<1;
  
  static final INSTRUCTION MFTBU (int RT) {
    return 31<<26 | RT<<21 | 424<<11 | 371<<1;
  }

  final void emitMFTBU (int RT) {
    INSTRUCTION mi = MFTBUtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mftbu", RT);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIZtemplate = 63<<26 | 15<<1;
  
  static final INSTRUCTION FCTIZ (int RA, int RB) {
    return 63<<26 | RA<<21 | RB<<11 | 15<<1;
  }

  final void emitFCTIZ (int RA, int RB) {
    INSTRUCTION mi = FCTIZtemplate | RA<<21 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fctiz", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  // -----------------------------------------------------------//
  // The following section contains assembler "macros" used by: //
  //    VM_Compiler                                             //
  //    VM_MagicCompiler                                        //
  //    VM_Barriers                                             //
  // -----------------------------------------------------------//
  
  // Emit baseline stack overflow instruction sequence.
  // Before:   FP is current (calling) frame
  //           PR is the current VM_Processor, which contains a pointer to the active thread.
  // After:    R0, S0 destroyed
  //
  void emitStackOverflowCheck (int frameSize) {
    emitL   ( 0,  VM_Entrypoints.activeThreadStackLimitOffset, PROCESSOR_REGISTER);   // R0 := &stack guard page
    emitCAL (S0, -frameSize, FP);                        // S0 := &new frame
    emitTLT (S0,  0);                                    // trap if new frame below guard page
    }

  // Emit baseline stack overflow instruction sequence for native method prolog.
  // For the lowest Java to C transition frame in the stack, check that there is space of
  // STACK_SIZE_NATIVE words available on the stack;  enlarge stack if necessary.
  // For subsequent Java to C transition frames, check for the requested size and don't resize
  // the stack if overflow
  // Before:   FP is current (calling) frame
  //           PR is the current VM_Processor, which contains a pointer to the active thread.
  // After:    R0, S0 destroyed
  //
  void emitNativeStackOverflowCheck (int frameSize) {
    emitL    (S0, VM_Entrypoints.activeThreadOffset, PROCESSOR_REGISTER);   // S0 := thread pointer
    emitL    (S0, VM_Entrypoints.jniEnvOffset, S0);      // S0 := thread.jniEnv
    emitL    ( 0, VM_Entrypoints.JNIRefsTopOffset,S0);   // R0 := thread.jniEnv.JNIRefsTop
    emitL    (S0, VM_Entrypoints.activeThreadOffset, PROCESSOR_REGISTER);   // S0 := thread pointer
    emitCMPI ( 0, 0);                                 	 // check if S0 == 0 -> first native frame on stack
    emitBEQ(5);                                      	 // skip 4 instructions forward
    // check for enough space for requested frame size
    emitL   ( 0,  VM_Entrypoints.stackLimitOffset, S0);  // R0 := &stack guard page
    emitCAL (S0, -frameSize, FP);                        // S0 := &new frame pointer
    emitTLT (S0,  0);                                    // trap if new frame below guard page
    emitB(8);                                      	 // branch 5 instructions forward    
    // check for enough space for STACK_SIZE_JNINATIVE 
    emitL   ( 0,  VM_Entrypoints.stackLimitOffset, S0);  // R0 := &stack guard page
    emitLIL(S0, 1);
    emitSLI(S0, S0, STACK_LOG_JNINATIVE);
    emitSF (S0, S0, FP);             // S0 := &new frame pointer

    emitCMP(0, S0);
    emitBLE( 2 );
    emitTWI ( 1 );                                    // trap if new frame pointer below guard page
    }

  // Emit baseline call instruction sequence.
  // Taken:    offset of sp save area within current (baseline) stackframe, in bytes
  // Before:   LR is address to call
  //           FP is address of current frame
  // After:    no registers changed
  //
  static final int CALL_INSTRUCTIONS = 3; // number of instructions generated by emitCall()
  void emitCall (int spSaveAreaOffset) {
    emitST(SP, spSaveAreaOffset, FP); // save SP
    emitBLRL  ();
    emitL (SP, spSaveAreaOffset, FP); // restore SP
    }

  // Emit baseline call instruction sequence.
  // Taken:    offset of sp save area within current (baseline) stackframe, in bytes
  //           "hidden" parameter (e.g. for fast invokeinterface collision resolution
  // Before:   LR is address to call
  //           FP is address of current frame
  // After:    no registers changed
  //
  void emitCallWithHiddenParameter (int spSaveAreaOffset, int hiddenParameter) {
    emitST  (SP, spSaveAreaOffset, FP); // save SP
    emitLVAL(SP, hiddenParameter);      // pass "hidden" parameter in SP scratch  register
    emitBLRL();
    emitL   (SP, spSaveAreaOffset, FP); // restore SP
    }

  //-#if RVM_WITH_SPECIALIZATION

  // Emit baseline call instruction sequence.
  // Taken:    offset of sp save area within current (baseline) stackframe, in bytes
  //           call site number for specialization
  //
  // Before:   LR is address to call
  //           FP is address of current frame
  // After:    no registers changed
  //
  void emitSpecializationCall (int spSaveAreaOffset, VM_Method m, int bIP) {
      int callSiteNumber = 0;
      if (VM_SpecializationSentry.isValid()) {
	  callSiteNumber =
	      VM_SpecializationCallSites.getCallSiteNumber(null, m, bIP);
      }
      emitST  (SP, spSaveAreaOffset, FP); // save SP
      emitLVAL(0, callSiteNumber<<2);      // pass call site in reg. 0
      emitBLRL();
      emitL   (SP, spSaveAreaOffset, FP); // restore SP
  }

  // Emit baseline call instruction sequence.
  // Taken:    offset of sp save area within current (baseline) stackframe, in bytes
  //           call site number for specialization
  //
  // Before:   LR is address to call
  //           FP is address of current frame
  // After:    no registers changed
  //
  void emitSpecializationCallWithHiddenParameter(int spSaveAreaOffset, 
						 int hiddenParameter,
						 VM_Method m,
						 int bIP) 
  {
      int callSiteNumber = 0;
      if (VM_SpecializationSentry.isValid()) {
	  callSiteNumber =
	      VM_SpecializationCallSites.getCallSiteNumber(null, m, bIP);
      }
      emitST  (SP, spSaveAreaOffset, FP); // save SP
      emitLVAL(SP, hiddenParameter);    // pass "hidden" parameter in reg. SP 
      emitLVAL(0, callSiteNumber<<2);      // pass call site in reg. 0
      emitBLRL();
      emitL   (SP, spSaveAreaOffset, FP); // restore SP
  }

  //-#endif

  // Emit baseline dynamically linked call instruction sequence.
  // Taken:    offset of sp save area within current (baseline) stackframe, in bytes
  //           method /or/ field id associated with dynamic link site
  // Before:   LR is address to call
  //           FP is address of current frame
  // After:    no registers changed
  //
  void emitDynamicCall (int spSaveAreaOffset, int memberId) {
    emitST(SP, spSaveAreaOffset, FP); // save SP
    emitBLRL();
    emitDATA(memberId);
    emitL (SP, spSaveAreaOffset, FP); // restore SP
    }
}
