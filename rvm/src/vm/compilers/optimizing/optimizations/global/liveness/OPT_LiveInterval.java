/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class contains useful methods for managing liveIntervals.
 *
 * @author Michael Hind
 * @author Mauricio Serrano
 */
final class OPT_LiveInterval {

  private static final boolean DEBUG = false;

  /**
   * This method iterates over each element in the the passed live set.
   * For each element, it checks if an existing live interval node for
   * the basic block passed exists.  If one does not exist, it creates
   * a node with the end instruction being "inst".  If one already exists
   * no action is taken.
   *
   * @param set  the set of registers, encoded as a OPT_LiveSet object 
   * @param block the basic block
   * @param inst the intruction where the register's live range ends, 
   *             null represents the end of the basic block
   */
  static public void createEndLiveRange(OPT_LiveSet set, 
                                        OPT_BasicBlock block, 
                                        OPT_Instruction inst) {
    if (DEBUG) {
      if (inst == null) {
        System.out.println("The following are live on exit of block "+ 
                           block.getNumber()  +"\n"+ set);
      } else {
        System.out.println("The following are live ending at inst\n  "+ inst
                           +" for block "+  block.getNumber() +"\n"+ set);
      }
    }

    OPT_LiveSetEnumerator enum = set.enumerator();
    while (enum.hasMoreElements()) {
      OPT_RegisterOperand regOp = (OPT_RegisterOperand) enum.nextElement();
      createEndLiveRange(regOp.register, block, inst);
    }
  }

  /**
   * This method checks if an existing unresolved live interval node,
   * i.e., one that has an end instruction, but no beginning instruction,
   * is present for the register and basic block passed.  If one does not exist,
   * it creates a node with the end instruction being "inst".  
   * If one already exists no action is taken.
   *
   * @param set  the set of registers, encoded as a OPT_LiveSet object 
   * @param block the basic block
   */
  static public void createEndLiveRange(OPT_Register reg, 
                                        OPT_BasicBlock block,
                                        OPT_Instruction inst) { 

    if (DEBUG) {
      System.out.println("Marking Register "+ reg
                         +"'s live range as ENDing at instruction\n   "+
                         inst +" in block #"+ block.getNumber());
      printLiveIntervalList(block);
    }

    if (!containsUnresolvedElement(block, reg)) {
      OPT_LiveIntervalElement elem = 
        new OPT_LiveIntervalElement(reg, null, inst);

      // add elem to the list for the basic block
      block.prependLiveIntervalElement(elem);
    }
  }

  /**
   * This method finds the LiveInterval node for the register and basic block
   * passed.  It then sets the begin instruction to the instruction passed
   * and moves the node to the proper place on the list block list.
   * (The block list is sorted by "begin" instruction.
   *
   * @param reg   the register of interest
   * @param inst  the "begin" instruction
   * @param block the basic block of interest
   */
  static public void setStartLiveRange(OPT_Register reg, 
                                       OPT_Instruction inst, 
                                       OPT_BasicBlock block) {
    if (DEBUG) {
      System.out.println("Marking Register "+ reg
                         +"'s live range as STARTing at instruction\n   "+
                         inst +" in block #"+ block.getNumber());
    }

    OPT_LiveIntervalElement prev = null; 
    OPT_LiveIntervalElement elem = 
      (OPT_LiveIntervalElement) block.getFirstLiveIntervalElement();
    while (elem != null) {
      if (elem.getRegister() == reg && elem.getBegin() == null) {
        break;
      }

      prev = elem;
      elem = (OPT_LiveIntervalElement) elem.getNext(); 
    }

    if (elem != null) {
      elem.setBegin(inst);

      // we want the list sorted by "begin" instruction.  Since
      // we are *assuming* that we are called in a traversal that is
      // visiting instructions backwards, the instr passed will always
      // be the most recent.  Thus, we move "elem" to the front of the list.
      if (prev != null) {
        // remove elem from current position
        prev.setNext(elem.next);

        // add it to the begining
        block.prependLiveIntervalElement(elem);
      }

      // if prev == null, the element is already first in the list!
    } else {
      // if we didn't find it, it means we have a def that is not later
      // used, i.e., a dead assignment.  This may exist because the 
      // instruction has side effects such as a function call or a PEI
      // In this case, we create a LiveIntervalElement node with begining
      // and ending instruction "inst"
      OPT_LiveIntervalElement newElem = new OPT_LiveIntervalElement(reg,
                                                                    inst,
                                                                    inst);
      block.prependLiveIntervalElement(newElem);
    }

    if (DEBUG) {
      System.out.println("after add");
      printLiveIntervalList(block);
    }
  }

  /**
   * This method finds any LiveInterval node that does not have a start
   * instruction (it is null) and moves this node to the front of the list.
   *
   * @param block the basic block of interest
   */
  static public void moveUpwardExposedRegsToFront(OPT_BasicBlock block) {

    OPT_LiveIntervalElement prev = 
      (OPT_LiveIntervalElement) block.getFirstLiveIntervalElement();
    if (prev == null)
      return;

    // The first element is already at the front, so move on to the next one
    OPT_LiveIntervalElement elem = prev.getNext();
    
    while (elem != null) {
      if (elem.getBegin() == null) {
        // remove elem from current position
        prev.setNext(elem.getNext());

        // add it to the begining, se
        block.prependLiveIntervalElement(elem);

        // the next victum is the *new* one after prev
        elem = (OPT_LiveIntervalElement) prev.getNext(); 
      } else {
        prev = elem;
        elem = (OPT_LiveIntervalElement) elem.getNext(); 
      }
    }
  }

  /** 
   * Check to see if an unresolved LiveIntervalElement node for the register
   * passed exists for the basic block passed.
   *
   * @param block the block
   * @param reg   the register of interest
   * @return <code>true</code> if it does or <code>false</code>
   *         if it does not
   */
  static private boolean containsUnresolvedElement(OPT_BasicBlock block, 
                                                   OPT_Register reg) {

    if (DEBUG) {
      System.out.println("containsUnresolvedElement called, block: "+ block
                         +" register: "+ reg);
      printLiveIntervalList(block);
    }

    for (OPT_LiveIntervalElement elem = block.getFirstLiveIntervalElement();
         elem != null;
         elem = (OPT_LiveIntervalElement) elem.getNext()) {
      // if we got an element, down case it to OPT_LiveIntervalElement
      if (elem.getRegister() == reg && elem.getBegin() == null) {
        return true;
      }
    }
    return false;
  }

  /** 
   * Print the live intervals for a block.
   *
   * @param block the block
   */
  static public void printLiveIntervalList(OPT_BasicBlock block) {
    System.out.println("Live Interval List for "+ block);
    for (OPT_LiveIntervalElement elem 
           = (OPT_LiveIntervalElement) block.getFirstLiveIntervalElement();
         elem != null;
         elem = (OPT_LiveIntervalElement) elem.getNext()) {
      System.out.println("  "+ elem);
    }
  }
}
