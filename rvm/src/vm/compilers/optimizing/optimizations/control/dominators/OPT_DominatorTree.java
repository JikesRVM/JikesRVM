/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * This class provides the abstraction of a dominator tree 
 *
 * TODO: we do not support IRs with exception handlers.
 *
 * @author Stephen Fink
 * @author Michael Hind
 */
public class OPT_DominatorTree extends OPT_Tree {
  /**
   * control for debugging messages.
   */
  static final boolean DEBUG = false;

  /**
   * True if we are computing regular dominators, false for post-dominators
   */
  private boolean forward;

  /**
   * The governing IR
   */
  private OPT_IR ir;        

  /**
   * A structure used to quickly index into the DominatorVertex tree
   */
  private OPT_DominatorTreeNode[] dominatorInfoMap;

  /** 
   * Build a dominator tree from an IR. NOTE: the dominator
   * information MUST be computed BEFORE calling this method!
   * We assume the scratch object of each basic block contains
   * the OPT_LTDominatorInfo object holding this information.
   *
   * @param ir the governing IR
   * @param forward are we building regular dominators or post-dominators?
   */
  public static void perform(OPT_IR ir, boolean forward) {
    if (forward) {
      ir.HIRInfo.dominatorTree = new OPT_DominatorTree(ir, forward);
      if (ir.options.PRINT_DOMINATORS) {
        if (DEBUG) {
          System.out.println("Here is the CFG for method "+
                             ir.method.getName() +"\n"+
                             ir.cfg);
        }
        System.out.println("Here is the Dominator Tree for method "+
                           ir.method.getName() +"\n"+
                           ir.HIRInfo.dominatorTree);
      }
    } else {
      ir.HIRInfo.postDominatorTree = new OPT_DominatorTree(ir, forward);
      if (ir.options.PRINT_POST_DOMINATORS) {
        if (DEBUG) {
          System.out.println("Here is the CFG for method "+
                             ir.method.getName() +"\n"+
                             ir.cfg);
        }
        System.out.println("Here is the Post-Dominator Tree for method "+
                           ir.method.getName() +"\n"+
                           ir.HIRInfo.postDominatorTree);
      }
    }
  }

  /** 
   * Build a dominator tree from an IR. NOTE: the dominator
   * information MUST be computed BEFORE calling this
   * constructor!
   *
   * @param ir the governing IR
   * @param forward are we building regular dominators or post-dominators?
   */
  OPT_DominatorTree(OPT_IR ir, boolean forward) {
    this.ir = ir;
    this.forward = forward;

    // The query methods of dominator information, such as
    // getDominanceFrontier, dominates, and domFrontierEnumerator
    // all use ir.getBasicBlock(int).  This method relies on 
    // the basic block map being up to date.  Here we ensure this
    // property, even though it is needed for computing the dominator
    // tree.
    ir.resetBasicBlockMap();

    // allocate the dominator vertex map
    dominatorInfoMap = 
      new OPT_DominatorTreeNode[ir.getMaxBasicBlockNumber() + 1];

    // allocate the tree and root node
    // Step 1: add all basic blocks to the tree as nodes
    for (Enumeration enum = ir.cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock)enum.nextElement();
      // We treat the exit node as not being part of the CFG
      if (!forward || !block.isExit()) {
        addNode(block);
      }
    }

    // Step 2: set the root value
    setRoot(dominatorInfoMap[getFirstNode().getNumber()]);

    // Step 3: Walk the nodes, for each node create link with parent
    //   Leaf nodes have no links to add
    for (Enumeration enum = ir.cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock)enum.nextElement();
      // skip the exit node
      if (forward && block.isExit()) {
        continue;
      }

      // get the tree node corresponding to "block"
      OPT_DominatorTreeNode blockNode = dominatorInfoMap[block.getNumber()];

      // if parent = null, this is the root of the tree, nothing to do
      if (OPT_LTDominatorInfo.getInfo(block) == null) {
        System.out.println("info field is null for block: " + block);
      }
      OPT_BasicBlock parent = OPT_LTDominatorInfo.getInfo(block).getDominator();
      if (parent != null) {
        OPT_DominatorTreeNode parentNode = dominatorInfoMap[parent.getNumber()];

        // tell the parent they have a child
        parentNode.addChild(blockNode);
      }
    }           // for loop     
  }             // method

  /**
   * Get the first node, either entry or exit
   * depending on which way we are viewing the graph
   * @return the entry node or exit node
   */
  private OPT_BasicBlock getFirstNode() {
    if (forward) {
      return ir.cfg.entry();
    } else {
      return ir.cfg.exit();
    }
  }

  /** 
   * Enumerate the children of the vertex corresponding to a basic 
   * block
   *
   * @param bb the basic block
   * @return an Enumeration of bb's children
   */
  public Enumeration getChildren(OPT_BasicBlock bb) {
    OPT_DominatorTreeNode node = dominatorInfoMap[bb.getNumber()];
    return  node.getChildren();
  }

  /** 
   * Return the parent of the vertex corresponding to a basic 
   * block
   *
   * @param bb the basic block
   * @return bb's parent
   */
  public OPT_BasicBlock getParent(OPT_BasicBlock bb) {
    OPT_DominatorTreeNode node = dominatorInfoMap[bb.getNumber()];
    return  ((OPT_DominatorTreeNode)node.getParent()).getBlock();
  }

  /** 
   * Return the (already calculated) dominance frontier for 
   * a basic block
   *
   * @param bb the basic block
   * @return a BitVector representing the dominance frontier
   */
  public OPT_BitVector getDominanceFrontier(OPT_BasicBlock bb) {
    OPT_DominatorTreeNode info = dominatorInfoMap[bb.getNumber()];
    return  info.getDominanceFrontier();
  }

  /** 
   * Return the (already calculated) dominance frontier for 
   * a basic block
   *
   * @param number the number of the basic block
   * @return a BitVector representing the dominance frontier
   */
  public OPT_BitVector getDominanceFrontier(int number) {
    return  getDominanceFrontier(ir.getBasicBlock(number));
  }

  /** 
   * Does basic block number b dominate all basic blocks in a set?
   *
   * @param b the number of the basic block to test
   * @param bits BitVector representation of the set of basic blocks to test
   * @return true or false
   */
  public boolean dominates(int b, OPT_BitVector bits) {
    for (int i = 0; i < bits.length(); i++) {
      if (!bits.get(i))
        continue;
      if (!dominates(b, i))
        return  false;
    }
    return  true;
  }

  /** 
   * Does basic block number "master" dominate basic block number "slave"?
   *
   * @param master the number of the proposed "master" basic block
   * @param slave  the number of the proposed "slave block
   * @return "master dominates slave"
   */
  public boolean dominates(int master, int slave) {
    OPT_BasicBlock masterBlock = ir.getBasicBlock(master);
    OPT_BasicBlock slaveBlock = ir.getBasicBlock(slave);
    OPT_DominatorTreeNode masterNode = 
        dominatorInfoMap[masterBlock.getNumber()];
    OPT_DominatorTreeNode slaveNode = dominatorInfoMap[slaveBlock.getNumber()];
    return  slaveNode.isDominatedBy(masterNode);
  }

  /** 
   * Does basic block number "master" dominate basic block number "slave"?
   *
   * @param master the number of the proposed "master" basic block
   * @param slave  the number of the proposed "slave block
   * @return "master dominates slave"
   */
  public boolean dominates(OPT_BasicBlock master, OPT_BasicBlock slave) {
    OPT_DominatorTreeNode masterNode = dominatorInfoMap[master.getNumber()];
    OPT_DominatorTreeNode slaveNode = dominatorInfoMap[slave.getNumber()];
    return slaveNode.isDominatedBy(masterNode);
  }

  /** 
   * Creates domniator tree nodes for the passed block and adds them to the
   * map.
   * @param b the basic block
   */
  private void addNode(OPT_BasicBlock b) {
    OPT_LTDominatorInfo info = (OPT_LTDominatorInfo)b.scratchObject;
    OPT_DominatorTreeNode node = new OPT_DominatorTreeNode(b);
    dominatorInfoMap[b.getNumber()] = node;
  }

  /**
   * Return the distance from the root of the dominator tree to a given
   * basic block
   *
   * @param b the basic block in question
   * @return b's depth
   */
  public int depth(OPT_BasicBlock b) {
    return  dominatorInfoMap[b.getNumber()].getDepth();
  }

  /**
   * Return the deepest common dominance ancestor of blocks a and b
   *
   * @param a first basic block
   * @param a second basic block
   * @return the deepest common dominance ancestor of blocks a and b
   */
  public OPT_BasicBlock 
      deepestCommonAncestor(OPT_BasicBlock a, OPT_BasicBlock b) {
    OPT_DominatorTreeNode n_a = dominatorInfoMap[a.getNumber()];
    OPT_DominatorTreeNode n_b = dominatorInfoMap[b.getNumber()];
    while (n_a != n_b)
      if (n_a.getDepth() > n_b.getDepth())
        n_a = (OPT_DominatorTreeNode)n_a.getParent(); 
      else 
        n_b = (OPT_DominatorTreeNode)n_b.getParent();
    return  n_a.getBlock();
  }

}



