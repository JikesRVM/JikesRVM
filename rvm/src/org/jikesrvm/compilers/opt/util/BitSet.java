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
package org.jikesrvm.compilers.opt.util;

import org.jikesrvm.VM;
import org.jikesrvm.util.BitVector;

/**
 * A bit set is a set of elements, each of which corresponds to a unique
 * integer from [0,MAX].
 */
public final class BitSet {

  /**
   * The backing bit vector that determines set membership.
   */
  private final BitVector vector;

  /**
   * The bijection between integer to object.
   */
  private final BitSetMapping map;

  /**
   * Creates an empty set corresponding to a given mapping.
   *
   * @param map a mapping
   */
  public BitSet(BitSetMapping map) {
    int length = map.getMappingSize();
    vector = new BitVector(length);
    this.map = map;
  }

  /**
   * Add all elements in bitset B to this bit set
   * @param B the elements to add to this set
   */
  public void addAll(BitSet B) {
    if (VM.VerifyAssertions) {
      VM._assert(map == B.map);
    }
    vector.or(B.vector);
  }

  /**
   * Add an object to this bit set.
   * @param o an object that's present in the mapping
   */
  public void add(Object o) {
    int n = map.getMappedIndex(o);
    vector.set(n);
  }

  /**
   * @param o an object that's present in the mapping
   * @return whether this set contains the object
   */
  public boolean contains(Object o) {
    int n = map.getMappedIndex(o);
    return vector.get(n);
  }

  /**
   * @return a String representation
   */
  @Override
  public String toString() {
    return vector.toString();
  }
}
