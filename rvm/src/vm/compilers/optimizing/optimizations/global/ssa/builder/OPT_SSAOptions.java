/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * This module defines parameters to the SSA construction process.
 * This is used to pass information between compiler phases.  
 * IMPORTANT: Phases that change the SSA state MUST update the SSA
 *            actual options held by the IR object.
 *
 * @author Stephen Fink
 */


import  java.util.*;


/**
 * put your documentation comment here
 */
class OPT_SSAOptions {
  /**
   * options for SSA construction
   */
  final static int MINIMAL = 0;
  final static int PRUNED = 1;
  final static int SEMI_PRUNED = 2;
  private boolean scalarsOnly;        // construct SSA only for scalars?
  private boolean backwards;          // construct Heap SSA for backwards 
  // analysis?
  private boolean insertUsePhis;      // constuct Heap SSA with uPhi functions?
  private boolean insertPEIDeps;      // constuct Heap SSA with PEI deps?
  private JDK2_Set heapTypes;         // restrict Heap SSA to this set of types?
  private boolean heapValid;          // is Heap SSA info valid?
  private boolean scalarValid;        // is Scalar SSA info valid?

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getScalarsOnly () {
    return  scalarsOnly;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getBackwards () {
    return  backwards;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getInsertUsePhis () {
    return  insertUsePhis;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getInsertPEIDeps () {
    return  insertPEIDeps;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final JDK2_Set getHeapTypes () {
    return  heapTypes;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getHeapValid () {
    return  heapValid;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean getScalarValid () {
    return  scalarValid;
  }

  /**
   * put your documentation comment here
   * @param b
   */
  final void setScalarsOnly (boolean b) {
    scalarsOnly = b;
  }

  /**
   * put your documentation comment here
   * @param b
   */
  final void setBackwards (boolean b) {
    backwards = b;
  }

  /**
   * put your documentation comment here
   * @param b
   */
  final void setInsertUsePhis (boolean b) {
    insertUsePhis = b;
  }

  /**
   * put your documentation comment here
   * @param b
   */
  final void setInsertPEIDeps (boolean b) {
    insertPEIDeps = b;
  }

  /**
   * put your documentation comment here
   * @param s
   */
  final void setHeapTypes (JDK2_Set s) {
    heapTypes = s;
  }

  // CAUTION: only Enter and LeaveSSA should use the following.
  // Don't use these unless you know what you're doing.
  final void setHeapValid (boolean b) {
    heapValid = b;
  }

  /**
   * put your documentation comment here
   * @param b
   */
  final void setScalarValid (boolean b) {
    scalarValid = b;
  }

  /**
   * Set up instructions for an form of heap Array SSA, or turn it
   * off
   */
  OPT_SSAOptions (boolean scalarsOnly, boolean backwards, boolean insertUsePhis, 
      JDK2_Set heapTypes) {
    this.scalarsOnly = scalarsOnly;
    this.backwards = backwards;
    this.insertUsePhis = insertUsePhis;
    this.heapTypes = heapTypes;
    scalarValid = false;
    heapValid = false;
  }

  /**
   * default configuration: just perform forward scalar SSA
   */
  OPT_SSAOptions () {
    this.scalarsOnly = true;
    this.backwards = false;
    this.insertUsePhis = false;
    this.heapTypes = null;
    scalarValid = false;
    heapValid = false;
  }

  /**
   * Given a desired set of SSA Options, does this set of SSA Options
   * describe enough information to satisfy the desiree?
   *
   * @param d the desired SSA options
   */
  boolean satisfies (OPT_SSAOptions d) {
    // 1. At a minimum , scalars must be valid
    if (!scalarValid)
      return  false;
    // 2. OK, scalar SSA is valid.  Is this enough?
    if (d.getScalarsOnly())
      return  true;
    // 3. OK, we desire more than scalars.  So now, at least
    //    Heap SSA must be valid
    if (!heapValid)
      return  false;
    // 4. OK, Heap Array SSA is valid.  Do we have the correct
    //    backwards, usePhis, and heapTypes??
    if (backwards != d.getBackwards())
      return  false;
    if (insertUsePhis != d.getInsertUsePhis())
      return  false;
    if (heapTypes != d.getHeapTypes())
      return  false;
    // Got this far.  SUCCESS!!
    return  true;
  }
}



