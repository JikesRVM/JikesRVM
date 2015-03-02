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
package org.jikesrvm.compilers.opt.controlflow;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.dfsolver.DF_LatticeCell;
import org.jikesrvm.compilers.opt.dfsolver.DF_Solution;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Calculate dominators for basic blocks.
 * <p> Uses the algorithm contained in Dragon book, pg. 670-1.
 * <pre>
 *       D(n0) := { n0 }
 *       for n in N - { n0 } do D(n) := N;
 *       while changes to any D(n) occur do
 *         for n in N - {n0} do
 *             D(n) := {n} U (intersect of D(p) over all predecessors p of n)
 * </pre>
 * <p> TODO: we do not support IRs with exception handlers!!
 */
public class Dominators {
  /**
   * Control for debug output
   */
  static final boolean DEBUG = false;
  /**
   * Should we compute post-dominators instead of dominators? This is
   * false by default.
   */
  static boolean COMPUTE_POST_DOMINATORS = false;

  private Map<BasicBlock, DominatorInfo> dominatorInfo;

  /**
   * Calculate the dominators for an IR.
   *
   * @param ir the IR in question
   */
  public void perform(IR ir) {
    if (ir.hasReachableExceptionHandlers()) {
      throw new OperationNotImplementedException("IR with exception handlers");
    }
    DominatorSystem system = new DominatorSystem(ir);
    if (DEBUG) {
      System.out.print("Solving...");
    }
    if (DEBUG) {
      System.out.println(system);
    }
    system.solve();
    if (DEBUG) {
      System.out.println("done");
    }
    DF_Solution solution = system.getSolution();
    if (DEBUG) {
      System.out.println("Dominator Solution :" + solution);
    }
    if (DEBUG) {
      System.out.print("Updating blocks ...");
    }
    updateBlocks(solution);
    if (DEBUG) {
      System.out.println("done.");
    }
    if (ir.options.PRINT_DOMINATORS) {
      printDominators(ir);
    }
  }

  /**
   * Calculate the "approximate" dominators for an IR i.e., the
   * dominators in the factored CFG rather than the normal CFG.
   * <p> (No exception is thrown if the input IR has handler blocks.)
   *
   * @param ir the IR in question
   */
  public void computeApproxDominators(IR ir) {
    DominatorSystem system = new DominatorSystem(ir);
    if (DEBUG) {
      System.out.print("Solving...");
    }
    if (DEBUG) {
      System.out.println(system);
    }
    system.solve();
    if (DEBUG) {
      System.out.println("done");
    }
    DF_Solution solution = system.getSolution();
    if (DEBUG) {
      System.out.println("Dominator Solution :" + solution);
    }
    if (DEBUG) {
      System.out.print("Updating blocks ...");
    }
    updateBlocks(solution);
    if (DEBUG) {
      System.out.println("done.");
    }
    if (ir.options.PRINT_DOMINATORS) {
      printDominators(ir);
    }
  }

  /**
   * Calculate the postdominators for an IR.
   *
   * @param ir the IR in question
   */
  public void computeApproxPostdominators(IR ir) {
    Dominators.COMPUTE_POST_DOMINATORS = true;
    DominatorSystem system = new DominatorSystem(ir);
    if (DEBUG) {
      System.out.print("Solving...");
    }
    if (DEBUG) {
      System.out.println(system);
    }
    system.solve();
    if (DEBUG) {
      System.out.println("done");
    }
    DF_Solution solution = system.getSolution();
    if (DEBUG) {
      System.out.println("Postdominator Solution :" + solution);
    }
    if (DEBUG) {
      System.out.print("Updating blocks ...");
    }
    updateBlocks(solution);
    if (DEBUG) {
      System.out.println("done.");
    }
    if (ir.options.PRINT_DOMINATORS) {
      printDominators(ir);
    }
    Dominators.COMPUTE_POST_DOMINATORS = false;
  }

  /**
   * Creates a {@code DominatorInfo} for each basic block
   * in the data flow system solution.
   *
   * @param solution the solution to the Dominators equations
   */
  public void updateBlocks(DF_Solution solution) {
    int capacityToPreventRehash = (int) (solution.size() * 1.4f);
    dominatorInfo = new HashMap<BasicBlock, DominatorInfo>(capacityToPreventRehash);
    for (final DF_LatticeCell latticeCell : solution.values()) {
      DominatorCell cell = (DominatorCell) latticeCell;
      BasicBlock b = cell.block;
      dominatorInfo.put(b, new DominatorInfo(cell.dominators));
      if (DEBUG) {
        System.out.println("Dominators of " + b + ":" + cell.dominators);
      }
    }
  }

  /**
   * Print the (already calculated) dominators.
   * @param ir the IR
   */
  public void printDominators(IR ir) {
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock b = e.nextElement();
      DominatorInfo i = dominatorInfo.get(b);
      System.out.println("Dominators of " + b + ":" + i.dominators);
    }
  }

  public DominatorInfo getDominatorInfo(BasicBlock b) {
    return dominatorInfo.get(b);
  }
}
