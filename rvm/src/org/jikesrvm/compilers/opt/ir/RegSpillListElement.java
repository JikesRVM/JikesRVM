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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.VM;

/**
 * A class to hold each element in the GCIRMap
 */
public class RegSpillListElement {

  /**
   * this should be a symbolic register
   */
  private final Register symbolicReg;

  /**
   * this could be either a spill or a real reg number
   */
  private int value;

  /**
   * Constructor
   * @param symbolicReg the symbolic register holding the reference
   */
  public RegSpillListElement(Register symbolicReg) {
    this.symbolicReg = symbolicReg;
  }

  /**
   * Sets the spill component associated with this object
   * @param value the spill value
   */
  public final void setSpill(int value) {
    if (VM.VerifyAssertions) VM._assert(value > 0);
    this.value = value;
  }

  /**
   * Sets the real (i.e., physical) register component associated with
   *  this object
   * @param reg the real (physical) register
   */
  public final void setRealReg(Register reg) {
    // we store registers as non-positive numbers to distinguish them from
    // spills
    this.value = -reg.number;
  }

  /**
   * Is this a spill?
   * @return whether this is a spill
   */
  public final boolean isSpill() {
    return (value > 0);
  }

  /**
   * returns the symbolic register associated with this object
   * @return the symbolic register associated with this object
   */
  public final Register getSymbolicReg() {
    return symbolicReg;
  }

  /**
   * returns the real (physical) register associated with this object
   * @return the real (physical) register associated with this object
   */
  public final int getRealRegNumber() {
    if (VM.VerifyAssertions) {
      VM._assert(!isSpill(), "RegSpillListElement asked for a Real Reg, when it had a spill");
    }

    // real regs are stored as non-positive values
    return -value;
  }

  /**
   * returns the spill value associated with this object
   * @return the spill value associated with this object
   */
  public final int getSpill() {
    if (VM.VerifyAssertions) {
      VM._assert(isSpill(), "RegSpillListElement asked for a spill, when it had a real register");
    }

    return value;
  }

  /**
   * return a string version of this object
   * @return string version of this object
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    buf.append("(").append(symbolicReg).append(", ");
    if (isSpill()) {
      buf.append("Sp: ").append(getSpill());
    } else {
      buf.append("Reg: ").append(getRealRegNumber());
    }
    buf.append(")  ");
    return buf.toString();
  }
}



