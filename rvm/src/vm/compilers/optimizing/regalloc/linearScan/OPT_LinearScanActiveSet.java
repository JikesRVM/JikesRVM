/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Random;

/**
 * "Active set" for linear scan register allocation.
 * This version is maintained sorted in order of increasing
 * live interval start point.
 *
 * @author Mauricio Serrano
 * @author Michael Hind
 * @modified Stephen Fink
 */
final class OPT_LinearScanActiveSet extends OPT_RegisterAllocatorState {
  
  /**
   * How many live intervals are there
   */
  private int count;			
  
  /**
   * first entry on a linked list of active live intervals sorted 
   * by increasing end point
   */
  OPT_LinearScanLiveInterval start;

  /**
   * last entry on a linked list of active live intervals sorted 
   * by increasing end point
   */
  OPT_LinearScanLiveInterval end;
  
  /**
   * Required to deal with register state
   */
  OPT_GenericStackManager stackManager;	

  /**
   * A structure holding register allocation preferences
   */
  OPT_RegisterPreferences pref;

  /**
   * Have we spilled any yet?
   */
  boolean spilled;		
  
  final static private boolean debug = false;

  /**
   * Constructor
   * @param sm the stack manager
   */
  OPT_LinearScanActiveSet (OPT_GenericStackManager sm) { 
    spilled = false; 
    stackManager = sm;
    pref = sm.getPreferences();
  }
  
  /**
   *  For each new interval, we scan the list of active intervals from 
   *  beginning to end.  We remove any "expired" intervals - those
   *  intervals that no longer overlap the new interval because their 
   *  end point precedes the new interval's start point - and makes the 
   *  corresponding register available for allocation
   *  @param newInterval the new interval
   */
  void expireOldIntervalsWithHoles(OPT_LinearScanLiveInterval newInterval) 
    throws OPT_OptimizingCompilerException {

    OPT_LinearScanLiveInterval j;
    for (j = start; 
	 j != null && !j.overlapsStartPt(newInterval);
	 j = j.nextActive) {
      
      // We don't free intervals that come from physical registers.  These
      // were inserted by the calling convention expansion code.
      if (!j.register.isPhysical()) {
	if (debug){ VM.sysWrite("ExpireOldIntervals: " + j+'\n');  }
	
	j.freeIntervalWithHoles();	
	--count;
      }
    }

    start = j;
    if (j != null) {
      j.prevActive = null;
    } else {
      end = null;
    }
  }
  
  /**
   * insert new interval into the active set 
   * @param newInterval the interval to insert
   */
  void insert(OPT_LinearScanLiveInterval newInterval) {
    if (debug) { VM.sysWrite("ActiveSet:insert: " + newInterval +"\n"); }
    OPT_LinearScanLiveInterval j, prev = null;
    for (j = start; j != null; j = j.nextActive)  {
      if (j.dend >= newInterval.dend) {
	break;
      }
      prev = j;
    }

    if (j == null) {
      // new interval should go at the end
      end = newInterval;
    }

    if (prev == null) {
      // insert at the beginning
      newInterval.nextActive = start;
      newInterval.prevActive = null;
      start = newInterval;
    } else {
      // insert in middle or end
      newInterval.nextActive = prev.nextActive;
      prev.nextActive = newInterval;
      newInterval.prevActive = prev;
      if (j != null) {
	j.prevActive = newInterval;
      }
    }
    if (debug) { VM.sysWrite(toString()+"\n"); } 

    ++count;
  }
  
  /**
   * Spill the passed interval
   * @param spillCausingInterval the interval causing the spill
   * @param restrict governing restrictions on physical register
   *                            assignment
   */
  void spillWithHoles(OPT_LinearScanLiveInterval spillCausingInterval,
                      OPT_RegisterRestrictions restrict) 
    throws OPT_OptimizingCompilerException {
    
    // Mark that we've spilled for this IR
    spilled = true;

    // Choose an interval to spill
    OPT_LinearScanLiveInterval intervalToSpill = 
      spillHeuristic(spillCausingInterval);
    
    OPT_Register spilledSymbolicReg = intervalToSpill.register;
    OPT_Register spilledRealReg = getMapping(spilledSymbolicReg);
    OPT_Register spillCausingSymbolicReg = spillCausingInterval.register;
    
    // If register to spill already has a real reg and
    //  ... and
    //  the interval causing the spill has no more live intervals
    //  ... and
    //  there is no restriction on allocating spillCausingSymbolicReg to
    //  spilledRealReg.
    if ((spilledRealReg != null) &&  
	/* the rest of this condition is for handling holes */
	(OPT_RegisterAllocatorState.getPhysicalRegResurrectList
         (spilledRealReg) == null) && // no hole interval
	(spillCausingInterval.getNextInterval() == null) && // not necessary
        !restrict.isForbidden(spillCausingSymbolicReg,spilledRealReg)) {
      
      // Free the spilled register
      spilledRealReg.freeRegister();

      // mark the symbolic as not being allocated
      spilledSymbolicReg.deallocateRegister();

      // allocate the spilled register to the symbolic that needs it
      spilledRealReg.allocateToRegister(spillCausingSymbolicReg);
	
      // don't want to reuse a spill location because intervalToSpill's start
      // point might overlap an interval that expired and it's 
      // spill location was put in the reuse list
      intervalToSpill.spilltoNewLocation(stackManager);
	
    } else {
      spillCausingInterval.spill(stackManager);
    }
  }
  
  /**
   * Based on the spill heuristic pick a live interval to spill
   * @param spillCausingInterval the live interval causing the spill
   * @return the interval to spill
   */
  OPT_LinearScanLiveInterval spillHeuristic(OPT_LinearScanLiveInterval 
                                            spillCausingInterval) {
    OPT_LinearScanLiveInterval intervalToSpill;
    intervalToSpill = spillMinUnitCost(spillCausingInterval);
    return intervalToSpill;
  }

  public String toString () {
    String msg = "Active set is:\n";
    for (OPT_LinearScanLiveInterval j = start; j != null; j = j.nextActive)
      msg += "\t" + j + "\n";
    return msg;
  }
  
  
  OPT_LinearScanLiveInterval spillMinUnitCost (OPT_LinearScanLiveInterval i) 
    throws OPT_OptimizingCompilerException {
    
    // pick the one that has the minimum unit cost
    // unit cost is calculated by just counting the number of defs
    // and uses of a register
    
    int min_unit_cost = countDefs(i.register) + countUses(i.register);
    OPT_LinearScanLiveInterval spill_reg = i;
    
    
    for ( OPT_LinearScanLiveInterval j = start ;
	  j != null && (j.register.getRegisterAllocated() != null) ;
	  j = j.nextActive ) {
      if (!j.register.isPhysical() &&
	  (i.register.getType() == j.register.getType())) {
	int tmp_cost =  countDefs(j.register) + countUses(j.register);
	if (min_unit_cost > tmp_cost) {
	  min_unit_cost = tmp_cost;
	  spill_reg = j;
	}
      }
    }
    
    return spill_reg;
    
  }
  
  
  int countDefs(OPT_Register reg) {
    int count = 0;
    for (OPT_RegisterOperandEnumeration e = OPT_RegisterInfo.defs(reg);  
	 e.hasMoreElements();) {
      e.next();
      count++;
    }
    return count;
  }
  
  int countUses(OPT_Register reg) {
    int count = 0;
    for (OPT_RegisterOperandEnumeration e = OPT_RegisterInfo.uses(reg); 
	 e.hasMoreElements();) {
      e.next();
      count++;
    }
    return count;
  }
}
