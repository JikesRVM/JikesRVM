/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Live variable interval analysis for linear scan register allocator
 *
 * @author Mauricio J. Serrano
 * @modified Stephen Fink
 */

import java.io.PrintStream;

final class OPT_LinearScanLiveAnalysis extends OPT_CompilerPhase 
  implements OPT_Operators {
  
  /**
   *  The first live interval in sorted order
   */
  private OPT_LinearScanLiveInterval firstInterval;

  /**
   *  The last live interval in sorted order
   */
  OPT_LinearScanLiveInterval lastInterval;

  /**
   *  The ir, for convenience
   */
  private OPT_IR ir;

  /**
   *  A list of basic blocks in topological order
   */
  private OPT_BasicBlock ListOfBlocks;

  /**
   *  A reverse topological list of basic blocks
   */
  private OPT_BasicBlock reverseTopFirst;
  
  static final private boolean debug = false;

  final boolean shouldPerform(OPT_Options options) { return true; }
  final String getName() { return "Live Interval Analysis"; }
  final boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  }

  /**
   *  Compute live intervals for this IR
   *  The result is a sorted (by beginning point) list of live intervals
   *    The beginning is "firstInterval" the end is "lastInterval"
   *  @param ir the IR
   */
  void perform(OPT_IR ir) {
    this.ir = ir;
    OPT_ControlFlowGraph cfg = ir.cfg;

    // Create topological list and a reverse topological list
    // The results are on ListOfBlocks and reverseTopFirst lists
    createTopAndReverseList(cfg);

    // give DFN values to each instruction
    assignDepthFirstNumbers(cfg);

    // Initialize registers 
    initializeRegisters();
    
    OPT_LinearScanLiveInterval liveIn  =
      new OPT_LinearScanLiveInterval(null, 0, 0, ir);
    OPT_LinearScanLiveInterval liveOut = 
      new OPT_LinearScanLiveInterval(null, 0, 0, ir);
    
    // visit each basic block in the ListOfBlocks list
    for (OPT_BasicBlock bb = ListOfBlocks; 
	 bb !=null; 
	 bb=(OPT_BasicBlock)bb.nextSorted) {

      // visit each live interval for this basic block
      for (OPT_LiveIntervalElement live = bb.getFirstLiveIntervalElement();
	   live != null; 
	   live = live.getNext()) {
	if (debug) { System.out.println(live); }


	OPT_LinearScanLiveInterval resultingInterval
	  = processLiveInterval(live, bb);
	
	if (live.getEnd() == null) { 
	  // the live interval is still alive, insert at the end of the
	  // liveOut active list
	  liveOut.addAfterOnActiveList(resultingInterval);

	} // end == null
      } // foreach live interval element for this block

      // done with the basic block, exchange liveIn and liveOut
      // and clear liveOut for the next basic block
      OPT_LinearScanLiveInterval temp = liveIn;
      liveIn = liveOut;  
      liveOut = temp;

      // walk the new liveout (previously liveIn) active list, 
      // setting the prev/next Active fields to null
      cleanActiveList(liveOut);
    } // foreach basic block
    
    if (debug) { printLiveIntervals(); }

    // only needed if field is used by other code
    clearRegistersLiveInterval();

    if (debug) {
      VM.sysWrite("**** START OF MIR LIVE INTERVAL DUMP "+ir.method+" ****\n");
      printLiveIntervals();
      VM.sysWrite("**** END   OF MIR LIVE INTERVAL DUMP ****\n");
    }

    // we return the computed live info, by placing it on the IR
    ir.MIRInfo.liveInfo = this;
  }
  
  /**
   *  Create topological list and a reverse topological list
   *  The results are on ListOfBlocks and reverseTopFirst lists
   *  @param cfg the control flow graph
   */
  private void createTopAndReverseList(OPT_ControlFlowGraph cfg) {
    // DFS: create a list of nodes (basic blocks) in a topological order 
    cfg.clearDFS();
    ListOfBlocks = cfg.entry();
    ListOfBlocks.sortDFS();
    
    // This loop reverses the topological list by using the sortedPrev field
    reverseTopFirst = null;
    for (OPT_BasicBlock bb = ListOfBlocks; 
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
   *  This method processes all basic blocks, do the following to each block
   *   1) add it to the begining of the "ListOfBlocks" list
   *   2) number the instructions
   *   3) process the instructions that restrict physical register
   *   assignment
   *  @param cfg the control flow graph
   */
  void assignDepthFirstNumbers(OPT_ControlFlowGraph cfg) {
    int curDFN = ir.numberInstructions() - 1;

    // restrictions = new OPT_LinkedList();
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    
    if (debug) {  printVCGgraph(cfg, curDFN);  }
    
    ListOfBlocks = null;
    for (OPT_BasicBlock bb = reverseTopFirst; 
	 bb != null; 
	 bb = (OPT_BasicBlock)bb.sortedPrev) {

      // insert bb at the front of the list
      bb.nextSorted = ListOfBlocks;
      ListOfBlocks = bb;

      // number the instructions last to first
      for (OPT_Instruction inst = bb.lastInstruction(); 
	   inst != null;
	   inst = inst.getPrev()) {

	setDFN(inst, curDFN);

	curDFN--;
      }
    }

    if (debug) {  printDFNS();  }
  }

  /**
   * Initialize registers live interval and "should" value
   */
  private void initializeRegisters() {
    for (OPT_Register reg = ir.regpool.getFirstRegister(); 
	 reg != null;
	 reg = reg.getNext()) {
      setLiveInterval(reg, null); // for the live interval
    }
  }
    
  /**
   * Null-out live interval, only needed if the field is used by other code
   */
  private void clearRegistersLiveInterval() {
    for (OPT_Register reg = ir.regpool.getFirstRegister(); 
	 reg != null;
	 reg = reg.getNext()) {
      setLiveInterval(reg, null); 
    }
  }

  /**
   * For each live interval associated with this block
   * we either add a new interval, or extend a previous interval
   * if it is contiguous
   *
   * @param live the liveIntervalElement for a basic block/reg pair
   * @param bb the basic block
   * @return the resulting LinearScanLiveInterval
   */
  private OPT_LinearScanLiveInterval
    processLiveInterval(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {

    OPT_LinearScanLiveInterval resultingInterval;
    // Get the reg and (adjusted) begin, end pair for this interval
    OPT_Register reg = live.getRegister();
    int dfnEnd = getDfnEnd(live, bb);
    int dfnBegin = getDfnBegin(live, bb);
    
    // check for an existing live interval for this register
    OPT_LinearScanLiveInterval existingInterval = getLiveInterval(reg);
    if (existingInterval == null) {
      // create a new live interval
      OPT_LinearScanLiveInterval newInterval = 
	new OPT_LinearScanLiveInterval(reg, dfnBegin, dfnEnd, ir);
      
      // associate the interval with the register and it to list
      setLiveInterval(reg, newInterval);
      appendInterval(newInterval);
      
      resultingInterval = newInterval;
      
    } else {
      if (!shouldConcatenate(existingInterval, live, bb)) {
        // create a new interval
	OPT_LinearScanLiveInterval newInterval = 
	  new OPT_LinearScanLiveInterval(reg, dfnBegin, dfnEnd, ir);
	
	// hook up the existing and new intervals
	// and associate the new interval with this register
	existingInterval.setNextInterval(newInterval);
	newInterval.setPrevInterval(existingInterval);
	setLiveInterval(reg, newInterval);
	
	// add the new interval to the main (sorted) list
	appendInterval(newInterval);  
	
	// promote newInterval
	resultingInterval = newInterval;
      } else {
	// we extend the live interval based on the live interval element
	existingInterval.extendEnd(dfnEnd);
	
	// remove it from active list
	existingInterval.removeFromActiveList();
	
	// promote existingInterval
	resultingInterval = existingInterval;
      } 
    } 
    
    return resultingInterval;
  }
  
  /**
   * Should we simply merge the live interval live into a previous linear
   * scan interval for this register?
   *
   * @param previous the previous linear scan interval in question
   * @param live the live interval being queried
   * @param bb the basic block in which live resides.
   */
  boolean shouldConcatenate(OPT_LinearScanLiveInterval previous,
                            OPT_LiveIntervalElement live,
                            OPT_BasicBlock bb) {

    // we only concatenate live if it begins the basic block.  For, if
    // live starts in the middle of the basic block, the register cannot
    // be live for the entire basic block.
    if (live.getBegin() != null) {
      if (live.getBegin() != bb.firstRealInstruction()) {
        return false;
      }
    }

    // OK: live starts at the beginning of the basic block.
    // Now make sure it is contiguous with previous
    int dfnBegin = getDfnBegin(live, bb);
    if (previous.dend + 1 < dfnBegin) {
      return false;
    }

    // If got this far, it's OK to merge into the previous interval.
    return true;
  }


  /**
   *  walk the passed list, setting the prev/next Active fields to null
   *  @param list the list to process
   */
  private void cleanActiveList(OPT_LinearScanLiveInterval list) {
    OPT_LinearScanLiveInterval next;
    for (OPT_LinearScanLiveInterval l = list; l != null; l = next) {
      next = l.getNextActive();  	// save next for next iteration
      l.setPrevActive(null);
      l.setNextActive(null);
    }
  }
  
  /**
   *  Print the DFN numbers associated with each instruction
   */
  private void printDFNS() {
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
	 inst != null;
	 inst = inst.nextInstructionInCodeOrder()) {
      System.out.println(getDFN(inst) +" "+ inst);
    }
  }
  
  /**
   * @return the first live interval in sorted order
   */
 OPT_LinearScanLiveInterval getFirstLiveInterval() {
    return firstInterval;
  }
  
  /**
   *  Add the passed interval at the end of the list
   *  @param interval the interval to add to the end
   */
  private void appendInterval(OPT_LinearScanLiveInterval interval) {
    if (lastInterval != null) {
      // Add it after lastInterval
      lastInterval.setNextLive(interval);
    } else {
      firstInterval = interval;
    }
    lastInterval = interval;
  }

  /**
   *
   */
  private int getDfnEnd(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
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
   *
   */
  private int getDfnBegin(OPT_LiveIntervalElement live, OPT_BasicBlock bb) {
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
   *  Associates the passed live interval with the passed register
   *  @param reg the register
   *  @param interval the live interval
   */
  private static void setLiveInterval(OPT_Register reg, 
				      OPT_LinearScanLiveInterval interval) {
    reg.scratchObject = interval;
  }
  
  /**
   *  returns the live interval associated with the passed register
   *  @param reg the register
   *  @return the live interval or null
   */
  private static OPT_LinearScanLiveInterval getLiveInterval(OPT_Register reg) {
    return (OPT_LinearScanLiveInterval) reg.scratchObject;
  }
  
  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  private static int getDFN(OPT_Instruction inst) {
    return  inst.scratch;
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  private static void setDFN(OPT_Instruction inst, int dfn) {
    inst.scratch = dfn;
  }
  
  /**
   * Print all the live intervals
   */
  void printLiveIntervals() {
    for (OPT_LinearScanLiveInterval li = firstInterval; 
	 li != null; 
	 li = li.getNextLive()) {
      System.out.println(li);
    }
  }

  /**
   * print the graph if it is non-trival, but manageable
   * @param cfg the control flow graph
   */
  private void printVCGgraph(OPT_ControlFlowGraph cfg, int curDFN) {
    if (curDFN >= 25 && curDFN < 150) {
      OPT_VCG.printVCG("graphs.vcg" + curDFN, cfg);
    }
  }

  /**
   *  JC (8/14) 
   *  compute statistics to be used for generating features in 
   *  OPT_LinearScanActiveSet.java
   */
  private void computeStatistics() {
    for (OPT_LinearScanLiveInterval li = firstInterval; 
	 li != null; 
	 li = li.getNextLive())  {
      if (li.getNextInterval() != null) {
	for (OPT_LinearScanLiveInterval tmp = li;
	     tmp != null;
	     tmp = tmp.getNextInterval()) {
	  li.setInstSpanned(li.getInstSpanned() + (tmp.dend-tmp.dbeg));
	}
      } else {
	li.setInstSpanned(li.dend - li.dbeg);
      }
    }
  }
}
