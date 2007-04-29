/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;

/**
 * Instruction priority representation
 * Used by the scheduler to enumerate over instructions
 *
 * @see OPT_Scheduler
 * @author Igor Pechtchanski
 */
abstract class OPT_Priority
    implements OPT_InstructionEnumeration {

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public abstract void reset ();

  /**
   * Returns true if there are more instructions, false otherwise
   *
   * @return true if there are more instructions, false otherwise
   */
  public abstract boolean hasMoreElements ();

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public final OPT_Instruction nextElement () {
    return  next();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  public abstract OPT_Instruction next ();
}



