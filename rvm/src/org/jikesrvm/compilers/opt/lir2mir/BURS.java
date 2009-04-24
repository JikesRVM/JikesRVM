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
 * A few common utilites used for invoking BURS tree-pattern matching
 * to do instruction selection.  The interesting code is in the
 * subclasses of this class.
 */
public abstract class BURS {

  public static final boolean DEBUG = false;

  protected final BURS_TreeNode NullTreeNode = new BURS_TreeNode(NULL_opcode);
  protected final BURS_TreeNode LongConstant = new BURS_TreeNode(LONG_CONSTANT_opcode);
  protected final BURS_TreeNode AddressConstant = new BURS_TreeNode(ADDRESS_CONSTANT_opcode);
  protected final BURS_TreeNode Register = new BURS_TreeNode(REGISTER_opcode);
  protected final BURS_TreeNode BranchTarget = new BURS_TreeNode(BRANCH_TARGET_opcode);

  // initialize scratch field for expression tree labeling.
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

  /**
   * Prepare to convert a block. Must be called before invoke.
   * @param bb
   */
  final void prepareForBlock(BasicBlock bb) {
    if (DEBUG) {
      VM.sysWrite("FINAL LIR\n");
      bb.printExtended();
    }
    lastInstr = bb.firstInstruction();
  }

  /**
   * Must be called after invoke for all non-empty blocks.
   * @param bb
   */
  final void finalizeBlock(BasicBlock bb) {
    lastInstr.BURS_backdoor_linkWithNext(bb.lastInstruction());
    lastInstr = null;
    if (DEBUG) {
      VM.sysWrite("INITIAL MIR\n");
      bb.printExtended();
    }
  }

  /**
   * append an instruction (in other words emit an MIR instruction)
   */
  public final void append(Instruction instruction) {
    lastInstr.BURS_backdoor_linkWithNext(instruction);
    lastInstr = instruction;
  }
}


