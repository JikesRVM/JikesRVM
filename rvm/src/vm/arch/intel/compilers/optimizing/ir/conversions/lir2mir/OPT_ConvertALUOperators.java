/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Convert instructions with 3-operand binary ALU operators to use 
 * 2-operand ALU operators. 
 * Convert instructions with 2-operand unary ALU operators to use 
 * 1-operand ALU operators.
 *
 * The general idea is to turn instructions like
 *
 *   op r100 = r200, r300      =====>       move r100 = r200
 *                                          op   r100 <-- r300
 *
 * but there are several special cases that one could do better on
 * (by improving the odds of colaescing away the move or avoiding it entirely)
 * 
 *   op r100 = r100, r300      =====>       op   r100 <-- r300
 *                                          
 *   op r100 = r200, r100      =====>       op   r100 <-- r200
 *      (of op is commutative) 
 *
 *   op r100 = r200, r100      =====>        move rtemp = r100
 *      (if op is non commutative)           move r100  = r200
 *                                           op   r100 <--  rtemp 
 * 
 * But we also keep our eyes open for the special (somewhat common) case 
 * of one of the uses being the last use of a temporary.  When this happens
 * we can sometimes avoid inserting a move at all. When this happens, we 
 * rewrite:
 *   op r100 = r200, r300     =====>         op r200 = r200, r300
 * and replace all uses of r100 with r200. 
 * We can legally do this when 
 *  (1) r100 is ssa
 *  (2) r200 does not span a basic block 
 *  (3) this instruction is the only use of r200.
 * Ditto on r300 if op is commutative and r200 doesn't work out.
 *
 * @author Dave Grove
 */
final class OPT_ConvertALUOperators extends OPT_CompilerPhase 
  implements OPT_Operators {

  private static final boolean OPTIMIZE = true;

  final String getName() { return "ConvertALUOps"; }
  final OPT_CompilerPhase newExecution(OPT_IR ir) { return this; }

  final void perform(OPT_IR ir) { 
    // Compute simple ssa, u/d chains, spansBasicBlock
    // to catch some easy cases where we don't have to insert move operations
    if (OPTIMIZE) {
      OPT_DefUse.computeDU(ir);
      OPT_DefUse.recomputeSSA(ir);
      OPT_DefUse.recomputeSpansBasicBlock(ir);
    }

    for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
	 s != null; 
	 s = s.nextInstructionInCodeOrder()) {
      // Calling OPT_Simplifier.simplify ensures that the instruction is 
      // in normalized form. This reduces the number of cases we have to 
      // worry about (and does last minute constant folding on the off 
      // chance we've missed an opportunity...)
      OPT_Simplifier.simplify(s);

      switch(s.getOpcode()) {
      case INT_ADD_opcode: commutative(s, INT_ADD_ACC, ir); break;
      case LONG_ADD_opcode: commutative(s, LONG_ADD_ACC, ir); break;
      case INT_SUB_opcode: noncommutative(s, INT_SUB_ACC, ir); break;
      case LONG_SUB_opcode: noncommutative(s, LONG_SUB_ACC, ir); break;
      case INT_MUL_opcode: commutative(s, INT_MUL_ACC, ir); break;
      case LONG_MUL_opcode: commutative(s, LONG_MUL_ACC, ir); break;
      case INT_SHL_opcode: noncommutative(s, INT_SHL_ACC, ir); break;
      case LONG_SHL_opcode: noncommutative(s, LONG_SHL_ACC, ir); break;
      case INT_SHR_opcode: noncommutative(s, INT_SHR_ACC, ir); break;
      case LONG_SHR_opcode: noncommutative(s, LONG_SHR_ACC, ir); break;
      case INT_USHR_opcode: noncommutative(s, INT_USHR_ACC, ir); break;
      case LONG_USHR_opcode: noncommutative(s, LONG_USHR_ACC, ir); break;
      case INT_AND_opcode: commutative(s, INT_AND_ACC, ir); break;
      case LONG_AND_opcode: commutative(s, LONG_AND_ACC, ir); break;
      case INT_OR_opcode: commutative(s, INT_OR_ACC, ir); break;
      case LONG_OR_opcode: noncommutative(s, LONG_OR_ACC, ir); break;
      case INT_XOR_opcode: noncommutative(s, INT_XOR_ACC, ir); break;
      case LONG_XOR_opcode: noncommutative(s, LONG_XOR_ACC, ir); break;
      case INT_NEG_opcode: unary(s, INT_NEG_ACC, ir); break;
      case LONG_NEG_opcode: unary(s, LONG_NEG_ACC, ir); break;
      case BOOLEAN_NOT_opcode: unary(s, BOOLEAN_NOT_ACC, ir); break;
      case INT_NOT_opcode: unary(s, INT_NOT_ACC, ir); break;
      case LONG_NOT_opcode: unary(s, LONG_NOT_ACC, ir); break;
      default:
	break; // nothing to do
      }
    }
  }

  private void commutative(OPT_Instruction s, OPT_Operator opCode, OPT_IR ir) {
    OPT_RegisterOperand result = Binary.getClearResult(s);
    OPT_Operand op1 = Binary.getClearVal1(s);
    OPT_Operand op2 = Binary.getClearVal2(s);

    // Attempt to avoid inserting redundant move as described in header comment
    if (OPTIMIZE) {
      if (result.similar(op1)) {
	BinaryAcc.mutate(s, opCode, result, op2);
	return;
      } else if (result.similar(op2)) {
	BinaryAcc.mutate(s, opCode, result, op1);
	return;
      } else {
	if (result.register.isSSA()) {
	  if (op1 instanceof OPT_RegisterOperand) {
	    OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
	    if (!rop1.register.spansBasicBlock() && 
		OPT_DefUse.exactlyOneUse(rop1.register)) {
	      OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
	      BinaryAcc.mutate(s, opCode, rop1, op2);
	      return;
	    }
	  }
	  if (op2 instanceof OPT_RegisterOperand) {
	    OPT_RegisterOperand rop2 = (OPT_RegisterOperand)op2;
	    if (!rop2.register.spansBasicBlock() && 
		OPT_DefUse.exactlyOneUse(rop2.register)) {
	      OPT_DefUse.mergeRegisters(ir, rop2.register, result.register);
	      BinaryAcc.mutate(s, opCode, rop2, op1);
	      return;
	    }
	  }
	}
      }
    }
    // Sigh, need the move instruction after all
    s.insertBefore(Move.create(OPT_IRTools.getMoveOp(result.type, true), 
			       result, op1));
    BinaryAcc.mutate(s, opCode, result.copyD2D(), op2);
  }    

  private void noncommutative(OPT_Instruction s, OPT_Operator opCode, 
			      OPT_IR ir) {
    OPT_RegisterOperand result = Binary.getClearResult(s);
    OPT_Operand op1 = Binary.getClearVal1(s);
    OPT_Operand op2 = Binary.getClearVal2(s);

    if (OPTIMIZE) {
      // Attempt to avoid inserting redundant move as described in header comment
      if (result.similar(op1)) {
	BinaryAcc.mutate(s, opCode, result, op2);
	return;
      } else if (result.similar(op2)) {
	OPT_RegisterOperand tmp = ir.gc.temps.makeTemp(op2);
	s.insertBefore(Move.create(OPT_IRTools.getMoveOp(tmp.type, true), 
				   tmp, op2));
	s.insertBefore(Move.create(OPT_IRTools.getMoveOp(result.type, true), 
				   result, op1));
	BinaryAcc.mutate(s, opCode, result.copyD2D(), tmp.copyD2U());
	return;
      } else {
	if (result.register.isSSA()) {
	  if (op1 instanceof OPT_RegisterOperand) {
	    OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
	    if (!rop1.register.spansBasicBlock() && 
		OPT_DefUse.exactlyOneUse(rop1.register)) {
	      OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
	      BinaryAcc.mutate(s, opCode, rop1, op2);
	      return;
	    }
	  }
	}
      }
    }
    // Sigh, need the move instruction after all
    s.insertBefore(Move.create(OPT_IRTools.getMoveOp(result.type, true), 
			       result, op1));
    BinaryAcc.mutate(s, opCode, result.copyD2D(), op2);
  }

  private void unary(OPT_Instruction s, OPT_Operator opCode, OPT_IR ir) {
    OPT_RegisterOperand result = Unary.getClearResult(s);
    OPT_Operand op1 = Unary.getClearVal(s);

    if (OPTIMIZE) {
      // Attempt to avoid inserting redundant move as described in header comment
      if (result.similar(op1)) {
	UnaryAcc.mutate(s, opCode, result);
	return;
      } else {
	if (result.register.isSSA()) {
	  if (op1 instanceof OPT_RegisterOperand) {
	    OPT_RegisterOperand rop1 = (OPT_RegisterOperand)op1;
	    if (!rop1.register.spansBasicBlock() && 
		OPT_DefUse.exactlyOneUse(rop1.register)) {
	      OPT_DefUse.mergeRegisters(ir, rop1.register, result.register);
	      UnaryAcc.mutate(s, opCode, rop1);
	      return;
	    }
	  }
	}
      }
    }
    // Sigh, need the move instruction after all
    s.insertBefore(Move.create(OPT_IRTools.getMoveOp(result.type, true), 
			       result, op1));
    UnaryAcc.mutate(s, opCode, result.copyD2D());
  }
}
