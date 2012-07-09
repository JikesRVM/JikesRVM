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

import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;

/**
 * Represents instruction priority. Used by the scheduler to enumerate over
 * instructions.
 *
 * @see Scheduler
 */
abstract class Priority implements InstructionEnumeration {

  /**
   * Resets the enumeration to the first instruction in sequence
   */
  public abstract void reset();

  /**
   * Returns {@code true} if there are more instructions, false otherwise
   *
   * @return {@code true} if there are more instructions, false otherwise
   */
  @Override
  public abstract boolean hasMoreElements();

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  @Override
  public final Instruction nextElement() {
    return next();
  }

  /**
   * Returns the next instruction in sequence
   *
   * @return the next instruction in sequence
   */
  @Override
  public abstract Instruction next();
}



