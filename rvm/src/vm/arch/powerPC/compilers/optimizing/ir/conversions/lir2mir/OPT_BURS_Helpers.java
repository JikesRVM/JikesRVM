/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Contains architecture-specific helper functions for BURS.
 * @author Dave Grove
 * @author Mauricio J. Serrano
 */
abstract class OPT_BURS_Helpers extends OPT_BURS_Common_Helpers
  implements OPT_Operators, OPT_PhysicalRegisterConstants {
  
  OPT_BURS_Helpers(OPT_BURS burs) {
    super(burs);
  }

  /**
   * returns true if an unsigned integer in 16 bits
   */
  protected final boolean UI16 (OPT_Operand a) {
    return  (IV(a) & 0xffff0000) == 0;
  }

  /**
   * returns true if an unsigned integer in 15 bits
   */
  protected final boolean UI15 (OPT_Operand a) {
    return  (IV(a) & 0xffff8000) == 0;
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  protected final boolean SI16 (OPT_Operand a) {
    return  SI16(IV(a));
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  protected final boolean SI16 (int value) {
    return  (value <= 32767) && (value >= -32768);
  }

  /**
   * returns true if lower 16-bits are zero
   */
  protected final boolean U16 (OPT_Operand a) {
    return  (IV(a) & 0xffff) == 0;
  }

  /**
   * returns true if lower 16-bits are zero
   */
  protected final boolean U16 (int value) {
    return  (value & 0xffff) == 0;
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean MASK (OPT_Operand a) {
    return  MASK(IV(a));
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean MASK (int value) {
    if (value < 0)
      value = ~value;
    return  POSITIVE_MASK(value);
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean POSITIVE_MASK (OPT_Operand a) {
    return  POSITIVE_MASK(IV(a));
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  protected final boolean POSITIVE_MASK (int value) {
    if (value == 0)
      return  false;
    do {
      if ((value & 0x1) == 1)
        break;
      value = value >>> 1;
    } while (true);
    do {
      if ((value & 0x1) == 0)
        return  false;
      value = value >>> 1;
    } while (value != 0);
    return  true;
  }

  protected final boolean MASK_AND_OR (OPT_Operand and, OPT_Operand or) {
    int value1 = IV(and);
    int value2 = IV(or);
    return  ((~value1 & value2) == value2) && MASK(value1);
  }

  /**
   * Integer Shift Right Immediate
   */
  protected final OPT_IntConstantOperand SRI (OPT_Operand o, int amount) {
    return  IC(IV(o) >>> amount);
  }

  protected final OPT_IntConstantOperand SRI (int i, int amount) {
    return  IC(i >>> amount);
  }

  /**
   * Integer And Immediate
   */
  protected final OPT_IntConstantOperand ANDI (OPT_Operand o, int mask) {
    return  IC(IV(o) & mask);
  }

  /**
   * Calculate Lower 16 Bits
   */
  protected final OPT_IntConstantOperand CAL16 (OPT_Operand o) {
    return  IC(OPT_Bits.PPCMaskLower16(IV(o)));
  }

  /**
   * Calculate Lower 16 Bits
   */
  protected final OPT_IntConstantOperand CAL16 (int i) {
    return  IC(OPT_Bits.PPCMaskLower16(i));
  }

  /**
   * Calculate Upper 16 Bits
   */
  protected final OPT_IntConstantOperand CAU16 (OPT_Operand o) {
    return  IC(OPT_Bits.PPCMaskUpper16(IV(o)));
  }

  /**
   * Calculate Upper 16 Bits
   */
  protected final OPT_IntConstantOperand CAU16 (int i) {
    return  IC(OPT_Bits.PPCMaskUpper16(i));
  }

  /**
   * Mask Begin
   */
  protected final OPT_IntConstantOperand MB (OPT_Operand o) {
    return  IC(MaskBegin(IV(o)));
  }

  protected final int MaskBegin (int integer) {
    int value;
    for (value = 0; integer >= 0; integer = integer << 1, value++);
    return  value;
  }

  /**
   * Mask End
   */
  protected final OPT_IntConstantOperand ME (OPT_Operand o) {
    return  IC(MaskEnd(IV(o)));
  }

  protected final int MaskEnd (int integer) {
    int value;
    for (value = 31; (integer & 0x1) == 0; integer = integer >>> 1, value--);
    return  value;
  }
  
  // access functions
  protected final OPT_Register getXER () {
    return  getIR().regpool.getPhysicalRegisterSet().getXER();
  }

  protected final OPT_Register getLR () {
    return  getIR().regpool.getPhysicalRegisterSet().getLR();
  }

  protected final OPT_Register getCTR () {
    return  getIR().regpool.getPhysicalRegisterSet().getCTR();
  }

  protected final OPT_Register getTU () {
    return  getIR().regpool.getPhysicalRegisterSet().getTU();
  }

  protected final OPT_Register getTL () {
    return  getIR().regpool.getPhysicalRegisterSet().getTL();
  }

  protected final OPT_Register getCR () {
    return  getIR().regpool.getPhysicalRegisterSet().getCR();
  }

  /* RVM registers */
  protected final OPT_Register getJTOC () {
    return  getIR().regpool.getPhysicalRegisterSet().getJTOC();
  }

  /**
   * Emit code to load a float value from the JTOC.
   * @param operator
   * @param RT
   * @param field
   */
  private final void emitLFtoc(OPT_Operator operator, 
                               OPT_Register RT, VM_Field field) {
    OPT_Register JTOC = regpool.getPhysicalRegisterSet().getJTOC();
    int offset = field.getOffset();
    int valueHigh = OPT_Bits.PPCMaskUpper16(offset);
    OPT_Instruction s;
    if (valueHigh == 0) {
      s = MIR_Load.create(operator, D(RT), A(JTOC), IC(offset));
      EMIT(s);
    } else {
      OPT_Register reg = regpool.getAddress();
      int valueLow = OPT_Bits.PPCMaskLower16(offset);
      EMIT(MIR_Binary.create(PPC_ADDIS, A(reg), A(JTOC), IC(valueHigh)));
      s = MIR_Load.create(operator, D(RT), A(reg), IC(valueLow));
      EMIT(s);
    }
  }

  /**
   * Emit code to load an Integer Constant.
   * reg must be != 0
   */
  protected final void IntConstant(OPT_Register reg, int value) {
    int lo = OPT_Bits.PPCMaskLower16(value);
    int hi = OPT_Bits.PPCMaskUpper16(value);
    if (hi != 0) {
      EMIT(MIR_Unary.create(PPC_LDIS, I(reg), IC(hi)));
      if (lo != 0)
        EMIT(MIR_Binary.create(PPC_ADDI, I(reg), I(reg), IC(lo)));
    } else {
      EMIT(MIR_Unary.create(PPC_LDI, I(reg), IC(lo)));
    }
  }

  /**
   * Emit code to get a caught exception object into a register
   */
  protected final void GET_EXCEPTION_OBJECT(OPT_Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_LocationOperand loc = new OPT_LocationOperand(-offset);
    EMIT(MIR_Load.mutate(s, PPC_LAddr, Nullary.getClearResult(s), 
                         A(FP), IC(offset), loc, TG()));
  }

  /**
   * Emit code to move a value in a register to the stack location
   * where a caught exception object is expected to be.
   */
  protected final void SET_EXCEPTION_OBJECT(OPT_Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_LocationOperand loc = new OPT_LocationOperand(-offset);
    OPT_RegisterOperand obj = (OPT_RegisterOperand)CacheOp.getRef(s);
    EMIT(MIR_Store.mutate(s, PPC_STAddr, obj, A(FP), IC(offset), loc, TG()));
  }

  /**
   * Emit code to move 32 bits from FPRs to GPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void FPR2GPR_32(OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STFS, val, A(FP), IC(offset), null, TG()));
    EMIT(MIR_Load.mutate(s, PPC_LWZ, Unary.getClearResult(s), 
                         A(FP), IC(offset), null, TG()));
  }

  /**
   * Emit code to move 32 bits from GPRs to FPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void GPR2FPR_32(OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STW, val, A(FP), IC(offset), null, TG()));
    EMIT(MIR_Load.mutate(s, PPC_LFS, Unary.getClearResult(s), A(FP), 
                         IC(offset), null, TG()));
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void FPR2GPR_64(OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STFD, val, A(FP), IC(offset), 
                          null, TG()));
    OPT_RegisterOperand i1 = Unary.getClearResult(s);
    //-#if RVM_FOR_32_ADDR
    OPT_RegisterOperand i2 = I(regpool.getSecondReg(i1.register));
    EMIT(MIR_Load.create(PPC_LWZ, i1, A(FP), IC(offset), null, TG()));
    EMIT(MIR_Load.mutate(s, PPC_LWZ, i2, A(FP), IC(offset+4), null, TG()));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Load.mutate(s, PPC_LAddr, i1, A(FP), IC(offset), null, TG()));
    //-#endif
  }

  /**
   * Emit code to move 64 bits from GPRs to FPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  protected final void GPR2FPR_64(OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand i1 = (OPT_RegisterOperand)Unary.getClearVal(s);
    EMIT(MIR_Store.create(PPC_STAddr, i1, A(FP), IC(offset), null, TG()));
    //-#if RVM_FOR_32_ADDR
    OPT_RegisterOperand i2 = I(regpool.getSecondReg(i1.register));
    EMIT(MIR_Store.create(PPC_STW, i2, A(FP), IC(offset+4), null, TG()));
    //-#endif
    EMIT(MIR_Load.mutate(s, PPC_LFD, Unary.getClearResult(s), A(FP), 
                         IC(offset), null, TG()));
  }

  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  protected final void PROLOGUE(OPT_Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i=0; i<numFormals; i++) {
      if (Prologue.getFormal(s, i).type.isLongType()) numLongs ++;
    }
    //-#if RVM_FOR_32_ADDR
    if (numLongs != 0) {
      OPT_Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals+numLongs);
      for (int sidx=0, s2idx=0; sidx<numFormals; sidx++) {
        OPT_RegisterOperand sForm = Prologue.getClearFormal(s, sidx);
        Prologue.setFormal(s2, s2idx++, sForm);
        if (sForm.type.isLongType()) {
          Prologue.setFormal(s2, s2idx++, 
                             I(regpool.getSecondReg(sForm.register)));
        }
      }                                                                      
      EMIT(s2);
    } else {
      EMIT(s);
    }
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(s);
    //-#endif
  }


  /**
   * Expand a call instruction.
   */
  protected final void CALL(OPT_Instruction s) {
    OPT_Operand target = Call.getClearAddress(s);
    OPT_MethodOperand meth = Call.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    //-#if RVM_FOR_32_ADDR
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }
    //-#endif

    // Step 2: Figure out what the result and result2 values will be
    OPT_RegisterOperand result = Call.getClearResult(s);
    //-#if RVM_FOR_32_ADDR
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type.isLongType()) {
      result2 = I(regpool.getSecondReg(result.register));
    }
    //-#endif

    // Step 3: Figure out what the operator is going to be
    OPT_Operator callOp;
    if (target instanceof OPT_RegisterOperand) {
      // indirect call through target (contains code addr)
      OPT_Register ctr = regpool.getPhysicalRegisterSet().getCTR();
      EMIT(MIR_Move.create(PPC_MTSPR, A(ctr), 
                           (OPT_RegisterOperand)target));
      target = null;
      callOp = PPC_BCTRL;
    } else if (target instanceof OPT_BranchOperand) {
      // Earlier analysis has tagged this call as recursive, 
      // set up for a direct call.
      callOp = PPC_BL;
    } else {
      throw  new OPT_OptimizingCompilerException("Unexpected target operand "
                                                 + target + " to call " + s);
    }

    // Step 4: Mutate the Call to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed 
    // arguments, so some amount of copying is required. We'll hope the 
    // opt compiler can manage to make this more efficient than it looks.
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getClearParam(s, i);
    }
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Call.mutate(s, callOp, result, result2, 
                         (OPT_BranchOperand)target, meth, 
                         numParams + longParams));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Call.mutate(s, callOp, result, null,
                         (OPT_BranchOperand)target, meth,
                         numParams));
    //-#endif
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      //-#if RVM_FOR_32_ADDR
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
      }
      //-#endif
    }
  }

  /**
   * Expand a syscall instruction.
   */
  protected final void SYSCALL(OPT_Instruction s) {
    burs.ir.setHasSysCall(true);
    OPT_Operand target = Call.getClearAddress(s);
    OPT_MethodOperand meth = Call.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    //-#if RVM_FOR_32_ADDR
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType().isLongType()) {
        longParams++;
      }
    }
    //-#endif

    // Step 2: Figure out what the result and result2 values will be
    OPT_RegisterOperand result = Call.getClearResult(s);
    //-#if RVM_FOR_32_ADDR
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type.isLongType()) {
      result2 = I(regpool.getSecondReg(result.register));
    }
    //-#endif

    // Step 3: Figure out what the operator is going to be
    OPT_Operator callOp;
    if (target instanceof OPT_RegisterOperand) {
      // indirect call through target (contains code addr)
      OPT_Register ctr = regpool.getPhysicalRegisterSet().getCTR();
      EMIT(MIR_Move.create(PPC_MTSPR, A(ctr), 
                           (OPT_RegisterOperand)target));
      target = null;
      callOp = PPC_BCTRL_SYS;
    } else if (target instanceof OPT_BranchOperand) {
      // Earlier analysis has tagged this call as recursive, 
      // set up for a direct call.
      callOp = PPC_BL_SYS;
    } else {
      throw  new OPT_OptimizingCompilerException("Unexpected target operand "
                                                 + target + " to call " + s);
    }

    // Step 4: Mutate the SysCall to an MIR_Call.
    // Note MIR_Call and Call have a different number of fixed 
    // arguments, so some amount of copying is required. We'll hope the 
    // opt compiler can manage to make this more efficient than it looks.
    OPT_Operand[] params = new OPT_Operand[numParams];
    for (int i = 0; i < numParams; i++) {
      params[i] = Call.getClearParam(s, i);
    }
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Call.mutate(s, callOp, result, result2, 
                         (OPT_BranchOperand)target, meth, 
                         numParams + longParams));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Call.mutate(s, callOp, result, null,
                         (OPT_BranchOperand)target, meth,
                         numParams));
    //-#endif
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      //-#if RVM_FOR_32_ADDR
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type.isLongType()) {
          MIR_Call.setParam(s, mirCallIdx++, 
                            L(regpool.getSecondReg(rparam.register)));
        }
      }
      //-#endif
    }
  }

  protected final void RETURN(OPT_Instruction s, OPT_Operand value) {
    if (value != null) {
      OPT_RegisterOperand rop = (OPT_RegisterOperand)value;
      //-#if RVM_FOR_32_ADDR
      if (value.getType().isLongType()) {
        OPT_Register pair = regpool.getSecondReg(rop.register);
        EMIT(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), I(pair)));
      } else 
      //-#endif
      {
        EMIT(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), null));
      }
    } else {
      EMIT(MIR_Return.mutate(s, PPC_BLR, null, null));
    }
  }


  protected final void SHL_USHR(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_IntConstantOperand shift1, 
                                OPT_IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x < y) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
                                    IC((32 - (y - x)) & 0x1f), 
                                    IC(y), IC(31))); 
    } else {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
                                    IC(x - y), IC(y), IC(31 - (x - y))));
    }
  }

  protected final void USHR_SHL(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_IntConstantOperand shift1, 
                                OPT_IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x < y) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
                                    IC(y - x), IC(0), IC(31 - y))); 
    } else {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
                                    IC((32 - (x - y)) & 0x1f), 
                                    IC(x - y), IC(31 - y)));
    }
  }

  protected final void USHR_AND(OPT_Instruction s, 
                                OPT_RegisterOperand Def, 
                                OPT_RegisterOperand left, 
                                OPT_IntConstantOperand Mask, 
                                OPT_IntConstantOperand Shift) {
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
    EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, 
                                  IC((32 - shift) & 0x1f), 
                                  IC(MB), IC(ME)));
  }

  protected final void AND_USHR(OPT_Instruction s, 
                                OPT_RegisterOperand Def, 
                                OPT_RegisterOperand left, 
                                OPT_IntConstantOperand Mask, 
                                OPT_IntConstantOperand Shift) {
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
    EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, 
                                  IC((32 - shift) & 0x1f), 
                                  IC(MB), IC(ME)));
  }

  protected final void AND_MASK(OPT_Instruction s, 
                                OPT_RegisterOperand Def, 
                                OPT_RegisterOperand left, 
                                OPT_IntConstantOperand Mask) {
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC(0), 
                                    IC((ME + 1) & 0x1f), IC(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, IC(0), 
                                    IC(MB), IC(ME)));
    }
  }

  /**
   * emit basic code to handle an INT_IFCMP when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP(OPT_Instruction s,
                           OPT_RegisterOperand val1, OPT_Operand val2,
                           OPT_ConditionOperand cond,
                           boolean immediate) {
    OPT_RegisterOperand cr = regpool.makeTempCondition();
    OPT_Operator op;
    if (immediate) {
      op = cond.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op = cond.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, val1, val2));
    EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, cr.copyD2U(),
                               new OPT_PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  //-#if RVM_FOR_64_ADDR
  /**
   * emit basic code to handle an INT_IFCMP when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP64(OPT_Instruction s,
                           OPT_RegisterOperand val1, OPT_Operand val2,
                           OPT_ConditionOperand cond,
                           boolean immediate) {
    OPT_RegisterOperand cr = regpool.makeTempCondition();
    OPT_Operator op;
    if (immediate) {
      op = cond.isUNSIGNED() ? PPC64_CMPLI : PPC64_CMPI;
    } else {
      op = cond.isUNSIGNED() ? PPC64_CMPL : PPC64_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, val1, val2));
    EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, cr.copyD2U(),
                               new OPT_PowerPCConditionOperand(cond),
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }
  //-#endif

  /**
   * emit basic code to handle an INT_IFCMP2 when no folding
   * of the compare into some other computation is possible.
   */
  protected final void CMP2(OPT_Instruction s,
                            OPT_RegisterOperand val1, OPT_Operand val2,
                            OPT_ConditionOperand cond1, OPT_ConditionOperand cond2,
                            boolean immediate) {
    OPT_Operator op1;
    OPT_Operator op2;
    if (immediate) {
      op1 = cond1.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
      op2 = cond2.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op1 = cond1.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
      op2 = cond2.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    if (op1 == op2) {
      OPT_RegisterOperand cr = regpool.makeTempCondition();
      EMIT(MIR_Binary.create(op1, cr, val1, val2));
      EMIT(MIR_CondBranch2.mutate(s, PPC_BCOND2, cr.copyD2U(),
                                  new OPT_PowerPCConditionOperand(cond1),
                                  IfCmp2.getTarget1(s),
                                  IfCmp2.getBranchProfile1(s),
                                  new OPT_PowerPCConditionOperand(cond2),
                                  IfCmp2.getTarget2(s),
                                  IfCmp2.getBranchProfile2(s)));
    } else {
      OPT_RegisterOperand cr1 = regpool.makeTempCondition();
      OPT_RegisterOperand cr2 = regpool.makeTempCondition();
      EMIT(MIR_Binary.create(op1, cr1, val1, val2));
      EMIT(MIR_Binary.create(op2, cr2, val1, val2));
      EMIT(MIR_CondBranch.create(PPC_BCOND, cr1.copyD2U(),
                                 new OPT_PowerPCConditionOperand(cond1),
                                 IfCmp2.getTarget1(s),
                                 IfCmp2.getBranchProfile1(s)));
      EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, cr2.copyD2U(),
                                 new OPT_PowerPCConditionOperand(cond2),
                                 IfCmp2.getTarget2(s),
                                 IfCmp2.getBranchProfile2(s)));
    }
  }


  /**
   * Uses the record capability to avoid compare 
   */
  protected final void CMP_ZERO(OPT_Instruction s, OPT_Operator op, 
                                OPT_RegisterOperand def, OPT_Operand left,
                                OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    if (!def.register.spansBasicBlock()) {
      def.register = regpool.getPhysicalRegisterSet().getTemp();
    }
    EMIT(MIR_Unary.create(op, def, left));
    EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
                               new OPT_PowerPCConditionOperand(cond), 
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  protected final void CMP_ZERO(OPT_Instruction s, OPT_Operator op, 
                                OPT_RegisterOperand def, OPT_RegisterOperand left, 
                                OPT_Operand right, OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    if (!def.register.spansBasicBlock()) {
      def.register = regpool.getPhysicalRegisterSet().getTemp();
    }
    EMIT(MIR_Binary.create(op, def, left, right));
    EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
                               new OPT_PowerPCConditionOperand(cond), 
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  protected final void CMP_ZERO_AND_MASK(OPT_Instruction s, 
                                         OPT_RegisterOperand def, 
                                         OPT_RegisterOperand left, 
                                         OPT_IntConstantOperand Mask,
                                         OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM._assert(!cond.isUNSIGNED());
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, IC(0), 
                                    IC((ME + 1) & 0x1f), IC(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      EMIT(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, IC(0), 
                                    IC(MB), IC(ME)));
    }
    EMIT(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
                               new OPT_PowerPCConditionOperand(cond), 
                               IfCmp.getTarget(s),
                               IfCmp.getBranchProfile(s)));
  }

  // boolean compare
  // Support for boolean cmp 
  private OPT_ConditionOperand cc;
  private OPT_Operand val1;
  private OPT_Operand val2;
  protected final void PUSH_BOOLCMP(OPT_ConditionOperand c,
                                    OPT_Operand v1,
                                    OPT_Operand v2) {
    if (VM.VerifyAssertions) VM._assert(cc == null);
    cc = c;
    val1 = v1;
    val2 = v2;
  }
  protected final void FLIP_BOOLCMP() {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    cc = cc.flipCode();
  }

  protected final void EMIT_PUSHED_BOOLCMP(OPT_RegisterOperand res) {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    if (val2 instanceof OPT_IntConstantOperand) {
      BOOLEAN_CMP_IMM(res, cc, R(val1), IC(val2));
    } else {
      BOOLEAN_CMP(res, cc, R(val1), R(val2));
    }
    if (VM.VerifyAssertions) {
      cc = null;
      val1 = null;
      val2 = null;
    }
  }

  protected final void EMIT_BOOLCMP_BRANCH(OPT_BranchOperand target,
                                           OPT_BranchProfileOperand bp) {
    if (VM.VerifyAssertions) VM._assert(cc != null);
    OPT_RegisterOperand cr = regpool.makeTempCondition();
    OPT_Operator op;
    if (val2 instanceof OPT_IntConstantOperand) {
      op = cc.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op = cc.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    EMIT(MIR_Binary.create(op, cr, R(val1), val2));
    EMIT(MIR_CondBranch.create(PPC_BCOND, cr.copyD2U(),
                               new OPT_PowerPCConditionOperand(cc),
                               target, bp));
    if (VM.VerifyAssertions) {
      cc = null;
      val1 = null;
      val2 = null;
    }
  }


  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 199 
   */
  protected final void BOOLEAN_CMP_IMM(OPT_RegisterOperand def, 
                                       OPT_ConditionOperand cmp,
                                       OPT_RegisterOperand one, 
                                       OPT_IntConstantOperand two) {
    OPT_Register t1, t = regpool.getInteger();
    OPT_Register zero = regpool.getPhysicalRegisterSet().getTemp();
    int value = two.value;
    switch (cmp.value) {
    case OPT_ConditionOperand.EQUAL:
      if (value == 0) {
        EMIT(MIR_Unary.create(PPC_CNTLZW, R(t), one)); 
      } else {
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(t), one, IC(value)));
        EMIT(MIR_Unary.create(PPC_CNTLZW, R(t), R(t)));
      }
      EMIT(MIR_Binary.create(PPC_SRWI, def, R(t), IC(5)));
      break;
    case OPT_ConditionOperand.NOT_EQUAL:
      if (value == 0) {
        EMIT(MIR_Binary.create(PPC_ADDIC, R(t), one, IC(-1)));
        EMIT(MIR_Binary.create(PPC_SUBFE, def, R(t), one.copyRO()));
      } else {
        t1 = regpool.getInteger();
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(t1), one, IC(value)));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(t), R(t1), IC(-1)));
        EMIT(MIR_Binary.create(PPC_SUBFE, def, R(t), R(t1)));
      }
      break;
    case OPT_ConditionOperand.LESS:
      if (value == 0) {
        EMIT(MIR_Binary.create(PPC_SRWI, def, one, IC(31)));
      } else if (value > 0) {
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(zero), one, 
                               IC(value - 1)));
        EMIT(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else if (value != 0xFFFF8000) {
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), 
                               IC(value - 1)));
        EMIT(MIR_Unary.create(PPC_ADDME, def, R(t)));
      } else {                  // value = 0xFFFF8000
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Unary.create(PPC_LDIS, R(zero), IC(1)));
        EMIT(MIR_Binary.create(PPC_SUBFC, R(zero), one.copyRO(), R(zero)));
        EMIT(MIR_Unary.create(PPC_ADDME, def, R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER:
      if (value == 0) {
        EMIT(MIR_Unary.create(PPC_NEG, R(t), one));
        EMIT(MIR_Binary.create(PPC_ANDC, R(t), R(t), one.copyRO()));
        EMIT(MIR_Binary.create(PPC_SRWI, def, R(t), IC(31)));
      } else if (value >= 0) {
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), 
                               IC(-value - 1)));
        EMIT(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else {
        t1 = regpool.getInteger();
        EMIT(MIR_Unary.create(PPC_LDI, R(t1), IC(1)));
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), 
                               IC(-value - 1)));
        EMIT(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
      }
      break;
    case OPT_ConditionOperand.LESS_EQUAL:
      if (value == 0) {
        EMIT(MIR_Binary.create(PPC_ADDI, R(t), one, IC(-1)));
        EMIT(MIR_Binary.create(PPC_OR, R(t), R(t), one.copyRO()));
        EMIT(MIR_Binary.create(PPC_SRWI, def, R(t), IC(31)));
      } else if (value >= 0) {
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), IC(value)));
        EMIT(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else {
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), IC(value)));
        EMIT(MIR_Unary.create(PPC_ADDME, def, R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER_EQUAL:
      if (value == 0) {
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_XORI, def, R(t), IC(1)));
      } else if (value >= 0) {
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), IC(-value)));
        EMIT(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else if (value != 0xFFFF8000) {
        t1 = regpool.getInteger();
        EMIT(MIR_Unary.create(PPC_LDI, R(t1), IC(1)));
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), IC(-value)));
        EMIT(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
      } else {
        EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null));
        // value == 0xFFFF8000; code sequence below does not work --dave 7/25/02
        break;
        /*
          t1 = regpool.getInteger();
          EMIT(MIR_Unary.create(PPC_LDI, R(t1), IC(1)));
          EMIT(MIR_Binary.create(PPC_SRAWI, R(t), one, IC(31)));
          EMIT(MIR_Unary.create(PPC_LDIS, R(zero), IC(1)));
          EMIT(MIR_Binary.create(PPC_ADDC, R(zero), one.copyRO(), R(zero)));
          EMIT(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
        */
      }
      break;
    case OPT_ConditionOperand.HIGHER:
      EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null)); // todo
      break;
    case OPT_ConditionOperand.LOWER:
      EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null)); // todo
      break;
    case OPT_ConditionOperand.HIGHER_EQUAL:
      EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null)); // todo
      break;
    case OPT_ConditionOperand.LOWER_EQUAL:
      EMIT(MIR_Unary.create(PPC_LDI, R(t), IC(-1)));
      EMIT(MIR_Binary.create(PPC_SUBFIC, R(zero), one, IC(value)));
      EMIT(MIR_Unary.create(PPC_SUBFZE, def, R(t)));
      break;

    default:
      EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BOOLEAN_CMP(OPT_RegisterOperand def, 
                                   OPT_ConditionOperand cmp,
                                   OPT_RegisterOperand one,
                                   OPT_RegisterOperand two) {
    OPT_Register t1, zero, t = regpool.getInteger();
    switch (cmp.value) {
    case OPT_ConditionOperand.EQUAL:
      {
        EMIT(MIR_Binary.create(PPC_SUBF, R(t), one, two));
        EMIT(MIR_Unary.create(PPC_CNTLZW, R(t), R(t)));
        EMIT(MIR_Binary.create(PPC_SRWI, def, R(t), IC(5)));
      }
      break;
    case OPT_ConditionOperand.NOT_EQUAL:
      {
        t1 = regpool.getInteger();
        EMIT(MIR_Binary.create(PPC_SUBF, R(t), one, two));
        EMIT(MIR_Binary.create(PPC_ADDIC, R(t1), R(t), IC(-1)));
        EMIT(MIR_Binary.create(PPC_SUBFE, def, R(t1), R(t)));
      }
      break;
    case OPT_ConditionOperand.LESS_EQUAL:
      {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t1), two, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFC, R(zero), one.copyRO(), two.copyRO()));
        EMIT(MIR_Binary.create(PPC_ADDE, def, R(t1), R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER_EQUAL:
      {
        t1 = regpool.getInteger();
        zero = regpool.getPhysicalRegisterSet().getTemp();
        EMIT(MIR_Binary.create(PPC_SRWI, R(t), two, IC(31)));
        EMIT(MIR_Binary.create(PPC_SRAWI, R(t1), one, IC(31)));
        EMIT(MIR_Binary.create(PPC_SUBFC, R(zero), two.copyRO(), one.copyRO()));
        EMIT(MIR_Binary.create(PPC_ADDE, def, R(t1), R(t)));
      }
      break;
    default:
      EMIT(BooleanCmp.create(BOOLEAN_CMP, def, one, two, cmp, null)); // todo
    }
  }

  protected final void BYTE_LOAD(OPT_Instruction s, 
                                 OPT_Operator opcode, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left, 
                                 OPT_Operand right, 
                                 OPT_LocationOperand loc, 
                                 OPT_Operand guard) {
    OPT_RegisterOperand reg1 = regpool.makeTempInt();
    EMIT(MIR_Load.mutate(s, opcode, reg1, left, right, loc, guard));
    EMIT(MIR_Unary.create(PPC_EXTSB, def, reg1.copyD2U()));
  }

  private final int PowerOf2 (int v) {
    int i = 31;
    int power = -1;
    for (; v != 0; v = v << 1, i--)
      if (v < 0) {
        if (power == -1)
          power = i; 
        else 
          return  -1;
      }
    return  power;
  }

  /**
   * Emits instructions to perform an integer shift operation. This is because
   * we need to take the logical AND of the RHS (the amount to shift by) with
   * 31 before we perform the shift. Thus, we create and use a new temporary
   * register to hold the (RHS &amp; 0x1f).
   * @param s instruction, containing the destination register and operands
   * (both registers) - the value <code>P(p)</code> one uses in PPC32.rules.
   * @param shift the shift operation to perform: one of <code>PPC_SLW</code>,
   * <code>PPC_SRW</code>, or <code>PPC_SRAW</code>
   */
  protected void INT_SHIFT(OPT_Instruction s, OPT_Operator shift)
  {
    if (VM.VerifyAssertions) VM._assert(shift==PPC_SRW || shift==PPC_SRAW ||
                                        shift==PPC_SLW);
        OPT_Register t0=regpool.getInteger();
        EMIT(MIR_Binary.create(PPC_ANDIr, I(t0), R(Binary.getVal2(s)), IC(0x1f)));
        EMIT(MIR_Binary.mutate(s, shift, Binary.getResult(s),
                           R(Binary.getVal1(s)), I(t0)));
  }

  protected final void INT_DIV_IMM(OPT_Instruction s, 
                                   OPT_RegisterOperand def, 
                                   OPT_RegisterOperand left, 
                                   OPT_RegisterOperand c, 
                                   OPT_IntConstantOperand right) {
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.create(PPC_SRAWI, c, left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, def, c.copyD2U()));
    } else {
      IntConstant(c.register, right.value);
      EMIT(MIR_Binary.mutate(s, PPC_DIVW, def, left, c));
    }
  }

  protected final void INT_REM(OPT_Instruction s, 
                               OPT_RegisterOperand def, 
                               OPT_RegisterOperand left, 
                               OPT_RegisterOperand right) {
    OPT_Register temp = regpool.getInteger();
    EMIT(MIR_Binary.mutate(s, PPC_DIVW, I(temp), left, right));
    EMIT(MIR_Binary.create(PPC_MULLW, I(temp), I(temp), 
                           right.copyU2U()));
    EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
  }

  protected final void INT_REM_IMM(OPT_Instruction s, 
                                   OPT_RegisterOperand def, 
                                   OPT_RegisterOperand left, 
                                   OPT_RegisterOperand c, 
                                   OPT_IntConstantOperand right) {
    OPT_Register temp = regpool.getInteger();
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.mutate(s, PPC_SRAWI, I(temp), left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, I(temp), I(temp)));
      EMIT(MIR_Binary.create(PPC_SLWI, I(temp), I(temp), IC(power)));
      EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
    } else {
      IntConstant(c.register, right.value);
      EMIT(MIR_Binary.mutate(s, PPC_DIVW, I(temp), left, c));
      EMIT(MIR_Binary.create(PPC_MULLW, I(temp), I(temp), c.copyU2U()));
      EMIT(MIR_Binary.create(PPC_SUBF, def, I(temp), left.copyU2U()));
    }
  }

  /**
   * Conversion
   */
  protected final void INT_2LONG(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    OPT_Register defHigh = def.register;
    //-#if RVM_FOR_32_ADDR
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    EMIT(MIR_Move.mutate(s, PPC_MOVE, I(defLow), left));
    EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), left.copyU2U(), IC(31)));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    //EMIT(MIR_Move.mutate(s, PPC_MOVE, L(defHigh), left));
    EMIT(MIR_Unary.create(PPC64_EXTSW, def, left));
    //-#endif

  }

  protected final void INT_2ADDRSigExt(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, left));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Unary.create(PPC64_EXTSW, def, left)); 
    //-#endif

  }

  protected final void INT_2ADDRZerExt(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, left));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Unary.create(PPC64_EXTZW, def, left)); 
    //-#endif

  }

  //-#if RVM_FOR_64_ADDR
  protected final void LONG_2ADDR(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, left));
  }
  //-#endif
  
  protected final void ADDR_2INT(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, left));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Unary.create(PPC64_EXTSW, def, left)); 
    //-#endif
  }

  protected final void ADDR_2LONG(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    //-#if RVM_FOR_32_ADDR
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    EMIT(MIR_Move.mutate(s, PPC_MOVE, I(defLow), left));
    EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), left.copyU2U(), IC(31)));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, left));
    //-#endif
  }

  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 83 
   */
  protected final void INT_2DOUBLE(OPT_Instruction s, 
                                   OPT_RegisterOperand def, 
                                   OPT_RegisterOperand left) {
    OPT_Register res = def.register;
    OPT_Register src = left.register;
    OPT_Register FP = regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand temp = regpool.makeTempInt();
    int p = burs.ir.stackManager.allocateSpaceForConversion();
    EMIT(MIR_Unary.mutate(s, PPC_LDIS, temp, IC(0x4330)));
    // TODO: valid location?
    EMIT(MIR_Store.create(PPC_STW, I(temp.register), A(FP), IC(p), 
                          new OPT_TrueGuardOperand()));
    OPT_Register t1 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_XORIS, I(t1), I(src), IC(0x8000)));
    EMIT(MIR_Store.create(PPC_STW, I(t1), A(FP), IC(p + 4), 
                          new OPT_TrueGuardOperand()));
    EMIT(MIR_Load.create(PPC_LFD, D(res), A(FP), IC(p)));
    OPT_Register tempF = regpool.getDouble();
    emitLFtoc(PPC_LFD, tempF, VM_Entrypoints.I2DconstantField);
    EMIT(MIR_Binary.create(PPC_FSUB, D(res), D(res), D(tempF)));
  }

  // LONG arithmetic:
  protected final void LONG_2INT(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    OPT_Register srcHigh = left.register;
    //-#if RVM_FOR_32_ADDR
    OPT_Register srcLow = regpool.getSecondReg(srcHigh);
    EMIT(MIR_Move.mutate(s, PPC_MOVE, def, I(srcLow)));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Unary.create(PPC64_EXTSW, def, left)); //sign extend to prevent highest bits to be faulty
    //-#endif
  }

  protected final void LONG_MOVE(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Move.create(PPC_MOVE, I(defReg), I(leftReg)));
    EMIT(MIR_Move.create(PPC_MOVE, 
                         I(regpool.getSecondReg(defReg)), 
                         I(regpool.getSecondReg(leftReg))));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Move.create(PPC_MOVE, L(defReg), L(leftReg)));
    //-#endif

  }
  
  final static boolean fits (long val, int bits) {
      val = val >> bits-1;
      return (val == 0L || val == -1L);
  }

  //-#if RVM_FOR_64_ADDR
  protected final void LONG_CONSTANT(OPT_Instruction s, 
                                     OPT_RegisterOperand def, 
                                     OPT_AddressConstantOperand left) {
    LONG_CONSTANT(s, def, LC(left.value.toLong()));
  }
  //-#endif

  protected final void LONG_CONSTANT(OPT_Instruction s, 
                                     OPT_RegisterOperand def, 
                                     OPT_LongConstantOperand left) {
    //-#if RVM_FOR_32_ADDR
    long value = left.value;
    int valueHigh = (int)(value >> 32);
    int valueLow = (int)(value & 0xffffffff);
    OPT_Register register = def.register;
    IntConstant(register, valueHigh);
    IntConstant(regpool.getSecondReg(register), valueLow);
    //-#endif
    //-#if RVM_FOR_64_ADDR
    long value = left.value;
    int bytes01 = (int) (value & 0x000000000000ffffL);
    int bytes23 = (int) ((value & 0x00000000ffff0000L) >>> 16);
    int bytes45 = (int) ((value & 0x0000ffff00000000L) >>> 32);
    int bytes67 = (int) ((value & 0xffff000000000000L) >>> 48);
    OPT_Register register = def.register;
    OPT_Register temp1 = regpool.getLong();
    if (fits(value, 16)){
      EMIT(MIR_Unary.create(PPC_LDI, L(register), IC(bytes01)));
    } else if (fits(value, 32)) {
      EMIT(MIR_Unary.create(PPC_LDIS, L(register), IC(bytes23)));
      if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
    } else if (fits(value, 48)) {
      EMIT(MIR_Unary.create(PPC_LDI, L(register), IC(bytes45)));
      EMIT(MIR_Binary.create(PPC64_SLDI, L(register), L(register), IC(32)));
      if (bytes23 != 0) EMIT(MIR_Binary.create(PPC_ORIS, L(register), L(register), IC(bytes23)));
      if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
    } else {
      EMIT(MIR_Unary.create(PPC_LDIS, L(register), IC(bytes67)));
      if (bytes45 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes45)));
      EMIT(MIR_Binary.create(PPC64_SLDI, L(register), L(register), IC(32)));
      if (bytes23 != 0) EMIT(MIR_Binary.create(PPC_ORIS, L(register), L(register), IC(bytes23)));
      if (bytes01 != 0) EMIT(MIR_Binary.create(PPC_ORI, L(register), L(register), IC(bytes01)));
    }
    //-#endif
  }

  protected final void LONG_ADD(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_RegisterOperand right) {
    //-#if RVM_FOR_32_ADDR
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    OPT_Register rightReg = right.register;
    EMIT(MIR_Binary.create(PPC_ADDC, 
                           I(regpool.getSecondReg(defReg)), 
                           I(regpool.getSecondReg(leftReg)), 
                           I(regpool.getSecondReg(rightReg))));
    EMIT(MIR_Binary.create(PPC_ADDE, I(defReg), I(leftReg), 
                           I(rightReg)));
    //-#endif
  }

  /* Notice: switching operands! */
  protected final void LONG_SUB(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_RegisterOperand right) {
    //-#if RVM_FOR_32_ADDR
    OPT_Register defReg = def.register;
    OPT_Register leftReg = right.register;
    OPT_Register rightReg = left.register;
    EMIT(MIR_Binary.create(PPC_SUBFC, 
                           I(regpool.getSecondReg(defReg)), 
                           I(regpool.getSecondReg(leftReg)), 
                           I(regpool.getSecondReg(rightReg))));
    EMIT(MIR_Binary.create(PPC_SUBFE, I(defReg), 
                           I(leftReg), I(rightReg)));
    //-#endif
  }

  protected final void LONG_NEG(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left) {
    //-#if RVM_FOR_32_ADDR
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    EMIT(MIR_Binary.create(PPC_SUBFIC, 
                           I(regpool.getSecondReg(defReg)), 
                           I(regpool.getSecondReg(leftReg)), 
                           IC(0)));
    EMIT(MIR_Unary.create(PPC_SUBFZE, I(defReg), I(leftReg)));
    //-#endif
  }

  protected final void LONG_NOT(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Binary.create(PPC_NOR, I(defReg), I(leftReg), I(leftReg)));
    EMIT(MIR_Binary.create(PPC_NOR, 
                           I(regpool.getSecondReg(defReg)), 
                           I(regpool.getSecondReg(leftReg)), 
                           I(regpool.getSecondReg(leftReg))));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Binary.create(PPC_NOR, L(defReg), L(leftReg), L(leftReg)));
    //-#endif
  }

  protected final void LONG_LOG(OPT_Instruction s, 
                                OPT_Operator operator, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_RegisterOperand right) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    OPT_Register rightReg = right.register;
    //-#if RVM_FOR_32_ADDR
    EMIT(MIR_Binary.create(operator, I(defReg), 
                           I(leftReg), I(rightReg)));
    EMIT(MIR_Binary.create(operator, 
                           I(regpool.getSecondReg(defReg)), 
                           I(regpool.getSecondReg(leftReg)), 
                           I(regpool.getSecondReg(rightReg))));
    //-#endif
    //-#if RVM_FOR_64_ADDR
    EMIT(MIR_Binary.create(operator, L(defReg),
                           L(leftReg), L(rightReg)));
    //-#endif
  }

  //-#if RVM_FOR_32_ADDR
  /**
   * taken from "PowerPC Microprocessor Family, 
   * The Programming Environment for 32-bit Microprocessors 
   * */
  protected final void LONG_SHL(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_RegisterOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = regpool.getSecondReg(leftHigh);
    OPT_Register shift = right.register;
    OPT_Register t0 = regpool.getInteger();
    OPT_Register t31 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_SUBFIC, I(t31), I(shift), IC(32)));
    EMIT(MIR_Binary.create(PPC_SLW, I(defHigh), I(leftHigh), I(shift)));
    EMIT(MIR_Binary.create(PPC_SRW, I(t0), I(leftLow), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defHigh), I(defHigh), I(t0)));
    EMIT(MIR_Binary.create(PPC_ADDI, I(t31), I(shift), IC(-32)));
    EMIT(MIR_Binary.create(PPC_SLW, I(t0), I(leftLow), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defHigh), I(defHigh), I(t0)));
    EMIT(MIR_Binary.create(PPC_SLW, I(defLow), I(leftLow), I(shift)));
  }

  protected final void LONG_SHL_IMM(OPT_Instruction s, 
                                    OPT_RegisterOperand def, 
                                    OPT_RegisterOperand left, 
                                    OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defHigh), 
                                    I(leftHigh), 
                                    IC(shift), IC(0), IC(31 - shift)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI, I(defHigh), I(defHigh), 
                                    I(leftLow), IC(shift), 
                                    IC(32 - shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), 
                                    IC(shift), IC(0), IC(31 - shift)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defHigh), I(leftLow)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    } else {
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SLWI, I(defHigh), I(leftLow), 
                             IC(shift)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defLow), IC(0)));
    }
  }

  protected final void LONG_USHR(OPT_Instruction s, 
                                 OPT_RegisterOperand def, 
                                 OPT_RegisterOperand left, 
                                 OPT_RegisterOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = regpool.getSecondReg(leftHigh);
    OPT_Register shift = right.register;
    OPT_Register t0 = regpool.getInteger();
    OPT_Register t31 = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_SUBFIC, I(t31), I(shift), IC(32)));
    EMIT(MIR_Binary.create(PPC_SRW, I(defLow), I(leftLow), I(shift)));
    EMIT(MIR_Binary.create(PPC_SLW, I(t0), I(leftHigh), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defLow), I(defLow), I(t0)));
    EMIT(MIR_Binary.create(PPC_ADDI, I(t31), I(shift), IC(-32)));
    EMIT(MIR_Binary.create(PPC_SRW, I(t0), I(leftHigh), I(t31)));
    EMIT(MIR_Binary.create(PPC_OR, I(defLow), I(defLow), I(t0)));
    EMIT(MIR_Binary.create(PPC_SRW, I(defHigh), I(leftHigh), I(shift)));
  }

  protected final void LONG_USHR_IMM(OPT_Instruction s, 
                                     OPT_RegisterOperand def, 
                                     OPT_RegisterOperand left, 
                                     OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), 
                                    IC(32 - shift), IC(shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI, I(defLow), I(defLow), 
                                    I(leftHigh), IC(32 - shift), 
                                    IC(0), IC(shift - 1)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defHigh), 
                                    I(leftHigh),
                                    IC(32 - shift), IC(shift), IC(31)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defLow), I(leftHigh)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
    } else {
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SRWI, I(defLow), I(leftHigh), 
                             IC(shift)));
      EMIT(MIR_Unary.create(PPC_LDI, I(defHigh), IC(0)));
    }
  }

  protected final void LONG_SHR_IMM(OPT_Instruction s, 
                                    OPT_RegisterOperand def, 
                                    OPT_RegisterOperand left, 
                                    OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      EMIT(MIR_RotateAndMask.create(PPC_RLWINM, I(defLow), I(leftLow), 
                                    IC(32 - shift), IC(shift), IC(31)));
      EMIT(MIR_RotateAndMask.create(PPC_RLWIMI, I(defLow), I(defLow), 
                                    I(leftHigh), IC(32 - shift), IC(0), 
                                    IC(shift - 1)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), 
                             IC(shift)));
    } else if (shift == 32) {
      EMIT(MIR_Move.create(PPC_MOVE, I(defLow), I(leftHigh)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), 
                             IC(31)));
    } else {
      shift = shift - 32;
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defLow), I(leftHigh), 
                             IC(shift)));
      EMIT(MIR_Binary.create(PPC_SRAWI, I(defHigh), I(leftHigh), 
                             IC(31)));
    }
  }

  protected final void LONG_MUL(OPT_Instruction s, 
                                OPT_RegisterOperand def, 
                                OPT_RegisterOperand left, 
                                OPT_RegisterOperand right) {
    OPT_Register dH = def.register;
    OPT_Register dL = regpool.getSecondReg(dH);
    OPT_Register lH = left.register;
    OPT_Register lL = regpool.getSecondReg(lH);
    OPT_Register rH = right.register;
    OPT_Register rL = regpool.getSecondReg(rH);
    OPT_Register tH = regpool.getInteger();
    OPT_Register t = regpool.getInteger();
    EMIT(MIR_Binary.create(PPC_MULHWU, I(tH), I(lL), I(rL)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(t), I(lL), I(rH)));
    EMIT(MIR_Binary.create(PPC_ADD, I(tH), I(tH), I(t)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(t), I(lH), I(rL)));
    EMIT(MIR_Binary.create(PPC_ADD, I(dH), I(tH), I(t)));
    EMIT(MIR_Binary.create(PPC_MULLW, I(dL), I(lL), I(rL)));
  }
  //-#endif
 
  // LONG_DIV and LONG_REM are handled by system calls in 32-bit version
  //-#if RVM_FOR_64_ADDR
  void LONG_DIV_IMM(OPT_Instruction s,
                    OPT_RegisterOperand def,
                    OPT_RegisterOperand left,
                    OPT_RegisterOperand c,
                    OPT_IntConstantOperand right) {
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.create(PPC64_SRADI, c, left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, def, c.copyD2U()));
    } else {
      IntConstant(c.register, right.value);
      EMIT(MIR_Binary.mutate(s, PPC64_DIVD, def, left, c));
    }
  }

  void LONG_REM(OPT_Instruction s,
                OPT_RegisterOperand def,
                OPT_RegisterOperand left,
                OPT_RegisterOperand right) {
    OPT_Register temp = regpool.getLong();
    EMIT(MIR_Binary.mutate(s, PPC64_DIVD, L(temp), left, right));
    EMIT(MIR_Binary.create(PPC64_MULLD, L(temp), L(temp),
                           right.copyU2U()));
    EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
  }

  void LONG_REM_IMM(OPT_Instruction s,
                    OPT_RegisterOperand def,
                    OPT_RegisterOperand left,
                    OPT_RegisterOperand c,
                    OPT_IntConstantOperand right) {
    OPT_Register temp = regpool.getLong();
    int power = PowerOf2(right.value);
    if (power != -1) {
      EMIT(MIR_Binary.mutate(s, PPC64_SRADI, L(temp), left, IC(power)));
      EMIT(MIR_Unary.create(PPC_ADDZE, L(temp), L(temp)));
      EMIT(MIR_Binary.create(PPC64_SLDI, L(temp), L(temp), IC(power)));
      EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
    } else {
      IntConstant(c.register, right.value);
      EMIT(MIR_Binary.mutate(s, PPC64_DIVD, L(temp), left, c));
      EMIT(MIR_Binary.create(PPC64_MULLD, L(temp), L(temp), c.copyU2U()));
      EMIT(MIR_Binary.create(PPC_SUBF, def, L(temp), left.copyU2U()));
    }
  }
  //-#endif

  //-#if RVM_FOR_32_ADDR
  protected final void LONG_LOAD_addi(OPT_Instruction s, 
                                      OPT_RegisterOperand def, 
                                      OPT_RegisterOperand left, 
                                      OPT_IntConstantOperand right, 
                                      OPT_LocationOperand loc, 
                                      OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions) VM._assert(imm < (0x8000 - 4));
    OPT_Instruction inst = MIR_Load.create(PPC_LWZ, 
                                           I(defHigh), 
                                           left, IC(imm), loc, 
                                           guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (OPT_LocationOperand)loc.copy();
    }
    inst = MIR_Load.create(PPC_LWZ, I(defLow), left.copyU2U(), 
                           IC(imm + 4), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_LOAD_addis(OPT_Instruction s, 
                                       OPT_RegisterOperand def, 
                                       OPT_RegisterOperand left, 
                                       OPT_RegisterOperand right, 
                                       OPT_IntConstantOperand Value, 
                                       OPT_LocationOperand loc, 
                                       OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    int value = Value.value;
    EMIT(MIR_Binary.create(PPC_ADDIS, right, left, 
                           IC(OPT_Bits.PPCMaskUpper16(value))));
    OPT_Instruction inst = MIR_Load.create(PPC_LWZ, I(defHigh), 
                                           right.copyD2U(), 
                                           IC(OPT_Bits.PPCMaskLower16(value)), 
                                           loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null) {
      loc = (OPT_LocationOperand)loc.copy();
    }
    inst = MIR_Load.create(PPC_LWZ, I(defLow), 
                           right.copyD2U(), 
                           IC(OPT_Bits.PPCMaskLower16(value) + 4), 
                           loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_LOAD_addx(OPT_Instruction s, 
                                      OPT_RegisterOperand def, 
                                      OPT_RegisterOperand left, 
                                      OPT_RegisterOperand right, 
                                      OPT_LocationOperand loc, 
                                      OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Instruction inst = 
      MIR_Load.create(PPC_LWZX, I(defHigh), left, right, loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    OPT_RegisterOperand kk = regpool.makeTempInt();
    EMIT(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), IC(4)));
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = MIR_Load.create(PPC_LWZX, I(defLow), left.copyU2U(), 
                           kk.copyD2U(), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addi(OPT_Instruction s, 
                                       OPT_RegisterOperand def, 
                                       OPT_RegisterOperand left, 
                                       OPT_IntConstantOperand right, 
                                       OPT_LocationOperand loc, 
                                       OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions)
      VM._assert(imm < (0x8000 - 4));
    OPT_Instruction inst = MIR_Store.create(PPC_STW, I(defHigh), 
                                            left, IC(imm), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = MIR_Store.create(PPC_STW, I(defLow), 
                            left.copyU2U(), 
                            IC(imm + 4), loc, 
                            guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addis(OPT_Instruction s, 
                                        OPT_RegisterOperand def, 
                                        OPT_RegisterOperand left, 
                                        OPT_RegisterOperand right, 
                                        OPT_IntConstantOperand Value, 
                                        OPT_LocationOperand loc, 
                                        OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    int value = Value.value;
    EMIT(MIR_Binary.create(PPC_ADDIS, right, left, 
                           IC(OPT_Bits.PPCMaskUpper16(value))));
    OPT_Instruction inst = 
      MIR_Store.create(PPC_STW, I(defHigh), 
                       right.copyD2U(), 
                       IC(OPT_Bits.PPCMaskLower16(value)), 
                       loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = MIR_Store.create(PPC_STW, I(defLow), right.copyD2U(), 
                            IC(OPT_Bits.PPCMaskLower16(value) + 4), loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
  }

  protected final void LONG_STORE_addx(OPT_Instruction s, 
                                       OPT_RegisterOperand def, 
                                       OPT_RegisterOperand left, 
                                       OPT_RegisterOperand right, 
                                       OPT_LocationOperand loc, 
                                       OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = regpool.getSecondReg(defHigh);
    OPT_Instruction inst = MIR_Store.create(PPC_STWX, I(defHigh), left, 
                                            right, loc, guard);
    inst.copyPosition(s);
    EMIT(inst);
    OPT_RegisterOperand kk = regpool.makeTempInt();
    EMIT(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), IC(4)));
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = MIR_Store.create(PPC_STWX, I(defLow), 
                            left.copyU2U(), 
                            kk.copyD2U(), loc, 
                            guard);
    inst.copyPosition(s);
    EMIT(inst);
  }
  //-#endif

  protected final void DOUBLE_IFCMP(OPT_Instruction s, OPT_Operator op, 
                                    OPT_RegisterOperand left, 
                                    OPT_Operand right) {
    boolean UeqL = (op == DOUBLE_CMPL) || (op == FLOAT_CMPL);
    boolean UeqG = (op == DOUBLE_CMPG) || (op == FLOAT_CMPG);
    OPT_ConditionOperand c = IfCmp.getCond(s);
    OPT_BranchOperand target = IfCmp.getTarget(s);
    OPT_BranchOperand branch = null;
    OPT_BasicBlock bb = s.getBasicBlock();
    if (c.value == OPT_ConditionOperand.EQUAL || 
        (UeqL && (c.value == OPT_ConditionOperand.GREATER || 
                  c.value == OPT_ConditionOperand.GREATER_EQUAL)) || 
        (UeqG && (c.value == OPT_ConditionOperand.LESS || 
                  c.value == OPT_ConditionOperand.LESS_EQUAL))) {
      OPT_Instruction lastInstr = bb.lastRealInstruction();
      if (lastInstr.operator() == GOTO) {
        // We're in trouble if there is another instruction between 
        // s and lastInstr!
        if (VM.VerifyAssertions)
          VM._assert(s.nextInstructionInCodeOrder() == lastInstr);
        // Set branch = target of GOTO
        branch = (OPT_BranchOperand)Goto.getTarget(lastInstr);
      } else {
        // Set branch = label of next (fallthrough basic block)
        branch = bb.nextBasicBlockInCodeOrder().makeJumpTarget();
      }
    } else {
      branch = (OPT_BranchOperand)target.copy();
    }
    OPT_RegisterOperand cr = regpool.makeTempCondition();
    EMIT(MIR_Binary.create(PPC_FCMPU, cr, left, right));

    // Propagate branch probabilities as follows:  assume the
    // probability of overflow (first condition) is zero, and
    // propagate the original probability to the second condition.
    EMIT(MIR_CondBranch2.create(PPC_BCOND2, cr.copyD2U(), 
                                OPT_PowerPCConditionOperand.UNORDERED(),
                                branch, 
                                new OPT_BranchProfileOperand(0f),
                                new OPT_PowerPCConditionOperand(c), 
                                target,
                                IfCmp.getBranchProfile(s)));
  }


  /**
   * Expansion of LOWTABLESWITCH.  
   *
   * @param s the instruction to expand
   */
  protected final void LOWTABLESWITCH(OPT_Instruction s) {
    // (1) We're changing index from a U to a DU.
    //     Inject a fresh copy instruction to make sure we aren't
    //     going to get into trouble (if someone else was also using index).
    OPT_RegisterOperand newIndex = regpool.makeTempInt(); 
    EMIT(MIR_Move.create(PPC_MOVE, newIndex, LowTableSwitch.getIndex(s))); 
    int number = LowTableSwitch.getNumberOfTargets(s);
    OPT_Instruction s2 = CPOS(s,MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex, number*2));
    for (int i=0; i<number; i++) {
      MIR_LowTableSwitch.setTarget(s2,i,LowTableSwitch.getTarget(s,i));
      MIR_LowTableSwitch.setBranchProfile(s2,i,LowTableSwitch.getBranchProfile(s,i));
    }
    EMIT(s2);
  }




  // Take the generic LIR trap_if and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See VM_TrapConstants.java.
  // Also see OPT_ConvertToLowLevelIR.java which generates most of these TRAP_IFs.
  protected final void TRAP_IF(OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    OPT_RegisterOperand v1 = (OPT_RegisterOperand)TrapIf.getClearVal1(s);
    OPT_RegisterOperand v2 = (OPT_RegisterOperand)TrapIf.getClearVal2(s);
    OPT_ConditionOperand cond = TrapIf.getClearCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getClearTCode(s);
    
    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
        if (cond.isLOWER_EQUAL()) {
          EMIT(MIR_Trap.mutate(s, PPC_TW, gRes, 
                               new OPT_PowerPCTrapOperand(cond),
                               v1, v2, tc));
        } else {
          throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
        }
      }
      break;
    default:
      throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
    }
  }
  

  // Take the generic LIR trap_if and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See VM_TrapConstants.java.
  // Also see OPT_ConvertToLowLevelIR.java which generates most of these TRAP_IFs.
  protected final void TRAP_IF_IMM(OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    OPT_RegisterOperand v1 =  (OPT_RegisterOperand)TrapIf.getClearVal1(s);
    OPT_IntConstantOperand v2 = (OPT_IntConstantOperand)TrapIf.getClearVal2(s);
    OPT_ConditionOperand cond = TrapIf.getClearCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getClearTCode(s);

    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
        if (cond.isLOWER_EQUAL()) {
          EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, 
                               new OPT_PowerPCTrapOperand(cond),
                               v1, v2, tc));
        } else if (cond.isHIGHER_EQUAL()) {
          // have flip the operands and use non-immediate so trap handler can recognize.
          OPT_RegisterOperand tmp = regpool.makeTempInt();
          IntConstant(tmp.register, v2.value);
          EMIT(MIR_Trap.mutate(s, PPC_TW, gRes,
                               new OPT_PowerPCTrapOperand(cond.flipOperands()),
                               tmp, v1, tc)); 
        } else {
          throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
        }
      }
      break;
    case VM_Runtime.TRAP_DIVIDE_BY_ZERO:
      {
        //-#if RVM_FOR_32_ADDR
        // A slightly ugly matter, but we need to deal with combining
        // the two pieces of a long register from a LONG_ZERO_CHECK.  
        // A little awkward, but probably the easiest workaround...
        if (v1.type.isLongType()) {
          OPT_RegisterOperand rr = regpool.makeTempInt();
          EMIT(MIR_Binary.create(PPC_OR, rr, v1, 
                                 I(regpool.getSecondReg(v1.register))));
          v1 = rr.copyD2U();
        }
        
        if (cond.isEQUAL() && v2.value == 0) {
          EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, 
                               new OPT_PowerPCTrapOperand(cond),
                               v1, v2, tc));
        } else {
          throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
        }
        //-#endif
        //-#if RVM_FOR_64_ADDR
        if (cond.isEQUAL() && v2.value == 0) {
          if (v1.type.isLongType()) {
            EMIT(MIR_Trap.mutate(s, PPC64_TDI, gRes,
                                 new OPT_PowerPCTrapOperand(cond),
                                 v1, v2, tc));
          } else {
            EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes,
                                 new OPT_PowerPCTrapOperand(cond),
                                 v1, v2, tc));
          }
        } else {
          throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
        }
        //-#endif
      }
      break;

    default:
      throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
    }
  }



  // Take the generic LIR trap and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See VM_TrapConstants.java.
  protected final void TRAP(OPT_Instruction s) {
    OPT_RegisterOperand gRes = Trap.getClearGuardResult(s);
    OPT_TrapCodeOperand tc = Trap.getClearTCode(s);
    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_NULL_POINTER:
      {
        VM_Method target = VM_Entrypoints.raiseNullPointerException;
        mutateTrapToCall(s, target);
      }
      break;
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
        VM_Method target = VM_Entrypoints.raiseArrayBoundsException;
        mutateTrapToCall(s, target);
      }
      break;
    case VM_Runtime.TRAP_DIVIDE_BY_ZERO:
      {
        VM_Method target = VM_Entrypoints.raiseArithmeticException;
        mutateTrapToCall(s, target);
      }
      break;
    case VM_Runtime.TRAP_CHECKCAST:
      {
        EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, 
                             OPT_PowerPCTrapOperand.ALWAYS(),
                             R(12), IC(VM_TrapConstants.CHECKCAST_TRAP & 0xffff), tc));
      }
      break;
    case VM_Runtime.TRAP_MUST_IMPLEMENT:
      {
        EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, 
                             OPT_PowerPCTrapOperand.ALWAYS(),
                             R(12), IC(VM_TrapConstants.MUST_IMPLEMENT_TRAP & 0xffff), tc));
      }
      break;
    case VM_Runtime.TRAP_STORE_CHECK:
      {
        EMIT(MIR_Trap.mutate(s, PPC_TWI, gRes, 
                             OPT_PowerPCTrapOperand.ALWAYS(),
                             R(12), IC(VM_TrapConstants.STORE_CHECK_TRAP & 0xffff), tc));
      }
      break;
    default:
      throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
    }
  }


  private final void mutateTrapToCall(OPT_Instruction s, 
                                      VM_Method target) {
    int offset = target.getOffset();
    OPT_RegisterOperand tmp = regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
    OPT_Register JTOC = regpool.getPhysicalRegisterSet().getJTOC();
    OPT_MethodOperand meth = OPT_MethodOperand.STATIC(target);
    meth.setIsNonReturningCall(true);
    if (SI16(offset)) {
      EMIT(MIR_Load.create(PPC_LAddr, tmp, A(JTOC), IC(offset)));
    } else {
      OPT_RegisterOperand tmp2 = regpool.makeTempInt();
      IntConstant(tmp2.register, offset);
      EMIT(MIR_Load.create(PPC_LAddrX, tmp, A(JTOC), tmp2));
    }
    EMIT(MIR_Move.create(PPC_MTSPR, A(CTR), tmp.copyD2U()));
    EMIT(MIR_Call.mutate0(s, PPC_BCTRL, null, null, meth));
  }

  /* special case handling OSR instructions */
  void OSR(OPT_BURS burs, OPT_Instruction s) {
//-#if RVM_WITH_OSR
    if (VM.VerifyAssertions) VM._assert(OsrPoint.conforms(s));

    // 1. how many params
    int numparam = OsrPoint.getNumberOfElements(s);
    int numlong = 0;
    //-#if RVM_FOR_32_ADDR
    for (int i = 0; i < numparam; i++) {
      if (OsrPoint.getElement(s, i).getType().isLongType()) {
        numlong++;
      }
    }
    //-#endif
    
    // 2. collect params
    OPT_Operand[] params = new OPT_Operand[numparam];
    for (int i = 0; i <numparam; i++) {
      params[i] = OsrPoint.getClearElement(s, i);
    }

    OPT_InlinedOsrTypeInfoOperand typeInfo = 
      OsrPoint.getClearInlinedTypeInfo(s);

    if (VM.VerifyAssertions) VM._assert(typeInfo != null);

    // 3: only makes second half register of long being used
    //    creates room for long types.
    burs.append(OsrPoint.mutate(s, YIELDPOINT_OSR, 
                                typeInfo,
                                numparam + numlong));

    // set the number of valid params in osr type info, used
    // in LinearScan
    typeInfo.validOps = numparam;

    int pidx = numparam;
    for (int i = 0; i < numparam; i++) {
      OPT_Operand param = params[i];
      OsrPoint.setElement(s, i, param);
      //-#if RVM_FOR_32_ADDR
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        // the second half is appended at the end
        // OPT_LinearScan will update the map.
        if (rparam.type.isLongType()) {
          OsrPoint.setElement(s, pidx++, 
                            L(burs.ir.regpool.getSecondReg(rparam.register)));
        }
      }
      //-#endif
    }

    if (VM.VerifyAssertions) VM._assert(pidx == (numparam+numlong));
//-#endif
  }
}
