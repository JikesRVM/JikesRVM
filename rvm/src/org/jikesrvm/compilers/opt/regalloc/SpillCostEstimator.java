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

import java.util.HashMap;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * An object that returns an estimate of the relative cost of spilling a
 * symbolic register.
 */
abstract class SpillCostEstimator {

  private final HashMap<Register, Double> map = new HashMap<Register, Double>();

  /**
   * Returns a number that represents an estimate of the relative cost of
   * spilling register {@code r}.
   *
   * @param r the register to check
   * @return a cost estimate for spilling; may be zero
   */
  double getCost(Register r) {
    Double d = map.get(r);
    if (d == null) {
      return 0;
    } else {
      return d;
    }
  }

  /**
   * Calculates the estimated cost for each register.
   *
   * @param ir the IR object
   */
  abstract void calculate(IR ir);

  /**
   * Updates the cost for a particular register.
   *
   * @param r register whose cost is to be updated
   * @param delta change in cost for the register
   */
  protected void update(Register r, double delta) {
    double c = getCost(r);
    c += delta;
    map.put(r, c);
  }
}
