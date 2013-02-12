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

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.dfsolver.DF_AbstractCell;
import org.jikesrvm.compilers.opt.dfsolver.DF_Solution;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Perform index propagation (see Fink, Knobe && Sarkar, SAS 2000)
 *
 * <p> This analysis computes for each Array SSA variable A,
 * the set of value numbers V(k) such that location
 * A[k] is "available" at def A, and thus at all uses of A
 *
 * <p> We formulate this as a data flow problem as described in the paper.
 *
 * <p> This class relies on Array SSA form, global value numbering, and
 * the dataflow equation solver framework.
 *
 * <p> TODO: This implementation is not terribly efficient.  Speed it up.
 */
public final class IndexPropagation extends CompilerPhase {

  /**
   * Constructor for this compiler phase
   */
  private static final Constructor<CompilerPhase> constructor =
      getCompilerPhaseConstructor(IndexPropagation.class);

  /**
   * Get a constructor object for this compiler phase
   * @return compiler phase constructor
   */
  @Override
  public Constructor<CompilerPhase> getClassConstructor() {
    return constructor;
  }

  /**
   * @return <code>true</code> iff SSA is constructed on the HIR
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return options.SSA;
  }

  /**
   * Return the name of this compiler phase.
   * @return "Index Propagation"
   */
  @Override
  public String getName() {
    return "Index Propagation";
  }

  /**
   * Print verbose debugging messages?
   */
  private static final boolean DEBUG = false;

  /**
   * Perform the analysis.
   * <p> Pre-condition: The IR is in Array SSA form and global value numbers
   *    have been computed.
   *
   * @param ir the IR to optimize
   */
  @Override
  public void perform(IR ir) {
    if (ir.desiredSSAOptions.getAbort()) return;
    IndexPropagationSystem system = new IndexPropagationSystem(ir);
    if (DEBUG) {
      System.out.print("Solving...");
    }
    system.solve();
    if (DEBUG) {
      System.out.println("done");
    }
    DF_Solution solution = system.getSolution();
    if (DEBUG) {
      System.out.println("Index Propagation Solution: " + solution);
    }
    ir.HIRInfo.indexPropagationSolution = solution;
  }

  /**
   * An ObjectCell is a lattice cell for the index propagation
   * problem, used in redundant load elimination for fields.
   * <p> An ObjectCell represents a set of value numbers,
   * indicating that
   * the elements indexed by these value numbers are available for
   * a certain field type.
   *
   * <p> Note: this implementation does not scale, and is not terribly
   * efficient.
   */
  static final class ObjectCell extends DF_AbstractCell {
    /**
     * a bound on the size of a lattice cell.
     */
    private static final int CAPACITY = 10;
    /**
     * a set of value numbers comparising this lattice cell.
     */
    private int[] numbers = null;
    /**
     * The number of value numbers in this cell.
     */
    private int size = 0;
    /**
     * The heap variable this lattice cell tracks information for.
     */
    private final HeapVariable<?> key;
    /**
     * Does this lattice cell represent TOP?
     */
    private boolean TOP = true;

    /**
     * Create a lattice cell corresponding to a heap variable.
     * @param   key the heap variable associated with this cell.
     */
    ObjectCell(HeapVariable<?> key) {
      this.key = key;
    }

    /**
     * Return the key
     */
    HeapVariable<?> getKey() {
      return key;
    }

    /**
     * Does this cell represent the TOP element in the dataflow lattice?
     * @return true or false.
     */
    boolean isTOP() {
      return TOP;
    }

    /**
     * Does this cell represent the BOTTOM element in the dataflow lattice?
     * @return true or false.
     */
    boolean isBOTTOM() {
      return !TOP && (size == 0);
    }

    /**
     * Mark this cell as representing (or not) the TOP element in the
     * dataflow lattice.
     * @param b should this cell contain TOP?
     */
    void setTOP(boolean b) {
      TOP = b;
      numbers = null;
    }

    /**
     * Set the value of this cell to BOTTOM.
     */
    void setBOTTOM() {
      clear();
    }

    /**
     * Does this cell contain the value number v?
     *
     * @param v value number in question
     * @return true or false
     */
    boolean contains(int v) {

      if (isTOP()) return true;
      if (v == GlobalValueNumberState.UNKNOWN) return false;

      for (int i = 0; i < size; i++) {
        if (numbers[i] == v) {
          return true;
        }
      }
      return false;
    }

    /**
     * Add a value number to this cell.
     *
     * @param v value number
     */
    void add(int v) {
      if (isTOP()) return;

      if ((size < CAPACITY) && !contains(v)) {
        if (size == 0) {
          numbers = new int[CAPACITY];
        }
        numbers[size] = v;
        size++;
      }
    }

    /**
     * Remove a value number from this cell.
     *
     * @param v value number
     */
    void remove(int v) {
      if (isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      int[] old = numbers;
      int[] numbers = new int[CAPACITY];
      int index = 0;
      for (int i = 0; i < size; i++) {
        if (old[i] == v) {
          size--;
        } else {
          numbers[index++] = old[i];
        }
      }
    }

    /**
     * Clear all value numbers from this cell.
     */
    void clear() {
      setTOP(false);
      size = 0;
      numbers = null;
    }

    /**
     * Return a deep copy of the value numbers in this cell.
     * @return a deep copy of the value numbers in this cell, null to
     * represent empty set.
     */
    int[] copyValueNumbers() {
      if (isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      if (size == 0) return null;

      int[] result = new int[size];
      for (int i = 0; i < size; i++) {
        result[i] = numbers[i];
      }
      return result;
    }

    /**
     * Return a string representation of this cell
     * @return a string representation of this cell
     */
    @Override
    public String toString() {
      StringBuilder s = new StringBuilder(key.toString());

      if (isTOP()) return s.append("{TOP}").toString();
      if (isBOTTOM()) return s.append("{BOTTOM}").toString();

      s.append("{");
      for (int i = 0; i < size; i++) {
        s.append(" ").append(numbers[i]);
      }
      s.append("}");
      return s.toString();
    }

    /**
     * Do two sets of value numbers differ?
     * <p> SIDE EFFECT: sorts the sets
     *
     * @param set1 first set to compare
     * @param set2 second set to compare
     * @return true iff the two sets are different
     */
    public static boolean setsDiffer(int[] set1, int[] set2) {
      if ((set1 != null) && (set2 != null)) {
        Arrays.sort(set1);
        Arrays.sort(set2);
        return !Arrays.equals(set1, set2);
      } else {
        return set1 == set2;
      }
    }
  }

  /**
   * An ArrayCell is a lattice cell for the index propagation
   * problem, used in redundant load elimination for one-dimensional arrays.
   * <p> An ArrayCell represents a set of value number pairs,
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
   */
  static final class ArrayCell extends DF_AbstractCell {
    /**
     * a bound on the size of a lattice cell.
     */
    private static final int CAPACITY = 10;
    /**
     * a set of value number pairs comparising this lattice cell.
     */
    private ValueNumberPair[] numbers = null;
    /**
     * The number of value number pairs in this cell.
     */
    private int size = 0;
    /**
     * The heap variable this lattice cell tracks information for.
     */
    private final HeapVariable<?> key;
    /**
     * Does this lattice cell represent TOP?
     */
    private boolean TOP = true;

    /**
     * Create a lattice cell corresponding to a heap variable.
     * @param   key the heap variable associated with this cell.
     */
    ArrayCell(HeapVariable<?> key) {
      this.key = key;
    }

    /**
     * Return the key
     */
    HeapVariable<?> getKey() {
      return key;
    }

    /**
     * Does this cell represent the TOP element in the dataflow lattice?
     * @return true or false.
     */
    boolean isTOP() {
      return TOP;
    }

    /**
     * Does this cell represent the BOTTOM element in the dataflow lattice?
     * @return true or false.
     */
    boolean isBOTTOM() {
      return !TOP && (size == 0);
    }

    /**
     * Mark this cell as representing (or not) the TOP element in the
     * dataflow lattice.
     * @param b should this cell contain TOP?
     */
    void setTOP(boolean b) {
      TOP = b;
      numbers = null;
    }

    /**
     * Set the value of this cell to BOTTOM.
     */
    void setBOTTOM() {
      clear();
    }

    /**
     * Does this cell contain the value number pair v1, v2?
     *
     * @param v1 first value number
     * @param v2 second value number
     * @return true or false
     */
    boolean contains(int v1, int v2) {
      if (isTOP()) return true;
      if (v1 == GlobalValueNumberState.UNKNOWN) return false;
      if (v2 == GlobalValueNumberState.UNKNOWN) return false;
      if (size == 0) return false;

      ValueNumberPair p = new ValueNumberPair(v1, v2);
      for (int i = 0; i < size; i++) {
        if (numbers[i].equals(p)) {
          return true;
        }
      }
      return false;
    }

    /**
     * Add a value number pair to this cell.
     *
     * @param v1 first value number
     * @param v2 second value number
     */
    void add(int v1, int v2) {
      if (isTOP()) return;

      if ((size < CAPACITY) && !contains(v1, v2)) {
        if (size == 0) {
          numbers = new ValueNumberPair[CAPACITY];
        }
        ValueNumberPair p = new ValueNumberPair(v1, v2);
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
    void remove(int v1, int v2) {
      if (isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }
      ValueNumberPair[] old = numbers;
      ValueNumberPair[] numbers = new ValueNumberPair[CAPACITY];
      int index = 0;
      ValueNumberPair p = new ValueNumberPair(v1, v2);
      for (int i = 0; i < size; i++) {
        if (old[i].equals(p)) {
          size--;
        } else {
          numbers[index++] = old[i];
        }
      }
    }

    /**
     * Clear all value numbers from this cell.
     */
    void clear() {
      setTOP(false);
      size = 0;
      numbers = null;
    }

    /**
     * Return a deep copy of the value numbers in this cell
     * @return a deep copy of the value numbers in this cell
     */
    ValueNumberPair[] copyValueNumbers() {
      if (isTOP()) {
        throw new OptimizingCompilerException("Unexpected lattice operation");
      }

      if (size == 0) return null;

      ValueNumberPair[] result = new ValueNumberPair[size];
      for (int i = 0; i < size; i++) {
        result[i] = new ValueNumberPair(numbers[i]);
      }
      return result;
    }

    /**
     * Return a string representation of this cell
     * @return a string representation of this cell
     */
    @Override
    public String toString() {
      StringBuilder s = new StringBuilder(key.toString());

      if (isTOP()) return s.append("{TOP}").toString();
      if (isBOTTOM()) return s.append("{BOTTOM}").toString();

      s.append("{");
      for (int i = 0; i < size; i++) {
        s.append(" ").append(numbers[i]);
      }
      s.append("}");
      return s.toString();
    }

    /**
     * Do two sets of value number pairs differ?
     * <p> SIDE EFFECT: sorts the sets
     *
     * @param set1 first set to compare
     * @param set2 second set to compare
     * @return true iff the two sets are different
     */
    public static boolean setsDiffer(ValueNumberPair[] set1, ValueNumberPair[] set2) {
      if ((set1 != null) && (set2 != null)) {
        Arrays.sort(set1);
        Arrays.sort(set2);
        return !Arrays.equals(set1, set2);
      } else {
        return set1 == set2;
      }
    }
  }
}
