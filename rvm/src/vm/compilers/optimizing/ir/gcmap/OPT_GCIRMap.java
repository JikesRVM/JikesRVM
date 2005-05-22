/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.*;

/**
 *
 *  This class holds GC maps for various program points.
 *  This data structure is IR-based.  In a later phase, this information
 *  will be used to create the final GC map (see VM_OptMachineCodeMap.java)
 *
 *  @author Michael Hind
 */
public final class OPT_GCIRMap {

  /**
   *  This is the list of maps
   *   Each element on the list is an OPT_GCIRMapElement, which is a pair
   *          - an IR instruction (the GC point)
   *          - a list of OPT_RegSpillListElement, which initially hold symbolic 
   *               registers that are references
   *               (these are expanded to either physical regs or spills
   *                by the register allocator)
   */
  private OPT_LinkedList list = new OPT_LinkedList();

  /**
   *  The number of GC points in the map, i.e., the number of instructions
   *  we have maps for. 
   */
  private int numInstructionMaps;

  /**
   *  Used for class-wide debugging
   */
  private static final boolean DEBUG = false;

  /**
   * returns the number of GC points in this map, i.e., the number of 
   * instructions we have maps for. 
   * @return the number of GC points in this map
   */
  public final int getNumInstructionMaps() {
    return numInstructionMaps;
  }

  /**
   *  Calculates the number of spill entries in this GCIRMap
   *  This is the total number of spills for all instructions
   *  in this map.
   *  @return  the number of spill entries in this map
   */
  public final int countNumSpillElements() {
    // Since spill locations are not determined until after
    // register allocation occurs, i.e., after the initial
    // IR-based maps are created, we actually count the
    // number of spills.
    int count = 0;
    OPT_GCIRMapEnumerator GCIRMEnum = enumerator();
    while (GCIRMEnum.hasMoreElements()) {
      OPT_GCIRMapElement elem = (OPT_GCIRMapElement)GCIRMEnum.nextElement();
      count += elem.countNumSpillElements();
    }
    return count;
  }

  /**
   * This method creates a regSpillList from the passed live set.
   * @param set the set of registers, encoded as a OPT_LiveSet object 
   * @return a list corresponding to the set passed
   */
  public OPT_LinkedList createDU(OPT_LiveSet set) {
    if (DEBUG) {
      System.out.println("creating a RegList for " + set);
    }

    // construct register list
    OPT_LinkedList regList = new OPT_LinkedList();
    OPT_LiveSetEnumerator lsEnum = set.enumerator();
    while (lsEnum.hasMoreElements()) {
      OPT_RegisterOperand regOp = (OPT_RegisterOperand)lsEnum.nextElement();

      // add this register to the regList, if it is a reference
      //  and not a physcial register
      if (regOp.type.isReferenceType() && !regOp.register.isPhysical()) {
        OPT_RegSpillListElement elem = 
          new OPT_RegSpillListElement(regOp.register);
        regList.append(elem);
      }
    }
    return regList;
  }

  /**
   * This method inserts a new entry into the GCIRMap
   * @param inst    the IR instruction we care about
   * @param regList the set of symbolic registers as a list
   */
  public void insert(OPT_Instruction inst, OPT_LinkedList regList) {

    // make a GCIRMapElement and put it on the big list
    OPT_GCIRMapElement item = new OPT_GCIRMapElement(inst, regList);

    if (DEBUG) {
      System.out.println("Inserting new item: "+ item);
    }

    list.append(item);
    numInstructionMaps++;
  }

  /**
   * This method removes an entry in the GCIRMap that is specified
   * by inst. Only one element of the list will be removed per call.
   * If the instruction is not found in the GC Map and exeception is thrown.
   * @param inst    the IR instruction we want to remove
   */
  public void delete(OPT_Instruction inst) {

    boolean instructionInList = false;
    // make sure list is not null
    if (list.first() != null) {

      OPT_GCIRMapElement ptr = (OPT_GCIRMapElement)list.first(); 
      // is it at the head of the list
      if (ptr.getInstruction() == inst) {
        numInstructionMaps--;
        instructionInList = true;
        list.removeHead();
      } else {
        // is it in the list
        for (OPT_GCIRMapElement ptr_next = (OPT_GCIRMapElement)ptr.getNext();
             ptr_next != null; 
             ptr = ptr_next,    ptr_next = (OPT_GCIRMapElement)ptr.getNext()) {
          
          if (ptr_next.getInstruction() == inst) {
            numInstructionMaps--;
            instructionInList = true;
            list.removeNext(ptr);
            break;
          }
        }
      }
    }
    if (! instructionInList) {
      throw new OPT_OptimizingCompilerException("OPT_GCIRMap.delete("+inst+
                                                ") did not delete instruction from GC Map ");
    }
  }

  /**
   * This method moves an entry in the GCIRMap that is specified
   * by inst to the end of the list. Only one element of the list will be moved per call.
   * If the instruction is not found in the GC Map and exeception is thrown.
   * @param inst    the IR instruction we want to remove
   */
  public void moveToEnd(OPT_Instruction inst) {
    boolean instructionInList = false;
    if (list.first() != null) {
      OPT_GCIRMapElement ptr = (OPT_GCIRMapElement)list.first(); 
      if (ptr.getInstruction() == inst) {
        // it is at the head of the list
        instructionInList = true;
        list.removeHead();
        list.append(ptr);
      } else {
        // is it in the list
        for (OPT_GCIRMapElement ptr_next = (OPT_GCIRMapElement)ptr.getNext();
             ptr_next != null; 
             ptr = ptr_next,    ptr_next = (OPT_GCIRMapElement)ptr.getNext()) {
          if (ptr_next.getInstruction() == inst) {
            instructionInList = true;
            list.removeNext(ptr);
            list.append(ptr_next);
            break;
          }
        }
      }
    }
    if (!instructionInList) {
      throw new OPT_OptimizingCompilerException("OPT_GCIRMap.moveToEnd("+inst+
                                                ") did not delete instruction from GC Map ");
    }
  }

  /**
   * This method inserts an entry for a "twin" instruction immediately after the 
   * original entry.
   * If the instruction is not found in the GC Map an exeception is thrown.
   * @param inst    the orignal IR instruction 
   * @param twin    the new twin IR instruction 
   */
  public void insertTwin(OPT_Instruction inst, OPT_Instruction twin) {
    for (OPT_GCIRMapElement ptr = (OPT_GCIRMapElement)list.first(); 
         ptr != null; 
         ptr  = (OPT_GCIRMapElement)ptr.getNext()) {
      if (ptr.getInstruction() == inst) {
        numInstructionMaps++;
        ptr.insertAfter(ptr.createTwin(twin));
        return;
      }
    }           
    throw new OPT_OptimizingCompilerException("OPT_GCIRMap.createTwin: "+inst+" not found");
  }

  /**
   * creates and returns an enumerator for this object
   * @return an enumerator for this object
   */
  public OPT_GCIRMapEnumerator enumerator() {
    return  new OPT_GCIRMapEnumerator(list);
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
    StringBuffer buf = new StringBuffer("");
    if (list.first() == null) {
      buf.append("empty"); 
    } else {
      for (OPT_GCIRMapElement ptr = (OPT_GCIRMapElement)list.first(); 
           ptr != null; ptr = (OPT_GCIRMapElement)ptr.getNext()) {
        buf.append(ptr);
      }
    }
    return  buf.toString();
  }
}



