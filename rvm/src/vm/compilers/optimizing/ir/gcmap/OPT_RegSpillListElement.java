/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;

/**
 * A class to hold each element in the GCIRMap
 * @author Michael Hind
 */
public class OPT_RegSpillListElement extends com.ibm.JikesRVM.opt.OPT_LinkedListElement {

  /**
   * this should be a symbolic register
   */
  private OPT_Register symbolicReg; 

  /**
   * this could be either a spill or a real reg number
   */
  private int value;            

  /**
   * Constructor
   * @param symbolicReg the symbolic register holding the reference
   */
  public OPT_RegSpillListElement(OPT_Register symbolicReg) {
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
  public final void setRealReg(OPT_Register reg) {
    // we store registers as non-positive numbers to distinguish them from 
    // spills
    this.value = -reg.number;
  }

  /**
   * Is this a spill?
   * @return whether this is a spill
   */
  public final boolean isSpill() {
    return  (value > 0);
  }

  /**
   * returns the symbolic register associated with this object
   * @return the symbolic register associated with this object
   */
  public final OPT_Register getSymbolicReg() {
    return  symbolicReg;
  }

  /**
   * returns the real (physical) register associated with this object
   * @return the real (physical) register associated with this object
   */
  public final int getRealRegNumber() {
    if (VM.VerifyAssertions) {
      VM._assert(!isSpill(), 
        "OPT_RegSpillListElement asked for a Real Reg, when it had a spill");
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
      VM._assert(isSpill(), 
      "OPT_RegSpillListElement asked for a spill, when it had a real register");
    }

    return value;
  }

  /**
   * return a string version of this object
   * @return string version of this object
   */
  public String toString() {
    StringBuffer buf = new StringBuffer("");
    buf.append("(" + symbolicReg + ", ");
    if (isSpill()) {
      buf.append("Sp: " + getSpill());
    } else {
      buf.append("Reg: " + getRealRegNumber());
    }
    buf.append(")  ");
    return  buf.toString();
  }
}



