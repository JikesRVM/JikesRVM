/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * An OPT_IPObjectCell is a lattice cell for the index propagation 
 * problem, used in redundant load elimination for fields.
 * <p> An OPT_IPObjectCell represents a set of value numbers, 
 * indicating that
 * the elements indexed by these value numbers are available for
 * a certain field type.
 *
 * <p> Note: this implementation does not scale, and is not terribly
 * efficient.
 *
 * @author Stephen Fink
 */
class OPT_IPObjectCell extends OPT_DF_AbstractCell {
  /**
   * a bound on the size of a lattice cell.
   */
  final int CAPACITY = 10; 
  /**
   * a set of value numbers comparising this lattice cell.
   */
  int[] numbers = new int[CAPACITY];
  /**
   * The number of value numbers in this cell.
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
  OPT_IPObjectCell (OPT_HeapVariable key) {
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
   * Does this cell contain the value number v?
   *
   * @param v value number in question
   * @return true or false
   */
  boolean contains (int v) {
    if (isTOP())
      return  true;
    if (v == OPT_GlobalValueNumberState.UNKNOWN)
      return  false;
    for (int i = 0; i < size; i++) {
      if (numbers[i] == v)
        return  true;
    }
    return  false;
  }

  /**
   * Add a value number to this cell.
   *
   * @param v value number
   */
  void add (int v) {
    if (isTOP())
      return;
    if ((size < CAPACITY) && !contains(v)) {
      numbers[size] = v;
      size++;
    }
  }

  /**
   * Remove a value number from this cell.
   *
   * @param v value number
   */
  void remove (int v) {
    if (isTOP()) {
      throw  new OPT_OptimizingCompilerException(
          "Unexpected lattice operation");
    }
    int[] old = numbers;
    int[] numbers = new int[CAPACITY];
    int index = 0;
    for (int i = 0; i < size; i++) {
      if (old[i] == v) {
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
  int[] getValueNumbers () {
    if (isTOP()) {
      throw  new OPT_OptimizingCompilerException(
          "Unexpected lattice operation");
    }
    int[] result = new int[size];
    for (int i = 0; i < size; i++) {
      result[i] = numbers[i];
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
   * Do two sets of value numbers differ? 
   * <p> SIDE EFFECT: sorts the sets
   * 
   * @param set1 first set to compare
   * @param set2 second set to compare
   * @return true iff the two sets are different
   */
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

  /** 
   * Sort an array of value numbers with bubble sort. 
   * Note that these sets
   * will be small  (< CAPACITY), so bubble sort
   * should be ok.
   * @param set the set to sort
   */
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



