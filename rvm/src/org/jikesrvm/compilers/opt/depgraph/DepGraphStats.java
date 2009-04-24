/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.depgraph;

import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;

/**
 * Dependence Graph Statistics
 *
 * (This module will only be used for experimental measurements, so
 * compile-time overhead is less of a concern.)
 *
 * @see DepGraph
 */
public class DepGraphStats {
  /**
   * Create a statistical summary of a dependence graph for a given basic
   * block.
   *
   * @param   dg        the dependence graph
   * @param   bbName    name of the basic block
   */
  DepGraphStats(DepGraph dg, String bbName) {
    // First pass -- compute numNodes
    int _numNodes = 0;
    boolean containsLoadOrStore = false;
    for (DepGraphNode n = (DepGraphNode) dg.firstNode(); n != null; n = (DepGraphNode) n.getNext()) {
      _numNodes++;
      Instruction instr = n.instruction();
      if (instr.isImplicitStore() || instr.isImplicitLoad()) {
        containsLoadOrStore = true;
      }
    }
    DepGraphNode[] nodes = new DepGraphNode[_numNodes];
    int[] ECT = new int[_numNodes];              // Earliest Completion Times
    int _totalTime = 0;
    int _critPathLength = 0;
    // Second pass -- compute times
    int i = 0;
    for (DepGraphNode n = (DepGraphNode) dg.firstNode(); n != null; n = (DepGraphNode) n.getNext()) {
      nodes[i] = n;
      ECT[i] = 0;
      for (DepGraphEdge e = (DepGraphEdge) n.firstInEdge(); e != null; e = (DepGraphEdge) e.getNextIn()) {
        DepGraphNode pred = (DepGraphNode) e.fromNode();
        // Look for pred in nodes[]
        int j;
        for (j = 0; j < i; j++) {
          if (nodes[j] == pred) {
            break;
          }
        }
        if (j == i) {
          // Not found
          throw new OptimizingCompilerException("DepGraphStats: dep graph is not topologically sorted ???");
          // NOTE: I could not use SortedGraphIterator
          // for top sort because DepGraphNode
          // is not a subclass of SortedGraphNode
        }
        // TODO: add edge latency also??
        ECT[i] = Math.max(ECT[i], ECT[j]);
      }         // for ( e = ... )
      Instruction instr = n.instruction();
      int curTime = estimateExecutionTime(instr);
      _totalTime += curTime;
      ECT[i] += curTime;
      _critPathLength = Math.max(_critPathLength, ECT[i]);
      i++;
    }           // for ( n = ... )
    System.out.println("@@@@ BB " +
                       bbName +
                       "; totalTime = " +
                       _totalTime +
                       "; containsLoadOrStore = " +
                       containsLoadOrStore +
                       "; critPathLength = " +
                       _critPathLength);
  }

  /**
   * Print the dependence graph stats for all basic blocks in an IR.
   * @param ir the IR
   */
  public static void printBasicBlockStatistics(IR ir) {
    final boolean DEBUG = false;
    System.out.println();
    System.out.println("**** START OF printBasicBlockStatistics() for method " + ir.method + " ****");
    if (DEBUG) {
      ir.printInstructions();
    }

    // Performing live analysis may reduce dependences between PEIs and stores
    if (ir.options.L2M_HANDLER_LIVENESS) {
      new LiveAnalysis(false, false, true).perform(ir);
    }

    for (BasicBlock bb = ir.firstBasicBlockInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      //DepGraph dg =
      new DepGraph(ir, bb.firstRealInstruction(), bb.lastRealInstruction(), bb);
    }
    System.out.println("**** END OF printBasicBlockStatistics() ****");
  }

  /**
   * Return an estimate of the number of cycles for a given instruction.
   * Currently, this estimate is comically simple.
   * @param instr the instruction
   */
  int estimateExecutionTime(Instruction instr) {
    if (instr.operator() == NULL_CHECK) {
      return 0;
    } else {
      return 1;
    }
  }
}
