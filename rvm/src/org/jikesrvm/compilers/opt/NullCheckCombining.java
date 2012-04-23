/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.GuardCarrier;
import org.jikesrvm.compilers.opt.ir.GuardResultCarrier;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_COMBINE;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK;
import org.vmmagic.unboxed.Offset;

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
 */
public class NullCheckCombining extends CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public final String getName() {
    return "NullCheckCombining";
  }

  /**
   * Perform nullcheck combining and valdiation register removal.
   *
   * @param ir the IR to transform
   */
  @Override
  public void perform(IR ir) {
    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      if (!bb.isEmpty()) {
        Instruction lastInstr = bb.lastInstruction();

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
          Instruction activeNullCheck = null;
          Operand activeGuard = null;
          for (Instruction instr = bb.firstRealInstruction(),
              nextInstr = null; instr != lastInstr; instr = nextInstr) {
            nextInstr = instr.nextInstructionInCodeOrder();
            Operator op = instr.operator();
            if (op == GUARD_MOVE) {
              if (activeGuard != null && Move.getVal(instr).similar(activeGuard)) {
                activeGuard = Move.getResult(instr);
              }
            } else if (op == GUARD_COMBINE) {
              if (activeGuard != null &&
                  (Binary.getVal1(instr) == activeGuard || Binary.getVal2(instr) == activeGuard)) {
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
                if (activeGuard != null && canFold(instr, activeGuard, true)) {
                  instr.markAsPEI();
                  activeNullCheck.remove();
                  activeGuard = null;
                  combined = true;
                }
                remaining |= (activeGuard == null);
                activeGuard = null;   // don't attempt to move PEI past a store; could do better.
              }
            } else if (isExplicitLoad(instr, op)) {
              if (activeGuard != null && canFold(instr, activeGuard, false)) {
                instr.markAsPEI();
                activeNullCheck.remove();
                activeGuard = null;
                combined = true;
              } else if (instr.isPEI()) {
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
        for (Instruction instr = bb.firstRealInstruction(), nextInstr = null; instr != lastInstr; instr = nextInstr)
        {
          nextInstr = instr.nextInstructionInCodeOrder();
          Operator op = instr.operator();
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

  private boolean isExplicitStore(Instruction s, Operator op) {
    if (op.isExplicitStore()) return true;
    for (int i = 0, n = s.getNumberOfDefs(); i < n; i++) {
      if (s.getOperand(i) instanceof MemoryOperand) return true;
    }
    return false;
  }

  private boolean isExplicitLoad(Instruction s, Operator op) {
    if (op.isExplicitLoad()) return true;
    int numOps = s.getNumberOfOperands();
    int numUses = s.getNumberOfUses();
    for (int i = numOps - numUses; i < numOps; i++) {
      if (s.getOperand(i) instanceof MemoryOperand) {
        return true;
      }
    }
    return false;
  }

  private boolean canFold(Instruction s, Operand activeGuard, boolean isStore) {
    if (GuardCarrier.conforms(s) && GuardCarrier.hasGuard(s) && activeGuard.similar(GuardCarrier.getGuard(s))) {
      if (!VM.ExplicitlyGuardLowMemory) return true;
      // TODO: In theory, lowMemory is protected even on AIX.
      // However, enabling this causes a large number of failures.
      // Figure out why that is the case and enable some variant of this.
      // if (isStore) return true; // Even on AIX low memory is write protected
      return VM.BuildForPowerPC && Operators.helper.canFoldNullCheckAndLoad(s);
    }
    for (int i = 0, n = s.getNumberOfOperands(); i < n; i++) {
      Operand op = s.getOperand(i);
      if (op instanceof MemoryOperand) {
        MemoryOperand memOp = (MemoryOperand) op;
        if (activeGuard.similar(memOp.guard)) {
          return !VM.ExplicitlyGuardLowMemory || isStore || ((memOp.index == null) && (memOp.disp.sLT(Offset.zero())));
        }
      }
    }
    return false;
  }
}



