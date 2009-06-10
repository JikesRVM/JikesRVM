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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.dfsolver.DF_LatticeCell;
import org.jikesrvm.compilers.opt.dfsolver.DF_System;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Implementation of the dataflow equation system to calculate dominators.
 */
class DominatorSystem extends DF_System {

  /**
   * The governing IR.
   */
  private final IR ir;

  /**
   * Default constructor.
   * @param ir the governing IR
   */
  public DominatorSystem(IR ir) {
    this.ir = ir;
    setupEquations();
  }

  /**
   * Go through each basic block in the IR, and add equations
   * to the system as required.
   * <p> Uses the algorithm contained in Dragon book, pg. 670-1.
   * <pre>
   *     D(n0) := { n0 }
   *     for n in N - { n0 } do D(n) := N;
   *     while changes to any D(n) occur do
   *       for n in N - {n0} do
   *           D(n) := {n} U (intersect of D(p) over all predecessors p of n)
   * </pre>
   */
  void setupEquations() {
    // loop through each basic block in the IR
    for (BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock bb = e.next();
      // add a data-flow equation for this basic block
      // DOM(n) = {n} MEET {pred(n)}
      DF_LatticeCell dom = findOrCreateCell(bb);
      DF_LatticeCell[] pred = getCellsForPredecessors(bb);
      newEquation(dom, DominatorOperator.MEET, pred);
    }
  }

  /**
   * Initialize the lattice variables (Dominator sets) for
   * each basic block.
   */
  protected void initializeLatticeCells() {
    if (Dominators.COMPUTE_POST_DOMINATORS) {
      BasicBlock exit = ir.cfg.exit();
      DominatorCell last = (DominatorCell) getCell(exit);
      for (final DF_LatticeCell latticeCell : cells.values()) {
        DominatorCell cell = (DominatorCell) latticeCell;
        if (cell == last) {
          cell.addSingleBlock(cell.block);
        } else {
          cell.setTOP(ir);
        }
      }
    } else {
      BasicBlock start = ir.cfg.entry();
      DominatorCell first = (DominatorCell) getCell(start);
      for (final DF_LatticeCell latticeCell : cells.values()) {
        DominatorCell cell = (DominatorCell) latticeCell;
        if (cell == first) {
          cell.addSingleBlock(cell.block);
        } else {
          cell.setTOP(ir);
        }
      }
    }
  }

  /**
   * Initialize the work list for the dataflow equation system.
   * <p> The initial work list is every equation containing the start
   * node.
   */
  protected void initializeWorkList() {
    if (Dominators.COMPUTE_POST_DOMINATORS) {
      // Add every equation to work list (to be safe)
      // WARNING: an "end node" may be part of a cycle
      for (BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
        BasicBlock bb = e.next();
        addCellAppearancesToWorkList(getCell(bb));
      }
    } else {
      DominatorCell first = (DominatorCell) getCell(ir.cfg.entry());
      addCellAppearancesToWorkList(first);
    }
  }

  /**
   * Get the DF_LatticeCell key corresponding to a basic block
   * @param bb the basic block
   * @return the key (just the block itself)
   */
  Object getKey(BasicBlock bb) {
    return bb;
  }

  /**
   * Make a new DF_LatticeCell key corresponding to a basic block
   * @param key the basic block
   * @return the new cell
   */
  protected DF_LatticeCell makeCell(Object key) {
    return new DominatorCell((BasicBlock) key, ir);
  }

  /**
   * Return a list of lattice cells corresponding to the
   * predecessors of a basic block.
   * @param bb the basic block
   */
  DF_LatticeCell[] getCellsForPredecessors(BasicBlock bb) {
    if (Dominators.COMPUTE_POST_DOMINATORS) {
      /****
       if ( bb.mayThrowUncaughtException() ) {
       if (Dominators.DEBUG) VM.sysWrite("LOCATION #1 ...\n");
       // Include exit node as an output node
       DF_LatticeCell s[] = new DF_LatticeCell[bb.getNumberOfOut()+1];
       BasicBlockEnumeration e = bb.getOut();
       for (int i=0; i<s.length-1; i++ ) {
       BasicBlock p = e.next();
       s[i] = findOrCreateCell(getKey(p));
       }
       s[s.length-1] = findOrCreateCell(getKey(ir.cfg.exit()));
       return s;
       }
       else
       ****/
      {
        if (Dominators.DEBUG) {
          VM.sysWrite("LOCATION #2 ...\n");
        }
        DF_LatticeCell[] s = new DF_LatticeCell[bb.getNumberOfOut()];
        BasicBlockEnumeration e = bb.getOut();
        for (int i = 0; i < s.length; i++) {
          BasicBlock p = e.next();
          s[i] = findOrCreateCell(getKey(p));
        }
        return s;
      }
    } else {
      if (Dominators.DEBUG) {
        System.out.println("LOCATION #3 ...");
      }
      DF_LatticeCell[] s = new DF_LatticeCell[bb.getNumberOfIn()];
      BasicBlockEnumeration e = bb.getIn();
      for (int i = 0; i < s.length; i++) {
        BasicBlock p = e.next();
        s[i] = findOrCreateCell(getKey(p));
      }
      return s;
    }
  }
}
