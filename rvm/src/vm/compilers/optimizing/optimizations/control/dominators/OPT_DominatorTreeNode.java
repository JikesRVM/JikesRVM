/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * This class implements a node in the dominator tree.
 *
 * <p> TODO: we do not support IRs with exception handlers!!
 * @author Michael Hind
 * @author Martin Trapp
 */
class OPT_DominatorTreeNode extends OPT_TreeNode {

  /**
   * the basic block this node represents
   */
  private OPT_BasicBlock block;

  /**
   * distance from the root of the dominator tree
   */
  private int depth = -1;

  /**
   * representation of the dominance frontier for this node
   */
  private OPT_BitVector dominanceFrontier;

  /**
   * the cache to hold the set of nodes that dominate this one.  This is 
   * computed on demand by walking up the tree.
   */
  OPT_BitVector dominators;

  /**
   * lower bound of dominated nodes range
   */
  private int low = 0;
  
  /**
   * upper bound of dominated nodes range
   */
  private int high = 0;
  
  /**
   * Construct a dominator tree node for a given basic block.
   * @param   block the basic block
   */
  OPT_DominatorTreeNode(OPT_BasicBlock block) {
    this.block = block;
  }

  /**
   * Get the basic block for this dominator tree node
   * @return the basic block
   */
  OPT_BasicBlock getBlock() {
    return  block;
  }

  /**
   * Return the distance of this node from the root of the dominator tree.
   * @return the distance of this node from the root of the dominator tree.
   */
  int getDepth() {
    if (depth == -1) {
      OPT_DominatorTreeNode dad = (OPT_DominatorTreeNode)getParent();
      if (dad == null)
        depth = 0; 
      else 
        depth = dad.getDepth() + 1;
    }
    return  depth;
  }

  /**
   * Return a bit set representing the dominance frontier for this node
   * @return a bit set representing the dominance frontier for this node
   */
  OPT_BitVector getDominanceFrontier() {
    return  dominanceFrontier;
  }

  /**
   * Set a bit set representing the dominance frontier for this node
   * @param set the bit set
   */
  void setDominanceFrontier(OPT_BitVector set) {
    dominanceFrontier = set;
  }

  /**
   * Return a string representation of the dominance frontier for this
   * node.
   * @return a string representation of the dominance frontier for this
   * node 
   */
  String dominanceFrontierString() {
    return  dominanceFrontier.toString();
  }

  /**
   *   This method returns the set of blocks that dominates the passed
   *   block, i.e., it answers the question "Who dominates me?"
   *
   *   @param ir the governing IR
   *   @return a BitVector containing those blocks that dominate me
   */
  OPT_BitVector dominators(OPT_IR ir) {
    // Currently, this set is computed on demand, 
    // but we cache it for the next time.
    if (dominators == null) {
      dominators = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
      dominators.set(block.getNumber());
      OPT_DominatorTreeNode node = this;
      while ((node = (OPT_DominatorTreeNode)getParent()) != null) {
        dominators.set(node.getBlock().getNumber());
      }
    }
    return  dominators;
  }

  /**
   *  This method returns true if the passed node dominates this node
   *  @param master the proposed dominating node
   *  @return whether the passed node dominates me
   */
  boolean _isDominatedBy(OPT_DominatorTreeNode master) {
    OPT_DominatorTreeNode node = this;
    while ((node != null) && (node != master)) {
      node = (OPT_DominatorTreeNode)node.getParent();
    }
    return node == master;
  }

  /**
   *  This method returns true if the passed node dominates this node
   *  @param master the proposed dominating node
   *  @return whether the passed node dominates me
   */
  boolean isDominatedBy(OPT_DominatorTreeNode master) {
    if (low == 0) initializeRanges();
    return master.low <= low && master.high >= high;
  }

  private void initializeRanges () {
    OPT_DominatorTreeNode node = this;
    OPT_DominatorTreeNode parent = (OPT_DominatorTreeNode) getParent();
    while (parent != null) {
      node = parent;
      parent = (OPT_DominatorTreeNode) node.getParent();
    }
    node.initializeRanges (0);
  }

  private int initializeRanges (int i) {
    low = ++i;
    Enumeration childEnum = getChildren();
    while (childEnum.hasMoreElements()) {
      OPT_DominatorTreeNode 
        child = (OPT_DominatorTreeNode) childEnum.nextElement();
      i = child.initializeRanges (i);
    }
    high = ++i;
    return i;
  }
  
  /**
   * Enumerate the basic blocks in the dominance frontier for this node.
   * @param ir the governing IR
   * @return an enumeration of the basic blocks in the dominance frontier
   * for this node.
   */
  OPT_BasicBlockEnumeration domFrontierEnumerator(OPT_IR ir) {
    return  ir.getBasicBlocks(dominanceFrontier);
  }

  /**
   * String-i-fies the node
   * @return the node as a string
   */
  final public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(block);
    sb.append(" ("+low+", "+high+")");
    return  sb.toString();
  }
}



