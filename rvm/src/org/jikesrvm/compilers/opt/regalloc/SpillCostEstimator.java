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
   * Return a number that represents an estimate of the relative cost of
   * spilling register r.
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
   * Calculate the estimated cost for each register.
   */
  abstract void calculate(IR ir);

  /**
   * Update the cost for a particular register.
   */
  protected void update(Register r, double delta) {
    double c = getCost(r);
    c += delta;
    map.put(r, c);
  }
}
