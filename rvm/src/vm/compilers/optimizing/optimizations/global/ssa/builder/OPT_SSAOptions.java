/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;

/**
 * This module defines parameters to the SSA construction process.
 * This is used to pass information between compiler phases.  
 * IMPORTANT: Phases that change the SSA state MUST update the SSA
 *            actual options held by the IR object.
 *
 * @author Stephen Fink
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
  private java.util.Set heapTypes;    // restrict Heap SSA to this set of types?
  private boolean heapValid;          // is Heap SSA info valid?
  private boolean scalarValid;        // is Scalar SSA info valid?

  final boolean getScalarsOnly () {
    return  scalarsOnly;
  }

  final boolean getBackwards () {
    return  backwards;
  }

  final boolean getInsertUsePhis () {
    return  insertUsePhis;
  }

  final boolean getInsertPEIDeps () {
    return  insertPEIDeps;
  }

  final java.util.Set getHeapTypes () {
    return  heapTypes;
  }

  final boolean getHeapValid () {
    return  heapValid;
  }

  final boolean getScalarValid () {
    return  scalarValid;
  }

  final void setScalarsOnly (boolean b) {
    scalarsOnly = b;
  }

  final void setBackwards (boolean b) {
    backwards = b;
  }

  final void setInsertUsePhis (boolean b) {
    insertUsePhis = b;
  }

  final void setInsertPEIDeps (boolean b) {
    insertPEIDeps = b;
  }

  final void setHeapTypes (java.util.Set s) {
    heapTypes = s;
  }

  // CAUTION: only Enter and LeaveSSA should use the following.
  // Don't use these unless you know what you're doing.
  final void setHeapValid (boolean b) {
    heapValid = b;
  }

  final void setScalarValid (boolean b) {
    scalarValid = b;
  }

  /**
   * Set up instructions for an form of heap Array SSA, or turn it
   * off
   */
  OPT_SSAOptions (boolean scalarsOnly, boolean backwards, boolean insertUsePhis, 
      java.util.Set heapTypes) {
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



