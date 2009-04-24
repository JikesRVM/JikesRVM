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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Iterator;

import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.SPLIT;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;

/**
 * Utility to help coalesce registers.
 *
 * @see CoalesceMoves
 */
class Coalesce {

  /**
   * Attempt to coalesce register r2 into register r1.  If this is legal,
   * <ul>
   * <li> rewrite all defs and uses of r2 as defs and uses of r1
   * <li> update the liveness information
   * <li> update the def-use chains
   * </ul>
   * <strong>PRECONDITION </strong> def-use chains must be computed and valid.
   * <strong>PRECONDITION </strong> instructions are numbered, with
   * numbers stored in Instruction.scratch
   *
   * @param ir the governing IR
   * @param live liveness information for the IR
   * @param r1
   * @param r2
   * @return true if the transformation succeeded, false otherwise.
   */
  public static boolean attempt(IR ir, LiveAnalysis live, Register r1, Register r2) {

    // make sure r1 and r2 are not simultaneously live
    if (isLiveAtDef(r2, r1, live)) return false;
    if (isLiveAtDef(r1, r2, live)) return false;

    // Liveness is OK.  Check for SPLIT operations
    if (split(r1, r2)) return false;

    // Don't merge a register with itself
    if (r1 == r2) return false;

    // Update liveness information to reflect the merge.
    live.merge(r1, r2);

    // Merge the defs.
    for (RegisterOperandEnumeration e = DefUse.defs(r2); e.hasMoreElements();) {
      RegisterOperand def = e.nextElement();
      DefUse.removeDef(def);
      def.setRegister(r1);
      DefUse.recordDef(def);
    }
    // Merge the uses.
    for (RegisterOperandEnumeration e = DefUse.uses(r2); e.hasMoreElements();) {
      RegisterOperand use = e.nextElement();
      DefUse.removeUse(use);
      use.setRegister(r1);
      DefUse.recordUse(use);
    }
    return true;
  }

  /**
   * Is register r1 live at any def of register r2?
   * <p>
   * <strong>PRECONDITION </strong> def-use chains must be computed and valid.
   * <strong>PRECONDITION </strong> instructions are numbered, with
   * numbers stored in Instruction.scratch
   *
   * <p> Note: this implementation is not efficient.  The liveness data
   * structures must be re-designed to support this efficiently.
   */
  private static boolean isLiveAtDef(Register r1, Register r2, LiveAnalysis live) {

    for (Iterator<LiveIntervalElement> e = live.iterateLiveIntervals(r1); e.hasNext();) {
      LiveIntervalElement elem = e.next();
      BasicBlock bb = elem.getBasicBlock();
      Instruction begin = (elem.getBegin() == null) ? bb.firstInstruction() : elem.getBegin();
      Instruction end = (elem.getEnd() == null) ? bb.lastInstruction() : elem.getEnd();
      int low = begin.scratch;
      int high = end.scratch;
      for (RegisterOperandEnumeration defs = DefUse.defs(r2); defs.hasMoreElements();) {
        Operand def = defs.nextElement();
        int n = def.instruction.scratch;
        if (n >= low && n < high) {
          return true;
        }
      }
    }

    // no conflict was found.
    return false;
  }

  /**
   * Is there an instruction r1 = split r2 or r2 = split r1??
   */
  private static boolean split(Register r1, Register r2) {
    for (RegisterOperandEnumeration e = DefUse.defs(r1); e.hasMoreElements();) {
      RegisterOperand def = e.nextElement();
      Instruction s = def.instruction;
      if (s.operator == SPLIT) {
        Operand rhs = Unary.getVal(s);
        if (rhs.similar(def)) return true;
      }
    }
    for (RegisterOperandEnumeration e = DefUse.defs(r2); e.hasMoreElements();) {
      RegisterOperand def = e.nextElement();
      Instruction s = def.instruction;
      if (s.operator == SPLIT) {
        Operand rhs = Unary.getVal(s);
        if (rhs.similar(def)) return true;
      }
    }
    return false;
  }
}
