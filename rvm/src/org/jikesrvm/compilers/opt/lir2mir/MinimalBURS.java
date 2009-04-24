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

import org.jikesrvm.ArchitectureSpecificOpt.BURS_Debug;
import org.jikesrvm.ArchitectureSpecificOpt.BURS_STATE;
import org.jikesrvm.ArchitectureSpecificOpt.BURS_TreeNode;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OTHER_OPERAND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL_opcode;
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
 * dirty instruction selection for infrequntly executed basic blocks.
 *
 * @see BURS_STATE
 * @see BURS_TreeNode
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
   * Build BURS trees for dependence graph <code>bb</code>, label the trees, and
   * then generate MIR instructions based on the labeling.
   * @param bb   The dependence graph.   XXX Is this correct?
   */
  public void invoke(BasicBlock bb) {
    BURS_STATE burs = new BURS_STATE(this);
    for (InstructionEnumeration e = bb.forwardRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.next();
      BURS_TreeNode tn = buildTree(s);
      burs.label(tn);
      BURS_STATE.mark(tn, /* goalnt */(byte) 1);
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
   * any LIR instruction that has > 2 "real" operands e.g., a CALL.
   *
   * @param s The instruction for which a tree must be built
   */
  private BURS_TreeNode buildTree(Instruction s) {

    BURS_TreeNode root = new BURS_TreeNode(new DepGraphNode(s));
    BURS_TreeNode cur = root;
    for (OperandEnumeration uses = s.getUses(); uses.hasMoreElements();) {
      Operand op = uses.next();
      if (op == null) continue;

      // Set child = BURS_TreeNode for operand op
      BURS_TreeNode child;
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
        BURS_TreeNode child1 = cur.child2;
        BURS_TreeNode aux = new BURS_TreeNode(OTHER_OPERAND_opcode);
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

  // Generate code for a single tree root.
  private void generateTree(BURS_TreeNode k, BURS_STATE burs) {
    BURS_TreeNode child1 = k.child1;
    BURS_TreeNode child2 = k.child2;
    if (child1 != null) {
      if (child2 != null) {
        // k has two children; use register labeling to
        // determine order that minimizes register pressure
        if (k.isSuperNodeRoot()) {
          byte act = BURS_STATE.action[k.rule(k.getNonTerminal())];
          if ((act & BURS_STATE.RIGHT_CHILD_FIRST) != 0) {
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
      if (DEBUG) VM.sysWrite(k + " " + BURS_Debug.string[rule] + "\n");
    }
  }
}
