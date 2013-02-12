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
import java.util.HashSet;
import java.util.Iterator;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.util.BitVector;

/**
 * This class holds data associated with a basic block as computed by the
 * Lengauer-Tarjan dominator calculation.
 * @see LTDominators
 */
class LTDominatorInfo {
  static final boolean DEBUG = false;

  private int semiDominator;
  /** the immediate dominator */
  private BasicBlock dominator;
  private BasicBlock parent;
  private final HashSet<BasicBlock> bucket;
  private BasicBlock label;
  private BasicBlock ancestor;
  // Used to keep the trees balanced, during path compression
  private int size;
  private BasicBlock child;

  // used to capture activation record state to avoid the use of recursion
  // in Step 1 of the LT algorithm
  // A null value will signal that we have not started to process this
  // block, otherwise, we'll skip the (redundant)
  // initialization step for the block
  //  See LTDominators.DFS() for details
  private Enumeration<BasicBlock> bbEnum;

  /**
   * @param block the basic block this info is associated with
   */
  LTDominatorInfo(BasicBlock block) {
    semiDominator = 0;
    dominator = null;
    parent = null;
    bucket = new HashSet<BasicBlock>();
    ancestor = null;
    label = block;
    size = 1;
    child = null;
    bbEnum = null;
  }

  /**
   *   This method returns the set of blocks that dominates the passed
   *   block, i.e., it answers the question "Who dominates me?"
   *
   *   @param block the block of interest
   *   @param ir    the governing ir
   *   @return a BitVector containing those blocks that dominate the passed one
   */
  public BitVector dominators(BasicBlock block, IR ir) {
    // Currently this set is computed on demand.  We may want to cache
    // the result for reuse.  The cost of computing is the height of the
    // the dominator tree.
    BitVector dominators = new BitVector(ir.getMaxBasicBlockNumber() + 1);
    dominators.set(block.getNumber());
    while ((block = getIdom(block)) != null) {
      dominators.set(block.getNumber());
    }
    return dominators;
  }

  /**
   *   This method determines if the 1st parameter (block) is dominated by
   *   the 2nd parameter (master), i.e., must control pass through "master"
   *   before reaching "block"
   *
   *   @param block the block we care about
   *   @param master the potential dominating block
   *   @return whether master dominates block
   */
  public static boolean isDominatedBy(BasicBlock block, BasicBlock master) {
    if (block == master) {
      return true;
    }
    // walk up the dominator tree looking for the passed block
    block = getIdom(block);
    while (block != null && block != master) {
      block = getIdom(block);
    }
    // If we found the master, the condition is true
    return block == master;
  }

  /**
   * Sets the semidominator for this node
   * @param value the new value
   */
  public void setSemiDominator(int value) {
    semiDominator = value;
  }

  /**
   * Returns the semidomintor for this node
   * @return the semidomintor for this node
   */
  public int getSemiDominator() {
    return semiDominator;
  }

  /**
   * Sets the immediate dominator for this node
   * @param value the value to set
   */
  public void setDominator(BasicBlock value) {
    dominator = value;
  }

  /**
   * Returns the immediate dominator for this node
   * @return the immediate dominator for this node
   */
  public BasicBlock getDominator() {
    return dominator;
  }

  /**
   * Sets the parent of this block
   * @param value the value
   */
  public void setParent(BasicBlock value) {
    parent = value;
  }

  /**
   * Returns the parent of this block
   * @return the parent of this block
   */
  public BasicBlock getParent() {
    return parent;
  }

  /**
   * Returns an iterator over this block's bucket
   * @return an iterator over this block's bucket
   */
  public Iterator<BasicBlock> getBucketIterator() {
    return bucket.iterator();
  }

  /**
   * Removes the passed block from the bucket for this node
   * @param block the block to remove from the bucket
   */
  public void removeFromBucket(BasicBlock block) {
    bucket.remove(block);
  }

  /**
   * Adds the passed block from the bucket for this node
   * @param block the block to add to our bucket
   */
  public void addToBucket(BasicBlock block) {
    bucket.add(block);
  }

  /**
   * Sets the ancestor for the value passed
   * @param value the ancestor value
   */
  public void setAncestor(BasicBlock value) {
    ancestor = value;
  }

  /**
   * Returns the ancestor for this block
   * @return the ancestor for this block
   */
  public BasicBlock getAncestor() {
    return ancestor;
  }

  /**
   * sets the label
   * @param value the label
   */
  public void setLabel(BasicBlock value) {
    label = value;
  }

  /**
   * returns the label
   * @return the label
   */
  public BasicBlock getLabel() {
    return label;
  }

  /**
   * sets the size
   * @param value the size
   */
  public void setSize(int value) {
    size = value;
  }

  /**
   * returns the size
   * @return the size
   */
  public int getSize() {
    return size;
  }

  /**
   * sets the child field
   * @param value the child value
   */
  public void setChild(BasicBlock value) {
    child = value;
  }

  /**
   * returns the child
   * @return the child
   */
  public BasicBlock getChild() {
    return child;
  }

  /**
   * Helper method to return the Info field associated with a block
   * @return the basic block enumeration, could be null
   */
  public Enumeration<BasicBlock> getEnum() {
    return bbEnum;
  }

  /**
   * set the basic block enum field
   * @param bbEnum basic block enum
   */
  public void setEnum(Enumeration<BasicBlock> bbEnum) {
    this.bbEnum = bbEnum;
  }

  /**
   * Helper method to return the Info field associated with a block
   * @param block the block of interest
   * @return the LTInfo info
   */
  public static LTDominatorInfo getInfo(BasicBlock block) {
    return (LTDominatorInfo) block.scratchObject;
  }

  /**
   * return the immediate dominator of a basic block.
   * Note: the dominator info must be pre-calculated
   * @param bb the basic block in question
   * @return bb's immediate dominator
   */
  public static BasicBlock getIdom(BasicBlock bb) {
    return getInfo(bb).dominator;
  }

  /**
   * Prints a string version of objection
   */
  @Override
  public String toString() {
    return super.toString() + " [Parent: " + parent + " SDom: " + semiDominator + " Dom: " + dominator + "]";
  }
}
