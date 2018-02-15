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
package org.jikesrvm.compilers.baseline.ia32;

import static org.jikesrvm.classloader.ClassLoaderConstants.CP_CLASS;
import static org.jikesrvm.classloader.ClassLoaderConstants.CP_STRING;
import static org.jikesrvm.compilers.common.assembler.ia32.AssemblerConstants.*;
import static org.jikesrvm.ia32.ArchConstants.SSE2_BASE;
import static org.jikesrvm.ia32.ArchConstants.SSE2_FULL;
import static org.jikesrvm.ia32.BaselineConstants.EBP_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.EBX_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.EDI_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.FPU_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.LG_WORDSIZE;
import static org.jikesrvm.ia32.BaselineConstants.S0;
import static org.jikesrvm.ia32.BaselineConstants.S1;
import static org.jikesrvm.ia32.BaselineConstants.SAVED_GPRS;
import static org.jikesrvm.ia32.BaselineConstants.SAVED_GPRS_FOR_SAVE_LS_REGISTERS;
import static org.jikesrvm.ia32.BaselineConstants.SP;
import static org.jikesrvm.ia32.BaselineConstants.T0;
import static org.jikesrvm.ia32.BaselineConstants.T0_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.T1;
import static org.jikesrvm.ia32.BaselineConstants.T1_SAVE_OFFSET;
import static org.jikesrvm.ia32.BaselineConstants.TR;
import static org.jikesrvm.ia32.BaselineConstants.WORDSIZE;
import static org.jikesrvm.ia32.BaselineConstants.XMM_SAVE_OFFSET;
import static org.jikesrvm.ia32.RegisterConstants.EAX;
import static org.jikesrvm.ia32.RegisterConstants.EBP;
import static org.jikesrvm.ia32.RegisterConstants.EBX;
import static org.jikesrvm.ia32.RegisterConstants.ECX;
import static org.jikesrvm.ia32.RegisterConstants.EDI;
import static org.jikesrvm.ia32.RegisterConstants.EDX;
import static org.jikesrvm.ia32.RegisterConstants.ESI;
import static org.jikesrvm.ia32.RegisterConstants.ESP;
import static org.jikesrvm.ia32.RegisterConstants.FP0;
import static org.jikesrvm.ia32.RegisterConstants.FP1;
import static org.jikesrvm.ia32.RegisterConstants.NATIVE_PARAMETER_FPRS;
import static org.jikesrvm.ia32.RegisterConstants.NATIVE_PARAMETER_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.NONVOLATILE_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.NUM_PARAMETER_FPRS;
import static org.jikesrvm.ia32.RegisterConstants.NUM_PARAMETER_GPRS;
import static org.jikesrvm.ia32.RegisterConstants.THREAD_REGISTER;
import static org.jikesrvm.ia32.RegisterConstants.XMM0;
import static org.jikesrvm.ia32.RegisterConstants.XMM1;
import static org.jikesrvm.ia32.RegisterConstants.XMM2;
import static org.jikesrvm.ia32.RegisterConstants.XMM3;
import static org.jikesrvm.ia32.StackframeLayoutConstants.X87_FPU_STATE_SIZE;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_BODY_OFFSET;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ia32.StackframeLayoutConstants.BASELINE_XMM_STATE_SIZE;
import static org.jikesrvm.ia32.TrapConstants.RVM_TRAP_BASE;
import static org.jikesrvm.mm.mminterface.Barriers.*;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ARRAY_LENGTH_BYTES;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.NEEDS_DYNAMIC_LINK;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_DOES_IMPLEMENT_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_INTERFACE_DISPATCH_TABLE_INDEX;
import static org.jikesrvm.objectmodel.TIBLayoutConstants.TIB_SUPERCLASS_IDS_INDEX;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_BYTE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_SHORT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_SHORT;
import static org.jikesrvm.runtime.RuntimeEntrypoints.TRAP_UNREACHABLE_BYTECODE;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.AosEntrypoints;
import org.jikesrvm.adaptive.recompilation.InvocationCounts;
import org.jikesrvm.classloader.DynamicTypeCheck;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.InterfaceInvocation;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.compilers.baseline.EdgeCounts;
import org.jikesrvm.compilers.baseline.TemplateCompilerFramework;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.assembler.AbstractAssembler;
import org.jikesrvm.compilers.common.assembler.AbstractLister;
import org.jikesrvm.compilers.common.assembler.ForwardReference;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.compilers.common.assembler.ia32.Lister;
import org.jikesrvm.ia32.RegisterConstants.GPR;
import org.jikesrvm.ia32.RegisterConstants.XMM;
import org.jikesrvm.jni.ia32.JNICompiler;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * BaselineCompilerImpl is the baseline compiler implementation for the IA32 architecture.
 */
public final class BaselineCompilerImpl extends BaselineCompiler {

  private final Assembler asm;
  private final Lister lister;

  static {
    // Force resolution of BaselineMagic before using in genMagic
    Object x = BaselineMagic.generateMagic(null, null, null, Offset.zero());
  }

  private final int parameterWords;
  private Offset firstLocalOffset;

  static final Offset NO_SLOT = Offset.zero();
  static final Offset ONE_SLOT = NO_SLOT.plus(WORDSIZE);
  static final Offset TWO_SLOTS = ONE_SLOT.plus(WORDSIZE);
  static final Offset THREE_SLOTS = TWO_SLOTS.plus(WORDSIZE);
  static final Offset FOUR_SLOTS = THREE_SLOTS.plus(WORDSIZE);
  static final Offset FIVE_SLOTS = FOUR_SLOTS.plus(WORDSIZE);
  private static final Offset MINUS_ONE_SLOT = NO_SLOT.minus(WORDSIZE);

  /**
   * Create a BaselineCompilerImpl object for the compilation of method.
   *
   * @param cm the method that will be associated with this compilation
   */
  public BaselineCompilerImpl(BaselineCompiledMethod cm) {
    super(cm);
    stackHeights = new int[bcodes.length()];
    parameterWords = method.getParameterWords() + (method.isStatic() ? 0 : 1); // add 1 for this pointer
    asm = new Assembler(bcodes.length(),shouldPrint, this);
    lister = asm.getLister();
  }

  @Override
  protected AbstractAssembler getAssembler() {
    return asm;
  }

  @Override
  protected AbstractLister getLister() {
    return lister;
  }

  /**
   * Have we encountered a bytecode without valid stack heights? if so throw this exception
   */
  private static final class UnreachableBytecodeException extends Exception {
    private static final long serialVersionUID = 8300835844142105706L;
    UnreachableBytecodeException() {}
  }


  @Override
  protected void initializeCompiler() {
    //nothing to do for Intel
  }

  @Uninterruptible
  public static int locationToOffset(short location) {
    return -location;
  }

  @Uninterruptible
  public static short offsetToLocation(Offset offset) {
    return (short)-offset.toInt();
  }


  @Uninterruptible
  public static short offsetToLocation(int offset) {
    return (short)-offset;
  }

  @Uninterruptible
  static short getEmptyStackOffset(NormalMethod m) {
    return (short)getFirstLocalOffset(m).minus(m.getLocalWords() << LG_WORDSIZE).plus(WORDSIZE).toInt();
  }

  /**
   * @param method the method in question
   *
   * @return offset of first parameter
   */
  @Uninterruptible
  private static Offset getFirstLocalOffset(NormalMethod method) {
    if (method.getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      return STACKFRAME_BODY_OFFSET.minus(JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    } else if (method.hasBaselineSaveLSRegistersAnnotation()) {
      return STACKFRAME_BODY_OFFSET.minus(SAVED_GPRS_FOR_SAVE_LS_REGISTERS << LG_WORDSIZE);
    } else {
      return STACKFRAME_BODY_OFFSET.minus(SAVED_GPRS << LG_WORDSIZE);
    }
  }

  @Uninterruptible
  static Offset getStartLocalOffset(NormalMethod method) {
    return getFirstLocalOffset(method).plus(WORDSIZE);
  }

  /**
   * Adjust the value of ESP/RSP
   *
   * @param size amount to change ESP/RSP by
   * @param mayClobber can the value in S0 or memory be destroyed?
   * (i.e. can we use a destructive short push/pop opcode)
   */
  private void adjustStack(int size, boolean mayClobber) {
    final boolean debug = false;
    if (size != 0) {
      if (mayClobber) {
        // first try short opcodes
        switch(size >> LG_WORDSIZE) {
        case -2:
          if (debug) {
            asm.emitPUSH_Imm(0xFA1FACE);
            asm.emitPUSH_Imm(0xFA2FACE);
          } else {
            asm.emitPUSH_Reg(EAX);
            asm.emitPUSH_Reg(EAX);
          }
          return;
        case -1:
          if (debug) {
            asm.emitPUSH_Imm(0xFA3FACE);
          } else {
            asm.emitPUSH_Reg(EAX);
          }
          return;
        case 1:
          asm.emitPOP_Reg(S1);
          if (debug) {
            asm.emitMOV_Reg_Imm(S1, 0xFA4FACE);
          }
          return;
        case 2:
          asm.emitPOP_Reg(S1);
          asm.emitPOP_Reg(S1);
          if (debug) {
            asm.emitMOV_Reg_Imm(S1, 0xFA5FACE);
          }
          return;
        }
      }
      if (VM.BuildFor32Addr) {
        asm.emitADD_Reg_Imm(SP, size);
      } else {
        asm.emitADD_Reg_Imm_Quad(SP, size);
      }
    }
  }

  /**
   * Move a value from the stack into a register using the shortest encoding and
   * the appropriate width for 32/64
   *
   * @param dest register to load into
   * @param off offset on stack
   */
  private void stackMoveHelper(GPR dest, Offset off) {
    stackMoveHelper(asm, dest, off);
  }

  /**
   * Move a value from the stack into a register using the shortest encoding and
   * the appropriate width for 32/64
   *
   * @param asm the assembler instance
   * @param dest register to load into
   * @param off offset on stack
   */
  private static void stackMoveHelper(Assembler asm, GPR dest, Offset off) {
    if (WORDSIZE == 4) {
      if (off.isZero()) {
        asm.emitMOV_Reg_RegInd(dest, SP);
      } else {
        asm.emitMOV_Reg_RegDisp(dest, SP, off);
      }
    } else {
      if (off.isZero()) {
        asm.emitMOV_Reg_RegInd_Quad(dest, SP);
      } else {
        asm.emitMOV_Reg_RegDisp_Quad(dest, SP, off);
      }
    }
  }

  /*
   * implementation of abstract methods of BaselineCompiler
   */

  /**
   * Nothing to do on IA32.
   */
  @Override
  protected void starting_bytecode() {}

  @Override
  protected void emit_prologue() {
    genPrologue();
  }

  @Override
  protected void emit_threadSwitchTest(int whereFrom) {
    genThreadSwitchTest(whereFrom);
  }

  @Override
  protected boolean emit_Magic(MethodReference magicMethod) {
    return genMagic(magicMethod);
  }

  /*
   * Loading constants
   */

  @Override
  protected void emit_aconst_null() {
    asm.emitPUSH_Imm(0);
  }

  @Override
  protected void emit_iconst(int val) {
    asm.emitPUSH_Imm(val);
  }

  @Override
  protected void emit_lconst(int val) {
    asm.emitPUSH_Imm(0);    // high part
    asm.emitPUSH_Imm(val);  //  low part
  }

  @Override
  protected void emit_fconst_0() {
    asm.emitPUSH_Imm(0);
  }

  @Override
  protected void emit_fconst_1() {
    asm.emitPUSH_Imm(0x3f800000);
  }

  @Override
  protected void emit_fconst_2() {
    asm.emitPUSH_Imm(0x40000000);
  }

  @Override
  protected void emit_dconst_0() {
    if (VM.BuildFor32Addr) {
      asm.emitPUSH_Imm(0x00000000);
      asm.emitPUSH_Imm(0x00000000);
    } else {
      adjustStack(-WORDSIZE, true);
      asm.emitPUSH_Imm(0x00000000);
    }
  }

  @Override
  protected void emit_dconst_1() {
    if (VM.BuildFor32Addr) {
      asm.emitPUSH_Imm(0x3ff00000);
      asm.emitPUSH_Imm(0x00000000);
    } else {
      adjustStack(-WORDSIZE, true);
      asm.generateJTOCpush(Entrypoints.oneDoubleField.getOffset());
    }
  }

  @Override
  protected void emit_ldc(Offset offset, byte type) {
    if (VM.BuildFor32Addr || (type == CP_CLASS) || (type == CP_STRING)) {
      asm.generateJTOCpush(offset);
    } else {
      asm.generateJTOCloadInt(T0, offset);
      asm.emitPUSH_Reg(T0);
    }
  }

  @Override
  protected void emit_ldc2(Offset offset, byte type) {
    if (VM.BuildFor32Addr) {
      asm.emitPUSH_Abs(Magic.getTocPointer().plus(offset).plus(WORDSIZE)); // high 32 bits
      asm.emitPUSH_Abs(Magic.getTocPointer().plus(offset));   // low 32 bits
    } else {
      adjustStack(-WORDSIZE, true);
      asm.generateJTOCpush(offset);
    }
  }

  /*
   * loading local variables
   */

  @Override
  protected void emit_regular_iload(int index) {
    try {
      Offset offset = localOffset(index);
      if (offset.EQ(Offset.zero())) {
        asm.emitPUSH_RegInd(ESP);
      } else {
        asm.emitPUSH_RegDisp(ESP, offset);
      }
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_fload(int index) {
    // identical to iload
    emit_regular_iload(index);
  }

  @Override
  protected void emit_regular_aload(int index) {
    // identical to iload
    emit_regular_iload(index);
  }

  @Override
  protected void emit_lload(int index) {
    try {
      Offset offset = localOffset(index);
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_RegDisp(ESP, offset); // high part
        asm.emitPUSH_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
      } else {
        adjustStack(-WORDSIZE, true);
        asm.emitPUSH_RegDisp(ESP, offset);
      }
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_dload(int index) {
    // identical to lload
    emit_lload(index);
  }

  /*
   * storing local variables
   */

  @Override
  protected void emit_istore(int index) {
    try {
      Offset offset = localOffset(index).minus(WORDSIZE); // pop computes EA after ESP has moved by WORDSIZE!
      if (offset.EQ(Offset.zero())) {
        asm.emitPOP_RegInd(ESP);
      } else {
        asm.emitPOP_RegDisp(ESP, offset);
      }
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_fstore(int index) {
    // identical to istore
    emit_istore(index);
  }

  @Override
  protected void emit_astore(int index) {
    // identical to istore
    emit_istore(index);
  }

  @Override
  protected void emit_lstore(int index) {
    try {
      if (VM.BuildFor32Addr) {
        // pop computes EA after ESP has moved by 4!
        Offset offset = localOffset(index + 1).minus(WORDSIZE);
        asm.emitPOP_RegDisp(ESP, offset); // high part
        asm.emitPOP_RegDisp(ESP, offset); // low part (ESP has moved by 4!!)
      } else {
        Offset offset = localOffset(index + 1).minus(WORDSIZE);
        asm.emitPOP_RegDisp(ESP, offset);
        adjustStack(WORDSIZE, true); // throw away top word
      }
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_dstore(int index) {
    // identical to lstore
    emit_lstore(index);
  }

  /*
   * array loads
   */

  @Override
  protected void emit_iaload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(S0); // S0 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    if (VM.BuildFor32Addr) {
      // push [S0+T0<<2]
      asm.emitPUSH_RegIdx(S0, T0, WORD, NO_SLOT);
    } else {
      // T1 = [S0+T0<<2] NB: must use 32-bit memory access!
      asm.emitMOV_Reg_RegIdx(T1, S0, T0, WORD, NO_SLOT);
      asm.emitPUSH_Reg(T1); // push int on stack
    }
  }

  @Override
  protected void emit_faload() {
    // identical to iaload
    emit_iaload();
  }

  @Override
  protected void emit_aaload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(T1); // T1 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, T1); // T0 is index, T1 is address of array
    if (NEEDS_OBJECT_ALOAD_BARRIER) {
      // rewind 2 args on stack
      asm.emitPUSH_Reg(T1); // T1 is array ref
      asm.emitPUSH_Reg(T0); // T0 is array index
      Barriers.compileArrayLoadBarrier(asm, true);
    } else {
      asm.emitPUSH_RegIdx(T1, T0, (short)LG_WORDSIZE, NO_SLOT); // push [S0+T0*WORDSIZE]
    }
  }

  @Override
  protected void emit_caload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(S0); // S0 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    if (VM.BuildFor32Addr) {
      asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
    } else {
      asm.emitMOVZXQ_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
    }
    asm.emitPUSH_Reg(T1);        // push short onto stack
  }

  /**
   * Emits code to load an int local variable and then load from a character array
   * @param index the local index to load
   */
  @Override
  protected void emit_iload_caload(int index) {
    try {
      Offset offset = localOffset(index);
      if (offset.EQ(Offset.zero())) {
        asm.emitMOV_Reg_RegInd(T0, SP); // T0 is array index
      } else {
        asm.emitMOV_Reg_RegDisp(T0, SP, offset); // T0 is array index
      }
      // NB MSBs of T0 are already clear in 64bit
      asm.emitPOP_Reg(S0); // S0 is array ref
      genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
      // T1 = (int)[S0+T0<<1]
      if (VM.BuildFor32Addr) {
        asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
      } else {
        asm.emitMOVZXQ_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
      }
      asm.emitPUSH_Reg(T1);        // push short onto stack
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_saload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(S0); // S0 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    if (VM.BuildFor32Addr) {
      asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
    } else {
      asm.emitMOVSXQ_Reg_RegIdx_Word(T1, S0, T0, SHORT, NO_SLOT);
    }
    asm.emitPUSH_Reg(T1);        // push short onto stack
  }

  @Override
  protected void emit_baload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(S0); // S0 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
    // T1 = (int)[S0+T0<<1]
    if (VM.BuildFor32Addr) {
      asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, BYTE, NO_SLOT);
    } else {
      asm.emitMOVSXQ_Reg_RegIdx_Byte(T1, S0, T0, BYTE, NO_SLOT);
    }
    asm.emitPUSH_Reg(T1);        // push byte onto stack
  }

  @Override
  protected void emit_laload() {
    asm.emitPOP_Reg(T0); // T0 is array index
    asm.emitPOP_Reg(T1); // T1 is array ref
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, T1); // T0 is index, T1 is address of array
    if (VM.BuildFor32Addr) {
      asm.emitPUSH_RegIdx(T1, T0, LONG, ONE_SLOT); // load high part of desired long array element
      asm.emitPUSH_RegIdx(T1, T0, LONG, NO_SLOT);  // load low part of desired long array element
    } else {
      adjustStack(-WORDSIZE, true);
      asm.emitPUSH_RegIdx(T1, T0, LONG, NO_SLOT);  // load desired long array element
    }
  }

  @Override
  protected void emit_daload() {
    // identical to laload
    emit_laload();
  }

  /*
   * array stores
   */

  /**
   * Generates a primitive array store for the given size.
   *
   * @param size in bytes of the array store to generate
   */
  private void primitiveArrayStoreHelper(int size) {
    Barriers.compileModifyCheck(asm, (size == 8) ? 3 * WORDSIZE : 2 * WORDSIZE);
    if (VM.BuildFor32Addr) {
      if (size == 8) {
        asm.emitPOP_Reg(S1);       // S1 is the low value
      }
      asm.emitPOP_Reg(T1);         // T1 is the value/high value
      asm.emitPOP_Reg(T0);         // T0 is array index
      asm.emitPOP_Reg(S0);         // S0 is array ref
    } else {
      asm.emitPOP_Reg(T1);         // T1 is the value
      if (size == 8) {
        adjustStack(WORDSIZE, true); // throw away slot
      }
      asm.emitPOP_Reg(T0);         // T0 is array index
      asm.emitPOP_Reg(S0);         // S0 is array ref
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genBoundsCheck(asm, T0, S0);   // T0 is index, S0 is address of array
    switch (size) {
      case 8:
        if (VM.BuildFor32Addr) {
          asm.emitMOV_RegIdx_Reg(S0, T0, LONG, NO_SLOT, S1);  // [S0+T0<<<3] <- S1
          asm.emitMOV_RegIdx_Reg(S0, T0, LONG, ONE_SLOT, T1); // [4+S0+T0<<<3] <- T1
        } else {
          asm.emitMOV_RegIdx_Reg_Quad(S0, T0, LONG, NO_SLOT, T1); // [S0+T0<<<3] <- T1
        }
        break;
      case 4:
        asm.emitMOV_RegIdx_Reg(S0, T0, WORD, NO_SLOT, T1); // [S0 + T0<<2] <- T1
        break;
      case 2:
        // store halfword element into array i.e. [S0 +T0] <- T1 (halfword)
        asm.emitMOV_RegIdx_Reg_Word(S0, T0, SHORT, NO_SLOT, T1);
        break;
      case 1:
        asm.emitMOV_RegIdx_Reg_Byte(S0, T0, BYTE, NO_SLOT, T1); // [S0 + T0<<2] <- T1
        break;
      default:
        if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED, "Unhandled byte size!");
        } else {
          VM.sysFail("Unhandled byte size");
        }
    }
  }

  /**
   * Private helper to perform an array bounds check
   * @param index offset from current SP to the array index
   * @param arrayRef offset from current SP to the array reference
   */
  private void boundsCheckHelper(Offset index, Offset arrayRef) {
    stackMoveHelper(T0, index); // T0 is array index
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    stackMoveHelper(S0, arrayRef); // S0 is array ref
    genBoundsCheck(asm, T0, S0); // T0 is index, S0 is address of array
  }

  @Override
  protected void emit_iastore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (NEEDS_INT_ASTORE_BARRIER) {
      boundsCheckHelper(ONE_SLOT, TWO_SLOTS);
      Barriers.compileArrayStoreBarrierInt(asm, this);
    } else {
      primitiveArrayStoreHelper(4);
    }
  }

  @Override
  protected void emit_fastore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (NEEDS_FLOAT_ASTORE_BARRIER) {
      boundsCheckHelper(ONE_SLOT, TWO_SLOTS);
      Barriers.compileArrayStoreBarrierFloat(asm, this);
    } else {
      primitiveArrayStoreHelper(4);
    }
  }


  @Override
  protected void emit_aastore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (doesCheckStore) {
      genParameterRegisterLoad(asm, 3);
      asm.generateJTOCcall(Entrypoints.aastoreMethod.getOffset());
    } else {
      genParameterRegisterLoad(asm, 3);
      asm.generateJTOCcall(Entrypoints.aastoreUninterruptibleMethod.getOffset());
    }
  }

  @Override
  protected void emit_castore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (NEEDS_CHAR_ASTORE_BARRIER) {
      boundsCheckHelper(ONE_SLOT, TWO_SLOTS);
      Barriers.compileArrayStoreBarrierChar(asm, this);
    } else {
      primitiveArrayStoreHelper(2);
    }
  }

  @Override
  protected void emit_sastore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (NEEDS_SHORT_ASTORE_BARRIER) {
      boundsCheckHelper(ONE_SLOT, TWO_SLOTS);
      Barriers.compileArrayStoreBarrierShort(asm, this);
    } else {
      primitiveArrayStoreHelper(2);
    }
  }

  @Override
  protected void emit_bastore() {
    Barriers.compileModifyCheck(asm, 2 * WORDSIZE);
    if (NEEDS_BYTE_ASTORE_BARRIER) {
      boundsCheckHelper(ONE_SLOT, TWO_SLOTS);
      Barriers.compileArrayStoreBarrierByte(asm, this);
    } else {
      primitiveArrayStoreHelper(1);
    }
  }

  @Override
  protected void emit_lastore() {
    Barriers.compileModifyCheck(asm, 3 * WORDSIZE);
    if (NEEDS_LONG_ASTORE_BARRIER) {
      boundsCheckHelper(TWO_SLOTS, THREE_SLOTS);
      Barriers.compileArrayStoreBarrierLong(asm, this);
    } else {
      primitiveArrayStoreHelper(8);
    }
  }

  @Override
  protected void emit_dastore() {
    Barriers.compileModifyCheck(asm, 3 * WORDSIZE);
    if (NEEDS_DOUBLE_ASTORE_BARRIER) {
      boundsCheckHelper(TWO_SLOTS, THREE_SLOTS);
      Barriers.compileArrayStoreBarrierDouble(asm, this);
    } else {
      primitiveArrayStoreHelper(8);
    }
  }

  /*
   * expression stack manipulation
   */

  @Override
  protected void emit_pop() {
    adjustStack(WORDSIZE, true);
  }

  @Override
  protected void emit_pop2() {
    // This could be encoded as the single 3 byte instruction
    // asm.emitADD_Reg_Imm(SP, 8);
    // or as the following 2 1 byte instructions. There doesn't appear to be any
    // performance difference.
    adjustStack(WORDSIZE * 2, true);
  }

  @Override
  protected void emit_dup() {
    // This could be encoded as the 2 instructions totalling 4 bytes:
    // asm.emitMOV_Reg_RegInd(T0, SP);
    // asm.emitPUSH_Reg(T0);
    // However, there doesn't seem to be any performance difference to:
    asm.emitPUSH_RegInd(SP);
  }

  @Override
  protected void emit_dup_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_dup_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_dup2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_dup2_x1() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_dup2_x2() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(S1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
    asm.emitPUSH_Reg(S1);
    asm.emitPUSH_Reg(T1);
    asm.emitPUSH_Reg(S0);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_swap() {
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

  @Override
  protected void emit_iadd() {
    asm.emitPOP_Reg(T0);
    asm.emitADD_RegInd_Reg(SP, T0);
  }

  @Override
  protected void emit_isub() {
    asm.emitPOP_Reg(T0);
    asm.emitSUB_RegInd_Reg(SP, T0);
  }

  @Override
  protected void emit_imul() {
    asm.emitPOP_Reg(T0);
    asm.emitPOP_Reg(T1);
    asm.emitIMUL2_Reg_Reg(T0, T1);
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_idiv() {
    asm.emitPOP_Reg(ECX);  // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitPOP_Reg(EAX);  // EAX is dividend
    asm.emitCDQ();         // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);
    asm.emitPUSH_Reg(EAX); // push result
  }

  @Override
  protected void emit_irem() {
    asm.emitPOP_Reg(ECX);  // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
    asm.emitPOP_Reg(EAX);  // EAX is dividend
    asm.emitCDQ();         // sign extend EAX into EDX
    asm.emitIDIV_Reg_Reg(EAX, ECX);
    asm.emitPUSH_Reg(EDX); // push remainder
  }

  @Override
  protected void emit_ineg() {
    asm.emitNEG_RegInd(SP); // [SP] <- -[SP]
  }

  @Override
  protected void emit_ishl() {
    asm.emitPOP_Reg(ECX);
    asm.emitSHL_RegInd_Reg(SP, ECX);
  }

  @Override
  protected void emit_ishr() {
    asm.emitPOP_Reg(ECX);
    asm.emitSAR_RegInd_Reg(SP, ECX);
  }

  @Override
  protected void emit_iushr() {
    asm.emitPOP_Reg(ECX);
    asm.emitSHR_RegInd_Reg(SP, ECX);
  }

  @Override
  protected void emit_iand() {
    asm.emitPOP_Reg(T0);
    asm.emitAND_RegInd_Reg(SP, T0);
  }

  @Override
  protected void emit_ior() {
    asm.emitPOP_Reg(T0);
    asm.emitOR_RegInd_Reg(SP, T0);
  }

  @Override
  protected void emit_ixor() {
    asm.emitPOP_Reg(T0);
    asm.emitXOR_RegInd_Reg(SP, T0);
  }

  @Override
  protected void emit_iinc(int index, int val) {
    try {
      Offset offset = localOffset(index);
      asm.emitADD_RegDisp_Imm(ESP, offset, val);
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  /*
   * long ALU
   */

  @Override
  protected void emit_ladd() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);                       // the low half of one long
      asm.emitPOP_Reg(S0);                       // the high half
      asm.emitADD_RegInd_Reg(SP, T0);            // add low halves
      asm.emitADC_RegDisp_Reg(SP, ONE_SLOT, S0); // add high halves with carry
    } else {
      asm.emitPOP_Reg(T0);  // the long value
      asm.emitPOP_Reg(S0);  // throw away slot
      asm.emitADD_RegInd_Reg_Quad(SP, T0); // add values
    }
  }

  @Override
  protected void emit_lsub() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);                       // the low half of one long
      asm.emitPOP_Reg(S0);                       // the high half
      asm.emitSUB_RegInd_Reg(SP, T0);            // subtract low halves
      asm.emitSBB_RegDisp_Reg(SP, ONE_SLOT, S0); // subtract high halves with borrow
    } else {
      asm.emitPOP_Reg(T0);         // the long value
      adjustStack(WORDSIZE, true); // throw away slot
      asm.emitSUB_RegInd_Reg_Quad(SP, T0); // sub values
    }
  }

  @Override
  protected void emit_lmul() {
    if (VM.BuildFor64Addr) {
      asm.emitPOP_Reg(T0); // the long value
      asm.emitPOP_Reg(S0); // throw away slot
      asm.emitIMUL2_Reg_RegInd_Quad(T0, SP);
      asm.emitMOV_RegInd_Reg_Quad(SP, T0);
    } else {
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
      ForwardReference fr1 = asm.forwardJcc(NE);
      // EDX:EAX = 32bit multiply of multiplier and multiplicand low
      asm.emitMUL_Reg_Reg(EAX, EDX);
      // Jump over 64bit multiply
      ForwardReference fr2 = asm.forwardJMP();
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
  }

  @Override
  protected void emit_ldiv() {
    if (VM.BuildFor64Addr) {
      asm.emitPOP_Reg(ECX);  // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
      asm.emitPOP_Reg(EAX);  // throw away slot
      asm.emitPOP_Reg(EAX);  // EAX is dividend
      asm.emitCDO();         // sign extend EAX into EDX
      asm.emitIDIV_Reg_Reg_Quad(EAX, ECX);
      asm.emitPUSH_Reg(EAX); // push result
    } else {
      // (1) zero check
      asm.emitMOV_Reg_RegInd(T0, SP);
      asm.emitOR_Reg_RegDisp(T0, SP, ONE_SLOT);
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr1 = asm.forwardJcc(NE);
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
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
      asm.emitMOV_Reg_Abs(S0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));
      asm.emitCALL_RegDisp(S0, Entrypoints.sysLongDivideIPField.getOffset());
      // (5) pop space for arguments
      adjustStack(4 * WORDSIZE, true);
      // (6) restore RVM nonvolatiles
      for (int i = numNonVols - 1; i >= 0; i--) {
        asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
      }
      // (7) pop expression stack
      adjustStack(WORDSIZE * 4, true);
      // (8) push results
      asm.emitPUSH_Reg(T1);
      asm.emitPUSH_Reg(T0);
    }
  }

  @Override
  protected void emit_lrem() {
    if (VM.BuildFor64Addr) {
      asm.emitPOP_Reg(ECX);  // ECX is divisor; NOTE: can't use symbolic registers because of intel hardware requirements
      asm.emitPOP_Reg(EAX);  // throw away slot
      asm.emitPOP_Reg(EAX);  // EAX is dividend
      asm.emitCDO();         // sign extend EAX into EDX
      asm.emitIDIV_Reg_Reg_Quad(EAX, ECX);
      asm.emitPUSH_Reg(EDX); // push result
    } else {
      // (1) zero check
      asm.emitMOV_Reg_RegInd(T0, SP);
      asm.emitOR_Reg_RegDisp(T0, SP, ONE_SLOT);
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr1 = asm.forwardJcc(NE);
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO + RVM_TRAP_BASE);    // trap if divisor is 0
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
      asm.emitMOV_Reg_Abs(S0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));
      asm.emitCALL_RegDisp(S0, Entrypoints.sysLongRemainderIPField.getOffset());
      // (5) pop space for arguments
      adjustStack(4 * WORDSIZE, true);
      // (6) restore RVM nonvolatiles
      for (int i = numNonVols - 1; i >= 0; i--) {
        asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
      }
      // (7) pop expression stack
      adjustStack(WORDSIZE * 4, true);
      // (8) push results
      asm.emitPUSH_Reg(T1);
      asm.emitPUSH_Reg(T0);
    }
  }

  @Override
  protected void emit_lneg() {
    if (VM.BuildFor32Addr) {
      // The following is fewer instructions, but larger code
      // asm.emitNOT_RegDisp(SP, ONE_SLOT);
      // asm.emitNEG_RegInd(SP);
      // asm.emitSBB_RegDisp_Imm(SP, ONE_SLOT, -1);
      // this implementation is shorter and promotes ESP folding
      asm.emitPOP_Reg(T0); // T0 = low
      asm.emitNEG_Reg(T0); // T0 = -low
      asm.emitPOP_Reg(T1); // T1 = high
      asm.emitNOT_Reg(T1); // T1 = ~T1 (doesn't effect flags)
      asm.emitSBB_Reg_Imm(T1, -1); // T1 = high + 1 - CF
      asm.emitPUSH_Reg(T1);
      asm.emitPUSH_Reg(T0);
    } else {
      asm.emitNEG_RegInd_Quad(SP);
    }
  }

  @Override
  protected void emit_lshl() {
    if (VM.BuildFor32Addr) {
      if (SSE2_BASE) {
        asm.emitPOP_Reg(T0);                  // shift amount (6 bits)
        asm.emitMOVQ_Reg_RegInd(XMM1, SP);    // XMM1 <- [SP]
        asm.emitAND_Reg_Imm(T0, 0x3F);        // mask to 6bits
        asm.emitMOVD_Reg_Reg(XMM0, T0);      // XMM0 <- T0
        asm.emitPSLLQ_Reg_Reg(XMM1, XMM0);    // XMM1 <<= XMM0
        asm.emitMOVQ_RegInd_Reg(SP, XMM1);    // [SP] <- XMM1
      } else {
        if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
        if (VM.VerifyAssertions) VM._assert(ECX != T1);
        asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
        asm.emitPOP_Reg(T0);                   // pop low half
        asm.emitPOP_Reg(T1);                   // pop high half
        asm.emitAND_Reg_Imm(ECX, 0x3F);
        asm.emitCMP_Reg_Imm(ECX, 32);
        ForwardReference fr1 = asm.forwardJcc(LT);
        asm.emitMOV_Reg_Reg(T1, T0);  // high half = low half
        asm.emitXOR_Reg_Reg(T0, T0);  // low half = 0
        fr1.resolve(asm);
        asm.emitSHLD_Reg_Reg_Reg(T1, T0, ECX);  // shift high half, filling from low
        asm.emitSHL_Reg_Reg(T0, ECX);           // shift low half
        asm.emitPUSH_Reg(T1);                   // push high half
        asm.emitPUSH_Reg(T0);                   // push low half
      }
    } else {
      asm.emitPOP_Reg(ECX);
      asm.emitSHL_RegInd_Reg_Quad(SP, ECX);
    }
  }

  @Override
  protected void emit_lshr() {
    if (VM.BuildFor32Addr) {
      if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
      if (VM.VerifyAssertions) VM._assert(ECX != T1);
      asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
      asm.emitPOP_Reg(T0);                   // pop low half
      asm.emitPOP_Reg(T1);                   // pop high half
      asm.emitAND_Reg_Imm(ECX, 0x3F);
      asm.emitCMP_Reg_Imm(ECX, 32);
      ForwardReference fr1 = asm.forwardJcc(LT);
      asm.emitMOV_Reg_Reg(T0, T1);  // low half = high half
      asm.emitSAR_Reg_Imm(T1, 31);  // high half = sign extension of low half
      fr1.resolve(asm);
      asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);  // shift low half, filling from high
      asm.emitSAR_Reg_Reg(T1, ECX);           // shift high half
      asm.emitPUSH_Reg(T1);                   // push high half
      asm.emitPUSH_Reg(T0);                   // push low half
    } else {
      asm.emitPOP_Reg(ECX);
      asm.emitSAR_RegInd_Reg_Quad(SP, ECX);
    }
  }

  @Override
  protected void emit_lushr() {
    if (VM.BuildFor32Addr) {
      if (SSE2_BASE) {
        asm.emitPOP_Reg(T0);                  // shift amount (6 bits)
        asm.emitMOVQ_Reg_RegInd(XMM1, SP);    // XMM1 <- [SP]
        asm.emitAND_Reg_Imm(T0, 0x3F);        // mask to 6bits
        asm.emitMOVD_Reg_Reg(XMM0, T0);      // XMM0 <- T0
        asm.emitPSRLQ_Reg_Reg(XMM1, XMM0);    // XMM1 >>>= XMM0
        asm.emitMOVQ_RegInd_Reg(SP, XMM1);    // [SP] <- XMM1
      } else {
        if (VM.VerifyAssertions) VM._assert(ECX != T0); // ECX is constrained to be the shift count
        if (VM.VerifyAssertions) VM._assert(ECX != T1);
        asm.emitPOP_Reg(ECX);                  // shift amount (6 bits)
        asm.emitPOP_Reg(T0);                   // pop low half
        asm.emitPOP_Reg(T1);                   // pop high half
        asm.emitAND_Reg_Imm(ECX, 0x3F);
        asm.emitCMP_Reg_Imm(ECX, 32);
        ForwardReference fr1 = asm.forwardJcc(LT);
        asm.emitMOV_Reg_Reg(T0, T1);  // low half = high half
        asm.emitXOR_Reg_Reg(T1, T1);  // high half = 0
        fr1.resolve(asm);
        asm.emitSHRD_Reg_Reg_Reg(T0, T1, ECX);  // shift low half, filling from high
        asm.emitSHR_Reg_Reg(T1, ECX);           // shift high half
        asm.emitPUSH_Reg(T1);                   // push high half
        asm.emitPUSH_Reg(T0);                   // push low half
      }
    } else {
      asm.emitPOP_Reg(ECX);
      asm.emitSHR_RegInd_Reg_Quad(SP, ECX);
    }
  }

  @Override
  protected void emit_land() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);        // low
      asm.emitPOP_Reg(S0);        // high
      asm.emitAND_RegInd_Reg(SP, T0);
      asm.emitAND_RegDisp_Reg(SP, ONE_SLOT, S0);
    } else {
      asm.emitPOP_Reg(T0); // long value
      asm.emitPOP_Reg(S0); // throw away slot
      asm.emitAND_RegInd_Reg_Quad(SP, T0);
    }
  }

  @Override
  protected void emit_lor() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);        // low
      asm.emitPOP_Reg(S0);        // high
      asm.emitOR_RegInd_Reg(SP, T0);
      asm.emitOR_RegDisp_Reg(SP, ONE_SLOT, S0);
    } else {
      asm.emitPOP_Reg(T0); // long value
      asm.emitPOP_Reg(S0); // throw away slot
      asm.emitOR_RegInd_Reg_Quad(SP, T0);
    }
  }

  /**
   * Emit code to implement the lxor bytecode
   */
  @Override
  protected void emit_lxor() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);        // low
      asm.emitPOP_Reg(S0);        // high
      asm.emitXOR_RegInd_Reg(SP, T0);
      asm.emitXOR_RegDisp_Reg(SP, ONE_SLOT, S0);
    } else {
      asm.emitPOP_Reg(T0); // long value
      asm.emitPOP_Reg(S0); // throw away slot
      asm.emitXOR_RegInd_Reg_Quad(SP, T0);
    }
  }

  /*
   * float ALU
   */

  @Override
  protected void emit_fadd() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);            // XMM0 = value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitADDSS_Reg_RegInd(XMM0, SP);            // XMM0 += value1
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);               // FPU reg. stack <- value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitFADD_Reg_RegInd(FP0, SP);              // FPU reg. stack += value1
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_fsub() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 = value1
      asm.emitSUBSS_Reg_RegInd(XMM0, SP);            // XMM0 -= value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);    // FPU reg. stack <- value1
      asm.emitFSUB_Reg_RegInd(FP0, SP);              // FPU reg. stack -= value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_fmul() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);            // XMM0 = value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitMULSS_Reg_RegInd(XMM0, SP);            // XMM0 *= value1
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);               // FPU reg. stack <- value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitFMUL_Reg_RegInd(FP0, SP);              // FPU reg. stack *= value1
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  /**
   * Emit code to implement the fdiv bytecode
   */
  @Override
  protected void emit_fdiv() {
    if (SSE2_BASE) {
      asm.emitMOVSS_Reg_RegDisp(XMM0, SP, ONE_SLOT); // XMM0 = value1
      asm.emitDIVSS_Reg_RegInd(XMM0, SP);            // XMM0 /= value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);            // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);    // FPU reg. stack <- value1
      asm.emitFDIV_Reg_RegInd(FP0, SP);              // FPU reg. stack /= value2
      adjustStack(WORDSIZE, true);                   // throw away slot
      asm.emitFSTP_RegInd_Reg(SP, FP0);              // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_frem() {
    asm.emitFLD_Reg_RegInd(FP0, SP);                 // FPU reg. stack <- value2, or a
    adjustStack(WORDSIZE, true);                     // throw away slot
    asm.emitFLD_Reg_RegInd(FP0, SP);                 // FPU reg. stack <- value1, or b
    int retryLabel = asm.getMachineCodeIndex();      // come here if partial remainder not complete
    asm.emitFPREM();                                 // FPU reg. stack <- a%b
    asm.emitFSTSW_Reg(EAX);                          // AX = fpsw
    asm.emitAND_Reg_Imm(EAX, 0x400);                 // is C2 set?
    asm.emitJCC_Cond_Imm(NE, retryLabel);            // if yes then goto retryLabel and continue to compute remainder
    asm.emitFSTP_RegInd_Reg(SP, FP0);                // POP FPU reg. stack (results) onto java stack
    asm.emitFFREEP_Reg(FP0);
  }

  @Override
  protected void emit_fneg() {
    // flip sign bit
    asm.emitXOR_RegInd_Imm(SP, 0x80000000);
  }

  /*
   * double ALU
   */

  @Override
  protected void emit_dadd() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      adjustStack(WORDSIZE * 2, true);                  // throw away long slot
      asm.emitADDSD_Reg_RegInd(XMM0, SP);               // XMM0 += value1
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);               // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // FPU reg. stack <- value2
      adjustStack(WORDSIZE * 2, true);                  // throw away long slot
      asm.emitFADD_Reg_RegInd_Quad(FP0, SP);            // FPU reg. stack += value1
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);            // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_dsub() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);   // XMM0 = value1
      asm.emitSUBSD_Reg_RegInd(XMM0, SP);                // XMM0 -= value2
      adjustStack(WORDSIZE * 2, true);                     // throw away long slot
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);               // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS);  // FPU reg. stack <- value1
      asm.emitFSUB_Reg_RegInd_Quad(FP0, SP);             // FPU reg. stack -= value2
      adjustStack(WORDSIZE * 2, true);                     // throw away long slot
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_dmul() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);               // XMM0 = value2
      adjustStack(WORDSIZE * 2, true);                  // throw away long slot
      asm.emitMULSD_Reg_RegInd(XMM0, SP);               // XMM0 *= value1
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);               // set result on stack
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // FPU reg. stack <- value2
      adjustStack(WORDSIZE * 2, true);                  // throw away long slot
      asm.emitFMUL_Reg_RegInd_Quad(FP0, SP);            // FPU reg. stack *= value1
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);            // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_ddiv() {
    if (SSE2_BASE) {
      asm.emitMOVSD_Reg_RegDisp(XMM0, SP, TWO_SLOTS);   // XMM0 = value1
      asm.emitDIVSD_Reg_RegInd(XMM0, SP);                // XMM0 /= value2
      adjustStack(WORDSIZE * 2, true);                     // throw away long slot
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);               // set result on stack
    } else {
      asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS);  // FPU reg. stack <- value1
      asm.emitFDIV_Reg_RegInd_Quad(FP0, SP);             // FPU reg. stack /= value2
      adjustStack(WORDSIZE * 2, true);                     // throw away long slot
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);             // POP FPU reg. stack onto stack
    }
  }

  @Override
  protected void emit_drem() {
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);            // FPU reg. stack <- value2, or a
    adjustStack(WORDSIZE * 2, true);                 // throw away slot
    asm.emitFLD_Reg_RegInd_Quad(FP0, SP);            // FPU reg. stack <- value1, or b
    int retryLabel = asm.getMachineCodeIndex();      // come here if partial remainder not complete
    asm.emitFPREM();                                 // FPU reg. stack <- a%b
    asm.emitFSTSW_Reg(EAX);                          // AX = fpsw
    asm.emitAND_Reg_Imm(EAX, 0x400);                 // is C2 set?
    asm.emitJCC_Cond_Imm(NE, retryLabel);            // if yes then goto retryLabel and continue to compute remainder
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);           // POP FPU reg. stack (results) onto java stack
    asm.emitFFREEP_Reg(FP0);                         // throw away top of stack
  }

  @Override
  protected void emit_dneg() {
    // flip sign bit
    asm.emitXOR_RegDisp_Imm_Byte(SP, Offset.fromIntZeroExtend(7), 0x80);
  }

  /*
   * conversion ops
   */

  @Override
  protected void emit_i2l() {
    if (VM.BuildFor32Addr) {
      asm.emitPUSH_RegInd(SP);                   // duplicate int on stack
      asm.emitSAR_RegDisp_Imm(SP, ONE_SLOT, 31); // sign extend as high word of long
    } else {
      asm.emitPOP_Reg(EAX);
      asm.emitCDQE();
      adjustStack(-WORDSIZE, true);
      asm.emitPUSH_Reg(EAX);
    }
  }

  @Override
  protected void emit_l2i() {
    asm.emitPOP_Reg(T0);         // long value
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    adjustStack(WORDSIZE, true); // throw away slot
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_i2f() {
    if (SSE2_BASE) {
      asm.emitCVTSI2SS_Reg_RegInd(XMM0, SP);
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFILD_Reg_RegInd(FP0, SP);
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    }
  }

  @Override
  protected void emit_i2d() {
    if (SSE2_BASE) {
      asm.emitCVTSI2SD_Reg_RegInd(XMM0, SP);
      adjustStack(-WORDSIZE, true); // grow the stack
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFILD_Reg_RegInd(FP0, SP);
      adjustStack(-WORDSIZE, true); // grow the stack
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    }
  }

  @Override
  protected void emit_l2f() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    adjustStack(WORDSIZE, true); // shrink the stack
    asm.emitFSTP_RegInd_Reg(SP, FP0);
  }

  @Override
  protected void emit_l2d() {
    asm.emitFILD_Reg_RegInd_Quad(FP0, SP);
    asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
  }

  @Override
  protected void emit_f2d() {
    if (SSE2_BASE) {
      asm.emitCVTSS2SD_Reg_RegInd(XMM0, SP);
      adjustStack(-WORDSIZE, true); // throw away slot
      asm.emitMOVSD_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);
      adjustStack(-WORDSIZE, true); // throw away slot
      asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
    }
  }

  @Override
  protected void emit_d2f() {
    if (SSE2_BASE) {
      asm.emitCVTSD2SS_Reg_RegInd(XMM0, SP);
      adjustStack(WORDSIZE, true); // throw away slot
      asm.emitMOVSS_RegInd_Reg(SP, XMM0);
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
      adjustStack(WORDSIZE, true); // throw away slot
      asm.emitFSTP_RegInd_Reg(SP, FP0);
    }
  }

  @Override
  protected void emit_f2i() {
    if (SSE2_BASE) {
      // Set up value in XMM0
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);
      asm.emitXOR_Reg_Reg(T1, T1);         // adjust = 0
      asm.emitXOR_Reg_Reg(T0, T0);         // result = 0
      // value cmp maxint
      asm.generateJTOCcmpFloat(XMM0, Entrypoints.maxintFloatField.getOffset());
      ForwardReference fr1 = asm.forwardJcc(PE); // if NaN goto fr1
      asm.emitSET_Cond_Reg_Byte(LGE, T1); // T1 = (value >= maxint) ? 1 : 0;
      asm.emitCVTTSS2SI_Reg_Reg(T0, XMM0); // T0 = (int)value, or 0x80000000 if value > maxint
      asm.emitSUB_Reg_Reg(T0, T1);         // T0 = T0 - T1, ie fix max int case
      fr1.resolve(asm);
      asm.emitMOV_RegInd_Reg(SP,T0);       // push result
    } else {
      // TODO: use x87 operations to do this conversion inline taking care of
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
      asm.emitMOV_Reg_Abs(S0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));
      asm.emitCALL_RegDisp(S0, Entrypoints.sysFloatToIntIPField.getOffset());
      // (4) pop argument;
      asm.emitPOP_Reg(S0);
      // (5) restore RVM nonvolatiles
      for (int i = numNonVols - 1; i >= 0; i--) {
        asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
      }
      // (6) put result on expression stack
      asm.emitMOV_RegInd_Reg(SP, T0);
    }
  }

  @Override
  protected void emit_f2l() {
    if (VM.BuildFor32Addr) {
      // TODO: SSE3 has a FISTTP instruction that stores the value with truncation
      // meaning the FPSCW can be left alone

      // Setup value into FP1
      asm.emitFLD_Reg_RegInd(FP0, SP);
      // Setup maxlong into FP0
      asm.emitFLD_Reg_Abs(FP0, Magic.getTocPointer().plus(Entrypoints.maxlongFloatField.getOffset()));
      // if value > maxlong or NaN goto fr1; FP0 = value
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);
      ForwardReference fr1 = asm.forwardJcc(LLE);
      // Normally the status and control word rounds numbers, but for conversion
      // to an integer/long value we want truncation. We therefore save the FPSCW,
      // set it to truncation perform operation then restore
      adjustStack(-WORDSIZE, true);                            // Grow the stack
      asm.emitFNSTCW_RegDisp(SP, MINUS_ONE_SLOT);              // [SP-4] = fpscw
      asm.emitMOVZX_Reg_RegDisp_Word(T0, SP, MINUS_ONE_SLOT);  // EAX = fpscw
      asm.emitOR_Reg_Imm(T0, 0xC00);                           // EAX = FPSCW in truncate mode
      asm.emitMOV_RegInd_Reg(SP, T0);                          // [SP] = new fpscw value
      asm.emitFLDCW_RegInd(SP);                                // Set FPSCW
      asm.emitFISTP_RegInd_Reg_Quad(SP, FP0);                  // Store 64bit long
      asm.emitFLDCW_RegDisp(SP, MINUS_ONE_SLOT);               // Restore FPSCW
      ForwardReference fr2 = asm.forwardJMP();
      fr1.resolve(asm);
      asm.emitFSTP_Reg_Reg(FP0, FP0);                          // pop FPU*1
      ForwardReference fr3 = asm.forwardJcc(PE); // if value == NaN goto fr3
      asm.emitMOV_RegInd_Imm(SP, 0x7FFFFFFF);
      asm.emitPUSH_Imm(-1);
      ForwardReference fr4 = asm.forwardJMP();
      fr3.resolve(asm);
      asm.emitMOV_RegInd_Imm(SP, 0);
      asm.emitPUSH_Imm(0);
      fr2.resolve(asm);
      fr4.resolve(asm);
    } else {
      // Set up value in XMM0
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);
      asm.emitXOR_Reg_Reg(T1, T1);         // adjust = 0
      asm.emitXOR_Reg_Reg(T0, T0);         // result = 0
      // value cmp maxlong
      asm.generateJTOCcmpFloat(XMM0, Entrypoints.maxlongFloatField.getOffset());
      ForwardReference fr1 = asm.forwardJcc(PE); // if NaN goto fr1
      asm.emitSET_Cond_Reg_Byte(LGE, T1); // T1 = (value >= maxint) ? 1 : 0;
      asm.emitCVTTSS2SI_Reg_Reg_Quad(T0, XMM0); // T0 = (int)value, or 0x80000000 if value > maxint
      asm.emitSUB_Reg_Reg_Quad(T0, T1);         // T0 = T0 - T1, ie fix max long case
      fr1.resolve(asm);
      asm.emitPUSH_Reg(T0);               // push result
    }
  }

  @Override
  protected void emit_d2i() {
    if (SSE2_BASE) {
      // Set up value in XMM0
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);
      adjustStack(2 * WORDSIZE, true);       // throw away slots
      asm.emitXOR_Reg_Reg(T1, T1);         // adjust = 0
      asm.emitXOR_Reg_Reg(T0, T0);         // result = 0
      // value cmp maxint
      asm.generateJTOCcmpDouble(XMM0, Entrypoints.maxintField.getOffset());
      ForwardReference fr1 = asm.forwardJcc(PE); // if NaN goto fr1
      asm.emitSET_Cond_Reg_Byte(LGE, T1); // T1 = (value >= maxint) ? 1 : 0;
      asm.emitCVTTSD2SI_Reg_Reg(T0, XMM0); // T0 = (int)value, or 0x80000000 if value > maxint
      asm.emitSUB_Reg_Reg(T0, T1);         // T0 = T0 - T1, ie fix max int case
      fr1.resolve(asm);
      asm.emitPUSH_Reg(T0);                // push result
    } else {
      // TODO: use x87 operations to do this conversion inline taking care of
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
      asm.emitMOV_Reg_Abs(S0, Magic.getTocPointer().plus(Entrypoints.the_boot_recordField.getOffset()));
      asm.emitCALL_RegDisp(S0, Entrypoints.sysDoubleToIntIPField.getOffset());
      // (4) pop arguments
      asm.emitPOP_Reg(S0);
      asm.emitPOP_Reg(S0);
      // (5) restore RVM nonvolatiles
      for (int i = numNonVols - 1; i >= 0; i--) {
        asm.emitPOP_Reg(NONVOLATILE_GPRS[i]);
      }
      // (6) put result on expression stack
      adjustStack(WORDSIZE, true); // throw away slot
      asm.emitMOV_RegInd_Reg(SP, T0);
    }
  }

  @Override
  protected void emit_d2l() {
    if (VM.BuildFor32Addr) {
      // TODO: SSE3 has a FISTTP instruction that stores the value with truncation
      // meaning the FPSCW can be left alone

      // Setup value into FP1
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
      // Setup maxlong into FP0
      asm.emitFLD_Reg_Abs_Quad(FP0, Magic.getTocPointer().plus(Entrypoints.maxlongField.getOffset()));
      // if value > maxlong or NaN goto fr1; FP0 = value
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);
      ForwardReference fr1 = asm.forwardJcc(LLE);
      // Normally the status and control word rounds numbers, but for conversion
      // to an integer/long value we want truncation. We therefore save the FPSCW,
      // set it to truncation perform operation then restore
      asm.emitFNSTCW_RegDisp(SP, MINUS_ONE_SLOT);              // [SP-4] = fpscw
      asm.emitMOVZX_Reg_RegDisp_Word(T0, SP, MINUS_ONE_SLOT);  // EAX = fpscw
      asm.emitOR_Reg_Imm(T0, 0xC00);                           // EAX = FPSCW in truncate mode
      asm.emitMOV_RegInd_Reg(SP, T0);                          // [SP] = new fpscw value
      asm.emitFLDCW_RegInd(SP);                                // Set FPSCW
      asm.emitFISTP_RegInd_Reg_Quad(SP, FP0);                  // Store 64bit long
      asm.emitFLDCW_RegDisp(SP, MINUS_ONE_SLOT);               // Restore FPSCW
      ForwardReference fr2 = asm.forwardJMP();
      fr1.resolve(asm);
      asm.emitFSTP_Reg_Reg(FP0, FP0);                          // pop FPU*1
      ForwardReference fr3 = asm.forwardJcc(PE); // if value == NaN goto fr3
      asm.emitMOV_RegDisp_Imm(SP, ONE_SLOT, 0x7FFFFFFF);
      asm.emitMOV_RegInd_Imm(SP, -1);
      ForwardReference fr4 = asm.forwardJMP();
      fr3.resolve(asm);
      asm.emitMOV_RegDisp_Imm(SP, ONE_SLOT, 0);
      asm.emitMOV_RegInd_Imm(SP, 0);
      fr2.resolve(asm);
      fr4.resolve(asm);
    } else {
      // Set up value in XMM0
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);
      asm.emitXOR_Reg_Reg(T1, T1);         // adjust = 0
      asm.emitXOR_Reg_Reg(T0, T0);         // result = 0
      // value cmp maxlong
      asm.generateJTOCcmpDouble(XMM0, Entrypoints.maxlongField.getOffset());
      ForwardReference fr1 = asm.forwardJcc(PE); // if NaN goto fr1
      asm.emitSET_Cond_Reg_Byte(LGE, T1); // T1 = (value >= maxlong) ? 1 : 0;
      asm.emitCVTTSD2SIQ_Reg_Reg_Quad(T0, XMM0); // T0 = (int)value, or 0x80...00 if value > maxlong
      asm.emitSUB_Reg_Reg_Quad(T0, T1);          // T0 = T0 - T1, ie fix max long case
      fr1.resolve(asm);
      asm.emitMOV_RegInd_Reg_Quad(SP, T0); // push result
    }
  }

  @Override
  protected void emit_i2b() {
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

  @Override
  protected void emit_i2c() {
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

  @Override
  protected void emit_i2s() {
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

  @Override
  protected void emit_regular_lcmp() {
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T0);                // (S0:T0) = (high half value2: low half value2)
      asm.emitPOP_Reg(S0);
      asm.emitPOP_Reg(T1);                // (..:T1) = (.. : low half of value1)
      asm.emitSUB_Reg_Reg(T1, T0);        // T1 = T1 - T0
      asm.emitPOP_Reg(T0);                // (T0:..) = (high half of value1 : ..)
      // NB pop does not alter the carry register
      asm.emitSBB_Reg_Reg(T0, S0);        // T0 = T0 - S0 - CF
      asm.emitOR_Reg_Reg(T1, T0);         // T1 = T1 | T0 updating ZF
      asm.emitSET_Cond_Reg_Byte(NE, T1);
      asm.emitMOVZX_Reg_Reg_Byte(T1, T1); // T1 = (value1 != value2) ? 1 : 0
      asm.emitSAR_Reg_Imm(T0, 31);        // T0 = (value1 < value2) ? -1 : 0
      asm.emitOR_Reg_Reg(T1, T0);         // T1 = T1 | T0
      asm.emitPUSH_Reg(T1);               // push result on stack
    } else {
      // using a shift in 64bits costs an extra byte in the opcode
      asm.emitPOP_Reg(T0);                // T0 is long value2
      adjustStack(WORDSIZE, true);        // throw away slot
      asm.emitPOP_Reg(T1);                // T1 is long value1
      adjustStack(WORDSIZE, true);        // throw away slot
      asm.emitCMP_Reg_Reg_Quad(T1, T0);   // 64bit compare
      asm.emitSET_Cond_Reg_Byte(LT, T0); // T0 = value1 < value2 ? 1 : 0
      asm.emitSET_Cond_Reg_Byte(GT, T1); // T1 = value1 > value2 ? 1 : 0
      asm.emitSUB_Reg_Reg_Byte(T1, T0);   // T1 = (value1 > value2 ? 1 : 0) - (value1 < value2 ? 1 : 0)
      asm.emitMOVSX_Reg_Reg_Byte(T1, T1); // Fix sign extension
      asm.emitPUSH_Reg(T1);               // push result on stack
    }
  }

  @Override
  protected void emit_regular_DFcmpGL(boolean single, boolean unorderedGT) {
    if (SSE2_BASE) {
      if (single) {
        asm.emitMOVSS_Reg_RegInd(XMM0, SP);               // XMM0 = value2
        asm.emitMOVSS_Reg_RegDisp(XMM1, SP, ONE_SLOT);    // XMM1 = value1
        adjustStack(WORDSIZE * 2, true);                  // throw away slots
      } else {
        asm.emitMOVSD_Reg_RegInd(XMM0, SP);              // XMM0 = value2
        asm.emitMOVSD_Reg_RegDisp(XMM1, SP, TWO_SLOTS);  // XMM1 = value1
        adjustStack(WORDSIZE * 4, true);                  // throw away slots
      }
    } else {
      if (single) {
        asm.emitFLD_Reg_RegInd(FP0, SP);                  // Setup value2 into FP1,
        asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);       // value1 into FP0
        adjustStack(WORDSIZE * 2, true);                  // throw away slots
      } else {
        asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // Setup value2 into FP1,
        asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // value1 into FP0
        adjustStack(WORDSIZE * 4, true);                  // throw away slots
      }
    }
    if (unorderedGT) {
      asm.emitMOV_Reg_Imm(T0, 1);                         // result/T0 = 1 (high bits are 0)
    } else {
      asm.emitXOR_Reg_Reg(T0, T0);                        // clear high bits of result
    }
    if (SSE2_BASE) {
      if (single) {
        asm.emitUCOMISS_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
      } else {
        asm.emitUCOMISD_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
      }
    } else {
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                  // compare and pop FPU *1
    }
    ForwardReference fr1 = null;
    if (unorderedGT) {
     fr1 = asm.forwardJcc(PE);                  // if unordered goto push result (1)
    }
    asm.emitSET_Cond_Reg_Byte(LGT, T0);         // T0 = XMM0 > XMM1 ? 1 : 0
    asm.emitSBB_Reg_Imm(T0, 0);                           // T0 -= XMM0 < or unordered XMM1 ? 1 : 0
    if (unorderedGT) {
      fr1.resolve(asm);
    }
    asm.emitPUSH_Reg(T0);                                 // push result on stack
    if (!SSE2_BASE) {
      asm.emitFSTP_Reg_Reg(FP0, FP0);                     // pop FPU*1
    }
  }

  /*
   * branching
   */

  /**
   * @param bc the branch condition
   * @return assembler constant equivalent for the branch condition
   */
  @Pure
  private byte mapCondition(BranchCondition bc) {
    switch (bc) {
      case EQ: return EQ;
      case NE: return NE;
      case LT: return LT;
      case GE: return GE;
      case GT: return GT;
      case LE: return LE;
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        return -1;
    }
  }

  @Override
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {2})
  protected void emit_lcmp_if(int bTarget, BranchCondition bc) {
    if (VM.BuildFor32Addr) {
      if (bc == BranchCondition.LE || bc == BranchCondition.GT) {
        // flip operands in these cases
        if (bc == BranchCondition.LE) {
          bc = BranchCondition.GE;
        } else {
          bc = BranchCondition.LT;
        }
        asm.emitPOP_Reg(T1);                // (T0:T1) = (high half value2: low half value2)
        asm.emitPOP_Reg(T0);
        asm.emitPOP_Reg(S0);                // (..:S0) = (.. : low half of value1)
        asm.emitSUB_Reg_Reg(T1, S0);        // T1 = T1 - S0
        asm.emitPOP_Reg(S0);                // (S0:..) = (high half of value1 : ..)
        // NB pop does not alter the carry register
        asm.emitSBB_Reg_Reg(T0, S0);        // T0 = T0 - S0 - CF
      } else {
        asm.emitPOP_Reg(T0);                // (S0:T0) = (high half value2: low half value2)
        asm.emitPOP_Reg(S0);
        asm.emitPOP_Reg(T1);                // (..:T1) = (.. : low half of value1)
        asm.emitSUB_Reg_Reg(T1, T0);        // T1 = T1 - T0
        asm.emitPOP_Reg(T0);                // (T0:..) = (high half of value1 : ..)
        // NB pop does not alter the carry register
        asm.emitSBB_Reg_Reg(T0, S0);        // T0 = T0 - S0 - CF
        if (bc == BranchCondition.EQ || bc == BranchCondition.NE) {
          asm.emitOR_Reg_Reg(T1, T0);       // T1 = T1 | T0 updating ZF
        }
      }
    } else {
      asm.emitPOP_Reg(T0);                // T0 is long value2
      adjustStack(WORDSIZE, true);        // throw away slot
      asm.emitPOP_Reg(T1);                // T1 is long value1
      adjustStack(WORDSIZE, true);        // throw away slot
      asm.emitCMP_Reg_Reg_Quad(T1, T0);   // 64bit compare
    }
    genCondBranch(mapCondition(bc), bTarget);
  }

  @Override
  protected void emit_DFcmpGL_if(boolean single, boolean unorderedGT, int bTarget, BranchCondition bc) {
    if (SSE2_BASE) {
      if (single) {
        asm.emitMOVSS_Reg_RegInd(XMM0, SP);               // XMM0 = value2
        asm.emitMOVSS_Reg_RegDisp(XMM1, SP, ONE_SLOT);    // XMM1 = value1
        adjustStack(WORDSIZE * 2, true);                    // throw away slots
      } else {
        asm.emitMOVSD_Reg_RegInd(XMM0, SP);              // XMM0 = value2
        asm.emitMOVSD_Reg_RegDisp(XMM1, SP, TWO_SLOTS);  // XMM1 = value1
        adjustStack(WORDSIZE * 4, true);                    // throw away slots
      }
    } else {
      if (single) {
        asm.emitFLD_Reg_RegInd(FP0, SP);                  // Setup value2 into FP1,
        asm.emitFLD_Reg_RegDisp(FP0, SP, ONE_SLOT);       // value1 into FP0
        adjustStack(WORDSIZE * 2, true);                    // throw away slots
      } else {
        asm.emitFLD_Reg_RegInd_Quad(FP0, SP);             // Setup value2 into FP1,
        asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, TWO_SLOTS); // value1 into FP0
        adjustStack(WORDSIZE * 4, true);                    // throw away slots
      }
    }
    if (SSE2_BASE) {
      if (single) {
        asm.emitUCOMISS_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
      } else {
        asm.emitUCOMISD_Reg_Reg(XMM1, XMM0);              // compare value1 and value2
      }
    } else {
      asm.emitFUCOMIP_Reg_Reg(FP0, FP1);                  // compare and pop FPU *1
      asm.emitFSTP_Reg_Reg(FP0, FP0);                     // pop FPU*1
    }
    byte asm_bc = -1;
    boolean unordered_taken = false;
    switch (bc) {
      case EQ:
        asm_bc = EQ;
        unordered_taken = false;
        break;
      case NE:
        asm_bc = NE;
        unordered_taken = true;
        break;
      case LT:
        asm_bc = LLT;
        unordered_taken = !unorderedGT;
        break;
      case GE:
        asm_bc = LGE;
        unordered_taken = unorderedGT;
        break;
      case GT:
        asm_bc = LGT;
        unordered_taken = unorderedGT;
        break;
      case LE:
        asm_bc = LLE;
        unordered_taken = !unorderedGT;
        break;
      default:
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
      // Allocate two counters: taken and not taken
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;
      if (!unordered_taken) {
        ForwardReference notTaken1 = asm.forwardJcc(PE);
        ForwardReference notTaken2 = asm.forwardJcc(asm.flipCode(asm_bc));
        // Increment taken counter & jump to target
        incEdgeCounter(T1, null, entry + EdgeCounts.TAKEN);
        asm.emitJMP_ImmOrLabel(mTarget, bTarget);
        // Increment not taken counter
        notTaken1.resolve(asm);
        notTaken2.resolve(asm);
        incEdgeCounter(T1, null, entry + EdgeCounts.NOT_TAKEN);
      } else {
        ForwardReference taken1 = asm.forwardJcc(PE);
        ForwardReference taken2 = asm.forwardJcc(asm_bc);
        // Increment taken counter & jump to target
        incEdgeCounter(T1, null, entry + EdgeCounts.NOT_TAKEN);
        ForwardReference notTaken = asm.forwardJMP();
        // Increment taken counter
        taken1.resolve(asm);
        taken2.resolve(asm);
        incEdgeCounter(T1, null, entry + EdgeCounts.TAKEN);
        asm.emitJMP_ImmOrLabel(mTarget, bTarget);
        notTaken.resolve(asm);
      }
    } else {
      if (unordered_taken) {
        asm.emitJCC_Cond_ImmOrLabel(PE, mTarget, bTarget);
        asm.emitJCC_Cond_ImmOrLabel(asm_bc, mTarget, bTarget);
      } else {
        ForwardReference notTaken = asm.forwardJcc(PE);
        asm.emitJCC_Cond_ImmOrLabel(asm_bc, mTarget, bTarget);
        notTaken.resolve(asm);
      }
    }
  }

  @Override
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {2})
  protected void emit_if(int bTarget, BranchCondition bc) {
    asm.emitPOP_Reg(T0);
    asm.emitTEST_Reg_Reg(T0, T0);
    genCondBranch(mapCondition(bc), bTarget);
  }

  @Override
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {2})
  protected void emit_if_icmp(int bTarget, BranchCondition bc) {
    asm.emitPOP_Reg(T1);
    asm.emitPOP_Reg(T0);
    asm.emitCMP_Reg_Reg(T0, T1);
    genCondBranch(mapCondition(bc), bTarget);
  }

  @Override
  protected void emit_if_acmpeq(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    if (VM.BuildFor32Addr) {
      asm.emitCMP_Reg_Reg(T0, S0);
    } else {
      asm.emitCMP_Reg_Reg_Quad(T0, S0);
    }
    genCondBranch(EQ, bTarget);
  }

  @Override
  protected void emit_if_acmpne(int bTarget) {
    asm.emitPOP_Reg(S0);
    asm.emitPOP_Reg(T0);
    if (VM.BuildFor32Addr) {
      asm.emitCMP_Reg_Reg(T0, S0);
    } else {
      asm.emitCMP_Reg_Reg_Quad(T0, S0);
    }
    genCondBranch(NE, bTarget);
  }

  @Override
  protected void emit_ifnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    if (VM.BuildFor32Addr) {
      asm.emitTEST_Reg_Reg(T0, T0);
    } else {
      asm.emitTEST_Reg_Reg_Quad(T0, T0);
    }
    genCondBranch(EQ, bTarget);
  }

  @Override
  protected void emit_ifnonnull(int bTarget) {
    asm.emitPOP_Reg(T0);
    if (VM.BuildFor32Addr) {
      asm.emitTEST_Reg_Reg(T0, T0);
    } else {
      asm.emitTEST_Reg_Reg_Quad(T0, T0);
    }
    genCondBranch(NE, bTarget);
  }

  @Override
  protected void emit_goto(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }

  @Override
  protected void emit_jsr(int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    asm.emitCALL_ImmOrLabel(mTarget, bTarget);
  }

  @Override
  protected void emit_ret(int index) {
    try {
      Offset offset = localOffset(index);
      // Can be:
      // asm.emitJMP_RegDisp(ESP, offset);
      // but this will cause call-return branch prediction pairing to fail
      asm.emitPUSH_RegDisp(ESP, offset);
      asm.emitRET();
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_tableswitch(int defaultval, int low, int high) {
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    int n = high - low + 1;                       // n = number of normal cases (0..n-1)
    asm.emitPOP_Reg(T1);                          // T1 is index of desired case
    asm.emitSUB_Reg_Imm(T1, low);                 // relativize T1
    asm.emitCMP_Reg_Imm(T1, n);                   // 0 <= relative index < n

    if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
      int firstCounter = edgeCounterIdx;
      edgeCounterIdx += (n + 1);

      // Jump around code for default case
      ForwardReference fr = asm.forwardJcc(LLT);
      incEdgeCounter(S0, null, firstCounter + n);
      asm.emitJMP_ImmOrLabel(mTarget, bTarget);
      fr.resolve(asm);

      // Increment counter for the appropriate case
      incEdgeCounter(S0, T1, firstCounter);
    } else {
      asm.emitJCC_Cond_ImmOrLabel(LGE, mTarget, bTarget);   // if not, goto default case
    }

    // T0 = EIP at start of method
    asm.emitMETHODSTART_Reg(T0);
    asm.emitTableswitchCode(T0, T1);
    // Emit data for the tableswitch, i.e. the addresses that will be
    // loaded for the cases
    for (int i = 0; i < n; i++) {
      int offset = bcodes.getTableSwitchOffset(i);
      bTarget = biStart + offset;
      mTarget = bytecodeMap[bTarget];
      asm.emitOFFSET_Imm_ImmOrLabel(i, mTarget, bTarget);
    }
    bcodes.skipTableSwitchOffsets(n);
  }

  /**
   * Emit code to implement the lookupswitch bytecode.
   * Uses linear search, one could use a binary search tree instead,
   * but this is the baseline compiler, so don't worry about it.
   *
   * @param defaultval bcIndex of the default target
   * @param npairs number of pairs in the lookup switch
   */
  @Override
  protected void emit_lookupswitch(int defaultval, int npairs) {
    asm.emitPOP_Reg(T0);
    for (int i = 0; i < npairs; i++) {
      int match = bcodes.getLookupSwitchValue(i);
      asm.emitCMP_Reg_Imm(T0, match);
      int offset = bcodes.getLookupSwitchOffset(i);
      int bTarget = biStart + offset;
      int mTarget = bytecodeMap[bTarget];
      if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
        // Flip conditions so we can jump over the increment of the taken counter.
        ForwardReference fr = asm.forwardJcc(NE);
        incEdgeCounter(S0, null, edgeCounterIdx++);
        asm.emitJMP_ImmOrLabel(mTarget, bTarget);
        fr.resolve(asm);
      } else {
        asm.emitJCC_Cond_ImmOrLabel(EQ, mTarget, bTarget);
      }
    }
    bcodes.skipLookupSwitchPairs(npairs);
    int bTarget = biStart + defaultval;
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
      incEdgeCounter(S0, null, edgeCounterIdx++); // increment default counter
    }
    asm.emitJMP_ImmOrLabel(mTarget, bTarget);
  }

  /*
   * returns (from function; NOT ret)
   */

  @Override
  protected void emit_ireturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    if (VM.BuildFor64Addr) {
      asm.emitAND_Reg_Reg(T0, T0); // clear MSBs
    }
    genEpilogue(WORDSIZE, WORDSIZE);
  }

  @Override
  protected void emit_lreturn() {
    if (method.isSynchronized()) genMonitorExit();
    if (VM.BuildFor32Addr) {
      asm.emitPOP_Reg(T1); // low half
      asm.emitPOP_Reg(T0); // high half
      genEpilogue(2 * WORDSIZE, 2 * WORDSIZE);
    } else {
      asm.emitPOP_Reg(T0);
      genEpilogue(2 * WORDSIZE, WORDSIZE);
    }
  }

  @Override
  protected void emit_freturn() {
    if (method.isSynchronized()) genMonitorExit();
    if (SSE2_FULL) {
      asm.emitMOVSS_Reg_RegInd(XMM0, SP);
    } else {
      asm.emitFLD_Reg_RegInd(FP0, SP);
    }
    genEpilogue(WORDSIZE, 0);
  }

  @Override
  protected void emit_dreturn() {
    if (method.isSynchronized()) genMonitorExit();
    if (SSE2_FULL) {
      asm.emitMOVSD_Reg_RegInd(XMM0, SP);
    } else {
      asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
    }
    genEpilogue(2 * WORDSIZE, 0);
  }

  @Override
  protected void emit_areturn() {
    if (method.isSynchronized()) genMonitorExit();
    asm.emitPOP_Reg(T0);
    genEpilogue(WORDSIZE, WORDSIZE);
  }

  @Override
  protected void emit_return() {
    if (method.isSynchronized()) genMonitorExit();
    genEpilogue(0, 0);
  }

  /*
   * field access
   */

  @Override
  protected void emit_unresolved_getstatic(FieldReference fieldRef) {
    emitDynamicLinkingSequence(asm, T0, fieldRef, true);
    if (NEEDS_OBJECT_GETSTATIC_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType()) {
      Barriers.compileGetstaticBarrier(asm, T0, fieldRef.getId());
      return;
    }
    if (fieldRef.getSize() <= BYTES_IN_INT) {
      // get static field - [SP--] = [T0<<0+JTOC]
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_RegDisp(T0, Magic.getTocPointer().toWord().toOffset());
      } else {
        asm.generateJTOCloadInt(T0, T0);
        asm.emitPUSH_Reg(T0);
      }
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor32Addr) {
        // JMM: field could be volatile so we need to guarantee atomic access
        if (SSE2_BASE) {
          asm.emitMOVQ_Reg_RegDisp(XMM0, T0, Magic.getTocPointer().toWord().toOffset());
          adjustStack(-2 * WORDSIZE, false);
          asm.emitMOVQ_RegInd_Reg(SP, XMM0);
        } else {
          asm.emitFLD_Reg_RegDisp_Quad(FP0, T0, Magic.getTocPointer().toWord().toOffset());
          adjustStack(-2 * WORDSIZE, false);
          asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
        }
      } else {
        if (fieldRef.getNumberOfStackSlots() != 1) {
          adjustStack(-WORDSIZE, true);
        }
        asm.generateJTOCpush(T0);
      }
    }
  }

  @Override
  protected void emit_resolved_getstatic(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();
    if (NEEDS_OBJECT_GETSTATIC_BARRIER && !fieldRef.getFieldContentsType().isPrimitiveType() && !field.isUntraced()) {
      Barriers.compileGetstaticBarrierImm(asm, fieldOffset, fieldRef.getId());
      return;
    }
    if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_Abs(Magic.getTocPointer().plus(fieldOffset));
      } else {
        asm.generateJTOCloadInt(T0, fieldOffset);
        asm.emitPUSH_Reg(T0);
      }
    } else { // field is two words (double or long)
      if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
      if (VM.BuildFor32Addr) {
        // JMM: we need to guarantee atomic access for volatile fields
        if (field.isVolatile()) {
          if (SSE2_BASE) {
            asm.emitMOVQ_Reg_Abs(XMM0, Magic.getTocPointer().plus(fieldOffset));
            adjustStack(-2 * WORDSIZE, true);
            asm.emitMOVQ_RegInd_Reg(SP, XMM0);
          } else {
            asm.emitFLD_Reg_Abs_Quad(FP0, Magic.getTocPointer().plus(fieldOffset));
            adjustStack(-2 * WORDSIZE, true);
            asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
          }
        } else {
          asm.emitPUSH_Abs(Magic.getTocPointer().plus(fieldOffset).plus(WORDSIZE)); // get high part
          asm.emitPUSH_Abs(Magic.getTocPointer().plus(fieldOffset));                // get low part
        }
      } else {
        if (fieldRef.getNumberOfStackSlots() != 1) {
          adjustStack(-WORDSIZE, true);
        }
        asm.generateJTOCpush(fieldOffset);
      }
    }
  }

  @Override
  protected void emit_unresolved_putstatic(FieldReference fieldRef) {
    emitDynamicLinkingSequence(asm, T0, fieldRef, true);
    if (NEEDS_OBJECT_PUTSTATIC_BARRIER && fieldRef.getFieldContentsType().isReferenceType()) {
      Barriers.compilePutstaticBarrier(asm, T0, fieldRef.getId());
    } else {
      if (fieldRef.getSize() <= BYTES_IN_INT) { // field is one word
        if (VM.BuildFor32Addr) {
          asm.emitPOP_RegDisp(T0, Magic.getTocPointer().toWord().toOffset());
        } else {
          asm.emitPOP_Reg(T1);
          asm.generateJTOCstoreInt(T0, T1);
        }
      } else { // field is two words (double or long)
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor32Addr) {
          // JMM: field could be volatile so we need to guarantee atomic access
          if (SSE2_BASE) {
            asm.emitMOVQ_Reg_RegInd(XMM0, SP);
            asm.emitMOVQ_RegDisp_Reg(T0, Magic.getTocPointer().toWord().toOffset(), XMM0);
          } else {
            asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
            asm.emitFSTP_RegDisp_Reg_Quad(T0, Magic.getTocPointer().toWord().toOffset(), FP0);
          }
          adjustStack(2 * WORDSIZE, false);
        } else {
          asm.generateJTOCpop(T0);
          if (fieldRef.getNumberOfStackSlots() != 1) {
            adjustStack(WORDSIZE, true);
          }
        }
      }
    }
    // The field may be volatile
    asm.emitMFENCE();
  }

  @Override
  protected void emit_resolved_putstatic(FieldReference fieldRef) {
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();
    if (NEEDS_OBJECT_PUTSTATIC_BARRIER && field.isReferenceType() && !field.isUntraced()) {
      Barriers.compilePutstaticBarrierImm(asm, fieldOffset, fieldRef.getId());
    } else {
      if (field.getSize() <= BYTES_IN_INT) { // field is one word
        if (VM.BuildFor32Addr) {
          asm.emitPOP_Abs(Magic.getTocPointer().plus(fieldOffset));
        } else {
          asm.emitPOP_Reg(T1);
          asm.generateJTOCstoreInt(fieldOffset, T1);
        }
      } else { // field is two words (double or long)
        if (VM.VerifyAssertions) VM._assert(fieldRef.getSize() == BYTES_IN_LONG);
        if (VM.BuildFor32Addr) {
          // JMM: we need to guarantee atomic access for volatile fields
          if (field.isVolatile()) {
            if (SSE2_BASE) {
              asm.emitMOVQ_Reg_RegInd(XMM0, SP);
              asm.emitMOVQ_Abs_Reg(Magic.getTocPointer().plus(fieldOffset), XMM0);
            } else {
              asm.emitFLD_Reg_RegInd_Quad(FP0, SP);
              asm.emitFSTP_Abs_Reg_Quad(Magic.getTocPointer().plus(fieldOffset), FP0);
            }
            adjustStack(2 * WORDSIZE, false);
          } else {
            asm.emitPOP_Abs(Magic.getTocPointer().plus(fieldOffset));          // store low part
            asm.emitPOP_Abs(Magic.getTocPointer().plus(fieldOffset).plus(WORDSIZE)); // store high part
          }
        } else {
          asm.generateJTOCpop(fieldOffset);
          if (fieldRef.getNumberOfStackSlots() != 1) {
            adjustStack(WORDSIZE, true);
          }
        }
      }
    }
    if (field.isVolatile()) {
      asm.emitMFENCE();
    }
  }

  @Override
  protected void emit_unresolved_getfield(FieldReference fieldRef) {
    TypeReference fieldType = fieldRef.getFieldContentsType();
    emitDynamicLinkingSequence(asm, T0, fieldRef, true);
    if (fieldType.isReferenceType()) {
      // 32/64bit reference load
      if (NEEDS_OBJECT_GETFIELD_BARRIER) {
        Barriers.compileGetfieldBarrier(asm, T0, fieldRef.getId());
      } else {
        asm.emitPOP_Reg(S0);                                  // S0 is object reference
        asm.emitPUSH_RegIdx(S0, T0, BYTE, NO_SLOT); // place field value on stack
      }
    } else if (fieldType.isBooleanType()) {
      // 8bit unsigned load
      asm.emitPOP_Reg(S0);                                                // S0 is object reference
      asm.emitMOVZX_Reg_RegIdx_Byte(T1, S0, T0, BYTE, NO_SLOT); // T1 is field value
      asm.emitPUSH_Reg(T1);                                               // place value on stack
    } else if (fieldType.isByteType()) {
      // 8bit signed load
      asm.emitPOP_Reg(S0);                                                // S0 is object reference
      asm.emitMOVSX_Reg_RegIdx_Byte(T1, S0, T0, BYTE, NO_SLOT); // T1 is field value
      asm.emitPUSH_Reg(T1);                                               // place value on stack
    } else if (fieldType.isShortType()) {
      // 16bit signed load
      asm.emitPOP_Reg(S0);                                                // S0 is object reference
      asm.emitMOVSX_Reg_RegIdx_Word(T1, S0, T0, BYTE, NO_SLOT); // T1 is field value
      asm.emitPUSH_Reg(T1);                                               // place value on stack
    } else if (fieldType.isCharType()) {
      // 16bit unsigned load
      asm.emitPOP_Reg(S0);                                                // S0 is object reference
      asm.emitMOVZX_Reg_RegIdx_Word(T1, S0, T0, BYTE, NO_SLOT); // T1 is field value
      asm.emitPUSH_Reg(T1);                                               // place value on stack
    } else if (fieldType.isIntType() || fieldType.isFloatType() ||
               (VM.BuildFor32Addr && fieldType.isWordLikeType())) {
      // 32bit load
      asm.emitPOP_Reg(S0);                                  // S0 is object reference
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_RegIdx(S0, T0, BYTE, NO_SLOT); // place field value on stack
      } else {
        asm.emitMOV_Reg_RegIdx(T1, S0, T0, BYTE, NO_SLOT); // T1 is field value
        asm.emitPUSH_Reg(T1);  // place value on stack
      }
    } else {
      // 64bit load
      if (VM.VerifyAssertions) {
        VM._assert(fieldType.isLongType() || fieldType.isDoubleType() ||
                   (VM.BuildFor64Addr && fieldType.isWordLikeType()));
      }
      asm.emitPOP_Reg(T1);           // T1 is object reference
      // NB it's unknown whether the field is volatile, so it is necessary to
      // emit instruction sequences that provide atomic access.
      if (VM.BuildFor32Addr) {
        // NB this is a 64bit copy from memory to the stack so implement
        // as a slightly optimized Intel memory copy using the FPU
        if (SSE2_BASE) {
          asm.emitMOVQ_Reg_RegIdx(XMM0, T1, T0, BYTE, NO_SLOT); // XMM0 is field value
          adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
          asm.emitMOVQ_RegInd_Reg(SP, XMM0); // place value on stack
        } else {
          asm.emitFLD_Reg_RegIdx_Quad(FP0, T1, T0, BYTE, NO_SLOT); // FP0 is field value
          adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
          asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // place value on stack
        }
      } else {
        if (!fieldType.isWordLikeType()) {
          // Note that the stack musn't be clobbered at this point.
          // If the stack was clobbered and a NPE occurred and a
          // garbage collection was triggered in exception handling
          // (e.g. in a gcstress build), the stack state would not be
          // as the GC maps expect which would lead to a failure with
          // a "bad GC map".
          adjustStack(-WORDSIZE, false); // add empty slot
        }
        asm.emitPUSH_RegIdx(T1, T0, BYTE, NO_SLOT); // place value on stack
      }
    }
  }

  @Override
  protected void emit_resolved_getfield(FieldReference fieldRef) {
    TypeReference fieldType = fieldRef.getFieldContentsType();
    RVMField field = fieldRef.peekResolvedField();
    Offset fieldOffset = field.getOffset();
    if (field.isReferenceType()) {
      // 32/64bit reference load
      if (NEEDS_OBJECT_GETFIELD_BARRIER && !field.isUntraced()) {
        Barriers.compileGetfieldBarrierImm(asm, fieldOffset, fieldRef.getId());
      } else {
        asm.emitPOP_Reg(T0);                   // T0 is object reference
        asm.emitPUSH_RegDisp(T0, fieldOffset); // place field value on stack
      }
    } else if (fieldType.isBooleanType()) {
      // 8bit unsigned load
      asm.emitPOP_Reg(S0);                                 // S0 is object reference
      asm.emitMOVZX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
      asm.emitPUSH_Reg(T0);                                // place value on stack
    } else if (fieldType.isByteType()) {
      // 8bit signed load
      asm.emitPOP_Reg(S0);                                 // S0 is object reference
      asm.emitMOVSX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
      asm.emitPUSH_Reg(T0);                                // place value on stack
    } else if (fieldType.isShortType()) {
      // 16bit signed load
      asm.emitPOP_Reg(S0);                                 // S0 is object reference
      asm.emitMOVSX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
      asm.emitPUSH_Reg(T0);                                // place value on stack
    } else if (fieldType.isCharType()) {
      // 16bit unsigned load
      asm.emitPOP_Reg(S0);                                 // S0 is object reference
      asm.emitMOVZX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
      asm.emitPUSH_Reg(T0);                                // place value on stack
    } else if (fieldType.isIntType() || fieldType.isFloatType() ||
               (VM.BuildFor32Addr && fieldType.isWordLikeType())) {
      // 32bit load
      asm.emitPOP_Reg(S0);                   // S0 is object reference
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_RegDisp(S0, fieldOffset); // place value on stack
      } else {
        asm.emitMOV_Reg_RegDisp(T0, S0, fieldOffset); // T0 is field value
        asm.emitPUSH_Reg(T0);  // place value on stack
      }
    } else {
      // 64bit load
      if (VM.VerifyAssertions) {
        VM._assert(fieldType.isLongType() || fieldType.isDoubleType() ||
                   (VM.BuildFor64Addr && fieldType.isWordLikeType()));
      }
      asm.emitPOP_Reg(T0); // T0 is object reference
      if (VM.BuildFor32Addr && field.isVolatile()) {
        // NB this is a 64bit copy from memory to the stack so implement
        // as a slightly optimized Intel memory copy using the FPU
        if (SSE2_BASE) {
          asm.emitMOVQ_Reg_RegDisp(XMM0, T0, fieldOffset); // XMM0 is field value
          adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
          asm.emitMOVQ_RegInd_Reg(SP, XMM0); // replace reference with value on stack
        } else {
          asm.emitFLD_Reg_RegDisp_Quad(FP0, T0, fieldOffset); // FP0 is field value
          adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
          asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // replace reference with value on stack
        }
      } else if (VM.BuildFor32Addr && !field.isVolatile()) {
        asm.emitPUSH_RegDisp(T0, fieldOffset.plus(ONE_SLOT)); // place high half on stack
        asm.emitPUSH_RegDisp(T0, fieldOffset);                // place low half on stack
      } else {
        if (!fieldType.isWordLikeType()) {
          adjustStack(-WORDSIZE, true); // add empty slot
        }
        asm.emitPUSH_RegDisp(T0, fieldOffset); // place value on stack
      }
    }
  }

  /**
   * Emits code to load a reference local variable and then perform a field load
   * @param index the local index to load
   * @param fieldRef the referenced field
   */
  @Override
  protected void emit_aload_resolved_getfield(int index, FieldReference fieldRef) {
    try {
      Offset offset = localOffset(index);
      TypeReference fieldType = fieldRef.getFieldContentsType();
      RVMField field = fieldRef.peekResolvedField();
      Offset fieldOffset = field.getOffset();
      if (field.isReferenceType()) {
        // 32/64bit reference load
        if (NEEDS_OBJECT_GETFIELD_BARRIER && !field.isUntraced()) {
          emit_regular_aload(index);
          Barriers.compileGetfieldBarrierImm(asm, fieldOffset, fieldRef.getId());
        } else {
          stackMoveHelper(S0, offset);  // S0 is object reference
          asm.emitPUSH_RegDisp(S0, fieldOffset); // place field value on stack
        }
      } else if (fieldType.isBooleanType()) {
        // 8bit unsigned load
        stackMoveHelper(S0, offset);                         // S0 is object reference
        asm.emitMOVZX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
        asm.emitPUSH_Reg(T0);                                // place value on stack
      } else if (fieldType.isByteType()) {
        // 8bit signed load
        stackMoveHelper(S0, offset);                         // S0 is object reference
        asm.emitMOVSX_Reg_RegDisp_Byte(T0, S0, fieldOffset); // T0 is field value
        asm.emitPUSH_Reg(T0);                                // place value on stack
      } else if (fieldType.isShortType()) {
        // 16bit signed load
        stackMoveHelper(S0, offset);                         // S0 is object reference
        asm.emitMOVSX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
        asm.emitPUSH_Reg(T0);                                // place value on stack
      } else if (fieldType.isCharType()) {
        // 16bit unsigned load
        stackMoveHelper(S0, offset);                         // S0 is object reference
        asm.emitMOVZX_Reg_RegDisp_Word(T0, S0, fieldOffset); // T0 is field value
        asm.emitPUSH_Reg(T0);                                // place value on stack
      } else if (fieldType.isIntType() || fieldType.isFloatType() ||
                 (VM.BuildFor32Addr && fieldType.isWordLikeType())) {
        // 32bit load
        stackMoveHelper(S0, offset);                         // S0 is object reference
        if (VM.BuildFor32Addr) {
          asm.emitPUSH_RegDisp(S0, fieldOffset); // place value on stack
        } else {
          asm.emitMOV_Reg_RegDisp(T0, S0, fieldOffset); // T0 is field value
          asm.emitPUSH_Reg(T0);  // place value on stack
        }
      } else {
        // 64bit load
        if (VM.VerifyAssertions) {
          VM._assert(fieldType.isLongType() || fieldType.isDoubleType() ||
                     (VM.BuildFor64Addr && fieldType.isWordLikeType()));
        }
        stackMoveHelper(S0, offset);                  // S0 is object reference
        if (VM.BuildFor32Addr && field.isVolatile()) {
          // NB this is a 64bit copy from memory to the stack so implement
          // as a slightly optimized Intel memory copy using the FPU
          if (SSE2_BASE) {
            asm.emitMOVQ_Reg_RegDisp(XMM0, S0, fieldOffset); // XMM0 is field value
            adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
            asm.emitMOVQ_RegInd_Reg(SP, XMM0); // replace reference with value on stack
          } else {
            asm.emitFLD_Reg_RegDisp_Quad(FP0, S0, fieldOffset); // FP0 is field value
            adjustStack(-2 * WORDSIZE, true); // adjust stack down to hold 64bit value
            asm.emitFSTP_RegInd_Reg_Quad(SP, FP0); // replace reference with value on stack
          }
        } else if (VM.BuildFor32Addr && !field.isVolatile()) {
          asm.emitPUSH_RegDisp(S0, fieldOffset.plus(ONE_SLOT)); // place high half on stack
          asm.emitPUSH_RegDisp(S0, fieldOffset);                // place low half on stack
        } else {
          if (!fieldType.isWordLikeType()) {
            adjustStack(-WORDSIZE, true); // add empty slot
          }
          asm.emitPUSH_RegDisp(S0, fieldOffset); // place value on stack
        }
      }
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  @Override
  protected void emit_unresolved_putfield(FieldReference fieldRef) {
    Barriers.compileModifyCheck(asm, fieldRef.getNumberOfStackSlots() * WORDSIZE);
    TypeReference fieldType = fieldRef.getFieldContentsType();
    emitDynamicLinkingSequence(asm, T0, fieldRef, true);
    if (fieldType.isReferenceType()) {
      // 32/64bit reference store
      if (NEEDS_OBJECT_PUTFIELD_BARRIER) {
        Barriers.compilePutfieldBarrier(asm, T0, fieldRef.getId());
      } else {
        asm.emitPOP_Reg(T1);  // T1 is the value to be stored
        asm.emitPOP_Reg(S0);  // S0 is the object reference
        if (VM.BuildFor32Addr) {
          asm.emitMOV_RegIdx_Reg(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
        } else {
          asm.emitMOV_RegIdx_Reg_Quad(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
        }
      }
    } else if (NEEDS_BOOLEAN_PUTFIELD_BARRIER && fieldType.isBooleanType()) {
      Barriers.compilePutfieldBarrierBoolean(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_BYTE_PUTFIELD_BARRIER &&  fieldType.isByteType()) {
      Barriers.compilePutfieldBarrierByte(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_CHAR_PUTFIELD_BARRIER && fieldType.isCharType()) {
      Barriers.compilePutfieldBarrierChar(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_DOUBLE_PUTFIELD_BARRIER && fieldType.isDoubleType()) {
      Barriers.compilePutfieldBarrierDouble(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_FLOAT_PUTFIELD_BARRIER && fieldType.isFloatType()) {
      Barriers.compilePutfieldBarrierFloat(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_INT_PUTFIELD_BARRIER && fieldType.isIntType()) {
      Barriers.compilePutfieldBarrierInt(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_LONG_PUTFIELD_BARRIER && fieldType.isLongType()) {
      Barriers.compilePutfieldBarrierLong(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_SHORT_PUTFIELD_BARRIER && fieldType.isShortType()) {
      Barriers.compilePutfieldBarrierShort(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_WORD_PUTFIELD_BARRIER && fieldType.isWordType()) {
      Barriers.compilePutfieldBarrierWord(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_ADDRESS_PUTFIELD_BARRIER && fieldType.isAddressType()) {
      Barriers.compilePutfieldBarrierAddress(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_OFFSET_PUTFIELD_BARRIER && fieldType.isOffsetType()) {
      Barriers.compilePutfieldBarrierOffset(asm, T0, fieldRef.getId(), this);
    } else if (NEEDS_EXTENT_PUTFIELD_BARRIER && fieldType.isExtentType()) {
      Barriers.compilePutfieldBarrierExtent(asm, T0, fieldRef.getId(), this);
    } else if (fieldType.isBooleanType() || fieldType.isByteType()) { // no need for primitive write barriers
      // 8bit store
      asm.emitPOP_Reg(T1);  // T1 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      asm.emitMOV_RegIdx_Reg_Byte(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else if (fieldType.isShortType() || fieldType.isCharType()) {
      // 16bit store
      asm.emitPOP_Reg(T1);  // T1 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      asm.emitMOV_RegIdx_Reg_Word(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else if (fieldType.isIntType() || fieldType.isFloatType() ||
               (VM.BuildFor32Addr && fieldType.isWordLikeType())) {
      // 32bit store
      asm.emitPOP_Reg(T1);  // T1 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      asm.emitMOV_RegIdx_Reg(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
    } else {
      // 64bit store
      if (VM.VerifyAssertions) {
        VM._assert(fieldType.isLongType() || fieldType.isDoubleType() ||
                   (VM.BuildFor64Addr && fieldType.isWordLikeType()));
      }
      if (VM.BuildFor32Addr) {
        // NB this is a 64bit copy from the stack to memory so implement
        // as a slightly optimized Intel memory copy using the FPU
        asm.emitMOV_Reg_RegDisp(S0, SP, TWO_SLOTS); // S0 is the object reference
        if (SSE2_BASE) {
          asm.emitMOVQ_Reg_RegInd(XMM0, SP); // XMM0 is the value to be stored
          asm.emitMOVQ_RegIdx_Reg(S0, T0, BYTE, NO_SLOT, XMM0); // [S0+T0] <- XMM0
        } else {
          asm.emitFLD_Reg_RegInd_Quad(FP0, SP); // FP0 is the value to be stored
          asm.emitFSTP_RegIdx_Reg_Quad(S0, T0, BYTE, NO_SLOT, FP0); // [S0+T0] <- FP0
        }
        if (!fieldType.isWordLikeType()) {
          adjustStack(WORDSIZE * 3, true); // complete popping the values and reference
        } else {
          adjustStack(WORDSIZE * 2, true); // complete popping the values and reference
        }
      } else {
        asm.emitPOP_Reg(T1);  // T1 is the value to be stored
        if (!fieldType.isWordLikeType()) {
          adjustStack(WORDSIZE, true); // throw away slot
        }
        asm.emitPOP_Reg(S0);  // S0 is the object reference
        asm.emitMOV_RegIdx_Reg_Quad(S0, T0, BYTE, NO_SLOT, T1); // [S0+T0] <- T1
      }
    }
    // The field may be volatile.
    asm.emitMFENCE();
  }

  @Override
  protected void emit_resolved_putfield(FieldReference fieldRef) {
    Barriers.compileModifyCheck(asm, fieldRef.getNumberOfStackSlots() * WORDSIZE);
    RVMField field = fieldRef.peekResolvedField();
    TypeReference fieldType = fieldRef.getFieldContentsType();
    Offset fieldOffset = field.getOffset();
    if (field.isReferenceType()) {
      // 32/64bit reference store
      if (NEEDS_OBJECT_PUTFIELD_BARRIER && !field.isUntraced()) {
        Barriers.compilePutfieldBarrierImm(asm, fieldOffset, fieldRef.getId());
      } else {
        asm.emitPOP_Reg(T0);  // T0 is the value to be stored
        asm.emitPOP_Reg(S0);  // S0 is the object reference
        // [S0+fieldOffset] <- T0
        if (VM.BuildFor32Addr) {
          asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);
        } else {
          asm.emitMOV_RegDisp_Reg_Quad(S0, fieldOffset, T0);
        }
      }
    } else if (NEEDS_BOOLEAN_PUTFIELD_BARRIER && fieldType.isBooleanType()) {
      Barriers.compilePutfieldBarrierBooleanImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_BYTE_PUTFIELD_BARRIER &&  fieldType.isByteType()) {
      Barriers.compilePutfieldBarrierByteImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_CHAR_PUTFIELD_BARRIER && fieldType.isCharType()) {
      Barriers.compilePutfieldBarrierCharImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_DOUBLE_PUTFIELD_BARRIER && fieldType.isDoubleType()) {
      Barriers.compilePutfieldBarrierDoubleImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_FLOAT_PUTFIELD_BARRIER && fieldType.isFloatType()) {
      Barriers.compilePutfieldBarrierFloatImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_INT_PUTFIELD_BARRIER && fieldType.isIntType()) {
      Barriers.compilePutfieldBarrierIntImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_LONG_PUTFIELD_BARRIER && fieldType.isLongType()) {
      Barriers.compilePutfieldBarrierLongImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_SHORT_PUTFIELD_BARRIER && fieldType.isShortType()) {
      Barriers.compilePutfieldBarrierShortImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_WORD_PUTFIELD_BARRIER && fieldType.isWordType()) {
      Barriers.compilePutfieldBarrierWordImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_ADDRESS_PUTFIELD_BARRIER && fieldType.isAddressType()) {
      Barriers.compilePutfieldBarrierAddressImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_OFFSET_PUTFIELD_BARRIER && fieldType.isOffsetType()) {
      Barriers.compilePutfieldBarrierOffsetImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (NEEDS_EXTENT_PUTFIELD_BARRIER && fieldType.isExtentType()) {
      Barriers.compilePutfieldBarrierExtentImm(asm, fieldOffset, fieldRef.getId(), this);
    } else if (field.getSize() == BYTES_IN_BYTE) {  // no need for primitive write barriers
      // 8bit store
      asm.emitPOP_Reg(T0);  // T0 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg_Byte(S0, fieldOffset, T0);
    } else if (field.getSize() == BYTES_IN_SHORT) {
      // 16bit store
      asm.emitPOP_Reg(T0);  // T0 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg_Word(S0, fieldOffset, T0);
    } else if (field.getSize() == BYTES_IN_INT) {
      // 32bit store
      asm.emitPOP_Reg(T0);  // T0 is the value to be stored
      asm.emitPOP_Reg(S0);  // S0 is the object reference
      // [S0+fieldOffset] <- T0
      asm.emitMOV_RegDisp_Reg(S0, fieldOffset, T0);
    } else {
      // 64bit store
      if (VM.VerifyAssertions) {
        VM._assert(field.getSize() == BYTES_IN_LONG);
      }
      if (VM.BuildFor32Addr) {
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
        adjustStack(WORDSIZE * 3, true); // complete popping the values and reference
      } else {
        asm.emitPOP_Reg(T1);           // T1 is the value to be stored
        if (!field.getType().isWordLikeType()) {
          adjustStack(WORDSIZE, true); // throw away slot
        }
        asm.emitPOP_Reg(S0);           // S0 is the object reference
        asm.emitMOV_RegDisp_Reg_Quad(S0, fieldOffset, T1); // [S0+fieldOffset] <- T1
      }
    }
    if (field.isVolatile()) {
      asm.emitMFENCE();
    }
  }

  /*
   * method invocation
   */

  @Override
  protected void emit_unresolved_invokevirtual(MethodReference methodRef) {
    emitDynamicLinkingSequence(asm, T0, methodRef, true);            // T0 has offset of method
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    Offset objectOffset =
      Offset.fromIntZeroExtend(methodRefparameterWords << LG_WORDSIZE).minus(WORDSIZE); // object offset into stack
    stackMoveHelper(T1, objectOffset);                               // T1 has "this" parameter
    asm.baselineEmitLoadTIB(S0, T1);                                // S0 has TIB
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegIdx(S0, S0, T0, BYTE, NO_SLOT); // S0 has address of virtual method
    } else {
      asm.emitMOV_Reg_RegIdx_Quad(S0, S0, T0, BYTE, NO_SLOT); // S0 has address of virtual method
    }
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_Reg(S0);                                            // call virtual method
    genResultRegisterUnload(methodRef);                              // push return value, if any
  }

  @Override
  protected void emit_resolved_invokevirtual(MethodReference methodRef) {
    int methodRefparameterWords = methodRef.getParameterWords() + 1; // +1 for "this" parameter
    Offset methodRefOffset = methodRef.peekResolvedMethod().getOffset();
    Offset objectOffset =
      Offset.fromIntZeroExtend(methodRefparameterWords << LG_WORDSIZE).minus(WORDSIZE); // object offset into stack
    stackMoveHelper(T1, objectOffset);                               // T1 has "this" parameter
    asm.baselineEmitLoadTIB(S0, T1);                                 // S0 has TIB
    genParameterRegisterLoad(methodRef, true);
    asm.emitCALL_RegDisp(S0, methodRefOffset);                       // call virtual method
    genResultRegisterUnload(methodRef);                              // push return value, if any
  }

  @Override
  protected void emit_resolved_invokespecial(MethodReference methodRef, RVMMethod target) {
    if (target.isObjectInitializer()) {
      genParameterRegisterLoad(methodRef, true);
      asm.generateJTOCcall(target.getOffset());
      genResultRegisterUnload(target.getMemberRef().asMethodReference());
    } else {
      if (VM.VerifyAssertions) VM._assert(!target.isStatic());
      // invoke via class's tib slot
      Offset methodRefOffset = target.getOffset();
      asm.generateJTOCloadWord(S0, target.getDeclaringClass().getTibOffset());
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, methodRefOffset);
      genResultRegisterUnload(methodRef);
    }
  }

  @Override
  protected void emit_unresolved_invokespecial(MethodReference methodRef) {
    emitDynamicLinkingSequence(asm, S0, methodRef, true);
    genParameterRegisterLoad(methodRef, true);
    asm.generateJTOCcall(S0);
    genResultRegisterUnload(methodRef);
  }

  @Override
  protected void emit_unresolved_invokestatic(MethodReference methodRef) {
    emitDynamicLinkingSequence(asm, S0, methodRef, true);
    genParameterRegisterLoad(methodRef, false);
    asm.generateJTOCcall(S0);
    genResultRegisterUnload(methodRef);
  }

  @Override
  protected void emit_resolved_invokestatic(MethodReference methodRef) {
    Offset methodOffset = methodRef.peekResolvedMethod().getOffset();
    genParameterRegisterLoad(methodRef, false);
    asm.generateJTOCcall(methodOffset);
    genResultRegisterUnload(methodRef);
  }

  @Override
  protected void emit_invokeinterface(MethodReference methodRef) {
    final int count = methodRef.getParameterWords() + 1; // +1 for "this" parameter

    RVMMethod resolvedMethod = null;
    resolvedMethod = methodRef.peekInterfaceMethod();

    // (1) Emit dynamic type checking sequence if required to do so inline.
    if (VM.BuildForIMTInterfaceInvocation) {
      if (methodRef.isMiranda()) {
        // TODO: It's not entirely clear that we can just assume that
        //       the class actually implements the interface.
        //       However, we don't know what interface we need to be checking
        //       so there doesn't appear to be much else we can do here.
      } else {
        if (resolvedMethod == null) {
          // Can't successfully resolve it at compile time.
          // Call uncommon case typechecking routine to do the right thing when this code actually executes.
          // T1 = "this" object
          stackMoveHelper(T1, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
          asm.emitPUSH_Imm(methodRef.getId()); // push dict id of target
          asm.emitPUSH_Reg(T1);                // push "this"
          genParameterRegisterLoad(asm, 2);    // pass 2 parameter word
          // check that "this" class implements the interface
          asm.generateJTOCcall(Entrypoints.unresolvedInvokeinterfaceImplementsTestMethod.getOffset());
        } else {
          RVMClass interfaceClass = resolvedMethod.getDeclaringClass();
          int interfaceIndex = interfaceClass.getDoesImplementIndex();
          int interfaceMask = interfaceClass.getDoesImplementBitMask();
          // T1 = "this" object
          stackMoveHelper(T1, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
          asm.baselineEmitLoadTIB(S0, T1); // S0 = tib of "this" object
          if (VM.BuildFor32Addr) {
            asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));  // implements bit vector
          } else {
            asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));  // implements bit vector
          }

          if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
            // must do arraybounds check of implements bit vector
            if (ARRAY_LENGTH_BYTES == 4) {
              asm.emitCMP_RegDisp_Imm(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
            } else {
              asm.emitCMP_RegDisp_Imm_Quad(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
            }
            asm.emitBranchLikelyNextInstruction();
            ForwardReference fr = asm.forwardJcc(LGT);
            asm.emitINT_Imm(RuntimeEntrypoints.TRAP_MUST_IMPLEMENT + RVM_TRAP_BASE);
            fr.resolve(asm);
          }

          // Test the appropriate bit and if set, branch around another trap imm
          if (interfaceIndex == 0) {
            asm.emitTEST_RegInd_Imm(S0, interfaceMask);
          } else {
            asm.emitTEST_RegDisp_Imm(S0, Offset.fromIntZeroExtend(interfaceIndex << LOG_BYTES_IN_INT), interfaceMask);
          }
          asm.emitBranchLikelyNextInstruction();
          ForwardReference fr = asm.forwardJcc(NE);
          asm.emitINT_Imm(RuntimeEntrypoints.TRAP_MUST_IMPLEMENT + RVM_TRAP_BASE);
          fr.resolve(asm);
        }
      }
    }

    // (2) Emit interface invocation sequence.
    if (VM.BuildForIMTInterfaceInvocation) {
      InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(methodRef);
      // squirrel away signature ID
      Offset offset = ArchEntrypoints.hiddenSignatureIdField.getOffset();
      asm.emitMOV_RegDisp_Imm(THREAD_REGISTER, offset, sig.getId());
      // T1 = "this" object
      stackMoveHelper(T1, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
      asm.baselineEmitLoadTIB(S0, T1);
      // Load the IMT Base into S0
      if (VM.BuildFor32Addr) {
        asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_INTERFACE_DISPATCH_TABLE_INDEX << LG_WORDSIZE));
      } else {
        asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_INTERFACE_DISPATCH_TABLE_INDEX << LG_WORDSIZE));
      }
      genParameterRegisterLoad(methodRef, true);
      asm.emitCALL_RegDisp(S0, sig.getIMTOffset()); // the interface call
    } else {
      int itableIndex = -1;
      if (VM.BuildForITableInterfaceInvocation && resolvedMethod != null) {
        // get the index of the method in the Itable
        itableIndex =
          InterfaceInvocation.getITableIndex(resolvedMethod.getDeclaringClass(),
                                             methodRef.getName(),
                                             methodRef.getDescriptor());
      }
      if (itableIndex == -1) {
        // itable index is not known at compile-time.
        // call "invokeInterface" to resolve object + method id into
        // method address
        int methodRefId = methodRef.getId();
        // "this" parameter is obj
        if (count == 1) {
          asm.emitPUSH_RegInd(SP);
        } else {
          asm.emitPUSH_RegDisp(SP, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
        }
        asm.emitPUSH_Imm(methodRefId);             // id of method to call
        genParameterRegisterLoad(asm, 2);          // pass 2 parameter words
        // invokeinterface(obj, id) returns address to call
        asm.generateJTOCcall(Entrypoints.invokeInterfaceMethod.getOffset());
        if (VM.BuildFor32Addr) {
          asm.emitMOV_Reg_Reg(S0, T0);             // S0 has address of method
        } else {
          asm.emitMOV_Reg_Reg_Quad(S0, T0);        // S0 has address of method
        }
        genParameterRegisterLoad(methodRef, true);
        asm.emitCALL_Reg(S0);                      // the interface method (its parameters are on stack)
      } else {
        // itable index is known at compile-time.
        // call "findITable" to resolve object + interface id into
        // itable address
        // T0 = "this" object
        stackMoveHelper(T0, Offset.fromIntZeroExtend((count - 1) << LG_WORDSIZE));
        asm.baselineEmitLoadTIB(S0, T0);
        asm.emitPUSH_Reg(S0);
        asm.emitPUSH_Imm(resolvedMethod.getDeclaringClass().getInterfaceId()); // interface id
        genParameterRegisterLoad(asm, 2);                                      // pass 2 parameter words
        asm.generateJTOCcall(Entrypoints.findItableMethod.getOffset()); // findItableOffset(tib, id) returns iTable
        if (VM.BuildFor32Addr) {
          asm.emitMOV_Reg_Reg(S0, T0);                                         // S0 has iTable
        } else {
          asm.emitMOV_Reg_Reg_Quad(S0, T0);                                    // S0 has iTable
        }
        genParameterRegisterLoad(methodRef, true);
        // the interface call
        asm.emitCALL_RegDisp(S0, Offset.fromIntZeroExtend(itableIndex << LG_WORDSIZE));
      }
    }
    genResultRegisterUnload(methodRef);
  }

  /*
   * other object model functions
   */

  @Override
  protected void emit_resolved_new(RVMClass typeRef) {
    int instanceSize = typeRef.getInstanceSize();
    Offset tibOffset = typeRef.getTibOffset();
    int whichAllocator = MemoryManager.pickAllocator(typeRef, method);
    int align = ObjectModel.getAlignment(typeRef);
    int offset = ObjectModel.getOffsetForAlignment(typeRef, false);
    int site = MemoryManager.getAllocationSite(true);
    asm.emitPUSH_Imm(instanceSize);
    asm.generateJTOCpush(tibOffset);                             // put tib on stack
    asm.emitPUSH_Imm(typeRef.hasFinalizer() ? 1 : 0);    // does the class have a finalizer?
    asm.emitPUSH_Imm(whichAllocator);
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    asm.emitPUSH_Imm(site);
    genParameterRegisterLoad(asm, 7);                    // pass 7 parameter words
    asm.generateJTOCcall(Entrypoints.resolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_unresolved_new(TypeReference typeRef) {
    int site = MemoryManager.getAllocationSite(true);
    asm.emitPUSH_Imm(typeRef.getId());
    asm.emitPUSH_Imm(site);            // site
    genParameterRegisterLoad(asm, 2);  // pass 2 parameter words
    asm.generateJTOCcall(Entrypoints.unresolvedNewScalarMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_resolved_newarray(RVMArray array) {
    int width = array.getLogElementSize();
    Offset tibOffset = array.getTibOffset();
    int headerSize = ObjectModel.computeHeaderSize(array);
    int whichAllocator = MemoryManager.pickAllocator(array, method);
    int site = MemoryManager.getAllocationSite(true);
    int align = ObjectModel.getAlignment(array);
    int offset = ObjectModel.getOffsetForAlignment(array, false);
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(width);                 // logElementSize
    asm.emitPUSH_Imm(headerSize);            // headerSize
    asm.generateJTOCpush(tibOffset);                 // tib
    asm.emitPUSH_Imm(whichAllocator);        // allocator
    asm.emitPUSH_Imm(align);
    asm.emitPUSH_Imm(offset);
    asm.emitPUSH_Imm(site);
    genParameterRegisterLoad(asm, 8);        // pass 8 parameter words
    asm.generateJTOCcall(Entrypoints.resolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_unresolved_newarray(TypeReference tRef) {
    int site = MemoryManager.getAllocationSite(true);
    // count is already on stack- nothing required
    asm.emitPUSH_Imm(tRef.getId());
    asm.emitPUSH_Imm(site);           // site
    genParameterRegisterLoad(asm, 3); // pass 3 parameter words
    asm.generateJTOCcall(Entrypoints.unresolvedNewArrayMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_multianewarray(TypeReference typeRef, int dimensions) {
    // TODO: implement direct call to RuntimeEntrypoints.buildTwoDimensionalArray
    // Calculate the offset from FP on entry to newarray:
    //      1 word for each parameter, plus 1 for return address on
    //      stack and 1 for code technique in Linker
    final int PARAMETERS = 4;
    final int OFFSET_WORDS = PARAMETERS + 2;

    // setup parameters for newarrayarray routine
    asm.emitPUSH_Imm(method.getId());           // caller
    asm.emitPUSH_Imm(dimensions);               // dimension of arrays
    asm.emitPUSH_Imm(typeRef.getId());          // type of array elements
    asm.emitPUSH_Imm((dimensions + OFFSET_WORDS) << LG_WORDSIZE);  // offset to dimensions from FP on entry to newarray

    genParameterRegisterLoad(asm, PARAMETERS);
    asm.generateJTOCcall(ArchEntrypoints.newArrayArrayMethod.getOffset());
    adjustStack(dimensions * WORDSIZE, true);   // clear stack of dimensions
    asm.emitPUSH_Reg(T0);                       // push array ref on stack
  }

  @Override
  protected void emit_arraylength() {
    asm.emitPOP_Reg(T0);                // T0 is array reference
    if (ARRAY_LENGTH_BYTES == 4) {
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_RegDisp(T0, ObjectModel.getArrayLengthOffset());
      } else {
        asm.emitMOV_Reg_RegDisp(T0, T0, ObjectModel.getArrayLengthOffset());
        asm.emitPUSH_Reg(T0);
      }
    } else {
      asm.emitPUSH_RegDisp(T0, ObjectModel.getArrayLengthOffset());
    }
  }

  @Override
  protected void emit_athrow() {
    genParameterRegisterLoad(asm, 1);          // pass 1 parameter word
    asm.generateJTOCcall(Entrypoints.athrowMethod.getOffset());
  }

  @Override
  protected void emit_checkcast(TypeReference typeRef) {
    asm.emitPUSH_RegInd(SP);                        // duplicate the object ref on the stack
    asm.emitPUSH_Imm(typeRef.getId());               // TypeReference id.
    genParameterRegisterLoad(asm, 2);                     // pass 2 parameter words
    asm.generateJTOCcall(Entrypoints.checkcastMethod.getOffset()); // checkcast(obj, type reference id);
  }

  @Override
  protected void emit_checkcast_resolvedInterface(RVMClass type) {
    int interfaceIndex = type.getDoesImplementIndex();
    int interfaceMask = type.getDoesImplementBitMask();

    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegInd(ECX, SP);      // load object from stack into ECX
    } else {
      asm.emitMOV_Reg_RegInd_Quad(ECX, SP); // load object from stack into ECX
    }
    ForwardReference isNull = asm.forwardJECXZ(); // forward branch if ECX == 0

    asm.baselineEmitLoadTIB(S0, ECX);      // S0 = TIB of object
    // S0 = implements bit vector
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));
    }

    if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      // must do arraybounds check of implements bit vector
      if (ARRAY_LENGTH_BYTES == 4) {
        asm.emitCMP_RegDisp_Imm(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
      } else {
        asm.emitCMP_RegDisp_Imm_Quad(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
      }
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr = asm.forwardJcc(LGT);
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_CHECKCAST + RVM_TRAP_BASE);
      fr.resolve(asm);
    }

    // Test the appropriate bit and if set, branch around another trap imm
    asm.emitTEST_RegDisp_Imm(S0, Offset.fromIntZeroExtend(interfaceIndex << LOG_BYTES_IN_INT), interfaceMask);
    asm.emitBranchLikelyNextInstruction();
    ForwardReference fr = asm.forwardJcc(NE);
    asm.emitINT_Imm(RuntimeEntrypoints.TRAP_CHECKCAST + RVM_TRAP_BASE);
    fr.resolve(asm);
    isNull.resolve(asm);
  }

  @Override
  protected void emit_checkcast_resolvedClass(RVMClass type) {
    int LHSDepth = type.getTypeDepth();
    int LHSId = type.getId();

    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegInd(ECX, SP);      // load object from stack
    } else {
      asm.emitMOV_Reg_RegInd_Quad(ECX, SP); // load object from stack
    }
    ForwardReference isNull = asm.forwardJECXZ(); // jump forward if ECX == 0

    asm.baselineEmitLoadTIB(S0, ECX);       // S0 = TIB of object
    // S0 = superclass IDs
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LG_WORDSIZE));
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LG_WORDSIZE));
    }
    if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
      // must do arraybounds check of superclass display
      if (ARRAY_LENGTH_BYTES == 4) {
        asm.emitCMP_RegDisp_Imm(S0, ObjectModel.getArrayLengthOffset(), LHSDepth);
      } else {
        asm.emitCMP_RegDisp_Imm_Quad(S0, ObjectModel.getArrayLengthOffset(), LHSDepth);
      }
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr = asm.forwardJcc(LGT);
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_CHECKCAST + RVM_TRAP_BASE);
      fr.resolve(asm);
    }

    // Load id from display at required depth and compare against target id.
    asm.emitMOVZX_Reg_RegDisp_Word(S0, S0, Offset.fromIntZeroExtend(LHSDepth << LOG_BYTES_IN_SHORT));
    asm.emitCMP_Reg_Imm(S0, LHSId);
    asm.emitBranchLikelyNextInstruction();
    ForwardReference fr = asm.forwardJcc(EQ);
    asm.emitINT_Imm(RuntimeEntrypoints.TRAP_CHECKCAST + RVM_TRAP_BASE);
    fr.resolve(asm);
    isNull.resolve(asm);
  }

  @Override
  protected void emit_checkcast_final(RVMType type) {
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegInd(ECX, SP);      // load object from stack
    } else {
      asm.emitMOV_Reg_RegInd_Quad(ECX, SP); // load object from stack
    }
    ForwardReference isNull = asm.forwardJECXZ(); // jump forward if ECX == 0

    asm.baselineEmitLoadTIB(S0, ECX);      // TIB of object
    asm.generateJTOCcmpWord(S0, type.getTibOffset());
    asm.emitBranchLikelyNextInstruction();
    ForwardReference fr = asm.forwardJcc(EQ);
    asm.emitINT_Imm(RuntimeEntrypoints.TRAP_CHECKCAST + RVM_TRAP_BASE);
    fr.resolve(asm);
    isNull.resolve(asm);
  }

  @Override
  protected void emit_instanceof(TypeReference typeRef) {
    asm.emitPUSH_Imm(typeRef.getId());
    genParameterRegisterLoad(asm, 2);          // pass 2 parameter words
    asm.generateJTOCcall(Entrypoints.instanceOfMethod.getOffset());
    asm.emitPUSH_Reg(T0);
  }

  @Override
  protected void emit_instanceof_resolvedInterface(RVMClass type) {
    int interfaceIndex = type.getDoesImplementIndex();
    int interfaceMask = type.getDoesImplementBitMask();

    asm.emitPOP_Reg(ECX);                 // load object from stack
    ForwardReference isNull = asm.forwardJECXZ(); // test for null

    asm.baselineEmitLoadTIB(S0, ECX);     // S0 = TIB of object
    // S0 = implements bit vector
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_DOES_IMPLEMENT_INDEX << LG_WORDSIZE));
    }
    ForwardReference outOfBounds = null;
    if (DynamicTypeCheck.MIN_DOES_IMPLEMENT_SIZE <= interfaceIndex) {
      // must do arraybounds check of implements bit vector
      if (ARRAY_LENGTH_BYTES == 4) {
        asm.emitCMP_RegDisp_Imm(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
      } else {
        asm.emitCMP_RegDisp_Imm_Quad(S0, ObjectModel.getArrayLengthOffset(), interfaceIndex);
      }
      outOfBounds = asm.forwardJcc(LLE);
    }

    // Test the implements bit and push true if it is set
    asm.emitTEST_RegDisp_Imm(S0, Offset.fromIntZeroExtend(interfaceIndex << LOG_BYTES_IN_INT), interfaceMask);
    ForwardReference notMatched = asm.forwardJcc(EQ);
    asm.emitPUSH_Imm(1);
    ForwardReference done = asm.forwardJMP();

    // push false
    isNull.resolve(asm);
    if (outOfBounds != null) outOfBounds.resolve(asm);
    notMatched.resolve(asm);
    asm.emitPUSH_Imm(0);

    done.resolve(asm);
  }

  @Override
  protected void emit_instanceof_resolvedClass(RVMClass type) {
    int LHSDepth = type.getTypeDepth();
    int LHSId = type.getId();

    asm.emitPOP_Reg(ECX);                 // load object from stack
    ForwardReference isNull = asm.forwardJECXZ(); // test for null

    // get superclass display from object's TIB
    asm.baselineEmitLoadTIB(S0, ECX);
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegDisp(S0, S0, Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LG_WORDSIZE));
    } else {
      asm.emitMOV_Reg_RegDisp_Quad(S0, S0, Offset.fromIntZeroExtend(TIB_SUPERCLASS_IDS_INDEX << LG_WORDSIZE));
    }
    ForwardReference outOfBounds = null;
    if (DynamicTypeCheck.MIN_SUPERCLASS_IDS_SIZE <= LHSDepth) {
      // must do arraybounds check of superclass display
      if (ARRAY_LENGTH_BYTES == 4) {
        asm.emitCMP_RegDisp_Imm(S0, ObjectModel.getArrayLengthOffset(), LHSDepth);
      } else {
        asm.emitCMP_RegDisp_Imm_Quad(S0, ObjectModel.getArrayLengthOffset(), LHSDepth);
      }
      outOfBounds = asm.forwardJcc(LLE);
    }

    // Load id from display at required depth and compare against target id; push true if matched
    asm.emitMOVZX_Reg_RegDisp_Word(S0, S0, Offset.fromIntZeroExtend(LHSDepth << LOG_BYTES_IN_SHORT));
    asm.emitCMP_Reg_Imm(S0, LHSId);
    ForwardReference notMatched = asm.forwardJcc(NE);
    asm.emitPUSH_Imm(1);
    ForwardReference done = asm.forwardJMP();

    // push false
    isNull.resolve(asm);
    if (outOfBounds != null) outOfBounds.resolve(asm);
    notMatched.resolve(asm);
    asm.emitPUSH_Imm(0);

    done.resolve(asm);
  }

  @Override
  protected void emit_instanceof_final(RVMType type) {
    asm.emitPOP_Reg(ECX);                 // load object from stack
    ForwardReference isNull = asm.forwardJECXZ(); // test for null

    // compare TIB of object to desired TIB and push true if equal
    asm.baselineEmitLoadTIB(S0, ECX);
    asm.generateJTOCcmpWord(S0, type.getTibOffset());
    ForwardReference notMatched = asm.forwardJcc(NE);
    asm.emitPUSH_Imm(1);
    ForwardReference done = asm.forwardJMP();

    // push false
    isNull.resolve(asm);
    notMatched.resolve(asm);
    asm.emitPUSH_Imm(0);

    done.resolve(asm);
  }

  @Override
  protected void emit_monitorenter() {
    if (VM.BuildFor32Addr) {
      asm.emitMOV_Reg_RegInd(T0, SP);      // T0 is object reference
    } else {
      asm.emitMOV_Reg_RegInd_Quad(T0, SP); // T0 is object reference
    }
    genNullCheck(asm, T0);
    genParameterRegisterLoad(asm, 1);      // pass 1 parameter word
    asm.generateJTOCcall(Entrypoints.lockMethod.getOffset());
  }

  @Override
  protected void emit_monitorexit() {
    genParameterRegisterLoad(asm, 1);          // pass 1 parameter word
    asm.generateJTOCcall(Entrypoints.unlockMethod.getOffset());
  }

  //----------------//
  // implementation //
  //----------------//

  private void genPrologue() {
    if (shouldPrint) asm.comment("prologue for " + method);
    if (klass.hasBridgeFromNativeAnnotation()) {
      // replace the normal prologue with a special prolog
      JNICompiler.generateGlueCodeForJNIMethod(asm, method, compiledMethod.getId());
      // set some constants for the code generation of the rest of the method
      // firstLocalOffset is shifted down because more registers are saved
      firstLocalOffset = STACKFRAME_BODY_OFFSET.minus(JNICompiler.SAVED_GPRS_FOR_JNI << LG_WORDSIZE);
    } else {

      genStackOverflowCheck();

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
      // store caller's frame pointer
      asm.emitPUSH_RegDisp(TR, ArchEntrypoints.framePointerField.getOffset());
      // establish new frame
      if (VM.BuildFor32Addr) {
        asm.emitMOV_RegDisp_Reg(THREAD_REGISTER, ArchEntrypoints.framePointerField.getOffset(), SP);
      } else {
        asm.emitMOV_RegDisp_Reg_Quad(THREAD_REGISTER, ArchEntrypoints.framePointerField.getOffset(), SP);
      }
      /*
       * NOTE: until the end of the prologue SP holds the framepointer.
       */
      if (VM.VerifyAssertions) VM._assert(STACKFRAME_METHOD_ID_OFFSET.toInt() == -WORDSIZE);
      asm.emitPUSH_Imm(compiledMethod.getId());

      /*
       * save registers
       */
      if (VM.VerifyAssertions) VM._assert(EDI_SAVE_OFFSET.toInt() == -2 * WORDSIZE);
      asm.emitPUSH_Reg(EDI); // save nonvolatile EDI register
      if (VM.VerifyAssertions) VM._assert(EBX_SAVE_OFFSET.toInt() == -3 * WORDSIZE);
      asm.emitPUSH_Reg(EBX); // save nonvolatile EBX register

      int savedRegistersSize;

      if (method.hasBaselineSaveLSRegistersAnnotation()) {
        if (VM.VerifyAssertions) VM._assert(EBP_SAVE_OFFSET.toInt() == -4 * WORDSIZE);
        asm.emitPUSH_Reg(EBP);
        savedRegistersSize = SAVED_GPRS_FOR_SAVE_LS_REGISTERS << LG_WORDSIZE;
      } else {
        savedRegistersSize = SAVED_GPRS << LG_WORDSIZE;       // default
      }

      /* handle "dynamic brige" methods:
       * save all registers except FP, SP, TR, S0 (scratch), and
       * EDI and EBX saved above.
       */
      // TODO: (SJF): When I try to reclaim ESI, I may have to save it here?
      if (klass.hasDynamicBridgeAnnotation()) {
        savedRegistersSize += 2 << LG_WORDSIZE;
        if (VM.VerifyAssertions) VM._assert(T0_SAVE_OFFSET.toInt() == -4 * WORDSIZE);
        asm.emitPUSH_Reg(T0);
        if (VM.VerifyAssertions) VM._assert(T1_SAVE_OFFSET.toInt() == -5 * WORDSIZE);
        asm.emitPUSH_Reg(T1);
        if (SSE2_FULL) {
          // TODO: Store SSE2 Control word?
          adjustStack(-BASELINE_XMM_STATE_SIZE, true); // adjust stack to bottom of saved area
          if (VM.VerifyAssertions) VM._assert(XMM_SAVE_OFFSET.toInt() == (-5 * WORDSIZE) - BASELINE_XMM_STATE_SIZE);
          asm.emitMOVQ_RegDisp_Reg(SP, Offset.fromIntSignExtend(24), XMM3);
          asm.emitMOVQ_RegDisp_Reg(SP, Offset.fromIntSignExtend(16), XMM2);
          asm.emitMOVQ_RegDisp_Reg(SP, Offset.fromIntSignExtend(8), XMM1);
          asm.emitMOVQ_RegInd_Reg(SP, XMM0);
          savedRegistersSize += BASELINE_XMM_STATE_SIZE;
        } else {
          if (VM.VerifyAssertions) VM._assert(FPU_SAVE_OFFSET.toInt() == (-5 * WORDSIZE) - X87_FPU_STATE_SIZE);
          adjustStack(-X87_FPU_STATE_SIZE, true); // adjust stack to bottom of saved area
          asm.emitFNSAVE_RegInd(SP);
          savedRegistersSize += X87_FPU_STATE_SIZE;
        }
      }

      // copy registers to callee's stackframe
      firstLocalOffset = STACKFRAME_BODY_OFFSET.minus(savedRegistersSize);
      Offset firstParameterOffset = Offset.fromIntSignExtend(savedRegistersSize + STACKFRAME_HEADER_SIZE + (parameterWords << LG_WORDSIZE) - WORDSIZE);
      genParameterCopy(firstParameterOffset);
      int emptyStackOffset = (method.getLocalWords() << LG_WORDSIZE) - (parameterWords << LG_WORDSIZE);
      if (emptyStackOffset != 0) {
        adjustStack(-emptyStackOffset, true); // set aside room for non parameter locals
      }
      /* defer generating code which may cause GC until
       * locals were initialized. see emit_deferred_prologue
       */
      if (method.isForOsrSpecialization()) {
        return;
      }

      if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
        // use (nonvolatile) EBX to hold base of this method's counter array
        if (NEEDS_OBJECT_ALOAD_BARRIER) {
          asm.generateJTOCpush(Entrypoints.edgeCountersField.getOffset());
          asm.emitPUSH_Imm(getEdgeCounterIndex());
          Barriers.compileArrayLoadBarrier(asm, false);
          if (VM.BuildFor32Addr) {
            asm.emitMOV_Reg_Reg(EBX, T0);
          } else {
            asm.emitMOV_Reg_Reg_Quad(EBX, T0);
          }
        } else {
          if (VM.BuildFor32Addr) {
            asm.emitMOV_Reg_Abs(EBX, Magic.getTocPointer().plus(Entrypoints.edgeCountersField.getOffset()));
            asm.emitMOV_Reg_RegDisp(EBX, EBX, getEdgeCounterOffset());
          } else {
            asm.generateJTOCpush(Entrypoints.edgeCountersField.getOffset());
            asm.emitPOP_Reg(EBX);
            asm.emitMOV_Reg_RegDisp_Quad(EBX, EBX, getEdgeCounterOffset());
          }
        }
      }

      if (method.isSynchronized()) genMonitorEnter();

      genThreadSwitchTest(RVMThread.PROLOGUE);
    }
  }

  /**
   * Emit deferred prologue
   */
  @Override
  protected void emit_deferred_prologue() {

    if (VM.VerifyAssertions) VM._assert(method.isForOsrSpecialization());

    if (isInterruptible) {
      Offset offset = Entrypoints.stackLimitField.getOffset();
      if (VM.BuildFor32Addr) {
        // S0<-limit
        asm.emitMOV_Reg_RegDisp(S0, THREAD_REGISTER, offset);
        asm.emitSUB_Reg_Reg(S0, SP);
        asm.emitADD_Reg_Imm(S0, method.getOperandWords() << LG_WORDSIZE);
      } else {
        // S0<-limit
        asm.emitMOV_Reg_RegDisp_Quad(S0, THREAD_REGISTER, offset);
        asm.emitSUB_Reg_Reg_Quad(S0, SP);
        asm.emitADD_Reg_Imm_Quad(S0, method.getOperandWords() << LG_WORDSIZE);
      }
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr = asm.forwardJcc(LT);    // Jmp around trap
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE); // trap
      fr.resolve(asm);
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow
    }

    /* never do monitor enter for synced method since the specialized
     * code starts after original monitor enter.
     */

    genThreadSwitchTest(RVMThread.PROLOGUE);
  }

  /**
   * Generate method epilogue, releasing values from stack and returning
   * @param returnSize the size in bytes of the returned value
   * @param bytesPopped number of paramter bytes already released
   */
  private void genEpilogue(int returnSize, int bytesPopped) {
    if (klass.hasBridgeFromNativeAnnotation()) {
      // pop locals and parameters, get to saved GPR's
      adjustStack((method.getLocalWords() << LG_WORDSIZE) + (returnSize - bytesPopped), true);
      JNICompiler.generateEpilogForJNIMethod(asm, this.method);
    } else if (klass.hasDynamicBridgeAnnotation()) {
      // we never return from a DynamicBridge frame
      asm.emitINT_Imm(0xFF);
    } else {
      // normal method
      if (method.hasBaselineSaveLSRegistersAnnotation()) {
        // There is one more word out of the total that is for callee-saves, hense 4 * WORDSIZE here rather than 3 * WORDSIZE below.
        int spaceToRelease = fp2spOffset(NO_SLOT).toInt() - bytesPopped - (4 * WORDSIZE);
        adjustStack(spaceToRelease, true);
        if (VM.VerifyAssertions) VM._assert(EBP_SAVE_OFFSET.toInt() == -(4 * WORDSIZE));
        asm.emitPOP_Reg(EBP);             // restore nonvolatile EBP register
      } else {
        int spaceToRelease = fp2spOffset(NO_SLOT).toInt() - bytesPopped - (3 * WORDSIZE);
        adjustStack(spaceToRelease, true);
      }
      if (VM.VerifyAssertions) VM._assert(EBX_SAVE_OFFSET.toInt() == -(3 * WORDSIZE));
      asm.emitPOP_Reg(EBX);  // restore non-volatile EBX register
      if (VM.VerifyAssertions) VM._assert(EDI_SAVE_OFFSET.toInt() == -(2 * WORDSIZE));
      asm.emitPOP_Reg(EDI);  // restore non-volatile EDI register
      asm.emitPOP_Reg(ECX); // throw away CMID
      // SP == frame pointer
      asm.emitPOP_RegDisp(TR, ArchEntrypoints.framePointerField.getOffset()); // discard frame
      // return to caller, pop parameters from stack
      if (parameterWords == 0) {
        asm.emitRET();
      } else {
        asm.emitRET_Imm(parameterWords << LG_WORDSIZE);
      }
    }
  }

  private void genStackOverflowCheck() {
    /*
     * Generate stacklimit check.
     *
     * NOTE: The stack overflow check MUST happen before the frame is created.
     * If the check were to happen after frame creation, the stack pointer
     * could already be well below the stack limit. This would be a problem
     * because the IA32 stack overflow handling code imposes a bound on the
     * difference between the stack pointer and the stack limit.
     *
     * NOTE: Frame sizes for the baseline compiler can get very large because
     * each non-parameter local slot and each slot for the operand stack
     * requires one machine word.
     *
     * The Java Virtual Machine Specification has an overview of the limits for
     * the local words and operand words in section 4.11,
     * "Limitations of the Java Virtual Machine".
     */
    if (isInterruptible) {
      int frameSize = calculateRequiredSpaceForFrame(method);
      // S0<-limit
      if (VM.BuildFor32Addr) {
        asm.emitMOV_Reg_Reg(S0, ESP);
        asm.emitSUB_Reg_Imm(S0, frameSize);
        asm.emitCMP_Reg_RegDisp(S0, TR, Entrypoints.stackLimitField.getOffset());
      } else {
        asm.emitMOV_Reg_Reg_Quad(S0, ESP);
        asm.emitSUB_Reg_Imm_Quad(S0, frameSize);
        asm.emitCMP_Reg_RegDisp_Quad(S0, TR, Entrypoints.stackLimitField.getOffset());
      }
      asm.emitBranchLikelyNextInstruction();
      ForwardReference fr = asm.forwardJcc(LGT);        // Jmp around trap if OK
      asm.emitINT_Imm(RuntimeEntrypoints.TRAP_STACK_OVERFLOW + RVM_TRAP_BASE);     // trap
      fr.resolve(asm);
    } else {
      // TODO!! make sure stackframe of uninterruptible method doesn't overflow guard page
    }
  }

  /**
   * Calculates the space that is required for creating a frame for the
   * given method, in bytes. This quantity is necessary to be able to do
   * a stack overflow check before creating the frame.
   * <p>
   * Note that this method doesn't return the complete frame size:
   * the parameters are in the caller's frame for the baseline compiler
   * and the caller's frame has already been created when the callee is
   * called. The additional space that's required is necessary for
   * holding the non-parameter locals and the operand stack.
   *
   * @param method a method with bytecodes
   * @return space required to create the frame, in bytes
   */
  public static int calculateRequiredSpaceForFrame(NormalMethod method) {
    int frameWords = 3; // method id, EDI, EDX

    if (method.hasBaselineSaveLSRegistersAnnotation()) {
      frameWords++; // EBP
    }

    if (method.getDeclaringClass().hasDynamicBridgeAnnotation()) {
      frameWords += 2; // T0, T1
      if (SSE2_FULL) {
        frameWords += (BASELINE_XMM_STATE_SIZE / WORDSIZE);
      } else {
        frameWords += (X87_FPU_STATE_SIZE / WORDSIZE);
      }
    }

    frameWords += method.getOperandWords();
    frameWords += method.getLocalWords();
    // parameters are in the caller's frame so they don't
    // count towards the space for the method's frame
    frameWords -= method.getParameterWords();
    if (!method.isStatic()) frameWords--;

    return frameWords * WORDSIZE;
  }

  /**
   * Generate instructions to acquire lock on entry to a method
   */
  private void genMonitorEnter() {
    try {
      if (method.isStatic()) {
        Offset klassOffset = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType()));
        // push java.lang.Class object for klass
        asm.generateJTOCpush(klassOffset);
      } else {
        // push "this" object
        asm.emitPUSH_RegDisp(ESP, localOffset(0));
      }
      // pass 1 parameter
      genParameterRegisterLoad(asm, 1);
      asm.generateJTOCcall(Entrypoints.lockMethod.getOffset());
      // after this instruction, the method has the monitor
      lockOffset = asm.getMachineCodeIndex();
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  /**
   * Generate instructions to release lock on exit from a method
   */
  private void genMonitorExit() {
    try {
      if (method.isStatic()) {
        Offset klassOffset = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass.getClassForType()));
        // push java.lang.Class object for klass
        asm.generateJTOCpush(klassOffset);
      } else {
        asm.emitPUSH_RegDisp(ESP, localOffset(0));                    // push "this" object
      }
      genParameterRegisterLoad(asm, 1); // pass 1 parameter
      asm.generateJTOCcall(Entrypoints.unlockMethod.getOffset());
    } catch (UnreachableBytecodeException e) {
      asm.emitINT_Imm(TRAP_UNREACHABLE_BYTECODE + RVM_TRAP_BASE);
    }
  }

  /**
   * Generate an explicit null check (compare to zero).
   *
   * @param asm the assembler to generate into
   * @param objRefReg the register containing the reference
   */
  @Inline
  private static void genNullCheck(Assembler asm, GPR objRefReg) {
    // compare to zero
    asm.emitTEST_Reg_Reg(objRefReg, objRefReg);
    // Jmp around trap if index is OK
    asm.emitBranchLikelyNextInstruction();
    ForwardReference fr = asm.forwardJcc(NE);
    // trap
    asm.emitINT_Imm(RuntimeEntrypoints.TRAP_NULL_POINTER + RVM_TRAP_BASE);
    fr.resolve(asm);
  }

  /**
   * Generate an array bounds check trapping if the array bound check fails,
   * otherwise falling through.
   * @param asm the assembler to generate into
   * @param indexReg the register containing the index
   * @param arrayRefReg the register containing the array reference
   */
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {1,2})
  static void genBoundsCheck(Assembler asm, GPR indexReg, GPR arrayRefReg) {
    // compare index to array length
    if (ARRAY_LENGTH_BYTES == 4) {
      asm.emitCMP_RegDisp_Reg(arrayRefReg, ObjectModel.getArrayLengthOffset(), indexReg);
    } else {
      asm.emitCMP_RegDisp_Reg_Quad(arrayRefReg, ObjectModel.getArrayLengthOffset(), indexReg);
    }
    // Jmp around trap if index is OK
    asm.emitBranchLikelyNextInstruction();
    ForwardReference fr = asm.forwardJcc(LGT);
    // "pass" index param to C trap handler
    asm.emitMOV_RegDisp_Reg(THREAD_REGISTER, ArchEntrypoints.arrayIndexTrapParamField.getOffset(), indexReg);
    // trap
    asm.emitINT_Imm(RuntimeEntrypoints.TRAP_ARRAY_BOUNDS + RVM_TRAP_BASE);
    fr.resolve(asm);
  }

  /**
   * Emits a conditional branch on the given condition and bytecode target.
   * The caller has just emitted the instruction sequence to set the condition codes.
   *
   * @param cond condition byte
   * @param bTarget target bytecode index
   */
  private void genCondBranch(byte cond, int bTarget) {
    int mTarget = bytecodeMap[bTarget];
    if (!VM.runningTool && ((BaselineCompiledMethod) compiledMethod).hasCounterArray()) {
      // Allocate two counters: taken and not taken
      int entry = edgeCounterIdx;
      edgeCounterIdx += 2;

      // Flip conditions so we can jump over the increment of the taken counter.
      ForwardReference notTaken = asm.forwardJcc(asm.flipCode(cond));

      // Increment taken counter & jump to target
      incEdgeCounter(T1, null, entry + EdgeCounts.TAKEN);
      asm.emitJMP_ImmOrLabel(mTarget, bTarget);

      // Increment not taken counter
      notTaken.resolve(asm);
      incEdgeCounter(T1, null, entry + EdgeCounts.NOT_TAKEN);
    } else {
      asm.emitJCC_Cond_ImmOrLabel(cond, mTarget, bTarget);
    }
  }

  /**
   * Generate code to increment edge counter
   * @param scratch register to use as scratch
   * @param idx optional register holding index value or null
   * @param counterIdx index in to counters array
   */
  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {1,2})
  private void incEdgeCounter(GPR scratch, GPR idx, int counterIdx) {
    if (VM.VerifyAssertions) VM._assert(((BaselineCompiledMethod) compiledMethod).hasCounterArray());
    if (idx == null) {
      asm.emitMOV_Reg_RegDisp(scratch, EBX, Offset.fromIntZeroExtend(counterIdx << LOG_BYTES_IN_INT));
    } else {
      asm.emitMOV_Reg_RegIdx(scratch, EBX, idx, WORD, Offset.fromIntZeroExtend(counterIdx << LOG_BYTES_IN_INT));
    }
    asm.emitADD_Reg_Imm(scratch, 1);
    // Add 1 to scratch, if the add overflows subtract 1 (the carry flag).
    // Add saturates at 0xFFFFFFFF
    asm.emitSBB_Reg_Imm(scratch, 0);
    if (idx == null) {
      asm.emitMOV_RegDisp_Reg(EBX, Offset.fromIntSignExtend(counterIdx << LOG_BYTES_IN_INT), scratch);
    } else {
      asm.emitMOV_RegIdx_Reg(EBX, idx, WORD, Offset.fromIntSignExtend(counterIdx << LOG_BYTES_IN_INT), scratch);
    }
  }

  /**
   * Copy parameters from operand stack into registers.
   * Assumption: parameters are laid out on the stack in order
   * with SP pointing to the last parameter.
   * Also, this method is called before the generation of a "helper" method call.
   * Assumption: no floating-point parameters.
   * @param asm assembler to use for generation
   * @param params number of parameter words (including "this" if any).
   */
  static void genParameterRegisterLoad(Assembler asm, int params) {
    if (VM.VerifyAssertions) VM._assert(0 < params);
    if (0 < NUM_PARAMETER_GPRS) {
      stackMoveHelper(asm, T0, Offset.fromIntZeroExtend((params - 1) << LG_WORDSIZE));
    }
    if (1 < params && 1 < NUM_PARAMETER_GPRS) {
      stackMoveHelper(asm, T1, Offset.fromIntZeroExtend((params - 2) << LG_WORDSIZE));
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
  protected void genParameterRegisterLoad(MethodReference method, boolean hasThisParam) {
    int max = NUM_PARAMETER_GPRS + NUM_PARAMETER_FPRS;
    if (max == 0) return; // quit looking when all registers are full
    int gpr = 0;  // number of general purpose registers filled
    int fpr = 0;  // number of floating point  registers filled
    GPR T = T0; // next GPR to get a parameter
    int params = method.getParameterWords() + (hasThisParam ? 1 : 0);
    Offset offset = Offset.fromIntSignExtend((params - 1) << LG_WORDSIZE); // stack offset of first parameter word
    if (hasThisParam) {
      if (gpr < NUM_PARAMETER_GPRS) {
        stackMoveHelper(T, offset);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
        max--;
      }
      offset = offset.minus(WORDSIZE);
    }
    for (TypeReference type : method.getParameterTypes()) {
      if (max == 0) return; // quit looking when all registers are full
      TypeReference t = type;
      if (t.isLongType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          if (WORDSIZE == 4) {
            stackMoveHelper(T, offset); // lo register := hi mem (== hi order word)
            T = T1; // at most 2 parameters can be passed in general purpose registers
            gpr++;
            max--;
            if (gpr < NUM_PARAMETER_GPRS) {
              stackMoveHelper(T, offset.minus(WORDSIZE)); // hi register := lo mem (== lo order word)
              gpr++;
              max--;
            }
          } else {
            // initially offset will point at junk word, move down and over
            stackMoveHelper(T, offset.minus(WORDSIZE));
            T = T1; // at most 2 parameters can be passed in general purpose registers
            gpr++;
            max--;
          }
        }
        offset = offset.minus(2 * WORDSIZE);
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          if (SSE2_FULL) {
            asm.emitMOVSS_Reg_RegDisp(XMM.lookup(fpr), SP, offset);
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
            asm.emitMOVSD_Reg_RegDisp(XMM.lookup(fpr), SP, offset.minus(WORDSIZE));
          } else {
            asm.emitFLD_Reg_RegDisp_Quad(FP0, SP, offset.minus(WORDSIZE));
          }
          fpr++;
          max--;
        }
        offset = offset.minus(2 * WORDSIZE);
      } else if (t.isReferenceType() || t.isWordLikeType()) {
        if (gpr < NUM_PARAMETER_GPRS) {
          stackMoveHelper(T, offset);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
          max--;
        }
        offset = offset.minus(WORDSIZE);
      } else { // t is object, int, short, char, byte, or boolean
        if (gpr < NUM_PARAMETER_GPRS) {
          if (offset.isZero()) {
            asm.emitMOV_Reg_RegInd(T, SP);
          } else {
            asm.emitMOV_Reg_RegDisp(T, SP, offset);
          }
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
   * Stores parameters into local space of the callee's stackframe.
   * <p>
   * Assumption: although some parameters may be passed in registers,
   * space for all parameters is laid out in order on the caller's stackframe.
   *
   * @param srcOffset offset from frame pointer of first parameter in caller's stackframe.
   */
  private void genParameterCopy(Offset srcOffset) {
    int gpr = 0;  // number of general purpose registers unloaded
    int fpr = 0;  // number of floating point registers unloaded
    GPR T = T0; // next GPR to get a parameter
    int dstOffset = 0; // offset from the bottom of the locals for the current parameter
    if (!method.isStatic()) { // handle "this" parameter
      if (gpr < NUM_PARAMETER_GPRS) {
        asm.emitPUSH_Reg(T);
        T = T1; // at most 2 parameters can be passed in general purpose registers
        gpr++;
      } else { // no parameters passed in registers
        asm.emitPUSH_RegDisp(SP, srcOffset);
      }
      dstOffset -= WORDSIZE;
    }
    int[] fprOffset = new int[NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    boolean[] is32bit = new boolean[NUM_PARAMETER_FPRS]; // to handle floating point parameters in registers
    int spIsOffBy = 0; // in the case of doubles and floats SP may drift from the expected value as we don't use push/pop
    for (TypeReference t : method.getParameterTypes()) {
      if (t.isLongType()) {
        if (spIsOffBy != 0) {
          // fix up SP if it drifted
          adjustStack(-spIsOffBy, true);
          spIsOffBy = 0;
        }
        if (gpr < NUM_PARAMETER_GPRS) {
          if (VM.BuildFor32Addr) {
            asm.emitPUSH_Reg(T);                          // hi mem := lo register (== hi order word)
            T = T1;                                       // at most 2 parameters can be passed in general purpose registers
            gpr++;
            if (gpr < NUM_PARAMETER_GPRS) {
              asm.emitPUSH_Reg(T);  // lo mem := hi register (== lo order word)
              gpr++;
            } else {
              asm.emitPUSH_RegDisp(SP, srcOffset); // lo mem from caller's stackframe
            }
          } else {
            adjustStack(-WORDSIZE, true);                 // create empty slot
            asm.emitPUSH_Reg(T);                          // push long
            T = T1;                                       // at most 2 parameters can be passed in general purpose registers
            gpr++;
          }
        } else {
          if (VM.BuildFor32Addr) {
            asm.emitPUSH_RegDisp(SP, srcOffset);   // hi mem from caller's stackframe
            asm.emitPUSH_RegDisp(SP, srcOffset);   // lo mem from caller's stackframe
          } else {
            adjustStack(-WORDSIZE, true);          // create empty slot
            asm.emitPUSH_RegDisp(SP, srcOffset);   // push long
          }
        }
        dstOffset -= 2 * WORDSIZE;
      } else if (t.isFloatType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          spIsOffBy += WORDSIZE;
          fprOffset[fpr] = dstOffset;
          is32bit[fpr] = true;
          fpr++;
        } else {
          if (spIsOffBy != 0) {
            // fix up SP if it drifted
            adjustStack(-spIsOffBy, true);
            spIsOffBy = 0;
          }
          asm.emitPUSH_RegDisp(SP, srcOffset);
        }
        dstOffset -= WORDSIZE;
      } else if (t.isDoubleType()) {
        if (fpr < NUM_PARAMETER_FPRS) {
          spIsOffBy += 2 * WORDSIZE;
          dstOffset -= WORDSIZE;
          fprOffset[fpr] = dstOffset;
          dstOffset -= WORDSIZE;
          is32bit[fpr] = false;
          fpr++;
        } else {
          if (spIsOffBy != 0) {
            // fix up SP if it drifted
            adjustStack(-spIsOffBy, true);
            spIsOffBy = 0;
          }
          if (VM.BuildFor32Addr) {
            asm.emitPUSH_RegDisp(SP, srcOffset);   // hi mem from caller's stackframe
            asm.emitPUSH_RegDisp(SP, srcOffset);   // lo mem from caller's stackframe
          } else {
            adjustStack(-WORDSIZE, true);          // create empty slot
            asm.emitPUSH_RegDisp(SP, srcOffset);   // push double
          }
          dstOffset -= 2 * WORDSIZE;
        }
      } else { // t is object, int, short, char, byte, or boolean
        if (spIsOffBy != 0) {
          // fix up SP if it drifted
          adjustStack(-spIsOffBy, true);
          spIsOffBy = 0;
        }
        if (gpr < NUM_PARAMETER_GPRS) {
          asm.emitPUSH_Reg(T);
          T = T1; // at most 2 parameters can be passed in general purpose registers
          gpr++;
        } else {
          asm.emitPUSH_RegDisp(SP, srcOffset);
        }
        dstOffset -= WORDSIZE;
      }
    }
    if (spIsOffBy != 0) {
      // fix up SP if it drifted
      adjustStack(-spIsOffBy, true);
    }
    for (int i = fpr - 1; 0 <= i; i--) { // unload the floating point register stack (backwards)
      if (is32bit[i]) {
        if (SSE2_BASE) {
          asm.emitMOVSS_RegDisp_Reg(SP, Offset.fromIntSignExtend(fprOffset[i] - dstOffset - WORDSIZE), XMM.lookup(i));
        } else {
          asm.emitFSTP_RegDisp_Reg(SP, Offset.fromIntSignExtend(fprOffset[i] - dstOffset - WORDSIZE), FP0);
        }
      } else {
        if (SSE2_BASE) {
          asm.emitMOVSD_RegDisp_Reg(SP, Offset.fromIntSignExtend(fprOffset[i] - dstOffset - WORDSIZE), XMM.lookup(i));
        } else {
          asm.emitFSTP_RegDisp_Reg_Quad(SP, Offset.fromIntSignExtend(fprOffset[i] - dstOffset - WORDSIZE), FP0);
        }
      }
    }
  }

  /**
   * Pushes return value of method from register to operand stack.
   *
   * @param m the method whose return value is to be pushed
   */
  private void genResultRegisterUnload(MethodReference m) {
    TypeReference t = m.getReturnType();

    if (t.isVoidType()) {
      // nothing to do
    } else if (t.isLongType()) {
      if (VM.BuildFor32Addr) {
        asm.emitPUSH_Reg(T0); // high half
        asm.emitPUSH_Reg(T1); // low half
      } else {
        adjustStack(-WORDSIZE, true);
        asm.emitPUSH_Reg(T0); // long value
      }
    } else if (t.isFloatType()) {
      adjustStack(-WORDSIZE, true);
      if (SSE2_FULL) {
        asm.emitMOVSS_RegInd_Reg(SP, XMM0);
      } else {
        asm.emitFSTP_RegInd_Reg(SP, FP0);
      }
    } else if (t.isDoubleType()) {
      adjustStack(-2 * WORDSIZE, true);
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
    asm.emitCMP_RegDisp_Imm(THREAD_REGISTER, Entrypoints.takeYieldpointField.getOffset(), 0);
    ForwardReference fr1;
    Offset yieldOffset;
    if (whereFrom == RVMThread.PROLOGUE) {
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(EQ);
      yieldOffset = Entrypoints.yieldpointFromPrologueMethod.getOffset();
    } else if (whereFrom == RVMThread.BACKEDGE) {
      // Take yieldpoint if yieldpoint flag is >0
      fr1 = asm.forwardJcc(LE);
      yieldOffset = Entrypoints.yieldpointFromBackedgeMethod.getOffset();
    } else { // EPILOGUE
      // Take yieldpoint if yieldpoint flag is non-zero (either 1 or -1)
      fr1 = asm.forwardJcc(EQ);
      yieldOffset = Entrypoints.yieldpointFromEpilogueMethod.getOffset();
    }
    asm.generateJTOCcall(yieldOffset);
    fr1.resolve(asm);

    if (VM.BuildForAdaptiveSystem && options.INVOCATION_COUNTERS) {
      int id = compiledMethod.getId();
      InvocationCounts.allocateCounter(id);
      asm.generateJTOCloadWord(ECX, AosEntrypoints.invocationCountsField.getOffset());
      if (VM.BuildFor32Addr) {
        asm.emitSUB_RegDisp_Imm(ECX, Offset.fromIntZeroExtend(compiledMethod.getId() << LOG_BYTES_IN_INT), 1);
      } else {
        asm.emitSUB_RegDisp_Imm_Quad(ECX, Offset.fromIntZeroExtend(compiledMethod.getId() << LOG_BYTES_IN_INT), 1);
      }
      ForwardReference notTaken = asm.forwardJcc(GT);
      asm.emitPUSH_Imm(id);
      genParameterRegisterLoad(asm, 1);
      asm.generateJTOCcall(AosEntrypoints.invocationCounterTrippedMethod.getOffset());
      notTaken.resolve(asm);
    }
  }

  /**
   * Generate magic method
   * @param m method to generate
   * @return true if magic method was generated
   */
  private boolean genMagic(MethodReference m) {
    if (BaselineMagic.generateMagic(asm, m, method, fp2spOffset(NO_SLOT))) {
      return true;
    } else if (m.isSysCall()) {
      TypeReference[] args = m.getParameterTypes();
      TypeReference rtype = m.getReturnType();
      Offset offsetToLastArg = THREE_SLOTS; // the three regs saved in (1)
      Offset offsetToFirstArg = offsetToLastArg.plus((m.getParameterWords() - 1) << LG_WORDSIZE);
      boolean[] inRegister = VM.BuildFor32Addr ? null : new boolean[args.length];
      int paramBytes = 0;

      // (1) save three RVM nonvolatile/special registers
      //     we don't have to save EBP: the callee will
      //     treat it as a framepointer and save/restore
      //     it for us.
      asm.emitPUSH_Reg(EBX);
      asm.emitPUSH_Reg(ESI);
      asm.emitPUSH_Reg(EDI);

      // (2) Pass args in registers passing from left-to-right
      //     (NB avoid the first argument holding the target function address)
      int gpRegistersInUse = 0;
      int fpRegistersInUse = 0;
      Offset offsetToJavaArg = offsetToFirstArg;
      if (VM.BuildFor64Addr) {
        for (int i = 1; i < args.length; i++) {
          TypeReference arg = args[i];
          if (arg.isFloatType()) {
            if (fpRegistersInUse < NATIVE_PARAMETER_FPRS.length) {
              inRegister[i] = true;
              offsetToJavaArg = offsetToJavaArg.minus(WORDSIZE);
              asm.emitMOVSS_Reg_RegDisp((XMM)NATIVE_PARAMETER_FPRS[fpRegistersInUse], SP, offsetToJavaArg);
              fpRegistersInUse++;
            }
          } else if (arg.isDoubleType()) {
            if (fpRegistersInUse < NATIVE_PARAMETER_FPRS.length) {
              inRegister[i] = true;
              offsetToJavaArg = offsetToJavaArg.minus(2 * WORDSIZE);
              asm.emitMOVSD_Reg_RegDisp((XMM)NATIVE_PARAMETER_FPRS[fpRegistersInUse], SP, offsetToJavaArg);
              fpRegistersInUse++;
            }
          } else if (arg.isLongType()) {
            if (gpRegistersInUse < NATIVE_PARAMETER_GPRS.length) {
              inRegister[i] = true;
              offsetToJavaArg = offsetToJavaArg.minus(2 * WORDSIZE);
              asm.emitMOV_Reg_RegDisp_Quad(NATIVE_PARAMETER_GPRS[gpRegistersInUse], SP, offsetToJavaArg);
              gpRegistersInUse++;
            }
          } else if (arg.isWordLikeType() || arg.isReferenceType()) {
            if (gpRegistersInUse < NATIVE_PARAMETER_GPRS.length) {
              inRegister[i] = true;
              offsetToJavaArg = offsetToJavaArg.minus(WORDSIZE);
              asm.emitMOV_Reg_RegDisp_Quad(NATIVE_PARAMETER_GPRS[gpRegistersInUse], SP, offsetToJavaArg);
              gpRegistersInUse++;
            }
          } else {
            if (gpRegistersInUse < NATIVE_PARAMETER_GPRS.length) {
              inRegister[i] = true;
              offsetToJavaArg = offsetToJavaArg.minus(WORDSIZE);
              asm.emitMOV_Reg_RegDisp(NATIVE_PARAMETER_GPRS[gpRegistersInUse], SP, offsetToJavaArg);
              gpRegistersInUse++;
            }
          }
        }
      }

      // (3) Stack alignment
      ForwardReference dontRealignStack = null;
      int argsToPush = 0;
      if (VM.BuildFor64Addr) {
        for (int i = args.length - 1; i >= 1; i--) {
          if (!inRegister[i]) {
            TypeReference arg = args[i];
            if (arg.isLongType() || arg.isDoubleType()) {
              argsToPush += 2;
            } else {
              argsToPush ++;
            }
          }
        }
        asm.emitTEST_Reg_Imm(SP, 0x8);
        if ((argsToPush & 1) != 0) {
          dontRealignStack = asm.forwardJcc(NE);
        } else {
          dontRealignStack = asm.forwardJcc(EQ);
        }
      }

      Offset initialOffsetToFirstArg = offsetToFirstArg;
      Offset initialOffsetToLastArg = offsetToLastArg;
      // Generate argument pushing and call code upto twice, once with realignment
      ForwardReference afterCalls = null;
      for (int j = VM.BuildFor32Addr ? 1 : 0;  j < 2; j++) {
        if (j == 0) {
          adjustStack(-WORDSIZE, true);
          offsetToFirstArg = offsetToFirstArg.plus(WORDSIZE);
          offsetToLastArg = offsetToLastArg.plus(WORDSIZE);
        } else {
          if (dontRealignStack != null) dontRealignStack.resolve(asm);
          offsetToFirstArg = initialOffsetToFirstArg;
          offsetToLastArg = initialOffsetToLastArg;
          paramBytes = 0;
        }
        // (4) Stack remaining args to target function from right-to-left
        //     (NB avoid the first argument holding the target function address)
        offsetToJavaArg = offsetToLastArg;
        for (int i = args.length - 1; i >= 1; i--) {
          TypeReference arg = args[i];
          if (VM.BuildFor32Addr) {
            if (arg.isLongType() || arg.isDoubleType()) {
              asm.emitPUSH_RegDisp(SP, offsetToJavaArg.plus(WORDSIZE));
              asm.emitPUSH_RegDisp(SP, offsetToJavaArg.plus(WORDSIZE));
              offsetToJavaArg = offsetToJavaArg.plus(4 * WORDSIZE);
              offsetToFirstArg = offsetToFirstArg.plus(2 * WORDSIZE);
              offsetToLastArg = offsetToLastArg.plus(2 * WORDSIZE);
              paramBytes += 2 * WORDSIZE;
            } else {
              asm.emitPUSH_RegDisp(SP, offsetToJavaArg);
              offsetToJavaArg = offsetToJavaArg.plus(2 * WORDSIZE);
              offsetToFirstArg = offsetToFirstArg.plus(WORDSIZE);
              offsetToLastArg = offsetToLastArg.plus(WORDSIZE);
              paramBytes += WORDSIZE;
            }
          } else {
            if (!inRegister[i]) {
              if (arg.isLongType() || arg.isDoubleType()) {
                adjustStack(-WORDSIZE, true);
                asm.emitPUSH_RegDisp(SP, offsetToJavaArg.plus(WORDSIZE));
                offsetToJavaArg = offsetToJavaArg.plus(4 * WORDSIZE);
                offsetToFirstArg = offsetToFirstArg.plus(2 * WORDSIZE);
                offsetToLastArg = offsetToLastArg.plus(2 * WORDSIZE);
                paramBytes += 2 * WORDSIZE;
              } else {
                asm.emitPUSH_RegDisp(SP, offsetToJavaArg);
                offsetToJavaArg = offsetToJavaArg.plus(2 * WORDSIZE);
                offsetToFirstArg = offsetToFirstArg.plus(WORDSIZE);
                offsetToLastArg = offsetToLastArg.plus(WORDSIZE);
                paramBytes += WORDSIZE;
              }
            } else {
              if (arg.isLongType() || arg.isDoubleType()) {
                offsetToJavaArg = offsetToJavaArg.plus(2 * WORDSIZE);
              } else {
                offsetToJavaArg = offsetToJavaArg.plus(WORDSIZE);
              }
            }
          }
        }
        if (VM.VerifyAssertions) VM._assert(offsetToFirstArg.EQ(offsetToJavaArg));

        // (5) invoke target function with address given by the first argument
        if (VM.BuildFor32Addr) {
          asm.emitMOV_Reg_RegDisp(S0, SP, offsetToFirstArg);
          asm.emitCALL_Reg(S0);
        } else {
          asm.emitMOV_Reg_RegDisp_Quad(T0, SP, offsetToFirstArg);
          asm.emitCALL_Reg(T0);
        }

        // (6) pop space for arguments
        if (j == 0) {
          offsetToFirstArg = offsetToFirstArg.minus(WORDSIZE);
          offsetToLastArg = offsetToLastArg.minus(WORDSIZE);
          adjustStack(paramBytes + WORDSIZE, true);
          afterCalls = asm.forwardJMP();
        } else {
          adjustStack(paramBytes, true);
        }
      }

      if (afterCalls != null) afterCalls.resolve(asm);

      // (7) restore RVM registers
      asm.emitPOP_Reg(EDI);
      asm.emitPOP_Reg(ESI);
      asm.emitPOP_Reg(EBX);

      // (8) pop expression stack (including the first parameter)
      adjustStack(m.getParameterWords() << LG_WORDSIZE, true);

      // (9) push return value
      if (rtype.isLongType()) {
        if (VM.BuildFor32Addr) {
          asm.emitPUSH_Reg(T1);
          asm.emitPUSH_Reg(T0);
        } else {
          adjustStack(-WORDSIZE, true);
          asm.emitPUSH_Reg(T0);
        }
      } else if (rtype.isDoubleType()) {
        adjustStack(-2 * WORDSIZE, true);
        if (VM.BuildFor32Addr) {
          asm.emitFSTP_RegInd_Reg_Quad(SP, FP0);
        } else {
          asm.emitMOVSD_RegInd_Reg(SP, XMM0);
        }
      } else if (rtype.isFloatType()) {
        adjustStack(-WORDSIZE, true);
        if (VM.BuildFor32Addr) {
          asm.emitFSTP_RegInd_Reg(SP, FP0);
        } else {
          asm.emitMOVSS_RegInd_Reg(SP, XMM0);
        }
      } else if (!rtype.isVoidType()) {
        asm.emitPUSH_Reg(T0);
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * @param local index of local
   * @return offset of Java local variable (off stack pointer)
   * assuming ESP is still positioned as it was at the
   * start of the current bytecode (biStart)
   * @throws UnreachableBytecodeException when the stack heights information for
   *  the current bytecode is invalid. This can only happen when the bytecode is
   *  unreachable.
   */
  private Offset localOffset(int local) throws UnreachableBytecodeException {
    int stackHeight = stackHeights[biStart];
    // Have we computed stack height information?
    if (stackHeight < TemplateCompilerFramework.stackHeightForEmptyBasicBlock(method)) {
      // If stack heights weren't computed, the bytecode must be unreachable.
      throw new UnreachableBytecodeException();
    }
    if (VM.VerifyAssertions) VM._assert(method.getLocalWords() > local);
    return Offset.fromIntZeroExtend((stackHeights[biStart] - local) << LG_WORDSIZE);
  }

  /**
   * Translates a FP offset into an SP offset
   * assuming ESP is still positioned as it was at the
   * start of the current bytecode (biStart).
   *
   * @param offset the FP offset
   * @return the SP offset
   */
  private Offset fp2spOffset(Offset offset) {
    Offset offsetToFrameHead = Offset.fromIntSignExtend(stackHeights[biStart] << LG_WORDSIZE).minus(firstLocalOffset);
    return offset.plus(offsetToFrameHead);
  }

  /**
   * Emit dynamic linking sequence placing the offset of the given member in reg
   * @param asm assembler to generate code into
   * @param reg register to hold offset to method
   * @param ref method reference to be resolved
   * @param couldBeZero could the value in the offsets table require resolving
   */
  static void emitDynamicLinkingSequence(Assembler asm, GPR reg, MemberReference ref, boolean couldBeZero) {
    int memberId = ref.getId();
    Offset memberOffset = Offset.fromIntZeroExtend(memberId << 2);
    Offset tableOffset = Entrypoints.memberOffsetsField.getOffset();
    if (couldBeZero) {
      int retryLabel = asm.getMachineCodeIndex();            // branch here after dynamic class loading
      asm.generateJTOCloadWord(reg, tableOffset); // reg is offsets table
      if (VM.BuildFor32Addr) {
        asm.emitMOV_Reg_RegDisp(reg, reg, memberOffset);       // reg is offset of member, or 0 if member's class isn't loaded
      } else {
        asm.emitMOVSXDQ_Reg_RegDisp(reg, reg, memberOffset);       // reg is offset of member, or 0 if member's class isn't loaded
      }
      if (NEEDS_DYNAMIC_LINK == 0) {
        asm.emitTEST_Reg_Reg(reg, reg);                      // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      } else {
        asm.emitCMP_Reg_Imm(reg, NEEDS_DYNAMIC_LINK);        // reg ?= NEEDS_DYNAMIC_LINK, is field's class loaded?
      }
      ForwardReference fr = asm.forwardJcc(NE);       // if so, skip call instructions
      asm.emitPUSH_Imm(memberId);                            // pass member's dictId
      genParameterRegisterLoad(asm, 1);                      // pass 1 parameter word
      Offset resolverOffset = Entrypoints.resolveMemberMethod.getOffset();
      asm.generateJTOCcall(resolverOffset);                  // does class loading as sideffect
      asm.emitJMP_Imm(retryLabel);                           // reload reg with valid value
      fr.resolve(asm);                                       // come from Jcc above.
    } else {
      asm.generateJTOCloadWord(reg, tableOffset); // reg is offsets table
      if (VM.BuildFor32Addr) {
        asm.emitMOV_Reg_RegDisp(reg, reg, memberOffset);      // reg is offset of member
      } else {
        asm.emitMOVSXDQ_Reg_RegDisp(reg, reg, memberOffset);  // reg is offset of member
      }
    }
  }

  /**
   * OSR routine to emit code to invoke a compiled method (with known jtoc
   * offset). Treat it like a resolved invoke static, but take care of
   * this object in the case.<p>
   *
   * I have not thought about GCMaps for invoke_compiledmethod.<p>
   * TODO: Figure out what the above GCMaps comment means and fix it!
   */
  @Override
  protected void emit_invoke_compiledmethod(CompiledMethod cm) {
    Offset methodOffset = cm.getOsrJTOCoffset();
    boolean takeThis = !cm.method.isStatic();
    MethodReference ref = cm.method.getMemberRef().asMethodReference();
    genParameterRegisterLoad(ref, takeThis);
    asm.generateJTOCcall(methodOffset);
    genResultRegisterUnload(ref);
  }

  /**
   * Implementation for OSR load return address bytecode
   */
  @Override
  protected void emit_loadretaddrconst(int bcIndex) {
    asm.generateLoadReturnAddress(bcIndex);
  }

  /**
   * Generate branch for pending goto OSR mechanism
   * @param bTarget is optional, it emits a JUMP instruction, but the caller
   * is responsible for patching the target address by calling the resolve method
   * of the returned forward reference.
   */
  @Override
  protected ForwardReference emit_pending_goto(int bTarget) {
    return asm.generatePendingJMP(bTarget);
  }

  @Override
  protected void ending_method() {
    asm.noteEndOfBytecodes();
  }

}

