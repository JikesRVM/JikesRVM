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

import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.ArchitectureSpecificOpt.RegisterRestrictions;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.util.GraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;

/**
 * Main driver for linear scan register allocation.
 */
public final class LinearScan extends OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  LinearScan() {
    super("Linear Scan Composite Phase",
          new OptimizationPlanElement[]{new OptimizationPlanAtomicElement(new IntervalAnalysis()),
                                            new OptimizationPlanAtomicElement(new RegisterRestrictionsPhase()),
                                            new OptimizationPlanAtomicElement(new LinearScanPhase()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps1()),
                                            new OptimizationPlanAtomicElement(new SpillCode()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps2()),
                                            new OptimizationPlanAtomicElement(new UpdateOSRMaps()),});
  }

  /**
   * Mark FMOVs that end a live range?
   */
  static final boolean MUTATE_FMOV = VM.BuildForIA32;

  /*
   * debug flags
   */
  static final boolean DEBUG = false;
  static final boolean VERBOSE_DEBUG = false;
  static final boolean GC_DEBUG = false;
  static final boolean DEBUG_COALESCE = false;

  /**
   * Register allocation is required
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Linear Scan Composite Phase";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  Associates the passed live interval with the passed register, using
   *  the scratchObject field of Register.
   *
   *  @param reg the register
   *  @param interval the live interval
   */
  static void setInterval(Register reg, CompoundInterval interval) {
    reg.scratchObject = interval;
  }

  /**
   *  Returns the interval associated with the passed register.
   *  @param reg the register
   *  @return the live interval or {@code null}
   */
  static CompoundInterval getInterval(Register reg) {
    return (CompoundInterval) reg.scratchObject;
  }

  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  static int getDFN(Instruction inst) {
    return inst.scratch;
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  static void setDFN(Instruction inst, int dfn) {
    inst.scratch = dfn;
  }

  /**
   *  Prints the DFN numbers associated with each instruction.
   *
   *  @param ir the IR that contains the instructions
   */
  static void printDfns(IR ir) {
    System.out.println("DFNS: **** " + ir.getMethod() + "****");
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      System.out.println(getDFN(inst) + " " + inst);
    }
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the end of the live interval. If the
   * interval is open-ended, the dfn for the end of the basic block will
   * be returned instead.
   */
  static int getDfnEnd(LiveIntervalElement live, BasicBlock bb) {
    Instruction end = live.getEnd();
    int dfnEnd;
    if (end != null) {
      dfnEnd = getDFN(end);
    } else {
      dfnEnd = getDFN(bb.lastInstruction());
    }
    return dfnEnd;
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the beginning of the live interval. If the
   * interval is open-ended, the dfn for the beginning of the basic block will
   * be returned instead.
   */
  static int getDfnBegin(LiveIntervalElement live, BasicBlock bb) {
    Instruction begin = live.getBegin();
    int dfnBegin;
    if (begin != null) {
      dfnBegin = getDFN(begin);
    } else {
      dfnBegin = getDFN(bb.firstInstruction());
    }
    return dfnBegin;
  }

  /**
   * Implements a basic live interval (no holes), which is a pair
   * <pre>
   *   begin    - the starting point of the interval
   *   end      - the ending point of the interval
   * </pre>
   *
   * <p> Begin and end are numbers given to each instruction by a numbering pass.
   */
  static class BasicInterval {

    /**
     * DFN of the beginning instruction of this interval
     */
    private final int begin;
    /**
     * DFN of the last instruction of this interval
     */
    private int end;

    BasicInterval(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    /**
     * @return the DFN signifying the beginning of this basic interval
     */
    final int getBegin() {
      return begin;
    }

    /**
     * @return the DFN signifying the end of this basic interval
     */
    final int getEnd() {
      return end;
    }

    /**
     * Extends a live interval to a new endpoint.
     *
     * @param newEnd the new end point
     */
    final void setEnd(int newEnd) {
      end = newEnd;
    }

    final boolean startsAfter(int dfn) {
      return begin > dfn;
    }

    final boolean startsBefore(int dfn) {
      return begin < dfn;
    }

    final boolean contains(int dfn) {
      return begin <= dfn && end >= dfn;
    }

    final boolean startsBefore(BasicInterval i) {
      return begin < i.begin;
    }

    final boolean endsAfter(BasicInterval i) {
      return end > i.end;
    }

    final boolean sameRange(BasicInterval i) {
      return begin == i.begin && end == i.end;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof BasicInterval)) return false;

      BasicInterval i = (BasicInterval) o;
      return sameRange(i);
    }

    final boolean endsBefore(int dfn) {
      return end < dfn;
    }

    final boolean endsAfter(int dfn) {
      return end > dfn;
    }

    final boolean intersects(BasicInterval i) {
      int iBegin = i.getBegin();
      int iEnd = i.getEnd();
      return !(endsBefore(iBegin + 1) || startsAfter(iEnd - 1));
    }

    @Override
    public String toString() {
      String s = "[ " + begin + ", " + end + " ] ";
      return s;
    }
  }

  /**
   * A basic interval contained in a CompoundInterval.
   */
  static class MappedBasicInterval extends BasicInterval {
    final CompoundInterval container;

    MappedBasicInterval(BasicInterval b, CompoundInterval c) {
      super(b.begin, b.end);
      this.container = c;
    }

    MappedBasicInterval(int begin, int end, CompoundInterval c) {
      super(begin, end);
      this.container = c;
    }

    @Override
    public boolean equals(Object o) {
      if (super.equals(o)) {
        MappedBasicInterval i = (MappedBasicInterval) o;
        return container == i.container;
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return "<" + container.getRegister() + ">:" + super.toString();
    }

  }

  /**
   * Implements a live interval with holes; ie; a list of basic live
   * intervals.
   */
  static class CompoundInterval extends IncreasingStartIntervalSet {
    /** Support for Set serialization */
    static final long serialVersionUID = 7423553044815762071L;
    /**
     * Is this compound interval fully contained in infrequent code?
     */
    private boolean _infrequent = true;

    final void setFrequent() { _infrequent = false; }

    final boolean isInfrequent() { return _infrequent; }

    /**
     * The register this compound interval represents
     */
    private final Register reg;

    /**
     * A spill location assigned for this interval.
     */
    private SpillLocationInterval spillInterval;

    SpillLocationInterval getSpillInterval() { return spillInterval; }

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
     * @param register the register for the compund interval
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
     *
     * @param live the new live range
     * @param bb the basic block for live
     * @return the new basic interval if one was created; null otherwise
     */
    BasicInterval addRange(LiveIntervalElement live, BasicBlock bb) {

      if (shouldConcatenate(live, bb)) {
        // concatenate with the last basic interval
        BasicInterval last = last();
        last.setEnd(getDfnEnd(live, bb));
        return null;
      } else {
        // create a new basic interval and append it to the list.
        BasicInterval newInterval = new MappedBasicInterval(getDfnBegin(live, bb), getDfnEnd(live, bb), this);
        add(newInterval);
        return newInterval;
      }
    }

    /**
     * Should we simply merge the live interval <code>live</code> into a
     *  previous BasicInterval?
     *
     * @param live the live interval being queried
     * @param bb the basic block in which live resides.
     * @return {@code true} if the interval should be concatenated, {@code false}
     *  if it should'nt
     */
    private boolean shouldConcatenate(LiveIntervalElement live, BasicBlock bb) {

      BasicInterval last = last();

      // Make sure the new live range starts after the last basic interval
      if (VM.VerifyAssertions) {
        VM._assert(last.getEnd() <= getDfnBegin(live, bb));
      }

      int dfnBegin = getDfnBegin(live, bb);
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
        int dBegin = getDFN(bb.firstInstruction());
        return last.getEnd() + 1 >= dBegin;
      }
    }

    /**
     * Assign this compound interval to a free spill location.
     *
     * @param spillManager governing spill location manager
     */
    void spill(SpillLocationManager spillManager) {
      spillInterval = spillManager.findOrCreateSpillLocation(this);
      RegisterAllocatorState.setSpill(reg, spillInterval.getOffset());
      RegisterAllocatorState.clearOneToOne(reg);
      if (VERBOSE_DEBUG) {
        System.out.println("Assigned " + reg + " to location " + spillInterval.getOffset());
      }
    }

    boolean isSpilled() {
      return (RegisterAllocatorState.getSpill(getRegister()) != 0);
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
     * @return {@code true} if this interval has been assigned to
     *  a physical register
     */
    boolean isAssigned() {
      return (RegisterAllocatorState.getMapping(getRegister()) != null);
    }

    /**
     * @return the physical register this interval is assigned to, {@code null}
     *  if none assigned
     */
    Register getAssignment() {
      return RegisterAllocatorState.getMapping(getRegister());
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
     * PRECONDITION: all basic intervals in i must appear in this compound
     * interval, unless they end after the end of this interval
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

      // SJF: the loop as written is slightly more efficient than calling
      // removeAll().  However, for some reason, replacing the loop with
      // the call to removeAll breaks the compiler on OptTestHarness
      // -oc:O2 -method RVMMethod replaceCharWithString -.  This is deeply
      // disturbing.  TODO: fix it.  (Hope the problem goes away if/when
      // we migrate to classpath libraries).
      // removeAll(result);
      for (BasicInterval b : result) {
        remove(b);
      }

      return result;
    }

    /**
     * SJF: Apparently our java.util implementation of removeAll()
     * doesn't work.  Perhaps I've somehow screwed up the comparator with
     * the "consistent with equals" property?
     * It breaks javalex on BaseOptMarkSweep on IA32
     * Hopefully this problem will go away if/when we switch to classpath.
     * Else, perhaps I'll ditch use of java.util Collections and write my
     * own collection classes.
     * In the meantime, here's an ugly hack to get around the problem.
     *
     * @param c container for the intervals that should be removed
     */
    void removeAll(CompoundInterval c) {
      for (BasicInterval b : c) {
        remove(b);
      }
    }

    /**
     * @return the lowest DFN in this compound interval
     */
    int getLowerBound() {
      BasicInterval b = first();
      return b.getBegin();
    }

    /**
     * @return the highest DFN in this compound interval
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
     * @param s   The instruction in question
     * @return the first basic interval that contains a given
     * instruction, {@code null} if there is no such interval

     */
    BasicInterval getBasicInterval(Instruction s) {
      return getBasicInterval(getDFN(s));
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
      String str = "[" + getRegister() + "]:";
      for (Iterator<BasicInterval> i = iterator(); i.hasNext();) {
        BasicInterval b = i.next();
        str = str + b;
      }
      return str;
    }
  }

  /**
   * "Active set" for linear scan register allocation.
   * This version is maintained sorted in order of increasing
   * live interval end point.
   */
  static final class ActiveSet extends IncreasingEndMappedIntervalSet {
    /** Support for Set serialization */
    static final long serialVersionUID = 2570397490575294294L;
    /**
     * Governing ir
     */
    private final IR ir;

    /**
     * Manager of spill locations;
     */
    private final SpillLocationManager spillManager;

    /**
     * An object to help estimate spill costs
     */
    private final transient SpillCostEstimator spillCost;

    /**
     * Have we spilled anything?
     */
    private boolean spilled;

    ActiveSet(IR ir, SpillLocationManager sm) {
      super();
      spilled = false;
      this.ir = ir;
      this.spillManager = sm;

      switch (ir.options.REGALLOC_SPILL_COST_ESTIMATE) {
        case OptOptions.REGALLOC_SIMPLE_SPILL_COST:
          spillCost = new SimpleSpillCost(ir);
          break;
        case OptOptions.REGALLOC_BRAINDEAD_SPILL_COST:
          spillCost = new BrainDeadSpillCost(ir);
          break;
        case OptOptions.REGALLOC_BLOCK_COUNT_SPILL_COST:
          spillCost = new BlockCountSpillCost(ir);
          break;
        default:
          OptimizingCompilerException.UNREACHABLE("unsupported spill cost");
          spillCost = null;
      }
    }

    boolean spilledSomething() {
      return spilled;
    }

    /**
     *  For each new basic interval, we scan the list of active basic
     *  intervals in order of increasing end point.  We remove any "expired"
     *  intervals - those
     *  intervals that no longer overlap the new interval because their
     *  end point precedes the new interval's start point - and makes the
     *  corresponding register available for allocation
     *
     *  @param newInterval the new interval
     */
    void expireOldIntervals(BasicInterval newInterval) {

      for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
        MappedBasicInterval bi = (MappedBasicInterval) e.next();

        // break out of the loop when we reach an interval that is still
        // alive
        int newStart = newInterval.getBegin();
        if (bi.endsAfter(newStart)) break;

        if (VERBOSE_DEBUG) System.out.println("Expire " + bi);

        // note that the bi interval no longer is live
        freeInterval(bi);

        // remove bi from the active set
        e.remove();

      }
    }

    void freeInterval(MappedBasicInterval bi) {
      CompoundInterval container = bi.container;
      Register r = container.getRegister();

      if (r.isPhysical()) {
        r.deallocateRegister();
        return;
      }

      if (container.isSpilled()) {
        // free the spill location iff this is the last interval in the
        // compound interval.
        BasicInterval last = container.last();
        if (last.sameRange(bi)) {
          spillManager.freeInterval(container.getSpillInterval());
        }
      } else {
        // free the assigned register
        if (VM.VerifyAssertions) VM._assert(container.getAssignment().isAllocated());
        container.getAssignment().deallocateRegister();
      }

    }

    void allocate(BasicInterval newInterval, CompoundInterval container) {

      if (DEBUG) {
        System.out.println("Allocate " + newInterval + " " + container.getRegister());
      }

      Register r = container.getRegister();

      if (container.isSpilled()) {
        // We previously decided to spill the compound interval.  No further
        // action is needed.
        if (VERBOSE_DEBUG) System.out.println("Previously spilled " + container);
      } else {
        if (container.isAssigned()) {
          // The compound interval was previously assigned to a physical
          // register.
          Register phys = container.getAssignment();
          if (!currentlyActive(phys)) {
            // The assignment of newInterval to phys is still OK.
            // Update the live ranges of phys to include the new basic
            // interval
            if (VERBOSE_DEBUG) {
              System.out.println("Previously assigned to " +
                                 phys +
                                 " " +
                                 container +
                                 " phys interval " +
                                 getInterval(phys));
            }
            updatePhysicalInterval(phys, newInterval);
            if (VERBOSE_DEBUG) {
              System.out.println(" now phys interval " + getInterval(phys));
            }
            // Mark the physical register as currently allocated
            phys.allocateRegister();
          } else {
            // The previous assignment is not OK, since the physical
            // register is now in use elsewhere.
            if (DEBUG) {
              System.out.println("Previously assigned, " + phys + " " + container);
            }
            // first look and see if there's another free register for
            // container.
            if (VERBOSE_DEBUG) System.out.println("Looking for free register");
            Register freeR = findAvailableRegister(container);
            if (VERBOSE_DEBUG) System.out.println("Free register? " + freeR);

            if (freeR == null) {
              // Did not find a free register to assign.  So, spill one of
              // the two intervals concurrently allocated to phys.

              CompoundInterval currentAssignment = getCurrentInterval(phys);
              // choose which of the two intervals to spill
              double costA = spillCost.getCost(container.getRegister());
              double costB = spillCost.getCost(currentAssignment.getRegister());
              if (VERBOSE_DEBUG) {
                System.out.println("Current assignment " + currentAssignment + " cost " + costB);
              }
              if (VERBOSE_DEBUG) {
                System.out.println("Cost of spilling" + container + " cost " + costA);
              }
              CompoundInterval toSpill = (costA < costB) ? container : currentAssignment;
              // spill it.
              Register p = toSpill.getAssignment();
              toSpill.spill(spillManager);
              spilled = true;
              if (VERBOSE_DEBUG) {
                System.out.println("Spilled " + toSpill + " from " + p);
              }
              CompoundInterval physInterval = getInterval(p);
              physInterval.removeAll(toSpill);
              if (VERBOSE_DEBUG) System.out.println("  after spill phys" + getInterval(p));
              if (toSpill != container) updatePhysicalInterval(p, newInterval);
              if (VERBOSE_DEBUG) {
                System.out.println(" now phys interval " + getInterval(p));
              }
            } else {
              // found a free register for container! use it!
              if (DEBUG) {
                System.out.println("Switch container " + container + "from " + phys + " to " + freeR);
              }
              CompoundInterval physInterval = getInterval(phys);
              if (DEBUG) {
                System.out.println("Before switch phys interval" + physInterval);
              }
              physInterval.removeAll(container);
              if (DEBUG) {
                System.out.println("Intervals of " + phys + " now " + physInterval);
              }

              container.assign(freeR);
              updatePhysicalInterval(freeR, container, newInterval);
              if (VERBOSE_DEBUG) {
                System.out.println("Intervals of " + freeR + " now " + getInterval(freeR));
              }
              // mark the free register found as currently allocated.
              freeR.allocateRegister();
            }
          }
        } else {
          // This is the first attempt to allocate the compound interval.
          // Attempt to find a free physical register for this interval.
          Register phys = findAvailableRegister(r);
          if (phys != null) {
            // Found a free register.  Perform the register assignment.
            container.assign(phys);
            if (DEBUG) {
              System.out.println("First allocation " + phys + " " + container);
            }
            updatePhysicalInterval(phys, newInterval);
            if (VERBOSE_DEBUG) System.out.println("  now phys" + getInterval(phys));
            // Mark the physical register as currently allocated.
            phys.allocateRegister();
          } else {
            // Could not find a free physical register.  Some member of the
            // active set must be spilled.  Choose a spill candidate.
            CompoundInterval spillCandidate = getSpillCandidate(container);
            if (VM.VerifyAssertions) {
              VM._assert(!spillCandidate.isSpilled());
              VM._assert((spillCandidate.getRegister().getType() == r.getType()) ||
                         (spillCandidate.getRegister().isNatural() && r.isNatural()));
              VM._assert(!ir.stackManager.getRestrictions().mustNotSpill(spillCandidate.getRegister()));
              if (spillCandidate.getAssignment() != null) {
                VM._assert(!ir.stackManager.getRestrictions().
                    isForbidden(r, spillCandidate.getAssignment()));
              }
            }
            if (spillCandidate != container) {
              // spill a previously allocated interval.
              phys = spillCandidate.getAssignment();
              spillCandidate.spill(spillManager);
              spilled = true;
              if (VERBOSE_DEBUG) {
                System.out.println("Spilled " + spillCandidate + " from " + phys);
              }
              CompoundInterval physInterval = getInterval(phys);
              if (VERBOSE_DEBUG) {
                System.out.println(" assigned " + phys + " to " + container);
              }
              physInterval.removeAll(spillCandidate);
              if (VERBOSE_DEBUG) System.out.println("  after spill phys" + getInterval(phys));
              updatePhysicalInterval(phys, newInterval);
              if (VERBOSE_DEBUG) {
                System.out.println(" now phys interval " + getInterval(phys));
              }
              container.assign(phys);
            } else {
              // spill the new interval.
              if (VERBOSE_DEBUG) System.out.println("spilled " + container);
              container.spill(spillManager);
              spilled = true;
            }
          }
        }
      }
    }

    /**
     * Updates the interval representing the allocations of a physical
     * register p to include a new interval i.
     *
     * @param p a physical register
     * @param i the new interval
     */
    private void updatePhysicalInterval(Register p, BasicInterval i) {
      // Get a representation of the intervals already assigned to p.
      CompoundInterval physInterval = getInterval(p);
      if (physInterval == null) {
        // no interval yet.  create one.
        setInterval(p, new CompoundInterval(i, p));
      } else {
        // incorporate i into the set of intervals assigned to p
        CompoundInterval ci = new CompoundInterval(i, p);
        if (VM.VerifyAssertions) VM._assert(!ci.intersects(physInterval));
        physInterval.addAll(ci);
      }
    }

    /**
     * Update the interval representing the allocations of a physical
     * register p to include a new compound interval c.  Include only
     * those basic intervals in c up to and including basic interval stop.
     *
     * @param p a physical register
     * @param c the new interval
     * @param stop the last interval to be included
     */
    private void updatePhysicalInterval(Register p, CompoundInterval c, BasicInterval stop) {
      // Get a representation of the intervals already assigned to p.
      CompoundInterval physInterval = getInterval(p);
      if (physInterval == null) {
        // no interval yet.  create one.
        setInterval(p, c.copy(p, stop));
      } else {
        // incorporate c into the set of intervals assigned to p
        if (VM.VerifyAssertions) VM._assert(!c.intersects(physInterval));
        // copy to a new BasicInterval so "equals" will work as expected,
        // since "stop" may be a MappedBasicInterval.
        stop = new BasicInterval(stop.getBegin(), stop.getEnd());
        physInterval.addNonIntersectingInterval(c, stop);
      }
    }

    /**
     * @param r a physical register
     * @return whether the particular physical register is currently allocated to an
     * interval in the active set
     */
    boolean currentlyActive(Register r) {
      for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
        MappedBasicInterval i = (MappedBasicInterval) e.next();
        CompoundInterval container = i.container;
        if (RegisterAllocatorState.getMapping(container.getRegister()) == r) {
          return true;
        }
      }
      return false;
    }

    /**
     * @param r a physical register
     * @return the interval that the physical register is allocated to
     * @throws OptimizingCompilerException if the register is not currently
     *  allocated to any interval
     */
    CompoundInterval getCurrentInterval(Register r) {
      for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
        MappedBasicInterval i = (MappedBasicInterval) e.next();
        CompoundInterval container = i.container;
        if (RegisterAllocatorState.getMapping(container.getRegister()) == r) {
          return container;
        }
      }
      OptimizingCompilerException.UNREACHABLE("getCurrentInterval", "Not Currently Active ", r.toString());
      return null;
    }

    /**
     * @param ci interval to allocate
     * @return a free physical register to allocate to the compound
     * interval, {@code null} if no free physical register is found
     */
    Register findAvailableRegister(CompoundInterval ci) {

      if (ir.options.FREQ_FOCUS_EFFORT && ci.isInfrequent()) {
        // don't bother trying to find an available register
        return null;
      }

      Register r = ci.getRegister();
      RegisterRestrictions restrict = ir.stackManager.getRestrictions();

      // first attempt to allocate to the preferred register
      if (ir.options.REGALLOC_COALESCE_MOVES) {
        Register p = getPhysicalPreference(ci);
        if (p != null) {
          if (DEBUG_COALESCE) {
            System.out.println("REGISTER PREFERENCE " + ci + " " + p);
          }
          return p;
        }
      }

      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = PhysicalRegisterSet.getPhysicalRegisterType(r);

      // next attempt to allocate to a volatile
      if (!restrict.allVolatilesForbidden(r)) {
        for (Enumeration<Register> e = phys.enumerateVolatiles(type); e.hasMoreElements();) {
          Register p = e.nextElement();
          if (allocateToPhysical(ci, p)) {
            return p;
          }
        }
      }

      // next attempt to allocate to a Nonvolatile.  we allocate the
      // novolatiles backwards.
      for (Enumeration<Register> e = phys.enumerateNonvolatilesBackwards(type); e.hasMoreElements();) {
        Register p = e.nextElement();
        if (allocateToPhysical(ci, p)) {
          return p;
        }
      }

      // no allocation succeeded.
      return null;
    }

    /**
     * @param symb symbolic register to allocate
     * @return a free physical register to allocate to the symbolic
     * register, {@code null} if no free physical register is found
     */
    Register findAvailableRegister(Register symb) {

      RegisterRestrictions restrict = ir.stackManager.getRestrictions();

      // first attempt to allocate to the preferred register
      if (ir.options.REGALLOC_COALESCE_MOVES) {
        Register p = getPhysicalPreference(symb);
        if (p != null) {
          if (DEBUG_COALESCE) {
            System.out.println("REGISTER PREFERENCE " + symb + " " + p);
          }
          return p;
        }
      }

      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = PhysicalRegisterSet.getPhysicalRegisterType(symb);

      // next attempt to allocate to a volatile
      if (!restrict.allVolatilesForbidden(symb)) {
        for (Enumeration<Register> e = phys.enumerateVolatiles(type); e.hasMoreElements();) {
          Register p = e.nextElement();
          if (allocateNewSymbolicToPhysical(symb, p)) {
            return p;
          }
        }
      }

      // next attempt to allocate to a Nonvolatile.  we allocate the
      // novolatiles backwards.
      for (Enumeration<Register> e = phys.enumerateNonvolatilesBackwards(type); e.hasMoreElements();) {
        Register p = e.nextElement();
        if (allocateNewSymbolicToPhysical(symb, p)) {
          return p;
        }
      }

      // no allocation succeeded.
      return null;
    }

    /**
     * Given the current state of the register allocator, compute the
     * available physical register to which a symbolic register has the
     * highest preference.
     *
     * @param r the symbolic register in question.
     * @return the preferred register, {@code null} if no preference found.
     */
    private Register getPhysicalPreference(Register r) {
      // a mapping from Register to Integer
      // (physical register to weight);
      HashMap<Register, Integer> map = new HashMap<Register, Integer>();

      CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for affinity
      for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
        CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
        CoalesceGraph.Node src = (CoalesceGraph.Node) edge.from();
        Register neighbor = src.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          Register r2 = RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateNewSymbolicToPhysical(r, neighbor)) {
            int w = edge.getWeight();
            Integer oldW = map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor, w);
            } else {
              map.put(neighbor, oldW + w);
            }
            break;
          }
        }
      }
      // walk through all out edges of the node, searching for affinity
      for (Enumeration<GraphEdge> in = node.outEdges(); in.hasMoreElements();) {
        CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
        CoalesceGraph.Node dest = (CoalesceGraph.Node) edge.to();
        Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          Register r2 = RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateNewSymbolicToPhysical(r, neighbor)) {
            int w = edge.getWeight();
            Integer oldW = map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor, w);
            } else {
              map.put(neighbor, oldW + w);
            }
            break;
          }
        }
      }
      // OK, now find the highest preference.
      Register result = null;
      int weight = -1;
      for (Map.Entry<Register, Integer> entry : map.entrySet()) {
        int w = entry.getValue();
        if (w > weight) {
          weight = w;
          result = entry.getKey();
        }
      }
      return result;
    }

    /**
     * Given the current state of the register allocator, compute the
     * available physical register to which an interval has the highest
     * preference.
     *
     * @param ci the interval in question
     * @return the preferred register, {@code null} if no preference found
     */
    private Register getPhysicalPreference(CompoundInterval ci) {
      // a mapping from Register to Integer
      // (physical register to weight);
      HashMap<Register, Integer> map = new HashMap<Register, Integer>();
      Register r = ci.getRegister();

      CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for affinity
      for (Enumeration<GraphEdge> in = node.inEdges(); in.hasMoreElements();) {
        CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
        CoalesceGraph.Node src = (CoalesceGraph.Node) edge.from();
        Register neighbor = src.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          Register r2 = RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateToPhysical(ci, neighbor)) {
            int w = edge.getWeight();
            Integer oldW = map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor, w);
            } else {
              map.put(neighbor, oldW + w);
            }
            break;
          }
        }
      }
      // walk through all out edges of the node, searching for affinity
      for (Enumeration<GraphEdge> in = node.outEdges(); in.hasMoreElements();) {
        CoalesceGraph.Edge edge = (CoalesceGraph.Edge) in.nextElement();
        CoalesceGraph.Node dest = (CoalesceGraph.Node) edge.to();
        Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          Register r2 = RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateToPhysical(ci, neighbor)) {
            int w = edge.getWeight();
            Integer oldW = map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor, w);
            } else {
              map.put(neighbor, oldW + w);
            }
            break;
          }
        }
      }
      // OK, now find the highest preference.
      Register result = null;
      int weight = -1;
      for (Map.Entry<Register, Integer> entry : map.entrySet()) {
        int w = entry.getValue();
        if (w > weight) {
          weight = w;
          result = entry.getKey();
        }
      }
      return result;
    }

    /**
     * Checks whether it's ok to allocate an interval to a physical
     * register.
     *
     * @param i the interval to allocate
     * @param p the physical register
     * @return {@code true} if it's ok to allocate the interval to the
     *  given physical register, {@code false} otherwise
     */
    private boolean allocateToPhysical(CompoundInterval i, Register p) {
      RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      Register r = i.getRegister();
      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      if (p != null && !phys.isAllocatable(p)) return false;

      if (VERBOSE_DEBUG && p != null) {
        if (!p.isAvailable()) System.out.println("unavailable " + i + p);
        if (restrict.isForbidden(r, p)) System.out.println("forbidden" + i + p);
      }

      if ((p != null) && p.isAvailable() && !restrict.isForbidden(r, p)) {
        CompoundInterval pInterval = getInterval(p);
        if (pInterval == null) {
          // no assignment to p yet
          return true;
        } else {
          if (!i.intersects(pInterval)) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * NOTE: This routine assumes we're processing the first interval of
     * register symb; so p.isAvailable() is the key information needed.
     *
     * @param symb the symbolic register
     * @param p the physical register
     * @return whether it's ok to allocate the symbolic register to the physical
     * register
     */
    private boolean allocateNewSymbolicToPhysical(Register symb, Register p) {
      RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      if (p != null && !phys.isAllocatable(p)) return false;

      if (VERBOSE_DEBUG && p != null) {
        if (!p.isAvailable()) System.out.println("unavailable " + symb + p);
        if (restrict.isForbidden(symb, p)) System.out.println("forbidden" + symb + p);
      }

      return (p != null) && p.isAvailable() && !restrict.isForbidden(symb, p);
    }

    /**
     * @param newInterval a new interval
     * @return an interval that can be spilled. This may be chosen from the
     * existing candidates and the new interval
     */
    private CompoundInterval getSpillCandidate(CompoundInterval newInterval) {
      if (VERBOSE_DEBUG) System.out.println("GetSpillCandidate from " + this);

      if (ir.options.FREQ_FOCUS_EFFORT && newInterval.isInfrequent()) {
        // if it's legal to spill this infrequent interval, then just do so!
        // don't spend any more effort.
        RegisterRestrictions restrict = ir.stackManager.getRestrictions();
        if (!restrict.mustNotSpill(newInterval.getRegister())) {
          return newInterval;
        }
      }

      return spillMinUnitCost(newInterval);
    }

    /**
     * Chooses the interval with the minimal unit cost (defined as the number
     * of defs and uses).
     *
     * @param newInterval a new interval
     * @return the interval with the minimal number of defs and uses
     */
    private CompoundInterval spillMinUnitCost(CompoundInterval newInterval) {
      if (VERBOSE_DEBUG) {
        System.out.println(" interval caused a spill: " + newInterval);
      }
      RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      Register r = newInterval.getRegister();
      double minCost = spillCost.getCost(r);
      if (VERBOSE_DEBUG) {
        System.out.println(" spill cost: " + r + " " + minCost);
      }
      CompoundInterval result = newInterval;
      if (restrict.mustNotSpill(result.getRegister())) {
        if (VERBOSE_DEBUG) {
          System.out.println(" must not spill: " + result.getRegister());
        }
        result = null;
        minCost = Double.MAX_VALUE;
      }
      for (Iterator<BasicInterval> e = iterator(); e.hasNext();) {
        MappedBasicInterval b = (MappedBasicInterval) e.next();
        CompoundInterval i = b.container;
        Register newR = i.getRegister();
        if (VERBOSE_DEBUG) {
          if (i.isSpilled()) {
            System.out.println(" not candidate, already spilled: " + newR);
          }
          if ((r.getType() != newR.getType()) || (r.isNatural() && newR.isNatural())) {
            System.out.println(" not candidate, type mismatch : " + r.getType() + " " + newR + " " + newR.getType());
          }
          if (restrict.mustNotSpill(newR)) {
            System.out.println(" not candidate, must not spill: " + newR);
          }
        }
        if (!newR.isPhysical() &&
            !i.isSpilled() &&
            (r.getType() == newR.getType() || (r.isNatural() && newR.isNatural())) &&
            !restrict.mustNotSpill(newR)) {
          // Found a potential spill interval. Check if the assignment
          // works if we spill this interval.
          if (checkAssignmentIfSpilled(newInterval, i)) {
            double iCost = spillCost.getCost(newR);
            if (VERBOSE_DEBUG) {
              System.out.println(" potential candidate " + i + " cost " + iCost);
            }
            if (iCost < minCost) {
              if (VERBOSE_DEBUG) System.out.println(" best candidate so far" + i);
              minCost = iCost;
              result = i;
            }
          } else {
            if (VERBOSE_DEBUG) {
              System.out.println(" not a candidate, insufficient range: " + i);
            }
          }
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert(result != null);
      }
      return result;
    }

    /**
     * Checks if it would be possible to assign an interval to the physical
     * register of another interval if that other interval were spilled.
     *
     * @param spill the interval that's a candidate for spilling
     * @param i the interval that we want to assign to the register of the spill interval
     * @return {@code true} if the allocation would fit,  {@code false} otherwise
     */
    private boolean checkAssignmentIfSpilled(CompoundInterval i, CompoundInterval spill) {
      Register r = spill.getAssignment();

      RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      if (restrict.isForbidden(i.getRegister(), r)) return false;

      // 1. Speculatively simulate the spill.
      CompoundInterval rI = getInterval(r);
      CompoundInterval cache = rI.removeIntervalsAndCache(spill);

      // 2. Check the fit.
      boolean result = !rI.intersects(i);

      // 3. Undo the simulated spill.
      rI.addAll(cache);

      return result;
    }

    /**
     * Finds a basic interval for a register so that the interval contains
     * the instruction.
     *
     * @param r the register whose interval is desired
     * @param s the reference instruction
     * @return {@code null} if there is no basic interval for the given register
     *  that contains the instruction, the interval otherwise. If there are
     *  multiple intervals, the first one will be returned.
     */
    BasicInterval getBasicInterval(Register r, Instruction s) {
      CompoundInterval c = getInterval(r);
      if (c == null) return null;
      return c.getBasicInterval(s);
    }

  }

  /**
   * The following represents the intervals assigned to a particular spill
   * location
   */
  static class SpillLocationInterval extends CompoundInterval {
    /** Support for Set serialization */
    static final long serialVersionUID = 2854333172650538517L;
    /**
     * The spill location, an offset from the frame pointer
     */
    private final int frameOffset;

    int getOffset() {
      return frameOffset;
    }

    /**
     * The size of the spill location, in bytes.
     */
    private final int size;

    int getSize() {
      return size;
    }

    SpillLocationInterval(int frameOffset, int size) {
      super(null);
      this.frameOffset = frameOffset;
      this.size = size;
    }

    @Override
    public String toString() {
      return super.toString() + "<Offset:" + frameOffset + "," + size + ">";
    }

    /**
     * Redefine hash code for reproducibility.
     */
    @Override
    public int hashCode() {
      BasicInterval first = first();
      BasicInterval last = last();
      return frameOffset + (first.getBegin() << 4) + (last.getEnd() << 12);
    }
  }

  /**
   * Implements a set of Basic Intervals, sorted by start number.
   * This version uses container-mapping as a function in the comparator.
   */
  static class IncreasingStartMappedIntervalSet extends IntervalSet {
    /** Support for Set serialization */
    static final long serialVersionUID = -975667667343524421L;

    private static class StartComparator implements Comparator<BasicInterval> {
      @Override
      public int compare(BasicInterval b1, BasicInterval b2) {
        int result = b1.getBegin() - b2.getBegin();
        if (result == 0) {
          result = b1.getEnd() - b2.getEnd();
        }
        if (result == 0) {
          if (b1 instanceof MappedBasicInterval) {
            if (b2 instanceof MappedBasicInterval) {
              MappedBasicInterval mb1 = (MappedBasicInterval) b1;
              MappedBasicInterval mb2 = (MappedBasicInterval) b2;
              return mb1.container.getRegister().number - mb2.container.getRegister().number;
            }
          }
        }
        return result;
      }
    }

    static final StartComparator c = new StartComparator();

    IncreasingStartMappedIntervalSet() {
      super(c);
    }
  }

  /**
   * Implements a set of Basic Intervals, sorted by end number.
   * This version uses container-mapping as a function in the comparator.
   */
  static class IncreasingEndMappedIntervalSet extends IntervalSet {
    /** Support for Set serialization */
    static final long serialVersionUID = -3121737650157210290L;

    private static class EndComparator implements Comparator<BasicInterval> {
      @Override
      public int compare(BasicInterval b1, BasicInterval b2) {
        int result = b1.getEnd() - b2.getEnd();
        if (result == 0) {
          result = b1.getBegin() - b2.getBegin();
        }
        if (result == 0) {
          if (b1 instanceof MappedBasicInterval) {
            if (b2 instanceof MappedBasicInterval) {
              MappedBasicInterval mb1 = (MappedBasicInterval) b1;
              MappedBasicInterval mb2 = (MappedBasicInterval) b2;
              return mb1.container.getRegister().number - mb2.container.getRegister().number;
            }
          }
        }
        return result;
      }
    }

    static final EndComparator c = new EndComparator();

    IncreasingEndMappedIntervalSet() {
      super(c);
    }
  }
}
