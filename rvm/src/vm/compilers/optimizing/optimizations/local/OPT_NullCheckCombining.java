/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * This module performs two tasks:
 * <ul>
 *   <li> (1) When possible, it folds null checks into the first load/store
 *            that is being guarded by the null check
 *   <li> (2) It removes all validation registers from the IR
 * </ul>
 *
 * <p> Doing (1) more or less implies either (a) doing (2) or 
 * (b) making large changes to the MIR operator set such that 
 * all load/stores produce validation results.
 * Although this would be possible, it would not be a trivial change. 
 * So, until we have an urgent need to preserve guard operands all 
 * the way through the MIR, we'll take the easy way out.
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 */
public class OPT_NullCheckCombining extends OPT_CompilerPhase
    implements OPT_Operators {

  OPT_NullCheckCombining () { }

  public final String getName () {
    return "NullCheckCombining";
  }

  /**
   * Perform nullcheck combining and valdiation register removal.
   * 
   * @param ir the IR to transform
   */
  public void perform (OPT_IR ir) {
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
         bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (!bb.isEmpty()) {
        OPT_Instruction lastInstr = bb.lastInstruction();
        boolean combined;
        boolean remaining;
        // (1) Combine null checks in bb into the first load/store in 
        // bb they guard.
        // Restrict this to respect PEI ordering.
        // Only do locally, since we don't understand control flow here.
        // We could be more aggressive about moving PEIs past stores 
        // by determining which stores actually update global or 
        // handler-visible state.
        do {
          combined = remaining = false;
          OPT_Instruction activeNullCheck = null;
          OPT_Operand activeGuard = null;
          for (OPT_Instruction instr = bb.firstRealInstruction(), 
              nextInstr = null; 
              instr != lastInstr; instr = nextInstr) {
            nextInstr = instr.nextInstructionInCodeOrder();
            OPT_Operator op = instr.operator();
            if (op == GUARD_MOVE) {
              if (activeGuard != null && 
                  Move.getVal(instr).similar(activeGuard)) {
                activeGuard = Move.getResult(instr);
              }
            } else if (op == GUARD_COMBINE) {
              if (activeGuard != null && 
                  (Binary.getVal1(instr) == activeGuard || 
                   Binary.getVal2(instr) == activeGuard)) {
                remaining |= (activeGuard == null);
                activeGuard = null;
              }
            } else if (op == NULL_CHECK) {
              remaining |= (activeGuard == null);
              activeGuard = NullCheck.getGuardResult(instr);
              activeNullCheck = instr;
            } else if (isExplicitStore(instr, op)) {
              if (instr.isPEI()) {
                // can't reorder PEI's 
                // NOTE: don't mark remaining, since we'd hit the same problem instr again.
                activeGuard = null;
              } else {
                if (activeGuard != null && isGuardedBy(instr, activeGuard)) {
                  instr.markAsPEI();
                  activeNullCheck.remove();
                  activeGuard = null;
                  combined = true;
                }
                remaining |= (activeGuard == null);
                activeGuard = null;   // don't attempt to move PEI past a store; could do better.
              }
            } else if (isExplicitLoad(instr, op)) {
              if (activeGuard != null && isGuardedBy(instr, activeGuard)) {
                instr.markAsPEI();
                activeNullCheck.remove();
                activeGuard = null;
                combined = true;
              } else 
                if (instr.isPEI()) {
                // can't reorder PEI's 
                // NOTE: don't mark remaining, since we'd hit the same problem instr again.
                activeGuard = null;
              }
            } else {
              if (op.isImplicitStore() || op.isPEI()) {
                // NOTE: don't mark remaining, since we'd hit the same problem instr again.
                activeGuard = null; // don't reorder PEI's; be conservative about stores.
              }
            }
          }
        } while (combined & remaining);

        // (2) Blow away all validation registers in bb.
        for (OPT_Instruction instr = bb.firstRealInstruction(), nextInstr = null; 
             instr != lastInstr; instr = nextInstr) {
          nextInstr = instr.nextInstructionInCodeOrder();
          OPT_Operator op = instr.operator();
          if (op == GUARD_MOVE || op == GUARD_COMBINE) {
            instr.remove();
          } else {
            if (GuardResultCarrier.conforms(op)) {
              GuardResultCarrier.setGuardResult(instr, null);
            }
            if (GuardCarrier.conforms(op)) {
              GuardCarrier.setGuard(instr, null);
            }
          }
        }
      }
    }
  }

  private boolean isExplicitStore(OPT_Instruction s, OPT_Operator op) {
    if (op.isExplicitStore()) return true;
    for (int i=0, n=s.getNumberOfDefs(); i<n; i++) {
      if (s.getOperand(i) instanceof OPT_MemoryOperand) return true;
    }
    return false;
  }

  private boolean isExplicitLoad(OPT_Instruction s, OPT_Operator op) {
    if (op.isExplicitLoad()) return true;
    int numOps  = s.getNumberOfOperands();
    int numUses = s.getNumberOfUses();
    for (int i= numOps - numUses; i<numOps; i++) {
      if (s.getOperand(i) instanceof OPT_MemoryOperand) return true;
    }
    return false;
  }

  private boolean isGuardedBy(OPT_Instruction s, OPT_Operand activeGuard) {
    if (GuardCarrier.conforms(s) && 
        GuardCarrier.hasGuard(s) && 
        activeGuard.similar(GuardCarrier.getGuard(s))) {
      return true;
    }
    for (int i=0, n = s.getNumberOfOperands(); i<n; i++) {
      OPT_Operand op = s.getOperand(i);
      if (op instanceof OPT_MemoryOperand &&
          activeGuard.similar(((OPT_MemoryOperand)op).guard)) {
        return true;
      }
    }
    return false;
  }
}



