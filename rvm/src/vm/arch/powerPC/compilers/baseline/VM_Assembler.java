/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Machine code generators:
 *
 * Corresponding to a PowerPC assembler instruction of the form
 *    xx A,B,C
 * there will be a method
 *    void emitXX (int A, int B, int C).
 * 
 * The emitXX method appends this instruction to an VM_MachineCode object.
 * The name of a method for generating assembler instruction with the record
 * bit set (say xx.) will be end in a lower-case r (emitXXr).
 * 
 * mIP will be incremented to point to the next machine instruction.
 * 
 * Machine code generators:
 *
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Derek Lieber
 * @modified Dave Grove
 */
final class VM_Assembler implements VM_BaselineConstants,
				    VM_AssemblerConstants {

  VM_Assembler (int length) {
    this(length, false);
  }

  VM_Assembler (int length, boolean sp) {
    mc = new VM_MachineCode();
    mIP = 0;
    shouldPrint = sp;
  }

  /* machine code */
  VM_MachineCode mc;
  private int    mIP    = 0; // current machine code instruction

  private boolean shouldPrint;

  /* assembler stuff:

     labels and comments can be added to the an assembler instruction by
     calling the label and comment methods with the appropriate Strings
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

  void noteBytecode (int i, String bcode) {
    if (!VM.TraceAssembler) return;
    VM.sysWrite("[" + i + "] " + bcode + "\n");
    if (!VM.TraceAssembler) return;
  }

  private void asm (int inum, INSTRUCTION mi, String opcode, String args) {
    String instr = right(hex(inum<<2),6) + "| " + right(hex((int)mi),8);
    instr += " " + left(opcode,6) + left(args,20);
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

  int getMachineCodeIndex () {
    return mIP;
  }

  /* Handling forward branch references */

  VM_ForwardReference forwardRefs = null;

  /* call before emiting code for the branch */
  final void reserveForwardBranch (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.UnconditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting code for the branch */
  final void reserveForwardConditionalBranch (int where) {
    emitNOP();
    VM_ForwardReference fr = new VM_ForwardReference.ConditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting code for the branch */
  final void reserveShortForwardConditionalBranch (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.ConditionalBranch(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting data for the case branch */
  final void reserveForwardCase (int where) {
    VM_ForwardReference fr = new VM_ForwardReference.SwitchCase(mIP, where);
    forwardRefs = VM_ForwardReference.enqueue(forwardRefs, fr);
  }

  /* call before emiting code for the target */
  final void resolveForwardReferences (int label) {
    if (forwardRefs == null) return; 
    forwardRefs = VM_ForwardReference.resolveMatching(this, forwardRefs, label);
  }

  final void patchUnconditionalBranch(int sourceMachinecodeIndex) {
    if (VM.TraceAssembler) System.out.print(" <- " + hex(sourceMachinecodeIndex << 2));
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if (VM.VerifyAssertions) VM.assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
    instr |= (delta<<2);
    mc.putInstruction(sourceMachinecodeIndex, instr);
  }
  
  final void patchConditionalBranch(int sourceMachinecodeIndex) {
    if (VM.TraceAssembler) System.out.print(" << " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      if (VM.VerifyAssertions) VM.assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
      instr ^= 0x01000008; // make skip instruction with opposite sense
      mc.putInstruction(sourceMachinecodeIndex-1, instr); // skip unconditional branch to target
      mc.putInstruction(sourceMachinecodeIndex,  Btemplate | (delta&0xFFFFFF)<<2);
    }
  }

  final void patchShortBranch(int sourceMachinecodeIndex) {
    if (VM.TraceAssembler) System.out.print(" << " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = mIP - sourceMachinecodeIndex;
    INSTRUCTION instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      throw new InternalError("Long offset doesn't fit in short branch\n");
    }
  }

  final void patchSwitchCase(int sourceMachinecodeIndex) {
    if (VM.TraceAssembler) System.out.print(" <+ " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = (mIP - sourceMachinecodeIndex) << 2;
    // correction is number of bytes of source off switch base
    int         correction = (int)mc.getInstruction(sourceMachinecodeIndex);
    INSTRUCTION offset = (INSTRUCTION)(delta+correction);
    mc.putInstruction(sourceMachinecodeIndex, offset);
  }


  /* machine instructions */

  static final int Atemplate = 31<<26 | 10<<1;

  final void emitA (int RT, int RA, int RB) {
    INSTRUCTION mi = Atemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "a", RT,  RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int AEtemplate = 31<<26 | 138<<1;

  final void emitAE (int RT, int RA, int RB) {
    INSTRUCTION mi = AEtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "ae", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int AIrtemplate = 13<<26;

  final void emitAIr (int RT, int RA, int SI) {
    if (VM.VerifyAssertions) VM.assert(fits(SI, 16));
    INSTRUCTION mi = AIrtemplate | RT<<21 | RA<<16 | (SI & 0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "ai.", RT, RA, signedHex(SI));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDtemplate = 31<<26 | 28<<1;

  final void emitAND (int RA, int RS, int RB) {
    INSTRUCTION mi = ANDtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "and", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDItemplate = 28<<26;

  final void emitANDI (int RA, int RS, int U) {
    if (VM.VerifyAssertions) VM.assert((U>>>16) == 0);
    INSTRUCTION mi = ANDItemplate | RS<<21 | RA<<16 | U;
    if (VM.TraceAssembler)
      asm(mIP, mi, "andi.", RA, RS, hex(U));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Btemplate = 18<<26;

  /** Deprecated! */
  final void emitB (int relative_address) {
    if (VM.VerifyAssertions) VM.assert(fits(relative_address,24));
    INSTRUCTION mi = Btemplate | (relative_address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "b", signedHex(relative_address<<2));
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitB (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    emitB(relative_address);
  }

  final VM_ForwardReference emitForwardB() {
    VM_ForwardReference fr = new VM_ForwardReference.ShortBranch(mIP);
    emitB(0);
    return fr;
  }

  static final int BLAtemplate = 18<<26 | 3;

  final void emitBLA (int address) {
    if (VM.VerifyAssertions) VM.assert(fits(address,24));
    INSTRUCTION mi = BLAtemplate | (address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bla", hex(address));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLtemplate = 18<<26 | 1;

  /** Deprecated! */
  final void emitBL (int relative_address) {
    if (VM.VerifyAssertions) VM.assert(fits(relative_address,24));
    INSTRUCTION mi = BLtemplate | (relative_address&0xFFFFFF)<<2;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bl", signedHex(relative_address<<2));
    mIP++;
    mc.addInstruction(mi);
  }

  final void emitBL (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    emitBL(relative_address);
  }


  final VM_ForwardReference emitForwardBL() {
    VM_ForwardReference fr = new VM_ForwardReference.ShortBranch(mIP);
    emitBL(0);
    return fr;
  }

  static final int BCtemplate = 16<<26;

  public static final int flipCode(int cc) {
    switch(cc) {
    case LT: return GE;
    case GT: return LE;
    case EQ: return NE;
    case LE: return GT;
    case GE: return LT;
    case NE: return EQ;
    }
    if (VM.VerifyAssertions) VM.assert(false);
    return -1;
  }

  private final void _emitBC (int cc, int relative_address) {
    if (fits(relative_address, 14)) {
      INSTRUCTION mi = BCtemplate | cc | (relative_address&0x3FFF)<<2;
      if (VM.TraceAssembler) {
	switch(cc) {
	case LT: asm(mIP, mi, "blt", signedHex(relative_address<<2)); break;
	case GT: asm(mIP, mi, "bgt", signedHex(relative_address<<2)); break;
	case EQ: asm(mIP, mi, "beq", signedHex(relative_address<<2)); break;
	case LE: asm(mIP, mi, "ble", signedHex(relative_address<<2)); break;
	case GE: asm(mIP, mi, "bge", signedHex(relative_address<<2)); break;
	case NE: asm(mIP, mi, "bne", signedHex(relative_address<<2)); break;
	}
      }
      mIP++;
      mc.addInstruction(mi);
    } else {
      _emitBC(flipCode(cc), 2);
      emitB(relative_address-1);
    }
  }

  final void emitBC (int cc, int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardConditionalBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBC(cc, relative_address);
  }

  final void emitBC (int cc, int relative_address) {
    relative_address -= mIP;
    if (VM.VerifyAssertions) VM.assert(relative_address < 0);
    _emitBC(cc, relative_address);
  }

  final VM_ForwardReference emitForwardBC(int cc) {
    VM_ForwardReference fr = new VM_ForwardReference.ShortBranch(mIP);
    _emitBC(cc, 0);
    return fr;
  }

  final void emitBGT (int relative_address) {
    _emitBC(GT, relative_address);
  }

  final void emitBLT (int relative_address) {
    _emitBC(LT, relative_address);
  }

  final void emitBEQ (int relative_address) {
    _emitBC(EQ, relative_address);
  }

  final void emitBLE (int relative_address) {
    _emitBC(LE, relative_address);
  }

  final void emitBGE (int relative_address) {
    _emitBC(GE, relative_address);
  }

  final void emitBNE (int relative_address) {
    _emitBC(NE, relative_address);
  }

  // delta i: difference between address of case i and of delta 0
  final void emitSwitchCase(int i, int relative_address, int bTarget) {
    int data = i << 2;
    if (relative_address == 0) {
      reserveForwardCase(bTarget);
    } else {
      data += ((relative_address - mIP) << 2);
    }
    if (VM.TraceAssembler) asm(mIP, data, "DATA", "" + data);
    mIP++;
    mc.addInstruction(data);
  }

  static final int BLRtemplate = 19<<26 | 0x14<<21 | 16<<1;

  final void emitBLR () {
    INSTRUCTION mi = BLRtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "blr");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLRLtemplate = 19<<26 | 0x14<<21 | 16<<1 | 1;

  final void emitBLRL () {
    INSTRUCTION mi = BLRLtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "blrl");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCTRtemplate = 19<<26 | 0x14<<21 | 528<<1;

  final void emitBCTR () {
    INSTRUCTION mi = BCTRtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bctr");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCTRLtemplate = 19<<26 | 0x14<<21 | 528<<1 | 1;

  final void emitBCTRL () {
    INSTRUCTION mi = BCTRLtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bctrl");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CALtemplate = 14<<26;

  final void emitCAL (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = CALtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "cal", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CAUtemplate = 15<<26;

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

  final void emitCMPI (int BF, int RA, int V) {
    if (VM.VerifyAssertions) VM.assert(fits(V, 16));
    INSTRUCTION mi = CMPItemplate | BF<<23 | RA<<16 | (V&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "cmpi", BF, RA, signedHex(V));
    mIP++;
    mc.addInstruction(mi);
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

  final void emitCRAND (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crand", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDCtemplate = 19<<26 | 129<<1;

  final void emitCRANDC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRANDCtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crandc", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORtemplate = 19<<26 | 449<<1;

  final void emitCROR (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "cror", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORCtemplate = 19<<26 | 417<<1;

  final void emitCRORC (int BT, int BA, int BB) {
    INSTRUCTION mi = CRORCtemplate | BT<<21 | BA<<16 | BB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "crorc", BT, BA, BB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FAtemplate = 63<<26 | 21<<1;

  final void emitFA (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FAtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fa", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FAstemplate = 59<<26 | 21<<1; // single-percision add

  final void emitFAs (int FRT, int FRA,int FRB) {
    INSTRUCTION mi = FAstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "faS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FABStemplate = 63<<26 | 264<<1;

  final void emitFABS (int FRT, int FRB) {
    INSTRUCTION mi = FABStemplate | FRT<<21 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fabs", FRT, FRB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCMPUtemplate = 63<<26;

  final void emitFCMPU (int FRA,int FRB) {
    INSTRUCTION mi = FCMPUtemplate | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fcmpu", FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDtemplate = 63<<26 | 18<<1;

  final void emitFD (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fd", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDstemplate = 59<<26 | 18<<1; // single-precision divide

  final void emitFDs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FDstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fdS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMtemplate = 63<<26 | 25<<1;

  final void emitFM (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMtemplate | FRT<<21 | FRA<<16 | FRB<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fm", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMstemplate = 59<<26 | 25<<1; // single-precision fm

  final void emitFMs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FMstemplate | FRT<<21 | FRA<<16 | FRB<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fmS", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMAtemplate = 63<<26 | 29<<1;

  final void emitFMA (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FMAtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fma", FRT, FRA, FRC, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNMStemplate = 63<<26 | 30<<1;

  final void emitFNMS (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FNMStemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fnms", FRT, FRA, FRC, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNEGtemplate = 63<<26 | 40<<1;

  final void emitFNEG (int FRT, int FRB) {
    INSTRUCTION mi = FNEGtemplate | FRT<<21 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fneg", FRT, FRB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FStemplate = 63<<26 | 20<<1;

  final void emitFS (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fs", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSstemplate = 59<<26 | 20<<1;

  final void emitFSs (int FRT, int FRA, int FRB) {
    INSTRUCTION mi = FSstemplate | FRT<<21 | FRA<<16 | FRB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "FSs", FRT, FRA, FRB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSELtemplate = 63<<26 | 23<<1;

  final void emitFSEL (int FRT, int FRA, int FRC, int FRB) {
    INSTRUCTION mi = FSELtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    if (VM.TraceAssembler)
      asm(mIP, mi, "fsel", FRT, FRA, FRB, FRC );
    mIP++;
    mc.addInstruction(mi);
  }

  // LOAD/ STORE MULTIPLE

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambiguous) 
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
  // (the Assembler Language Reference seems ambiguous) 
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

  final void emitL (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = Ltemplate  | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "l", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZtemplate = 34<<26;

  final void emitLBZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LBZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lbz", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZXtemplate = 31<<26 | 87<<1;

  final void emitLBZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LBZXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lbzx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAtemplate = 42<<26;

  final void emitLHA (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LHAtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lha", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZtemplate = 40<<26;

  final void emitLHZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LHZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhz", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDtemplate = 50<<26;

  final void emitLFD (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFDtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfd", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDUtemplate = 51<<26;

  final void emitLFDU (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFDUtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfdu", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDXtemplate = 31<<26 | 599<<1;

  final void emitLFDX (int FRT, int RA, int RB) {
    INSTRUCTION mi = LFDXtemplate | FRT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfdx", FRT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFStemplate = 48<<26;

  final void emitLFS (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LFStemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lfs", FRT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAXtemplate = 31<<26 | 343<<1;

  final void emitLHAX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHAXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhax", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZXtemplate = 31<<26 | 279<<1;

  final void emitLHZX (int RT, int RA, int RB) {
    INSTRUCTION mi = LHZXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lhzx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
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

  final void emitLU (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = LUtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "lu", RT, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LXtemplate = 31<<26 | 23<<1;

  final void emitLX (int RT, int RA, int RB) {
    INSTRUCTION mi = LXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LUXtemplate = 31<<26 | 55<<1;

  final void emitLUX (int RT, int RA, int RB) {
    INSTRUCTION mi = LUXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lux", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  
  static final int LWARXtemplate = 31<<26 | 20<<1;

  final void emitLWARX (int RT, int RA, int RB) {
    INSTRUCTION mi = LWARXtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "lwarx", RT, RA, RB );
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFLRtemplate = 31<<26 | 0x08<<16 | 339<<1;

  final void emitMFLR (int RT) {
    INSTRUCTION mi = MFLRtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mflr", RT);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFCRtemplate = 31<<26 | 19<<1;

  final void emitMFCR (int RT) {
    INSTRUCTION mi = MFCRtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mfcr", RT);
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int MFSPRtemplate = 31<<26 | 339<<1;

  final void emitMFSPR (int RT, int SPR) {
    INSTRUCTION mi = MFSPRtemplate | RT<<21 | SPR<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mfspr", RT, SPR);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTLRtemplate = 31<<26 | 0x08<<16 | 467<<1;

  final void emitMTLR (int RS) {
    INSTRUCTION mi = MTLRtemplate | RS<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mtlr", RS);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTCRFtemplate = 31<<26 | 144<<1;

  final void emitMTCRF (int mask, int RS) {
    INSTRUCTION mi = MTCRFtemplate | mask<<12 | RS<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mtcrf", mask, RS);
    mIP++;
    mc.addInstruction(mi);
  }


  static final int MTCTRtemplate = 31<<26 | 0x09<<16 | 467<<1;

  final void emitMTCTR (int RS) {
    INSTRUCTION mi = MTCTRtemplate | RS<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mtctr", RS);
    mIP++;
    mc.addInstruction(mi);
  }
 
  static final int MULHWUtemplate = 31<<26 | 11<<1;

  final void emitMULHWU (int RT, int RA, int RB) {
    INSTRUCTION mi = MULHWUtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mulhwu", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DIVtemplate = 31<<26 | 491<<1;

  final void emitDIV (int RT, int RA, int RB) {
    INSTRUCTION mi = DIVtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "div", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULStemplate = 31<<26 | 235<<1;

  final void emitMULS (int RT, int RA, int RB) {
    INSTRUCTION mi = MULStemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "muls", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int NEGtemplate = 31<<26 | 104<<1;

  final void emitNEG (int RT, int RA) {
    INSTRUCTION mi = NEGtemplate | RT<<21 | RA<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "neg", RT, RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORtemplate = 31<<26 | 444<<1;

  final void emitOR (int RA, int RS, int RB) {
    INSTRUCTION mi = ORtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "or", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int RLWINM_template = 21<<26;

  final void emitRLWINM (int RA, int RS, int SH, int MB, int ME) {
    INSTRUCTION mi = RLWINM_template | RS<<21 | RA<<16 | SH<<11 | MB<<6 | ME<<1;
    /*
    if (VM.TraceAssembler)
      asm(mIP, mi, "rlwinm", RA, RS, SH, MB, ME);
    */
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFrtemplate = 31<<26 | 8<<1 | 1;

  final void emitSFr (int RT, int RA, int RB) {
    INSTRUCTION mi = SFrtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sf.", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFtemplate = 31<<26 | 8<<1;

  final void emitSF (int RT, int RA, int RB) {
    INSTRUCTION mi = SFtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sf", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFItemplate = 8<<26;

  final void emitSFI (int RA, int RS, int S) {
    if (VM.VerifyAssertions) VM.assert(fits(S,16));
    INSTRUCTION mi = SFItemplate | RS<<21 | RA<<16 | S;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfi", RA, RS, signedHex(S));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFErtemplate = 31<<26 | 136<<1 | 1;

  final void emitSFEr (int RT, int RA, int RB) {
    INSTRUCTION mi = SFErtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfe.", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFEtemplate = 31<<26 | 136<<1;

  final void emitSFE (int RT, int RA, int RB) {
    INSTRUCTION mi = SFEtemplate | RT<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfe", RT, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SFZEtemplate = 31<<26 | 200<<1;

  final void emitSFZE (int RT, int RA) {
    INSTRUCTION mi = SFZEtemplate | RT<<21 | RA<<16;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sfze", RT, RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLtemplate = 31<<26 | 24<<1;

  final void emitSL (int RA, int RS, int RB) {
    INSTRUCTION mi = SLtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sl", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLItemplate = 21<<26;

  final void emitSLI (int RA, int RS, int N) {
    INSTRUCTION mi = SLItemplate | RS<<21 | RA<<16 | N<<11 | (31-N)<<1;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sli", RA, RS, signedHex(N));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRtemplate = 31<<26 | 536<<1;

  final void emitSR (int RA, int RS, int RB) {
    INSTRUCTION mi = SRtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sr", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAtemplate = 31<<26 | 792<<1;

  final void emitSRA (int RA, int RS, int RB) {
    INSTRUCTION mi = SRAtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sra", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAItemplate = 31<<26 | 824<<1;

  final void emitSRAI (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAItemplate | RS<<21 | RA<<16 | SH<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "srai", RA, RS, signedHex(SH));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAIrtemplate = 31<<26 | 824<<1 | 1;

  final void emitSRAIr (int RA, int RS, int SH) {
    INSTRUCTION mi = SRAIrtemplate | RS<<21 | RA<<16 | SH<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "srai.", RA, RS, signedHex(SH));
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STtemplate = 36<<26;

  final void emitST (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "st", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBtemplate = 38<<26;

  final void emitSTB (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STBtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stb", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBXtemplate = 31<<26 | 215<<1;

  final void emitSTBX (int RS, int RA, int RB) {
    INSTRUCTION mi = STBXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stbx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STHXtemplate = 31<<26 | 407<<1;

  final void emitSTHX (int RS, int RA, int RB) {
    INSTRUCTION mi = STHXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sthx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STXtemplate = 31<<26 | 151<<1;

  final void emitSTX (int RS, int RA, int RB) {
    INSTRUCTION mi = STXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stx", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDtemplate = 54<<26;

  final void emitSTFD (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFDtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfd", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDUtemplate = 55<<26;

  final void emitSTFDU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFDUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfdu", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDXtemplate = 31<<26 | 727<<1;

  final void emitSTFDX (int FRS, int RA, int RB) {
    INSTRUCTION mi = STFDXtemplate | FRS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfdx", FRS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFStemplate = 52<<26;

  final void emitSTFS (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFStemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfs", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFSUtemplate = 53<<26;

  final void emitSTFSU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STFSUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stfsu", FRS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STUtemplate = 37<<26;

  final void emitSTU (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM.assert(fits(D, 16));
    INSTRUCTION mi = STUtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    if (VM.TraceAssembler)
      asm(mIP, mi, "stu", RS, signedHex(D), RA);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STUXtemplate = 31<<26 | 183<<1;

  final void emitSTUX (int RS, int RA, int RB) {
    INSTRUCTION mi = STUXtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stux", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWCXrtemplate = 31<<26 | 150<<1 | 1;

  final void emitSTWCXr (int RS, int RA, int RB) {
    INSTRUCTION mi = STWCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "stwcx.", RS, RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Ttemplate = 31<<26 | 4<<1;

  static final int TItemplate = 3<<26;

  final void emitTI (int TO, int RA, int SI) {
    INSTRUCTION mi = TItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    if (VM.TraceAssembler)
      asm(mIP, mi, "ti", TO, RA, signedHex(SI));
    mIP++;
    mc.addInstruction(mi);
  }
  
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

  final void emitXOR (int RA, int RS, int RB) {
    INSTRUCTION mi = XORtemplate | RS<<21 | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "xor", RA, RS, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORItemplate = 26<<26;

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
  static final int BNTStemplate = BCtemplate | GE | THREAD_SWITCH_BIT<<16;
  final VM_ForwardReference emitBNTS () {
    VM_ForwardReference fr = new VM_ForwardReference.ShortBranch(mIP);
    INSTRUCTION mi = BNTStemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "bge", THREAD_SWITCH_BIT, signedHex(0));
    mIP++;
    mc.addInstruction(mi);
    return fr;
  }

  final void emitLoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitL  (RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitCAU(RT, RA, offset>>16);
      emitL  (RT, offset&0xFFFF, RT);
    } else {
      emitCAU(RT, RA, (offset>>16)+1);
      emitL  (RT, offset|0xFFFF0000, RT);
    }
  }
    

  final void emitLtoc (int RT, int offset) {
    emitLoffset(RT, JTOC, offset);
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

  // Convert generated machine code into final form.
  //
  VM_MachineCode finalizeMachineCode (int[] bytecodeMap) {
    mc.setBytecodeMap(bytecodeMap);
    return makeMachineCode();
  }

  VM_MachineCode makeMachineCode () {
    mc.finish();
    if (shouldPrint) {
      INSTRUCTION[] instructions = mc.getInstructions();
      boolean saved = VM_BaselineCompiler.options.PRINT_MACHINECODE;
      try {
	VM_BaselineCompiler.options.PRINT_MACHINECODE = false;
	for (int i = 0; i < instructions.length; i++) {
	  VM.sysWrite(VM_Services.getHexString(i << LG_INSTRUCTION_WIDTH, true));
	  VM.sysWrite(" : ");
	  VM.sysWrite(VM_Services.getHexString(instructions[i], false));
	  VM.sysWrite("  ");
	  VM.sysWrite(PPC_Disassembler.disasm(instructions[i], i << LG_INSTRUCTION_WIDTH));
	  VM.sysWrite("\n");
	}
      } finally {
	VM_BaselineCompiler.options.PRINT_MACHINECODE = saved;
      }
    }
    return mc;
  }

   /**
    * Append an array of INSTRUCTION to the current machine code
    */
   void appendInstructions (INSTRUCTION[] instructionSegment) {
     for (int i=0; i<instructionSegment.length; i++) {
       mc.addInstruction(instructionSegment[i]);
     }
   }

  // new PowerPC instuctions

  static final int SYNCtemplate = 31<<26 | 598<<1;
  
  final void emitSYNC () {
    INSTRUCTION mi = SYNCtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "sync");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ICBItemplate = 31<<26 | 982<<1;
  
  final void emitICBI (int RA, int RB) {
    INSTRUCTION mi = ICBItemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "icbi", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ISYNCtemplate = 19<<26 | 150<<1;
  
  final void emitISYNC () {
    INSTRUCTION mi = ISYNCtemplate;
    if (VM.TraceAssembler)
      asm(mIP, mi, "isync");
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBFtemplate = 31<<26 | 86<<1;
  
  final void emitDCBF (int RA, int RB) {
    INSTRUCTION mi = DCBFtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "dcbf", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBSTtemplate = 31<<26 | 54<<1;
  
  final void emitDCBST (int RA, int RB) {
    INSTRUCTION mi = DCBSTtemplate | RA<<16 | RB<<11;
    if (VM.TraceAssembler)
      asm(mIP, mi, "dcbst", RA, RB);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBtemplate = 31<<26 | 392<<11 | 371<<1;
  
  final void emitMFTB (int RT) {
    INSTRUCTION mi = MFTBtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mftb", RT);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBUtemplate = 31<<26 | 424<<11 | 371<<1;
  
  final void emitMFTBU (int RT) {
    INSTRUCTION mi = MFTBUtemplate | RT<<21;
    if (VM.TraceAssembler)
      asm(mIP, mi, "mftbu", RT);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIZtemplate = 63<<26 | 15<<1;
  
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
    emitL   ( 0,  VM_Entrypoints.activeThreadStackLimitField.getOffset(), PROCESSOR_REGISTER);   // R0 := &stack guard page
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
    emitL    (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitL    (S0, VM_Entrypoints.jniEnvField.getOffset(), S0);      // S0 := thread.jniEnv
    emitL    ( 0, VM_Entrypoints.JNIRefsTopField.getOffset(),S0);   // R0 := thread.jniEnv.JNIRefsTop
    emitL    (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitCMPI ( 0, 0);                                 	 // check if S0 == 0 -> first native frame on stack
    emitBEQ(5);                                      	 // skip 4 instructions forward
    // check for enough space for requested frame size
    emitL   ( 0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
    emitCAL (S0, -frameSize, FP);                        // S0 := &new frame pointer
    emitTLT (S0,  0);                                    // trap if new frame below guard page
    emitB(8);                                      	 // branch 5 instructions forward    
    // check for enough space for STACK_SIZE_JNINATIVE 
    emitL   ( 0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
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
      callSiteNumber = VM_SpecializationCallSites.getCallSiteNumber(null, m, bIP);
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
						 int bIP) {
    int callSiteNumber = 0;
    if (VM_SpecializationSentry.isValid()) {
      callSiteNumber = VM_SpecializationCallSites.getCallSiteNumber(null, m, bIP);
    }
    emitST  (SP, spSaveAreaOffset, FP); // save SP
    emitLVAL(SP, hiddenParameter);    // pass "hidden" parameter in reg. SP 
    emitLVAL(0, callSiteNumber<<2);      // pass call site in reg. 0
    emitBLRL();
    emitL   (SP, spSaveAreaOffset, FP); // restore SP
  }
  //-#endif
}
