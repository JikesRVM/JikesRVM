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

import java.util.Iterator;
import java.util.SortedSet;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * Implements a live interval with holes; i.e. an ordered set of basic live
 * intervals. There is exactly one instance of this class for each
 * Register.
 * <p>
 * The order that this set imposes is inconsistent with equals.
 * <p>
 * This class is designed for use by a single thread.
 */
class CompoundInterval extends IncreasingStartIntervalSet {
  /** Support for Set serialization */
  static final long serialVersionUID = 7423553044815762071L;
  /**
   * Is this compound interval fully contained in infrequent code?
   */
  private boolean _infrequent = true;

  final void setFrequent() {
    _infrequent = false;
  }

  final boolean isInfrequent() {
    return _infrequent;
  }

  /**
   * The register this compound interval represents or {@code null}
   * if this interval is not associated with a register (i.e. if it
   * represents a spill location).
   */
  private final Register reg;

  /**
   * A spill location assigned for this interval.
   */
  private SpillLocationInterval spillInterval;

  SpillLocationInterval getSpillInterval() {
    return spillInterval;
  }

  /**
   * @return the register this interval represents
   */
  Register getRegister() {
    return reg;
  }

  /**
   * Creates a new compound interval of a single Basic interval.
   *
   * @param dfnBegin interval's begin
   * @param dfnEnd interval's end
   * @param register the register for the compound interval
   */
  CompoundInterval(int dfnBegin, int dfnEnd, Register register) {
    BasicInterval newInterval = new MappedBasicInterval(dfnBegin, dfnEnd, this);
    add(newInterval);
    reg = register;
  }

  /**
   * Creates a new compound interval of a single Basic interval.
   *
   * @param i interval providing start and end for the new interval
   * @param register the register for the compound interval
   */
  CompoundInterval(BasicInterval i, Register register) {
    BasicInterval newInterval = new MappedBasicInterval(i.getBegin(), i.getEnd(), this);
    add(newInterval);
    reg = register;
  }

  /**
   * Dangerous constructor: use with care.
   * <p>
   * Creates a compound interval with a register but doesn't actually
   * add any intervals to the compound interval.
   *
   * @param r a register
   */
  protected CompoundInterval(Register r) {
    reg = r;
  }

  /**
   * Copies the ranges from this interval into a new interval associated
   * with a register.
   *
   * @param r the register for the new interval
   * @return the new interval
   */
  CompoundInterval copy(Register r) {
    CompoundInterval result = new CompoundInterval(r);

    for (Iterator<BasicInterval> i = iterator(); i.hasNext();) {
      BasicInterval b = i.next();
      result.add(b);
    }
    return result;
  }

  /**
   * Copies the basic intervals up to and including stop into a new interval.
   * The new interval is associated with the given register.
   *
   * @param r the register for the new interval
   * @param stop the interval to stop at
   * @return the new interval
   */
  CompoundInterval copy(Register r, BasicInterval stop) {
    CompoundInterval result = new CompoundInterval(r);

    for (Iterator<BasicInterval> i = iterator(); i.hasNext();) {
      BasicInterval b = i.next();
      result.add(b);
      if (b.sameRange(stop)) return result;
    }
    return result;
  }

  /**
   * Add a new live range to this compound interval.
   * @param regAllocState depth-first numbers for for instructions
   * @param live the new live range
   * @param bb the basic block for live
   *
   * @return the new basic interval if one was created; null otherwise
   */
  BasicInterval addRange(RegisterAllocatorState regAllocState, LiveIntervalElement live, BasicBlock bb) {
    if (shouldConcatenate(regAllocState, live, bb)) {
      // concatenate with the last basic interval
      BasicInterval last = last();
      last.setEnd(regAllocState.getDfnEnd(live, bb));
      return null;
    } else {
      // create a new basic interval and append it to the list.
      BasicInterval newInterval = new MappedBasicInterval(regAllocState.getDfnBegin(live, bb), regAllocState.getDfnEnd(live, bb), this);
      add(newInterval);
      return newInterval;
    }
  }

  /**
   * Should we simply merge the live interval <code>live</code> into a
   *  previous BasicInterval?
   * @param regAllocState depth-first numbers for for instructions
   * @param live the live interval being queried
   * @param bb the basic block in which live resides.
   *
   * @return {@code true} if the interval should be concatenated, {@code false}
   *  if it should'nt
   */
  private boolean shouldConcatenate(RegisterAllocatorState regAllocState, LiveIntervalElement live, BasicBlock bb) {

    BasicInterval last = last();

    // Make sure the new live range starts after the last basic interval
    if (VM.VerifyAssertions) {
      VM._assert(last.getEnd() <= regAllocState.getDfnBegin(live, bb));
    }

    int dfnBegin = regAllocState.getDfnBegin(live, bb);
    if (live.getBegin() != null) {
      if (live.getBegin() == bb.firstRealInstruction()) {
        // live starts the basic block.  Now make sure it is contiguous
        // with the last interval.
        return last.getEnd() + 1 >= dfnBegin;
      } else {
        // live does not start the basic block.  Merge with last iff
        // last and live share an instruction.  This happens when a
        // register is def'ed and use'd in the same instruction.
        return last.getEnd() == dfnBegin;
      }
    } else {
      // live.getBegin == null.
      // Merge if it is contiguous with the last interval.
      int dBegin = regAllocState.getDFN(bb.firstInstruction());
      return last.getEnd() + 1 >= dBegin;
    }
  }

  /**
   * Assign this compound interval to a free spill location.
   *
   * @param spillManager governing spill location manager
   * @param regAllocState current state of the register allocator
   */
  void spill(SpillLocationManager spillManager, RegisterAllocatorState regAllocState) {
    spillInterval = spillManager.findOrCreateSpillLocation(this);
    regAllocState.setSpill(reg, spillInterval.getOffset());
    regAllocState.clearOneToOne(reg);
    if (LinearScan.VERBOSE_DEBUG) {
      System.out.println("Assigned " + reg + " to location " + spillInterval.getOffset());
    }
  }

  boolean isSpilled(RegisterAllocatorState regAllocState) {
    return (regAllocState.getSpill(getRegister()) != 0);
  }

  /**
   * Assign this compound interval to a physical register.
   *
   * @param r the register to assign to
   */
  void assign(Register r) {
    getRegister().allocateToRegister(r);
  }

  /**
   * @param regAllocState current state of the register allocator
   * @return {@code true} if this interval has been assigned to
   *  a physical register
   */
  boolean isAssigned(RegisterAllocatorState regAllocState) {
    return (regAllocState.getMapping(getRegister()) != null);
  }

  /**
   * @param regAllocState current state of the register allocator
   * @return the physical register this interval is assigned to, {@code null}
   *  if none assigned
   */
  Register getAssignment(RegisterAllocatorState regAllocState) {
    return regAllocState.getMapping(getRegister());
  }

  /**
   * Merges this interval with another, non-intersecting interval.
   * <p>
   * Precondition: BasicInterval stop is an interval in i.  This version
   * will only merge basic intervals up to and including stop into this.
   *
   * @param i a non-intersecting interval for merging
   * @param stop a interval to stop at
   */
  void addNonIntersectingInterval(CompoundInterval i, BasicInterval stop) {
    SortedSet<BasicInterval> headSet = i.headSetInclusive(stop);
    addAll(headSet);
  }

  /**
   * Computes the headSet() [from java.util.SortedSet] but includes all
   * elements less than upperBound <em>inclusive</em>.
   *
   * @param upperBound the interval acting as upper bound
   * @return the head set
   * @see SortedSet#headSet(Object)
   */
  SortedSet<BasicInterval> headSetInclusive(BasicInterval upperBound) {
    BasicInterval newUpperBound = new BasicInterval(upperBound.getBegin() + 1, upperBound.getEnd());
    return headSet(newUpperBound);
  }

  /**
   * Computes the headSet() [from java.util.SortedSet] but includes all
   * elements less than upperBound <em>inclusive</em>.
   *
   * @param upperBound the instruction number acting as upper bound
   * @return the head set
   * @see SortedSet#headSet(Object)
   */
  SortedSet<BasicInterval> headSetInclusive(int upperBound) {
    BasicInterval newUpperBound = new BasicInterval(upperBound + 1, upperBound + 1);
    return headSet(newUpperBound);
  }

  /**
   * Computes the tailSet() [from java.util.SortedSet] but includes all
   * elements greater than lowerBound <em>inclusive</em>.
   *
   * @param lowerBound the instruction number acting as lower bound
   * @return the tail set
   * @see SortedSet#tailSet(Object)
   */
  SortedSet<BasicInterval> tailSetInclusive(int lowerBound) {
    BasicInterval newLowerBound = new BasicInterval(lowerBound - 1, lowerBound - 1);
    return tailSet(newLowerBound);
  }

  /**
   * Removes some basic intervals from this compound interval, and returns
   * the intervals actually removed.
   * <p>
   * PRECONDITION: All basic intervals in the other interval that have the
   * same begin as an interval in this compound interval must have the same
   * end. For example, for a compound interval {@code [(1,2)(2,3)]},
   * the other interval would be allowed to contain {@code (1,2)} and/or
   * {@code (2,2)} but not {@code (1,3)}.
   * <p>
   * A violation of the precondition that would have an effect will trigger
   * an assertion failure in assertion-enabled builds.
   *
   * @param other interval to check for intervals that we want to remove
   *  from this
   * @return the basic intervals that were removed
   */
  CompoundInterval removeIntervalsAndCache(CompoundInterval other) {
    CompoundInterval result = new CompoundInterval(other.getRegister());
    Iterator<BasicInterval> myIterator = iterator();
    Iterator<BasicInterval> otherIterator = other.iterator();
    BasicInterval current = myIterator.hasNext() ? myIterator.next() : null;
    BasicInterval otherCurrent = otherIterator.hasNext() ? otherIterator.next() : null;

    while (otherCurrent != null && current != null) {
      if (current.startsBefore(otherCurrent)) {
        current = myIterator.hasNext() ? myIterator.next() : null;
      } else if (otherCurrent.startsBefore(current)) {
        otherCurrent = otherIterator.hasNext() ? otherIterator.next() : null;
      } else {
        if (VM.VerifyAssertions) VM._assert(current.sameRange(otherCurrent));

        otherCurrent = otherIterator.hasNext() ? otherIterator.next() : null;
        BasicInterval next = myIterator.hasNext() ? myIterator.next() : null;
        // add the interval to the cache
        result.add(current);
        current = next;
      }
    }

    removeAll(result);
    return result;
  }

  /**
   * @return the lowest DFN in this compound interval at this time
   */
  int getLowerBound() {
    BasicInterval b = first();
    return b.getBegin();
  }

  /**
   * @return the highest DFN in this compound interval at this time
   */
  int getUpperBound() {
    BasicInterval b = last();
    return b.getEnd();
  }

  boolean intersects(CompoundInterval i) {

    if (isEmpty()) return false;
    if (i.isEmpty()) return false;

    // Walk over the basic intervals of this interval and i.
    // Restrict the walking to intervals that might intersect.
    int lower = Math.max(getLowerBound(), i.getLowerBound());
    int upper = Math.min(getUpperBound(), i.getUpperBound());

    // we may have to move one interval lower on each side.
    BasicInterval b = getBasicInterval(lower);
    int myLower = (b == null) ? lower : b.getBegin();
    if (myLower > upper) return false;
    b = i.getBasicInterval(lower);
    int otherLower = (b == null) ? lower : b.getBegin();
    if (otherLower > upper) return false;

    SortedSet<BasicInterval> myTailSet = tailSetInclusive(myLower);
    SortedSet<BasicInterval> otherTailSet = i.tailSetInclusive(otherLower);
    Iterator<BasicInterval> myIterator = myTailSet.iterator();
    Iterator<BasicInterval> otherIterator = otherTailSet.iterator();

    BasicInterval current = myIterator.hasNext() ? myIterator.next() : null;
    BasicInterval currentI = otherIterator.hasNext() ? otherIterator.next() : null;

    while (current != null && currentI != null) {
      if (current.getBegin() > upper) break;
      if (currentI.getBegin() > upper) break;
      if (current.intersects(currentI)) return true;

      if (current.startsBefore(currentI)) {
        current = myIterator.hasNext() ? myIterator.next() : null;
      } else {
        currentI = otherIterator.hasNext() ? otherIterator.next() : null;
      }
    }
    return false;
  }

  /**
   * @param dfnNumbers depth-first numbers for for instructions
   * @param s   The instruction in question
   * @return the first basic interval that contains a given
   * instruction, {@code null} if there is no such interval

   */
  BasicInterval getBasicInterval(RegisterAllocatorState dfnNumbers, Instruction s) {
    return getBasicInterval(dfnNumbers.getDFN(s));
  }

  /**
   * @param n The DFN of the instruction in question
   * @return the first basic interval that contains a given
   * instruction, {@code null} if there is no such interval
   */
  BasicInterval getBasicInterval(int n) {
    SortedSet<BasicInterval> headSet = headSetInclusive(n);
    if (!headSet.isEmpty()) {
      BasicInterval last = headSet.last();
      return last.contains(n) ? last : null;
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder("[");
    str.append(getRegister());
    str.append("]:");
    for (Iterator<BasicInterval> i = iterator(); i.hasNext();) {
      BasicInterval b = i.next();
      str.append(b);
    }
    return str.toString();
  }
}
