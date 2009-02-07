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
package org.jikesrvm.compilers.opt.lir2mir.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BBend;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Label;
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
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTTSD2SI;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTTSS2SI;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FISTP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FLDCW;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FNSTCW;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FSTP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FUCOMIP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_IMUL2;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_JCC2;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_JMP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVZX__W;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MUL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_NOT;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_OR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SAR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHLD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHRD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_TEST;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_UCOMISD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_UCOMISS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XOR;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_USHR_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StackLocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.runtime.Entrypoints;

/**
 * Handles the conversion from LIR to MIR of operators whose
 * expansion requires the introduction of new control flow (new basic blocks).
 */
public abstract class ComplexLIR2MIRExpansion extends IRTools {

  /**
   * Converts the given IR to low level IA32 IR.
   *
   * @param ir IR to convert
   */
  public static void convert(IR ir) {
    Instruction nextInstr;
    for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = nextInstr) {
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
          Operand val2 = IfCmp.getVal2(s);
          if (val2 instanceof RegisterOperand) {
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
        case FLOAT_2LONG_opcode:
          nextInstr = float_2long(s, ir);
          break;
        case DOUBLE_2INT_opcode:
          nextInstr = double_2int(s, ir);
          break;
        case DOUBLE_2LONG_opcode:
          nextInstr = double_2long(s, ir);
          break;
        default:
          nextInstr = s.nextInstructionInCodeOrder();
          break;
      }
    }
    DefUse.recomputeSpansBasicBlock(ir);
  }

  private static Instruction float_2int(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
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
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock nanBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanBB);
    BasicBlock maxintBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, maxintBB);
    BasicBlock nanTestBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanTestBB);
    BasicBlock f2iBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, f2iBB);

    // Move the maxintFloat value and the value into registers and compare and
    // branch if they are <= or unordered. NB we don't use a memory operand as
    // that would require 2 jccs
    RegisterOperand result = Unary.getResult(s);
    RegisterOperand value = Unary.getVal(s).asRegister();
    MemoryOperand maxint = BURS_Helpers.loadFromJTOC(Entrypoints.maxintFloatField.getOffset(), (byte)4);
    RegisterOperand maxintReg = ir.regpool.makeTempFloat();
    s.insertBefore(CPOS(s,MIR_Move.create(IA32_MOVSS, maxintReg, maxint)));
    MIR_Compare.mutate(s, IA32_UCOMISS, maxintReg.copyRO(), value);
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.LLE(),
        nanTestBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
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
        IA32ConditionOperand.PE(),
        nanBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
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

  private static Instruction float_2long(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 6 basic blocks (in code order)
    // 1: the current block that does a test to see if this is a regular f2l or
    //    branches to the maxint/NaN case
    // 2: a block to perform a regular f2l
    // 3: a block to test for NaN
    // 4: a block to perform give maxint
    // 5: a block to perform NaN
    // 6: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock nanBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanBB);
    BasicBlock maxintBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, maxintBB);
    BasicBlock nanTestBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanTestBB);
    BasicBlock f2lBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, f2lBB);

    // Move the maxlongFloat value and the value into x87 registers and compare and
    // branch if they are <= or unordered.
    RegisterOperand resultHi = Unary.getResult(s);
    resultHi.setType(TypeReference.Int);
    RegisterOperand resultLo = new RegisterOperand(ir.regpool.getSecondReg(resultHi.getRegister()),
        TypeReference.Int);
    RegisterOperand value = Unary.getVal(s).asRegister();
    RegisterOperand cw = ir.regpool.makeTempInt();
    MemoryOperand maxlong = BURS_Helpers.loadFromJTOC(Entrypoints.maxlongFloatField.getOffset(), (byte)4);
    RegisterOperand st0 = new RegisterOperand(ir.regpool.getPhysicalRegisterSet().getST0(),
        TypeReference.Float);
    RegisterOperand st1 = new RegisterOperand(ir.regpool.getPhysicalRegisterSet().getST1(),
        TypeReference.Float);
    int offset = -ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand slLo = new StackLocationOperand(true, offset, 4);
    StackLocationOperand slHi = new StackLocationOperand(true, offset+4, 4);
    StackLocationOperand sl = new StackLocationOperand(true, offset, 8);
    MemoryOperand scratchLo = new MemoryOperand(ir.regpool.makeTROp(), null, (byte)0,
        Entrypoints.scratchStorageField.getOffset(), (byte)4,
        new LocationOperand(Entrypoints.scratchStorageField), null);
    MemoryOperand scratchHi = new MemoryOperand(ir.regpool.makeTROp(), null, (byte)0,
        Entrypoints.scratchStorageField.getOffset().plus(4), (byte)4,
        new LocationOperand(Entrypoints.scratchStorageField), null);

    s.insertBefore(CPOS(s, MIR_Move.create(IA32_MOVSS, slLo, value)));
    s.insertBefore(CPOS(s, MIR_Move.create(IA32_FLD, st0, slLo.copy())));
    s.insertBefore(CPOS(s, MIR_Move.create(IA32_FLD, st0.copyRO(), maxlong)));
    MIR_Compare.mutate(s, IA32_FUCOMIP, st0.copyRO(), st1);
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.LLE(),
        nanTestBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    testBB.insertOut(f2lBB);
    testBB.insertOut(nanTestBB);

    // Convert float to long knowing that if the value is < min long the Intel
    // unspecified result is min long
    // TODO: this would be a lot simpler and faster with SSE3's FISTTP instruction
    f2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FNSTCW, scratchLo.copy())));
    f2lBB.appendInstruction(CPOS(s, MIR_Unary.create(IA32_MOVZX__W, cw, scratchLo.copy())));
    f2lBB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_OR, cw, IC(0xC00))));
    f2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, scratchHi, cw.copyRO())));
    f2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FLDCW, scratchHi.copy())));
    f2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_FISTP, sl, st0.copyRO())));
    f2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FLDCW, scratchLo.copy())));
    f2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, resultLo, slLo.copy())));
    f2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, resultHi, slHi)));
    f2lBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    f2lBB.insertOut(nextBB);

    // Did the compare find a NaN or a maximum integer?
    nanTestBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_FSTP, st0.copyRO(), st0.copyRO())));
    nanTestBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.PE(),
        nanBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    nanTestBB.insertOut(nanBB);
    nanTestBB.insertOut(maxintBB);

    // Value was >= max long
    maxintBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultLo.copyRO(),
        IC((int)Long.MAX_VALUE))));
    maxintBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultHi.copyRO(),
        IC((int)(Long.MAX_VALUE >>> 32)))));
    maxintBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    maxintBB.insertOut(nextBB);

    // In case of NaN result is 0
    nanBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultLo.copyRO(),
        IC(0))));
    nanBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultHi.copyRO(),
        IC(0))));
    nanBB.insertOut(nextBB);
    return nextInstr;
  }

  private static Instruction double_2int(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 6 basic blocks (in code order)
    // 1: the current block that does a test to see if this is a regular d2i or
    //    branches to the maxint/NaN case
    // 2: a block to perform a regular d2i
    // 3: a block to test for NaN
    // 4: a block to perform give maxint
    // 5: a block to perform NaN
    // 6: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock nanBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanBB);
    BasicBlock maxintBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, maxintBB);
    BasicBlock nanTestBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanTestBB);
    BasicBlock d2iBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, d2iBB);

    // Move the maxint value and the value into registers and compare and
    // branch if they are <= or unordered. NB we don't use a memory operand as
    // that would require 2 jccs
    RegisterOperand result = Unary.getResult(s);
    RegisterOperand value = Unary.getVal(s).asRegister();
    MemoryOperand maxint = BURS_Helpers.loadFromJTOC(Entrypoints.maxintField.getOffset(), (byte)8);
    RegisterOperand maxintReg = ir.regpool.makeTempFloat();
    s.insertBefore(CPOS(s,MIR_Move.create(IA32_MOVSD, maxintReg, maxint)));
    MIR_Compare.mutate(s, IA32_UCOMISD, maxintReg.copyRO(), value);
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.LLE(),
        nanTestBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    testBB.insertOut(d2iBB);
    testBB.insertOut(nanTestBB);

    // Convert float to int knowing that if the value is < min int the Intel
    // unspecified result is min int
    d2iBB.appendInstruction(CPOS(s, MIR_Unary.create(IA32_CVTTSD2SI, result, value.copy())));
    d2iBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    d2iBB.insertOut(nextBB);

    // Did the compare find a NaN or a maximum integer?
    nanTestBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.PE(),
        nanBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
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

  private static Instruction double_2long(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 6 basic blocks (in code order)
    // 1: the current block that does a test to see if this is a regular f2l or
    //    branches to the maxint/NaN case
    // 2: a block to perform a regular f2l
    // 3: a block to test for NaN
    // 4: a block to perform give maxint
    // 5: a block to perform NaN
    // 6: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock nanBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanBB);
    BasicBlock maxintBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, maxintBB);
    BasicBlock nanTestBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nanTestBB);
    BasicBlock d2lBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, d2lBB);

    // Move the maxlongFloat value and the value into x87 registers and compare and
    // branch if they are <= or unordered.
    RegisterOperand resultHi = Unary.getResult(s);
    resultHi.setType(TypeReference.Int);
    RegisterOperand resultLo = new RegisterOperand(ir.regpool.getSecondReg(resultHi.getRegister()),
        TypeReference.Int);
    RegisterOperand value = Unary.getVal(s).asRegister();
    RegisterOperand cw = ir.regpool.makeTempInt();
    MemoryOperand maxlong = BURS_Helpers.loadFromJTOC(Entrypoints.maxlongField.getOffset(), (byte)8);
    RegisterOperand st0 = new RegisterOperand(ir.regpool.getPhysicalRegisterSet().getST0(),
        TypeReference.Double);
    RegisterOperand st1 = new RegisterOperand(ir.regpool.getPhysicalRegisterSet().getST1(),
        TypeReference.Double);
    int offset = -ir.stackManager.allocateSpaceForConversion();
    StackLocationOperand slLo = new StackLocationOperand(true, offset, 4);
    StackLocationOperand slHi = new StackLocationOperand(true, offset+4, 4);
    StackLocationOperand sl = new StackLocationOperand(true, offset, 8);
    MemoryOperand scratchLo = new MemoryOperand(ir.regpool.makeTROp(), null, (byte)0,
        Entrypoints.scratchStorageField.getOffset(), (byte)4,
        new LocationOperand(Entrypoints.scratchStorageField), null);
    MemoryOperand scratchHi = new MemoryOperand(ir.regpool.makeTROp(), null, (byte)0,
        Entrypoints.scratchStorageField.getOffset().plus(4), (byte)4,
        new LocationOperand(Entrypoints.scratchStorageField), null);

    s.insertBefore(CPOS(s, MIR_Move.create(IA32_MOVSD, sl, value)));
    s.insertBefore(CPOS(s, MIR_Move.create(IA32_FLD, st0, sl.copy())));
    s.insertBefore(CPOS(s, MIR_Move.create(IA32_FLD, st0.copyRO(), maxlong)));
    MIR_Compare.mutate(s, IA32_FUCOMIP, st0.copyRO(), st1);
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.LLE(),
        nanTestBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    testBB.insertOut(d2lBB);
    testBB.insertOut(nanTestBB);

    // Convert double to long knowing that if the value is < min long the Intel
    // unspecified result is min long
    // TODO: this would be a lot simpler and faster with SSE3's FISTTP instruction
    d2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FNSTCW, scratchLo.copy())));
    d2lBB.appendInstruction(CPOS(s, MIR_Unary.create(IA32_MOVZX__W, cw, scratchLo.copy())));
    d2lBB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_OR, cw, IC(0xC00))));
    d2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, scratchHi, cw.copyRO())));
    d2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FLDCW, scratchHi.copy())));
    d2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_FISTP, sl.copy(), st0.copyRO())));
    d2lBB.appendInstruction(CPOS(s, MIR_UnaryNoRes.create(IA32_FLDCW, scratchLo.copy())));
    d2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, resultLo, slLo)));
    d2lBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV, resultHi, slHi)));
    d2lBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    d2lBB.insertOut(nextBB);

    // Did the compare find a NaN or a maximum integer?
    nanTestBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_FSTP, st0.copyRO(), st0.copyRO())));
    nanTestBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.PE(),
        nanBB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    nanTestBB.insertOut(nanBB);
    nanTestBB.insertOut(maxintBB);

    // Value was >= max long
    maxintBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultLo.copyRO(),
        IC((int)Long.MAX_VALUE))));
    maxintBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultHi.copyRO(),
        IC((int)(Long.MAX_VALUE >>> 32)))));
    maxintBB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    maxintBB.insertOut(nextBB);

    // In case of NaN result is 0
    nanBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultLo.copyRO(),
        IC(0))));
    nanBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        resultHi.copyRO(),
        IC(0))));
    nanBB.insertOut(nextBB);
    return nextInstr;
  }

  private static Instruction long_shl(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    Register lhsReg = Binary.getResult(s).getRegister();
    Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    Operand val1 = Binary.getVal1(s);
    Register rhsReg;
    Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().getRegister();
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(ecx, TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new RegisterOperand(ecx, TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(lowrhsReg, TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHL,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        IC(0))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg, TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg, TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHLD,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHL,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static Instruction long_shr(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    Register lhsReg = Binary.getResult(s).getRegister();
    Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    Operand val1 = Binary.getVal1(s);
    Register rhsReg;
    Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().getRegister();
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(ecx, TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new RegisterOperand(ecx, TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(rhsReg, TypeReference.Int))));
    if (lhsReg != rhsReg) {
      shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg, TypeReference.Int))));
    }
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new RegisterOperand(lhsReg, TypeReference.Int),
        IC(31))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg, TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg, TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHRD,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SAR,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static Instruction long_ushr(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block that does a test if the shift is > 32
    // 2: a block to perform a shift in the range 32 to 63
    // 3: a block to perform a shift in the range 0 to 31
    // 4: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock shift32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift32BB);
    BasicBlock shift64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, shift64BB);

    // Source registers
    Register lhsReg = Binary.getResult(s).getRegister();
    Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    Operand val1 = Binary.getVal1(s);
    Register rhsReg;
    Register lowrhsReg;
    if (val1.isRegister()) {
      rhsReg = val1.asRegister().getRegister();
      lowrhsReg = ir.regpool.getSecondReg(rhsReg);
    } else {
      // shift is of a constant so set up registers
      int low = val1.asLongConstant().lower32();
      int high = val1.asLongConstant().upper32();

      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          IC(low))));
      testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          IC(high))));
      rhsReg = lhsReg;
      lowrhsReg = lowlhsReg;
    }

    // ecx = shift amount
    Register ecx = ir.regpool.getPhysicalRegisterSet().getECX();
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(ecx, TypeReference.Int),
        Binary.getVal2(s))));

    // Determine shift of 32 to 63 or 0 to 31
    testBB.appendInstruction(CPOS(s, MIR_Test.create(IA32_TEST,
        new RegisterOperand(ecx, TypeReference.Int),
        IC(32))));

    // if (ecx & 32 == 0) goto shift32BB
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.EQ(),
        shift32BB.makeJumpTarget(),
        BranchProfileOperand.likely())));

    testBB.insertOut(shift32BB);
    testBB.insertOut(shift64BB); // fall-through

    // Perform shift in the range 32 to 63
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(rhsReg, TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHR,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift64BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lhsReg, TypeReference.Int),
        IC(0))));

    shift64BB.appendInstruction(CPOS(s, MIR_Branch.create(IA32_JMP,
        nextBB.makeJumpTarget())));
    shift64BB.insertOut(nextBB);

    // Perform shift in the range 0 to 31
    if (lhsReg != rhsReg) {
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lhsReg, TypeReference.Int),
          new RegisterOperand(rhsReg, TypeReference.Int))));
      shift32BB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
          new RegisterOperand(lowlhsReg, TypeReference.Int),
          new RegisterOperand(lowrhsReg, TypeReference.Int))));
    }
    shift32BB.appendInstruction(CPOS(s, MIR_DoubleShift.create(IA32_SHRD,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));
    shift32BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_SHR,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(ecx, TypeReference.Int))));

    shift32BB.insertOut(nextBB);

    s.remove();
    return nextInstr;
  }

  private static Instruction long_mul(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    while(Label.conforms(nextInstr)||BBend.conforms(nextInstr)) {
      nextInstr = nextInstr.nextInstructionInCodeOrder();
    }
    // we need 4 basic blocks
    // 1: the current block and a test for 32bit or 64bit multiply
    // 2: 32bit multiply block
    // 3: 64bit multiply block
    // 4: the next basic block
    BasicBlock testBB = s.getBasicBlock();
    BasicBlock nextBB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, nextBB);
    BasicBlock mul64BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, mul64BB);
    BasicBlock mul32BB = testBB.splitNodeAt(s,ir);
    ir.cfg.linkInCodeOrder(testBB, mul32BB);

    // Source registers
    Register lhsReg = Binary.getResult(s).getRegister();
    Register lowlhsReg = ir.regpool.getSecondReg(lhsReg);
    Register rhsReg1 = Binary.getVal1(s).asRegister().getRegister();
    Register lowrhsReg1 = ir.regpool.getSecondReg(rhsReg1);
    Register rhsReg2 = Binary.getVal2(s).asRegister().getRegister();
    Register lowrhsReg2 = ir.regpool.getSecondReg(rhsReg2);

    // Working registers
    Register edx = ir.regpool.getPhysicalRegisterSet().getEDX();
    Register eax = ir.regpool.getPhysicalRegisterSet().getEAX();
    Register tmp = ir.regpool.getInteger();

    // The general form of the multiply is
    // (a,b) * (c,d) = (l(a imul d)+l(b imul c)+u(b mul d), l(b mul d))

    // Determine whether we need a 32bit or 64bit multiply
    // edx, flags = a | c
    // edx = d
    // eax = b
    // if ((a | c) != 0) goto mul64BB
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(rhsReg2, TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(tmp, TypeReference.Int),
        new RegisterOperand(rhsReg1, TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_OR,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(tmp, TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(lowrhsReg1, TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(eax, TypeReference.Int),
        new RegisterOperand(lowrhsReg2, TypeReference.Int))));
    testBB.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
        IA32ConditionOperand.NE(),
        mul64BB.makeJumpTarget(),
        BranchProfileOperand.unlikely())));
    testBB.insertOut(mul64BB);
    testBB.insertOut(mul32BB);

    // multiply 32: on entry EAX = d, EDX = b, tmp = a
    // edx:eax = b * d
    mul32BB.appendInstruction(CPOS(s, MIR_Multiply.create(IA32_MUL,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(eax, TypeReference.Int),
        new RegisterOperand(edx, TypeReference.Int))));
    mul32BB.appendInstruction(MIR_Branch.create(IA32_JMP, nextBB.makeJumpTarget()));
    mul32BB.insertOut(nextBB);

    // multiply 64: on entry EAX = d, EDX = b, tmp = a
    // edx = b imul c
    // tmp = a imul d
    // tmp1 = (a imul d) + (b imul c)
    // edx:eax = b * d
    // edx = u(b mul d) + l(a imul d) + l(b imul c)
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(rhsReg2, TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_IMUL2,
        new RegisterOperand(tmp, TypeReference.Int),
        new RegisterOperand(eax, TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
        new RegisterOperand(tmp, TypeReference.Int),
        new RegisterOperand(edx, TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_Multiply.create(IA32_MUL,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(eax, TypeReference.Int),
        new RegisterOperand(lowrhsReg1, TypeReference.Int))));
    mul64BB.appendInstruction(CPOS(s, MIR_BinaryAcc.create(IA32_ADD,
        new RegisterOperand(edx, TypeReference.Int),
        new RegisterOperand(tmp, TypeReference.Int))));
    mul64BB.insertOut(nextBB);

    // move result from edx:eax to lhsReg:lowlhsReg
    nextBB.prependInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lhsReg, TypeReference.Int),
        new RegisterOperand(edx, TypeReference.Int))));
    nextBB.prependInstruction(CPOS(s, MIR_Move.create(IA32_MOV,
        new RegisterOperand(lowlhsReg, TypeReference.Int),
        new RegisterOperand(eax, TypeReference.Int))));
    s.remove();
    return nextInstr;
  }

  private static Instruction long_ifcmp(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    ConditionOperand cond = IfCmp.getCond(s);
    Register xh = ((RegisterOperand) IfCmp.getVal1(s)).getRegister();
    Register xl = ir.regpool.getSecondReg(xh);
    RegisterOperand yh = (RegisterOperand) IfCmp.getClearVal2(s);
    yh.setType(TypeReference.Int);
    RegisterOperand yl = new RegisterOperand(ir.regpool.getSecondReg(yh.getRegister()), TypeReference.Int);
    basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
    return nextInstr;
  }

  private static Instruction long_ifcmp_imm(Instruction s, IR ir) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    ConditionOperand cond = IfCmp.getCond(s);
    Register xh = ((RegisterOperand) IfCmp.getVal1(s)).getRegister();
    Register xl = ir.regpool.getSecondReg(xh);
    LongConstantOperand rhs = (LongConstantOperand) IfCmp.getVal2(s);
    int low = rhs.lower32();
    int high = rhs.upper32();
    IntConstantOperand yh = IC(high);
    IntConstantOperand yl = IC(low);

    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      Register th = ir.regpool.getInteger();
      Register tl = ir.regpool.getInteger();
      if (high == 0) {
        if (low == 0) { // 0,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(xl, TypeReference.Int)));
        } else if (low == -1) { // 0,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(tl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(tl, TypeReference.Int),
                                              new RegisterOperand(xh, TypeReference.Int)));
        } else { // 0,*
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(tl, TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(tl, TypeReference.Int),
                                              new RegisterOperand(xh, TypeReference.Int)));
        }
      } else if (high == -1) {
        if (low == 0) { // -1,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(th, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(xl, TypeReference.Int)));
        } else if (low == -1) { // -1,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(th, TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(tl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(tl, TypeReference.Int)));
        } else { // -1,*
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(th, TypeReference.Int)));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(tl, TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(tl, TypeReference.Int)));
        }
      } else {
        if (low == 0) { // *,0
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(th, TypeReference.Int), yh));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(xl, TypeReference.Int)));
        } else if (low == -1) { // *,-1
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(th, TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_UnaryAcc.create(IA32_NOT, new RegisterOperand(tl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(tl, TypeReference.Int)));
        } else { // neither high nor low is special
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(th, TypeReference.Int),
                                         new RegisterOperand(xh, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(th, TypeReference.Int), yh));
          s.insertBefore(MIR_Move.create(IA32_MOV,
                                         new RegisterOperand(tl, TypeReference.Int),
                                         new RegisterOperand(xl, TypeReference.Int)));
          s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, new RegisterOperand(tl, TypeReference.Int), yl));
          s.insertBefore(MIR_BinaryAcc.create(IA32_OR,
                                              new RegisterOperand(th, TypeReference.Int),
                                              new RegisterOperand(tl, TypeReference.Int)));
        }
      }
      MIR_CondBranch.mutate(s,
                            IA32_JCC,
                            new IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
      return nextInstr;
    } else {
      // pick up a few special cases where the sign of xh is sufficient
      if (rhs.value == 0L) {
        if (cond.isLESS()) {
          // xh < 0 implies true
          s.insertBefore(MIR_Compare.create(IA32_CMP, new RegisterOperand(xh, TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                IA32ConditionOperand.LT(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new RegisterOperand(xh, TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      } else if (rhs.value == -1L) {
        if (cond.isLESS_EQUAL()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new RegisterOperand(xh, TypeReference.Int), IC(-1)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                IA32ConditionOperand.LE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        } else if (cond.isGREATER()) {
          s.insertBefore(MIR_Compare.create(IA32_CMP, new RegisterOperand(xh, TypeReference.Int), IC(0)));
          MIR_CondBranch.mutate(s,
                                IA32_JCC,
                                IA32ConditionOperand.GE(),
                                IfCmp.getTarget(s),
                                IfCmp.getBranchProfile(s));
          return nextInstr;
        }
      }

      basic_long_ifcmp(s, ir, cond, xh, xl, yh, yl);
      return nextInstr;
    }
  }

  private static void basic_long_ifcmp(Instruction s, IR ir, ConditionOperand cond, Register xh,
                                       Register xl, Operand yh, Operand yl) {
    if (cond.isEQUAL() || cond.isNOT_EQUAL()) {
      RegisterOperand th = ir.regpool.makeTempInt();
      RegisterOperand tl = ir.regpool.makeTempInt();
      // tricky... ((xh^yh)|(xl^yl) == 0) <==> (lhll == rhrl)!!
      s.insertBefore(MIR_Move.create(IA32_MOV, th, new RegisterOperand(xh, TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, th.copyD2D(), yh));
      s.insertBefore(MIR_Move.create(IA32_MOV, tl, new RegisterOperand(xl, TypeReference.Int)));
      s.insertBefore(MIR_BinaryAcc.create(IA32_XOR, tl.copyD2D(), yl));
      s.insertBefore(MIR_BinaryAcc.create(IA32_OR, th.copyD2D(), tl.copyD2U()));
      MIR_CondBranch.mutate(s,
                            IA32_JCC,
                            new IA32ConditionOperand(cond),
                            IfCmp.getTarget(s),
                            IfCmp.getBranchProfile(s));
    } else {
      // Do the naive thing and generate multiple compare/branch implementation.
      IA32ConditionOperand cond1;
      IA32ConditionOperand cond2;
      IA32ConditionOperand cond3;
      if (cond.isLESS()) {
        cond1 = IA32ConditionOperand.LT();
        cond2 = IA32ConditionOperand.GT();
        cond3 = IA32ConditionOperand.LLT();
      } else if (cond.isGREATER()) {
        cond1 = IA32ConditionOperand.GT();
        cond2 = IA32ConditionOperand.LT();
        cond3 = IA32ConditionOperand.LGT();
      } else if (cond.isLESS_EQUAL()) {
        cond1 = IA32ConditionOperand.LT();
        cond2 = IA32ConditionOperand.GT();
        cond3 = IA32ConditionOperand.LLE();
      } else if (cond.isGREATER_EQUAL()) {
        cond1 = IA32ConditionOperand.GT();
        cond2 = IA32ConditionOperand.LT();
        cond3 = IA32ConditionOperand.LGE();
      } else {
        // I don't think we use the unsigned compares for longs,
        // so defer actually implementing them until we find a test case. --dave
        cond1 = cond2 = cond3 = null;
        OptimizingCompilerException.TODO();
      }

      BasicBlock myBlock = s.getBasicBlock();
      BasicBlock test2Block = myBlock.createSubBlock(s.bcIndex, ir, 0.25f);
      BasicBlock falseBlock = myBlock.splitNodeAt(s, ir);
      BasicBlock trueBlock = IfCmp.getTarget(s).target.getBasicBlock();

      falseBlock.recomputeNormalOut(ir);
      myBlock.insertOut(test2Block);
      myBlock.insertOut(falseBlock);
      myBlock.insertOut(trueBlock);
      test2Block.insertOut(falseBlock);
      test2Block.insertOut(trueBlock);
      ir.cfg.linkInCodeOrder(myBlock, test2Block);
      ir.cfg.linkInCodeOrder(test2Block, falseBlock);

      s.remove();

      myBlock.appendInstruction(CPOS(s, MIR_Compare.create(IA32_CMP, new RegisterOperand(xh, TypeReference.Int), yh)));
      myBlock.appendInstruction(CPOS(s, MIR_CondBranch2.create(IA32_JCC2,
                                                       cond1,
                                                       trueBlock.makeJumpTarget(),
                                                       new BranchProfileOperand(),
                                                       cond2,
                                                       falseBlock.makeJumpTarget(),
                                                       new BranchProfileOperand())));
      test2Block.appendInstruction(CPOS(s, MIR_Compare.create(IA32_CMP, new RegisterOperand(xl, TypeReference.Int), yl)));
      test2Block.appendInstruction(CPOS(s, MIR_CondBranch.create(IA32_JCC,
                                                         cond3,
                                                         trueBlock.makeJumpTarget(),
                                                         new BranchProfileOperand())));
    }
  }

  // the fcmoi/fcmoip was generated by burs
  // we do the rest of the expansion here because in some
  // cases we must remove a trailing goto, and we
  // can't do that in burs!
  private static Instruction fp_ifcmp(Instruction s) {
    Instruction nextInstr = s.nextInstructionInCodeOrder();
    BranchOperand testFailed;
    BasicBlock bb = s.getBasicBlock();
    Instruction lastInstr = bb.lastRealInstruction();
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
    Instruction fcomi = s.prevInstructionInCodeOrder();
    Operand val1 = MIR_Compare.getVal1(fcomi);
    Operand val2 = MIR_Compare.getVal2(fcomi);
    ConditionOperand c = IfCmp.getCond(s);
    BranchOperand target = IfCmp.getTarget(s);
    BranchProfileOperand branchProfile = IfCmp.getBranchProfile(s);

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
      case ConditionOperand.CMPL_EQUAL:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        // Check whether val1 and val2 operands are the same
        if (!val1.similar(val2)) {
          s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                                IA32ConditionOperand.PE(),
                                                // PF == 1
                                                testFailed,
                                                new BranchProfileOperand(0f),
                                                IA32ConditionOperand.EQ(),
                                                // ZF == 1
                                                target,
                                                branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, (BranchOperand) (testFailed.copy())));
        } else {
          // As val1 == val2 result of compare must be == or UNORDERED
          s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.PO(),  // PF == 0
                                               target, branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        }
        break;
      case ConditionOperand.CMPL_GREATER:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.LGT(), // CF == 0 and ZF == 0
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case ConditionOperand.CMPG_LESS:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              IA32ConditionOperand.PE(),
                                              // PF == 1
                                              testFailed,
                                              new BranchProfileOperand(0f),
                                              IA32ConditionOperand.LLT(),
                                              // CF == 1
                                              target,
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, (BranchOperand) (testFailed.copy())));
        break;
      case ConditionOperand.CMPL_GREATER_EQUAL:
        if (VM.VerifyAssertions) VM._assert(!c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.LGE(), // CF == 0
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case ConditionOperand.CMPG_LESS_EQUAL:
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              IA32ConditionOperand.PE(),
                                              // PF == 1
                                              testFailed,
                                              new BranchProfileOperand(0f),
                                              IA32ConditionOperand.LGT(),
                                              // ZF == 0 and CF == 0
                                              (BranchOperand) (testFailed.copy()),
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, target));
        break;
        // Branches that WILL be taken after unordered comparison
        // (i.e. UNORDERED is a goto to target)
      case ConditionOperand.CMPL_NOT_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        // Check whether val1 and val2 operands are the same
        if (!val1.similar(val2)) {
          s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                                IA32ConditionOperand.PE(),
                                                // PF == 1
                                                target,
                                                new BranchProfileOperand(0f),
                                                IA32ConditionOperand.NE(),
                                                // ZF == 0
                                                (BranchOperand) (target.copy()),
                                                branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        } else {
          // As val1 == val2 result of compare must be == or UNORDERED
          s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.PE(),  // PF == 1
                                               target, branchProfile));
          s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        }
        break;
      case ConditionOperand.CMPL_LESS:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.LLT(),   // CF == 1
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case ConditionOperand.CMPG_GREATER_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              IA32ConditionOperand.PE(),
                                              // PF == 1
                                              target,
                                              new BranchProfileOperand(0f),
                                              IA32ConditionOperand.LLT(),
                                              // CF == 1
                                              testFailed,
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, (BranchOperand) (target.copy())));
        break;
      case ConditionOperand.CMPG_GREATER:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch2.create(IA32_JCC2,
                                              IA32ConditionOperand.PE(),
                                              // PF == 1
                                              target,
                                              new BranchProfileOperand(0f),
                                              IA32ConditionOperand.LGT(),
                                              // ZF == 0 and CF == 0
                                              (BranchOperand) (target.copy()),
                                              branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      case ConditionOperand.CMPL_LESS_EQUAL:
        if (VM.VerifyAssertions) VM._assert(c.branchIfUnordered());
        s.insertBefore(MIR_CondBranch.create(IA32_JCC, IA32ConditionOperand.LLE(), // CF == 1 or ZF == 1
                                             target, branchProfile));
        s.insertBefore(MIR_Branch.create(IA32_JMP, testFailed));
        break;
      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    s.remove();
    return nextInstr;
  }
}

