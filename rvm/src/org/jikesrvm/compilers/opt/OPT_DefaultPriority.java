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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;

/**
 * Default (IR-order) instruction list
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Priority
 * @see OPT_Scheduler
 */
class OPT_DefaultPriority extends OPT_Priority {
  // Underlying enumeration.
  private OPT_BasicBlock bb;
  private OPT_Instruction i;
  private OPT_InstructionEnumeration instr;

  /**
   * Creates new priority object for a given basic block
   *
   * @param bb basic block
   */
  public OPT_DefaultPriority(OPT_BasicBlock bb) {
    this.bb = bb;
  }

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public final void reset() {
    i = bb.firstInstruction();
    instr = bb.forwardRealInstrEnumerator();
  }

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public final boolean hasMoreElements() {
    return i != null || instr.hasMoreElements();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final OPT_Instruction next() {
    if (i != null) {
      OPT_Instruction r = i;
      i = null;
      return r;
    }
    return instr.next();
  }
}
