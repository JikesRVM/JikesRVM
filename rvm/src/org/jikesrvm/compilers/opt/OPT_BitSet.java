/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;

/**
 * OPT_BitSet.java
 *
 * A bit set is a set of elements, each of which corresponds to a unique
 * integer from [0,MAX].
 */
public final class OPT_BitSet {

  /**
   * The backing bit vector that determines set membership.
   */
  private final OPT_BitVector vector;

  /**
   * The bijection between integer to object.
   */
  private final OPT_BitSetMapping map;

  /**
   * Constructor: create an empty set corresponding to a given mapping
   */
  public OPT_BitSet(OPT_BitSetMapping map) {
    int length = map.getMappingSize();
    vector = new OPT_BitVector(length);
    this.map = map;
  }

  /**
   * Add all elements in bitset B to this bit set
   */
  public void addAll(OPT_BitSet B) {
    if (VM.VerifyAssertions) {
      VM._assert(map == B.map);
    }
    vector.or(B.vector);
  }

  /**
   * Add an object to this bit set.
   */
  public void add(Object o) {
    int n = map.getMappedIndex(o);
    vector.set(n);
  }

  /**
   * Does this set contain a certain object?
   */
  public boolean contains(Object o) {
    int n = map.getMappedIndex(o);
    return vector.get(n);
  }

  /**
   * @return a String representation
   */
  public String toString() {
    return vector.toString();
  }
}
