/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OPT_DefUse;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.MIR_Binary;
import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Store;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ATTEMPT_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ATTEMPT_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_CMPG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_CMPL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_CMPG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_CMPL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.GET_TIME_BASE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_CMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_CMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_CMPI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_CMPL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_CMPLI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_FCTIDZ;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC64_LD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_ADDI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_B;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCOND;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCOND2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_CMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_CMPI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_CMPL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_CMPLI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_FCMPU;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_FCTIWZ;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LDI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LInt;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MFTB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MFTBU;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_OR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_SLW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_SRAW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_SRW;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STAddrCXr;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STFD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_STWCXr;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_SUBFIC;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PowerPCConditionOperand;

/**
 * Handles the conversion from LIR to MIR of operators whose 
 * expansion requires the introduction of new control flow (new basic blocks).
 *
 * TODO: Make these methods virtual; spilt into Common superclass with
 *       32/64 subclasses.
 *       
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @modified Vivek Sarkar
 * @modified Igor Pechtchanski
 * @modified Martin Trapp
 * @modified Stephen Fink
 */
public abstract class OPT_ComplexLIR2MIRExpansion extends OPT_IRTools {

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
      case DOUBLE_2LONG_opcode:case FLOAT_2LONG_opcode:
        if (VM.BuildFor64Addr) {
          double_2long(s, ir);
        } else {
          if (VM.VerifyAssertions) VM._assert(false);
        }
        break;
      case LONG_SHR_opcode:
        if (VM.BuildFor32Addr) {
          long_shr(s, ir);
        } else {
          if (VM.VerifyAssertions) VM._assert(false);
        }          
        break;
      case LONG_IFCMP_opcode:
        if (VM.BuildFor32Addr) {
          long_ifcmp(s, ir);
        } else {
          if (VM.VerifyAssertions) VM._assert(false);
        }          
        break;
      case BOOLEAN_CMP_INT_opcode:
        boolean_cmp(s, ir, true);
        break;
      case BOOLEAN_CMP_ADDR_opcode:
        boolean_cmp(s, ir, VM.BuildFor32Addr);
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
      case ATTEMPT_INT_opcode:
        attempt(s, ir, false);
        break;
      case ATTEMPT_ADDR_opcode:
        attempt(s, ir, true);
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
    OPT_Register temp = ir.regpool.getDouble();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB3 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand cond = ir.regpool.makeTempCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC_FCMPU, cond, D(src), D(src)));
    BB1.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(0)));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, cond.copyD2U(), 
                                                OPT_PowerPCConditionOperand.UNORDERED(), BB3.makeJumpTarget(),
                                                new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Unary.create(PPC_FCTIWZ, D(temp), D(src)));
    BB2.appendInstruction(MIR_Store.create(PPC_STFD, D(temp), A(FP), IC(p)));
    BB2.appendInstruction(MIR_Load.create(PPC_LInt, I(res), A(FP), IC(p + 4)));
    // fix up CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    s.remove();
  }

  private static void double_2long (OPT_Instruction s, OPT_IR ir) {
    if (VM.VerifyAssertions) VM._assert(VM.BuildFor64Addr);
    OPT_Register res = Unary.getResult(s).register;
    OPT_Register src = ((OPT_RegisterOperand)Unary.getVal(s)).register;
    OPT_Register FP = ir.regpool.getPhysicalRegisterSet().getFP();
    int p = ir.stackManager.allocateSpaceForConversion();
    OPT_Register temp = ir.regpool.getDouble();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB3 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand cond = ir.regpool.makeTempCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC_FCMPU, cond, D(src), D(src)));
    BB1.appendInstruction(MIR_Unary.create(PPC_LDI, L(res), IC(0)));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, cond.copyD2U(), 
                                                OPT_PowerPCConditionOperand.UNORDERED(), BB3.makeJumpTarget(),
                                                new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Unary.create(PPC64_FCTIDZ, D(temp), D(src)));
    BB2.appendInstruction(MIR_Store.create(PPC_STFD, D(temp), A(FP), IC(p)));
    BB2.appendInstruction(MIR_Load.create(PPC64_LD, L(res), A(FP), IC(p)));
    // fix up CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    s.remove();
  }

  private static void boolean_cmp (OPT_Instruction s, OPT_IR ir, boolean cmp32Bit) {
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
    if (VM.BuildFor64Addr && !cmp32Bit) {
      if (two instanceof OPT_IntConstantOperand) {
        op = cond.isUNSIGNED() ? PPC64_CMPLI : PPC64_CMPI;
      } else { 
        op = cond.isUNSIGNED() ? PPC64_CMPL : PPC64_CMP;
      }
    } else if (two instanceof OPT_IntConstantOperand) {
      op = cond.isUNSIGNED() ? PPC_CMPLI : PPC_CMPI;
    } else { 
      op = cond.isUNSIGNED() ? PPC_CMPL : PPC_CMP;
    }
    BB1.appendInstruction(MIR_Binary.create(op, t, one, two));
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND, t.copyD2U(), 
                                                OPT_PowerPCConditionOperand.get(cond), BB3.makeJumpTarget(),
                                                new OPT_BranchProfileOperand()));
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(0)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB4.makeJumpTarget()));
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(1)));
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
      firstCond = OPT_PowerPCConditionOperand.LESS_EQUAL();
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
    default: if (VM.VerifyAssertions) VM._assert (false);
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
                             new OPT_BranchProfileOperand(0.5f)));
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(firstConst)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB3.appendInstruction
      (MIR_CondBranch.create(PPC_BCOND, t.copyD2U(),
                             OPT_PowerPCConditionOperand.EQUAL(),
                             BB5.makeJumpTarget(),
                             OPT_BranchProfileOperand.unlikely()));

    BB4.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(-firstConst)));
    BB4.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB5.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(0)));
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
    if (VM.BuildFor32Addr) {
      threeValueLongCmp_32(s, ir);
    } else {
      threeValueLongCmp_64(s, ir);
    }
  }
      
  private static void threeValueLongCmp_32(OPT_Instruction s, OPT_IR ir) {
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
                              new OPT_BranchProfileOperand(0.49f),
                              OPT_PowerPCConditionOperand.GREATER(),
                              BB5.makeJumpTarget(),
                              new OPT_BranchProfileOperand(0.49f)));
    BB2.appendInstruction(MIR_Binary.create(PPC_CMPL, t.copyD2D(), lone, ltwo));
    BB2.appendInstruction
      (MIR_CondBranch2.create(PPC_BCOND2, t.copyD2U(),
                              OPT_PowerPCConditionOperand.LESS(),
                              BB4.makeJumpTarget(),
                              new OPT_BranchProfileOperand(0.49f),
                              OPT_PowerPCConditionOperand.GREATER(),
                              BB5.makeJumpTarget(),
                              new OPT_BranchProfileOperand(0.49f)));
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(0)));
    BB3.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB4.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(-1)));
    BB4.appendInstruction(MIR_Branch.create(PPC_B, BB6.makeJumpTarget()));
    BB5.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(1)));
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

  private static void threeValueLongCmp_64(OPT_Instruction s, OPT_IR ir) {
    OPT_Register res = Binary.getClearResult(s).register;
    OPT_RegisterOperand one = (OPT_RegisterOperand) Binary.getClearVal1(s);
    OPT_RegisterOperand two = (OPT_RegisterOperand) Binary.getClearVal2(s);
    res.setSpansBasicBlock();
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB5 = BB1.splitNodeAt(s, ir);
    s = s.remove();
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB4 = BB1.createSubBlock(0, ir);
    OPT_RegisterOperand t = ir.regpool.makeTempInt();
    t.register.setCondition();
    BB1.appendInstruction(MIR_Binary.create(PPC64_CMP, t, one, two));
    BB1.appendInstruction
      (MIR_CondBranch2.create(PPC_BCOND2, t.copyD2U(),
                              OPT_PowerPCConditionOperand.LESS(),
                              BB3.makeJumpTarget(),
                              new OPT_BranchProfileOperand(0.49f),
                              OPT_PowerPCConditionOperand.GREATER(),
                              BB4.makeJumpTarget(),
                              new OPT_BranchProfileOperand(0.49f)));
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(0)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB5.makeJumpTarget()));
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(-1)));
    BB3.appendInstruction(MIR_Branch.create(PPC_B, BB5.makeJumpTarget()));
    BB4.appendInstruction(MIR_Unary.create(PPC_LDI, I(res), IC(1)));
    // fix CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB1.insertOut(BB4);
    BB2.insertOut(BB5);
    BB3.insertOut(BB5);
    BB4.insertOut(BB5);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);
    ir.cfg.linkInCodeOrder(BB4, BB5);
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
    OPT_Register t31 = ir.regpool.getInteger();
    OPT_Register t0 = ir.regpool.getInteger();
    OPT_Register cr = ir.regpool.getCondition();
    defLow.setSpansBasicBlock();
    defHigh.setSpansBasicBlock();
    s.insertBefore(MIR_Binary.create(PPC_SUBFIC, I(t31), I(shift), IC(32)));
    s.insertBefore(MIR_Binary.create(PPC_SRW, I(defLow), I(leftLow), I(shift)));
    s.insertBefore(MIR_Binary.create(PPC_SLW, I(t0), I(leftHigh), I(t31)));
    s.insertBefore(MIR_Binary.create(PPC_OR, I(defLow), I(defLow), I(t0)));
    s.insertBefore(MIR_Binary.create(PPC_ADDI, I(t31), I(shift), IC(-32)));
    s.insertBefore(MIR_Binary.create(PPC_SRAW, I(t0), I(leftHigh), I(t31)));
    s.insertBefore(MIR_Binary.create(PPC_SRAW, I(defHigh), I(leftHigh), 
                                   I(shift)));
    s.insertBefore(MIR_Binary.create(PPC_CMPI, I(cr), I(t31), IC(0)));
    MIR_CondBranch.mutate(s, PPC_BCOND, I(cr), 
                          OPT_PowerPCConditionOperand.LESS_EQUAL(), 
                          BB3.makeJumpTarget(),
                          new OPT_BranchProfileOperand());
    // insert the branch and second compare
    BB2.appendInstruction(MIR_Move.create(PPC_MOVE, I(defLow), I(t0)));
    // fix up CFG
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB3);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
  }

  private static void long_ifcmp(OPT_Instruction s, OPT_IR ir) {
    if (VM.VerifyAssertions) VM._assert(!IfCmp.getCond(s).isUNSIGNED());
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
    if (VM.BuildFor32Addr) {
      OPT_BasicBlock BB1 = s.getBasicBlock();
      BB1 = BB1.segregateInstruction(s,ir);
      OPT_Register defHigh = Nullary.getResult(s).register;
      OPT_Register defLow = ir.regpool.getSecondReg(defHigh);
      OPT_Register t0 = ir.regpool.getInteger();
      OPT_Register cr = ir.regpool.getCondition();
      defLow.setSpansBasicBlock();
      defHigh.setSpansBasicBlock();
      // Try to get the base
      OPT_Register TU = ir.regpool.getPhysicalRegisterSet().getTU();
      OPT_Register TL = ir.regpool.getPhysicalRegisterSet().getTL();
      s.insertBefore(MIR_Move.create(PPC_MFTBU, I(defHigh), I(TU)));
      s.insertBefore(MIR_Move.create(PPC_MFTB, I(defLow), I(TL)));
      // Try again to see if it changed
      s.insertBefore(MIR_Move.create(PPC_MFTBU, I(t0), I(TU)));
      s.insertBefore(MIR_Binary.create(PPC_CMP, I(cr), I(t0), I(defHigh)));
      MIR_CondBranch.mutate(s, PPC_BCOND, I(cr), 
                            OPT_PowerPCConditionOperand.NOT_EQUAL(), 
                            BB1.makeJumpTarget(),
                            new OPT_BranchProfileOperand());
      // fix up CFG
      BB1.insertOut(BB1);
    } else {
      // We read the 64-bit time base register atomically
      OPT_Register def = Nullary.getResult(s).register;
      // See PowerPC Architecture, Book II, pp.352-353
      OPT_Register TL = ir.regpool.getPhysicalRegisterSet().getTL();
      MIR_Move.mutate(s, PPC_MFTB, L(def), L(TL));
    }
  }

  private static void attempt(OPT_Instruction s, OPT_IR ir, boolean isAddress) {
    OPT_BasicBlock BB1 = s.getBasicBlock();
    OPT_BasicBlock BB4 = BB1.splitNodeAt(s, ir);
    OPT_BasicBlock BB2 = BB1.createSubBlock(0, ir);
    OPT_BasicBlock BB3 = BB2.createSubBlock(0, ir);
    BB1.insertOut(BB2);
    BB1.insertOut(BB3);
    BB2.insertOut(BB4);
    BB3.insertOut(BB4);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    ir.cfg.linkInCodeOrder(BB2, BB3);
    ir.cfg.linkInCodeOrder(BB3, BB4);

    // mutate ATTEMPT into a STWCX
    OPT_RegisterOperand newValue = (OPT_RegisterOperand)Attempt.getNewValue(s);
    OPT_RegisterOperand address = (OPT_RegisterOperand)Attempt.getAddress(s);
    OPT_Operand offset = Attempt.getOffset(s);
    OPT_LocationOperand location = Attempt.getLocation(s);
    OPT_Operand guard = Attempt.getGuard(s);
    OPT_RegisterOperand result = Attempt.getResult(s);
    MIR_Store.mutate(s, (isAddress? PPC_STAddrCXr : PPC_STWCXr), newValue, address, offset, location,
                     guard);

    
    // Branch to BB3 iff the STWXC succeeds (CR(0) is EQUAL)
    // Else fall through to BB2
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    BB1.appendInstruction(MIR_CondBranch.create(PPC_BCOND,
                                                I(phys.getConditionRegister(0)),
                                                OPT_PowerPCConditionOperand.EQUAL(),
                                                BB3.makeJumpTarget(),
                                                new OPT_BranchProfileOperand()));
    // BB2 sets result to FALSE and jumps to BB4
    BB2.appendInstruction(MIR_Unary.create(PPC_LDI, result.copyRO(), IC(0)));
    BB2.appendInstruction(MIR_Branch.create(PPC_B, BB4.makeJumpTarget()));
    
    // BB3 sets result to TRUE and falls through to BB4
    BB3.appendInstruction(MIR_Unary.create(PPC_LDI, result.copyRO(), IC(1)));
  }


}
