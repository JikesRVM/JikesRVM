/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/** 
 * OPT_BitSet.java
 *
 * A bit set is a set of elements, each of which corresponds to a unique
 * integer from [0,MAX].  
 *
 * @author by Stephen Fink
 */
public final class OPT_BitSet {

  /**
   * The backing bit vector that determines set membership.
   */
  private OPT_BitVector vector;

  /**
   * The bijection between integer to object. 
   */
  private OPT_BitSetMapping map;

  /**
   * Constructor: create an empty set corresponding to a given mapping
   */
  OPT_BitSet(OPT_BitSetMapping map) {
    int length = map.getMappingSize();
    vector = new OPT_BitVector(length);
    this.map = map;
  }

  /**
   * Add all elements in bitset B to this bit set
   */
  public void addAll(OPT_BitSet B) {
    if (VM.VerifyAssertions) {
      VM.assert(map == B.map);
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
