/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.VM_Collector;

/**
 * VM_Compiler is the baseline compiler class for powerPC architectures.
 *
 * <p> The <code> compile() </code> method translates the bytecodes of a 
 * method to straightforward machine code.
 * 
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_Compiler extends VM_BaselineCompiler 
  implements VM_BaselineConstants,
	     VM_AssemblerConstants {

  // stackframe pseudo-constants //
  /*private*/ int frameSize;            //!!TODO: make private once VM_MagicCompiler is merged in
  /*private*/ int spSaveAreaOffset;     //!!TODO: make private once VM_MagicCompiler is merged in
  private int emptyStackOffset;
  private int firstLocalOffset;
  private int spillOffset;

  // If we're doing a short forward jump of less than 
  // this number of bytecodes, then we can always use a short-form
  // conditional branch (don't have to emit a nop & bc).
  private static final int SHORT_FORWARD_LIMIT = 500;

  /**
   * Create a VM_Compiler object for the compilation of method.
   */
  VM_Compiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    if (VM.VerifyAssertions) VM._assert(T3 <= LAST_VOLATILE_GPR);           // need 4 gp temps
    if (VM.VerifyAssertions) VM._assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
    if (VM.VerifyAssertions) VM._assert(S0 < SP && SP <= LAST_SCRATCH_GPR); // need 2 scratch
    frameSize        = getFrameSize(method);
    spSaveAreaOffset = getSPSaveAreaOffset(method);
    firstLocalOffset = getFirstLocalOffset(method);
    emptyStackOffset = getEmptyStackOffset(method);
  }


  //----------------//
  // more interface //
  //----------------//
  
  // position of spill area within method's stackframe.
  public static int getMaxSpillOffset (VM_Method m) throws VM_PragmaUninterruptible {
    int params = m.getOperandWords()<<2; // maximum parameter area
    int spill  = params - (MIN_PARAM_REGISTERS << 2);
    if (spill < 0) spill = 0;
    return STACKFRAME_HEADER_SIZE + spill - 4;
  }
  
  // position of operand stack within method's stackframe.
  public static int getEmptyStackOffset (VM_Method m) throws VM_PragmaUninterruptible {
    int stack = m.getOperandWords()<<2; // maximum stack size
    return getMaxSpillOffset(m) + stack + 4; // last local
  }
  
  // position of locals within method's stackframe.
  public static int getFirstLocalOffset (VM_Method m) throws VM_PragmaUninterruptible {
    int locals = m.getLocalWords()<<2;       // input param words + pure locals
    return getEmptyStackOffset(m) - 4 + locals; // bottom-most local
  }
  
  // position of SP save area within method's stackframe.
  static int getSPSaveAreaOffset (VM_Method m) throws VM_PragmaUninterruptible {
     return getFirstLocalOffset(m) + 4;
  }
  
  // size of method's stackframe.
  static int getFrameSize (VM_Method m) throws VM_PragmaUninterruptible {
    int size;
    if (!m.isNative()) {
      size = getSPSaveAreaOffset(m) + 4;
      if (m.getDeclaringClass().isDynamicBridge()) {
	size += (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * 8;
	size += (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * 4;
      }
      size = (size + STACKFRAME_ALIGNMENT_MASK) & 
              ~STACKFRAME_ALIGNMENT_MASK; // round up
      return size;
    } else {
      // space for:
      //   -AIX header (6 words)
      //   -parameters and 2 new JNI parameters (jnienv + obj), minimum 8 words
      //   -JNI_SAVE_AREA_OFFSET = savedSP + savedJTOC  + Processor_Register
      //                           nonvolatile registers + GC flag + 
      //                           affinity (20 words) +
      //                           saved volatile registers
      int argSpace = 4 * (m.getParameterWords()+ 2);
      if (argSpace<32)
	argSpace = 32;
      size = AIX_FRAME_HEADER_SIZE + argSpace + JNI_SAVE_AREA_SIZE;     
    }

    size = (size + STACKFRAME_ALIGNMENT_MASK) & ~STACKFRAME_ALIGNMENT_MASK; // round up
    return size;

  }

  /*
   * implementation of abstract methods of VM_BaselineCompiler
   */

  /*
   * Misc routines not directly tied to a particular bytecode
   */

  /**
   * Emit the prologue for the method
   */
  protected final void emit_prologue() {
    genPrologue();
  }

  /**
   * Emit the code for a threadswitch tests (aka a yieldpoint).
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  protected final void emit_threadSwitchTest(int whereFrom) {
    genThreadSwitchTest(whereFrom);
  }

  /**
   * Emit the code to implement the spcified magic.
   * @param magicMethod desired magic
   */
  protected final void emit_Magic(VM_Method magicMethod) {
    VM_MagicCompiler.generateInlineCode(this, magicMethod);
  }


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected final void emit_aconst_null() {
    asm.emitLIL(T0,  0);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected final void emit_iconst(int val) {
    asm.emitLIL(T0, val);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected final void emit_lconst(int val) {
    if (val == 0) {
      asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    } else if (val == 1) {
      if (VM.VerifyAssertions) VM._assert(val == 1);
      asm.emitLFDtoc(F0, VM_Entrypoints.longOneField.getOffset(), T0);
    }
    asm.emitSTFDU (F0, -8, SP);
  }

  /**
   * Emit code to load 0.0f
   */
  protected final void emit_fconst_0() {
    asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    asm.emitSTFSU (F0, -4, SP);
  }

  /**
   * Emit code to load 1.0f
   */
  protected final void emit_fconst_1() {
    asm.emitLFStoc(F0, VM_Entrypoints.oneFloatField.getOffset(), T0);
    asm.emitSTFSU (F0, -4, SP);
  }

  /**
   * Emit code to load 2.0f
   */
  protected final void emit_fconst_2() {
    asm.emitLFStoc(F0, VM_Entrypoints.twoFloatField.getOffset(), T0);
    asm.emitSTFSU (F0, -4, SP);
  }

  /**
   * Emit code to load 0.0d
   */
  protected final void emit_dconst_0() {
    asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    asm.emitSTFDU (F0, -8, SP);
  }

  /**
   * Emit code to load 1.0d
   */
  protected final void emit_dconst_1() {
    asm.emitLFStoc(F0, VM_Entrypoints.oneFloatField.getOffset(), T0);
    asm.emitSTFDU (F0, -8, SP);
  }

  /**
   * Emit code to load a 32 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc(int offset) {
    asm.emitLtoc(T0,  offset);
    asm.emitSTU (T0, -4, SP);
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc2(int offset) {
    asm.emitLFDtoc(F0,  offset, T0);
    asm.emitSTFDU (F0, -8, SP);
  }


  /*
   * loading local variables
   */


  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected final void emit_iload(int index) {
    int offset = localOffset(index);
    asm.emitL(T0, offset, FP);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected final void emit_lload(int index) {
    int offset = localOffset(index) - 4;
    asm.emitLFD  (F0, offset, FP);
    asm.emitSTFDU(F0, -8, SP);
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected final void emit_fload(int index) {
    int offset = localOffset(index);
    asm.emitL(T0, offset, FP);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected final void emit_dload(int index) {
    int offset = localOffset(index) - 4;
    asm.emitLFD  (F0, offset, FP);
    asm.emitSTFDU(F0, -8, SP);
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected final void emit_aload(int index) {
    int offset = localOffset(index);
    asm.emitL(T0, offset, FP);
    asm.emitSTU(T0, -4, SP);
  }


  /*
   * storing local variables
   */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected final void emit_istore(int index) {
    asm.emitL(T0, 0, SP);
    asm.emitCAL(SP, 4, SP);
    int offset = localOffset(index);
    asm.emitST(T0, offset, FP);
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected final void emit_lstore(int index) {
    asm.emitLFD(F0, 0, SP);
    asm.emitCAL(SP, 8, SP);
    int offset = localOffset(index)-4;
    asm.emitSTFD(F0, offset, FP);
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected final void emit_fstore(int index) {
    asm.emitL  (T0, 0, SP);
    asm.emitCAL(SP, 4, SP);
    int offset = localOffset(index);
    asm.emitST(T0, offset, FP);
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected final void emit_dstore(int index) {
    asm.emitLFD(F0, 0, SP);
    asm.emitCAL(SP, 8, SP);
    int offset = localOffset(index)-4;
    asm.emitSTFD(F0, offset, FP);
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected final void emit_astore(int index) {
    asm.emitL(T0, 0, SP);
    asm.emitCAL(SP, 4, SP);
    int offset = localOffset(index);
    asm.emitST(T0, offset, FP);
  }


  /*
   * array loads
   */


  /**
   * Emit code to load from an int array
   */
  protected final void emit_iaload() {
    aloadSetup(2);
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitLX  (T2, T0, T1);  // load desired int array element
    asm.emitSTU (T2,  4, SP);  
  }

  /**
   * Emit code to load from a long array
   */
  protected final void emit_laload() {
    aloadSetup(3);
    asm.emitSLI (T0, T0,  3);  // convert two word index to byte index
    asm.emitLFDX(F0, T0, T1);  // load desired (long) array element
    asm.emitSTFD(F0,  0, SP);  
  }

  /**
   * Emit code to load from a float array
   */
  protected final void emit_faload() {
    aloadSetup(2);
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitLX  (T2, T0, T1);  // load desired (float) array element
    asm.emitSTU (T2,  4, SP);  
  }

  /**
   * Emit code to load from a double array
   */
  protected final void emit_daload() {
    aloadSetup(3);
    asm.emitSLI (T0, T0,  3);  // convert two word index to byte index
    asm.emitLFDX(F0, T0, T1);  // load desired (double) array element
    asm.emitSTFD(F0,  0, SP);  
  }

  /**
   * Emit code to load from a reference array
   */
  protected final void emit_aaload() {
    aloadSetup(2);
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitLX  (T2, T0, T1);  // load desired (ref) array element
    asm.emitSTU (T2,  4, SP);  
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected final void emit_baload() {
    aloadSetup(0);
    asm.emitLBZX(T2, T0, T1);  // no load byte algebraic ...
    asm.emitSLI (T2, T2, 24);
    asm.emitSRAI(T2, T2, 24);  // propogate the sign bit
    asm.emitSTU (T2,  4, SP);  
  }

  /**
   * Emit code to load from a char array
   */
  protected final void emit_caload() {
    aloadSetup(1);
    asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
    asm.emitLHZX(T2, T0, T1);  // load desired (char) array element
    asm.emitSTU (T2,  4, SP);  
  }

  /**
   * Emit code to load from a short array
   */
  protected final void emit_saload() {
    aloadSetup(1);
    asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
    asm.emitLHAX(T2, T0, T1);  // load desired (short) array element
    asm.emitSTU (T2,  4, SP);  
  }


  /*
   * array stores
   */


  /**
   * Emit code to store to an int array
   */
  protected final void emit_iastore() {
    astoreSetup(2);
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitSTX (T3, T0, T1);  // store int value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }

  /**
   * Emit code to store to a long array
   */
  protected final void emit_lastore() {
    astoreLong();
  }

  /**
   * Emit code to store to a float array
   */
  protected final void emit_fastore() {
    astoreSetup(2);
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitSTX (T3, T0, T1);  // store float value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }

  /**
   * Emit code to store to a double array
   */
  protected final void emit_dastore() {
    astoreLong();
  }

  /**
   * Emit code to store to a reference array
   */
  protected final void emit_aastore() {
    asm.emitLtoc(T0,  VM_Entrypoints.checkstoreMethod.getOffset());
    asm.emitMTLR(T0);
    asm.emitL   (T0,  8, SP);  //  T0 := arrayref
    asm.emitL   (T1,  0, SP);  //  T1 := value
    asm.emitCall(spSaveAreaOffset);   // checkstore(arrayref, value)
    if (VM_Collector.NEEDS_WRITE_BARRIER) 
      VM_Barriers.compileArrayStoreBarrier(asm, spSaveAreaOffset);
    astoreSetup(-1);	// NOT (dfb): following 4 lines plus emitTLLE seem redundant and possibly bogus
    asm.emitL   (T1,  8, SP);                    // T1 is array ref
    asm.emitL   (T0,  4, SP);                    // T0 is array index
    asm.emitL   (T2,  VM_ObjectModel.getArrayLengthOffset(), T1);  // T2 is array length
    asm.emitL   (T3,  0, SP);                    // T3 is value to store
    emitSegmentedArrayAccess (asm, T1, T0, T2, 2);
    asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
    asm.emitSLI (T0, T0,  2);  // convert word index to byte index
    asm.emitSTX (T3, T0, T1);  // store ref value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected final void emit_bastore() {
    astoreSetup(0);
    asm.emitSTBX(T3, T0, T1);  // store byte value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }

  /**
   * Emit code to store to a char array
   */
  protected final void emit_castore() {
    astoreSetup(1);
    asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
    asm.emitSTHX(T3, T0, T1);  // store char value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }

  /**
   * Emit code to store to a short array
   */
  protected final void emit_sastore() {
    astoreSetup(1);
    asm.emitSLI (T0, T0,  1);  // convert halfword index to byte index
    asm.emitSTHX(T3, T0, T1);  // store short value in array
    asm.emitCAL (SP, 12, SP);  // complete 3 pops
  }


  /*
   * expression stack manipulation
   */


  /**
   * Emit code to implement the pop bytecode
   */
  protected final void emit_pop() {
    asm.emitCAL(SP, 4, SP);
  }

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected final void emit_pop2() {
    asm.emitCAL(SP, 8, SP);
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    asm.emitL  (T0,  0, SP);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected final void emit_dup_x1() {
    asm.emitL  (T0,  0, SP);
    asm.emitL  (T1,  4, SP);
    asm.emitST (T0,  4, SP);
    asm.emitST (T1,  0, SP);
    asm.emitSTU(T0, -4, SP);
  }

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected final void emit_dup_x2() {
    asm.emitL   (T0,  0, SP);
    asm.emitLFD (F0,  4, SP);
    asm.emitST  (T0,  8, SP);
    asm.emitSTFD(F0,  0, SP);
    asm.emitSTU (T0, -4, SP);
  }

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected final void emit_dup2() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitSTFDU(F0, -8, SP);
  }

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected final void emit_dup2_x1() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitL    (T0,  8, SP);
    asm.emitSTFD (F0,  4, SP);
    asm.emitST   (T0,  0, SP);
    asm.emitSTFDU(F0, -8, SP);
  }

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected final void emit_dup2_x2() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitLFD  (F1,  8, SP);
    asm.emitSTFD (F0,  8, SP);
    asm.emitSTFD (F1,  0, SP);
    asm.emitSTFDU(F0, -8, SP);
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    asm.emitL  (T0,  0, SP);
    asm.emitL  (T1,  4, SP);
    asm.emitST (T0,  4, SP);
    asm.emitST (T1,  0, SP);
  }


  /*
   * int ALU
   */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected final void emit_iadd() {
    asm.emitL  (T0,  0, SP);
    asm.emitL  (T1,  4, SP);
    asm.emitA  (T2, T1, T0);
    asm.emitSTU(T2,  4, SP);
  }

  /**
   * Emit code to implement the isub bytecode
   */
  protected final void emit_isub() {
    asm.emitL  (T0,  0, SP);
    asm.emitL  (T1,  4, SP);
    asm.emitSF (T2, T0, T1);
    asm.emitSTU(T2,  4, SP);
  }

  /**
   * Emit code to implement the imul bytecode
   */
  protected final void emit_imul() {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitMULS(T1,T0, T1);
    asm.emitSTU(T1, 4, SP);
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    asm.emitL   (T0, 4, SP);
    asm.emitL   (T1, 0, SP);
    asm.emitTEQ0(T1);
    asm.emitDIV (T0, T0, T1);  // T0 := T0/T1
    asm.emitSTU (T0, 4, SP);
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    asm.emitL   (T0, 4, SP);
    asm.emitL   (T1, 0, SP);
    asm.emitTEQ0(T1);
    asm.emitDIV (T2, T0, T1);   // T2 := T0/T1
    asm.emitMULS(T2, T2, T1);   // T2 := [T0/T1]*T1
    asm.emitSF  (T1, T2, T0);   // T1 := T0 - [T0/T1]*T1
    asm.emitSTU (T1, 4, SP);
  }

  /**
   * Emit code to implement the ineg bytecode
   */
  protected final void emit_ineg() {
    asm.emitL  (T0,  0, SP);
    asm.emitNEG(T0, T0);
    asm.emitST (T0,  0, SP);
  }

  /**
   * Emit code to implement the ishl bytecode
   */
  protected final void emit_ishl() {
    asm.emitL   (T0,  4, SP);
    asm.emitL   (T1,  0, SP);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSL  (T0, T0, T1);
    asm.emitSTU (T0,  4, SP);
  }

  /**
   * Emit code to implement the ishr bytecode
   */
  protected final void emit_ishr() {
    asm.emitL   (T0,  4, SP);
    asm.emitL   (T1,  0, SP);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSRA (T0, T0, T1);
    asm.emitSTU (T0,  4, SP);
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    asm.emitL   (T0,  4, SP);
    asm.emitL   (T1,  0, SP);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSR  (T0, T0, T1);
    asm.emitSTU (T0,  4, SP);
  }

  /**
   * Emit code to implement the iand bytecode
   */
  protected final void emit_iand() {
    asm.emitL   (T0,  0, SP);
    asm.emitL   (T1,  4, SP);
    asm.emitAND (T2, T0, T1);
    asm.emitSTU (T2,  4, SP);
  }

  /**
   * Emit code to implement the ior bytecode
   */
  protected final void emit_ior() {
    asm.emitL   (T0,  0,SP);
    asm.emitL   (T1,  4,SP);
    asm.emitOR  (T2, T0,T1);
    asm.emitSTU (T2,  4,SP);
  }

  /**
   * Emit code to implement the ixor bytecode
   */
  protected final void emit_ixor() {
    asm.emitL   (T0,  0,SP);
    asm.emitL   (T1,  4,SP);
    asm.emitXOR (T2, T0,T1);
    asm.emitSTU (T2,  4,SP);
  }

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected final void emit_iinc(int index, int val) {
    int offset = localOffset(index);
    asm.emitL  (T0, offset, FP);
    asm.emitCAL(T0, val, T0);
    asm.emitST (T0, offset, FP);
  }


  /*
   * long ALU
   */


  /**
   * Emit code to implement the ladd bytecode
   */
  protected final void emit_ladd() {
    asm.emitL  (T0,  4, SP);
    asm.emitL  (T1, 12, SP);
    asm.emitL  (T2,  0, SP);
    asm.emitL  (T3,  8, SP);
    asm.emitA  (T0, T1, T0);
    asm.emitAE (T1, T2, T3);
    asm.emitST (T0, 12, SP);
    asm.emitSTU(T1,  8, SP);
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    asm.emitL  (T0,  4, SP);
    asm.emitL  (T1, 12, SP);
    asm.emitL  (T2,  0, SP);
    asm.emitL  (T3,  8, SP);
    asm.emitSF (T0, T0, T1);
    asm.emitSFE(T1, T2, T3);
    asm.emitST (T0, 12, SP);
    asm.emitSTU(T1,  8, SP);
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    asm.emitL     (T1, 12, SP);
    asm.emitL     (T3,  4, SP);
    asm.emitL     (T0,  8, SP);
    asm.emitL     (T2,  0, SP);
    asm.emitMULHWU(S0, T1, T3);
    asm.emitMULS  (T0, T0, T3);
    asm.emitA     (T0, T0, S0);
    asm.emitMULS  (S0, T1, T2);
    asm.emitMULS  (T1, T1, T3);
    asm.emitA     (T0, T0, S0);
    asm.emitST    (T1, 12, SP);
    asm.emitSTU   (T0,  8, SP);
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    asm.emitL   (T3,  4, SP);
    asm.emitL   (T2,  0, SP);
    asm.emitOR  (T0, T3, T2); // or two halfs of denomenator together
    asm.emitTEQ0(T0);         // trap if 0.
    asm.emitL   (T1, 12, SP);
    asm.emitL   (T0,  8, SP);
    VM_MagicCompiler.generateSysCall(asm, 16, VM_Entrypoints.sysLongDivideIPField);
    asm.emitST  (T1, 12, SP); 
    asm.emitSTU (T0,  8, SP); 
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    asm.emitL   (T3,  4, SP);
    asm.emitL   (T2,  0, SP);
    asm.emitOR  (T0, T3, T2); // or two halfs of denomenator together
    asm.emitTEQ0(T0);         // trap if 0.
    asm.emitL   (T1, 12, SP);
    asm.emitL   (T0,  8, SP);
    VM_MagicCompiler.generateSysCall(asm, 16, VM_Entrypoints.sysLongRemainderIPField);
    asm.emitST  (T1, 12, SP); 
    asm.emitSTU (T0,  8, SP); 
  }

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    asm.emitL   (T0,  4, SP);
    asm.emitL   (T1,  0, SP);
    asm.emitSFI (T0, T0, 0x0);
    asm.emitSFZE(T1, T1);
    asm.emitST  (T0,  4, SP);
    asm.emitSTU (T1,  0, SP);
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    asm.emitL   (T0,  0, SP);    // T0 is n
    asm.emitL   (T1,  8, SP);    // T1 is low bits of l
    asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
    asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
    asm.emitSL  (T3, T1, T0);    // low bits of l shifted n or n-32 bits
    asm.emitL   (T2,  4, SP);    // T2 is high bits of l
    VM_ForwardReference fr1 = asm.emitForwardBC(EQ); // if shift less than 32, goto
    asm.emitSTU (T3,  4, SP);    // store high bits of result
    asm.emitLIL (T0,  0);        // low bits are zero
    asm.emitST  (T0,  4, SP);    // store 'em
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    asm.emitSL  (T2, T2, T0);    // high bits of l shifted n bits left
    asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0; 
    asm.emitSR  (T1, T1, T0);    // T1 is middle bits of result
    asm.emitOR  (T2, T2, T1);    // T2 is high bits of result
    asm.emitSTU (T2,  4, SP);    // store high bits of result
    asm.emitST  (T3,  4, SP);    // store low bits of result           
    fr2.resolve(asm);
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    asm.emitL   (T0,  0, SP);    // T0 is n
    asm.emitL   (T2,  4, SP);    // T2 is high bits of l
    asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
    asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
    asm.emitSRA (T3, T2, T0);    // high bits of l shifted n or n-32 bits
    asm.emitL   (T1,  8, SP);    // T1 is low bits of l
    VM_ForwardReference fr1 = asm.emitForwardBC(EQ);
    asm.emitST  (T3,  8, SP);    // store low bits of result
    asm.emitSRAI(T0, T3, 0x1F);  // propogate a full work of sign bit
    asm.emitSTU (T0,  4, SP);    // store high bits of result
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    asm.emitSR  (T1, T1, T0);    // low bits of l shifted n bits right
    asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0;
    asm.emitSL  (T2, T2, T0);    // T2 is middle bits of result
    asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
    asm.emitST  (T1,  8, SP);    // store low bits of result 
    asm.emitSTU (T3,  4, SP);    // store high bits of result          
    fr2.resolve(asm);
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    asm.emitL   (T0,  0, SP);    // T0 is n
    asm.emitL   (T2,  4, SP);    // T2 is high bits of l
    asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
    asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
    asm.emitSR  (T3, T2, T0);    // high bits of l shifted n or n-32 bits
    asm.emitL   (T1,  8, SP);    // T1 is low bits of l
    VM_ForwardReference fr1 = asm.emitForwardBC(EQ);
    asm.emitST  (T3,  8, SP);    // store low bits of result
    asm.emitLIL (T0,  0);        // high bits are zero
    asm.emitSTU (T0,  4, SP);    // store 'em
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    asm.emitSR  (T1, T1, T0);    // low bits of l shifted n bits right
    asm.emitSFI (T0, T0, 0x20);  // T0 := 32 - T0;
    asm.emitSL  (T2, T2, T0);    // T2 is middle bits of result
    asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
    asm.emitST  (T1,  8, SP);    // store low bits of result 
    asm.emitSTU (T3,  4, SP);    // store high bits of result          
    fr2.resolve(asm);
  }

  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    asm.emitL  (T0,  4, SP);
    asm.emitL  (T1, 12, SP);
    asm.emitL  (T2,  0, SP);
    asm.emitL  (T3,  8, SP);
    asm.emitAND(T0, T1, T0);
    asm.emitAND(T1, T2, T3);
    asm.emitST (T0, 12, SP);
    asm.emitSTU(T1,  8, SP);
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    asm.emitL  (T0,  4, SP);
    asm.emitL  (T1, 12, SP);
    asm.emitL  (T2,  0, SP);
    asm.emitL  (T3,  8, SP);
    asm.emitOR (T0, T1, T0);
    asm.emitOR (T1, T2, T3);
    asm.emitST (T0, 12, SP);
    asm.emitSTU(T1,  8, SP);
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    asm.emitL  (T0,  4, SP);
    asm.emitL  (T1, 12, SP);
    asm.emitL  (T2,  0, SP);
    asm.emitL  (T3,  8, SP);
    asm.emitXOR(T0, T1, T0);
    asm.emitXOR(T1, T2, T3);
    asm.emitST (T0, 12, SP);
    asm.emitSTU(T1,  8, SP);
  }


  /*
   * float ALU
   */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitLFS  (F1,  4, SP);
    asm.emitFAs  (F0, F1, F0);
    asm.emitSTFSU(F0,  4, SP);
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitLFS  (F1,  4, SP);
    asm.emitFSs  (F0, F1, F0);
    asm.emitSTFSU(F0,  4, SP);
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitLFS  (F1,  4, SP);
    asm.emitFMs  (F0, F1, F0); // single precision multiply
    asm.emitSTFSU(F0,  4, SP);
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitLFS  (F1,  4, SP);
    asm.emitFDs  (F0, F1, F0);
    asm.emitSTFSU(F0,  4, SP);
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    asm.emitLFS   (F1,  0, SP);              // F1 is b
    asm.emitLFS   (F0,  4, SP);              // F0 is a
    VM_MagicCompiler.generateSysCall(asm, 8, VM_Entrypoints.sysDoubleRemainderIPField);
    asm.emitSTFSU (F0,  4, SP);
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    asm.emitLFS (F0,  0, SP);
    asm.emitFNEG(F0, F0);
    asm.emitSTFS(F0,  0, SP);
  }


  /*
   * double ALU
   */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitLFD  (F1,  8, SP);
    asm.emitFA   (F0, F1, F0);
    asm.emitSTFDU(F0,  8, SP);
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitLFD  (F1,  8, SP);
    asm.emitFS   (F0, F1, F0);
    asm.emitSTFDU(F0,  8, SP);
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitLFD  (F1,  8, SP);
    asm.emitFM   (F0, F1, F0);
    asm.emitSTFDU(F0,  8, SP);
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitLFD  (F1,  8, SP);
    asm.emitFD   (F0, F1, F0);
    asm.emitSTFDU(F0,  8, SP);
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    asm.emitLFD   (F1,  0, SP);              // F1 is b
    asm.emitLFD   (F0,  8, SP);              // F0 is a
    VM_MagicCompiler.generateSysCall(asm, 16, VM_Entrypoints.sysDoubleRemainderIPField);
    asm.emitSTFDU (F0,  8, SP);
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    asm.emitLFD (F0,  0, SP);
    asm.emitFNEG(F0, F0);
    asm.emitSTFD(F0,  0, SP);
  }


  /*
   * conversion ops
   */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    asm.emitL   (T0,  0, SP);
    asm.emitSRAI(T1, T0, 31);
    asm.emitSTU (T1, -4, SP);
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {
    asm.emitL     (T0,  0, SP);               // T0 is X (an int)
    asm.emitCMPI  (T0,  0);                   // is X < 0
    asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicField.getOffset(), T1);  // F0 is MAGIC
    asm.emitSTFD  (F0, -4, SP);               // MAGIC on stack
    asm.emitST    (T0,  0, SP);               // if 0 <= X, MAGIC + X 
    VM_ForwardReference fr = asm.emitForwardBC(GE);
    asm.emitL     (T0, -4, SP);               // T0 is top of MAGIC
    asm.emitCAL   (T0, -1, T0);               // decrement top of MAGIC
    asm.emitST    (T0, -4, SP);               // MAGIC + X is on stack
    fr.resolve(asm);
    asm.emitLFD   (F1, -4, SP);               // F1 is MAGIC + X
    asm.emitFS    (F1, F1, F0);               // F1 is X
    asm.emitSTFS  (F1,  0, SP);               // float(X) is on stack 
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    asm.emitL     (T0,  0, SP);               // T0 is X (an int)
    asm.emitCMPI  (T0,  0);                   // is X < 0
    asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicField.getOffset(), T1);  // F0 is MAGIC
    asm.emitSTFD  (F0, -4, SP);               // MAGIC on stack
    asm.emitST    (T0,  0, SP);               // if 0 <= X, MAGIC + X 
    VM_ForwardReference fr = asm.emitForwardBC(GE); // ow, handle X < 0
    asm.emitL     (T0, -4, SP);               // T0 is top of MAGIC
    asm.emitCAL   (T0, -1, T0);               // decrement top of MAGIC
    asm.emitST    (T0, -4, SP);               // MAGIC + X is on stack
    fr.resolve(asm);
    asm.emitLFD   (F1, -4, SP);               // F1 is MAGIC + X
    asm.emitFS    (F1, F1, F0);               // F1 is X
    asm.emitSTFDU (F1, -4, SP);               // float(X) is on stack 
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    asm.emitCAL(SP, 4, SP); // throw away top of the long
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    asm.emitL   (T1, 4, SP);
    asm.emitL   (T0, 0, SP);
    VM_MagicCompiler.generateSysCall(asm, 8, VM_Entrypoints.sysLongToFloatIPField);
    asm.emitSTFSU(F0,  4, SP);
  }

  /**
   * Emit code to implement the l2d bytecode
   */
  protected final void emit_l2d() {
    asm.emitL   (T1, 4, SP);
    asm.emitL   (T0, 0, SP);
    VM_MagicCompiler.generateSysCall(asm, 8, VM_Entrypoints.sysLongToDoubleIPField);
    asm.emitSTFD(F0,  0, SP);
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitFCTIZ(F0, F0);
    asm.emitSTFD (F0, -4, SP); 
  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
    asm.emitLFS (F0,  0, SP);
    VM_MagicCompiler.generateSysCall(asm, 4, VM_Entrypoints.sysFloatToLongIPField);
    asm.emitST  (T1,  0, SP);
    asm.emitSTU (T0, -4, SP);
  }

  /**
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    asm.emitLFS  (F0,  0, SP);
    asm.emitSTFDU(F0, -4, SP);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    asm.emitLFD  (F0,  0, SP);
    asm.emitFCTIZ(F0, F0);
    asm.emitSTFD (F0,  0, SP);
    asm.emitCAL  (SP,  4, SP);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    asm.emitLFD (F0,  0, SP);
    VM_MagicCompiler.generateSysCall(asm, 8, VM_Entrypoints.sysDoubleToLongIPField);
    asm.emitST  (T1, 4, SP);
    asm.emitSTU (T0, 0, SP);
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    asm.emitLFD  (F0, 0, SP);
    asm.emitSTFSU(F0, 4, SP);
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    asm.emitL   (T0,  3, SP);
    asm.emitSRAI(T0, T0, 24);
    asm.emitST  (T0,  0, SP);
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    asm.emitLHZ(T0, 2, SP);
    asm.emitST (T0, 0, SP);
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    asm.emitLHA(T0, 2, SP);
    asm.emitST (T0, 0, SP);
  }


  /*
   * comparision ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    asm.emitL    (T1,  8, SP);  // T1 is ah
    asm.emitL    (T3,  0, SP);  // T3 is bh
    asm.emitCMP  (T1, T3);      // ah ? al
    VM_ForwardReference fr1 = asm.emitForwardBC(LT);
    VM_ForwardReference fr2 = asm.emitForwardBC(GT);
    asm.emitL    (T0, 12, SP);  // (ah == bh), T0 is al
    asm.emitL    (T2,  4, SP);  // T2 is bl
    asm.emitCMPL (T0, T2);      // al ? bl (logical compare)
    VM_ForwardReference fr3 = asm.emitForwardBC(LT);
    VM_ForwardReference fr4 = asm.emitForwardBC(GT);
    asm.emitLIL  (T0,  0);      // a == b
    asm.emitSTU  (T0, 12, SP);  // push  0
    VM_ForwardReference fr5 = asm.emitForwardB();
    fr1.resolve(asm);
    fr3.resolve(asm);
    asm.emitLIL  (T0, -1);      // a <  b
    asm.emitSTU  (T0, 12, SP);  // push -1
    VM_ForwardReference fr6 = asm.emitForwardB();
    fr2.resolve(asm);
    fr4.resolve(asm);
    asm.emitLIL  (T0,  1);      // a >  b
    asm.emitSTU  (T0, 12, SP);  // push  1
    fr5.resolve(asm);
    fr6.resolve(asm);
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    asm.emitLFS  (F0,  4, SP);
    asm.emitLFS  (F1,  0, SP);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(LE);
    asm.emitLIL  (T0,  1); // the GT bit of CR0
    asm.emitSTU  (T0,  4, SP);
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLIL  (T0, -1); // the LT or UO bits of CR0
    asm.emitSTU  (T0,  4, SP);
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLIL  (T0,  0);
    asm.emitSTU  (T0,  4, SP); // the EQ bit of CR0
    fr2.resolve(asm);
    fr4.resolve(asm);
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    asm.emitLFS  (F0,  4, SP);
    asm.emitLFS  (F1,  0, SP);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(GE);
    asm.emitLIL  (T0, -1);     // the LT bit of CR0
    asm.emitSTU  (T0,  4, SP);
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLIL  (T0,  1);     // the GT or UO bits of CR0
    asm.emitSTU  (T0,  4, SP);
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLIL  (T0,  0);     // the EQ bit of CR0
    asm.emitSTU  (T0,  4, SP);
    fr2.resolve(asm);
    fr4.resolve(asm);
  }

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    asm.emitLFD  (F0,  8, SP);
    asm.emitLFD  (F1,  0, SP);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(LE);
    asm.emitLIL  (T0,  1); // the GT bit of CR0
    asm.emitSTU  (T0, 12, SP);
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLIL  (T0, -1); // the LT or UO bits of CR0
    asm.emitSTU  (T0, 12, SP);
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLIL  (T0,  0);
    asm.emitSTU  (T0, 12, SP); // the EQ bit of CR0
    fr2.resolve(asm);
    fr4.resolve(asm);
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    asm.emitLFD  (F0,  8, SP);
    asm.emitLFD  (F1,  0, SP);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(GE);
    asm.emitLIL  (T0, -1); // the LT bit of CR0
    asm.emitSTU  (T0, 12, SP);
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLIL  (T0,  1); // the GT or UO bits of CR0
    asm.emitSTU  (T0, 12, SP);
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLIL  (T0,  0); // the EQ bit of CR0
    asm.emitSTU  (T0, 12, SP);
    fr2.resolve(asm);
    fr4.resolve(asm);
  }


  /*
   * branching
   */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifeq(int bTarget) {
    asm.emitL  (T0,  0, SP);
    asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    asm.emitCAL(SP,  4, SP); // completes pop
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    asm.emitL  (T0,  0, SP);
    asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    asm.emitCAL(SP,  4, SP); // completes pop
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget) {
    asm.emitL  (T0,  0, SP);
    asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    asm.emitCAL(SP,  4, SP); // completes pop
    genCondBranch(LT, bTarget);
  }

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget) {
    asm.emitL  (T0,  0, SP);
    asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    asm.emitCAL(SP,  4, SP); // completes pop
    genCondBranch(GE, bTarget);
  }

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget) {
    asm.emitL  (T0,  0, SP);
    asm.emitAIr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    asm.emitCAL(SP,  4, SP); // completes pop
    genCondBranch(GT, bTarget);
  }

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget) {
    asm.emitL  (T0, 0, SP);
    asm.emitAIr(0,  T0,  0); // T0 to 0 and sets CR0 
    asm.emitCAL(SP, 4, SP);  // completes pop
    genCondBranch(LE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(LT, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(GE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(GT, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    asm.emitL  (T0, 4, SP);
    asm.emitL  (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(LE, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    asm.emitL (T0, 4, SP);
    asm.emitL (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget) {
    asm.emitL (T0, 4, SP);
    asm.emitL (T1, 0, SP);
    asm.emitCMP(T0, T1);    // sets CR0
    asm.emitCAL(SP, 8, SP); // completes 2 pops
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    asm.emitL   (T0,  0, SP);
    asm.emitLIL (T1,  0);
    asm.emitCMP (T0, T1);  
    asm.emitCAL (SP,  4, SP);
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    asm.emitL   (T0,  0, SP);
    asm.emitLIL (T1,  0);
    asm.emitCMP (T0, T1);  
    asm.emitCAL (SP,  4, SP);
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the goto and gotow bytecodes
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_goto(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitB(mTarget, bTarget);
  }

  /**
   * Emit code to implement the jsr and jsrw bytecode
   * @param bTarget target bytecode of the jsr
   */
  protected final void emit_jsr(int bTarget) {
    VM_ForwardReference fr = asm.emitForwardBL();
    fr.resolve(asm); // get PC into LR...
    asm.emitMFLR(T1);           // LR +  0
    asm.emitCAL (T1, 16, T1);   // LR +  4  (LR + 16 is ret address)
    asm.emitSTU (T1, -4, SP);   // LR +  8
    asm.emitBL(bytecodeMap[bTarget], bTarget); // LR + 12
  }

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected final void emit_ret(int index) {
    int offset = localOffset(index);
    asm.emitL(T0, offset, FP);
    asm.emitMTLR(T0);
    asm.emitBLR ();
  }

  /**
   * Emit code to implement the tableswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param low low value of switch
   * @param high high value of switch
   */
  protected final void emit_tableswitch(int defaultval, int low, int high) {
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    int n = high-low+1;       // n = number of normal cases (0..n-1)
    int firstCounter = edgeCounterIdx; // only used if options.EDGE_COUNTERS;

    asm.emitL   (T0, 0, SP);  // T0 is index
    asm.emitCAL (SP,  4, SP); // pop index from stack
    if (asm.fits(16, -low)) {
      asm.emitCAL(T0, -low, T0);
    } else {
      asm.emitLVAL(T1, low);
      asm.emitSF  (T0, T1, T0); 
    }
    asm.emitLVAL(T2, n);
    asm.emitCMPL(T0, T2);
    if (options.EDGE_COUNTERS) {
      edgeCounterIdx += n+1; // allocate n+1 counters
      // Load counter array for this method
      asm.emitLtoc (T2, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLoffset(T2, T2, getEdgeCounterOffset());
      
      VM_ForwardReference fr = asm.emitForwardBC(LT); // jump around jump to default target
      incEdgeCounter(T2, S0, firstCounter + n);
      asm.emitB (mTarget, bTarget);
      fr.resolve(asm);
    } else {
      // conditionally jump to default target
      if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
	asm.emitShortBC(GE, mTarget, bTarget);
      } else {
	asm.emitBC  (GE, mTarget, bTarget); 
      }
    }
    VM_ForwardReference fr1 = asm.emitForwardBL();
    for (int i=0; i<n; i++) {
      int offset = fetch4BytesSigned();
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      asm.emitSwitchCase(i, mTarget, bTarget);
    }
    fr1.resolve(asm);
    asm.emitMFLR(T1);         // T1 is base of table
    asm.emitSLI (T0, T0,  2); // convert to bytes
    if (options.EDGE_COUNTERS) {
      incEdgeCounterIdx(T2, S0, firstCounter, T0);
    }
    asm.emitLX  (T0, T0, T1); // T0 is relative offset of desired case
    asm.emitA   (T1, T1, T0); // T1 is absolute address of desired case
    asm.emitMTLR(T1);
    asm.emitBLR ();
  }

  /**
   * Emit code to implement the lookupswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected final void emit_lookupswitch(int defaultval, int npairs) {
    if (options.EDGE_COUNTERS) {
      // Load counter array for this method
      asm.emitLtoc (T2, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLoffset(T2, T2, getEdgeCounterOffset());
    }

    asm.emitL   (T0,  0, SP); // T0 is key
    asm.emitCAL (SP,  4, SP); // pop key
    for (int i=0; i<npairs; i++) {
      int match   = fetch4BytesSigned();
      if (asm.fits(match, 16)) {
	asm.emitCMPI(T0, match);
      } else {
	asm.emitLVAL(T1, match);
	asm.emitCMP(T0, T1);
      }
      int offset  = fetch4BytesSigned();
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      if (options.EDGE_COUNTERS) {
	// Flip conditions so we can jump over the increment of the taken counter.
	VM_ForwardReference fr = asm.emitForwardBC(NE);
	// Increment counter & jump to target
	incEdgeCounter(T2, S0, edgeCounterIdx++);
	asm.emitB(mTarget, bTarget);
	fr.resolve(asm);
      } else {
	if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
	  asm.emitShortBC(EQ, mTarget, bTarget);
	} else {
	  asm.emitBC(EQ, mTarget, bTarget);
	}
      }
    }
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    if (options.EDGE_COUNTERS) {
      incEdgeCounter(T2, S0, edgeCounterIdx++);
    }
    asm.emitB(mTarget, bTarget);
  }


  /*
   * returns (from function; NOT ret)
   */


  /**
   * Emit code to implement the ireturn bytecode
   */
  protected final void emit_ireturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitL(T0, 0, SP);
    genEpilogue();
  }

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected final void emit_lreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitL(T1, 4, SP); // hi register := lo word (which is at higher memory address)
    asm.emitL(T0, 0, SP); // lo register := hi word (which is at lower  memory address)
    genEpilogue();
  }

  /**
   * Emit code to implement the freturn bytecode
   */
  protected final void emit_freturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitLFS(F0, 0, SP);
    genEpilogue();
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitLFD(F0, 0, SP);
    genEpilogue();
  }

  /**
   * Emit code to implement the areturn bytecode
   */
  protected final void emit_areturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    asm.emitL(T0, 0, SP);
    genEpilogue();
  }

  /**
   * Emit code to implement the return bytecode
   */
  protected final void emit_return() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    genEpilogue();
  }


  /*
   * field access
   */


  /**
   * Emit code to implement a dynamically linked getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getstatic(VM_Field fieldRef) {
    emitDynamicLinkingSequence(fieldRef); // leaves field offset in T2
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitLX (T0, T2, JTOC);
      asm.emitSTU (T0, -4, SP);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFDX (F0, T2, JTOC);
      asm.emitSTFDU (F0, -8, SP);
    }
  }

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitLtoc(T0, fieldOffset);
      asm.emitSTU (T0, -4, SP);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFDtoc(F0, fieldOffset, T0);
      asm.emitSTFDU (F0, -8, SP);
    }
  }


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putstatic(VM_Field fieldRef) {
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compileUnresolvedPutstaticBarrier(asm, spSaveAreaOffset, fieldRef.getDictionaryId());
    }
    emitDynamicLinkingSequence(fieldRef);		      // leaves field offset in T2
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitL    (T0, 0, SP);
      asm.emitCAL  (SP, 4, SP);
      asm.emitSTX(T0, T2, JTOC);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFD    (F0, 0, SP );
      asm.emitCAL    (SP, 8, SP);
      asm.emitSTFDX(F0, T2, JTOC);
    }
  }

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putstatic(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compilePutstaticBarrier(asm, spSaveAreaOffset, fieldOffset);
    }
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitL    (T0, 0, SP);
      asm.emitCAL  (SP, 4, SP);
      asm.emitSTtoc(T0, fieldOffset, T1);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFD    (F0, 0, SP );
      asm.emitCAL    (SP, 8, SP);
      asm.emitSTFDtoc(F0, fieldOffset, T0);
    }
  }


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_Field fieldRef) {
    emitDynamicLinkingSequence(fieldRef);		      // leaves field offset in T2
    asm.emitL (T1, 0, SP); // T1 = object reference
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitLX(T0, T2, T1); // use field offset in T2 from emitDynamicLinkingSequence()
      asm.emitST(T0, 0, SP);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFDX (F0, T2, T1); // use field offset in T2 from emitDynamicLinkingSequence()
      asm.emitSTFDU(F0, -4, SP);
    }
  }

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    asm.emitL (T1, 0, SP); // T1 = object reference
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitL (T0, fieldOffset, T1);
      asm.emitST(T0, 0, SP);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFD  (F0, fieldOffset, T1);
      asm.emitSTFDU(F0, -4, SP);
    }
  }


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_Field fieldRef) {
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compileUnresolvedPutfieldBarrier(asm, spSaveAreaOffset, fieldRef.getDictionaryId());
    }
    emitDynamicLinkingSequence(fieldRef);		      // leaves field offset in T2
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitL  (T1, 4, SP); // T1 = object reference
      asm.emitL  (T0, 0, SP); // T0 = value
      asm.emitCAL(SP, 8, SP);  
      asm.emitSTX(T0, T2, T1);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFD (F0,  0, SP); // F0 = doubleword value
      asm.emitL   (T1,  8, SP); // T1 = object reference
      asm.emitCAL (SP, 12, SP);
      asm.emitSTFDX(F0, T2, T1);
    }
  }

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_Field fieldRef) {
    int fieldOffset = fieldRef.getOffset();
    if (VM_Collector.NEEDS_WRITE_BARRIER && !fieldRef.getType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrier(asm, spSaveAreaOffset, fieldOffset);
    }
    if (fieldRef.getSize() == 4) { // field is one word
      asm.emitL  (T1, 4, SP); // T1 = object reference
      asm.emitL  (T0, 0, SP); // T0 = value
      asm.emitCAL(SP, 8, SP);  
      asm.emitST (T0, fieldOffset, T1);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == 8);
      asm.emitLFD (F0,  0, SP); // F0 = doubleword value
      asm.emitL   (T1,  8, SP); // T1 = object reference
      asm.emitCAL (SP, 12, SP);
      asm.emitSTFD(F0, fieldOffset, T1);
    }
  }


  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokevirtual(VM_Method methodRef) {
    int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int objectOffset = (methodRefParameterWords << 2) - 4;
    asm.emitL   (T0, objectOffset,      SP); // load this
    VM_ObjectModel.baselineEmitLoadTIB(asm, T1, T0); // load TIB
    emitDynamicLinkingSequence(methodRef); // leaves method offset in T2
    asm.emitLX  (T2, T2, T1);  
    asm.emitMTLR(T2);
    genMoveParametersToRegisters(true, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokevirtual(VM_Method methodRef) {
    int methodRefParameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int objectOffset = (methodRefParameterWords << 2) - 4;
    asm.emitL   (T0, objectOffset,      SP); // load this
    VM_ObjectModel.baselineEmitLoadTIB(asm, T1, T0); // load TIB
    int methodOffset = methodRef.getOffset();
    asm.emitL   (T2, methodOffset,   T1);
    asm.emitMTLR(T2);
    genMoveParametersToRegisters(true, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param targetRef the method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_Method methodRef, VM_Method target) {
    if (target.isObjectInitializer()) { // invoke via method's jtoc slot
      asm.emitLtoc(T0, target.getOffset());
    } else { // invoke via class's tib slot
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      asm.emitLtoc(T0, target.getDeclaringClass().getTibOffset());
      asm.emitL   (T0, target.getOffset(), T0);
    }
    asm.emitMTLR(T0);
    genMoveParametersToRegisters(true, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, methodRef, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokespecial(VM_Method methodRef) {
    // must be a static method; if it was a super then declaring class _must_ be resolved
    emitDynamicLinkingSequence(methodRef); // leaves method offset in T2
    asm.emitLX    (T0, T2, JTOC); 
    asm.emitMTLR(T0);
    genMoveParametersToRegisters(true, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, methodRef, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_Method methodRef) {
    emitDynamicLinkingSequence(methodRef);		      // leaves method offset in T2
    asm.emitLX  (T0, T2, JTOC); // method offset left in T2 by emitDynamicLinkingSequence
    asm.emitMTLR(T0);
    genMoveParametersToRegisters(false, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_Method methodRef) {
    int methodOffset = methodRef.getOffset();
    asm.emitLtoc(T0, methodOffset);
    asm.emitMTLR(T0);
    genMoveParametersToRegisters(false, methodRef);
    //-#if RVM_WITH_SPECIALIZATION
    asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
    //-#else
    asm.emitCall(spSaveAreaOffset);
    //-#endif
    genPopParametersAndPushReturnValue(false, methodRef);
  }


  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   */
  protected final void emit_invokeinterface(VM_Method methodRef, int count) {
    // (1) Emit dynamic type checking sequence if required to 
    // do so inline.
    if (VM.BuildForIMTInterfaceInvocation || 
	(VM.BuildForITableInterfaceInvocation && 
	 VM.DirectlyIndexedITables)) {
      VM_Method resolvedMethodRef = null;
      try {
	resolvedMethodRef = methodRef.resolveInterfaceMethod(false);
      } catch (VM_ResolutionException e) {
	// actually can't be thrown when we pass false for canLoad.
      }
      if (resolvedMethodRef == null) {
	// might be a ghost ref. Call uncommon case typechecking routine 
	// to deal with this
	asm.emitLtoc(T0, VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());
	asm.emitMTLR(T0);
	asm.emitLIL (T0, methodRef.getDictionaryId());  // dictionaryId of method we are trying to call
	asm.emitL   (T1, (count-1) << 2, SP);           // the "this" object
	VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
	asm.emitCall(spSaveAreaOffset);                 // throw exception, if link error
      } else {
	// normal case.  Not a ghost ref.
	asm.emitLtoc(T0, VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());
	asm.emitMTLR(T0);
	asm.emitLtoc(T0, methodRef.getDeclaringClass().getTibOffset()); // tib of the interface method
	asm.emitL   (T0, TIB_TYPE_INDEX << 2, T0);                   // type of the interface method
	asm.emitL   (T1, (count-1) << 2, SP);                        // the "this" object
	VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
	asm.emitCall(spSaveAreaOffset);                              // throw exception, if link error
      }
    }
    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      int signatureId = VM_ClassLoader.
	findOrCreateInterfaceMethodSignatureId(methodRef.getName(), 
					       methodRef.getDescriptor());
      int offset      = VM_InterfaceInvocation.getIMTOffset(signatureId);
      genMoveParametersToRegisters(true, methodRef); // T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      if (VM.BuildForIndirectIMT) {
	// Load the IMT base into S0
	asm.emitL(S0, TIB_IMT_TIB_INDEX << 2, S0);
      }
      asm.emitL   (S0, offset, S0);                  // the method address
      asm.emitMTLR(S0);
      //-#if RVM_WITH_SPECIALIZATION
      asm.emitSpecializationCallWithHiddenParameter(spSaveAreaOffset, 
						    signatureId, method, 
						    biStart);
      //-#else
      asm.emitCallWithHiddenParameter(spSaveAreaOffset, signatureId);
      //-#endif
    } else if (VM.BuildForITableInterfaceInvocation && 
	       VM.DirectlyIndexedITables && 
	       methodRef.getDeclaringClass().isResolved()) {
      methodRef = methodRef.resolve();
      VM_Class I = methodRef.getDeclaringClass();
      genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      asm.emitL   (S0, TIB_ITABLES_TIB_INDEX << 2, S0); // iTables 
      asm.emitL   (S0, I.getInterfaceId() << 2, S0);  // iTable
      asm.emitL   (S0, VM_InterfaceInvocation.getITableIndex(I, methodRef) << 2, S0); // the method to call
      asm.emitMTLR(S0);
      //-#if RVM_WITH_SPECIALIZATION
      asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
      //-#else
      asm.emitCall(spSaveAreaOffset);
      //-#endif
    } else {
      VM_Class I = methodRef.getDeclaringClass();
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation) {
	// get the index of the method in the Itable
	if (I.isLoaded()) {
	  itableIndex = VM_InterfaceInvocation.getITableIndex(I, methodRef);
	}
      }
      if (itableIndex == -1) {
	// itable index is not known at compile-time.
	// call "invokeInterface" to resolve object + method id into 
	// method address
	int methodRefId = methodRef.getDictionaryId();
	asm.emitLtoc(T0, VM_Entrypoints.invokeInterfaceMethod.getOffset());
	asm.emitMTLR(T0);
	asm.emitL   (T0, (count-1) << 2, SP); // object
	asm.emitLVAL(T1, methodRefId);        // method id
	asm.emitCall(spSaveAreaOffset);       // T0 := resolved method address
	asm.emitMTLR(T0);
	genMoveParametersToRegisters(true, methodRef);
	//-#if RVM_WITH_SPECIALIZATION
	asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
	//-#else
	asm.emitCall(spSaveAreaOffset);
	//-#endif
      } else {
	// itable index is known at compile-time.
	// call "findITable" to resolve object + interface id into 
	// itable address
	asm.emitLtoc(T0, VM_Entrypoints.findItableMethod.getOffset());
	asm.emitMTLR(T0);
	asm.emitL   (T0, (count-1) << 2, SP);     // object
	VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
	asm.emitLVAL(T1, I.getInterfaceId());    // interface id
	asm.emitCall(spSaveAreaOffset);   // T0 := itable reference
	asm.emitL   (T0, itableIndex << 2, T0); // T0 := the method to call
	asm.emitMTLR(T0);
	genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
	//-#if RVM_WITH_SPECIALIZATION
	asm.emitSpecializationCall(spSaveAreaOffset, method, biStart);
	//-#else
	asm.emitCall(spSaveAreaOffset);
	//-#endif
      }
    }
    genPopParametersAndPushReturnValue(true, methodRef);
  }
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param typeRef the VM_Class to instantiate
   */
  protected final void emit_resolved_new(VM_Class typeRef) {
    int instanceSize = typeRef.getInstanceSize();
    int tibOffset = typeRef.getTibOffset();
    asm.emitLtoc(T0, VM_Entrypoints.quickNewScalarMethod.getOffset());
    asm.emitMTLR(T0);
    asm.emitLVAL(T0, instanceSize);
    asm.emitLtoc(T1, tibOffset);
    asm.emitLVAL(T2, typeRef.hasFinalizer()?1:0);
    asm.emitCall(spSaveAreaOffset);
    asm.emitSTU (T0, -4, SP);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param the dictionaryId of the VM_Class to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(int dictionaryId) {
    asm.emitLtoc(T0, VM_Entrypoints.newScalarMethod.getOffset());
    asm.emitMTLR(T0);
    asm.emitLVAL(T0, dictionaryId);
    asm.emitCall(spSaveAreaOffset);
    asm.emitSTU (T0, -4, SP);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_resolved_newarray(VM_Array array) {
    int width      = array.getLogElementSize();
    int tibOffset  = array.getTibOffset();
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(array);
    asm.emitLtoc (T0, VM_Entrypoints.quickNewArrayMethod.getOffset());
    asm.emitMTLR (T0);
    asm.emitL    (T0,  0, SP);                // T0 := number of elements
    asm.emitSLI  (T1, T0, width);             // T1 := number of bytes
    asm.emitCAL  (T1, headerSize, T1);        //    += header bytes
    asm.emitLtoc (T2, tibOffset);             // T2 := tib
    asm.emitCall(spSaveAreaOffset);
    asm.emitST   (T0, 0, SP);
  }

  /**
   * Emit code to dynamically link and allocate an array
   * @param the dictionaryId of the VM_Array to dynamically link & instantiate
   */
  protected final void emit_unresolved_newarray(int dictionaryId) {
    asm.emitLtoc (T0, VM_Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitMTLR (T0);
    asm.emitL    (T0, 0, SP);                // T0 := number of elements
    asm.emitLVAL (T1, dictionaryId);         // T1 := dictionaryId of array
    asm.emitCall(spSaveAreaOffset);
    asm.emitST   (T0, 0, SP);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the VM_Array to instantiate
   * @param dimensions the number of dimensions
   * @param dictionaryId, the dictionaryId of typeRef
   */
  protected final void emit_multianewarray(VM_Array typeRef, int dimensions, int dictionaryId) {
    asm.emitLtoc(T0, VM_Entrypoints.newArrayArrayMethod.getOffset());
    asm.emitMTLR(T0);
    asm.emitLVAL(T0, dimensions);
    asm.emitLVAL(T1, dictionaryId);
    asm.emitSLI (T2, T0,  2); // number of bytes of array dimension args
    asm.emitA   (T2, SP, T2); // offset of word *above* first...
    asm.emitSF  (T2, FP, T2); // ...array dimension arg
    asm.emitCall(spSaveAreaOffset);
    asm.emitSTU (T0, (dimensions - 1)<<2, SP); // pop array dimension args, push return val
  }

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    asm.emitL (T0, 0, SP);
    asm.emitL (T1, VM_ObjectModel.getArrayLengthOffset(), T0);
    if (VM.BuildForRealtimeGC) {
      asm.emitCMPI(T1, 0);
      VM_ForwardReference fr = asm.emitForwardBC(GE);
      asm.emitNEG(T1, T1);
      fr.resolve(asm);
    }
    asm.emitST(T1, 0, SP);
  }

  /**
   * Emit code to implement the athrow bytecode
   */
  protected final void emit_athrow() {
    asm.emitLtoc(T0, VM_Entrypoints.athrowMethod.getOffset());
    asm.emitMTLR(T0);
    asm.emitL   (T0, 0, SP);
    asm.emitCall(spSaveAreaOffset);
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected final void emit_checkcast(VM_Type typeRef, VM_Method target) {
    asm.emitLtoc(T0,  target.getOffset());
    asm.emitMTLR(T0);
    asm.emitL   (T0,  0, SP); // checkcast(obj, klass) consumes obj
    asm.emitLVAL(T1, typeRef.getTibOffset());
    asm.emitCall(spSaveAreaOffset);               // but obj remains on stack afterwords
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected final void emit_instanceof(VM_Type typeRef, VM_Method target) {
    asm.emitLtoc(T0,  target.getOffset());            
    asm.emitMTLR(T0);
    asm.emitL   (T0, 0, SP);
    asm.emitLVAL(T1, typeRef.getTibOffset());
    asm.emitCall(spSaveAreaOffset);
    asm.emitST  (T0, 0, SP);
  }

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected final void emit_monitorenter() {
    asm.emitL   (S0, VM_Entrypoints.lockMethod.getOffset(), JTOC);
    asm.emitMTLR(S0);
    asm.emitCall(spSaveAreaOffset);
    asm.emitCAL (SP, 4, SP);
  }

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected final void emit_monitorexit() {
    asm.emitL     (T0, 0, SP);
    asm.emitL   (S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);
    asm.emitMTLR(S0);
    asm.emitCall(spSaveAreaOffset);
    asm.emitCAL (SP, 4, SP);
  }

  
  // offset of i-th local variable with respect to FP
  private int localOffset (int i) {
    int offset = firstLocalOffset - (i << 2);
    if (VM.VerifyAssertions) VM._assert(offset < 0x8000);
    return offset;
  }

  private void emitDynamicLinkingSequence(VM_Field fieldRef) {
    emitDynamicLinkingSequence(fieldRef.getDictionaryId(), 
			       VM_Entrypoints.fieldOffsetsField.getOffset(),
			       VM_Entrypoints.resolveFieldMethod.getOffset());
  }

  private void emitDynamicLinkingSequence(VM_Method methodRef) {
    emitDynamicLinkingSequence(methodRef.getDictionaryId(), 
			       VM_Entrypoints.methodOffsetsField.getOffset(),
			       VM_Entrypoints.resolveMethodMethod.getOffset());
  }

  private void emitDynamicLinkingSequence(int memberId,
					  int tableOffset,
					  int resolverOffset) {
    int label = asm.getMachineCodeIndex();

    // load offset table
    asm.emitLtoc (T2, tableOffset);
    asm.emitLoffset(T2, T2, memberId << 2);

    // test for non-zero offset and branch around call to resolver
    asm.emitCMPI (T2, 0);				      // T2 ?= 0, is field's class loaded?
    VM_ForwardReference fr1 = asm.emitForwardBC(NE);
    asm.emitLtoc (T0, resolverOffset);
    asm.emitMTLR (T0);
    asm.emitLVAL (T0, memberId);                              // dictionaryId of member we are resolving
    asm.emitCall (spSaveAreaOffset);			      // link; will throw exception if link error
    asm.emitB    (label);                       	      // go back and try again
    fr1.resolve(asm);
  }

  // Load/Store assist
  private void aloadSetup (int logSize) {
    asm.emitL   (T1,  4, SP);                    // T1 is array ref
    asm.emitL   (T0,  0, SP);                    // T0 is array index
    asm.emitL   (T2,  VM_ObjectModel.getArrayLengthOffset(), T1);  // T2 is array length
    if (logSize >= 0)
	emitSegmentedArrayAccess(asm, T1, T0, T2, logSize);
    if (VM.BuildForRealtimeGC || !options.ANNOTATIONS ||
	!method.queryAnnotationForBytecode(biStart, VM_Method.annotationBoundsCheck)) {
      asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
    }
  }

  private void astoreSetup (int logSize) {
    asm.emitL   (T1,  8, SP);                    // T1 is array ref
    asm.emitL   (T0,  4, SP);                    // T0 is array index
    asm.emitL   (T2,  VM_ObjectModel.getArrayLengthOffset(), T1);  // T2 is array length
    asm.emitL   (T3,  0, SP);                    // T3 is value to store
    if (logSize >= 0)
	emitSegmentedArrayAccess(asm, T1, T0, T2, logSize);
    if ( VM.BuildForRealtimeGC || !options.ANNOTATIONS ||
	 !method.queryAnnotationForBytecode(biStart,
					VM_Method.annotationBoundsCheck)) {
      asm.emitTLLE(T2, T0);      // trap if index < 0 or index >= length
    }
  }

  private void astoreLong () {
    asm.emitL    (T1, 12, SP);                    // T1 is array ref
    asm.emitL    (T0,  8, SP);                    // T0 is array index
    asm.emitL    (T2,  VM_ObjectModel.getArrayLengthOffset(), T1);  // T2 is array length
    asm.emitLFD  (F0,  0, SP);                    // F0 is value to store
    emitSegmentedArrayAccess(asm, T1, T0, T2, 3);
    if ( VM.BuildForRealtimeGC || !options.ANNOTATIONS ||
	 !method.queryAnnotationForBytecode(biStart,
					VM_Method.annotationBoundsCheck)) {
      asm.emitTLLE(T2, T0);     // trap if index < 0 or index >= length
    }
    asm.emitSLI  (T0, T0,  3);  // convert double index to byte index
    asm.emitSTFDX(F0, T0, T1);  // store double value in array
    asm.emitCAL  (SP, 16, SP);  // complete 3 pops (1st is 2 words)
  }

  private static void emitSegmentedArrayAccess (VM_Assembler asm, int Tarr, int Tidx, int Tlen, int shift) {
    if (VM.BuildForRealtimeGC) {
    //-#if RVM_WITH_REALTIME_GC
      VM_SegmentedArray.emitSegmentedArrayAccess(asm, Tarr, Tidx, Tlen, shift);
    //-#endif
    }
  }  

  // Emit code to buy a stackframe, store incoming parameters, 
  // and acquire method synchronization lock.
  //
  private void genPrologue () {
    if (klass.isBridgeFromNative()) {
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, method);
    }

    // Generate trap if new frame would cross guard page.
    //
    if (isInterruptible) {
      asm.emitStackOverflowCheck(frameSize);                            // clobbers R0, S0
    }

    // Buy frame.
    //
    asm.emitSTU (FP, -frameSize, FP); // save old FP & buy new frame (trap if new frame below guard page) !!TODO: handle frames larger than 32k when addressing local variables, etc.
    
    // If this is a "dynamic bridge" method, then save all registers except GPR0, FPR0, JTOC, and FP.
    //
    if (klass.isDynamicBridge()) {
      int offset = frameSize;
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
         asm.emitSTFD (i, offset -= 8, FP);
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
         asm.emitST (i, offset -= 4, FP);
    }
    
    // Fill in frame header.
    //
    asm.emitLVAL(S0, compiledMethod.getId());
    asm.emitMFLR(0);
    asm.emitST  (S0, STACKFRAME_METHOD_ID_OFFSET, FP);                   // save compiled method id
    asm.emitST  (0, frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // save LR !!TODO: handle discontiguous stacks when saving return address
    
    // Setup expression stack and locals.
    //
    asm.emitCAL (SP, emptyStackOffset, FP);                              // setup expression stack
    genMoveParametersToLocals();                                                   // move parameters to locals
   
    // Perform a thread switch if so requested.
    //
    genThreadSwitchTest(VM_Thread.PROLOGUE); //           (VM_BaselineExceptionDeliverer WONT release the lock (for synchronized methods) during prologue code)

    // Acquire method syncronization lock.  (VM_BaselineExceptionDeliverer will release the lock (for synchronized methods) after  prologue code)
    //
    if (method.isSynchronized()) 
      genSynchronizedMethodPrologue();

    // Mark start of code for which source lines exist (for jdp debugger breakpointing).
    //
    asm.emitSENTINAL(); 
  }

  // Emit code to acquire method synchronization lock.
  //
  private void genSynchronizedMethodPrologue() {
    if (method.isStatic()) { // put java.lang.Class object for VM_Type into T0
      if (VM.writingBootImage) {
	VM.deferClassObjectCreation(klass);
      } else {
	klass.getClassForType();
      }
      int tibOffset = klass.getTibOffset();
      asm.emitLtoc(T0, tibOffset);
      asm.emitL   (T0, 0, T0);
      asm.emitL   (T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { // first local is "this" pointer
      asm.emitL(T0, localOffset(0), FP);
    }
    asm.emitL     (S0, VM_Entrypoints.lockMethod.getOffset(), JTOC); // call out...
    asm.emitMTLR  (S0);                                  // ...of line lock
    asm.emitCall(spSaveAreaOffset);
    lockOffset = 4*(asm.getMachineCodeIndex() - 1); // after this instruction, the method has the monitor
  }

  // Emit code to release method synchronization lock.
  //
  private void genSynchronizedMethodEpilogue () {
    if (method.isStatic()) { // put java.lang.Class for VM_Type into T0
      int tibOffset = klass.getTibOffset();
      asm.emitLtoc(T0, tibOffset);
      asm.emitL   (T0, 0, T0);
      asm.emitL   (T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { // first local is "this" pointer
      asm.emitL(T0, localOffset(0), FP); //!!TODO: think about this - can anybody store into local 0 (ie. change the value of "this")?
    }
    asm.emitL   (S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);  // call out...
    asm.emitMTLR(S0);                                     // ...of line lock
    asm.emitCall(spSaveAreaOffset);
  }
    
  // Emit code to discard stackframe and return to caller.
  //
  private void genEpilogue () {
    if (klass.isDynamicBridge()) {// Restore non-volatile registers.
      // we never return from a DynamicBridge frame
      asm.emitTWI(-1);
    } else {
      if (frameSize <= 0x8000) {
	asm.emitCAL(FP, frameSize, FP); // discard current frame
      } else {
	asm.emitL(FP, 0, FP);           // discard current frame
      }
      asm.emitL   (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
      asm.emitMTLR(S0);
      asm.emitBLR (); // branch always, through link register
    }
  }


  /**
   * Emit the code for a bytecode level conditional branch
   * @param cc the condition code to branch on
   * @param bTarget the target bytecode index
   */
  private void genCondBranch(int cc, int bTarget) {
    if (options.EDGE_COUNTERS) {
      // Allocate 2 counters, taken and not taken
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;

      // Load counter array for this method
      asm.emitLtoc (T0, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLoffset(T0, T0, getEdgeCounterOffset());

      // Flip conditions so we can jump over the increment of the taken counter.
      VM_ForwardReference fr = asm.emitForwardBC(asm.flipCode(cc));

      // Increment taken counter & jump to target
      incEdgeCounter(T0, T1, entry+VM_EdgeCounts.TAKEN);
      asm.emitB(bytecodeMap[bTarget], bTarget);

      // Not taken
      fr.resolve(asm);
      incEdgeCounter(T0, T1, entry+VM_EdgeCounts.NOT_TAKEN);
    } else {
      if (bTarget - SHORT_FORWARD_LIMIT < biStart) {
	asm.emitShortBC(cc, bytecodeMap[bTarget], bTarget);
      } else {
	asm.emitBC(cc, bytecodeMap[bTarget], bTarget);
      }
    }
  }

  /**
   * increment an edge counter.  
   * @param counters register containing base of counter array
   * @param scratch scratch register
   * @param counterIdx index of counter to increment
   */
  private final void incEdgeCounter(int counters, int scratch, int counterIdx) {
    asm.emitL      (scratch, counterIdx<<2, counters);
    asm.emitCAL    (scratch, 1, scratch);
    asm.emitRLWINM (scratch, scratch, 0, 1, 31);
    asm.emitST     (scratch, counterIdx<<2, counters);
  }

  private final void incEdgeCounterIdx(int counters, int scratch, int base, int counterIdx) {
    asm.emitCAL     (counters, base<<2, counters);
    asm.emitLX      (scratch, counterIdx, counters);
    asm.emitCAL     (scratch, 1, scratch);
    asm.emitRLWINM  (scratch, scratch, 0, 1, 31);
    asm.emitSTX     (scratch, counterIdx, counters);
  }    

  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private void genThreadSwitchTest (int whereFrom) {
    if (isInterruptible) {
      VM_ForwardReference fr;
      // alternate ways of setting the thread switch bit
      if (VM.BuildForDeterministicThreadSwitching) { // set THREAD_SWITCH_BIT every N method calls
	
	// Decrement counter
	asm.emitL  (T2, VM_Entrypoints.deterministicThreadSwitchCountField.getOffset(), PROCESSOR_REGISTER);
	asm.emitCAL(T2, -1, T2);  // decrement it
	asm.emitST (T2, VM_Entrypoints.deterministicThreadSwitchCountField.getOffset(), PROCESSOR_REGISTER);
        
	// If counter reaches zero, set threadswitch bit
	asm.emitCMPI(T2, 0);
	fr = asm.emitForwardBC(GT);
	asm.emitCRORC(THREAD_SWITCH_BIT, 0, 0); // set thread switch bit
      } else if (!VM.BuildForThreadSwitchUsingControlRegisterBit) {
	asm.emitL(S0, VM_Entrypoints.threadSwitchRequestedField.getOffset(), PROCESSOR_REGISTER);
	asm.emitCMPI(THREAD_SWITCH_REGISTER, S0, 0); // set THREAD_SWITCH_SWITCH_REGISTER, S0, 0); // set THREAD_SWITCH_BIT in CR
	fr = asm.emitBNTS(); // skip, unless THREAD_SWITCH_BIT in CR is set
      } else { // else rely on the timer interrupt to set the THREAD_SWITCH_BIT
	fr = asm.emitBNTS(); // skip, unless THREAD_SWITCH_BIT in CR is set
      }
      if (whereFrom == VM_Thread.PROLOGUE) {
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromPrologueMethod.getOffset(), JTOC);
      } else if (whereFrom == VM_Thread.BACKEDGE) {
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromBackedgeMethod.getOffset(), JTOC);
      } else { // EPILOGUE
	asm.emitL   (S0, VM_Entrypoints.threadSwitchFromEpilogueMethod.getOffset(), JTOC);
      }
      asm.emitMTLR(S0);
      asm.emitCall(spSaveAreaOffset);
      fr.resolve(asm);
    }
  }

  // parameter stuff //

  // store parameters from registers into local variables of current method.
  private void genMoveParametersToLocals () {
    // AIX computation will differ
    spillOffset = getFrameSize(method) + STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int localIndex = 0;
    if (!method.isStatic()) {
      if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex++);
      else asm.emitST(gp++, localOffset(localIndex++), FP);
    }
    VM_Type [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++, localIndex++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
        if (gp > LAST_VOLATILE_GPR) genUnspillDoubleword(localIndex++);
	else {
	  asm.emitST(gp++, localOffset(localIndex + 1), FP); // lo mem := lo register (== hi word)
	  if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex);
	  else asm.emitST(gp++, localOffset(localIndex), FP);// hi mem := hi register (== lo word)
	  localIndex += 1;
	}
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillWord(localIndex);
	else asm.emitSTFS(fp++, localOffset(localIndex), FP);
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillDoubleword(localIndex++);
	else asm.emitSTFD(fp++, localOffset(localIndex++) - 4, FP);
      } else { // t is object, int, short, char, byte, or boolean
        if (gp > LAST_VOLATILE_GPR) genUnspillWord(localIndex);
	else asm.emitST(gp++, localOffset(localIndex), FP);
      }
    }
  }

  // load parameters into registers before calling method "m".
  private void genMoveParametersToRegisters (boolean hasImplicitThisArg, VM_Method m) {
    // AIX computation will differ
    spillOffset = STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int stackOffset = m.getParameterWords()<<2;
    if (hasImplicitThisArg) {
      if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset);
      else asm.emitL(gp++, stackOffset, SP);
    }
    VM_Type [] types = m.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
	stackOffset -= 8;
        if (gp > LAST_VOLATILE_GPR) genSpillDoubleword(stackOffset);
	else {
	  asm.emitL(gp++, stackOffset,   SP);       // lo register := lo mem (== hi order word)
	  if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset+4);
	  else asm.emitL(gp++, stackOffset+4, SP);  // hi register := hi mem (== lo order word)
	}
      } else if (t.isFloatType()) {
	stackOffset -= 4;
        if (fp > LAST_VOLATILE_FPR) genSpillWord(stackOffset);
	else asm.emitLFS(fp++, stackOffset, SP);
      } else if (t.isDoubleType()) {
	stackOffset -= 8;
        if (fp > LAST_VOLATILE_FPR) genSpillDoubleword(stackOffset);
	else asm.emitLFD(fp++, stackOffset, SP);
      } else { // t is object, int, short, char, byte, or boolean
	stackOffset -= 4;
        if (gp > LAST_VOLATILE_GPR) genSpillWord(stackOffset);
	else asm.emitL(gp++, stackOffset, SP);
      }
    }
    if (VM.VerifyAssertions) VM._assert(stackOffset == 0);
  }

  // push return value of method "m" from register to operand stack.
  private void genPopParametersAndPushReturnValue (boolean hasImplicitThisArg, VM_Method m) {
    VM_Type t = m.getReturnType();
    int parameterSize = 
      (m.getParameterWords() + (hasImplicitThisArg ? 1 : 0) ) << 2;
    if (t.isVoidType()) {
      if (0 < parameterSize) asm.emitCAL(SP, parameterSize, SP);
    } else if (t.isLongType()) {
      asm.emitST (FIRST_VOLATILE_GPR+1, parameterSize-4, SP); // hi mem := hi register (== lo word)
      asm.emitSTU(FIRST_VOLATILE_GPR,   parameterSize-8, SP); // lo mem := lo register (== hi word)
    } else if (t.isFloatType()) {
      asm.emitSTFSU(FIRST_VOLATILE_FPR, parameterSize-4, SP);
    } else if (t.isDoubleType()) {
      asm.emitSTFDU(FIRST_VOLATILE_FPR, parameterSize-8, SP);
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitSTU(FIRST_VOLATILE_GPR, parameterSize-4, SP);
    }
  }

  private void genSpillWord (int stackOffset) {
     asm.emitL (0, stackOffset, SP);
     asm.emitST(0, spillOffset, FP);
     spillOffset += 4;
  }
     
  private void genSpillDoubleword (int stackOffset) {
     asm.emitLFD (0, stackOffset, SP);
     asm.emitSTFD(0, spillOffset, FP);
     spillOffset += 8;
  }
               
  private void genUnspillWord (int localIndex) {
     asm.emitL (0, spillOffset, FP);
     asm.emitST(0, localOffset(localIndex), FP);
     spillOffset += 4;
  }
                      
  private void genUnspillDoubleword (int localIndex) {
     asm.emitLFD (0, spillOffset, FP);
     asm.emitSTFD(0, localOffset(localIndex) - 4, FP);
     spillOffset += 8;
  }
}
