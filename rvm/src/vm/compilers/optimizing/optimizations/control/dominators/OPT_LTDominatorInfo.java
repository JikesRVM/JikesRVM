/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Iterator;
import java.util.HashSet;

/**
 * This class holds data associated with a basic block as computed by the
 * Lengauer-Tarjan dominator calculation.  
 * @see OPT_LTDominators
 *
 * @author Michael Hind
 */
class OPT_LTDominatorInfo {
  static final boolean DEBUG = false;

  private int semiDominator;
  private OPT_BasicBlock dominator;             // the imediate dominator
  private OPT_BasicBlock parent;
  private java.util.HashSet bucket;
  private OPT_BasicBlock label;
  private OPT_BasicBlock ancestor;
  // Used to keep the trees balanced, during path compression
  private int size;
  private OPT_BasicBlock child;

  // used to capture activation record state to avoid the use of recursion
  // in Step 1 of the LT algorithm
  // A null value will signal that we have not started to process this
  // block, otherwise, we'll skip the (redundant) 
  // initialization step for the block
  //  See OPT_LTDominators.DFS() for details
  private OPT_BasicBlockEnumeration enum;

  /**
   * @param  the basic block this info is associated with
   *
   */
  OPT_LTDominatorInfo(OPT_BasicBlock block) {
    semiDominator = 0;
    dominator = null;
    parent = null;
    bucket = new java.util.HashSet();
    ancestor = null;
    label = block;
    size = 1;
    child = null;
    enum = null;
  }

  /**
   *   This method returns the set of blocks that dominates the passed
   *   block, i.e., it answers the question "Who dominates me?"
   *
   *   @param size  the size of the BitVector, i.e., 
   *                the number of blocks in the CFG
   *   @param block the block of interest
   *   @param ir    the governing ir
   *   @return a BitVector containing those blocks that dominate the passed one
   */
  public OPT_BitVector dominators(OPT_BasicBlock block, OPT_IR ir) {
    // Currently this set is computed on demand.  We may want to cache
    // the result for reuse.  The cost of computing is the height of the
    // the dominator tree.
    OPT_BitVector dominators = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
    dominators.set(block.getNumber());
    while ((block = getIdom(block)) != null) {
      dominators.set(block.getNumber());
    }
    return  dominators;
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
  public static boolean isDominatedBy(OPT_BasicBlock block, 
                                      OPT_BasicBlock master) {
    if (block == master) {
      return  true;
    }
    // walk up the dominator tree looking for the passed block
    block = getIdom(block);
    while (block != null && block != master) {
      block = getIdom(block);
    }
    // If we found the master, the condition is true
    return  block == master;
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
    return  semiDominator;
  }

  /**
   * Sets the immediate dominator for this node
   * @param value the value to set
   */
  public void setDominator(OPT_BasicBlock value) {
    dominator = value;
  }

  /**
   * Returns the immediate dominator for this node
   * @return the immediate dominator for this node
   */
  public OPT_BasicBlock getDominator() {
    return  dominator;
  }

  /**
   * Sets the parent of this block
   * @param value the value
   */
  public void setParent(OPT_BasicBlock value) {
    parent = value;
  }

  /**
   * Returns the parent of this block
   * @return the parent of this block
   */
  public OPT_BasicBlock getParent() {
    return  parent;
  }

  /**
   * Returns an iterator over this block's bucket
   * @return an iterator over this block's bucket
   */
  public java.util.Iterator getBucketIterator() {
    return  bucket.iterator();
  }

  /**
   * Removes the passed block from the bucket for this node
   * @param block the block to remove from the bucket
   */
  public void removeFromBucket(OPT_BasicBlock block) {
    bucket.remove(block);
  }

  /**
   * Adds the passed block from the bucket for this node
   * @param block the block to add to our bucket
   */
  public void addToBucket(OPT_BasicBlock block) {
    bucket.add(block);
  }

  /**
   * Sets the ancestor for the value passed
   * @param value the ancestor value
   */
  public void setAncestor(OPT_BasicBlock value) {
    ancestor = value;
  }

  /**
   * Returns the ancestor for this block
   * @return the ancestor for this block
   */
  public OPT_BasicBlock getAncestor() {
    return  ancestor;
  }

  /**
   * sets the label
   * @param value the label
   */
  public void setLabel(OPT_BasicBlock value) {
    label = value;
  }

  /**
   * returns the label
   * @return the label
   */
  public OPT_BasicBlock getLabel() {
    return  label;
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
    return  size;
  }

  /**
   * sets the child field
   * @param value the child value
   */
  public void setChild(OPT_BasicBlock value) {
    child = value;
  }

  /**
   * returns the child
   * @return the child
   */
  public OPT_BasicBlock getChild() {
    return  child;
  }

  /**
   * Helper method to return the Info field associated with a block
   * @return the basic block enumeration, could be null
   */
  public OPT_BasicBlockEnumeration getEnum() {
    return enum;
  }

  /**
   * set the basic block enum field
   * @param enum basic block enum
   */
  public void setEnum(OPT_BasicBlockEnumeration enum) {
    this.enum = enum;
  }

  /**
   * Helper method to return the Info field associated with a block
   * @param block the block of interest
   * @return the LTInfo info
   */
  public static OPT_LTDominatorInfo getInfo (OPT_BasicBlock block) {
    return  (OPT_LTDominatorInfo)block.scratchObject;
  }

  /**
   * return the immediate dominator of a basic block. 
   * Note: the dominator info must be pre-calculated
   * @param bb the basic block in question
   * @return bb's immediate dominator
   */
  public static OPT_BasicBlock getIdom(OPT_BasicBlock bb) {
    return  getInfo(bb).dominator;
  }

  /**
   * Prints a string version of objection
   */
  public String toString() {
    return  super.toString() + " [Parent: " + parent + " SDom: " + semiDominator
        + " Dom: " + dominator + "]";
  }
}
