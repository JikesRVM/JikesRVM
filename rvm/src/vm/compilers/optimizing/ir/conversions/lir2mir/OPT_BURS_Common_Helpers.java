/*
 * (C) Copyright IBM Corp. 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Contains BURS helper functions common to all platforms.
 * 
 * @author Dave Grove
 * @author Stephen Fink
 */
abstract class OPT_BURS_Common_Helpers extends OPT_PhysicalRegisterTools
  implements OPT_Operators, OPT_PhysicalRegisterConstants {

  /** Infinte cost for a rule */
  protected static final int INFINITE = 0x7fff;

  /**
   * The burs object
   */
  protected final OPT_BURS burs;

  /**
   * The register pool of the IR being processed
   */
  protected final OPT_RegisterPool regpool;

  OPT_BURS_Common_Helpers(OPT_BURS b) {
    burs = b;
    regpool = b.ir.regpool;
  }

  public final OPT_IR getIR() { return burs.ir; }

  protected final void EMIT(OPT_Instruction s) {
    burs.append(s);
  }

  // returns the given operand as a register
  protected final OPT_RegisterOperand R(OPT_Operand op) {
    return (OPT_RegisterOperand) op;
  }

  // returns the given operand as an integer constant
  protected final OPT_IntConstantOperand IC(OPT_Operand op) {
    return (OPT_IntConstantOperand) op;
  }
   
  // returns the given operand as a long constant
  protected final OPT_LongConstantOperand LC(OPT_Operand op) {
    return (OPT_LongConstantOperand) op;
  }

  // returns the integer value of the given operand
  protected final int IV(OPT_Operand op) {
    return IC(op).value;
  }

  // is a == 0?
  protected final boolean ZERO(OPT_Operand a) {
    return (IV(a) == 0);
  }

  // is a == 1?
  protected final boolean ONE(OPT_Operand a) {
    return (IV(a) == 1);
  }

  // is a == -1?
  protected final boolean MINUSONE(OPT_Operand a) {
    return (IV(a) == -1);
  }

  protected final int FITS(OPT_Operand op, int numBits, int trueCost) {
    return FITS(op, numBits, trueCost, INFINITE);
  }
  protected final int FITS(OPT_Operand op, int numBits, int trueCost, int falseCost) {
    if (op.isIntConstant() && OPT_Bits.fits(IV(op),numBits)) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  protected final int isZERO(int x, int trueCost) {
    return isZERO(x, trueCost, INFINITE);
  }
  protected final int isZERO(int x, int trueCost, int falseCost) {
    return x == 0 ? trueCost : falseCost;
  }

  protected final int isONE(int x, int trueCost) {
    return isONE(x, trueCost, INFINITE);
  }
  protected final int isONE(int x, int trueCost, int falseCost) {
    return x == 1 ? trueCost : falseCost;
  }


  // helper functions for condition operands
  protected final boolean EQ_NE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
            (cond == OPT_ConditionOperand.NOT_EQUAL));
  }

  protected final boolean EQ_LT_LE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
            (cond == OPT_ConditionOperand.LESS) ||
            (cond == OPT_ConditionOperand.LESS_EQUAL));
  }

  protected final boolean EQ_GT_GE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
            (cond == OPT_ConditionOperand.GREATER) ||
            (cond == OPT_ConditionOperand.GREATER_EQUAL));
  }

   /* node accessors */
   protected final OPT_Instruction P(OPT_BURS_TreeNode p) {
      return p.getInstruction();
   }
   protected final OPT_Instruction PL(OPT_BURS_TreeNode p) {
      return p.child1.getInstruction();
   }
   protected final OPT_Instruction PR(OPT_BURS_TreeNode p) {
      return p.child2.getInstruction();
   }
   protected final OPT_Instruction PLR(OPT_BURS_TreeNode p) {
      return p.child1.child2.getInstruction();
   }
   protected final OPT_Instruction PRR(OPT_BURS_TreeNode p) {
      return p.child2.child2.getInstruction();
   }
   protected final OPT_Instruction PLL(OPT_BURS_TreeNode p) {
      return p.child1.child1.getInstruction();
   }
   protected final OPT_Instruction PRL(OPT_BURS_TreeNode p) {
      return p.child2.child1.getInstruction();
   }
   protected final OPT_Instruction PLLL(OPT_BURS_TreeNode p) {
      return p.child1.child1.child1.getInstruction();
   }
   protected final OPT_Instruction PLLR(OPT_BURS_TreeNode p) {
      return p.child1.child1.child2.getInstruction();
   }
   protected final OPT_Instruction PRLL(OPT_BURS_TreeNode p) {
      return p.child2.child1.child1.getInstruction();
   }
   protected final OPT_Instruction PRLR(OPT_BURS_TreeNode p) {
      return p.child2.child1.child2.getInstruction();
   }

   protected final int V(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p).value;
   }
   protected final int VL(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child1).value;
   }
   protected final int VR(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child2).value;
   }
   protected final int VLR(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child1.child2).value;
   }
   protected final int VRR(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child2.child2).value;
   }
   protected final int VLL(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child1.child1).value;
   }
   protected final int VRL(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child2.child1).value;
   }
   protected final int VLLL(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child1.child1.child1).value;
   }
   protected final int VLLR(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child1.child1.child2).value;
   }
   protected final int VRLL(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child2.child1.child1).value;
   }
   protected final int VRLR(OPT_BURS_TreeNode p) {
      return ((OPT_BURS_IntConstantTreeNode)p.child2.child1.child2).value;
   }
}
