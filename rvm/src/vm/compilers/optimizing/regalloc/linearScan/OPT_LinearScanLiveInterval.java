/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Implements a live interval, which is a triple
 *  (register, begin, end)
 *   register - the register we are describing
 *   begin    - the starting point of the interval
 *   end      - the ending point of the interval
 *
 *   begin and end are numbers given to each instruction by a numbering pass
 *
 * @author Mauricio Serrano
 * @modified Stephen Fink
 */
final class OPT_LinearScanLiveInterval extends OPT_RegisterAllocatorState 
implements OPT_Operators, OPT_PhysicalRegisterConstants {
  
  private static final boolean debug = false;
  
  OPT_PhysicalRegisterSet phys;
  OPT_Register register;   // register for which this interval is for
  int dbeg;                // DFN begin
  int dend;                // DFN end
  
  // next live interval on the LiveAnalysis list
  private OPT_LinearScanLiveInterval nextLive;
  
  // if non-null, next/prev live interval
  private OPT_LinearScanLiveInterval nextInterval;
  private OPT_LinearScanLiveInterval prevInterval;

  // next/prev live interval on the "active" list
  OPT_LinearScanLiveInterval nextActive;
  OPT_LinearScanLiveInterval prevActive;
  
  // max number of intervals this live interval overlaps
  private int maxOverlap;
  
  // number of instructions this live interval spans 
  // based on the linearization of the BBs
  private int linearInstSpanned; 
  
  // governing IR
  OPT_IR ir;

  OPT_LinearScanLiveInterval(OPT_Register reg, int dBeg, int dEnd,
                             OPT_IR ir) {
    dbeg = dBeg;
    dend = dEnd;
    register = reg;
    this.ir = ir;
    phys = ir.regpool.getPhysicalRegisterSet();
  }
  
  // Methods associated with the "nextLive" field
  final OPT_LinearScanLiveInterval getNextLive()  {
    return nextLive; 
  }

  final void setNextLive(OPT_LinearScanLiveInterval live)  {
    nextLive = live; 
  }

  
  // Methods associated with the prev/next Interval
  OPT_LinearScanLiveInterval getNextInterval() {
    return nextInterval;
  }
  
  void setNextInterval(OPT_LinearScanLiveInterval interval) {
    nextInterval = interval;
  }
  
  OPT_LinearScanLiveInterval getPrevInterval() {
    return prevInterval;
  }

  //////////////////////////////////////////////////////////////////  
  // Active List Methods
  void setPrevInterval(OPT_LinearScanLiveInterval interval) {
    prevInterval = interval;
  }
  
  OPT_LinearScanLiveInterval getPrevActive() {
    return prevActive;
  }
  
  OPT_LinearScanLiveInterval getNextActive() {
    return nextActive;
  }
  
  void setPrevActive(OPT_LinearScanLiveInterval interval) {
    prevActive = interval;
  }

  void setNextActive(OPT_LinearScanLiveInterval interval) {
    nextActive = interval;
  }
  
  /**
   * Insert the passed interval after me
   *
   * @param interval the interval to append
   */
 void addAfterOnActiveList(OPT_LinearScanLiveInterval interval) {
    if (nextActive != null) {
      nextActive.prevActive = interval;
      interval.nextActive = nextActive;
    }
    interval.prevActive = this;
    nextActive = interval;
  }

  /**
   *  Remove the passed interval from the active list
   *  @param interval the interval to remove
   */
  void removeFromActiveList() {
    if (prevActive != null) {
      prevActive.nextActive = nextActive;
      if (nextActive != null) {
	nextActive.prevActive = prevActive;
      }
      prevActive = null;
      nextActive = null;
    }
  }
  //////////////////////////////////////////////////////////////////  

  // extend a live interval
  void extend(int dBeg, int dEnd) {
    if (dBeg < dbeg) dbeg = dBeg;
    if (dEnd > dend) dend = dEnd;
  }
  
  void extendEnd(int dEnd) {
    if (dEnd > dend) dend = dEnd;
  }

  /**
   * Does this interval start after dfn?
   * @param dfn the depth first numbering to compare to 
   */
  boolean startsAfter(int dfn) {
    return dbeg > dfn;
  }
  
  /**
   * Does this interval end after dfn
   * @param dfn the depth first numbering to compare to 
   */
  boolean endsAfter(int dfn) {
    return dend > dfn;
  }

  /**
   * Is this interval live at the starting point of the interval passed
   * @param v the interval to compare to
   */
  boolean overlapsStartPt(OPT_LinearScanLiveInterval v) {
    return (dend > v.dbeg);// && (dbeg <= v.dbeg);
  }
  
  /**
   * Is this interval represent a register that is simultaneously live
   * with another interval?
   * 
   * @param resurrect
   */
  boolean intersects(OPT_LinearScanLiveInterval resurrect) {
    if (debug) {
      System.out.println("checking intersects "+this+" with "+resurrect);
      System.out.println("live:");
      for (OPT_LinearScanLiveInterval l = this; l != null; l = l.nextInterval)
	System.out.println(l);
      System.out.println("ress:");
      for (OPT_LinearScanLiveInterval l = resurrect; l != null; 
           l = l.nextInterval)
	System.out.println(l+" ");
    }
    OPT_LinearScanLiveInterval Live = this, Resurrect = resurrect;
    OPT_LinearScanLiveInterval live = this;
    while (true) {
      if (resurrect.dbeg < live.dbeg) {
	if (resurrect.dend > live.dbeg) 
	  return true;
	//System.out.println("   "+live+" "+resurrect);
	resurrect = resurrect.nextInterval;
	if (resurrect == null)
	  break;
      } else if (resurrect.dbeg < live.dend) {
	return true;
      } else {
	live = live.nextInterval;
	if (live == null) 
	  break;
      }
    }
    return false;
    }
  /**
   * Merge this interval with another, non-intersecting interval.
   */
  void mergeWithNonIntersectingInterval(OPT_LinearScanLiveInterval resurrect) {
    // now merge the live and resurrect lists
    OPT_LinearScanLiveInterval Resurrect = resurrect;
    OPT_LinearScanLiveInterval live = this;
    do {
      OPT_LinearScanLiveInterval prev = null;
      do {
	if (resurrect.dbeg < live.dbeg)
	  break;
	prev = live;
	live = live.nextInterval;
      } while (live != null);
      if (prev == null)
	break;
      prev.nextInterval = resurrect;
      resurrect.prevInterval = prev;
      // switch resurrect and live
      OPT_LinearScanLiveInterval temp = resurrect;
      resurrect = live;
      live = temp;
    } while (resurrect != null);
    
    if (debug) {
      System.out.println("merge:");
      for (OPT_LinearScanLiveInterval l = Resurrect; l != null; 
           l = l.nextInterval)
	System.out.println(l+" ");
    }
  }
  
  boolean isVolatile() {
    return register.isVolatile();
  }
  
  /**
   * activateInterval: pick a register from the register pool and assign it
   * to this live interval.
   * Return true if no spill is necessary (typically, because a physical
   *  register was found)
   * Return false if no physical register was found, and the register 
   *  allocator must spill
   *
   * @param sm the register manager
   * @return whether a spill was needed to activate the register
   */
  boolean activateIntervalWithHoles(OPT_GenericStackManager sm) 
    throws OPT_OptimizingCompilerException {
    
    OPT_RegisterPreferences pref = sm.getPreferences();
    if (VM.VerifyAssertions) 
      VM.assert(!(register.isFloatingPoint() && register.isInteger()),
		"Symbolic register " + register + 
                " is both integer & float ???");
    
    OPT_Register Register = register;
    if (prevInterval == null) {
      // We're processing the first interval of the live range.
      // Try to allocate a physical register for this interval.
      OPT_Register r = sm.allocateRegister(Register, this);
      
      return (r != null);
    } else {
      // An earlier interval of this live range was already processed
      OPT_Register realReg  = getMapping(Register);
      if ( realReg == null )
	// The live range was spilled when processing the earlier interval
	// No additional spill is needed.
	return true;
      else {
	// The previous intervals of this live range were allocated to 
        // realReg ...
	if (realReg.mapsToRegister != null && 
            realReg.mapsToRegister != Register) {
	  // ... but realReg is now mapped to an interval for a different 
          // live range.
	  // We must spill either the current live range or 
          // the one assigned to realReg
	  // (see CMVC defect 156365 for a historical note).
	  return false;
	}
	else {
	  // ... in this case we can just assign realReg to the current interval
	  realReg.allocateRegister(Register);
	  OPT_RegisterAllocatorState.putPhysicalRegResurrectList(realReg, null);
	  return true;
	}
      }
    }
  }

  /**
   *
   */
  void replaceSymbolic(OPT_IR ir)  throws OPT_OptimizingCompilerException  {
    if (register.isAllocated()) {
      register.cloneTo(getMapping(register),ir);
    }
  }
  
  /**
   *
   */
  void freeIntervalWithHoles() 
    throws OPT_OptimizingCompilerException {
    OPT_Register Register = register;
    OPT_Register realReg  = getMapping(Register);
    OPT_LinearScanLiveInterval NextInterval = nextInterval;
    if (realReg == null) {
      if (NextInterval == null) {
	ir.stackManager.freeSpillLocation(Register);
      }
      return;
    }
    if (NextInterval == null) {
      if (debug) { System.out.println(dend+" "+Register+" free "+realReg); }
      realReg.freeRegister();
    } else { 
      // implicit hole interval will start
      // drop the register and put a resurrect time
      if (debug) {
	System.out.println(dend+" "+Register+" drop "+realReg+
                           " res:"+NextInterval);
      }
      realReg.deallocateRegister();

      // the register will resurrect at next interval
      OPT_RegisterAllocatorState.putPhysicalRegResurrectList(realReg, 
                                                             NextInterval); 
    }
  }
  
  /**
   *
   */
  void spill(OPT_GenericStackManager sm) {
    if (debug) { VM.sysWrite("\tspill register "+register+'\n'); }
    sm.getSpillLocation(register);
  }
  
  /**
   *
   */
  void spilltoNewLocation (OPT_GenericStackManager sm) 
    throws OPT_OptimizingCompilerException {
    if (debug)
      VM.sysWrite("\tspill register "+register+'\n');
    sm.getNewSpillLocation(register);
  }
  
  /**
   *  String-i-fy this live interval
   */
  public String toString() {
    String s = "[ "+dbeg + ", " + dend + " ] "+register;
    if (prevInterval != null) // hole interval
      s += " #";
    return s;
  }
  
  // JC (8/15) get and set methods for max overlap
  public int getMaxOverlap() {
    return maxOverlap;
  }
  
  public void setMaxOverlap (int i) {
    maxOverlap = i;
  }
  
  public int getInstSpanned() {
    return linearInstSpanned;
  }
  
  public void setInstSpanned (int i) {
    linearInstSpanned = i;
  }
  
}
