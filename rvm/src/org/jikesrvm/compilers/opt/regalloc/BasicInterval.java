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

/**
 * Implements a basic live interval (no holes), which is a pair
 * <pre>
 *   begin    - the starting point of the interval
 *   end      - the ending point of the interval
 * </pre>
 *
 * <p> Begin and end are numbers given to each instruction by a numbering pass.
 */
class BasicInterval {

  /**
   * DFN of the beginning instruction of this interval
   */
  protected final int begin;
  /**
   * DFN of the last instruction of this interval
   */
  protected int end;

  BasicInterval(int begin, int end) {
    this.begin = begin;
    this.end = end;
  }

  /**
   * @return the DFN signifying the beginning of this basic interval
   */
  final int getBegin() {
    return begin;
  }

  /**
   * @return the DFN signifying the end of this basic interval
   */
  final int getEnd() {
    return end;
  }

  /**
   * Extends a live interval to a new endpoint.
   *
   * @param newEnd the new end point
   */
  final void setEnd(int newEnd) {
    end = newEnd;
  }

  final boolean startsAfter(int dfn) {
    return begin > dfn;
  }

  final boolean startsBefore(int dfn) {
    return begin < dfn;
  }

  final boolean endsBefore(int dfn) {
    return end < dfn;
  }

  final boolean endsAfter(int dfn) {
    return end > dfn;
  }

  final boolean contains(int dfn) {
    return begin <= dfn && end >= dfn;
  }

  final boolean startsBefore(BasicInterval i) {
    return begin < i.begin;
  }

  final boolean endsAfter(BasicInterval i) {
    return end > i.end;
  }

  final boolean sameRange(BasicInterval i) {
    return begin == i.begin && end == i.end;
  }

  final boolean intersects(BasicInterval i) {
    int iBegin = i.getBegin();
    int iEnd = i.getEnd();
    return !(endsBefore(iBegin + 1) || startsAfter(iEnd - 1));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + begin;
    result = prime * result + end;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    BasicInterval other = (BasicInterval) obj;
    return sameRange(other);
  }

  @Override
  public String toString() {
    String s = "[ " + begin + ", " + end + " ] ";
    return s;
  }
}
