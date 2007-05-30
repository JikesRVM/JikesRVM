/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.ArchitectureSpecific.OPT_BURS_TreeNode;
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterTools;
import org.jikesrvm.ArchitectureSpecific.OPT_RegisterPool;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConditionOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.vmmagic.unboxed.Address;

/**
 * Contains BURS helper functions common to all platforms.
 */
public abstract class OPT_BURS_Common_Helpers extends OPT_PhysicalRegisterTools {

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

  protected OPT_BURS_Common_Helpers(OPT_BURS b) {
    burs = b;
    regpool = b.ir.regpool;
  }

  @Override
  public final OPT_IR getIR() { return burs.ir; }

  protected final void EMIT(OPT_Instruction s) {
    burs.append(s);
  }

  // returns the given operand as a register
  protected static OPT_RegisterOperand R(OPT_Operand op) {
    return op.asRegister();
  }

  // returns the given operand as an address constant
  protected static OPT_AddressConstantOperand AC(OPT_Operand op) {
    return op.asAddressConstant();
  }

  // returns the given operand as an integer constant
  protected static OPT_IntConstantOperand IC(OPT_Operand op) {
    return op.asIntConstant();
  }

  // returns the given operand as a long constant
  protected static OPT_LongConstantOperand LC(OPT_Operand op) {
    return op.asLongConstant();
  }

  // returns the integer value of the given operand
  protected static int IV(OPT_Operand op) {
    return IC(op).value;
  }

  // returns the Address value of the given operand
  protected static Address AV(OPT_Operand op) {
    return AC(op).value;
  }

  // is a == 0?
  protected static boolean ZERO(OPT_Operand a) {
    return (IV(a) == 0);
  }

  // is a == 1?
  protected static boolean ONE(OPT_Operand a) {
    return (IV(a) == 1);
  }

  // is a == -1?
  protected static boolean MINUSONE(OPT_Operand a) {
    return (IV(a) == -1);
  }

  protected static int FITS(OPT_Operand op, int numBits, int trueCost) {
    return FITS(op, numBits, trueCost, INFINITE);
  }

  protected static int FITS(OPT_Operand op, int numBits, int trueCost, int falseCost) {
    if (op.isIntConstant() && OPT_Bits.fits(IV(op), numBits)) {
      return trueCost;
    } else if (op.isAddressConstant() && OPT_Bits.fits(AV(op), numBits)) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  protected static int isZERO(int x, int trueCost) {
    return isZERO(x, trueCost, INFINITE);
  }

  protected static int isZERO(int x, int trueCost, int falseCost) {
    return x == 0 ? trueCost : falseCost;
  }

  protected static int isONE(int x, int trueCost) {
    return isONE(x, trueCost, INFINITE);
  }

  protected static int isONE(int x, int trueCost, int falseCost) {
    return x == 1 ? trueCost : falseCost;
  }

  // helper functions for condition operands
  protected static boolean EQ_NE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) || (cond == OPT_ConditionOperand.NOT_EQUAL));
  }

  protected static boolean EQ_LT_LE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
            (cond == OPT_ConditionOperand.LESS) ||
            (cond == OPT_ConditionOperand.LESS_EQUAL));
  }

  protected static boolean EQ_GT_GE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
            (cond == OPT_ConditionOperand.GREATER) ||
            (cond == OPT_ConditionOperand.GREATER_EQUAL));
  }

  /* node accessors */
  protected static OPT_Instruction P(OPT_BURS_TreeNode p) {
    return p.getInstruction();
  }

  protected static OPT_Instruction PL(OPT_BURS_TreeNode p) {
    return p.child1.getInstruction();
  }

  protected static OPT_Instruction PLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLLLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLRL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLRLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLLRLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLRLLL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PLLR(OPT_BURS_TreeNode p) {
    return p.child1.child1.child2.getInstruction();
  }

  protected static OPT_Instruction PLLRL(OPT_BURS_TreeNode p) {
    return p.child1.child1.child2.child1.getInstruction();
  }

  protected static OPT_Instruction PLR(OPT_BURS_TreeNode p) {
    return p.child1.child2.getInstruction();
  }

  protected static OPT_Instruction PLRR(OPT_BURS_TreeNode p) {
    return p.child1.child2.child2.getInstruction();
  }

  protected static OPT_Instruction PR(OPT_BURS_TreeNode p) {
    return p.child2.getInstruction();
  }

  protected static OPT_Instruction PRL(OPT_BURS_TreeNode p) {
    return p.child2.child1.getInstruction();
  }

  protected static OPT_Instruction PRLL(OPT_BURS_TreeNode p) {
    return p.child2.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PRLLL(OPT_BURS_TreeNode p) {
    return p.child2.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PRLLLL(OPT_BURS_TreeNode p) {
    return p.child2.child1.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PRLLRLLL(OPT_BURS_TreeNode p) {
    return p.child2.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static OPT_Instruction PRLR(OPT_BURS_TreeNode p) {
    return p.child2.child1.child2.getInstruction();
  }

  protected static OPT_Instruction PRLRL(OPT_BURS_TreeNode p) {
    return p.child2.child1.child2.child1.getInstruction();
  }

  protected static OPT_Instruction PRR(OPT_BURS_TreeNode p) {
    return p.child2.child2.getInstruction();
  }

  protected static OPT_Instruction PRRL(OPT_BURS_TreeNode p) {
    return p.child2.child2.child1.getInstruction();
  }

  protected static int V(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p).value;
  }

  protected static int VL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1).value;
  }

  protected static int VLL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1).value;
  }

  protected static int VLLL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1).value;
  }

  protected static int VLLLL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child1).value;
  }

  protected static int VLLLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child2).value;
  }

  protected static int VLLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLLRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLLRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child2).value;
  }

  protected static int VLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child2).value;
  }

  protected static int VLLRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLRLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLRLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child1.child2.child2).value;
  }

  protected static int VLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2).value;
  }

  protected static int VLRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2.child1.child2).value;
  }

  protected static int VLRL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2.child1).value;
  }

  protected static int VLRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2.child2).value;
  }

  protected static int VLRLL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2.child1.child1).value;
  }

  protected static int VLRRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child1.child2.child2.child2).value;
  }

  protected static int VR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2).value;
  }

  protected static int VRL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1).value;
  }

  protected static int VRLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child2).value;
  }

  protected static int VRLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VRLLRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child2).value;
  }

  protected static int VRLRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child2.child1.child2).value;
  }

  protected static int VRLRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child2.child2).value;
  }

  protected static int VRLL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child1).value;
  }

  protected static int VRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child1.child2).value;
  }

  protected static int VRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child2).value;
  }

  protected static int VRRL(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child2.child1).value;
  }

  protected static int VRRLR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child2.child1.child2).value;
  }

  protected static int VRRR(OPT_BURS_TreeNode p) {
    return ((OPT_BURS_IntConstantTreeNode) p.child2.child2.child2).value;
  }
}
