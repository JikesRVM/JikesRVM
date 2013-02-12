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

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.liveness.LiveSet;
import org.jikesrvm.compilers.opt.liveness.LiveSetEnumerator;
import org.jikesrvm.util.LinkedListRVM;

/**
 *  This class holds GC maps for various program points.
 *  This data structure is IR-based.  In a later phase, this information
 *  will be used to create the final GC map (see OptMachineCodeMap.java)
 */
public final class GCIRMap implements Iterable<GCIRMapElement> {
  /**
   *  This is the list of maps.
   *  Each element on the list is an GCIRMapElement, which is a pair
   *   <ol>
   *     <li>an IR instruction (the GC point)
   *     <li>a list of RegSpillListElement, which initially hold symbolic
   *         registers that are references (these are expanded to either
   *         physical regs or spills by the register allocator)
   *   </ol>
   */
  private final LinkedListRVM<GCIRMapElement> list = new LinkedListRVM<GCIRMapElement>();

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
    for (GCIRMapElement elem : this) {
      count += elem.countNumSpillElements();
    }
    return count;
  }

  /**
   * TODO What is this method doing in this class ?? RJG
   *
   * This method creates a regSpillList from the passed live set.
   * @param set the set of registers, encoded as a LiveSet object
   * @return a list corresponding to the set passed
   */
  public List<RegSpillListElement> createDU(LiveSet set) {
    if (DEBUG) {
      System.out.println("creating a RegList for " + set);
    }

    // construct register list
    List<RegSpillListElement> regList = new LinkedListRVM<RegSpillListElement>();
    LiveSetEnumerator lsEnum = set.enumerator();
    while (lsEnum.hasMoreElements()) {
      RegisterOperand regOp = lsEnum.nextElement();

      // add this register to the regList, if it is a reference
      //  and not a physcial register
      if (regOp.getType().isReferenceType() && !regOp.getRegister().isPhysical()) {
        RegSpillListElement elem = new RegSpillListElement(regOp.getRegister());
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
  public void insert(Instruction inst, List<RegSpillListElement> regList) {

    // make a GCIRMapElement and put it on the big list
    GCIRMapElement item = new GCIRMapElement(inst, regList);

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
  public void delete(Instruction inst) {

    Iterator<GCIRMapElement> iter = list.iterator();
    while (iter.hasNext()) {
      GCIRMapElement ptr = iter.next();
      if (ptr.getInstruction() == inst) {
        iter.remove();
        return;
      }
    }
    throw new OptimizingCompilerException("GCIRMap.delete(" +
                                              inst +
                                              ") did not delete instruction from GC Map ");
  }

  /**
   * This method moves an entry in the GCIRMap that is specified
   * by inst to the end of the list. Only one element of the list will be moved per call.
   * If the instruction is not found in the GC Map and exception is thrown.
   * @param inst    the IR instruction we want to remove
   */
  public void moveToEnd(Instruction inst) {
    Iterator<GCIRMapElement> iter = list.iterator();
    while (iter.hasNext()) {
      GCIRMapElement newPtr = iter.next();
      if (newPtr.getInstruction() == inst) {
        iter.remove();
        list.add(newPtr);
        return;
      }
    }
    throw new OptimizingCompilerException("GCIRMap.moveToEnd(" +
                                              inst +
                                              ") did not delete instruction from GC Map ");
  }

  /**
   * This method inserts an entry for a "twin" instruction immediately after the
   * original entry.
   * If the instruction is not found in the GC Map an exception is thrown.
   * @param inst    the original IR instruction
   * @param twin    the new twin IR instruction
   */
  public void insertTwin(Instruction inst, Instruction twin) {
    ListIterator<GCIRMapElement> iter = list.listIterator();
    while (iter.hasNext()) {
      GCIRMapElement newPtr = iter.next();
      if (newPtr.getInstruction() == inst) {
        iter.add(newPtr.createTwin(twin));
        return;
      }
    }
    throw new OptimizingCompilerException("GCIRMap.createTwin: " + inst + " not found");
  }

  @Override
  public Iterator<GCIRMapElement> iterator() {
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
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("");
    if (list.isEmpty()) {
      buf.append("empty");
    } else {
      for (GCIRMapElement ptr : list) {
        buf.append(ptr);
      }
    }
    return buf.toString();
  }
}
