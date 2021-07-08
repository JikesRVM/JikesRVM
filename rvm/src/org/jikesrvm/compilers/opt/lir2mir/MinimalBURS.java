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

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.IR;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OTHER_OPERAND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.ALIGNED_SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR_opcode;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.InlinedOsrTypeInfoOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

/**
 * This class contains code for quick and dirty instruction selection
 * by forcing each instruction to be a tree and generating the trees in
 * the same input as the input LIR instructions.
 * This results in poor code quality, but can be done very quickly.
 * The intended purpose is to reduce compile time by doing quick and
 * dirty instruction selection for infrequently executed basic blocks.
 *
 * @see BURS_StateCoder
 * @see AbstractBURS_TreeNode
 */
final class MinimalBURS extends BURS {

  /**
   * Create a BURS object for the given IR.
   *
   * @param ir the IR to translate from LIR to MIR.
   */
  MinimalBURS(IR ir) {
    super(ir);
  }

  /**
   * Build BURS trees for the basic block <code>bb</code>, label the trees, and
   * then generate MIR instructions based on the labeling.
   *
   * @param bb the basic block to process
   */
  public void invoke(BasicBlock bb) {
    BURS_StateCoder burs = makeCoder();
    for (Enumeration<Instruction> e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      AbstractBURS_TreeNode tn = buildTree(s);
      label(tn);
      mark(tn, /* goalnt */(byte) 1);
      generateTree(tn, burs);
    }
  }

  ////////////////////////////////
  // Implementation
  ////////////////////////////////

  /**
   * Build a BURS Tree for each Instruction.
   * Complete BURS trees by adding leaf nodes as needed, and
   * creating tree edges by calling insertChild1() or insertChild2()
   * This step is also where we introduce intermediate tree nodes for
   * any LIR instruction that has &gt; 2 "real" operands e.g., a CALL.
   *
   * @param s The instruction for which a tree must be built
   * @return the root of the newly constructed tree
   */
  private AbstractBURS_TreeNode buildTree(Instruction s) {
    AbstractBURS_TreeNode root = AbstractBURS_TreeNode.create(new DepGraphNode(s));
    AbstractBURS_TreeNode cur = root;
    for (Enumeration<Operand> uses = s.getUses(); uses.hasMoreElements();) {
      Operand op = uses.nextElement();
      if (op == null) continue;

      // Set child = AbstractBURS_TreeNode for operand op
      AbstractBURS_TreeNode child;
      if (op instanceof RegisterOperand) {
        if (op.asRegister().getRegister().isValidation()) continue;
        child = Register;
      } else if (op instanceof IntConstantOperand) {
        child = new BURS_IntConstantTreeNode(((IntConstantOperand) op).value);
      } else if (op instanceof LongConstantOperand) {
        child = LongConstant;
      } else if (op instanceof AddressConstantOperand) {
        child = AddressConstant;
      } else if (op instanceof BranchOperand && s.isCall()) {
        child = BranchTarget;
      } else if (op instanceof InlinedOsrTypeInfoOperand && s.isYieldPoint()) {
        child = NullTreeNode;
      } else {
        continue;
      }

      // Attach child as child of cur_parent in correct position
      if (cur.child1 == null) {
        cur.child1 = child;
      } else if (cur.child2 == null) {
        cur.child2 = child;
      } else {
        // Create auxiliary node so as to represent
        // a instruction with arity > 2 in a binary tree.
        AbstractBURS_TreeNode child1 = cur.child2;
        AbstractBURS_TreeNode aux = AbstractBURS_TreeNode.create(OTHER_OPERAND_opcode);
        cur.child2 = aux;
        cur = aux;
        cur.child1 = child1;
        cur.child2 = child;
      }
    }

    // patch for calls & return
    switch (s.getOpcode()) {
      case CALL_opcode:
      case SYSCALL_opcode:
      case ALIGNED_SYSCALL_opcode:
      case YIELDPOINT_OSR_opcode:
        if (cur.child2 == null) {
          cur.child2 = NullTreeNode;
        }
        // fall through
      case RETURN_opcode:
        if (cur.child1 == null) {
          cur.child1 = NullTreeNode;
        }
    }
    return root;
  }



  /**
   * Generates code for a single tree root.
   * @param k the root to start generation at
   * @param burs the current BURS state
   */
  private void generateTree(AbstractBURS_TreeNode k, BURS_StateCoder burs) {
    AbstractBURS_TreeNode child1 = k.child1;
    AbstractBURS_TreeNode child2 = k.child2;
    if (child1 != null) {
      if (child2 != null) {
        // k has two children; use register labeling to
        // determine order that minimizes register pressure
        if (k.isSuperNodeRoot()) {
          byte act = action(k.rule(k.getNonTerminal()));
          if ((act & BURS_StateCoder.RIGHT_CHILD_FIRST) != 0) {
            // rule selected forces order of evaluation
            generateTree(child2, burs);
            generateTree(child1, burs);
          } else {
            generateTree(child1, burs);
            generateTree(child2, burs);
          }
        } else {
          generateTree(child1, burs);
          generateTree(child2, burs);
        }
      } else {
        generateTree(child1, burs);
      }
    } else if (child2 != null) {
      generateTree(child2, burs);
    }

    if (k.isSuperNodeRoot()) {
      int nonterminal = k.getNonTerminal();
      int rule = k.rule(nonterminal);
      burs.code(k, nonterminal, rule);
      if (DEBUG) VM.sysWriteln(k + " " + debug(rule));
    }
  }
}
