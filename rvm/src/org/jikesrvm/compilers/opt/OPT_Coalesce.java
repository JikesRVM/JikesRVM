/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.util.Iterator;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SPLIT;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Unary;

/**
 * Utility to help coalesce registers.
 *
 * @see OPT_CoalesceMoves
 */
class OPT_Coalesce {

  /**
   * Attempt to coalesce register r2 into register r1.  If this is legal,
   * <ul>
   * <li> rewrite all defs and uses of r2 as defs and uses of r1
   * <li> update the liveness information
   * <li> update the def-use chains
   * </ul>
   * <strong>PRECONDITION </strong> def-use chains must be computed and valid.
   * <strong>PRECONDITION </strong> instructions are numbered, with
   * numbers stored in OPT_Instruction.scratch
   *
   * @param ir the governing IR
   * @param live liveness information for the IR
   * @param r1
   * @param r2
   * @return true if the transformation succeeded, false otherwise.
   */
  public static boolean attempt(OPT_IR ir, OPT_LiveAnalysis live, 
                                OPT_Register r1, OPT_Register r2) {

    // make sure r1 and r2 are not simultaneously live
    if (isLiveAtDef(r2,r1,live)) return false;
    if (isLiveAtDef(r1,r2,live)) return false;

    // Liveness is OK.  Check for SPLIT operations
    if (split(r1,r2)) return false;

    // Don't merge a register with itself               
    if (r1 == r2) return false;

    // Update liveness information to reflect the merge.
    live.merge(r1,r2);
    
    // Merge the defs.
    for (OPT_RegisterOperandEnumeration e = OPT_DefUse.defs(r2);
         e.hasMoreElements(); ) {
      OPT_RegisterOperand def= e.nextElement();
      OPT_DefUse.removeDef(def);
      def.register = r1;
      OPT_DefUse.recordDef(def);
    }
    // Merge the uses.
    for (OPT_RegisterOperandEnumeration e = OPT_DefUse.uses(r2);
         e.hasMoreElements(); ) {
      OPT_RegisterOperand use = e.nextElement();
      OPT_DefUse.removeUse(use);
      use.register = r1;
      OPT_DefUse.recordUse(use);
    }
    return true;
  }

  /**
   * Is register r1 live at any def of register r2?
   * <p>
   * <strong>PRECONDITION </strong> def-use chains must be computed and valid.
   * <strong>PRECONDITION </strong> instructions are numbered, with
   * numbers stored in OPT_Instruction.scratch
   *
   * <p> Note: this implementation is not efficient.  The liveness data
   * structures must be re-designed to support this efficiently.
   */
  private static boolean isLiveAtDef(OPT_Register r1, OPT_Register r2,
                                     OPT_LiveAnalysis live) {

    for (Iterator<OPT_LiveIntervalElement> e = live.iterateLiveIntervals(r1); e.hasNext(); ) {
      OPT_LiveIntervalElement elem = e.next();
      OPT_BasicBlock bb = elem.getBasicBlock();
      OPT_Instruction begin = (elem.getBegin() == null) ?  
        bb.firstInstruction() : elem.getBegin();
      OPT_Instruction end = (elem.getEnd() == null) ?
        bb.lastInstruction() : elem.getEnd();
      int low = begin.scratch;
      int high = end.scratch;
      for (OPT_RegisterOperandEnumeration defs = OPT_DefUse.defs(r2); defs.hasMoreElements(); ) {
        OPT_Operand def = defs.nextElement();
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
  private static boolean split(OPT_Register r1, OPT_Register r2) {
    for (OPT_RegisterOperandEnumeration e = OPT_DefUse.defs(r1);
         e.hasMoreElements(); ) {
      OPT_RegisterOperand def = e.nextElement();
      OPT_Instruction s = def.instruction;
      if (s.operator == SPLIT) {
        OPT_Operand rhs = Unary.getVal(s);
        if (rhs.similar(def)) return true;
      }
    }
    for (OPT_RegisterOperandEnumeration e = OPT_DefUse.defs(r2); 
         e.hasMoreElements(); ) {
      OPT_RegisterOperand def = e.nextElement();
      OPT_Instruction s = def.instruction;
      if (s.operator == SPLIT) {
        OPT_Operand rhs = Unary.getVal(s);
        if (rhs.similar(def)) return true;
      }
    }
    return false;
  }
}
