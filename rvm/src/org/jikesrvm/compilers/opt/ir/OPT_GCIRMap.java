/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.jikesrvm.compilers.opt.OPT_LiveSet;
import org.jikesrvm.compilers.opt.OPT_LiveSetEnumerator;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.util.VM_LinkedList;

/**
 *
 *  This class holds GC maps for various program points.
 *  This data structure is IR-based.  In a later phase, this information
 *  will be used to create the final GC map (see VM_OptMachineCodeMap.java)
 */
public final class OPT_GCIRMap implements Iterable<OPT_GCIRMapElement> {
  /**
   *  This is the list of maps
   *   Each element on the list is an OPT_GCIRMapElement, which is a pair
   *          - an IR instruction (the GC point)
   *          - a list of OPT_RegSpillListElement, which initially hold symbolic 
   *               registers that are references
   *               (these are expanded to either physical regs or spills
   *                by the register allocator)
   */
  private VM_LinkedList<OPT_GCIRMapElement> list = new VM_LinkedList<OPT_GCIRMapElement>();

  /**
   *  Used for class-wide debugging
   */
  private static final boolean DEBUG = false;

  /**
   * returns the number of GC points in this map, i.e., the number of 
   * instructions we have maps for. 
   * @return the number of GC points in this map
   */
  public int getNumInstructionMaps() {
    return list.size();
  }

  /**
   *  Calculates the number of spill entries in this GCIRMap
   *  This is the total number of spills for all instructions
   *  in this map.
   *  @return the number of spill entries in this map
   */
  public int countNumSpillElements() {
    // Since spill locations are not determined until after
    // register allocation occurs, i.e., after the initial
    // IR-based maps are created, we actually count the
    // number of spills.
    int count = 0;
    for (OPT_GCIRMapElement elem : this) {
      count += elem.countNumSpillElements();
    }
    return count;
  }

  /**
   * TODO What is this method doing in this class ?? RJG
   *
   * This method creates a regSpillList from the passed live set.
   * @param set the set of registers, encoded as a OPT_LiveSet object 
   * @return a list corresponding to the set passed
   */
  public List<OPT_RegSpillListElement> createDU(OPT_LiveSet set) {
    if (DEBUG) {
      System.out.println("creating a RegList for " + set);
    }

    // construct register list
    List<OPT_RegSpillListElement> regList = new VM_LinkedList<OPT_RegSpillListElement>();
    OPT_LiveSetEnumerator lsEnum = set.enumerator();
    while (lsEnum.hasMoreElements()) {
      OPT_RegisterOperand regOp = lsEnum.nextElement();

      // add this register to the regList, if it is a reference
      //  and not a physcial register
      if (regOp.type.isReferenceType() && !regOp.register.isPhysical()) {
        OPT_RegSpillListElement elem =
            new OPT_RegSpillListElement(regOp.register);
        regList.add(elem);
      }
    }
    return regList;
  }

  /**
   * This method inserts a new entry into the GCIRMap
   * @param inst    the IR instruction we care about
   * @param regList the set of symbolic registers as a list
   */
  public void insert(OPT_Instruction inst, List<OPT_RegSpillListElement> regList) {

    // make a GCIRMapElement and put it on the big list
    OPT_GCIRMapElement item = new OPT_GCIRMapElement(inst, regList);

    if (DEBUG) {
      System.out.println("Inserting new item: " + item);
    }

    list.add(item);
  }

  /**
   * This method removes an entry in the GCIRMap that is specified
   * by inst. Only one element of the list will be removed per call.
   * If the instruction is not found in the GC Map and exeception is thrown.
   * @param inst    the IR instruction we want to remove
   */
  public void delete(OPT_Instruction inst) {

    Iterator<OPT_GCIRMapElement> iter = list.iterator();
    while (iter.hasNext()) {
      OPT_GCIRMapElement ptr = iter.next();
      if (ptr.getInstruction() == inst) {
        iter.remove();
        return;
      }
    }
    throw new OPT_OptimizingCompilerException("OPT_GCIRMap.delete(" + inst +
                                              ") did not delete instruction from GC Map ");
  }

  /**
   * This method moves an entry in the GCIRMap that is specified
   * by inst to the end of the list. Only one element of the list will be moved per call.
   * If the instruction is not found in the GC Map and exeception is thrown.
   * @param inst    the IR instruction we want to remove
   */
  public void moveToEnd(OPT_Instruction inst) {
    Iterator<OPT_GCIRMapElement> iter = list.iterator();
    while (iter.hasNext()) {
      OPT_GCIRMapElement newPtr = iter.next();
      if (newPtr.getInstruction() == inst) {
        iter.remove();
        list.add(newPtr);
        return;
      }
    }
    throw new OPT_OptimizingCompilerException("OPT_GCIRMap.moveToEnd(" + inst +
                                              ") did not delete instruction from GC Map ");
  }

  /**
   * This method inserts an entry for a "twin" instruction immediately after the 
   * original entry.
   * If the instruction is not found in the GC Map an exeception is thrown.
   * @param inst    the orignal IR instruction 
   * @param twin    the new twin IR instruction 
   */
  public void insertTwin(OPT_Instruction inst, OPT_Instruction twin) {
    ListIterator<OPT_GCIRMapElement> iter = list.listIterator();
    while (iter.hasNext()) {
      OPT_GCIRMapElement newPtr = iter.next();
      if (newPtr.getInstruction() == inst) {
        iter.add(newPtr.createTwin(twin));
        return;
      }
    }
    throw new OPT_OptimizingCompilerException("OPT_GCIRMap.createTwin: " + inst + " not found");
  }

  public Iterator<OPT_GCIRMapElement> iterator() {
    return list.iterator();
  }

  /**
   * dumps the map
   */
  public void dump() {
    System.out.println(toString());
  }

  /**
   * @return string version of this object
   */
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    if (list.isEmpty()) {
      buf.append("empty");
    } else {
      for (OPT_GCIRMapElement ptr : list) {
        buf.append(ptr);
      }
    }
    return buf.toString();
  }
}



