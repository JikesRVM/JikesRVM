/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Handles the conversion from LIR to MIR of operators whose 
 * expansion requires the introduction of new control flow (new basic blocks).
 * 
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @modified Vivek Sarkar
 * @modified Igor Pechtchanski
 * @modified Martin Trapp
 */
abstract class OPT_ComplexLIR2MIRExpansion extends OPT_RVMIRTools {

  /**
   * Converts the given IR to low level PowerPC IR.
   *
   * @param ir IR to convert
   */
  public static void convert (OPT_IR ir) {
    for (OPT_Instruction next = null, s = ir.firstInstructionInCodeOrder(); 
	 s != null; 
	 s = next) {
      next = s.nextInstructionInCodeOrder();
      switch (s.getOpcode()) {
      case DOUBLE_2INT_opcode:case FLOAT_2INT_opcode:
	double_2int(s, ir);
	break;
      case LONG_SHR_opcode:
	long_shr(s, ir);
	break;
      case LONG_IFCMP_opcode:
	long_ifcmp(s, ir);
	break;
      case BOOLEAN_CMP_opcode:
	boolean_cmp(s, ir);
	break;
      case DOUBLE_CMPL_opcode:
      case FLOAT_CMPL_opcode:
      case DOUBLE_CMPG_opcode:
      case FLOAT_CMPG_opcode:
	threeValueCmp(s, ir);
	break;
      case LONG_CMP_opcode:
	threeValueLongCmp(s, ir);
	break;
      case GET_TIME_BASE_opcode:
	get_time_base(s, ir);
	break;
      }
    }
    OPT_DefUse.recomputeSpansBasicBlock(ir);
  }

  private static void double_2int (OPT_Instruction s, OPT_IR ir) {
    OPT_Register res = Unary.getResult(s).register;
    OPT_Register src = ((OPT_RegisterOperand)Unary.getVal(s)).register;
    OPT_Register FP = ir.regpool.getPhysicalRegisterSet().getFP();
    int p = ir.stackManager.allocateSpaceForConversion();
    OPT_Register temp = ir.regpool.getDouble(false);
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB3 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand cond = ir.regpool.makeTempCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC_FCMPU, cond, D(src), D(src)));
    BB1.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(0)));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, cond.copyD2U(), 
						OPT_PowerPCConditionOperand.UNORDERED(), BB3.makeJumpTarget(),
						new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Unary.create(PPC_FCTIWZ, D(temp), D(src)));
    BB2.appendInstruction(nonPEIGC(MIR_Store.create(PPC_STFD, D(temp), 
						    R(FP), I(p))));
    BB2.appendInstruction(nonPEIGC(MIR_Load.create(PPC_LWZ, R(res), R(FP), 
						   I(p + 4))));
    // fix up CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    s.remove();
  }

  private static void boolean_cmp (OPT_Instruction s, OPT_IR ir) {
    // undo the optimization because it cannot efficiently be generated
    OPT_Register res = BooleanCmp.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand)BooleanCmp.getClearVal1(s);
    OPT_Operand two = BooleanCmp.getClearVal2(s);
    OPT_ConditionOperand cond = BooleanCmp.getClearCond(s);
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB4 = BB1.splitNodeAt(s, ir);
    s = s.remove();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand t = ir.regpool.makeTempInt();
    t.register.setCondition();
    OPT_Operator op;
    if (two instanceof OPT_IntConstantOperand) {
      op = cond.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op = cond.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    BB1.appendInstruction(MIR_Binary.create(op, t, one, two));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, t.copyD2U(), 
						OPT_PowerPCConditionOperand.get(cond), BB3.makeJumpTarget(),
						new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(0)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB4.makeJumpTarget()));
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(1)));
    // fix CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB4);
    BB3.insertOut(BB4);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);
  }

  /**
   * compare to values and set result to -1, 0, 1 for <, =, >, respectively
   * @param s the compare instruction
   * @param ir the governing IR
   */
  private static void threeValueCmp (OPT_Instruction s, OPT_IR ir) {
    OPT_PowerPCConditionOperand
      firstCond = OPT_PowerPCConditionOperand.LESS_EQUAL();;
    int firstConst = 1;
      
    switch (s.operator.opcode) {
    case DOUBLE_CMPG_opcode:
    case FLOAT_CMPG_opcode:
      firstCond  = OPT_PowerPCConditionOperand.GREATER_EQUAL();
      firstConst = -1;
      break;
    case DOUBLE_CMPL_opcode:
    case FLOAT_CMPL_opcode:
      break;
    default: if (VM.VerifyAssertions) VM.assert (false);
      break;
    }
    OPT_Register res = Binary.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand two = (OPT_RegisterOperand) Binary.getClearVal2(s);
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB6 = BB1.splitNodeAt(s, ir);
    s = s.remove();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB4 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB5 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand t = ir.regpool.makeTempInt();
    t.register.setCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC_FCMPU, t, one, two));
    BB1.appendInstruction
      (MIR_CondBranch.create(PPC_BCOND, t.copyD2U(), firstCond,
			     BB3.makeJumpTarget(),
			     new OPT_BranchProfileOperand(0.5)));
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(firstConst)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB3.appendInstruction
      (MIR_CondBranch.create(PPC_BCOND, t.copyD2U(),
			     OPT_PowerPCConditionOperand.EQUAL(),
			     BB5.makeJumpTarget(),
			     new OPT_BranchProfileOperand(0.01)));

    BB4.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(-firstConst)));
    BB4.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB5.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(0)));
    // fix CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB6);
    BB3.insertOut(BB4);
    BB3.insertOut(BB5);
    BB4.insertOut(BB6);
    BB5.insertOut(BB6);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);
    ir.cfg.linkInCodeOrder(BB4, BB5);
    ir.cfg.linkInCodeOrder(BB5, BB6);
  }
  /**
   * compare to values and set result to -1, 0, 1 for <, =, >, respectively
   * @param s the compare instruction
   * @param ir the governing IR
   */
  private static void threeValueLongCmp (OPT_Instruction s, OPT_IR ir) {
    OPT_Register res = Binary.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand two = (OPT_RegisterOperand) Binary.getClearVal2(s);
    OPT_RegisterOperand lone = L(ir.regpool.getSecondReg(one.register));
    OPT_RegisterOperand ltwo = L(ir.regpool.getSecondReg(two.register));
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB6 = BB1.splitNodeAt(s, ir);
    s = s.remove();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB4 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB5 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand t = ir.regpool.makeTempInt();
    t.register.setCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC_CMP, t, one, two));
    BB1.appendInstruction
      (MIR_CondBranch2.create(PPC_BCOND2, t.copyD2U(),
			      OPT_PowerPCConditionOperand.LESS(),
			      BB4.makeJumpTarget(),
			      new OPT_BranchProfileOperand(0.49),
			      OPT_PowerPCConditionOperand.GREATER(),
			      BB5.makeJumpTarget(),
			      new OPT_BranchProfileOperand(0.49)));
    BB2.appendInstruction(MIR_Binary.create(PPC_CMPL, t.copyD2D(), lone, ltwo));
    BB2.appendInstruction
      (MIR_CondBranch2.create(PPC_BCOND2, t.copyD2U(),
			      OPT_PowerPCConditionOperand.LESS(),
			      BB4.makeJumpTarget(),
			      new OPT_BranchProfileOperand(0.49),
			      OPT_PowerPCConditionOperand.GREATER(),
			      BB5.makeJumpTarget(),
			      new OPT_BranchProfileOperand(0.49)));
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(0)));
    BB3.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB4.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(-1)));
    BB4.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB5.appendInstruction(MIR_Unary.create(PPC_LDI, R(res), I(1)));
    // fix CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB4);
    BB1.insertOut(BB5);
    BB2.insertOut(BB3);
    BB2.insertOut(BB4);
    BB2.insertOut(BB5);
    BB3.insertOut(BB6);
    BB4.insertOut(BB6);
    BB5.insertOut(BB6);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);
    ir.cfg.linkInCodeOrder(BB4, BB5);
    ir.cfg.linkInCodeOrder(BB5, BB6);
  }

  
  private static void long_shr (OPT_Instruction s, OPT_IR ir) {
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.splitNodeAt(s, ir);
    OPT_Register defHigh = Binary.getResult(s).register;
    OPT_Register defLow = ir.regpool.getSecondReg(defHigh);
    OPT_RegisterOperand left = (OPT_RegisterOperand)Binary.getVal1(s);
    OPT_Register leftHigh = left.register;
    OPT_Register leftLow = ir.regpool.getSecondReg(leftHigh);
    OPT_RegisterOperand shiftOp = (OPT_RegisterOperand)Binary.getVal2(s);
    OPT_Register shift = shiftOp.register;
    OPT_Register t31 = ir.regpool.getInteger(false);
    OPT_Register t0 = ir.regpool.getInteger(false);
    OPT_Register cr = ir.regpool.getCondition(false);
    defLow.setSpansBasicBlock();
    defHigh.setSpansBasicBlock();
    s.insertBack(MIR_Binary.create(PPC_SUBFIC, R(t31), R(shift), I(32)));
    s.insertBack(MIR_Binary.create(PPC_SRW, R(defLow), R(leftLow), R(shift)));
    s.insertBack(MIR_Binary.create(PPC_SLW, R(t0), R(leftHigh), R(t31)));
    s.insertBack(MIR_Binary.create(PPC_OR, R(defLow), R(defLow), R(t0)));
    s.insertBack(MIR_Binary.create(PPC_ADDI, R(t31), R(shift), I(-32)));
    s.insertBack(MIR_Binary.create(PPC_SRAW, R(t0), R(leftHigh), R(t31)));
    s.insertBack(MIR_Binary.create(PPC_SRAW, R(defHigh), R(leftHigh), 
				   R(shift)));
    s.insertBack(MIR_Binary.create(PPC_CMPI, R(cr), R(t31), I(0)));
    MIR_CondBranch.mutate(s, PPC_BCOND, R(cr), 
			  OPT_PowerPCConditionOperand.LESS_EQUAL(), 
			  BB3.makeJumpTarget(),
			  new OPT_BranchProfileOperand());
    // insert the branch and second compare
    BB2.appendInstruction(MIR_Move.create(PPC_MOVE, R(defLow), R(t0)));
    // fix up CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
  }


  private static void long_ifcmp(OPT_Instruction s, OPT_IR ir) {
    if (VM.VerifyAssertions) VM.assert(!IfCmp.getCond(s).isUNSIGNED());
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB3 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);

    s.remove(); // s is in BB1, we'll mutate it and insert in BB3 below.

    OPT_RegisterOperand cr = ir.regpool.makeTempCondition();
    OPT_RegisterOperand val1 = (OPT_RegisterOperand)IfCmp.getClearVal1(s);
    OPT_RegisterOperand val2 = (OPT_RegisterOperand)IfCmp.getClearVal2(s);
    OPT_RegisterOperand lval1 = L(ir.regpool.getSecondReg(val1.register));
    OPT_RegisterOperand lval2 = L(ir.regpool.getSecondReg(val2.register));
    OPT_PowerPCConditionOperand cond = 
      new OPT_PowerPCConditionOperand(IfCmp.getCond(s));
    BB1.appendInstruction(MIR_Binary.create(PPC_CMP, cr, val1, val2));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, cr.copyD2U(),
						OPT_PowerPCConditionOperand.NOT_EQUAL(),
						BB3.makeJumpTarget(),
						new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Binary.create(PPC_CMPL, cr.copyD2D(),
					    lval1, lval2));
    BB3.prependInstruction(MIR_CondBranch.mutate(s, PPC_BCOND, cr.copyD2U(),
						 cond,
						 IfCmp.getTarget(s),
						 IfCmp.getBranchProfile(s)));
  }

  private static void get_time_base (OPT_Instruction s, OPT_IR ir) {
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB2 = BB1.splitNodeAt(s, ir);
    OPT_Register defHigh = Nullary.getResult(s).register;
    OPT_Register defLow = ir.regpool.getSecondReg(defHigh);
    OPT_Register t0 = ir.regpool.getInteger(false);
    OPT_Register cr = ir.regpool.getCondition(false);
    defLow.setSpansBasicBlock();
    defHigh.setSpansBasicBlock();
    // Try to get the base
    OPT_Register TU = ir.regpool.getPhysicalRegisterSet().getTU();
    OPT_Register TL = ir.regpool.getPhysicalRegisterSet().getTL();
    s.insertBack(MIR_Move.create(PPC_MFTBU, R(defHigh), R(TU)));
    s.insertBack(MIR_Move.create(PPC_MFTB, R(defLow), R(TL)));
    // Try again to see if it changed
    s.insertBack(MIR_Move.create(PPC_MFTBU, R(t0), R(TU)));
    s.insertBack(MIR_Binary.create(PPC_CMP, R(cr), R(t0), R(defHigh)));
    MIR_CondBranch.mutate(s, PPC_BCOND, R(cr), 
			  OPT_PowerPCConditionOperand.NOT_EQUAL(), 
			  BB2.makeJumpTarget(),
			  new OPT_BranchProfileOperand());
    // fix up CFG
    BB1.insertOut(BB2);
    BB2.insertOut(BB2);
    ir.cfg.linkInCodeOrder(BB1, BB2);
  }
}



