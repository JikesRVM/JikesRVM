/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import  java.io.*;
import  java.util.Enumeration;

/**
 * A compiler phase to compute and print the critical path
 * of each basic block.
 *
 * This class is declared as "final" which implies that all its methods
 * are "final" too.      
 *
 * @author Igor Pechtchanski
 */
final class OPT_ComputeCriticalPath extends OPT_CompilerPhase {

  public final boolean shouldPerform(OPT_Options options) {
    return  options.DG_CRITICAL_PATH;
  }

  public final String getName() {
    return  "Critical Path Computation";
  }

  /**
   * For each basic block, build the dependence graph and
   * compute critical path.
   *
   * @param ir the IR in question 
   */
  public final void perform(OPT_IR ir) {
    if (verbose >= 1)
      debug("Computing CP for " + ir.method);

    // Performing live analysis may reduce dependences between PEIs and stores
    if (ir.options.HANDLER_LIVENESS) {  
      new OPT_LiveAnalysis(false, false, true).perform(ir);
    }

    // iterate over each basic block
    for (BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      if (bb.isEmpty())
        continue;
      // Build Dependence graph
      dg = new OPT_DepGraph(ir, bb.firstInstruction(), 
                            bb.lastRealInstruction(), bb);
      int bl = 0;
      int cp = 0;
      // Compute critical paths
      for (DoublyLinkedListElement gn = dg.firstNode(); gn != null; 
          gn = gn.getNext()) {
        OPT_DepGraphNode dgn = (OPT_DepGraphNode)gn;
        if (verbose >= 5)
          debug("Computing critical path for " + dgn);
        computeCriticalPath(dgn, 0);
        int d = dgn.instruction().scratch;
        if (d > cp)
          cp = d;
        bl++;
      }
      cp++;
      System.err.println("::: BL=" + bl + " CP=" + cp + " LOC=" + ir.method
          + ":" + bb);
    }
    /* for */

  }
  // DepGraph for BB.
  // For internal use only.
  private OPT_DepGraph dg;

  /**
   * Initialize critical path computation.
   */
  OPT_ComputeCriticalPath() {
  }
  private static final int verbose = 0;

  private static final void debug(String s) {
    System.err.println(s);
  }
  private static String SPACES = null;

  private static final void debug(int depth, String s) {
    if (SPACES == null)
      SPACES = dup(7200, ' ');
    debug(SPACES.substring(0, depth*2) + s);
  }

  // DFS to compute critical path for all instructions
  // For internal use only.
  private final void computeCriticalPath(OPT_DepGraphNode n, int depth) {
    if (verbose >= 5)
      debug(depth, "Visiting " + n);
    OPT_Instruction i = n.instruction();
    if (i.scratch != -1)
      return;
    int cp = 0;
    for (OPT_DepGraph.NodeEnumeration succ = dg.successors(n); 
        succ.hasMoreElements();) {
      OPT_DepGraphNode np = succ.next();
      computeCriticalPath(np, depth + 1);
      OPT_Instruction j = np.instruction();
      int d = j.scratch;
      if (j.scratch + 1 > cp)
        cp = d + 1;
    }
    i.scratch = cp;
  }

  // Generates a string of a given length filled by a given character.
  // For internal use only.
  private static final String dup(int len, char c) {
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < len; i++)
      sb.append(c);
    return  sb.toString();
  }
}



