/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterConstants;
import org.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;
import org.jikesrvm.ArchitectureSpecific.OPT_RegisterRestrictions;
import org.jikesrvm.ArchitectureSpecific.OPT_StackManager;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.OPT_GCIRMapElement;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_RegSpillListElement;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.osr.OSR_Constants;
import org.jikesrvm.osr.OSR_LocalRegPair;
import org.jikesrvm.osr.OSR_MethodVariables;
import org.jikesrvm.osr.OSR_VariableMapElement;
import org.vmmagic.unboxed.Word;

/**
 * Main driver for linear scan register allocation.
 *
 * @author Stephen Fink
 * @author Michael Hind
 * @author Mauricio Serrano
 */
public final class OPT_LinearScan extends OPT_OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  OPT_LinearScan() {
    super("Linear Scan Composite Phase", new OPT_OptimizationPlanElement[] {
          new OPT_OptimizationPlanAtomicElement(new IntervalAnalysis()),
          new OPT_OptimizationPlanAtomicElement(new RegisterRestrictions()),
          new OPT_OptimizationPlanAtomicElement(new LinearScan()),
          new OPT_OptimizationPlanAtomicElement(new UpdateGCMaps1()),
          new OPT_OptimizationPlanAtomicElement(new SpillCode()),
          new OPT_OptimizationPlanAtomicElement(new UpdateGCMaps2()),
          new OPT_OptimizationPlanAtomicElement(new UpdateOSRMaps()),
          });
  }

  /**
   * Mark FMOVs that end a live range?
   */
  private static final boolean MUTATE_FMOV = VM.BuildForIA32;

  /**
   * Attempt to coalesce to eliminate register moves?
   */
  static final boolean COALESCE_MOVES = true;

  /**
   * Attempt to coalesce stack locations?
   */
  private static final boolean COALESCE_SPILLS = true;

  /**
   * debug flags
   */
  private static final boolean debug = false;
  private static final boolean verboseDebug = false;
  private static final boolean gcdebug = false;
  private static final boolean debugCoalesce = false;

  /**
   * Register allocation is required
   */
  public boolean shouldPerform(OPT_Options options) { 
    return true; 
  }

  public String getName() { 
    return "Linear Scan Composite Phase"; 
  }

  public boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   *  Associates the passed live interval with the passed register, using
   *  the scratchObject field of OPT_Register.
   *
   *  @param reg the register
   *  @param interval the live interval
   */
  static void setInterval(OPT_Register reg, CompoundInterval interval) {
    reg.scratchObject = interval;
  }

  /**
   *  Returns the interval associated with the passed register.
   *  @param reg the register
   *  @return the live interval or null
   */
  static CompoundInterval getInterval(OPT_Register reg) {
    return (CompoundInterval)reg.scratchObject;
  }

  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  static int getDFN(OPT_Instruction inst) {
    return inst.scratch;
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  static void setDFN(OPT_Instruction inst, int dfn) {
    inst.scratch = dfn;
  }

  /**
   *  Print the DFN numbers associated with each instruction
   */
  static void printDfns(OPT_IR ir) {
    System.out.println("DFNS: **** " + ir.getMethod() + "****");
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
         inst != null;
         inst = inst.nextInstructionInCodeOrder()) {
      System.out.println(getDFN(inst) +" "+ inst);
    }
  }

  /**
   * Return the Depth-first-number of the end of the live interval.
   * Return the dfn for the end of the basic block if the interval is
   * open-ended.
   */
  static int getDfnEnd(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
    OPT_Instruction end  = live.getEnd();
    int dfnEnd;
    if (end != null) {
      dfnEnd = getDFN(end);
    } else {
      dfnEnd = getDFN(bb.lastInstruction());
    }
    return dfnEnd;
  }

  /**
   * Return the Depth-first-number of the beginning of the live interval.
   * Return the dfn for the beginning of the basic block if the interval is
   * open-ended.
   */
  static int getDfnBegin(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
    OPT_Instruction begin = live.getBegin();
    int dfnBegin;
    if (begin != null) {
      dfnBegin = getDFN(begin);
    } else {
      dfnBegin = getDFN(bb.firstInstruction());
    }
    return dfnBegin;
  }


  public static final class LinearScanState {
    /**
     * The live interval information, a set of Basic Intervals 
     * sorted by increasing start point
     */
    public final ArrayList<BasicInterval> intervals =
      new ArrayList<BasicInterval>();

    /**
     * Was any register spilled?
     */
    public boolean spilledSomething = false;

    /**
     * Analysis information used by linear scan.
     */
    public ActiveSet active;
  }

  /**
   * A phase to compute register restrictions.
   */
  static final class RegisterRestrictions extends OPT_CompilerPhase {

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public OPT_CompilerPhase newExecution(OPT_IR ir) {
      return this;
    }

    public boolean shouldPerform(OPT_Options options) { 
      return true; 
    }

    public String getName() { 
      return "Register Restrictions"; 
    }

    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    /**
     *  @param ir the IR
     */
    public void perform(OPT_IR ir) {
      
      //  The registerManager has already been initialized
      OPT_GenericStackManager sm = ir.stackManager;

      // Set up register restrictions
      sm.computeRestrictions(ir);
    }
  }

  static final class LinearScan extends OPT_CompilerPhase implements
    OPT_PhysicalRegisterConstants, OPT_Operators {

      /**
       * An object which manages spill location assignments.
       */
      private SpillLocationManager spillManager;

      /**
       * The governing IR
       * Also used by ClassWriter
       */
      public OPT_IR ir;

      /**
       * Constructor
       */
      public LinearScan() {}

      /**
       * Constructor for this compiler phase
       */
      private static final Constructor<OPT_CompilerPhase> constructor = getCompilerPhaseConstructor("org.jikesrvm.compilers.opt.OPT_LinearScan$LinearScan");

      /**
       * Get a constructor object for this compiler phase
       * @return compiler phase constructor
       */
      public Constructor<OPT_CompilerPhase> getClassConstructor() {
        return constructor;
      }

      /**
       * Register allocation is required
       */
      public boolean shouldPerform(OPT_Options options) { 
        return true; 
      }

      public String getName() { 
        return "Linear Scan"; 
      }

      public boolean printingEnabled(OPT_Options options, boolean before) {
        return false;
      }

      /**
       *  Perform the linear scan register allocation algorithm
       *  See TOPLAS 21(5), Sept 1999, p 895-913
       *  @param ir the IR
       */
      public void perform(OPT_IR ir) {

        this.ir = ir;

        //  The registerManager has already been initialized
        //OPT_GenericStackManager sm = ir.stackManager;

        // Get register restrictions
        // OPT_RegisterRestrictions restrict = sm.getRestrictions(); - unused

        // Create the object that manages spill locations
        spillManager = new SpillLocationManager(ir);

        // Create an (empty) set of active intervals.
        ActiveSet active = new ActiveSet(ir,spillManager);
        ir.MIRInfo.linearScanState.active = active;

        // Intervals sorted by increasing start point
        for (BasicInterval b : ir.MIRInfo.linearScanState.intervals) {

          MappedBasicInterval bi = (MappedBasicInterval)b;
          CompoundInterval ci = bi.container;

          active.expireOldIntervals(bi);

          // If the interval does not correspond to a physical register
          // then we process it.
          if (!ci.getRegister().isPhysical()) {
            // Update register allocation based on the new interval.
            active.allocate(bi,ci);
          } else {
            // Mark the physical register as currently allocated.
            ci.getRegister().allocateRegister();
          }
          active.add(bi);
        }

        // update the state.
        if (active.spilledSomething()) {
          ir.MIRInfo.linearScanState.spilledSomething = true;
        }
      }
  }

  /**
   * Implements a basic live interval (no holes), which is a pair
   *   begin    - the starting point of the interval
   *   end      - the ending point of the interval
   *
   *   Begin and end are numbers given to each instruction by a numbering pass
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

    /**
     * Default constructor.
     */
    BasicInterval(int begin, int end)  {
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
     * Extend a live interval to a new endpoint
     */
    final void setEnd(int newEnd) {
      end = newEnd;
    }

    /**
     * Does this interval start after dfn?
     * @param dfn the depth first numbering to compare to 
     */
    final boolean startsAfter(int dfn) {
      return begin > dfn;
    }

    /**
     * Does this interval start before dfn?
     * @param dfn the depth first numbering to compare to 
     */
    final boolean startsBefore(int dfn) {
      return begin < dfn;
    }

    /**
     * Does this interval contain a dfn?
     * @param dfn the depth first numbering to compare to 
     */
    final boolean contains(int dfn) {
      return begin <= dfn && end >= dfn;
    }

    /**
     * Does this interval start before another?
     * @param i the interval to compare with
     */
    final boolean startsBefore(BasicInterval i) {
      return begin < i.begin;
    }

    /**
     * Does this interval end after another?
     * @param i the interval to compare with
     */
    final boolean endsAfter(BasicInterval i) {
      return end > i.end;
    }

    /**
     * Does this interval represent the same range as another?
     * @param i the interval to compare with
     */
    final boolean sameRange(BasicInterval i) {
      return begin == i.begin && end == i.end;
    }

    /**
     * Redefine equals
     */
    public boolean equals(Object o) {
      if (!(o instanceof BasicInterval)) return false;

      BasicInterval i = (BasicInterval)o;
      return sameRange(i);
    }

    /**
     * Does this interval end before dfn
     * @param dfn the depth first numbering to compare to 
     */
    final boolean endsBefore(int dfn) {
      return end < dfn;
    }

    /**
     * Does this interval end after dfn
     * @param dfn the depth first numbering to compare to 
     */
    final boolean endsAfter(int dfn) {
      return end > dfn;
    }

    /**
     * Does this interval intersect with another?
     */
    final boolean intersects(BasicInterval i) {
      int iBegin = i.getBegin();
      int iEnd = i.getEnd();
      return !(endsBefore(iBegin+1) || startsAfter(iEnd-1));
    }

    /**
     * Return a String representation
     */
    public String toString() {
      String s = "[ "+ begin + ", " + end + " ] ";
      return s;
    }
  }

  /**
   * A basic interval contained in a CompoundInterval.
   */
  static class MappedBasicInterval extends BasicInterval {
    final CompoundInterval container;

    MappedBasicInterval(BasicInterval b, CompoundInterval c) {
      super(b.begin,b.end);
      this.container = c;
    }

    MappedBasicInterval(int begin, int end, CompoundInterval c) {
      super(begin,end);
      this.container = c;
    }

    /**
     * Redefine equals
     */
    public boolean equals(Object o) {
      if (super.equals(o)) {
        MappedBasicInterval i = (MappedBasicInterval)o;
        return container == i.container;
      } else {
        return false;
      }
    }

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
    private final OPT_Register reg;

    /**
     * A spill location assigned for this interval.
     */
    private SpillLocationInterval spillInterval;
    SpillLocationInterval getSpillInterval() { return spillInterval; }

    /**
     * Return the register this interval represents
     */
    OPT_Register getRegister() { 
      return reg;
    }

    /**
     * Create a new compound interval of a single Basic interval
     */
    CompoundInterval(int dfnBegin, int dfnEnd, OPT_Register register) {
      BasicInterval newInterval = new MappedBasicInterval(dfnBegin,dfnEnd,this);
      add(newInterval);
      reg = register;
    }

    /**
     * Create a new compound interval of a single Basic interval
     */
    CompoundInterval(BasicInterval i, OPT_Register register) {
      BasicInterval newInterval = new MappedBasicInterval(i.getBegin(),i.getEnd(),this);
      add(newInterval);
      reg = register;
    }

    /**
     * Dangerous constructor: use with care.
     */
    CompoundInterval(OPT_Register r) {
      reg = r;
    }

    /**
     * Copy the ranges into a new interval associated with a register r.
     */
    CompoundInterval copy(OPT_Register r) {
      CompoundInterval result = new CompoundInterval(r);

      for (Iterator<BasicInterval> i = iterator(); i.hasNext(); ) {
        BasicInterval b = i.next();
        result.add(b);
      }
      return result;
    }

    /**
     * Copy the ranges into a new interval associated with a register r.
     * Copy only the basic intervals up to and including stop.
     */
    CompoundInterval copy(OPT_Register r, BasicInterval stop) {
      CompoundInterval result = new CompoundInterval(r);

      for (Iterator<BasicInterval> i = iterator(); i.hasNext(); ) {
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
    BasicInterval addRange(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {

      if (shouldConcatenate(live,bb)) {
        // concatenate with the last basic interval
        BasicInterval last = last();
        last.setEnd(getDfnEnd(live,bb)); 
        return null;
      } else {
        // create a new basic interval and append it to the list.
        BasicInterval newInterval = new MappedBasicInterval(getDfnBegin(live,bb),
                                                      getDfnEnd(live,bb),this);
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
     */
    private boolean shouldConcatenate(OPT_LiveIntervalElement live,
                                      OPT_BasicBlock bb) {

      BasicInterval last = last();

      // Make sure the new live range starts after the last basic interval
      if (VM.VerifyAssertions) {
        VM._assert(last.getEnd() <= getDfnBegin(live,bb));
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
      OPT_RegisterAllocatorState.setSpill(reg,spillInterval.getOffset());
      OPT_RegisterAllocatorState.clearOneToOne(reg);
      if (verboseDebug) {
        System.out.println("Assigned " + reg + " to location " +
                           spillInterval.getOffset());
      }
    }

    /**
     * Has this interval been spilled?
     */
    boolean isSpilled() {
      return (OPT_RegisterAllocatorState.getSpill(getRegister()) != 0);
    }

    /**
     * Assign this compound interval to a physical register.
     */
    void assign(OPT_Register r) {
      getRegister().allocateToRegister(r);
    }

    /**
     * Has this interval been assigned to a physical register?
     */
    boolean isAssigned() {
      return (OPT_RegisterAllocatorState.getMapping(getRegister()) != null);
    }

    /**
     * Get the physical register this interval is assigned to. null if
     * none assigned.
     */
    OPT_Register getAssignment() {
      return OPT_RegisterAllocatorState.getMapping(getRegister());
    }

    /**
     * Merge this interval with another, non-intersecting interval.
     * Precondition: BasicInterval stop is an interval in i.  This version
     * will only merge basic intervals up to and including stop into this.
     */
    void addNonIntersectingInterval(CompoundInterval i, BasicInterval stop) {
      SortedSet<BasicInterval> headSet = i.headSetInclusive(stop);
      addAll(headSet);
    }

    /**
     * Compute the headSet() [from java.util.SortedSet] but include all
     * elements less than upperBound <em>inclusive</em>
     */
    SortedSet<BasicInterval> headSetInclusive(BasicInterval upperBound) {
      BasicInterval newUpperBound = new BasicInterval(upperBound.getBegin()+1,upperBound.getEnd());
      return headSet(newUpperBound);
    }

    /**
     * Compute the headSet() [from java.util.SortedSet] but include all
     * elements less than upperBound <em>inclusive</em>
     */
    SortedSet<BasicInterval> headSetInclusive(int upperBound) {
      BasicInterval newUpperBound = new BasicInterval(upperBound+1,upperBound+1);
      return headSet(newUpperBound);
    }

    /**
     * Compute the tailSet() [from java.util.SortedSet] but include all
     * elements greater than lowerBound <em>inclusive</em>
     */
    SortedSet<BasicInterval> tailSetInclusive(int lowerBound) {
      BasicInterval newLowerBound = new BasicInterval(lowerBound-1,lowerBound-1);
      return tailSet(newLowerBound);
    }

    /**
     * Remove some basic intervals from this compound interval, and return 
     * the intervals actually removed.
     *
     * PRECONDITION: all basic intervals in i must appear in this compound
     * interval, unless they end after the end of this interval
     */
    CompoundInterval removeIntervalsAndCache(CompoundInterval i) {
      CompoundInterval result = new CompoundInterval(i.getRegister());
      Iterator<BasicInterval> myIterator = iterator();
      Iterator<BasicInterval> otherIterator = i.iterator();
      BasicInterval current = myIterator.hasNext() ?
          myIterator.next() : null;
      BasicInterval currentI = otherIterator.hasNext() ?
          otherIterator.next() : null;

      while (currentI != null && current != null) {
        if (current.startsBefore(currentI)) {
          current = myIterator.hasNext() ?
              myIterator.next() : null;
        } else if (currentI.startsBefore(current)) {
          currentI = otherIterator.hasNext() ?
              otherIterator.next() : null;
        } else {
          if (VM.VerifyAssertions) VM._assert(current.sameRange(currentI));

          currentI = otherIterator.hasNext() ?
              otherIterator.next() : null;
          BasicInterval next = myIterator.hasNext() ?
              myIterator.next() : null;
          // add the interval to the cache
          result.add(current);
          current = next;
        }
      }

      // SJF: the loop as written is slightly more efficient than calling
      // removeAll().  However, for some reason, replacing the loop with
      // the call to removeAll breaks the compiler on OptTestHarness
      // -oc:O2 -method VM_Method replaceCharWithString -.  This is deeply
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
     */
    void removeAll(CompoundInterval c) {
      for (BasicInterval b : c) {
        remove(b);
      }
    }

    /**
     * Return the lowest DFN in this compound interval.
     */
    int getLowerBound() {
      BasicInterval b = first();
      return b.getBegin();
    }

    /**
     * Return the highest DFN in this compound interval.
     */
    int getUpperBound() {
      BasicInterval b = last();
      return b.getEnd();
    }

    /**
     * Does this interval intersect with i?
     */
    boolean intersects(CompoundInterval i) {

      if (isEmpty()) return false;
      if (i.isEmpty()) return false;
      
      // Walk over the basic intervals of this interval and i.  
      // Restrict the walking to intervals that might intersect.
      int lower = Math.max(getLowerBound(),i.getLowerBound());
      int upper = Math.min(getUpperBound(),i.getUpperBound());

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

      BasicInterval current = myIterator.hasNext() ?
          myIterator.next() : null;
      BasicInterval currentI = otherIterator.hasNext() ?
          otherIterator.next() : null;

      while (current != null && currentI != null) {
        if (current.getBegin() > upper) break;
        if (currentI.getBegin() > upper) break;
        if (current.intersects(currentI)) return true;

        if (current.startsBefore(currentI)) {
          current = myIterator.hasNext() ?
              myIterator.next() : null;
        } else {
          currentI = otherIterator.hasNext() ?
              otherIterator.next() : null;
        }
      }
      return false;
    }

    /**
     * Return the first basic interval that contains a given
     * instruction.
     *
     * If there is no such interval, return null;
     * @param s   The instruction in question
     */
    BasicInterval getBasicInterval(OPT_Instruction s) {
      return getBasicInterval(getDFN(s));
    }
    /**
     * Return the first basic interval that contains the given
     * instruction.
     *
     * If there is no such interval, return null;
     * @param n The DFN of the instruction in question
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

    /**
     * Make a String representation
     */
    public String toString() {
      String str = "[" + getRegister() + "]:";
      for (Iterator<BasicInterval> i = iterator(); i.hasNext(); ) {
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
    private final OPT_IR ir;

    /**
     * Manager of spill locations;
     */
    private final SpillLocationManager spillManager;

    /**
     * An object to help estimate spill costs
     */ 
    private final OPT_SpillCostEstimator spillCost;

    /**
     * Have we spilled anything?
     */
    private boolean spilled;

    /**
     * Default constructor
     */
    ActiveSet(OPT_IR ir, SpillLocationManager sm) {
      super();
      spilled = false;
      this.ir = ir;
      this.spillManager = sm;

      switch (ir.options.SPILL_COST_ESTIMATE) {
        case OPT_Options.SIMPLE_SPILL_COST:
          spillCost = new OPT_SimpleSpillCost(ir);
          break;
        case OPT_Options.BRAINDEAD_SPILL_COST:
          spillCost = new OPT_BrainDeadSpillCost(ir);
          break;
        case OPT_Options.BLOCK_COUNT_SPILL_COST:
          spillCost = new OPT_BlockCountSpillCost(ir);
          break;
        default:
          OPT_OptimizingCompilerException.UNREACHABLE("unsupported spill cost");
	  spillCost = null;
      }
    }

    /**
     * Have we spilled anything?
     */
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

      for (Iterator<BasicInterval> e = iterator(); e.hasNext(); ) {
        MappedBasicInterval bi = (MappedBasicInterval)e.next();

        // break out of the loop when we reach an interval that is still
        // alive
        int newStart = newInterval.getBegin();
        if (bi.endsAfter(newStart)) break;

        if (verboseDebug) System.out.println("Expire " + bi);

        // note that the bi interval no longer is live 
        freeInterval(bi);       

        // remove bi from the active set
        e.remove();

      }
    }
    /**
     * Take action when a basic interval becomes inactive
     */
    void freeInterval(MappedBasicInterval bi) {
      CompoundInterval container = bi.container;
      OPT_Register r = container.getRegister(); 

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

    /**
     * Assign a basic interval to either a register or a spill location.
     */
    void allocate(BasicInterval newInterval, CompoundInterval container) {

      if (debug) System.out.println("Allocate " + newInterval + " " +
                                           container.getRegister());

      OPT_Register r = container.getRegister();

      if (container.isSpilled()) {
        // We previously decided to spill the compound interval.  No further
        // action is needed.
        if (verboseDebug) System.out.println("Previously spilled " + container);
      } else {
        if (container.isAssigned()) {
          // The compound interval was previously assigned to a physical
          // register.
          OPT_Register phys = container.getAssignment();
          if (!currentlyActive(phys)) {
            // The assignment of newInterval to phys is still OK.
            // Update the live ranges of phys to include the new basic
            // interval
            if (verboseDebug) System.out.println("Previously assigned to " + phys +
                                                 " " + container + 
                                                 " phys interval " +
                                                 getInterval(phys));
            updatePhysicalInterval(phys,newInterval);
            if (verboseDebug) System.out.println(" now phys interval " + 
                                                 getInterval(phys));
            // Mark the physical register as currently allocated
            phys.allocateRegister();
          } else {
            // The previous assignment is not OK, since the physical
            // register is now in use elsewhere.  
            if (debug) System.out.println(
                                          "Previously assigned, " + phys + " " + container);
            // first look and see if there's another free register for
            // container. 
            if (verboseDebug) System.out.println( "Looking for free register");
            OPT_Register freeR = findAvailableRegister(container);
            if (verboseDebug) System.out.println( "Free register? " + freeR);

            if (freeR == null) {
              // Did not find a free register to assign.  So, spill one of
              // the two intervals concurrently allocated to phys.

              CompoundInterval currentAssignment = getCurrentInterval(phys); 
              // choose which of the two intervals to spill
              double costA = spillCost.getCost(container.getRegister());
              double costB = spillCost.getCost(currentAssignment.getRegister());
              if (verboseDebug) System.out.println( "Current assignment " +
                                                    currentAssignment + " cost "
                                                    + costB);
              if (verboseDebug) System.out.println( "Cost of spilling" +
                                                    container + " cost "
                                                    + costA);
              CompoundInterval toSpill = (costA < costB) ? container : 
                currentAssignment;
              // spill it.
              OPT_Register p = toSpill.getAssignment();
              toSpill.spill(spillManager);
              spilled=true;
              if (verboseDebug) System.out.println("Spilled " + toSpill+
                                                   " from " + p);
              CompoundInterval physInterval = getInterval(p);
              physInterval.removeAll(toSpill);
              if (verboseDebug) System.out.println("  after spill phys" + getInterval(p));
              if (toSpill != container) updatePhysicalInterval(p,newInterval);       
              if (verboseDebug) System.out.println(" now phys interval " + 
                                                   getInterval(p));
            } else {
              // found a free register for container! use it!
              if (debug) System.out.println("Switch container " 
                                            + container + "from " + phys + " to " + freeR); 
              CompoundInterval physInterval = getInterval(phys);
              if (debug) System.out.println("Before switch phys interval"
                                            + physInterval);
              physInterval.removeAll(container);
              if (debug) System.out.println("Intervals of " 
                                            + phys + " now " +
                                            physInterval); 

              container.assign(freeR);
              updatePhysicalInterval(freeR,container,newInterval);
              if (verboseDebug) System.out.println("Intervals of " 
                                                   + freeR + " now " +
                                                   getInterval(freeR)); 
              // mark the free register found as currently allocated.
              freeR.allocateRegister();
            }
          }
        } else {
          // This is the first attempt to allocate the compound interval.
          // Attempt to find a free physical register for this interval.
          OPT_Register phys = findAvailableRegister(r);
          if (phys != null) {
            // Found a free register.  Perfom the register assignment.
            container.assign(phys);
            if (debug) System.out.println("First allocation " 
                                          + phys + " " + container); 
            updatePhysicalInterval(phys,newInterval);       
            if (verboseDebug) System.out.println("  now phys" + getInterval(phys));
            // Mark the physical register as currently allocated.
            phys.allocateRegister();
          } else {
            // Could not find a free physical register.  Some member of the
            // active set must be spilled.  Choose a spill candidate.
            CompoundInterval spillCandidate = getSpillCandidate(container);
            if (VM.VerifyAssertions) {
              VM._assert(!spillCandidate.isSpilled());
              VM._assert((spillCandidate.getRegister().getType() == r.getType())
                || (spillCandidate.getRegister().isNatural() && r.isNatural()));
              VM._assert(!ir.stackManager.getRestrictions().mustNotSpill
                        (spillCandidate.getRegister()));
              if (spillCandidate.getAssignment() != null) {
                VM._assert(!ir.stackManager.getRestrictions().
                          isForbidden(r,spillCandidate.getAssignment()));
              }
            }
            if (spillCandidate != container) {
              // spill a previously allocated interval.
              phys = spillCandidate.getAssignment();
              spillCandidate.spill(spillManager);
              spilled=true;
              if (verboseDebug) System.out.println("Spilled " + spillCandidate +
                                                   " from " + phys);
              CompoundInterval physInterval = getInterval(phys);
              if (verboseDebug) System.out.println(" assigned " 
                                                   + phys + " to " + container);
              physInterval.removeAll(spillCandidate);
              if (verboseDebug) System.out.println("  after spill phys" + getInterval(phys));
              updatePhysicalInterval(phys,newInterval);       
              if (verboseDebug) System.out.println(" now phys interval " + 
                                                   getInterval(phys));
              container.assign(phys);
            } else {
              // spill the new interval.
              if (verboseDebug) System.out.println("spilled " + container);
              container.spill(spillManager);
              spilled=true;
            }
          }
        }
      }
    }
    /**
     * Update the interval representing the allocations of a physical
     * register p to include a new interval i
     */
    private void updatePhysicalInterval(OPT_Register p, BasicInterval i) { 
      // Get a representation of the intervals already assigned to p.
      CompoundInterval physInterval = getInterval(p);
      if (physInterval == null) {
        // no interval yet.  create one.
        setInterval(p,new CompoundInterval(i,p));
      } else {
        // incorporate i into the set of intervals assigned to p
        CompoundInterval ci = new CompoundInterval(i,p);
        if (VM.VerifyAssertions) VM._assert(!ci.intersects(physInterval));
        physInterval.addAll(ci);
      }
    }

    /**
     * Update the interval representing the allocations of a physical
     * register p to include a new compound interval c.  Include only
     * those basic intervals in c up to and including basic interval stop. 
     */
    private void updatePhysicalInterval(OPT_Register p, 
                                        CompoundInterval c, 
                                        BasicInterval stop) { 
      // Get a representation of the intervals already assigned to p.
      CompoundInterval physInterval = getInterval(p);
      if (physInterval == null) {
        // no interval yet.  create one.
        setInterval(p,c.copy(p,stop));
      } else {
        // incorporate c into the set of intervals assigned to p
        if (VM.VerifyAssertions) VM._assert(!c.intersects(physInterval));
        // copy to a new BasicInterval so "equals" will work as expected,
        // since "stop" may be a MappedBasicInterval.
        stop = new BasicInterval(stop.getBegin(),stop.getEnd());
        physInterval.addNonIntersectingInterval(c,stop);
      }
    }

    /**
     * Is a particular physical register currently allocated to an
     * interval in the active set?
     */
    boolean currentlyActive(OPT_Register r) {
      for (Iterator<BasicInterval> e = iterator(); e.hasNext(); ) {
        MappedBasicInterval i = (MappedBasicInterval)e.next();
        CompoundInterval container = i.container;
        if (OPT_RegisterAllocatorState.getMapping(container.getRegister()) == r) {
          return true;
        }
      }
      return false;
    }

    /**
     * Given that a physical register r is currently allocated to an
     * interval in the active set, return the interval.
     */
    CompoundInterval getCurrentInterval(OPT_Register r) {
      for (Iterator<BasicInterval> e = iterator(); e.hasNext(); ) {
        MappedBasicInterval i = (MappedBasicInterval)e.next();
        CompoundInterval container = i.container;
        if (OPT_RegisterAllocatorState.getMapping(container.getRegister()) == r) {
          return container;
        }
      }
      OPT_OptimizingCompilerException.UNREACHABLE("getCurrentInterval",
                                                  "Not Currently Active ",
                                                  r.toString());
      return null;
    }

    /**
     * try to find a free physical register to allocate to the compound
     * interval.  if no free physical register is found, return null;
     */
    OPT_Register findAvailableRegister(CompoundInterval ci) {

      if (ir.options.FREQ_FOCUS_EFFORT && ci.isInfrequent()) {
        // don't bother trying to find an available register
        return null;
      }

      OPT_Register r = ci.getRegister();
      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();

      // first attempt to allocate to the preferred register
      if (COALESCE_MOVES) {
        OPT_Register p = getPhysicalPreference(ci);
        if (p != null) {
          if (debugCoalesce) {
            System.out.println("REGISTER PREFERENCE " + ci + " " + p);
          }
          return p;
        }
      }

      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(r);

      // next attempt to allocate to a volatile
      if (!restrict.allVolatilesForbidden(r)) {
        for (Enumeration<OPT_Register> e = phys.enumerateVolatiles(type); 
             e.hasMoreElements(); ) {
          OPT_Register p = e.nextElement();
          if (allocateToPhysical(ci,p)) {
            return p;
          }
        }
      }

      // next attempt to allocate to a Nonvolatile.  we allocate the
      // novolatiles backwards.
      for (Enumeration<OPT_Register> e = phys.enumerateNonvolatilesBackwards(type);
           e.hasMoreElements(); ) {
        OPT_Register p = e.nextElement();
        if (allocateToPhysical(ci,p)) {
          return p;
        }
      }

      // no allocation succeeded.
      return null;
    }
    /**
     * Try to find a free physical register to allocate to a symbolic
     * register.
     */
    OPT_Register findAvailableRegister(OPT_Register symb) {

      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();

      // first attempt to allocate to the preferred register
      if (COALESCE_MOVES) {
        OPT_Register p = getPhysicalPreference(symb);
        if (p != null) {
          if (debugCoalesce) {
            System.out.println("REGISTER PREFERENCE " + symb + " " + p);
          }
          return p;
        }
      }

      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(symb);

      // next attempt to allocate to a volatile
      if (!restrict.allVolatilesForbidden(symb)) {
        for (Enumeration<OPT_Register> e = phys.enumerateVolatiles(type); 
             e.hasMoreElements(); ) {
          OPT_Register p = e.nextElement();
          if (allocateNewSymbolicToPhysical(symb,p)) {
            return p;
          }
        }
      }

      // next attempt to allocate to a Nonvolatile.  we allocate the
      // novolatiles backwards.
      for (Enumeration<OPT_Register> e = phys.enumerateNonvolatilesBackwards(type);
           e.hasMoreElements(); ) {
        OPT_Register p = e.nextElement();
        if (allocateNewSymbolicToPhysical(symb,p)) {
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
     * @return the preferred register.  null if no preference found.
     */
    private OPT_Register getPhysicalPreference(OPT_Register r) {
      // a mapping from OPT_Register to Integer
      // (physical register to weight);
      HashMap<OPT_Register,Integer> map =
        new HashMap<OPT_Register,Integer>();

      OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      OPT_SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for affinity
      for (Enumeration<OPT_GraphEdge> in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node src = (OPT_CoalesceGraph.Node)edge.from();
        OPT_Register neighbor = src.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateNewSymbolicToPhysical(r,neighbor)) {
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
      for (Enumeration<OPT_GraphEdge> in = node.outEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node dest = (OPT_CoalesceGraph.Node)edge.to();
        OPT_Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateNewSymbolicToPhysical(r,neighbor)) {
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
      OPT_Register result = null;
      int weight = -1;
      for (Map.Entry<OPT_Register, Integer> entry : map.entrySet()) {
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
     * @return the preferred register.  null if no preference found.
     */
    private OPT_Register getPhysicalPreference(CompoundInterval ci) {
      // a mapping from OPT_Register to Integer
      // (physical register to weight);
      HashMap<OPT_Register, Integer> map =
        new HashMap<OPT_Register, Integer>();
      OPT_Register r = ci.getRegister();

      OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      OPT_SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for affinity
      for (Enumeration<OPT_GraphEdge> in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node src = (OPT_CoalesceGraph.Node)edge.from();
        OPT_Register neighbor = src.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateToPhysical(ci,neighbor)) {
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
      for (Enumeration<OPT_GraphEdge> in = node.outEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node dest = (OPT_CoalesceGraph.Node)edge.to();
        OPT_Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic()) {
          // if r is assigned to a physical register r2, treat the
          // affinity as an affinity for r2
          OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
          if (r2 != null && r2.isPhysical()) {
            neighbor = r2;
          }
        }
        if (neighbor.isPhysical()) {
          // if this is a candidate interval, update its weight
          if (allocateToPhysical(ci,neighbor)) {
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
      OPT_Register result = null;
      int weight = -1;
      for (Map.Entry<OPT_Register, Integer> entry : map.entrySet()) {
        int w = entry.getValue();
        if (w > weight) {
          weight = w;
          result = entry.getKey();
        }
      }
      return result;
    }
    /**
     * Check whether it's ok to allocate an interval i to physical
     * register p.  If so, return true; If not, return false.
     */
    private boolean allocateToPhysical(CompoundInterval i, OPT_Register p) {
      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      OPT_Register r = i.getRegister();
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      if (p!=null && !phys.isAllocatable(p)) return false;

      if (verboseDebug && p!= null) {
        if (!p.isAvailable()) System.out.println("unavailable " + i + p);
        if (restrict.isForbidden(r,p)) System.out.println("forbidden" + i + p);
      }

      if ((p != null) && p.isAvailable() && !restrict.isForbidden(r,p)) {
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
     * Check whether it's ok to allocate symbolic register to a physical 
     * register p.  If so, return true; If not, return false.
     *
     * NOTE: This routine assumes we're processing the first interval of
     * register symb; so p.isAvailable() is the key information needed.
     */
    private boolean allocateNewSymbolicToPhysical(OPT_Register symb, OPT_Register p) {
      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      if (p!=null && !phys.isAllocatable(p)) return false;

      if (verboseDebug && p!= null) {
        if (!p.isAvailable()) System.out.println("unavailable " + symb + p);
        if (restrict.isForbidden(symb,p)) System.out.println("forbidden" + symb + p);
      }

      return (p != null) && p.isAvailable() && !restrict.isForbidden(symb, p);
    }


    /**
     * choose one of the active intervals or the newInterval to spill.
     */
    private CompoundInterval getSpillCandidate(CompoundInterval newInterval) {
      if (verboseDebug) System.out.println("GetSpillCandidate from " + this);

      if (ir.options.FREQ_FOCUS_EFFORT && newInterval.isInfrequent()) {
        // if it's legal to spill this infrequent interval, then just do so!
        // don't spend any more effort.
        OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
        if (!restrict.mustNotSpill(newInterval.getRegister())) {
          return newInterval;
        }
      }

      return spillMinUnitCost(newInterval);
    }

    /**
     * Choose the interval with the min unit cost (defined as the number
     * of defs and uses)
     */
    private CompoundInterval spillMinUnitCost(CompoundInterval newInterval) {
      if (verboseDebug) {
        System.out.println(" interval caused a spill: " + newInterval);
      }
      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      OPT_Register r = newInterval.getRegister();
      double minCost = spillCost.getCost(r);
      if (verboseDebug) {
        System.out.println(" spill cost: " + r + " " + minCost);
      }
      CompoundInterval result = newInterval;
      if (restrict.mustNotSpill(result.getRegister())) {
        if (verboseDebug) {
          System.out.println(" must not spill: " + result.getRegister());
        }
        result = null;
        minCost = Integer.MAX_VALUE;
      }
      for (Iterator<BasicInterval> e = iterator(); e.hasNext(); ) {
        MappedBasicInterval b = (MappedBasicInterval)e.next();
        CompoundInterval i = b.container;
        OPT_Register newR = i.getRegister();
        if (verboseDebug) {
          if (i.isSpilled())  {
            System.out.println(" not candidate, already spilled: " + newR);
          }
          if ((r.getType() != newR.getType()) || (r.isNatural() && newR.isNatural())) {
            System.out.println(" not candidate, type mismatch : " +
                               r.getType() + " " + newR + " " +
                               newR.getType());
          }
          if (restrict.mustNotSpill(newR)) {
            System.out.println(" not candidate, must not spill: " + newR);
          }
        }
        if (!newR.isPhysical() && !i.isSpilled() && 
            (r.getType()== newR.getType() || (r.isNatural() && newR.isNatural())) &&
            !restrict.mustNotSpill(newR)) {
          // Found a potential spill interval. Check if the assignment
          // works if we spill this interval.  
          if (checkAssignmentIfSpilled(newInterval,i)) {
            double iCost = spillCost.getCost(newR);
            if (verboseDebug) System.out.println(" potential candidate " +
                                                 i + " cost " + iCost);
            if (iCost < minCost) {
              if (verboseDebug) System.out.println(" best candidate so far" + i);
              minCost = iCost;
              result = i;
            }
          } else {
            if (verboseDebug) {
              System.out.println(" not a candidate, insufficient range: "
                                 + i);
            }
          }
        }
      }
      if (VM.VerifyAssertions) {
        VM._assert (result != null);
      }
      return result;
    }

    /**
     * Check whether, if we spilled interval spill, we could then assign
     * interval i to physical register spill.getRegister().  
     *
     * @return true if the allocation would fit.  false otherwise
     */
    private boolean checkAssignmentIfSpilled(CompoundInterval i,
                                             CompoundInterval spill) {
      OPT_Register r = spill.getAssignment();

      OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
      if (restrict.isForbidden(i.getRegister(),r)) return false;

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
     * Find the basic interval for register r containing instruction s.
     * If there are two such intervals, return the 1st one.
     * If there is none, return null.
     */
    BasicInterval getBasicInterval(OPT_Register r, OPT_Instruction s) {
      CompoundInterval c = getInterval(r);
      if (c == null) return null;
      return c.getBasicInterval(s);
    }

  } 

  /**
   * phase to compute linear scan intervals.
   */
  static final class IntervalAnalysis extends OPT_CompilerPhase implements
  OPT_Operators {
    /**
     * the governing ir
     */
    OPT_IR ir;

    /**
     * a list of basic blocks in topological order
     */
    private OPT_BasicBlock listOfBlocks;

    /**
     *  a reverse topological list of basic blocks
     */
    private OPT_BasicBlock reverseTopFirst;


    /**
     * Constructor
     */
    public IntervalAnalysis() {}
            
    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<OPT_CompilerPhase> constructor = getCompilerPhaseConstructor("org.jikesrvm.compilers.opt.OPT_LinearScan$IntervalAnalysis");

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<OPT_CompilerPhase> getClassConstructor() {
      return constructor;
    }

    /**
     * should we perform this phase? yes.
     */
    public boolean shouldPerform(OPT_Options options) { return true; }

    /**
     * a name for this phase.
     */
    public String getName() { return "Interval Analysis"; }

    /**
     * should we print the ir?
     */
    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    /**
     * compute live intervals for this ir
     * the result is a sorted (by beginning point) set of compound
     * intervals, stored in the private 'intervals' field.
     *
     * note: this implementation bashes the 'scratchobject' field on all
     * registers and the 'scratch' field on instructions.
     *
     * @param ir the ir
     */
    public void perform(OPT_IR ir) {
      this.ir = ir;

      OPT_ControlFlowGraph cfg = ir.cfg;
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      LinearScanState state = new LinearScanState();
      ir.MIRInfo.linearScanState = state;

      // create topological list and a reverse topological list
      // the results are on listOfBlocks and reverseTopFirst lists
      createTopAndReverseList(cfg);

      // give dfn values to each instruction
      assignDepthFirstNumbers(cfg);

      // initialize registers 
      initializeRegisters();

      int lastBeginSeen = -1;
      
      // visit each basic block in the listOfBlocks list
      for (OPT_BasicBlock bb = listOfBlocks; bb !=null; 
           bb=(OPT_BasicBlock)bb.nextSorted) {

        // visit each live interval for this basic block
        for (OPT_LiveIntervalElement live = bb.getFirstLiveIntervalElement();
             live != null; 
             live = live.getNext()) {

          // check that we process live intervals in order of increasing
          // begin.
          if (VM.VerifyAssertions) {
            int begin = getDfnBegin(live,bb);
            VM._assert(begin >= lastBeginSeen);
            lastBeginSeen = begin;
          }
          
          // skip registers which are not allocated.
          if (live.getRegister().isPhysical() &&
              !phys.isAllocatable(live.getRegister())) continue;

          CompoundInterval resultingInterval = processLiveInterval(live, bb);
          if (!bb.getInfrequent() && resultingInterval != null) {
            resultingInterval.setFrequent();
          }
        } 
      }

      // debug support
      if (verboseDebug) {
        VM.sysWrite("**** start of interval dump "+ir.method+" ****\n");
        VM.sysWrite(ir.MIRInfo.linearScanState.intervals.toString());
        VM.sysWrite("**** end   of interval dump ****\n");
      }
    }

    /**
     *  create topological list and a reverse topological list
     *  the results are on listOfBlocks and reverseTopFirst lists
     *  @param cfg the control flow graph
     */
    private void createTopAndReverseList(OPT_ControlFlowGraph cfg) {
      // dfs: create a list of nodes (basic blocks) in a topological order 
      cfg.clearDFS();
      listOfBlocks = cfg.entry();
      listOfBlocks.sortDFS();

      // this loop reverses the topological list by using the sortedPrev field
      reverseTopFirst = null;
      for (OPT_BasicBlock bb = listOfBlocks; 
           bb != null; 
           bb= (OPT_BasicBlock)bb.nextSorted) {

        // put back pointers in the "prev" field
        // set reverseTopFirst to be the more recent node we've seen, 
        // it will be the front of the list when we are done
        bb.sortedPrev = reverseTopFirst;
        reverseTopFirst = bb;
      }
    }    

    /**
     *  this method processes all basic blocks, do the following to each block
     *   1) add it to the begining of the "listOfBlocks" list
     *   2) number the instructions
     *   3) process the instructions that restrict physical register
     *   assignment
     *  @param cfg the control flow graph
     */
    void assignDepthFirstNumbers(OPT_ControlFlowGraph cfg) {

      int curDfn = ir.numberInstructions() - 1;

      listOfBlocks = null;
      for (OPT_BasicBlock bb = reverseTopFirst; 
           bb != null;
           bb = (OPT_BasicBlock)bb.sortedPrev) {

        // insert bb at the front of the list
        bb.nextSorted = listOfBlocks;
        listOfBlocks = bb;

        // number the instructions last to first
        for (OPT_Instruction inst = bb.lastInstruction(); 
             inst != null;
             inst = inst.getPrev()) {

          setDFN(inst, curDfn);

          curDfn--;
        }
      }

      if (debug) {  printDfns(ir);  }
    }

    /**
     * Initialize the interval for each register to null.
     */
    private void initializeRegisters() {
      for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); 
           reg != null;
           reg = reg.getNext()) {
        setInterval(reg, null); 
        OPT_RegisterAllocatorState.setSpill(reg,0);
        // clear the 'long' type if it's persisted to here.
        if (VM.BuildFor32Addr && reg.isLong()) {
          reg.clearType();
          reg.setInteger();
        }
      }
    }

    /**
     * for each live interval associated with this block
     * we either add a new interval, or extend a previous interval
     * if it is contiguous
     *
     * @param live the liveintervalelement for a basic block/reg pair
     * @param bb the basic block
     * @return the resulting CompoundInterval. null if the live interval
     * is not relevant to register allocation.
     */
    private CompoundInterval processLiveInterval(OPT_LiveIntervalElement live, 
                                                 OPT_BasicBlock bb) {

      // get the reg and (adjusted) begin, end pair for this interval
      OPT_Register reg = live.getRegister();
      int dfnend = getDfnEnd(live, bb);
      int dfnbegin = getDfnBegin(live, bb);

      if (MUTATE_FMOV && reg.isFloatingPoint()) {
        OPT_Operators.helper.mutateFMOVs(live, reg, dfnbegin, dfnend);
      }

      // check for an existing live interval for this register
      CompoundInterval existingInterval = getInterval(reg);
      if (existingInterval == null) {
        // create a new live interval
        CompoundInterval newInterval = new CompoundInterval(dfnbegin,
                                                            dfnend,reg);
        if (verboseDebug) System.out.println("created a new interval " + newInterval);

        // associate the interval with the register 
        setInterval(reg, newInterval);

        // add the new interval to the sorted set of intervals.  
        BasicInterval b = newInterval.first();
        ir.MIRInfo.linearScanState.intervals.add(b);

        return newInterval;

      } else {
        // add the new live range to the existing interval
        ArrayList<BasicInterval> intervals =
          ir.MIRInfo.linearScanState.intervals;
        BasicInterval added = existingInterval.addRange(live,bb);
        if (added != null) {
           intervals.add(added);
        }
        if (verboseDebug) System.out.println("Extended old interval " + reg); 
        if (verboseDebug) System.out.println(existingInterval);

        return existingInterval;
      } 
    }
  }

  /**
   * The following class manages allocation and reuse of spill locations.
   */
  static class SpillLocationManager implements OPT_PhysicalRegisterConstants {

    /**
     * The governing IR
     */
    private final OPT_IR ir;

    /**
     * Set of spill locations which were previously allocated, but may be
     * free since the assigned register is no longer live.
     */
    final HashSet<SpillLocationInterval> freeIntervals =
      new HashSet<SpillLocationInterval>();

    /**
     * Return a spill location that is valid to hold the contents of
     * compound interval ci. 
     */
    SpillLocationInterval findOrCreateSpillLocation(CompoundInterval ci) {
      SpillLocationInterval result = null;

      OPT_Register r = ci.getRegister();
      int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(r);
      if (type == -1) {
        type = DOUBLE_REG;
      }
      int spillSize = OPT_PhysicalRegisterSet.getSpillSize(type);

      // Search the free intervals and try to find an interval to
      // reuse. First look for the preferred interval.
      if (COALESCE_SPILLS) {
        result = getSpillPreference(ci,spillSize);
        if (result != null) {
          if (debugCoalesce) {
            System.out.println("SPILL PREFERENCE " + ci + " " + result);
          }
          freeIntervals.remove(result);
        }
      }

      // Now search for any free interval.
      if (result == null) {
        for (SpillLocationInterval s : freeIntervals) {
          if (s.getSize() == spillSize && !s.intersects(ci)) {
            result = s;
            freeIntervals.remove(result);
            break;
          }
        }
      }

      if (result == null) {
        // Could not find an interval to reuse.  Create a new interval.
        int location = ir.stackManager.allocateNewSpillLocation(type);
        result = new SpillLocationInterval(location,spillSize);
      }

      // Update the spill location interval to hold the new spill
      result.addAll(ci);

      return result;
    }

    /**
     * Record that a particular interval is potentially available for
     * reuse
     */
    void freeInterval(SpillLocationInterval i) {
      freeIntervals.add(i);
    }

    /**
     * Constructor.
     */
    SpillLocationManager(OPT_IR ir) {
      this.ir = ir;
    }

    /**
     * Given the current state of the register allocator, compute the
     * available spill location to which ci has the highest preference.
     *
     * @param ci the interval to spill
     * @param spillSize the size of spill location needed
     * @return the interval to spill to.  null if no preference found.
     */
    SpillLocationInterval getSpillPreference(CompoundInterval ci, 
                                             int spillSize) {
      // a mapping from SpillLocationInterval to Integer 
      // (spill location to weight);
      HashMap<SpillLocationInterval,Integer> map =
        new HashMap<SpillLocationInterval,Integer>();
      OPT_Register r = ci.getRegister();

      OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      OPT_SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for spill
      // location affinity
      for (Enumeration<OPT_GraphEdge> in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node src = (OPT_CoalesceGraph.Node)edge.from();
        OPT_Register neighbor = src.getRegister();
        if (neighbor.isSymbolic() && neighbor.isSpilled()) {
          int spillOffset = OPT_RegisterAllocatorState.getSpill(neighbor);
          // if this is a candidate interval, update its weight
          for (SpillLocationInterval s : freeIntervals) {
            if (s.getOffset() == spillOffset &&
                s.getSize() == spillSize && !s.intersects(ci)) {
              int w = edge.getWeight();
              Integer oldW = map.get(s);
              if (oldW == null) {
                map.put(s, w);
              } else {
                map.put(s, oldW + w);
              }
              break;
            }
          }
        }
      }

      // walk through all out edges of the node, searching for spill
      // location affinity
      for (Enumeration<OPT_GraphEdge> in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node dest = (OPT_CoalesceGraph.Node)edge.to();
        OPT_Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic() && neighbor.isSpilled()) {
          int spillOffset = OPT_RegisterAllocatorState.getSpill(neighbor);
          // if this is a candidate interval, update its weight
          for (SpillLocationInterval s : freeIntervals) {
            if (s.getOffset() == spillOffset &&
                s.getSize() == spillSize && !s.intersects(ci)) {
              int w = edge.getWeight();
              Integer oldW = map.get(s);
              if (oldW == null) {
                map.put(s, w);
              } else {
                map.put(s, oldW + w);
              }
              break;
            }
          }
        }
      }

      // OK, now find the highest preference. 
      SpillLocationInterval result = null;
      int weight = -1;
      for (Map.Entry<SpillLocationInterval,Integer> entry : map.entrySet()) {
        int w = entry.getValue();
        if (w > weight) {
          weight = w;
          result = entry.getKey();
        }
      }
      return result;
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

    public String toString() {
      return super.toString() + "<Offset:" + frameOffset + "," + size +
        ">"; 
    }

    /**
     * Redefine hash code for reproducibility.
     */
    public int hashCode() {
      BasicInterval first = first();
      BasicInterval last = last();
      return frameOffset + (first.getBegin()<<4) + (last.getEnd()<<12);
    }
  }

  /**
   * Implements a set of Basic Intervals, sorted by start number.
   * This version does NOT use container-mapping as a function in the comparator.
   */
  static class IncreasingStartIntervalSet extends IntervalSet {
    /** Support for Set serialization */
    static final long serialVersionUID = -7086728932911844728L;
    
    private static class StartComparator implements Comparator<BasicInterval> {
      public int compare(BasicInterval b1, BasicInterval b2) {
        int result = b1.getBegin() - b2.getBegin();
        if (result == 0) {
          result = b1.getEnd() - b2.getEnd();
        }
        return result;
      }
    }
    static final StartComparator c = new StartComparator();

    IncreasingStartIntervalSet() {
      super(c /*,true*/);
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
      public int compare(BasicInterval b1, BasicInterval b2) {
        int result = b1.getBegin() - b2.getBegin();
        if (result == 0) {
          result = b1.getEnd() - b2.getEnd();
        }
        if (result == 0) {
          if (b1 instanceof MappedBasicInterval) {
            if (b2 instanceof MappedBasicInterval) {
              MappedBasicInterval mb1 = (MappedBasicInterval)b1;
              MappedBasicInterval mb2 = (MappedBasicInterval)b2;
              return mb1.container.getRegister().number -
                mb2.container.getRegister().number;
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
      public int compare(BasicInterval b1, BasicInterval b2) {
        int result = b1.getEnd() - b2.getEnd();
        if (result == 0) {
          result = b1.getBegin() - b2.getBegin();
        }
        if (result == 0) {
          if (b1 instanceof MappedBasicInterval) {
            if (b2 instanceof MappedBasicInterval) {
              MappedBasicInterval mb1 = (MappedBasicInterval)b1;
              MappedBasicInterval mb2 = (MappedBasicInterval)b2;
              return mb1.container.getRegister().number -
                mb2.container.getRegister().number;
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

  abstract static class IntervalSet extends TreeSet<BasicInterval> {

    /**
     * Create an interval set sorted by increasing start or end number
     */
    IntervalSet(Comparator<BasicInterval> c) {
      super(c);
    }

    /**
     * Return a String representation
     */
    public String toString() {
      String result = "";
      for (BasicInterval b : this) {
        result = result + b + "\n";
      }
      return result;
    }
  }

  /**
   * Update GC maps after register allocation but before inserting spill
   * code.
   */
  static final class UpdateGCMaps1 extends OPT_CompilerPhase {

    public boolean shouldPerform(OPT_Options options) { 
      return true; 
    }

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public OPT_CompilerPhase newExecution(OPT_IR ir) {
      return this;
    }

    public String getName() { 
      return "Update GCMaps 1"; 
    }

    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    /**
     *  Iterate over the IR-based GC map collection and for each entry
     *  replace the symbolic reg with the real reg or spill it was allocated
     *  @param ir the IR
     */
    public void perform(OPT_IR ir) {

      for (OPT_GCIRMapElement GCelement : ir.MIRInfo.gcIRMap) {
        if (gcdebug) { 
          VM.sysWrite("GCelement " + GCelement);
        }

        for (OPT_RegSpillListElement elem : GCelement.regSpillList()) {
          OPT_Register symbolic = elem.getSymbolicReg();

          if (gcdebug) { 
            VM.sysWrite("get location for "+symbolic+'\n'); 
          }

          if (symbolic.isAllocated()) {
            OPT_Register ra = OPT_RegisterAllocatorState.getMapping(symbolic);
            elem.setRealReg(ra);
            if (gcdebug) {  VM.sysWrite(ra+"\n"); }

          } else if (symbolic.isSpilled()) {
            int spill = symbolic.getSpillAllocated();
            elem.setSpill(spill);
            if (gcdebug) {   VM.sysWrite(spill+"\n"); }
          } else {
            OPT_OptimizingCompilerException.UNREACHABLE( "LinearScan", 
                                                         "register not alive:", 
                                                         symbolic.toString());
          }
        } 
      } 
    }
  }
  /**
   * Update GC Maps again, to account for changes induced by spill code.
   */
  static final class UpdateGCMaps2 extends OPT_CompilerPhase {
    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public OPT_CompilerPhase newExecution(OPT_IR ir) {
      return this;
    }

    public boolean shouldPerform(OPT_Options options) { 
      return true; 
    }

    public String getName() { 
      return "Update GCMaps 2"; 
    }

    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    /**
     *  @param ir the IR
     */
    public void perform(OPT_IR ir) {
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      OPT_ScratchMap scratchMap = ir.stackManager.getScratchMap();

      if (gcdebug) {
        System.out.println("SCRATCH MAP:\n" + scratchMap);
      }
      if (scratchMap.isEmpty()) return;

      // Walk over each instruction that has a GC point.
      for (OPT_GCIRMapElement GCelement : ir.MIRInfo.gcIRMap) {
        // new elements to add to the gc map
        HashSet<OPT_RegSpillListElement> newElements = new HashSet<OPT_RegSpillListElement>();

        OPT_Instruction GCinst = GCelement.getInstruction();

        // Get the linear-scan DFN for this instruction.
        int dfn = GCinst.scratch;

        if (gcdebug) { 
          VM.sysWrite("GCelement at " + dfn + " , " + GCelement);
        }

        // a set of elements to delete from the GC Map
        HashSet<OPT_RegSpillListElement> toDelete =
          new HashSet<OPT_RegSpillListElement>(3);

        // For each element in the GC Map ...
        for (OPT_RegSpillListElement elem : GCelement.regSpillList()) {
          if (gcdebug) { 
            VM.sysWrite("Update " + elem + "\n");
          }
          if (elem.isSpill()) {
            // check if the spilled value currently is cached in a scratch
            // register     
            OPT_Register r = elem.getSymbolicReg();
            OPT_Register scratch = scratchMap.getScratch(r,dfn);
            if (scratch != null) {
              if (gcdebug) { 
                VM.sysWrite("cached in scratch register " + scratch + "\n");
              }
              // we will add a new element noting that the scratch register
              // also must be including in the GC map
              OPT_RegSpillListElement newElem = new OPT_RegSpillListElement(r);
              newElem.setRealReg(scratch);
              newElements.add(newElem);
              // if the scratch register is dirty, then delete the spill
              // location from the map, since it doesn't currently hold a
              // valid value
              if (scratchMap.isDirty(GCinst,r)) {
                toDelete.add(elem);
              }
            }
          } else {
            // check if the physical register is currently spilled.
            int n = elem.getRealRegNumber();
            OPT_Register r = phys.get(n); 
            if (scratchMap.isScratch(r,dfn)) {
              // The regalloc state knows where the physical register r is
              // spilled.
              if (gcdebug) { 
                VM.sysWrite("CHANGE to spill location " + 
                            OPT_RegisterAllocatorState.getSpill(r) + "\n"); 
              }
              elem.setSpill(OPT_RegisterAllocatorState.getSpill(r));
            }
          }

        } 
        // delete all obsolete elements
        for (OPT_RegSpillListElement deadElem : toDelete) {
          GCelement.deleteRegSpillElement(deadElem);
        }

        // add each new Element to the gc map
        for (OPT_RegSpillListElement newElem : newElements) {
          GCelement.addRegSpillElement(newElem);
        }
      } 
    }
  }
  /**
   * Insert Spill Code after register assignment.
   */
  static final class SpillCode extends OPT_CompilerPhase implements
    OPT_Operators{
    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public OPT_CompilerPhase newExecution(OPT_IR ir) {
      return this;
    }

    public boolean shouldPerform(OPT_Options options) { 
      return true; 
    }

    public String getName() { 
      return "Spill Code"; 
    }

    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    /**
     *  @param ir the IR
     */
    public void perform(OPT_IR ir) {
      replaceSymbolicRegisters(ir);

      // Generate spill code if necessary
      if (ir.hasSysCall() || ir.MIRInfo.linearScanState.spilledSomething) {     
        OPT_StackManager stackMan = (OPT_StackManager)ir.stackManager;
        stackMan.insertSpillCode(ir.MIRInfo.linearScanState.active);
        //      stackMan.insertSpillCode();
      }

      if (VM.BuildForIA32)
        OPT_Operators.helper.rewriteFPStack(ir);
    }

    /**
     *  Iterate over the IR and replace each symbolic register with its
     *  allocated physical register.
     *  Also used by ClassWriter
     */
    public static void replaceSymbolicRegisters(OPT_IR ir) {
      for (OPT_InstructionEnumeration inst = ir.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = inst.next();
        for (OPT_OperandEnumeration ops = s.getOperands(); 
             ops.hasMoreElements(); ) {
          OPT_Operand op = ops.next();
          if (op.isRegister()) {
            OPT_RegisterOperand rop = op.asRegister();
            OPT_Register r = rop.register;
            if (r.isSymbolic() && !r.isSpilled()) {
              OPT_Register p = OPT_RegisterAllocatorState.getMapping(r);
              if (VM.VerifyAssertions) VM._assert(p!=null);
              rop.register = p;
            }
          }
        }
      }
    } 

   }

  /**
   * Update GC maps after register allocation but before inserting spill
   * code.
   */
  static final class UpdateOSRMaps extends OPT_CompilerPhase {

    public boolean shouldPerform(OPT_Options options) { 
      return true; 
    }

    /**
     * Constructor
     */
    public UpdateOSRMaps() {}

    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<OPT_CompilerPhase> constructor = getCompilerPhaseConstructor("org.jikesrvm.compilers.opt.OPT_LinearScan$UpdateOSRMaps");

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<OPT_CompilerPhase> getClassConstructor() {
      return constructor;
    }

    public String getName() { 
      return "Update OSRMaps"; 
    }

    public boolean printingEnabled(OPT_Options options, boolean before) {
      return false;
    }

    private OPT_IR ir;
    /*
     * Iterate over the IR-based OSR map, and update symbolic registers
     * with real reg number or spill locations.
     * Verify there are only two types of operands:
     *    OPT_ConstantOperand
     *    OPT_RegisterOperand
     *        for integer constant, we save the value of the integer
     *
     * The LONG register has another half part.
     *
     * CodeSpill replaces any allocated symbolic register by 
     * physical registers. 
     */
    public void perform(OPT_IR ir)
      throws OPT_OptimizingCompilerException {
      this.ir = ir;

      // list of OsrVariableMapElement
      //LinkedList<OSR_VariableMapElement> mapList = ir.MIRInfo.osrVarMap.list;
      //for (int numOsrs=0, m=mapList.size(); numOsrs<m; numOsrs++) {
      //  OSR_VariableMapElement elm = mapList.get(numOsrs);
      /* for each osr instruction */
      for (OSR_VariableMapElement elm : ir.MIRInfo.osrVarMap.list) {

        // for each inlined method
        //LinkedList<OSR_MethodVariables> mvarsList = elm.mvars;                   XXX Remove once proven correct
        //for (int numMvars=0, n=mvarsList.size(); numMvars<n; numMvars++) {
        //  OSR_MethodVariables mvar = mvarsList.get(numMvars);
        for (OSR_MethodVariables mvar : elm.mvars) {

          // for each tuple
          //LinkedList<OSR_LocalRegPair> tupleList = mvar.tupleList;
          //for (int numTuple=0, k=tupleList.size(); numTuple<k; numTuple++) {
            //OSR_LocalRegPair tuple = tupleList.get(numTuple);
          for (OSR_LocalRegPair tuple : mvar.tupleList) {

            OPT_Operand op = tuple.operand;
            if (op.isRegister()) {
              OPT_Register sym_reg = ((OPT_RegisterOperand)op).register;

              setRealPosition(tuple, sym_reg);

              // get another half part of long register
              if (VM.BuildFor32Addr && (tuple.typeCode == OSR_Constants.LongTypeCode)) {

                OSR_LocalRegPair other = tuple._otherHalf;
                OPT_Operand other_op = other.operand;

                if (VM.VerifyAssertions) VM._assert(other_op.isRegister());

                OPT_Register other_reg = ((OPT_RegisterOperand)other_op).register;
                setRealPosition(other, other_reg);
              }
      /* According to OPT_ConvertToLowLevelIR, StringConstant, LongConstant,
       * NullConstant, FloatConstant, and DoubleConstant are all materialized
       * The only thing left is the integer constant.
       * POTENTIAL DRAWBACKS: since any long, float, and double are moved
       * to register and treated as use, it may consume more registers and
       * add unnecessary MOVEs.
       *
       * Perhaps, OPT_ConvertToLowLevelIR can skip OsrPoint instruction.
       */
            } else if (op.isIntConstant()) {
              setTupleValue(tuple,
                            OSR_Constants.ICONST,
                            ((OPT_IntConstantOperand)op).value
                            );
              if (VM.BuildFor32Addr && (tuple.typeCode == OSR_Constants.LongTypeCode)) {
                OSR_LocalRegPair other = tuple._otherHalf;
                OPT_Operand other_op = other.operand;

                if (VM.VerifyAssertions) VM._assert(other_op.isIntConstant());
                setTupleValue(other,
                              OSR_Constants.ICONST,
                              ((OPT_IntConstantOperand)other_op).value
                              );                              
              } 
            } else if (op.isAddressConstant()) {
              setTupleValue(tuple,
                            OSR_Constants.ACONST,
                            ((OPT_AddressConstantOperand)op).value.toWord()
                            );
            } else if (VM.BuildFor64Addr && op.isLongConstant()) {
              setTupleValue(tuple,
                            OSR_Constants.LCONST,
                            Word.fromLong(((OPT_LongConstantOperand)op).value)
                            );
            } else {
              throw new OPT_OptimizingCompilerException("OPT_LinearScan",
                        "Unexpected operand type at ", op.toString());
            } // for the op type
          } // for each tuple
        } // for each inlined method
      } // for each osr instruction

      this.ir = null;
    } // end of method
 
    void setRealPosition(OSR_LocalRegPair tuple, 
                                      OPT_Register sym_reg) {
      if (VM.VerifyAssertions) VM._assert(sym_reg != null);

      int REG_MASK = 0x01F;      

      // now it is not symbolic register anymore.
      // is is really confusing that sometimes a sym reg is a phy, 
      // and sometimes not.
      if (sym_reg.isAllocated()) {
        setTupleValue(tuple,
                      OSR_Constants.PHYREG,
                      sym_reg.number & REG_MASK
                      ); 
      } else if (sym_reg.isPhysical()) {
        setTupleValue(tuple,
                      OSR_Constants.PHYREG,
                      sym_reg.number & REG_MASK
                      );
      } else if (sym_reg.isSpilled()) {
        setTupleValue(tuple,
                      OSR_Constants.SPILL,
                      sym_reg.getSpillAllocated()
                      );
      } else {
        dumpIR(ir, "PANIC");
        throw new RuntimeException("OPT_LinearScan PANIC in OSRMAP, "
                                       +sym_reg+" is not alive");
      }
    } // end of setRealPosition

    static void setTupleValue(OSR_LocalRegPair tuple,
                                    int type,
                                    int value) {
      tuple.valueType = type;
      tuple.value     = Word.fromIntSignExtend(value);
    } // end of setTupleValue

    static void setTupleValue(OSR_LocalRegPair tuple,
                                    int type,
                                    Word value) {
      tuple.valueType = type;
      tuple.value     = value;
    } // end of setTupleValue
  } // end of inner class
}
