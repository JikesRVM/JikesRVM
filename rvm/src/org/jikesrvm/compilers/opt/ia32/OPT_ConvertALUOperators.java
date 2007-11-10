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

import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_DefUse;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.OPT_Simplifier;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.ia32.VM_ArchConstants;

/**
 * <ul>
 * <li> Convert instructions with 3-operand binary ALU operators to use
 *      2-operand ALU operators.
 * <li> Convert instructions with 2-operand unary ALU operators to use
 *      1-operand ALU operators.
 * </ul>
 *
 * <pre>
 * In the most general case, we must do the following:
 *
 *  op r100 = r200, r300      =====>    move r100 = r200
 *                                      op   r100 <-- r300
 *
 * but there are several easy cases where we can avoid the move
 *
 *  op r100 = r100, r300      =====>    op   r100 <-- r300
 *
 *  op r100 = r200, r100      =====>    op   r100 <-- r200
 *  (if op is commutative)
 *
 * but, we must be careful in this case. If r100 spans a basic block,
 * then we are better doing the following (since it will break the
 * BURS expression tree _after_ op).
 *
 *  op r100 = r200, r100      =====>    move rtemp = r200
 *  (if op is non commutative)          op   rtemp <-- r100
 *                                      move r100  = rtemp
 *
 * We also keep our eyes open for the special (somewhat common) case
 * of one of the uses being the last use of a temporary.  When this happens
 * we can sometimes avoid inserting a move at all. When this happens, we
 * rewrite:
 *
 *  op r100 = r200, r300     =====>    op r200 <-- r300
 * and replace all uses of r100 with r200.
 *
 * We aren't doing a full live analysis, but the following conditions
 * covers the cases where it is critical to get this right:
 *  (a) r100 is ssa
 *  (b) r100 does not span a basic block
 *  (c) r200 does not span a basic block
 *  (d) this instruction is the last use of r200
 *  (e) r200 is ssa
 * These conditions are designed to be cheap to verify and
 * cover those cases where it is advantegous from BURS's perspective to
 * coalesce the registers to avoid the move instruction.
 *
 * If we are in the following very similar case:
 *  op r100 = r200, r300     =====>      op r200 <-- r300
 *                                           move r100 = r200
 *  (1) r200 does not span a basic block
 *  (2) this instruction is the last use of r200
 * then we want the move instruction here (but after op), because
 * merging registers r100 and r200 would force BURS to break its
 * exprssion trep _before_ op since r200 would now span a basic block
 * (since r100 spans a basic block).
 * We depend on the register allocator to later coalesce r100 and r200,
 * since they are not simultaneously live.
 * Ditto (5) and (6) on r300 if op is commutative and r200 doesn't work out.
 *
 * </pre>
 */
public class OPT_ConvertALUOperators extends OPT_CompilerPhase implements OPT_Operators, VM_ArchConstants {

  private static final boolean OPTIMIZE = true;

  @Override
  public final String getName() { return "ConvertALUOps"; }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  @Override
  public final void perform(OPT_IR ir) {
    // Calling OPT_Simplifier.simplify ensures that the instruction is
    // in normalized form. This reduces the number of cases we have to
    // worry about (and does last minute constant folding on the off
    // chance we've missed an opportunity...)
    // BURS assumes that this has been done, so we must do it even if
    // OPTIMIZE is false.
    for (OPT_InstructionEnumeration instrs = ir.forwardInstrEnumerator(); instrs.hasMoreElements();) {
      OPT_Instruction s = instrs.next();
      OPT_Simplifier.simplify(ir.regpool, s);
    }

    if (OPTIMIZE) {
      // Compute simple ssa, u/d chains, spansBasicBlock
      // to catch some additional cases where we don't have to insert moves
      OPT_DefUse.computeDU(ir);
      OPT_DefUse.recomputeSSA(ir);
      OPT_DefUse.recomputeSpansBasicBlock(ir);
      for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
        markDead(reg);
      }
    }

    // Reverse pass over instructions supports simple live analysis.
    for (OPT_Instruction next, s = ir.lastInstructionInCodeOrder(); s != null; s = next) {
      next = s.prevInstructionInCodeOrder();

      switch (s.getOpcode()) {
      case REF_ADD_opcode:
        s.operator = INT_ADD;
        break;
      case REF_SUB_opcode:
        s.operator = INT_SUB;
        break;
      case REF_NEG_opcode:
        s.operator = INT_NEG;
        break;
      case REF_NOT_opcode:
        s.operator = INT_NOT;
        break;
      case REF_AND_opcode:
        s.operator = INT_AND;
        break;
      case REF_OR_opcode:
        s.operator = INT_OR;
        break;
      case REF_XOR_opcode:
        s.operator = INT_XOR;
        break;
      case REF_SHL_opcode:
        s.operator = INT_SHL;
        break;
      case REF_SHR_opcode:
        s.operator = INT_SHR;
        break;
      case REF_USHR_opcode:
        s.operator = INT_USHR;
        break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case BOOLEAN_CMP_ADDR_opcode:
        s.operator = BOOLEAN_CMP_INT;
        break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case FLOAT_ADD_opcode:
        if (!SSE2_FULL)
          s.operator = FP_ADD;
        break;
      case DOUBLE_ADD_opcode:
        if (!SSE2_FULL)
          s.operator = FP_ADD;
        break;
      case FLOAT_SUB_opcode:
        if (!SSE2_FULL)
          s.operator = FP_SUB;
        break;
      case DOUBLE_SUB_opcode:
        if (!SSE2_FULL)
          s.operator = FP_SUB;
        break;
      case FLOAT_MUL_opcode:
        if (!SSE2_FULL)
          s.operator = FP_MUL;
        break;
      case DOUBLE_MUL_opcode:
        if (!SSE2_FULL)
          s.operator = FP_MUL;
        break;
      case FLOAT_DIV_opcode:
        if (!SSE2_FULL)
          s.operator = FP_DIV;
        break;
      case DOUBLE_DIV_opcode:
        if (!SSE2_FULL)
          s.operator = FP_DIV;
        break;
      case FLOAT_REM_opcode:
        if (!SSE2_FULL)
          s.operator = FP_REM;
        break;
      case DOUBLE_REM_opcode:
        if (!SSE2_FULL)
          s.operator = FP_REM;
        break;
      case FLOAT_NEG_opcode:
        if (!SSE2_FULL)
          s.operator = FP_NEG;
        break;
      case DOUBLE_NEG_opcode:
        if (!SSE2_FULL)
          s.operator = FP_NEG;
        break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case INT_COND_MOVE_opcode:
      case REF_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_CMOV : (CondMove.getVal1(s).isLong() ? LCMP_CMOV : CMP_CMOV);
        break;
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_FCMOV : CMP_FCMOV;
        break;
      case LONG_COND_MOVE_opcode:
        OPT_OptimizingCompilerException.TODO();
        break;
      case GUARD_COND_MOVE_opcode:
        OPT_OptimizingCompilerException.TODO();
        break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case INT_2FLOAT_opcode:
        if (!SSE2_FULL)
          s.operator = INT_2FP;
        break;
      case INT_2DOUBLE_opcode:
        if (!SSE2_FULL)
          s.operator = INT_2FP;
        break;
      case LONG_2FLOAT_opcode:
        if (!SSE2_FULL)
          s.operator = LONG_2FP;
        break;
      case LONG_2DOUBLE_opcode:
        if (!SSE2_FULL)
          s.operator = LONG_2FP;
        break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case REF_LOAD_opcode:
        s.operator = INT_LOAD;
        break;
      case REF_STORE_opcode:
        s.operator = INT_STORE;
        break;
      case REF_ALOAD_opcode:
        s.operator = INT_ALOAD;
        break;
      case REF_ASTORE_opcode:
        s.operator = INT_ASTORE;
        break;
      case REF_MOVE_opcode:
        s.operator = INT_MOVE;
        break;
      case REF_IFCMP_opcode:
        s.operator = INT_IFCMP;
        break;
      case ATTEMPT_ADDR_opcode:
        s.operator = ATTEMPT_INT;
        break;
      case PREPARE_ADDR_opcode:
        s.operator = PREPARE_INT;
        break;
      case INT_2ADDRSigExt_opcode:
        s.operator = INT_MOVE;
        break;
      case INT_2ADDRZerExt_opcode:
        s.operator = INT_MOVE;
        break;
      case ADDR_2INT_opcode:
        s.operator = INT_MOVE;
        break;
      case ADDR_2LONG_opcode:
        s.operator = INT_2LONG;
        break;
      }

      if (OPTIMIZE) {
        // update liveness
        for (OPT_OperandEnumeration defs = s.getPureDefs(); defs.hasMoreElements();) {
          OPT_Operand op = defs.next();
          if (op.isRegister()) {
            markDead(op.asRegister().getRegister());
          }
        }
        for (OPT_OperandEnumeration uses = s.getUses(); // includes def/uses
             uses.hasMoreElements();) {
          OPT_Operand op = uses.next();
          if (op.isRegister()) {
            markLive(op.asRegister().getRegister());
          }
        }
      }
    }
  }

  // Use the scratch field of the register to record
  // dead/live for local live analysis.
  private static void markDead(OPT_Register r) {
    r.scratch = 0;
  }

  private static void markLive(OPT_Register r) {
    r.scratch = 1;
  }

  @SuppressWarnings("unused")
  // completes the set
  private static boolean isLive(OPT_Register r) {
    return r.scratch == 1;
  }
}
