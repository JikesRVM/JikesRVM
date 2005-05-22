/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.lang.Math;

/**
 * Dependence Graph Statistics 
 *
 * (This module will only be used for experimental measurements, so 
 * compile-time overhead is less of a concern.)
 *
 * @see OPT_DepGraph
 * @author Vivek Sarkar
 */
class OPT_DepGraphStats implements OPT_Operators {
  /**
   * The number of nodes in the dependence graph
   */
  int numNodes;
  /**
   * The total volume (expected cycles) of work represented by nodes in
   * the dependence graph.
   */
  int totalTime;
  /**
   * The length of the critical path through the dependence graph
   */
  int critPathLength;
  static final boolean debug = false;

  /**
   * Create a statistical summary of a dependence graph for a given basic
   * block.
   * 
   * @param   dg        the dependence graph
   * @param   bbName    name of the basic block
   */
  OPT_DepGraphStats(OPT_DepGraph dg, String bbName) {
    // First pass -- compute numNodes
    int _numNodes = 0;
    boolean containsLoadOrStore = false;
    for (OPT_DepGraphNode n = (OPT_DepGraphNode)dg.firstNode(); 
        n != null; n = (OPT_DepGraphNode)n.getNext()) {
      _numNodes++;
      OPT_Instruction instr = n.instruction();
      if (instr.isImplicitStore() || instr.isImplicitLoad())
        containsLoadOrStore = true;
    }
    OPT_DepGraphNode nodes[] = new OPT_DepGraphNode[_numNodes];
    int ECT[] = new int[_numNodes];              // Earliest Completion Times
    int _totalTime = 0;
    int _critPathLength = 0;
    // Second pass -- compute times
    int i = 0;
    for (OPT_DepGraphNode n = (OPT_DepGraphNode)dg.firstNode(); n != null; 
        n = (OPT_DepGraphNode)n.getNext()) {
      nodes[i] = n;
      ECT[i] = 0;
      for (OPT_DepGraphEdge e = (OPT_DepGraphEdge)n.firstInEdge(); e != 
          null; e = (OPT_DepGraphEdge)e.getNextIn()) {
        OPT_DepGraphNode pred = (OPT_DepGraphNode)e.fromNode();
        // Look for pred in nodes[]
        int j;
        for (j = 0; j < i; j++) {
          if (nodes[j] == pred)
            break;
        }
        if (j == i) {
          // Not found
          throw  new OPT_OptimizingCompilerException(
              "OPT_DepGraphStats: dep graph is not topologically sorted ???");
          // NOTE: I could not use OPT_SortedGraphIterator 
          // for top sort because OPT_DepGraphNode
          // is not a subclass of OPT_SortedGraphNode
        }
        // TODO: add edge latency also??
        ECT[i] = Math.max(ECT[i], ECT[j]);
      }         // for ( e = ... )
      OPT_Instruction instr = n.instruction();
      int curTime = estimateExecutionTime(instr);
      _totalTime += curTime;
      ECT[i] += curTime;
      _critPathLength = Math.max(_critPathLength, ECT[i]);
      i++;
    }           // for ( n = ... )
    System.out.println("@@@@ BB " + bbName + "; totalTime = " + _totalTime
        + "; containsLoadOrStore = " + containsLoadOrStore + 
        "; critPathLength = "
        + _critPathLength);
  }

  /**
   * Print the dependence graph stats for all basic blocks in an IR.
   * @param ir the IR
   */
  static void printBasicBlockStatistics(OPT_IR ir) {
    System.out.println();
    System.out.println("**** START OF printBasicBlockStatistics() for method "
        + ir.method + " ****");
    if (debug) {
      ir.printInstructions();
    }

    // Performing live analysis may reduce dependences between PEIs and stores
    if (ir.options.HANDLER_LIVENESS) {  
      new OPT_LiveAnalysis(false, false, true).perform(ir);
    }

    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
        bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      OPT_DepGraph dg = new OPT_DepGraph(ir, bb.firstRealInstruction(), 
                                         bb.lastRealInstruction(), bb);
      OPT_DepGraphStats s = new OPT_DepGraphStats(dg, bb.toString());
    }
    System.out.println("**** END OF printBasicBlockStatistics() ****");
  }

  /**
   * Return an estimate of the number of cycles for a given instruction.
   * Currently, this estimate is comically simple.
   * @param instr the instruction
   */
  int estimateExecutionTime(OPT_Instruction instr) {
    if (instr.operator() == NULL_CHECK)
      return  0; 
    else 
      return  1;
  }
}
