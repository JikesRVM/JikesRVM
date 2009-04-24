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
package org.jikesrvm.compilers.opt.ssa;

/**
 * utility class: represents a pair of value numbers.
 */
class ValueNumberPair implements Comparable<ValueNumberPair> {
  /** the value number of an array pointer */
  final int v1;
  /** the value number of an array index */
  final int v2;

  /** Construct a pair from the given arguments */
  ValueNumberPair(int v1, int v2) {
    this.v1 = v1;
    this.v2 = v2;
  }

  /** Copy a pair */
  ValueNumberPair(ValueNumberPair p) {
    this.v1 = p.v1;
    this.v2 = p.v2;
  }

  public boolean equals(Object o) {
    if (!(o instanceof ValueNumberPair)) {
      return false;
    }
    ValueNumberPair p = (ValueNumberPair) o;
    return (v1 == p.v1) && (v2 == p.v2);
  }

  public int hashCode() {
    return v1 << 16 | v2;
  }

  public String toString() {
    return "<" + v1 + "," + v2 + ">";
  }

  // total order over ValueNumberPairs
  public int compareTo(ValueNumberPair p) {
    if (v1 > p.v1) {
      return 1;
    } else if (v1 < p.v1) {
      return -1;
    } else if (v2 > p.v2) {
      // v1 == p.v1
      return 1;
    } else if (v2 < p.v2) {
      return -1;
    } else {
      // v2 == p.v2
      return 0;
    }
  }
}
