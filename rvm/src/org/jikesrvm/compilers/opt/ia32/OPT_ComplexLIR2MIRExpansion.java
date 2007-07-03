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
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.compilers.opt.OPT_DefUse;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Label;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_Compare;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_DoubleShift;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Multiply;
import org.jikesrvm.compilers.opt.ir.MIR_Test;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryAcc;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BranchOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_MemoryOperand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_IMUL2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JCC2;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_JMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MOVSS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_MUL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_NOT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_OR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SAR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHLD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_SHRD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_TEST;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_XOR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_UCOMISS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IA32_CVTTSS2SI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_IA32ConditionOperand;

/**
 * Handles the conversion from LIR to MIR of operators whose
 * expansion requires the introduction of new control flow (new basic blocks).
 */
public abstract class OPT_ComplexLIR2MIRExpansion extends OPT_IRTools {

  /**
   * Converts the given IR to low level IA32 IR.
   *
   * @param ir IR to convert
   */
  public static void convert(OPT_IR ir) {
    OPT_Instruction nextInstr;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = nextInstr) {
      switch (s.getOpcode()) {
        case LONG_MUL_opcode:
          nextInstr = long_mul(s, ir);
          break;
        case LONG_SHL_opcode:
          nextInstr = long_shl(s, ir);
          break;
        case LONG_SHR_opcode:
          nextInstr = long_shr(s, ir);
          break;
        case LONG_USHR_opcode:
          nextInstr = long_ushr(s, ir);
          break;
        case LONG_IFCMP_opcode: {
          OPT_Operand val2 = IfCmp.getVal2(s);
          if (val2 instanceof OPT_RegisterOperand) {
            nextInstr = long_ifcmp(s, ir);
          } else {
            nextInstr = long_ifcmp_imm(s, ir);
          }
          break;
        }
        case FLOAT_IFCMP_opcode:
        case DOUBLE_IFCMP_opcode:
          nextInstr = fp_ifcmp(s);
          break;
        case FLOAT_2INT_opcode:
          nextInstr = float_2int(s, ir);
          break;
        default:
          nextInstr = s.nextInstructionInCodeOrder();
          break;
      }
    }
    OPT_DefUse.recomputeSpansBasicBlock(ir);
  }

  private static OPT_Instruction float_2int(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 6 basic blocks (in code order)
    // 1: the current block that does a test to see if this is a regular f2i or
    //    branches to the maxint/NaN case
    // 2: a block to perform a regular f2i
    // 3: a block to test for NaN
    // 4: a block to perform give maxint
    // 5: a block to perform NaN
    // 6: the next basic block
    OPT_BasicBlock testBB = s.getBasicBlock();
    OPT_BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    OPT_BasicBlock nanBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanBB);
    OPT_BasicBlock maxintBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, maxintBB);
    OPT_BasicBlock nanTestBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanTestBB);
    OPT_BasicBlock f2iBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, f2iBB);

    // Move the maxintFloat value and the value into registers and compare and
    // branch if they are <= or unordered. NB we don't use a memory operand as
    // that would require 2 jccs
    OPT_RegisterOperand result = Unary.getResult(s);
    OPT_RegisterOperand value = Unary.getVal(s).asRegister();
    OPT_MemoryOperand maxint = OPT_BURS_Helpers.loadFromJTOC(VM_Entrypoints.maxintFloatField.getOffset());
    OPT_RegisterOperand maxintReg = ir.regpool.makeTempFloat();;
    s.insertBefore(CPOS(s,MIR_Move.create(IA32_MOVSS, maxintReg, maxint)));
    MIR_Compare.mutate(s, IA32_UCOMISS, maxintReg.copyRO(), value);
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.LLE(),
        nanTestBB.makeJumpTarget(),
        OPT_BranchProfileOperand.unlikely())));
    testBB.insertOut(f2iBB);
    testBB.insertOut(nanTestBB);

    // Convert float to int knowing that if the value is < min int the Intel
    // unspecified result is min int
    f2iBB.appendInstruction(CPOS(s, MIR_Unary.create(IA32_CVTTSS2SI, result, value.copy())));
    f2iBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    f2iBB.insertOut(nextBB);
    
    // Did the compare find a NaN or a maximum integer?
    nanTestBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.PE(),
        nanBB.makeJumpTarget(),
        OPT_BranchProfileOperand.unlikely())));
    nanTestBB.insertOut(nanBB);
    nanTestBB.insertOut(maxintBB);

    // Value was >= max integer
    maxintBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
                                                       result.copyRO(),
                                                       IC(Integer.MAX_VALUE))));
    maxintBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    maxintBB.insertOut(nextBB);
    
    // In case of NaN result is 0
    nanBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
                                                    result.copyRO(),
                                                    IC(0))));
    nanBB.insertOut(nextBB);
    return nextInstr; 
  }

  private static OPT_Instruction long_shl(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    OPT_BasicBlock testBB = s.getBasicBlock();
    OPT_BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    OPT_BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    OPT_BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    OPT_Register lhsReg = Binary.getResult(s).register;
    OPT_Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    OPT_Operand val1 = Binary.getVal1(s);
    OPT_Register rhsReg;
    OPT_Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().register;
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        OPT_BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHL,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        IC(0))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHLD,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHL,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static OPT_Instruction long_shr(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    OPT_BasicBlock testBB = s.getBasicBlock();
    OPT_BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    OPT_BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    OPT_BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    OPT_Register lhsReg = Binary.getResult(s).register;
    OPT_Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    OPT_Operand val1 = Binary.getVal1(s);
    OPT_Register rhsReg;
    OPT_Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().register;
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        OPT_BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
    if (lhsReg != rhsReg) {
      shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
    }
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        IC(31))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHRD,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static OPT_Instruction long_ushr(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    OPT_BasicBlock testBB = s.getBasicBlock();
    OPT_BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    OPT_BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    OPT_BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    OPT_Register lhsReg = Binary.getResult(s).register;
    OPT_Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    OPT_Operand val1 = Binary.getVal1(s);
    OPT_Register rhsReg;
    OPT_Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().register;
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    OPT_Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        OPT_BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHR,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        IC(0))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(rhsReg, VM_TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
          new OPT_RegisterOperand(lowrhsReg, VM_TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHRD,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHR,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(ecx, VM_TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static OPT_Instruction long_mul(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = s.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block and a test for 32bit or 64bit multiply
    // 2: 32bit multiply block
    // 3: 64bit multiply block
    // 4: the next basic block
    OPT_BasicBlock testBB = s.getBasicBlock();
    OPT_BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    OPT_BasicBlock mul64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, mul64BB);
    OPT_BasicBlock mul32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, mul32BB);

    // Source registers
    OPT_Register lhsReg = Binary.getResult(s).register;
    OPT_Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    OPT_Register rhsReg1 = Binary.getVal1(s).asRegister().register;
    OPT_Register lowrhsReg1 = ir.regpool.getSecondReg(rhsReg1);
    OPT_Register rhsReg2 = Binary.getVal2(s).asRegister().register;
    OPT_Register lowrhsReg2 = ir.regpool.getSecondReg(rhsReg2);

    // Working registers
    OPT_Register edx = ir.regpool.getPhysicalRegisterSet().getEDX();
    OPT_Register eax = ir.regpool.getPhysicalRegisterSet().getEAX();
    OPT_Register tmp = ir.regpool.getInteger();

    // The general form of the multiply is
    // (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))

    // Determine whether we need a 32bit or 64bit multiply
    // edx, flags = a | c
    // edx = d
    // eax = b
    // if ((a | c) != 0) goto mul64BB
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
        new OPT_RegisterOperand(rhsReg1, VM_TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(eax, VM_TypeReference.Int),
        new OPT_RegisterOperand(lowrhsReg2, VM_TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        OPT_IA32ConditionOperand.NE(),
        mul64BB.makeJumpTarget(),
        OPT_BranchProfileOperand.unlikely())));
    testBB.insertOut(mul64BB);
    testBB.insertOut(mul32BB);

    // multiply 32: on entry EAX = d, EDX = b, tmp = a
    // edx:eax = b * d
    mul32BB.appendInstruction(CPOS(s, MIR_Multiply.create(IA32_MUL,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(eax, VM_TypeReference.Int),
        new OPT_RegisterOperand(edx, VM_TypeReference.Int))));
    mul32BB.appendInstruction(MIR_Branch.create(IA32_JMP, nextBB.makeJumpTarget()));
    mul32BB.insertOut(nextBB);

    // multiply 64: on entry EAX = d, EDX = b, tmp = a
    // edx = b imul c
    // tmp = a imul d
    // tmp1 = (a imul d) + (b imul c)
    // edx:eax = b * d
    // edx = u(b mul d) + l(a imul d) + l(b imul c)
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(rhsReg2, VM_TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
        new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
        new OPT_RegisterOperand(eax, VM_TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
        new OPT_RegisterOperand(tmp, VM_TypeReference.Int),
        new OPT_RegisterOperand(edx, VM_TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_Multiply.create(IA32_MUL,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(eax, VM_TypeReference.Int),
        new OPT_RegisterOperand(lowrhsReg1, VM_TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
        new OPT_RegisterOperand(edx, VM_TypeReference.Int),
        new OPT_RegisterOperand(tmp, VM_TypeReference.Int))));
    mul64BB.insertOut(nextBB);

    // move result from edx:eax to lhsReg:lowlhsReg
    nextInstr.insertBefore(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(edx, VM_TypeReference.Int))));
    nextInstr.insertBefore(CPOS(s, MIR_Move.create(IA32_MOV,
        new OPT_RegisterOperand(lowlhsReg, VM_TypeReference.Int),
        new OPT_RegisterOperand(eax, VM_TypeReference.Int))));
    s.remove();
    return nextInstr;
  }

  private static OPT_Instruction long_ifcmp(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand) IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_RegisterOperand yh = (OPT_RegisterOperand) IfCmp.getClearVal2(s);
    OPT_RegisterOperand yl = new OPT_RegisterOperand(ir.regpool.getSecondReg(yh.register), VM_TypeReference.Int);
    basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
    return nextInstr;
  }

  private static OPT_Instruction long_ifcmp_imm(OPT_Instruction s, OPT_IR ir) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_ConditionOperand cond = IfCmp.getCond(s);
    OPT_Register xh = ((OPT_RegisterOperand) IfCmp.getVal1(s)).register;
    OPT_Register xl = ir.regpool.getSecondReg(xh);
    OPT_LongConstantOperand rhs = (OPT_LongConstantOperand) IfCmp.getVal2(s);
    int low = rhs.lower32();
    int high = rhs.upper32();
    OPT_IntConstantOperand yh = IC(high);
    OPT_IntConstantOperand yl = IC(low);

    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      OPT_Register th = ir.regpool.getInteger();
      OPT_Register tl = ir.regpool.getInteger();
      if (high == 0) {
        if (low == 0) { // 0,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // 0,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
        } else { // 0,*
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
        }
      } else if (high == -1) {
        if (low == 0) { // -1,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // -1,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        } else { // -1,*
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(th, VM_TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        }
      } else {
        if (low == 0) { // *,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
        } else if (low == -1) { // *,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        } else { // neither high nor low is special
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(th, VM_TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new OPT_RegisterOperand(tl, VM_TypeReference.Int),
                                         new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new OPT_RegisterOperand(tl, VM_TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new OPT_RegisterOperand(th, VM_TypeReference.Int),
                                              new OPT_RegisterOperand(tl, VM_TypeReference.Int)));
        }
      }
      MIR_CondBranch.mutate(s,
                            IA32_JCC,
                            new OPT_IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
      return nextInstr;
    } else {
      // pick up a few special cases where the sign of xh is sufficient
      if (rhs.value == 0L) {
        if (cond.isLESS()) {
          // xh < 0 implies true
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                OPT_IA32ConditionOperand.LT(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                OPT_IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      } else if (rhs.value == -1L) {
        if (cond.isLESS_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(-1)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                OPT_IA32ConditionOperand.LE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                OPT_IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      }

      basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
      return nextInstr;
    }
  }

  private static void basic_long_ifcmp(OPT_Instruction s, OPT_IR ir, OPT_ConditionOperand cond, OPT_Register xh,
                                       OPT_Register xl, OPT_Operand yh, OPT_Operand yl) {
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      OPT_RegisterOperand th = ir.regpool.makeTempInt();
      OPT_RegisterOperand tl = ir.regpool.makeTempInt();
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      s.insertBefore(MIR_Move.create(IA32_MOV, th, new OPT_RegisterOperand(xh, VM_TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, th.copyD2D(), yh));
      s.insertBefore(MIR_Move.create(IA32_MOV, tl, new OPT_RegisterOperand(xl, VM_TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, tl.copyD2D(), yl));
      s.insertBefore(MIR_BinaryAcc.create(IA32_OR, th.copyD2D(), tl.copyD2U()));
      MIR_CondBranch.mutate(s,
                            IA32_JCC,
                            new OPT_IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
    } else {
      // Do the naive thing and generate multiple compare/branch implementation.
      OPT_IA32ConditionOperand cond1;
      OPT_IA32ConditionOperand cond2;
      OPT_IA32ConditionOperand cond3;
      if (cond.isLESS()) {
        cond1 = OPT_IA32ConditionOperand.LT();
        cond2 = OPT_IA32ConditionOperand.GT();
        cond3 = OPT_IA32ConditionOperand.LLT();
      } else if (cond.isGREATER()) {
        cond1 = OPT_IA32ConditionOperand.GT();
        cond2 = OPT_IA32ConditionOperand.LT();
        cond3 = OPT_IA32ConditionOperand.LGT();
      } else if (cond.isLESS_EQUAL()) {
        cond1 = OPT_IA32ConditionOperand.LT();
        cond2 = OPT_IA32ConditionOperand.GT();
        cond3 = OPT_IA32ConditionOperand.LLE();
      } else if (cond.isGREATER_EQUAL()) {
        cond1 = OPT_IA32ConditionOperand.GT();
        cond2 = OPT_IA32ConditionOperand.LT();
        cond3 = OPT_IA32ConditionOperand.LGE();
      } else {
        // I don't think we use the unsigned compares for longs,
        // so defer actually implementing them until we find a test case. --dave
        cond1 = cond2 = cond3 = null;
        OPT_OptimizingCompilerException.TODO();
      }

      OPT_BasicBlock myBlock = s.getBasicBlock();
      OPT_BasicBlock test2Block = myBlock.createSubBlock(s.bcIndex, ir, 0.25f);
      OPT_BasicBlock falseBlock = myBlock.splitNodeAt(s, ir);
      OPT_BasicBlock trueBlock = IfCmp.getTarget(s).target.getBasicBlock();

      falseBlock.recomputeNormalOut(ir);
      myBlock.insertOut(test2Block);
      myBlock.insertOut(falseBlock);
      myBlock.insertOut(trueBlock);
      test2Block.insertOut(falseBlock);
      test2Block.insertOut(trueBlock);
      ir.cfg.linkInCodeOrder(myBlock, test2Block);
      ir.cfg.linkInCodeOrder(test2Block, falseBlock);

      s.remove();

      myBlock.appendInstruction(CPOS(s, MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xh, VM_TypeReference.Int), yh)));
      myBlock.appendInstruction(CPOS(s, MIR_CondBranch2.create(IA32_JCC2,
                                                       cond1,
                                                       trueBlock.makeJumpTarget(),
                                                       new OPT_BranchProfileOperand(),
                                                       cond2,
                                                       falseBlock.makeJumpTarget(),
                                                       new OPT_BranchProfileOperand())));
      test2Block.appendInstruction(CPOS(s, MIR_Compare.create(IA32_CMP, new OPT_RegisterOperand(xl, VM_TypeReference.Int), yl)));
      test2Block.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
                                                         cond3,
                                                         trueBlock.makeJumpTarget(),
                                                         new OPT_BranchProfileOperand())));
    }
  }

  // the fcmoi/fcmoip was generated by burs
  // we do the rest of the expansion here because in some
  // cases we must remove a trailing goto, and we
  // can't do that in burs!
  private static OPT_Instruction fp_ifcmp(OPT_Instruction s) {
    OPT_Instruction nextInstr = s.nextInstructionInCodeOrder();
    OPT_BranchOperand testFailed;
    OPT_BasicBlock bb = s.getBasicBlock();
    OPT_Instruction lastInstr = bb.lastRealInstruction();
    if (lastInstr.operator() == IA32_JMP) {
      // We're in trouble if there is another instruction between s and lastInstr!
      if (VM.VerifyAssertions) VM._assert(s.nextInstructionInCodeOrder() == lastInstr);
      // Set testFailed to target of GOTO
      testFailed = MIR_Branch.getTarget(lastInstr);
      nextInstr = lastInstr.nextInstructionInCodeOrder();
      lastInstr.remove();
    } else {
      // Set testFailed to label of next (fallthrough basic block)
      testFailed = bb.nextBasicBlockInCodeOrder().makeJumpTarget();
    }

    // Translate condition operand respecting IA32 FCOMI/COMISS/COMISD
    OPT_Instruction fcomi = s.prevInstructionInCodeOrder();
    OPT_Operand val1 = MIR_Compare.getVal1(fcomi);
    OPT_Operand val2 = MIR_Compare.getVal2(fcomi);
    OPT_ConditionOperand c = IfCmp.getCond(s);
    OPT_BranchOperand target = IfCmp.getTarget(s);
    OPT_BranchProfileOperand branchProfile = IfCmp.getBranchProfile(s);

    // FCOMI sets ZF, PF, and CF as follows:
    // Compare Results      ZF     PF      CF
    // left > right          0      0       0
    // left < right          0      0       1
    // left == right         1      0       0
    // UNORDERED             1      1       1

    // Propagate branch probabilities as follows: assume the
    // probability of unordered (first condition) is zero, and
    // propagate the original probability to the second condition.
    switch (c.value) {
      // Branches that WON'T be taken after unordered comparison
      // (i.e. UNORDERED is a goto to testFailed)
      case OPT_ConditionOperand.CMPL_EQUAL:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        // Check whether val1 and val2 operands are the same
        if (!val1.similar(val2)) {
          s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                                OPT_IA32ConditionOperand.PE(),
                                                // PF == 1
                                                testFailed,
                                                new OPT_BranchProfileOperand(0f),
                                                OPT_IA32ConditionOperand.EQ(),
                                                // ZF == 1
                                                target,
                                                branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand) (testFailed.copy())));
        } else {
          // As val1 == val2 result of compare must be == or UNORDERED
          s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.PO(),  // PF == 0
                                               target, branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        }
        break;
      case OPT_ConditionOperand.CMPL_GREATER:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.LGT(), // CF == 0 and ZF == 0
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case OPT_ConditionOperand.CMPG_LESS:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              OPT_IA32ConditionOperand.PE(),
                                              // PF == 1
                                              testFailed,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.LLT(),
                                              // CF == 1
                                              target,
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand) (testFailed.copy())));
        break;
      case OPT_ConditionOperand.CMPL_GREATER_EQUAL:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.LGE(), // CF == 0
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case OPT_ConditionOperand.CMPG_LESS_EQUAL:
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              OPT_IA32ConditionOperand.PE(),
                                              // PF == 1
                                              testFailed,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.LGT(),
                                              // ZF == 0 and CF == 0
                                              (OPT_BranchOperand) (testFailed.copy()),
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, target));
        break;
        // Branches that WILL be taken after unordered comparison
        // (i.e. UNORDERED is a goto to target)
      case OPT_ConditionOperand.CMPL_NOT_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        // Check whether val1 and val2 operands are the same
        if (!val1.similar(val2)) {
          s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                                OPT_IA32ConditionOperand.PE(),
                                                // PF == 1
                                                target,
                                                new OPT_BranchProfileOperand(0f),
                                                OPT_IA32ConditionOperand.NE(),
                                                // ZF == 0
                                                (OPT_BranchOperand) (target.copy()),
                                                branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        } else {
          // As val1 == val2 result of compare must be == or UNORDERED
          s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.PE(),  // PF == 1
                                               target, branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        }
        break;
      case OPT_ConditionOperand.CMPL_LESS:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.LLT(),   // CF == 1
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case OPT_ConditionOperand.CMPG_GREATER_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              OPT_IA32ConditionOperand.PE(),
                                              // PF == 1
                                              target,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.LLT(),
                                              // CF == 1
                                              testFailed,
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, (OPT_BranchOperand) (target.copy())));
        break;
      case OPT_ConditionOperand.CMPG_GREATER:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              OPT_IA32ConditionOperand.PE(),
                                              // PF == 1
                                              target,
                                              new OPT_BranchProfileOperand(0f),
                                              OPT_IA32ConditionOperand.LGT(),
                                              // ZF == 0 and CF == 0
                                              (OPT_BranchOperand) (target.copy()),
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case OPT_ConditionOperand.CMPL_LESS_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, OPT_IA32ConditionOperand.LLE(), // CF == 1 or ZF == 1
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      default:
        OPT_OptimizingCompilerException.UNREACHABLE();
    }
    s.remove();
    return nextInstr;
  }
}

