/*
 * (C) Copyright IBM Corp. 2001
 */
// $id$

import instructionFormats.*;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * OPT_LoopDepthEdgeCounts
 * 
 * Use the loop nesting depth to approximate edge counts.
 *
 * @see OPT_StaticBranchProbEdgeCounts
 * @see OPT_EdgeCounts
 *
 * @author Matthew Arnold
 * */

class OPT_LoopDepthEdgeCounts extends OPT_EdgeCounts
implements OPT_Constants, OPT_Operators
{

  static final boolean DEBUG = false;

  /**
   * Are basic block frequencies available?
   *
   * @return Whether basic block frequencies are available
   */
  boolean basicBlockFrequenciesAvailable() {
    return true;
  }

  /**
   * Are intraprocedural edge frequencies available?
   *
   * @return Whether edge frequencies are available
   */
  boolean edgeFrequenciesAvailable() {
    // For now, not available with loop nest depth approximates
    return false; 
  }

  /**
   * Get the frequency of an edge
   *
   * @param source The source of the edge
   * @param target The target of the edge
   * @return count The number of times the edge was executed
   */
  double getEdgeFrequency(OPT_BasicBlock source, OPT_BasicBlock target) {
    // For now, no edge weights when using loop depth approximations.
    return -1.0;
  }

  /**
   * Set the frequency of an edge
   *
   * @param source The source of the edge
   * @param target The target of the edge
   * @param count The number of times the edge was executed
   */
  void setEdgeFrequency(OPT_BasicBlock source, OPT_BasicBlock target, 
			double count) {
    // For now, no edge weights when using loop depth approximations.
    // Do nothing.
  }


  /**
   * Get the frequency of a basic block
   *
   * @param bb The basic block
   * @return The number of times the basic block is executed
   */
  double getBasicBlockFrequency(OPT_BasicBlock bb) {

    Double d = (Double) basicBlockMap.get(bb);
    if (d != null)
      return  d.doubleValue();
    return -1.0;
  }

  /**
   * Initialize any needed information about the edge counts.
   * Called at the beginning of compilation.
   *
   * @param ir the governing ir
   * 
   */
  void initialize(OPT_IR ir) {
    basicBlockMap = new Hashtable();
  }


  /**
   * Make sure basic blocks have a valid block weight.
   *
   * @param ir the governing ir
   */
  void updateCFGFrequencies(OPT_IR ir) {
    internalUpdateCFGFrequencies(ir);
  }

  //------- Implementation --------------

  /**
   * Assume each loop is executed this many times.  This could become
   * a command-line option if it needs to be varied.  
   */
  static final int LOOP_MULTIPLIER = 10;

    /**
     * Make sure that all basic blocks have  a valid block weight.
     *
     * @param ir The governing IR
     *
     */
    
  void internalUpdateCFGFrequencies(OPT_IR ir) {
    // Create an up to date loop structure tree
    OPT_DominatorsPhase dom = new OPT_DominatorsPhase();
    dom.perform(ir);

    OPT_LSTGraph lst = ir.HIRInfo.LoopStructureTree;
    if (lst == null) {
      throw new OPT_OptimizingCompilerException("null loop structure tree");
    }

    // enumerate each basic block ...
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();

      // set the estimate count for this basic block
      int depth = lst.getLoopNestDepth(bb);
      if (DEBUG) 
	VM.sysWrite("Depth is " + depth + "\n");
      int freq = 1;
      for (int i=0; i<depth; i++) 
	freq *= LOOP_MULTIPLIER;
      basicBlockMap.put(bb,new Double(freq));
    }
  }

  /**
   * Used to map basic blocks to weights.
   */
  Hashtable basicBlockMap;

}








