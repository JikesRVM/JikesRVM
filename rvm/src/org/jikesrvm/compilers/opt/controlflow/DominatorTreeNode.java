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
package org.jikesrvm.compilers.opt.controlflow;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.util.TreeNode;
import org.jikesrvm.util.BitVector;

/**
 * This class implements a node in the dominator tree.
 *
 * <p> TODO: we do not support IRs with exception handlers!!
 */
public class DominatorTreeNode extends TreeNode {

  /**
   * the basic block this node represents
   */
  private final BasicBlock block;

  /**
   * distance from the root of the dominator tree, lazily initialized (-1 => not
   * initialized)
   */
  private int depth = -1;

  /**
   * representation of the dominance frontier for this node
   */
  private BitVector dominanceFrontier;

  /**
   * the cache to hold the set of nodes that dominate this one.  This is
   * computed on demand by walking up the tree.
   */
  BitVector dominators;

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
  DominatorTreeNode(BasicBlock block) {
    this.block = block;
  }

  /**
   * Get the basic block for this dominator tree node
   * @return the basic block
   */
  public BasicBlock getBlock() {
    return block;
  }

  /**
   * Return the distance of this node from the root of the dominator tree.
   * @return the distance of this node from the root of the dominator tree.
   */
  int getDepth() {
    if (depth == -1) {
      DominatorTreeNode dad = (DominatorTreeNode) getParent();
      if (dad == null) {
        depth = 0;
      } else {
        depth = dad.getDepth() + 1;
      }
    }
    return depth;
  }

  /**
   * Return a bit set representing the dominance frontier for this node
   * @return a bit set representing the dominance frontier for this node
   */
  BitVector getDominanceFrontier() {
    return dominanceFrontier;
  }

  /**
   * Set a bit set representing the dominance frontier for this node
   * @param set the bit set
   */
  void setDominanceFrontier(BitVector set) {
    dominanceFrontier = set;
  }

  /**
   * Return a string representation of the dominance frontier for this
   * node.
   * @return a string representation of the dominance frontier for this
   * node
   */
  String dominanceFrontierString() {
    return dominanceFrontier.toString();
  }

  /**
   *   This method returns the set of blocks that dominates the passed
   *   block, i.e., it answers the question "Who dominates me?"
   *
   *   @param ir the governing IR
   *   @return a BitVector containing those blocks that dominate me
   */
  BitVector dominators(IR ir) {
    // Currently, this set is computed on demand,
    // but we cache it for the next time.
    if (dominators == null) {
      dominators = new BitVector(ir.getMaxBasicBlockNumber() + 1);
      dominators.set(block.getNumber());
      DominatorTreeNode node = this;
      while ((node = (DominatorTreeNode) getParent()) != null) {
        dominators.set(node.getBlock().getNumber());
      }
    }
    return dominators;
  }

  /**
   *  This method returns true if the passed node dominates this node
   *  @param master the proposed dominating node
   *  @return whether the passed node dominates me
   */
  boolean _isDominatedBy(DominatorTreeNode master) {
    DominatorTreeNode node = this;
    while ((node != null) && (node != master)) {
      node = (DominatorTreeNode) node.getParent();
    }
    return node == master;
  }

  /**
   *  This method returns true if the passed node dominates this node
   *  @param master the proposed dominating node
   *  @return whether the passed node dominates me
   */
  boolean isDominatedBy(DominatorTreeNode master) {
    if (low == 0) initializeRanges();
    return master.low <= low && master.high >= high;
  }

  private void initializeRanges() {
    DominatorTreeNode node = this;
    DominatorTreeNode parent = (DominatorTreeNode) getParent();
    while (parent != null) {
      node = parent;
      parent = (DominatorTreeNode) node.getParent();
    }
    node.initializeRanges(0);
  }

  private int initializeRanges(int i) {
    low = ++i;
    Enumeration<TreeNode> childEnum = getChildren();
    while (childEnum.hasMoreElements()) {
      DominatorTreeNode child = (DominatorTreeNode) childEnum.nextElement();
      i = child.initializeRanges(i);
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
  BasicBlockEnumeration domFrontierEnumerator(IR ir) {
    return ir.getBasicBlocks(dominanceFrontier);
  }

  /**
   * String-i-fies the node
   * @return the node as a string
   */
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(block);
    sb.append(" (").append(low).append(", ").append(high).append(")");
    return sb.toString();
  }
}



