/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * An OPT_IPArrayCell is a lattice cell for the index propagation 
 * problem, used in redundant load elimination for one-dimensional arrays.
 * <p> An OPT_IPArrayCell represents a set of value number pairs, 
 * indicating that
 * the elements indexed by these value numbers are available for
 * a certain array type.
 *
 * <p> For example, suppose p is an int[], with value number v(p).
 * Then the value number pair <p,5> for heap variable I[ means
 * that p[5] is available.
 *
 * <p> Note: this implementation does not scale, and is not terribly
 * efficient.
 *
 * @author Stephen Fink
 */
class OPT_IPArrayCell extends OPT_DF_AbstractCell {
  /**
   * a bound on the size of a lattice cell.
   */
  final int CAPACITY = 10;      
  /**
   * a set of value number pairs comparising this lattice cell.
   */
  OPT_ValueNumberPair[] numbers = new OPT_ValueNumberPair[CAPACITY];
  /**
   * The number of value number pairs in this cell.
   */
  int size = 0;
  /**
   * The heap variable this lattice cell tracks information for.
   */
  OPT_HeapVariable key;
  /**
   * Does this lattice cell represent TOP?
   */
  boolean TOP = true;

  /**
   * Create a latticle cell corresponding to a heap variable.
   * @param   key the heap variable associated with this cell.
   */
  OPT_IPArrayCell (OPT_HeapVariable key) {
    this.key = key;
  }

  /**
   * Does this cell represent the TOP element in the dataflow lattice?
   * @return true or false.
   */
  boolean isTOP () {
    return  TOP;
  }

  /**
   * Mark this cell as representing (or not) the TOP element in the 
   * dataflow lattice.
   * @param b should this cell contain TOP?
   */
  void setTOP (boolean b) {
    TOP = b;
  }

  /**
   * Set the value of this cell to BOTTOM.
   */
  void setBOTTOM () {
    clear();
  }

  /**
   * Does this cell contain the value number pair v1, v2?
   *
   * @param v1 first value number
   * @param v2 second value number
   * @return true or false
   */
  boolean contains (int v1, int v2) {
    if (isTOP())
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

  /**
   * Add a value number pair to this cell.
   *
   * @param v1 first value number
   * @param v2 second value number
   */
  void add (int v1, int v2) {
    if (isTOP())
      return;
    if ((size < CAPACITY) && !contains(v1, v2)) {
      OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
      numbers[size] = p;
      size++;
    }
  }

  /**
   * Remove a value number pair from this cell.
   *
   * @param v1 first value number
   * @param v2 second value number
   */
  void remove (int v1, int v2) {
    if (isTOP()) {
      throw  new OPT_OptimizingCompilerException( 
                                         "Unexpected lattice operation");
    }
    OPT_ValueNumberPair[] old = numbers;
    OPT_ValueNumberPair[] numbers = new OPT_ValueNumberPair[CAPACITY];
    int index = 0;
    OPT_ValueNumberPair p = new OPT_ValueNumberPair(v1, v2);
    for (int i = 0; i < size; i++) {
      if (old[i].equals(p)) {
        size--;
      } 
      else {
        numbers[index++] = old[i];
      }
    }
  }

  /**
   * Clear all value numbers from this cell.
   */
  void clear () {
    setTOP(false);
    size = 0;
  }

  /**
   * Return a deep copy of the value numbers in this cell
   * @return a deep copy of the value numbers in this cell 
   */
  OPT_ValueNumberPair[] getValueNumbers () {
    if (isTOP()) {
      throw  new OPT_OptimizingCompilerException(
          "Unexpected lattice operation");
    }
    OPT_ValueNumberPair[] result = new OPT_ValueNumberPair[size];
    for (int i = 0; i < size; i++) {
      result[i] = new OPT_ValueNumberPair(numbers[i]);
    }
    return  result;
  }

  /**
   * Return a string representation of this cell 
   * @return a string representation of this cell 
   */
  public String toString () {
    StringBuffer s = new StringBuffer(key.toString());
    if (isTOP())
      return  s.append(" TOP").toString();
    for (int i = 0; i < size; i++) {
      s.append(" ").append(numbers[i]);
    }
    return  s.toString();
  }

  /**
   * Do two sets of value number pairs differ? 
   * <p> SIDE EFFECT: sorts the sets
   * 
   * @param set1 first set to compare
   * @param set2 second set to compare
   * @return true iff the two sets are different
   */
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

  /**
   * Sort an array of value number pairs with bubble sort. 
   * Note that these sets
   * will be small  (< CAPACITY), so bubble sort
   * should be ok.
   * @param set the set to sort
   */
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



