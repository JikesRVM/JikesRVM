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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.ADDRESS_CONSTANT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BRANCH_TARGET_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_CONSTANT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REGISTER_opcode;

/**
 * A few common utilities used for invoking BURS tree-pattern matching
 * to do instruction selection.  The interesting code is in the
 * subclasses of this class.
 */
public abstract class BURS {

  public static final boolean DEBUG = false;

  protected final AbstractBURS_TreeNode NullTreeNode = AbstractBURS_TreeNode.create(NULL_opcode);
  protected final AbstractBURS_TreeNode LongConstant = AbstractBURS_TreeNode.create(LONG_CONSTANT_opcode);
  protected final AbstractBURS_TreeNode AddressConstant = AbstractBURS_TreeNode.create(ADDRESS_CONSTANT_opcode);
  protected final AbstractBURS_TreeNode Register = AbstractBURS_TreeNode.create(REGISTER_opcode);
  protected final AbstractBURS_TreeNode BranchTarget = AbstractBURS_TreeNode.create(BRANCH_TARGET_opcode);

  BURS(IR ir) {
    this.ir = ir;
    NullTreeNode.setNumRegisters(0);
    LongConstant.setNumRegisters(0);
    AddressConstant.setNumRegisters(0);
    Register.setNumRegisters(1);
    BranchTarget.setNumRegisters(0);
  }

  public final IR ir;
  protected Instruction lastInstr;

  /** @return the architecture dependent BURS coder */
  BURS_StateCoder makeCoder() {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_STATE(this);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_STATE(this);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_STATE(this);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_STATE(this);
      }
    }
  }

  /**
   * Recursively labels the tree with costs.
   * @param tn the tree to label
   */
  static void label(AbstractBURS_TreeNode tn) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_STATE.label(tn);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_STATE.label(tn);
      }
    } else {
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_STATE.label(tn);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_STATE.label(tn);
      }
    }
  }

  /**
   * Traverses the tree, marking the non-terminal to be generated for each
   * sub-tree.
   *
   * @param tn the tree to traverse
   * @param goalnt the goal
   */
  static void mark(AbstractBURS_TreeNode tn, byte goalnt) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_STATE.mark(tn, goalnt);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_STATE.mark(tn, goalnt);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_STATE.mark(tn, goalnt);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_STATE.mark(tn, goalnt);
      }
    }
  }

  /**
   *  @param rule the rule's number
   *  @return the action associated with a rule
   */
  static byte action(int rule) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        return org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_STATE.action(rule);
      } else {
        return org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_STATE.action(rule);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        return org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_STATE.action(rule);
      } else {
        return org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_STATE.action(rule);
      }
    }
  }

  /**
   * Dumps the tree for debugging.
   * @param tn the root of the tree to be dumped
   */
  static void dumpTree(AbstractBURS_TreeNode tn) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_STATE.dumpTree(tn);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_STATE.dumpTree(tn);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_STATE.dumpTree(tn);
      } else {
        org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_STATE.dumpTree(tn);
      }
    }
  }

  /**
   * @param rule the rule's number
   * @return debug string for a particular rule
   */
  static String debug(int rule) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        return org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_Debug.string[rule];
      } else {
        return org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_Debug.string[rule];
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        return org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_Debug.string[rule];
      } else {
        return org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_Debug.string[rule];
      }
    }
  }

  /**
   * Prepares for conversion of a block. This method must be called before
   * using an invoke method from the subclasses.
   *
   * @param bb a basic block
   */
  final void prepareForBlock(BasicBlock bb) {
    if (DEBUG) {
      VM.sysWriteln("FINAL LIR");
      bb.printExtended();
    }
    lastInstr = bb.firstInstruction();
  }

  /**
   * Finalizes a block. This method must be called after
   * using an invoke method for all non-empty blocks.
   *
   * @param bb a basic block
   */
  final void finalizeBlock(BasicBlock bb) {
    lastInstr.BURS_backdoor_linkWithNext(bb.lastInstruction());
    lastInstr = null;
    if (DEBUG) {
      VM.sysWriteln("INITIAL MIR");
      bb.printExtended();
    }
  }

  /**
   * Appends an instruction, i.e. emits an MIR instruction.
   *
   * @param instruction the instruction to emit
   */
  public final void append(Instruction instruction) {
    lastInstr.BURS_backdoor_linkWithNext(instruction);
    lastInstr = instruction;
  }
}


