/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.jni.VM_JNICompiler;

/**
 * VM_Compiler is the baseline compiler class for the IA32 architecture.
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 * @author Dave Grove
 * @author Perry Cheng
 */
public class VM_Compiler extends VM_BaselineCompiler implements VM_BaselineConstants, VM_SizeConstants {

  private final int parameterWords;
  private int firstLocalOffset;

  /**
   * Create a VM_Compiler object for the compilation of method.
   */
  VM_Compiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    stackHeights = new int[bcodes.length()];
    parameterWords = method.getParameterWords() + (method.isStatic() ? 0 : 1); // add 1 for this pointer
  }

  /**
   * The last true local
   */
  public static int getEmptyStackOffset (VM_NormalMethod m) {
    return getFirstLocalOffset(m) - (m.getLocalWords()<<LG_WORDSIZE) + WORDSIZE;
  }

  /**
   * This is misnamed.  It should be getFirstParameterOffset.
   * It will not work as a base to access true locals.
   * TODO!! make sure it is not being used incorrectly
   */
  public static int getFirstLocalOffset (VM_NormalMethod method) {
    if (method.getDeclaringClass().isBridgeFromNative())
      return STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    else
      return STACKFRAME_BODY_OFFSET - (SAVED_GPRS << LG_WORDSIZE);
  }
  

  /*
   * implementation of abstract methods of VM_BaselineCompiler
   */

  /*
   * Misc routines not directly tied to a particular bytecode
   */

  /**
   * Notify VM_Compiler that we are starting code gen for the bytecode biStart
   */
  protected final void starting_bytecode() {}

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
   * @param body        method magic occurred in
   * @param magicMethod desired magic
   */
  protected final boolean emit_Magic(VM_MethodReference magicMethod) {
    return genMagic(magicMethod);
  }


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected final void emit_aconst_null() {
    asm.emitPUSH_Imm(0);
  }

  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected final void emit_iconst(int val) {
    asm.emitPUSH_Imm(val);
  }

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected final void emit_lconst(int val) {
    asm.emitPUSH_Imm(0);    // high part
    asm.emitPUSH_Imm(val);  //  low part
  }

  /**
   * Emit code to load 0.0f
   */
  protected final void emit_fconst_0() {
    asm.emitPUSH_Imm(0);
  }

  /**
   * Emit code to load 1.0f
   */
  protected final void emit_fconst_1() {
    asm.emitPUSH_Imm(0x3f800000);
  }

  /**
   * Emit code to load 2.0f
   */
  protected final void emit_fconst_2() {
    asm.emitPUSH_Imm(0x40000000);
  }

  /**
   * Emit code to load 0.0d
   */
  protected final void emit_dconst_0() {
    asm.emitPUSH_Imm(0x00000000);
    asm.emitPUSH_Imm(0x00000000);
  }

  /**
   * Emit code to load 1.0d
   */
  protected final void emit_dconst_1() {
    asm.emitPUSH_Imm(0x3ff00000);
    asm.emitPUSH_Imm(0x00000000);
  }

  /**
   * Emit code to load a 32 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc(int offset) {
    asm.emitPUSH_RegDisp(JTOC, offset);   
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc2(int offset) {
    asm.emitPUSH_RegDisp(JTOC, offset+4); // high 32 bits 
    asm.emitPUSH_RegDisp(JTOC, offset);   // low 32 bits
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
    asm.emitPUSH_RegDisp(ESP,offset);
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected final void emit_lload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset); // high part
    asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected final void emit_fload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp (ESP, offset);
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected final void emit_dload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset); // high part
    asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected final void emit_aload(int index) {
    int offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset);
  }


  /*
   * storing local variables
   */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected final void emit_istore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset); 
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected final void emit_lstore(int index) {
    int offset = localOffset(index+1) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset); // high part
    asm.emitPOP_RegDisp(ESP, offset); //  low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected final void emit_fstore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset);
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected final void emit_dstore(int index) {
    int offset = localOffset(index+1) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset); // high part
    asm.emitPOP_RegDisp(ESP, offset); //  low part (ESP has moved by 4!!)
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected final void emit_astore(int index) {
    int offset = localOffset(index) - 4; // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp (ESP, offset);
  }


  /*
   * array loads
   */


  /**
   * Emit code to load from an int array
   */
  protected final void emit_iaload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired int array element
  }

  /**
   * Emit code to load from a long array
   */
  protected final void emit_laload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of desired long array element
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of desired long array element
  }

  /**
   * Emit code to load from a float array
   */
  protected final void emit_faload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired float array element
  }

  /**
   * Emit code to load from a double array
   */
  protected final void emit_daload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);             // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, WORDSIZE); // load high part of double
    asm.emitPUSH_RegIdx(S0, T0, asm.LONG, 0);        // load low part of double
  }

  /**
   * Emit code to load from a reference array
   */
  protected final void emit_aaload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);       // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);       // S0 is the array ref
    genBoundsCheck(asm, T0, S0);              // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);      // complete popping the 2 args
    asm.emitPUSH_RegIdx(S0, T0, asm.WORD, 0); // push desired object array element
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected final void emit_baload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                     // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                     // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // complete popping the 2 args
    asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, asm.BYTE, 0); // load byte and sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                   // push sign extended byte onto stack
  }

  /**
   * Emit code to load from a char array
   */
  protected final void emit_caload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
    asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword without sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                    // push char onto stack
  }

  /**
   * Emit code to load from a short array
   */
  protected final void emit_saload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                      // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 4);                      // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                             // T0 is index, S0 is address of array
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                     // complete popping the 2 args
    asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, asm.SHORT, 0); // load halfword sign extend to a 32 bit word
    asm.emitPUSH_Reg(T1);                                    // push sign extended short onto stack
  }


  /*
   * array stores
   */


  /**
   * Emit code to store to an int array
   */
  protected final void emit_iastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the int value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a long array
   */
  protected final void emit_lastore() {
    VM_Barriers.compileModifyCheck(asm, 12);
    asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitPOP_Reg(T1);                                    // low part of long value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
    asm.emitPOP_Reg(T1);                                    // high part of long value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
  }

  /**
   * Emit code to store to a float array
   */
  protected final void emit_fastore() {
    VM_Barriers.compileModifyCheck(asm, 12);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the float value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a double array
   */
  protected final void emit_dastore() {
    VM_Barriers.compileModifyCheck(asm, 12);
    asm.emitMOV_Reg_RegDisp(T0, SP, 8);                     // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 12);                    // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                            // T0 is index, S0 is address of array
    asm.emitPOP_Reg(T1);                                    // low part of double value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, 0, T1);        // [S0 + T0<<3 + 0] <- T1 store low part into array i.e.  
    asm.emitPOP_Reg(T1);                                    // high part of double value
    asm.emitMOV_RegIdx_Reg(S0, T0, asm.LONG, WORDSIZE, T1); // [S0 + T0<<3 + 4] <- T1 store high part into array i.e. 
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2);                    // remove index and ref from the stack
  }

  /**
   * Emit code to store to a reference array
   */
  protected final void emit_aastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitPUSH_RegDisp(SP, 2<<LG_WORDSIZE);        // duplicate array ref
    asm.emitPUSH_RegDisp(SP, 1<<LG_WORDSIZE);        // duplicate object value
    genParameterRegisterLoad(2);                     // pass 2 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkstoreMethod.getOffset()); // checkstore(array ref, value)
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);              // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);              // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                     // T0 is index, S0 is address of array
    if (MM_Interface.NEEDS_WRITE_BARRIER) 
      VM_Barriers.compileArrayStoreBarrier(asm);
    else {
      asm.emitMOV_Reg_RegDisp(T1, SP, 0);              // T1 is the object value
      asm.emitMOV_RegIdx_Reg(S0, T0, asm.WORD, 0, T1); // [S0 + T0<<2] <- T1
    }
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);             // complete popping the 3 args
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected final void emit_bastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the byte value
    asm.emitMOV_RegIdx_Reg_Byte(S0, T0, asm.BYTE, 0, T1); // [S0 + T0<<2] <- T1
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }

  /**
   * Emit code to store to a char array
   */
  protected final void emit_castore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the char value
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }

  /**
   * Emit code to store to a short array
   */
  protected final void emit_sastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, 4);                   // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, 8);                   // S0 is the array ref
    genBoundsCheck(asm, T0, S0);                          // T0 is index, S0 is address of array
    asm.emitMOV_Reg_RegDisp(T1, SP, 0);                   // T1 is the short value
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, asm.SHORT, 0, T1);// store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                  // complete popping the 3 args
  }


  /*
   * expression stack manipulation
   */


  /**
   * Emit code to implement the pop bytecode
   */
  protected final void emit_pop() {
    asm.emitPOP_Reg(T0);
  }

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected final void emit_pop2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(T0);
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    asm.emitMOV_Reg_RegInd (T0, SP);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected final void emit_dup_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected final void emit_dup_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected final void emit_dup2() {
    asm.emitMOV_Reg_RegDisp (T0, SP, 4);
    asm.emitMOV_Reg_RegInd (S0, SP);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
  }

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected final void emit_dup2_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected final void emit_dup2_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(JTOC);                  // JTOC is scratch register
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(JTOC);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    // restore JTOC register
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
  }


  /*
   * int ALU
   */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected final void emit_iadd() {
    asm.emitPOP_Reg(T0);
    asm.emitADD_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the isub bytecode
   */
  protected final void emit_isub() {
    asm.emitPOP_Reg(T0);
    asm.emitSUB_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the imul bytecode
   */
  protected final void emit_imul() {
    asm.emitPOP_Reg (T0);
    asm.emitIMUL2_Reg_RegInd(T0, SP);
    asm.emitMOV_RegInd_Reg (SP, T0);
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    asm.emitMOV_Reg_RegDisp(ECX, SP, 0); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, 4); // EAX is dividend
    asm.emitCDQ ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2); // complete popping the 2 values
    asm.emitPUSH_Reg(EAX);               // push result
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    asm.emitMOV_Reg_RegDisp(ECX, SP, 0); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, 4); // EAX is dividend
    asm.emitCDQ ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE*2); // complete popping the 2 values
    asm.emitPUSH_Reg(EDX);               // push remainder
  }

  /**
   * Emit code to implement the ineg bytecode
   */
  protected final void emit_ineg() {
    asm.emitNEG_RegInd(SP); // [SP] <- -[SP]
  }

  /**
   * Emit code to implement the ishl bytecode
   */
  protected final void emit_ishl() {
    asm.emitPOP_Reg(ECX);
    asm.emitSHL_RegInd_Reg(SP, ECX);   
  }

  /**
   * Emit code to implement the ishr bytecode
   */
  protected final void emit_ishr() {
    asm.emitPOP_Reg (ECX);
    asm.emitSAR_RegInd_Reg (SP, ECX);  
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    asm.emitPOP_Reg (ECX);
    asm.emitSHR_RegInd_Reg(SP, ECX); 
  }

  /**
   * Emit code to implement the iand bytecode
   */
  protected final void emit_iand() {
    asm.emitPOP_Reg(T0);
    asm.emitAND_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the ior bytecode
   */
  protected final void emit_ior() {
    asm.emitPOP_Reg(T0);
    asm.emitOR_RegInd_Reg (SP, T0);
  }

  /**
   * Emit code to implement the ixor bytecode
   */
  protected final void emit_ixor() {
    asm.emitPOP_Reg(T0);
    asm.emitXOR_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected final void emit_iinc(int index, int val) {
    int offset = localOffset(index);
    asm.emitADD_RegDisp_Imm(ESP, offset, val);
  }


  /*
   * long ALU
   */


  /**
   * Emit code to implement the ladd bytecode
   */
  protected final void emit_ladd() {
    asm.emitPOP_Reg(T0);                 // the low half of one long
    asm.emitPOP_Reg(S0);                 // the high half
    asm.emitADD_RegInd_Reg(SP, T0);          // add low halves
    asm.emitADC_RegDisp_Reg(SP, WORDSIZE, S0);   // add high halves with carry
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    asm.emitPOP_Reg(T0);                 // the low half of one long
    asm.emitPOP_Reg(S0);                 // the high half
    asm.emitSUB_RegInd_Reg(SP, T0);          // subtract low halves
    asm.emitSBB_RegDisp_Reg(SP, WORDSIZE, S0);   // subtract high halves with borrow
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    // 0: JTOC is used as scratch registers (see 14)
    // 1: load value1.low temp0, i.e., save value1.low
    // 2: eax <- temp0 eax is value1.low
    // 3: edx:eax <- eax * value2.low (product of the two low halves)
    // 4: store eax which is  result.low into place --> value1.low is destroyed
    // 5: temp1 <- edx which is the carry of the product of the low halves
    // aex and edx now free of results
    // 6: aex <- temp0 which is still value1.low
    // 7: pop into aex aex <- value2.low  --> value2.low is sort of destroyed
    // 8: edx:eax <- eax * value1.hi  (value2.low * value1.hi)
    // 9: temp1 += aex
    // 10: pop into eax; eax <- value2.hi -> value2.hi is sort of destroyed
    // 11: edx:eax <- eax * temp0 (value2.hi * value1.low)
    // 12: temp1 += eax  temp1 is now result.hi
    // 13: store result.hi
    // 14: restore JTOC
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);
    if (VM.VerifyAssertions) VM._assert(S0 != EDX);
    asm.emitMOV_Reg_RegDisp (JTOC, SP, 8);          // step 1: JTOC is temp0
    asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 2
    asm.emitMUL_Reg_RegInd(EAX, SP);    // step 3
    asm.emitMOV_RegDisp_Reg (SP, 8, EAX);           // step 4
    asm.emitMOV_Reg_Reg (S0, EDX);              // step 5: S0 is temp1
    asm.emitMOV_Reg_Reg (EAX, JTOC);            // step 6
    asm.emitPOP_Reg (EAX);                  // step 7: SP changed!
    asm.emitIMUL1_Reg_RegDisp(EAX, SP, 8);// step 8
    asm.emitADD_Reg_Reg (S0, EAX);      // step 9
    asm.emitPOP_Reg (EAX);                  // step 10: SP changed!
    asm.emitIMUL1_Reg_Reg(EAX, JTOC);    // step 11
    asm.emitADD_Reg_Reg (S0, EAX);      // step 12
    asm.emitMOV_RegDisp_Reg (SP, 4, S0);            // step 13
    // restore JTOC register
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);
    asm.emitOR_Reg_RegDisp(T0, SP, 4);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongDivideIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);
    asm.emitOR_Reg_RegDisp(T0, SP, 4);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+20);
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongRemainderIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    asm.emitNEG_RegDisp(SP, 4);    // [SP+4] <- -[SP+4] or high <- -high
    asm.emitNEG_RegInd(SP);    // [SP] <- -[SP] or low <- -low
    asm.emitSBB_RegDisp_Imm(SP, 4, 0); // [SP+4] += borrow or high += borrow
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    if (VM.VerifyAssertions) VM._assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert (ECX != T1);
    if (VM.VerifyAssertions) VM._assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: pop high half into T1
    // 4: ECX <- JTOC, copy the shift count
    // 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 6: branch to step 12 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 8: T1 <- T0, or replace the high half with the low half.  This accounts for the 32 bit shift
    // 9: shift T1 left by ECX bits
    // 10: T0 <- 0
    // 11: branch to step 14
    // 12: shift left double from T0 into T1 by ECX bits.  T0 is unaltered
    // 13: shift left T0, the low half, also by ECX bits
    // 14: push high half from T1
    // 15: push the low half from T0
    // 16: restore the JTOC
    asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
    asm.emitPOP_Reg (T0);                   // pop low half 
    asm.emitPOP_Reg (T1);                   // pop high half
    asm.emitMOV_Reg_Reg (ECX, JTOC);
    asm.emitAND_Reg_Imm (JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitMOV_Reg_Reg (T1, T0);               // low replaces high
    asm.emitSHL_Reg_Reg (T1, ECX);
    asm.emitXOR_Reg_Reg (T0, T0);
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitSHLD_Reg_Reg_Reg(T1, T0, ECX);          // shift high half (step 12)
    asm.emitSHL_Reg_Reg (T0, ECX);                   // shift low half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 14)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    if (VM.VerifyAssertions) VM._assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert (ECX != T1);
    if (VM.VerifyAssertions) VM._assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: pop high half into T1
    // 4: ECX <- JTOC, copy the shift count
    // 5: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 6: branch to step 13 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 7: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 8: T0 <- T1, or replace the low half with the high half.  This accounts for the 32 bit shift
    // 9: shift T0 right arithmetic by ECX bits
    // 10: ECX <- 31
    // 11: shift T1 right arithmetic by ECX=31 bits, thus exending the sigh
    // 12: branch to step 15
    // 13: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
    // 14: shift right arithmetic T1, the high half, also by ECX bits
    // 15: push high half from T1
    // 16: push the low half from T0
    // 17: restore JTOC
    asm.emitPOP_Reg (JTOC);                 // original shift amount 6 bits
    asm.emitPOP_Reg (T0);                   // pop low half 
    asm.emitPOP_Reg (T1);                   // pop high
    asm.emitMOV_Reg_Reg (ECX, JTOC);
    asm.emitAND_Reg_Imm (JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitMOV_Reg_Reg (T0, T1);               // replace low with high
    asm.emitSAR_Reg_Reg (T0, ECX);                   // and shift it
    asm.emitMOV_Reg_Imm (ECX, 31);
    asm.emitSAR_Reg_Reg (T1, ECX);                   // set high half
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half (step 13)
    asm.emitSAR_Reg_Reg (T1, ECX);                   // shift high half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 15)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    if (VM.VerifyAssertions) VM._assert (ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert (ECX != T1);
    if (VM.VerifyAssertions) VM._assert (ECX != JTOC);
    // 1: pop shift amount into JTOC (JTOC must be restored at the end)
    // 2: pop low half into T0
    // 3: ECX <- JTOC, copy the shift count
    // 4: JTOC <- JTOC & 32 --> if 0 then shift amount is less than 32
    // 5: branch to step 11 if results is zero
    // the result is not zero --> the shift amount is greater than 32
    // 6: ECX <- ECX XOR JTOC   --> ECX is orginal shift amount minus 32
    // 7: pop high half into T0 replace the low half with the high 
    //        half.  This accounts for the 32 bit shift
    // 8: shift T0 right logical by ECX bits
    // 9: T1 <- 0                        T1 is the high half
    // 10: branch to step 14
    // 11: pop high half into T1
    // 12: shift right double from T1 into T0 by ECX bits.  T1 is unaltered
    // 13: shift right logical T1, the high half, also by ECX bits
    // 14: push high half from T1
    // 15: push the low half from T0
    // 16: restore JTOC
    asm.emitPOP_Reg(JTOC);                // original shift amount 6 bits
    asm.emitPOP_Reg(T0);                  // pop low half 
    asm.emitMOV_Reg_Reg(ECX, JTOC);
    asm.emitAND_Reg_Imm(JTOC, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(asm.EQ);
    asm.emitXOR_Reg_Reg (ECX, JTOC);
    asm.emitPOP_Reg (T0);                   // replace low with high
    asm.emitSHR_Reg_Reg (T0, ECX);      // and shift it (count - 32)
    asm.emitXOR_Reg_Reg (T1, T1);               // high <- 0
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPOP_Reg (T1);                   // high half (step 11)
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);          // shift low half
    asm.emitSHR_Reg_Reg (T1, ECX);                   // shift high half
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half (step 14)
    asm.emitPUSH_Reg(T0);                   // push low half
    // restore JTOC
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitAND_RegInd_Reg(SP, T0);
    asm.emitAND_RegDisp_Reg(SP, 4, S0);
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitOR_RegInd_Reg(SP, T0);
    asm.emitOR_RegDisp_Reg(SP, 4, S0);
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitXOR_RegInd_Reg(SP, T0);
    asm.emitXOR_RegDisp_Reg(SP, 4, S0);
  }


  /*
   * float ALU
   */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFADD_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack += value1
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
    asm.emitFSUB_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack -= value2
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFMUL_Reg_RegDisp(FP0, SP, WORDSIZE); // FPU reg. stack *= value1
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1
    asm.emitFDIV_Reg_RegDisp(FP0, SP, 0);        // FPU reg. stack /= value2
    asm.emitPOP_Reg   (T0);           // discard 
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    asm.emitFLD_Reg_RegInd (FP0, SP);        // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp (FP0, SP, WORDSIZE); // FPU reg. stack <- value1, or b
    asm.emitFPREM ();             // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg(SP, WORDSIZE, FP0); // POP FPU reg. stack (results) onto java stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);        // POP FPU reg. stack onto java stack
    asm.emitPOP_Reg   (T0);           // shrink the stack (T0 discarded)
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    asm.emitFLD_Reg_RegInd (FP0, SP); // FPU reg. stack <- value1
    asm.emitFCHS  ();      // change sign to stop of FPU stack
    asm.emitFSTP_RegInd_Reg(SP, FP0); // POP FPU reg. stack onto stack
  }


  /*
   * double ALU
   */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);        // FPU reg. stack <- value2
    asm.emitFADD_Reg_RegDisp_Quad(FP0, SP, 8);        // FPU reg. stack += value1
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);  // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);        // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
    asm.emitFSUB_Reg_RegDisp_Quad(FP0, SP, 0);          // FPU reg. stack -= value2
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2
    asm.emitFMUL_Reg_RegDisp_Quad(FP0, SP, 8);          // FPU reg. stack *= value1
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 8);          // FPU reg. stack <- value1
    asm.emitFDIV_Reg_RegInd_Quad(FP0, SP);          // FPU reg. stack /= value2
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);          // POP FPU reg. stack onto stack
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP);          // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp_Quad (FP0, SP, 2*WORDSIZE); // FPU reg. stack <- value1, or b
    asm.emitFPREM ();               // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg_Quad(SP, 2*WORDSIZE, FP0); // POP FPU reg. stack (result) onto java stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);         // POP FPU reg. stack onto java stack
    asm.emitADD_Reg_Imm   (SP, 2*WORDSIZE); // shrink the stack
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    asm.emitFLD_Reg_RegInd_Quad (FP0, SP); // FPU reg. stack <- value1
    asm.emitFCHS  ();      // change sign to stop of FPU stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // POP FPU reg. stack onto stack
  }


  /*
   * conversion ops
   */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    asm.emitPOP_Reg (EAX);
    asm.emitCDQ ();
    asm.emitPUSH_Reg(EDX);
    asm.emitPUSH_Reg(EAX);
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {
    asm.emitFILD_Reg_RegInd(FP0, SP);
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    asm.emitFILD_Reg_RegInd(FP0, SP);
    asm.emitPUSH_Reg(T0);             // grow the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    asm.emitPOP_Reg (T0); // low half of the long
    asm.emitPOP_Reg (S0); // high half of the long
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the l2d bytecode
   */
  protected final void emit_l2d() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push arg to C function 
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysFloatToIntIPField.getOffset());
    // (4) pop argument;
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push arg to C function 
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysFloatToLongIPField.getOffset());
    // (4) pop argument;
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 0, T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    asm.emitFLD_Reg_RegInd(FP0, SP);
    asm.emitSUB_Reg_Imm(SP, WORDSIZE);                // grow the stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToIntIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitPOP_Reg(S0); // shrink stack by 1 word
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    for (int i = 0; i<numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    asm.emitPUSH_RegDisp(SP, numNonVols*WORDSIZE+4);
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToLongIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols-1; i >=0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, 4, T1);
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE);                // shrink the stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVSX_Reg_Reg_Byte(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVZX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    asm.emitPOP_Reg   (T0);
    asm.emitMOVSX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg  (T0);
  }


  /*
   * comparision ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    asm.emitPOP_Reg(T0);        // the low half of value2
    asm.emitPOP_Reg(S0);        // the high half of value2
    asm.emitPOP_Reg(T1);        // the low half of value1
    asm.emitSUB_Reg_Reg(T1, T0);        // subtract the low half of value2 from
                                // low half of value1, result into T1
    asm.emitPOP_Reg(T0);        // the high half of value 1
    //  pop does not alter the carry register
    asm.emitSBB_Reg_Reg(T0, S0);        // subtract the high half of value2 plus
                                // borrow from the high half of value 1,
                                // result in T0
    asm.emitMOV_Reg_Imm(S0, -1);        // load -1 into S0
    VM_ForwardReference fr1 = asm.forwardJcc(asm.LT); // result negative --> branch to end
    asm.emitMOV_Reg_Imm(S0, 0);        // load 0 into S0
    asm.emitOR_Reg_Reg(T0, T1);        // result 0 
    VM_ForwardReference fr2 = asm.forwardJcc(asm.EQ); // result 0 --> branch to end
    asm.emitMOV_Reg_Imm(S0, 1);        // load 1 into S0
    fr1.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);        // push result on stack
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
    asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp(FP0, SP, WORDSIZE);          // copy value1 into FPU
    asm.emitFLD_Reg_RegInd(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 2*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    VM_ForwardReference fr1,fr2,fr3;
    asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, WORDSIZE*2);        // copy value1 into FPU
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                        // copy value2 into FPU
    asm.emitADD_Reg_Imm(SP, 4*WORDSIZE);                // popping the stack
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);                        // eax is used by FNSTSW
    asm.emitXOR_Reg_Reg(S0, S0);                        // S0 <- 0
    asm.emitFUCOMPP();                        // compare and pop FPU *2
    asm.emitFNSTSW();                     // move FPU flags into (E)AX
    asm.emitSAHF();                       // store AH into flags
    fr1 = asm.forwardJcc(asm.EQ);        // branch if ZF set (eq. or unord.)
    // ZF not set ->  neither equal nor unordered
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr2 = asm.forwardJcc(asm.LLT);        // branch if CF set (val2 < val1)
    asm.emitMOV_Reg_Imm(S0, -1);                        // load -1 into S0
    fr1.resolve(asm);                        // ZF set (equal or unordered)
    fr3 = asm.forwardJcc(asm.LGE);        // branch if CF not set (not unordered)
    asm.emitMOV_Reg_Imm(S0, 1);                        // load 1 into S0
    fr3.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Reg(S0);                        // push result on stack
  }


  /*
   * branching
   */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifeq(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(asm.EQ, bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(asm.NE, bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(asm.LT, bTarget);
  }

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(asm.GE, bTarget);
  }

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(asm.GT, bTarget);
  }

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(asm.LE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.EQ, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.NE, bTarget);
  }

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.LT, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.GE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.GT, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.LE, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.EQ, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(asm.NE, bTarget);
  }

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(asm.EQ, bTarget);
  }

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(asm.NE, bTarget);
  }

  /**
   * Emit code to implement the goto and gotow bytecodes
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_goto(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }

  /**
   * Emit code to implement the jsr and jsrw bytecode
   * @param bTarget target bytecode of the jsr
   */
  protected final void emit_jsr(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitCALL_ImmOrLabel(mTarget, bTarget);
  }

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected final void emit_ret(int index) {
    int offset = localOffset(index);
    asm.emitJMP_RegDisp(ESP, offset); 
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
    int n = high-low+1;                        // n = number of normal cases (0..n-1)
    asm.emitPOP_Reg (T0);                          // T0 is index of desired case
    asm.emitSUB_Reg_Imm(T0, low);                     // relativize T0
    asm.emitCMP_Reg_Imm(T0, n);                       // 0 <= relative index < n

    if (!VM.runningTool && compiledMethod.hasCounterArray()) {
      int firstCounter = edgeCounterIdx;
      edgeCounterIdx += (n + 1);

      // Jump around code for default case
      VM_ForwardReference fr = asm.forwardJcc(asm.LLT);
      incEdgeCounter(S0, firstCounter + n);
      asm.emitJMP_ImmOrLabel(mTarget, bTarget);
      fr.resolve(asm);

      // Increment counter for the appropriate case
      incEdgeCounterIdx(S0, T0, firstCounter);
    } else {
      asm.emitJCC_Cond_ImmOrLabel (asm.LGE, mTarget, bTarget);   // if not, goto default case
    }
    asm.emitCALL_Imm(asm.getMachineCodeIndex() + 5 + (n<<LG_WORDSIZE) ); 
    // jump around table, pushing address of 0th delta
    for (int i=0; i<n; i++) {                  // create table of deltas
      int offset = bcodes.getTableSwitchOffset(i);
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      // delta i: difference between address of case i and of delta 0
      asm.emitOFFSET_Imm_ImmOrLabel(i, mTarget, bTarget );
    }
    bcodes.skipTableSwitchOffsets(n);
    asm.emitPOP_Reg (S0);                          // S0 = address of 0th delta 
    asm.emitADD_Reg_RegIdx (S0, S0, T0, asm.WORD, 0);     // S0 += [S0 + T0<<2]
    asm.emitPUSH_Reg(S0);                          // push computed case address
    asm.emitRET ();                            // goto case
  }
  
  /**
   * Emit code to implement the lookupswitch bytecode.
   * Uses linear search, one could use a binary search tree instead,
   * but this is the baseline compiler, so don't worry about it.
   * 
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected final void emit_lookupswitch(int defaultval, int npairs) {
    asm.emitPOP_Reg(T0);
    for (int i=0; i<npairs; i++) {
      int match   = bcodes.getLookupSwitchValue(i);
      asm.emitCMP_Reg_Imm(T0, match);
      int offset  = bcodes.getLookupSwitchOffset(i);
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      if (!VM.runningTool && compiledMethod.hasCounterArray()) {
        // Flip conditions so we can jump over the increment of the taken counter.
        VM_ForwardReference fr = asm.forwardJcc(asm.NE);
        incEdgeCounter(S0, edgeCounterIdx++);
        asm.emitJMP_ImmOrLabel(mTarget, bTarget);
        fr.resolve(asm);
      } else {
        asm.emitJCC_Cond_ImmOrLabel(asm.EQ, mTarget, bTarget);
      }
    }
    bcodes.skipLookupSwitchPairs(npairs);
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && compiledMethod.hasCounterArray()) {
      incEdgeCounter(S0, edgeCounterIdx++); // increment default counter
    }
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }


  /*
   * returns (from function; NOT ret)
   */


  /**
   * Emit code to implement the ireturn bytecode
   */
  protected final void emit_ireturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    genEpilogue(4); 
  }

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected final void emit_lreturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T1); // low half
    asm.emitPOP_Reg(T0); // high half
    genEpilogue(8);
  }

  /**
   * Emit code to implement the freturn bytecode
   */
  protected final void emit_freturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitFLD_Reg_RegInd(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE); // pop the stack
    genEpilogue(4);
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE<<1); // pop the stack
    genEpilogue(8);
  }

  /**
   * Emit code to implement the areturn bytecode
   */
  protected final void emit_areturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    genEpilogue(4); 
  }

  /**
   * Emit code to implement the return bytecode
   */
  protected final void emit_return() {
    if (method.isSynchronized()) genMonitorExit();
    genEpilogue(0); 
  }


  /*
   * field access
   */


  /**
   * Emit code to implement a dynamically linked getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getstatic(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef, true); 
    if (fieldRef.getSize() == BYTES_IN_INT) { 
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get static field
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, WORDSIZE); // get high part
      asm.emitPUSH_RegIdx (JTOC, T0, asm.BYTE, 0);        // get low part
    }
  }

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitPUSH_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPUSH_RegDisp(JTOC, fieldOffset+WORDSIZE); // get high part
      asm.emitPUSH_RegDisp(JTOC, fieldOffset);          // get low part
    }
  }


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putstatic(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef, true);
// putstatic barrier currently unsupported
//     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
//       VM_Barriers.compilePutstaticBarrier(asm, T0);
//       emitDynamicLinkingSequence(T0, fieldRef, false);
//     }
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, 0);        // store low part
      asm.emitPOP_RegIdx(JTOC, T0, asm.BYTE, WORDSIZE); // store high part
    }
  }

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putstatic(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
// putstatic barrier currently unsupported
//     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
//       VM_Barriers.compilePutstaticBarrierImm(asm, fieldOffset);
//     }
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitPOP_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPOP_RegDisp(JTOC, fieldOffset);          // store low part
      asm.emitPOP_RegDisp(JTOC, fieldOffset+WORDSIZE); // store high part
    }
  }


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef, true);
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitMOV_Reg_RegDisp(S0, SP, 0);              // S0 is object reference
      asm.emitMOV_Reg_RegIdx(S0, S0, T0, asm.BYTE, 0); // S0 is field value
      asm.emitMOV_RegDisp_Reg(SP, 0, S0);              // replace reference with value on stack
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitMOV_Reg_RegDisp(S0, SP, 0);                     // S0 is object reference
      asm.emitMOV_Reg_RegIdx(T1, S0, T0, asm.BYTE, WORDSIZE); // T1 is high part of field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T1);                     // replace reference with value on stack
      asm.emitPUSH_RegIdx(S0, T0, asm.BYTE, 0);               // push the low part of field value
    }
  }

  /**
   * Emit code to implement a getfield
   * @param method   the method this bytecode is in
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);           // T0 is object reference
      asm.emitMOV_Reg_RegDisp(T0, T0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T0);           // replace reference with value on stack
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitMOV_Reg_RegDisp(T0, SP, 0);                    // T0 is object reference
      asm.emitMOV_Reg_RegDisp(T1, T0, fieldOffset+WORDSIZE); // T1 is high part of field value
      asm.emitMOV_RegDisp_Reg(SP, 0, T1);                    // replace reference with high part of value on stack
      asm.emitPUSH_RegDisp(T0, fieldOffset);                 // push low part of field value
    }
  }


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T0, fieldRef, true);
    if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrier(asm, T0, fieldRef.getId());
      emitDynamicLinkingSequence(T0, fieldRef, false);
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);              // complete popping the value and reference
    } else {
      if (fieldRef.getSize() == BYTES_IN_INT) {// field is one word
        asm.emitMOV_Reg_RegDisp(T1, SP, 0);               // T1 is the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, 4);               // S0 is the object reference
        asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, T1); // [S0+T0] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*2);              // complete popping the value and reference
      } else { // field is two words (double or long)
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        asm.emitMOV_Reg_RegDisp(JTOC, SP, 0);                          // JTOC is low part of the value to be stored
        asm.emitMOV_Reg_RegDisp(T1, SP, 4);                            // T1 is high part of the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, 8);                            // S0 is the object reference
        asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, 0, JTOC);            // [S0+T0] <- JTOC
        asm.emitMOV_RegIdx_Reg (S0, T0, asm.BYTE, WORDSIZE, T1);       // [S0+T0+4] <- T1
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                           // complete popping the values and reference
        // restore JTOC
        VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());
      }
    }
  }


  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    VM_Barriers.compileModifyCheck(asm, 4);
    if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrierImm(asm, fieldOffset, fieldRef.getId());
      asm.emitADD_Reg_Imm(SP, WORDSIZE*2);          // complete popping the value and reference
    } else {
      if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
        asm.emitMOV_Reg_RegDisp(T0, SP, 0);           // T0 is the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, 4);           // S0 is the object reference
        asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0); // [S0+fieldOffset] <- T0
        asm.emitADD_Reg_Imm(SP, WORDSIZE*2);          // complete popping the value and reference
      } else { // field is two words (double or long)
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        // TODO!! use 8-byte move if possible
        asm.emitMOV_Reg_RegDisp(T0, SP, 0);                    // T0 is low part of the value to be stored
        asm.emitMOV_Reg_RegDisp(T1, SP, 4);                    // T1 is high part of the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, 8);                    // S0 is the object reference
        asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);          // store low part
        asm.emitMOV_RegDisp_Reg(S0, fieldOffset+WORDSIZE, T1); // store high part
        asm.emitADD_Reg_Imm(SP, WORDSIZE*3);                   // complete popping the values and reference
      }
    }
  }


  /*
   * method invocation
   */

  /**
   * Emit code to implement a dynamically linked invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokevirtual(VM_MethodReference methodRef) {
    emitDynamicLinkingSequence(T0, methodRef, true);
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int objectOffset = (methodRefparameterWords << 2) - 4;           // object offset into stack
    asm.emitMOV_Reg_RegDisp (T1, SP, objectOffset);                  // S0 has "this" parameter
    VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
    asm.emitMOV_Reg_RegIdx (S0, S0, T0, asm.BYTE, 0);                // S0 has address of virtual method
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_Reg(S0);                                      // call virtual method
    genResultRegisterUnload(methodRef);                    // push return value, if any
  }

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokevirtual(VM_MethodReference methodRef) {
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    int methodRefOffset = methodRef.peekResolvedMethod().getOffset();
    int objectOffset = (methodRefparameterWords << 2) - WORDSIZE; // object offset into stack
    asm.emitMOV_Reg_RegDisp (T1, SP, objectOffset);
    VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegDisp(S0, methodRefOffset);
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param targetRef the method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_MethodReference methodRef, VM_Method target) {
    if (target.isObjectInitializer()) {
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(JTOC, target.getOffset());
      genResultRegisterUnload(target.getMemberRef().asMethodReference());
    } else {
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      // invoke via class's tib slot
      int methodRefOffset = target.getOffset();
      asm.emitMOV_Reg_RegDisp (S0, JTOC, target.getDeclaringClass().getTibOffset());
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, methodRefOffset);
      genResultRegisterUnload(methodRef);
    }
  }

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokespecial(VM_MethodReference methodRef) {
    emitDynamicLinkingSequence(S0, methodRef, true);
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0);  // call static method
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_MethodReference methodRef) {
    emitDynamicLinkingSequence(S0, methodRef, true);
    genParameterRegisterLoad(methodRef, false);          
    asm.emitCALL_RegIdx(JTOC, S0, asm.BYTE, 0); 
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_MethodReference methodRef) {
    int methodOffset = methodRef.peekResolvedMethod().getOffset();
    genParameterRegisterLoad(methodRef, false);
    asm.emitCALL_RegDisp(JTOC, methodOffset);
    genResultRegisterUnload(methodRef);
  }


  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   * @param count number of parameter words (see invokeinterface bytecode)
   */
  protected final void emit_invokeinterface(VM_MethodReference methodRef) {
    int count = methodRef.getParameterWords() + 1; // +1 for "this" parameter

    VM_Method resolvedMethod = null;
    resolvedMethod = methodRef.peekInterfaceMethod();

    // (1) Emit dynamic type checking sequence if required to do so inline.
    if (VM.BuildForIMTInterfaceInvocation || 
        (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables)) {
      if (resolvedMethod == null) {
        // Can't successfully resolve it at compile time.
        // Call uncommon case typechecking routine to do the right thing when this code actually executes.
        asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                       // "this" object
        asm.emitPUSH_Imm(methodRef.getId());                                    // dict id of target
        VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
        asm.emitPUSH_Reg(S0);
        genParameterRegisterLoad(2);                                            // pass 2 parameter word
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
      } else {
        asm.emitMOV_Reg_RegDisp (T0, JTOC, resolvedMethod.getDeclaringClass().getTibOffset()); // tib of the interface method
        asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                 // "this" object
        asm.emitPUSH_RegDisp(T0, TIB_TYPE_INDEX << 2);                                    // type of the interface method
        VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
        asm.emitPUSH_Reg(S0);
        genParameterRegisterLoad(2);                                          // pass 2 parameter word
        asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
      }
    }

    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(methodRef);
      int offset = sig.getIMTOffset();
          
      // squirrel away signature ID
      VM_ProcessorLocalState.emitMoveImmToField(asm, 
                                                VM_Entrypoints.hiddenSignatureIdField.getOffset(),
                                                sig.getId());

      asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                  // "this" object
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
      if (VM.BuildForIndirectIMT) {
        // Load the IMT Base into S0
        asm.emitMOV_Reg_RegDisp(S0, S0, TIB_IMT_TIB_INDEX << 2);
      }
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, offset);                                             // the interface call
    } else if (VM.BuildForITableInterfaceInvocation && 
               VM.DirectlyIndexedITables && 
               resolvedMethod != null) {
      VM_Class I = resolvedMethod.getDeclaringClass();
      asm.emitMOV_Reg_RegDisp (T1, SP, (count-1) << 2);                                 // "this" object
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T1);
      asm.emitMOV_Reg_RegDisp (S0, S0, TIB_ITABLES_TIB_INDEX << 2);                     // iTables
      asm.emitMOV_Reg_RegDisp (S0, S0, I.getInterfaceId() << 2);                        // iTable
      genParameterRegisterLoad(methodRef, true);
      int idx = VM_InterfaceInvocation.getITableIndex(I, methodRef.getName(), methodRef.getDescriptor());
      asm.emitCALL_RegDisp(S0, idx << 2); // the interface call
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex = VM_InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(), 
                                                            methodRef.getName(), 
                                                            methodRef.getDescriptor());
      }
      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into 
        // method address
        int methodRefId = methodRef.getId();
        asm.emitPUSH_RegDisp(SP, (count-1)<<LG_WORDSIZE);  // "this" parameter is obj
        asm.emitPUSH_Imm(methodRefId);                 // id of method to call
        genParameterRegisterLoad(2);               // pass 2 parameter words
        asm.emitCALL_RegDisp(JTOC,  VM_Entrypoints.invokeInterfaceMethod.getOffset()); // invokeinterface(obj, id) returns address to call
        asm.emitMOV_Reg_Reg (S0, T0);                      // S0 has address of method
        genParameterRegisterLoad(methodRef, true);
        asm.emitCALL_Reg(S0);                          // the interface method (its parameters are on stack)
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into 
        // itable address
        asm.emitMOV_Reg_RegDisp (T0, SP, (count-1) << 2);             // "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T0);
        asm.emitPUSH_Reg(S0);
        asm.emitPUSH_Imm        (resolvedMethod.getDeclaringClass().getInterfaceId()); // interface id
        genParameterRegisterLoad(2);                                  // pass 2 parameter words
        asm.emitCALL_RegDisp    (JTOC,  VM_Entrypoints.findItableMethod.getOffset()); // findItableOffset(tib, id) returns iTable
        asm.emitMOV_Reg_Reg     (S0, T0);                             // S0 has iTable
        genParameterRegisterLoad(methodRef, true);
        asm.emitCALL_RegDisp    (S0, itableIndex << 2);               // the interface call
      }
    }
    genResultRegisterUnload(methodRef);
  }
 

  /*
   * other object model functions
   */ 


  /**
   * Emit code to allocate a scalar object
   * @param method  the method this bytecode is compiled in
   * @param typeRef the VM_Class to instantiate
   */
  protected final void emit_resolved_new(VM_Class typeRef) {
    int instanceSize = typeRef.getInstanceSize();
    int tibOffset = typeRef.getTibOffset();
    int whichAllocator = MM_Interface.pickAllocator(typeRef, method);
    int align = VM_ObjectModel.getAlignment(typeRef);
    int offset = VM_ObjectModel.getOffsetForAlignment(typeRef);
    asm.emitPUSH_Imm(instanceSize);            
    asm.emitPUSH_RegDisp (JTOC, tibOffset);       // put tib on stack    
    asm.emitPUSH_Imm(typeRef.hasFinalizer()?1:0); // does the class have a finalizer?
    asm.emitPUSH_Imm(whichAllocator);
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    genParameterRegisterLoad(6);                  // pass 6 parameter words
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.resolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg (T0);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(VM_TypeReference typeRef) {
    asm.emitPUSH_Imm(typeRef.getId());
    genParameterRegisterLoad(1);           // pass 1 parameter word
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg (T0);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_resolved_newarray(VM_Array array) {
    int width      = array.getLogElementSize();
    int tibOffset  = array.getTibOffset();
    int headerSize = VM_ObjectModel.computeHeaderSize(array);
    int whichAllocator = MM_Interface.pickAllocator(array, method);
    int align = VM_ObjectModel.getAlignment(array);
    int offset = VM_ObjectModel.getOffsetForAlignment(array);
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(width);                 // logElementSize
    asm.emitPUSH_Imm(headerSize);            // headerSize
    asm.emitPUSH_RegDisp(JTOC, tibOffset);   // tib
    asm.emitPUSH_Imm(whichAllocator);        // allocator
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    genParameterRegisterLoad(7);             // pass 7 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.resolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to dynamically link and allocate an array
   * @param tRef the type reference to dynamically link & instantiate
   */
  protected final void emit_unresolved_newarray(VM_TypeReference tRef) {
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(tRef.getId());
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the type reference to instantiate
   * @param dimensions the number of dimensions
   * @param dictionaryId, the dictionaryId of typeRef
   */
  protected final void emit_multianewarray(VM_TypeReference typeRef, int dimensions) {
    // Calculate the offset from FP on entry to newarray: 
    //      1 word for each parameter, plus 1 for return address on
    //      stack and 1 for code technique in VM_Linker
    final int PARAMETERS = 4;
    final int OFFSET_WORDS = PARAMETERS + 2;

    // setup parameters for newarrayarray routine
    asm.emitPUSH_Imm (method.getId());           // caller
    asm.emitPUSH_Imm (dimensions);               // dimension of arays
    asm.emitPUSH_Imm (typeRef.getId());          // type of array elements
    asm.emitPUSH_Imm ((dimensions + OFFSET_WORDS)<<LG_WORDSIZE);  // offset to dimensions from FP on entry to newarray 
    
    genParameterRegisterLoad(PARAMETERS);
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.newArrayArrayMethod.getOffset()); 
    for (int i = 0; i < dimensions ; i++) asm.emitPOP_Reg(S0); // clear stack of dimensions (todo use and add immediate to do this)
    asm.emitPUSH_Reg(T0);                              // push array ref on stack
  }

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    asm.emitMOV_Reg_RegDisp(T0, SP, 0);                   // T0 is array reference
    asm.emitMOV_Reg_RegDisp(T0, T0, VM_ObjectModel.getArrayLengthOffset()); // T0 is array length
    asm.emitMOV_RegDisp_Reg(SP, 0, T0);                   // replace reference with length on stack
  }

  /**
   * Emit code to implement the athrow bytecode
   */
  protected final void emit_athrow() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.athrowMethod.getOffset());
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_checkcast(VM_TypeReference typeRef) {
    asm.emitPUSH_RegInd (SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(typeRef.getId());               // VM_TypeReference id.
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.checkcastMethod.getOffset()); // checkcast(obj, type reference id);
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_checkcast_resolvedClass(VM_Type type) {
    asm.emitPUSH_RegInd (SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(type.getId());                  // VM_Type id.
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.checkcastResolvedClassMethod.getOffset()); // checkcast(obj, type id)
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_checkcast_final(VM_Type type) {
    asm.emitPUSH_RegInd (SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(type.getTibOffset());           // JTOC index that identifies klass  
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp (JTOC, VM_Entrypoints.checkcastFinalMethod.getOffset()); // checkcast(obj, TIB's JTOC offset)
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_instanceof(VM_TypeReference typeRef) {
    asm.emitPUSH_Imm(typeRef.getId());
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.instanceOfMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_instanceof_resolvedClass(VM_Type type) {
    asm.emitPUSH_Imm(type.getId());
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.instanceOfResolvedClassMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   */
  protected final void emit_instanceof_final(VM_Type type) {
    asm.emitPUSH_Imm(type.getTibOffset());  
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.instanceOfFinalMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected final void emit_monitorenter() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockMethod.getOffset());
  }

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected final void emit_monitorexit() {
    genParameterRegisterLoad(1);          // pass 1 parameter word
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockMethod.getOffset());  
  }


  //----------------//
  // implementation //
  //----------------//
  
  private final void genPrologue () {
    if (shouldPrint) asm.comment("prologue for " + method);
    if (klass.isBridgeFromNative()) {
      // replace the normal prologue with a special prolog
      VM_JNICompiler.generateGlueCodeForJNIMethod (asm, method, compiledMethod.getId());
      // set some constants for the code generation of the rest of the method
      // firstLocalOffset is shifted down because more registers are saved
      firstLocalOffset = STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI<<LG_WORDSIZE) ;
    } else {
      /* paramaters are on the stack and/or in registers;  There is space
       * on the stack for all the paramaters;  Parameter slots in the
       * stack are such that the first paramater has the higher address,
       * i.e., it pushed below all the other paramaters;  The return
       * address is the topmost entry on the stack.  The frame pointer
       * still addresses the previous frame.
       * The first word of the header, currently addressed by the stack
       * pointer, contains the return address.
       */

      /* establish a new frame:
       * push the caller's frame pointer in the stack, and
       * reset the frame pointer to the current stack top,
       * ie, the frame pointer addresses directly the word
       * that contains the previous frame pointer.
       * The second word of the header contains the frame
       * point of the caller.
       * The third word of the header contains the compiled method id of the called method.
       */
      asm.emitPUSH_RegDisp   (PR, VM_Entrypoints.framePointerField.getOffset());        // store caller's frame pointer
      VM_ProcessorLocalState.emitMoveRegToField(asm, VM_Entrypoints.framePointerField.getOffset(), SP); // establish new frame
      /*
       * NOTE: until the end of the prologue SP holds the framepointer.
       */
      asm.emitMOV_RegDisp_Imm(SP, STACKFRAME_METHOD_ID_OFFSET, compiledMethod.getId()); // 3rd word of header
      
      /*
       * save registers
       */
      asm.emitMOV_RegDisp_Reg (SP, JTOC_SAVE_OFFSET, JTOC);          // save nonvolatile JTOC register
      asm.emitMOV_RegDisp_Reg (SP, EBX_SAVE_OFFSET, EBX);            // save nonvolatile EBX register
    
      // establish the JTOC register
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_Entrypoints.jtocField.getOffset());

      if (!VM.runningTool && compiledMethod.hasCounterArray()) {
        // use (nonvolatile) EBX to hold base of this methods counter array
        asm.emitMOV_Reg_RegDisp(EBX, JTOC, VM_Entrypoints.edgeCountersField.getOffset());
        asm.emitMOV_Reg_RegDisp(EBX, EBX, getEdgeCounterOffset());
      }

      int savedRegistersSize   = SAVED_GPRS<<LG_WORDSIZE;       // default
      /* handle "dynamic brige" methods:
       * save all registers except FP, SP, PR, S0 (scratch), and
       * JTOC and EBX saved above.
       */
      // TODO: (SJF): When I try to reclaim ESI, I may have to save it here?
      if (klass.isDynamicBridge()) {
        savedRegistersSize += 2 << LG_WORDSIZE;
        asm.emitMOV_RegDisp_Reg (SP, T0_SAVE_OFFSET,  T0); 
        asm.emitMOV_RegDisp_Reg (SP, T1_SAVE_OFFSET,  T1); 
        asm.emitFNSAVE_RegDisp  (SP, FPU_SAVE_OFFSET);
        savedRegistersSize += FPU_STATE_SIZE;
      } 

      // copy registers to callee's stackframe
      firstLocalOffset         = STACKFRAME_BODY_OFFSET - savedRegistersSize;
      int firstParameterOffset = (parameterWords << LG_WORDSIZE) + WORDSIZE;
      genParameterCopy(firstParameterOffset, firstLocalOffset);

      int emptyStackOffset = firstLocalOffset - (method.getLocalWords() << LG_WORDSIZE) + WORDSIZE;
      asm.emitADD_Reg_Imm (SP, emptyStackOffset);               // set aside room for non parameter locals

      //-#if RVM_WITH_OSR
      /* defer generating code which may cause GC until 
       * locals were initialized. see emit_deferred_prologue
       */
      if (method.isForOsrSpecialization()) {
        return;
      }
      //-#endif

      /*
       * generate stacklimit check
       */
      if (isInterruptible) {
        // S0<-limit
        VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                                                  VM_Entrypoints.activeThreadStackLimitField.getOffset());

        asm.emitSUB_Reg_Reg (S0, SP);                                           // space left
        asm.emitADD_Reg_Imm (S0, method.getOperandWords() << LG_WORDSIZE);      // space left after this expression stack
        VM_ForwardReference fr = asm.forwardJcc(asm.LT);        // Jmp around trap if OK
        asm.emitINT_Imm ( VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE );     // trap
        fr.resolve(asm);
      } else {
        // TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
      }

      if (method.isSynchronized()) genMonitorEnter();

      genThreadSwitchTest(VM_Thread.PROLOGUE);
    }
  }

  //-#if RVM_WITH_OSR
  protected final void emit_deferred_prologue() {

    if (VM.VerifyAssertions) VM._assert(method.isForOsrSpecialization());
        
    if (isInterruptible) {
      // S0<-limit
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0,
                        VM_Entrypoints.activeThreadStackLimitField.getOffset());
      asm.emitSUB_Reg_Reg (S0, SP);                                       // spa
      asm.emitADD_Reg_Imm (S0, method.getOperandWords() << LG_WORDSIZE);  // spa
      VM_ForwardReference fr = asm.forwardJcc(asm.LT);    // Jmp around trap if 
      asm.emitINT_Imm ( VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE ); // tra
      fr.resolve(asm);
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow 
    }

    /* never do monitor enter for synced method since the specialized 
     * code starts after original monitor enter.
     */
//    if (method.isSynchronized()) genMonitorEnter();

    genThreadSwitchTest(VM_Thread.PROLOGUE);
  }
  //-#endif
  
  private final void genEpilogue (int bytesPopped) {
    if (klass.isBridgeFromNative()) {
      // pop locals and parameters, get to saved GPR's
      asm.emitADD_Reg_Imm(SP, (this.method.getLocalWords() << LG_WORDSIZE));
      VM_JNICompiler.generateEpilogForJNIMethod(asm, this.method);
    } else if (klass.isDynamicBridge()) {
      // we never return from a DynamicBridge frame
      asm.emitINT_Imm(0xFF);
    } else {
      // normal method
      asm.emitADD_Reg_Imm     (SP, fp2spOffset(0) - bytesPopped);     // SP becomes frame pointer
      asm.emitMOV_Reg_RegDisp (JTOC, SP, JTOC_SAVE_OFFSET);           // restore nonvolatile JTOC register
      asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);             // restore nonvolatile EBX register
      asm.emitPOP_RegDisp     (PR, VM_Entrypoints.framePointerField.getOffset()); // discard frame
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);    // return to caller- pop parameters from stack
    }
  }
   
  private final void genMonitorEnter () {
    if (method.isStatic()) {
      if (VM.writingBootImage) {
        VM.deferClassObjectCreation(klass);
      } else {
        klass.getClassForType();
      }
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);               // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);                             // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeField.getOffset()); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));                           // push "this" object
    }
    genParameterRegisterLoad(1);                                   // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockMethod.getOffset());  
    lockOffset = asm.getMachineCodeIndex();                       // after this instruction, the method has the monitor
  }
  
  private final void genMonitorExit () {
    if (method.isStatic()) {
      int tibOffset = klass.getTibOffset();
      asm.emitMOV_Reg_RegDisp (T0, JTOC, tibOffset);                   // T0 = tib for klass
      asm.emitMOV_Reg_RegInd (T0, T0);                             // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeField.getOffset()); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));                    // push "this" object
    }
    genParameterRegisterLoad(1); // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockMethod.getOffset());  
  }
  
  private final void genBoundsCheck (VM_Assembler asm, byte indexReg, byte arrayRefReg ) { 
    asm.emitCMP_RegDisp_Reg(arrayRefReg,
                            VM_ObjectModel.getArrayLengthOffset(), indexReg);  // compare index to array length
    VM_ForwardReference fr = asm.forwardJcc(asm.LGT);                     // Jmp around trap if index is OK
    
    // "pass" index param to C trap handler
    VM_ProcessorLocalState.emitMoveRegToField(asm, 
                                              VM_Entrypoints.arrayIndexTrapParamField.getOffset(),
                                              indexReg);

    asm.emitINT_Imm(VM_Runtime.TRAP_ARRAY_BOUNDS + RVM_TRAP_BASE );       // trap
    fr.resolve(asm);
  }

  /**
   * Emit a conditional branch on the given condition and bytecode target.
   * The caller has just emitted the instruction sequence to set the condition codes.
   */
  private final void genCondBranch(byte cond, int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && compiledMethod.hasCounterArray()) {
      // Allocate two counters: taken and not taken
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;

      // Flip conditions so we can jump over the increment of the taken counter.
      VM_ForwardReference notTaken = asm.forwardJcc(asm.flipCode(cond));

      // Increment taken counter & jump to target
      incEdgeCounter(T1, entry + VM_EdgeCounts.TAKEN);
      asm.emitJMP_ImmOrLabel(mTarget, bTarget);

      // Increment not taken counter
      notTaken.resolve(asm);
      incEdgeCounter(T1, entry + VM_EdgeCounts.NOT_TAKEN);
    } else {
      asm.emitJCC_Cond_ImmOrLabel(cond, mTarget, bTarget);
    }
  }


  private final void incEdgeCounter(byte scratch, int counterIdx) {
    if (VM.VerifyAssertions) VM._assert(compiledMethod.hasCounterArray());
    asm.emitMOV_Reg_RegDisp(scratch, EBX, counterIdx<<2);
    asm.emitINC_Reg(scratch);
    asm.emitAND_Reg_Imm(scratch, 0x7fffffff); // saturate at max int;
    asm.emitMOV_RegDisp_Reg(EBX, counterIdx<<2, scratch);
  }

  private final void incEdgeCounterIdx(byte scratch, byte idx, int counterIdx) {
    if (VM.VerifyAssertions) VM._assert(compiledMethod.hasCounterArray());
    asm.emitMOV_Reg_RegIdx(scratch, EBX, idx, asm.WORD, counterIdx<<2);
    asm.emitINC_Reg(scratch);
    asm.emitAND_Reg_Imm(scratch, 0x7fffffff); // saturate at max int;
    asm.emitMOV_RegIdx_Reg(EBX, idx, asm.WORD, counterIdx<<2, scratch);
  }


  /** 
   * Copy a single floating-point double parameter from operand stack into fp register stack.
   * Assumption: method to be called has exactly one parameter.
   * Assumption: parameter type is double.
   * Assumption: parameter is on top of stack.
   * Also, this method is only called before generation of a call
   * to doubleToInt() or doubleToLong()
   */
  private final void genParameterRegisterLoad () {
    if (0 < NUM_PARAMETER_FPRS) {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a "helper" method call.
   * Assumption: no floating-point parameters.
   * @param params number of parameter words (including "this" if any).
   */
  private final void genParameterRegisterLoad (int params) {
    if (VM.VerifyAssertions) VM._assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T0, SP, (params-1) << LG_WORDSIZE);
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T1, SP, (params-2) << LG_WORDSIZE);
    }
  }
   
  /** 
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of an explicit method call.
   * @param method is the method to be called.
   * @param hasThisParameter is the method virtual?
   */
  private final void genParameterRegisterLoad (VM_MethodReference method, boolean hasThisParam) {
    int max = NUM_PARAMETER_GPRS + NUM_PARAMETER_FPRS;
    if (max == 0) return; // quit looking when all registers are full
    int gpr = 0;  // number of general purpose registers filled
    int fpr = 0;  // number of floating point  registers filled
    byte  T = T0; // next GPR to get a parameter
    int params = method.getParameterWords() + (hasThisParam ? 1 : 0);
    int offset = (params-1) << LG_WORDSIZE; // stack offset of first parameter word
    if (hasThisParam) {
      if (gpr < NUM_PARAMETER_GPRS) {
        asm.emitMOV_Reg_RegDisp(T, SP, offset);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
        max--;
      }
      offset -= WORDSIZE;
    }
    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      if (max == 0) return; // quit looking when all registers are full
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_Reg_RegDisp(T, SP, offset); // lo register := hi mem (== hi order word)
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
          max--;
          if (gpr < NUM_PARAMETER_GPRS) {
            asm.emitMOV_Reg_RegDisp(T, SP, offset - WORDSIZE);  // hi register := lo mem (== lo order word)
            gpr++;
            max--;
          }
        }
        offset -= 2*WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          asm.emitFLD_Reg_RegDisp(FP0, SP, offset);
          fpr++;
          max--;
        }
        offset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, offset - WORDSIZE);
          fpr++;
          max--;
        }
        offset -= 2*WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_Reg_RegDisp(T, SP, offset);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
          max--;
        }
        offset -= WORDSIZE;
      }
    }
    if (VM.VerifyAssertions) VM._assert(offset == - WORDSIZE);
  }
   
  /** 
   * Store parameters into local space of the callee's stackframe.
   * Taken: srcOffset - offset from frame pointer of first parameter in caller's stackframe.
   *        dstOffset - offset from frame pointer of first local in callee's stackframe
   * Assumption: although some parameters may be passed in registers,
   * space for all parameters is layed out in order on the caller's stackframe.
   */
  private final void genParameterCopy (int srcOffset, int dstOffset) {
    int gpr = 0;  // number of general purpose registers unloaded
    int fpr = 0;  // number of floating point registers unloaded
    byte  T = T0; // next GPR to get a parameter
    if (!method.isStatic()) { // handle "this" parameter
      if (gpr < NUM_PARAMETER_GPRS) {
        asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
      } else { // no parameters passed in registers
        asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
        asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
      }
      srcOffset -= WORDSIZE;
      dstOffset -= WORDSIZE;
    }
    VM_TypeReference [] types     = method.getParameterTypes();
    int     [] fprOffset = new     int [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    boolean [] is32bit   = new boolean [NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    for (int i=0; i<types.length; i++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);    // hi mem := lo register (== hi order word)
          T = T1;                                       // at most 2 parameters can be passed in general purpose registers
          gpr++;
          srcOffset -= WORDSIZE;
          dstOffset -= WORDSIZE;
          if (gpr < NUM_PARAMETER_GPRS) {
            asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);  // lo mem := hi register (== lo order word)
            gpr++;
          } else {
            asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset); // lo mem from caller's stackframe
            asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
          }
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // hi mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
          srcOffset -= WORDSIZE;
          dstOffset -= WORDSIZE;
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset -= WORDSIZE;
        dstOffset -= WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          fprOffset[fpr] = dstOffset;
          is32bit[fpr]   = true;
          fpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset -= WORDSIZE;
        dstOffset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          srcOffset -= WORDSIZE;
          dstOffset -= WORDSIZE;
          fprOffset[fpr] = dstOffset;
          is32bit[fpr]   = false;
          fpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // hi mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
          srcOffset -= WORDSIZE;
          dstOffset -= WORDSIZE;
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset -= WORDSIZE;
        dstOffset -= WORDSIZE;
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset -= WORDSIZE;
        dstOffset -= WORDSIZE;
      }
    }
    for (int i=fpr-1; 0<=i; i--) { // unload the floating point register stack (backwards)
      if (is32bit[i]) {
        asm.emitFSTP_RegDisp_Reg(SP, fprOffset[i], FP0);
      } else {
        asm.emitFSTP_RegDisp_Reg_Quad(SP, fprOffset[i], FP0);
      }
    }
  }
   
  /** 
   * Push return value of method from register to operand stack.
   */
  private final void genResultRegisterUnload (VM_MethodReference method) {
    VM_TypeReference t = method.getReturnType();
    if (t.isVoidType()) return;
    if (t.isLongType()) {
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
    } else if (t.isFloatType()) {
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    } else if (t.isDoubleType()) {
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitPUSH_Reg(T0);
    }
  }
  
  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private final void genThreadSwitchTest (int whereFrom) {
    if (!isInterruptible) {
      return;
    } 

    // thread switch requested ??
    VM_ProcessorLocalState.emitCompareFieldWithImm(asm, VM_Entrypoints.takeYieldpointField.getOffset(), 0);
    VM_ForwardReference fr1;
    if (whereFrom == VM_Thread.PROLOGUE) {
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(asm.EQ);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromPrologueMethod.getOffset()); 
    } else if (whereFrom == VM_Thread.BACKEDGE) {
      // Take yieldpoint if yieldpoint flag is >0
      fr1 = asm.forwardJcc(asm.LE);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromBackedgeMethod.getOffset()); 
    } else { // EPILOGUE
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(asm.EQ);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromEpilogueMethod.getOffset()); 
    }
    fr1.resolve(asm);

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    if (options.INVOCATION_COUNTERS) {
      int id = compiledMethod.getId();
      com.ibm.JikesRVM.adaptive.VM_InvocationCounts.allocateCounter(id);
      asm.emitMOV_Reg_RegDisp(ECX, JTOC, VM_Entrypoints.invocationCountsField.getOffset());
      asm.emitSUB_RegDisp_Imm(ECX, compiledMethod.getId() << 2, 1);
      VM_ForwardReference notTaken = asm.forwardJcc(asm.GT);
      asm.emitPUSH_Imm(id);
      genParameterRegisterLoad(1);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.invocationCounterTrippedMethod.getOffset());
      notTaken.resolve(asm);
    }
    //-#endif
  }

  // Indicate if specified VM_Magic method causes a frame to be created on the runtime stack.
  // Taken:   VM_Method of the magic method being called
  // Returned: true if method causes a stackframe to be created
  //
  public static boolean checkForActualCall(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
    return methodName == VM_MagicNames.invokeMain             ||
      methodName == VM_MagicNames.invokeClassInitializer      ||
      methodName == VM_MagicNames.invokeMethodReturningVoid   ||
      methodName == VM_MagicNames.invokeMethodReturningInt    ||
      methodName == VM_MagicNames.invokeMethodReturningLong   ||
      methodName == VM_MagicNames.invokeMethodReturningFloat  ||
      methodName == VM_MagicNames.invokeMethodReturningDouble ||
      methodName == VM_MagicNames.invokeMethodReturningObject ||
      methodName == VM_MagicNames.addressArrayCreate;
  }

  private final boolean genMagic (VM_MethodReference m) {
    VM_Atom methodName = m.getName();

    if (m.getType() == VM_TypeReference.Address) {
      // Address magic

      VM_TypeReference[] types = m.getParameterTypes();

      // Loads all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.loadAddress ||
          methodName == VM_MagicNames.prepareAddress ||
          methodName == VM_MagicNames.loadWord ||
          methodName == VM_MagicNames.prepareWord ||
          methodName == VM_MagicNames.loadInt ||
          methodName == VM_MagicNames.prepareInt ||
          methodName == VM_MagicNames.loadFloat) {

        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg(T0);                      // address
          asm.emitPUSH_RegInd(T0);                  // pushes [T0+0]
        } else {
          // Load at offset
          asm.emitPOP_Reg (S0);                  // offset
          asm.emitPOP_Reg (T0);                  // object ref
          asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadByte) {
        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg (T0);                  // base 
          asm.emitMOVZX_Reg_RegInd_Byte(T0, T0) ;
          asm.emitPUSH_Reg (T0);
        } else {
          // Load at offset
          asm.emitPOP_Reg (S0);                  // offset
          asm.emitPOP_Reg (T0);                  // base 
          asm.emitMOVZX_Reg_RegIdx_Byte(T0, T0, S0, asm.BYTE, 0); // load and zero extend byte [T0+S0]
          asm.emitPUSH_Reg (T0);
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadShort ||
          methodName == VM_MagicNames.loadChar) {

        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg (T0);                  // base 
          asm.emitMOVZX_Reg_RegInd_Word(T0, T0);
          asm.emitPUSH_Reg (T0);
        } else {
          // Load at offset
          asm.emitPOP_Reg (S0);                  // offset
          asm.emitPOP_Reg (T0);                  // base 
          asm.emitMOVZX_Reg_RegIdx_Word(T0, T0, S0, asm.BYTE, 0); // load and zero extend word [T0+S0]
          asm.emitPUSH_Reg (T0);
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadLong ||
          methodName == VM_MagicNames.loadDouble) {
      
        if (types.length == 0) {
          // No offset
          throw new RuntimeException("Magic not implemented");
        } else {
          // Load at offset
          asm.emitPOP_Reg (S0);                  // offset
          asm.emitPOP_Reg (T0);                  // base 
          asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 4); // pushes [T0+S0+4]
          asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
        }
        return true;
      }

      // Stores all take the form:
      // ..., Address, [Offset], Value -> ...
      if (methodName == VM_MagicNames.store) {
        // Always at least one parameter to a store. 
        // First parameter is the type to be stored.
        VM_TypeReference storeType = types[0];

        if (storeType == VM_TypeReference.Int ||
            storeType == VM_TypeReference.Address ||
            storeType == VM_TypeReference.Word ||
            storeType == VM_TypeReference.Float) {
        
          if (types.length == 1) {
            // No offset
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(S0);                   // address
            asm.emitMOV_RegInd_Reg(S0,T0);         // [S0+0] <- T0
          } else {
            // Store at offset
            asm.emitPOP_Reg(S0);                   // offset
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // address 
            asm.emitMOV_RegIdx_Reg(T1, S0,
                                 asm.BYTE, 0, T0); // [T1+S0] <- T0
          }
          return true;
        }

        if (storeType == VM_TypeReference.Byte) {
          if (types.length == 1) {
            // No offset 
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // base 
            asm.emitMOV_RegInd_Reg_Byte(T1, T0);
          } else {
            // Store at offset
            asm.emitPOP_Reg(S0);                   // offset
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // base 
            asm.emitMOV_RegIdx_Reg_Byte(T1, S0, 
                                 asm.BYTE, 0, T0); // [T1+S0] <- (byte) T0     
          }
          return true;
        }

        if (storeType == VM_TypeReference.Short ||
            storeType == VM_TypeReference.Char) { 

          if (types.length == 1) {
            // No offset 
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // base 
            asm.emitMOV_RegInd_Reg_Word(T1, T0);
          } else {
            // Store at offset
            asm.emitPOP_Reg(S0);                   // offset    
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // base      
            asm.emitMOV_RegIdx_Reg_Word(T1, S0, 
                                 asm.BYTE, 0, T0); // [T1+S0] <- (word) T0     
          }
          return true;
        }

        if (storeType == VM_TypeReference.Double ||
            storeType == VM_TypeReference.Long) { 

          if (types.length == 1) {
            // No offset 
            throw new RuntimeException("Magic not implemented");
          } else {
            // Store at offset
            asm.emitMOV_Reg_RegDisp(T0, SP, +4);          // value high
            asm.emitMOV_Reg_RegInd (S0, SP);     // offset
            asm.emitMOV_Reg_RegDisp(T1, SP, +12);     // base 
            asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
            asm.emitMOV_Reg_RegDisp(T0, SP, +8);     // value low
            asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 4, T0); // [T1+S0+4] <- T0
            asm.emitADD_Reg_Imm    (SP, WORDSIZE * 4); // pop stack locations
          }
          return true;
        }
      } else if (methodName == VM_MagicNames.attempt) {
        // All attempts are similar 32 bit values.
	if (types.length == 3) {
          // Offset passed
          asm.emitPOP_Reg (S0);        // S0 = offset
        }
	asm.emitPOP_Reg (T1);          // newVal
        asm.emitPOP_Reg (EAX);         // oldVal (EAX is implicit arg to LCMPX
        if (types.length == 3) {
          asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
        } else {
          // No offset
          asm.emitMOV_Reg_RegInd(S0, SP);  // S0 = base
        }        

        asm.emitLockNextInstruction();
        asm.emitCMPXCHG_RegInd_Reg (S0, T1);   // atomic compare-and-exchange
        asm.emitMOV_RegInd_Imm (SP, 0);        // 'push' false (overwriting base)
        VM_ForwardReference fr = asm.forwardJcc(asm.NE); // skip if compare fails
        asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
        fr.resolve(asm);
        return true;
      }
    }

    if (methodName == VM_MagicNames.attemptInt ||
        methodName == VM_MagicNames.attemptObject ||
        methodName == VM_MagicNames.attemptAddress ||
        methodName == VM_MagicNames.attemptWord) {
      // attempt gets called with four arguments: base, offset, oldVal, newVal
      // returns ([base+offset] == oldVal)
      // if ([base+offset] == oldVal) [base+offset] := newVal
      // (operation on memory is atomic)
      asm.emitPOP_Reg (T1);            // newVal
      asm.emitPOP_Reg (EAX);           // oldVal (EAX is implicit arg to LCMPXCNG
      asm.emitPOP_Reg (S0);            // S0 = offset
      asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
      asm.emitLockNextInstruction();
      asm.emitCMPXCHG_RegInd_Reg (S0, T1);   // atomic compare-and-exchange
      asm.emitMOV_RegInd_Imm (SP, 0);        // 'push' false (overwriting base)
      VM_ForwardReference fr = asm.forwardJcc(asm.NE); // skip if compare fails
      asm.emitMOV_RegInd_Imm (SP, 1);        // 'push' true (overwriting base)
      fr.resolve(asm);
      return true;
    }
    
    if (methodName == VM_MagicNames.invokeMain) {
      // invokeMain gets "called" with two arguments:
      //   String[] mainArgs       // the arguments to the main method
      //   INSTRUCTION[] mainCode  // the code for the main method
      asm.emitPOP_Reg (S0);            // 
      genParameterRegisterLoad(1); // pass 1 parameter word     
      asm.emitCALL_Reg(S0);            // branches to mainCode with mainArgs on the stack
      return true;
    }
    
    if (methodName == VM_MagicNames.saveThreadState) {
      int offset = VM_Entrypoints.saveThreadStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.threadSwitch) {
      int offset = VM_Entrypoints.threadSwitchInstructionsField.getOffset();
      genParameterRegisterLoad(2); // pass 2 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }         
         
    if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      int offset = VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.invokeClassInitializer) {
      asm.emitPOP_Reg (S0);
      asm.emitCALL_Reg(S0); // call address just popped
      return true;
    }
    
    if (m.getType() == VM_TypeReference.SysCall) {
      VM_TypeReference[] args = m.getParameterTypes();
      VM_TypeReference rtype = m.getReturnType();
      int offsetToJavaArg = 3*WORDSIZE; // the three regs saved in (2)
      int paramBytes = 0;
      
      // (1) save three RVM nonvolatile/special registers
      //     we don't have to save EBP: the callee will
      //     treat it as a framepointer and save/restore
      //     it for us.
      asm.emitPUSH_Reg(EBX);
      asm.emitPUSH_Reg(ESI);    
      asm.emitPUSH_Reg(EDI);

      // (2) Push args to target function (reversed)
      for (int i=args.length-1; i>=0; i--) {
        VM_TypeReference arg = args[i];
        if (arg.isLongType() || arg.isDoubleType()) {
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg + 4);
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg + 4);
          offsetToJavaArg += 16;
          paramBytes += 8;
        } else {
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg);
          offsetToJavaArg += 8;
          paramBytes += 4;
        }
      }   

      // (3) invoke target function
      VM_Field ip = VM_Entrypoints.getSysCallField(m.getName().toString());
      asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
      asm.emitCALL_RegDisp(S0, ip.getOffset());
      
      // (4) pop space for arguments
      asm.emitADD_Reg_Imm(SP, paramBytes);

      // (5) restore RVM registers
      asm.emitPOP_Reg(EDI);
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);

      // (6) pop expression stack
      asm.emitADD_Reg_Imm(SP, paramBytes);

      // (7) push return value
      if (rtype.isLongType()) {
        asm.emitPUSH_Reg(T1);
        asm.emitPUSH_Reg(T0);
      } else if (rtype.isDoubleType()) {
        asm.emitSUB_Reg_Imm(SP, 8);
        asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      } else if (rtype.isFloatType()) {
        asm.emitSUB_Reg_Imm(SP, 4);
        asm.emitFSTP_RegInd_Reg(SP, FP0);
      } else if (!rtype.isVoidType()) {
        asm.emitPUSH_Reg(T0);
      }
      
      return true;
    }   
    
    if (methodName == VM_MagicNames.getFramePointer) {
      asm.emitLEA_Reg_RegDisp(S0, SP, fp2spOffset(0));
      asm.emitPUSH_Reg       (S0);
      return true;
    }
    
    if (methodName == VM_MagicNames.getCallerFramePointer) {
      asm.emitPOP_Reg(T0);                                       // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_FRAME_POINTER_OFFSET); // Caller FP
      return true;
    }

    if (methodName == VM_MagicNames.setCallerFramePointer) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_FRAME_POINTER_OFFSET, T0); // [S0+SFPO] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.getCompiledMethodID) {
      asm.emitPOP_Reg(T0);                                   // Callee FP
      asm.emitPUSH_RegDisp(T0, STACKFRAME_METHOD_ID_OFFSET); // Callee CMID
      return true;
    }

    if (methodName == VM_MagicNames.setCompiledMethodID) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, STACKFRAME_METHOD_ID_OFFSET, T0); // [S0+SMIO] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.getReturnAddressLocation) {
      asm.emitPOP_Reg(T0);                                        // Callee FP
      asm.emitADD_Reg_Imm(T0, STACKFRAME_RETURN_ADDRESS_OFFSET);  // location containing callee return address
      asm.emitPUSH_Reg(T0);                                       // Callee return address
      return true;
    }
    
    if (methodName == VM_MagicNames.getTocPointer ||
        methodName == VM_MagicNames.getJTOC ) {
      asm.emitPUSH_Reg(JTOC);
      return true;
    }
    
    // get the processor register (PR)
    if (methodName == VM_MagicNames.getProcessorRegister) {
      asm.emitPUSH_Reg(PR);
      return true;
    }  

    // set the processor register (PR)
    if (methodName == VM_MagicNames.setProcessorRegister) {
      asm.emitPOP_Reg(PR);
      return true;
    }
    
    // Get the value in ESI 
    if (methodName == VM_MagicNames.getESIAsProcessor) {
      asm.emitPUSH_Reg(ESI);
      return true;
    }  

    // Set the value in ESI
    if (methodName == VM_MagicNames.setESIAsProcessor) {
      asm.emitPOP_Reg(ESI);
      return true;
    }
  
    if (methodName == VM_MagicNames.getIntAtOffset ||
        methodName == VM_MagicNames.getWordAtOffset || 
        methodName == VM_MagicNames.getObjectAtOffset ||
        methodName == VM_MagicNames.getObjectArrayAtOffset ||
        methodName == VM_MagicNames.prepareInt ||
        methodName == VM_MagicNames.prepareObject ||
        methodName == VM_MagicNames.prepareAddress ||
        methodName == VM_MagicNames.prepareWord) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return true;
    }
    
    if (methodName == VM_MagicNames.getByteAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitMOVZX_Reg_RegIdx_Byte(T0, T0, S0, asm.BYTE, 0); // load and zero extend byte [T0+S0]
      asm.emitPUSH_Reg (T0);
      return true;
    }

    if (methodName == VM_MagicNames.getCharAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitMOVZX_Reg_RegIdx_Word(T0, T0, S0, asm.BYTE, 0); // load and zero extend char [T0+S0]
      asm.emitPUSH_Reg (T0);
      return true;
    }
    
    if (methodName == VM_MagicNames.setIntAtOffset ||
        methodName == VM_MagicNames.setWordAtOffset || 
        methodName == VM_MagicNames.setObjectAtOffset ) {
      if (m.getParameterTypes().length == 4) { 
        // discard locationMetadata parameter
        asm.emitPOP_Reg(T0); // locationMetadata, not needed by baseline compiler.
      }
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      return true;
    }
    
    if (methodName == VM_MagicNames.setByteAtOffset) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg_Byte(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- (byte) T0
      return true;
    }
    
    if (methodName == VM_MagicNames.setCharAtOffset) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg_Word(T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- (char) T0
      return true;
    }
    
    if (methodName == VM_MagicNames.getLongAtOffset ||
        methodName == VM_MagicNames.getDoubleAtOffset) {
      asm.emitPOP_Reg (T0);                  // object ref
      asm.emitPOP_Reg (S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 4); // pushes [T0+S0+4]
      asm.emitPUSH_RegIdx(T0, S0, asm.BYTE, 0); // pushes [T0+S0]
      return true;
    }
    
    if (methodName == VM_MagicNames.setLongAtOffset ||
        methodName == VM_MagicNames.setDoubleAtOffset) {
      asm.emitMOV_Reg_RegInd (T0, SP);          // value high
      asm.emitMOV_Reg_RegDisp(S0, SP, +8 );     // offset
      asm.emitMOV_Reg_RegDisp(T1, SP, +12);     // obj ref
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 0, T0); // [T1+S0] <- T0
      asm.emitMOV_Reg_RegDisp(T0, SP, +4 );     // value low
      asm.emitMOV_RegIdx_Reg (T1, S0, asm.BYTE, 4, T0); // [T1+S0+4] <- T0
      asm.emitADD_Reg_Imm    (SP, WORDSIZE * 4); // pop stack locations
      return true;
    }

    if (methodName == VM_MagicNames.addressArrayCreate) {
      // no resolution problem possible.
      emit_resolved_newarray(m.getType().resolve().asArray());
      return true;
    }

    if (methodName == VM_MagicNames.addressArrayLength) {
      emit_arraylength();  // argument order already correct
      return true;
    }

    if (methodName == VM_MagicNames.addressArrayGet) {
      if (m.getType() == VM_TypeReference.CodeArray) {
        emit_baload();
      } else {
        if (VM.BuildFor32Addr) {
          emit_iaload();   
        } else if (VM.BuildFor64Addr) {
          emit_laload();
        } else {
          VM._assert(NOT_REACHED);
        }
      }
      return true;
    }

    if (methodName == VM_MagicNames.addressArraySet) {
      if (m.getType() == VM_TypeReference.CodeArray) {
        emit_bastore();
      } else {
        if (VM.BuildFor32Addr) {
          emit_iastore();  
        } else if (VM.BuildFor64Addr) {
          VM._assert(false);  // not implemented
        } else {
          VM._assert(NOT_REACHED);
        }
      }
      return true;
    }

    if (methodName == VM_MagicNames.getMemoryInt ||
        methodName == VM_MagicNames.getMemoryWord ||
        methodName == VM_MagicNames.getMemoryAddress) {
      asm.emitPOP_Reg(T0);      // address
      asm.emitPUSH_RegInd(T0); // pushes [T0+0]
      return true;
    }

    if (methodName == VM_MagicNames.setMemoryInt ||
        methodName == VM_MagicNames.setMemoryWord ||
        methodName == VM_MagicNames.setMemoryAddress) {
      if (m.getParameterTypes().length == 3) {
        // discard locationMetadata parameter
        asm.emitPOP_Reg(T0); // locationMetadata, not needed by baseline compiler.
      }
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // address
      asm.emitMOV_RegInd_Reg(S0,T0); // [S0+0] <- T0
      return true;
    }
    
    if (methodName == VM_MagicNames.objectAsAddress         ||
        methodName == VM_MagicNames.addressAsByteArray      ||
        methodName == VM_MagicNames.addressAsIntArray       ||
        methodName == VM_MagicNames.addressAsObject         ||
        methodName == VM_MagicNames.addressAsObjectArray    ||
        methodName == VM_MagicNames.addressAsType           ||
        methodName == VM_MagicNames.objectAsType            ||
        methodName == VM_MagicNames.objectAsShortArray      ||
        methodName == VM_MagicNames.objectAsByteArray       ||
        methodName == VM_MagicNames.objectAsIntArray       ||
        methodName == VM_MagicNames.addressAsThread         ||
        methodName == VM_MagicNames.objectAsThread          ||
        methodName == VM_MagicNames.objectAsProcessor       ||
        methodName == VM_MagicNames.threadAsCollectorThread ||
        methodName == VM_MagicNames.addressAsRegisters      ||
        methodName == VM_MagicNames.addressAsStack          ||
        methodName == VM_MagicNames.floatAsIntBits          ||
        methodName == VM_MagicNames.intBitsAsFloat          ||
        methodName == VM_MagicNames.doubleAsLongBits        ||
        methodName == VM_MagicNames.longBitsAsDouble)
      {
        // no-op (a type change, not a representation change)
        return true;
      }
    
    // code for      VM_Type VM_Magic.getObjectType(Object object)
    if (methodName == VM_MagicNames.getObjectType) {
      asm.emitPOP_Reg (T0);                               // object ref
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      asm.emitPUSH_RegDisp(S0, TIB_TYPE_INDEX<<LG_WORDSIZE); // push VM_Type slot of TIB
      return true;
    }
    
    if (methodName == VM_MagicNames.getArrayLength) {
      asm.emitPOP_Reg(T0);                      // object ref
      asm.emitPUSH_RegDisp(T0, VM_ObjectModel.getArrayLengthOffset()); 
      return true;
    }
    
    if (methodName == VM_MagicNames.sync) {  // nothing required on IA32
      return true;
    }
    
    if (methodName == VM_MagicNames.isync) { // nothing required on IA32
      return true;
    }
    
    // baseline compiled invocation only: all paramaters on the stack
    // hi mem
    //      Code
    //      GPRs
    //      FPRs
    //      Spills
    // low-mem
    if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return true;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
      return true;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 4);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
      return true;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm  (SP, 8);
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      return true;
    }                 

    if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      int offset = VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(4); // pass 4 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return true;
    }                 

    // baseline invocation
    // one paramater, on the stack  -- actual code
    if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM._assert(klass.isDynamicBridge());

      // save the branch address for later
      asm.emitPOP_Reg (S0);             // S0<-code address

      asm.emitADD_Reg_Imm(SP, fp2spOffset(0) - 4); // just popped 4 bytes above.

      // restore FPU state
      asm.emitFRSTOR_RegDisp(SP, FPU_SAVE_OFFSET);

      // restore GPRs
      asm.emitMOV_Reg_RegDisp (T0,  SP, T0_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (T1,  SP, T1_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET); 
      asm.emitMOV_Reg_RegDisp (JTOC,  SP, JTOC_SAVE_OFFSET); 

      // pop frame
      asm.emitPOP_RegDisp (PR, VM_Entrypoints.framePointerField.getOffset()); // FP<-previous FP 

      // branch
      asm.emitJMP_Reg (S0);
      return true;
    }
                                                  
    if (methodName == VM_MagicNames.returnToNewStack) {
      // SP gets frame pointer for new stack
      asm.emitPOP_Reg (SP);     

      // restore nonvolatile registers
      asm.emitMOV_Reg_RegDisp (JTOC, SP, JTOC_SAVE_OFFSET);
      asm.emitMOV_Reg_RegDisp (EBX, SP, EBX_SAVE_OFFSET);

      // discard current stack frame
      asm.emitPOP_RegDisp (PR, VM_Entrypoints.framePointerField.getOffset());

      // return to caller- pop parameters from stack
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);    
      return true;
    }

    if (methodName == VM_MagicNames.roundToZero) {
      // Store the FPU Control Word to a JTOC slot
      asm.emitFNSTCW_RegDisp(JTOC, VM_Entrypoints.FPUControlWordField.getOffset());
      // Set the bits in the status word that control round to zero.
      // Note that we use a 32-bit OR, even though we only care about the
      // low-order 16 bits
      asm.emitOR_RegDisp_Imm(JTOC,VM_Entrypoints.FPUControlWordField.getOffset(), 0x00000c00);
      // Now store the result back into the FPU Control Word
      asm.emitFLDCW_RegDisp(JTOC,VM_Entrypoints.FPUControlWordField.getOffset());
      return true;
    }
    if (methodName == VM_MagicNames.clearFloatingPointState) {
      // Clear the hardware floating-point state
      asm.emitFNINIT();
      return true;
    }
    
    if (methodName == VM_MagicNames.getTimeBase) {
      asm.emitRDTSC();       // read timestamp counter instruction
      asm.emitPUSH_Reg(EDX); // upper 32 bits
      asm.emitPUSH_Reg(EAX); // lower 32 bits
      return true;
    }

    if (methodName == VM_MagicNames.wordFromInt ||
        methodName == VM_MagicNames.wordFromIntZeroExtend ||
        methodName == VM_MagicNames.wordFromIntSignExtend ||
        methodName == VM_MagicNames.wordToInt ||
        methodName == VM_MagicNames.wordToAddress ||
        methodName == VM_MagicNames.wordToOffset ||
        methodName == VM_MagicNames.wordToExtent ||
        methodName == VM_MagicNames.wordToWord) {
        if (VM.BuildFor32Addr) return true;     // no-op for 32-bit
        if (VM.VerifyAssertions) VM._assert(false);
    }

    if (methodName == VM_MagicNames.wordToLong) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg(T0);
        asm.emitPUSH_Imm(0); // upper 32 bits
        asm.emitPUSH_Reg(T0); // lower 32 bits
        return true;
      } // else fill unused stackslot 
      if (VM.VerifyAssertions) VM._assert(false);
    }

    if (methodName == VM_MagicNames.wordAnd) {
        asm.emitPOP_Reg(T0);
        asm.emitAND_RegInd_Reg(SP, T0);
        return true;
    }

    if (methodName == VM_MagicNames.wordOr) {
        asm.emitPOP_Reg(T0);
        asm.emitOR_RegInd_Reg(SP, T0);
        return true;
    }

    if (methodName == VM_MagicNames.wordXor) {
        asm.emitPOP_Reg(T0);
        asm.emitXOR_RegInd_Reg(SP, T0);
        return true;
    }

    if (methodName == VM_MagicNames.wordNot) {
        asm.emitNOT_RegInd (SP);
        return true;
    }

    if (methodName == VM_MagicNames.wordAdd) {
        asm.emitPOP_Reg(T0);
        asm.emitADD_RegInd_Reg(SP, T0);
        return true;
    }

    if (methodName == VM_MagicNames.wordSub ||
        methodName == VM_MagicNames.wordDiff) {
        asm.emitPOP_Reg(T0);
        asm.emitSUB_RegInd_Reg(SP, T0);
        return true;
    }

    if (methodName == VM_MagicNames.wordZero) {
        asm.emitPUSH_Imm(0);
        return true;
    }

    if (methodName == VM_MagicNames.wordOne) {
        asm.emitPUSH_Imm(1);
        return true;
    }

    if (methodName == VM_MagicNames.wordMax) {
        asm.emitPUSH_Imm(-1);
        return true;
    }

    if (methodName == VM_MagicNames.wordLT) {
        generateAddrComparison(asm.LLT);
        return true;
    }
    if (methodName == VM_MagicNames.wordLE) {
        generateAddrComparison(asm.LLE);
        return true;
    }
    if (methodName == VM_MagicNames.wordGT) {
        generateAddrComparison(asm.LGT);
        return true;
    }
    if (methodName == VM_MagicNames.wordGE) {
        generateAddrComparison(asm.LGE);
        return true;
    }
    if (methodName == VM_MagicNames.wordsLT) {
        generateAddrComparison(asm.LT);
        return true;
    }
    if (methodName == VM_MagicNames.wordsLE) {
        generateAddrComparison(asm.LE);
        return true;
    }
    if (methodName == VM_MagicNames.wordsGT) {
        generateAddrComparison(asm.GT);
        return true;
    }
    if (methodName == VM_MagicNames.wordsGE) {
        generateAddrComparison(asm.GE);
        return true;
    }
    if (methodName == VM_MagicNames.wordEQ) {
        generateAddrComparison(asm.EQ);
        return true;
    }
    if (methodName == VM_MagicNames.wordNE) {
        generateAddrComparison(asm.NE);
        return true;
    }
    if (methodName == VM_MagicNames.wordIsZero) {
        asm.emitPUSH_Imm(0);
        generateAddrComparison(asm.EQ);
        return true;
    }
    if (methodName == VM_MagicNames.wordIsMax) {
        asm.emitPUSH_Imm(-1);
        generateAddrComparison(asm.EQ);
        return true;
    }
    if (methodName == VM_MagicNames.wordLsh) {
        if (VM.BuildFor32Addr) {
          asm.emitPOP_Reg(ECX);
          asm.emitSHL_RegInd_Reg(SP, ECX);   
        }
        else
          VM._assert(false);  
        return true;
    }
    if (methodName == VM_MagicNames.wordRshl) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg (ECX);
        asm.emitSHR_RegInd_Reg (SP, ECX);  
      }
      else
        VM._assert(false);  
      return true;
    }
    if (methodName == VM_MagicNames.wordRsha) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg (ECX);
        asm.emitSAR_RegInd_Reg (SP, ECX);  
      }
      else
        VM._assert(false);  
      return true;
    }
    return false;
    
  }

  private void generateAddrComparison(byte comparator) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    asm.emitSET_Cond_Reg_Byte(comparator, T0);
    asm.emitMOVZX_Reg_Reg_Byte(T0, T0);   // Clear upper 3 bytes
    asm.emitPUSH_Reg(T0);
  }

  // Offset of Java local variable (off stack pointer)
  // assuming ESP is still positioned as it was at the 
  // start of the current bytecode (biStart)
  private final int localOffset  (int local) {
    return (stackHeights[biStart] - local)<<LG_WORDSIZE;
  }

  // Translate a FP offset into an SP offset 
  // assuming ESP is still positioned as it was at the 
  // start of the current bytecode (biStart)
  private final int fp2spOffset(int offset) {
    int offsetToFrameHead = (stackHeights[biStart] << LG_WORDSIZE) - firstLocalOffset;
    return offsetToFrameHead + offset;
  }

  private void emitDynamicLinkingSequence(byte reg, VM_MemberReference ref, boolean couldBeZero) {
    int memberId = ref.getId();
    int memberOffset = memberId << 2;
    int tableOffset = VM_Entrypoints.memberOffsetsField.getOffset();
    if (couldBeZero) {
      int resolverOffset = VM_Entrypoints.resolveMemberMethod.getOffset();
      int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
      asm.emitMOV_Reg_RegDisp (reg, JTOC, tableOffset);      // reg is offsets table
      asm.emitMOV_Reg_RegDisp (reg, reg, memberOffset);      // reg is offset of member, or 0 if member's class isn't loaded
      if (NEEDS_DYNAMIC_LINK == 0) {
        asm.emitTEST_Reg_Reg(reg, reg);                      // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      } else {
        asm.emitCMP_Reg_Imm(reg, NEEDS_DYNAMIC_LINK);        // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      }
      VM_ForwardReference fr = asm.forwardJcc(asm.NE);       // if so, skip call instructions
      asm.emitPUSH_Imm(memberId);                            // pass member's dictId
      genParameterRegisterLoad(1);                           // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, resolverOffset);            // does class loading as sideffect
      asm.emitJMP_Imm (retryLabel);                          // reload reg with valid value
      fr.resolve(asm);                                       // come from Jcc above.
    } else {
      asm.emitMOV_Reg_RegDisp (reg, JTOC, tableOffset);      // reg is offsets table
      asm.emitMOV_Reg_RegDisp (reg, reg, memberOffset);      // reg is offset of member
    }
  }

  //-#if RVM_WITH_OSR
  /**
   * Emit code to invoke a compiled method (with known jtoc offset).
   * Treat it like a resolved invoke static, but take care of
   * this object in the case.
   *
   * I havenot thought about GCMaps for invoke_compiledmethod 
   */
  protected final void emit_invoke_compiledmethod(VM_CompiledMethod cm) {
    int methodOffset = cm.getOsrJTOCoffset();
    boolean takeThis = !cm.method.isStatic();
    VM_MethodReference ref = cm.method.getMemberRef().asMethodReference();
    genParameterRegisterLoad(ref, takeThis);
    asm.emitCALL_RegDisp(JTOC, methodOffset);
    genResultRegisterUnload(ref);
  }

  protected final void emit_loadaddrconst(int bcIndex) {
    asm.registerLoadAddrConst(bcIndex);
    asm.emitPUSH_Imm(bcIndex);
  }

  /* bTarget is optional, it emits a JUMP instruction, but the caller
   * in resposible for patch the target address by call resolve method
   * of returned forward reference
   */
  protected final VM_ForwardReference emit_pending_goto(int bTarget) {
    return asm.generatePendingJMP(bTarget);
  }

  //-#endif
}
