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

import org.jikesrvm.compilers.opt.regalloc.LinearScan.BasicInterval;
import org.jikesrvm.compilers.opt.regalloc.LinearScan.MappedBasicInterval;

/**
 * Implements a set of Basic Intervals, sorted by start number.
 * This version uses container-mapping as a function in the comparator.
 */
class IncreasingStartMappedIntervalSet extends IntervalSet {
  /** Support for Set serialization */
  static final long serialVersionUID = -975667667343524421L;

  private static class StartComparator implements Comparator<BasicInterval> {
    @Override
    public int compare(BasicInterval b1, BasicInterval b2) {
      int result = b1.getBegin() - b2.getBegin();
      if (result == 0) {
        result = b1.getEnd() - b2.getEnd();
      }
      if (result == 0) {
        if (b1 instanceof MappedBasicInterval) {
          if (b2 instanceof MappedBasicInterval) {
            MappedBasicInterval mb1 = (MappedBasicInterval) b1;
            MappedBasicInterval mb2 = (MappedBasicInterval) b2;
            return mb1.container.getRegister().number - mb2.container.getRegister().number;
          }
        }
      }
      return result;
    }
  }

  static final IncreasingStartMappedIntervalSet.StartComparator c = new StartComparator();

  IncreasingStartMappedIntervalSet() {
    super(c);
  }
}
