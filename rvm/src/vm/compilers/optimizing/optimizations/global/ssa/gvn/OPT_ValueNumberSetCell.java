/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * OPT_ValueNumberSetCell.java
 *
 * OPT_ValueNumberSetCell is a lattice cell that represents a
 * set of value numbers, 
 *
 * TODO: use this implementaion for index propagation as well as
 *        dead store elimination
 *
 * @author Stpehen Fink
 *
 * Note: this implementation does not scale, and is not terribly
 * efficient.
 */
class OPT_ValueNumberSetCell extends OPT_DF_AbstractCell {
  final int CAPACITY;           // bound on size of a lattice cell
  // -1 means infinite
  final static int INITIAL_CAPACITY = 10;
  int[] numbers;
  int size = 0;
  Object key;
  boolean universal = false;    // represents universal set of value numbers?

  /**
   * constructor for a cell with infinite capacity
   */
  OPT_ValueNumberSetCell (Object key) {
    this.key = key;
    CAPACITY = -1;
    numbers = new int[INITIAL_CAPACITY];
  }

  public Object getKey () {
    return  key;
  }

  // Does this cell contain value number v?
  boolean contains (int v) {
    if (universal)
      return  true;
    if (v == OPT_GlobalValueNumberState.UNKNOWN)
      return  false;
    for (int i = 0; i < size; i++) {
      if (numbers[i] == v)
        return  true;
    }
    return  false;
  }

  // set this cell to represent the universal set
  // currently not used, so made private for protection
  private void setUniversal () {
    universal = true;
  }

  // Add value number v to this cell.
  void add (int v) {
    if (universal)
      return;
    if (CAPACITY == -1) {
      // case for infinite capacity
      if (!contains(v)) {
        if (size == numbers.length) {
          // grow
          int[] newNumbers = new int[2*numbers.length];
          for (int i = 0; i < numbers.length; i++) {
            newNumbers[i] = numbers[i];
          }
          numbers = newNumbers;
        }
        numbers[size] = v;
        size++;
      }
    } 
    else {
      // fixed capacity
      if ((size < CAPACITY) && !contains(v)) {
        numbers[size] = v;
        size++;
      }
    }
  }

  // Remove value number v from this cell.
  void remove (int v) {
    if (universal)
      throw  new OPT_OptimizingCompilerException(
          "remove from UNIVERSAL set not implemented");
    int[] old = numbers;
    numbers = new int[numbers.length];
    int index = 0;
    int newSize = size;
    for (int i = 0; i < size; i++) {
      if (old[i] == v) {
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
    if (universal)
      universal = false;
  }

  // Return (a copy) the value numbers in this cell
  int[] getValueNumbers () {
    if (universal)
      throw  new OPT_OptimizingCompilerException(
          "getValueNumbers(): not implemented for universal sets");
    int[] result = new int[size];
    for (int i = 0; i < size; i++) {
      result[i] = numbers[i];
    }
    return  result;
  }

  public String toString () {
    if (universal)
      return  ("UNIVERSAL");
    StringBuffer s = new StringBuffer(key.toString());
    for (int i = 0; i < size; i++) {
      s.append(" ").append(numbers[i]);
    }
    return  s.toString();
  }

  // do two sets differ? SIDE EFFECT: sorts the sets
  // TODO: factor this functionality out somewhere else
  public static boolean setsDiffer (int[] set1, int[] set2) {
    if (set1.length != set2.length)
      return  true;
    sort(set1);
    sort(set2);
    for (int i = 0; i < set1.length; i++) {
      if (set1[i] != set2[i])
        return  true;
    }
    return  false;
  }

  // sort a set with bubble sort. Note that these sets
  // should be small  ( < CAPACITY), so maybe bubble sort
  // is ok.
  // TODO: factor this functionality out somewhere else
  public static void sort (int[] set) {
    for (int i = set.length - 1; i >= 0; i--) {
      for (int j = 0; j < i; j++) {
        if (set[j] > set[j + 1]) {
          int temp = set[j + 1];
          set[j + 1] = set[j];
          set[j] = temp;
        }
      }
    }
  }
}



