/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$

package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class contains code for quick and dirty instruction selection
 * by forcing each instruction to be a tree and generating the trees in
 * the same input as the input LIR instructions. 
 * This results in poor code quality, but can be done very quickly.
 * The intended purpose is to reduce compile time by doing quick and 
 * dirty instruction selection for infrequntly executed basic blocks.
 *
 * @see OPT_BURS_STATE
 * @see OPT_BURS_TreeNode
 *
 * @author Dave Grove
 * @author Vivek Sarkar
 * @author Mauricio Serrano
 */
final class OPT_MinimalBURS extends OPT_BURS {

  /**
   * Create a BURS object for the given IR.
   * 
   * @param IR the IR to translate from LIR to MIR.
   */
  OPT_MinimalBURS (OPT_IR IR) {
    ir = IR;
  }

  /**
   * Build BURS trees for dependence graph <code>bb</code>, label the trees, and
   * then generate MIR instructions based on the labeling.
   * @param bb   The dependence graph.   XXX Is this correct?
   */
  public void invoke (OPT_BasicBlock bb) {
    OPT_BURS_STATE burs = new OPT_BURS_STATE(this);
    for (OPT_InstructionEnumeration e = bb.forwardRealInstrEnumerator();
         e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      OPT_BURS_TreeNode tn = buildTree(s);
      burs.label(tn);
      OPT_BURS_STATE.mark(tn, /* goalnt */(byte)1);
      generateTree(tn, burs);
    }
  }


  ////////////////////////////////
  // Implementation 
  ////////////////////////////////

  /**
   * Build a BURS Tree for each OPT_Instruction.
   * Complete BURS trees by adding leaf nodes as needed, and
   * creating tree edges by calling insertChild1() or insertChild2()
   * This step is also where we introduce intermediate tree nodes for 
   * any LIR instruction that has > 2 "real" operands e.g., a CALL.
   * 
   * @param s The instruction for which a tree must be built
   */
  private OPT_BURS_TreeNode buildTree(OPT_Instruction s) {
    
    OPT_BURS_TreeNode root = new OPT_BURS_TreeNode(new OPT_DepGraphNode(s));
    OPT_BURS_TreeNode cur = root;
    for (OPT_OperandEnumeration uses = s.getUses(); uses.hasMoreElements();) {
      OPT_Operand op = uses.next();
      if (op == null) continue;

      op.clear();
      // Set child = OPT_BURS_TreeNode for operand op
      OPT_BURS_TreeNode child;
      if (op instanceof OPT_RegisterOperand) {
        if (op.asRegister().register.isValidation()) continue;
        child = Register; 
      } else if (op instanceof OPT_IntConstantOperand) {
        child = new OPT_BURS_IntConstantTreeNode(((OPT_IntConstantOperand)op).value);
      } else if (op instanceof OPT_LongConstantOperand) {
        child = LongConstant;
      } else if (op instanceof OPT_AddressConstantOperand) {
        child = AddressConstant;
      } else if (op instanceof OPT_BranchOperand && s.isCall()) {
        child = BranchTarget;
      //-#if RVM_WITH_OSR
      } else if (op instanceof OPT_InlinedOsrTypeInfoOperand && s.isYieldPoint()) {
        child = NullTreeNode;
      //-#endif 
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
        OPT_BURS_TreeNode child1 = cur.child2;
        OPT_BURS_TreeNode aux = new OPT_BURS_TreeNode(OTHER_OPERAND_opcode);
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
    //-#if RVM_WITH_OSR
    case YIELDPOINT_OSR_opcode:
    //-#endif
      if (cur.child2 == null)
        cur.child2 = NullTreeNode;
      // fall through
    case RETURN_opcode:
      if (cur.child1 == null)
        cur.child1 = NullTreeNode;
    }
    return root;
  }

  // Generate code for a single tree root.
  private void generateTree(OPT_BURS_TreeNode k, OPT_BURS_STATE burs) {
    OPT_BURS_TreeNode child1 = k.child1;
    OPT_BURS_TreeNode child2 = k.child2;
    if (child1 != null) {
      if (child2 != null) {
        // k has two children; use register labeling to
        // determine order that minimizes register pressure
        if (k.isSuperNodeRoot()) {
          byte act = OPT_BURS_STATE.action[k.rule(k.getNonTerminal())];
          if ((act & OPT_BURS_STATE.RIGHT_CHILD_FIRST) != 0) {
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
      if (DEBUG) VM.sysWrite(k + " " + OPT_BURS_Debug.string[rule] + "\n");
    }
  }
}


