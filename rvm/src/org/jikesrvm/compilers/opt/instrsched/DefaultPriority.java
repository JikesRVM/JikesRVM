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
package org.jikesrvm.compilers.opt.instrsched;

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;

/**
 * Default (IR-order) instruction list
 * Used by the scheduler to enumerate over instructions
 *
 * @see Priority
 * @see Scheduler
 */
class DefaultPriority extends Priority {
  // Underlying enumeration.
  private final BasicBlock bb;
  private Instruction i;
  private InstructionEnumeration instr;

  /**
   * Creates new priority object for a given basic block
   *
   * @param bb basic block
   */
  public DefaultPriority(BasicBlock bb) {
    this.bb = bb;
  }

  @Override
  public final void reset() {
    i = bb.firstInstruction();
    instr = bb.forwardRealInstrEnumerator();
  }

  @Override
  public final boolean hasMoreElements() {
    return i != null || instr.hasMoreElements();
  }

  @Override
  public final Instruction next() {
    if (i != null) {
      Instruction r = i;
      i = null;
      return r;
    }
    return instr.next();
  }
}
