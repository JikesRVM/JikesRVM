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

  private final RegisterAllocatorState regAllocState;

  public ScratchMap(RegisterAllocatorState regAllocState) {
    this.regAllocState = regAllocState;
  }

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
      System.out.println("beginSymbolicInterval " + r + " " + scratch + " " +
          regAllocState.getDFN(begin));
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
      System.out.println("endSymbolicInterval " + r + " " +
          regAllocState.getDFN(end));
    }

    SymbolicInterval i = (SymbolicInterval) pending.get(r);
    i.end = end;
    pending.remove(r);
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
      System.out.println("beginScratchInterval " + r + " " +
          regAllocState.getDFN(begin));
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
      System.out.println("endScratchInterval " + r + " " +
          regAllocState.getDFN(end));
    }
    PhysicalInterval p = (PhysicalInterval) pending.get(r);
    p.end = end;
    pending.remove(r);
  }

  /**
   * Find or create the set of intervals corresponding to a register r.
   *
   * @param r the register to check
   * @return a possibly empty list of intervals
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
   * Is the given physical register being used as a scratch register
   * in the given instruction?
   * @param r a physical register
   * @param n the instruction's number
   * @return {@code true} if the register is used as a scratch register
   *  in the instruction, {@code false} otherwise
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
   * Gets the scratch register if a matching one exists.
   *
   * @param r a symbolic register
   * @param n the instruction number
   * @return if a symbolic register resides in a scratch register at an
   * instruction with the given number, then return the scratch register. Else,
   * return {@code null}.
   */
  Register getScratch(Register r, int n) {
    ArrayList<Interval> v = map.get(r);
    if (v == null) return null;
    for (Interval i : v) {
      if (i.contains(n)) return i.scratch;
    }
    return null;
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  /**
   * Records that the real value of a symbolic register is cached in
   * a dirty scratch register at a given instruction that is a GC point.
   *
   * @param s an instruction that is a GC point. Note: it is the caller's
   *    responsibility to check this
   * @param symb the symbolic register
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
   *
   * @param s an instruction that is a GC point
   * @param r register to check
   * @return {@code true} if the register is in a scratch register and
   *  the scratch register is dirty, {@code false} otherwise
   */
  public boolean isDirty(Instruction s, Register r) {
    HashSet<Register> set = dirtyMap.get(s);
    if (set == null) {
      return false;
    } else {
      return set.contains(r);
    }
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    for (ArrayList<Interval> v : map.values()) {
      for (Interval i : v) {
        result.append(i);
        result.append("\n");
      }
    }
    return result.toString();
  }

  /**
   * Super class of physical and symbolic intervals
   */
  private abstract class Interval {
    /**
     * The instruction before which the scratch range begins.
     */
    protected Instruction begin;
    /**
     * The instruction before which the scratch range ends.
     */
    protected Instruction end;
    /**
     * The physical scratch register or register evicted.
     */
    protected final Register scratch;

    protected Interval(Register scratch) {
      this.scratch = scratch;
    }

    /**
     * Does this interval contain the instruction numbered n?
     *
     * @param n instruction number
     * @return {@code true} if and only if the instruction with the
     *   given number is contained n this interval
     */
    protected final boolean contains(int n) {
      return (regAllocState.getDFN(begin) <= n &&
          regAllocState.getDFN(end) > n);
    }
  }

  /**
   * An object that represents an interval where a symbolic register
   * resides in a scratch register.<p>
   *
   * Note that this interval must not span a basic block.
   */
  private final class SymbolicInterval extends Interval {
    /**
     * The symbolic register
     */
    private final Register symbolic;

    SymbolicInterval(Register symbolic, Register scratch) {
      super(scratch);
      this.symbolic = symbolic;
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * Instruction identifies an instruction.
     *
     * @return a string representation of this interval
     */
    @Override
    public String toString() {
      return "SI: " + symbolic + " " + scratch + " [" +
          regAllocState.getDFN(begin) + "," + regAllocState.getDFN(end) + "]";
    }
  }

  /**
   * An object that represents an interval where a physical register's
   * contents are evicted so that the physical register can be used as a
   * scratch.<p>
   *
   * Note that this interval must not span a basic block.
   */
  private final class PhysicalInterval extends Interval {
    PhysicalInterval(Register scratch) {
      super(scratch);
    }

    /**
     * Return a string representation, assuming the 'scratch' field of
     * Instruction identifies an instruction.
     *
     * @return a string representation of this interval
     */
    @Override
    public String toString() {
      return "PI: " + scratch + " [" + regAllocState.getDFN(begin) +
          "," + regAllocState.getDFN(end) + "]";
    }
  }
}
