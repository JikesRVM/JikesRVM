/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;

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
final class OPT_NormalBURS extends OPT_BURS {

  private int numTreeRoots;

  /**
   * Create a BURS object for the given IR.
   * 
   * @param IR the IR to translate from LIR to MIR.
   */
  OPT_NormalBURS (OPT_IR IR) {
    ir = IR;
  }

  /**
   * Build BURS trees for dependence graph dg, label the trees, and
   * then generate MIR instructions based on the labeling.
   * @param dg the dependence graph.
   */
  public void invoke (OPT_DepGraph dg) {
    if (DEBUG) dg.printDepGraph();
    OPT_BURS_STATE burs = new OPT_BURS_STATE(this);
    buildTrees(dg);
    if (haveProblemEdges()) {
      problemEdgePrep();
      handleProblemEdges();
    }
    orderTrees(dg);
    labelTrees(burs);
    generateTrees(burs);
  }


  ////////////////////////////////
  // Implementation 
  ////////////////////////////////

  /**
   * Stage 1: Complete the expression trees and identify tree roots.
   * Complete BURS trees by adding leaf nodes as needed, and
   * creating tree edges by calling insertChild1() or insertChild2()
   * This step is also where we introduce intermediate tree nodes for 
   * any LIR instruction that has > 2 "real" operands e.g., a CALL.
   * We also mark nodes that must be tree roots.
   * 
   * @param dg  The dependence graph. 
   */
  private void buildTrees(OPT_DepGraph dg) {
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
            child = (OPT_BURS_TreeNode)e.fromNode().scratchObject;
          }
        } else if (op instanceof OPT_IntConstantOperand) {
          child = new OPT_BURS_IntConstantTreeNode(((OPT_IntConstantOperand)op).value);
        } else if (op instanceof OPT_LongConstantOperand) {
          child = LongConstant;
        } else if (op instanceof OPT_AddressConstantOperand) {
          child = AddressConstant;
        } else if (op instanceof OPT_BranchOperand && instr.isCall()) {
          child = BranchTarget;
        //-#if RVM_WITH_OSR
        } else if (op instanceof OPT_InlinedOsrTypeInfoOperand && instr.isYieldPoint()) {
          child = NullTreeNode; 
        //-#endif
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
      //-#if RVM_WITH_OSR
      case YIELDPOINT_OSR_opcode:
      //-#endif
        if (cur_parent.child2 == null)
          cur_parent.child2 = NullTreeNode;
        // fall through
      case RETURN_opcode:
        if (cur_parent.child1 == null)
          cur_parent.child1 = NullTreeNode;
      }

      if (mustBeTreeRoot(n)) {
        makeTreeRoot((OPT_BURS_TreeNode)n.scratchObject);
      }
    } 
  }


  /**
   * Stage 1b: Do bookkeeping to make it easier to identify 
   * harmless problem edges.
   */
  private void problemEdgePrep() {
    for (int i=0; i<numTreeRoots; i++) {
      OPT_BURS_TreeNode n = treeRoots[i];
      problemEdgePrep(n, n.dg_node);
    }
  }
  private void problemEdgePrep(OPT_BURS_TreeNode n, OPT_SpaceEffGraphNode root) {
    OPT_BURS_TreeNode child1 = n.child1;
    OPT_BURS_TreeNode child2 = n.child2;
    if (child1 != null && !child1.isTreeRoot()) {
      problemEdgePrep(child1, root);
    }
    if (child2 != null && !child2.isTreeRoot()) {
      problemEdgePrep(child2, root);
    }
    if (n.dg_node != null) {
      n.dg_node.nextSorted = root;
      n.dg_node.scratch = 0;
    }
  }

  /**
   * Stage 1c: Mark src node of some problem edges as tree roots to avoid 
   * cyclic dependencies.
   */
  private void handleProblemEdges() {
    // Stage 1: Remove problem edges whose destination
    //          is the root of their own tree; these edges 
    //          are trivially redundant with reg-true edges.
    int remaining = 0;
    for (int i=0; i<numProblemEdges; i++) {
      OPT_SpaceEffGraphEdge e = problemEdges[i];
      OPT_SpaceEffGraphNode src = e.fromNode();
      OPT_SpaceEffGraphNode dst = e.toNode();
      OPT_SpaceEffGraphNode srcRoot = src.nextSorted;
      if (srcRoot != dst) {
        // might still be trouble
        problemEdges[remaining++] = e;
      }
    }
    numProblemEdges = remaining;
    if (numProblemEdges == 0) return;

    // Still some edges that might introduce cycles.
    int searchnum = 0;
    for (int i=0; i<numProblemEdges; i++) {
      OPT_SpaceEffGraphEdge e = problemEdges[i];
      OPT_SpaceEffGraphNode src = e.fromNode();
      OPT_SpaceEffGraphNode dst = e.toNode();
      OPT_BURS_TreeNode n = (OPT_BURS_TreeNode)src.scratchObject;
      if (n.isTreeRoot()) continue; // some other problem edge already forced it
      OPT_SpaceEffGraphNode srcRoot = src.nextSorted;
      OPT_SpaceEffGraphNode dstRoot = dst.nextSorted;
      if (srcRoot == dstRoot && srcRoot != dst) {
        // potential for intra-tree cycle
        if (!trueEdgeRedundant(src, dst, srcRoot)) {
          if (DEBUG) {
            VM.sysWrite("Potential intra-tree cycle with edge "+e+
                        " forcing "+n+" to be a tree root\n");
          }
          makeTreeRoot(n);
          problemEdgePrep(n, n.dg_node);
        }
      } else {
        // potential for inter-tree cycle
        if (reachableRoot(dstRoot, srcRoot, ++searchnum)) {
          if (DEBUG) {
            VM.sysWrite("Potential inter-tree cycle with edge "+e+
                        " forcing "+n+" to be a tree root\n");
          }
          makeTreeRoot(n);
          problemEdgePrep(n, n.dg_node);
        }
      }
    }
  }
  // routine to identify harmless intra-tree edges.
  // if the edge is redundant wrt regTrue edges, then it
  // can be ignored.
  private boolean trueEdgeRedundant(OPT_SpaceEffGraphNode current,
                                    OPT_SpaceEffGraphNode goal,
                                    OPT_SpaceEffGraphNode root) {
    if (current == goal) return true;
    if (current.nextSorted != root) return false; // don't cross tree boundaries
    for (OPT_SpaceEffGraphEdge out = current.firstOutEdge();
         out != null;
         out = out.getNextOut()) {
      if (OPT_DepGraphEdge.isRegTrue(out) && 
          trueEdgeRedundant(out.toNode(), goal, root)) {
        return true;
      }
    }
    return false;
  }
  // routine to identify harmless inter-tree edges.
  // Is goal reachable via any edge in the current tree?
  private boolean reachableRoot(OPT_SpaceEffGraphNode current,
                                OPT_SpaceEffGraphNode goal,
                                int searchnum) {
    if (current == goal) return true;
    if (current.scratch == searchnum) return false;
    current.scratch = searchnum;
    OPT_BURS_TreeNode root = (OPT_BURS_TreeNode)current.scratchObject;
    if (reachableChild(root, goal, searchnum)) return true;
    return false;
  }
  private boolean reachableChild(OPT_BURS_TreeNode n,
                                 OPT_SpaceEffGraphNode goal,
                                 int searchnum) {
    OPT_SpaceEffGraphNode dgn = n.dg_node;
    if (dgn != null) {
      for (OPT_SpaceEffGraphEdge out = dgn.firstOutEdge();
           out != null;
           out = out.getNextOut()) {
        if (reachableRoot(out.toNode().nextSorted, goal, searchnum)) return true;
      }
    }
    if (n.child1 != null && !n.child1.isTreeRoot() &&
        reachableChild(n.child1, goal, searchnum)) {
      return true;
    }
    if (n.child2 != null && !n.child2.isTreeRoot() &&
        reachableChild(n.child2, goal, searchnum)) {
      return true;
    }
    return false;
  }

  /**
   * Stage 2: Construct topological ordering of tree roots based on the
   * dependencies between nodes in the tree. 
   * 
   * @param dg  The dependence graph. 
   */
  private void orderTrees(OPT_DepGraph dg) {
    // Initialize tree root field for all nodes
    for (int i=0; i<numTreeRoots; i++) {
      OPT_BURS_TreeNode n = treeRoots[i];
      n.dg_node.scratch = 0; 
      initTreeRootNode(n, n.dg_node);
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
      for (int i=0; i<numTreeRoots; i++) {
        OPT_BURS_TreeNode n = treeRoots[i];
        VM.sysWrite(n.dg_node.scratch + ":" + n + "\n");
      }
    }
  }

  /**
   * Stage 3: Label the trees with their min cost cover.
   * @param burs the OPT_BURS_STATE object.
   */
  private void labelTrees(OPT_BURS_STATE burs) {
    for (int i=0; i<numTreeRoots; i++) {
      OPT_BURS_TreeNode n = treeRoots[i];
      burs.label(n);
      OPT_BURS_STATE.mark(n, /* goalnt */(byte)1);
    }
  }


  /**
   * Stage 4: Visit the tree roots in topological order and 
   * emit MIR instructions by calling OPT_BURS_STATE.code on each
   * supernode in the tree.
   * 
   * @param burs the OPT_BURS_STATE object.
   */
  private void generateTrees(OPT_BURS_STATE burs) {
    // Append tree roots with predCount = 0 to readySet
    for (int i=0; i<numTreeRoots; i++) {
      OPT_BURS_TreeNode n = treeRoots[i];
      if (n.dg_node.scratch == 0) {
        readySetInsert(n);
      }
    }

    // Emit code for each tree root in readySet
    while (readySetNotEmpty()) {
      OPT_BURS_TreeNode k = readySetRemove();
      // Invoke burs.code on the supernodes of k in a post order walk of the
      // tree (thus code for children is emited before code for the parent).
      if (DEBUG) {
        VM.sysWrite("PROCESSING TREE ROOTED AT "+ k.dg_node + "\n");
        OPT_BURS_STATE.dumpTree(k);
      }
      numTreeRoots--;
      generateTree(k, burs);
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
    if (child1 != null) {
      if (child2 != null) {
        // k has two children; use register labeling to
        // determine order that minimizes register pressure
        if (k.isSuperNodeRoot()) {
          byte act = OPT_BURS_STATE.action[k.rule(k.getNonTerminal())];
          if ((act & OPT_BURS_STATE.LEFT_CHILD_FIRST) != 0) {
            // rule selected forces order of evaluation
            generateTree(child1, burs);
            generateTree(child2, burs);
          } else if ((act & OPT_BURS_STATE.RIGHT_CHILD_FIRST) != 0) {
            // rule selected forces order of evaluation
            generateTree(child2, burs);
            generateTree(child1, burs);
          } else if (child2.numRegisters() > child1.numRegisters()) {
            generateTree(child2, burs);
            generateTree(child1, burs);
          } else {
            generateTree(child1, burs);
            generateTree(child2, burs);
          }
        } else {
          if (child2.numRegisters() > child1.numRegisters()) {
            generateTree(child2, burs);
            generateTree(child1, burs);
          } else {
            generateTree(child1, burs);
            generateTree(child2, burs);
          } 
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
            readySetInsert((OPT_BURS_TreeNode)dest.scratchObject);
          }
        }
      }
    }
  }

  /**
   * Return true if node n must be a root of a BURS tree
   * based only on its register true dependencies.
   * If the node might later have to be marked as a tree
   * root, then include in a set of problem nodes.
   * @param n the dep graph node in question.
   */
  private boolean mustBeTreeRoot(OPT_DepGraphNode n) {
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
      // If our parent has a superset of our
      // other out edges (ignoring trueDepEdge)
      // then we don't have to worry about creating cycles
      // by not forcing n to be a tree root.
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
          if (!match) {
            // could be trouble. Remember for later processing.
            rememberAsProblemEdge(out);
          }
        }
      }
      return false;
    }
  }

  // NOTE: assumes n has exactly 1 reg true parent (ie it is in
  //       an expression tree and is not the tree root).
  private OPT_SpaceEffGraphNode regTrueParent(OPT_SpaceEffGraphNode n) {
    for (OPT_SpaceEffGraphEdge out = n.firstOutEdge(); 
         out != null; 
         out = out.getNextOut()) {
      if (OPT_DepGraphEdge.isRegTrue(out)) {
        return out.toNode();
      }
    }
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }


  /**
   * Initialize nextSorted for nodes in tree rooted at t i.e.
   * for all register true descendants of t up to but not including 
   * any new tree roots. 
   */
  private void initTreeRootNode(OPT_BURS_TreeNode t, 
                                OPT_SpaceEffGraphNode treeRoot) {
    // Recurse
    if (t.child1 != null) {
      if (t.child1.isTreeRoot()) {
        t.child1 = Register; 
      } else {
        initTreeRootNode(t.child1, treeRoot);
      }
    }
    if (t.child2 != null) {
      if (t.child2.isTreeRoot()) {
        t.child2 = Register; 
      } else {
        initTreeRootNode(t.child2, treeRoot);
      }
    }
    if (t.dg_node != null) {
      t.dg_node.nextSorted = treeRoot;
      if (DEBUG) VM.sysWrite(t.dg_node + " --> " + treeRoot + "\n");
    }
    if (t.child1 != null || t.child2 != null) {
      // label t as in section 9.10 of the dragon book
      int lchild = (t.child1 != null) ? t.child1.numRegisters() : 0;
      int rchild = (t.child2 != null) ? t.child2.numRegisters() : 0;
      if (lchild == rchild) {
        t.setNumRegisters(lchild+1);
      } else {
        t.setNumRegisters(Math.max(lchild, rchild));
      }
      if (DEBUG) VM.sysWrite("\tnum registers = "+t.numRegisters()+"\n");
    }
  }

  /**
   * Set of all tree roots.
   */
  private OPT_BURS_TreeNode[] treeRoots = new OPT_BURS_TreeNode[32];
  private void makeTreeRoot(OPT_BURS_TreeNode n) {
    if (numTreeRoots == treeRoots.length) {
      OPT_BURS_TreeNode[] tmp = new OPT_BURS_TreeNode[treeRoots.length*2];
      for (int i=0; i<treeRoots.length; i++) {
        tmp[i] = treeRoots[i];
      }
      treeRoots = tmp;
    }
    n.setTreeRoot();
    treeRoots[numTreeRoots++] = n;
  }


  /**
   * A priority queue of ready tree nodes. 
   * Used to keep track of the tree roots that are ready to be 
   * emitted during code generation (since all of their 
   * predecessors have been emitted already).  
   * readySetRemove returns the node that uses the maximum
   * number of registers.  This is a heuristic that tends to 
   * reduce register pressure and enable coalescing by the 
   * register allocator. (Goal is to end live ranges 'early').
   */
  private OPT_BURS_TreeNode[] heap = new OPT_BURS_TreeNode[16];
  private int numElements = 0;

  /**
   * Add a node to the ready set.
   */
  private void readySetInsert(OPT_BURS_TreeNode node) {
    OPT_Instruction s = node.getInstruction();
    if (s.operator == GUARD_COMBINE ||
        s.operator == GUARD_COND_MOVE ||
        s.operator == GUARD_MOVE || 
        !ResultCarrier.conforms(s)) {
      // Adjust numRegisters to bias away from picking trees that
      // are rooted in result carriers, since they start a new live 
      // range. We don't count guard operations as result carriers, since
      // guard operations get wiped out before register allocation anyways.
      node.setNumRegisters(node.numRegisters()+2);
    }

    numElements++;
    if (numElements == heap.length) {
      OPT_BURS_TreeNode[] tmp = new OPT_BURS_TreeNode[heap.length*2];
      for (int i=0; i<heap.length; i++) {
        tmp[i] = heap[i];
      }
      heap = tmp;
    }

    // restore heap property
    int current = numElements;
    heap[current] = node;
    for (int parent = current / 2;
         current > 1 && heap[current].numRegisters() > heap[parent].numRegisters();
         current = parent, parent = current / 2) {
      swap(current, parent);
    }
  }

  /**
   * Are there nodes to process on the stack?
   */
  private boolean readySetNotEmpty() {
    return numElements > 0;
  }

  /** 
   * Remove a node from the ready set
   */
  private OPT_BURS_TreeNode readySetRemove() {
    OPT_BURS_TreeNode ans = heap[1];
    heap[1] = heap[numElements--];
    heapify(1);
    return ans;
  }
    
  private void heapify(int p) {
    int l = p * 2;
    int r = l + 1;
    int max = p;
    if (l <= numElements &&
        heap[l].numRegisters() > heap[max].numRegisters()) {
      max = l;
    }
    if (r <= numElements &&
        heap[r].numRegisters() > heap[max].numRegisters()) {
      max = r;
    }
    if (max != p) {
      swap(p, max);
      heapify(max);
    }
  }

  private void swap(int x, int y) {
    OPT_BURS_TreeNode t = heap[x];
    heap[x] = heap[y];
    heap[y] = t;
  }


  /*
   * track problem nodes (nodes with outgoing non-reg-true edges)
   */
  private OPT_SpaceEffGraphEdge[] problemEdges;
  private int numProblemEdges = 0;

  void rememberAsProblemEdge(OPT_SpaceEffGraphEdge e) {
    if (problemEdges == null) {
      problemEdges = new OPT_SpaceEffGraphEdge[8];
    }
    if (numProblemEdges == problemEdges.length) {
      OPT_SpaceEffGraphEdge[] tmp = new OPT_SpaceEffGraphEdge[problemEdges.length*2];
      for (int i=0; i<problemEdges.length; i++) {
        tmp[i] = problemEdges[i];
      }
      problemEdges = tmp;
    }
    problemEdges[numProblemEdges++] = e;
  }

  private boolean haveProblemEdges() {
    return numProblemEdges > 0;
  }
}


