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

import java.util.Comparator;
import java.util.TreeSet;

abstract class IntervalSet extends TreeSet<BasicInterval> {

  /**
   * Create an interval set sorted by increasing start or end number
   *
   * @param c comparator to use for sorting
   */
  IntervalSet(Comparator<BasicInterval> c) {
    super(c);
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (BasicInterval b : this) {
      result.append(b);
      result.append('\n');
    }
    return result.toString();
  }
}
