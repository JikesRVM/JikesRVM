/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Contains architecture-specific helper functions for BURS.
 * @author Dave Grove
 * @author Mauricio J. Serrano
 */
abstract class OPT_BURS_Helpers extends OPT_PhysicalRegisterTools
  implements OPT_Operators, OPT_PhysicalRegisterConstants {

  // Generic helper functions.
  // Defined here to allow us to use them in the arch-specific
  // helper functions which are the bulk of this file.
  /**
   * @return the given operand as a register
   */
  static final OPT_RegisterOperand R (OPT_Operand op) {
    return  (OPT_RegisterOperand)op;
  }

  /**
   * @return the given operand as an integer constant
   */
  static final OPT_IntConstantOperand I (OPT_Operand op) {
    return  (OPT_IntConstantOperand)op;
  }

  /**
   * @return the given operand as a long constant
   */
  static final OPT_LongConstantOperand L (OPT_Operand op) {
    return  (OPT_LongConstantOperand)op;
  }

  /**
   * @return the integer value of the given operand
   */
  static final int IV (OPT_Operand op) {
    return  I(op).value;
  }

  // 
  // Begin PowerPC specific helper functions.
  // 
  /**
   * returns true if an unsigned integer in 16 bits
   */
  final boolean UI16 (OPT_Operand a) {
    return  (IV(a) & 0xffff0000) == 0;
  }

  /**
   * returns true if an unsigned integer in 15 bits
   */
  final boolean UI15 (OPT_Operand a) {
    return  (IV(a) & 0xffff8000) == 0;
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  final boolean SI16 (OPT_Operand a) {
    return  SI16(IV(a));
  }

  /**
   * returns true if a signed integer in 16 bits
   */
  final boolean SI16 (int value) {
    return  (value <= 32767) && (value >= -32768);
  }

  /**
   * returns true if lower 16-bits are zero
   */
  final boolean U16 (OPT_Operand a) {
    return  (IV(a) & 0xffff) == 0;
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  final boolean MASK (OPT_Operand a) {
    return  MASK(IV(a));
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  final boolean MASK (int value) {
    if (value < 0)
      value = ~value;
    return  POSITIVE_MASK(value);
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  final boolean POSITIVE_MASK (OPT_Operand a) {
    return  POSITIVE_MASK(IV(a));
  }

  /**
   * returns true if the constant fits the mask of PowerPC's RLWINM
   */
  final boolean POSITIVE_MASK (int value) {
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

  final boolean MASK_AND_OR (OPT_Operand and, OPT_Operand or) {
    int value1 = IV(and);
    int value2 = IV(or);
    return  ((~value1 & value2) == value2) && MASK(value1);
  }

  /**
   * Integer Shift Right Immediate
   */
  final OPT_IntConstantOperand SRI (OPT_Operand o, int amount) {
    return  I(IV(o) >>> amount);
  }

  /**
   * Integer And Immediate
   */
  final OPT_IntConstantOperand ANDI (OPT_Operand o, int mask) {
    return  I(IV(o) & mask);
  }

  /**
   * Calculate Lower 16 Bits
   */
  final OPT_IntConstantOperand CAL16 (OPT_Operand o) {
    return  I(OPT_Bits.PPCMaskLower16(IV(o)));
  }

  /**
   * Calculate Upper 16 Bits
   */
  final OPT_IntConstantOperand CAU16 (OPT_Operand o) {
    return  I(OPT_Bits.PPCMaskUpper16(IV(o)));
  }

  /**
   * Mask Begin
   */
  final OPT_IntConstantOperand MB (OPT_Operand o) {
    return  I(MaskBegin(IV(o)));
  }

  final int MaskBegin (int integer) {
    int value;
    for (value = 0; integer >= 0; integer = integer << 1, value++);
    return  value;
  }

  /**
   * Mask End
   */
  final OPT_IntConstantOperand ME (OPT_Operand o) {
    return  I(MaskEnd(IV(o)));
  }

  final int MaskEnd (int integer) {
    int value;
    for (value = 31; (integer & 0x1) == 0; integer = integer >>> 1, value--);
    return  value;
  }
  
  // access functions
  OPT_Register getXER () {
    return  getIR().regpool.getPhysicalRegisterSet().getXER();
  }

  OPT_Register getLR () {
    return  getIR().regpool.getPhysicalRegisterSet().getLR();
  }

  OPT_Register getCTR () {
    return  getIR().regpool.getPhysicalRegisterSet().getCTR();
  }

  OPT_Register getTU () {
    return  getIR().regpool.getPhysicalRegisterSet().getTU();
  }

  OPT_Register getTL () {
    return  getIR().regpool.getPhysicalRegisterSet().getTL();
  }

  OPT_Register getCR () {
    return  getIR().regpool.getPhysicalRegisterSet().getCR();
  }

  /* RVM registers */
  OPT_Register getJTOC () {
    return  getIR().regpool.getPhysicalRegisterSet().getJTOC();
  }

  /**
   * Emit code to load a float value from the JTOC.
   * @param burs
   * @param operator
   * @param RT
   * @param field
   */
  private void emitLFtoc(OPT_BURS burs, OPT_Operator operator, 
			 OPT_Register RT, VM_Field field) {
    OPT_Register JTOC = burs.ir.regpool.getPhysicalRegisterSet().getJTOC();
    int offset = field.getOffset();
    int valueHigh = OPT_Bits.PPCMaskUpper16(offset);
    OPT_Instruction s;
    if (valueHigh == 0) {
      s = OPT_RVMIRTools.nonPEIGC(MIR_Load.create(operator, 
						  D(RT), R(JTOC), 
						  I(offset)));
      burs.append(s);
    } else {
      OPT_Register reg = burs.ir.regpool.getInteger();
      int valueLow = OPT_Bits.PPCMaskLower16(offset);
      burs.append(MIR_Binary.create(PPC_ADDIS, R(reg), R(JTOC), I(valueHigh)));
      s = OPT_RVMIRTools.nonPEIGC(MIR_Load.create(operator, D(RT), 
						  R(reg), I(valueLow)));
      burs.append(s);
    }
  }

  /**
   * Emit code to load an Integer Constant.
   * reg must be != 0
   */
  void IntConstant(OPT_BURS burs, OPT_Register reg, int value) {
    int lo = OPT_Bits.PPCMaskLower16(value);
    int hi = OPT_Bits.PPCMaskUpper16(value);
    if (hi != 0) {
      burs.append(MIR_Unary.create(PPC_LDIS, R(reg), I(hi)));
      if (lo != 0)
        burs.append(MIR_Binary.create(PPC_ADDI, R(reg), R(reg), I(lo)));
    } else {
      burs.append(MIR_Unary.create(PPC_LDI, R(reg), I(lo)));
    }
  }

  /**
   * Emit code to get a caught exception object into a register
   */
  void GET_EXCEPTION_OBJECT(OPT_BURS burs, OPT_Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_LocationOperand loc = new OPT_LocationOperand(-offset);
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Load.mutate(s, PPC_LWZ, Nullary.getClearResult(s), 
                                 R(FP), I(offset), loc, TG())));
  }

  /**
   * Emit code to move a value in a register to the stack location
   * where a caught exception object is expected to be.
   */
  void SET_EXCEPTION_OBJECT(OPT_BURS burs, OPT_Instruction s) {
    burs.ir.stackManager.forceFrameAllocation();
    int offset = burs.ir.stackManager.allocateSpaceForCaughtException();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_LocationOperand loc = new OPT_LocationOperand(-offset);
    OPT_RegisterOperand obj = (OPT_RegisterOperand)CacheOp.getRef(s);
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Store.mutate(s, PPC_STW, obj, 
							 R(FP), I(offset), 
							 loc, TG())));
  }

  /**
   * Emit code to move 32 bits from FPRs to GPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  void FPR2GPR_32(OPT_BURS burs, OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STFS, val, 
							 R(FP), I(offset), 
							 null, TG())));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Load.mutate(s, PPC_LWZ, Unary.getClearResult(s), 
				 R(FP), I(offset), null, TG())));
  }

  /**
   * Emit code to move 32 bits from GPRs to FPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  void GPR2FPR_32(OPT_BURS burs, OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STW, val, 
							 R(FP), I(offset), 
							 null, TG())));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Load.mutate(s, PPC_LFS, Unary.getClearResult(s), R(FP), 
                                 I(offset), null, TG())));
  }

  /**
   * Emit code to move 64 bits from FPRs to GPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  void FPR2GPR_64(OPT_BURS burs, OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand val = (OPT_RegisterOperand)Unary.getClearVal(s);
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STFD, val, 
							 R(FP), I(offset), 
							 null, TG())));
    OPT_RegisterOperand i1 = Unary.getClearResult(s);
    OPT_RegisterOperand i2 = R(burs.ir.regpool.getSecondReg(i1.register));
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Load.create(PPC_LWZ, i1, 
							R(FP), I(offset), 
							null, TG())));
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Load.mutate(s, PPC_LWZ, i2, 
							R(FP), I(offset+4),
							null, TG())));
  }

  /**
   * Emit code to move 64 bits from GPRs to FPRs
   * Note: intentionally use 'null' location to prevent DepGraph
   * from assuming that load/store not aliased. We're stepping outside
   * the Java type system here!
   */
  void GPR2FPR_64(OPT_BURS burs, OPT_Instruction s) {
    int offset = burs.ir.stackManager.allocateSpaceForConversion();
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand i1 = (OPT_RegisterOperand)Unary.getClearVal(s);
    OPT_RegisterOperand i2 = R(burs.ir.regpool.getSecondReg(i1.register));
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STW, i1, 
							 R(FP), I(offset), 
							 null, TG())));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Store.create(PPC_STW, i2, R(FP), I(offset+4), null, 
                                  TG())));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Load.mutate(s, PPC_LFD, Unary.getClearResult(s), R(FP), 
                                 I(offset), null, TG())));
  }

  /**
   * Expand a prologue by expanding out longs into pairs of ints
   */
  void PROLOGUE(OPT_BURS burs, OPT_Instruction s) {
    int numFormals = Prologue.getNumberOfFormals(s);
    int numLongs = 0;
    for (int i=0; i<numFormals; i++) {
      if (Prologue.getFormal(s, i).type == VM_Type.LongType) numLongs ++;
    }
    if (numLongs != 0) {
      OPT_Instruction s2 = Prologue.create(IR_PROLOGUE, numFormals+numLongs);
      for (int sidx=0, s2idx=0; sidx<numFormals; sidx++) {
	OPT_RegisterOperand sForm = Prologue.getClearFormal(s, sidx);
	Prologue.setFormal(s2, s2idx++, sForm);
	if (sForm.type == VM_Type.LongType) {
	  Prologue.setFormal(s2, s2idx++, 
                             R(burs.ir.regpool.getSecondReg(sForm.register)));
	}
      }									     
      burs.append(s2);
    } else {
      burs.append(s);
    }
  }


  /**
   * Expand a call instruction.
   */
  void CALL(OPT_BURS burs, OPT_Instruction s) {
    OPT_Operand target = Call.getClearAddress(s);
    OPT_MethodOperand meth = Call.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = Call.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (Call.getParam(s, pNum).getType() == VM_Type.LongType) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be
    OPT_RegisterOperand result = Call.getClearResult(s);
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type == VM_Type.LongType) {
      result2 = R(burs.ir.regpool.getSecondReg(result.register));
    }

    // Step 3: Figure out what the operator is going to be
    OPT_Operator callOp;
    if (target instanceof OPT_RegisterOperand) {
      // indirect call through target (contains code addr)
      OPT_Register ctr = burs.ir.regpool.getPhysicalRegisterSet().getCTR();
      burs.append(MIR_Move.create(PPC_MTSPR, R(ctr), 
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
    burs.append(MIR_Call.mutate(s, callOp, result, result2, 
				(OPT_BranchOperand)target, meth, 
				numParams + longParams));
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type == VM_Type.LongType) {
          MIR_Call.setParam(s, mirCallIdx++, 
			    L(burs.ir.regpool.getSecondReg(rparam.register)));
        }
      }
    }
  }

  /**
   * Expand a syscall instruction.
   */
  void SYSCALL(OPT_BURS burs, OPT_Instruction s) {
    burs.ir.setHasSysCall(true);
    OPT_Operand target = CallSpecial.getClearAddress(s);
    OPT_MethodOperand meth = (OPT_MethodOperand)CallSpecial.getClearMethod(s);

    // Step 1: Find out how many parameters we're going to have.
    int numParams = CallSpecial.getNumberOfParams(s);
    int longParams = 0;
    for (int pNum = 0; pNum < numParams; pNum++) {
      if (CallSpecial.getParam(s, pNum).getType() == VM_Type.LongType) {
        longParams++;
      }
    }

    // Step 2: Figure out what the result and result2 values will be
    OPT_RegisterOperand result = CallSpecial.getClearResult(s);
    OPT_RegisterOperand result2 = null;
    if (result != null && result.type == VM_Type.LongType) {
      result2 = R(burs.ir.regpool.getSecondReg(result.register));
    }

    // Step 3: Figure out what the operator is going to be
    OPT_Operator callOp;
    if (target instanceof OPT_RegisterOperand) {
      // indirect call through target (contains code addr)
      OPT_Register ctr = burs.ir.regpool.getPhysicalRegisterSet().getCTR();
      burs.append(MIR_Move.create(PPC_MTSPR, R(ctr), 
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
    burs.append(MIR_Call.mutate(s, callOp, result, result2, 
				(OPT_BranchOperand)target, meth, 
				numParams + longParams));
    for (int paramIdx = 0, mirCallIdx = 0; paramIdx < numParams;) {
      OPT_Operand param = params[paramIdx++];
      MIR_Call.setParam(s, mirCallIdx++, param);
      if (param instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand rparam = (OPT_RegisterOperand)param;
        if (rparam.type == VM_Type.LongType) {
          MIR_Call.setParam(s, mirCallIdx++, 
			    L(burs.ir.regpool.getSecondReg(rparam.register)));
        }
      }
    }
  }

  /**
   * Emit code for RETURN.
   * @param burs
   * @param s
   * @param value
   */
  void RETURN(OPT_BURS burs, OPT_Instruction s, OPT_Operand value) {
    if (value != null) {
      OPT_RegisterOperand rop = (OPT_RegisterOperand)value;
      if (value.getType() == VM_Type.LongType) {
	OPT_Register pair = burs.ir.regpool.getSecondReg(rop.register);
        burs.append(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), R(pair)));
      } else {
        burs.append(MIR_Return.mutate(s, PPC_BLR, rop.copyU2U(), null));
      }
    } else {
      burs.append(MIR_Return.mutate(s, PPC_BLR, null, null));
    }
  }


  void SHL_USHR(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, 
		OPT_IntConstantOperand shift1, 
		OPT_IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x < y) {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
					   I((32 - (y - x)) & 0x1f), 
					   I(y), I(31))); 
    } else {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
					   I(x - y), I(y), I(31 - (x - y))));
    }
  }

  void USHR_SHL(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, 
		OPT_IntConstantOperand shift1, 
		OPT_IntConstantOperand shift2) {
    int x = shift1.value;
    int y = shift2.value;
    if (x < y) {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
					   I(y - x), I(0), I(31 - y))); 
    } else {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, def, left, 
					   I((32 - (x - y)) & 0x1f), 
					   I(x - y), I(31 - y)));
    }
  }

  void USHR_AND(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand Def, 
		OPT_RegisterOperand left, 
		OPT_IntConstantOperand Mask, 
		OPT_IntConstantOperand Shift) {
    int shift = Shift.value;
    int mask = Mask.value;
    int MB = MaskBegin(mask);
    int ME = MaskEnd(mask);
    if (shift > ME) {           // result should be 0
      burs.append(MIR_Unary.create(PPC_LDI, Def, I(0)));
      return;
    } else if (shift > MB) {
      MB = shift;
    }
    burs.append(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, 
					 I((32 - shift) & 0x1f), 
					 I(MB), I(ME)));
  }

  void AND_USHR(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand Def, 
		OPT_RegisterOperand left, 
		OPT_IntConstantOperand Mask, 
		OPT_IntConstantOperand Shift) {
    int shift = Shift.value;
    int mask = Mask.value;
    int MB = MaskBegin(mask);
    int ME = MaskEnd(mask);
    if ((MB + shift) >= 32) {                   // result should be 0
      burs.append(MIR_Unary.create(PPC_LDI, Def, I(0)));
      return;
    }
    MB += shift;
    ME += shift;
    if (ME >= 32) {
      ME = 31;
    }
    burs.append(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, 
					 I((32 - shift) & 0x1f), 
					 I(MB), I(ME)));
  }

  void AND_MASK(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand Def, 
		OPT_RegisterOperand left, 
		OPT_IntConstantOperand Mask) {
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, I(0), 
					   I((ME + 1) & 0x1f), I(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, Def, left, I(0), 
					   I(MB), I(ME)));
    }
  }

  void BOOLEAN_CMP_IMM(OPT_BURS burs, OPT_Instruction s, 
		       OPT_RegisterOperand def, 
		       OPT_RegisterOperand one, 
		       OPT_IntConstantOperand two) {
    OPT_ConditionOperand cmp = BooleanCmp.getCond(s);
    if (!boolean_cmp_imm(burs, def, one, two.value, cmp))
      burs.append(s);
  }

  /**
   * emit basic code to handle an INT_IFCMP when no folding
   * of the compare into some other computation is possible.
   */
  void CMP(OPT_BURS burs, OPT_Instruction s,
	   OPT_RegisterOperand val1, OPT_Operand val2,
	   OPT_ConditionOperand cond,
	   boolean immediate) {
    OPT_RegisterOperand cr = burs.ir.regpool.makeTempCondition();
    OPT_Operator op;
    if (immediate) {
      op = cond.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op = cond.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    burs.append(MIR_Binary.create(op, cr, val1, val2));
    burs.append(MIR_CondBranch.mutate(s, PPC_BCOND, cr.copyD2U(),
				      new OPT_PowerPCConditionOperand(cond),
				      IfCmp.getTarget(s),
				      IfCmp.getBranchProfile(s)));
  }

  /**
   * emit basic code to handle an INT_IFCMP2 when no folding
   * of the compare into some other computation is possible.
   */
  void CMP2(OPT_BURS burs, OPT_Instruction s,
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
      OPT_RegisterOperand cr = burs.ir.regpool.makeTempCondition();
      burs.append(MIR_Binary.create(op1, cr, val1, val2));
      burs.append(MIR_CondBranch2.mutate(s, PPC_BCOND2, cr.copyD2U(),
					 new OPT_PowerPCConditionOperand(cond1),
					 IfCmp2.getTarget1(s),
					 IfCmp2.getBranchProfile1(s),
					 new OPT_PowerPCConditionOperand(cond2),
					 IfCmp2.getTarget2(s),
					 IfCmp2.getBranchProfile2(s)));
    } else {
      OPT_RegisterOperand cr1 = burs.ir.regpool.makeTempCondition();
      OPT_RegisterOperand cr2 = burs.ir.regpool.makeTempCondition();
      burs.append(MIR_Binary.create(op1, cr1, val1, val2));
      burs.append(MIR_Binary.create(op2, cr2, val1, val2));
      burs.append(MIR_CondBranch.create(PPC_BCOND, cr1.copyD2U(),
					new OPT_PowerPCConditionOperand(cond1),
					IfCmp2.getTarget1(s),
					IfCmp2.getBranchProfile1(s)));
      burs.append(MIR_CondBranch.mutate(s, PPC_BCOND, cr2.copyD2U(),
					new OPT_PowerPCConditionOperand(cond2),
					IfCmp2.getTarget2(s),
					IfCmp2.getBranchProfile2(s)));
    }
  }


  /**
   * Uses the record capability to avoid compare 
   */
  void CMP_ZERO(OPT_BURS burs, OPT_Instruction s, OPT_Operator op, 
		OPT_RegisterOperand def, OPT_Operand left,
		OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM.assert(!cond.isUNSIGNED());
    if (!def.register.spansBasicBlock()) {
      def.register = burs.ir.regpool.getPhysicalRegisterSet().getTemp();
    }
    burs.append(MIR_Unary.create(op, def, left));
    burs.append(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
				      new OPT_PowerPCConditionOperand(cond), 
				      IfCmp.getTarget(s),
				      IfCmp.getBranchProfile(s)));
  }

  void CMP_ZERO(OPT_BURS burs, OPT_Instruction s, OPT_Operator op, 
		OPT_RegisterOperand def, OPT_RegisterOperand left, 
		OPT_Operand right, OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM.assert(!cond.isUNSIGNED());
    if (!def.register.spansBasicBlock()) {
      def.register = burs.ir.regpool.getPhysicalRegisterSet().getTemp();
    }
    burs.append(MIR_Binary.create(op, def, left, right));
    burs.append(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
				      new OPT_PowerPCConditionOperand(cond), 
				      IfCmp.getTarget(s),
				      IfCmp.getBranchProfile(s)));
  }

  void CMP_ZERO_AND_MASK(OPT_BURS burs, OPT_Instruction s, 
			 OPT_RegisterOperand def, 
			 OPT_RegisterOperand left, 
			 OPT_IntConstantOperand Mask,
			 OPT_ConditionOperand cond) {
    if (VM.VerifyAssertions) VM.assert(!cond.isUNSIGNED());
    int mask = Mask.value;
    if (mask < 0) {
      mask = ~mask;
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      burs.append(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, I(0), 
					   I((ME + 1) & 0x1f), I(MB - 1)));
    } else {
      int MB = MaskBegin(mask);
      int ME = MaskEnd(mask);
      burs.append(MIR_RotateAndMask.create(PPC_RLWINMr, def, left, I(0), 
					   I(MB), I(ME)));
    }
    burs.append(MIR_CondBranch.mutate(s, PPC_BCOND, CR(0), 
				      new OPT_PowerPCConditionOperand(cond), 
				      IfCmp.getTarget(s),
				      IfCmp.getBranchProfile(s)));
  }

  // boolean compare
  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 199 
   */
  boolean boolean_cmp_imm(OPT_BURS burs, OPT_RegisterOperand def, 
			  OPT_RegisterOperand one, 
			  int value, OPT_ConditionOperand cmp) {
    OPT_Register t1, t = burs.ir.regpool.getInteger();
    OPT_Register zero = burs.ir.regpool.getPhysicalRegisterSet().getTemp();
    boolean convert = true;
    switch (cmp.value) {
    case OPT_ConditionOperand.EQUAL:
      if (value == 0) {
	burs.append(MIR_Unary.create(PPC_CNTLZW, R(t), one)); 
      } else {
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(t), one, I(value)));
	burs.append(MIR_Unary.create(PPC_CNTLZW, R(t), R(t)));
      }
      burs.append(MIR_Binary.create(PPC_SRWI, def, R(t), I(5)));
      break;
    case OPT_ConditionOperand.NOT_EQUAL:
      if (value == 0) {
	burs.append(MIR_Binary.create(PPC_ADDIC, R(t), one, I(-1)));
	burs.append(MIR_Binary.create(PPC_SUBFE, def, R(t), one.copyRO()));
      } else {
	t1 = burs.ir.regpool.getInteger();
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(t1), one, I(value)));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(t), R(t1), I(-1)));
	burs.append(MIR_Binary.create(PPC_SUBFE, def, R(t), R(t1)));
      }
      break;
    case OPT_ConditionOperand.LESS:
      if (value == 0) {
	burs.append(MIR_Binary.create(PPC_SRWI, def, one, I(31)));
      } else if (value > 0) {
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(zero), one, 
				      I(value - 1)));
	burs.append(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else if (value != 0xFFFF8000) {
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), 
				      I(value - 1)));
	burs.append(MIR_Unary.create(PPC_ADDME, def, R(t)));
      } else {                  // value = 0xFFFF8000
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Unary.create(PPC_LDIS, R(zero), I(1)));
	burs.append(MIR_Binary.create(PPC_SUBFC, R(zero), one.copyRO(), R(zero)));
	burs.append(MIR_Unary.create(PPC_ADDME, def, R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER:
      if (value == 0) {
	burs.append(MIR_Unary.create(PPC_NEG, R(t), one));
	burs.append(MIR_Binary.create(PPC_ANDC, R(t), R(t), one.copyRO()));
	burs.append(MIR_Binary.create(PPC_SRWI, def, R(t), I(31)));
      } else if (value >= 0) {
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), 
				      I(-value - 1)));
	burs.append(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else {
	t1 = burs.ir.regpool.getInteger();
	burs.append(MIR_Unary.create(PPC_LDI, R(t1), I(1)));
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), 
				      I(-value - 1)));
	burs.append(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
      }
      break;
    case OPT_ConditionOperand.LESS_EQUAL:
      if (value == 0) {
	burs.append(MIR_Binary.create(PPC_ADDI, R(t), one, I(-1)));
	burs.append(MIR_Binary.create(PPC_OR, R(t), R(t), one.copyRO()));
	burs.append(MIR_Binary.create(PPC_SRWI, def, R(t), I(31)));
      } else if (value >= 0) {
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), I(value)));
	burs.append(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else {
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFIC, R(zero), one.copyRO(), I(value)));
	burs.append(MIR_Unary.create(PPC_ADDME, def, R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER_EQUAL:
      if (value == 0) {
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_XORI, def, R(t), I(1)));
      } else if (value >= 0) {
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), I(-value)));
	burs.append(MIR_Unary.create(PPC_ADDZE, def, R(t)));
      } else if (value != 0xFFFF8000) {
	t1 = burs.ir.regpool.getInteger();
	burs.append(MIR_Unary.create(PPC_LDI, R(t1), I(1)));
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(zero), one.copyRO(), I(-value)));
	burs.append(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
      } else {
	t1 = burs.ir.regpool.getInteger();
	burs.append(MIR_Unary.create(PPC_LDI, R(t1), I(1)));
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t), one, I(31)));
	burs.append(MIR_Unary.create(PPC_LDIS, R(zero), I(1)));
	burs.append(MIR_Binary.create(PPC_ADDC, R(zero), one.copyRO(), R(zero)));
	burs.append(MIR_Binary.create(PPC_ADDE, def, R(t), R(t1)));
      }
      break;
    case OPT_ConditionOperand.HIGHER:
      return false; // todo
    case OPT_ConditionOperand.LOWER:
      return false; // todo
    case OPT_ConditionOperand.HIGHER_EQUAL:
      return false; // todo
    case OPT_ConditionOperand.LOWER_EQUAL:
      burs.append(MIR_Unary.create(PPC_LDI, R(t), I(-1)));
      burs.append(MIR_Binary.create(PPC_SUBFIC, R(zero), one, I(value)));
      burs.append(MIR_Unary.create(PPC_SUBFZE, def, R(t)));
      break;

    default:
      return false;
    }
    return true;
  }

  void BOOLEAN_CMP(OPT_BURS burs, OPT_Instruction s, 
		   OPT_RegisterOperand def, 
		   OPT_RegisterOperand one,
		   OPT_RegisterOperand two) {
    OPT_ConditionOperand cmp = BooleanCmp.getCond(s);
    if (!boolean_cmp(burs, def, one, two, cmp, s))
      burs.append(s);
  }

  boolean boolean_cmp (OPT_BURS burs, OPT_RegisterOperand def, 
		       OPT_RegisterOperand one, 
		       OPT_RegisterOperand two, OPT_ConditionOperand cmp,
		       OPT_Instruction inst) {
    OPT_Register t1, zero, t = burs.ir.regpool.getInteger();
    switch (cmp.value) {
    case OPT_ConditionOperand.EQUAL:
      {
	burs.append(MIR_Binary.create(PPC_SUBF, R(t), one, two));
	burs.append(MIR_Unary.create(PPC_CNTLZW, R(t), R(t)));
	burs.append(MIR_Binary.create(PPC_SRWI, def, R(t), I(5)));
      }
      break;
    case OPT_ConditionOperand.NOT_EQUAL:
      {
	t1 = burs.ir.regpool.getInteger();
	burs.append(MIR_Binary.create(PPC_SUBF, R(t), one, two));
	burs.append(MIR_Binary.create(PPC_ADDIC, R(t1), R(t), I(-1)));
	burs.append(MIR_Binary.create(PPC_SUBFE, def, R(t1), R(t)));
      }
      break;
    case OPT_ConditionOperand.LESS_EQUAL:
      {
	t1 = burs.ir.regpool.getInteger();
	zero = burs.ir.regpool.getPhysicalRegisterSet().getTemp();
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t1), two, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFC, R(zero), one.copyRO(), two.copyRO()));
	burs.append(MIR_Binary.create(PPC_ADDE, def, R(t1), R(t)));
      }
      break;
    case OPT_ConditionOperand.GREATER_EQUAL:
      {
	t1 = burs.ir.regpool.getInteger();
	zero = burs.ir.regpool.getPhysicalRegisterSet().getTemp();
	burs.append(MIR_Binary.create(PPC_SRWI, R(t), two, I(31)));
	burs.append(MIR_Binary.create(PPC_SRAWI, R(t1), one, I(31)));
	burs.append(MIR_Binary.create(PPC_SUBFC, R(zero), two.copyRO(), one.copyRO()));
	burs.append(MIR_Binary.create(PPC_ADDE, def, R(t1), R(t)));
      }
      break;
    default:
      return false;
    }
    return true;
  }

  void BYTE_LOAD(OPT_BURS burs, OPT_Instruction s, 
		 OPT_Operator opcode, 
		 OPT_RegisterOperand def, 
		 OPT_RegisterOperand left, 
		 OPT_Operand right, 
		 OPT_LocationOperand loc, 
		 OPT_Operand guard) {
    OPT_RegisterOperand reg1 = burs.ir.regpool.makeTempInt();
    burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Load.mutate(s, opcode, 
							reg1, left, right, 
							loc, guard)));
    burs.append(MIR_Unary.create(PPC_EXTSB, def, reg1.copyD2U()));
  }

  private int PowerOf2 (int v) {
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

  void INT_DIV_IMM(OPT_BURS burs, OPT_Instruction s, 
		   OPT_RegisterOperand def, 
		   OPT_RegisterOperand left, 
		   OPT_RegisterOperand c, 
		   OPT_IntConstantOperand right) {
    int power = PowerOf2(right.value);
    if (power != -1) {
      burs.append(MIR_Binary.create(PPC_SRAWI, c, left, I(power)));
      burs.append(MIR_Unary.create(PPC_ADDZE, def, c.copyD2U()));
    } else {
      IntConstant(burs, c.register, right.value);
      burs.append(MIR_Binary.mutate(s, PPC_DIVW, def, left, c));
    }
  }

  void INT_REM(OPT_BURS burs, OPT_Instruction s, 
	       OPT_RegisterOperand def, 
	       OPT_RegisterOperand left, 
	       OPT_RegisterOperand right) {
    OPT_Register temp = burs.ir.regpool.getInteger();
    burs.append(MIR_Binary.mutate(s, PPC_DIVW, R(temp), left, right));
    burs.append(MIR_Binary.create(PPC_MULLW, R(temp), R(temp), 
				  right.copyU2U()));
    burs.append(MIR_Binary.create(PPC_SUBF, def, R(temp), left.copyU2U()));
  }

  void INT_REM_IMM(OPT_BURS burs, OPT_Instruction s, 
		   OPT_RegisterOperand def, 
		   OPT_RegisterOperand left, 
		   OPT_RegisterOperand c, 
		   OPT_IntConstantOperand right) {
    OPT_Register temp = burs.ir.regpool.getInteger();
    int power = PowerOf2(right.value);
    if (power != -1) {
      burs.append(MIR_Binary.mutate(s, PPC_SRAWI, R(temp), left, I(power)));
      burs.append(MIR_Unary.create(PPC_ADDZE, R(temp), R(temp)));
      burs.append(MIR_Binary.create(PPC_SLWI, R(temp), R(temp), I(power)));
      burs.append(MIR_Binary.create(PPC_SUBF, def, R(temp), left.copyU2U()));
    } else {
      IntConstant(burs, c.register, right.value);
      burs.append(MIR_Binary.mutate(s, PPC_DIVW, R(temp), left, c));
      burs.append(MIR_Binary.create(PPC_MULLW, R(temp), R(temp), c.copyU2U()));
      burs.append(MIR_Binary.create(PPC_SUBF, def, R(temp), left.copyU2U()));
    }
  }

  /**
   * Conversion
   */
  void INT_2LONG(OPT_BURS burs, OPT_Instruction s, 
		 OPT_RegisterOperand def, 
		 OPT_RegisterOperand left) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    burs.append(MIR_Move.mutate(s, PPC_MOVE, R(defLow), left));
    burs.append(MIR_Binary.create(PPC_SRAWI, R(defHigh), left.copyU2U(), I(31)));
  }

  /**
   * taken from: The PowerPC Compiler Writer's Guide, pp. 83 
   */
  void INT_2DOUBLE(OPT_BURS burs, OPT_Instruction s, 
		   OPT_RegisterOperand def, 
		   OPT_RegisterOperand left) {
    OPT_Register res = def.register;
    OPT_Register src = left.register;
    OPT_Register FP = burs.ir.regpool.getPhysicalRegisterSet().getFP();
    OPT_RegisterOperand temp = burs.ir.regpool.makeTempInt();
    int p = burs.ir.stackManager.allocateSpaceForConversion();
    burs.append(MIR_Unary.mutate(s, PPC_LDIS, temp, I(0x4330)));
    // TODO: valid location?
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Store.create(PPC_STW, R(temp.register), R(FP), I(p), 
                                  new OPT_TrueGuardOperand())));
    OPT_Register t1 = burs.ir.regpool.getInteger();
    burs.append(MIR_Binary.create(PPC_XORIS, R(t1), R(src), I(0x8000)));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Store.create(PPC_STW, R(t1), R(FP), I(p + 4), 
                                  new OPT_TrueGuardOperand())));
    burs.append(OPT_RVMIRTools.nonPEIGC
                (MIR_Load.create(PPC_LFD, D(res), R(FP), I(p))));
    OPT_Register tempF = burs.ir.regpool.getDouble();
    emitLFtoc(burs, PPC_LFD, tempF, VM_Entrypoints.I2DconstantField);
    burs.append(MIR_Binary.create(PPC_FSUB, D(res), D(res), D(tempF)));
  }

  // DOUBLE arithmetic:
  void DOUBLE_REM(OPT_BURS burs, OPT_Instruction s, 
		  OPT_RegisterOperand def, 
		  OPT_RegisterOperand left, 
		  OPT_RegisterOperand right) {
    // FO = b, F1 = a
    OPT_Register res = def.register;
    OPT_Register a = left.register;
    OPT_Register b = right.register;
    OPT_Register tempF = burs.ir.regpool.getDouble();
    OPT_Register temp1 = burs.ir.regpool.getDouble();
    OPT_Register temp2 = burs.ir.regpool.getDouble();
    burs.append(MIR_Binary.create(PPC_FDIV, D(temp1), D(a), D(b)));
    emitLFtoc(burs, PPC_LFS, tempF, VM_Entrypoints.halfFloatField);
    burs.append(MIR_Unary.create(PPC_FNEG, D(temp2), D(tempF)));
    burs.append(MIR_Ternary.create(PPC_FSEL, D(tempF), D(temp1), D(tempF), 
				   D(temp2)));
    burs.append(MIR_Binary.create(PPC_FSUB, D(temp1), D(temp1), D(tempF)));
    emitLFtoc(burs, PPC_LFD, tempF, VM_Entrypoints.IEEEmagicField);
    burs.append(MIR_Binary.create(PPC_FADD, D(temp1), D(temp1), D(tempF)));
    burs.append(MIR_Binary.create(PPC_FSUB, D(temp1), D(temp1), D(tempF)));
    burs.append(MIR_Ternary.create(PPC_FNMSUB, D(res), D(temp1), D(b), D(a)));    
  }

  // LONG arithmetic:
  void LONG_2INT(OPT_BURS burs, OPT_Instruction s, 
		 OPT_RegisterOperand def, 
		 OPT_RegisterOperand left) {
    OPT_Register srcHigh = left.register;
    OPT_Register srcLow = burs.ir.regpool.getSecondReg(srcHigh);
    burs.append(MIR_Move.mutate(s, PPC_MOVE, def, R(srcLow)));
  }

  void LONG_MOVE(OPT_BURS burs, OPT_Instruction s, 
		 OPT_RegisterOperand def, 
		 OPT_RegisterOperand left) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    burs.append(MIR_Move.create(PPC_MOVE, R(defReg), R(leftReg)));
    burs.append(MIR_Move.create(PPC_MOVE, 
				R(burs.ir.regpool.getSecondReg(defReg)), 
				R(burs.ir.regpool.getSecondReg(leftReg))));
  }

  void LONG_CONSTANT(OPT_BURS burs, OPT_Instruction s, 
		     OPT_RegisterOperand def, 
		     OPT_LongConstantOperand left) {
    long value = left.value;
    int valueHigh = (int)(value >> 32);
    int valueLow = (int)(value & 0xffffffff);
    OPT_Register register = def.register;
    IntConstant(burs, register, valueHigh);
    IntConstant(burs, burs.ir.regpool.getSecondReg(register), valueLow);
  }

  void LONG_ADD(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, OPT_RegisterOperand right) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    OPT_Register rightReg = right.register;
    burs.append(MIR_Binary.create(PPC_ADDC, 
				  R(burs.ir.regpool.getSecondReg(defReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg)), 
				  R(burs.ir.regpool.getSecondReg(rightReg))));
    burs.append(MIR_Binary.create(PPC_ADDE, R(defReg), R(leftReg), 
				  R(rightReg)));
  }

  /* Notice: switching operands! */
  void LONG_SUB(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, 
		OPT_RegisterOperand right) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = right.register;
    OPT_Register rightReg = left.register;
    burs.append(MIR_Binary.create(PPC_SUBFC, 
				  R(burs.ir.regpool.getSecondReg(defReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg)), 
				  R(burs.ir.regpool.getSecondReg(rightReg))));
    burs.append(MIR_Binary.create(PPC_SUBFE, R(defReg), 
                                  R(leftReg), R(rightReg)));
  }

  void LONG_NEG(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    burs.append(MIR_Binary.create(PPC_SUBFIC, 
				  R(burs.ir.regpool.getSecondReg(defReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg)), 
				  I(0)));
    burs.append(MIR_Unary.create(PPC_SUBFZE, R(defReg), R(leftReg)));
  }

  void LONG_NOT(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    burs.append(MIR_Binary.create(PPC_NOR, R(defReg), R(leftReg), R(leftReg)));
    burs.append(MIR_Binary.create(PPC_NOR, 
				  R(burs.ir.regpool.getSecondReg(defReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg))));
  }

  void LONG_LOG(OPT_BURS burs, OPT_Instruction s, 
		OPT_Operator operator, 
		OPT_RegisterOperand def, OPT_RegisterOperand left, 
		OPT_RegisterOperand right) {
    OPT_Register defReg = def.register;
    OPT_Register leftReg = left.register;
    OPT_Register rightReg = right.register;
    burs.append(MIR_Binary.create(operator, R(defReg), 
				  R(leftReg), R(rightReg)));
    burs.append(MIR_Binary.create(operator, 
				  R(burs.ir.regpool.getSecondReg(defReg)), 
				  R(burs.ir.regpool.getSecondReg(leftReg)), 
				  R(burs.ir.regpool.getSecondReg(rightReg))));
  }

  /**
   * taken from "PowerPC Microprocessor Family, 
   * The Programming Environment for 32-bit Microprocessors 
   * */
  void LONG_SHL(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, OPT_RegisterOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = burs.ir.regpool.getSecondReg(leftHigh);
    OPT_Register shift = right.register;
    OPT_RegisterPool regpool = burs.ir.regpool;
    OPT_Register t0 = regpool.getInteger();
    OPT_Register t31 = regpool.getInteger();
    burs.append(MIR_Binary.create(PPC_SUBFIC, R(t31), R(shift), I(32)));
    burs.append(MIR_Binary.create(PPC_SLW, R(defHigh), R(leftHigh), R(shift)));
    burs.append(MIR_Binary.create(PPC_SRW, R(t0), R(leftLow), R(t31)));
    burs.append(MIR_Binary.create(PPC_OR, R(defHigh), R(defHigh), R(t0)));
    burs.append(MIR_Binary.create(PPC_ADDI, R(t31), R(shift), I(-32)));
    burs.append(MIR_Binary.create(PPC_SLW, R(t0), R(leftLow), R(t31)));
    burs.append(MIR_Binary.create(PPC_OR, R(defHigh), R(defHigh), R(t0)));
    burs.append(MIR_Binary.create(PPC_SLW, R(defLow), R(leftLow), R(shift)));
  }

  void LONG_SHL_IMM(OPT_BURS burs, OPT_Instruction s, 
		    OPT_RegisterOperand def, 
		    OPT_RegisterOperand left, 
		    OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = burs.ir.regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, R(defHigh), 
					   R(leftHigh), 
					   I(shift), I(0), I(31 - shift)));
      burs.append(MIR_RotateAndMask.create(PPC_RLWIMI, R(defHigh), R(defHigh), 
					   R(leftLow), I(shift), 
					   I(32 - shift), I(31)));
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, R(defLow), R(leftLow), 
					   I(shift), I(0), I(31 - shift)));
    } else if (shift == 32) {
      burs.append(MIR_Move.create(PPC_MOVE, R(defHigh), R(leftLow)));
      burs.append(MIR_Unary.create(PPC_LDI, R(defLow), I(0)));
    } else {
      shift = shift - 32;
      burs.append(MIR_Binary.create(PPC_SLWI, R(defHigh), R(leftLow), 
				    I(shift)));
      burs.append(MIR_Unary.create(PPC_LDI, R(defLow), I(0)));
    }
  }

  void LONG_USHR(OPT_BURS burs, OPT_Instruction s, 
		 OPT_RegisterOperand def, 
		 OPT_RegisterOperand left, OPT_RegisterOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = burs.ir.regpool.getSecondReg(leftHigh);
    OPT_Register shift = right.register;
    OPT_RegisterPool regpool = burs.ir.regpool;
    OPT_Register t0 = regpool.getInteger();
    OPT_Register t31 = regpool.getInteger();
    burs.append(MIR_Binary.create(PPC_SUBFIC, R(t31), R(shift), I(32)));
    burs.append(MIR_Binary.create(PPC_SRW, R(defLow), R(leftLow), R(shift)));
    burs.append(MIR_Binary.create(PPC_SLW, R(t0), R(leftHigh), R(t31)));
    burs.append(MIR_Binary.create(PPC_OR, R(defLow), R(defLow), R(t0)));
    burs.append(MIR_Binary.create(PPC_ADDI, R(t31), R(shift), I(-32)));
    burs.append(MIR_Binary.create(PPC_SRW, R(t0), R(leftHigh), R(t31)));
    burs.append(MIR_Binary.create(PPC_OR, R(defLow), R(defLow), R(t0)));
    burs.append(MIR_Binary.create(PPC_SRW, R(defHigh), R(leftHigh), R(shift)));
  }

  void LONG_USHR_IMM(OPT_BURS burs, OPT_Instruction s, 
		     OPT_RegisterOperand def, 
		     OPT_RegisterOperand left, 
		     OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = burs.ir.regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, R(defLow), R(leftLow), 
					   I(32 - shift), I(shift), I(31)));
      burs.append(MIR_RotateAndMask.create(PPC_RLWIMI, R(defLow), R(defLow), 
					   R(leftHigh), I(32 - shift), 
					   I(0), I(shift - 1)));
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, R(defHigh), 
					   R(leftHigh),
 					   I(32 - shift), I(shift), I(31)));
    } else if (shift == 32) {
      burs.append(MIR_Move.create(PPC_MOVE, R(defLow), R(leftHigh)));
      burs.append(MIR_Unary.create(PPC_LDI, R(defHigh), I(0)));
    } else {
      shift = shift - 32;
      burs.append(MIR_Binary.create(PPC_SRWI, R(defLow), R(leftHigh), 
				    I(shift)));
      burs.append(MIR_Unary.create(PPC_LDI, R(defHigh), I(0)));
    }
  }

  void LONG_SHR_IMM(OPT_BURS burs, OPT_Instruction s, 
		    OPT_RegisterOperand def, 
		    OPT_RegisterOperand left, 
		    OPT_IntConstantOperand right) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = burs.ir.regpool.getSecondReg(leftHigh);
    int shift = right.value & 0x3f;
    if (shift < 32) {
      burs.append(MIR_RotateAndMask.create(PPC_RLWINM, R(defLow), R(leftLow), 
					   I(32 - shift), I(shift), I(31)));
      burs.append(MIR_RotateAndMask.create(PPC_RLWIMI, R(defLow), R(defLow), 
					   R(leftHigh), I(32 - shift), I(0), 
					   I(shift - 1)));
      burs.append(MIR_Binary.create(PPC_SRAWI, R(defHigh), R(leftHigh), 
				    I(shift)));
    } else if (shift == 32) {
      burs.append(MIR_Move.create(PPC_MOVE, R(defLow), R(leftHigh)));
      burs.append(MIR_Binary.create(PPC_SRAWI, R(defHigh), R(leftHigh), 
				    I(31)));
    } else {
      shift = shift - 32;
      burs.append(MIR_Binary.create(PPC_SRAWI, R(defLow), R(leftHigh), 
				    I(shift)));
      burs.append(MIR_Binary.create(PPC_SRAWI, R(defHigh), R(leftHigh), 
				    I(31)));
    }
  }

  void LONG_MUL(OPT_BURS burs, OPT_Instruction s, 
		OPT_RegisterOperand def, 
		OPT_RegisterOperand left, OPT_RegisterOperand right) {
    OPT_Register dH = def.register;
    OPT_Register dL = burs.ir.regpool.getSecondReg(dH);
    OPT_Register lH = left.register;
    OPT_Register lL = burs.ir.regpool.getSecondReg(lH);
    OPT_Register rH = right.register;
    OPT_Register rL = burs.ir.regpool.getSecondReg(rH);
    OPT_RegisterPool regpool = burs.ir.regpool;
    OPT_Register tH = regpool.getInteger();
    OPT_Register t = regpool.getInteger();
    burs.append(MIR_Binary.create(PPC_MULHWU, R(tH), R(lL), R(rL)));
    burs.append(MIR_Binary.create(PPC_MULLW, R(t), R(lL), R(rH)));
    burs.append(MIR_Binary.create(PPC_ADD, R(tH), R(tH), R(t)));
    burs.append(MIR_Binary.create(PPC_MULLW, R(t), R(lH), R(rL)));
    burs.append(MIR_Binary.create(PPC_ADD, R(dH), R(tH), R(t)));
    burs.append(MIR_Binary.create(PPC_MULLW, R(dL), R(lL), R(rL)));
  }

  void LONG_LOAD_addi(OPT_BURS burs, OPT_Instruction s, 
		      OPT_RegisterOperand def, 
		      OPT_RegisterOperand left, 
		      OPT_IntConstantOperand right, 
		      OPT_LocationOperand loc, 
		      OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions) VM.assert(imm < (0x8000 - 4));
    OPT_Instruction inst = OPT_RVMIRTools.nonPEIGC (MIR_Load.create
						    (PPC_LWZ, 
						     R(defHigh), 
						     left, I(imm), loc, 
						     guard));
    inst.copyPosition(s);
    burs.append(inst);
    if (loc != null) {
      loc = (OPT_LocationOperand)loc.copy();
    }
    inst = OPT_RVMIRTools.nonPEIGC (MIR_Load.create(PPC_LWZ, 
						    R(defLow), 
						    left.copyU2U(), 
						    I(imm + 4), loc, 
						    guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void LONG_LOAD_addis(OPT_BURS burs, OPT_Instruction s, 
		       OPT_RegisterOperand def, 
		       OPT_RegisterOperand left, 
		       OPT_RegisterOperand right, 
		       OPT_IntConstantOperand Value, 
		       OPT_LocationOperand loc, OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    int value = Value.value;
    burs.append(MIR_Binary.create(PPC_ADDIS, right, left, 
				  I(OPT_Bits.PPCMaskUpper16(value))));
    OPT_Instruction inst = OPT_RVMIRTools.nonPEIGC(MIR_Load.create 
						   (PPC_LWZ, R(defHigh), 
						    right.copyD2U(), 
						    I(OPT_Bits.
						      PPCMaskLower16(value)), 
						    loc, guard));
    inst.copyPosition(s);
    burs.append(inst);
    if (loc != null) {
      loc = (OPT_LocationOperand)loc.copy();
    }
    inst = OPT_RVMIRTools.nonPEIGC(MIR_Load.create(PPC_LWZ, R(defLow), 
						   right.copyD2U(), 
						   I(OPT_Bits.PPCMaskLower16
						     (value) + 4), loc, 
						   guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void LONG_LOAD_addx(OPT_BURS burs, OPT_Instruction s, 
		      OPT_RegisterOperand def, 
		      OPT_RegisterOperand left, 
		      OPT_RegisterOperand right, 
		      OPT_LocationOperand loc, 
		      OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Instruction inst = OPT_RVMIRTools.nonPEIGC(MIR_Load.create
						   (PPC_LWZX, R(defHigh), 
						    left, right, loc, 
						    guard));
    inst.copyPosition(s);
    burs.append(inst);
    OPT_RegisterOperand kk = burs.ir.regpool.makeTempInt();
    burs.append(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), I(4)));
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = OPT_RVMIRTools.nonPEIGC(MIR_Load.create(PPC_LWZX, R(defLow), 
						   left.copyU2U(), 
						   kk.copyD2U(), loc, 
						   guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void LONG_STORE_addi(OPT_BURS burs, OPT_Instruction s, 
		       OPT_RegisterOperand def, 
		       OPT_RegisterOperand left, 
		       OPT_IntConstantOperand right, 
		       OPT_LocationOperand loc, 
		       OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    int imm = right.value;
    if (VM.VerifyAssertions)
      VM.assert(imm < (0x8000 - 4));
    OPT_Instruction inst = 
      OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STW, R(defHigh), 
					       left, I(imm), loc, guard));
    inst.copyPosition(s);
    burs.append(inst);
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STW, R(defLow), 
						    left.copyU2U(), 
						    I(imm + 4), loc, 
						    guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void LONG_STORE_addis(OPT_BURS burs, OPT_Instruction s, 
			OPT_RegisterOperand def, 
			OPT_RegisterOperand left, 
			OPT_RegisterOperand right, 
			OPT_IntConstantOperand Value, 
			OPT_LocationOperand loc, OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    int value = Value.value;
    burs.append(MIR_Binary.create(PPC_ADDIS, right, left, 
				  I(OPT_Bits.PPCMaskUpper16(value))));
    OPT_Instruction inst = 
      OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STW, R(defHigh), 
					       right.copyD2U(), 
					       I(OPT_Bits.PPCMaskLower16
						 (value)), 
					       loc, guard));
    inst.copyPosition(s);
    burs.append(inst);
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = OPT_RVMIRTools.nonPEIGC
      (MIR_Store.create(PPC_STW, R(defLow), right.copyD2U(), 
                        I(OPT_Bits.PPCMaskLower16(value) + 4), loc, guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void LONG_STORE_addx(OPT_BURS burs, OPT_Instruction s, 
		       OPT_RegisterOperand def, 
		       OPT_RegisterOperand left, 
		       OPT_RegisterOperand right, 
		       OPT_LocationOperand loc, 
		       OPT_Operand guard) {
    OPT_Register defHigh = def.register;
    OPT_Register defLow = burs.ir.regpool.getSecondReg(defHigh);
    OPT_Instruction inst = 
      OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STWX, R(defHigh), left, 
					       right, loc, guard));
    inst.copyPosition(s);
    burs.append(inst);
    OPT_RegisterOperand kk = burs.ir.regpool.makeTempInt();
    burs.append(MIR_Binary.create(PPC_ADDI, kk, right.copyU2U(), I(4)));
    if (loc != null)
      loc = (OPT_LocationOperand)loc.copy();
    inst = OPT_RVMIRTools.nonPEIGC(MIR_Store.create(PPC_STWX, R(defLow), 
						    left.copyU2U(), 
						    kk.copyD2U(), loc, 
						    guard));
    inst.copyPosition(s);
    burs.append(inst);
  }

  void DOUBLE_IFCMP(OPT_BURS burs, OPT_Instruction s, OPT_Operator op, 
		    OPT_RegisterOperand left, OPT_Operand right) {
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
          VM.assert(s.nextInstructionInCodeOrder() == lastInstr);
        // Set branch = target of GOTO
        branch = (OPT_BranchOperand)Goto.getTarget(lastInstr);
      } else {
        // Set branch = label of next (fallthrough basic block)
        branch = bb.nextBasicBlockInCodeOrder().makeJumpTarget();
      }
    } else {
      branch = (OPT_BranchOperand)target.copy();
    }
    OPT_RegisterOperand cr = burs.ir.regpool.makeTempCondition();
    burs.append(MIR_Binary.create(PPC_FCMPU, cr, left, right));

    // Propagate branch probabilities as follows:  assume the
    // probability of overflow (first condition) is zero, and
    // propagate the original probability to the second condition.
    burs.append(MIR_CondBranch2.create(PPC_BCOND2, cr.copyD2U(), 
				       OPT_PowerPCConditionOperand.UNORDERED(),
				       branch, 
				       new OPT_BranchProfileOperand(0.0),
				       new OPT_PowerPCConditionOperand(c), 
				       target,
				       IfCmp.getBranchProfile(s)));
  }


  /**
   * Expansion of LOWTABLESWITCH.  
   *
   * @param burs an OPT_BURS object
   * @param s the instruction to expand
   */
  final void LOWTABLESWITCH(OPT_BURS burs, OPT_Instruction s) {
    // (1) We're changing index from a U to a DU.
    //     Inject a fresh copy instruction to make sure we aren't
    //     going to get into trouble (if someone else was also using index).
    OPT_RegisterOperand newIndex = burs.ir.regpool.makeTempInt(); 
    burs.append(MIR_Move.create(PPC_MOVE, newIndex, LowTableSwitch.getIndex(s))); 
    int number = LowTableSwitch.getNumberOfTargets(s);
    OPT_Instruction s2 = CPOS(s,MIR_LowTableSwitch.create(MIR_LOWTABLESWITCH, newIndex, number*2));
    for (int i=0; i<number; i++) {
      MIR_LowTableSwitch.setTarget(s2,i,LowTableSwitch.getTarget(s,i));
      MIR_LowTableSwitch.setBranchProfile(s2,i,LowTableSwitch.getBranchProfile(s,i));
    }
    burs.append(s2);
  }




  // Take the generic LIR trap_if and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See VM_TrapConstants.java.
  // Also see OPT_ConvertToLowLevelIR.java which generates most of these TRAP_IFs.
  void TRAP_IF(OPT_BURS burs, OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    OPT_RegisterOperand v1 = (OPT_RegisterOperand)TrapIf.getClearVal1(s);
    OPT_RegisterOperand v2 = (OPT_RegisterOperand)TrapIf.getClearVal2(s);
    OPT_ConditionOperand cond = TrapIf.getClearCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getClearTCode(s);
    
    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
	if (cond.isLOWER_EQUAL()) {
	  burs.append(MIR_Trap.mutate(s, PPC_TW, gRes, 
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
  void TRAP_IF_IMM(OPT_BURS burs, OPT_Instruction s) {
    OPT_RegisterOperand gRes = TrapIf.getClearGuardResult(s);
    OPT_RegisterOperand v1 =  (OPT_RegisterOperand)TrapIf.getClearVal1(s);
    OPT_IntConstantOperand v2 = (OPT_IntConstantOperand)TrapIf.getClearVal2(s);
    OPT_ConditionOperand cond = TrapIf.getClearCond(s);
    OPT_TrapCodeOperand tc = TrapIf.getClearTCode(s);

    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
	if (cond.isLOWER_EQUAL()) {
	  burs.append(MIR_Trap.mutate(s, PPC_TWI, gRes, 
				      new OPT_PowerPCTrapOperand(cond),
				      v1, v2, tc));
	} else if (cond.isHIGHER_EQUAL()) {
	  // have flip the operands and use non-immediate so trap handler can recognize.
	  OPT_RegisterOperand tmp = burs.ir.regpool.makeTempInt();
	  IntConstant(burs, tmp.register, v2.value);
	  burs.append(MIR_Trap.mutate(s, PPC_TW, gRes,
				      new OPT_PowerPCTrapOperand(cond.flipOperands()),
				      tmp, v1, tc)); 
	} else {
	  throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
	}
      }
      break;
    case VM_Runtime.TRAP_DIVIDE_BY_ZERO:
      {
	// A slightly ugly matter, but we need to deal with combining
	// the two pieces of a long register from a LONG_ZERO_CHECK.  
	// A little awkward, but probably the easiest workaround...
	if (v1.type == VM_Type.LongType) {
	  OPT_RegisterOperand rr = burs.ir.regpool.makeTempInt();
	  burs.append(MIR_Binary.create(PPC_OR, rr, v1, 
					R(burs.ir.regpool.getSecondReg(v1.register))));
	  v1 = rr.copyD2U();
	}
	
	if (cond.isEQUAL() && v2.value == 0) {
	  burs.append(MIR_Trap.mutate(s, PPC_TWI, gRes, 
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



  // Take the generic LIR trap and coerce into the limited vocabulary
  // understand by C trap handler on PPC.  See VM_TrapConstants.java.
  void TRAP(OPT_BURS burs, OPT_Instruction s) {
    OPT_RegisterOperand gRes = Trap.getClearGuardResult(s);
    OPT_TrapCodeOperand tc = Trap.getClearTCode(s);
    switch(tc.getTrapCode()) {
    case VM_Runtime.TRAP_NULL_POINTER:
      {
	VM_Method target = VM_Entrypoints.raiseNullPointerException;
	mutateTrapToCall(burs, s, target);
      }
      break;
    case VM_Runtime.TRAP_ARRAY_BOUNDS:
      {
	VM_Method target = VM_Entrypoints.raiseArrayBoundsException;
	mutateTrapToCall(burs, s, target);
      }
      break;
    case VM_Runtime.TRAP_DIVIDE_BY_ZERO:
      {
	VM_Method target = VM_Entrypoints.raiseArithmeticException;
	mutateTrapToCall(burs, s, target);
      }
      break;
    case VM_Runtime.TRAP_CHECKCAST:
      {
	burs.append(MIR_Trap.mutate(s, PPC_TWI, gRes, 
				    OPT_PowerPCTrapOperand.ALWAYS(),
				    R(12), I(VM_TrapConstants.CHECKCAST_TRAP & 0xffff), tc));
      }
      break;
    case VM_Runtime.TRAP_MUST_IMPLEMENT:
      {
	burs.append(MIR_Trap.mutate(s, PPC_TWI, gRes, 
				    OPT_PowerPCTrapOperand.ALWAYS(),
				    R(12), I(VM_TrapConstants.MUST_IMPLEMENT_TRAP & 0xffff), tc));
      }
      break;
    case VM_Runtime.TRAP_STORE_CHECK:
      {
	burs.append(MIR_Trap.mutate(s, PPC_TWI, gRes, 
				    OPT_PowerPCTrapOperand.ALWAYS(),
				    R(12), I(VM_TrapConstants.STORE_CHECK_TRAP & 0xffff), tc));
      }
      break;
    default:
      throw new OPT_OptimizingCompilerException("Unexpected case of trap_if"+s);
    }
  }


  private void mutateTrapToCall(OPT_BURS burs, 
				OPT_Instruction s, 
				VM_Method target) {
    int offset = target.getOffset();
    OPT_RegisterOperand tmp = burs.ir.regpool.makeTemp(OPT_ClassLoaderProxy.JavaLangObjectArrayType);
    OPT_Register JTOC = burs.ir.regpool.getPhysicalRegisterSet().getJTOC();
    OPT_MethodOperand meth = OPT_MethodOperand.STATIC(target);
    meth.setIsNonReturningCall(true);
    if (SI16(offset)) {
      burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Load.create(PPC_LWZ, tmp, R(JTOC), I(offset))));
    } else {
      OPT_RegisterOperand tmp2 = burs.ir.regpool.makeTempInt();
      IntConstant(burs, tmp2.register, offset);
      burs.append(OPT_RVMIRTools.nonPEIGC(MIR_Load.create(PPC_LWZX, tmp, R(JTOC), tmp2)));
    }
    burs.append(MIR_Move.create(PPC_MTSPR, R(CTR), tmp.copyD2U()));
    burs.append(MIR_Call.mutate0(s, PPC_BCTRL, null, null, meth));
  }
}
