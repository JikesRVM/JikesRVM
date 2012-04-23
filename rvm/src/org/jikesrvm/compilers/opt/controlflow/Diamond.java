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
package org.jikesrvm.compilers.opt.controlflow;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Label;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;

/**
 * This class represents a diamond (if-then-else) structure in the
 * control-flow graph.
 */
final class Diamond {
  /**
   * The top of the diamond
   */
  private final BasicBlock top;

  /**
   * The bottom of the diamond
   */
  private final BasicBlock bottom;

  /**
   * The "taken" branch of the diamond (might be null)
   */
  private final BasicBlock taken;

  /**
   * The "not-taken" branch of the diamond (might be null)
   */
  private final BasicBlock notTaken;

  /**
   * The top of the diamond
   */
  BasicBlock getTop() { return top; }

  /**
   * The bottom of the diamond
   */
  BasicBlock getBottom() { return bottom; }

  /**
   * The "taken" branch of the diamond (might be null)
   */
  BasicBlock getTaken() { return taken;}

  /**
   * The "not-taken" branch of the diamond (might be null)
   */
  BasicBlock getNotTaken() { return notTaken; }

  Diamond(BasicBlock top, BasicBlock taken, BasicBlock notTaken, BasicBlock bottom) {
    this.top = top;
    this.taken = taken;
    this.notTaken = notTaken;
    this.bottom = bottom;
  }

  /**
   * See if bb is the root of a diamond.  If so, return an Diamond
   * representing the structure.
   *
   * @return a structure representing the diamond.  null if not
   * applicable.
   */
  static Diamond buildDiamond(BasicBlock bb) {
    if (bb.getNumberOfNormalOut() != 2) return null;

    // Identify the two out nodes from bb.
    BasicBlockEnumeration outNodes = bb.getNormalOut();
    BasicBlock out1 = outNodes.nextElement();
    BasicBlock out2 = outNodes.nextElement();
    int out1In = out1.getNumberOfIn();
    int out2In = out2.getNumberOfIn();

    if (out1In == 1 && out2In == 1) {
      // look for the case where the diamond has four non-empty blocks.
      if (out1.getNumberOfNormalOut() == 1 && out2.getNumberOfNormalOut() == 1) {
        BasicBlock b1 = out1.getNormalOut().nextElement();
        BasicBlock b2 = out2.getNormalOut().nextElement();
        if (b1 == b2) {
          return fourElementDiamond(bb, out1, out2, b1);
        }
      }
    } else if (out1In == 1) {
      // check for a 3-element diamond
      if (out1.getNumberOfNormalOut() == 1) {
        BasicBlock b1 = out1.getNormalOut().nextElement();
        if (b1 == out2) {
          return threeElementDiamond(bb, out1, out2);
        }
      }
    } else if (out2In == 1) {
      // check for a 3-element diamond
      if (out2.getNumberOfNormalOut() == 1) {
        BasicBlock b2 = out2.getNormalOut().nextElement();
        if (b2 == out1) {
          return threeElementDiamond(bb, out2, out1);
        }
      }
    }
    // didn't find a diamond
    return null;
  }

  /**
   * Given that four blocks form a diamond, return the correct structure.
   */
  private static Diamond fourElementDiamond(BasicBlock top, BasicBlock left, BasicBlock right,
                                                BasicBlock bottom) {

    Instruction cb = top.firstBranchInstruction();
    // for now we only support IfCmp diamonds.
    if (VM.VerifyAssertions) VM._assert(IfCmp.conforms(cb));

    BranchOperand takenTarget = IfCmp.getTarget(cb);
    if (Label.getBlock(takenTarget.target).block == left) {
      return new Diamond(top, left, right, bottom);
    } else {
      return new Diamond(top, right, left, bottom);
    }
  }

  /**
   * Given that three blocks form a diamond, return the correct structure.
   */
  private static Diamond threeElementDiamond(BasicBlock top, BasicBlock side, BasicBlock bottom) {

    Instruction cb = top.firstBranchInstruction();
    // for now we only support IfCmp diamonds.
    if (VM.VerifyAssertions) VM._assert(IfCmp.conforms(cb));

    BranchOperand takenTarget = IfCmp.getTarget(cb);
    if (Label.getBlock(takenTarget.target).block == side) {
      return new Diamond(top, side, null, bottom);
    } else {
      return new Diamond(top, null, side, bottom);
    }
  }

  /**
   * Return a string representation.
   */
  @Override
  public String toString() {
    return "[" + top + "," + taken + "," + notTaken + "," + bottom + "]";
  }
}
