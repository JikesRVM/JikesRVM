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
package org.jikesrvm.compilers.opt.ssa;

/**
 * This module defines parameters to the SSA construction process.
 * This is used to pass information between compiler phases.
 * <p>
 * IMPORTANT: Phases that change the SSA state MUST update the SSA
 *            actual options held by the IR object.
 */
public class SSAOptions {
  /*
   * options for SSA construction
   */
  /** construct SSA only for scalars? */
  private boolean scalarsOnly;
  /** construct Heap SSA for backwards analysis? */
  private boolean backwards;
  /** constuct Heap SSA with uPhi functions? */
  private boolean insertUsePhis;
  /** constuct Heap SSA with PEI deps? */
  private boolean insertPEIDeps;
  /** ignore guards (validation regs) ? */
  private boolean excludeGuards;
  /** restrict Heap SSA to this set of types? */
  private java.util.Set<Object> heapTypes;
  /** is Heap SSA info valid? */
  private boolean heapValid;
  /** is Scalar SSA info valid? */
  private boolean scalarValid;
  /** abort all ssa passes? */
  private boolean abort;

  final boolean getAbort() {
    return abort;
  }

  final void setAbort(boolean b) {
    abort = b;
  }

  final boolean getScalarsOnly() {
    return scalarsOnly;
  }

  final boolean getBackwards() {
    return backwards;
  }

  final boolean getInsertUsePhis() {
    return insertUsePhis;
  }

  final boolean getInsertPEIDeps() {
    return insertPEIDeps;
  }

  final boolean getExcludeGuards() {
    return excludeGuards;
  }

  final java.util.Set<Object> getHeapTypes() {
    return heapTypes;
  }

  public final boolean getHeapValid() {
    return heapValid;
  }

  public final boolean getScalarValid() {
    return scalarValid;
  }

  final void setScalarsOnly(boolean b) {
    scalarsOnly = b;
  }

  final void setBackwards(boolean b) {
    backwards = b;
  }

  final void setInsertUsePhis(boolean b) {
    insertUsePhis = b;
  }

  final void setExcludeGuards(boolean b) {
    excludeGuards = b;
  }

  final void setInsertPEIDeps(boolean b) {
    insertPEIDeps = b;
  }

  final void setHeapTypes(java.util.Set<Object> s) {
    heapTypes = s;
  }

  // CAUTION: only Enter and LeaveSSA should use the following.
  // Don't use these unless you know what you're doing.
  final void setHeapValid(boolean b) {
    heapValid = b;
  }

  final void setScalarValid(boolean b) {
    scalarValid = b;
  }

  /**
   * Set up instructions for an form of heap Array SSA, or turn it
   * off
   */
  SSAOptions(boolean scalarsOnly, boolean backwards, boolean insertUsePhis, java.util.Set<Object> heapTypes) {
    this.scalarsOnly = scalarsOnly;
    this.backwards = backwards;
    this.insertUsePhis = insertUsePhis;
    this.heapTypes = heapTypes;
    this.insertPEIDeps = false;
    this.excludeGuards = false;
    scalarValid = false;
    heapValid = false;
  }

  /**
   * default configuration: just perform forward scalar SSA
   */
  SSAOptions() {
    this.scalarsOnly = true;
    this.backwards = false;
    this.insertUsePhis = false;
    this.heapTypes = null;
    this.insertPEIDeps = false;
    this.excludeGuards = false;
    scalarValid = false;
    heapValid = false;
  }

  /**
   * Given a desired set of SSA Options, does this set of SSA Options
   * describe enough information to satisfy the desire?
   *
   * @param d the desired SSA options
   */
  boolean satisfies(SSAOptions d) {
    // 1. At a minimum , scalars must be valid
    if (!scalarValid) {
      return false;
    }
    // 2. OK, scalar SSA is valid.  Is this enough?
    if (d.getScalarsOnly()) {
      return true;
    }
    // 3. OK, we desire more than scalars.  So now, at least
    //    Heap SSA must be valid
    if (!heapValid) {
      return false;
    }
    // 4. OK, Heap Array SSA is valid.  Do we have the correct
    //    backwards, usePhis, and heapTypes??
    if (backwards != d.getBackwards()) {
      return false;
    }
    if (insertUsePhis != d.getInsertUsePhis()) {
      return false;
    }
    if (insertPEIDeps != d.getInsertPEIDeps()) {
      return false;
    }
    if (excludeGuards != d.getExcludeGuards()) {
      return false;
    }
    if (heapTypes != d.getHeapTypes()) {
      return false;
    }
    // Got this far.  SUCCESS!!
    return true;
  }
}



