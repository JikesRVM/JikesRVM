/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_ValueNumberPairSetCell is a lattice cell that
 * represents a set of value number pairs.
 *
 * TODO: use this implementation for index propagation as well as
 *        dead store elimination.
 *
 * @author Stephen Fink
 *
 * Note: this implementation does not scale, and is not terribly
 * efficient.
 */
class OPT_ValueNumberPairSetCell extends OPT_DF_AbstractCell {
  int CAPACITY = -1;            // bound on size of a lattice cell
  // -1 indicates infinite capacity
  final static int INITIAL_CAPACITY = 10;
  OPT_ValueNumberPair[] numbers;
  int size = 0;
  Object key;
  boolean universal = false;    // does this represent the universal set?

  /**
   * constructor for cell with infinite capacity
   */
  OPT_ValueNumberPairSetCell (Object key) {
    this.key = key;
    numbers = new OPT_ValueNumberPair[INITIAL_CAPACITY];
  }

  public Object getKey () {
    return  key;
  }

  // currently not used, so protect with private
  private void setUniversal () {
    universal = true;
  }

  // Does this cell contain value number pair v1, v2?
  boolean contains (OPT_ValueNumberPair v) {
    return  contains(v.v1, v.v2);
  }

  boolean contains (int v1, int v2) {
    if (universal)
      return  true;
    if (v1 == OPT_GlobalValueNumberState.UNKNOWN)
      return  false;
    if (v2 == OPT_GlobalValueNumberState.UNKNOWN)
      return  false;
    OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
    for (int i = 0; i < size; i++) {
      if (numbers[i].equals(p))
        return  true;
    }
    return  false;
  }

  // Add value number pair <v1,v2> to this cell.
  void add (OPT_ValueNumberPair v) {
    add(v.v1, v.v2);
  }

  void add (int v1, int v2) {
    if (universal)
      return;
    if (contains(v1, v2))
      return;
    if (CAPACITY == -1) {
      // infinite capacity
      if (size == numbers.length) {
        // grow to increase capacity
        OPT_ValueNumberPair[] newNumbers = 
            new OPT_ValueNumberPair[2*numbers.length];
        for (int i = 0; i < numbers.length; i++) {
          newNumbers[i] = numbers[i];
        }
        numbers = newNumbers;
      }
      numbers[size] = new OPT_ValueNumberPair(v1, v2);
      size++;
    } 
    else {
      // fixed capacity
      if (size < CAPACITY) {
        OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
        numbers[size] = p;
        size++;
      }
    }
  }

  // Remove value number pair <v1, v2> from this cell.
  void remove (OPT_ValueNumberPair v) {
    remove(v.v1, v.v2);
  }

  void remove (int v1, int v2) {
    if (universal)
      throw  new OPT_OptimizingCompilerException(
          "remove() not implemented for universal set");
    OPT_ValueNumberPair[] old = numbers;
    numbers = new OPT_ValueNumberPair[numbers.length];
    int index = 0;
    OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
    int newSize = size;
    for (int i = 0; i < size; i++) {
      if (old[i].equals(p)) {
        newSize--;
      } 
      else {
        numbers[index++] = old[i];
      }
    }
    size = newSize;
  }

  // Clear all value numbers from this cell
  void clear () {
    size = 0;
    universal = false;
  }

  // Return (a copy) the value numbers in this cell
  OPT_ValueNumberPair[] getValueNumbers () {
    if (universal)
      throw  new OPT_OptimizingCompilerException(
          "getValueNumbers() not implemented for universal set");
    OPT_ValueNumberPair[] result = new OPT_ValueNumberPair[size];
    for (int i = 0; i < size; i++) {
      result[i] = new OPT_ValueNumberPair(numbers[i]);
    }
    return  result;
  }

  public String toString () {
    if (universal)
      return  ("UNIVERSAL");
    StringBuffer s = new StringBuffer(key.toString());
    s.append("(SIZE " + size + ")");
    for (int i = 0; i < size; i++) {
      s.append(" ").append(numbers[i]);
    }
    return  s.toString();
  }

  // do two sets differ? SIDE EFFECT: sorts the sets
  // TODO: factor this functionality out somewhere else
  public static boolean setsDiffer (OPT_ValueNumberPair[] set1, 
        OPT_ValueNumberPair[] set2) {
    if (set1.length != set2.length)
      return  true;
    sort(set1);
    sort(set2);
    for (int i = 0; i < set1.length; i++) {
      if (!set1[i].equals(set2[i]))
        return  true;
    }
    return  false;
  }

  // sort a set with bubble sort. Note that these sets
  // should be small, so maybe bubble sort
  // is ok.
  // TODO: factor this functionality out somewhere else
  public static void sort (OPT_ValueNumberPair[] set) {
    for (int i = set.length - 1; i >= 0; i--) {
      for (int j = 0; j < i; j++) {
        if (set[j].greaterThan(set[j + 1])) {
          OPT_ValueNumberPair temp = set[j + 1];
          set[j + 1] = set[j];
          set[j] = temp;
        }
      }
    }
  }
}



