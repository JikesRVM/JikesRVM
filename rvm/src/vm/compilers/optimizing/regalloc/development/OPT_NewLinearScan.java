/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Main driver for linear scan register allocation.
 *
 * @author Mauricio Serrano
 * @author Michael Hind
 * @author Stephen Fink
 */
final class OPT_NewLinearScan extends OPT_CompilerPhase implements
OPT_PhysicalRegisterConstants, OPT_Operators {

  /**
   * Mark FMOVs that end a live range?
   */
  private final static boolean MUTATE_FMOV = true;

  /**
   * The live interval information, a set of Basic Intervals 
   * sorted by increasing start point
   */
  private IntervalSet intervals;

  /**
   * An object which manages spill location assignments.
   */
  private SpillLocationManager spillManager;

  /**
   * The governing IR
   */
  private OPT_IR ir;

  /**
   * debug flags
   */
  static private final boolean debug = false;
  static private final boolean verboseDebug = false;
  static private final boolean gcdebug = false;
  static private final boolean debugCoalesce = false;

  /**
   * Register allocation is required
   */
  final boolean shouldPerform(OPT_Options options) { 
    return true; 
  }

  final String getName() { 
    return "Linear Scan"; 
  }

  final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   *  Perform the linear scan register allocation algorithm
   *  See TOPLAS 21(5), Sept 1999, p 895-913
   *  @param ir the IR
   */
  void perform(OPT_IR ir) {
    this.ir = ir;

    // TODO: consider making a composite phase.  Can we share state
    // between elements of a composite phase??
    new IntervalAnalysis().perform(ir); 

    //  The registerManager has already been initialized
    OPT_GenericStackManager sm = ir.stackManager;

    // Set up register restrictions
    sm.computeRestrictions(ir);
    OPT_RegisterRestrictions restrict = sm.getRestrictions();

    // Create the object that manages spill locations
    spillManager = new SpillLocationManager(ir);

    // Create an (empty) set of active intervals.
    ActiveSet active = new ActiveSet(ir,spillManager);

    // Intervals sorted by increasing start point
    for (java.util.Iterator e = intervals.getIntervals(); e.hasNext(); ) {

      BasicInterval bi = (BasicInterval)e.next();

      active.expireOldIntervals(bi);

      // If the interval does not correspond to a physical register
      // then we process it.
      if (!bi.getRegister().isPhysical()) {
        // Update register allocation based on the new interval.
        active.allocate(bi);
      } 
      active.insert(bi);
    }

    // update GC maps.
    updateGCMaps(ir);

    replaceSymbolicRegisters(ir);

    // Generate spill code if necessary
    if (ir.hasSysCall() || active.spilledSomething()) {	
      sm.insertSpillCode();
    }

    // update GC maps again, to account for changes induced by spill code.
    updateGCMaps2(ir);
  }

  /**
   *  Iterate over the IR and replace each symbolic register with its
   *  allocated physical register.
   * 
   *  Side effect: update the fpStackHeight in MIRInfo
   */
  private void replaceSymbolicRegisters(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (Enumeration b = ir.getBasicBlocks(); b.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)b.nextElement();
      
      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (Enumeration inst = bb.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)inst.nextElement();
        for (Enumeration e2 = s.getOperands(); e2.hasMoreElements(); ) {
          OPT_Operand op = (OPT_Operand)e2.nextElement();
          if (op.isRegister()) {
            OPT_RegisterOperand rop = op.asRegister();
            OPT_Register r = rop.register;

            // if we see a physical FPR, update the MIR state.
            if (r.isPhysical() && r.isFloatingPoint() &&
		s.operator() != DUMMY_DEF && 
		s.operator() != DUMMY_USE) {
              int n = phys.getFPRIndex(r);
	      if (fpStackOffset != 0) {
		n += fpStackOffset;
		rop.register = phys.getFPR(n);
	      }
              ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight,
                                                  n+1);
            }

            if (r.isSymbolic() && !r.isSpilled()) {
              OPT_Register p = OPT_RegisterAllocatorState.getMapping(r);
              if (VM.VerifyAssertions) VM.assert(p!=null);
              // update MIR state if needed
              if (p.isFloatingPoint()) {
                int n = phys.getFPRIndex(p) + fpStackOffset;
                ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight,
                                                    n+1);
                p = phys.getFPR(n);
              }
              rop.register = p;
            }
          } else if (op instanceof OPT_BURSManagedFPROperand) {
	    int regNum = ((OPT_BURSManagedFPROperand)op).regNum;
	    s.replaceOperand(op, new OPT_RegisterOperand(phys.getFPR(regNum), 
							 VM_Type.DoubleType));
	  }
	}
        // account for any effect s has on the floating point stack
        // position.
        if (s.operator().isFpPop()) {
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
        }
        if (VM.VerifyAssertions) VM.assert(fpStackOffset >= 0);
      }
    }
  } 
  /**
   *  Iterate over the IR-based GC map collection and for each entry
   *  replace the symbolic reg with the real reg or spill it was allocated
   */
  private void updateGCMaps(OPT_IR ir) {

    for (OPT_GCIRMapEnumerator GCenum = ir.MIRInfo.gcIRMap.enumerator(); 
         GCenum.hasMoreElements(); ) {
      OPT_GCIRMapElement GCelement = GCenum.next();
      OPT_Instruction GCinst = GCelement.getInstruction();
      if (gcdebug) { 
        VM.sysWrite("GCelement " + GCelement);
      }

      for (OPT_RegSpillListEnumerator regEnum = 
           GCelement.regSpillListEnumerator();
           regEnum.hasMoreElements(); ) {
        OPT_RegSpillListElement elem = regEnum.next();
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

  /**
   * Update GC Maps again, to account for changes induced by spill code.
   */
  private void updateGCMaps2(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_ScratchMap scratchMap = ir.stackManager.getScratchMap();

    if (gcdebug) {
      System.out.println("SCRATCH MAP:\n" + scratchMap);
    }
    if (scratchMap.isEmpty()) return;

    // Walk over each instruction that has a GC point.
    for (OPT_GCIRMapEnumerator GCenum = ir.MIRInfo.gcIRMap.enumerator(); 
         GCenum.hasMoreElements(); ) {
      OPT_GCIRMapElement GCelement = GCenum.next();
      
      // new elements to add to the gc map
      java.util.HashSet newElements = new java.util.HashSet();

      OPT_Instruction GCinst = GCelement.getInstruction();

      // Get the linear-scan DFN for this instruction.
      int dfn = GCinst.scratch;

      if (gcdebug) { 
        VM.sysWrite("GCelement at " + dfn + " , " + GCelement);
      }

      // For each element in the GC Map ...
      for (OPT_RegSpillListEnumerator regEnum = 
           GCelement.regSpillListEnumerator();
           regEnum.hasMoreElements(); ) {
        OPT_RegSpillListElement elem = regEnum.next();
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
              VM.sysWrite("cached in to scratch register " + scratch + "\n");
            }
            // we will add a new element noting that the scratch register
            // also must be including in the GC map
            OPT_RegSpillListElement newElem = new OPT_RegSpillListElement(r);
            newElem.setRealReg(scratch);
            newElements.add(newElem);
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
      // add each new Element to the gc map
      for (java.util.Iterator i = newElements.iterator(); i.hasNext(); ) {
        OPT_RegSpillListElement newElem = (OPT_RegSpillListElement)i.next();
        GCelement.addRegSpillElement(newElem);
      }
    } 

  }

  /**
   *  Print the DFN numbers associated with each instruction
   */
  void printDfns() {
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
  int getDfnEnd(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
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
   * Return the Depth-first-number of the beginnin of the live interval.
   * Return the dfn for the beginning of the basic block if the interval is
   * open-ended.
   */
  int getDfnBegin(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
    OPT_Instruction begin = live.getBegin();
    int dfnBegin;
    if (begin != null) {
      dfnBegin = getDFN(begin);
    } else {
      dfnBegin = getDFN(bb.firstInstruction());
    }
    return dfnBegin;
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
   * Implements a basic live interval (no holes), which is a pair
   *   begin    - the starting point of the interval
   *   end      - the ending point of the interval
   *
   *   Begin and end are numbers given to each instruction by a numbering pass
   */
  final private class BasicInterval extends OPT_DoublyLinkedListElement {

    /**
     * DFN of the beginning instruction of this interval
     */
    private int begin;                    
    /**
     * DFN of the last instruction of this interval
     */
    private int end;                      
    /**
     * Compound interval which contains this interval
     */
    private CompoundInterval container;   

    /**
     * Default constructor.
     */
    BasicInterval(int begin, int end, CompoundInterval container)  {
      this.begin = begin;
      this.end = end;
      this.container = container;
    }

    /**
     * Create a copy of this interval in a different containing interval
     */
    BasicInterval copy(CompoundInterval container) {
      return new BasicInterval(begin,end,container);
    }

    /**
     * @return the DFN signifying the beginning of this basic interval
     */
    int getBegin() {
      return begin;
    }

    /**
     * @return the DFN signifying the end of this basic interval
     */
    int getEnd() {
      return end;
    }

    /**
     * @return the enclosing compound interval
     */
    CompoundInterval getContainer() {
      return container;
    }

    /**
     * Extend a live interval to include the interval [b,e]
     */
    void extend(int b, int e) {
      if (b < begin) begin = b;
      if (e > end) end = e;
    }

    /**
     * Extend a live interval to a new endpoint
     */
    void extendEnd(int newEnd) {
      if (newEnd > end) end = newEnd;
    }

    /**
     * Does this interval start after dfn?
     * @param dfn the depth first numbering to compare to 
     */
    boolean startsAfter(int dfn) {
      return begin > dfn;
    }

    /**
     * Does this interval start before dfn?
     * @param dfn the depth first numbering to compare to 
     */
    boolean startsBefore(int dfn) {
      return begin < dfn;
    }

    /**
     * Does this interval start before another ?
     * @param i the interval to compare with
     */
    boolean startsBefore(BasicInterval i) {
      return begin < i.begin;
    }

    /**
     * Does this interval represent the same range as another?
     * @param i the interval to compare with
     */
    boolean sameRange(BasicInterval i) {
      return begin == i.begin && end == i.end;
    }

    /**
     * Does this interval end before dfn
     * @param dfn the depth first numbering to compare to 
     */
    boolean endsBefore(int dfn) {
      return end < dfn;
    }

    /**
     * Does this interval end after dfn
     * @param dfn the depth first numbering to compare to 
     */
    boolean endsAfter(int dfn) {
      return end > dfn;
    }

    /**
     * Does this interval intersect with another?
     */
    boolean intersects(BasicInterval i) {
      int iBegin = i.getBegin();
      int iEnd = i.getEnd();
      return !(endsBefore(iBegin+1) || startsAfter(iEnd-1));
    }

    /**
     * Return the register this interval represents.
     */
    OPT_Register getRegister() {
      return container.getRegister();
    }

    /**
     * Has this interval been spilled?
     */
    boolean isSpilled() {
      return container.isSpilled();
    }

    /**
     * Get the physical register this interval is assigned to. null if
     * none assigned.
     */
    OPT_Register getAssignment() {
      return container.getAssignment();
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
   * Implements a live interval with holes; ie; a list of basic live
   * intervals.
   */
  class CompoundInterval {

    /**
     * A sorted linked list of basic intervals which together comprise this
     * compound interval.
     */
    private OPT_DoublyLinkedList basicIntervals = new OPT_DoublyLinkedList();

    /**
     * The register this compound interval represents
     */
    private OPT_Register reg;

    /**
     * A spill location assigned for this interval.
     */
    private SpillLocationInterval spillInterval;
    SpillLocationInterval getSpillInterval() { return spillInterval; }

    /**
     * Return the first basic interval comprising this compound interval.
     */
    private BasicInterval getFirstBasicInterval() {
      return (BasicInterval)basicIntervals.first();
    }

    /**
     * Return the last basic interval comprising this compound interval.
     */
    private BasicInterval getLastBasicInterval() {
      return (BasicInterval)basicIntervals.last();
    }

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
      BasicInterval newInterval = new BasicInterval(dfnBegin,dfnEnd,this);
      basicIntervals.append(newInterval);
      reg = register;
    }

    /**
     * Create a new compound interval of a single Basic interval
     */
    CompoundInterval(BasicInterval i, OPT_Register register) {
      BasicInterval newInterval = new BasicInterval(i.getBegin(),i.getEnd(),
                                                    this);
      basicIntervals.append(newInterval);
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
      BasicInterval i = (BasicInterval)basicIntervals.first();
      while (i != null) {
        result.basicIntervals.append(i.copy(result));
        i = (BasicInterval)i.getNext();
      }
      return result;
    }

    /**
     * Add a new basic interval to this compound interval.  Do not
     * concatentate or add to the master list of intervals.
     */
    void add(BasicInterval bi) {
      // create a new basic interval and append it to the list.
      basicIntervals.append(bi.copy(this));
    }
    /**
     * Add a new live range to this compound interval.
     *
     * @param live the new live range
     * @param bb the basic block for live
     */
    void addRange(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {

      if (shouldConcatenate(live,bb)) {
        // concatenate with the last basic interval
        getLastBasicInterval().extendEnd(getDfnEnd(live,bb)); 
      } else {
        // create a new basic interval and append it to the list.
        BasicInterval newInterval = new BasicInterval(getDfnBegin(live,bb),
                                                      getDfnEnd(live,bb),
                                                      this);
        basicIntervals.append(newInterval);
        // add the new interval to the master list of all intervals
        intervals.insert(newInterval);
      }
    }

    /**
     * Should we simply merge the live interval live into a previous 
     * BasicInterval?
     *
     * @param previous the previous linear scan interval in question
     * @param live the live interval being queried
     * @param bb the basic block in which live resides.
     */
    private boolean shouldConcatenate(OPT_LiveIntervalElement live,
                                      OPT_BasicBlock bb) {

      BasicInterval last = getLastBasicInterval();

      // Make sure the new live range starts after the last basic interval
      if (VM.VerifyAssertions) {
        VM.assert(last.getEnd() <= getDfnBegin(live,bb));
      }

      int dfnBegin = getDfnBegin(live, bb);
      if (live.getBegin() != null) {
        if (live.getBegin() == bb.firstRealInstruction()) {
          // live starts the basic block.  Now make sure it is contiguous
          // with the last interval.
          if (last.getEnd() + 1 < dfnBegin) {
            return false;
          } else {
            return true;
          }
        } else {
          // live does not start the basic block.  Merge with last iff
          // last and live share an instruction.  This happens when a
          // register is def'ed and use'd in the same instruction.
          if (last.getEnd() == dfnBegin) {
            return true;
          } else {
            return false;
          }
        }
      } else {
        // live.getBegin == null.  Conservatively merge with the previous
        // interval.
        return true;
      }
    }

    /**
     * Assign this compound interval to a free spill location.
     */
    void spill() {
      spillInterval = spillManager.findOrCreateSpillLocation(this);
      OPT_RegisterAllocatorState.setSpill(reg,spillInterval.getOffset());
      OPT_RegisterAllocatorState.clearOneToOne(reg);
      if (debug) {
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
     * TODO: clean up this interface some more.
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
     */
    void addNonIntersectingInterval(CompoundInterval i) {
      // Walk over the basic intervals of this interval and i.  Copy each
      // interval from i into this interval at the appropriate start point.
      BasicInterval current = (BasicInterval)basicIntervals.first();
      BasicInterval currentI = (BasicInterval)i.basicIntervals.first();

      while (currentI != null) {
        if (current != null) {
          if (currentI.getBegin() < current.getBegin()) {
            if (current == basicIntervals.first()) {
              basicIntervals.insert(currentI.copy(this));
            } else {
              current.insertBefore(currentI.copy(this));
            }
            currentI = (BasicInterval)currentI.getNext();
          } else {
            current = (BasicInterval)current.getNext();
          }
        } else {
          // we've reached the end of this interval.  append all remaining
          // intervals to the end.
          basicIntervals.append(currentI.copy(this));
          currentI = (BasicInterval)currentI.getNext();
        }
      }
    }

    /**
     * Remove some basic intervals from this compound interval.
     *
     * PRECONDITION: all basic intervals in i must appear in this compound
     * interval, unless they end after the end of this interval
     */
    void removeIntervals(CompoundInterval i) {
      BasicInterval current = (BasicInterval)basicIntervals.first();
      BasicInterval currentI = (BasicInterval)i.basicIntervals.first();

      while (currentI != null && current != null) {
        if (current.startsBefore(currentI)) {
          current = (BasicInterval)current.getNext();
        } else if (currentI.startsBefore(current)) {
          currentI = (BasicInterval)currentI.getNext();
        } else {
          if (VM.VerifyAssertions) VM.assert(current.sameRange(currentI));

          currentI = (BasicInterval)currentI.getNext();
          BasicInterval next = (BasicInterval)current.getNext();
          basicIntervals.remove(current);
          current = next;
        }
      }
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
      BasicInterval current = (BasicInterval)basicIntervals.first();
      BasicInterval currentI = (BasicInterval)i.basicIntervals.first();

      while (currentI != null && current != null) {
        if (current.startsBefore(currentI)) {
          current = (BasicInterval)current.getNext();
        } else if (currentI.startsBefore(current)) {
          currentI = (BasicInterval)currentI.getNext();
        } else {
          if (VM.VerifyAssertions) VM.assert(current.sameRange(currentI));

          currentI = (BasicInterval)currentI.getNext();
          BasicInterval next = (BasicInterval)current.getNext();
          // add the interval to the cache
          result.add(current);
          basicIntervals.remove(current);
          current = next;
        }
      }
      return result;
    }

    /**
     * Does this interval intersect with i?
     */
    boolean intersects(CompoundInterval i) {
      // Walk over the basic intervals of this interval and i.  Copy each
      // interval from i into this interval at the appropriate start point.
      BasicInterval current = (BasicInterval)basicIntervals.first();
      BasicInterval currentI = (BasicInterval)i.basicIntervals.first();

      while (current != null && currentI != null) {
        if (current.intersects(currentI)) return true;

        if (current.startsBefore(currentI)) {
          current = (BasicInterval)current.getNext();
        } else {
          currentI = (BasicInterval)currentI.getNext();
        }
      }

      return false;
    }

    /**
     * Make a String representation
     */
    public String toString() {
      String str = "[" + getRegister() + "]:";
      BasicInterval i =  (BasicInterval)basicIntervals.first();
      while (i != null) {
        str = str + i;
        i = (BasicInterval)i.getNext();
      }
      return str;
    }
  }

  /**
   * Implements a set of Basic Intervals, sorted by either start or end number.
   */
  class IntervalSet {

    private java.util.TreeSet sortedIntervals;

    private boolean sortByStart = true;

    private class EndComparator implements java.util.Comparator {
      public int compare(Object o1, Object o2) {
        BasicInterval b1 = (BasicInterval)o1;
        BasicInterval b2 = (BasicInterval)o2;
        int result = b1.getEnd() - b2.getEnd();
        if (result == 0) {
          result = b1.getBegin() - b2.getBegin();
        }
        if (result == 0) {
          result = b1.getRegister().getNumber() -
            b2.getRegister().getNumber();
        }
        return result;
      }
    }

    private class StartComparator implements java.util.Comparator {
      public int compare(Object o1, Object o2) {
        BasicInterval b1 = (BasicInterval)o1;
        BasicInterval b2 = (BasicInterval)o2;
        int result = b1.getBegin() - b2.getBegin();
        if (result == 0) {
          result = b1.getEnd() - b2.getEnd();
        }
        if (result == 0) {
          result = b1.getRegister().getNumber() -
            b2.getRegister().getNumber();
        }
        return result;
      }
    }

    /**
     * Create an interval set sorted by increasing start or end number
     */
    IntervalSet(boolean sortByStart) {
      this.sortByStart = sortByStart;
      if (sortByStart) {
        sortedIntervals = new java.util.TreeSet(new StartComparator());
      } else {
        sortedIntervals = new java.util.TreeSet(new EndComparator());
      }
    }

    /**
     * Return a linked list of intervals
     */
    java.util.Iterator getIntervals() {
      return sortedIntervals.iterator();
    }

    /**
     * Add a new interval to the list, maintaining sorted order.
     */
    void insert(BasicInterval b) {
      sortedIntervals.add(b);
    }

    /**
     * Return a String representation
     */
    public String toString() {
      String result = "";
      for (java.util.Iterator e = getIntervals(); e.hasNext();) {
        BasicInterval b = (BasicInterval)e.next();
        result = result + "(" + b.getRegister() + ")" + b + "\n";
      }
      return result;
    }
  }

  /**
   * "Active set" for linear scan register allocation.
   * This version is maintained sorted in order of increasing
   * live interval end point.
   */
  final class ActiveSet extends IntervalSet {

    /**
     * Attempt to coalesce to eliminate register moves?
     */
    final static boolean COALESCE_MOVES = true;

    /**
     * Governing ir
     */
    private OPT_IR ir;

    /**
     * Manager of spill locations;
     */
    private SpillLocationManager spillManager;

    /**
     * An object to help estimate spill costs
     */ 
    private OPT_SpillCostEstimator spillCost;

    /**
     * Have we spilled anything?
     */
    private boolean spilled;

    /**
     * Default constructor
     */
    ActiveSet(OPT_IR ir, SpillLocationManager sm) {
      super(false);
      spilled = false;
      this.ir = ir;
      this.spillManager = sm;

      if (ir.options.getOptLevel() >= 2) {
        if (ir.hasReachableExceptionHandlers()) {
          spillCost = new OPT_SimpleSpillCost(ir);
        } else {
          spillCost = new OPT_LoopDepthSpillCost(ir);
        }
      } else {
        switch (ir.options.SPILL_COST_ESTIMATE) {
          case OPT_Options.SIMPLE_SPILL_COST:
            spillCost = new OPT_SimpleSpillCost(ir);
            break;
          case OPT_Options.BRAINDEAD_SPILL_COST:
            spillCost = new OPT_BrainDeadSpillCost(ir);
            break;
          case OPT_Options.LOOPDEPTH_SPILL_COST:
            if (ir.hasReachableExceptionHandlers()) {
              spillCost = new OPT_SimpleSpillCost(ir);
            } else {
              spillCost = new OPT_LoopDepthSpillCost(ir);
            }
            break;
          default:
            OPT_OptimizingCompilerException.UNREACHABLE("unsupported spill cost");
        }
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

      for (java.util.Iterator e = getIntervals(); e.hasNext(); ) {
        BasicInterval bi = (BasicInterval)e.next();

        // break out of the loop when we reach an interval that is still
        // alive
        int newStart = newInterval.getBegin();
        if (bi.endsAfter(newStart)) break;

        if (debug) System.out.println("Expire " + bi);

        // note that the bi interval no longer is live 
        freeInterval(bi);	

        // remove bi from the active set
        e.remove();

      }
    }
    /**
     * Take action when a basic interval becomes inactive
     */
    void freeInterval(BasicInterval bi) {
      OPT_Register r = bi.getRegister(); 

      if (r.isPhysical()) return;

      if (bi.isSpilled()) {
        // free the spill location iff this is the last interval in the
        // compound interval.
        if (bi.getContainer().getLastBasicInterval() == bi) {
          spillManager.freeInterval(bi.getContainer().getSpillInterval());
        }
      } else {
        // free the assigned register
        bi.getAssignment().deallocateRegister();
      }

    }

    /**
     * Assign a basic interval to either a register or a spill location.
     */
    void allocate(BasicInterval newInterval) {

      if (debug) System.out.println("Allocate " + newInterval + " " +
                                    newInterval.getRegister());

      CompoundInterval container = newInterval.getContainer();
      OPT_Register r = newInterval.getRegister();

      if (container.isSpilled()) {
        // We previously decided to spill the compound interval.  No further
        // action is needed.
        if (debug) System.out.println("Previously spilled " + container);
        return;
      } else {
        if (container.isAssigned()) {
          // The compound interval was previously assigned to a physical
          // register.
          OPT_Register phys = container.getAssignment();
          if (!currentlyActive(phys)) {
            // The assignment of newInterval to phys is still OK.
            // Update the live ranges of phys to include the new basic
            // interval
            if (debug) System.out.println("Previously assigned to " + phys +
                                          " " + container + 
                                          " phys interval " +
                                          getInterval(phys));
            updatePhysicalInterval(phys,newInterval);
            if (debug) System.out.println(" now phys interval " + 
                                          getInterval(phys));
            return;
          } else {
            // The previous assignment is not OK, since the physical
            // register is now in use elsewhere.  We must undo the previous
            // decision and spill the compound interval.
            if (debug) System.out.println("Previously assigned, now spill " 
                                          + phys + " " + container);
            if (VM.VerifyAssertions) {
              OPT_RegisterRestrictions restrict = ir.stackManager.getRestrictions();
              VM.assert(!restrict.mustNotSpill(r));
            }
            container.spill();
            CompoundInterval physInterval = getInterval(phys);
            physInterval.removeIntervals(container);
            spilled=true;
          }
        } else {
          // This is the first attempt to allocate the compound interval.
          // Attempt to find a free physical register for this interval.
          OPT_Register phys = findAvailableRegister(container);
          if (phys != null) {
            // Found a free register.  Perfom the register assignment.
            container.assign(phys);
            if (debug) System.out.println("First allocation " 
                                          + phys + " " + container); 
            updatePhysicalInterval(phys,newInterval);       
            if (debug) System.out.println("  now phys" + getInterval(phys));
          } else {
            // Could not find a free physical register.  Some member of the
            // active set must be spilled.  Choose a spill candidate.
            CompoundInterval spillCandidate = getSpillCandidate(container);
            if (VM.VerifyAssertions) {
              VM.assert(!spillCandidate.isSpilled());
              VM.assert(spillCandidate.getRegister().getType() ==
                        r.getType());
              VM.assert(!ir.stackManager.getRestrictions().mustNotSpill
                        (spillCandidate.getRegister()));
              if (spillCandidate.getAssignment() != null) {
                VM.assert(!ir.stackManager.getRestrictions().
                          isForbidden(r,spillCandidate.getAssignment()));
              }
            }
            if (spillCandidate != container) {
              // spill a previously allocated interval.
              phys = spillCandidate.getAssignment();
              spillCandidate.spill();
              spilled=true;
              if (debug) System.out.println("Spilled " + spillCandidate +
                                            " from " + phys);
              CompoundInterval physInterval = getInterval(phys);
              if (debug) System.out.println(" assigned " 
                                            + phys + " to " + container);
              physInterval.removeIntervals(spillCandidate);
              if (debug) System.out.println("  after spill phys" + getInterval(phys));
              updatePhysicalInterval(phys,newInterval);       
              if (debug) System.out.println(" now phys interval " + 
                                            getInterval(phys));
              container.assign(phys);
            } else {
              // spill the new interval.
              if (debug) System.out.println("spilled " + container);
              container.spill();
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
        if (VM.VerifyAssertions) VM.assert(!ci.intersects(physInterval));
        physInterval.addNonIntersectingInterval(ci);
      }
    }
    /**
     * Is a particular physical register currently allocated to an
     * interval in the active set?
     */
    final boolean currentlyActive(OPT_Register r) {
      for (java.util.Iterator e = getIntervals(); e.hasNext(); ) {
        BasicInterval i = (BasicInterval)e.next();
        if (OPT_RegisterAllocatorState.getMapping(i.getRegister()) == r) {
          return true;
        }
      }
      return false;
    }

    /**
     * try to find a free physical register to allocate to the new
     * interval.  if no free physical register is found, return null;
     */
    final OPT_Register findAvailableRegister(CompoundInterval newInterval) {

      OPT_Register r = newInterval.getRegister();

      // first attempt to allocate to the preferred register
      if (COALESCE_MOVES) {
        OPT_Register p = getPhysicalPreference(newInterval);
        if (p != null) {
          if (debugCoalesce) {
            System.out.println("REGISTER PREFERENCE " + newInterval + " "
                               + p);
          }
          return p;
        }
      }

      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = phys.getPhysicalRegisterType(r);

      // next attempt to allocate to a volatile
      for (Enumeration e = phys.enumerateVolatiles(type); 
           e.hasMoreElements(); ) {
        OPT_Register p = (OPT_Register)e.nextElement();
        if (allocateToPhysical(newInterval,p)) {
          return p;
        }
      }

      // next attempt to allocate to a Nonvolatile.  we allocate the
      // novolatiles backwards.
      for (Enumeration e = phys.enumerateNonvolatilesBackwards(type);
           e.hasMoreElements(); ) {
        OPT_Register p = (OPT_Register)e.nextElement();
        if (allocateToPhysical(newInterval,p)) {
          return p;
        }
      }

      //-#if RVM_FOR_IA32
      // Try to allocate EBP
      if (type == INT_REG) {
        OPT_Register EBP = phys.getEBP();
        if (allocateToPhysical(newInterval,EBP)) {
          return EBP;
        }
      }
      //-#endif

      // no allocation succeeded.
      return null;
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
      java.util.HashMap map = new java.util.HashMap();
      OPT_Register r = ci.getRegister();

      OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      OPT_SpaceEffGraphNode node = graph.findNode(r);

      // Return null if no affinities.
      if (node == null) return null;
      
      // walk through all in edges of the node, searching for affinity
      for (Enumeration in = node.inEdges(); in.hasMoreElements(); ) {
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
            Integer oldW = (Integer)map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor,new Integer(w));
            } else {
              map.put(neighbor,new Integer(oldW.intValue() + w));
            }
            break;
          }
        }
      }
      // walk through all out edges of the node, searching for affinity
      for (Enumeration in = node.outEdges(); in.hasMoreElements(); ) {
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
            Integer oldW = (Integer)map.get(neighbor);
            if (oldW == null) {
              map.put(neighbor,new Integer(w));
            } else {
              map.put(neighbor,new Integer(oldW.intValue() + w));
            }
            break;
          }
        }
      }
      // OK, now find the highest preference. 
      OPT_Register result = null;
      int weight = -1;
      for (java.util.Iterator i = map.entrySet().iterator(); i.hasNext(); ) {
        java.util.Map.Entry entry = (java.util.Map.Entry)i.next();
        int w = ((Integer)entry.getValue()).intValue();
        if (w > weight) {
          weight = w;
          result = (OPT_Register)entry.getKey();
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
     * choose one of the active intervals or the newInterval to spill.
     */
    private CompoundInterval getSpillCandidate(CompoundInterval newInterval) {
      if (verboseDebug) System.out.println("GetSpillCandidate from " + this);
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
      for (java.util.Iterator e = getIntervals(); e.hasNext(); ) {
        BasicInterval b = (BasicInterval)e.next();
        CompoundInterval i = b.getContainer();
        OPT_Register newR = i.getRegister();
        if (verboseDebug) {
          if (i.isSpilled())  {
            System.out.println(" not candidate, already spilled: " + newR);
          }
          if (r.getType() != newR.getType()) {
            System.out.println(" not candidate, type mismatch : " +
                               r.getType() + " " + newR + " " +
                               newR.getType());
          }
          if (restrict.mustNotSpill(newR)) {
            System.out.println(" not candidate, must not spill: " + newR);
          }
        }
        if (!newR.isPhysical() && !i.isSpilled() && 
            r.getType()== newR.getType() &&
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
        VM.assert (result != null);
      }
      return result;
    }

    /**
     * Check whether, if we spilled interval spill, we could then assign
     * interval i to physical register spill.getRegister().  
     *
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
      rI.addNonIntersectingInterval(cache);

      return result;
    }
  }

  /**
   * phase to compute linear scan intervals.
   */
  final class IntervalAnalysis extends OPT_CompilerPhase {
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
     * should we perform this phase? yes.
     */
    final boolean shouldPerform(OPT_Options options) { return true; }

    /**
     * a name for this phase.
     */
    final String getName() { return "linear scan interval analysis"; }

    /**
     * should we print the ir?
     */
    final boolean printingEnabled(OPT_Options options, boolean before) {
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
    void perform(OPT_IR ir) {
      this.ir = ir;
      OPT_ControlFlowGraph cfg = ir.cfg;
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      intervals = new IntervalSet(true);

      // create topological list and a reverse topological list
      // the results are on listOfBlocks and reverseTopFirst lists
      createTopAndReverseList(cfg);

      // give dfn values to each instruction
      assignDepthFirstNumbers(cfg);

      // initialize registers 
      initializeRegisters();

      // the set of compound intervals active on entry/exit to a basic block;
      java.util.HashSet activeIn  = new java.util.HashSet(10);
      java.util.HashSet activeOut  = new java.util.HashSet(10);

      // visit each basic block in the listOfBlocks list
      for (OPT_BasicBlock bb = listOfBlocks; bb !=null; 
           bb=(OPT_BasicBlock)bb.nextSorted) {

        // visit each live interval for this basic block
        for (OPT_LiveIntervalElement live = bb.getFirstLiveIntervalElement();
             live != null; 
             live = live.getNext()) {

          // skip registers which are not allocated.
          if (live.getRegister().isPhysical() &&
              !phys.isAllocatable(live.getRegister())) continue;

          CompoundInterval resultingInterval = processLiveInterval(live, 
                                                                   bb);
          if (live.getEnd() == null) { 
            // the live interval is still alive at the end of this basic
            // block. insert at the end of the active list
            activeOut.add(resultingInterval);
          } 
        } 

        // done with the basic block, set activeIn = activeOut
        activeIn = activeOut;
        activeOut = new java.util.HashSet(10);
      }

      // debug support
      if (debug) {
        VM.sysWrite("**** start of interval dump "+ir.method+" ****\n");
        VM.sysWrite(intervals.toString());
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
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

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

      if (debug) {  printDfns();  }
    }

    /**
     * Initialize the interval for each register to null.
     */
    private void initializeRegisters() {
      for (OPT_Register reg = ir.regpool.getFirstRegister(); 
           reg != null;
           reg = reg.getNext()) {
        setInterval(reg, null); 
        OPT_RegisterAllocatorState.setSpill(reg,0);
        // clear the 'long' type if it's persisted to here.
        if (reg.isLong()) {
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
     * @return the resulting CompoundInterval
     */
    private CompoundInterval processLiveInterval(OPT_LiveIntervalElement live, 
                                                 OPT_BasicBlock bb) {

      // get the reg and (adjusted) begin, end pair for this interval
      OPT_Register reg = live.getRegister();
      int dfnend = getDfnEnd(live, bb);
      int dfnbegin = getDfnBegin(live, bb);

      // mutate FMOVs that end live ranges
      if (MUTATE_FMOV) {
        if (reg.isFloatingPoint()) {
          OPT_Instruction end = live.getEnd();
          if (end != null && end.operator == IA32_FMOV) {
            if (dfnend == dfnbegin) {
              // if end, an FMOV, both begins and ends the live range,
              // then end is dead.  Change it to a NOP. 
              Empty.mutate(end,NOP);
            } else {
              if (VM.VerifyAssertions) {		      
                OPT_Operand value = MIR_Move.getValue(end);
                VM.assert(value.isRegister());
                VM.assert(MIR_Move.getValue(end).asRegister().register 
                            == reg);
              }
              end.operator = IA32_FMOV_ENDING_LIVE_RANGE;
            }
          }
        }
      }

      // check for an existing live interval for this register
      CompoundInterval existingInterval = getInterval(reg);
      if (existingInterval == null) {
        // create a new live interval
        CompoundInterval newInterval = new CompoundInterval(dfnbegin,
                                                            dfnend,reg);
        if (debug) System.out.println("created a new interval " + newInterval);

        // associate the interval with the register 
        OPT_NewLinearScan.setInterval(reg, newInterval);

        // add the new interval to the sorted set of intervals.  
        intervals.insert(newInterval.getFirstBasicInterval());

        return newInterval;

      } else {
        // add the new live range to the existing interval
        existingInterval.addRange(live,bb);
        if (debug) System.out.println("Extended old interval " + existingInterval);

        return existingInterval;
      } 
    }
  }

  /**
   * The following class manages allocation and reuse of spill locations.
   */
  class SpillLocationManager implements OPT_PhysicalRegisterConstants {

    /**
     * Attempt to coalesce stack locations?
     */
    private final static boolean COALESCE_SPILLS = true;

    /**
     * The governing IR
     */
    private OPT_IR ir;

    /**
     * Set of spill locations which were previously allocated, but may be
     * free since the assigned register is no longer live.
     */
    java.util.HashSet freeIntervals = new java.util.HashSet();

    /**
     * Return a spill location that is valid to hold the contents of
     * compound interval ci. 
     */
    SpillLocationInterval findOrCreateSpillLocation(CompoundInterval ci) {
      SpillLocationInterval result = null;

      OPT_Register r = ci.getRegister();
      OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      int type = phys.getPhysicalRegisterType(r);
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
        for (java.util.Iterator i = freeIntervals.iterator(); i.hasNext(); ) {
          SpillLocationInterval s = (SpillLocationInterval)i.next();
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
      result.addNonIntersectingInterval(ci);

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
    SpillLocationManager (OPT_IR ir) {
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
      java.util.HashMap map = new java.util.HashMap();
      OPT_Register r = ci.getRegister();

      OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
      OPT_SpaceEffGraphNode node = graph.findNode(r);
      
      // Return null if no affinities.
      if (node == null) return null;

      // walk through all in edges of the node, searching for spill
      // location affinity
      for (Enumeration in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node src = (OPT_CoalesceGraph.Node)edge.from();
        OPT_Register neighbor = src.getRegister();
        if (neighbor.isSymbolic() && neighbor.isSpilled()) {
          int spillOffset = OPT_RegisterAllocatorState.getSpill(neighbor);
          // if this is a candidate interval, update its weight
          for (java.util.Iterator i = freeIntervals.iterator(); i.hasNext(); ) {
            SpillLocationInterval s = (SpillLocationInterval)i.next();
            if (s.getOffset() == spillOffset && 
                s.getSize() == spillSize && !s.intersects(ci)) {
              int w = edge.getWeight();
              Integer oldW = (Integer)map.get(s);
              if (oldW == null) {
                map.put(s,new Integer(w));
              } else {
                map.put(s,new Integer(oldW.intValue() + w));
              }
              break;
            }
          }
        }
      }
      
      // walk through all out edges of the node, searching for spill
      // location affinity
      for (Enumeration in = node.inEdges(); in.hasMoreElements(); ) {
        OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
        OPT_CoalesceGraph.Node dest = (OPT_CoalesceGraph.Node)edge.to();
        OPT_Register neighbor = dest.getRegister();
        if (neighbor.isSymbolic() && neighbor.isSpilled()) {
          int spillOffset = OPT_RegisterAllocatorState.getSpill(neighbor);
          // if this is a candidate interval, update its weight
          for (java.util.Iterator i = freeIntervals.iterator(); i.hasNext(); ) {
            SpillLocationInterval s = (SpillLocationInterval)i.next();
            if (s.getOffset() == spillOffset && 
                s.getSize() == spillSize && !s.intersects(ci)) {
              int w = edge.getWeight();
              Integer oldW = (Integer)map.get(s);
              if (oldW == null) {
                map.put(s,new Integer(w));
              } else {
                map.put(s,new Integer(oldW.intValue() + w));
              }
              break;
            }
          }
        }
      }

      // OK, now find the highest preference. 
      SpillLocationInterval result = null;
      int weight = -1;
      for (java.util.Iterator i = map.entrySet().iterator(); i.hasNext(); ) {
        java.util.Map.Entry entry = (java.util.Map.Entry)i.next();
        int w = ((Integer)entry.getValue()).intValue();
        if (w > weight) {
          weight = w;
          result = (SpillLocationInterval)entry.getKey();
        }
      }
      return result;
    }
  }

  /**
   * The following represents the intervals assigned to a particular spill
   * location
   */
  class SpillLocationInterval extends CompoundInterval {
    /**
     * The spill location, an offset from the frame pointer
     */
    private int frameOffset;
    int getOffset() { 
      return frameOffset;
    }
    /**
     * The size of the spill location, in bytes.
     */
    private int size;
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
  }
}
