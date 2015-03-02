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

/**
 * Implements a set of Basic Intervals, sorted by start number.
 * <p>
 * This version does NOT use container-mapping as a function in the comparator.
 */
class IncreasingStartIntervalSet extends IntervalSet {
  /** Support for Set serialization */
  static final long serialVersionUID = -7086728932911844728L;

  /**
   * Imposes an ascending ordering based on the start points of basic intervals.
   * <p>
   * Note that this ordering is inconsistent with equals for objects of type
   * {@link MappedBasicInterval}. It would consider two MappedBasicIntervals
   * with different containers the same if begin and end would match.
   */
  private static class StartComparator implements Comparator<BasicInterval> {
    @Override
    public int compare(BasicInterval b1, BasicInterval b2) {
      int result = b1.getBegin() - b2.getBegin();
      if (result == 0) {
        result = b1.getEnd() - b2.getEnd();
      }
      return result;
    }
  }

  static final IncreasingStartIntervalSet.StartComparator c = new StartComparator();

  IncreasingStartIntervalSet() {
    super(c /*,true*/);
  }
}
