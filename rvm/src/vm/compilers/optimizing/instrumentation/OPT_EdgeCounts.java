/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.instructionFormats.*;
import java.util.Vector;
import java.util.Hashtable;

/**
 * An interface to make edge weights available to the IR during
 * compilation.  
 *
 * @author Matthew Arnold
 * */

abstract class OPT_EdgeCounts
implements OPT_Constants, OPT_Operators
{

  static final boolean DEBUG = false;

  /**
   * Get the frequency of an edge
   *
   * @param source The source of the edge
   * @param target The target of the edge
   * @return count The number of times the edge was executed
   */
  abstract double getEdgeFrequency(OPT_BasicBlock source, OPT_BasicBlock target);

  /**
   * Set the frequency of an edge
   *
   * @param source The source of the edge
   * @param target The target of the edge
   * @param count The number of times the edge was executed
   */
  abstract void setEdgeFrequency(OPT_BasicBlock source, OPT_BasicBlock target, 
				 double count);

  /**
   * Get the frequency of a basic block
   *
   * @param bb The basic block
   * @return The number of times the basic block is executed
   */
  abstract double getBasicBlockFrequency(OPT_BasicBlock bb);

  /**
   * Initialize any needed information about the edge counts.
   * Called at the beginning of compilation.
   *
   * @param ir the governing ir
   * 
   */
  abstract void initialize(OPT_IR ir);


  /**
   * Do whatever needs to be done to ensure that all edges/blocks 
   * have a valid edge weights.  May be called at several points 
   * throughout compilation.
   *
   * Postcondition:  All edges in the CFG should have a valid edge weight.
   *
   * @param ir the governing ir
   */
  abstract void updateCFGFrequencies(OPT_IR ir);



  /**
   * Are basic block frequencies available?
   *
   * @return Whether basic block frequencies are available
   */
  abstract boolean basicBlockFrequenciesAvailable();

  /**
   * Are intraprocedural edge frequencies available?
   *
   * @return Whether edge frequencies are available
   */
  abstract boolean edgeFrequenciesAvailable();


  /**
   * Called when compilation is complete, so data structures can be
   * cleaned up.  Since blockes are cached based on basic blocks, the
   * whole IR would be leaked if the cache were not cleared.  If
   * the Jikes RVM implemented weak references this would not be necessary.
   *
   */
  void compilationFinished() {
  }




}


