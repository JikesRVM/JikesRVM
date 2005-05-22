/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.*;

/**
 *  This class holds each element in the OPT_GCIRMap
 *  @author Michael Hind
 */
public final class OPT_GCIRMapElement extends OPT_LinkedListElement {

  /**
   *  The instruction, i.e., GC point
   */
  private OPT_Instruction inst;

  /**
   *  The list of references (either symbolic regs or physical regs & spills)
   */
  private OPT_LinkedList regSpillList;

  /**
   * Constructor
   * @param inst the instruction of interest
   * @param regSpillList the list of references either symbolic (before regalloc)
   *                or physical/spill location (after regalloc)
   */
  public OPT_GCIRMapElement(OPT_Instruction inst, 
                           OPT_LinkedList regSpillList) {
    this.inst = inst;
    this.regSpillList = regSpillList;
  }

  /**
   * Create a twin entry: required when the same MIR GC point
   * is split into two instructions, both of which are PEIs
   * after register allocation/GCIRMap creation.
   */
  public OPT_GCIRMapElement createTwin (OPT_Instruction inst) {
    return new OPT_GCIRMapElement(inst, this.regSpillList);
  }

  /**
   * return the instruction with this entry
   * @return the instruction with this entry
   */
  public final OPT_Instruction getInstruction() {
    return inst;
  }

  /**
   * returns an enumerator to access the registers/spills for this entry
   * @return an enumerator to access the registers/spills for this entry
   */
  public final OPT_RegSpillListEnumerator regSpillListEnumerator() {
    return new OPT_RegSpillListEnumerator(regSpillList);
  }

  /**
   * Add a new spill list element for this map element
   */
  public final void addRegSpillElement(OPT_RegSpillListElement e) {
    regSpillList.append(e);
  }

  /**
   * Delete a spill list element from this map element
   */
  public final void deleteRegSpillElement(OPT_RegSpillListElement e) {
    regSpillList.remove(e);
  }

  /**
   * Counts and returns the number of references for this map
   * @return the number of references, either regs or spills for this map
   */
  public final int countNumElements() {
    int count = 0;

    // traverse the list and compute how many regs exist
    OPT_RegSpillListEnumerator rslEnum = regSpillListEnumerator();
    while (rslEnum.hasMoreElements()) {
      OPT_RegSpillListElement elem = 
        (OPT_RegSpillListElement) rslEnum.nextElement();
      count++;
    }
    return count;
  }

  /**
   * Counts and returns the number of register elements (not spills) 
   *     for this entry
   * @return the number of register elements for this entry
   */
  public final int countNumRegElements() {
    int count = 0;

    // traverse the list and compute how many regs exist
    OPT_RegSpillListEnumerator rslEnum = regSpillListEnumerator();
    while (rslEnum.hasMoreElements()) {
      OPT_RegSpillListElement elem = 
        (OPT_RegSpillListElement) rslEnum.nextElement();
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
  public final int countNumSpillElements() {
    int count = 0;
    // traverse the list and compute how many spills exist
    OPT_RegSpillListEnumerator rslEnum = regSpillListEnumerator();
    while (rslEnum.hasMoreElements()) {
      OPT_RegSpillListElement elem = 
        (OPT_RegSpillListElement) rslEnum.nextElement();
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
    StringBuffer buf = new StringBuffer("");
    buf.append(" Instruction: " + inst.bcIndex + ", " + inst);
    OPT_RegSpillListEnumerator rslEnum = regSpillListEnumerator();
    while (rslEnum.hasMoreElements()) {
      OPT_RegSpillListElement elem = (OPT_RegSpillListElement)
          rslEnum.nextElement();
      buf.append(elem + "  ");
    }
    buf.append("\n");
    return  buf.toString();
  }
}



