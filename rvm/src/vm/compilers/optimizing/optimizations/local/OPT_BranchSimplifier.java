/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Simplify and canonicalize conditional branches with constant operands.
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
 * @author Martin Trapp
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
        OPT_RegisterOperand guard = IfCmp.getGuardResult (s);
        OPT_Operand val1 = IfCmp.getVal1(s);
        if (val1.isConstant()) {
          OPT_Operand val2 = IfCmp.getVal2(s);
          if (val2.isConstant()) {
            // constant fold
            int cond = IfCmp.getCond(s).evaluate(val1, val2);
            if (cond == OPT_ConditionOperand.TRUE) {  // branch taken
              insertTrueGuard (s, guard);
              Goto.mutate(s, GOTO, IfCmp.getTarget(s));
              removeBranchesAfterGotos(bb);
            } else if (cond == OPT_ConditionOperand.FALSE) {  // branch taken
              // branch not taken
              insertTrueGuard (s, guard);
              s.remove();
            }
            else
              VM.sysFail("comparison irreducible");
            // hack. Just start over since Enumeration has changed.
            branches = bb.enumerateBranchInstructions();
            bb.recomputeNormalOut(ir);
            didSomething = true;
            continue;
          } else {
            // Canonicalize by making second argument the constant
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
        OPT_RegisterOperand guard = IfCmp2.getGuardResult (s);
        OPT_Operand val1 = IfCmp2.getVal1(s);
        if (val1.isConstant()) {
          OPT_Operand val2 = IfCmp2.getVal2(s);
          if (val2.isConstant()) {
            // constant fold
            int cond1 = IfCmp2.getCond1(s).evaluate(val1, val2);
            int cond2 = IfCmp2.getCond2(s).evaluate(val1, val2);
            if (cond1 == OPT_ConditionOperand.TRUE) {
              // target 1 taken
              insertTrueGuard (s, guard);
              Goto.mutate(s, GOTO, IfCmp2.getTarget1(s));
              removeBranchesAfterGotos(bb);
            } else if (cond2 == OPT_ConditionOperand.TRUE) {
              // target 2 taken
              insertTrueGuard (s, guard);
              Goto.mutate(s, GOTO, IfCmp2.getTarget2(s));
              removeBranchesAfterGotos(bb);
            } else if (cond1 != OPT_ConditionOperand.UNKNOWN &&
                       cond2 != OPT_ConditionOperand.UNKNOWN) {
              // not taken
              insertTrueGuard (s, guard);
              s.remove();
            }
            else 
              VM.sysFail("comparison irreducible");
            // hack. Just start over since Enumeration has changed.
            branches = bb.enumerateBranchInstructions();
            bb.recomputeNormalOut(ir);
            didSomething = true;
          } else {
            // Canonicalize by making second argument the constant
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
      } else if (InlineGuard.conforms(s)) {
        OPT_Operand val = InlineGuard.getValue(s);
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
        if (GuardResultCarrier.conforms (s))
          insertTrueGuard (s, GuardResultCarrier.getGuardResult (s));
        s.remove();
      }
    }
  }

  
  private static void insertTrueGuard (OPT_Instruction inst,
                                       OPT_RegisterOperand guard) {
    if (guard == null) return;  // Probably bad style but correct IR
    OPT_Instruction trueGuard = Move.create(GUARD_MOVE, guard.copyD2D(), 
                                            new OPT_TrueGuardOperand());
    trueGuard.position = inst.position;
    trueGuard.bcIndex  = inst.bcIndex;
    inst.insertBefore (trueGuard);
    OPT_DefUse.updateDUForNewInstruction(trueGuard);
  }
}
