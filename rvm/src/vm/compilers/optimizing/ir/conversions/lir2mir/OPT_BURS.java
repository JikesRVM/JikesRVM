/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.io.*;
import instructionFormats.*;

/**
 * This class contains methods for invoking BURS tree-pattern matching
 * from the OPT Compiler.  BURS is invoked on trees obtained from the
 * dependence graph of a basic block.
 *
 * @see OPT_DepGraph
 * @see OPT_BURS_STATE
 * @see OPT_BURS_TreeNode
 *
 * @author Dave Grove
 * @author Vivek Sarkar
 * @author Mauricio Serrano
 */
final class OPT_BURS implements OPT_Operators {

  static final boolean DEBUG = false;

  private static final OPT_BURS_TreeNode NullTreeNode = 
    new OPT_BURS_TreeNode(NULL_opcode);
  private static final OPT_BURS_TreeNode IntConstant = 
    new OPT_BURS_TreeNode(INT_CONSTANT_opcode);
  //-#if RVM_FOR_IA32
  // TODO: The only reason these are in an IA32 ifdef is that the
  // ppc grammar only uses one nonterminal.  Once that gets fixed,
  // we could also use these on PPC to make the rules simpler.
  private static final OPT_BURS_TreeNode IntConstantMinusOne = 
    new OPT_BURS_TreeNode(MINUS_ONE_opcode);
  private static final OPT_BURS_TreeNode IntConstantZero = 
    new OPT_BURS_TreeNode(ZERO_opcode);
  private static final OPT_BURS_TreeNode IntConstantOne = 
    new OPT_BURS_TreeNode(ONE_opcode);
  //-#endif
  private static final OPT_BURS_TreeNode LongConstant = 
    new OPT_BURS_TreeNode(LONG_CONSTANT_opcode);
  private static final OPT_BURS_TreeNode Register = 
    new OPT_BURS_TreeNode(REGISTER_opcode);
  private static final OPT_BURS_TreeNode BranchTarget = 
    new OPT_BURS_TreeNode(BRANCH_TARGET_opcode);

  // initialize scratch field for expression tree labeling.
  static {
    NullTreeNode.setNumRegisters(0);
    IntConstant.setNumRegisters(0);
    //-#if RVM_FOR_IA32
    IntConstantMinusOne.setNumRegisters(0);;
    IntConstantZero.setNumRegisters(0);
    IntConstantOne.setNumRegisters(0);
    //-#endif
    LongConstant.setNumRegisters(0);
    Register.setNumRegisters(1);
    BranchTarget.setNumRegisters(0);
  }


  OPT_IR ir;
  private OPT_Instruction lastInstr;
  private int numTreeRoots;

  /**
   * Create a BURS object for the given IR.
   * 
   * @param IR the IR to translate from LIR to MIR.
   */
  OPT_BURS (OPT_IR IR) {
    ir = IR;
  }

  /**
   * Prepare to convert a block. Must be called before invoke.
   * @param bb
   */
  public void prepareForBlock (OPT_BasicBlock bb) {
    if (DEBUG) {
      VM.sysWrite("BLOCK\n");
      bb.printExtended();
    }
    lastInstr = bb.firstInstruction();
  }

  /**
   * Must be called after invoke for all non-empty blocks.
   * @param bb
   */
  public void finalizeBlock (OPT_BasicBlock bb) {
    lastInstr.linkWithNext(bb.lastInstruction());
    lastInstr = null;
  }


  /**
   * Build BURS trees for dependence graph dg, label the trees, and
   * then generate MIR instructions based on the labeling.
   * @param dg the dependence graph.
   */
  public void invoke (OPT_DepGraph dg) {
    if (DEBUG) dg.printDepGraph();
    OPT_BURS_STATE burs = new OPT_BURS_STATE(this);
    completeTrees(dg, burs);
    labelTrees(dg, burs);
    orderTrees(dg, burs);
    generateTrees(dg, burs);
  }


  ////////////////////////////////
  // Implementation 
  ////////////////////////////////

  /**
   * append an instruction (in other words emit an MIR instruction)
   */
  void append (OPT_Instruction instruction) {
    lastInstr.linkWithNext(instruction);
    lastInstr = instruction;
  }


  /**
   * Stage 1: Complete the expression trees and identify tree roots.
   * Complete BURS trees by adding leaf nodes as needed, and
   * creating tree edges by calling insertChild1() or insertChild2()
   * This step is also where we introduce intermediate tree nodes for 
   * any LIR instruction that has > 2 "real" operands e.g., a CALL.
   * We also mark nodes that must be tree roots.
   * 
   * @param dg  The dependence graph. 
   * @param burs the OPT_BURS_STATE object.
   */
  private void completeTrees(OPT_DepGraph dg, OPT_BURS_STATE burs) {
    OPT_DepGraphNode bbNodes = (OPT_DepGraphNode)dg.firstNode();
    OPT_Instruction firstInstruction = bbNodes.instruction();
    for (OPT_DepGraphNode n = bbNodes; 
	 n != null; 
	 n = (OPT_DepGraphNode)n.getNext()) {
      // Initialize n.treeNode
      OPT_BURS_TreeNode cur_parent = new OPT_BURS_TreeNode(n);
      n.scratchObject = cur_parent;
      OPT_Instruction instr = n.instruction();
      // cur_parent = current parent node for var length IR instructions
      // loop for USES of an instruction
      for (OPT_OperandEnumeration uses = instr.getUses(); 
	   uses.hasMoreElements();) {
        // Create tree edge for next use.
        OPT_Operand op = uses.next();
        if (op == null) continue;

	op.clear();
        // Set child = OPT_BURS_TreeNode for operand op
        OPT_BURS_TreeNode child;
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand regOp = (OPT_RegisterOperand)op;
          // ignore validation registers
          if (regOp.register.isValidation()) continue;
          OPT_DepGraphEdge e = OPT_DepGraphEdge.findInputEdge(n, op);
          if (e == null) {        // operand is leaf	    
            child = Register; 
	  } else {
            OPT_BURS_TreeNode fromNode = 
	      (OPT_BURS_TreeNode)e.fromNode().scratchObject;
            if (fromNode.isTreeRoot()) { // operand is leaf
              child = Register; 
	    } else {            // Set child = tree node created for fromNode
	      child = fromNode;
	    }
	  }
        } else if (op instanceof OPT_IntConstantOperand) {
          child = IntConstant;                  // generic INT_CONSTANT
	  //-#if RVM_FOR_IA32
	  switch (((OPT_IntConstantOperand)op).value) {
	  case -1: child = IntConstantMinusOne; break;
	  case  0: child = IntConstantZero; break;
	  case  1: child = IntConstantOne; break;
	  }
	  //-#endif
        } else if (op instanceof OPT_LongConstantOperand) {
          child = LongConstant;
        } else if (op instanceof OPT_BranchOperand && instr.isCall()) {
	  child = BranchTarget;
        } else {
	  continue;
        }

        // Attach child as child of cur_parent in correct position
        if (cur_parent.child1 == null) {
          cur_parent.child1 = child; 
	} else if (cur_parent.child2 == null) {
          cur_parent.child2 = child; 
	} else {
          // Create auxiliary node so as to represent
          // a instruction with arity > 2 in a binary tree.
          OPT_BURS_TreeNode child1 = cur_parent.child2;
          OPT_BURS_TreeNode aux = new OPT_BURS_TreeNode(OTHER_OPERAND_opcode);
          cur_parent.child2 = aux;
          cur_parent = aux;
          cur_parent.child1 = child1;
          cur_parent.child2 = child;
        }
      }         // for (uses = ...)

      // patch for calls & return 
      switch (instr.getOpcode()) {
      case CALL_opcode:
      case SYSCALL_opcode:
	if (cur_parent.child2 == null)
	  cur_parent.child2 = NullTreeNode;
	// fall through
      case RETURN_opcode:
	if (cur_parent.child1 == null)
	  cur_parent.child1 = NullTreeNode;
      }

      if (mustBeTreeRoot(n)) {
        OPT_BURS_TreeNode treeNode = (OPT_BURS_TreeNode)n.scratchObject;
        treeNode.setTreeRoot();
	numTreeRoots++;
        if (DEBUG) {
          VM.sysWrite("DEBUG DUMP OF TREE ROOTED at "+n.instruction()+"\n");
          OPT_BURS_STATE.dumpTree(treeNode);
          VM.sysWrite('\n');
        }
      }
    } 
  }


  /**
   * Stage2: Label the trees with their min cost cover.
   * @param dg  The dependence graph. 
   * @param burs the OPT_BURS_STATE object.
   */
  private void labelTrees(OPT_DepGraph dg, OPT_BURS_STATE burs) {
    int i = 0;
    for (OPT_SpaceEffGraphNode node = dg.firstNode(); 
	 node != null; 
	 node = node.getNext()) {
      OPT_BURS_TreeNode n = (OPT_BURS_TreeNode)node.scratchObject;
      if (n.isTreeRoot()) {
        burs.label(n);
        OPT_BURS_STATE.mark(n, /* goalnt */(byte)1);
        if (DEBUG) {
          VM.sysWrite("START OF PROCESSING FOR TREE #" + (++i) + ": ");
          OPT_BURS_STATE.dumpTree(n);
          VM.sysWrite("\nEND OF PROCESSING FOR TREE #" + i + "\n");
        }
      }
    }
  }


  /**
   * Stage 3: Construct topological ordering of tree roots based on the
   * dependencies between nodes in the tree. 
   * 
   * @param dg  The dependence graph. 
   * @param burs the OPT_BURS_STATE object.
   */
  private void orderTrees(OPT_DepGraph dg, OPT_BURS_STATE burs) {
    // Initialize tree root field for all nodes
    if (DEBUG) VM.sysWrite("Setting tree roots\n");
    for (OPT_SpaceEffGraphNode node = dg.firstNode(); 
	 node != null; 
	 node = node.getNext()) {
      OPT_BURS_TreeNode n = (OPT_BURS_TreeNode)node.scratchObject;
      if (n.isTreeRoot()) {
	node.scratch = 0; 
        initTreeRootNode(n, node);
      } else {
	node.scratch = -1;
      }
    }

    // Initialize predCount[*]
    for (OPT_SpaceEffGraphNode node = dg.firstNode(); 
	 node != null; 
	 node = node.getNext()) {
      OPT_SpaceEffGraphNode n_treeRoot = node.nextSorted;
      for (OPT_SpaceEffGraphEdge in = node.firstInEdge(); 
	   in != null; 
	   in = in.getNextIn()) {
        OPT_SpaceEffGraphNode source_treeRoot = in.fromNode().nextSorted;
        if (source_treeRoot != n_treeRoot)
          n_treeRoot.scratch++;
      }
    }
    if (DEBUG) {
      VM.sysWrite("predcounts:\n");
      for (OPT_SpaceEffGraphNode n = dg.firstNode();
	   n != null; 
	   n = n.getNext()) {
	if (((OPT_BURS_TreeNode)n.scratchObject).isTreeRoot()) {
	  VM.sysWrite(n.scratch + ":" + n + "\n");
	}
      }
    }
  }

  /**
   * Stage 4: Visit the tree roots in topological order and 
   * emit MIR instructions by calling OPT_BURS_STATE.code on each
   * supernode in the tree.
   * 
   * @param dg  The dependence graph. 
   * @param burs the OPT_BURS_STATE object.
   */
  private void generateTrees(OPT_DepGraph dg, OPT_BURS_STATE burs) {
    // Append tree roots with predCount = 0 to readyStack
    for (OPT_SpaceEffGraphNode n = dg.lastNode();
	 n != null; 
	 n = n.getPrev()) {
      if (n.scratch == 0) {
	readyStackPush((OPT_BURS_TreeNode)n.scratchObject);
      }
    }
    // Emit code for each tree root in readyStack
    while (readyStackNotEmpty()) {
      OPT_BURS_TreeNode k = readyStackPop();
      // Invoke burs.code on the supernodes of k in a post order walk of the
      // tree (thus code for children is emited before code for the parent).
      if (DEBUG) VM.sysWrite("PROCESSING FOR TREEROOT #" + k.dg_node + "\n");
      numTreeRoots--;
      generateTree(k, burs);
      if (DEBUG) VM.sysWrite("END OF PROCESSING FOR TREEROOT #\n");
    }  
    if (numTreeRoots != 0)
      throw new OPT_OptimizingCompilerException("BURS", 
						"Not all tree roots were processed");
  }

  // Generate code for a single tree root.
  // Also process inter-tree dependencies from this tree to other trees.
  private void generateTree(OPT_BURS_TreeNode k, OPT_BURS_STATE burs) {
    OPT_BURS_TreeNode child1 = k.child1;
    OPT_BURS_TreeNode child2 = k.child2;
    if (child1 != null && !child1.isTreeRoot()) {
      if (child2 != null && !child2.isTreeRoot()) {
	// k has two children; use register labeling to
	// determine order that minimizes register pressure
	if (child2.numRegisters() > child1.numRegisters()) {
	  generateTree(child2, burs);
	  generateTree(child1, burs);
	} else {
	  generateTree(child1, burs);
	  generateTree(child2, burs);
	} 
      } else {
	generateTree(child1, burs);
      }
    } else if (child2 != null && !child2.isTreeRoot()) {
      generateTree(child2, burs);
    }

    if (k.isSuperNodeRoot()) {
      int nonterminal = k.getNonTerminal();
      int rule = k.rule(nonterminal);
      burs.code(k, nonterminal, rule);
      if (DEBUG) {
        VM.sysWrite("PROCESSING FOR SUPERNODE #" + k.dg_node + "\n");
        VM.sysWrite(k + " " + OPT_BURS_Debug.string[rule] + "\n");
        VM.sysWrite("END OF PROCESSING FOR SUPERNODE #\n");
      }
    }      

    OPT_DepGraphNode dgnode = k.dg_node;
    if (dgnode != null) {
      OPT_SpaceEffGraphNode source = dgnode.nextSorted;
      for (OPT_SpaceEffGraphEdge out = dgnode.firstOutEdge(); 
	   out != null; 
	   out = out.getNextOut()) {
        OPT_SpaceEffGraphNode dest = out.toNode().nextSorted;
        if (source != dest) {
          int count = --dest.scratch;
          if (DEBUG) VM.sysWrite(count + ": edge " + source + " to " + dest + "\n");
          if (count == 0) {
            readyStackPush((OPT_BURS_TreeNode)dest.scratchObject);
	  }
        }
      }
    }
  }


  /**
   * Return true if node n must be a root of a BURS tree.
   * @param n the dep graph node in question.
   */
  private boolean mustBeTreeRoot (OPT_DepGraphNode n) {
    // A "fan-out" node must be a root of a BURS tree.
    // (A fan-out node is a node with > 1 outgoing register-true dependences)
    OPT_SpaceEffGraphEdge trueDepEdge = null;
    for (OPT_SpaceEffGraphEdge out = n.firstOutEdge(); 
	 out != null; 
	 out = out.getNextOut()) {
      if (OPT_DepGraphEdge.isRegTrue(out)) {
        if (trueDepEdge != null) return true;
        trueDepEdge = out;
      }
    }

    if (trueDepEdge == null) {
      return true; // 0 outgoing register-true dependencies
    } else {
      // Exactly 1 true edge, since we would have bailed out of above
      // loop if we'd found a second one.
      // If the node produces a register value that is live on exit
      // from the basic block then it must be the root of a BURS tree.
      OPT_Instruction instr = n.instruction();
      if (instr.operator() == IR_PROLOGUE) return true;
      OPT_RegisterOperand rop = ResultCarrier.getResult(instr);
      if (rop.register.spansBasicBlock()) return true;
      OPT_SpaceEffGraphNode parent = trueDepEdge.toNode();
      // Must ensure that our parent has a superset of our
      // other out edges (ignoring trueDepEdge)
      // this avoids a problem with creating circular dependencies
      // among tree roots.
      for (OPT_SpaceEffGraphEdge out = n.firstOutEdge(); 
	   out != null; 
	   out = out.getNextOut()) {
	if (out != trueDepEdge) {
	  boolean match = false;
	  for (OPT_SpaceEffGraphEdge out2 = parent.firstOutEdge(); 
	       out2 != null; 
	       out2 = out2.getNextOut()) {
	    if (out2.toNode() == out.toNode()) {
	      match = true;
	      break;
	    }
	  }
	  if (!match) return true;
	}
      }
      return false;
    }
  }


  /**
   * Initialize nextSorted for nodes in tree rooted at t i.e.
   * for all register true descendants of t up to but not including 
   * any new tree roots. 
   */
  private void initTreeRootNode(OPT_BURS_TreeNode t, 
				OPT_SpaceEffGraphNode treeRoot) {
    // Recurse
    OPT_BURS_TreeNode child1 = t.child1;
    if (child1 != null && !child1.isTreeRoot()) {
      initTreeRootNode(child1, treeRoot);
    }
    OPT_BURS_TreeNode child2 = t.child2;
    if (child2 != null && !child2.isTreeRoot()) {
      initTreeRootNode(child2, treeRoot);
    }

    if (t.dg_node != null) {
      t.dg_node.nextSorted = treeRoot;
      if (DEBUG) VM.sysWrite(t.dg_node + " --> " + treeRoot + "\n");
    }
    if (child1 != null || child2 != null) {
      // label t as in section 9.10 of the dragon book
      int lchild = (child1 != null) ? child1.numRegisters() : 0;
      int rchild = (child2 != null) ? child2.numRegisters() : 0;
      if (lchild == rchild) {
	t.setNumRegisters(lchild+1);
      } else {
	t.setNumRegisters(Math.max(lchild, rchild));
      }
      if (DEBUG) VM.sysWrite("\tnum registers = "+t.numRegisters()+"\n");
    }
  }


  /**
   * The actual work list of tree roots, ready to be processed. 
   * (The "ready stack").
   */
  private OPT_BURS_TreeNode[] stack = new OPT_BURS_TreeNode[32];
  private int stackTop = 0;

  /**
   * We keep track of tree roots that represent Move instructions separately.
   * We only process moves when the 'normal' stack is empty, in an attempt
   * to force MIR move instructions to the bottom of live ranges.  This
   * may help facilitate register coalescing, for the case when the move
   * instruction ends one live range and begins another.  Out-of-SSA
   * creates this pattern frequently.
   */
  private OPT_BURS_TreeNode[] moveStack = new OPT_BURS_TreeNode[32];
  private int moveStackTop = 0;

  /**
   * Push a node that represents a MOVE onto a separate stack.
   */
  private void moveStackPush(OPT_BURS_TreeNode node) {
    if (moveStackTop == moveStack.length) {
      OPT_BURS_TreeNode[] tmp = new OPT_BURS_TreeNode[moveStack.length*2];
      for (int i=0; i<moveStack.length; i++) {
	tmp[i] = moveStack[i];
      }
      moveStack = tmp;
    }
    moveStack[moveStackTop++] = node;
  }

  /**
   * Return top of the work stack representing Moves.
   */
  private OPT_BURS_TreeNode moveStackPop() {
    return moveStack[--moveStackTop];
  }

  /**
   * Is a particular tree rooted at node only a MOVE instruction?
   *
   * We encourage standalone moves to float to the bottom of the
   * basic block, in the hopes of reducing lifetimes and facilitating
   * global coalescing downstream. 
   */
  private boolean isSingleMove(OPT_BURS_TreeNode node) {
    OPT_Instruction s = node.getInstruction();
    if (s != null && s.isMove()) {
      return node.child1 == Register || node.child1 == IntConstant;
    } 
    return false;
  }

  /**
   * Push node on work stack.  If the node represents a move instruction,
   * we push it on a separate stack.
   */
  private void readyStackPush(OPT_BURS_TreeNode node) {
    if (isSingleMove(node)) {
      moveStackPush(node);
    } else {
      // push on the regular stack.
      if (stackTop == stack.length) {
        OPT_BURS_TreeNode[] tmp = new OPT_BURS_TreeNode[stack.length*2];
        for (int i=0; i<stack.length; i++) {
          tmp[i] = stack[i];
        }
        stack = tmp;
      }
      stack[stackTop++] = node;
    }
  }

  /**
   * Return top of work stack.  If and only if the 'normal' stack is
   * empty, then return a node from the stack of MOVE nodes.
   */
  private OPT_BURS_TreeNode readyStackPop() {
    if (stackTop == 0) {
      return moveStackPop();
    } else {
      return stack[--stackTop];
    }
  }

  /**
   * Are there nodes to process on the stack?
   */
  private boolean readyStackNotEmpty() {
    return (stackTop > 0 || moveStackTop >0);
  }
}
