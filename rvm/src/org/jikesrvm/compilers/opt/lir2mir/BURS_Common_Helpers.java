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
package org.jikesrvm.compilers.opt.lir2mir;

import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.GenericRegisterPool;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.util.Bits;
import org.vmmagic.unboxed.Address;

/**
 * Contains BURS helper functions common to all platforms.
 */
public abstract class BURS_Common_Helpers extends GenericPhysicalRegisterTools {

  /** Infinite cost for a rule */
  protected static final int INFINITE = 0x7fff;

  /**
   * The BURS object
   */
  protected final BURS burs;

  /**
   * The register pool of the IR being processed
   */
  protected final GenericRegisterPool regpool;

  protected BURS_Common_Helpers(BURS b) {
    burs = b;
    regpool = b.ir.regpool;
  }

  @Override
  public final IR getIR() {
    return burs.ir;
  }

  protected final void EMIT(Instruction s) {
    burs.append(s);
  }

  // returns the given operand as a register
  protected static RegisterOperand R(Operand op) {
    return op.asRegister();
  }

  // returns the given operand as an address constant
  protected static AddressConstantOperand AC(Operand op) {
    return op.asAddressConstant();
  }

  // returns the given operand as an integer constant
  protected static IntConstantOperand IC(Operand op) {
    return op.asIntConstant();
  }

  // returns the given operand as a long constant
  protected static LongConstantOperand LC(Operand op) {
    return op.asLongConstant();
  }

  // returns the integer value of the given operand
  protected static int IV(Operand op) {
    return IC(op).value;
  }

  // returns the long value of the given operand
  protected static long LV(Operand op) {
    return LC(op).value;
  }

  // returns the Address value of the given operand
  protected static Address AV(Operand op) {
    return AC(op).value;
  }

  // is a == 0?
  protected static boolean ZERO(Operand a) {
    return (IV(a) == 0);
  }

  // is a == 1?
  protected static boolean ONE(Operand a) {
    return (IV(a) == 1);
  }

  // is a == -1?
  protected static boolean MINUSONE(Operand a) {
    return (IV(a) == -1);
  }

  protected static int FITS(Operand op, int numBits, int trueCost) {
    return FITS(op, numBits, trueCost, INFINITE);
  }

  protected static int FITS(Operand op, int numBits, int trueCost, int falseCost) {
    if (op.isIntConstant() && Bits.fits(IV(op), numBits)) {
      return trueCost;
    } else if (op.isAddressConstant() && Bits.fits(AV(op), numBits)) {
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
  protected static boolean EQ_NE(ConditionOperand c) {
    int cond = c.value;
    return ((cond == ConditionOperand.EQUAL) || (cond == ConditionOperand.NOT_EQUAL));
  }

  protected static boolean EQ_LT_LE(ConditionOperand c) {
    int cond = c.value;
    return ((cond == ConditionOperand.EQUAL) ||
            (cond == ConditionOperand.LESS) ||
            (cond == ConditionOperand.LESS_EQUAL));
  }

  protected static boolean EQ_GT_GE(ConditionOperand c) {
    int cond = c.value;
    return ((cond == ConditionOperand.EQUAL) ||
            (cond == ConditionOperand.GREATER) ||
            (cond == ConditionOperand.GREATER_EQUAL));
  }

  /* node accessors */
  protected static Instruction P(AbstractBURS_TreeNode p) {
    return p.getInstruction();
  }

  protected static Instruction PL(AbstractBURS_TreeNode p) {
    return p.child1.getInstruction();
  }

  protected static Instruction PLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.getInstruction();
  }

  protected static Instruction PLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLLLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLRL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLLLRLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLLLRLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLRLLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLR(AbstractBURS_TreeNode p) {
    return p.child1.child1.child2.getInstruction();
  }

  protected static Instruction PLLRL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLLRLL(AbstractBURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLLRLLR(AbstractBURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.child2.getInstruction();
  }

  protected static Instruction PLR(AbstractBURS_TreeNode p) {
    return p.child1.child2.getInstruction();
  }

  protected static Instruction PLRL(AbstractBURS_TreeNode p) {
    return p.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLRLL(AbstractBURS_TreeNode p) {
    return p.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLRLLRL(AbstractBURS_TreeNode p) {
    return p.child1.child2.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLRR(AbstractBURS_TreeNode p) {
    return p.child1.child2.child2.getInstruction();
  }

  protected static Instruction PR(AbstractBURS_TreeNode p) {
    return p.child2.getInstruction();
  }

  protected static Instruction PRL(AbstractBURS_TreeNode p) {
    return p.child2.child1.getInstruction();
  }

  protected static Instruction PRLL(AbstractBURS_TreeNode p) {
    return p.child2.child1.child1.getInstruction();
  }

  protected static Instruction PRLLL(AbstractBURS_TreeNode p) {
    return p.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLLLL(AbstractBURS_TreeNode p) {
    return p.child2.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLLR(AbstractBURS_TreeNode p) {
    return p.child2.child1.child1.child2.getInstruction();
  }

  protected static Instruction PRLLRLLL(AbstractBURS_TreeNode p) {
    return p.child2.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLR(AbstractBURS_TreeNode p) {
    return p.child2.child1.child2.getInstruction();
  }

  protected static Instruction PRLRL(AbstractBURS_TreeNode p) {
    return p.child2.child1.child2.child1.getInstruction();
  }

  protected static Instruction PRR(AbstractBURS_TreeNode p) {
    return p.child2.child2.getInstruction();
  }

  protected static Instruction PRRL(AbstractBURS_TreeNode p) {
    return p.child2.child2.child1.getInstruction();
  }

  protected static Instruction PRRR(AbstractBURS_TreeNode p) {
    return p.child2.child2.child2.getInstruction();
  }

  protected static int V(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p).value;
  }

  protected static int VL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1).value;
  }

  protected static int VLL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1).value;
  }

  protected static int VLLL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1).value;
  }

  protected static int VLLLL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1).value;
  }

  protected static int VLLLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2).value;
  }

  protected static int VLLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLLRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child2).value;
  }

  protected static int VLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2).value;
  }

  protected static int VLLRLLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child2.child2).value;
  }

  protected static int VLLRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLRLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLRLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child2).value;
  }

  protected static int VLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2).value;
  }

  protected static int VLRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child2).value;
  }

  protected static int VLRL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1).value;
  }

  protected static int VLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child2).value;
  }

  protected static int VLRLL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child1).value;
  }

  protected static int VLRLLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child1.child2.child2).value;
  }

  protected static int VLRRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child2.child2).value;
  }

  protected static int VR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2).value;
  }

  protected static int VRL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1).value;
  }

  protected static int VRLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2).value;
  }

  protected static int VRLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VRLLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child2).value;
  }

  protected static int VRLRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2.child1.child2).value;
  }

  protected static int VRLRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2.child2).value;
  }

  protected static int VRLL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1).value;
  }

  protected static int VRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2).value;
  }

  protected static int VRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2).value;
  }

  protected static int VRRL(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child1).value;
  }

  protected static int VRRLR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child1.child2).value;
  }

  protected static int VRRR(AbstractBURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child2).value;
  }
}
