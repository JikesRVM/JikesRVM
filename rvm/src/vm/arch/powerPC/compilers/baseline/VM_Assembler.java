/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
 * @modified Kris Venstermans
 * @modified Daniel Frampton
 */
public final class VM_Assembler implements VM_BaselineConstants,
                                    VM_AssemblerConstants {

  private VM_MachineCode mc;
  private int mIP; // current machine code instruction
  private boolean shouldPrint;
  private VM_Compiler compiler; // VM_Baseline compiler instance for this assembler.  May be null.

  public VM_Assembler (int length) {
    this(length, false);
  }

  public VM_Assembler (int length, boolean sp, VM_Compiler comp) {
    this(length, sp);
    compiler = comp;
  }

  public VM_Assembler (int length, boolean sp) {
    mc = new VM_MachineCode();
    mIP = 0;
    shouldPrint = sp;
  }

  final static boolean fits (long val, int bits) {
    val = val >> bits-1;
    return (val == 0L || val == -1L);
  }

  final static boolean fits (int val, int bits) {
    val = val >> bits-1;
    return (val == 0 || val == -1);
  }

  void noteBytecode (int i, String bcode) throws NoInlinePragma {
    String s1 = VM_Services.getHexString(mIP << LG_INSTRUCTION_WIDTH, true);
    VM.sysWrite(s1 + ": [" + i + "] " + bcode + "\n");
  }

  void noteBytecode (int i, String bcode, int x) throws NoInlinePragma {
    noteBytecode(i, bcode+" "+x);
  }

  void noteBytecode (int i, String bcode, long x) throws NoInlinePragma {
    noteBytecode(i, bcode+" "+x);
  }

  void noteBytecode (int i, String bcode, Object o) throws NoInlinePragma {
    noteBytecode(i, bcode+" "+o);
  }

  void noteBytecode (int i, String bcode, int x, int y) throws NoInlinePragma {
    noteBytecode(i, bcode+" "+x+" "+y);
  }

  void noteBranchBytecode (int i, String bcode, int off,
                           int bt) throws NoInlinePragma {
    noteBytecode(i, bcode +" "+off+" ["+bt+"] ");
  }

  void noteTableswitchBytecode (int i, int l, int h, int d) throws NoInlinePragma {
    noteBytecode(i, "tableswitch [" + l + "--" + h + "] " + d);
  }

  void noteLookupswitchBytecode (int i, int n, int d) throws NoInlinePragma {
    noteBytecode(i, "lookupswitch [<" + n + ">]" + d);
  }
  

  /* Handling backward branch references */

  public int getMachineCodeIndex () {
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
    int delta = mIP - sourceMachinecodeIndex;
    int instr = mc.getInstruction(sourceMachinecodeIndex);
    if (VM.VerifyAssertions) VM._assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
    instr |= (delta<<2);
    mc.putInstruction(sourceMachinecodeIndex, instr);
  }
  
  final void patchConditionalBranch(int sourceMachinecodeIndex) {
    int delta = mIP - sourceMachinecodeIndex;
    int instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      if (VM.VerifyAssertions) VM._assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
      instr ^= 0x01000008; // make skip instruction with opposite sense
      mc.putInstruction(sourceMachinecodeIndex-1, instr); // skip unconditional branch to target
      mc.putInstruction(sourceMachinecodeIndex,  Btemplate | (delta&0xFFFFFF)<<2);
    }
  }

  final void patchShortBranch(int sourceMachinecodeIndex) {
    int delta = mIP - sourceMachinecodeIndex;
    int instr = mc.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      mc.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      throw new InternalError("Long offset doesn't fit in short branch\n");
    }
  }

  //-#if RVM_WITH_OSR
  private int toBePatchedMCAddr;
  private int targetBCpc = -1;
 
  final void registerLoadAddrConst(int target) {
    toBePatchedMCAddr = mIP;
    targetBCpc = target;
  }
 
  /* the prologue is always before any real bytecode index.
   *
   * CAUTION: the machine code to be patched has following pattern:
   *          BL 4
   *          MFLR T1                   <- address in LR
   *          ADDI  T1, offset, T1       <- toBePatchedMCAddr
   *          STU
   *
   * The third instruction should be patched with accurate relative address.
   * It is computed by (mIP - toBePatchedMCAddr + 1)*4;
   */
  final void patchLoadAddrConst(int bIP){
    if (bIP != targetBCpc) return;
 
    int offset = (mIP - toBePatchedMCAddr + 1)*4;
    int mi = ADDI(T1, offset, T1);
    mc.putInstruction(toBePatchedMCAddr, mi);
    targetBCpc = -1;
  }
 
  final int ADDI(int RT, int D, int RA) {
    return ADDItemplate | RT << 21 | RA << 16 | (D&0xFFFF);
  }

  public final VM_ForwardReference generatePendingJMP(int bTarget) {
    return this.emitForwardB();
  }
  //-#endif RVM_WITH_OSR

  final void patchSwitchCase(int sourceMachinecodeIndex) {
    int delta = (mIP - sourceMachinecodeIndex) << 2;
    // correction is number of bytes of source off switch base
    int         correction = (int)mc.getInstruction(sourceMachinecodeIndex);
    int offset = (int)(delta+correction);
    mc.putInstruction(sourceMachinecodeIndex, offset);
  }


  /* machine instructions */

  static final int ADDtemplate = 31<<26 | 10<<1;

  public final void emitADD (int RT, int RA, int RB) {
    int mi = ADDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDEtemplate = 31<<26 | 138<<1;

  public final void emitADDE (int RT, int RA, int RB) {
    int mi = ADDEtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDICrtemplate = 13<<26;

  public final void emitADDICr (int RT, int RA, int SI) {
    if (VM.VerifyAssertions) VM._assert(fits(SI, 16));
    int mi = ADDICrtemplate | RT<<21 | RA<<16 | (SI & 0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDtemplate = 31<<26 | 28<<1;

  public final void emitAND (int RA, int RS, int RB) {
    int mi = ANDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ANDItemplate = 28<<26;

  public final void emitANDI (int RA, int RS, int U) {
    if (VM.VerifyAssertions) VM._assert((U>>>16) == 0);
    int mi = ANDItemplate | RS<<21 | RA<<16 | U;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int Btemplate = 18<<26;

  private final void _emitB (int relative_address) {
    if (VM.VerifyAssertions) VM._assert(fits(relative_address,24));
    int mi = Btemplate | (relative_address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitB (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitB(relative_address);
  }

  public final void emitB (int relative_address) {
    relative_address -= mIP;
    if (VM.VerifyAssertions) VM._assert(relative_address < 0);
    _emitB(relative_address);
  }

  public final VM_ForwardReference emitForwardB() {
    VM_ForwardReference fr;
    if (compiler != null) {
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitB(0);
    return fr;
  }

  static final int BLAtemplate = 18<<26 | 3;

  public final void emitBLA (int address) {
    if (VM.VerifyAssertions) VM._assert(fits(address,24));
    int mi = BLAtemplate | (address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BLtemplate = 18<<26 | 1;

  private final void _emitBL (int relative_address) {
    if (VM.VerifyAssertions) VM._assert(fits(relative_address,24));
    int mi = BLtemplate | (relative_address&0xFFFFFF)<<2;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitBL (int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBL(relative_address);
  }


  public final VM_ForwardReference emitForwardBL() {
    VM_ForwardReference fr;
    if (compiler != null) { 
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitBL(0);
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
    if (VM.VerifyAssertions) VM._assert(false);
    return -1;
  }

  private final void _emitBC (int cc, int relative_address) {
    if (fits(relative_address, 14)) {
      int mi = BCtemplate | cc | (relative_address&0x3FFF)<<2;
      mIP++;
      mc.addInstruction(mi);
    } else {
      _emitBC(flipCode(cc), 2);
      _emitB(relative_address-1);
    }
  }

  public final void emitBC (int cc, int relative_address, int label) {
    if (relative_address == 0) {
      reserveForwardConditionalBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBC(cc, relative_address);
  }

  public final void emitShortBC (int cc, int relative_address, int label) {
    if (relative_address == 0) {
      reserveShortForwardConditionalBranch(label);
    } else {
      relative_address -= mIP;
    }
    _emitBC(cc, relative_address);
  }

  public final void emitBC (int cc, int relative_address) {
    relative_address -= mIP;
    if (VM.VerifyAssertions) VM._assert(relative_address < 0);
    _emitBC(cc, relative_address);
  }

  public final VM_ForwardReference emitForwardBC(int cc) {
    VM_ForwardReference fr;
    if (compiler != null) {
      fr = new ShortBranch(mIP, compiler.spTopOffset);
    } else {
      fr = new VM_ForwardReference.ShortBranch(mIP);
    }
    _emitBC(cc, 0);
    return fr;
  }

  // delta i: difference between address of case i and of delta 0
  public final void emitSwitchCase(int i, int relative_address, int bTarget) {
    int data = i << 2;
    if (relative_address == 0) {
      reserveForwardCase(bTarget);
    } else {
      data += ((relative_address - mIP) << 2);
    }
    mIP++;
    mc.addInstruction(data);
  }

  static final int BCLRtemplate = 19<<26 | 0x14<<21 | 16<<1;

  public final void emitBCLR () {
    int mi = BCLRtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCLRLtemplate = 19<<26 | 0x14<<21 | 16<<1 | 1;

  public final void emitBCLRL () {
    int mi = BCLRLtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCCTRtemplate = 19<<26 | 0x14<<21 | 528<<1;

  public final void emitBCCTR () {
    int mi = BCCTRtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int BCCTRLtemplate = 19<<26 | 0x14<<21 | 528<<1 | 1;

  public final void emitBCCTRL () {
    int mi = BCCTRLtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDItemplate = 14<<26;

  public final void emitADDI (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = ADDItemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ADDIStemplate = 15<<26;

  public final void emitADDIS (int RT, int RA, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    int mi = ADDIStemplate | RT<<21 | RA<<16 | UI;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitADDIS (int RT, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    int mi = ADDIStemplate | RT<<21 | UI;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPtemplate = 31<<26;
  public final void emitCMP (int BF, int RA, int RB) {
    int mi = CMPtemplate | BF<<23 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitCMP (int RA, int RB) {
    int mi = CMPtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPDtemplate = CMPtemplate | 1 << 21;
  public final void emitCMPD (int RA, int RB) {
    int mi = CMPDtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPItemplate = 11<<26;
  public final void emitCMPI (int BF, int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    int mi = CMPItemplate | BF<<23 | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitCMPI (int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    int mi = CMPItemplate | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPDItemplate = CMPItemplate | 1 << 21;
  public final void emitCMPDI (int RA, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    int mi = CMPDItemplate | RA<<16 | (V&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPLtemplate = 31<<26 | 32<<1;
  public final void emitCMPL (int BF, int RA, int RB) {
    int mi = CMPLtemplate | BF<<23 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitCMPL (int RA, int RB) {
    int mi = CMPLtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CMPLDtemplate = CMPLtemplate | 1<<21;
  public final void emitCMPLD (int RA, int RB) {
    int mi = CMPLDtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDtemplate = 19<<26 | 257<<1;

  public final void emitCRAND (int BT, int BA, int BB) {
    int mi = CRANDtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRANDCtemplate = 19<<26 | 129<<1;

  public final void emitCRANDC (int BT, int BA, int BB) {
    int mi = CRANDCtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORtemplate = 19<<26 | 449<<1;

  public final void emitCROR (int BT, int BA, int BB) {
    int mi = CRORtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int CRORCtemplate = 19<<26 | 417<<1;

  public final void emitCRORC (int BT, int BA, int BB) {
    int mi = CRORCtemplate | BT<<21 | BA<<16 | BB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FADDtemplate = 63<<26 | 21<<1;

  public final void emitFADD (int FRT, int FRA,int FRB) {
    int mi = FADDtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FADDStemplate = 59<<26 | 21<<1; // single-percision add

  public final void emitFADDS (int FRT, int FRA,int FRB) {
    int mi = FADDStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FABStemplate = 63<<26 | 264<<1;

  public final void emitFABS (int FRT, int FRB) {
    int mi = FABStemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCMPUtemplate = 63<<26;

  public final void emitFCMPU (int FRA,int FRB) {
    int mi = FCMPUtemplate | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDIVtemplate = 63<<26 | 18<<1;

  public final void emitFDIV (int FRT, int FRA, int FRB) {
    int mi = FDIVtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FDIVStemplate = 59<<26 | 18<<1; // single-precision divide

  public final void emitFDIVS (int FRT, int FRA, int FRB) {
    int mi = FDIVStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMULtemplate = 63<<26 | 25<<1;

  public final void emitFMUL (int FRT, int FRA, int FRB) {
    int mi = FMULtemplate | FRT<<21 | FRA<<16 | FRB<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMULStemplate = 59<<26 | 25<<1; // single-precision fm

  public final void emitFMULS (int FRT, int FRA, int FRB) {
    int mi = FMULStemplate | FRT<<21 | FRA<<16 | FRB<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FMADDtemplate = 63<<26 | 29<<1;

  public final void emitFMADD (int FRT, int FRA, int FRC, int FRB) {
    int mi = FMADDtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNMSUBtemplate = 63<<26 | 30<<1;

  public final void emitFNMSUB (int FRT, int FRA, int FRC, int FRB) {
    int mi = FNMSUBtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FNEGtemplate = 63<<26 | 40<<1;

  public final void emitFNEG (int FRT, int FRB) {
    int mi = FNEGtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSUBtemplate = 63<<26 | 20<<1;

  public final void emitFSUB (int FRT, int FRA, int FRB) {
    int mi = FSUBtemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSUBStemplate = 59<<26 | 20<<1;

  public final void emitFSUBS (int FRT, int FRA, int FRB) {
    int mi = FSUBStemplate | FRT<<21 | FRA<<16 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FSELtemplate = 63<<26 | 23<<1;

  public final void emitFSEL (int FRT, int FRA, int FRC, int FRB) {
    int mi = FSELtemplate | FRT<<21 | FRA<<16 | FRB<<11 | FRC<<6;
    mIP++;
    mc.addInstruction(mi);
  }

  // LOAD/ STORE MULTIPLE

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambiguous) 
  //
  public final void emitLMW(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = (46<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  // TODO!! verify that D is sign extended 
  // (the Assembler Language Reference seems ambiguous) 
  //
  public final void emitSTMW(int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = (47<<26)  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }


  static final int LWZtemplate = 32<<26;

  public final void emitLWZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LWZtemplate  | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZtemplate = 34<<26;

  public final void emitLBZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LBZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LBZXtemplate = 31<<26 | 87<<1;

  public final void emitLBZX (int RT, int RA, int RB) {
    int mi = LBZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAtemplate = 42<<26;

  public final void emitLHA (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LHAtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZtemplate = 40<<26;

  public final void emitLHZ (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LHZtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDtemplate = 50<<26;

  public final void emitLFD (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LFDtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDUtemplate = 51<<26;

  public final void emitLFDU (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LFDUtemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFDXtemplate = 31<<26 | 599<<1;

  public final void emitLFDX (int FRT, int RA, int RB) {
    int mi = LFDXtemplate | FRT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFStemplate = 48<<26;

  public final void emitLFS (int FRT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LFStemplate | FRT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LFSXtemplate = 31<<26 | 535<<1;

  static final int LFSX (int FRT, int RA, int RB) {
    return 31<<26 | FRT<<21 | RA<<16 | RB<<11 | 535<<1;
  }

  final void emitLFSX (int FRT, int RA, int RB) {
    int mi = LFSXtemplate | FRT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHAXtemplate = 31<<26 | 343<<1;

  public final void emitLHAX (int RT, int RA, int RB) {
    int mi = LHAXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LHZXtemplate = 31<<26 | 279<<1;

  public final void emitLHZX (int RT, int RA, int RB) {
    int mi = LHZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  private final void emitLI (int RT, int D) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = ADDItemplate | RT<<21 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZUtemplate = 33<<26;

  public final void emitLWZU (int RT, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = LWZUtemplate | RT<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZXtemplate = 31<<26 | 23<<1;

  public final void emitLWZX (int RT, int RA, int RB) {
    int mi = LWZXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWZUXtemplate = 31<<26 | 55<<1;

  public final void emitLWZUX (int RT, int RA, int RB) {
    int mi = LWZUXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  
  static final int LWARXtemplate = 31<<26 | 20<<1;

  public final void emitLWARX (int RT, int RA, int RB) {
    int mi = LWARXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFLRtemplate = 31<<26 | 0x08<<16 | 339<<1;

  public final void emitMFLR (int RT) {
    int mi = MFLRtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFSPRtemplate = 31<<26 | 339<<1;

  final void emitMFSPR (int RT, int SPR) {
    int mi = MFSPRtemplate | RT<<21 | SPR<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTLRtemplate = 31<<26 | 0x08<<16 | 467<<1;

  public final void emitMTLR (int RS) {
    int mi = MTLRtemplate | RS<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MTCTRtemplate = 31<<26 | 0x09<<16 | 467<<1;

  public final void emitMTCTR (int RS) {
    int mi = MTCTRtemplate | RS<<21;
    mIP++;
    mc.addInstruction(mi);
  }
 
  static final int FMRtemplate = 63<<26 | 72<<1;

  final void emitFMR (int RA, int RB) {
    int mi = FMRtemplate | RA<<21 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FRSPtemplate = 63<<26 | 12<<1;

  static final int FRSP (int RT, int RB) {
    return 63<<26 | RT<<21 |  RB<<11 | 12<<1;
  }

  final void emitFRSP (int RT, int RB) {
    int mi = FRSPtemplate | RT<<21 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULHWUtemplate = 31<<26 | 11<<1;

  public final void emitMULHWU (int RT, int RA, int RB) {
    int mi = MULHWUtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DIVWtemplate = 31<<26 | 491<<1;

  public final void emitDIVW (int RT, int RA, int RB) {
    int mi = DIVWtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULLWtemplate = 31<<26 | 235<<1;

  public final void emitMULLW (int RT, int RA, int RB) {
    int mi = MULLWtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int NEGtemplate = 31<<26 | 104<<1;

  public final void emitNEG (int RT, int RA) {
    int mi = NEGtemplate | RT<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORtemplate = 31<<26 | 444<<1;

  public final void emitOR (int RA, int RS, int RB) {
    int mi = ORtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  // move register RT <- RS
  public final void emitMR(int RT, int RS) {
    emitOR(RT, RS, RS);
  }

  static final int ORItemplate = 24<<26;

  public final void emitORI (int RA, int RS, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    int mi = ORItemplate | RS<<21 | RA<<16 | (UI&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ORIStemplate = 25<<26;

  public final void emitORIS (int RA, int RS, int UI) {
    if (VM.VerifyAssertions) VM._assert(UI == (UI&0xFFFF));
    int mi = ORIStemplate | RS<<21 | RA<<16 | (UI&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int RLWINM_template = 21<<26;

  public final void emitRLWINM (int RA, int RS, int SH, int MB, int ME) {
    int mi = RLWINM_template | RS<<21 | RA<<16 | SH<<11 | MB<<6 | ME<<1;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFCrtemplate = 31<<26 | 8<<1 | 1;

  public final void emitSUBFCr (int RT, int RA, int RB) {
    int mi = SUBFCrtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFCtemplate = 31<<26 | 8<<1;

  public final void emitSUBFC (int RT, int RA, int RB) {
    int mi = SUBFCtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFICtemplate = 8<<26;

  public final void emitSUBFIC (int RA, int RS, int S) {
    if (VM.VerifyAssertions) VM._assert(fits(S,16));
    int mi = SUBFICtemplate | RS<<21 | RA<<16 | S;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFErtemplate = 31<<26 | 136<<1 | 1;

  public final void emitSUBFEr (int RT, int RA, int RB) {
    int mi = SUBFErtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFEtemplate = 31<<26 | 136<<1;

  public final void emitSUBFE (int RT, int RA, int RB) {
    int mi = SUBFEtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SUBFZEtemplate = 31<<26 | 200<<1;

  public final void emitSUBFZE (int RT, int RA) {
    int mi = SUBFZEtemplate | RT<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLWtemplate = 31<<26 | 24<<1;

  public final void emitSLW (int RA, int RS, int RB) {
    int mi = SLWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLWItemplate = 21<<26;

  public final void emitSLWI (int RA, int RS, int N) {
    int mi = SLWItemplate | RS<<21 | RA<<16 | N<<11 | (31-N)<<1;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRWtemplate = 31<<26 | 536<<1;

  public final void emitSRW (int RA, int RS, int RB) {
    int mi = SRWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWtemplate = 31<<26 | 792<<1;

  public final void emitSRAW (int RA, int RS, int RB) {
    int mi = SRAWtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWItemplate = 31<<26 | 824<<1;

  public final void emitSRAWI (int RA, int RS, int SH) {
    int mi = SRAWItemplate | RS<<21 | RA<<16 | SH<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRAWIrtemplate = 31<<26 | 824<<1 | 1;

  public final void emitSRAWIr (int RA, int RS, int SH) {
    int mi = SRAWIrtemplate | RS<<21 | RA<<16 | SH<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWtemplate = 36<<26;

  public final void emitSTW (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STWtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBtemplate = 38<<26;

  public final void emitSTB (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STBtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STBXtemplate = 31<<26 | 215<<1;

  public final void emitSTBX (int RS, int RA, int RB) {
    int mi = STBXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STHXtemplate = 31<<26 | 407<<1;

  public final void emitSTHX (int RS, int RA, int RB) {
    int mi = STHXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWXtemplate = 31<<26 | 151<<1;

  public final void emitSTWX (int RS, int RA, int RB) {
    int mi = STWXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }
  static final int STFSXtemplate = 31<<26 | 663<<1;

  static final int STFSX(int FRS, int RA, int RB) {
    return 31<<26 | FRS<<21 | RA<<16 | RB<<11 | 663<<1;
  }

  final void emitSTFSX (int FRS, int RA, int RB) {
    int mi = STFSXtemplate | FRS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }


  static final int STFDtemplate = 54<<26;

  public final void emitSTFD (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STFDtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDUtemplate = 55<<26;

  public final void emitSTFDU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STFDUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFDXtemplate = 31<<26 | 727<<1;

  public final void emitSTFDX (int FRS, int RA, int RB) {
    int mi = STFDXtemplate | FRS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFStemplate = 52<<26;

  public final void emitSTFS (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STFStemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STFSUtemplate = 53<<26;

  public final void emitSTFSU (int FRS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STFSUtemplate | FRS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWUtemplate = 37<<26;

  public final void emitSTWU (int RS, int D, int RA) {
    if (VM.VerifyAssertions) VM._assert(fits(D, 16));
    int mi = STWUtemplate | RS<<21 | RA<<16 | (D&0xFFFF);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWUXtemplate = 31<<26 | 183<<1;

  public final void emitSTWUX (int RS, int RA, int RB) {
    int mi = STWUXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STWCXrtemplate = 31<<26 | 150<<1 | 1;

  public final void emitSTWCXr (int RS, int RA, int RB) {
    int mi = STWCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWtemplate = 31<<26 | 4<<1;
  static final int TWLEtemplate = TWtemplate | 0x14<<21 ;

  public final void emitTWLE (int RA, int RB) {
    int mi = TWLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWLTtemplate = TWtemplate | 0x10<<21;

  public final void emitTWLT (int RA, int RB) {
    int mi = TWLTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWNEtemplate = TWtemplate | 0x18<<21;

  public final void emitTWNE (int RA, int RB) {
    int mi = TWNEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWLLEtemplate = TWtemplate | 0x6<<21;

  public final void emitTWLLE (int RA, int RB) {
    int mi = TWLLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TWItemplate = 3<<26;

  public final void emitTWI (int TO, int RA, int SI) {
    int mi = TWItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int TWEQItemplate = TWItemplate | 0x4<<21;

  public final void emitTWEQ0 (int RA) {
    int mi = TWEQItemplate | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int TWWItemplate = TWItemplate | 0x3EC<<16;      // RA == 12

  public final void emitTWWI (int imm) {
    int mi = TWWItemplate | imm;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORtemplate = 31<<26 | 316<<1;

  public final void emitXOR (int RA, int RS, int RB) {
    int mi = XORtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int XORItemplate = 26<<26;

  public final void emitXORI (int RA, int RS, int V) {
    if (VM.VerifyAssertions) VM._assert(fits(V, 16));
    int mi = XORItemplate |  RS<<21 | RA<<16  | V&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }

  /* macro instructions */

  public final void emitNOP () {
    int mi = 24<<26; //ORI 0,0,0
    mIP++;
    mc.addInstruction(mi);
  }

  //private: use emitLIntOffset or emitLAddrOffset instead
  private final void emitLDoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLD(RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLD (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLD (RT, offset|0xFFFF0000, RT);
    }
  }
  
  //private: use emitLIntOffset or emitLAddrOffset instead
  private final void emitLWAoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLWA (RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLWA (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLWA (RT, offset|0xFFFF0000, RT);
    }
  }
    
  //private: use emitLIntOffset or emitLAddrOffset instead
  private final void emitLWZoffset(int RT, int RA, int offset) {
    if (fits(offset, 16)) {
      emitLWZ (RT, offset, RA);
    } else if ((offset & 0x8000) == 0) {
      emitADDIS(RT, RA, offset>>16);
      emitLWZ (RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, RA, (offset>>16)+1);
      emitLWZ (RT, offset|0xFFFF0000, RT);
    }
  }
    
  //prefer to use emitLIntToc or emitLAddrToc instead
  public final void emitLDtoc (int RT, int offset) {
    emitLDoffset(RT, JTOC, offset); 
  }

  //prefer to use emitLIntToc or emitLAddrToc instead
  public final void emitLWAtoc (int RT, int offset) {
    emitLWAoffset(RT, JTOC, offset);
  }

  //prefer to use emitLIntToc or emitLAddrToc instead
  public final void emitLWZtoc (int RT, int offset) {
    emitLWZoffset(RT, JTOC, offset);
  }

  public final void emitSTDtoc (int RT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTD(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(Rz, JTOC, offset>>16);
      emitSTD(RT, offset&0xFFFF, Rz);
    } else {
      emitADDIS(Rz, JTOC, (offset>>16)+1);
      emitSTD(RT, offset|0xFFFF0000, Rz);
    }
  } 

  public final void emitSTWtoc (int RT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTW(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(Rz, JTOC, offset>>16);
      emitSTW(RT, offset&0xFFFF, Rz);
    } else {
      emitADDIS(Rz, JTOC, (offset>>16)+1);
      emitSTW(RT, offset|0xFFFF0000, Rz);
    }
  }

  public final void emitADDItoc (int RT, int offset) {
    if (fits(offset, 16)) {
      emitADDI(RT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS(RT, JTOC, offset>>16);
      emitADDI(RT, offset&0xFFFF, RT);
    } else {
      emitADDIS(RT, JTOC, (offset>>16)+1);
      emitADDI(RT, offset|0xFFFF0000, RT);
    }
  }

  public final void emitLFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS( Rz, JTOC, offset>>16);
      emitLFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS( Rz, JTOC, (offset>>16)+1);
      emitLFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  public final void emitSTFDtoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFD(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS ( Rz, JTOC, offset>>16);
      emitSTFD(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS ( Rz, JTOC, (offset>>16)+1);
      emitSTFD(FRT, offset|0xFFFF0000, Rz);
    }
  }

  public final void emitLFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitLFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS( Rz, JTOC, offset>>16);
      emitLFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS( Rz, JTOC, (offset>>16)+1);
      emitLFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  public final void emitSTFStoc (int FRT, int offset, int Rz) {
    if (fits(offset, 16)) {
      emitSTFS(FRT, offset, JTOC);
    } else if (0 == (offset&0x8000)) {
      emitADDIS ( Rz, JTOC, offset>>16);
      emitSTFS(FRT, offset&0xFFFF, Rz);
    } else {
      emitADDIS ( Rz, JTOC, (offset>>16)+1);
      emitSTFS(FRT, offset|0xFFFF0000, Rz);
    }
  }

  public final void emitLVALAddr (int RT, Address addr) {
//-#if RVM_FOR_64_ADDR
    long val = addr.toLong();
    if (!fits(val,48)){
      emitADDIS(RT, (int)(val>>>48));
      emitORI(RT, RT, (int)((val>>>32)&0xFFFF));
      emitSLDI(RT,RT,32);
      emitORIS(RT, RT, (int)((val>>>16)&0xFFFF));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else if (!fits(val,32)){
      emitLI(RT, (int)(val>>>32));
      emitSLDI(RT,RT,32);
      emitORIS(RT, RT, (int)((val>>>16)&0xFFFF));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else 
//-#else
    int val = addr.toInt();
//-#endif         
    if (!fits(val,16)){
      emitADDIS(RT, (int)(val>>>16));
      emitORI(RT, RT, (int)(val&0xFFFF));
    } else {
      emitLI(RT, (int)val);
    }
  }

  public final void emitLVAL (int RT, int val) {
    if (fits(val, 16)) { 
      emitLI(RT, val);
    } else { 
      emitADDIS(RT, val>>>16);
      emitORI(RT, RT, val&0xFFFF);
    }
  }

  // Convert generated machine code into final form.
  //
  VM_MachineCode finalizeMachineCode (int[] bytecodeMap) {
    mc.setBytecodeMap(bytecodeMap);
    return makeMachineCode();
  }

  public VM_MachineCode makeMachineCode () {
    mc.finish();
    if (shouldPrint) {
      VM.sysWriteln();
      VM_CodeArray instructions = mc.getInstructions();
      boolean saved = VM_BaselineCompiler.options.PRINT_MACHINECODE;
      try {
        VM_BaselineCompiler.options.PRINT_MACHINECODE = false;
        for (int i = 0; i < instructions.length(); i++) {
          VM.sysWrite(VM_Services.getHexString(i << LG_INSTRUCTION_WIDTH, true));
          VM.sysWrite(" : ");
          VM.sysWrite(VM_Services.getHexString(instructions.get(i), false));
          VM.sysWrite("  ");
          VM.sysWrite(PPC_Disassembler.disasm(instructions.get(i), i << LG_INSTRUCTION_WIDTH));
          VM.sysWrite("\n");
        }
      } finally {
        VM_BaselineCompiler.options.PRINT_MACHINECODE = saved;
      }
    }
    return mc;
  }

  /**
   * Append a VM_CodeArray to the current machine code
   */
  public void appendInstructions (VM_CodeArray instructionSegment) {
    for (int i=0; i<instructionSegment.length(); i++) {
      mIP++;
      mc.addInstruction(instructionSegment.get(i));
    }
  }

  // new PowerPC instuctions
    
  // The "sync" on Power 4 architectures are expensive and so we use "lwsync" instead to
  //   implement SYNC.  On older arhictectures, there is no problem but the weaker semantics
  //   of lwsync means that there are memory consistency bugs we might need to flush out.
  // static final int SYNCtemplate = 31<<26 | 598<<1;
  // static final int LWSYNCtemplate = 31<<26 | 1 << 21 | 598<<1;
  static final int SYNCtemplate = 31<<26 | 1 << 21 | 598<<1;

  public final void emitSYNC () {
    int mi = SYNCtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ICBItemplate = 31<<26 | 982<<1;
  
  public final void emitICBI (int RA, int RB) {
    int mi = ICBItemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int ISYNCtemplate = 19<<26 | 150<<1;
  
  public final void emitISYNC () {
    int mi = ISYNCtemplate;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBFtemplate = 31<<26 | 86<<1;
  
  public final void emitDCBF (int RA, int RB) {
    int mi = DCBFtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int DCBSTtemplate = 31<<26 | 54<<1;
  
  public final void emitDCBST (int RA, int RB) {
    int mi = DCBSTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBtemplate = 31<<26 | 392<<11 | 371<<1;
  
  public final void emitMFTB (int RT) {
    int mi = MFTBtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MFTBUtemplate = 31<<26 | 424<<11 | 371<<1;
  
  public final void emitMFTBU (int RT) {
    int mi = MFTBUtemplate | RT<<21;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIWZtemplate = 63<<26 | 15<<1;
  
  public final void emitFCTIWZ (int FRT, int FRB) {
    int mi = FCTIWZtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int EXTSBtemplate = 31<<26 | 954<<1;

  public final void emitEXTSB (int RA, int RS) {
    int mi = EXTSBtemplate | RS<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }


  // PowerPC 64-bit instuctions
  static final int DIVDtemplate = 31<<26 | 489<<1;

  public final void emitDIVD (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = DIVDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int EXTSWtemplate = 31<<26 | 986<<1;

  public final void emitEXTSW (int RA, int RS) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = EXTSWtemplate | RS<<21 | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCTIDZtemplate = 63<<26 | 815<<1;
  
  public final void emitFCTIDZ (int FRT, int FRB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = FCTIDZtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int FCFIDtemplate = 63<<26 | 846<<1;

  public final void emitFCFID (int FRT, int FRB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = FCFIDtemplate | FRT<<21 | FRB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDtemplate = 58<<26;

  public final void emitLD (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    int mi = LDtemplate  | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int LDARXtemplate = 31<<26 | 84<<1;

  public final void emitLDARX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = LDARXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDUtemplate = 58<<26 | 1;

  public final void emitLDU (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    int mi = LDUtemplate | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDUXtemplate = 31<<26 | 53<<1;

  public final void emitLDUX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = LDUXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LDXtemplate = 31<<26 | 21<<1;

  public final void emitLDX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = LDXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWAtemplate = 58<<26 | 2;

  public final void emitLWA (int RT, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    int mi = LWAtemplate | RT<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int LWAXtemplate = 31<<26 | 341<<1;

  public final void emitLWAX (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = LWAXtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULHDUtemplate = 31<<26 | 9<<1;

  public final void emitMULHDU (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = MULHDUtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int MULLDtemplate = 31<<26 | 233<<1;

  public final void emitMULLD (int RT, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = MULLDtemplate | RT<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SLDItemplate = 30<<26 | 1<<2;

  public final void emitSLDI (int RA, int RS, int N) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SLDItemplate | RS<<21 | RA<<16 | (N&0x1F)<<11 | ((63-N)&0x1F)<<6 | ((63-N)&0x20) | (N&0x20)>>4 ;
    mIP++;
    mc.addInstruction(mi);
  }

  public final void emitRLDINM (int RA, int RS, int SH, int MB, int ME) {
    VM._assert(false, "PLEASE IMPLEMENT ME");
  }

  static final int SLDtemplate = 31<<26 | 27<<1;

  public final void emitSLD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SLDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADtemplate = 31<<26 | 794<<1;

  public final void emitSRAD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SRADtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADItemplate = 31<<26 | 413<<2;

  public final void emitSRADI (int RA, int RS, int SH) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SRADItemplate | RS<<21 | RA<<16 | (SH&0x1F)<<11 | (SH&0x20)>>4;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRADIrtemplate = SRADItemplate | 1;

  public final void emitSRADIr (int RA, int RS, int SH) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SRADIrtemplate | RS<<21 | RA<<16 | (SH&0x1F)<<11 | (SH&0x20)>>4;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int SRDtemplate = 31<<26 | 539<<1;

  public final void emitSRD (int RA, int RS, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = SRDtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDtemplate = 62<<26;

  public final void emitSTD (int RS, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    int mi = STDtemplate | RS<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDCXrtemplate = 31<<26 | 214<<1 | 1;

  public final void emitSTDCXr (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = STDCXrtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDUtemplate = 62<<26 | 1;

  public final void emitSTDU (int RS, int DS, int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    if (VM.VerifyAssertions) VM._assert(fits(DS, 16));
    int mi = STDUtemplate | RS<<21 | RA<<16 | (DS&0xFFFC);
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDUXtemplate = 31<<26 | 181<<1;

  public final void emitSTDUX (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = STDUXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int STDXtemplate = 31<<26 | 149<<1;

  public final void emitSTDX (int RS, int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = STDXtemplate | RS<<21 | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDtemplate = 31<<26 | 68<<1;
  static final int TDLEtemplate = TDtemplate | 0x14<<21;

  public final void emitTDLE (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDLTtemplate = TDtemplate | 0x10<<21;

  public final void emitTDLT (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDLTtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDLLEtemplate = TDtemplate | 0x6<<21 ;

  public final void emitTDLLE (int RA, int RB) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDLLEtemplate | RA<<16 | RB<<11;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDItemplate = 2<<26;

  public final void emitTDI (int TO, int RA, int SI) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDItemplate | TO<<21 | RA<<16 | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }
  
  static final int TDEQItemplate = TDItemplate | 0x4<<21;

  public final void emitTDEQ0 (int RA) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDEQItemplate | RA<<16;
    mIP++;
    mc.addInstruction(mi);
  }

  static final int TDWItemplate = TDItemplate | 0x1F<<21 | 0xC<<16;     

  public final void emitTDWI (int SI) {
    if (!VM.BuildFor64Addr && VM.VerifyAssertions) VM._assert(false);
    int mi = TDWItemplate | SI&0xFFFF;
    mIP++;
    mc.addInstruction(mi);
  }

  // -------------------------------------------------------------- //
  // The following section contains macros to handle address values //
  // -------------------------------------------------------------- //
  public final void emitCMPLAddr(int reg1, int reg2){                    
    if (VM.BuildFor64Addr)
      emitCMPLD(reg1, reg2);
    else
      emitCMPL(reg1, reg2);
  }

  public final void emitCMPAddr(int reg1, int reg2){                    
    if (VM.BuildFor64Addr)
      emitCMPD(reg1, reg2);
    else
      emitCMP(reg1, reg2);
  }

  public final void emitCMPAddrI (int RA, int V) {
    if (VM.BuildFor64Addr)
      emitCMPDI(RA, V);
    else
      emitCMPI(RA, V);
  }

  public final void emitSRAddr(int RA, int RS, int RB){                    
    if (VM.BuildFor64Addr)
      emitSRD(RA, RS, RB);
    else
      emitSRW(RA, RS, RB);
  }

  public final void emitSRA_Addr(int RA, int RS, int RB){                    
    if (VM.BuildFor64Addr)
      emitSRAD(RA, RS, RB);
    else
      emitSRAW(RA, RS, RB);
  }

  public final void emitSRA_AddrI(int RA, int RS, int SH){                    
    if (VM.BuildFor64Addr)
      emitSRADI(RA, RS, SH);
    else
      emitSRAWI(RA, RS, SH);
  }

  public final void emitSLAddr (int RA, int RS, int RB) {
    if (VM.BuildFor64Addr)
      emitSLD(RA, RS, RB);
    else
      emitSLW(RA, RS, RB);
  }

  public final void emitSLAddrI(int RA, int RS, int N){                    
    if (VM.BuildFor64Addr)
      emitSLDI(RA, RS, N);
    else
      emitSLWI(RA, RS, N);
  }

  public final void emitTAddrLT (int RA, int RB) {
    if (VM.BuildFor64Addr)
      emitTDLT(RA, RB);
    else
      emitTWLT(RA, RB);
  }

  public final void emitTAddrLE (int RA, int RB) {
    if (VM.BuildFor64Addr)
      emitTDLE(RA, RB);
    else
      emitTWLE(RA, RB);
  }

  public final void emitTAddrLLE (int RA, int RB) {
    if (VM.BuildFor64Addr)
      emitTDLLE(RA, RB);
    else
      emitTWLLE(RA, RB);
  }

  public final void emitTAddrI (int TO, int RA, int SI) {
    if (VM.BuildFor64Addr)
      emitTDI(T0, RA, SI);
    else
      emitTWI(T0, RA, SI);
  }

  public final void emitTAddrEQ0 (int RA) {
    if (VM.BuildFor64Addr)
      emitTDEQ0(RA);
    else
      emitTWEQ0(RA);
  }

  public final void emitTAddrWI (int SI) {
    if (VM.BuildFor64Addr)
      emitTDWI(SI);
    else
      emitTWWI(SI);
  }

  public final void emitSTAddr(int src_reg, int offset, int dest_reg){                    
    if (VM.BuildFor64Addr)
      emitSTD(src_reg, offset, dest_reg);                   
    else
      emitSTW(src_reg, offset, dest_reg);                                    
  }

  public final void emitSTAddrX(int src_reg, int offset_reg, int dest_reg){           
    if (VM.BuildFor64Addr) 
      emitSTDX(src_reg, offset_reg, dest_reg);           
    else 
      emitSTWX(src_reg, offset_reg, dest_reg);                            
  }

  public final void emitSTAddrU(int src_reg, int offset, int dest_reg){            
    if (VM.BuildFor64Addr) 
      emitSTDU(src_reg, offset, dest_reg);           
    else 
      emitSTWU(src_reg, offset, dest_reg);                            
  }

  public final void emitLAddr(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLD(dest_reg, offset, src_reg);         
    else 
      emitLWZ(dest_reg, offset, src_reg);                         
  }

  public final void emitLAddrX(int dest_reg, int offset_reg, int src_reg){ 
    if (VM.BuildFor64Addr) 
      emitLDX(dest_reg, offset_reg, src_reg);
    else 
      emitLWZX(dest_reg, offset_reg, src_reg);
  }

  final void emitLAddrU(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLDU(dest_reg, offset, src_reg);         
    else 
      emitLWZU(dest_reg, offset, src_reg);                         
  }

  public final void emitLAddrToc(int dest_reg, int TOCoffset){            
    if (VM.BuildFor64Addr) 
      emitLDtoc(dest_reg, TOCoffset);
    else 
      emitLWZtoc(dest_reg, TOCoffset);
  }
  
  final void emitRLAddrINM (int RA, int RS, int SH, int MB, int ME) {
    if (VM.BuildFor64Addr) 
      emitRLDINM(RA, RS, SH, MB, ME);
    else 
      emitRLWINM(RA, RS, SH, MB, ME);
  }

  public final void emitLInt(int dest_reg, int offset, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLWA(dest_reg, offset, src_reg);         
    else 
      emitLWZ(dest_reg, offset, src_reg);                         
  }

  public final void emitLIntX(int dest_reg, int offset_reg, int src_reg){         
    if (VM.BuildFor64Addr) 
      emitLWAX(dest_reg, offset_reg, src_reg);         
    else 
      emitLWZX(dest_reg, offset_reg, src_reg);                         
  }

  public final void emitLIntToc(int dest_reg, int TOCoffset){            
    if (VM.BuildFor64Addr) 
      emitLWAtoc(dest_reg, TOCoffset);
    else 
      emitLWZtoc(dest_reg, TOCoffset);
  }
  
  public final void emitLIntOffset(int RT, int RA, int offset) {
    if (VM.BuildFor64Addr) 
      emitLWAoffset(RT, RA, offset);
    else 
      emitLWZoffset(RT, RA, offset);
  }

  public final void emitLAddrOffset(int RT, int RA, int offset) {
      if (VM.BuildFor64Addr) 
        emitLDoffset(RT, RA, offset);
      else 
        emitLWZoffset(RT, RA, offset);
  }

  // -----------------------------------------------------------//
  // The following section contains assembler "macros" used by: //
  //    VM_Compiler                                             //
  //    VM_Barriers                                             //
  // -----------------------------------------------------------//
  
  // Emit baseline stack overflow instruction sequence.
  // Before:   FP is current (calling) frame
  //           PR is the current VM_Processor, which contains a pointer to the active thread.
  // After:    R0, S0 destroyed
  //
  void emitStackOverflowCheck (int frameSize) {
    emitLAddr ( 0,  VM_Entrypoints.activeThreadStackLimitField.getOffset(), PROCESSOR_REGISTER);   // R0 := &stack guard page
    emitADDI(S0, -frameSize, FP);                        // S0 := &new frame
    emitTAddrLT (S0,  0);                                    // trap if new frame below guard page
  }

  /**
   * Emit the trap pattern (trap LLT 1) we use for nullchecks on reg; 
   * @param reg the register number containing the ptr to null check
   */
  void emitNullCheck (int RA) {
    // TDLLT 1 or TWLLT 1
    int mi = (VM.BuildFor64Addr ? TDItemplate : TWItemplate ) | 0x2<<21 | RA<<16 | 1;
    mIP++;
    mc.addInstruction(mi);
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
  public void emitNativeStackOverflowCheck (int frameSize) {
    emitLAddr   (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitLAddr   (S0, VM_Entrypoints.jniEnvField.getOffset(), S0);      // S0 := thread.jniEnv
    emitLInt   ( 0, VM_Entrypoints.JNIRefsTopField.getOffset(),S0);   // R0 := thread.jniEnv.JNIRefsTop
    emitLAddr   (S0, VM_Entrypoints.activeThreadField.getOffset(), PROCESSOR_REGISTER);   // S0 := thread pointer
    emitCMPI ( 0, 0);                                    // check if S0 == 0 -> first native frame on stack
    VM_ForwardReference fr1 = emitForwardBC(EQ);
    // check for enough space for requested frame size
    emitLAddr  ( 0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
    emitADDI(S0, -frameSize, FP);                        // S0 := &new frame pointer
    emitTAddrLT (S0,  0);                                    // trap if new frame below guard page
    VM_ForwardReference fr2 = emitForwardB();

    // check for enough space for STACK_SIZE_JNINATIVE 
    fr1.resolve(this);
    emitLAddr (0,  VM_Entrypoints.stackLimitField.getOffset(), S0);  // R0 := &stack guard page
    emitLVAL  (S0, STACK_SIZE_JNINATIVE);
    emitSUBFC (S0, S0, FP);             // S0 := &new frame pointer

    emitCMPLAddr(0, S0);
    VM_ForwardReference fr3 = emitForwardBC(LE);
    emitTAddrWI ( 1 );                                    // trap if new frame pointer below guard page
    fr2.resolve(this);
    fr3.resolve(this);
  }

  private static class ShortBranch extends VM_ForwardReference.ShortBranch {
    final int spTopOffset;
    
    ShortBranch (int source, int sp) {
      super(source);
      spTopOffset = sp;
    }
    public void resolve (VM_Assembler asm) {
      super.resolve(asm);
      if (asm.compiler != null) {
        asm.compiler.spTopOffset = spTopOffset;
      }
    }
  }
  public static String getOpcodeName(int opcode) {
    String s;
    switch (opcode) {
    case 0x00: s = "nop";
      break;
    case 0x01: s = "aconst_null";
      break;
    case 0x02: s = "iconst_m1"; 
      break;
    case 0x03: s = "iconst_0"; 
      break;
    case 0x04: s = "iconst_1"; 
      break;
    case 0x05: s = "iconst_2"; 
      break;
    case 0x06: s = "iconst_3"; 
      break;
    case 0x07: s = "iconst_4"; 
      break;
    case 0x08: s = "iconst_5"; 
      break;
    case 0x09: s = "lconst_0"; 
      break;
    case 0x0a: s = "lconst_1"; 
      break;
    case 0x0b: s = "fconst_0"; 
      break;
    case 0x0c: s = "fconst_1"; 
      break;
    case 0x0d: s = "fconst_2"; 
      break;
    case 0x0e: s = "dconst_0"; 
      break;
    case 0x0f: s = "dconst_1"; 
      break;
    case 0x10: s = "bipush"; 
      break;
    case 0x11: s = "sipush"; 
      break;
    case 0x12: s = "ldc"; 
      break;
    case 0x13: s = "ldc_w"; 
      break;
    case 0x14: s = "ldc2_w"; 
      break;
    case 0x15: s = "iload"; 
      break;
    case 0x16: s = "lload"; 
      break;
    case 0x17: s = "fload"; 
      break;
    case 0x18: s = "dload"; 
      break;
    case 0x19: s = "aload"; 
      break;
    case 0x1a: s = "iload_0"; 
      break;
    case 0x1b: s = "iload_1"; 
      break;
    case 0x1c: s = "iload_2"; 
      break;
    case 0x1d: s = "iload_3"; 
      break;
    case 0x1e: s = "lload_0"; 
      break;
    case 0x1f: s = "lload_1"; 
      break;
    case 0x20: s = "lload_2"; 
      break;
    case 0x21: s = "lload_3"; 
      break;
    case 0x22: s = "fload_0"; 
      break;
    case 0x23: s = "fload_1"; 
      break;
    case 0x24: s = "fload_2"; 
      break;
    case 0x25: s = "fload_3"; 
      break;
    case 0x26: s = "dload_0"; 
      break;
    case 0x27: s = "dload_1"; 
      break;
    case 0x28: s = "dload_2"; 
      break;
    case 0x29: s = "dload_3"; 
      break;
    case 0x2a: s = "aload_0"; 
      break;
    case 0x2b: s = "aload_1"; 
      break;
    case 0x2c: s = "aload_2"; 
      break;
    case 0x2d: s = "aload_3"; 
      break;
    case 0x2e: s = "iaload"; 
      break;
    case 0x2f: s = "laload"; 
      break;
    case 0x30: s = "faload"; 
      break;
    case 0x31: s = "daload"; 
      break;
    case 0x32: s = "aaload"; 
      break;
    case 0x33: s = "baload"; 
      break;
    case 0x34: s = "caload"; 
      break;
    case 0x35: s = "saload"; 
      break;
    case 0x36: s = "istore"; 
      break;
    case 0x37: s = "lstore"; 
      break;
    case 0x38: s = "fstore"; 
      break;
    case 0x39: s = "dstore"; 
      break;
    case 0x3a: s = "astore"; 
      break;
    case 0x3b: s = "istore_0"; 
      break;
    case 0x3c: s = "istore_1"; 
      break;
    case 0x3d: s = "istore_2"; 
      break;
    case 0x3e: s = "istore_3"; 
      break;
    case 0x3f: s = "lstore_0"; 
      break;
    case 0x40: s = "lstore_1"; 
      break;
    case 0x41: s = "lstore_2"; 
      break;
    case 0x42: s = "lstore_3"; 
      break;
    case 0x43: s = "fstore_0"; 
      break;
    case 0x44: s = "fstore_1"; 
      break;
    case 0x45: s = "fstore_2"; 
      break;
    case 0x46: s = "fstore_3"; 
      break;
    case 0x47: s = "dstore_0"; 
      break;
    case 0x48: s = "dstore_1"; 
      break;
    case 0x49: s = "dstore_2"; 
      break;
    case 0x4a: s = "dstore_3"; 
      break;
    case 0x4b: s = "astore_0"; 
      break;
    case 0x4c: s = "astore_1"; 
      break;
    case 0x4d: s = "astore_2"; 
      break;
    case 0x4e: s = "astore_3"; 
      break;
    case 0x4f: s = "iastore"; 
      break;
    case 0x50: s = "lastore";  
      break;
    case 0x51: s = "fastore"; 
      break;
    case 0x52: s = "dastore"; 
      break;
    case 0x53: s = "aastore"; 
      break;
    case 0x54: s = "bastore"; 
      break;
    case 0x55: s = "castore"; 
      break;
    case 0x56: s = "sastore"; 
      break;
    case 0x57: s = "pop"; 
      break;
    case 0x58: s = "pop2"; 
      break;
    case 0x59: s = "dup"; 
      break;
    case 0x5a: s = "dup_x1"; 
      break;
    case 0x5b: s = "dup_x2"; 
      break;
    case 0x5c: s = "dup2"; 
      break;
    case 0x5d: s = "dup2_x1"; 
      break;
    case 0x5e: s = "dup2_x2"; 
      break;
    case 0x5f: s = "swap"; 
      break;
    case 0x60: s = "iadd"; 
      break;
    case 0x61: s = "ladd"; 
      break;
    case 0x62: s = "fadd"; 
      break;
    case 0x63: s = "dadd"; 
      break;
    case 0x64: s = "isub"; 
      break;
    case 0x65: s = "lsub"; 
      break;
    case 0x66: s = "fsub"; 
      break;
    case 0x67: s = "dsub"; 
      break;
    case 0x68: s = "imul"; 
      break;
    case 0x69: s = "lmul"; 
      break;
    case 0x6a: s = "fmul"; 
      break;
    case 0x6b: s = "dmul"; 
      break;
    case 0x6c: s = "idiv"; 
      break;
    case 0x6d: s = "ldiv"; 
      break;
    case 0x6e: s = "fdiv"; 
      break;
    case 0x6f: s = "ddiv"; 
      break;
    case 0x70: s = "irem"; 
      break;
    case 0x71: s = "lrem"; 
      break;
    case 0x72: s = "frem"; 
      break;
    case 0x73: s = "drem"; 
      break;
    case 0x74: s = "ineg"; 
      break;
    case 0x75: s = "lneg"; 
      break;
    case 0x76: s = "fneg"; 
      break;
    case 0x77: s = "dneg"; 
      break;
    case 0x78: s = "ishl"; 
      break;
    case 0x79: s = "lshl"; 
      break;
    case 0x7a: s = "ishr"; 
      break;
    case 0x7b: s = "lshr"; 
      break;
    case 0x7c: s = "iushr"; 
      break;
    case 0x7d: s = "lushr"; 
      break;
    case 0x7e: s = "iand"; 
      break;
    case 0x7f: s = "land"; 
      break;
    case 0x80: s = "ior"; 
      break;
    case 0x81: s = "lor"; 
      break;
    case 0x82: s = "ixor"; 
      break;
    case 0x83: s = "lxor"; 
      break;
    case 0x84: s = "iinc"; 
      break;
    case 0x85: s = "i2l"; 
      break;
    case 0x86: s = "i2f"; 
      break;
    case 0x87: s = "i2d"; 
      break;
    case 0x88: s = "l2i"; 
      break;
    case 0x89: s = "l2f"; 
      break;
    case 0x8a: s = "l2d"; 
      break;
    case 0x8b: s = "f2i"; 
      break;
    case 0x8c: s = "f2l"; 
      break;
    case 0x8d: s = "f2d"; 
      break;
    case 0x8e: s = "d2i"; 
      break;
    case 0x8f: s = "d2l"; 
      break;
    case 0x90: s = "d2f"; 
      break;
    case 0x91: s = "i2b"; 
      break;
    case 0x92: s = "i2c"; 
      break;
    case 0x93: s = "i2s"; 
      break;
    case 0x94: s = "lcmp"; 
      break;
    case 0x95: s = "fcmpl"; 
      break;
    case 0x96: s = "fcmpg"; 
      break;
    case 0x97: s = "dcmpl"; 
      break;
    case 0x98: s = "dcmpg"; 
      break;
    case 0x99: s = "ifeq"; 
      break;
    case 0x9a: s = "ifne"; 
      break;
    case 0x9b: s = "iflt"; 
      break;
    case 0x9c: s = "ifge"; 
      break;
    case 0x9d: s = "ifgt"; 
      break;
    case 0x9e: s = "ifle"; 
      break;
    case 0x9f: s = "if_icmpeq"; 
      break;
    case 0xa0: s = "if_icmpne"; 
      break;
    case 0xa1: s = "if_icmplt"; 
      break;
    case 0xa2: s = "if_icmpge"; 
      break;
    case 0xa3: s = "if_icmpgt"; 
      break;
    case 0xa4: s = "if_icmple"; 
      break;
    case 0xa5: s = "if_acmpeq"; 
      break;
    case 0xa6: s = "if_acmpne"; 
      break;
    case 0xa7: s = "goto"; 
      break;
    case 0xa8: s = "jsr"; 
      break;
    case 0xa9: s = "ret"; 
      break;
    case 0xaa: s = "tableswitch"; 
      break;
    case 0xab: s = "lookupswitch"; 
      break;
    case 0xac: s = "ireturn"; 
      break;
    case 0xad: s = "lreturn"; 
      break;
    case 0xae: s = "freturn"; 
      break;
    case 0xaf: s = "dreturn"; 
      break;
    case 0xb0: s = "areturn"; 
      break;
    case 0xb1: s = "return"; 
      break;
    case 0xb2: s = "getstatic"; 
      break;
    case 0xb3: s = "putstatic"; 
      break;
    case 0xb4: s = "getfield"; 
      break;
    case 0xb5: s = "putfield"; 
      break;
    case 0xb6: s = "invokevirtual"; 
      break;
    case 0xb7: s = "invokespecial"; 
      break;
    case 0xb8: s = "invokestatic"; 
      break;
    case 0xb9: s = "invokeinterface"; 
      break;
    case 0xba: s = "unused"; 
      break;
    case 0xbb: s = "new"; 
      break;
    case 0xbc: s = "newarray"; 
      break;
    case 0xbd: s = "anewarray"; 
      break;
    case 0xbe: s = "arraylength"; 
      break;
    case 0xbf: s = "athrow"; 
      break;
    case 0xc0: s = "checkcast"; 
      break;
    case 0xc1: s = "instanceof"; 
      break;
    case 0xc2: s = "monitorenter";
      break;
    case 0xc3: s = "monitorexit"; 
      break;
    case 0xc4: s = "wide"; 
      break;
    case 0xc5: s = "multianewarray"; 
      break;
    case 0xc6: s = "ifnull"; 
      break;
    case 0xc7: s = "ifnonnull"; 
      break;
    case 0xc8: s = "goto_w"; 
      break;
    case 0xc9: s = "jsr_w";
      break;
    default: s = "UNKNOWN";
      break;
    }
    return s;
  }

}
