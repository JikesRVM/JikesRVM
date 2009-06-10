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
package org.jikesrvm.compilers.opt.regalloc.ppc;

import org.jikesrvm.compilers.opt.CompilerPhase;
import org.jikesrvm.compilers.opt.Options;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * This class splits live ranges for certain special cases before register
 * allocation.
 *
 * On PPC, this phase is currently a No-op.
 */
class MIRSplitRanges extends CompilerPhase {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public final boolean shouldPerform(Options options) {
    return false;
  }

  /**
   * Return the name of this phase
   * @return "Live Range Splitting"
   */
  public final String getName() {
    return "MIR Range Splitting";
  }

  /**
   * The main method.
   *
   * @param ir the governing IR
   */
  public final void perform(IR ir) {
  }
}

