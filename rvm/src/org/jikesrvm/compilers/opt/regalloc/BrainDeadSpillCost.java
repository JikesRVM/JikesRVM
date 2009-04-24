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

import org.jikesrvm.compilers.opt.ir.IR;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register.
 *
 * This implementation returns a cost of zero for all registers.
 */
class BrainDeadSpillCost extends SpillCostEstimator {

  BrainDeadSpillCost(IR ir) {
    calculate(ir);
  }

  /**
   * Calculate the estimated cost for each register.
   * This brain-dead version does nothing.
   */
  void calculate(IR ir) {
  }
}
