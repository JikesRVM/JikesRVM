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
package org.jikesrvm.compilers.opt.lir2mir.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Binary;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Return;
import org.jikesrvm.compilers.opt.ir.MIR_RotateAndMask;
import org.jikesrvm.compilers.opt.ir.MIR_Store;
import org.jikesrvm.compilers.opt.ir.MIR_Trap;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCTrapOperand;
import org.jikesrvm.compilers.opt.lir2mir.BURS;
import org.jikesrvm.compilers.opt.lir2mir.BURS_Common_Helpers;
import org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants;
import org.jikesrvm.compilers.opt.util.Bits;
import org.jikesrvm.ppc.TrapConstants;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Contains architecture-specific helper functions for BURS.
 */
abstract class BURS_Helpers extends BURS_Common_Helpers
    implements Operators, PhysicalRegisterConstants {

  BURS_Helpers(BURS burs) {
    super(burs);
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  protected final boolean SI16(Address value) {
    return (value.LE(Address.fromIntSignExtend(32767)) || value.GE(Address.fromIntSignExtend(-32768)));
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  protected final boolean SI16(int value) {
    return (value <= 32767) && (value >= -32768);
  }

  /**
   * returns true if a signed integer in 32 bits
   */
  protected final boolean SI32(long value) {
    return (value <= 0x7FFFFFFFL) && (value >= 0xFFFFFFFF80000000L);
  }

  /**
   * returns true if lower 16-bits are zero
   */
  protected final boolean U16(int value) {
    return (value & 0xffff) == 0;
  }

  /**
   * returns true if lower 16-bits are zero
   */
  protected final boolean U16(Address a) {
    return a.toWord().and(Word.fromIntZeroExtend(0xffff)).isZero();
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean MASK(int value) {
    if (value < 0) {
      value = ~value;
    }
    return POSITIVE_MASK(value);
  }


  /**
   *
   * @param value
   * @return {@code true} if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean POSITIVE_MASK(int value) {
    if (value == 0) {
      return false;
    }
    do {
      if ((value & 0x1) == 1) {
        break;
      }
      value = value >>> 1;
    } while (true);
    do {
      if ((value & 0x1) == 0) {
        return false;
      }
      value = value >>> 1;
    } while (value != 0);
    return true;
  }

  protected final boolean MASK_AND_OR(int and, int or) {
    return ((~and & or) == or) && MASK(and);
  }

  /**
   * Integer Shift Right Immediate
   */
  protected final IntConstantOperand SRI(int i, int amount) {
    return IC(i >>> amount);
  }

  /**
   * Integer And Immediate
   */
  protected final IntConstantOperand ANDI(int i, int mask) {
    return IC(i & mask);
  }

  /**
   * Calculate Lower 16 Bits
   */
  protected final IntConstantOperand CAL16(Address a) {
    return IC(Bits.PPCMaskLower16(a.toWord().toOffset()));
  }

  /**
   * Calculate Lower 16 Bits
   */
  protected final IntConstantOperand CAL16(int i) {
    return IC(Bits.PPCMaskLower16(i));
  }

  /**
   * Calculate Upper 16 Bits
   */
  protected final IntConstantOperand CAU16(Address a) {
    return IC(Bits.PPCMaskUpper16(a.toWord().toOffset()));
  }

  /**
   * Calculate Upper 16 Bits
   */
  protected final IntConstantOperand CAU16(int i) {
    return IC(Bits.PPCMaskUpper16(i));
  }

  /**
   * Mask Begin
   */
  protected final int MaskBegin(int integer) {
    int value;
    for (value = 0; integer >= 0; integer = integer << 1, value++) ;
    return value;
  }

  /**
   * Mask End
   */
  protected final int MaskEnd(int integer) {
    int value;
    for (value = 31; (integer & 0x1) == 0; integer = integer >>> 1, value--) ;
    return value;
  }

  // access functions
  protected final Register getXER() {
    return getIR().regpool.getPhysicalRegisterSet().getXER();
  }

  protected final Register getLR() {
    return getIR().regpool.getPhysicalRegisterSet().getLR();
  }

  protected final Register getCTR() {
    return getIR().regpool.getPhysicalRegisterSet().getCTR();
  }

  protected final Register getTU() {
    return getIR().regpool.getPhysicalRegisterSet().getTU();
  }

  protected final Register getTL() {
    return getIR().regpool.getPhysicalRegisterSet().getTL();
  }

  protected final Register getCR() {
    return getIR().regpool.getPhysicalRegisterSet().getCR();
  }

  /* RVM registers */
  protected final Register getJTOC() {
    return getIR().regpool.getPhysicalRegisterSet().getJTOC();
  }

  /**
   * Emit code to load a float value from the JTOC.
   * @param operator
   * @param RT
   * @param field
   */
  private void emitLFtoc(Operator operator, Register RT, RVMField field) {
    Register JTOC = regpool.getPhysicalRegisterSet().getJTOC();
    Offset offset = field.getOffset();
    int valueLow = Bits.PPCMaskLower16(offset);
    Instruction s;
    if (Bits.fits(offset, 16)) {
      s = MIR_Load.create(operator, D(RT), A(JTOC), IC(valueLow));
      EMIT(s);
    } else {
      int valueHigh = Bits.PPCMaskUpper16(offset);
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 32));
      Register reg = regpool.getAddress();
      EMIT(MIR_Binary.create(PPC_ADDIS, A(reg), A(JTOC), IC(valueHigh)));
      s = MIR_Load.create(operator, D(RT), A(reg), IC(valueLow));
      EMIT(s);
    }
  }

  /**
   * Emit code to load an Integer Constant.
   * reg must be != 0
   */
  protected final void IntConstant(Register reg, int value) {
    int lo = Bits.PPCMaskLower16(value);
    int hi = Bits.PPCMaskUpper16(value);
    if (hi != 0) {
      EMIT(MIR_Unary.create(PPC_LDIS, I(reg), IC(hi)));
      if (lo != 0) {
        EMIT(MIR_Binary.create(PPC_ADDI, I(reg), I(reg), IC(lo)));
      }
    } else {
      EMIT(MIR_Unary.create(PPC_LDI, I(reg), IC(lo)));
    }
  }

  /**
   * Emit code to get a caught exception object into a register
   */
  protected final void GET_EXCEPTION_OBJECT(Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    LocationOperand loc = new LocationOperand(-offset);
    EMIT(MIR_Load.mutate(s, PPC_LAddr, Nullary.getClearResult(s), A(FP), IC(offset), loc, TG()));
  }

  /**
   * Emit code to move a value in a register to the stack location
   * where a caught exception object is expected to be.
   */
  protected final void SET_EXCEPTION_OBJECT(Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    LocationOperand loc = new LocationOperand(-offset);
    RegisterOperand obj = (RegisterOperand) CacheOp.getRef(s);
    EMIT(MIR_Store.mutate(s, PPC_STAddr, obj, A(FP), IC(offset), loc, TG()));
  }

  /**
   * Emit code to move 32 bits from FPRs to GPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void FPR2GPR_32(Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    RegisterOperand val = (RegisterOperand) Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STFS, val, A(FP), IC(offset), null, TG()));
    EMIT(MIR_Load.mutate(s, PPC_LWZ, Unary.getClearResult(s), A(FP), IC(offset), null, TG()));
  }

  /**
   * Emits code to move 32 bits from GPRs to FPRs.<p>
   *
   * Note: intentionally uses {@code null} location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void GPR2FPR_32(Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    RegisterOperand val = (RegisterOperand) Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STW, val, A(FP), IC(offset), null, TG()));
    EMIT(MIR_Load.mutate(s, PPC_LFS, Unary.getClearResult(s), A(FP), IC(offset), null, TG()));
  }

  /**
   * Emit codes to move 64 bits from FPRs to GPRs.<p>
   *
   * Note: intentionally uses {@code null} location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void FPR2GPR_64(Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    RegisterOperand val = (RegisterOperand) Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STFD, val, A(FP), IC(offset), null, TG()));
    RegisterOperand i1 = Unary.getClearResult(s);
    if (VM.BuildFor32Addr) {
      RegisterOperand i2 = I(regpool.getSecondReg(i1.getRegister()));
      EMIT(MIR_Load.create(PPC_LWZ, i1, A(FP), IC(offset), null, TG()));
      EMIT(MIR_Load.mutate(s, PPC_LWZ, i2, A(FP), IC(offset + 4), null, TG()));
    } else {
      EMIT(MIR_Load.mutate(s, PPC_LAddr, i1, A(FP), IC(offset), null, TG()));
    }
  }

  /**
   * Emits code to move 64 bits from GPRs to FPRs.<p>
   *
   * Note: intentionally uses {@code null} location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void GPR2FPR_64(Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    RegisterOperand i1 = (RegisterOperand) Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STAddr, i1, A(FP), IC(offset), null, TG()));
    if (VM.BuildFor32Addr) {
      RegisterOperand i2 = I(regpool.getSecondReg(i1.getRegister()));
      EMIT(MIR_Store.create(PPC_STW, i2, A(FP), IC(offset + 4), null, TG()));
    }
    EMIT(MIR_Load.mutate(s, PPC_LFD, Unary.getClearResult(s), A(FP), IC(offset), null, TG()));
  }

  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  protected final void PROLOGUE(Instruction s) {
    if (VM.BuildFor32Addr) {
      int numFormals = Prologue.getNumberOfFormals(s);
      int numLongs = 0;
      for (int i = 0; i < numFormals; i++) {
        if (Prologue.getFormal(s, i).getType().isLongType()) numLongs++;
      }
      if (numLongs != 0) {
        Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals + numLongs);
        for (int sidx = 0, s2idx = 0; sidx < numFormals; sidx++) {
          RegisterOperand sForm = Prologue.getClearFormal(s, sidx);
          Prologue.setFormal(s2, s2idx++, sForm);
          if (sForm.getType().isLongType()) {
            Prologue.setFormal(s2, s2idx++, I(regpool.getSecondReg(sForm.getRegister())));
          }
        }
        EMIT(s2);
      } else {
        EMIT(s);
      }
    } else {
      EMIT(s);
    }
  }

  /**
   * Expand a call instruction.
   */
  protected final void CALL(Instruction s) {
    Operand target = Call.getClearAddress(s);
    MethodOperand meth = Call.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    if (VM.BuildFor32Addr) {
      for (int pNum = 0; pNum < numParams; pNum++) {
        if (Call.getParam(s, pNum).getType().isLongType()) {
          longParams++;
        }
      }
    }

    // Step 2: Figure out what the result and result2 values will be
    RegisterOperand result = Call.getClearResult(s);
    RegisterOperand result2 = null;
    if (VM.BuildFor32Addr) {
      if (result != null && result.getType().isLongType()) {
        result2 = I(regpool.getSecondReg(result.getRegister()));
      }
    }

    // Step 3: Figure out what the operator is going to be
    Operator callOp;
    if (target instanceof RegisterOperand) {
      // indirect call through target (contains code addr)
      Register ctr = regpool.getPhysicalRegisterSet().getCTR();
      EMIT(MIR_Move.create(PPC_MTSPR, A(ctr), (RegisterOperand) target));
      target = null;
      callOp = PPC_BCTRL;
    } else if (target instanceof BranchOperand) {
      // Earlier analysis has tagged this call as recursive,
      // set up for a direct call.
      callOp = PPC_BL;
    } else {
      throw new OptimizingCompilerException("Unexpected target operand " + target + " to call " + s);
    }

    // Step 4: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required. We'll hope the
    // opt compiler can manage to make this more efficient than it looks.
    Operand[] params = new Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getClearParam(s, i);
    }
    EMIT(MIR_Call.mutate(s, callOp, result, result2, (BranchOperand) target, meth, numParams + longParams));
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      if (VM.BuildFor32Addr) {
        if (param instanceof RegisterOperand) {
          RegisterOperand rparam = (RegisterOperand) param;
          if (rparam.getType().isLongType()) {
            MIR_Call.setParam(s, mirCallIdx++, L(regpool.getSecondReg(rparam.getRegister())));
          }
        }
      }
    }
  }

  /**
   * Expand a syscall instruction.
   */
  protected final void SYSCALL(Instruction s) {
    burs.ir.setHasSysCall(true);
    Operand target = Call.getClearAddress(s);
    MethodOperand meth = Call.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    if (VM.BuildFor32Addr) {
      for (int pNum = 0; pNum < numParams; pNum++) {
        if (Call.getParam(s, pNum).getType().isLongType()) {
          longParams++;
        }
      }
    }

    // Step 2: Figure out what the result and result2 values will be
    RegisterOperand result = Call.getClearResult(s);
    RegisterOperand result2 = null;
    if (VM.BuildFor32Addr) {
      if (result != null && result.getType().isLongType()) {
        result2 = I(regpool.getSecondReg(result.getRegister()));
      }
    }

    // Step 3: Figure out what the operator is going to be
    Operator callOp;
    if (target instanceof RegisterOperand) {
      // indirect call through target (contains code addr)
      Register ctr = regpool.getPhysicalRegisterSet().getCTR();
      EMIT(MIR_Move.create(PPC_MTSPR, A(ctr), (RegisterOperand) target));
      target = null;
      callOp = PPC_BCTRL_SYS;
    } else if (target instanceof BranchOperand) {
      // Earlier analysis has tagged this call as recursive,
      // set up for a direct call.
      callOp = PPC_BL_SYS;
    } else {
      throw new OptimizingCompilerException("Unexpected target operand " + target + " to call " + s);
    }

    // Step 4: Mutate the SysCall to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed
    // arguments, so some amount of copying is required. We'll hope the
    // opt compiler can manage to make this more efficient than it looks.
    Operand[] params = new Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getClearParam(s, i);
    }
    EMIT(MIR_Call.mutate(s, callOp, result, result2, (BranchOperand) target, meth, numParams + longParams));
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      if (VM.BuildFor32Addr) {
        if (param instanceof RegisterOperand) {
          RegisterOperand rparam = (RegisterOperand) param;
          if (rparam.getType().isLongType()) {
            MIR_Call.setParam(s, mirCallIdx++, L(regpool.getSecondReg(rparam.getRegister())));
          }
        }
      }
    }
  }

  protected final void RETURN(Instruction s, Operand value) {
    if (value != null) {
      RegisterOperand rop = (RegisterOperand) value;
      if (VM.BuildFor32Addr && value.getType().isLongType()) {
        Register pair = regpool.getSecondReg(rop.getRegister());
        EMIT(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), I(pair)));
      } else {
        EMIT(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), null));
      }
    } else {
      EMIT(MIR_Return.mutate(s, PPC_BLR, null, null));
    }
  }

  protected final void SHL_USHR(Instruction s, RegisterOperand def, RegisterOperand left,
                                IntConstantOperand shift1, IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x <= y) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, IC((32 - (y - x)) & 0x1f), IC(y), IC(31)));
    } else {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, IC(x - y), IC(y), IC(31 - (x - y))));
    }
  }

  protected final void USHR_SHL(Instruction s, RegisterOperand def, RegisterOperand left,
                                IntConstantOperand shift1, IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x <= y) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, IC(y - x), IC(0), IC(31 - y)));
    } else {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, IC((32 - (x - y)) & 0x1f), IC(x - y), IC(31 - y)));
    }
  }

  protected final void USHR_AND(Instruction s, RegisterOperand Def, RegisterOperand left,
                                IntConstantOperand Mask, IntConstantOperand Shift) {
    int shift = Shift.value;
    int mask = Mask.value;
    int MB = MaskBegin(mask);
    int ME = MaskEnd(mask);
    if (shift > ME) {           // result should be 0
      EMIT(MIR_Unary.create(PPC_LDI, Def, IC(0)));
      return;
    } else if (shift > MB) {
      MB = shift;
    }
    EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC((32 - shift) & 0x1f), IC(MB), IC(ME)));
  }

  protected final void AND_USHR(Instruction s, RegisterOperand Def, RegisterOperand left,
                                IntConstantOperand Mask, IntConstantOperand Shift) {
    int shift = Shift.value;
    int mask = Mask.value;
    int MB = MaskBegin(mask);
    int ME = MaskEnd(mask);
    if ((MB + shift) >= 32) {                   // result should be 0
      EMIT(MIR_Unary.create(PPC_LDI, Def, IC(0)));
      return;
    }
    MB += shift;
    ME += shift;
    if (ME >= 32) {
      ME = 31;
    }
    EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC((32 - shift) & 0x1f), IC(MB), IC(ME)));
  }

  protected final void AND_MASK(Instruction s, RegisterOperand Def, RegisterOperand left,
                                IntConstantOperand Mask) {
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC(0), IC((ME + 1) & 0x1f), IC(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC(0), IC(MB), IC(ME)));
    }
  }

  /**
   * emit basic code to handle an INT_IFCMP when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP(Instruction s, RegisterOperand val1, Operand val2, ConditionOperand cond,
                           boolean immediate) {
    RegisterOperand cr = regpool.makeTempCondition();
    Operator op;
    if (immediate) {
      op = cond.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else {
      op = cond.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, val1, val2));
    EMIT(MIR_CondBranch.mutate(s,
                               PPC_BCOND,
                               cr.copyD2U(),
                               new PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  /**
   * emit basic code to handle an INT_IFCMP when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP64(Instruction s, RegisterOperand val1, Operand val2, ConditionOperand cond,
                             boolean immediate) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    RegisterOperand cr = regpool.makeTempCondition();
    Operator op;
    if (immediate) {
      op = cond.isUNSIGNED() ? PPC64_CMPLI : PPC64_CMPI;
    } else {
      op = cond.isUNSIGNED() ? PPC64_CMPL : PPC64_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, val1, val2));
    EMIT(MIR_CondBranch.mutate(s,
                               PPC_BCOND,
                               cr.copyD2U(),
                               new PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  /**
   * emit basic code to handle an INT_IFCMP2 when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP2(Instruction s, RegisterOperand val1, Operand val2, ConditionOperand cond1,
                            ConditionOperand cond2, boolean immediate) {
    Operator op1;
    Operator op2;
    if (immediate) {
      op1 = cond1.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
      op2 = cond2.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else {
      op1 = cond1.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
      op2 = cond2.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    if (op1 == op2) {
      RegisterOperand cr = regpool.makeTempCondition();
      EMIT(MIR_Binary.create(op1, cr, val1, val2));
      EMIT(MIR_CondBranch2.mutate(s,
                                  PPC_BCOND2,
                                  cr.copyD2U(),
                                  new PowerPCConditionOperand(cond1),
                                  IfCmp2.getTarget1(s),
                                  IfCmp2.getBranchProfile1(s),
                                  new PowerPCConditionOperand(cond2),
                                  IfCmp2.getTarget2(s),
                                  IfCmp2.getBranchProfile2(s)));
    } else {
      RegisterOperand cr1 = regpool.makeTempCondition();
      RegisterOperand cr2 = regpool.makeTempCondition();
      EMIT(MIR_Binary.create(op1, cr1, val1, val2));
      EMIT(MIR_Binary.create(op2, cr2, val1, val2));
      EMIT(MIR_CondBranch.create(PPC_BCOND,
                                 cr1.copyD2U(),
                                 new PowerPCConditionOperand(cond1),
                                 IfCmp2.getTarget1(s),
                                 IfCmp2.getBranchProfile1(s)));
      EMIT(MIR_CondBranch.mutate(s,
                                 PPC_BCOND,
                                 cr2.copyD2U(),
                                 new PowerPCConditionOperand(cond2),
                                 IfCmp2.getTarget2(s),
                                 IfCmp2.getBranchProfile2(s)));
    }
  }

  /**
   * Uses the record capability to avoid compare
   */
  protected final void CMP_ZERO(Instruction s, Operator op, RegisterOperand def, Operand left,
                                ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    if (!def.getRegister().spansBasicBlock()) {
      def.setRegister(regpool.getPhysicalRegisterSet().getTemp());
    }
    EMIT(MIR_Unary.create(op, def, left));
    EMIT(MIR_CondBranch.mutate(s,
                               PPC_BCOND,
                               CR(0),
                               new PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  protected final void CMP_ZERO(Instruction s, Operator op, RegisterOperand def, RegisterOperand left,
                                Operand right, ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    if (!def.getRegister().spansBasicBlock()) {
      def.setRegister(regpool.getPhysicalRegisterSet().getTemp());
    }
    EMIT(MIR_Binary.create(op, def, left, right));
    EMIT(MIR_CondBranch.mutate(s,
                               PPC_BCOND,
                               CR(0),
                               new PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  protected final void CMP_ZERO_AND_MASK(Instruction s, RegisterOperand def, RegisterOperand left,
                                         IntConstantOperand Mask, ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, IC(0), IC((ME + 1) & 0x1f), IC(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, IC(0), IC(MB), IC(ME)));
    }
    EMIT(MIR_CondBranch.mutate(s,
                               PPC_BCOND,
                               CR(0),
                               new PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  // boolean compare
  // Support for boolean cmp
  private ConditionOperand cc;
  private Operand val1;
  private Operand val2;
  private boolean isAddress;

  protected final void PUSH_BOOLCMP(ConditionOperand c, Operand v1, Operand v2, boolean address) {
    if (VM.VerifyAssertions) VM._assert(cc == null);
    cc = c;
    val1 = v1;
    val2 = v2;
    isAddress = address;
  }

  protected final void FLIP_BOOLCMP() {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    cc = cc.flipCode();
  }

  protected final void EMIT_PUSHED_BOOLCMP(RegisterOperand res) {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    if (isAddress) {
      if (val2 instanceof IntConstantOperand) {
        BOOLEAN_CMP_ADDR_IMM(res, cc, R(val1), IC(val2));
      } else {
        BOOLEAN_CMP_ADDR(res, cc, R(val1), R(val2));
      }
    } else {
      if (val2 instanceof IntConstantOperand) {
        BOOLEAN_CMP_INT_IMM(res, cc, R(val1), IC(val2));
      } else {
        BOOLEAN_CMP_INT(res, cc, R(val1), R(val2));
      }
    }
    if (VM.VerifyAssertions) {
      cc = null;
      val1 = null;
      val2 = null;
    }
  }

  protected final void EMIT_BOOLCMP_BRANCH(BranchOperand target, BranchProfileOperand bp) {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    RegisterOperand cr = regpool.makeTempCondition();
    Operator op;
    if (VM.BuildFor64Addr && isAddress) {
      if (val2 instanceof IntConstantOperand) {
        op = cc.isUNSIGNED() ? PPC64_CMPLI : PPC64_CMPI;
      } else {
        op = cc.isUNSIGNED() ? PPC64_CMPL : PPC64_CMP;
      }
    } else if (val2 instanceof IntConstantOperand) {
      op = cc.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else {
      op = cc.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, R(val1), val2));
    EMIT(MIR_CondBranch.create(PPC_BCOND, cr.copyD2U(), new PowerPCConditionOperand(cc), target, bp));
    if (VM.VerifyAssertions) {
      cc = null;
      val1 = null;
      val2 = null;
    }
  }

  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 199
   */
  protected final void BOOLEAN_CMP_INT_IMM(RegisterOperand def, ConditionOperand cmp, RegisterOperand one,
                                           IntConstantOperand two) {
    Register t1, t = regpool.getInteger();
    Register zero = regpool.getPhysicalRegisterSet().getTemp();
    int value = two.value;
    switch (cmp.value) {
      case ConditionOperand.EQUAL:
        if (value == 0) {
          EMIT(MIR_Unary.create(PPC_CNTLZW, I(t), one));
        } else {
          EMIT(MIR_Binary.create(PPC_SUBFIC, I(t), one, IC(value)));
          EMIT(MIR_Unary.create(PPC_CNTLZW, I(t), I(t)));
        }
        EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(LOG_BITS_IN_INT)));
        break;
      case ConditionOperand.NOT_EQUAL:
        if (value == 0) {
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one));
            EMIT(MIR_Binary.create(PPC_ADDIC, A(t), A(t1), IC(-1)));
            EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t), A(t1)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDIC, A(t), one, IC(-1)));
            EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t), one.copyRO()));
          }
        } else {
          t1 = regpool.getAddress();
          if (VM.BuildFor64Addr) {
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one));
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(t1), A(t1), IC(value)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(t1), one, IC(value)));
          }
          EMIT(MIR_Binary.create(PPC_ADDIC, A(t), A(t1), IC(-1)));
          EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t), A(t1)));
        }
        break;
      case ConditionOperand.LESS:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_SRWI, def, one, IC(BITS_IN_INT - 1)));
        } else if (value > 0) {
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), A(t1), IC(value - 1)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value - 1)));
          }
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else if (value != 0xFFFF8000) {
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), A(t1), IC(value - 1)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value - 1)));
          }
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        } else {                  // value = 0xFFFF8000
          t1 = regpool.getAddress();
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          EMIT(MIR_Unary.create(PPC_LDI, A(t1), IC(0xFFFF8000)));
          EMIT(MIR_Binary.create(PPC_ADDI, A(t1), A(t1), IC(-1))); //constructing 0xFFFF7FFF
          if (VM.BuildFor64Addr) {
            Register t2 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t2), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_SUBFC, I(zero), A(t2), I(t1)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFC, I(zero), one.copyRO(), I(t1)));
          }
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        }
        break;
      case ConditionOperand.GREATER:
        if (value == 0) {
          EMIT(MIR_Unary.create(PPC_NEG, I(t), one));
          EMIT(MIR_Binary.create(PPC_ANDC, I(t), I(t), one.copyRO()));
          EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(BITS_IN_INT - 1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRAWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), A(t1), IC(-value - 1)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value - 1)));
          }
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else {
          t1 = regpool.getInteger();
          EMIT(MIR_Binary.create(PPC_SRAWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), A(t1), IC(-value - 1)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value - 1)));
          }
          EMIT(MIR_Unary.create(PPC_LDI, I(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_ADDE, def, I(t), I(t1)));
        }
        break;
      case ConditionOperand.LESS_EQUAL:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_ADDI, I(t), one, IC(-1)));
          EMIT(MIR_Binary.create(PPC_OR, I(t), I(t), one.copyRO()));
          EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(BITS_IN_INT - 1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_SUBFIC, I(zero), A(t1), IC(value)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value)));
          }
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else {
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_SUBFIC, I(zero), A(t1), IC(value)));
          } else {
            EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value)));
          }
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        }
        break;
      case ConditionOperand.GREATER_EQUAL:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
          EMIT(MIR_Binary.create(PPC_XORI, def, I(t), IC(1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRAWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), A(t1), IC(-value)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value)));
          }
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else if (value != 0xFFFF8000) {
          t1 = regpool.getInteger();
          EMIT(MIR_Unary.create(PPC_LDI, I(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAWI, I(t), one, IC(BITS_IN_INT - 1)));
          if (VM.BuildFor64Addr) {
            t1 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), A(t1), IC(-value)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value)));
          }
          EMIT(MIR_Binary.create(PPC_ADDE, def, I(t), I(t1)));
        } else { //value 0xFFFF8000
          t1 = regpool.getInteger();
          EMIT(MIR_Unary.create(PPC_LDI, I(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAWI, I(t), one, IC(BITS_IN_INT - 1)));
          EMIT(MIR_Binary.create(PPC_SLWI, I(zero), I(t1), IC(15))); //constructing 0x00008000
          if (VM.BuildFor64Addr) {
            Register t2 = regpool.getAddress();
            EMIT(MIR_Unary.create(PPC64_EXTSW, A(t2), one.copyRO()));
            EMIT(MIR_Binary.create(PPC_ADDC, I(zero), A(t2), I(zero)));
          } else {
            EMIT(MIR_Binary.create(PPC_ADDC, I(zero), one.copyRO(), I(zero)));
          }
          EMIT(MIR_Binary.create(PPC_ADDE, def, I(t), I(t1)));
        }
        break;
      case ConditionOperand.HIGHER:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.LOWER:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.HIGHER_EQUAL:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.LOWER_EQUAL:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
        //KV: this code snippet does not the right thing for BOOLEAN_ADDRESS_CMP, so I doubt it will work here.
        //KV: my guess is that value is an unsigned immediate, used by SUBFIC as signed
        //EMIT(MIR_Unary.create(PPC_LDI, I(t), IC(-1)));
        //if(VM.BuildFor64Addr) {
        //t1 = regpool.getAddress();
        //          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t1), one));
        //EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), A(t1), IC(value)));
        //} else {
        //EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one, IC(value)));
        //}
        //EMIT(MIR_Unary.create(PPC_SUBFZE, def, I(t)));
        break;

      default:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BOOLEAN_CMP_ADDR_IMM(RegisterOperand def, ConditionOperand cmp, RegisterOperand one,
                                            IntConstantOperand two) {
    Register t1, t = regpool.getAddress();
    Register zero = regpool.getPhysicalRegisterSet().getTemp();
    int value = two.value;
    switch (cmp.value) {
      case ConditionOperand.EQUAL:
        if (value == 0) {
          EMIT(MIR_Unary.create(PPC_CNTLZAddr, I(t), one));
        } else {
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(t), one, IC(value)));
          EMIT(MIR_Unary.create(PPC_CNTLZAddr, I(t), A(t)));
        }
        EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(LOG_BITS_IN_ADDRESS)));
        break;
      case ConditionOperand.NOT_EQUAL:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_ADDIC, A(t), one, IC(-1)));
          EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t), one.copyRO()));
        } else {
          t1 = regpool.getAddress();
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(t1), one, IC(value)));
          EMIT(MIR_Binary.create(PPC_ADDIC, A(t), A(t1), IC(-1)));
          EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t), A(t1)));
        }
        break;
      case ConditionOperand.LESS:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_SRAddrI, def, one, IC(BITS_IN_ADDRESS - 1)));
        } else if (value > 0) {
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one, IC(value - 1)));
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else if (value != 0xFFFF8000) {
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value - 1)));
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        } else {                  // value = 0xFFFF8000
          t1 = regpool.getAddress();
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Unary.create(PPC_LDI, A(t1), IC(0xFFFF8000))); //constructing 0xFFFF7FFF
          EMIT(MIR_Binary.create(PPC_ADDI, A(t1), A(t1), IC(-1)));
          EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), one.copyRO(), A(t1)));
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        }
        break;
      case ConditionOperand.GREATER:
        if (value == 0) {
          EMIT(MIR_Unary.create(PPC_NEG, A(t), one));
          EMIT(MIR_Binary.create(PPC_ANDC, A(t), A(t), one.copyRO()));
          EMIT(MIR_Binary.create(PPC_SRAddrI, def, A(t), IC(BITS_IN_ADDRESS - 1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value - 1)));
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else {
          t1 = regpool.getInteger();
          EMIT(MIR_Unary.create(PPC_LDI, I(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value - 1)));
          EMIT(MIR_Binary.create(PPC_ADDE, def, I(t), I(t1)));
        }
        break;
      case ConditionOperand.LESS_EQUAL:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_ADDI, A(t), one, IC(-1)));
          EMIT(MIR_Binary.create(PPC_OR, A(t), A(t), one.copyRO()));
          EMIT(MIR_Binary.create(PPC_SRAddrI, def, A(t), IC(BITS_IN_ADDRESS - 1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value)));
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else {
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one.copyRO(), IC(value)));
          EMIT(MIR_Unary.create(PPC_ADDME, def, I(t)));
        }
        break;
      case ConditionOperand.GREATER_EQUAL:
        if (value == 0) {
          EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_XORI, def, I(t), IC(1)));
        } else if (value >= 0) {
          EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value)));
          EMIT(MIR_Unary.create(PPC_ADDZE, def, I(t)));
        } else if (value != 0xFFFF8000) {
          t1 = regpool.getInteger();
          EMIT(MIR_Unary.create(PPC_LDI, I(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_ADDIC, A(zero), one.copyRO(), IC(-value)));
          EMIT(MIR_Binary.create(PPC_ADDE, def, I(t), I(t1)));
        } else { //value 0xFFFF8000
          t1 = regpool.getAddress();
          EMIT(MIR_Unary.create(PPC_LDI, A(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAAddrI, A(t), one, IC(BITS_IN_ADDRESS - 1)));
          EMIT(MIR_Binary.create(PPC_SLWI, A(zero), A(t1), IC(15))); //constructing 0x00008000
          EMIT(MIR_Binary.create(PPC_ADDC, A(zero), one.copyRO(), A(zero)));
          EMIT(MIR_Binary.create(PPC_ADDE, def, A(t), A(t1)));
        }
        break;
      case ConditionOperand.HIGHER:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.LOWER:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.HIGHER_EQUAL:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
        break;
      case ConditionOperand.LOWER_EQUAL:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
        //KV: this code snippet does not the rigth thing :
        //KV: my guess is that value is an unsigned immediate, used by SUBFIC as signed
        // EMIT(MIR_Unary.create(PPC_LDI, I(t), IC(-1)));
        // EMIT(MIR_Binary.create(PPC_SUBFIC, A(zero), one, IC(value)));
        // EMIT(MIR_Unary.create(PPC_SUBFZE, def, I(t)));
        break;

      default:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BOOLEAN_CMP_INT(RegisterOperand def, ConditionOperand cmp, RegisterOperand one,
                                       RegisterOperand two) {
    Register t1, zero, t = regpool.getInteger();
    switch (cmp.value) {
      case ConditionOperand.EQUAL: {
        EMIT(MIR_Binary.create(PPC_SUBF, I(t), one, two));
        EMIT(MIR_Unary.create(PPC_CNTLZW, I(t), I(t)));
        EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(LOG_BITS_IN_INT)));
      }
      break;
      case ConditionOperand.NOT_EQUAL: {
        t1 = regpool.getAddress();
        EMIT(MIR_Binary.create(PPC_SUBF, I(t), one, two));
        if (VM.BuildFor64Addr) {
          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t), I(t)));
        }
        EMIT(MIR_Binary.create(PPC_ADDIC, A(t1), A(t), IC(-1)));
        EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t1), A(t)));
      }
      break;
      case ConditionOperand.LESS_EQUAL: {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRWI, I(t), one, IC(BITS_IN_INT - 1)));
        EMIT(MIR_Binary.create(PPC_SRAWI, I(t1), two, IC(BITS_IN_INT - 1)));
        if (VM.BuildFor64Addr) {
          Register t2 = regpool.getAddress();
          Register t3 = regpool.getAddress();
          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t2), one.copyRO()));
          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t3), two.copyRO()));
          EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), A(t2), A(t3)));
        } else {
          EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), one.copyRO(), two.copyRO()));
        }
        EMIT(MIR_Binary.create(PPC_ADDE, def, I(t1), I(t)));
      }
      break;
      case ConditionOperand.GREATER_EQUAL: {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRWI, I(t), two, IC(BITS_IN_INT - 1)));
        EMIT(MIR_Binary.create(PPC_SRAWI, I(t1), one, IC(BITS_IN_INT - 1)));
        if (VM.BuildFor64Addr) {
          Register t2 = regpool.getAddress();
          Register t3 = regpool.getAddress();
          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t2), one.copyRO()));
          EMIT(MIR_Unary.create(PPC64_EXTSW, A(t3), two.copyRO()));
          EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), A(t3), A(t2)));
        } else {
          EMIT(MIR_Binary.create(PPC_SUBFC, I(zero), two.copyRO(), one.copyRO()));
        }
        EMIT(MIR_Binary.create(PPC_ADDE, def, I(t1), I(t)));
      }
      break;
      default:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_INT, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BOOLEAN_CMP_ADDR(RegisterOperand def, ConditionOperand cmp, RegisterOperand one,
                                        RegisterOperand two) {
    Register t1, zero, t = regpool.getAddress();
    switch (cmp.value) {
      case ConditionOperand.EQUAL: {
        EMIT(MIR_Binary.create(PPC_SUBF, A(t), one, two));
        EMIT(MIR_Unary.create(PPC_CNTLZAddr, I(t), A(t)));
        EMIT(MIR_Binary.create(PPC_SRWI, def, I(t), IC(LOG_BITS_IN_ADDRESS)));
      }
      break;
      case ConditionOperand.NOT_EQUAL: {
        t1 = regpool.getAddress();
        EMIT(MIR_Binary.create(PPC_SUBF, A(t), one, two));
        EMIT(MIR_Binary.create(PPC_ADDIC, A(t1), A(t), IC(-1)));
        EMIT(MIR_Binary.create(PPC_SUBFE, def, A(t1), A(t)));
      }
      break;
      case ConditionOperand.LESS_EQUAL: {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), one, IC(BITS_IN_ADDRESS - 1)));
        EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t1), two, IC(BITS_IN_ADDRESS - 1)));
        EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), one.copyRO(), two.copyRO()));
        EMIT(MIR_Binary.create(PPC_ADDE, def, I(t1), I(t)));
      }
      break;
      case ConditionOperand.GREATER_EQUAL: {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRAddrI, I(t), two, IC(BITS_IN_ADDRESS - 1)));
        EMIT(MIR_Binary.create(PPC_SRAAddrI, I(t1), one, IC(BITS_IN_ADDRESS - 1)));
        EMIT(MIR_Binary.create(PPC_SUBFC, A(zero), two.copyRO(), one.copyRO()));
        EMIT(MIR_Binary.create(PPC_ADDE, def, I(t1), I(t)));
      }
      break;
      default:
        EMIT(BooleanCmp.create(BOOLEAN_CMP_ADDR, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BYTE_LOAD(Instruction s, Operator opcode, RegisterOperand def,
                                 RegisterOperand left, Operand right, LocationOperand loc,
                                 Operand guard) {
    RegisterOperand reg1 = regpool.makeTempInt();
    EMIT(MIR_Load.mutate(s, opcode, reg1, left, right, loc, guard));
    EMIT(MIR_Unary.create(PPC_EXTSB, def, reg1.copyD2U()));
  }

  private int PowerOf2(int v) {
    int i = 31;
    int power = -1;
    for (; v != 0; v = v << 1, i--) {
      if (v < 0) {
        if (power == -1) {
          power = i;
        } else {
          return -1;
        }
      }
    }
    return power;
  }

  protected final void INT_DIV_IMM(Instruction s, RegisterOperand def, RegisterOperand left,
                                   RegisterOperand c, IntConstantOperand right) {
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.create(PPC_SRAWI, c, left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, def, c.copyD2U()));
    } else {
      IntConstant(c.getRegister(), right.value);
      EMIT(MIR_Binary.mutate(s, PPC_DIVW, def, left, c));
    }
  }

  protected final void INT_REM(Instruction s, RegisterOperand def, RegisterOperand left,
                               RegisterOperand right) {
    Register temp = regpool.getInteger();
    EMIT(MIR_Binary.mutate(s, PPC_DIVW, I(temp), left, right));
    EMIT(MIR_Binary.create(PPC_MULLW, I(temp), I(temp), right.copyU2U()));
    EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
  }

  protected final void INT_REM_IMM(Instruction s, RegisterOperand def, RegisterOperand left,
                                   RegisterOperand c, IntConstantOperand right) {
    Register temp = regpool.getInteger();
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.mutate(s, PPC_SRAWI, I(temp), left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, I(temp), I(temp)));
      EMIT(MIR_Binary.create(PPC_SLWI, I(temp), I(temp), IC(power)));
      EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
    } else {
      IntConstant(c.getRegister(), right.value);
      EMIT(MIR_Binary.mutate(s, PPC_DIVW, I(temp), left, c));
      EMIT(MIR_Binary.create(PPC_MULLW, I(temp), I(temp), c.copyU2U()));
      EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
    }
  }

  /**
   * Conversion
   */
  protected final void INT_2LONG(Instruction s, RegisterOperand def, RegisterOperand left) {
    Register defHigh = def.getRegister();
    if (VM.BuildFor32Addr) {
      Register defLow = regpool.getSecondReg(defHigh);
      EMIT(MIR_Move.mutate(s, PPC_MOVE, I(defLow), left));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), left.copyU2U(), IC(31)));
    } else {
      EMIT(MIR_Unary.create(PPC64_EXTSW, def, left));
    }
  }

  protected final void INT_2ADDRZerExt(Instruction s, RegisterOperand def, RegisterOperand left) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    EMIT(MIR_Unary.create(PPC64_EXTZW, def, left));
  }

  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 83
   */
  protected final void INT_2DOUBLE(Instruction s, RegisterOperand def, RegisterOperand left) {
    Register res = def.getRegister();
    Register src = left.getRegister();
    Register FP = regpool.getPhysicalRegisterSet().getFP();
    RegisterOperand temp = regpool.makeTempInt();
    int p = burs.ir.stackManager.allocateSpaceForConversion();
    EMIT(MIR_Unary.mutate(s, PPC_LDIS, temp, IC(0x4330)));
    // TODO: valid location?
    EMIT(MIR_Store.create(PPC_STW, I(temp.getRegister()), A(FP), IC(p), new TrueGuardOperand()));
    Register t1 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_XORIS, I(t1), I(src), IC(0x8000)));
    EMIT(MIR_Store.create(PPC_STW, I(t1), A(FP), IC(p + 4), new TrueGuardOperand()));
    EMIT(MIR_Load.create(PPC_LFD, D(res), A(FP), IC(p)));
    Register tempF = regpool.getDouble();
    emitLFtoc(PPC_LFD, tempF, Entrypoints.I2DconstantField);
    EMIT(MIR_Binary.create(PPC_FSUB, D(res), D(res), D(tempF)));
  }

  // LONG arithmetic:
  protected final void LONG_2INT(Instruction s, RegisterOperand def, RegisterOperand left) {
    if (VM.BuildFor32Addr) {
      Register srcHigh = left.getRegister();
      Register srcLow = regpool.getSecondReg(srcHigh);
      EMIT(MIR_Move.mutate(s, PPC_MOVE, def, I(srcLow)));
    } else {
      EMIT(MIR_Move.create(PPC_MOVE, def, left));
    }
  }

  protected final void LONG_MOVE(Instruction s, RegisterOperand def, RegisterOperand left) {
    Register defReg = def.getRegister();
    Register leftReg = left.getRegister();
    if (VM.BuildFor32Addr) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defReg), I(leftReg)));
      EMIT(MIR_Move.create(PPC_MOVE, I(regpool.getSecondReg(defReg)), I(regpool.getSecondReg(leftReg))));
    } else {
      EMIT(MIR_Move.create(PPC_MOVE, L(defReg), L(leftReg)));
    }
  }

  protected final void LONG_CONSTANT(Instruction s, RegisterOperand def, AddressConstantOperand left) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    LONG_CONSTANT(s, def, LC(left.value.toLong()));
  }

  protected final void LONG_CONSTANT(Instruction s, RegisterOperand def, LongConstantOperand left) {
    if (VM.BuildFor32Addr) {
      long value = left.value;
      int valueHigh = (int) (value >> 32);
      int valueLow = (int) (value & 0xffffffff);
      Register register = def.getRegister();
      IntConstant(register, valueHigh);
      IntConstant(regpool.getSecondReg(register), valueLow);
    } else {
      long value = left.value;
      int bytes01 = (int) (value & 0x000000000000ffffL);
      int bytes23 = (int) ((value & 0x00000000ffff0000L) >>> 16);
      int bytes45 = (int) ((value & 0x0000ffff00000000L) >>> 32);
      int bytes67 = (int) ((value & 0xffff000000000000L) >>> 48);
      Register register = def.getRegister();
      Register temp1 = regpool.getLong();
      if (Bits.fits(value, 16)) {
        EMIT(MIR_Unary.create(PPC_LDI, L(register), IC(bytes01)));
      } else if (Bits.fits(value, 32)) {
        EMIT(MIR_Unary.create(PPC_LDIS, L(register), IC(bytes23)));
        if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
      } else if (Bits.fits(value, 48)) {
        EMIT(MIR_Unary.create(PPC_LDI, L(register), IC(bytes45)));
        if (bytes45 != 0) EMIT(MIR_Binary.create(PPC64_SLDI, L(register), L(register), IC(32)));
        if (bytes23 != 0) EMIT(MIR_Binary.create(PPC_ORIS, L(register), L(register), IC(bytes23)));
        if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
      } else {
        EMIT(MIR_Unary.create(PPC_LDIS, L(register), IC(bytes67)));
        if (bytes45 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes45)));
        EMIT(MIR_Binary.create(PPC64_SLDI, L(register), L(register), IC(32)));
        if (bytes23 != 0) EMIT(MIR_Binary.create(PPC_ORIS, L(register), L(register), IC(bytes23)));
        if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
      }
    }
  }

  protected final void LONG_ADD(Instruction s, RegisterOperand def, RegisterOperand left,
                                RegisterOperand right) {
    if (VM.BuildFor32Addr) {
      Register defReg = def.getRegister();
      Register leftReg = left.getRegister();
      Register rightReg = right.getRegister();
      EMIT(MIR_Binary.create(PPC_ADDC,
                             I(regpool.getSecondReg(defReg)),
                             I(regpool.getSecondReg(leftReg)),
                             I(regpool.getSecondReg(rightReg))));
      EMIT(MIR_Binary.create(PPC_ADDE, I(defReg), I(leftReg), I(rightReg)));
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  /* Notice: switching operands! */
  protected final void LONG_SUB(Instruction s, RegisterOperand def, RegisterOperand left,
                                RegisterOperand right) {
    if (VM.BuildFor32Addr) {
      Register defReg = def.getRegister();
      Register leftReg = right.getRegister();
      Register rightReg = left.getRegister();
      EMIT(MIR_Binary.create(PPC_SUBFC,
                             I(regpool.getSecondReg(defReg)),
                             I(regpool.getSecondReg(leftReg)),
                             I(regpool.getSecondReg(rightReg))));
      EMIT(MIR_Binary.create(PPC_SUBFE, I(defReg), I(leftReg), I(rightReg)));
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  protected final void LONG_NEG(Instruction s, RegisterOperand def, RegisterOperand left) {
    if (VM.BuildFor32Addr) {
      Register defReg = def.getRegister();
      Register leftReg = left.getRegister();
      EMIT(MIR_Binary.create(PPC_SUBFIC, I(regpool.getSecondReg(defReg)), I(regpool.getSecondReg(leftReg)), IC(0)));
      EMIT(MIR_Unary.create(PPC_SUBFZE, I(defReg), I(leftReg)));
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  protected final void LONG_NOT(Instruction s, RegisterOperand def, RegisterOperand left) {
    Register defReg = def.getRegister();
    Register leftReg = left.getRegister();
    if (VM.BuildFor32Addr) {
      EMIT(MIR_Binary.create(PPC_NOR, I(defReg), I(leftReg), I(leftReg)));
      EMIT(MIR_Binary.create(PPC_NOR,
                             I(regpool.getSecondReg(defReg)),
                             I(regpool.getSecondReg(leftReg)),
                             I(regpool.getSecondReg(leftReg))));
    } else {
      EMIT(MIR_Binary.create(PPC_NOR, L(defReg), L(leftReg), L(leftReg)));
    }
  }

  protected final void LONG_LOG(Instruction s, Operator operator, RegisterOperand def,
                                RegisterOperand left, RegisterOperand right) {
    Register defReg = def.getRegister();
    Register leftReg = left.getRegister();
    Register rightReg = right.getRegister();
    if (VM.BuildFor32Addr) {
      EMIT(MIR_Binary.create(operator, I(defReg), I(leftReg), I(rightReg)));
      EMIT(MIR_Binary.create(operator,
                             I(regpool.getSecondReg(defReg)),
                             I(regpool.getSecondReg(leftReg)),
                             I(regpool.getSecondReg(rightReg))));
    } else {
      EMIT(MIR_Binary.create(operator, L(defReg), L(leftReg), L(rightReg)));
    }
  }

  /**
   * taken from "PowerPC Microprocessor Family,
   * The Programming Environment for 32-bit Microprocessors
   * */
  protected final void LONG_SHL(Instruction s, RegisterOperand def, RegisterOperand left,
                                RegisterOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Register leftHigh = left.getRegister();
    Register leftLow = regpool.getSecondReg(leftHigh);
    Register shift = right.getRegister();
    Register t0 = regpool.getInteger();
    Register t31 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_SUBFIC, I(t31), I(shift), IC(32)));
    EMIT(MIR_Binary.create(PPC_SLW, I(defHigh), I(leftHigh), I(shift)));
    EMIT(MIR_Binary.create(PPC_SRW, I(t0), I(leftLow), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defHigh), I(defHigh), I(t0)));
    EMIT(MIR_Binary.create(PPC_ADDI, I(t31), I(shift), IC(-32)));
    EMIT(MIR_Binary.create(PPC_SLW, I(t0), I(leftLow), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defHigh), I(defHigh), I(t0)));
    EMIT(MIR_Binary.create(PPC_SLW, I(defLow), I(leftLow), I(shift)));
  }

  protected final void LONG_SHL_IMM(Instruction s, RegisterOperand def, RegisterOperand left,
                                    IntConstantOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Register leftHigh = left.getRegister();
    Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defHigh), I(leftHigh), IC(shift), IC(0), IC(31 - shift)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI, I(defHigh), I(defHigh), I(leftLow), IC(shift), IC(32 - shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), IC(shift), IC(0), IC(31 - shift)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defHigh), I(leftLow)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    } else if (shift < 64) {
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SLWI, I(defHigh), I(leftLow), IC(shift)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    } else {
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    }
  }

  protected final void LONG_USHR(Instruction s, RegisterOperand def, RegisterOperand left,
                                 RegisterOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Register leftHigh = left.getRegister();
    Register leftLow = regpool.getSecondReg(leftHigh);
    Register shift = right.getRegister();
    Register t0 = regpool.getInteger();
    Register t31 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_SUBFIC, I(t31), I(shift), IC(32)));
    EMIT(MIR_Binary.create(PPC_SRW, I(defLow), I(leftLow), I(shift)));
    EMIT(MIR_Binary.create(PPC_SLW, I(t0), I(leftHigh), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defLow), I(defLow), I(t0)));
    EMIT(MIR_Binary.create(PPC_ADDI, I(t31), I(shift), IC(-32)));
    EMIT(MIR_Binary.create(PPC_SRW, I(t0), I(leftHigh), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defLow), I(defLow), I(t0)));
    EMIT(MIR_Binary.create(PPC_SRW, I(defHigh), I(leftHigh), I(shift)));
  }

  protected final void LONG_USHR_IMM(Instruction s, RegisterOperand def, RegisterOperand left,
                                     IntConstantOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Register leftHigh = left.getRegister();
    Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), IC(32 - shift), IC(shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI,
                                    I(defLow),
                                    I(defLow),
                                    I(leftHigh),
                                    IC(32 - shift),
                                    IC(0),
                                    IC(shift - 1)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defHigh), I(leftHigh), IC(32 - shift), IC(shift), IC(31)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defLow), I(leftHigh)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
    } else if (shift < 64) {
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SRWI, I(defLow), I(leftHigh), IC(shift)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
    } else {
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    }
  }

  protected final void LONG_SHR_IMM(Instruction s, RegisterOperand def, RegisterOperand left,
                                    IntConstantOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Register leftHigh = left.getRegister();
    Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), IC(32 - shift), IC(shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI,
                                    I(defLow),
                                    I(defLow),
                                    I(leftHigh),
                                    IC(32 - shift),
                                    IC(0),
                                    IC(shift - 1)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), IC(shift)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defLow), I(leftHigh)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), IC(31)));
    } else {
      if (shift > 63) shift = 63;
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defLow), I(leftHigh), IC(shift)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), IC(31)));
    }
  }

  protected final void LONG_MUL(Instruction s, RegisterOperand def, RegisterOperand left,
                                RegisterOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register dH = def.getRegister();
    Register dL = regpool.getSecondReg(dH);
    Register lH = left.getRegister();
    Register lL = regpool.getSecondReg(lH);
    Register rH = right.getRegister();
    Register rL = regpool.getSecondReg(rH);
    Register tH = regpool.getInteger();
    Register t = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_MULHWU, I(tH), I(lL), I(rL)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(t), I(lL), I(rH)));
    EMIT(MIR_Binary.create(PPC_ADD, I(tH), I(tH), I(t)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(t), I(lH), I(rL)));
    EMIT(MIR_Binary.create(PPC_ADD, I(dH), I(tH), I(t)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(dL), I(lL), I(rL)));
  }

  // LONG_DIV and LONG_REM are handled by system calls in 32-bit version
  void LONG_DIV_IMM(Instruction s, RegisterOperand def, RegisterOperand left, RegisterOperand c,
                    IntConstantOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.create(PPC64_SRADI, c, left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, def, c.copyD2U()));
    } else {
      IntConstant(c.getRegister(), right.value);
      EMIT(MIR_Binary.mutate(s, PPC64_DIVD, def, left, c));
    }
  }

  void LONG_REM(Instruction s, RegisterOperand def, RegisterOperand left, RegisterOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    Register temp = regpool.getLong();
    EMIT(MIR_Binary.mutate(s, PPC64_DIVD, L(temp), left, right));
    EMIT(MIR_Binary.create(PPC64_MULLD, L(temp), L(temp), right.copyU2U()));
    EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
  }

  void LONG_REM_IMM(Instruction s, RegisterOperand def, RegisterOperand left, RegisterOperand c,
                    IntConstantOperand right) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    Register temp = regpool.getLong();
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.mutate(s, PPC64_SRADI, L(temp), left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, L(temp), L(temp)));
      EMIT(MIR_Binary.create(PPC64_SLDI, L(temp), L(temp), IC(power)));
      EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
    } else {
      IntConstant(c.getRegister(), right.value);
      EMIT(MIR_Binary.mutate(s, PPC64_DIVD, L(temp), left, c));
      EMIT(MIR_Binary.create(PPC64_MULLD, L(temp), L(temp), c.copyU2U()));
      EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
    }
  }

  protected final void LONG_LOAD_addi(Instruction s, RegisterOperand def, RegisterOperand left,
                                      IntConstantOperand right, LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions) VM._assert(imm < (0x8000 - 4));
    Instruction inst = MIR_Load.create(PPC_LWZ, I(defHigh), left, IC(imm), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    if (guard != null) {
      guard = guard.copy();
    }
    inst = MIR_Load.create(PPC_LWZ, I(defLow), left.copyU2U(), IC(imm + 4), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_LOAD_addis(Instruction s, RegisterOperand def, RegisterOperand left,
                                       RegisterOperand right, AddressConstantOperand Value,
                                       LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Offset value = AV(Value).toWord().toOffset();
    EMIT(MIR_Binary.create(PPC_ADDIS, right, left, IC(Bits.PPCMaskUpper16(value))));
    Instruction inst =
        MIR_Load.create(PPC_LWZ, I(defHigh), right.copyD2U(), IC(Bits.PPCMaskLower16(value)), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    inst = MIR_Load.create(PPC_LWZ, I(defLow), right.copyD2U(), IC(Bits.PPCMaskLower16(value) + 4), loc);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_LOAD_addx(Instruction s, RegisterOperand def, RegisterOperand left,
                                      RegisterOperand right, LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Instruction inst = MIR_Load.create(PPC_LWZX, I(defHigh), left, right, loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    RegisterOperand kk = regpool.makeTempInt();
    EMIT(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), IC(4)));
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    if (guard != null) {
      guard = guard.copy();
    }
    inst = MIR_Load.create(PPC_LWZX, I(defLow), left.copyU2U(), kk.copyD2U(), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addi(Instruction s, RegisterOperand def, RegisterOperand left,
                                       IntConstantOperand right, LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions) {
      VM._assert(imm < (0x8000 - 4));
    }
    Instruction inst = MIR_Store.create(PPC_STW, I(defHigh), left, IC(imm), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    if (guard != null) {
      guard = guard.copy();
    }
    inst = MIR_Store.create(PPC_STW, I(defLow), left.copyU2U(), IC(imm + 4), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addis(Instruction s, RegisterOperand def, RegisterOperand left,
                                        RegisterOperand right, AddressConstantOperand Value,
                                        LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Offset value = AV(Value).toWord().toOffset();
    EMIT(MIR_Binary.create(PPC_ADDIS, right, left, IC(Bits.PPCMaskUpper16(value))));
    Instruction inst =
        MIR_Store.create(PPC_STW, I(defHigh), right.copyD2U(), IC(Bits.PPCMaskLower16(value)), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    if (guard != null) {
      guard = guard.copy();
    }
    inst = MIR_Store.create(PPC_STW, I(defLow), right.copyD2U(), IC(Bits.PPCMaskLower16(value) + 4), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addx(Instruction s, RegisterOperand def, RegisterOperand left,
                                       RegisterOperand right, LocationOperand loc, Operand guard) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
    Register defHigh = def.getRegister();
    Register defLow = regpool.getSecondReg(defHigh);
    Instruction inst = MIR_Store.create(PPC_STWX, I(defHigh), left, right, loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    RegisterOperand kk = regpool.makeTempInt();
    EMIT(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), IC(4)));
    if (loc != null) {
      loc = (LocationOperand) loc.copy();
    }
    if (guard != null) {
      guard = guard.copy();
    }
    inst = MIR_Store.create(PPC_STWX, I(defLow), left.copyU2U(), kk.copyD2U(), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void DOUBLE_IFCMP(Instruction s, RegisterOperand left, Operand right) {
    // Create compare
    RegisterOperand cr = regpool.makeTempCondition();
    EMIT(MIR_Binary.create(PPC_FCMPU, cr, left, right));
    // Branch depends on condition
    ConditionOperand c = IfCmp.getCond(s);
    BranchOperand target = IfCmp.getTarget(s);
    if (!c.branchIfUnordered()) {
      // If branch doesn't branch when unordered then we need just one
      // branch destination
      EMIT(MIR_CondBranch.create(PPC_BCOND,
                                 cr.copyD2U(),
                                 new PowerPCConditionOperand(c),
                                 target,
                                 IfCmp.getBranchProfile(s)));
    } else {
      if ((c.value != ConditionOperand.NOT_EQUAL) || (!left.similar(right))) {
        // Propagate branch probabilities as follows: assume the
        // probability of unordered (first condition) is zero, and
        // propagate the original probability to the second condition.
        EMIT(MIR_CondBranch2.create(PPC_BCOND2,
                                    cr.copyD2U(),
                                    PowerPCConditionOperand.UNORDERED(),
                                    target,
                                    new BranchProfileOperand(0f),
                                    new PowerPCConditionOperand(c),
                                    (BranchOperand) target.copy(),
                                    IfCmp.getBranchProfile(s)));
      } else {
        // If branch is effectively a NaN test we just need 1 branch
        EMIT(MIR_CondBranch.create(PPC_BCOND,
                                   cr.copyD2U(),
                                   new PowerPCConditionOperand(c),
                                   target,
                                   IfCmp.getBranchProfile(s)));
      }
    }
  }

  /**
   * Expansion of LOWTABLESWITCH.
   *
   * @param s the instruction to expand
   */
  protected final void LOWTABLESWITCH(Instruction s) {
    // (1) We're changing index from a U to a DU.
    //     Inject a fresh copy instruction to make sure we aren't
    //     going to get into trouble (if someone else was also using index).
    RegisterOperand newIndex = regpool.makeTempInt();
    EMIT(MIR_Move.create(PPC_MOVE, newIndex, LowTableSwitch.getIndex(s)));
    int number = LowTableSwitch.getNumberOfTargets(s);
    Instruction s2 = CPOS(s, MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex.copyRO(), number * 2));
    for (int i = 0; i < number; i++) {
      MIR_LowTableSwitch.setTarget(s2, i, LowTableSwitch.getTarget(s, i));
      MIR_LowTableSwitch.setBranchProfile(s2, i, LowTableSwitch.getBranchProfile(s, i));
    }
    EMIT(s2);
  }

  // Take the generic LIR trap_if and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See TrapConstants.java.
  // Also see ConvertToLowLevelIR.java which generates most of these TRAP_IFs.
  protected final void TRAP_IF(Instruction s) {
    RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    RegisterOperand v1 = (RegisterOperand) TrapIf.getClearVal1(s);
    RegisterOperand v2 = (RegisterOperand) TrapIf.getClearVal2(s);
    ConditionOperand cond = TrapIf.getClearCond(s);
    TrapCodeOperand tc = TrapIf.getClearTCode(s);

    switch (tc.getTrapCode()) {
      case RuntimeEntrypoints.TRAP_ARRAY_BOUNDS: {
        if (cond.isLOWER_EQUAL()) {
          EMIT(MIR_Trap.mutate(s, PPC_TW, gRes, new PowerPCTrapOperand(cond), v1, v2, tc));
        } else {
          throw new OptimizingCompilerException("Unexpected case of trap_if" + s);
        }
      }
      break;
      default:
        throw new OptimizingCompilerException("Unexpected case of trap_if" + s);
    }
  }

  /**
   * Take the generic LIR trap_if and coerce into the limited
   * vocabulary understood by the C trap handler on PPC.  See
   * TrapConstants.java.  Also see ConvertToLowLevelIR.java
   * which generates most of these TRAP_IFs.
   *
   * @param s the instruction to expand
   * @param longConstant is the argument a long constant?
   */
  protected final void TRAP_IF_IMM(Instruction s, boolean longConstant) {
    RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    RegisterOperand v1 = (RegisterOperand) TrapIf.getClearVal1(s);
    ConditionOperand cond = TrapIf.getClearCond(s);
    TrapCodeOperand tc = TrapIf.getClearTCode(s);

    switch (tc.getTrapCode()) {
      case RuntimeEntrypoints.TRAP_ARRAY_BOUNDS: {
        IntConstantOperand v2 = (IntConstantOperand) TrapIf.getClearVal2(s);
        if (cond.isLOWER_EQUAL()) {
          EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, new PowerPCTrapOperand(cond), v1, v2, tc));
        } else if (cond.isHIGHER_EQUAL()) {
          // have flip the operands and use non-immediate so trap handler can recognize.
          RegisterOperand tmp = regpool.makeTempInt();
          IntConstant(tmp.getRegister(), v2.value);
          EMIT(MIR_Trap.mutate(s, PPC_TW, gRes, new PowerPCTrapOperand(cond.flipOperands()), tmp, v1, tc));
        } else {
          throw new OptimizingCompilerException("Unexpected case of trap_if" + s);
        }
      }
      break;
      case RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO: {
        ConstantOperand v2 = (ConstantOperand) TrapIf.getClearVal2(s);
        if (VM.VerifyAssertions) {
          if (longConstant) {
            long val = ((LongConstantOperand) v2).value;
            boolean caseMatchesExpected = val == 0L && cond.isEQUAL();
            if (!caseMatchesExpected) {
              String msg = "Unexpected case of trap_if" + s;
              VM._assert(VM.NOT_REACHED, msg);
            }
          } else {
            int val = ((IntConstantOperand) v2).value;
            boolean caseMatchesExpected = val == 0L && cond.isEQUAL();
            if (!caseMatchesExpected) {
              String msg = "Unexpected case of trap_if" + s;
              VM._assert(VM.NOT_REACHED, msg);
            }
          }
        }

        if (longConstant) {
          if (VM.BuildFor32Addr) {
            // A slightly ugly matter, but we need to deal with combining
            // the two pieces of a long register from a LONG_ZERO_CHECK.
            // A little awkward, but probably the easiest workaround...
            RegisterOperand rr = regpool.makeTempInt();
            EMIT(MIR_Binary.create(PPC_OR, rr, v1, I(regpool.getSecondReg(v1.getRegister()))));
            v1 = rr.copyD2U();
            v2 = IC(0);
            EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, new PowerPCTrapOperand(cond), v1, v2, tc));
          } else {
            EMIT(MIR_Trap.mutate(s, PPC64_TDI, gRes, new PowerPCTrapOperand(cond), v1, v2, tc));
          }
        } else {
          EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, new PowerPCTrapOperand(cond), v1, v2, tc));
        }
      }
      break;

      default:
        throw new OptimizingCompilerException("Unexpected case of trap_if" + s);
    }
  }

  // Take the generic LIR trap and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See TrapConstants.java.
  protected final void TRAP(Instruction s) {
    RegisterOperand gRes = Trap.getClearGuardResult(s);
    TrapCodeOperand tc = Trap.getClearTCode(s);
    switch (tc.getTrapCode()) {
      case RuntimeEntrypoints.TRAP_NULL_POINTER: {
        RVMMethod target = Entrypoints.raiseNullPointerException;
        mutateTrapToCall(s, target);
      }
      break;
      case RuntimeEntrypoints.TRAP_ARRAY_BOUNDS: {
        RVMMethod target = Entrypoints.raiseArrayBoundsException;
        mutateTrapToCall(s, target);
      }
      break;
      case RuntimeEntrypoints.TRAP_DIVIDE_BY_ZERO: {
        RVMMethod target = Entrypoints.raiseArithmeticException;
        mutateTrapToCall(s, target);
      }
      break;
      case RuntimeEntrypoints.TRAP_CHECKCAST: {
        EMIT(MIR_Trap.mutate(s,
                             PPC_TWI,
                             gRes,
                             PowerPCTrapOperand.ALWAYS(),
                             I(12),
                             IC(TrapConstants.CHECKCAST_TRAP & 0xffff),
                             tc));
      }
      break;
      case RuntimeEntrypoints.TRAP_MUST_IMPLEMENT: {
        EMIT(MIR_Trap.mutate(s,
                             PPC_TWI,
                             gRes,
                             PowerPCTrapOperand.ALWAYS(),
                             I(12),
                             IC(TrapConstants.MUST_IMPLEMENT_TRAP & 0xffff),
                             tc));
      }
      break;
      case RuntimeEntrypoints.TRAP_STORE_CHECK: {
        EMIT(MIR_Trap.mutate(s,
                             PPC_TWI,
                             gRes,
                             PowerPCTrapOperand.ALWAYS(),
                             I(12),
                             IC(TrapConstants.STORE_CHECK_TRAP & 0xffff),
                             tc));
      }
      break;
      default:
        throw new OptimizingCompilerException("Unexpected case of trap_if" + s);
    }
  }

  private void mutateTrapToCall(Instruction s, RVMMethod target) {
    Offset offset = target.getOffset();
    RegisterOperand tmp = regpool.makeTemp(TypeReference.JavaLangObjectArray);
    Register JTOC = regpool.getPhysicalRegisterSet().getJTOC();
    MethodOperand meth = MethodOperand.STATIC(target);
    meth.setIsNonReturningCall(true);
    int valueLow = Bits.PPCMaskLower16(offset);
    if (Bits.fits(offset, 16)) {
      EMIT(MIR_Load.create(PPC_LAddr, tmp, A(JTOC), IC(valueLow)));
    } else {
      int valueHigh = Bits.PPCMaskUpper16(offset);
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 32));
      Register reg = regpool.getAddress();
      EMIT(MIR_Binary.create(PPC_ADDIS, A(reg), A(JTOC), IC(valueHigh)));
      EMIT(MIR_Load.create(PPC_LAddr, tmp, A(reg), IC(valueLow)));
    }
    EMIT(MIR_Move.create(PPC_MTSPR, A(CTR), tmp.copyD2U()));
    EMIT(MIR_Call.mutate0(s, PPC_BCTRL, null, null, meth));
  }

  /* special case handling OSR instructions */
  void OSR(BURS burs, Instruction s) {
    if (VM.VerifyAssertions) VM._assert(OsrPoint.conforms(s));

    // 1. how many params
    int numparam = OsrPoint.getNumberOfElements(s);
    int numlong = 0;
    if (VM.BuildFor32Addr) {
      for (int i = 0; i < numparam; i++) {
        if (OsrPoint.getElement(s, i).getType().isLongType()) {
          numlong++;
        }
      }
    }

    // 2. collect params
    Operand[] params = new Operand[numparam];
    for (int i = 0; i < numparam; i++) {
      params[i] = OsrPoint.getClearElement(s, i);
    }

    InlinedOsrTypeInfoOperand typeInfo = OsrPoint.getClearInlinedTypeInfo(s);

    if (VM.VerifyAssertions) VM._assert(typeInfo != null);

    // 3: only makes second half register of long being used
    //    creates room for long types.
    burs.append(OsrPoint.mutate(s, YIELDPOINT_OSR, typeInfo, numparam + numlong));

    // set the number of valid params in osr type info, used
    // in LinearScan
    typeInfo.validOps = numparam;

    int pidx = numparam;
    for (int i = 0; i < numparam; i++) {
      Operand param = params[i];
      OsrPoint.setElement(s, i, param);
      if (VM.BuildFor32Addr) {
        if (param instanceof RegisterOperand) {
          RegisterOperand rparam = (RegisterOperand) param;
          // the second half is appended at the end
          // LinearScan will update the map.
          if (rparam.getType().isLongType()) {
            OsrPoint.setElement(s, pidx++, L(burs.ir.regpool.getSecondReg(rparam.getRegister())));
          }
        }
      }
    }

    if (VM.VerifyAssertions) VM._assert(pidx == (numparam + numlong));
  }
}
