/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.adaptive.recompilation.VM_InvocationCounts;
import org.jikesrvm.adaptive.VM_AosEntrypoints;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_FieldReference;
import org.jikesrvm.classloader.VM_InterfaceInvocation;
import org.jikesrvm.classloader.VM_InterfaceMethodSignature;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiler;
import org.jikesrvm.compilers.baseline.VM_EdgeCounts;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.assembler.VM_ForwardReference;
import org.jikesrvm.compilers.common.assembler.ia32.VM_Assembler;
import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.ia32.VM_ProcessorLocalState;
import org.jikesrvm.jni.ia32.VM_JNICompiler;
import org.jikesrvm.memorymanagers.mminterface.MM_Constants;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_MagicNames;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.runtime.VM_Statics;
import org.jikesrvm.runtime.VM_ArchEntrypoints;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * VM_Compiler is the baseline compiler class for the IA32 architecture.
 */
public abstract class VM_Compiler extends VM_BaselineCompiler implements VM_BaselineConstants, VM_SizeConstants {

  private final int parameterWords;
  private int firstLocalOffset;

  private static final Offset NO_SLOT = Offset.zero();
  private static final Offset ONE_SLOT = NO_SLOT.plus(WORDSIZE);
  private static final Offset TWO_SLOTS = ONE_SLOT.plus(WORDSIZE);
  private static final Offset THREE_SLOTS = TWO_SLOTS.plus(WORDSIZE);
  private static final Offset FOUR_SLOTS = THREE_SLOTS.plus(WORDSIZE);
  private static final Offset FIVE_SLOTS = FOUR_SLOTS.plus(WORDSIZE);
  private static final Offset MINUS_ONE_SLOT = NO_SLOT.minus(WORDSIZE);

  /**
   * Create a VM_Compiler object for the compilation of method.
   */
  protected VM_Compiler(VM_BaselineCompiledMethod cm) {
    super(cm);
    stackHeights = new int[bcodes.length()];
    parameterWords = method.getParameterWords() + (method.isStatic() ? 0 : 1); // add 1 for this pointer
  }

  protected void initializeCompiler() {
    //nothing to do for intel
  }

  public final int getLastFixedStackRegister() {
    return -1; //doesn't dedicate registers to stack;
  }

  public final int getLastFloatStackRegister() {
    return -1; //doesn't dedicate registers to stack;
  }

  @Uninterruptible
  @Inline
  public static int getGeneralLocalLocation(int index, int[] localloc, VM_NormalMethod m) {
    return offsetToLocation(getStartLocalOffset(m) -
                            (index << LOG_BYTES_IN_ADDRESS)); //we currently do not use location arrays on intel
  }

  @Uninterruptible
  @Inline
  public static int getFloatLocalLocation(int index, int[] localloc, VM_NormalMethod m) {
    return offsetToLocation(getStartLocalOffset(m) -
                            (index << LOG_BYTES_IN_ADDRESS)); //we currently do not use location arrays on intel
  }

  @Inline
  @Uninterruptible
  public static int locationToOffset(int location) {
    return -location;
  }

  @Inline
  @Uninterruptible
  public static int offsetToLocation(int offset) {
    return -offset;
  }

  /**
   * The last true local
   */
  public static int getEmptyStackOffset(VM_NormalMethod m) {
    return getFirstLocalOffset(m) - (m.getLocalWords() << LG_WORDSIZE) + WORDSIZE;
  }

  /**
   * This is misnamed.  It should be getFirstParameterOffset.
   * It will not work as a base to access true locals.
   * TODO!! make sure it is not being used incorrectly
   */
  @Uninterruptible
  @Inline
  private static int getFirstLocalOffset(VM_NormalMethod method) {
    if (method.getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      return STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    } else {
      return STACKFRAME_BODY_OFFSET - (SAVED_GPRS << LG_WORDSIZE);
    }
  }

  @Uninterruptible
  @Inline
  private static int getStartLocalOffset(VM_NormalMethod method) {
    return getFirstLocalOffset(method) + WORDSIZE;
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
   * @param type the type of the constant
   */
  protected final void emit_ldc(Offset offset, byte type) {
    asm.emitPUSH_RegDisp(JTOC, offset);
  }

  /**
   * Emit code to load a 64 bit constant
   * @param offset JTOC offset of the constant
   * @param type the type of the constant
   */
  protected final void emit_ldc2(Offset offset, byte type) {
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegDisp(XMM0, JTOC, offset); // XMM0 is constant value
      asm.emitADD_Reg_Imm(SP, -8);        // adjust stack
      asm.emitMOVQ_RegInd_Reg(SP, XMM0);  // place value on stack
    } else {
      asm.emitPUSH_RegDisp(JTOC, offset.plus(4)); // high 32 bits
      asm.emitPUSH_RegDisp(JTOC, offset);   // low 32 bits
    }
  }

  /*
  * loading local variables
  */

  /**
   * Emit code to load an int local variable
   * @param index the local index to load
   */
  protected final void emit_iload(int index) {
    Offset offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset);
  }

  /**
   * Emit code to local a float local variable
   * @param index the local index to load
   */
  protected final void emit_fload(int index) {
    // identical to iload - code replicated for BaseBase compiler performance
    Offset offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset);
  }

  /**
   * Emit code to load a reference local variable
   * @param index the local index to load
   */
  protected final void emit_aload(int index) {
    // identical to iload - code replicated for BaseBase compiler performance
    Offset offset = localOffset(index);
    asm.emitPUSH_RegDisp(ESP, offset);
  }

  /**
   * Emit code to load a long local variable
   * @param index the local index to load
   */
  protected final void emit_lload(int index) {
    Offset offset = localOffset(index);
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegDisp(XMM0, SP, offset.minus(4)); // XMM0 is local value
      asm.emitADD_Reg_Imm(SP, -8);        // adjust stack
      asm.emitMOVQ_RegInd_Reg(SP, XMM0);  // place value on stack
    } else {
      asm.emitPUSH_RegDisp(ESP, offset); // high part
      asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
    }
  }

  /**
   * Emit code to load a double local variable
   * @param index the local index to load
   */
  protected final void emit_dload(int index) {
    // identical to lload - code replicated for BaseBase compiler performance
    Offset offset = localOffset(index);
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegDisp(XMM0, SP, offset.minus(4)); // XMM0 is local value
      asm.emitADD_Reg_Imm(SP, -8);        // adjust stack
      asm.emitMOVQ_RegInd_Reg(SP, XMM0);  // place value on stack
    } else {
      asm.emitPUSH_RegDisp(ESP, offset); // high part
      asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
    }
  }

  /*
  * storing local variables
  */

  /**
   * Emit code to store an int to a local variable
   * @param index the local index to load
   */
  protected final void emit_istore(int index) {
    Offset offset = localOffset(index).minus(4); // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset);
  }

  /**
   * Emit code to store a float to a local variable
   * @param index the local index to load
   */
  protected final void emit_fstore(int index) {
    // identical to istore - code replicated for BaseBase compiler performance
    Offset offset = localOffset(index).minus(4); // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset);
  }

  /**
   * Emit code to store a reference to a local variable
   * @param index the local index to load
   */
  protected final void emit_astore(int index) {
    // identical to istore - code replicated for BaseBase compiler performance
    Offset offset = localOffset(index).minus(4); // pop computes EA after ESP has moved by 4!
    asm.emitPOP_RegDisp(ESP, offset);
  }

  /**
   * Emit code to store a long to a local variable
   * @param index the local index to load
   */
  protected final void emit_lstore(int index) {
    if (SSE2_BASE) {
      Offset offset = localOffset(index).minus(4);
      asm.emitMOVQ_Reg_RegInd(XMM0, SP);  // XMM0 is stack value
      asm.emitMOVQ_RegDisp_Reg(SP, offset, XMM0);  // place value in local
      asm.emitADD_Reg_Imm(SP, 8);
    } else {
      // pop computes EA after ESP has moved by 4!
      Offset offset = localOffset(index + 1).minus(4);
      asm.emitPOP_RegDisp(ESP, offset); // high part
      asm.emitPOP_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
    }
  }

  /**
   * Emit code to store an double  to a local variable
   * @param index the local index to load
   */
  protected final void emit_dstore(int index) {
    // identical to lstore - code replicated for BaseBase compiler performance
    if (SSE2_BASE) {
      Offset offset = localOffset(index).minus(4);
      asm.emitMOVQ_Reg_RegInd(XMM0, SP);  // XMM0 is stack value
      asm.emitMOVQ_RegDisp_Reg(SP, offset, XMM0);  // place value in local
      asm.emitADD_Reg_Imm(SP, 8);
    } else {
      // pop computes EA after ESP has moved by 4!
      Offset offset = localOffset(index + 1).minus(4);
      asm.emitPOP_RegDisp(ESP, offset); // high part
      asm.emitPOP_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
    }
  }

  /*
  * array loads
  */

  /**
   * Emit code to load from an int array
   */
  protected final void emit_iaload() {
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // push [S0+T0<<2]
    asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.WORD, NO_SLOT);
  }

  /**
   * Emit code to load from a float array
   */
  protected final void emit_faload() {
    // identical to iaload - code replicated for BaseBase compiler performance
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // push [S0+T0<<2]
    asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.WORD, NO_SLOT);
  }

  /**
   * Emit code to load from a reference array
   */
  protected final void emit_aaload() {
    // identical to iaload - code replicated for BaseBase compiler performance
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // push [S0+T0<<2]
    asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.WORD, NO_SLOT);
  }

  /**
   * Emit code to load from a char array
   */
  protected final void emit_caload() {
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, VM_Assembler.SHORT, NO_SLOT);
    asm.emitPUSH_Reg(T1);        // push short onto stack
  }

  /**
   * Emit code to load from a short array
   */
  protected final void emit_saload() {
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, VM_Assembler.SHORT, NO_SLOT);
    asm.emitPUSH_Reg(T1);        // push short onto stack
  }

  /**
   * Emit code to load from a byte/boolean array
   */
  protected final void emit_baload() {
    asm.emitMOV_Reg_RegInd(T0, SP);            // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is array ref
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT);
    asm.emitPUSH_Reg(T1);        // push short onto stack
  }

  /**
   * Emit code to load from a long array
   */
  protected final void emit_laload() {
    asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);    // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT);   // S0 is the array ref
    if (!SSE2_BASE) {
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    }
    genBoundsCheck(asm, T0, S0);                 // T0 is index, S0 is address of array
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegIdx(XMM0, S0, T0, VM_Assembler.LONG, NO_SLOT);
      asm.emitMOVQ_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.LONG, ONE_SLOT); // load high part of desired long array element
      asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.LONG, NO_SLOT);  // load low part of desired long array element
    }
  }

  /**
   * Emit code to load from a double array
   */
  protected final void emit_daload() {
    // identical to laload - code replicated for BaseBase compiler performance
    asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);    // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT);   // S0 is the array ref
    if (!SSE2_BASE) {
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2);     // complete popping the 2 args
    }
    genBoundsCheck(asm, T0, S0);                 // T0 is index, S0 is address of array
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegIdx(XMM0, S0, T0, VM_Assembler.LONG, NO_SLOT);
      asm.emitMOVQ_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.LONG, ONE_SLOT); // load high part of desired long array element
      asm.emitPUSH_RegIdx(S0, T0, VM_Assembler.LONG, NO_SLOT);  // load low part of desired long array element
    }
  }

  /*
  * array stores
  */

  /**
   * Emit code to store to an int array
   */
  protected final void emit_iastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is array ref
    asm.emitMOV_Reg_RegInd(T1, SP);             // T1 is the byte value
    asm.emitADD_Reg_Imm(SP, 3*WORDSIZE);        // Shrink stack
    genBoundsCheck(asm, T0, S0);                // T0 is index, S0 is address of array
    asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.WORD, NO_SLOT, T1); // [S0 + T0<<2] <- T1
  }

  /**
   * Emit code to store to a float array
   */
  protected final void emit_fastore() {
    // identical to iastore - code replicated for BaseBase compiler performance
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is array ref
    asm.emitMOV_Reg_RegInd(T1, SP);             // T1 is the byte value
    asm.emitADD_Reg_Imm(SP, 3*WORDSIZE);        // Shrink stack
    genBoundsCheck(asm, T0, S0);                // T0 is index, S0 is address of array
    asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.WORD, NO_SLOT, T1); // [S0 + T0<<2] <- T1
  }


  /**
   * Emit code to store to a reference array
   */
  protected final void emit_aastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitPUSH_RegDisp(SP, TWO_SLOTS); // duplicate array ref
    asm.emitPUSH_RegDisp(SP, ONE_SLOT);  // duplicate object value
    genParameterRegisterLoad(2);         // pass 2 parameter
    // call checkstore(array ref, value)
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkstoreMethod.getOffset());
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is the array ref
    genBoundsCheck(asm, T0, S0);        // T0 is index, S0 is address of array
    if (MM_Constants.NEEDS_WRITE_BARRIER) {
      VM_Barriers.compileArrayStoreBarrier(asm);
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 3); // complete popping the 3 args
    } else {
      asm.emitMOV_Reg_RegDisp(T1, SP, NO_SLOT); // T1 is the object value
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 3); // complete popping the 3 args
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.WORD, NO_SLOT, T1); // [S0 + T0<<2] <- T1
    }
  }

  /**
   * Emit code to store to a char array
   */
  protected final void emit_castore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is array ref
    asm.emitMOV_Reg_RegInd(T1, SP);             // T1 is the char value
    asm.emitADD_Reg_Imm(SP, 3*WORDSIZE);        // Shrink stack
    genBoundsCheck(asm, T0, S0);        // T0 is index, S0 is address of array
    // store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, VM_Assembler.SHORT, NO_SLOT, T1);
  }

  /**
   * Emit code to store to a short array
   */
  protected final void emit_sastore() {
    // identical to castore - code replicated for BaseBase compiler performance
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is array ref
    asm.emitMOV_Reg_RegInd(T1, SP);             // T1 is the char value
    asm.emitADD_Reg_Imm(SP, 3*WORDSIZE);        // Shrink stack
    genBoundsCheck(asm, T0, S0);        // T0 is index, S0 is address of array
    // store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
    asm.emitMOV_RegIdx_Reg_Word(S0, T0, VM_Assembler.SHORT, NO_SLOT, T1);
  }

  /**
   * Emit code to store to a byte/boolean array
   */
  protected final void emit_bastore() {
    VM_Barriers.compileModifyCheck(asm, 8);
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // T0 is array index
    asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is array ref
    asm.emitMOV_Reg_RegInd(T1, SP);             // T1 is the byte value
    asm.emitADD_Reg_Imm(SP, 3*WORDSIZE);        // Shrink stack
    genBoundsCheck(asm, T0, S0);         // T0 is index, S0 is address of array
    asm.emitMOV_RegIdx_Reg_Byte(S0, T0, VM_Assembler.BYTE, NO_SLOT, T1); // [S0 + T0<<2] <- T1
  }

  /**
   * Emit code to store to a long array
   */
  protected final void emit_lastore() {
    VM_Barriers.compileModifyCheck(asm, 12);
    asm.emitMOV_Reg_RegDisp(T0, SP, TWO_SLOTS);    // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, THREE_SLOTS);  // S0 is the array ref
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegInd(XMM0,SP);            // XMM0 is the value
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 4);       // remove index and ref from the stack
    } else {
      asm.emitMOV_Reg_RegInd(T1, SP);              // low part of long value
    }
    genBoundsCheck(asm, T0, S0);                   // T0 is index, S0 is address of array
    if (SSE2_BASE) {
      asm.emitMOVQ_RegIdx_Reg(S0, T0, VM_Assembler.LONG, NO_SLOT, XMM0); // [S0+T0<<<3] <- XMM0
    } else {
      // [S0 + T0<<3 + 0] <- T1 store low part into array
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.LONG, NO_SLOT, T1);
      asm.emitMOV_Reg_RegDisp(T1, SP, ONE_SLOT); // high part of long value
      // [S0 + T0<<3 + 4] <- T1 store high part into array
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 4);      // remove index and ref from the stack
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.LONG, ONE_SLOT, T1);
    }
  }

  /**
   * Emit code to store to a double array
   */
  protected final void emit_dastore() {
    // identical to lastore - code replicated for BaseBase compiler performance
    VM_Barriers.compileModifyCheck(asm, 12);
    asm.emitMOV_Reg_RegDisp(T0, SP, TWO_SLOTS);    // T0 is the array index
    asm.emitMOV_Reg_RegDisp(S0, SP, THREE_SLOTS);  // S0 is the array ref
    if (SSE2_BASE) {
      asm.emitMOVQ_Reg_RegInd(XMM0,SP);            // XMM0 is the value
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 4);       // remove index and ref from the stack
    } else {
      asm.emitMOV_Reg_RegInd(T1, SP);              // low part of long value
    }
    genBoundsCheck(asm, T0, S0);                   // T0 is index, S0 is address of array
    if (SSE2_BASE) {
      asm.emitMOVQ_RegIdx_Reg(S0, T0, VM_Assembler.LONG, NO_SLOT, XMM0); // [S0+T0<<<3] <- XMM0
    } else {
      // [S0 + T0<<3 + 0] <- T1 store low part into array
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.LONG, NO_SLOT, T1);
      asm.emitMOV_Reg_RegDisp(T1, SP, ONE_SLOT); // high part of long value
      // [S0 + T0<<3 + 4] <- T1 store high part into array
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 4);      // remove index and ref from the stack
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.LONG, ONE_SLOT, T1);
    }
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
    // This could be encoded as the single 3 byte instruction
    // asm.emitADD_Reg_Imm(SP, 8);
    // or as the following 2 1 byte instructions. There doesn't appear to be any
    // performance difference.
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(T0);
  }

  /**
   * Emit code to implement the dup bytecode
   */
  protected final void emit_dup() {
    // This could be encoded as the 2 instructions totalling 4 bytes:
    // asm.emitMOV_Reg_RegInd(T0, SP);
    // asm.emitPUSH_Reg(T0);
    // However, there doesn't seem to be any performance difference to:
    asm.emitPUSH_RegInd(SP);
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
    asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);
    asm.emitMOV_Reg_RegInd(S0, SP);
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
    VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_ArchEntrypoints.jtocField.getOffset());
  }

  /**
   * Emit code to implement the swap bytecode
   */
  protected final void emit_swap() {
    // This could be encoded as the 4 instructions totalling 14 bytes:
    // asm.emitMOV_Reg_RegInd(T0, SP);
    // asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT);
    // asm.emitMOV_RegDisp_Reg(SP, ONE_SLOT, T0);
    // asm.emitMOV_RegInd_Reg(SP, S0);
    // But the following is 4bytes:
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
    asm.emitPOP_Reg(T0);
    asm.emitIMUL2_Reg_RegInd(T0, SP);
    asm.emitMOV_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the idiv bytecode
   */
  protected final void emit_idiv() {
    asm.emitMOV_Reg_RegDisp(ECX,
                            SP,
                            NO_SLOT); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, ONE_SLOT); // EAX is dividend
    asm.emitCDQ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the 2 values
    asm.emitPUSH_Reg(EAX);               // push result
  }

  /**
   * Emit code to implement the irem bytecode
   */
  protected final void emit_irem() {
    asm.emitMOV_Reg_RegDisp(ECX,
                            SP,
                            NO_SLOT); // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitMOV_Reg_RegDisp(EAX, SP, ONE_SLOT); // EAX is dividend
    asm.emitCDQ();                      // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);      // compute EAX/ECX - Quotient in EAX, remainder in EDX
    asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the 2 values
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
    asm.emitPOP_Reg(ECX);
    asm.emitSAR_RegInd_Reg(SP, ECX);
  }

  /**
   * Emit code to implement the iushr bytecode
   */
  protected final void emit_iushr() {
    asm.emitPOP_Reg(ECX);
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
    asm.emitOR_RegInd_Reg(SP, T0);
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
    Offset offset = localOffset(index);
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
    asm.emitADC_RegDisp_Reg(SP, ONE_SLOT, S0);   // add high halves with carry
  }

  /**
   * Emit code to implement the lsub bytecode
   */
  protected final void emit_lsub() {
    asm.emitPOP_Reg(T0);                 // the low half of one long
    asm.emitPOP_Reg(S0);                 // the high half
    asm.emitSUB_RegInd_Reg(SP, T0);          // subtract low halves
    asm.emitSBB_RegDisp_Reg(SP, ONE_SLOT, S0);   // subtract high halves with borrow
  }

  /**
   * Emit code to implement the lmul bytecode
   */
  protected final void emit_lmul() {
    // stack: value1.high = mulitplier
    //        value1.low
    //        value2.high = multiplicand
    //        value2.low  <-- ESP
    if (VM.VerifyAssertions) VM._assert(S0 != EAX);
    if (VM.VerifyAssertions) VM._assert(S0 != EDX);
    // EAX = multiplicand low; SP changed!
    asm.emitPOP_Reg(EAX);
    // EDX = multiplicand high
    asm.emitPOP_Reg(EDX);
    // stack: value1.high = mulitplier
    //        value1.low  <-- ESP
    //        value2.high = multiplicand
    //        value2.low
    // S0 = multiplier high
    asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT);
    // is one operand > 2^32 ?
    asm.emitOR_Reg_Reg(EDX, S0);
    // EDX = multiplier low
    asm.emitMOV_Reg_RegInd(EDX, SP);
    // Jump if we need a 64bit multiply
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    // EDX:EAX = 32bit multiply of multiplier and multiplicand low
    asm.emitMUL_Reg_Reg(EAX, EDX);
    // Jump over 64bit multiply
    VM_ForwardReference fr2 = asm.forwardJMP();
    // Start of 64bit multiply
    fr1.resolve(asm);
    // EDX = multiplicand high * multiplier low
    asm.emitIMUL2_Reg_RegDisp(EDX, SP, MINUS_ONE_SLOT);
    // S0 = multiplier high * multiplicand low
    asm.emitIMUL2_Reg_Reg(S0, EAX);
    // S0 = S0 + EDX
    asm.emitADD_Reg_Reg(S0, EDX);
    // EDX:EAX = 32bit multiply of multiplier and multiplicand low
    asm.emitMUL_Reg_RegInd(EAX, SP);
    // EDX = EDX + S0
    asm.emitADD_Reg_Reg(EDX, S0);
    // Finish up
    fr2.resolve(asm);
    // store EDX:EAX to stack
    asm.emitMOV_RegDisp_Reg(SP, ONE_SLOT, EDX);
    asm.emitMOV_RegInd_Reg(SP, EAX);
  }

  /**
   * Emit code to implement the ldiv bytecode
   */
  protected final void emit_ldiv() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);
    asm.emitOR_Reg_RegDisp(T0, SP, ONE_SLOT);
    asm.emitBranchLikelyNextInstruction();
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    Offset off = Offset.fromIntSignExtend(numNonVols * WORDSIZE);
    for (int i = 0; i < numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(20));
    asm.emitPUSH_RegDisp(SP, off.plus(20));
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongDivideIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols - 1; i >= 0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lrem bytecode
   */
  protected final void emit_lrem() {
    // (1) zero check
    asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);
    asm.emitOR_Reg_RegDisp(T0, SP, ONE_SLOT);
    asm.emitBranchLikelyNextInstruction();
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitINT_Imm(VM_Runtime.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
    fr1.resolve(asm);
    // (2) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    Offset off = Offset.fromIntSignExtend(numNonVols * WORDSIZE);
    for (int i = 0; i < numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (3) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(20));
    asm.emitPUSH_RegDisp(SP, off.plus(20));
    // (4) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysLongRemainderIPField.getOffset());
    // (5) pop space for arguments
    asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);
    // (6) restore RVM nonvolatiles
    for (int i = numNonVols - 1; i >= 0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (7) pop expression stack
    asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);
    // (8) push results
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the lneg bytecode
   */
  protected final void emit_lneg() {
    asm.emitNOT_RegDisp(SP, ONE_SLOT);    // [SP+4] <- ~[SP+4] or high <- ~high
    asm.emitNEG_RegInd(SP);    // [SP] <- -[SP] or low <- -low
    asm.emitSBB_RegDisp_Imm(SP, ONE_SLOT, -1); // [SP+4] += 1+borrow or high += 1+borrow
  }

  /**
   * Emit code to implement the lshsl bytecode
   */
  protected final void emit_lshl() {
    if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert(ECX != T1);
    asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
    asm.emitPOP_Reg(T0);                   // pop low half
    asm.emitPOP_Reg(T1);                   // pop high half
    asm.emitTEST_Reg_Imm(ECX, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitSHLD_Reg_Reg_Reg(T1, T0, ECX);  // shift high half
    asm.emitSHL_Reg_Reg(T0, ECX);           // shift low half
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitMOV_Reg_Reg(T1, T0);  // shift high half
    asm.emitSHL_Reg_Reg(T1, ECX);
    asm.emitXOR_Reg_Reg(T0, T0);  // low half == 0
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half
    asm.emitPUSH_Reg(T0);                   // push low half
  }

  /**
   * Emit code to implement the lshr bytecode
   */
  protected final void emit_lshr() {
    if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert(ECX != T1);
    asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
    asm.emitPOP_Reg(T0);                   // pop low half
    asm.emitPOP_Reg(T1);                   // pop high half
    asm.emitTEST_Reg_Imm(ECX, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);  // shift high half
    asm.emitSAR_Reg_Reg(T1, ECX);           // shift low half
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitMOV_Reg_Reg(T0, T1);  // low half = high half
    asm.emitSAR_Reg_Imm(T1, 31);  // high half = high half >> 31
    asm.emitSAR_Reg_Reg(T0, ECX); // low half = high half >> ecx
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half
    asm.emitPUSH_Reg(T0);                   // push low half
  }

  /**
   * Emit code to implement the lushr bytecode
   */
  protected final void emit_lushr() {
    if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
    if (VM.VerifyAssertions) VM._assert(ECX != T1);
    asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
    asm.emitPOP_Reg(T0);                   // pop low half
    asm.emitPOP_Reg(T1);                   // pop high half
    asm.emitTEST_Reg_Imm(ECX, 32);
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);  // shift high half
    asm.emitSHR_Reg_Reg(T1, ECX);           // shift low half
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitMOV_Reg_Reg(T0, T1);  // low half = high half
    asm.emitXOR_Reg_Reg(T1, T1);  // high half = 0
    asm.emitSHR_Reg_Reg(T0, ECX); // low half = high half >>> ecx
    fr2.resolve(asm);
    asm.emitPUSH_Reg(T1);                   // push high half
    asm.emitPUSH_Reg(T0);                   // push low half
  }

  /**
   * Emit code to implement the land bytecode
   */
  protected final void emit_land() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitAND_RegInd_Reg(SP, T0);
    asm.emitAND_RegDisp_Reg(SP, ONE_SLOT, S0);
  }

  /**
   * Emit code to implement the lor bytecode
   */
  protected final void emit_lor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitOR_RegInd_Reg(SP, T0);
    asm.emitOR_RegDisp_Reg(SP, ONE_SLOT, S0);
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  protected final void emit_lxor() {
    asm.emitPOP_Reg(T0);        // low
    asm.emitPOP_Reg(S0);        // high
    asm.emitXOR_RegInd_Reg(SP, T0);
    asm.emitXOR_RegDisp_Reg(SP, ONE_SLOT, S0);
  }

  /*
  * float ALU
  */

  /**
   * Emit code to implement the fadd bytecode
   */
  protected final void emit_fadd() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);            // XMM0 = value2
      asm.emitADDSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 += value1
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);               // FPU reg. stack <- value2
      asm.emitFADD_Reg_RegDisp(FP0, SP, ONE_SLOT);   // FPU reg. stack += value1
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the fsub bytecode
   */
  protected final void emit_fsub() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 = value1
      asm.emitSUBSS_Reg_RegInd(XMM0, SP);            // XMM0 -= value2
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);    // FPU reg. stack <- value1
      asm.emitFSUB_Reg_RegDisp(FP0, SP, NO_SLOT);    // FPU reg. stack -= value2
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the fmul bytecode
   */
  protected final void emit_fmul() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);            // XMM0 = value2
      asm.emitMULSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 *= value1
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);               // FPU reg. stack <- value2
      asm.emitFMUL_Reg_RegDisp(FP0, SP, ONE_SLOT);   // FPU reg. stack *= value1
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  protected final void emit_fdiv() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 = value1
      asm.emitDIVSS_Reg_RegInd(XMM0, SP);            // XMM0 /= value2
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);    // FPU reg. stack <- value1
      asm.emitFDIV_Reg_RegDisp(FP0, SP, NO_SLOT);    // FPU reg. stack /= value2
      asm.emitPOP_Reg(T0);                           // discard
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the frem bytecode
   */
  protected final void emit_frem() {
    // TODO: Something else when SSE2?
    asm.emitFLD_Reg_RegInd(FP0, SP);                 // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);      // FPU reg. stack <- value1, or b
    asm.emitFPREM();                                 // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg(SP, ONE_SLOT, FP0);     // POP FPU reg. stack (results) onto java stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);                // POP FPU reg. stack onto java stack
    asm.emitPOP_Reg(T0);                             // shrink the stack (T0 discarded)
  }

  /**
   * Emit code to implement the fneg bytecode
   */
  protected final void emit_fneg() {
    if (SSE2_BASE) {
      asm.emitXORPS_Reg_Reg(XMM0, XMM0);             // XMM0 = 0
      asm.emitSUBSS_Reg_RegInd(XMM0, SP);            // XMM0 -= value
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);               // FPU reg. stack <- value1
      asm.emitFCHS();                                // change sign to stop of FPU stack
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /*
  * double ALU
  */

  /**
   * Emit code to implement the dadd bytecode
   */
  protected final void emit_dadd() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);                // XMM0 = value2
      asm.emitADDSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);    // XMM0 += value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // discard
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);                // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);              // FPU reg. stack <- value2
      asm.emitFADD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // FPU reg. stack += value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // shrink the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the dsub bytecode
   */
  protected final void emit_dsub() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);    // XMM0 = value1
      asm.emitSUBSD_Reg_RegInd(XMM0, SP);                // XMM0 -= value2
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // discard
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);                // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS);  // FPU reg. stack <- value1
      asm.emitFSUB_Reg_RegDisp_Quad(FP0, SP, NO_SLOT);   // FPU reg. stack -= value2
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // shrink the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the dmul bytecode
   */
  protected final void emit_dmul() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);                // XMM0 = value2
      asm.emitMULSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);    // XMM0 *= value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // discard
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);                // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);              // FPU reg. stack <- value2
      asm.emitFMUL_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // FPU reg. stack *= value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // shrink the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the ddiv bytecode
   */
  protected final void emit_ddiv() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);    // XMM0 = value1
      asm.emitDIVSD_Reg_RegInd(XMM0, SP);                // XMM0 /= value2
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // discard
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);                // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS);  // FPU reg. stack <- value1
      asm.emitFDIV_Reg_RegInd_Quad(FP0, SP);             // FPU reg. stack /= value2
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);             // shrink the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the drem bytecode
   */
  protected final void emit_drem() {
    // TODO: Something else when SSE2?
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);                // FPU reg. stack <- value2, or a
    asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS);    // FPU reg. stack <- value1, or b
    asm.emitFPREM();                                     // FPU reg. stack <- a%b
    asm.emitFSTP_RegDisp_Reg_Quad(SP, TWO_SLOTS, FP0);   // POP FPU reg. stack (result) onto java stack
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);               // POP FPU reg. stack onto java stack
    asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);               // shrink the stack
  }

  /**
   * Emit code to implement the dneg bytecode
   */
  protected final void emit_dneg() {
    if (SSE2_BASE) {
      asm.emitXORPD_Reg_Reg(XMM0, XMM0);                 // XMM0 = 0
      asm.emitSUBSD_Reg_RegInd(XMM0, SP);                // XMM0 -= value
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);                // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);              // FPU reg. stack <- value1
      asm.emitFCHS();                                    // change sign to stop of FPU stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  /*
  * conversion ops
  */

  /**
   * Emit code to implement the i2l bytecode
   */
  protected final void emit_i2l() {
    asm.emitPUSH_RegInd(SP);                   // duplicate int on stack
    asm.emitSAR_RegDisp_Imm(SP, ONE_SLOT, 31); // sign extend as high word of long
  }

  /**
   * Emit code to implement the l2i bytecode
   */
  protected final void emit_l2i() {
    asm.emitMOV_Reg_RegInd(T0, SP);    // low half of the long
    asm.emitADD_Reg_Imm(SP, WORDSIZE); // throw away high half of the long
    asm.emitMOV_RegInd_Reg(SP, T0);
  }

  /**
   * Emit code to implement the i2f bytecode
   */
  protected final void emit_i2f() {
    if (SSE2_BASE) {
      asm.emitCVTSI2SS_Reg_RegInd(XMM0, SP);
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFILD_Reg_RegInd(FP0, SP);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    }
  }

  /**
   * Emit code to implement the i2d bytecode
   */
  protected final void emit_i2d() {
    if (SSE2_BASE) {
      asm.emitCVTSI2SD_Reg_RegInd(XMM0, SP);
      asm.emitADD_Reg_Imm(SP, -WORDSIZE); // grow the stack
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFILD_Reg_RegInd(FP0, SP);
      asm.emitADD_Reg_Imm(SP, -WORDSIZE); // grow the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    }
  }

  /**
   * Emit code to implement the l2f bytecode
   */
  protected final void emit_l2f() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitADD_Reg_Imm(SP, WORDSIZE); // shrink the stack
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
   * Emit code to implement the f2d bytecode
   */
  protected final void emit_f2d() {
    if (SSE2_BASE) {
      asm.emitCVTSS2SD_Reg_RegInd(XMM0, SP);
      asm.emitADD_Reg_Imm(SP, -WORDSIZE); // grow the stack
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);
      asm.emitADD_Reg_Imm(SP, -WORDSIZE); // grow the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    }
  }

  /**
   * Emit code to implement the d2f bytecode
   */
  protected final void emit_d2f() {
    if (SSE2_BASE) {
      asm.emitCVTSD2SS_Reg_RegInd(XMM0, SP);
      asm.emitADD_Reg_Imm(SP, WORDSIZE); // shrink the stack
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
      asm.emitADD_Reg_Imm(SP, WORDSIZE); // shrink the stack
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    }
  }

  /**
   * Emit code to implement the f2i bytecode
   */
  protected final void emit_f2i() {
    if (SSE2_BASE) {
      // Set up max int in XMM0
      asm.emitMOVSS_Reg_RegDisp(XMM0, JTOC, VM_Entrypoints.maxintFloatField.getOffset());
      // Set up value in XMM1
      asm.emitMOVSS_Reg_RegInd(XMM1, SP);
      // if value > maxint or NaN goto fr1; FP0 = value
      asm.emitUCOMISS_Reg_Reg(XMM0, XMM1);
      VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LLE);
      asm.emitCVTTSS2SI_Reg_Reg(T0, XMM1);
      asm.emitMOV_RegInd_Reg(SP, T0);
      VM_ForwardReference fr2 = asm.forwardJMP();
      fr1.resolve(asm);
      VM_ForwardReference fr3 = asm.forwardJcc(VM_Assembler.PE); // if value == NaN goto fr3
      asm.emitMOV_RegInd_Imm(SP, 0x7FFFFFFF);
      VM_ForwardReference fr4 = asm.forwardJMP();
      fr3.resolve(asm);
      asm.emitMOV_RegInd_Imm(SP, 0);
      fr2.resolve(asm);
      fr4.resolve(asm);
    } else {
      // TODO: use SSE/x87 operations to do this conversion inline taking care of
      // the boundary cases that differ between x87 and Java

      // (1) save RVM nonvolatiles
      int numNonVols = NONVOLATILE_GPRS.length;
      Offset off = Offset.fromIntSignExtend(numNonVols * WORDSIZE);
      for (int i = 0; i < numNonVols; i++) {
        asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
      }
      // (2) Push arg to C function
      asm.emitPUSH_RegDisp(SP, off);
      // (3) invoke C function through bootrecord
      asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
      asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysFloatToIntIPField.getOffset());
      // (4) pop argument;
      asm.emitPOP_Reg(S0);
      // (5) restore RVM nonvolatiles
      for (int i = numNonVols - 1; i >= 0; i--) {
        asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
      }
      // (6) put result on expression stack
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0);
    }
  }

  /**
   * Emit code to implement the f2l bytecode
   */
  protected final void emit_f2l() {
	 // TODO: SSE3 has a FISTTP instruction that stores the value with truncation
    // meaning the FPSCW can be left alone

	 // Setup value into FP1
    asm.emitFLD_Reg_RegInd(FP0, SP);
    // Setup maxint into FP0
    asm.emitFLD_Reg_RegDisp(FP0, JTOC, VM_Entrypoints.maxlongFloatField.getOffset());
    // if value > maxint or NaN goto fr1; FP0 = value
    asm.emitFUCOMIP_Reg_Reg(FP0, FP1);
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LLE);
    // Normally the status and control word rounds numbers, but for conversion
    // to an integer/long value we want truncation. We therefore save the FPSCW,
    // set it to truncation perform operation then restore
    asm.emitADD_Reg_Imm(SP, -WORDSIZE);                      // Grow the stack
    asm.emitFNSTCW_RegDisp(SP, MINUS_ONE_SLOT);              // [SP-4] = fpscw
    asm.emitMOVZX_Reg_RegDisp_Word(T0, SP, MINUS_ONE_SLOT);  // EAX = fpscw
    asm.emitOR_Reg_Imm(T0, 0xC00);                           // EAX = FPSCW in truncate mode
    asm.emitMOV_RegInd_Reg(SP, T0);                          // [SP] = new fpscw value
    asm.emitFLDCW_RegInd(SP);                                // Set FPSCW
    asm.emitFISTP_RegInd_Reg_Quad(SP, FP0);                  // Store 64bit long
    asm.emitFLDCW_RegDisp(SP, MINUS_ONE_SLOT);               // Restore FPSCW
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitFSTP_Reg_Reg(FP0, FP0);                          // pop FPU*1
    VM_ForwardReference fr3 = asm.forwardJcc(VM_Assembler.PE); // if value == NaN goto fr3
    asm.emitMOV_RegInd_Imm(SP, 0x7FFFFFFF);
    asm.emitPUSH_Imm(-1);
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr3.resolve(asm);
    asm.emitMOV_RegInd_Imm(SP, 0);
    asm.emitPUSH_Imm(0);
    fr2.resolve(asm);
    fr4.resolve(asm);
  }

  /**
   * Emit code to implement the d2i bytecode
   */
  protected final void emit_d2i() {
    // TODO: use SSE/x87 operations to do this conversion inline taking care of
    // the boundary cases that differ between x87 and Java

    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    Offset off = Offset.fromIntSignExtend(numNonVols * WORDSIZE);
    for (int i = 0; i < numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToIntIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols - 1; i >= 0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitPOP_Reg(S0); // shrink stack by 1 word
    asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0);
  }

  /**
   * Emit code to implement the d2l bytecode
   */
  protected final void emit_d2l() {
    // TODO: use SSE/x87 operations to do this conversion inline taking care of
    // the boundary cases that differ between x87 and Java

    // (1) save RVM nonvolatiles
    int numNonVols = NONVOLATILE_GPRS.length;
    Offset off = Offset.fromIntSignExtend(numNonVols * WORDSIZE);
    for (int i = 0; i < numNonVols; i++) {
      asm.emitPUSH_Reg(NONVOLATILE_GPRS[i]);
    }
    // (2) Push args to C function (reversed)
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    asm.emitPUSH_RegDisp(SP, off.plus(4));
    // (3) invoke C function through bootrecord
    asm.emitMOV_Reg_RegDisp(S0, JTOC, VM_Entrypoints.the_boot_recordField.getOffset());
    asm.emitCALL_RegDisp(S0, VM_Entrypoints.sysDoubleToLongIPField.getOffset());
    // (4) pop arguments
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(S0);
    // (5) restore RVM nonvolatiles
    for (int i = numNonVols - 1; i >= 0; i--) {
      asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
    }
    // (6) put result on expression stack
    asm.emitMOV_RegDisp_Reg(SP, ONE_SLOT, T1);
    asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0);
  }

  /**
   * Emit code to implement the i2b bytecode
   */
  protected final void emit_i2b() {
    // This could be coded as 2 instructions as follows:
    // asm.emitMOVSX_Reg_RegInd_Byte(T0, SP);
    // asm.emitMOV_RegInd_Reg(SP, T0);
    // Indirection via ESP requires an extra byte for the indirection, so the
    // total code size is 6 bytes. The 3 instruction version below is only 4
    // bytes long and faster on Pentium 4 benchmarks.
    asm.emitPOP_Reg(T0);
    asm.emitMOVSX_Reg_Reg_Byte(T0, T0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the i2c bytecode
   */
  protected final void emit_i2c() {
    // This could be coded as zeroing the high 16bits on stack:
    // asm.emitMOV_RegDisp_Imm_Word(SP, Offset.fromIntSignExtend(2), 0);
    // or as 2 instructions:
    // asm.emitMOVZX_Reg_RegInd_Word(T0, SP);
    // asm.emitMOV_RegInd_Reg(SP, T0);
    // Benchmarks show the following sequence to be more optimal on a Pentium 4
    asm.emitPOP_Reg(T0);
    asm.emitMOVZX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the i2s bytecode
   */
  protected final void emit_i2s() {
    // This could be coded as 2 instructions as follows:
    // asm.emitMOVSX_Reg_RegInd_Word(T0, SP);
    // asm.emitMOV_RegInd_Reg(SP, T0);
    // Indirection via ESP requires an extra byte for the indirection, so the
    // total code size is 6 bytes. The 3 instruction version below is only 4
    // bytes long and faster on Pentium 4 benchmarks.
    asm.emitPOP_Reg(T0);
    asm.emitMOVSX_Reg_Reg_Word(T0, T0);
    asm.emitPUSH_Reg(T0);
  }

  /*
  * comparision ops
  */

  /**
   * Emit code to implement the lcmp bytecode
   */
  protected final void emit_lcmp() {
    asm.emitPOP_Reg(T0);                // (S0:T0) = (high half value2: low half value2)
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);                // (..:T1) = (.. : low half of value1)
    asm.emitSUB_Reg_Reg(T1, T0);        // T1 = T1 - T0
    asm.emitPOP_Reg(T0);                // (T0:..) = (high half of value1 : ..)
    // NB pop does not alter the carry register
    asm.emitSBB_Reg_Reg(T0, S0);        // T0 = T0 - S0 - CF
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LT);
    asm.emitOR_Reg_Reg(T0, T1);         // T0 = T0 | T1
    VM_ForwardReference fr2 = asm.forwardJcc(VM_Assembler.NE);
    asm.emitPUSH_Imm(0);                // push result on stack
    VM_ForwardReference fr3 = asm.forwardJMP();
    fr2.resolve(asm);
    asm.emitPUSH_Imm(1);                // push result on stack
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPUSH_Imm(-1);                // push result on stack
    fr3.resolve(asm);
    fr4.resolve(asm);
  }

  /**
   * Emit code to implement the fcmpl bytecode
   */
  protected final void emit_fcmpl() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      asm.emitMOVSS_Reg_RegDisp(XMM1, SP, ONE_SLOT);    // XMM1 = value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);            // popping the stack
      asm.emitUCOMISS_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);                  // Setup value2 into FP1,
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);       // value1 into FP0
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);            // popping the stack
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                // compare and pop FPU *1
    }
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LLT);
    VM_ForwardReference fr2 = asm.forwardJcc(VM_Assembler.LGT);
    asm.emitPUSH_Imm(0);                                // push result on stack
    VM_ForwardReference fr3 = asm.forwardJMP();
    fr2.resolve(asm);
    asm.emitPUSH_Imm(1);                                // push result on stack
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPUSH_Imm(-1);                               // push result on stack
    fr3.resolve(asm);
    fr4.resolve(asm);
    if (!SSE2_BASE) {
      asm.emitFSTP_Reg_Reg(FP0, FP0);                   // pop FPU*1
    }
  }

  /**
   * Emit code to implement the fcmpg bytecode
   */
  protected final void emit_fcmpg() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      asm.emitMOVSS_Reg_RegDisp(XMM1, SP, ONE_SLOT);    // XMM1 = value1
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);            // popping the stack
      asm.emitUCOMISS_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);                  // Setup value2 into FP1,
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);       // value1 into FP0
      asm.emitADD_Reg_Imm(SP, 2 * WORDSIZE);            // popping the stack
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                // compare and pop FPU *1
    }
    // TODO: It's bad to have 2 conditional jumps within 16bytes of each other
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LGT); // if > goto push 1
    VM_ForwardReference fr3 = asm.forwardJcc(VM_Assembler.NE);  // if < goto push -1
    VM_ForwardReference fr2 = asm.forwardJcc(VM_Assembler.PE);  // if unordered goto push 1
    asm.emitPUSH_Imm(0);                                // push result of 0 on stack
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr1.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Imm(1);                                // push result of 1 on stack
    VM_ForwardReference fr5 = asm.forwardJMP();
    fr3.resolve(asm);
    asm.emitPUSH_Imm(-1);                               // push result of -1 on stack
    fr4.resolve(asm);
    fr5.resolve(asm);
    if (!SSE2_BASE) {
      asm.emitFSTP_Reg_Reg(FP0, FP0);                   // pop FPU*1
    }
  }

  /**
   * Emit code to implement the dcmpl bytecode
   */
  protected final void emit_dcmpl() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      asm.emitMOVSD_Reg_RegDisp(XMM1, SP, TWO_SLOTS);   // XMM1 = value1
      asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);            // popping the stack
      asm.emitUCOMISD_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // Setup value2 into FP1,
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // value1 into FP0
      asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);            // popping the stack
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                // compare and pop FPU *1
    }
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LLT);
    VM_ForwardReference fr2 = asm.forwardJcc(VM_Assembler.LGT);
    asm.emitPUSH_Imm(0);                                // push result on stack
    VM_ForwardReference fr3 = asm.forwardJMP();
    fr2.resolve(asm);
    asm.emitPUSH_Imm(1);                                // push result on stack
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPUSH_Imm(-1);                               // push result on stack
    fr3.resolve(asm);
    fr4.resolve(asm);
    if (!SSE2_BASE) {
      asm.emitFSTP_Reg_Reg(FP0, FP0);                   // pop FPU*1
    }
  }

  /**
   * Emit code to implement the dcmpg bytecode
   */
  protected final void emit_dcmpg() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      asm.emitMOVSD_Reg_RegDisp(XMM1, SP, TWO_SLOTS);   // XMM1 = value1
      asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);            // popping the stack
      asm.emitUCOMISD_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // Setup value2 into FP1,
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // value1 into FP0
      asm.emitADD_Reg_Imm(SP, 4 * WORDSIZE);            // popping the stack
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                // compare and pop FPU *1
    }
    // TODO: It's bad to have 2 conditional jumps within 16bytes of each other
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.LGT); // if > goto push1
    VM_ForwardReference fr3 = asm.forwardJcc(VM_Assembler.NE);  // if < goto push -1
    VM_ForwardReference fr2 = asm.forwardJcc(VM_Assembler.PE);  // if unordered goto push 1
    asm.emitPUSH_Imm(0);                                // push result of 0 on stack
    VM_ForwardReference fr4 = asm.forwardJMP();
    fr1.resolve(asm);
    fr2.resolve(asm);
    asm.emitPUSH_Imm(1);                                // push result of 1 on stack
    VM_ForwardReference fr5 = asm.forwardJMP();
    fr3.resolve(asm);
    asm.emitPUSH_Imm(-1);                               // push result of -1 on stack
    fr4.resolve(asm);
    fr5.resolve(asm);
    if (!SSE2_BASE) {
      asm.emitFSTP_Reg_Reg(FP0, FP0);                   // pop FPU*1
    }
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
    genCondBranch(VM_Assembler.EQ, bTarget);
  }

  /**
   * Emit code to implement the ifne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifne(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(VM_Assembler.NE, bTarget);
  }

  /**
   * Emit code to implement the iflt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_iflt(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(VM_Assembler.LT, bTarget);
  }

  /**
   * Emit code to implement the ifge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifge(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(VM_Assembler.GE, bTarget);
  }

  /**
   * Emit code to implement the ifgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifgt(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(VM_Assembler.GT, bTarget);
  }

  /**
   * Emit code to implement the ifle bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifle(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Imm(T0, 0);
    genCondBranch(VM_Assembler.LE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpeq(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.EQ, bTarget);
  }

  /**
   * Emit code to implement the if_icmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpne(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.NE, bTarget);
  }

  /**
   * Emit code to implement the if_icmplt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmplt(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.LT, bTarget);
  }

  /**
   * Emit code to implement the if_icmpge bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpge(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.GE, bTarget);
  }

  /**
   * Emit code to implement the if_icmpgt bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmpgt(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.GT, bTarget);
  }

  /**
   * Emit code to implement the if_icmple bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_icmple(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.LE, bTarget);
  }

  /**
   * Emit code to implement the if_acmpeq bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpeq(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.EQ, bTarget);
  }

  /**
   * Emit code to implement the if_acmpne bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_if_acmpne(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    genCondBranch(VM_Assembler.NE, bTarget);
  }

  /**
   * Emit code to implement the ifnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(VM_Assembler.EQ, bTarget);
  }

  /**
   * Emit code to implement the ifnonnull bytecode
   * @param bTarget target bytecode of the branch
   */
  protected final void emit_ifnonnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(VM_Assembler.NE, bTarget);
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
    Offset offset = localOffset(index);
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
    int n = high - low + 1;                        // n = number of normal cases (0..n-1)
    asm.emitPOP_Reg(T0);                          // T0 is index of desired case
    asm.emitSUB_Reg_Imm(T0, low);                     // relativize T0
    asm.emitCMP_Reg_Imm(T0, n);                       // 0 <= relative index < n

    if (!VM.runningTool && ((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
      int firstCounter = edgeCounterIdx;
      edgeCounterIdx += (n + 1);

      // Jump around code for default case
      VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.LLT);
      incEdgeCounter(S0, firstCounter + n);
      asm.emitJMP_ImmOrLabel(mTarget, bTarget);
      fr.resolve(asm);

      // Increment counter for the appropriate case
      incEdgeCounterIdx(S0, T0, firstCounter);
    } else {
      asm.emitJCC_Cond_ImmOrLabel(VM_Assembler.LGE, mTarget, bTarget);   // if not, goto default case
    }
    asm.emitCALL_Imm(asm.getMachineCodeIndex() + 5 + (n << LG_WORDSIZE));
    // jump around table, pushing address of 0th delta
    for (int i = 0; i < n; i++) {                  // create table of deltas
      int offset = bcodes.getTableSwitchOffset(i);
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      // delta i: difference between address of case i and of delta 0
      asm.emitOFFSET_Imm_ImmOrLabel(i, mTarget, bTarget);
    }
    bcodes.skipTableSwitchOffsets(n);
    asm.emitPOP_Reg(S0);                          // S0 = address of 0th delta
    asm.emitADD_Reg_RegIdx(S0, S0, T0, VM_Assembler.WORD, NO_SLOT);     // S0 += [S0 + T0<<2]
    asm.emitPUSH_Reg(S0);                          // push computed case address
    asm.emitRET();                            // goto case
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
    for (int i = 0; i < npairs; i++) {
      int match = bcodes.getLookupSwitchValue(i);
      asm.emitCMP_Reg_Imm(T0, match);
      int offset = bcodes.getLookupSwitchOffset(i);
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      if (!VM.runningTool && ((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
        // Flip conditions so we can jump over the increment of the taken counter.
        VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.NE);
        incEdgeCounter(S0, edgeCounterIdx++);
        asm.emitJMP_ImmOrLabel(mTarget, bTarget);
        fr.resolve(asm);
      } else {
        asm.emitJCC_Cond_ImmOrLabel(VM_Assembler.EQ, mTarget, bTarget);
      }
    }
    bcodes.skipLookupSwitchPairs(npairs);
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && ((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
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
    if (SSE2_FULL) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);
    }
    asm.emitADD_Reg_Imm(SP, WORDSIZE); // pop the stack
    genEpilogue(4);
  }

  /**
   * Emit code to implement the dreturn bytecode
   */
  protected final void emit_dreturn() {
    if (method.isSynchronized()) genMonitorExit();
    if (SSE2_FULL) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    }
    asm.emitADD_Reg_Imm(SP, WORDSIZE << 1); // pop the stack
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
    if (fieldRef.getSize() <= BYTES_IN_INT) {
      asm.emitPUSH_RegIdx(JTOC, T0, VM_Assembler.BYTE, NO_SLOT);        // get static field
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPUSH_RegIdx(JTOC, T0, VM_Assembler.BYTE, ONE_SLOT); // get high part
      asm.emitPUSH_RegIdx(JTOC, T0, VM_Assembler.BYTE, NO_SLOT);        // get low part
    }
  }

  /**
   * Emit code to implement a getstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getstatic(VM_FieldReference fieldRef) {
    Offset fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitPUSH_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPUSH_RegDisp(JTOC, fieldOffset.plus(WORDSIZE)); // get high part
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
    if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitPOP_RegIdx(JTOC, T0, VM_Assembler.BYTE, NO_SLOT);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPOP_RegIdx(JTOC, T0, VM_Assembler.BYTE, NO_SLOT);        // store low part
      asm.emitPOP_RegIdx(JTOC, T0, VM_Assembler.BYTE, ONE_SLOT); // store high part
    }
  }

  /**
   * Emit code to implement a putstatic
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putstatic(VM_FieldReference fieldRef) {
    Offset fieldOffset = fieldRef.peekResolvedField().getOffset();
// putstatic barrier currently unsupported
//     if (MM_Interface.NEEDS_WRITE_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
//       VM_Barriers.compilePutstaticBarrierImm(asm, fieldOffset);
//     }
    if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      asm.emitPOP_RegDisp(JTOC, fieldOffset);
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      asm.emitPOP_RegDisp(JTOC, fieldOffset);          // store low part
      asm.emitPOP_RegDisp(JTOC, fieldOffset.plus(WORDSIZE)); // store high part
    }
  }

  /**
   * Emit code to implement a dynamically linked getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_getfield(VM_FieldReference fieldRef) {
    VM_TypeReference fieldType = fieldRef.getFieldContentsType();
    emitDynamicLinkingSequence(T0, fieldRef, true);
    if (fieldType.isReferenceType()) {
      // 32bit reference load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOV_Reg_RegIdx(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else if (fieldType.isBooleanType()) {
      // 8bit unsigned load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVZX_Reg_RegIdx_Byte(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else if (fieldType.isByteType()) {
      // 8bit signed load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else if (fieldType.isShortType()) {
      // 16bit signed load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else if (fieldType.isCharType()) {
      // 16bit unsigned load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else if (fieldType.isIntType() || fieldType.isFloatType() || fieldType.isWordType()) {
      // 32bit load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOV_Reg_RegIdx(T1, S0, T0, VM_Assembler.BYTE, NO_SLOT); // T1 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T1); // replace reference with value on stack
    } else {
      // 64bit load
      if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
      // NB this is a 64bit copy from memory to the stack so implement
      // as a slightly optimized Intel memory copy using the FPU
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT);  // S0 is object reference
      asm.emitSUB_Reg_Imm(SP, WORDSIZE); // adjust stack down one word to hold 64bit value
      if (SSE2_BASE) {
        asm.emitMOVQ_Reg_RegIdx(XMM0, S0, T0, VM_Assembler.BYTE, NO_SLOT); // XMM0 is field value
        asm.emitMOVQ_RegInd_Reg(SP, XMM0); // replace reference with value on stack
      } else {
        asm.emitFLD_Reg_RegIdx_Quad(FP0, S0, T0, VM_Assembler.BYTE, NO_SLOT); // FP0 is field value
        asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // replace reference with value on stack
      }
    }
  }

  /**
   * Emit code to implement a getfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_getfield(VM_FieldReference fieldRef) {
    VM_TypeReference fieldType = fieldRef.getFieldContentsType();
    Offset fieldOffset = fieldRef.peekResolvedField().getOffset();
    if (fieldType.isReferenceType()) {
      // 32bit reference load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOV_Reg_RegDisp(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else if (fieldType.isBooleanType()) {
      // 8bit unsigned load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVZX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else if (fieldType.isByteType()) {
      // 8bit signed load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVSX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else if (fieldType.isShortType()) {
      // 16bit signed load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVSX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else if (fieldType.isCharType()) {
      // 16bit unsigned load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOVZX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else if (fieldType.isIntType() || fieldType.isFloatType() || fieldType.isWordType()) {
      // 32bit load
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT); // S0 is object reference
      asm.emitMOV_Reg_RegDisp(T0, S0, fieldOffset); // T0 is field value
      asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0); // replace reference with value on stack
    } else {
      // 64bit load
      if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
      // NB this is a 64bit copy from memory to the stack so implement
      // as a slightly optimized Intel memory copy using the FPU
      asm.emitMOV_Reg_RegDisp(S0, SP, NO_SLOT);  // S0 is object reference
      asm.emitSUB_Reg_Imm(SP, WORDSIZE); // adjust stack down one word to hold 64bit value
      if (SSE2_BASE) {
        asm.emitMOVQ_Reg_RegDisp(XMM0, S0, fieldOffset); // XMM0 is field value
        asm.emitMOVQ_RegInd_Reg(SP, XMM0); // replace reference with value on stack
      } else {
        asm.emitFLD_Reg_RegDisp_Quad(FP0, S0, fieldOffset); // FP0 is field value
        asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // replace reference with value on stack
      }
    }
  }

  /**
   * Emit code to implement a dynamically linked putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_unresolved_putfield(VM_FieldReference fieldRef) {
    VM_TypeReference fieldType = fieldRef.getFieldContentsType();
    emitDynamicLinkingSequence(T0, fieldRef, true);
    if (fieldType.isReferenceType()) {
      // 32bit reference store
      if (MM_Constants.NEEDS_WRITE_BARRIER) {
        VM_Barriers.compilePutfieldBarrier(asm, T0, fieldRef.getId());
        emitDynamicLinkingSequence(T0, fieldRef, false);
        asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      } else {
        asm.emitMOV_Reg_RegDisp(T1, SP, NO_SLOT);  // T1 is the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
        asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
        asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.BYTE, NO_SLOT, T1); // [S0+T0] <- T1
      }
    } else if (fieldType.isBooleanType() || fieldType.isByteType()) {
      // 8bit store
      asm.emitMOV_Reg_RegDisp(T1, SP, NO_SLOT);  // T1 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      asm.emitMOV_RegIdx_Reg_Byte(S0, T0, VM_Assembler.BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else if (fieldType.isShortType() || fieldType.isCharType()) {
      // 16bit store
      asm.emitMOV_Reg_RegDisp(T1, SP, NO_SLOT);  // T1 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      asm.emitMOV_RegIdx_Reg_Word(S0, T0, VM_Assembler.BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else if (fieldType.isIntType() || fieldType.isFloatType() || fieldType.isWordType()) {
      // 32bit store
      asm.emitMOV_Reg_RegDisp(T1, SP, NO_SLOT);  // T1 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      asm.emitMOV_RegIdx_Reg(S0, T0, VM_Assembler.BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else {
      // 64bit store
      if (VM.VerifyAssertions) VM._assert(fieldType.isLongType() || fieldType.isDoubleType());
      // NB this is a 64bit copy from the stack to memory so implement
      // as a slightly optimized Intel memory copy using the FPU
      asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is the object reference
      if (SSE2_BASE) {
        asm.emitMOVQ_Reg_RegInd(XMM0, SP); // XMM0 is the value to be stored
        asm.emitMOVQ_RegIdx_Reg(S0, T0, VM_Assembler.BYTE, NO_SLOT, XMM0); // [S0+T0] <- XMM0
      } else {
        asm.emitFLD_Reg_RegInd_Quad(FP0, SP); // FP0 is the value to be stored
        asm.emitFSTP_RegIdx_Reg_Quad(S0, T0, VM_Assembler.BYTE, NO_SLOT, FP0); // [S0+T0] <- FP0
      }
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 3); // complete popping the values and reference
    }
  }

  /**
   * Emit code to implement a putfield
   * @param fieldRef the referenced field
   */
  protected final void emit_resolved_putfield(VM_FieldReference fieldRef) {
    VM_TypeReference fieldType = fieldRef.getFieldContentsType();
    Offset fieldOffset = fieldRef.peekResolvedField().getOffset();
    VM_Barriers.compileModifyCheck(asm, 4);
    if (fieldType.isReferenceType()) {
      // 32bit reference store
      if (MM_Constants.NEEDS_WRITE_BARRIER) {
        VM_Barriers.compilePutfieldBarrierImm(asm, fieldOffset, fieldRef.getId());
        asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      } else {
        asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);  // T0 is the value to be stored
        asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
        asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
        // [S0+fieldOffset] <- T0
        asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);
      }
    } else if (fieldType.isBooleanType() || fieldType.isByteType()) {
      // 8bit store
      asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);  // T0 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg_Byte(S0, fieldOffset, T0);
    } else if (fieldType.isShortType() || fieldType.isCharType()) {
      // 16bit store
      asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);  // T0 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg_Word(S0, fieldOffset, T0);
    } else if (fieldType.isIntType() || fieldType.isFloatType() || fieldType.isWordType()) {
      // 32bit store
      asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);  // T0 is the value to be stored
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT); // S0 is the object reference
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 2); // complete popping the value and reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);
    } else {
      // 64bit store
      if (VM.VerifyAssertions) {
        VM._assert(fieldType.isLongType() || fieldType.isDoubleType(), "What type is this?" + fieldType);
      }
      // NB this is a 64bit copy from the stack to memory so implement
      // as a slightly optimized Intel memory copy using the FPU
      asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is the object reference
      if (SSE2_BASE) {
        asm.emitMOVQ_Reg_RegInd(XMM0, SP); // XMM0 is the value to be stored
        asm.emitMOVQ_RegDisp_Reg(S0, fieldOffset, XMM0); // [S0+fieldOffset] <- XMM0
      } else {
        asm.emitFLD_Reg_RegInd_Quad(FP0, SP); // FP0 is the value to be stored
        asm.emitFSTP_RegDisp_Reg_Quad(S0, fieldOffset, FP0);
      }
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 3); // complete popping the values and reference
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
    Offset objectOffset =
        Offset.fromIntZeroExtend(methodRefparameterWords << 2).minus(4);           // object offset into stack
    asm.emitMOV_Reg_RegDisp(T1, SP, objectOffset);                  // S0 has "this" parameter
    VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
    asm.emitMOV_Reg_RegIdx(S0, S0, T0, VM_Assembler.BYTE, NO_SLOT);                // S0 has address of virtual method
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
    Offset methodRefOffset = methodRef.peekResolvedMethod().getOffset();
    Offset objectOffset =
        Offset.fromIntZeroExtend(methodRefparameterWords << 2).minus(WORDSIZE); // object offset into stack
    asm.emitMOV_Reg_RegDisp(T1, SP, objectOffset);
    VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegDisp(S0, methodRefOffset);
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement a dynamically linked invokespecial
   * @param methodRef The referenced method
   * @param target    The method to invoke
   */
  protected final void emit_resolved_invokespecial(VM_MethodReference methodRef, VM_Method target) {
    if (target.isObjectInitializer()) {
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(JTOC, target.getOffset());
      genResultRegisterUnload(target.getMemberRef().asMethodReference());
    } else {
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      // invoke via class's tib slot
      Offset methodRefOffset = target.getOffset();
      asm.emitMOV_Reg_RegDisp(S0, JTOC, target.getDeclaringClass().getTibOffset());
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
    asm.emitCALL_RegIdx(JTOC, S0, VM_Assembler.BYTE, NO_SLOT);  // call static method
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement a dynamically linked invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_unresolved_invokestatic(VM_MethodReference methodRef) {
    emitDynamicLinkingSequence(S0, methodRef, true);
    genParameterRegisterLoad(methodRef, false);
    asm.emitCALL_RegIdx(JTOC, S0, VM_Assembler.BYTE, NO_SLOT);
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement invokestatic
   * @param methodRef the referenced method
   */
  protected final void emit_resolved_invokestatic(VM_MethodReference methodRef) {
    Offset methodOffset = methodRef.peekResolvedMethod().getOffset();
    genParameterRegisterLoad(methodRef, false);
    asm.emitCALL_RegDisp(JTOC, methodOffset);
    genResultRegisterUnload(methodRef);
  }

  /**
   * Emit code to implement the invokeinterface bytecode
   * @param methodRef the referenced method
   */
  protected final void emit_invokeinterface(VM_MethodReference methodRef) {
    int count = methodRef.getParameterWords() + 1; // +1 for "this" parameter

    VM_Method resolvedMethod = null;
    resolvedMethod = methodRef.peekInterfaceMethod();

    // (1) Emit dynamic type checking sequence if required to do so inline.
    if (VM.BuildForIMTInterfaceInvocation || (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables)) {
      if (methodRef.isMiranda()) {
        // TODO: It's not entirely clear that we can just assume that
        //       the class actually implements the interface.
        //       However, we don't know what interface we need to be checking
        //       so there doesn't appear to be much else we can do here.
      } else {
        if (resolvedMethod == null) {
          // Can't successfully resolve it at compile time.
          // Call uncommon case typechecking routine to do the right thing when this code actually executes.
          asm.emitMOV_Reg_RegDisp(T1,
                                  SP,
                                  Offset.fromIntZeroExtend((count - 1) << 2));                       // "this" object
          asm.emitPUSH_Imm(methodRef.getId());                                    // dict id of target
          VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
          asm.emitPUSH_Reg(S0);
          genParameterRegisterLoad(2);                                            // pass 2 parameter word
          asm.emitCALL_RegDisp(JTOC,
                               VM_Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
        } else {
          asm.emitMOV_Reg_RegDisp(T0,
                                  JTOC,
                                  resolvedMethod.getDeclaringClass().getTibOffset()); // tib of the interface method
          asm.emitMOV_Reg_RegDisp(T1,
                                  SP,
                                  Offset.fromIntZeroExtend((count - 1) <<
                                                           2));                                 // "this" object
          asm.emitPUSH_RegDisp(T0,
                               Offset.fromIntZeroExtend(TIB_TYPE_INDEX <<
                                                        2));                                    // type of the interface method
          VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
          asm.emitPUSH_Reg(S0);
          genParameterRegisterLoad(2);                                          // pass 2 parameter word
          asm.emitCALL_RegDisp(JTOC,
                               VM_Entrypoints.invokeinterfaceImplementsTestMethod.getOffset());// check that "this" class implements the interface
        }
      }
    }

    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(methodRef);

      // squirrel away signature ID
      VM_ProcessorLocalState.emitMoveImmToField(asm, VM_ArchEntrypoints.hiddenSignatureIdField.getOffset(), sig.getId());
      // T1 = "this" object
      asm.emitMOV_Reg_RegDisp(T1, SP,
          Offset.fromIntZeroExtend((count - 1) << 2));
      VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
      if (VM.BuildForIndirectIMT) {
        // Load the IMT Base into S0
        asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_IMT_TIB_INDEX << 2));
      }
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, sig.getIMTOffset());                                             // the interface call
    } else if (VM.BuildForITableInterfaceInvocation && VM.DirectlyIndexedITables && resolvedMethod != null) {
      VM_Class I = resolvedMethod.getDeclaringClass();
      // T1 = "this" object
      asm.emitMOV_Reg_RegDisp(T1, SP,
          Offset.fromIntZeroExtend((count - 1) << 2));
      VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T1);
      // S0 = iTables
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_ITABLES_TIB_INDEX << 2));
      // S0 = iTable
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(I.getInterfaceId() << 2));
      genParameterRegisterLoad(methodRef, true);
      int idx = VM_InterfaceInvocation.getITableIndex(I, methodRef.getName(), methodRef.getDescriptor());
      asm.emitCALL_RegDisp(S0, Offset.fromIntZeroExtend(idx << 2)); // the interface call
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex =
            VM_InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(),
                                                  methodRef.getName(),
                                                  methodRef.getDescriptor());
      }
      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into
        // method address
        int methodRefId = methodRef.getId();
        // "this" parameter is obj
        asm.emitPUSH_RegDisp(SP, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
        asm.emitPUSH_Imm(methodRefId);             // id of method to call
        genParameterRegisterLoad(2);               // pass 2 parameter words
        // invokeinterface(obj, id) returns address to call
        asm.emitCALL_RegDisp(JTOC,
                             VM_Entrypoints.invokeInterfaceMethod.getOffset());
        asm.emitMOV_Reg_Reg(S0, T0);               // S0 has address of method
        genParameterRegisterLoad(methodRef, true);
        asm.emitCALL_Reg(S0);                     // the interface method (its parameters are on stack)
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into
        // itable address
        // T0 = "this" object
        asm.emitMOV_Reg_RegDisp(T0, SP, Offset.fromIntZeroExtend((count - 1) << 2));
        VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T0);
        asm.emitPUSH_Reg(S0);
        asm.emitPUSH_Imm(resolvedMethod.getDeclaringClass().getInterfaceId()); // interface id
        genParameterRegisterLoad(2);                                  // pass 2 parameter words
        asm.emitCALL_RegDisp(JTOC,
                             VM_Entrypoints.findItableMethod.getOffset()); // findItableOffset(tib, id) returns iTable
        asm.emitMOV_Reg_Reg(S0, T0);                             // S0 has iTable
        genParameterRegisterLoad(methodRef, true);
        // the interface call
        asm.emitCALL_RegDisp(S0, Offset.fromIntZeroExtend(itableIndex << 2));
      }
    }
    genResultRegisterUnload(methodRef);
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
    Offset tibOffset = typeRef.getTibOffset();
    int whichAllocator = MM_Interface.pickAllocator(typeRef, method);
    int align = VM_ObjectModel.getAlignment(typeRef);
    int offset = VM_ObjectModel.getOffsetForAlignment(typeRef);
    int site = MM_Interface.getAllocationSite(true);
    asm.emitPUSH_Imm(instanceSize);
    asm.emitPUSH_RegDisp(JTOC, tibOffset);       // put tib on stack
    asm.emitPUSH_Imm(typeRef.hasFinalizer() ? 1 : 0); // does the class have a finalizer?
    asm.emitPUSH_Imm(whichAllocator);
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    asm.emitPUSH_Imm(site);
    genParameterRegisterLoad(7);                  // pass 7 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.resolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to dynamically link and allocate a scalar object
   * @param typeRef typeReference to dynamically link & instantiate
   */
  protected final void emit_unresolved_new(VM_TypeReference typeRef) {
    int site = MM_Interface.getAllocationSite(true);
    asm.emitPUSH_Imm(typeRef.getId());
    asm.emitPUSH_Imm(site);                 // site
    genParameterRegisterLoad(2);            // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to allocate an array
   * @param array the VM_Array to instantiate
   */
  protected final void emit_resolved_newarray(VM_Array array) {
    int width = array.getLogElementSize();
    Offset tibOffset = array.getTibOffset();
    int headerSize = VM_ObjectModel.computeHeaderSize(array);
    int whichAllocator = MM_Interface.pickAllocator(array, method);
    int site = MM_Interface.getAllocationSite(true);
    int align = VM_ObjectModel.getAlignment(array);
    int offset = VM_ObjectModel.getOffsetForAlignment(array);
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(width);                 // logElementSize
    asm.emitPUSH_Imm(headerSize);            // headerSize
    asm.emitPUSH_RegDisp(JTOC, tibOffset);   // tib
    asm.emitPUSH_Imm(whichAllocator);        // allocator
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    asm.emitPUSH_Imm(site);
    genParameterRegisterLoad(8);             // pass 8 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.resolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to dynamically link and allocate an array
   * @param tRef the type reference to dynamically link & instantiate
   */
  protected final void emit_unresolved_newarray(VM_TypeReference tRef) {
    int site = MM_Interface.getAllocationSite(true);
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(tRef.getId());
    asm.emitPUSH_Imm(site);                 // site
    genParameterRegisterLoad(3);            // pass 3 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to allocate a multi-dimensional array
   * @param typeRef the type reference to instantiate
   * @param dimensions the number of dimensions
   */
  protected final void emit_multianewarray(VM_TypeReference typeRef, int dimensions) {
    // Calculate the offset from FP on entry to newarray:
    //      1 word for each parameter, plus 1 for return address on
    //      stack and 1 for code technique in VM_Linker
    final int PARAMETERS = 4;
    final int OFFSET_WORDS = PARAMETERS + 2;

    // setup parameters for newarrayarray routine
    asm.emitPUSH_Imm(method.getId());           // caller
    asm.emitPUSH_Imm(dimensions);               // dimension of arays
    asm.emitPUSH_Imm(typeRef.getId());          // type of array elements
    asm.emitPUSH_Imm((dimensions + OFFSET_WORDS) << LG_WORDSIZE);  // offset to dimensions from FP on entry to newarray

    genParameterRegisterLoad(PARAMETERS);
    asm.emitCALL_RegDisp(JTOC, VM_ArchEntrypoints.newArrayArrayMethod.getOffset());
    for (int i = 0; i < dimensions; i++) {
      asm.emitPOP_Reg(S0); // clear stack of dimensions (todo use and add immediate to do this)
    }
    asm.emitPUSH_Reg(T0);                       // push array ref on stack
  }

  /**
   * Emit code to implement the arraylength bytecode
   */
  protected final void emit_arraylength() {
    asm.emitMOV_Reg_RegDisp(T0, SP, NO_SLOT);                   // T0 is array reference
    asm.emitMOV_Reg_RegDisp(T0, T0, VM_ObjectModel.getArrayLengthOffset()); // T0 is array length
    asm.emitMOV_RegDisp_Reg(SP, NO_SLOT, T0);                   // replace reference with length on stack
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
    asm.emitPUSH_RegInd(SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(typeRef.getId());               // VM_TypeReference id.
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkcastMethod.getOffset()); // checkcast(obj, type reference id);
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected final void emit_checkcast_resolvedClass(VM_Type type) {
    asm.emitPUSH_RegInd(SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(type.getId());                  // VM_Type id.
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkcastResolvedClassMethod.getOffset()); // checkcast(obj, type id)
  }

  /**
   * Emit code to implement the checkcast bytecode
   * @param type the LHS type
   */
  protected final void emit_checkcast_final(VM_Type type) {
    asm.emitPUSH_RegInd(SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(type.getTibOffset().toInt());           // JTOC index that identifies klass
    genParameterRegisterLoad(2);                     // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.checkcastFinalMethod.getOffset()); // checkcast(obj, TIB's JTOC offset)
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
   * @param type the LHS type
   */
  protected final void emit_instanceof_resolvedClass(VM_Type type) {
    asm.emitPUSH_Imm(type.getId());
    genParameterRegisterLoad(2);          // pass 2 parameter words
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.instanceOfResolvedClassMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  /**
   * Emit code to implement the instanceof bytecode
   * @param type the LHS type
   */
  protected final void emit_instanceof_final(VM_Type type) {
    asm.emitPUSH_Imm(type.getTibOffset().toInt());
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

  private void genPrologue() {
    if (shouldPrint) asm.comment("prologue for " + method);
    if (klass.hasBridgeFromNativeAnnotation()) {
      // replace the normal prologue with a special prolog
      VM_JNICompiler.generateGlueCodeForJNIMethod(asm, method, compiledMethod.getId());
      // set some constants for the code generation of the rest of the method
      // firstLocalOffset is shifted down because more registers are saved
      firstLocalOffset = STACKFRAME_BODY_OFFSET - (VM_JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
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
      asm.emitPUSH_RegDisp(PR, VM_ArchEntrypoints.framePointerField.getOffset());        // store caller's frame pointer
      VM_ProcessorLocalState.emitMoveRegToField(asm,
                                                VM_ArchEntrypoints.framePointerField.getOffset(),
                                                SP); // establish new frame
      /*
       * NOTE: until the end of the prologue SP holds the framepointer.
       */
      asm.emitMOV_RegDisp_Imm(SP,
                              Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET),
                              compiledMethod.getId()); // 3rd word of header

      /*
      * save registers
      */
      asm.emitMOV_RegDisp_Reg(SP, JTOC_SAVE_OFFSET, JTOC);          // save nonvolatile JTOC register
      asm.emitMOV_RegDisp_Reg(SP, EBX_SAVE_OFFSET, EBX);            // save nonvolatile EBX register

      // establish the JTOC register
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, JTOC, VM_ArchEntrypoints.jtocField.getOffset());

      if (!VM.runningTool && ((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
        // use (nonvolatile) EBX to hold base of this methods counter array
        asm.emitMOV_Reg_RegDisp(EBX, JTOC, VM_Entrypoints.edgeCountersField.getOffset());
        asm.emitMOV_Reg_RegDisp(EBX, EBX, getEdgeCounterOffset());
      }

      int savedRegistersSize = SAVED_GPRS << LG_WORDSIZE;       // default
      /* handle "dynamic brige" methods:
       * save all registers except FP, SP, PR, S0 (scratch), and
       * JTOC and EBX saved above.
       */
      // TODO: (SJF): When I try to reclaim ESI, I may have to save it here?
      if (klass.hasDynamicBridgeAnnotation()) {
        savedRegistersSize += 2 << LG_WORDSIZE;
        asm.emitMOV_RegDisp_Reg(SP, T0_SAVE_OFFSET, T0);
        asm.emitMOV_RegDisp_Reg(SP, T1_SAVE_OFFSET, T1);
        if (SSE2_FULL) {
          // TODO: Store SSE2 Control word?
          asm.emitMOVQ_RegDisp_Reg(SP, XMM_SAVE_OFFSET.plus( 0), XMM0);
          asm.emitMOVQ_RegDisp_Reg(SP, XMM_SAVE_OFFSET.plus( 8), XMM1);
          asm.emitMOVQ_RegDisp_Reg(SP, XMM_SAVE_OFFSET.plus(16), XMM2);
          asm.emitMOVQ_RegDisp_Reg(SP, XMM_SAVE_OFFSET.plus(24), XMM3);
          savedRegistersSize += XMM_STATE_SIZE;
        } else {
          asm.emitFNSAVE_RegDisp(SP, FPU_SAVE_OFFSET);
          savedRegistersSize += FPU_STATE_SIZE;
        }
      }

      // copy registers to callee's stackframe
      firstLocalOffset = STACKFRAME_BODY_OFFSET - savedRegistersSize;
      Offset firstParameterOffset = Offset.fromIntSignExtend((parameterWords << LG_WORDSIZE) + WORDSIZE);
      genParameterCopy(firstParameterOffset, Offset.fromIntSignExtend(firstLocalOffset));

      int emptyStackOffset = firstLocalOffset - (method.getLocalWords() << LG_WORDSIZE) + WORDSIZE;
      asm.emitADD_Reg_Imm(SP, emptyStackOffset);               // set aside room for non parameter locals

      /* defer generating code which may cause GC until
       * locals were initialized. see emit_deferred_prologue
       */
      if (method.isForOsrSpecialization()) {
        return;
      }

      /*
       * generate stacklimit check
       */
      if (isInterruptible) {
        // S0<-limit
        VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, VM_Entrypoints.activeThreadStackLimitField.getOffset());

        asm.emitSUB_Reg_Reg(S0, SP);                                           // space left
        asm.emitADD_Reg_Imm(S0, method.getOperandWords() << LG_WORDSIZE);      // space left after this expression stack
        asm.emitBranchLikelyNextInstruction();
        VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.LT);        // Jmp around trap if OK
        asm.emitINT_Imm(VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE);     // trap
        fr.resolve(asm);
      } else {
        // TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
      }

      if (method.isSynchronized()) genMonitorEnter();

      genThreadSwitchTest(VM_Thread.PROLOGUE);
    }
  }

  protected final void emit_deferred_prologue() {

    if (VM.VerifyAssertions) VM._assert(method.isForOsrSpecialization());

    if (isInterruptible) {
      // S0<-limit
      VM_ProcessorLocalState.emitMoveFieldToReg(asm, S0, VM_Entrypoints.activeThreadStackLimitField.getOffset());
      asm.emitSUB_Reg_Reg(S0, SP);                                       // spa
      asm.emitADD_Reg_Imm(S0, method.getOperandWords() << LG_WORDSIZE);  // spa
      asm.emitBranchLikelyNextInstruction();
      VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.LT);    // Jmp around trap if
      asm.emitINT_Imm(VM_Runtime.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE); // tra
      fr.resolve(asm);
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow
    }

    /* never do monitor enter for synced method since the specialized
     * code starts after original monitor enter.
     */

    genThreadSwitchTest(VM_Thread.PROLOGUE);
  }

  private void genEpilogue(int bytesPopped) {
    if (klass.hasBridgeFromNativeAnnotation()) {
      // pop locals and parameters, get to saved GPR's
      asm.emitADD_Reg_Imm(SP, (this.method.getLocalWords() << LG_WORDSIZE));
      VM_JNICompiler.generateEpilogForJNIMethod(asm, this.method);
    } else if (klass.hasDynamicBridgeAnnotation()) {
      // we never return from a DynamicBridge frame
      asm.emitINT_Imm(0xFF);
    } else {
      // normal method
      asm.emitADD_Reg_Imm(SP, fp2spOffset(NO_SLOT).toInt() - bytesPopped);     // SP becomes frame pointer
      asm.emitMOV_Reg_RegDisp(JTOC, SP, JTOC_SAVE_OFFSET);           // restore nonvolatile JTOC register
      asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);             // restore nonvolatile EBX register
      asm.emitPOP_RegDisp(PR, VM_ArchEntrypoints.framePointerField.getOffset()); // discard frame
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);    // return to caller- pop parameters from stack
    }
  }

  private void genMonitorEnter() {
    if (method.isStatic()) {
      Offset klassOffset = Offset.fromIntSignExtend(VM_Statics.findOrCreateObjectLiteral(klass.getClassForType()));
      // push java.lang.Class object for klass
      asm.emitPUSH_RegDisp(JTOC, klassOffset);
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));                           // push "this" object
    }
    genParameterRegisterLoad(1);                                   // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.lockMethod.getOffset());
    lockOffset = asm.getMachineCodeIndex();                       // after this instruction, the method has the monitor
  }

  private void genMonitorExit() {
    if (method.isStatic()) {
      asm.emitMOV_Reg_RegDisp(T0, JTOC, klass.getTibOffset());                   // T0 = tib for klass
      asm.emitMOV_Reg_RegInd(T0, T0);                             // T0 = VM_Class for klass
      asm.emitPUSH_RegDisp(T0, VM_Entrypoints.classForTypeField.getOffset()); // push java.lang.Class object for klass
    } else {
      asm.emitPUSH_RegDisp(ESP, localOffset(0));                    // push "this" object
    }
    genParameterRegisterLoad(1); // pass 1 parameter
    asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.unlockMethod.getOffset());
  }

  /**
   * Generate an array bounds check trapping if the array bound check fails,
   * otherwise falling through.
   * @param asm the assembler to generate into
   * @param indexReg the register containing the index
   * @param arrayRefReg the register containing the array reference
   */
  private static void genBoundsCheck(VM_Assembler asm, byte indexReg, byte arrayRefReg) {
    // compare index to array length
    asm.emitCMP_RegDisp_Reg(arrayRefReg,
                            VM_ObjectModel.getArrayLengthOffset(),
                            indexReg);
    // Jmp around trap if index is OK
    asm.emitBranchLikelyNextInstruction();
    VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.LGT);
    // "pass" index param to C trap handler
    VM_ProcessorLocalState.emitMoveRegToField(asm,
        VM_ArchEntrypoints.arrayIndexTrapParamField.getOffset(), indexReg);
    // trap
    asm.emitINT_Imm(VM_Runtime.TRAP_ARRAY_BOUNDS + RVM_TRAP_BASE);
    fr.resolve(asm);
  }

  /**
   * Emit a conditional branch on the given condition and bytecode target.
   * The caller has just emitted the instruction sequence to set the condition codes.
   */
  private void genCondBranch(byte cond, int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && ((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
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

  private void incEdgeCounter(byte scratch, int counterIdx) {
    if (VM.VerifyAssertions) VM._assert(((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray());
    asm.emitMOV_Reg_RegDisp(scratch, EBX, Offset.fromIntZeroExtend(counterIdx << 2));
    asm.emitADD_Reg_Imm(scratch, 1);
    // Don't write back result if it would make the counter negative (ie
    // saturate at 0x7FFFFFFF)
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.S);
    asm.emitMOV_RegDisp_Reg(EBX, Offset.fromIntSignExtend(counterIdx << 2), scratch);
    fr1.resolve(asm);
  }

  private void incEdgeCounterIdx(byte scratch, byte idx, int counterIdx) {
    if (VM.VerifyAssertions) VM._assert(((VM_BaselineCompiledMethod) compiledMethod).hasCounterArray());
    asm.emitMOV_Reg_RegIdx(scratch, EBX, idx, VM_Assembler.WORD, Offset.fromIntZeroExtend(counterIdx << 2));
    asm.emitADD_Reg_Imm(scratch, 1);
    // Don't write back result if it would make the counter negative (ie
    // saturate at 0x7FFFFFFF)
    VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.S);
    asm.emitMOV_RegIdx_Reg(EBX, idx, VM_Assembler.WORD, Offset.fromIntSignExtend(counterIdx << 2), scratch);
    fr1.resolve(asm);
  }

  /**
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a "helper" method call.
   * Assumption: no floating-point parameters.
   * @param params number of parameter words (including "this" if any).
   */
  private void genParameterRegisterLoad(int params) {
    if (VM.VerifyAssertions) VM._assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T0, SP, Offset.fromIntZeroExtend((params - 1) << LG_WORDSIZE));
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      asm.emitMOV_Reg_RegDisp(T1, SP, Offset.fromIntZeroExtend((params - 2) << LG_WORDSIZE));
    }
  }

  /**
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are layed out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of an explicit method call.
   * @param method is the method to be called.
   * @param hasThisParam is the method virtual?
   */
  private void genParameterRegisterLoad(VM_MethodReference method, boolean hasThisParam) {
    int max = NUM_PARAMETER_GPRS + NUM_PARAMETER_FPRS;
    if (max == 0) return; // quit looking when all registers are full
    int gpr = 0;  // number of general purpose registers filled
    int fpr = 0;  // number of floating point  registers filled
    byte T = T0; // next GPR to get a parameter
    int params = method.getParameterWords() + (hasThisParam ? 1 : 0);
    Offset offset = Offset.fromIntZeroExtend((params - 1) << LG_WORDSIZE); // stack offset of first parameter word
    if (hasThisParam) {
      if (gpr < NUM_PARAMETER_GPRS) {
        asm.emitMOV_Reg_RegDisp(T, SP, offset);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
        max--;
      }
      offset = offset.minus(WORDSIZE);
    }
    for (VM_TypeReference type : method.getParameterTypes()) {
      if (max == 0) return; // quit looking when all registers are full
      VM_TypeReference t = type;
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_Reg_RegDisp(T, SP, offset); // lo register := hi mem (== hi order word)
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
          max--;
          if (gpr < NUM_PARAMETER_GPRS) {
            asm.emitMOV_Reg_RegDisp(T, SP, offset.minus(WORDSIZE));  // hi register := lo mem (== lo order word)
            gpr++;
            max--;
          }
        }
        offset = offset.minus(2 * WORDSIZE);
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          if (SSE2_FULL) {
            asm.emitMOVSS_Reg_RegDisp((byte)fpr, SP, offset);
          } else {
            asm.emitFLD_Reg_RegDisp(FP0, SP, offset);
          }
          fpr++;
          max--;
        }
        offset = offset.minus(WORDSIZE);
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          if (SSE2_FULL) {
            asm.emitMOVSD_Reg_RegDisp((byte)fpr, SP, offset.minus(WORDSIZE));
          } else {
            asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, offset.minus(WORDSIZE));
          }
          fpr++;
          max--;
        }
        offset = offset.minus(2 * WORDSIZE);
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_Reg_RegDisp(T, SP, offset);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
          max--;
        }
        offset = offset.minus(WORDSIZE);
      }
    }
    if (VM.VerifyAssertions) VM._assert(offset.EQ(Offset.fromIntSignExtend(-WORDSIZE)));
  }

  /**
   * Store parameters into local space of the callee's stackframe.
   * Taken: srcOffset - offset from frame pointer of first parameter in caller's stackframe.
   *        dstOffset - offset from frame pointer of first local in callee's stackframe
   * Assumption: although some parameters may be passed in registers,
   * space for all parameters is layed out in order on the caller's stackframe.
   */
  private void genParameterCopy(Offset srcOffset, Offset dstOffset) {
    int gpr = 0;  // number of general purpose registers unloaded
    int fpr = 0;  // number of floating point registers unloaded
    byte T = T0; // next GPR to get a parameter
    if (!method.isStatic()) { // handle "this" parameter
      if (gpr < NUM_PARAMETER_GPRS) {
        asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
      } else { // no parameters passed in registers
        asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
        asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
      }
      srcOffset = srcOffset.minus(WORDSIZE);
      dstOffset = dstOffset.minus(WORDSIZE);
    }
    //KV: todo: This seems not to work
    //OffsetArray fprOffset = OffsetArray.create(NUM_PARAMETER_FPRS); // to handle floating point parameters in registers
    int[] fprOffset = new int[NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    boolean[] is32bit = new boolean[NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    for (VM_TypeReference t : method.getParameterTypes()) {
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);    // hi mem := lo register (== hi order word)
          T =
              T1;                                       // at most 2 parameters can be passed in general purpose registers
          gpr++;
          srcOffset = srcOffset.minus(WORDSIZE);
          dstOffset = dstOffset.minus(WORDSIZE);
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
          srcOffset = srcOffset.minus(WORDSIZE);
          dstOffset = dstOffset.minus(WORDSIZE);
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset = srcOffset.minus(WORDSIZE);
        dstOffset = dstOffset.minus(WORDSIZE);
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          //fprOffset.set(fpr, dstOffset);
          if (SSE2_FULL) {
            asm.emitMOVSS_RegDisp_Reg(SP, dstOffset, (byte)fpr);
          } else {
            fprOffset[fpr] = dstOffset.toInt();
            is32bit[fpr] = true;
          }
          fpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset = srcOffset.minus(WORDSIZE);
        dstOffset = dstOffset.minus(WORDSIZE);
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          srcOffset = srcOffset.minus(WORDSIZE);
          dstOffset = dstOffset.minus(WORDSIZE);
          //fprOffset.set(fpr,  dstOffset);
          if (SSE2_FULL) {
            asm.emitMOVSD_RegDisp_Reg(SP, dstOffset, (byte)fpr);
          } else {
            fprOffset[fpr] = dstOffset.toInt();
            is32bit[fpr] = false;
          }
          fpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // hi mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
          srcOffset = srcOffset.minus(WORDSIZE);
          dstOffset = dstOffset.minus(WORDSIZE);
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);   // lo mem from caller's stackframe
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset = srcOffset.minus(WORDSIZE);
        dstOffset = dstOffset.minus(WORDSIZE);
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, T);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
        } else {
          asm.emitMOV_Reg_RegDisp(S0, SP, srcOffset);
          asm.emitMOV_RegDisp_Reg(SP, dstOffset, S0);
        }
        srcOffset = srcOffset.minus(WORDSIZE);
        dstOffset = dstOffset.minus(WORDSIZE);
      }
    }
    if (!SSE2_FULL) {
      for (int i = fpr - 1; 0 <= i; i--) { // unload the floating point register stack (backwards)
        if (is32bit[i]) {
          //asm.emitFSTP_RegDisp_Reg(SP, fprOffset.get(i), FP0);
          asm.emitFSTP_RegDisp_Reg(SP, Offset.fromIntSignExtend(fprOffset[i]), FP0);
        } else {
          //asm.emitFSTP_RegDisp_Reg_Quad(SP, fprOffset.get(i), FP0);
          asm.emitFSTP_RegDisp_Reg_Quad(SP, Offset.fromIntSignExtend(fprOffset[i]), FP0);
        }
      }
    }
  }

  /**
   * Push return value of method from register to operand stack.
   */
  private void genResultRegisterUnload(VM_MethodReference m) {
    VM_TypeReference t = m.getReturnType();

    if (t.isVoidType()) return;
    if (t.isLongType()) {
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
    } else if (t.isFloatType()) {
      asm.emitSUB_Reg_Imm(SP, 4);
      if (SSE2_FULL) {
        asm.emitMOVSS_RegInd_Reg(SP, XMM0);
      } else {
        asm.emitFSTP_RegInd_Reg(SP, FP0);
      }
    } else if (t.isDoubleType()) {
      asm.emitSUB_Reg_Imm(SP, 8);
      if (SSE2_FULL) {
        asm.emitMOVSD_RegInd_Reg(SP, XMM0);
      } else {
        asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      }
    } else { // t is object, int, short, char, byte, or boolean
      asm.emitPUSH_Reg(T0);
    }
  }

  /**
   * @param whereFrom is this thread switch from a PROLOGUE, BACKEDGE, or EPILOGUE?
   */
  private void genThreadSwitchTest(int whereFrom) {
    if (!isInterruptible) {
      return;
    }

    // thread switch requested ??
    VM_ProcessorLocalState.emitCompareFieldWithImm(asm, VM_Entrypoints.takeYieldpointField.getOffset(), 0);
    VM_ForwardReference fr1;
    if (whereFrom == VM_Thread.PROLOGUE) {
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(VM_Assembler.EQ);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromPrologueMethod.getOffset());
    } else if (whereFrom == VM_Thread.BACKEDGE) {
      // Take yieldpoint if yieldpoint flag is >0
      fr1 = asm.forwardJcc(VM_Assembler.LE);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromBackedgeMethod.getOffset());
    } else { // EPILOGUE
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(VM_Assembler.EQ);
      asm.emitCALL_RegDisp(JTOC, VM_Entrypoints.yieldpointFromEpilogueMethod.getOffset());
    }
    fr1.resolve(asm);

    if (VM.BuildForAdaptiveSystem && options.INVOCATION_COUNTERS) {
      int id = compiledMethod.getId();
      VM_InvocationCounts.allocateCounter(id);
      asm.emitMOV_Reg_RegDisp(ECX, JTOC, VM_AosEntrypoints.invocationCountsField.getOffset());
      asm.emitSUB_RegDisp_Imm(ECX, Offset.fromIntZeroExtend(compiledMethod.getId() << 2), 1);
      VM_ForwardReference notTaken = asm.forwardJcc(VM_Assembler.GT);
      asm.emitPUSH_Imm(id);
      genParameterRegisterLoad(1);
      asm.emitCALL_RegDisp(JTOC, VM_AosEntrypoints.invocationCounterTrippedMethod.getOffset());
      notTaken.resolve(asm);
    }
  }

  // Indicate if specified VM_Magic method causes a frame to be created on the runtime stack.
  // Taken:   VM_Method of the magic method being called
  // Returned: true if method causes a stackframe to be created
  //
  public static boolean checkForActualCall(VM_MethodReference methodToBeCalled) {
    VM_Atom methodName = methodToBeCalled.getName();
    return methodName == VM_MagicNames.invokeClassInitializer ||
           methodName == VM_MagicNames.invokeMethodReturningVoid ||
           methodName == VM_MagicNames.invokeMethodReturningInt ||
           methodName == VM_MagicNames.invokeMethodReturningLong ||
           methodName == VM_MagicNames.invokeMethodReturningFloat ||
           methodName == VM_MagicNames.invokeMethodReturningDouble ||
           methodName == VM_MagicNames.invokeMethodReturningObject ||
           methodName == VM_MagicNames.addressArrayCreate;
  }

  private boolean genMagic(VM_MethodReference m) {
    VM_Atom methodName = m.getName();

    if (m.getType() == VM_TypeReference.Address) {
      // Address magic

      VM_TypeReference[] types = m.getParameterTypes();

      // Loads all take the form:
      // ..., Address, [Offset] -> ..., Value

      if (methodName == VM_MagicNames.loadAddress ||
          methodName == VM_MagicNames.prepareAddress ||
          methodName == VM_MagicNames.prepareObjectReference ||
          methodName == VM_MagicNames.loadWord ||
          methodName == VM_MagicNames.loadObjectReference ||
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
          asm.emitPOP_Reg(S0);                  // offset
          asm.emitPOP_Reg(T0);                  // object ref
          asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, NO_SLOT); // pushes [T0+S0]
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadByte) {
        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVZX_Reg_RegInd_Byte(T0, T0);
          asm.emitPUSH_Reg(T0);
        } else {
          // Load at offset
          asm.emitPOP_Reg(S0);                  // offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVZX_Reg_RegIdx_Byte(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend byte [T0+S0]
          asm.emitPUSH_Reg(T0);
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadShort) {
        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVSX_Reg_RegInd_Word(T0, T0);
          asm.emitPUSH_Reg(T0);
        } else {
          // Load at offset
          asm.emitPOP_Reg(S0);                  // offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVSX_Reg_RegIdx_Word(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend word [T0+S0]
          asm.emitPUSH_Reg(T0);
        }
        return true;
      }

      if (methodName == VM_MagicNames.loadChar) {

        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVZX_Reg_RegInd_Word(T0, T0);
          asm.emitPUSH_Reg(T0);
        } else {
          // Load at offset
          asm.emitPOP_Reg(S0);                  // offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitMOVZX_Reg_RegIdx_Word(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend word [T0+S0]
          asm.emitPUSH_Reg(T0);
        }
        return true;
      }

      if (methodName == VM_MagicNames.prepareLong
               || methodName == VM_MagicNames.loadLong
               || methodName == VM_MagicNames.loadDouble) {

        if (types.length == 0) {
          // No offset
          asm.emitPOP_Reg(T0);                // base
          asm.emitPUSH_RegDisp(T0, ONE_SLOT); // pushes [T0+4]
          asm.emitPUSH_RegInd(T0);            // pushes [T0]
        } else {
          // Load at offset
          asm.emitPOP_Reg(S0);                  // offset
          asm.emitPOP_Reg(T0);                  // base
          asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, ONE_SLOT); // pushes [T0+S0+4]
          asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, NO_SLOT);  // pushes [T0+S0]
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
            storeType == VM_TypeReference.ObjectReference ||
            storeType == VM_TypeReference.Word ||
            storeType == VM_TypeReference.Float) {

          if (types.length == 1) {
            // No offset
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(S0);                   // address
            asm.emitMOV_RegInd_Reg(S0, T0);         // [S0+0] <- T0
          } else {
            // Store at offset
            asm.emitPOP_Reg(S0);                   // offset
            asm.emitPOP_Reg(T0);                   // value
            asm.emitPOP_Reg(T1);                   // address
            asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- T0
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
            asm.emitMOV_RegIdx_Reg_Byte(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- (byte) T0
          }
          return true;
        }

        if (storeType == VM_TypeReference.Short || storeType == VM_TypeReference.Char) {

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
            asm.emitMOV_RegIdx_Reg_Word(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- (word) T0
          }
          return true;
        }

        if (storeType == VM_TypeReference.Double || storeType == VM_TypeReference.Long) {

          if (types.length == 1) {
            // No offset
            asm.emitMOV_Reg_RegInd(T0, SP);             // value high
            asm.emitMOV_Reg_RegDisp(T1, SP, TWO_SLOTS); // base
            asm.emitMOV_RegInd_Reg(T1, T0);             // [T1] <- T0
            asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);  // value low
            asm.emitMOV_RegDisp_Reg(T1, ONE_SLOT, T0);  // [T1+4] <- T0
            asm.emitADD_Reg_Imm(SP, WORDSIZE * 3);      // pop stack locations
          } else {
            // Store at offset
            asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);          // value high
            asm.emitMOV_Reg_RegInd(S0, SP);     // offset
            asm.emitMOV_Reg_RegDisp(T1, SP, THREE_SLOTS);     // base
            asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- T0
            asm.emitMOV_Reg_RegDisp(T0, SP, TWO_SLOTS);     // value low
            asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, ONE_SLOT, T0); // [T1+S0+4] <- T0
            asm.emitADD_Reg_Imm(SP, WORDSIZE * 4); // pop stack locations
          }
          return true;
        }
      } else if (methodName == VM_MagicNames.attempt) {
        // All attempts are similar 32 bit values.
        if (types.length == 3) {
          // Offset passed
          asm.emitPOP_Reg(S0);        // S0 = offset
        }
        asm.emitPOP_Reg(T1);          // newVal
        asm.emitPOP_Reg(EAX);         // oldVal (EAX is implicit arg to LCMPX
        if (types.length == 3) {
          asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
        } else {
          // No offset
          asm.emitMOV_Reg_RegInd(S0, SP);  // S0 = base
        }
        asm.emitXOR_Reg_Reg(T0, T0);
        asm.emitLockNextInstruction();
        asm.emitCMPXCHG_RegInd_Reg(S0, T1);   // atomic compare-and-exchange
        asm.emitMOV_RegInd_Imm(SP, 1);        // 'push' true (overwriting base)
        asm.emitBranchLikelyNextInstruction();
        VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.EQ); // skip if compare fails
        asm.emitMOV_RegInd_Imm(SP, 0);        // 'push' false (overwriting base)
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
      asm.emitPOP_Reg(T1);            // newVal
      asm.emitPOP_Reg(EAX);           // oldVal (EAX is implicit arg to LCMPXCNG
      asm.emitPOP_Reg(S0);            // S0 = offset
      asm.emitADD_Reg_RegInd(S0, SP);  // S0 += base
      asm.emitLockNextInstruction();
      asm.emitCMPXCHG_RegInd_Reg(S0, T1);   // atomic compare-and-exchange
      asm.emitMOV_RegInd_Imm(SP, 1);        // 'push' true (overwriting base)
      asm.emitBranchLikelyNextInstruction();
      VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.EQ); // skip if compare fails
      asm.emitMOV_RegInd_Imm(SP, 0);        // 'push' false (overwriting base)
      fr.resolve(asm);
      return true;
    }

    if (methodName == VM_MagicNames.attemptLong) {
      // attempt gets called with four arguments: base, offset, oldVal, newVal
      // returns ([base+offset] == oldVal)
      // if ([base+offset] == oldVal) [base+offset] := newVal
      // (operation on memory is atomic)
      //t1:t0 with s0:ebx
      asm.emitMOV_Reg_RegDisp(T1, SP, THREE_SLOTS);
      asm.emitMOV_Reg_RegDisp(T0, SP, TWO_SLOTS);   // T1:T0 (EDX:EAX) -> oldVal
      asm.emitMOV_RegDisp_Reg(SP, THREE_SLOTS, EBX);  // Save EBX
      asm.emitMOV_RegDisp_Reg(SP, TWO_SLOTS, ESI);    // Save ESI
      asm.emitMOV_Reg_RegInd(EBX, SP);
      asm.emitMOV_Reg_RegDisp(S0, SP, ONE_SLOT);   // S0:EBX (ECX:EBX) -> newVal
      asm.emitMOV_Reg_RegDisp(ESI, SP, FIVE_SLOTS); // ESI := base
      asm.emitADD_Reg_RegDisp(ESI, SP, FOUR_SLOTS); // ESI += offset
      asm.emitLockNextInstruction();
      asm.emitCMPXCHG8B_RegInd (ESI);        // atomic compare-and-exchange
      VM_ForwardReference fr1 = asm.forwardJcc(VM_Assembler.NE); // skip if compare fails
      asm.emitMOV_RegDisp_Imm (SP, FIVE_SLOTS, 1);        // 'push' true (overwriting base)
      VM_ForwardReference fr2 = asm.forwardJMP(); // skip if compare fails
      fr1.resolve(asm);
      asm.emitMOV_RegDisp_Imm (SP, FIVE_SLOTS, 0);        // 'push' false (overwriting base)
      fr2.resolve(asm);
      asm.emitMOV_Reg_RegDisp(EBX, SP, THREE_SLOTS);  // Restore EBX
      asm.emitMOV_Reg_RegDisp(ESI, SP, TWO_SLOTS);  // Restore ESI
      asm.emitADD_Reg_Imm(SP, WORDSIZE*5);      // adjust SP popping the 4 args (6 slots) and pushing the result
      return true;
    }

    if (methodName == VM_MagicNames.saveThreadState) {
      Offset offset = VM_ArchEntrypoints.saveThreadStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.threadSwitch) {
      Offset offset = VM_ArchEntrypoints.threadSwitchInstructionsField.getOffset();
      genParameterRegisterLoad(2); // pass 2 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.restoreHardwareExceptionState) {
      Offset offset = VM_ArchEntrypoints.restoreHardwareExceptionStateInstructionsField.getOffset();
      genParameterRegisterLoad(1); // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.invokeClassInitializer) {
      asm.emitPOP_Reg(S0);
      asm.emitCALL_Reg(S0); // call address just popped
      return true;
    }

    if (m.isSysCall()) {
      VM_TypeReference[] args = m.getParameterTypes();
      VM_TypeReference rtype = m.getReturnType();
      Offset offsetToJavaArg = THREE_SLOTS; // the three regs saved in (2)
      int paramBytes = 0;

      // (1) save three RVM nonvolatile/special registers
      //     we don't have to save EBP: the callee will
      //     treat it as a framepointer and save/restore
      //     it for us.
      asm.emitPUSH_Reg(EBX);
      asm.emitPUSH_Reg(ESI);
      asm.emitPUSH_Reg(EDI);

      // (2) Push args to target function (reversed), except for the first argument
      for (int i = args.length - 1; i >= 1; i--) {
        VM_TypeReference arg = args[i];
        if (arg.isLongType() || arg.isDoubleType()) {
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg.plus(4));
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg.plus(4));
          offsetToJavaArg = offsetToJavaArg.plus(16);
          paramBytes += 8;
        } else {
          asm.emitPUSH_RegDisp(SP, offsetToJavaArg);
          offsetToJavaArg = offsetToJavaArg.plus(8);
          paramBytes += 4;
        }
      }

      // (3) invoke target function with address given by the first argument
      asm.emitMOV_Reg_RegDisp(S0, SP, offsetToJavaArg);
      asm.emitCALL_Reg(S0);

      // (4) pop space for arguments
      asm.emitADD_Reg_Imm(SP, paramBytes);

      // (5) restore RVM registers
      asm.emitPOP_Reg(EDI);
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);

      // (6) pop expression stack (including the first parameter)
      asm.emitADD_Reg_Imm(SP, paramBytes + 4);

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
      asm.emitLEA_Reg_RegDisp(S0, SP, fp2spOffset(NO_SLOT));
      asm.emitPUSH_Reg(S0);
      return true;
    }

    if (methodName == VM_MagicNames.getCallerFramePointer) {
      asm.emitPOP_Reg(T0);                                       // Callee FP
      asm.emitPUSH_RegDisp(T0, Offset.fromIntSignExtend(STACKFRAME_FRAME_POINTER_OFFSET)); // Caller FP
      return true;
    }

    if (methodName == VM_MagicNames.setCallerFramePointer) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, Offset.fromIntSignExtend(STACKFRAME_FRAME_POINTER_OFFSET), T0); // [S0+SFPO] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.getCompiledMethodID) {
      asm.emitPOP_Reg(T0);                                   // Callee FP
      asm.emitPUSH_RegDisp(T0, Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET)); // Callee CMID
      return true;
    }

    if (methodName == VM_MagicNames.setCompiledMethodID) {
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // fp
      asm.emitMOV_RegDisp_Reg(S0, Offset.fromIntSignExtend(STACKFRAME_METHOD_ID_OFFSET), T0); // [S0+SMIO] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.getReturnAddressLocation) {
      asm.emitPOP_Reg(T0);                                        // Callee FP
      asm.emitADD_Reg_Imm(T0, STACKFRAME_RETURN_ADDRESS_OFFSET);  // location containing callee return address
      asm.emitPUSH_Reg(T0);                                       // Callee return address
      return true;
    }

    if (methodName == VM_MagicNames.getTocPointer || methodName == VM_MagicNames.getJTOC) {
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
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, NO_SLOT); // pushes [T0+S0]
      return true;
    }

    if (methodName == VM_MagicNames.getUnsignedByteAtOffset) {
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitMOVZX_Reg_RegIdx_Byte(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend byte [T0+S0]
      asm.emitPUSH_Reg(T0);
      return true;
    }

    if (methodName == VM_MagicNames.getByteAtOffset) {
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitMOVSX_Reg_RegIdx_Byte(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend byte [T0+S0]
      asm.emitPUSH_Reg(T0);
      return true;
    }

    if (methodName == VM_MagicNames.getCharAtOffset) {
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitMOVZX_Reg_RegIdx_Word(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend char [T0+S0]
      asm.emitPUSH_Reg(T0);
      return true;
    }

    if (methodName == VM_MagicNames.getShortAtOffset) {
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitMOVSX_Reg_RegIdx_Word(T0, T0, S0, VM_Assembler.BYTE, NO_SLOT); // load and zero extend char [T0+S0]
      asm.emitPUSH_Reg(T0);
      return true;
    }

    if (methodName == VM_MagicNames.setIntAtOffset ||
        methodName == VM_MagicNames.setWordAtOffset ||
        methodName == VM_MagicNames.setObjectAtOffset) {
      if (m.getParameterTypes().length == 4) {
        // discard locationMetadata parameter
        asm.emitPOP_Reg(T0); // locationMetadata, not needed by baseline compiler.
      }
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.setByteAtOffset) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg_Byte(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- (byte) T0
      return true;
    }

    if (methodName == VM_MagicNames.setCharAtOffset) {
      asm.emitPOP_Reg(T0);                   // value
      asm.emitPOP_Reg(S0);                   // offset
      asm.emitPOP_Reg(T1);                   // obj ref
      asm.emitMOV_RegIdx_Reg_Word(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- (char) T0
      return true;
    }

    if (methodName == VM_MagicNames.prepareLong ||
        methodName == VM_MagicNames.getLongAtOffset || methodName == VM_MagicNames.getDoubleAtOffset) {
      asm.emitPOP_Reg(T0);                  // object ref
      asm.emitPOP_Reg(S0);                  // offset
      asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, ONE_SLOT); // pushes [T0+S0+4]
      asm.emitPUSH_RegIdx(T0, S0, VM_Assembler.BYTE, NO_SLOT); // pushes [T0+S0]
      return true;
    }

    if (methodName == VM_MagicNames.setLongAtOffset || methodName == VM_MagicNames.setDoubleAtOffset) {
      asm.emitMOV_Reg_RegInd(T0, SP);          // value high
      asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS);     // offset
      asm.emitMOV_Reg_RegDisp(T1, SP, THREE_SLOTS);     // obj ref
      asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, NO_SLOT, T0); // [T1+S0] <- T0
      asm.emitMOV_Reg_RegDisp(T0, SP, ONE_SLOT);     // value low
      asm.emitMOV_RegIdx_Reg(T1, S0, VM_Assembler.BYTE, ONE_SLOT, T0); // [T1+S0+4] <- T0
      asm.emitADD_Reg_Imm(SP, WORDSIZE * 4); // pop stack locations
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

    if (methodName == VM_MagicNames.setMemoryInt || methodName == VM_MagicNames.setMemoryWord) {
      if (m.getParameterTypes().length == 3) {
        // discard locationMetadata parameter
        asm.emitPOP_Reg(T0); // locationMetadata, not needed by baseline compiler.
      }
      asm.emitPOP_Reg(T0);  // value
      asm.emitPOP_Reg(S0);  // address
      asm.emitMOV_RegInd_Reg(S0, T0); // [S0+0] <- T0
      return true;
    }

    if (methodName == VM_MagicNames.objectAsAddress ||
        methodName == VM_MagicNames.addressAsByteArray ||
        methodName == VM_MagicNames.addressAsObject ||
        methodName == VM_MagicNames.addressAsObjectArray ||
        methodName == VM_MagicNames.objectAsType ||
        methodName == VM_MagicNames.objectAsShortArray ||
        methodName == VM_MagicNames.objectAsIntArray ||
        methodName == VM_MagicNames.objectAsProcessor ||
        methodName == VM_MagicNames.threadAsCollectorThread ||
        methodName == VM_MagicNames.floatAsIntBits ||
        methodName == VM_MagicNames.intBitsAsFloat ||
        methodName == VM_MagicNames.doubleAsLongBits ||
        methodName == VM_MagicNames.longBitsAsDouble) {
      // no-op (a type change, not a representation change)
      return true;
    }

    // code for      VM_Type VM_Magic.getObjectType(Object object)
    if (methodName == VM_MagicNames.getObjectType) {
      asm.emitPOP_Reg(T0);                               // object ref
      VM_ObjectModel.baselineEmitLoadTIB(asm, S0, T0);
      asm.emitPUSH_RegDisp(S0, Offset.fromIntZeroExtend(TIB_TYPE_INDEX << LG_WORDSIZE)); // push VM_Type slot of TIB
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
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      return true;
    }

    if (methodName == VM_MagicNames.invokeMethodReturningInt) {
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return true;
    }

    if (methodName == VM_MagicNames.invokeMethodReturningLong) {
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0); // high half
      asm.emitPUSH_Reg(T1); // low half
      return true;
    }

    if (methodName == VM_MagicNames.invokeMethodReturningFloat) {
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm(SP, 4);
      if (SSE2_FULL) {
        asm.emitMOVSS_RegInd_Reg(SP, XMM0);
      } else {
        asm.emitFSTP_RegInd_Reg(SP, FP0);
      }
      return true;
    }

    if (methodName == VM_MagicNames.invokeMethodReturningDouble) {
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitSUB_Reg_Imm(SP, 8);
      if (SSE2_FULL) {
        asm.emitMOVSD_RegInd_Reg(SP, XMM0);
      } else {
        asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
      }
      return true;
    }

    if (methodName == VM_MagicNames.invokeMethodReturningObject) {
      Offset offset = VM_ArchEntrypoints.reflectiveMethodInvokerInstructionsField.getOffset();
      genParameterRegisterLoad(5); // pass 5 parameter words
      asm.emitCALL_RegDisp(JTOC, offset);
      asm.emitPUSH_Reg(T0);
      return true;
    }

    // baseline invocation
    // one paramater, on the stack  -- actual code
    if (methodName == VM_MagicNames.dynamicBridgeTo) {
      if (VM.VerifyAssertions) VM._assert(klass.hasDynamicBridgeAnnotation());

      // save the branch address for later
      asm.emitPOP_Reg(S0);             // S0<-code address

      asm.emitADD_Reg_Imm(SP, fp2spOffset(NO_SLOT).toInt() - 4); // just popped 4 bytes above.

      if (SSE2_FULL) {
        // TODO: Restore SSE2 Control word?
        asm.emitMOVQ_Reg_RegDisp(XMM0, SP, XMM_SAVE_OFFSET.plus( 0));
        asm.emitMOVQ_Reg_RegDisp(XMM1, SP, XMM_SAVE_OFFSET.plus( 8));
        asm.emitMOVQ_Reg_RegDisp(XMM2, SP, XMM_SAVE_OFFSET.plus(16));
        asm.emitMOVQ_Reg_RegDisp(XMM3, SP, XMM_SAVE_OFFSET.plus(24));
      } else {
        // restore FPU state
        asm.emitFRSTOR_RegDisp(SP, FPU_SAVE_OFFSET);
      }

      // restore GPRs
      asm.emitMOV_Reg_RegDisp(T0, SP, T0_SAVE_OFFSET);
      asm.emitMOV_Reg_RegDisp(T1, SP, T1_SAVE_OFFSET);
      asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);
      asm.emitMOV_Reg_RegDisp(JTOC, SP, JTOC_SAVE_OFFSET);

      // pop frame
      asm.emitPOP_RegDisp(PR, VM_ArchEntrypoints.framePointerField.getOffset()); // FP<-previous FP

      // branch
      asm.emitJMP_Reg(S0);
      return true;
    }

    if (methodName == VM_MagicNames.returnToNewStack) {
      // SP gets frame pointer for new stack
      asm.emitPOP_Reg(SP);

      // restore nonvolatile registers
      asm.emitMOV_Reg_RegDisp(JTOC, SP, JTOC_SAVE_OFFSET);
      asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);

      // discard current stack frame
      asm.emitPOP_RegDisp(PR, VM_ArchEntrypoints.framePointerField.getOffset());

      // return to caller- pop parameters from stack
      asm.emitRET_Imm(parameterWords << LG_WORDSIZE);
      return true;
    }

    // software prefetch
    if (methodName == VM_MagicNames.prefetch || methodName == VM_MagicNames.prefetchNTA) {
      asm.emitPOP_Reg(T0);
      asm.emitPREFETCH_Reg(T0);
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
        methodName == VM_MagicNames.wordFromObject ||
        methodName == VM_MagicNames.wordFromIntZeroExtend ||
        methodName == VM_MagicNames.wordFromIntSignExtend ||
        methodName == VM_MagicNames.wordToInt ||
        methodName == VM_MagicNames.wordToAddress ||
        methodName == VM_MagicNames.wordToOffset ||
        methodName == VM_MagicNames.wordToObject ||
        methodName == VM_MagicNames.wordToObjectReference ||
        methodName == VM_MagicNames.wordToExtent ||
        methodName == VM_MagicNames.wordToWord ||
        methodName == VM_MagicNames.codeArrayToAddress) {
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
      asm.emitNOT_RegInd(SP);
      return true;
    }

    if (methodName == VM_MagicNames.wordPlus) {
      asm.emitPOP_Reg(T0);
      asm.emitADD_RegInd_Reg(SP, T0);
      return true;
    }

    if (methodName == VM_MagicNames.wordMinus || methodName == VM_MagicNames.wordDiff) {
      asm.emitPOP_Reg(T0);
      asm.emitSUB_RegInd_Reg(SP, T0);
      return true;
    }

    if (methodName == VM_MagicNames.wordZero || methodName == VM_MagicNames.wordNull) {
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
      generateAddrComparison(VM_Assembler.LLT);
      return true;
    }
    if (methodName == VM_MagicNames.wordLE) {
      generateAddrComparison(VM_Assembler.LLE);
      return true;
    }
    if (methodName == VM_MagicNames.wordGT) {
      generateAddrComparison(VM_Assembler.LGT);
      return true;
    }
    if (methodName == VM_MagicNames.wordGE) {
      generateAddrComparison(VM_Assembler.LGE);
      return true;
    }
    if (methodName == VM_MagicNames.wordsLT) {
      generateAddrComparison(VM_Assembler.LT);
      return true;
    }
    if (methodName == VM_MagicNames.wordsLE) {
      generateAddrComparison(VM_Assembler.LE);
      return true;
    }
    if (methodName == VM_MagicNames.wordsGT) {
      generateAddrComparison(VM_Assembler.GT);
      return true;
    }
    if (methodName == VM_MagicNames.wordsGE) {
      generateAddrComparison(VM_Assembler.GE);
      return true;
    }
    if (methodName == VM_MagicNames.wordEQ) {
      generateAddrComparison(VM_Assembler.EQ);
      return true;
    }
    if (methodName == VM_MagicNames.wordNE) {
      generateAddrComparison(VM_Assembler.NE);
      return true;
    }
    if (methodName == VM_MagicNames.wordIsZero) {
      asm.emitPUSH_Imm(0);
      generateAddrComparison(VM_Assembler.EQ);
      return true;
    }
    if (methodName == VM_MagicNames.wordIsNull) {
      asm.emitPUSH_Imm(0);
      generateAddrComparison(VM_Assembler.EQ);
      return true;
    }
    if (methodName == VM_MagicNames.wordIsMax) {
      asm.emitPUSH_Imm(-1);
      generateAddrComparison(VM_Assembler.EQ);
      return true;
    }
    if (methodName == VM_MagicNames.wordLsh) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg(ECX);
        asm.emitSHL_RegInd_Reg(SP, ECX);
      } else {
        VM._assert(false);
      }
      return true;
    }
    if (methodName == VM_MagicNames.wordRshl) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg(ECX);
        asm.emitSHR_RegInd_Reg(SP, ECX);
      } else {
        VM._assert(false);
      }
      return true;
    }
    if (methodName == VM_MagicNames.wordRsha) {
      if (VM.BuildFor32Addr) {
        asm.emitPOP_Reg(ECX);
        asm.emitSAR_RegInd_Reg(SP, ECX);
      } else {
        VM._assert(false);
      }
      return true;
    }
    return false;

  }

  private void generateAddrComparison(byte comparator) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, S0);
    VM_ForwardReference fr1 = asm.forwardJcc(comparator);
    asm.emitPUSH_Imm(0);
    VM_ForwardReference fr2 = asm.forwardJMP();
    fr1.resolve(asm);
    asm.emitPUSH_Imm(1);
    fr2.resolve(asm);
  }

  // Offset of Java local variable (off stack pointer)
  // assuming ESP is still positioned as it was at the
  // start of the current bytecode (biStart)
  private Offset localOffset(int local) {
    return Offset.fromIntZeroExtend((stackHeights[biStart] - local) << LG_WORDSIZE);
  }

  // Translate a FP offset into an SP offset
  // assuming ESP is still positioned as it was at the
  // start of the current bytecode (biStart)
  private Offset fp2spOffset(Offset offset) {
    int offsetToFrameHead = (stackHeights[biStart] << LG_WORDSIZE) - firstLocalOffset;
    return offset.plus(offsetToFrameHead);
  }

  private void emitDynamicLinkingSequence(byte reg, VM_MemberReference ref, boolean couldBeZero) {
    int memberId = ref.getId();
    Offset memberOffset = Offset.fromIntZeroExtend(memberId << 2);
    Offset tableOffset = VM_Entrypoints.memberOffsetsField.getOffset();
    if (couldBeZero) {
      Offset resolverOffset = VM_Entrypoints.resolveMemberMethod.getOffset();
      int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
      asm.emitMOV_Reg_RegDisp(reg, JTOC, tableOffset);      // reg is offsets table
      asm.emitMOV_Reg_RegDisp(reg,
                              reg,
                              memberOffset);      // reg is offset of member, or 0 if member's class isn't loaded
      if (NEEDS_DYNAMIC_LINK == 0) {
        asm.emitTEST_Reg_Reg(reg, reg);                      // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      } else {
        asm.emitCMP_Reg_Imm(reg, NEEDS_DYNAMIC_LINK);        // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      }
      VM_ForwardReference fr = asm.forwardJcc(VM_Assembler.NE);       // if so, skip call instructions
      asm.emitPUSH_Imm(memberId);                            // pass member's dictId
      genParameterRegisterLoad(1);                           // pass 1 parameter word
      asm.emitCALL_RegDisp(JTOC, resolverOffset);            // does class loading as sideffect
      asm.emitJMP_Imm(retryLabel);                          // reload reg with valid value
      fr.resolve(asm);                                       // come from Jcc above.
    } else {
      asm.emitMOV_Reg_RegDisp(reg, JTOC, tableOffset);      // reg is offsets table
      asm.emitMOV_Reg_RegDisp(reg, reg, memberOffset);      // reg is offset of member
    }
  }

  /**
   * Emit code to invoke a compiled method (with known jtoc offset).
   * Treat it like a resolved invoke static, but take care of
   * this object in the case.
   *
   * I havenot thought about GCMaps for invoke_compiledmethod
   * TODO: Figure out what the above GCMaps comment means and fix it!
   */
  protected final void emit_invoke_compiledmethod(VM_CompiledMethod cm) {
    Offset methodOffset = cm.getOsrJTOCoffset();
    boolean takeThis = !cm.method.isStatic();
    VM_MethodReference ref = cm.method.getMemberRef().asMethodReference();
    genParameterRegisterLoad(ref, takeThis);
    asm.emitCALL_RegDisp(JTOC, methodOffset);
    genResultRegisterUnload(ref);
  }

  protected final void emit_loadretaddrconst(int bcIndex) {
    asm.registerLoadRetAddrConst(bcIndex);
    asm.emitPUSH_Imm(bcIndex);
  }

  /* bTarget is optional, it emits a JUMP instruction, but the caller
   * in resposible for patch the target address by call resolve method
   * of returned forward reference
   */
  protected final VM_ForwardReference emit_pending_goto(int bTarget) {
    return asm.generatePendingJMP(bTarget);
  }
}
