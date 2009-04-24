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

import org.jikesrvm.ArchitectureSpecificOpt.BURS_TreeNode;
import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterTools;
import org.jikesrvm.ArchitectureSpecificOpt.RegisterPool;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.util.Bits;
import org.vmmagic.unboxed.Address;

/**
 * Contains BURS helper functions common to all platforms.
 */
public abstract class BURS_Common_Helpers extends PhysicalRegisterTools {

  /** Infinte cost for a rule */
  protected static final int INFINITE = 0x7fff;

  /**
   * The burs object
   */
  protected final BURS burs;

  /**
   * The register pool of the IR being processed
   */
  protected final RegisterPool regpool;

  protected BURS_Common_Helpers(BURS b) {
    burs = b;
    regpool = b.ir.regpool;
  }

  @Override
  public final IR getIR() { return burs.ir; }

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
  protected static Instruction P(BURS_TreeNode p) {
    return p.getInstruction();
  }

  protected static Instruction PL(BURS_TreeNode p) {
    return p.child1.getInstruction();
  }

  protected static Instruction PLL(BURS_TreeNode p) {
    return p.child1.child1.getInstruction();
  }

  protected static Instruction PLLL(BURS_TreeNode p) {
    return p.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLL(BURS_TreeNode p) {
    return p.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLLLL(BURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLLLLL(BURS_TreeNode p) {
    return p.child1.child1.child1.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLLRL(BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLLLRLL(BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLLLRLLL(BURS_TreeNode p) {
    return p.child1.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLRLLL(BURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PLLR(BURS_TreeNode p) {
    return p.child1.child1.child2.getInstruction();
  }

  protected static Instruction PLLRL(BURS_TreeNode p) {
    return p.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLLRLL(BURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLLRLLR(BURS_TreeNode p) {
    return p.child1.child1.child2.child1.child1.child2.getInstruction();
  }

  protected static Instruction PLR(BURS_TreeNode p) {
    return p.child1.child2.getInstruction();
  }

  protected static Instruction PLRL(BURS_TreeNode p) {
    return p.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLRLL(BURS_TreeNode p) {
    return p.child1.child2.child1.child1.getInstruction();
  }

  protected static Instruction PLRLLRL(BURS_TreeNode p) {
    return p.child1.child2.child1.child1.child2.child1.getInstruction();
  }

  protected static Instruction PLRR(BURS_TreeNode p) {
    return p.child1.child2.child2.getInstruction();
  }

  protected static Instruction PR(BURS_TreeNode p) {
    return p.child2.getInstruction();
  }

  protected static Instruction PRL(BURS_TreeNode p) {
    return p.child2.child1.getInstruction();
  }

  protected static Instruction PRLL(BURS_TreeNode p) {
    return p.child2.child1.child1.getInstruction();
  }

  protected static Instruction PRLLL(BURS_TreeNode p) {
    return p.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLLLL(BURS_TreeNode p) {
    return p.child2.child1.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLLR(BURS_TreeNode p) {
    return p.child2.child1.child1.child2.getInstruction();
  }

  protected static Instruction PRLLRLLL(BURS_TreeNode p) {
    return p.child2.child1.child1.child2.child1.child1.child1.getInstruction();
  }

  protected static Instruction PRLR(BURS_TreeNode p) {
    return p.child2.child1.child2.getInstruction();
  }

  protected static Instruction PRLRL(BURS_TreeNode p) {
    return p.child2.child1.child2.child1.getInstruction();
  }

  protected static Instruction PRR(BURS_TreeNode p) {
    return p.child2.child2.getInstruction();
  }

  protected static Instruction PRRL(BURS_TreeNode p) {
    return p.child2.child2.child1.getInstruction();
  }

  protected static Instruction PRRR(BURS_TreeNode p) {
    return p.child2.child2.child2.getInstruction();
  }

  protected static int V(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p).value;
  }

  protected static int VL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1).value;
  }

  protected static int VLL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1).value;
  }

  protected static int VLLL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1).value;
  }

  protected static int VLLLL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1).value;
  }

  protected static int VLLLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLLLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2).value;
  }

  protected static int VLLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLLRLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLLRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child1.child2.child2).value;
  }

  protected static int VLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2).value;
  }

  protected static int VLLRLLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child2.child2).value;
  }

  protected static int VLLRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child2).value;
  }

  protected static int VLLRLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VLLRLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VLLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child1.child2.child2).value;
  }

  protected static int VLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2).value;
  }

  protected static int VLRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child2).value;
  }

  protected static int VLRL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1).value;
  }

  protected static int VLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child2).value;
  }

  protected static int VLRLL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child1).value;
  }

  protected static int VLRLLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child1.child1.child2.child2).value;
  }

  protected static int VLRRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child1.child2.child2.child2).value;
  }

  protected static int VR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2).value;
  }

  protected static int VRL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1).value;
  }

  protected static int VRLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2).value;
  }

  protected static int VRLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child1.child2).value;
  }

  protected static int VRLLRLLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child1.child1.child2).value;
  }

  protected static int VRLLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1.child2.child2).value;
  }

  protected static int VRLRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2.child1.child2).value;
  }

  protected static int VRLRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2.child2).value;
  }

  protected static int VRLL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child1).value;
  }

  protected static int VRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child1.child2).value;
  }

  protected static int VRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2).value;
  }

  protected static int VRRL(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child1).value;
  }

  protected static int VRRLR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child1.child2).value;
  }

  protected static int VRRR(BURS_TreeNode p) {
    return ((BURS_IntConstantTreeNode) p.child2.child2.child2).value;
  }
}
