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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * This class holds information on scratch register usage, needed to
 * adjust GC Maps.
 */
public final class ScratchMap {

  private static final boolean DEBUG = false;

  /**
   * For each register, the set of intervals describing the register.
   */
  private final HashMap<Register, ArrayList<Interval>> map = new HashMap<Register, ArrayList<Interval>>();

  /**
   * For each register, a pending (incomplete) interval under
   * construction.
   */
  private final HashMap<Register, Interval> pending = new HashMap<Register, Interval>();

  /**
   * For each GC Point s, a set of symbolic registers that are cached in
   * dirty scratch registers before s.
   */
  private final HashMap<Instruction, HashSet<Register>> dirtyMap =
      new HashMap<Instruction, HashSet<Register>>();

  /**
   * Begin a new interval of scratch-ness for a symbolic register.
   *
   * @param r the symbolic register being moved into scratch
   * @param scratch the physical register being used as a scratch
   * @param begin the instruction before which the physical register is
   * vacated.
   */
  void beginSymbolicInterval(Register r, Register scratch, Instruction begin) {
    if (DEBUG) {
      System.out.println("beginSymbolicInterval " + r + " " + scratch + " " + begin.scratch);
    }

    SymbolicInterval i = new SymbolicInterval(r, scratch);
    i.begin = begin;
    ArrayList<Interval> v = findOrCreateIntervalSet(r);
    v.add(i);
    pending.put(r, i);
  }

  /**
   * End an interval of scratch-ness for a symbolic register.
   *
   * @param r the symbolic register being moved into scratch
   * @param end the instruction before which the scratch interval ends
   */
  public void endSymbolicInterval(Register r, Instruction end) {
    if (DEBUG) {
      System.out.println("endSymbolicInterval " + r + " " + end.scratch);
    }

    SymbolicInterval i = (SymbolicInterval) pending.get(r);
    i.end = end;
    pending.remove(i);
  }

  /**
   * Begin a new interval of scratch-ness for a physical register.
   *
   * @param r the physical register being used as a scratch
   * @param begin the instruction before which the physical register is
   * vacated.
   */
  void beginScratchInterval(Register r, Instruction begin) {
    if (DEBUG) {
      System.out.println("beginScratchInterval " + r + " " + begin.scratch);
    }
    PhysicalInterval p = new PhysicalInterval(r);
    p.begin = begin;
    ArrayList<Interval> v = findOrCreateIntervalSet(r);
    v.add(p);
    pending.put(r, p);
  }

  /**
   * End an interval of scratch-ness for a physical register.
   *
   * @param r the physical register being used as a scratch
   * @param end the instruction before which the physical register is
   * vacated.
   */
  public void endScratchInterval(Register r, Instruction end) {
    if (DEBUG) {
      System.out.println("endScratchInterval " + r + " " + end.scratch);
    }
    PhysicalInterval p = (PhysicalInterval) pending.get(r);
    p.end = end;
    pending.remove(r);
  }

  /**
   * Find or create the set of intervals corresponding to a register r.
   */
  private ArrayList<Interval> findOrCreateIntervalSet(Register r) {
    ArrayList<Interval> v = map.get(r);
    if (v == null) {
      v = new ArrayList<Interval>();
      map.put(r, v);
    }
    return v;
  }

  /**
   * If a physical register is being used as a scratch register at
   * instruction n, return true; else, return false;
   */
  boolean isScratch(Register r, int n) {
    ArrayList<Interval> v = map.get(r);
    if (v == null) return false;
    for (final Interval interval : v) {
      if (interval.contains(n)) return true;
    }
    return false;
  }

  /**
   * If a symbolic register resides in a scratch register at an
   * instruction numbered n, then return the scratch register. Else,
   * return null.
   */
  Register getScratch(Register r, int n) {
    ArrayList<Interval> v = map.get(r);
    if (v == null) return null;
    for (Interval i : v) {
      if (i.contains(n)) return i.scratch;
    }
    return null;
  }

  /**
   * Is this map empty?
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Note that at GC point s, the real value of register symb is cached in
   * a dirty scratch register.
   */
  public void markDirty(Instruction s, Register symb) {
    HashSet<Register> set = dirtyMap.get(s);
    if (set == null) {
      set = new HashSet<Register>(3);
      dirtyMap.put(s, set);
    }
    set.add(symb);
  }

  /**
   * At GC point s, is the value of register r cached in a dirty scratch
   * register?
   */
  public boolean isDirty(Instruction s, Register r) {
    HashSet<Register> set = dirtyMap.get(s);
    if (set == null) {
      return false;
    } else {
      return set.contains(r);
    }
  }

  /**
   * Return a String representation.
   */
  public String toString() {
    String result = "";
    for (ArrayList<Interval> v : map.values()) {
      for (Interval i : v) {
        result += i + "\n";
      }
    }
    return result;
  }

  /**
   * Super class of physical and symbolic intervals
   */
  private abstract static class Interval {
    /**
     * The instruction before which the scratch range begins.
     */
    Instruction begin;
    /**
     * The instruction before which the scratch range ends.
     */
    Instruction end;
    /**
     * The physical scratch register or register evicted.
     */
    final Register scratch;

    /**
     * Initialize scratch register
     */
    Interval(Register scratch) {
      this.scratch = scratch;
    }

    /**
     * Does this interval contain the instruction numbered n?
     */
    final boolean contains(int n) {
      return (begin.scratch <= n && end.scratch > n);
    }
  }

  /**
   * An object that represents an interval where a symbolic register
   * resides in a scratch register.
   * Note that this interval must not span a basic block.
   */
  static final class SymbolicInterval extends Interval {
    /**
     * The symbolic register
     */
    final Register symbolic;

    SymbolicInterval(Register symbolic, Register scratch) {
      super(scratch);
      this.symbolic = symbolic;
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * Instruction identifies an instruction.
     */
    public String toString() {
      return "SI: " + symbolic + " " + scratch + " [" + begin.scratch + "," + end.scratch + "]";
    }
  }

  /**
   * An object that represents an interval where a physical register's
   * contents are evicted so that the physical register can be used as a
   * scratch.  Note that this interval must not span a basic block.
   */
  static final class PhysicalInterval extends Interval {
    PhysicalInterval(Register scratch) {
      super(scratch);
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * Instruction identifies an instruction.
     */
    public String toString() {
      return "PI: " + scratch + " [" + begin.scratch + "," + end.scratch + "]";
    }
  }
}
