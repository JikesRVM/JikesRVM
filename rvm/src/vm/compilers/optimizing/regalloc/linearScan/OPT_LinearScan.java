/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Arrays;

/**
 * Main driver for linear scan register allocation.
 * @author Mauricio Serrano
 * @author Michael Hind
 */
final class OPT_LinearScan extends OPT_CompilerPhase 
  implements OPT_Operators, VM_Constants {

  /**
   *  The IR for this method
   */
  OPT_IR ir;

  /**
   *  The live interval information computed by a separate phase
   */
  OPT_LinearScanLiveAnalysis liveInfo;
  
  static private final boolean debug = false;
  static private final boolean gcdebug = false;
  
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

    //  The registerManager has already been initialized
    OPT_GenericStackManager sm = ir.stackManager;

    // Set up register restrictions
    sm.computeRestrictions(ir);
    OPT_RegisterRestrictions restrict = sm.getRestrictions();

    // Live intervals have already been computed by a seperate phase
    liveInfo = ir.MIRInfo.liveInfo;

    OPT_LinearScanActiveSet active = new OPT_LinearScanActiveSet(sm);
    
    // Live intervals sorted by increasing start point
    for (OPT_LinearScanLiveInterval li = liveInfo.getFirstLiveInterval(); 
	 li != null; 
	 li=li.getNextLive()) {
      
      if (debug) { VM.sysWrite("looking****** "+li+'\n');  }

      active.expireOldIntervalsWithHoles(li);

      
      // If the live interval does not correspond to a physical register
      //   then we process it
      if (!li.register.isPhysical()) {

	// if no physical registers left, we choose a live range to spill, 
	// which could be the current live interval for which we're 
	// allocating a register
	if (!li.activateIntervalWithHoles(sm))  {
	  active.spillWithHoles(li,restrict);
	}
      }
      
      // insert the new interval into the active set 
      active.insert(li);
    }
    
    // update GC maps, by replacing symbolic regs with the real
    // reg or spill
    updateGCMaps();
    
    // replace symbolic registers by real registers 
    for (OPT_LinearScanLiveInterval li = liveInfo.getFirstLiveInterval(); 
	 li != null; 
	 li = li.getNextLive()) {
      li.replaceSymbolic(ir);
    }
    
    // Generate spill code if necessary
    if (active.spilled) {	
      sm.insertSpillCode();
    }
    
    if (debug) { 
      OPT_Compiler.printInstructions(ir, "REGALLOC");
    }
  }
  
  /**
   *  Iterate over the IR-based GC map collection and for each entry
   *  replace the symbolic reg with the real reg or spill it was allocated
   */
  private void updateGCMaps()  throws OPT_OptimizingCompilerException {
    for (OPT_GCIRMapEnumerator GCenum = ir.MIRInfo.gcIRMap.enumerator(); 
	 GCenum.hasMoreElements(); ) {
      OPT_GCIRMapElement GCelement = GCenum.next();
      OPT_Instruction  GCinst    = GCelement.getInstruction();
      
      for (OPT_RegSpillListEnumerator regEnum = 
	     GCelement.regSpillListEnumerator();
	   regEnum.hasMoreElements(); ) {
	OPT_RegSpillListElement elem = regEnum.next();
	OPT_Register symbolic = elem.getSymbolicReg();

	if (gcdebug) { VM.sysWrite("      looking "+symbolic+'\n'); }

	if (symbolic.isAllocated()) {
	  OPT_Register ra = OPT_RegisterAllocatorState.getMapping(symbolic);
	  elem.setRealReg(ra);
	  if (gcdebug) {  VM.sysWrite(ra+"\n"); }

	} else if (symbolic.isSpilled()) {
	  int spill = symbolic.getSpillAllocated();
	  elem.setSpill(spill);
	  if (gcdebug) {   VM.sysWrite(spill+"\n"); }

	} else {
	  throw new OPT_OptimizingCompilerException(
	    "LinearScan",
	    "PANIC in GCMAPS, register not alive:", symbolic.toString());
	}
      } // for each register
    } // for each gc map
  } // method
} 
