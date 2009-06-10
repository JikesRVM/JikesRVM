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
package org.jikesrvm.compilers.opt.liveness;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.regalloc.LiveIntervalElement;

/**
 * This class contains useful methods for managing liveIntervals.
 */
final class LiveInterval {

  private static final boolean DEBUG = false;

  /**
   * This method iterates over each element in the the passed live set.
   * For each element, it checks if an existing live interval node for
   * the basic block passed exists.  If one does not exist, it creates
   * a node with the end instruction being "inst".  If one already exists
   * no action is taken.
   *
   * @param set  the set of registers, encoded as a LiveSet object
   * @param block the basic block
   * @param inst the intruction where the register's live range ends,
   *             null represents the end of the basic block
   */
  public static void createEndLiveRange(LiveSet set, BasicBlock block, Instruction inst) {
    if (DEBUG) {
      if (inst == null) {
        System.out.println("The following are live on exit of block " + block.getNumber() + "\n" + set);
      } else {
        System.out.println("The following are live ending at inst\n  " +
                           inst +
                           " for block " +
                           block.getNumber() +
                           "\n" +
                           set);
      }
    }

    LiveSetEnumerator lsEnum = set.enumerator();
    while (lsEnum.hasMoreElements()) {
      RegisterOperand regOp = lsEnum.nextElement();
      createEndLiveRange(regOp.getRegister(), block, inst);
    }
  }

  /**
   * This method checks if an existing unresolved live interval node, i.e.,
   * one that has an end instruction, but no beginning instruction, is present
   * for the register and basic block passed.  If one does not exist, it
   * creates a node with the end instruction being <code>inst</code>.  If one
   * already exists no action is taken.
   *
   * @param reg   The register
   * @param block The basic block
   * @param inst  The end instruction to use, if we have to create a neode.
   */
  public static void createEndLiveRange(Register reg, BasicBlock block, Instruction inst) {

    if (DEBUG) {
      System.out.println("Marking Register " +
                         reg +
                         "'s live range as ENDing at instruction\n   " +
                         inst +
                         " in block #" +
                         block.getNumber());
      printLiveIntervalList(block);
    }

    if (!containsUnresolvedElement(block, reg)) {
      LiveIntervalElement elem = new LiveIntervalElement(reg, null, inst);

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
  public static void setStartLiveRange(Register reg, Instruction inst, BasicBlock block) {
    if (DEBUG) {
      System.out.println("Marking Register " +
                         reg +
                         "'s live range as STARTing at instruction\n   " +
                         inst +
                         " in block #" +
                         block.getNumber());
    }

    LiveIntervalElement prev = null;
    LiveIntervalElement elem = block.getFirstLiveIntervalElement();
    while (elem != null) {
      if (elem.getRegister() == reg && elem.getBegin() == null) {
        break;
      }

      prev = elem;
      elem = elem.getNext();
    }

    if (elem != null) {
      elem.setBegin(inst);

      // we want the list sorted by "begin" instruction.  Since
      // we are *assuming* that we are called in a traversal that is
      // visiting instructions backwards, the instr passed will always
      // be the most recent.  Thus, we move "elem" to the front of the list.
      if (prev != null) {
        // remove elem from current position
        prev.setNext(elem.getNext());

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
      LiveIntervalElement newElem = new LiveIntervalElement(reg, inst, inst);
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
  public static void moveUpwardExposedRegsToFront(BasicBlock block) {

    LiveIntervalElement prev = block.getFirstLiveIntervalElement();
    if (prev == null) {
      return;
    }

    // The first element is already at the front, so move on to the next one
    LiveIntervalElement elem = prev.getNext();

    while (elem != null) {
      if (elem.getBegin() == null) {
        // remove elem from current position
        prev.setNext(elem.getNext());

        // add it to the begining, se
        block.prependLiveIntervalElement(elem);

        // the next victum is the *new* one after prev
        elem = prev.getNext();
      } else {
        prev = elem;
        elem = elem.getNext();
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
  private static boolean containsUnresolvedElement(BasicBlock block, Register reg) {

    if (DEBUG) {
      System.out.println("containsUnresolvedElement called, block: " + block + " register: " + reg);
      printLiveIntervalList(block);
    }

    for (LiveIntervalElement elem = block.getFirstLiveIntervalElement(); elem != null; elem = elem.getNext()) {
      // if we got an element, down case it to LiveIntervalElement
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
  public static void printLiveIntervalList(BasicBlock block) {
    System.out.println("Live Interval List for " + block);
    for (LiveIntervalElement elem = block.getFirstLiveIntervalElement(); elem != null; elem = elem.getNext()) {
      System.out.println("  " + elem);
    }
  }
}
