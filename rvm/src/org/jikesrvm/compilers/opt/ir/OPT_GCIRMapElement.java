/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import java.util.List;

/**
 *  This class holds each element in the OPT_GCIRMap
 */
public final class OPT_GCIRMapElement {

  /**
   *  The instruction, i.e., GC point
   */
  private final OPT_Instruction inst;

  /**
   *  The list of references (either symbolic regs or physical regs & spills)
   */
  private final List<OPT_RegSpillListElement> regSpillList;

  /**
   * Constructor
   * @param inst the instruction of interest
   * @param regSpillList the list of references either symbolic (before regalloc)
   *                or physical/spill location (after regalloc)
   */
  public OPT_GCIRMapElement(OPT_Instruction inst, List<OPT_RegSpillListElement> regSpillList) {
    this.inst = inst;
    this.regSpillList = regSpillList;
  }

  /**
   * Create a twin entry: required when the same MIR GC point
   * is split into two instructions, both of which are PEIs
   * after register allocation/GCIRMap creation.
   */
  public OPT_GCIRMapElement createTwin(OPT_Instruction inst) {
    return new OPT_GCIRMapElement(inst, this.regSpillList);
  }

  /**
   * return the instruction with this entry
   * @return the instruction with this entry
   */
  public OPT_Instruction getInstruction() {
    return inst;
  }

  /**
   * returns an enumerator to access the registers/spills for this entry
   * @return an enumerator to access the registers/spills for this entry
   */
  public List<OPT_RegSpillListElement> regSpillList() {
    return regSpillList;
  }

  /**
   * Add a new spill list element for this map element
   */
  public void addRegSpillElement(OPT_RegSpillListElement e) {
    regSpillList.add(e);
  }

  /**
   * Delete a spill list element from this map element
   */
  public void deleteRegSpillElement(OPT_RegSpillListElement e) {
    regSpillList.remove(e);
  }

  /**
   * Counts and returns the number of references for this map
   * @return the number of references, either regs or spills for this map
   */
  public int countNumElements() {
    return regSpillList.size();
  }

  /**
   * Counts and returns the number of register elements (not spills)
   *     for this entry
   * @return the number of register elements for this entry
   */
  public int countNumRegElements() {
    int count = 0;

    for (OPT_RegSpillListElement elem : regSpillList) {
      if (!elem.isSpill()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Counts and returns the number of spill for this entry
   * @return the number of spill for this entry
   */
  public int countNumSpillElements() {
    int count = 0;
    // traverse the list and compute how many spills exist
    for (OPT_RegSpillListElement elem : regSpillList) {
      if (elem.isSpill()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Return a string version of this object
   * @return a string version of this object
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    buf.append(" Instruction: ").append(inst.bcIndex).append(", ").append(inst);
    for (OPT_RegSpillListElement elem : regSpillList) {
      buf.append(elem).append("  ");
    }
    buf.append("\n");
    return buf.toString();
  }
}
