/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

/**
 * utility class: represents a pair of value numbers.
 */
class OPT_ValueNumberPair implements Comparable<OPT_ValueNumberPair> {
  /** the value number of an array pointer */
  final int v1;
  /** the value number of an array index */
  final int v2;

  /** Construct a pair from the given arguments */
  OPT_ValueNumberPair(int v1, int v2) {
    this.v1 = v1;
    this.v2 = v2;
  }

  /** Copy a pair */
  OPT_ValueNumberPair(OPT_ValueNumberPair p) {
    this.v1 = p.v1;
    this.v2 = p.v2;
  }

  public boolean equals(Object o) {
    if (!(o instanceof OPT_ValueNumberPair)) {
      return false;
    }
    OPT_ValueNumberPair p = (OPT_ValueNumberPair) o;
    return (v1 == p.v1) && (v2 == p.v2);
  }

  public int hashCode() {
    return v1 << 16 | v2;
  }

  public String toString() {
    return "<" + v1 + "," + v2 + ">";
  }

  // total order over OPT_ValueNumberPairs
  public int compareTo(OPT_ValueNumberPair p) {
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



