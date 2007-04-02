/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.*;
import java.util.HashMap;

/**
 * An object that returns an estimate of the relative cost of spilling a 
 * symbolic register.
 *
 * @author Stephen Fink
 */
abstract class OPT_SpillCostEstimator {

  private final HashMap<OPT_Register,Double> map =
	 new HashMap<OPT_Register,Double>(); 

  /**
   * Return a number that represents an estimate of the relative cost of
   * spilling register r.
   */
  double getCost(OPT_Register r) {
    Double d = map.get(r);
    if (d == null) return 0;
    else return d;
  }

  /**
   * Calculate the estimated cost for each register.
   */
  abstract void calculate(OPT_IR ir);

  /**
   * Update the cost for a particular register.
   */
  protected void update(OPT_Register r, double delta) {
    double c = getCost(r);
    c += delta;
    map.put(r, c);
  }
}
