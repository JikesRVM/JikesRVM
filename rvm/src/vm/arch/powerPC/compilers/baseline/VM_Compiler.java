/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.jni.*;

import org.vmmagic.pragma.*;

/**
 * VM_Compiler is the baseline compiler class for powerPC architectures.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @author Kris Venstermans
 * @author Perry Cheng
 * @modified Daniel Frampton
 */
public class VM_Compiler extends VM_BaselineCompiler 
  implements VM_BaselineConstants,
             VM_JNIStackframeLayoutConstants, 
             VM_AssemblerConstants {

  // stackframe pseudo-constants //
  private int frameSize;            
  private int emptyStackOffset;
  private int startLocalOffset;
  private int spillOffset;

  // current offset of the sp from fp
  int spTopOffset;

  // If we're doing a short forward jump of less than 
  // this number of bytecodes, then we can always use a short-form
  // conditional branch (don't have to emit a nop & bc).
  private static final int SHORT_FORWARD_LIMIT = 500;

  /**
   * Create a VM_Compiler object for the compilation of method.
   */
  VM_Compiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    if (VM.VerifyAssertions) VM._assert(T6 <= LAST_VOLATILE_GPR);           // need 4 gp temps
    if (VM.VerifyAssertions) VM._assert(F3 <= LAST_VOLATILE_FPR);           // need 4 fp temps
    if (VM.VerifyAssertions) VM._assert(S0 < S1 && S1 <= LAST_SCRATCH_GPR); // need 2 scratch
    stackHeights = new int[bcodes.length()];
    frameSize        = getFrameSize(method);
    startLocalOffset = getStartLocalOffset(method);
    emptyStackOffset = getEmptyStackOffset(method);
  }


  //----------------//
  // more interface //
  //----------------//
  
  // position of spill area within method's stackframe.
  public static int getMaxSpillOffset (VM_NormalMethod m) throws UninterruptiblePragma {
    int params = m.getOperandWords()<<LOG_BYTES_IN_STACKSLOT; // maximum parameter area
    int spill  = params - (MIN_PARAM_REGISTERS << LOG_BYTES_IN_STACKSLOT);
    if (spill < 0) spill = 0;
    return STACKFRAME_HEADER_SIZE + spill - BYTES_IN_STACKSLOT;
  }
  
  // position of operand stack within method's stackframe.
  public static int getEmptyStackOffset (VM_NormalMethod m) throws UninterruptiblePragma {
    int stack = m.getOperandWords()<<LOG_BYTES_IN_STACKSLOT; // maximum stack size
    return getMaxSpillOffset(m) + stack + BYTES_IN_STACKSLOT; // last local
  }
  
  // position of locals within method's stackframe.
  public static int getFirstLocalOffset (VM_NormalMethod m) throws UninterruptiblePragma {
    return getStartLocalOffset(m) - BYTES_IN_STACKSLOT;
  }
  
  // start position of locals within method's stackframe.
  public static int getStartLocalOffset (VM_NormalMethod m) throws UninterruptiblePragma {
    int locals = m.getLocalWords()<<LOG_BYTES_IN_STACKSLOT;       // input param words + pure locals
    return getEmptyStackOffset(m) + locals; // bottom-most local
  }
  
  // size of method's stackframe.
  public static int getFrameSize (VM_NormalMethod m) throws UninterruptiblePragma {
    int size = getStartLocalOffset(m);
    if (m.getDeclaringClass().isDynamicBridge()) {
      size += (LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) << LOG_BYTES_IN_DOUBLE;
      size += (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) << LOG_BYTES_IN_ADDRESS;
    }
    if (VM.BuildFor32Addr) {
      size = VM_Memory.alignUp(size , STACKFRAME_ALIGNMENT);
    }
    return size;
  }

  public static int getFrameSize (VM_NativeMethod m) throws UninterruptiblePragma {
    // space for:
    //   -NATIVE header (AIX 6 words, LINUX 2 words)
    //   -parameters and 2 extra JNI parameters (jnienv + obj), minimum 8 words
    //   -JNI_SAVE_AREA_OFFSET; see VM_JNIStackframeLayoutConstants
    int argSpace = BYTES_IN_STACKSLOT * (m.getParameterWords()+ 2);
    if (argSpace < 32)
      argSpace = 32;
    int size = NATIVE_FRAME_HEADER_SIZE + argSpace +
      com.ibm.JikesRVM.jni.VM_JNIStackframeLayoutConstants.JNI_SAVE_AREA_SIZE;
    if (VM.BuildFor32Addr) {
      size = VM_Memory.alignUp(size , STACKFRAME_ALIGNMENT);
    }
    return size;
  }

  /*
   * implementation of abstract methods of VM_BaselineCompiler
   */

  /*
   * Misc routines not directly tied to a particular bytecode
   */

  /**
   * About to start generating code for bytecode biStart.
   * Perform any platform specific setup
   */
  protected final void starting_bytecode() {
    spTopOffset = startLocalOffset - BYTES_IN_STACKSLOT - (stackHeights[biStart] * BYTES_IN_STACKSLOT);
  }

  /**
   * Emit the prologue for the method
   */
  protected final void emit_prologue() {
    spTopOffset = emptyStackOffset;
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
  protected final boolean emit_Magic(VM_MethodReference magicMethod) {
    return generateInlineCode(magicMethod);
  }

  /*
   * Helper functions for expression stack manipulation
   */
  private final void discardSlot() {
    spTopOffset += BYTES_IN_STACKSLOT;
  }

  private final void discardSlots(int n) {
    spTopOffset += n * BYTES_IN_STACKSLOT;
  }


  /**
   * Emit the code to push an intlike (boolean, byte, char, short, int) value 
   * contained in 'reg' onto the expression stack
   * @param reg register containing the value to push
   */
  private final void pushInt(int reg) {
    asm.emitSTW (reg, spTopOffset - BYTES_IN_INT, FP);
    spTopOffset -= BYTES_IN_STACKSLOT;
  }

  /**
   * Emit the code to push a float value 
   * contained in 'reg' onto the expression stack
   * @param reg register containing the value to push
   */
  private final void pushFloat(int reg) {
    asm.emitSTFS (reg, spTopOffset - BYTES_IN_FLOAT, FP);
    spTopOffset -= BYTES_IN_STACKSLOT;
  }
  
  /**
   * Emit the code to push a double value 
   * contained in 'reg' onto the expression stack
   * @param reg register containing the value to push
   */
  private final void pushDouble(int reg) {
    asm.emitSTFD (reg, spTopOffset -BYTES_IN_DOUBLE, FP);
    spTopOffset -= 2*BYTES_IN_STACKSLOT;
  }

  /**
   * Emit the code to push a double value 
   * contained in 'reg' onto the expression stack
   * @param reg register containing the value to push
   */
  private final void pushLowDoubleAsInt(int reg) {
    asm.emitSTFD (reg, spTopOffset -BYTES_IN_DOUBLE, FP);
    spTopOffset -= BYTES_IN_STACKSLOT;
  }

  /**
   * Emit the code to push a long value 
   * contained in 'reg1' and 'reg2' onto the expression stack
   * @param reg1 register containing,  the most significant 32 bits to push on 32bit arch (to lowest address), not used on 64bit
   * @param reg2 register containing,  the least significant 32 bits on 32bit arch (to highest address), the whole value on 64bit
   */
  private final void pushLong(int reg1, int reg2) {
    if (VM.BuildFor64Addr) {
      asm.emitSTD (reg2,  spTopOffset -BYTES_IN_LONG, FP);
    } else {
      asm.emitSTW (reg2,  spTopOffset -BYTES_IN_STACKSLOT, FP);
      asm.emitSTW (reg1, spTopOffset -2*BYTES_IN_STACKSLOT, FP);
    }
    spTopOffset -= 2 * BYTES_IN_STACKSLOT;
  }

  /**
   * Emit the code to push a long value 
   * contained in 'reg' onto the expression stack.
   * NOTE: in 32 bit mode, reg is actually a FP register and
   * we are treating the long value as if it were an FP value to do this in
   * one instruction!!!
   * @param reg register containing the value to push
   */
  private final void pushLongAsDouble(int reg) {
    asm.emitSTFD (reg, spTopOffset -BYTES_IN_LONG, FP);
    spTopOffset -= 2 * BYTES_IN_STACKSLOT;
  }

  /**
   * Emit the code to push a reference/address value 
   * contained in 'reg' onto the expression stack
   * @param reg register containing the value to push
   */
  private final void pushAddr(int reg) {
    asm.emitSTAddr(reg, spTopOffset -BYTES_IN_ADDRESS, FP);
    spTopOffset -= BYTES_IN_STACKSLOT;
  }
  
  /**
   * Emit the code to poke an address 
   * contained in 'reg' onto the expression stack on position idx.
   * @param reg register to peek the value into
   */
  private final void pokeAddr(int reg, int idx) {
    asm.emitSTAddr(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_ADDRESS + (idx<<LOG_BYTES_IN_STACKSLOT), FP);
  }

  /**
   * Emit the code to poke an int 
   * contained in 'reg' onto the expression stack on position idx.
   * @param reg register to peek the value into
   */
  private final void pokeInt(int reg, int idx) {
    asm.emitSTW(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_INT + (idx<<LOG_BYTES_IN_STACKSLOT), FP);
  }

  /**
   * Emit the code to pop a char value from the expression stack into 
   * the register 'reg' as an int.
   * @param reg register to pop the value into
   */
  private final void popCharAsInt(int reg) {
    asm.emitLHZ(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_CHAR, FP);
    discardSlot();
  }

  /**
   * Emit the code to pop a short value from the expression stack into 
   * the register 'reg' as an int.
   * @param reg register to pop the value into
   */
  private final void popShortAsInt(int reg) {
    asm.emitLHA(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_SHORT, FP);
    discardSlot();
  }
  
  /**
   * Emit the code to pop a byte value from the expression stack into 
   * the register 'reg' as an int.
   * @param reg register to pop the value into
   */
  private final void popByteAsInt(int reg) {
    asm.emitLWZ(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_INT, FP);
    asm.emitEXTSB(reg,reg);
    discardSlot();
  }

  /**
   * Emit the code to pop an intlike (boolean, byte, char, short, int) value 
   * from the expression stack into the register 'reg'. Sign extend on 64 bit platform.
   * @param reg register to pop the value into
   */
  private final void popInt(int reg) {
    asm.emitLInt(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_INT, FP);
    discardSlot();
  }

  /**
   * Emit the code to pop a float value
   * from the expression stack into the register 'reg'.
   * @param reg register to pop the value into
   */
  private final void popFloat(int reg) {
    asm.emitLFS (reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_FLOAT, FP);
    discardSlot();
  }

  /**
   * Emit the code to pop a double value
   * from the expression stack into the register 'reg'.
   * @param reg register to pop the value into
   */
  private final void popDouble(int reg) {
    asm.emitLFD(reg, spTopOffset + 2*BYTES_IN_STACKSLOT - BYTES_IN_DOUBLE, FP);
    discardSlots(2);
  }

  /**
   * Emit the code to push a long value 
   * contained in 'reg1' and 'reg2' onto the expression stack
   * @param reg1 register to pop,  the most significant 32 bits on 32bit arch (lowest address), not used on 64bit
   * @param reg2 register to pop,  the least significant 32 bits on 32bit arch (highest address), the whole value on 64bit
   */
  private final void popLong(int reg1, int reg2) {
    if (VM.BuildFor64Addr) {
      asm.emitLD (reg2,  spTopOffset + 2*BYTES_IN_STACKSLOT - BYTES_IN_LONG, FP);
    } else {
      asm.emitLWZ (reg1,  spTopOffset, FP);
      asm.emitLWZ (reg2,  spTopOffset + BYTES_IN_STACKSLOT, FP);
    }
    discardSlots(2);
  }

  /**
   * Emit the code to pop a long value
   * from the expression stack into the register 'reg'.
   * NOTE: in 32 bit mode, reg is actually a FP register and
   * we are treating the long value as if it were an FP value to do this in
   * one instruction!!!
   * @param reg register to pop the value into
   */
  private final void popLongAsDouble(int reg) {
    asm.emitLFD(reg, spTopOffset + 2*BYTES_IN_STACKSLOT - BYTES_IN_DOUBLE, FP);
    discardSlots(2);
  }

  /**
   * Emit the code to pop a reference/address value
   * from the expression stack into the register 'reg'.
   * @param reg register to pop the value into
   */
  private final void popAddr(int reg) {
    asm.emitLAddr(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_ADDRESS, FP);
    discardSlot();
  }

  /**
   * Emit the code to peek an intlike (boolean, byte, char, short, int) value 
   * from the expression stack into the register 'reg'.
   * @param reg register to peek the value into
   */
  final void peekInt(int reg, int idx) {
    asm.emitLInt(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_INT + (idx<<LOG_BYTES_IN_STACKSLOT), FP);
  }

  /**
   * Emit the code to peek a float value
   * from the expression stack into the register 'reg'.
   * @param reg register to peek the value into
   */
  private final void peekFloat(int reg, int idx) {
    asm.emitLFS(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_FLOAT + (idx<<LOG_BYTES_IN_STACKSLOT), FP);
  }

  /**
   * Emit the code to peek a double value
   * from the expression stack into the register 'reg'.
   * @param reg register to peek the value into
   */
  private final void peekDouble(int reg, int idx) {
    asm.emitLFD(reg, spTopOffset + 2*BYTES_IN_STACKSLOT - BYTES_IN_DOUBLE + (idx << LOG_BYTES_IN_STACKSLOT), FP);
  }

  /**
   * Emit the code to peek a long value 
   * from the expression stack into 'reg1' and 'reg2'.
   * @param reg1 register to peek,  the most significant 32 bits on 32bit arch (lowest address), not used on 64bit
   * @param reg2 register to peek,  the least significant 32 bits on 32bit arch (highest address), the whole value on 64bit
   */
  private final void peekLong(int reg1, int reg2, int idx) {
    if (VM.BuildFor64Addr) {
      asm.emitLD (reg2, spTopOffset + 2*BYTES_IN_STACKSLOT - BYTES_IN_LONG + (idx << LOG_BYTES_IN_STACKSLOT), FP);
    } else {
      asm.emitLWZ (reg1,  spTopOffset + (idx << LOG_BYTES_IN_STACKSLOT), FP);
      asm.emitLWZ (reg2,  spTopOffset + BYTES_IN_STACKSLOT + (idx << LOG_BYTES_IN_STACKSLOT), FP);
    }
  }

  /**
   * Emit the code to peek a reference/address value
   * from the expression stack into the register 'reg'.
   * @param reg register to peek the value into
   */
  final void peekAddr(int reg, int idx) {
    asm.emitLAddr(reg, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_ADDRESS + (idx<<LOG_BYTES_IN_STACKSLOT), FP);
  }

  
  /* 
   * Helper functions for manipulating local variables
   */

  /**
   * Emit the code to store an intlike (boolean, byte, char, short, int) value 
   * from 'reg' into local number 'index'
   * @param reg the register containing the value to store
   * @param index the local index in which to store it.
   */
  private final void storeIntLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_INT;
    asm.emitSTW(reg, offset, FP);
  }    

  /**
   * Emit the code to store a float value
   * from 'reg' into local number 'index'
   * @param reg the register containing the value to store
   * @param index the local index in which to store it.
   */
  private final void storeFloatLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_FLOAT;
    asm.emitSTFS(reg, offset, FP);
  }    

  /**
   * Emit the code to store a double value
   * from 'reg' into local number 'index'
   * @param reg the register containing the value to store
   * @param index the local index in which to store it.
   */
  private final void storeDoubleLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_DOUBLE;
    asm.emitSTFD(reg, offset, FP);
  }    

  /**
   * Emit the code to store a long value
   * from 'reg' into local number 'index'
   * NOTE: in 32 bit mode, reg is actually a FP register and
   * we are treating the long value as if it were an FP value to do this in
   * one instruction!!!
   * @param reg the register containing the value to store
   * @param index the local index in which to store it.
   */
  private final void storeLongLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_LONG;
    asm.emitSTFD(reg, offset, FP);
  }    

  /**
   * Emit the code to store a reference/address value
   * from 'reg' into local number 'index'
   * @param reg the register containing the value to store
   * @param index the local index in which to store it.
   */
  private final void storeAddrLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_ADDRESS;
    asm.emitSTAddr(reg, offset, FP);
  }    
  
  /**
   * Emit the code to load an intlike (boolean, byte, char, short, int) value 
   * from local number 'index' into 'reg'
   * @param reg the register to load the value into
   * @param index the local index from which to load it.
   */
  private final void loadIntLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_INT;
    asm.emitLInt(reg, offset, FP);
  }

  /**
   * Emit the code to load a float value
   * from local number 'index' into 'reg'
   * @param reg the register to load the value into
   * @param index the local index from which to load it.
   */
  private final void loadFloatLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_FLOAT;
    asm.emitLFS(reg, offset, FP);
  }

  /**
   * Emit the code to load an double value
   * from local number 'index' into 'reg'
   * @param reg the register to load the value into
   * @param index the local index from which to load it.
   */
  private final void loadDoubleLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_DOUBLE;
    asm.emitLFD  (reg, offset, FP);
  }

  /**
   * Emit the code to load a long value
   * from local number 'index' into 'reg'
   * NOTE: in 32 bit mode, reg is actually a FP register and
   * we are treating the long value as if it were an FP value to do this in
   * one instruction!!!
   * @param reg the register to load the value into
   * @param index the local index from which to load it.
   */
  private final void loadLongLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_LONG;
    asm.emitLFD  (reg, offset, FP);
  }

  /**
   * Emit the code to load a reference/address
   * from local number 'index' into 'reg'
   * @param reg the register to load the value into
   * @param index the local index from which to load it.
   */
  private final void loadAddrLocal(int reg, int index) {
    int offset = localOffset(index) - BYTES_IN_ADDRESS;
    asm.emitLAddr(reg, offset, FP);
  }


  /*
   * Loading constants
   */


  /**
   * Emit code to load the null constant.
   */
  protected final void emit_aconst_null() {
    asm.emitLVAL(T0,  0);
    pushAddr(T0);
  }

  /**
   * Emit code to load an int constant.
   * @param val the int constant to load
   */
  protected final void emit_iconst(int val) {
    asm.emitLVAL(T0, val);
    pushInt(T0);
  }

  /**
   * Emit code to load a long constant
   * @param val the lower 32 bits of long constant (upper32 are 0).
   */
  protected final void emit_lconst(int val) {
    if (val == 0) {
      asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    } else {
      if (VM.VerifyAssertions) VM._assert(val == 1);
      asm.emitLFDtoc(F0, VM_Entrypoints.longOneField.getOffset(), T0);
    }
    pushLongAsDouble(F0);
  }

  /**
   * Emit code to load 0.0f
   */
  protected final void emit_fconst_0() {
    asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    pushFloat(F0);
  }

  /**
   * Emit code to load 1.0f
   */
  protected final void emit_fconst_1() {
    asm.emitLFStoc(F0, VM_Entrypoints.oneFloatField.getOffset(), T0);
    pushFloat(F0);
  }

  /**
   * Emit code to load 2.0f
   */
  protected final void emit_fconst_2() {
    asm.emitLFStoc(F0, VM_Entrypoints.twoFloatField.getOffset(), T0);
    pushFloat(F0);
  }

  /**
   * Emit code to load 0.0d
   */
  protected final void emit_dconst_0() {
    asm.emitLFStoc(F0, VM_Entrypoints.zeroFloatField.getOffset(), T0);
    pushDouble(F0);
  }

  /**
   * Emit code to load 1.0d
   */
  protected final void emit_dconst_1() {
    asm.emitLFStoc(F0, VM_Entrypoints.oneFloatField.getOffset(), T0);
    pushDouble(F0);
  }

  /**
   * Emit code to load a 32 bit constant
   * (which may be a String and thus really 64 bits on 64 bit platform!)
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc(int offset) {
    if (VM_Statics.getSlotDescription(offset>>LOG_BYTES_IN_INT) == VM_Statics.STRING_LITERAL){
      asm.emitLAddrToc(T0,  offset);
      pushAddr(T0);
    } else {
      asm.emitLIntToc(T0, offset);
      pushInt(T0);
    }
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant 
   */
  protected final void emit_ldc2(int offset) {
    asm.emitLFDtoc(F0,  offset, T0);
    pushDouble(F0);
  }


  /*
   * loading local variables
   */


  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected final void emit_iload(int index) {
    loadIntLocal(T0, index);
    pushInt(T0);
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected final void emit_lload(int index) {
    loadLongLocal(F0, index);
    pushLongAsDouble(F0);
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected final void emit_fload(int index) {
    loadFloatLocal(F0, index);
    pushFloat(F0);
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected final void emit_dload(int index) {
    loadDoubleLocal(F0, index);
    pushDouble(F0);
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected final void emit_aload(int index) {
    loadAddrLocal(T0, index);
    pushAddr(T0);
  }


  /*
   * storing local variables
   */


  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected final void emit_istore(int index) {
    popInt(T0);
    storeIntLocal(T0, index);
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected final void emit_lstore(int index) {
    popLongAsDouble(F0);
    storeLongLocal(F0, index);
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected final void emit_fstore(int index) {
    popFloat(F0);
    storeFloatLocal(F0, index);
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected final void emit_dstore(int index) {
    popDouble(F0);
    storeDoubleLocal(F0, index);
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected final void emit_astore(int index) {
    popAddr(T0);
    storeAddrLocal(T0, index);
  }


  /*
   * array loads
   */


  /**
   * Emit code to load from an int array
   */
  protected final void emit_iaload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_INT);  // convert index to offset
    asm.emitLIntX(T2, T0, T1);  // load desired int array element
    pushInt(T2);
  }

  /**
   * Emit code to load from a long array
   */
  protected final void emit_laload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_LONG);  // convert index to offset
    asm.emitLFDX(F0, T0, T1);  // load desired (long) array element
    pushLongAsDouble(F0);
  }

  /**
   * Emit code to load from a float array
   */
  protected final void emit_faload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_FLOAT);  // convert index to offset
    asm.emitLWZX (T2, T0, T1);  // load desired (float) array element
    pushInt(T2);  //LFSX not implemented yet
//    asm.emitLFSX  (F0, T0, T1);  // load desired (float) array element
//    pushFloat(F0);
  }

  /**
   * Emit code to load from a double array
   */
  protected final void emit_daload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_DOUBLE);  // convert index to offset
    asm.emitLFDX (F0, T0, T1);  // load desired (double) array element
    pushDouble(F0);  
  }

  /**
   * Emit code to load from a reference array
   */
  protected final void emit_aaload() {
    genBoundsCheck();
    asm.emitSLWI  (T1, T1,  LOG_BYTES_IN_ADDRESS);  // convert index to offset
    asm.emitLAddrX(T2, T0, T1);  // load desired (ref) array element
    pushAddr(T2);
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected final void emit_baload() {
    genBoundsCheck();
    asm.emitLBZX(T2, T0, T1);  // no load byte algebraic ...
    asm.emitEXTSB(T2,T2);
    pushInt(T2);  
  }

  /**
   * Emit code to load from a char array
   */
  protected final void emit_caload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_CHAR);  // convert index to offset
    asm.emitLHZX(T2, T0, T1);  // load desired (char) array element
    pushInt(T2);  
  }

  /**
   * Emit code to load from a short array
   */
  protected final void emit_saload() {
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_SHORT);  // convert index to offset
    asm.emitLHAX(T2, T0, T1);  // load desired (short) array element
    pushInt(T2);  
  }


  /*
   * array stores
   */


  /**
   * Emit code to store to an int array
   */
  protected final void emit_iastore() {
    popInt(T2);      // T2 is value to store
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_INT);  // convert index to offset
    asm.emitSTWX (T2, T0, T1);  // store int value in array
  }

  /**
   * Emit code to store to a long array
   */
  protected final void emit_lastore() {
    popLongAsDouble(F0);                    // F0 is value to store
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_LONG);  // convert index to offset
    asm.emitSTFDX(F0, T0, T1);  // store long value in array
  }

  /**
   * Emit code to store to a float array
   */
  protected final void emit_fastore() {
    popInt(T2);      // T2 is value to store
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_FLOAT);  // convert index to offset
    asm.emitSTWX (T2, T0, T1);  // store float value in array
  }

  /**
   * Emit code to store to a double array
   */
  protected final void emit_dastore() {
    popDouble(F0);         // F0 is value to store
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_DOUBLE);  // convert index to offset
    asm.emitSTFDX(F0, T0, T1);  // store double value in array
  }

  /**
   * Emit code to store to a reference array
   */
  protected final void emit_aastore() {
    asm.emitLAddrToc(T0,  VM_Entrypoints.checkstoreMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T1, 0);    // T1 is value to store
    peekAddr(T0, 2);    // T0 is array ref 
    asm.emitBCCTRL();   // checkstore(arrayref, value)
    popAddr(T2);        // T2 is value to store
    genBoundsCheck();
    if (MM_Interface.NEEDS_WRITE_BARRIER) {
      VM_Barriers.compileArrayStoreBarrier(this);
    } else {
      asm.emitSLWI (T1, T1,  LOG_BYTES_IN_ADDRESS);  // convert index to offset
      asm.emitSTAddrX (T2, T0, T1);  // store ref value in array
    }
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected final void emit_bastore() {
    popInt(T2);      // T2 is value to store
    genBoundsCheck();
    asm.emitSTBX(T2, T0, T1);  // store byte value in array
  }

  /**
   * Emit code to store to a char array
   */
  protected final void emit_castore() {
    popInt(T2);      // T2 is value to store
    genBoundsCheck();
    asm.emitSLWI(T1, T1,  LOG_BYTES_IN_CHAR);  // convert index to offset
    asm.emitSTHX(T2, T0, T1);  // store char value in array
  }

  /**
   * Emit code to store to a short array
   */
  protected final void emit_sastore() {
    popInt(T2);      // T2 is value to store
    genBoundsCheck();
    asm.emitSLWI (T1, T1,  LOG_BYTES_IN_SHORT);  // convert index to offset
    asm.emitSTHX(T2, T0, T1);  // store short value in array
  }


  /*
   * expression stack manipulation
   */


  /**
   * Emit code to implement the pop bytecode
   */
  protected final void emit_pop() {
    discardSlot();
  }

  /**
   * Emit code to implement the pop2 bytecode
   */
  protected final void emit_pop2() {
    discardSlots(2);
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    peekAddr(T0, 0);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the dup_x1 bytecode
   */
  protected final void emit_dup_x1() {
    popAddr(T0);
    popAddr(T1);
    pushAddr(T0);
    pushAddr(T1);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the dup_x2 bytecode
   */
  protected final void emit_dup_x2() {
    popAddr(T0);
    popAddr(T1);
    popAddr(T2);
    pushAddr(T0);
    pushAddr(T2);
    pushAddr(T1);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the dup2 bytecode
   */
  protected final void emit_dup2() {
    peekAddr(T0, 0);
    peekAddr(T1, 1);
    pushAddr(T1);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the dup2_x1 bytecode
   */
  protected final void emit_dup2_x1() {
    popAddr(T0);
    popAddr(T1);
    popAddr(T2);
    pushAddr(T1);
    pushAddr(T0);
    pushAddr(T2);
    pushAddr(T1);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the dup2_x2 bytecode
   */
  protected final void emit_dup2_x2() {
    popAddr(T0);
    popAddr(T1);
    popAddr(T2);
    popAddr(T3);
    pushAddr(T1);
    pushAddr(T0);
    pushAddr(T3);
    pushAddr(T2);
    pushAddr(T1);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    popAddr(T0);
    popAddr(T1);
    pushAddr(T0);
    pushAddr(T1);
  }


  /*
   * int ALU
   */


  /**
   * Emit code to implement the iadd bytecode
   */
  protected final void emit_iadd() {
    popInt(T0);
    popInt(T1);
    asm.emitADD (T2, T1, T0);
    pushInt(T2);            
  }

  /**
   * Emit code to implement the isub bytecode
   */
  protected final void emit_isub() {
    popInt(T0);
    popInt(T1);
    asm.emitSUBFC(T2, T0, T1);
    pushInt(T2);            
  }

  /**
   * Emit code to implement the imul bytecode
   */
  protected final void emit_imul() {
    popInt(T1);
    popInt(T0);
    asm.emitMULLW(T1,T0, T1);
    pushInt(T1);            
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    popInt(T1);
    popInt(T0);
    asm.emitTWEQ0(T1);
    asm.emitDIVW (T0, T0, T1);  // T0 := T0/T1
    pushInt(T0);            
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    popInt(T1);
    popInt(T0);
    asm.emitTWEQ0(T1);
    asm.emitDIVW (T2, T0, T1);   // T2 := T0/T1
    asm.emitMULLW(T2, T2, T1);   // T2 := [T0/T1]*T1
    asm.emitSUBFC (T1, T2, T0);   // T1 := T0 - [T0/T1]*T1
    pushInt(T1);            
  }

  /**
   * Emit code to implement the ineg bytecode
   */
  protected final void emit_ineg() {
    popInt(T0);
    asm.emitNEG(T0, T0);
    pushInt(T0);            
  }

  /**
   * Emit code to implement the ishl bytecode
   */
  protected final void emit_ishl() {
    popInt(T1);
    popInt(T0);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSLW  (T0, T0, T1);
    pushInt(T0);            
  }

  /**
   * Emit code to implement the ishr bytecode
   */
  protected final void emit_ishr() {
    popInt(T1);
    popInt(T0);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSRAW (T0, T0, T1);
    pushInt(T0);            
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    popInt(T1);
    popInt(T0);
    asm.emitANDI(T1, T1, 0x1F);
    asm.emitSRW (T0, T0, T1);
    pushInt(T0);            
  }

  /**
   * Emit code to implement the iand bytecode
   */
  protected final void emit_iand() {
    popInt(T1);
    popInt(T0);
    asm.emitAND (T2, T0, T1);
    pushInt(T2);            
  }

  /**
   * Emit code to implement the ior bytecode
   */
  protected final void emit_ior() {
    popInt(T1);
    popInt(T0);
    asm.emitOR  (T2, T0,T1);
    pushInt(T2);            
  }

  /**
   * Emit code to implement the ixor bytecode
   */
  protected final void emit_ixor() {
    popInt(T1);
    popInt(T0);
    asm.emitXOR (T2, T0,T1);
    pushInt(T2);            
  }

  /**
   * Emit code to implement the iinc bytecode
   * @param index index of local
   * @param val value to increment it by
   */
  protected final void emit_iinc(int index, int val) {
    loadIntLocal(T0, index);
    asm.emitADDI(T0, val, T0);
    storeIntLocal(T0, index);
  }


  /*
   * long ALU
   */


  /**
   * Emit code to implement the ladd bytecode
   */
  protected final void emit_ladd() {
    popLong(T2,T0);
    popLong(T3,T1);
    asm.emitADD (T0, T1, T0);
    if (VM.BuildFor32Addr) {
      asm.emitADDE (T1, T2, T3);
    }
    pushLong(T1,T0);
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    popLong(T2,T0);
    popLong(T3,T1);
    asm.emitSUBFC(T0, T0, T1);
    if (VM.BuildFor32Addr) {
      asm.emitSUBFE(T1, T2, T3);
    }
    pushLong(T1,T0);
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    popLong(T2,T3);
    popLong(T0,T1);
    if (VM.BuildFor64Addr) {
      asm.emitMULLD (T1, T1, T3);
    } else {
      asm.emitMULHWU (S0, T1, T3);
      asm.emitMULLW  (T0, T0, T3);
      asm.emitADD    (T0, T0, S0);
      asm.emitMULLW  (S0, T1, T2);
      asm.emitMULLW  (T1, T1, T3);
      asm.emitADD    (T0, T0, S0);
    }
    pushLong(T0,T1);
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    popLong(T2,T3);
    if (VM.BuildFor64Addr) {
      popLong(T0,T1);
      asm.emitTDEQ0(T3);
      asm.emitDIVD(T1,T1,T3);
    } else {
      asm.emitOR  (T0, T3, T2); // or two halves of denominator together
      asm.emitTWEQ0(T0);         // trap if 0.
      popLong(T0,T1);
      generateSysCall(16, VM_Entrypoints.sysLongDivideIPField);
    }
    pushLong(T0,T1);
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    popLong(T2,T3);
    if (VM.BuildFor64Addr) {
      popLong(T0,T1);
      asm.emitTDEQ0(T3);
      asm.emitDIVD(T0,T1,T3);      // T0 := T1/T3
      asm.emitMULLD(T0, T0, T3);   // T0 := [T1/T3]*T3
      asm.emitSUBFC (T1, T0, T1);   // T1 := T1 - [T1/T3]*T3
    } else {
      asm.emitOR  (T0, T3, T2); // or two halves of denominator together
      asm.emitTWEQ0(T0);         // trap if 0.
      popLong(T0,T1);
      generateSysCall(16, VM_Entrypoints.sysLongRemainderIPField);
    }
    pushLong(T0,T1);
  }

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    popLong(T1,T0);
    if (VM.BuildFor64Addr) {
      asm.emitNEG(T0, T0);
    } else {
      asm.emitSUBFIC (T0, T0, 0x0);
      asm.emitSUBFZE(T1, T1);
    }
    pushLong(T1,T0);
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    popInt(T0);                    // T0 is n
    popLong(T2,T1);
    if (VM.BuildFor64Addr) {
      asm.emitANDI(T0, T0, 0x3F);
      asm.emitSLD  (T1, T1, T0);
      pushLong(T1,T1);
    } else {
      asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
      asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
      asm.emitSLW  (T3, T1, T0);    // low bits of l shifted n or n-32 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ); // if shift less than 32, goto
      asm.emitLVAL(T0,  0);        // low bits are zero
      pushLong(T3,T0);
      VM_ForwardReference fr2 = asm.emitForwardB();
      fr1.resolve(asm);
      asm.emitSLW  (T2, T2, T0);    // high bits of l shifted n bits left
      asm.emitSUBFIC (T0, T0, 0x20);  // T0 := 32 - T0; 
      asm.emitSRW (T1, T1, T0);    // T1 is middle bits of result
      asm.emitOR  (T2, T2, T1);    // T2 is high bits of result
      pushLong(T2,T3);
      fr2.resolve(asm);
    }
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    popInt(T0);                    // T0 is n
    popLong(T2,T1);
    if (VM.BuildFor64Addr) {
      asm.emitANDI(T0, T0, 0x3F);
      asm.emitSRAD (T1, T1, T0);
      pushLong(T1,T1);
    } else {
      asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
      asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
      asm.emitSRAW (T3, T2, T0);    // high bits of l shifted n or n-32 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ);
      asm.emitSRAWI(T0, T3, 0x1F);  // propogate a full work of sign bit
      pushLong(T0,T3);
      VM_ForwardReference fr2 = asm.emitForwardB();
      fr1.resolve(asm);
      asm.emitSRW (T1, T1, T0);    // low bits of l shifted n bits right
      asm.emitSUBFIC (T0, T0, 0x20);  // T0 := 32 - T0;
      asm.emitSLW  (T2, T2, T0);    // T2 is middle bits of result
      asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
      pushLong(T3,T1);
      fr2.resolve(asm);
    }
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    popInt(T0);                    // T0 is n
    popLong(T2,T1);
    if (VM.BuildFor64Addr) {
      asm.emitANDI(T0, T0, 0x3F);
      asm.emitSRD (T1, T1, T0);
      pushLong(T1,T1);
    } else {
      asm.emitANDI(T3, T0, 0x20);  // shift more than 31 bits?
      asm.emitXOR (T0, T3, T0);    // restrict shift to at most 31 bits
      asm.emitSRW (T3, T2, T0);    // high bits of l shifted n or n-32 bits
      VM_ForwardReference fr1 = asm.emitForwardBC(EQ);
      asm.emitLVAL(T0,  0);        // high bits are zero
      pushLong(T0,T3);
      VM_ForwardReference fr2 = asm.emitForwardB();
      fr1.resolve(asm);
      asm.emitSRW (T1, T1, T0);    // low bits of l shifted n bits right
      asm.emitSUBFIC (T0, T0, 0x20);  // T0 := 32 - T0;
      asm.emitSLW  (T2, T2, T0);    // T2 is middle bits of result
      asm.emitOR  (T1, T1, T2);    // T1 is low bits of result
      pushLong(T3,T1);
      fr2.resolve(asm);
    }
  }

  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    popLong(T2,T0);
    popLong(T3,T1);
    asm.emitAND (T0, T1, T0);
    if (VM.BuildFor32Addr) {
      asm.emitAND(T1, T2, T3);
    }
    pushLong(T1,T0);
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    popLong(T2,T0);
    popLong(T3,T1);
    asm.emitOR (T0, T1, T0);
    if (VM.BuildFor32Addr) {
      asm.emitOR(T1, T2, T3);
    }
    pushLong(T1,T0);
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    popLong(T2,T0);
    popLong(T3,T1);
    asm.emitXOR (T0, T1, T0);
    if (VM.BuildFor32Addr) {
      asm.emitXOR(T1, T2, T3);
    }
    pushLong(T1,T0);
  }


  /*
   * float ALU
   */


  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    popFloat(F0);
    popFloat(F1);
    asm.emitFADDS  (F0, F1, F0);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    popFloat(F0);
    popFloat(F1);
    asm.emitFSUBS  (F0, F1, F0);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    popFloat(F0);
    popFloat(F1);
    asm.emitFMULS  (F0, F1, F0); // single precision multiply
    pushFloat(F0);
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    popFloat(F0);
    popFloat(F1);
    asm.emitFDIVS  (F0, F1, F0);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    popFloat(F1);
    popFloat(F0);
    generateSysCall(16, VM_Entrypoints.sysDoubleRemainderIPField);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    popFloat(F0);
    asm.emitFNEG(F0, F0);
    pushFloat(F0);
  }


  /*
   * double ALU
   */


  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    popDouble(F0);
    popDouble(F1);
    asm.emitFADD   (F0, F1, F0);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    popDouble(F0);
    popDouble(F1);
    asm.emitFSUB   (F0, F1, F0);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    popDouble(F0);
    popDouble(F1);
    asm.emitFMUL   (F0, F1, F0);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    popDouble(F0);
    popDouble(F1);
    asm.emitFDIV   (F0, F1, F0);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    popDouble(F1);                 //F1 is b
    popDouble(F0);                 //F0 is a
    generateSysCall(16, VM_Entrypoints.sysDoubleRemainderIPField);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    popDouble(F0);
    asm.emitFNEG(F0, F0);
    pushDouble(F0);
  }


  /*
   * conversion ops
   */


  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    if (VM.BuildFor64Addr) {
      popInt(T0);
      pushLong(T0,T0); 
    } else {
      peekInt(T0, 0);
      asm.emitSRAWI(T1, T0, 31);
      pushInt(T1);
    }
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {  
    if (VM.BuildFor64Addr) {
      popInt(T0);               // TO is X  (an int)
      pushLong(T0,T0);
      popDouble(F0);            // load long 
      asm.emitFCFID(F0, F0);    // convert it
      pushFloat(F0);            // store the float
    } else {
      popInt(T0);               // TO is X  (an int)
      asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicField.getOffset(), T1);  // F0 is MAGIC
      asm.emitSTFD  (F0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);
      asm.emitSTW   (T0, VM_Entrypoints.scratchStorageField.getOffset()+4, PROCESSOR_REGISTER);
      asm.emitCMPI  (T0,  0);                // is X < 0
      VM_ForwardReference fr = asm.emitForwardBC(GE);
      asm.emitLInt  (T0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);
      asm.emitADDI  (T0, -1, T0);            // decrement top of MAGIC
      asm.emitSTW   (T0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER); // MAGIC + X in scratch field
      fr.resolve(asm);
      asm.emitLFD   (F1, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER); // F1 is MAGIC + X
      asm.emitFSUB  (F1, F1, F0);            // F1 is X
      pushFloat(F1);                         // float(X) is on stack 
    }
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    if (VM.BuildFor64Addr) {
      popInt(T0);               //TO is X  (an int)
      pushLong(T0,T0);
      popDouble(F0);              // load long 
      asm.emitFCFID(F0, F0);      // convert it
      pushDouble(F0);  // store the float
    } else {
      popInt(T0);                               // T0 is X (an int)
      asm.emitLFDtoc(F0, VM_Entrypoints.IEEEmagicField.getOffset(), T1);  // F0 is MAGIC
      pushDouble(F0);               // MAGIC on stack
      pokeInt(T0, 1);               // if 0 <= X, MAGIC + X 
      asm.emitCMPI  (T0,  0);                   // is X < 0
      VM_ForwardReference fr = asm.emitForwardBC(GE); // ow, handle X < 0
      popInt (T0);               // T0 is top of MAGIC
      asm.emitADDI   (T0, -1, T0);               // decrement top of MAGIC
      pushInt(T0);               // MAGIC + X is on stack
      fr.resolve(asm);
      popDouble(F1);               // F1 is MAGIC + X
      asm.emitFSUB    (F1, F1, F0);               // F1 is X
      pushDouble(F1);               // float(X) is on stack
    }
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    discardSlot();
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    popLong (T0, VM.BuildFor64Addr?T0:T1);
    generateSysCall(8, VM_Entrypoints.sysLongToFloatIPField);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the l2d bytecode
   */
  protected final void emit_l2d() {
    popLong (T0, VM.BuildFor64Addr?T0:T1);
    generateSysCall(8, VM_Entrypoints.sysLongToDoubleIPField);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    popFloat(F0);
    asm.emitFCMPU(F0, F0);
    VM_ForwardReference fr1 = asm.emitForwardBC(NE);
    // Normal case: F0 == F0 therefore not a NaN
    asm.emitFCTIWZ(F0, F0);
    if (VM.BuildFor64Addr) { 
      pushLowDoubleAsInt(F0);
    } else {
      asm.emitSTFD  (F0, VM_Entrypoints.scratchStorageField.getOffset(), PROCESSOR_REGISTER);
      asm.emitLWZ   (T0, VM_Entrypoints.scratchStorageField.getOffset() + 4, PROCESSOR_REGISTER);
      pushInt       (T0);
    }
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    // A NaN => 0
    asm.emitLVAL(T0, 0);
    pushInt(T0);
    fr2.resolve(asm);
  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
    popFloat(F0);
    generateSysCall(4, VM_Entrypoints.sysFloatToLongIPField);
    pushLong (T0, VM.BuildFor64Addr?T0:T1);
  }

  /**
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    popFloat(F0);
    pushDouble(F0);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    popDouble(F0);
    asm.emitFCTIWZ(F0, F0);
    pushLowDoubleAsInt(F0);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    popDouble(F0);
    generateSysCall(8, VM_Entrypoints.sysDoubleToLongIPField);
    pushLong (T0, VM.BuildFor64Addr?T0:T1);
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    popDouble(F0);
    pushFloat(F0);
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    popByteAsInt(T0);  
    pushInt(T0);
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    popCharAsInt(T0);
    pushInt(T0);
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    popShortAsInt(T0);
    pushInt(T0);
  }


  /*
   * comparison ops
   */


  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    popLong(T3, T2);
    popLong(T1, T0);
    //-#if RVM_FOR_64_ADDR
    asm.emitCMPD(T0, T2); 
    VM_ForwardReference fr1 = asm.emitForwardBC(LT);
    VM_ForwardReference fr2 = asm.emitForwardBC(GT);
    //-#else
    asm.emitCMP  (T1, T3);      // ah ? al
    VM_ForwardReference fr1 = asm.emitForwardBC(LT);
    VM_ForwardReference fr2 = asm.emitForwardBC(GT);
    asm.emitCMPL (T0, T2);      // al ? bl (logical compare)
    VM_ForwardReference fr3 = asm.emitForwardBC(LT);
    VM_ForwardReference fr4 = asm.emitForwardBC(GT);
    //-#endif
    asm.emitLVAL(T0,  0);      // a == b
    VM_ForwardReference fr5 = asm.emitForwardB();
    fr1.resolve(asm);
    //-#if RVM_FOR_32_ADDR
    fr3.resolve(asm);
    //-#endif
    asm.emitLVAL(T0, -1);      // a <  b
    VM_ForwardReference fr6 = asm.emitForwardB();
    fr2.resolve(asm);
    //-#if RVM_FOR_32_ADDR
    fr4.resolve(asm);
    //-#endif
    asm.emitLVAL(T0,  1);      // a >  b
    fr5.resolve(asm);
    fr6.resolve(asm);
    pushInt(T0);
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    popFloat(F1);
    popFloat(F0);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(LE);
    asm.emitLVAL(T0,  1); // the GT bit of CR0
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLVAL(T0, -1); // the LT or UO bits of CR0
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLVAL(T0,  0);
    fr2.resolve(asm);
    fr4.resolve(asm);
    pushInt(T0);
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    popFloat(F1);
    popFloat(F0);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(GE);
    asm.emitLVAL(T0, -1);     // the LT bit of CR0
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLVAL(T0,  1);     // the GT or UO bits of CR0
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLVAL(T0,  0);     // the EQ bit of CR0
    fr2.resolve(asm);
    fr4.resolve(asm);
    pushInt(T0);
  }

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    popDouble(F1);
    popDouble(F0);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(LE);
    asm.emitLVAL(T0,  1); // the GT bit of CR0
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLVAL(T0, -1); // the LT or UO bits of CR0
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLVAL(T0,  0);
    fr2.resolve(asm);
    fr4.resolve(asm);
    pushInt(T0);
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    popDouble(F1);
    popDouble(F0);
    asm.emitFCMPU(F0, F1);
    VM_ForwardReference fr1 = asm.emitForwardBC(GE);
    asm.emitLVAL(T0, -1); // the LT bit of CR0
    VM_ForwardReference fr2 = asm.emitForwardB();
    fr1.resolve(asm);
    VM_ForwardReference fr3 = asm.emitForwardBC(EQ);
    asm.emitLVAL(T0,  1); // the GT or UO bits of CR0
    VM_ForwardReference fr4 = asm.emitForwardB();
    fr3.resolve(asm);
    asm.emitLVAL(T0,  0); // the EQ bit of CR0
    fr2.resolve(asm);
    fr4.resolve(asm);
    pushInt(T0);
  }


  /*
   * branching
   */


  /**
   * Emit code to implement the ifeg bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifeq(int bTarget) {
    popInt(T0);
    asm.emitADDICr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    popInt(T0);
    asm.emitADDICr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget) {
    popInt(T0);
    asm.emitADDICr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    genCondBranch(LT, bTarget);
  }

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget) {
    popInt(T0);
    asm.emitADDICr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    genCondBranch(GE, bTarget);
  }

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget) {
    popInt(T0);
    asm.emitADDICr(T0, T0,  0); // compares T0 to 0 and sets CR0 
    genCondBranch(GT, bTarget);
  }

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget) {
    popInt(T0);
    asm.emitADDICr(0,  T0,  0); // T0 to 0 and sets CR0 
    genCondBranch(LE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(LT, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(GE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(GT, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    popInt(T1);
    popInt(T0);
    asm.emitCMP(T0, T1);    // sets CR0
    genCondBranch(LE, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    popAddr(T1);
    popAddr(T0);
    asm.emitCMPLAddr(T0, T1);    // sets CR0  
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget) {
    popAddr(T1);
    popAddr(T0);
    asm.emitCMPLAddr(T0, T1);    // sets CR0
    genCondBranch(NE, bTarget);
  }

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    popAddr(T0);
    asm.emitLVAL(T1,  0);
    asm.emitCMPLAddr(T0, T1);  
    genCondBranch(EQ, bTarget);
  }

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    popAddr(T0);
    asm.emitLVAL(T1,  0);
    asm.emitCMPLAddr (T0, T1);  
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
    int start = asm.getMachineCodeIndex();
    int delta = 4;
    asm.emitMFLR(T1);           // LR +  0
    asm.emitADDI (T1, delta*INSTRUCTION_WIDTH, T1);   // LR +  4  
    pushAddr(T1);   // LR +  8 
    asm.emitBL(bytecodeMap[bTarget], bTarget); // LR + 12
    int done = asm.getMachineCodeIndex();
    if (VM.VerifyAssertions) VM._assert((done - start) == delta);
  }

  /**
   * Emit code to implement the ret bytecode
   * @param index local variable containing the return address
   */
  protected final void emit_ret(int index) {
    loadAddrLocal(T0, index);
    asm.emitMTLR(T0);
    asm.emitBCLR ();
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

    popInt(T0);  // T0 is index
    if (asm.fits(16, -low)) {
      asm.emitADDI(T0, -low, T0);
    } else {
      asm.emitLVAL(T1, low);
      asm.emitSUBFC (T0, T1, T0); 
    }
    asm.emitLVAL(T2, n);
    asm.emitCMPL(T0, T2);
    if (options.EDGE_COUNTERS) {
      edgeCounterIdx += n+1; // allocate n+1 counters
      // Load counter array for this method
      asm.emitLAddrToc (T2, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(T2, T2, getEdgeCounterOffset());
      
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
      int offset = bcodes.getTableSwitchOffset(i);
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      asm.emitSwitchCase(i, mTarget, bTarget);
    }
    bcodes.skipTableSwitchOffsets(n);
    fr1.resolve(asm);
    asm.emitMFLR(T1);         // T1 is base of table
    asm.emitSLWI (T0, T0,  LOG_BYTES_IN_INT); // convert to bytes
    if (options.EDGE_COUNTERS) {
      incEdgeCounterIdx(T2, S0, firstCounter, T0);
    }
    asm.emitLIntX  (T0, T0, T1); // T0 is relative offset of desired case
    asm.emitADD  (T1, T1, T0); // T1 is absolute address of desired case
    asm.emitMTCTR(T1);
    asm.emitBCCTR ();
  }

  /**
   * Emit code to implement the lookupswitch bytecode
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  protected final void emit_lookupswitch(int defaultval, int npairs) {
    if (options.EDGE_COUNTERS) {
      // Load counter array for this method
      asm.emitLAddrToc (T2, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(T2, T2, getEdgeCounterOffset());
    }

    popInt(T0); // T0 is key
    for (int i=0; i<npairs; i++) {
      int match   = bcodes.getLookupSwitchValue(i);
      if (asm.fits(match, 16)) {
        asm.emitCMPI(T0, match);
      } else {
        asm.emitLVAL(T1, match);
        asm.emitCMP(T0, T1);
      }
      int offset  = bcodes.getLookupSwitchOffset(i);
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
    bcodes.skipLookupSwitchPairs(npairs);
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
    peekInt(T0, 0);
    genEpilogue();
  }

  /**
   * Emit code to implement the lreturn bytecode
   */
  protected final void emit_lreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    peekLong(T0, VM.BuildFor64Addr?T0:T1, 0);
    genEpilogue();
  }

  /**
   * Emit code to implement the freturn bytecode
   */
  protected final void emit_freturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    peekFloat(F0, 0);
    genEpilogue();
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
     peekDouble(F0, 0);
    genEpilogue();
  }

  /**
   * Emit code to implement the areturn bytecode
   */
  protected final void emit_areturn() {
    if (method.isSynchronized()) genSynchronizedMethodEpilogue();
    peekAddr(T0, 0);
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
  protected final void emit_unresolved_getstatic(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T1, fieldRef, true); 
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitLIntX (T0, T1, JTOC);
      pushInt(T0);
    } else { // field is two words (double or long ( or address on PPC64))
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor64Addr){
        if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
          asm.emitLDX(T0, T1, JTOC);
          pushAddr(T0);
          return;
        }
      }
      asm.emitLFDX (F0, T1, JTOC);
      pushDouble(F0);
    }
  }
  
  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitLIntToc(T0, fieldOffset);
      pushInt(T0);
    } else { // field is two words (double or long ( or address on PPC64))
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor64Addr){
        if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
          asm.emitLDtoc(T0, fieldOffset);
          pushAddr(T0);
          return;
        }
      }
      asm.emitLFDtoc(F0, fieldOffset, T0);
      pushDouble(F0);
    }
  }


  /**
   * Emit code to implement a dynamically linked putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putstatic(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T1, fieldRef, true);
// putstatic barrier currently unsupported
//     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
//       VM_Barriers.compilePutstaticBarrier(this); // NOTE: offset is in T1 from emitDynamicLinkingSequence
//       emitDynamicLinkingSequence(T1, fieldRef, false);
//     }
      if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
        popInt(T0);
        asm.emitSTWX(T0, T1, JTOC);
      } else { // field is two words (double or long (or address on PPC64))
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor64Addr){
          if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
            popAddr(T0);
            asm.emitSTDX(T0, T1, JTOC);
            return;
          }
        }
        popDouble(F0);
        asm.emitSTFDX(F0, T1, JTOC);
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
//       VM_Barriers.compilePutstaticBarrierImm(this, fieldOffset);
//     }
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      popInt(T0);
      asm.emitSTWtoc(T0, fieldOffset, T1);
    } else { // field is two words (double or long (or address on PPC64))
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor64Addr){
          if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
            popAddr(T0);
            asm.emitSTDtoc(T0, fieldOffset, T1);
            return;
          }
        }
      popDouble(F0);
      asm.emitSTFDtoc(F0, fieldOffset, T0);
    }
  }


  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T2, fieldRef, true);
    popAddr(T1); 
    if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T1); 
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitLIntX(T0, T2, T1); // use field offset in T2 from emitDynamicLinkingSequence()
      pushInt(T0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor64Addr){
        if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
          asm.emitLDX(T0, T2, T1);
          pushAddr(T0);
          return;
        }
      }
      asm.emitLFDX (F0, T2, T1); // use field offset in T2 from emitDynamicLinkingSequence()
      pushDouble(F0);
    }
  }

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    popAddr(T1); // T1 = object reference
    if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T1); 
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
      asm.emitLInt(T0, fieldOffset, T1);
      pushInt(T0);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor64Addr){
        if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
          asm.emitLD(T0, fieldOffset, T1);
          pushAddr(T0);
          return;
        }
      }
      asm.emitLFD  (F0, fieldOffset, T1);
      pushDouble(F0);
    }
  }


  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_FieldReference fieldRef) {
    emitDynamicLinkingSequence(T1, fieldRef, true);
    if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrier(this, fieldRef.getId()); // NOTE: offset is in T1 from emitDynamicLinkingSequence
      emitDynamicLinkingSequence(T1, fieldRef, false);  
      discardSlots(2);
    } else {
      if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
        popInt(T0); // T0 = value
        popAddr(T2); // T2 = object reference
        if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T2); 
        asm.emitSTWX(T0, T2, T1);
      } else { // field is two words (double or long)
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor64Addr){
          if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
            popAddr(T0);                // T0 = address value
            popAddr(T2);                // T2 = object reference
            if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T2);
            asm.emitSTDX(T0, T2, T1);
            return;
          }
        }
        popDouble(F0);     // F0 = doubleword value
        popAddr(T2);       // T2 = object reference
        if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T2); 
        asm.emitSTFDX(F0, T2, T1);
      }
    }
  }

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_FieldReference fieldRef) {
    int fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      VM_Barriers.compilePutfieldBarrierImm(this, fieldOffset, fieldRef.getId());
    }
    if (fieldRef.getSize() == BYTES_IN_INT) { // field is one word
        popInt(T0);        // T0 = value
        popAddr(T1);       // T1 = object reference
        if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T1); 
        asm.emitSTW(T0, fieldOffset, T1);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor64Addr){
          if (fieldRef.getNumberOfStackSlots() == 1){    //address only 1 stackslot!!!
            popAddr(T0);                // T0 = address value
            popAddr(T1);                // T1 = object reference
            if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T1);          
            asm.emitSTD(T0, fieldOffset, T1);
            return;
          }
        }
        popDouble(F0);     // F0 = doubleword value
        popAddr(T1);       // T1 = object reference
        if (VM.ExplicitlyGuardLowMemory) asm.emitNullCheck(T1); 
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
  protected final void emit_unresolved_invokevirtual(VM_MethodReference methodRef) {
    int objectIndex = methodRef.getParameterWords(); // +1 for "this" parameter, -1 to load it
    emitDynamicLinkingSequence(T2, methodRef, true); // leaves method offset in T2
    peekAddr(T0, objectIndex);
    VM_ObjectModel.baselineEmitLoadTIB(asm, T1, T0); // load TIB
    asm.emitLAddrX(T2, T2, T1);
    asm.emitMTCTR(T2);
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /**
   * Emit code to implement invokevirtual
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokevirtual(VM_MethodReference methodRef) {
    int objectIndex = methodRef.getParameterWords(); // +1 for "this" parameter, -1 to load it
    peekAddr(T0, objectIndex);
    VM_ObjectModel.baselineEmitLoadTIB(asm, T1, T0); // load TIB
    int methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.emitLAddr(T2, methodOffset, T1);
    asm.emitMTCTR(T2);
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef the referenced method
   * @param targetRef the method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_MethodReference methodRef, VM_Method target) {
    if (target.isObjectInitializer()) { // invoke via method's jtoc slot
      asm.emitLAddrToc(T0, target.getOffset());
    } else { // invoke via class's tib slot
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      asm.emitLAddrToc(T0, target.getDeclaringClass().getTibOffset());
      asm.emitLAddr(T0, target.getOffset(), T0);
    }
    asm.emitMTCTR(T0);
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }

  /**
   * Emit code to implement invokespecial
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokespecial(VM_MethodReference methodRef) {
    // must be a static method; if it was a super then declaring class _must_ be resolved
    emitDynamicLinkingSequence(T2, methodRef, true); // leaves method offset in T2
    asm.emitLAddrX(T0, T2, JTOC); 
    asm.emitMTCTR(T0);
    genMoveParametersToRegisters(true, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(true, methodRef);
  }


  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_MethodReference methodRef) {
    emitDynamicLinkingSequence(T2, methodRef, true);                  // leaves method offset in T2
    asm.emitLAddrX(T0, T2, JTOC); // method offset left in T2 by emitDynamicLinkingSequence
    asm.emitMTCTR(T0);
    genMoveParametersToRegisters(false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_MethodReference methodRef) {
    int methodOffset = methodRef.peekResolvedMethod().getOffset();
    asm.emitLAddrToc(T0, methodOffset);
    asm.emitMTCTR(T0);
    genMoveParametersToRegisters(false, methodRef);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(false, methodRef);
  }

  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   */
  protected final void emit_invokeinterface(VM_MethodReference methodRef) {
    int count = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    VM_Method resolvedMethod = null;
    resolvedMethod = methodRef.peekInterfaceMethod();

    // (1) Emit dynamic type checking sequence if required to 
    // do so inline.
    if (VM.BuildForIMTInterfaceInvocation || 
        (VM.BuildForITableInterfaceInvocation && 
         VM.DirectlyIndexedITables)) {
      if (resolvedMethod == null) {
        // Can't successfully resolve it at compile time.
        // Call uncommon case typechecking routine to do the right thing when this code actually executes.
        asm.emitLAddrToc(T0, VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLVAL(T0, methodRef.getId());            // id of method reference we are trying to call
        peekAddr(T1, count-1);           // the "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
        asm.emitBCCTRL();                 // throw exception, if link error
      } else {
        // normal case.  Not a ghost ref.
        asm.emitLAddrToc(T0, VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLAddrToc(T0, resolvedMethod.getDeclaringClass().getTibOffset()); // tib of the interface method
        asm.emitLAddr(T0, TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS, T0);                   // type of the interface method
        peekAddr(T1, count-1);                        // the "this" object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T1,T1);
        asm.emitBCCTRL();                              // throw exception, if link error
      }
    }
    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(methodRef);
      int offset = sig.getIMTOffset();
      genMoveParametersToRegisters(true, methodRef); // T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      if (VM.BuildForIndirectIMT) {
        // Load the IMT base into S0
        asm.emitLAddr(S0, TIB_IMT_TIB_INDEX << LOG_BYTES_IN_ADDRESS, S0);
      }
      asm.emitLAddr(S0, offset, S0);                  // the method address
      asm.emitMTCTR(S0);
      asm.emitLVAL(S1, sig.getId());      // pass "hidden" parameter in S1 scratch  register
      asm.emitBCCTRL();
    } else if (VM.BuildForITableInterfaceInvocation && 
               VM.DirectlyIndexedITables && 
               resolvedMethod != null) {
      VM_Class I = resolvedMethod.getDeclaringClass();
      genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
      VM_ObjectModel.baselineEmitLoadTIB(asm,S0,T0);
      asm.emitLAddr (S0, TIB_ITABLES_TIB_INDEX << LOG_BYTES_IN_ADDRESS, S0); // iTables 
      asm.emitLAddr (S0, I.getInterfaceId() << LOG_BYTES_IN_ADDRESS, S0);  // iTable
      int idx = VM_InterfaceInvocation.getITableIndex(I, methodRef.getName(), methodRef.getDescriptor());
      asm.emitLAddr(S0, idx << LOG_BYTES_IN_ADDRESS, S0); // the method to call
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex = VM_InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(), 
                                                            methodRef.getName(), methodRef.getDescriptor());
      }
      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into method address
        int methodRefId = methodRef.getId();
        asm.emitLAddrToc(T0, VM_Entrypoints.invokeInterfaceMethod.getOffset());
        asm.emitMTCTR(T0);
        peekAddr(T0, count-1); // object
        asm.emitLVAL(T1, methodRefId);        // method id
        asm.emitBCCTRL();       // T0 := resolved method address
        asm.emitMTCTR(T0);
        genMoveParametersToRegisters(true, methodRef);
        asm.emitBCCTRL();
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into 
        // itable address
        asm.emitLAddrToc(T0, VM_Entrypoints.findItableMethod.getOffset());
        asm.emitMTCTR(T0);
        peekAddr(T0, count-1);     // object
        VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
        asm.emitLVAL(T1, resolvedMethod.getDeclaringClass().getInterfaceId());    // interface id
        asm.emitBCCTRL();   // T0 := itable reference
        asm.emitLAddr(T0, itableIndex << LOG_BYTES_IN_ADDRESS, T0); // T0 := the method to call
        asm.emitMTCTR(T0);
        genMoveParametersToRegisters(true, methodRef);        //T0 is "this"
        asm.emitBCCTRL();
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
    int whichAllocator = MM_Interface.pickAllocator(typeRef, method);
    int align = VM_ObjectModel.getAlignment(typeRef);
    int offset = VM_ObjectModel.getOffsetForAlignment(typeRef);
    asm.emitLAddrToc(T0, VM_Entrypoints.resolvedNewScalarMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitLVAL(T0, instanceSize);
    asm.emitLAddrToc(T1, tibOffset);
    asm.emitLVAL(T2, typeRef.hasFinalizer()?1:0);
    asm.emitLVAL(T3, whichAllocator);
    asm.emitLVAL(T4, align);
    asm.emitLVAL(T5, offset);
    asm.emitBCCTRL();
    pushAddr(T0);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param typeRef the type reference to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(VM_TypeReference typeRef) {
    asm.emitLAddrToc(T0, VM_Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitLVAL(T0, typeRef.getId());
    asm.emitBCCTRL();
    pushAddr(T0);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_resolved_newarray(VM_Array array) {
    int width      = array.getLogElementSize();
    int tibOffset  = array.getTibOffset();
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(array);
    int whichAllocator = MM_Interface.pickAllocator(array, method);
    int align = VM_ObjectModel.getAlignment(array);
    int offset = VM_ObjectModel.getOffsetForAlignment(array);
    asm.emitLAddrToc (T0, VM_Entrypoints.resolvedNewArrayMethod.getOffset());
    asm.emitMTCTR(T0);
    peekInt(T0,0);                    // T0 := number of elements
    asm.emitLVAL(T1, width);         // T1 := log element size
    asm.emitLVAL(T2, headerSize);    // T2 := header bytes
    asm.emitLAddrToc(T3, tibOffset);  // T3 := tib
    asm.emitLVAL(T4, whichAllocator);// T4 := allocator
    asm.emitLVAL(T5, align);
    asm.emitLVAL(T6, offset);
    asm.emitBCCTRL();
    pokeAddr(T0,0);
  }

  /**
   * Emit code to dynamically link and allocate an array
   * @param typeRef the type reference to dynamically link & instantiate
   */
  protected final void emit_unresolved_newarray(VM_TypeReference typeRef) {
    asm.emitLAddrToc (T0, VM_Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitMTCTR(T0);
    peekInt(T0,0);                // T0 := number of elements
    asm.emitLVAL(T1, typeRef.getId());      // T1 := id of type ref
    asm.emitBCCTRL();
    pokeAddr(T0,0);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the VM_Array to instantiate
   * @param dimensions the number of dimensions
   * @param dictionaryId, the dictionaryId of typeRef
   */
  protected final void emit_multianewarray(VM_TypeReference typeRef, int dimensions) {
    asm.emitLAddrToc(T0, VM_Entrypoints.newArrayArrayMethod.getOffset());
    asm.emitMTCTR(T0);
    asm.emitLVAL(T0, method.getId());
    asm.emitLVAL(T1, dimensions);
    asm.emitLVAL(T2, typeRef.getId());
    asm.emitSLWI (T3, T1,  LOG_BYTES_IN_ADDRESS); // number of bytes of array dimension args
    asm.emitADDI  (T3, spTopOffset, T3);             // offset from FP to expression stack top
    asm.emitBCCTRL();
    discardSlots(dimensions);
    pushAddr(T0);
  }

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    popAddr(T0);
    asm.emitLInt(T1, VM_ObjectModel.getArrayLengthOffset(), T0);
    pushInt(T1);
  }

  /**
   * Emit code to implement the athrow bytecode
   */
  protected final void emit_athrow() {
    asm.emitLAddrToc(T0, VM_Entrypoints.athrowMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0, 0);    
    asm.emitBCCTRL();
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected final void emit_checkcast(VM_TypeReference typeRef) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.checkcastMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0,  0); // checkcast(obj, klass) consumes obj
    asm.emitLVAL(T1, typeRef.getId());
    asm.emitBCCTRL();               // but obj remains on stack afterwords
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected final void emit_checkcast_resolvedClass(VM_Type type) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.checkcastResolvedClassMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr (T0,  0); // checkcast(obj, klass) consumes obj
    asm.emitLVAL(T1, type.getId());
    asm.emitBCCTRL();               // but obj remains on stack afterwords
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   * @param target the method to invoke to implement this checkcast
   */
  protected final void emit_checkcast_final(VM_Type type) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.checkcastFinalMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0,  0); // checkcast(obj, klass) consumes obj
    asm.emitLVAL(T1, type.getTibOffset());
    asm.emitBCCTRL();               // but obj remains on stack afterwords
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected final void emit_instanceof(VM_TypeReference typeRef) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.instanceOfMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0, 0);
    asm.emitLVAL(T1, typeRef.getId());
    asm.emitBCCTRL();
    pokeInt(T0, 0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected final void emit_instanceof_resolvedClass(VM_Type type) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.instanceOfResolvedClassMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0, 0);
    asm.emitLVAL(T1, type.getId());
    asm.emitBCCTRL();
    pokeInt(T0, 0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param typeRef the LHS type
   * @param target the method to invoke to implement this instanceof
   */
  protected final void emit_instanceof_final(VM_Type type) {
    asm.emitLAddrToc(T0,  VM_Entrypoints.instanceOfFinalMethod.getOffset());
    asm.emitMTCTR(T0);
    peekAddr(T0, 0);
    asm.emitLVAL(T1, type.getTibOffset());
    asm.emitBCCTRL();
    pokeInt(T0, 0);
  }

  /**
   * Emit code to implement the monitorenter bytecode
   */
  protected final void emit_monitorenter() {
    asm.emitLAddr(S0, VM_Entrypoints.lockMethod.getOffset(), JTOC);
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();
    discardSlot();
  }

  /**
   * Emit code to implement the monitorexit bytecode
   */
  protected final void emit_monitorexit() {
    peekAddr(T0, 0);
    asm.emitLAddr(S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);
    asm.emitMTCTR(S0);
    asm.emitBCCTRL();
    discardSlot();
  }

  
  // offset of i-th local variable with respect to FP
  private int localOffset (int i) {
    int offset = startLocalOffset - (i << LOG_BYTES_IN_STACKSLOT);
    if (VM.VerifyAssertions) VM._assert(offset < 0x8000);
    return offset;
  }

  private void emitDynamicLinkingSequence(int reg, VM_MemberReference ref, boolean couldBeZero) {
    int memberId = ref.getId();
    int memberOffset = memberId << LOG_BYTES_IN_INT;
    int tableOffset = VM_Entrypoints.memberOffsetsField.getOffset();
    if (couldBeZero) {
      int resolverOffset = VM_Entrypoints.resolveMemberMethod.getOffset();
      int label = asm.getMachineCodeIndex();
      
      // load offset table
      asm.emitLAddrToc (reg, tableOffset);
      asm.emitLIntOffset(reg, reg, memberOffset);

      // test for non-zero offset and branch around call to resolver
      asm.emitCMPI (reg, NEEDS_DYNAMIC_LINK);         // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      VM_ForwardReference fr1 = asm.emitForwardBC(NE);
      asm.emitLAddrToc (T0, resolverOffset);
      asm.emitMTCTR (T0);
      asm.emitLVAL(T0, memberId);            // id of member we are resolving
      asm.emitBCCTRL ();                              // link; will throw exception if link error
      asm.emitB    (label);                   // go back and try again
      fr1.resolve(asm);
    } else {
      // load offset table
      asm.emitLAddrToc (reg, tableOffset);
      asm.emitLIntOffset(reg, reg, memberOffset);
    }
  }

  // Gen bounds check for array load/store bytecodes.
  // Does implicit null check and array bounds check.
  // Bounds check can always be implicit becuase array length is at negative offset from obj ptr.
  // Kills S0.
  // on return: T0 => base, T1 => index. 
  private void genBoundsCheck () {
    popInt(T1);      // T1 is array index
    popAddr(T0);     // T0 is array ref
    asm.emitLInt (S0,  VM_ObjectModel.getArrayLengthOffset(), T0);  // T2 is array length
    asm.emitTWLLE(S0, T1);      // trap if index < 0 or index >= length
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
    asm.emitSTAddrU (FP, -frameSize, FP); // save old FP & buy new frame (trap if new frame below guard page) !!TODO: handle frames larger than 32k when addressing local variables, etc.
    
    // If this is a "dynamic bridge" method, then save all registers except GPR0, FPR0, JTOC, and FP.
    // 
    if (klass.isDynamicBridge()) {
      int offset = frameSize;
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
         asm.emitSTFD (i, offset -= BYTES_IN_DOUBLE, FP);
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
         asm.emitSTAddr(i, offset -= BYTES_IN_ADDRESS, FP);

      //-#if RVM_WITH_OSR
      // round up first, save scratch FPRs
      offset = VM_Memory.alignDown(offset - STACKFRAME_ALIGNMENT + 1, STACKFRAME_ALIGNMENT);

      for (int i = LAST_SCRATCH_FPR; i >= FIRST_SCRATCH_FPR; --i)
        asm.emitSTFD(i, offset -= BYTES_IN_DOUBLE, FP);
      for (int i = LAST_SCRATCH_GPR; i >= FIRST_SCRATCH_GPR; --i)
        asm.emitSTAddr(i, offset -= BYTES_IN_ADDRESS, FP);
      //-#endif
    }
    
    // Fill in frame header.
    //
    asm.emitLVAL(S0, compiledMethod.getId());
    asm.emitMFLR(0);
    asm.emitSTW (S0, STACKFRAME_METHOD_ID_OFFSET, FP);                   // save compiled method id
    asm.emitSTAddr(0, frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // save LR !!TODO: handle discontiguous stacks when saving return address
    
    // Setup locals.
    //
    genMoveParametersToLocals();                  // move parameters to locals
   
    // Perform a thread switch if so requested.
    //-#if RVM_WITH_OSR
    /* defer generating prologues which may trigger GC, see emit_deferred_prologue*/
    if (method.isForOsrSpecialization()) {
      return;
    }
    //-#endif

    genThreadSwitchTest(VM_Thread.PROLOGUE); //           (VM_BaselineExceptionDeliverer WONT release the lock (for synchronized methods) during prologue code)

    // Acquire method syncronization lock.  (VM_BaselineExceptionDeliverer will release the lock (for synchronized methods) after  prologue code)
    //
    if (method.isSynchronized()) 
      genSynchronizedMethodPrologue();
  }

  //-#if RVM_WITH_OSR
  protected final void emit_deferred_prologue() {
    if (VM.VerifyAssertions) VM._assert(method.isForOsrSpecialization());
    genThreadSwitchTest(VM_Thread.PROLOGUE);

    /* donot generate sync for synced method because we are reenter 
     * the method in the middle.
     */
    //  if (method.isSymchronized()) genSynchronizedMethodPrologue();
  }
  //-#endif
  
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
      asm.emitLAddrToc(T0, tibOffset);
      asm.emitLAddr(T0, 0, T0);
      asm.emitLAddr(T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { // first local is "this" pointer
      asm.emitLAddr(T0, startLocalOffset - BYTES_IN_ADDRESS, FP);  
    }
    asm.emitLAddr(S0, VM_Entrypoints.lockMethod.getOffset(), JTOC); // call out...
    asm.emitMTCTR  (S0);                                  // ...of line lock
    asm.emitBCCTRL();
    lockOffset = BYTES_IN_INT*(asm.getMachineCodeIndex() - 1); // after this instruction, the method has the monitor
  }

  // Emit code to release method synchronization lock.
  //
  private void genSynchronizedMethodEpilogue () {
    if (method.isStatic()) { // put java.lang.Class for VM_Type into T0
      int tibOffset = klass.getTibOffset();
      asm.emitLAddrToc(T0, tibOffset);
      asm.emitLAddr(T0, 0, T0);
      asm.emitLAddr(T0, VM_Entrypoints.classForTypeField.getOffset(), T0); 
    } else { // first local is "this" pointer
      asm.emitLAddr(T0, startLocalOffset - BYTES_IN_ADDRESS, FP); //!!TODO: think about this - can anybody store into local 0 (ie. change the value of "this")?
    }
    asm.emitLAddr(S0, VM_Entrypoints.unlockMethod.getOffset(), JTOC);  // call out...
    asm.emitMTCTR(S0);                                     // ...of line lock
    asm.emitBCCTRL();
  }
    
  // Emit code to discard stackframe and return to caller.
  //
  private void genEpilogue () {
    if (klass.isDynamicBridge()) {// Restore non-volatile registers.
      // we never return from a DynamicBridge frame
      asm.emitTAddrWI(-1);
    } else {
      if (frameSize <= 0x8000) {
        asm.emitADDI(FP, frameSize, FP); // discard current frame
      } else {
        asm.emitLAddr(FP, 0, FP);           // discard current frame
      }
      asm.emitLAddr (S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); 
      asm.emitMTLR(S0);
      asm.emitBCLR (); // branch always, through link register
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
      asm.emitLAddrToc (T0, VM_Entrypoints.edgeCountersField.getOffset());
      asm.emitLAddrOffset(T0, T0, getEdgeCounterOffset());

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
    asm.emitLInt   (scratch, counterIdx<<2, counters);
    asm.emitADDI   (scratch, 1, scratch);
    asm.emitRLWINM (scratch, scratch, 0, 1, 31);
    asm.emitSTW    (scratch, counterIdx<<2, counters);
  }

  private final void incEdgeCounterIdx(int counters, int scratch, int base, int counterIdx) {
    asm.emitADDI    (counters, base<<2, counters);
    asm.emitLIntX   (scratch, counterIdx, counters);
    asm.emitADDI    (scratch, 1, scratch);
    asm.emitRLWINM  (scratch, scratch, 0, 1, 31);
    asm.emitSTWX    (scratch, counterIdx, counters);
  }    

  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private void genThreadSwitchTest (int whereFrom) {
    if (isInterruptible) {
      VM_ForwardReference fr;
      // yield if takeYieldpoint is non-zero.
      asm.emitLInt(S0, VM_Entrypoints.takeYieldpointField.getOffset(), PROCESSOR_REGISTER);
      asm.emitCMPI(S0, 0); 
      if (whereFrom == VM_Thread.PROLOGUE) {
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        fr = asm.emitForwardBC(EQ);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromPrologueMethod.getOffset(), JTOC);
      } else if (whereFrom == VM_Thread.BACKEDGE) {
        // Take yieldpoint if yieldpoint flag is >0
        fr = asm.emitForwardBC(LE);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromBackedgeMethod.getOffset(), JTOC);
      } else { // EPILOGUE
        // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
        fr = asm.emitForwardBC(EQ);
        asm.emitLAddr(S0, VM_Entrypoints.yieldpointFromEpilogueMethod.getOffset(), JTOC);
      }
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
      fr.resolve(asm);

      //-#if RVM_WITH_ADAPTIVE_SYSTEM
      if (options.INVOCATION_COUNTERS) {
        int id = compiledMethod.getId();
        com.ibm.JikesRVM.adaptive.VM_InvocationCounts.allocateCounter(id);
        asm.emitLAddrToc (T0, VM_Entrypoints.invocationCountsField.getOffset());
        asm.emitLVAL(T1, compiledMethod.getId() << LOG_BYTES_IN_INT);
        asm.emitLIntX   (T2, T0, T1);                       
        asm.emitADDICr  (T2, T2, -1);
        asm.emitSTWX  (T2, T0, T1);
        VM_ForwardReference fr2 = asm.emitForwardBC(asm.GT);
        asm.emitLAddrToc (T0, VM_Entrypoints.invocationCounterTrippedMethod.getOffset());
        asm.emitMTCTR(T0);
        asm.emitLVAL(T0, id);
        asm.emitBCCTRL();
        fr2.resolve(asm);
      }
      //-#endif
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
      if (gp > LAST_VOLATILE_GPR) genUnspillSlot(localIndex++);
      else asm.emitSTAddr(gp++, localOffset(localIndex++) - BYTES_IN_ADDRESS, FP);
    }
    VM_TypeReference [] types = method.getParameterTypes();
    for (int i=0; i<types.length; i++, localIndex++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        if (gp > LAST_VOLATILE_GPR) {
          genUnspillDoubleSlot(localIndex++);
        } else {
          if (VM.BuildFor64Addr) { 
            asm.emitSTD(gp++, localOffset(localIndex) - BYTES_IN_ADDRESS, FP);
          } else {
            asm.emitSTW(gp++, localOffset(localIndex + 1) - BYTES_IN_INT, FP); // lo mem := lo register (== hi word)
            if (gp > LAST_VOLATILE_GPR) {
              genUnspillSlot(localIndex);
            } else {
              asm.emitSTW(gp++, localOffset(localIndex) - BYTES_IN_INT, FP);// hi mem := hi register (== lo word)
            }
          }
          localIndex += 1;
        }
      } else if (t.isFloatType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillSlot(localIndex);
        else asm.emitSTFS(fp++, localOffset(localIndex) - BYTES_IN_FLOAT, FP);
      } else if (t.isDoubleType()) {
        if (fp > LAST_VOLATILE_FPR) genUnspillDoubleSlot(localIndex++);
        else asm.emitSTFD(fp++, localOffset(localIndex++) - BYTES_IN_DOUBLE, FP);
      } else if (t.isIntLikeType()) {
        if (gp > LAST_VOLATILE_GPR) genUnspillSlot(localIndex);
        else asm.emitSTW(gp++, localOffset(localIndex) - BYTES_IN_INT, FP);
      } else { // t is object
        if (gp > LAST_VOLATILE_GPR) genUnspillSlot(localIndex);
        else asm.emitSTAddr(gp++, localOffset(localIndex) - BYTES_IN_ADDRESS, FP);
      }
    }
  }

  // load parameters into registers before calling method "m".
  private void genMoveParametersToRegisters (boolean hasImplicitThisArg, VM_MethodReference m) {
    // AIX computation will differ
    spillOffset = STACKFRAME_HEADER_SIZE;
    int gp = FIRST_VOLATILE_GPR;
    int fp = FIRST_VOLATILE_FPR;
    int stackIndex = m.getParameterWords();
    if (hasImplicitThisArg) {
      if (gp > LAST_VOLATILE_GPR) {
        genSpillSlot(stackIndex);
      } else {
        peekAddr(gp++, stackIndex);
      }
    }
    VM_TypeReference [] types = m.getParameterTypes();
    for (int i=0; i<types.length; i++) {
      VM_TypeReference t = types[i];
      if (t.isLongType()) {
        stackIndex -= 2;
        if (gp > LAST_VOLATILE_GPR) { 
          genSpillDoubleSlot(stackIndex);
        } else {
          if (VM.BuildFor64Addr) {
            peekLong(gp, gp, stackIndex);
            gp++;  
          } else {
            peekInt(gp++, stackIndex);       // lo register := lo mem (== hi order word)
            if (gp > LAST_VOLATILE_GPR) {
              genSpillSlot(stackIndex + 1);
            } else {
              peekInt(gp++, stackIndex+1);  // hi register := hi mem (== lo order word)
            }
          }
        }
      } else if (t.isFloatType()) {
        stackIndex -= 1;
        if (fp > LAST_VOLATILE_FPR) {
          genSpillSlot(stackIndex);
        } else {
          peekFloat(fp++, stackIndex);
        }
      } else if (t.isDoubleType()) {
        stackIndex -= 2;
        if (fp > LAST_VOLATILE_FPR) {
          genSpillDoubleSlot(stackIndex);
        } else {
          peekDouble(fp++, stackIndex);
        }
      } else if (t.isIntLikeType()) {
        stackIndex -= 1;
        if (gp > LAST_VOLATILE_GPR) {
          genSpillSlot(stackIndex);
        } else {
          peekInt(gp++, stackIndex);
        }
      } else { // t is object
        stackIndex -= 1;
        if (gp > LAST_VOLATILE_GPR) {
          genSpillSlot(stackIndex);
        } else {
          peekAddr(gp++, stackIndex);
        }
      }
    }
    if (VM.VerifyAssertions) VM._assert(stackIndex == 0);
  }

  // push return value of method "m" from register to operand stack.
  private void genPopParametersAndPushReturnValue (boolean hasImplicitThisArg, VM_MethodReference m) {
    VM_TypeReference t = m.getReturnType();
    discardSlots(m.getParameterWords() + (hasImplicitThisArg?1:0));
    if (!t.isVoidType()) {
      if (t.isLongType()) {
        pushLong(FIRST_VOLATILE_GPR, VM.BuildFor64Addr?FIRST_VOLATILE_GPR: (FIRST_VOLATILE_GPR + 1));
      } else if (t.isFloatType()) {
        pushFloat(FIRST_VOLATILE_FPR);
      } else if (t.isDoubleType()) {
        pushDouble(FIRST_VOLATILE_FPR);
      } else if (t.isIntLikeType()) {
        pushInt(FIRST_VOLATILE_GPR);
      } else { // t is object
        pushAddr(FIRST_VOLATILE_GPR);
      }
    }
  }

  private void genSpillSlot (int stackIndex) {
     peekAddr(0, stackIndex);
     asm.emitSTAddr(0, spillOffset, FP);
     spillOffset += BYTES_IN_STACKSLOT;
  }
     
  private void genSpillDoubleSlot (int stackIndex) {
     peekDouble(0, stackIndex);
     asm.emitSTFD(0, spillOffset, FP);
     if (VM.BuildFor64Addr) {
       spillOffset += BYTES_IN_STACKSLOT;                
     } else {
       spillOffset += 2*BYTES_IN_STACKSLOT;
     }
  }
               
  private void genUnspillSlot (int localIndex) {
     asm.emitLAddr(0, spillOffset, FP);
     asm.emitSTAddr(0, localOffset(localIndex) - BYTES_IN_ADDRESS, FP);
     spillOffset += BYTES_IN_STACKSLOT;
  }
                      
  private void genUnspillDoubleSlot (int localIndex) {
     asm.emitLFD (0, spillOffset, FP);
     asm.emitSTFD(0, localOffset(localIndex) - BYTES_IN_DOUBLE , FP);
     if (VM.BuildFor64Addr) {
       spillOffset += BYTES_IN_STACKSLOT;
     } else {
       spillOffset += 2*BYTES_IN_STACKSLOT;
     }
  }


  //-#if RVM_WITH_OSR
  protected final void emit_loadaddrconst(int bcIndex) {
    asm.emitBL(1, 0);
    asm.emitMFLR(T1);                   // LR +  0
    asm.registerLoadAddrConst(bcIndex);
    asm.emitADDI (T1, bcIndex<<LOG_BYTES_IN_INT, T1);   
    pushAddr(T1);   // LR +  8
  }

  /**
   * Emit code to invoke a compiled method (with known jtoc offset).
   * Treat it like a resolved invoke static, but take care of
   * this object in the case.
   *
   * I havenot thought about GCMaps for invoke_compiledmethod 
   */
  protected final void emit_invoke_compiledmethod(VM_CompiledMethod cm) {
    int methOffset = cm.getOsrJTOCoffset();
    asm.emitLAddrToc(T0, methOffset);
    asm.emitMTCTR(T0);
    boolean takeThis = !cm.method.isStatic();
    VM_MethodReference ref = cm.method.getMemberRef().asMethodReference();
    genMoveParametersToRegisters(takeThis, ref);
    asm.emitBCCTRL();
    genPopParametersAndPushReturnValue(takeThis, ref);
  }

  protected final VM_ForwardReference emit_pending_goto(int bTarget) {
    return asm.generatePendingJMP(bTarget);
  }
  //-#endif


  //*************************************************************************
  //                             MAGIC
  //*************************************************************************

  /*
   *  Generate inline machine instructions for special methods that cannot be 
   *  implemented in java bytecodes. These instructions are generated whenever  
   *  we encounter an "invokestatic" bytecode that calls a method with a 
   *  signature of the form "static native VM_Magic.xxx(...)".
   *  23 Jan 1998 Derek Lieber
   * 
   * NOTE: when adding a new "methodName" to "generate()", be sure to also 
   * consider how it affects the values on the stack and update 
   * "checkForActualCall()" accordingly.
   * If no call is actually generated, the map will reflect the status of the 
   * locals (including parameters) at the time of the call but nothing on the 
   * operand stack for the call site will be mapped.
   *  7 Jul 1998 Janice Shepherd
   */

  /** Generate inline code sequence for specified method.
   * @param methodToBeCalled: method whose name indicates semantics of code to be generated
   * @return true if there was magic defined for the method
   */
  private boolean  generateInlineCode(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
      
    if (methodToBeCalled.getType() == VM_TypeReference.SysCall) {
      VM_TypeReference[] args = methodToBeCalled.getParameterTypes();

      // (1) Set up arguments according to OS calling convention
      int paramWords = methodToBeCalled.getParameterWords();
      int gp = FIRST_OS_PARAMETER_GPR;
      int fp = FIRST_OS_PARAMETER_FPR;
      int stackIndex = paramWords;
      int paramBytes = (VM.BuildFor64Addr? args.length : paramWords) * BYTES_IN_STACKSLOT;
      int callee_param_index = - BYTES_IN_STACKSLOT - paramBytes;
      
      for (int i=0; i<args.length; i++) {
        VM_TypeReference t = args[i];
        if (t.isLongType()) {
          stackIndex -= 2;
          callee_param_index += BYTES_IN_LONG;
          if (VM.BuildFor64Addr) {
            if (gp <= LAST_OS_PARAMETER_GPR) { 
              peekLong(gp, gp, stackIndex);
              gp++;  
            } else {
              peekLong(S0,S0, stackIndex);
              asm.emitSTD(S0, callee_param_index - BYTES_IN_LONG, FP);
            }
          } else {
          //-#if RVM_FOR_LINUX
          /* NOTE: following adjustment is not stated in SVR4 ABI, but 
           * was implemented in GCC.
           */
            gp += (gp + 1) & 0x01; // if gpr is even, gpr += 1
          //-#endif
            if (gp <= LAST_OS_PARAMETER_GPR){ 
              peekInt(gp++, stackIndex);   
            }   // lo register := lo mem (== hi order word)
            if (gp <= LAST_OS_PARAMETER_GPR){  
              peekInt(gp++, stackIndex+1);    // hi register := hi mem (== lo order word)
            } else {
              peekLong(S0, S1, stackIndex);
              asm.emitSTW(S0, callee_param_index - BYTES_IN_LONG, FP);
              asm.emitSTW(S1, callee_param_index - BYTES_IN_INT, FP);
            }
          }
        } else if (t.isFloatType()) {
          stackIndex -= 1;
          callee_param_index += BYTES_IN_STACKSLOT;
          if (fp <= LAST_OS_PARAMETER_FPR) { 
            peekFloat(fp++, stackIndex);
          } else {
              peekFloat(FIRST_SCRATCH_FPR, stackIndex);
              asm.emitSTFS(FIRST_SCRATCH_FPR, callee_param_index - BYTES_IN_FLOAT, FP);
          }
        } else if (t.isDoubleType()) {
          stackIndex -= 2;
          callee_param_index += BYTES_IN_DOUBLE;
          if (fp <= LAST_OS_PARAMETER_FPR) { 
            peekDouble(fp++, stackIndex);
          } else {
            peekDouble( FIRST_SCRATCH_FPR, stackIndex);
            asm.emitSTFD(FIRST_SCRATCH_FPR, callee_param_index - BYTES_IN_DOUBLE, FP);
          }
        } else if (t.isIntLikeType()) {
          stackIndex -= 1;
          callee_param_index += BYTES_IN_STACKSLOT;
          if (gp <= LAST_OS_PARAMETER_GPR)  {
            peekInt(gp++, stackIndex);
          } else {
            peekInt(S0, stackIndex);
            asm.emitSTAddr(S0, callee_param_index - BYTES_IN_ADDRESS, FP);// save int zero-extended to be sure
          }
        } else { // t is object
          stackIndex -= 1;
          callee_param_index += BYTES_IN_STACKSLOT;
          if (gp <= LAST_OS_PARAMETER_GPR){  
            peekAddr(gp++, stackIndex);
          } else {
            peekAddr(S0, stackIndex);
            asm.emitSTAddr(S0, callee_param_index - BYTES_IN_ADDRESS, FP);
          }
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert(stackIndex == 0);
      }

      // (2) Call it
      VM_Field ip = VM_Entrypoints.getSysCallField(methodName.toString());
      generateSysCall(paramBytes, ip);

      // (3) Pop Java expression stack
      discardSlots(paramWords);

      // (4) Push return value (if any)
      VM_TypeReference rtype = methodToBeCalled.getReturnType();
      if (rtype.isIntLikeType()) {
        pushInt(T0);
      } else if (rtype.isWordType() || rtype.isReferenceType()) {
        pushAddr(T0); 
      } else if (rtype.isDoubleType()) {
        pushDouble(FIRST_OS_PARAMETER_FPR);
      } else if (rtype.isFloatType()) {
        pushFloat(FIRST_OS_PARAMETER_FPR);
      } else if (rtype.isLongType()) {
        pushLong(T0, VM.BuildFor64Addr?T0:T1);
      }
      return true;
    }

    if (methodToBeCalled.getType() == VM_TypeReference.Address) {
      // Address.xyz magic

      VM_TypeReference[] types = methodToBeCalled.getParameterTypes();

      // Loads all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.loadAddress ||
          methodName == VM_MagicNames.loadObjectReference ||
          methodName == VM_MagicNames.loadWord) {

        if (types.length == 0) {
          popAddr(T0);                  // pop base 
          asm.emitLAddr(T0,  0, T0);    // *(base)
          pushAddr(T0);                 // push *(base)
        } else {
          popInt(T1);                   // pop offset
          popAddr(T0);                  // pop base 
          asm.emitLAddrX(T0, T1, T0);   // *(base+offset)
          pushAddr(T0);                 // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadChar ||
          methodName == VM_MagicNames.loadShort) {

        if (types.length == 0) {
          popAddr(T0);                  // pop base
          asm.emitLHZ(T0, 0, T0);       // load with zero extension.
          pushInt(T0);                  // push *(base) 
        } else {
          popInt(T1);                   // pop offset
          popAddr(T0);                  // pop base
          asm.emitLHZX(T0, T1, T0);     // load with zero extension.
          pushInt(T0);                  // push *(base+offset) 
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadByte) {
        if (types.length == 0) {
          popAddr(T0);                  // pop base
          asm.emitLBZ(T0, 0, T0);       // load with zero extension.
          pushInt(T0);                  // push *(base) 
        } else {
          popInt(T1);                   // pop offset
          popAddr(T0);                  // pop base
          asm.emitLBZX(T0, T1, T0);     // load with zero extension.
          pushInt(T0);                  // push *(base+offset) 
        }
        return true;
      }


      if (methodName == VM_MagicNames.loadInt ||
          methodName == VM_MagicNames.loadFloat) {

        if (types.length == 0) {
          popAddr(T0);                  // pop base 
          asm.emitLInt(T0,  0, T0);     // *(base)
          pushInt(T0);                  // push *(base)
        } else {
          popInt(T1);                   // pop offset
          popAddr(T0);                  // pop base 
          asm.emitLIntX(T0, T1, T0);    // *(base+offset)
          pushInt(T0);                  // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadDouble ||
          methodName == VM_MagicNames.loadLong) {

        if (types.length == 0) {
          popAddr(T1);                  // pop base 
          asm.emitLFD (F0, 0, T1);      // *(base)
          pushDouble(F0);               // push double
        } else {       
          popInt(T2);                   // pop offset 
          popAddr(T1);                  // pop base 
          asm.emitLFDX (F0, T1, T2);    // *(base+offset)
          pushDouble(F0);               // push *(base+offset)
        }
        return true;
      }
      
      // Prepares all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.prepareInt) {
        if (types.length == 0) {
          popAddr(T0);                             // pop base 
          asm.emitLWARX(T0, 0, T0);                // *(base), setting reservation address
          // this Integer is not sign extended !!
          pushInt(T0);                             // push *(base+offset)
        } else {
          popInt(T1);                              // pop offset
          popAddr(T0);                             // pop base 
          asm.emitLWARX(T0,  T1, T0);              // *(base+offset), setting reservation address
          // this Integer is not sign extended !!
          pushInt(T0);                             // push *(base+offset)
        }
        return true;
      }

      if (methodName == VM_MagicNames.prepareWord || 
          methodName == VM_MagicNames.prepareObjectReference ||
          methodName == VM_MagicNames.prepareAddress) {
        if (types.length == 0) {
          popAddr(T0);                             // pop base
          if (VM.BuildFor32Addr) {
            asm.emitLWARX(T0, 0, T0);            // *(base+offset), setting 
          } else {                               // reservation address
            asm.emitLDARX(T0, 0, T0);
          }
          pushAddr(T0);                            // push *(base+offset)
        } else {
          popInt(T1);                              // pop offset
          popAddr(T0);                             // pop base
          if (VM.BuildFor32Addr) {
            asm.emitLWARX(T0,  T1, T0);          // *(base+offset), setting 
          } else {                               // reservation address
            asm.emitLDARX(T0, T1, T0);
          }
          pushAddr(T0);                            // push *(base+offset)
        }
        return true;  
      } 

      // Attempts all take the form:
      // ..., Address, OldVal, NewVal, [Offset] -> ..., Success?

      if (methodName == VM_MagicNames.attempt && 
          types[0] == VM_TypeReference.Int) {
        if (types.length == 2) { 
          popInt(T2);                            // pop newValue                 
          discardSlot();                         // ignore oldValue            
          popAddr(T0);                           // pop base 
          asm.emitSTWCXr(T2,  0, T0);            // store new value and set CR0
          asm.emitLVAL(T0,  0);                  // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0,  1);                  // T0 := true
          fr.resolve(asm);
          pushInt(T0);                           // push success of store
        } else {
          popInt(T1);                            // pop offset
          popInt(T2);                            // pop newValue                 
          discardSlot();                         // ignore oldValue            
          popAddr(T0);                           // pop base 
          asm.emitSTWCXr(T2,  T1, T0);           // store new value and set CR0
          asm.emitLVAL(T0,  0);                  // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0,  1);                  // T0 := true
          fr.resolve(asm);
          pushInt(T0);                           // push success of store
        }
        return true;
      }

      if (methodName == VM_MagicNames.attempt &&
          (types[0] == VM_TypeReference.Address ||
           types[0] == VM_TypeReference.Word)) {

        if (types.length == 2) {
          popAddr(T2);                             // pop newValue
          discardSlot();                           // ignore oldValue
          popAddr(T0);                             // pop base 
          if (VM.BuildFor32Addr) {
            asm.emitSTWCXr(T2,  0, T0);          // store new value and set CR0
          } else { 
            asm.emitSTDCXr(T2,  0, T0);          // store new value and set CR0
          }
          asm.emitLVAL(T0, 0);                   // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);  // skip, if store failed
          asm.emitLVAL(T0, 1);                   // T0 := true
          fr.resolve(asm);  
          pushInt(T0);                           // push success of store
        } else {
          popInt(T1);                              // pop offset
          popAddr(T2);                             // pop newValue
          discardSlot();                           // ignore oldValue
          popAddr(T0);                             // pop base 
          if (VM.BuildFor32Addr) {
            asm.emitSTWCXr(T2,  T1, T0);         // store new value and set CR0
          } else {
            asm.emitSTDCXr(T2,  T1, T0);         // store new value and set CR0
          }
          asm.emitLVAL(T0, 0);                   // T0 := false
          VM_ForwardReference fr = asm.emitForwardBC(NE);             // skip, if store failed
          asm.emitLVAL(T0, 1);                   // T0 := true
          fr.resolve(asm);  
          pushInt(T0);                           // push success of store
        }
        return true;
      }

      // Stores all take the form:
      // ..., Address, Value, [Offset] -> ...
      if (methodName == VM_MagicNames.store) {

        if(types[0] == VM_TypeReference.Word ||
	   types[0] == VM_TypeReference.ObjectReference ||
           types[0] == VM_TypeReference.Address) {
          if (types.length == 1) {
            popAddr(T1);                 // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTAddrX(T1, 0, T0);   // *(base) = newvalue
          } else {
            popInt(T1);                  // pop offset
            popAddr(T2);                 // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTAddrX(T2, T1, T0); // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Byte) {
          if (types.length == 1) {
            popInt(T1);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTBX(T1, 0, T0);      // *(base) = newvalue
          } else {
            popInt(T1);                  // pop offset
            popInt(T2);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTBX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Int ||
           types[0] == VM_TypeReference.Float) {
          if (types.length == 1) {
            popInt(T1);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTWX(T1, 0, T0);      // *(base+offset) = newvalue
          } else {
            popInt(T1);                  // pop offset
            popInt(T2);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTWX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Short ||
           types[0] == VM_TypeReference.Char) {
          if (types.length == 1) {
            popInt(T1);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTHX(T1, 0, T0);      // *(base) = newvalue
          } else {
            popInt(T1);                  // pop offset
            popInt(T2);                  // pop newvalue
            popAddr(T0);                 // pop base 
            asm.emitSTHX(T2, T1, T0);    // *(base+offset) = newvalue
          }
          return true;
        }

        if(types[0] == VM_TypeReference.Double ||
          types[0] == VM_TypeReference.Long) {
          if (types.length == 1) {
            popLong(T2, T1);                      // pop newvalue low and high
            popAddr(T0);                          // pop base 
            if (VM.BuildFor32Addr) {
              asm.emitSTWX(T2, 0, T0);             // *(base) = newvalue low
              asm.emitSTWX(T1, BYTES_IN_INT, T0);  // *(base+4) = newvalue high
            } else {
              asm.emitSTDX(T1, 0, T0);           // *(base) = newvalue 
            }
          } else {
            popInt(T1);                           // pop offset
            popLong(T3, T2);                      // pop newvalue low and high
            popAddr(T0);                          // pop base 
            if (VM.BuildFor32Addr) {
              asm.emitSTWX(T3, T1, T0);           // *(base+offset) = newvalue low
              asm.emitADDI(T1, BYTES_IN_INT, T1); // offset += 4
              asm.emitSTWX(T2, T1, T0);           // *(base+offset) = newvalue high
            } else {
              asm.emitSTDX(T2, T1, T0);           // *(base+offset) = newvalue 
            }
          }
          return true;
        }
      }
    }

    if (methodName == VM_MagicNames.getFramePointer) {
      pushAddr(FP); 
    } else if (methodName == VM_MagicNames.getCallerFramePointer) {
      popAddr(T0);                               // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_FRAME_POINTER_OFFSET, T0); // load frame pointer of caller frame
      pushAddr(T1);                               // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setCallerFramePointer) {
      popAddr(T1); // value
      popAddr(T0); // fp
      asm.emitSTAddr(T1,  STACKFRAME_FRAME_POINTER_OFFSET, T0); // *(address+SFPO) := value
    } else if (methodName == VM_MagicNames.getCompiledMethodID) {
      popAddr(T0);                           // pop  frame pointer of callee frame
      asm.emitLInt (T1, STACKFRAME_METHOD_ID_OFFSET, T0); // load compiled method id
      pushInt(T1);                           // push method ID 
    } else if (methodName == VM_MagicNames.setCompiledMethodID) {
      popInt(T1); // value
      popAddr(T0); // fp
      asm.emitSTW(T1,  STACKFRAME_METHOD_ID_OFFSET, T0); // *(address+SNIO) := value
    } else if (methodName == VM_MagicNames.getNextInstructionAddress) {
      popAddr(T0);                                  // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // load frame pointer of caller frame
      pushAddr(T1);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.setNextInstructionAddress) {
      popAddr(T1); // value
      popAddr(T0); // fp
      asm.emitSTAddr(T1,  STACKFRAME_NEXT_INSTRUCTION_OFFSET, T0); // *(address+SNIO) := value
    } else if (methodName == VM_MagicNames.getReturnAddressLocation) {
      popAddr(T0);                                  // pop  frame pointer of callee frame
      asm.emitLAddr(T1, STACKFRAME_FRAME_POINTER_OFFSET, T0);    // load frame pointer of caller frame
      asm.emitADDI (T2, STACKFRAME_NEXT_INSTRUCTION_OFFSET, T1); // get location containing ret addr
      pushAddr(T2);                                  // push frame pointer of caller frame
    } else if (methodName == VM_MagicNames.getTocPointer ||
               methodName == VM_MagicNames.getJTOC) {
      pushAddr(JTOC); 
    } else if (methodName == VM_MagicNames.getProcessorRegister) {
      pushAddr(PROCESSOR_REGISTER);
    } else if (methodName == VM_MagicNames.setProcessorRegister) {
      popAddr(PROCESSOR_REGISTER);
    } else if (methodName == VM_MagicNames.getTimeBase) {
      if (VM.BuildFor64Addr) {
        asm.emitMFTB (T1);      // T1 := time base
      } else {
        int label = asm.getMachineCodeIndex();
        asm.emitMFTBU(T0);                      // T0 := time base, upper
        asm.emitMFTB (T1);                      // T1 := time base, lower
        asm.emitMFTBU(T2);                      // T2 := time base, upper
        asm.emitCMP  (T0, T2);                  // T0 == T2?
        asm.emitBC   (NE, label);               // lower rolled over, try again
      }
      pushLong(T0,T1);              
    } else if (methodName == VM_MagicNames.invokeMain) {
      popAddr(T0); // t0 := ip
      asm.emitMTCTR(T0);
      peekAddr(T0,0); // t0 := parameter
      asm.emitBCCTRL();          // call
      discardSlot(); // pop parameter
    } else if (methodName == VM_MagicNames.invokeClassInitializer) {
      popAddr(T0); // t0 := address to be called
      asm.emitMTCTR(T0);
      asm.emitBCCTRL();          // call
    } else if (methodName == VM_MagicNames.invokeMethodReturningVoid) {
      generateMethodInvocation(); // call method
    } else if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      generateMethodInvocation(); // call method
      pushInt(T0);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      generateMethodInvocation(); // call method
      pushLong(T0, VM.BuildFor64Addr?T0:T1);       // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      generateMethodInvocation(); // call method
      pushFloat(F0);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      generateMethodInvocation(); // call method
      pushDouble(F0);     // push result
    } else if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      generateMethodInvocation(); // call method
      pushAddr(T0);       // push result
    } else if (methodName == VM_MagicNames.addressArrayCreate) {
      VM_Array type = methodToBeCalled.getType().resolve().asArray();
      emit_resolved_newarray(type);
    } else if (methodName == VM_MagicNames.addressArrayLength) {
      emit_arraylength();
    } else if (methodName == VM_MagicNames.addressArrayGet) {
      if (VM.BuildFor32Addr || methodToBeCalled.getType() == VM_TypeReference.CodeArray) {
        emit_iaload();
      } else {
        genBoundsCheck();
        asm.emitSLDI (T1, T1,  LOG_BYTES_IN_ADDRESS);  // convert index to offset
        asm.emitLAddrX(T2, T0, T1);  // load desired array element
        pushAddr(T2);
      }
    } else if (methodName == VM_MagicNames.addressArraySet) {
      if (VM.BuildFor32Addr || methodToBeCalled.getType() == VM_TypeReference.CodeArray) {
        emit_iastore();  
      } else {
        popAddr(T2);                                   // T2 is value to store
        genBoundsCheck();
        asm.emitSLDI (T1, T1,  LOG_BYTES_IN_ADDRESS);  // convert index to offset
        asm.emitSTAddrX (T2, T0, T1);                  // store value in array
      }
    } else if (methodName == VM_MagicNames.getIntAtOffset) { 
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitLIntX (T0, T1, T0); // *(object+offset)
      pushInt(T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getObjectAtOffset ||
               methodName == VM_MagicNames.getWordAtOffset || 
               methodName == VM_MagicNames.getObjectArrayAtOffset) {
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitLAddrX(T0, T1, T0); // *(object+offset)
      pushAddr(T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.getByteAtOffset) {
      popInt(T1);   // pop offset
      popAddr(T0);   // pop object
      asm.emitLBZX(T0, T1, T0);   // load byte with zero extension.
      pushInt(T0);    // push *(object+offset) 
    } else if (methodName == VM_MagicNames.getCharAtOffset) {
      popInt(T1);   // pop offset
      popAddr(T0);   // pop object
      asm.emitLHZX(T0, T1, T0);   // load char with zero extension.
      pushInt(T0);    // push *(object+offset) 
    } else if (methodName == VM_MagicNames.setIntAtOffset){
      popInt(T2); // pop newvalue
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setObjectAtOffset ||
               methodName == VM_MagicNames.setWordAtOffset) {
      if (methodToBeCalled.getParameterTypes().length == 4) {
        discardSlot(); // discard locationMetadata parameter
      }
      popAddr(T2); // pop newvalue
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitSTAddrX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setByteAtOffset) {
      popInt(T2); // pop newvalue
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitSTBX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.setCharAtOffset) {
      popInt(T2); // pop newvalue
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitSTHX(T2, T1, T0); // *(object+offset) = newvalue
    } else if (methodName == VM_MagicNames.getLongAtOffset ||
               methodName == VM_MagicNames.getDoubleAtOffset) {
      popInt(T2); // pop offset
      popAddr(T1); // pop object
      asm.emitLFDX (F0, T1, T2); 
      pushDouble(F0);
    } else if ((methodName == VM_MagicNames.setLongAtOffset) 
               || (methodName == VM_MagicNames.setDoubleAtOffset)) {
      popLong(T3, T2);  
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      if (VM.BuildFor32Addr) {
        asm.emitSTWX(T3, T1, T0); // *(object+offset) = newvalue low
        asm.emitADDI(T1, BYTES_IN_INT, T1); // offset += 4
        asm.emitSTWX(T2, T1, T0); // *(object+offset) = newvalue high
      } else {
        asm.emitSTDX(T2, T1, T0); // *(object+offset) = newvalue 
      } 
    } else if (methodName == VM_MagicNames.getMemoryInt){
      popAddr(T0); // address
      asm.emitLInt (T0,  0, T0); // *address
      pushInt(T0); // *sp := *address
    } else if (methodName == VM_MagicNames.getMemoryWord ||
               methodName == VM_MagicNames.getMemoryAddress) {
      popAddr(T0); // address
      asm.emitLAddr(T0,  0, T0); // *address
      pushAddr(T0); // *sp := *address
    } else if (methodName == VM_MagicNames.setMemoryInt ){
      popInt(T1); // value
      popAddr(T0); // address
      asm.emitSTW(T1,  0, T0); // *address := value
    } else if (methodName == VM_MagicNames.setMemoryWord ||
               methodName == VM_MagicNames.setMemoryAddress) {
      if (methodToBeCalled.getParameterTypes().length == 3) {
        discardSlot(); // discard locationMetadata parameter
      }
      popAddr(T1); // value
      popAddr(T0); // address
      asm.emitSTAddr(T1,  0, T0); // *address := value
    } else if (methodName == VM_MagicNames.prepareInt){
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
      // this Integer is not sign extended !!
      pushInt(T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.prepareObject ||
               methodName == VM_MagicNames.prepareAddress ||
               methodName == VM_MagicNames.prepareWord) {
      popInt(T1); // pop offset
      popAddr(T0); // pop object
      if (VM.BuildFor32Addr) {
        asm.emitLWARX(T0,  T1, T0); // *(object+offset), setting processor's reservation address
      } else {
        asm.emitLDARX(T0, T1, T0);
      }
      pushAddr(T0); // push *(object+offset)
    } else if (methodName == VM_MagicNames.attemptInt){
      popInt(T2);  // pop newValue
      discardSlot(); // ignore oldValue
      popInt(T1);  // pop offset
      popAddr(T0);  // pop object
      asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
      asm.emitLVAL(T0,  0);  // T0 := false
      VM_ForwardReference fr = asm.emitForwardBC(NE); // skip, if store failed
      asm.emitLVAL(T0,  1);   // T0 := true
      fr.resolve(asm);
      pushInt(T0);  // push success of conditional store
    } else if (methodName == VM_MagicNames.attemptObject ||
               methodName == VM_MagicNames.attemptAddress ||
               methodName == VM_MagicNames.attemptObjectReference || 
               methodName == VM_MagicNames.attemptWord) {
      popAddr(T2);  // pop newValue
      discardSlot(); // ignore oldValue
      popInt(T1);  // pop offset
      popAddr(T0);  // pop object
      if (VM.BuildFor32Addr) {
        asm.emitSTWCXr(T2,  T1, T0); // store new value and set CR0
      } else {
        asm.emitSTDCXr(T2,  T1, T0); // store new value and set CR0
      }
      asm.emitLVAL(T0, 0);  // T0 := false
      VM_ForwardReference fr = asm.emitForwardBC(NE); // skip, if store failed
      asm.emitLVAL(T0, 1);   // T0 := true
      fr.resolve(asm);
      pushInt(T0);  // push success of conditional store
    } else if (methodName == VM_MagicNames.saveThreadState) {
      peekAddr(T0, 0); // T0 := address of VM_Registers object
      asm.emitLAddrToc(S0, VM_Entrypoints.saveThreadStateInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitBCCTRL(); // call out of line machine code
      discardSlot();  // pop arg
    } else if (methodName == VM_MagicNames.threadSwitch) {
      peekAddr(T1, 0); // T1 := address of VM_Registers of new thread
      peekAddr(T0, 1); // T0 := address of previous VM_Thread object
      asm.emitLAddrToc(S0, VM_Entrypoints.threadSwitchInstructionsField.getOffset());
      asm.emitMTCTR(S0);
      asm.emitBCCTRL();
      discardSlots(2);  // pop two args
    } else if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      peekAddr(T0, 0); // T0 := address of VM_Registers object
      asm.emitLAddrToc(S0, VM_Entrypoints.restoreHardwareExceptionStateInstructionsField.getOffset());
      asm.emitMTLR(S0);
      asm.emitBCLR(); // branch to out of line machine code (does not return)
    } else if (methodName == VM_MagicNames.returnToNewStack) {
      peekAddr(FP, 0);                                  // FP := new stackframe
      asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP); // fetch...
      asm.emitMTLR(S0);                                         // ...return address
      asm.emitBCLR ();                                           // return to caller
    } else if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM._assert(klass.isDynamicBridge());
         
      // fetch parameter (address to branch to) into CT register
      //
      peekAddr(T0, 0);
      asm.emitMTCTR(T0);

      // restore volatile and non-volatile registers
      // (note that these are only saved for "dynamic bridge" methods)
      //
      int offset = frameSize;

      // restore non-volatile and volatile fprs
      for (int i = LAST_NONVOLATILE_FPR; i >= FIRST_VOLATILE_FPR; --i)
        asm.emitLFD(i, offset -= BYTES_IN_DOUBLE, FP);
      
      // restore non-volatile gprs
      for (int i = LAST_NONVOLATILE_GPR; i >= FIRST_NONVOLATILE_GPR; --i)
        asm.emitLAddr(i, offset -= BYTES_IN_ADDRESS, FP);
            
      // skip saved thread-id, processor, and scratch registers
      offset -= (FIRST_NONVOLATILE_GPR - LAST_VOLATILE_GPR - 1) * BYTES_IN_ADDRESS;
         
      // restore volatile gprs
      for (int i = LAST_VOLATILE_GPR; i >= FIRST_VOLATILE_GPR; --i)
        asm.emitLAddr(i, offset -= BYTES_IN_ADDRESS, FP);
          
      // pop stackframe
      asm.emitLAddr(FP, 0, FP);
         
      // restore link register
      asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
      asm.emitMTLR(S0);

      asm.emitBCCTR(); // branch always, through count register
    } else if (methodName == VM_MagicNames.objectAsAddress         ||
               methodName == VM_MagicNames.addressAsByteArray      ||
               methodName == VM_MagicNames.addressAsIntArray       ||
               methodName == VM_MagicNames.addressAsObject         ||
               methodName == VM_MagicNames.addressAsObjectArray    ||
               methodName == VM_MagicNames.addressAsType           ||
               methodName == VM_MagicNames.objectAsType            ||
               methodName == VM_MagicNames.objectAsByteArray       ||
               methodName == VM_MagicNames.objectAsShortArray      ||
               methodName == VM_MagicNames.objectAsIntArray        ||
               methodName == VM_MagicNames.addressAsThread         ||
               methodName == VM_MagicNames.objectAsThread          ||
               methodName == VM_MagicNames.objectAsProcessor       ||
               methodName == VM_MagicNames.threadAsCollectorThread ||
               methodName == VM_MagicNames.addressAsRegisters      ||
               methodName == VM_MagicNames.addressAsStack          ||
               methodName == VM_MagicNames.floatAsIntBits          ||
               methodName == VM_MagicNames.intBitsAsFloat          ||
               methodName == VM_MagicNames.doubleAsLongBits        ||
               methodName == VM_MagicNames.longBitsAsDouble) {
      // no-op (a type change, not a representation change)
    } else if (methodName == VM_MagicNames.getObjectType) {
      popAddr(T0);                   // get object pointer
      VM_ObjectModel.baselineEmitLoadTIB(asm,T0,T0);
      asm.emitLAddr(T0,  TIB_TYPE_INDEX << LOG_BYTES_IN_ADDRESS, T0); // get "type" field from type information block
      pushAddr(T0);                   // *sp := type
    } else if (methodName == VM_MagicNames.getArrayLength) {
      popAddr(T0);                   // get object pointer
      asm.emitLInt(T0,  VM_ObjectModel.getArrayLengthOffset(), T0); // get array length field
      pushInt(T0);                   // *sp := length
    } else if (methodName == VM_MagicNames.sync) {
      asm.emitSYNC();
    } else if (methodName == VM_MagicNames.isync) {
      asm.emitISYNC();
    } else if (methodName == VM_MagicNames.dcbst) {
      popAddr(T0);    // address
      asm.emitDCBST(0, T0);
    } else if (methodName == VM_MagicNames.icbi) {
      popAddr(T0);    // address
      asm.emitICBI(0, T0);
    } else if (methodName == VM_MagicNames.wordToInt || 
               methodName == VM_MagicNames.wordToAddress ||
               methodName == VM_MagicNames.wordToOffset ||
               methodName == VM_MagicNames.wordToObject ||
               methodName == VM_MagicNames.wordFromObject ||
               methodName == VM_MagicNames.wordToObjectReference ||
               methodName == VM_MagicNames.wordToExtent ||
               methodName == VM_MagicNames.wordToWord) {
      // no-op   
    } else if (methodName == VM_MagicNames.wordToLong) {
      asm.emitLVAL(T0,0);
      pushAddr(T0);
    } else if (methodName == VM_MagicNames.wordFromInt ||
               methodName == VM_MagicNames.wordFromIntSignExtend) {
      if (VM.BuildFor64Addr) {
        popInt(T0);
        pushAddr(T0);
      } // else no-op
    } else if (methodName == VM_MagicNames.wordFromIntZeroExtend) {
      if (VM.BuildFor64Addr) {
        asm.emitLWZ(T0, spTopOffset + BYTES_IN_STACKSLOT - BYTES_IN_INT, FP);
        pokeAddr(T0,0);
      } // else no-op
    } else if (methodName == VM_MagicNames.wordFromLong) {
      discardSlot();
    } else if (methodName == VM_MagicNames.wordAdd) {
      if (VM.BuildFor64Addr && (methodToBeCalled.getParameterTypes()[0] == VM_TypeReference.Int)){
        popInt(T0);
      } else {
        popAddr(T0);
      }
      popAddr(T1);
      asm.emitADD (T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordSub ||
               methodName == VM_MagicNames.wordDiff) {
      if (VM.BuildFor64Addr && (methodToBeCalled.getParameterTypes()[0] == VM_TypeReference.Int)){
        popInt(T0);
                } else {
        popAddr(T0);
                }
      popAddr(T1);
      asm.emitSUBFC (T2, T0, T1);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordEQ) {
       generateAddrComparison(false, EQ);
    } else if (methodName == VM_MagicNames.wordNE) {
       generateAddrComparison(false, NE);
    } else if (methodName == VM_MagicNames.wordLT) {
      generateAddrComparison(false, LT);
    } else if (methodName == VM_MagicNames.wordLE) {
      generateAddrComparison(false, LE);
     } else if (methodName == VM_MagicNames.wordGT) {
      generateAddrComparison(false, GT);
    } else if (methodName == VM_MagicNames.wordGE) {
      generateAddrComparison(false, GE);
    } else if (methodName == VM_MagicNames.wordsLT) {
      generateAddrComparison(true, LT);
    } else if (methodName == VM_MagicNames.wordsLE) {
      generateAddrComparison(true, LE);
     } else if (methodName == VM_MagicNames.wordsGT) {
      generateAddrComparison(true, GT);
    } else if (methodName == VM_MagicNames.wordsGE) {
      generateAddrComparison(true, GE);
    } else if (methodName == VM_MagicNames.wordIsZero ||
               methodName == VM_MagicNames.wordIsNull) {
      // unsigned comparison generating a boolean
      asm.emitLVAL(T0,  0);
      pushAddr(T0);
      generateAddrComparison(false, EQ);
    } else if (methodName == VM_MagicNames.wordIsMax) {
      // unsigned comparison generating a boolean
      asm.emitLVAL(T0, -1);
      pushAddr(T0);
      generateAddrComparison(false, EQ);
    } else if (methodName == VM_MagicNames.wordZero) {
      asm.emitLVAL (T0,  0);
      pushAddr(T0);
    } else if (methodName == VM_MagicNames.wordOne) {
      asm.emitLVAL (T0,  1);
      pushAddr(T0);
    } else if (methodName == VM_MagicNames.wordMax) {
      asm.emitLVAL (T0, -1);
      pushAddr(T0);
    } else if (methodName == VM_MagicNames.wordAnd) {
      popAddr(T0);
      popAddr(T1);
      asm.emitAND(T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordOr) {
      popAddr(T0);
      popAddr(T1);
      asm.emitOR (T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordNot) {
      popAddr(T0);
      asm.emitLVAL(T1, -1);
      asm.emitXOR(T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordXor) {
      popAddr(T0);
      popAddr(T1);
      asm.emitXOR(T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordLsh) {
      popInt(T0);
      popAddr(T1);
           asm.emitSLAddr (T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordRshl) {
      popInt(T0);
      popAddr(T1);
           asm.emitSRAddr (T2, T1, T0);
      pushAddr(T2);
    } else if (methodName == VM_MagicNames.wordRsha) {
      popInt(T0);
      popAddr(T1);
           asm.emitSRA_Addr (T2, T1, T0);
      pushAddr(T2);
    } else {
      return false;
    }
    return true;
  }

  /** Emit code to perform an unsigned comparison on 2 address values
    * @param cc: condition to test
    */ 
  private void generateAddrComparison(boolean signed, int cc) {
    popAddr(T1);
    popAddr(T0);
    asm.emitLVAL(T2,  1);
    if (signed)
           asm.emitCMPAddr(T0, T1);
    else
           asm.emitCMPLAddr(T0, T1);
    VM_ForwardReference fr = asm.emitForwardBC(cc);
    asm.emitLVAL(T2,  0);
    fr.resolve(asm);
    pushInt(T2);
  }


  /** 
   * Indicate if specified VM_Magic method causes a frame to be created on the runtime stack.
   * @param methodToBeCalled:   VM_Method of the magic method being called
   * @return true if method causes a stackframe to be created
   */
  public static boolean checkForActualCall(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
    return methodName == VM_MagicNames.invokeMain                  ||
      methodName == VM_MagicNames.invokeClassInitializer      ||
      methodName == VM_MagicNames.invokeMethodReturningVoid   ||
      methodName == VM_MagicNames.invokeMethodReturningInt    ||
      methodName == VM_MagicNames.invokeMethodReturningLong   ||
      methodName == VM_MagicNames.invokeMethodReturningFloat  ||
      methodName == VM_MagicNames.invokeMethodReturningDouble ||
      methodName == VM_MagicNames.invokeMethodReturningObject ||
      methodName == VM_MagicNames.addressArrayCreate;
  }


  //----------------//
  // implementation //
  //----------------//

  /** 
   * Generate code to invoke arbitrary method with arbitrary parameters/return value.
   * We generate inline code that calls "VM_OutOfLineMachineCode.reflectiveMethodInvokerInstructions"
   * which, at runtime, will create a new stackframe with an appropriately sized spill area
   * (but no register save area, locals, or operand stack), load up the specified
   * fpr's and gpr's, call the specified method, pop the stackframe, and return a value.
   */
  private void generateMethodInvocation () {
    // On entry the stack looks like this:
    //
    //                       hi-mem
    //            +-------------------------+    \
    //            |         code[]          |     |
    //            +-------------------------+     |
    //            |         gprs[]          |     |
    //            +-------------------------+     |- java operand stack
    //            |         fprs[]          |     |
    //            +-------------------------+     |
    //            |         spills[]        |     |
    //            +-------------------------+    /

    // fetch parameters and generate call to method invoker
    //
    asm.emitLAddrToc (S0, VM_Entrypoints.reflectiveMethodInvokerInstructionsField.getOffset());
    peekAddr(T0, 3);        // t0 := code
    asm.emitMTCTR (S0);
    peekAddr(T1, 2);        // t1 := gprs
    peekAddr(T2, 1);        // t2 := fprs
    peekAddr(T3, 0);        // t3 := spills
    asm.emitBCCTRL();
    discardSlots(4);       // pop parameters
  }

  /** 
   * Generate call and return sequence to invoke a C function through the
   * boot record field specificed by target. 
   * Caller handles parameter passing and expression stack 
   * (setting up args, pushing return, adjusting stack height).
   *
   * <pre>
   *  Create a linkage area that's compatible with RS6000 "C" calling conventions.
   * Just before the call, the stack looks like this:
   *
   *                     hi-mem
   *            +-------------------------+  . . . . . . . .
   *            |          ...            |                  \
   *            +-------------------------+                   |
   *            |          ...            |    \              |
   *            +-------------------------+     |             |
   *            |       (int val0)        |     |  java       |- java
   *            +-------------------------+     |-  operand   |   stack
   *            |       (int val1)        |     |    stack    |    frame
   *            +-------------------------+     |             |
   *            |          ...            |     |             |
   *            +-------------------------+     |             |
   *            |      (int valN-1)       |     |             |
   *            +-------------------------+    /              |
   *            |          ...            |                   |
   *            +-------------------------+                   |
   *            |                         | <-- spot for this frame's callee's return address
   *            +-------------------------+                   |
   *            |          MI             | <-- this frame's method id
   *            +-------------------------+                   |
   *            |       saved FP          | <-- this frame's caller's frame
   *            +-------------------------+  . . . . . . . . /
   *            |      saved JTOC         |
   *            +-------------------------+  . . . . . . . . . . . . . .
   *            | parameterN-1 save area  | +  \                         \
   *            +-------------------------+     |                         |
   *            |          ...            | +   |                         |
   *            +-------------------------+     |- register save area for |
   *            |  parameter1 save area   | +   |    use by callee        |
   *            +-------------------------+     |                         |
   *            |  parameter0 save area   | +  /                          |  rs6000
   *            +-------------------------+                               |-  linkage
   *        +20 |       TOC save area     | +                             |    area
   *            +-------------------------+                               |
   *        +16 |       (reserved)        | -    + == used by callee      |
   *            +-------------------------+      - == ignored by callee   |
   *        +12 |       (reserved)        | -                             |
   *            +-------------------------+                               |
   *         +8 |       LR save area      | +                             |
   *            +-------------------------+                               |
   *         +4 |       CR save area      | +                             |
   *            +-------------------------+                               |
   *  FP ->  +0 |       (backlink)        | -                             |
   *            +-------------------------+  . . . . . . . . . . . . . . /
   *
   * Notes:
   * 1. parameters are according to host OS calling convention.
   * 2. space is also reserved on the stack for use by callee
   *    as parameter save area
   * 3. parameters are pushed on the java operand stack left to right
   *    java conventions) but if callee saves them, they will
   *    appear in the parameter save area right to left (C conventions)
   */
  private void generateSysCall(int parametersSize, VM_Field target) {
    int linkageAreaSize   = parametersSize + BYTES_IN_STACKSLOT + (6 * BYTES_IN_STACKSLOT);

    if (VM.BuildFor32Addr) {
      asm.emitSTWU (FP,  -linkageAreaSize, FP);        // create linkage area
    } else {
      asm.emitSTDU (FP,  -linkageAreaSize, FP);        // create linkage area
    }
    asm.emitSTAddr(JTOC, linkageAreaSize-BYTES_IN_STACKSLOT, FP);      // save JTOC

    // acquire toc and ip from bootrecord
    asm.emitLAddrToc(S0, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitLAddr(JTOC, VM_Entrypoints.sysTOCField.getOffset(), S0);
    asm.emitLAddr(0, target.getOffset(), S0);

    // call it
    asm.emitMTCTR(0);
    asm.emitBCCTRL(); 

    // cleanup
    asm.emitLAddr(JTOC, linkageAreaSize - BYTES_IN_STACKSLOT, FP);    // restore JTOC
    asm.emitADDI (FP, linkageAreaSize, FP);        // remove linkage area
  }
}

