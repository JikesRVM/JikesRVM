/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import  java.io.*;
import com.ibm.JikesRVM.opt.ir.instructionFormats.*;

/**
 * A few common utilites used for invoking BURS tree-pattern matching
 * to do instruction selection.  The interesting code is in the
 * subclasses of this class.
 *
 * @author Dave Grove
 * @author Vivek Sarkar
 * @author Mauricio Serrano
 */
public abstract class OPT_BURS implements OPT_Operators {

  protected static final boolean DEBUG = false;

  protected static final OPT_BURS_TreeNode NullTreeNode = 
    new OPT_BURS_TreeNode(NULL_opcode);
  protected static final OPT_BURS_TreeNode IntConstant = 
    new OPT_BURS_TreeNode(INT_CONSTANT_opcode);
  //-#if RVM_FOR_IA32
  // TODO: The only reason these are in an IA32 ifdef is that the
  // ppc grammar only uses one nonterminal.  Once that gets fixed,
  // we could also use these on PPC to make the rules simpler.
  protected static final OPT_BURS_TreeNode IntConstantMinusOne = 
    new OPT_BURS_TreeNode(MINUS_ONE_opcode);
  protected static final OPT_BURS_TreeNode IntConstantZero = 
    new OPT_BURS_TreeNode(ZERO_opcode);
  protected static final OPT_BURS_TreeNode IntConstantOne = 
    new OPT_BURS_TreeNode(ONE_opcode);
  //-#endif
  protected static final OPT_BURS_TreeNode LongConstant = 
    new OPT_BURS_TreeNode(LONG_CONSTANT_opcode);
  protected static final OPT_BURS_TreeNode Register = 
    new OPT_BURS_TreeNode(REGISTER_opcode);
  protected static final OPT_BURS_TreeNode BranchTarget = 
    new OPT_BURS_TreeNode(BRANCH_TARGET_opcode);

  // initialize scratch field for expression tree labeling.
  static {
    NullTreeNode.setNumRegisters(0);
    IntConstant.setNumRegisters(0);
    //-#if RVM_FOR_IA32
    IntConstantMinusOne.setNumRegisters(0);
    IntConstantZero.setNumRegisters(0);
    IntConstantOne.setNumRegisters(0);
    //-#endif
    LongConstant.setNumRegisters(0);
    Register.setNumRegisters(1);
    BranchTarget.setNumRegisters(0);
  }

  public OPT_IR ir;
  protected OPT_Instruction lastInstr;

  /**
   * Prepare to convert a block. Must be called before invoke.
   * @param bb
   */
  final void prepareForBlock (OPT_BasicBlock bb) {
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
  final void finalizeBlock (OPT_BasicBlock bb) {
    lastInstr.linkWithNext(bb.lastInstruction());
    lastInstr = null;
    if (DEBUG) {
      VM.sysWrite("INITIAL MIR\n");
      bb.printExtended();
    }
  }

  /**
   * append an instruction (in other words emit an MIR instruction)
   */
  public final void append (OPT_Instruction instruction) {
    lastInstr.linkWithNext(instruction);
    lastInstr = instruction;
  }
}


