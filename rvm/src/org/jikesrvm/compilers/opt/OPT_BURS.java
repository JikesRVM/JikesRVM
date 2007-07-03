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
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADDRESS_CONSTANT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BRANCH_TARGET_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_CONSTANT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NULL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REGISTER_opcode;

/**
 * A few common utilites used for invoking BURS tree-pattern matching
 * to do instruction selection.  The interesting code is in the
 * subclasses of this class.
 */
public abstract class OPT_BURS {

  public static final boolean DEBUG = false;

  protected final OPT_BURS_TreeNode NullTreeNode = new OPT_BURS_TreeNode(NULL_opcode);
  protected final OPT_BURS_TreeNode LongConstant = new OPT_BURS_TreeNode(LONG_CONSTANT_opcode);
  protected final OPT_BURS_TreeNode AddressConstant = new OPT_BURS_TreeNode(ADDRESS_CONSTANT_opcode);
  protected final OPT_BURS_TreeNode Register = new OPT_BURS_TreeNode(REGISTER_opcode);
  protected final OPT_BURS_TreeNode BranchTarget = new OPT_BURS_TreeNode(BRANCH_TARGET_opcode);

  // initialize scratch field for expression tree labeling.
  OPT_BURS(OPT_IR ir) {
    this.ir = ir;
    NullTreeNode.setNumRegisters(0);
    LongConstant.setNumRegisters(0);
    AddressConstant.setNumRegisters(0);
    Register.setNumRegisters(1);
    BranchTarget.setNumRegisters(0);
  }

  public OPT_IR ir;
  protected OPT_Instruction lastInstr;

  /**
   * Prepare to convert a block. Must be called before invoke.
   * @param bb
   */
  final void prepareForBlock(OPT_BasicBlock bb) {
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
  final void finalizeBlock(OPT_BasicBlock bb) {
    lastInstr.BURS_KLUDGE_linkWithNext(bb.lastInstruction());
    lastInstr = null;
    if (DEBUG) {
      VM.sysWrite("INITIAL MIR\n");
      bb.printExtended();
    }
  }

  /**
   * append an instruction (in other words emit an MIR instruction)
   */
  public final void append(OPT_Instruction instruction) {
    lastInstr.BURS_KLUDGE_linkWithNext(instruction);
    lastInstr = instruction;
  }
}


