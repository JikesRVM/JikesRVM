/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;
import com.ibm.jikesrvm.*;

import com.ibm.jikesrvm.opt.ir.*;

/**
 * This class represents a diamond (if-then-else) structure in the
 * control-flow graph.
 * 
 * @author Stephen Fink
 */
final class OPT_Diamond {
  /**
   * The top of the diamond
   */
  private final OPT_BasicBlock top;

  /**
   * The bottom of the diamond
   */
  private final OPT_BasicBlock bottom;

  /**
   * The "taken" branch of the diamond (might be null)
   */
  private final OPT_BasicBlock taken;

  /**
   * The "not-taken" branch of the diamond (might be null)
   */
  private final OPT_BasicBlock notTaken;

  /**
   * The top of the diamond
   */
  OPT_BasicBlock getTop() { return top; }

  /**
   * The bottom of the diamond
   */
  OPT_BasicBlock getBottom() { return bottom; }

  /**
   * The "taken" branch of the diamond (might be null)
   */
  OPT_BasicBlock getTaken() { return taken;}

  /**
   * The "not-taken" branch of the diamond (might be null)
   */
  OPT_BasicBlock getNotTaken() { return notTaken; }


  OPT_Diamond(OPT_BasicBlock top, OPT_BasicBlock taken, 
              OPT_BasicBlock notTaken, OPT_BasicBlock bottom) {
    this.top = top;
    this.taken = taken;
    this.notTaken = notTaken;
    this.bottom = bottom;
  }

  /**
   * See if bb is the root of a diamond.  If so, return an OPT_Diamond
   * representing the structure.
   *
   * @return a structure representing the diamond.  null if not
   * applicable.
   */
  static OPT_Diamond buildDiamond(OPT_BasicBlock bb) {
    if (bb.getNumberOfNormalOut() != 2) return null;

    // Identify the two out nodes from bb.
    OPT_BasicBlockEnumeration outNodes = bb.getNormalOut();
    OPT_BasicBlock out1 = outNodes.nextElement();
    OPT_BasicBlock out2 = outNodes.nextElement();
    int out1In = out1.getNumberOfIn();
    int out2In = out2.getNumberOfIn();

    if (out1In == 1 && out2In ==1) {
      // look for the case where the diamond has four non-empty blocks.
      if (out1.getNumberOfNormalOut() == 1 && 
          out2.getNumberOfNormalOut() == 1) {
        OPT_BasicBlock b1 = (OPT_BasicBlock)out1.getNormalOut().nextElement();
        OPT_BasicBlock b2 = (OPT_BasicBlock)out2.getNormalOut().nextElement();
        if (b1 == b2) {
          return fourElementDiamond(bb,out1,out2,b1);
        }
      }
    } else if (out1In == 1) {
      // check for a 3-element diamond
      if (out1.getNumberOfNormalOut() == 1) {
        OPT_BasicBlock b1 = (OPT_BasicBlock)out1.getNormalOut().nextElement();
        if (b1 == out2) {
          return threeElementDiamond(bb,out1,out2);
        }
      }
    } else if (out2In == 1) {
      // check for a 3-element diamond
      if (out2.getNumberOfNormalOut() == 1) {
        OPT_BasicBlock b2 = (OPT_BasicBlock)out2.getNormalOut().nextElement();
        if (b2 == out1) {
          return threeElementDiamond(bb,out2,out1);
        }
      }
    } 
    // didn't find a diamond
    return null;
  }
  /**
   * Given that four blocks form a diamond, return the correct structure.
   */
  private static OPT_Diamond fourElementDiamond(OPT_BasicBlock top,
                                                OPT_BasicBlock left,
                                                OPT_BasicBlock right,
                                                OPT_BasicBlock bottom) {
    
    OPT_Instruction cb = top.firstBranchInstruction();
    // for now we only support IfCmp diamonds.
    if (VM.VerifyAssertions) VM._assert(IfCmp.conforms(cb));

    OPT_BranchOperand takenTarget = IfCmp.getTarget(cb);
    if (Label.getBlock(takenTarget.target).block == left) {
      return new OPT_Diamond(top,left,right,bottom);
    } else {
      return new OPT_Diamond(top,right,left,bottom);
    }
  }
  /**
   * Given that three blocks form a diamond, return the correct structure.
   */
  private static OPT_Diamond threeElementDiamond(OPT_BasicBlock top,
                                                OPT_BasicBlock side,
                                                OPT_BasicBlock bottom) {
    
    OPT_Instruction cb = top.firstBranchInstruction();
    // for now we only support IfCmp diamonds.
    if (VM.VerifyAssertions) VM._assert(IfCmp.conforms(cb));

    OPT_BranchOperand takenTarget = IfCmp.getTarget(cb);
    if (Label.getBlock(takenTarget.target).block == side) {
      return new OPT_Diamond(top,side,null,bottom);
    } else {
      return new OPT_Diamond(top,null,side,bottom);
    }
  }

  /**
   * Return a string representation.
   */
  public String toString() {
    return "[" + top + "," + taken + "," + notTaken + "," + bottom + "]";
  }
}
