/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Simplify and cannonicalize conditional branches with constant operands.
 *
 * <p> This module performs no analysis, it simply attempts to 
 * simplify any branching instructions of a basic block that have constant 
 * operands. The intent is that analysis modules can call this 
 * transformation engine, allowing us to share the
 * simplification code among multiple analysis modules.
 *
 * @author Steve Fink
 * @author Dave Grove
 * @author Mauricio Serrano
 */
abstract class OPT_BranchSimplifier implements OPT_Operators {

  /**
   * Given a basic block, attempt to simplify any conditional branch
   * instructions with constant operands.
   * The instruction will be mutated in place.
   * The control flow graph will be updated, but the caller is responsible
   * for calling OPT_BranchOptmizations after simplify has been called on
   * all basic blocks in the IR to remove unreachable code.
   *
   * @param bb the basic block to simplify
   * @return true if we do something, false otherwise.
   */
  public static boolean simplify(OPT_BasicBlock bb, OPT_IR ir) {
    boolean didSomething = false;

    for (OPT_InstructionEnumeration branches = 
	   bb.enumerateBranchInstructions(); branches.hasMoreElements();) {
      OPT_Instruction s = branches.next();
      if (Goto.conforms(s)) {
	// nothing to do, but a common case so test first
      } else if (IfCmp.conforms(s)) {
	OPT_Operand val1 = IfCmp.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = IfCmp.getVal2(s);
	  if (val2.isConstant()) {
	    // constant fold
	    if (IfCmp.getCond(s).evaluate(val1, val2)) {
	      // branch taken
	      Goto.mutate(s, GOTO, IfCmp.getTarget(s));
	      removeBranchesAfterGotos(bb);
	    } else {
	      // branch not taken
	      s.remove();
	    }
	    // hack. Just start over since Enumeration has changed.
	    branches = bb.enumerateBranchInstructions();
	    bb.recomputeNormalOut(ir);
	    didSomething = true;
	    continue;
	  } else {
	    // Cannonicalize by making second argument the constant
	    IfCmp.setVal1(s, val2);
	    IfCmp.setVal2(s, val1);
	    IfCmp.setCond(s, IfCmp.getCond(s).flipOperands());

	  }
	}

	OPT_Operand val2 = IfCmp.getVal2(s);
	if (val2.isIntConstant()) {
	  // Tricks to get compare against zero.
	  int value = ((OPT_IntConstantOperand)val2).value;
	  OPT_ConditionOperand cond = IfCmp.getCond(s);
	  if (value == 1) {
	    if (cond.isLESS()) {
	      IfCmp.setCond(s, OPT_ConditionOperand.LESS_EQUAL());
	      IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
	    } else if (cond.isGREATER_EQUAL()) {
	      IfCmp.setCond(s, OPT_ConditionOperand.GREATER());
	      IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
	    }
	  } else if (value == -1) {
	    if (cond.isGREATER()) {
	      IfCmp.setCond(s, OPT_ConditionOperand.GREATER_EQUAL());
	      IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
	    } else if (cond.isLESS_EQUAL()) {
	      IfCmp.setCond(s, OPT_ConditionOperand.LESS());
	      IfCmp.setVal2(s, new OPT_IntConstantOperand(0));
	    }
	  }
	}
      } else if (IfCmp2.conforms(s)) {
	OPT_Operand val1 = IfCmp2.getVal1(s);
	if (val1.isConstant()) {
	  OPT_Operand val2 = IfCmp2.getVal2(s);
	  if (val2.isConstant()) {
	    // constant fold
	    if (IfCmp2.getCond1(s).evaluate(val1, val2)) {
	      // target 1 taken
	      Goto.mutate(s, GOTO, IfCmp2.getTarget1(s));
	      removeBranchesAfterGotos(bb);
	    } else if (IfCmp2.getCond2(s).evaluate(val1, val2)) {
	      // target 2 taken
	      Goto.mutate(s, GOTO, IfCmp2.getTarget2(s));
	      removeBranchesAfterGotos(bb);
	    } else {
	      // not taken
	      s.remove();
	    }
	    // hack. Just start over since Enumeration has changed.
	    branches = bb.enumerateBranchInstructions();
	    bb.recomputeNormalOut(ir);
	    didSomething = true;
	  } else {
	    // Cannonicalize by making second argument the constant
	    IfCmp2.setVal1(s, val2);
	    IfCmp2.setVal2(s, val1);
	    IfCmp2.setCond1(s, IfCmp2.getCond1(s).flipOperands());
	    IfCmp2.setCond2(s, IfCmp2.getCond2(s).flipOperands());
	  }
	}
      } else if (LookupSwitch.conforms(s)) {
	OPT_Operand val = LookupSwitch.getValue(s);
	if (val.isConstant()) {
          int value = ((OPT_IntConstantOperand)val).value;
          OPT_BranchOperand target = LookupSwitch.getDefault(s);
          int numMatches = LookupSwitch.getNumberOfMatches(s);
          for (int i=0; i<numMatches; i++) {
            if (value == LookupSwitch.getMatch(s, i).value) {
              target = LookupSwitch.getTarget(s, i);
              break;
            }
          }
          Goto.mutate(s, GOTO, target);
          removeBranchesAfterGotos(bb);
          bb.recomputeNormalOut(ir);
          didSomething = true;
	}
      } else if (TableSwitch.conforms(s)) {
	OPT_Operand val = TableSwitch.getValue(s);
	if (val.isConstant()) {
	  int value = ((OPT_IntConstantOperand)val).value;
	  OPT_BranchOperand target = TableSwitch.getDefault(s);
          int low = TableSwitch.getLow(s).value;
          int high = TableSwitch.getHigh(s).value;
          if (value >= low && value <= high) {
            target = TableSwitch.getTarget(s, value - low);
          }
          Goto.mutate(s, GOTO, target);
          removeBranchesAfterGotos(bb);
          bb.recomputeNormalOut(ir);
          didSomething = true;
        }
      } else if (MethodIfCmp.conforms(s)) {
	OPT_Operand val = MethodIfCmp.getValue(s);
	if (val.isNullConstant()) {
	  // branch not taken
	  s.remove();
	  // hack. Just start over since Enumeration has changed.
	  branches = bb.enumerateBranchInstructions();
	  bb.recomputeNormalOut(ir);
	  didSomething = true;
	  continue;
	} else if (val.isStringConstant()) {
	  // TODO:
	  VM.sysWrite("TODO: should constant fold MethodIfCmp on StringConstant");
	}
      } else if (TypeIfCmp.conforms(s)) {
	OPT_Operand val = TypeIfCmp.getValue(s);
	if (val.isNullConstant()) {
	  // Like an instanceof, therefore branch not taken
	  s.remove();
	  // hack. Just start over since Enumeration has changed.
	  branches = bb.enumerateBranchInstructions();
	  bb.recomputeNormalOut(ir);
	  didSomething = true;
	  continue;
	} else if (val.isStringConstant()) {
	  if (TypeIfCmp.getType(s).type == VM_Type.JavaLangStringType) {
	    // branch taken
	    Goto.mutate(s, GOTO, TypeIfCmp.getClearTarget(s));
	    removeBranchesAfterGotos(bb);
	  } else {
	    // Not taken
	    s.remove();
	  }
	  // hack. Just start over since Enumeration has changed.
	  branches = bb.enumerateBranchInstructions();
	  bb.recomputeNormalOut(ir);
	  didSomething = true;
	  continue;
	}
      }
    }
    return didSomething;
  }


  /** 
   * To maintain IR integrity, remove any branches that are after the 
   * first GOTO in the basic block.
   */
  private static void removeBranchesAfterGotos(OPT_BasicBlock bb) {
    // identify the first GOTO instruction in the basic block
    OPT_Instruction firstGoto = null;
    OPT_Instruction end = bb.lastRealInstruction();
    for (OPT_InstructionEnumeration branches = 
	   bb.enumerateBranchInstructions(); branches.hasMoreElements();) {
      OPT_Instruction s = branches.next();
      if (Goto.conforms(s)) {
        firstGoto = s;
        break;
      }
    }
    // remove all instructions after the first GOTO instruction
    if (firstGoto != null) {
      OPT_InstructionEnumeration ie = 
	OPT_IREnumeration.forwardIntraBlockIE(firstGoto, end);
      ie.next();
      for (; ie.hasMoreElements();) {
        OPT_Instruction s = ie.next();
        s.remove();
      }
    }
  }
}
