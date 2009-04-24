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
package org.jikesrvm.compilers.opt.regalloc;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * This class defines a LiveInterval node created by Live Variable analysis
 * and used in Linear Scan.
 *
 * @see LinearScan
 */
public final class LiveIntervalElement {

  /**
   * register that this live interval is for
   */
  private Register register;

  /**
   * instruction where the live interval begins
   * (null if alive at basic block entry)
   */
  private Instruction begin;

  /**
   * instruction where the live interval ends
   * (null if alive at basic block exit)
   */
  private Instruction end;

  /**
   * The basic block holding this live interval element
   */
  private BasicBlock bb;

  /**
   * LiveIntervalElements are linked in a singly-linked list; this is the
   * next pointer.
   */
  LiveIntervalElement next;

  /**
   * Use this constructor when the live interval spans a basic block
   * boundary.
   *
   * @param reg The Register whose live interval we are representing
   */
  public LiveIntervalElement(Register reg) {
    register = reg;
    begin = null;
    end = null;
  }

  /**
   * Use this constructur when the live interval is within a basic block
   *
   * @param reg   the Register whose live interval we are representing
   * @param begin the definition of the register
   * @param end   the last use of the register
   */
  public LiveIntervalElement(Register reg, Instruction begin, Instruction end) {
    register = reg;
    this.begin = begin;
    this.end = end;
  }

  public String toString() {
    return "Reg: " + register + "\n     Begin: " + begin + "\n     End:   " + end;
  }

  public int hashCode() {
    return register.hashCode();
  }

  /*
   * Getters and setters for instance fields
   */
  public Instruction getBegin() { return begin; }

  public void setBegin(Instruction begin) { this.begin = begin; }

  public Instruction getEnd() { return end; }

  public Register getRegister() { return register; }

  public void setRegister(Register r) { register = r; }

  public LiveIntervalElement getNext() { return next; }

  public void setNext(LiveIntervalElement Next) { next = Next; }

  public BasicBlock getBasicBlock() { return bb; }

  public void setBasicBlock(BasicBlock bb) { this.bb = bb; }
}
