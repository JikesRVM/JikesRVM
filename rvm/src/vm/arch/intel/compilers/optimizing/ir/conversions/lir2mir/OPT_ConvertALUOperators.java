/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

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
 * @author Dave Grove
 */
final class OPT_ConvertALUOperators extends OPT_CompilerPhase 
  implements OPT_Operators {

  private static final boolean OPTIMIZE = true;

  public final String getName() { return "ConvertALUOps"; }
  public final OPT_CompilerPhase newExecution(OPT_IR ir) { return this; }

  public final void perform(OPT_IR ir) { 
    // Calling OPT_Simplifier.simplify ensures that the instruction is 
    // in normalized form. This reduces the number of cases we have to 
    // worry about (and does last minute constant folding on the off 
    // chance we've missed an opportunity...)
    // BURS assumes that this has been done, so we must do it even if
    // OPTIMIZE is false.
    for (OPT_InstructionEnumeration instrs = ir.forwardInstrEnumerator();
         instrs.hasMoreElements();) {
      OPT_Instruction s = instrs.next(); 
      OPT_Simplifier.simplify(s);
    }

    if (OPTIMIZE) {
      // Compute simple ssa, u/d chains, spansBasicBlock
      // to catch some additional cases where we don't have to insert moves
      OPT_DefUse.computeDU(ir);
      OPT_DefUse.recomputeSSA(ir);
      OPT_DefUse.recomputeSpansBasicBlock(ir);
      for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); 
           reg != null; 
           reg = reg.getNext()) {
        markDead(reg);
      }
    }

    // Reverse pass over instructions supports simple live analysis.
    for (OPT_Instruction next, s = ir.lastInstructionInCodeOrder(); 
         s != null; 
         s = next) {
      next = s.prevInstructionInCodeOrder();
      
      switch(s.getOpcode()) {
      case BOOLEAN_NOT_opcode: unary(s, BOOLEAN_NOT_ACC, ir); break;

      case REF_ADD_opcode: commutative(s, INT_ADD_ACC, ir); break;
      case INT_ADD_opcode: commutative(s, INT_ADD_ACC, ir); break;
      case REF_SUB_opcode: noncommutative(s, INT_SUB_ACC, ir); break;
      case INT_SUB_opcode: noncommutative(s, INT_SUB_ACC, ir); break;
      case INT_MUL_opcode: commutative(s, INT_MUL_ACC, ir); break;
      case REF_SHL_opcode: noncommutative(s, INT_SHL_ACC, ir); break;
      case INT_SHL_opcode: noncommutative(s, INT_SHL_ACC, ir); break;
      case REF_SHR_opcode: noncommutative(s, INT_SHR_ACC, ir); break;
      case INT_SHR_opcode: noncommutative(s, INT_SHR_ACC, ir); break;
      case REF_USHR_opcode: noncommutative(s, INT_USHR_ACC, ir); break;
      case INT_USHR_opcode: noncommutative(s, INT_USHR_ACC, ir); break;
      case REF_AND_opcode: commutative(s, INT_AND_ACC, ir); break;
      case INT_AND_opcode: commutative(s, INT_AND_ACC, ir); break;
      case REF_OR_opcode: commutative(s, INT_OR_ACC, ir); break;
      case INT_OR_opcode: commutative(s, INT_OR_ACC, ir); break;
      case REF_XOR_opcode: commutative(s, INT_XOR_ACC, ir); break;
      case INT_XOR_opcode: commutative(s, INT_XOR_ACC, ir); break;
      case INT_NEG_opcode: unary(s, INT_NEG_ACC, ir); break;
      case REF_NOT_opcode: unary(s, INT_NOT_ACC, ir); break;
      case INT_NOT_opcode: unary(s, INT_NOT_ACC, ir); break;

      case LONG_ADD_opcode: commutative(s, LONG_ADD_ACC, ir); break;
      case LONG_SUB_opcode: noncommutative(s, LONG_SUB_ACC, ir); break;
      case LONG_MUL_opcode: commutative(s, LONG_MUL_ACC, ir); break;
      case LONG_SHL_opcode: noncommutative(s, LONG_SHL_ACC, ir); break;
      case LONG_SHR_opcode: noncommutative(s, LONG_SHR_ACC, ir); break;
      case LONG_USHR_opcode: noncommutative(s, LONG_USHR_ACC, ir); break;
      case LONG_AND_opcode: commutative(s, LONG_AND_ACC, ir); break;
      case LONG_OR_opcode: commutative(s, LONG_OR_ACC, ir); break;
      case LONG_XOR_opcode: commutative(s, LONG_XOR_ACC, ir); break;
      case LONG_NEG_opcode: unary(s, LONG_NEG_ACC, ir); break;
      case LONG_NOT_opcode: unary(s, LONG_NOT_ACC, ir); break;
      
		// BURS doesn't really care, so consolidate to reduce rule space
      case BOOLEAN_CMP_ADDR_opcode: s.operator = BOOLEAN_CMP_INT; break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case FLOAT_ADD_opcode: s.operator = FP_ADD; break;
      case DOUBLE_ADD_opcode: s.operator = FP_ADD; break;
      case FLOAT_SUB_opcode: s.operator = FP_SUB; break;
      case DOUBLE_SUB_opcode: s.operator = FP_SUB; break;
      case FLOAT_MUL_opcode: s.operator = FP_MUL; break;
      case DOUBLE_MUL_opcode: s.operator = FP_MUL; break;
      case FLOAT_DIV_opcode: s.operator = FP_DIV; break;
      case DOUBLE_DIV_opcode: s.operator = FP_DIV; break; 
      case FLOAT_REM_opcode: s.operator = FP_REM; break;
      case DOUBLE_REM_opcode: s.operator = FP_REM; break;
      case FLOAT_NEG_opcode: s.operator = FP_NEG; break;
      case DOUBLE_NEG_opcode: s.operator = FP_NEG; break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case INT_COND_MOVE_opcode: 
      case REF_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_CMOV : CMP_CMOV;
        break;
      case FLOAT_COND_MOVE_opcode:
      case DOUBLE_COND_MOVE_opcode:
        s.operator = CondMove.getCond(s).isFLOATINGPOINT() ? FCMP_FCMOV : CMP_FCMOV;
        break;
      case LONG_COND_MOVE_opcode: OPT_OptimizingCompilerException.TODO(); break;
      case GUARD_COND_MOVE_opcode: OPT_OptimizingCompilerException.TODO(); break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case INT_2FLOAT_opcode: s.operator = INT_2FP; break;
      case INT_2DOUBLE_opcode: s.operator = INT_2FP; break;
      case LONG_2FLOAT_opcode: s.operator = LONG_2FP; break;
      case LONG_2DOUBLE_opcode: s.operator = LONG_2FP; break;

      // BURS doesn't really care, so consolidate to reduce rule space
      case REF_LOAD_opcode: s.operator = INT_LOAD; break;
      case REF_STORE_opcode: s.operator = INT_STORE; break;
      case REF_ALOAD_opcode: s.operator = INT_ALOAD; break;
      case REF_ASTORE_opcode: s.operator = INT_ASTORE; break;
      case REF_MOVE_opcode: s.operator = INT_MOVE; break;
      case REF_IFCMP_opcode: s.operator = INT_IFCMP; break;
      case ATTEMPT_ADDR_opcode: s.operator = ATTEMPT_INT; break;
      case PREPARE_ADDR_opcode: s.operator = PREPARE_INT; break;
      case INT_2ADDRSigExt_opcode: s.operator = INT_MOVE; break;
      case INT_2ADDRZerExt_opcode: s.operator = INT_MOVE; break;
      case ADDR_2INT_opcode: s.operator = INT_MOVE; break;
      case ADDR_2LONG_opcode: s.operator = INT_2LONG; break;
		}

      if (OPTIMIZE) {
        // update liveness 
        for (OPT_OperandEnumeration defs = s.getPureDefs();
             defs.hasMoreElements();) {
          OPT_Operand op = defs.next();
          if (op.isRegister()) {
            markDead(op.asRegister().register);
          }
        }
        for (OPT_OperandEnumeration uses = s.getUses(); // includes def/uses
             uses.hasMoreElements();) {
          OPT_Operand op = uses.next();
          if (op.isRegister()) {
            markLive(op.asRegister().register);
          }
        }
      }
    }
  }

  private void commutative(OPT_Instruction s, OPT_Operator opCode, OPT_IR ir) {
    OPT_RegisterOperand result = Binary.getClearResult(s);
    OPT_Operand op1 = Binary.getClearVal1(s);
    OPT_Operand op2 = Binary.getClearVal2(s);

    // Handle the easy cases of avoiding useless moves.
    if (result.similar(op1)) {
      OPT_DefUse.removeUse(op1.asRegister());
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(result);
      BinaryAcc.mutate(s, opCode, result, op2);
      return;
    }
    if (result.similar(op2)) {
      OPT_DefUse.removeUse(op2.asRegister());
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(result);
      BinaryAcc.mutate(s, opCode, result, op1);
      return;
    }

    // attempt to detect additional cases using simple liveness and DU info
    if (OPTIMIZE) {
      if (op1.isRegister()) {
        OPT_RegisterOperand rop1 = op1.asRegister();
        if (!rop1.register.spansBasicBlock() && isDead(rop1.register)) {
          if (result.register.isSSA() && !result.register.spansBasicBlock() &&
              rop1.register.isSSA()) {
            OPT_DefUse.removeDef(result);
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.recordDefUse(rop1);
            OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
            rop1.register.putSSA(false);
            BinaryAcc.mutate(s, opCode, rop1, op2);
            return;
          } else {
            OPT_DefUse.removeDef(result);
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.recordDefUse(rop1);
            BinaryAcc.mutate(s, opCode, rop1, op2);
            OPT_Instruction move =   
              Move.create(getMoveOp(result.type), result, rop1.copy());
            OPT_DefUse.updateDUForNewInstruction(move);
            s.insertAfter(move);
            return;
          }
        }
      }
      if (op2.isRegister()) {
        OPT_RegisterOperand rop2 = op2.asRegister();
        if (!rop2.register.spansBasicBlock() && isDead(rop2.register)) {
          if (result.register.isSSA() && !result.register.spansBasicBlock() &&
              rop2.register.isSSA()) {
            OPT_DefUse.removeUse(rop2);
            OPT_DefUse.removeDef(result);
            OPT_DefUse.recordDefUse(rop2);
            OPT_DefUse.mergeRegisters(ir, rop2.register, result.register);
            rop2.register.putSSA(false);
            BinaryAcc.mutate(s, opCode, rop2, op1);
            return;
          } else {
            OPT_DefUse.removeDef(result);
            OPT_DefUse.removeUse(rop2);
            OPT_DefUse.recordDefUse(rop2);
            BinaryAcc.mutate(s, opCode, rop2, op1);
            OPT_Instruction move =   
              Move.create(getMoveOp(result.type), result, rop2.copy());
            OPT_DefUse.updateDUForNewInstruction(move);
            s.insertAfter(move);
            return;
          }
        }
      }
    }

    // Sigh, need some kind of move instruction
    OPT_Instruction move =   
      Move.create(getMoveOp(result.type), result.copyRO(), op1.copy());
    OPT_DefUse.updateDUForNewInstruction(move);
    s.insertBefore(move);
    OPT_DefUse.removeDef(result);
    OPT_DefUse.recordDefUse(result);
    if (op1.isRegister()) {
      OPT_DefUse.removeUse(op1.asRegister());
    }
    BinaryAcc.mutate(s, opCode, result, op2);
  }    

  private void noncommutative(OPT_Instruction s, OPT_Operator opCode, 
                              OPT_IR ir) {
    OPT_RegisterOperand result = Binary.getClearResult(s);
    OPT_Operand op1 = Binary.getClearVal1(s);
    OPT_Operand op2 = Binary.getClearVal2(s);

    // Handle the easy cases of avoiding useless moves.
    if (result.similar(op1)) {
      OPT_DefUse.removeUse(op1.asRegister());
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(result);
      BinaryAcc.mutate(s, opCode, result, op2);
      return;
    }

    // attempt to detect additional cases using simple liveness and DU info
    if (OPTIMIZE) {
      if (op1.isRegister()) {
        OPT_RegisterOperand rop1 = op1.asRegister();
        if (!rop1.register.spansBasicBlock() && isDead(rop1.register)) {
          if (result.register.isSSA() && !result.register.spansBasicBlock() &&
              rop1.register.isSSA()) {
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.removeDef(result);
            OPT_DefUse.recordDefUse(rop1);
            OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
            rop1.register.putSSA(false);
            BinaryAcc.mutate(s, opCode, rop1, op2);
            return;
          } else {
            OPT_DefUse.removeDef(result);
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.recordDefUse(rop1);
            BinaryAcc.mutate(s, opCode, rop1, op2);
            OPT_Instruction move =   
              Move.create(getMoveOp(result.type), result, rop1.copy());
            OPT_DefUse.updateDUForNewInstruction(move);
            s.insertAfter(move);
            return;
          }
        }
      }
    }

    // Sigh need some move instructions after all.
    if (result.similar(op2)) {
      OPT_RegisterOperand tmp = ir.regpool.makeTemp(op1);
      OPT_Instruction move = 
        Move.create(getMoveOp(tmp.type), tmp.copyRO(), op1.copy());
      s.insertBefore(move);
      OPT_DefUse.updateDUForNewInstruction(move);
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(tmp);
      if (op1.isRegister()) {
        OPT_DefUse.removeUse(op1.asRegister());
      }
      BinaryAcc.mutate(s, opCode, tmp, op2);
      move = Move.create(getMoveOp(tmp.type), result.copyRO(), tmp.copyRO());
      s.insertAfter(move);
      OPT_DefUse.updateDUForNewInstruction(move);
    } else {
      OPT_Instruction move =   
        Move.create(getMoveOp(result.type), result.copyRO(), op1.copy());
      OPT_DefUse.updateDUForNewInstruction(move);
      s.insertBefore(move);
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(result);
      if (op1.isRegister()) {
        OPT_DefUse.removeUse(op1.asRegister());
      }
      BinaryAcc.mutate(s, opCode, result, op2);
    }
  }

  private void unary(OPT_Instruction s, OPT_Operator opCode, OPT_IR ir) {
    OPT_RegisterOperand result = Unary.getClearResult(s);
    OPT_Operand op1 = Unary.getClearVal(s);

    // Handle the easy cases of avoiding useless moves.
    if (result.similar(op1)) {
      OPT_DefUse.removeUse(op1.asRegister());
      OPT_DefUse.removeDef(result);
      OPT_DefUse.recordDefUse(result);
      UnaryAcc.mutate(s, opCode, result);
      return;
    }

    // attempt to detect additional cases using simple liveness and DU info
    if (OPTIMIZE) {
      if (op1.isRegister()) {
        OPT_RegisterOperand rop1 = op1.asRegister();
        if (!rop1.register.spansBasicBlock() && isDead(rop1.register)) {
          if (result.register.isSSA() && !result.register.spansBasicBlock() &&
              rop1.register.isSSA()) {
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.removeDef(result);
            OPT_DefUse.recordDefUse(rop1);
            OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
            rop1.register.putSSA(false);
            UnaryAcc.mutate(s, opCode, rop1);
            return;
          } else {
            OPT_DefUse.removeDef(result);
            OPT_DefUse.removeUse(rop1);
            OPT_DefUse.recordDefUse(rop1);
            UnaryAcc.mutate(s, opCode, rop1);
            OPT_Instruction move =   
              Move.create(getMoveOp(result.type), result, rop1.copy());
            OPT_DefUse.updateDUForNewInstruction(move);
            s.insertAfter(move);
            return;
          }
        }
      }
    }

    // Sigh, need the move instruction before op.
    OPT_Instruction move =   
      Move.create(getMoveOp(result.type), result.copyRO(), op1.copy());
    OPT_DefUse.updateDUForNewInstruction(move);
    s.insertBefore(move);
    OPT_DefUse.removeDef(result);
    OPT_DefUse.recordDefUse(result);
    if (op1.isRegister()) {
      OPT_DefUse.removeUse(op1.asRegister());
    }
    UnaryAcc.mutate(s, opCode, result);
  }

  private static OPT_Operator getMoveOp(VM_TypeReference t) {
    OPT_Operator op = OPT_IRTools.getMoveOp(t);
    if (op == REF_MOVE) { 
      return INT_MOVE;
    } else {
      return op;
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
  private static boolean isDead(OPT_Register r) {
    return r.scratch == 0;
  }
  private static boolean isLive(OPT_Register r) {
    return r.scratch == 1;
  }
}
