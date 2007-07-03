/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

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
class OPT_Dominators {
  /**
   * Control for debug output
   */
  static final boolean DEBUG = false;
  /**
   * Should we compute post-dominators instead of dominators? This is
   * false by default.
   */
  static boolean COMPUTE_POST_DOMINATORS = false;

  /**
   * Calculate the dominators for an IR.
   * <p> After this pass, each basic block's scrach field points to
   * an <code> OPT_DominatorInfo </code> object, which holds the dominators
   * of the basic block.
   *
   * @param ir the IR in question
   */
  public static void perform(OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers()) {
      throw new OPT_OperationNotImplementedException("IR with exception handlers");
    }
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
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
    OPT_DF_Solution solution = system.getSolution();
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
   * <p> After this pass, each basic block's scratch field points to
   * an OPT_DominatorInfo object, which holds the dominators
   * of the basic block.
   *
   * @param ir the IR in question
   */
  public static void computeApproxDominators(OPT_IR ir) {
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
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
    OPT_DF_Solution solution = system.getSolution();
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
   * <p> After this pass, each basic block's scrach field points to
   * an OPT_DominatorInfo object, which holds the postdominators
   * of the basic block.
   *
   * @param ir the IR in question
   */
  public static void computeApproxPostdominators(OPT_IR ir) {
    OPT_Dominators.COMPUTE_POST_DOMINATORS = true;
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
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
    OPT_DF_Solution solution = system.getSolution();
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
    OPT_Dominators.COMPUTE_POST_DOMINATORS = false;
  }

  /**
   * For each basic block in the data flow system solution,
   * create an <code> OPT_DominatorInfo </code> and store it in the basic
   * blocks scratchObject
   *
   * @param solution the solution to the Dominators equations
   */
  public static void updateBlocks(OPT_DF_Solution solution) {
    for (final OPT_DF_LatticeCell latticeCell : solution.values()) {
      OPT_DominatorCell cell = (OPT_DominatorCell) latticeCell;
      OPT_BasicBlock b = cell.block;
      b.scratchObject = new OPT_DominatorInfo(cell.dominators);
      if (DEBUG) {
        System.out.println("Dominators of " + b + ":" + cell.dominators);
      }
    }
  }

  /**
   * Print the (already calculated) dominators.
   * @param ir the IR
   */
  public static void printDominators(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock b = e.next();
      OPT_DominatorInfo i = (OPT_DominatorInfo) b.scratchObject;
      System.out.println("Dominators of " + b + ":" + i.dominators);
    }
  }
}



