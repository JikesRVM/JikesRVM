/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * Implementation of the dataflow equation system to calculate dominators.
 */
class OPT_DominatorSystem extends OPT_DF_System {

  /**
   * The governing IR.
   */
  OPT_IR ir;

  /**
   * Default constructor.
   * @param ir the governing IR
   */
  public OPT_DominatorSystem(OPT_IR ir) {
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
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
         e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      // add a data-flow equation for this basic block
      // DOM(n) = {n} MEET {pred(n)}
      OPT_DF_LatticeCell dom = findOrCreateCell(bb);
      OPT_DF_LatticeCell[] pred = getCellsForPredecessors(bb);
      newEquation(dom, OPT_DominatorOperator.MEET, pred);
    }
  }

  /**
   * Initialize the lattice variables (Dominator sets) for
   * each basic block.
   */
  protected void initializeLatticeCells() {
    if (OPT_Dominators.COMPUTE_POST_DOMINATORS) {
      OPT_BasicBlock exit = ir.cfg.exit();
      OPT_DominatorCell last = (OPT_DominatorCell) getCell(exit);
      for (final OPT_DF_LatticeCell latticeCell : cells.values()) {
        OPT_DominatorCell cell = (OPT_DominatorCell) latticeCell;
        if (cell == last) {
          cell.addSingleBlock(cell.block);
        } else {
          cell.setTOP(ir);
        }
      }
    } else {
      OPT_BasicBlock start = ir.cfg.entry();
      OPT_DominatorCell first = (OPT_DominatorCell) getCell(start);
      for (final OPT_DF_LatticeCell latticeCell : cells.values()) {
        OPT_DominatorCell cell = (OPT_DominatorCell) latticeCell;
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
    if (OPT_Dominators.COMPUTE_POST_DOMINATORS) {
      // Add every equation to work list (to be safe)
      // WARNING: an "end node" may be part of a cycle
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
           e.hasMoreElements();) {
        OPT_BasicBlock bb = e.next();
        addCellAppearancesToWorkList(getCell(bb));
      }
    } else {
      OPT_DominatorCell first = (OPT_DominatorCell) getCell(ir.cfg.entry());
      addCellAppearancesToWorkList(first);
    }
  }

  /**
   * Get the OPT_DF_LatticeCell key corresponding to a basic block
   * @param bb the basic block
   * @return the key (just the block itself)
   */
  Object getKey(OPT_BasicBlock bb) {
    return bb;
  }

  /**
   * Make a new OPT_DF_LatticeCell key corresponding to a basic block
   * @param key the basic block
   * @return the new cell
   */
  protected OPT_DF_LatticeCell makeCell(Object key) {
    return new OPT_DominatorCell((OPT_BasicBlock) key, ir);
  }

  /**
   * Return a list of lattice cells corresponding to the
   * predecessors of a basic block.
   * @param bb the basic block
   */
  OPT_DF_LatticeCell[] getCellsForPredecessors(OPT_BasicBlock bb) {
    if (OPT_Dominators.COMPUTE_POST_DOMINATORS) {
      /****
       if ( bb.mayThrowUncaughtException() ) {
       if (OPT_Dominators.DEBUG) VM.sysWrite("LOCATION #1 ...\n");
       // Include exit node as an output node
       OPT_DF_LatticeCell s[] = new OPT_DF_LatticeCell[bb.getNumberOfOut()+1];
       OPT_BasicBlockEnumeration e = bb.getOut();
       for (int i=0; i<s.length-1; i++ ) {
       OPT_BasicBlock p = e.next();
       s[i] = findOrCreateCell(getKey(p));
       }
       s[s.length-1] = findOrCreateCell(getKey(ir.cfg.exit()));
       return s;
       }
       else
       ****/
      {
        if (OPT_Dominators.DEBUG) {
          VM.sysWrite("LOCATION #2 ...\n");
        }
        OPT_DF_LatticeCell[] s = new OPT_DF_LatticeCell[bb.getNumberOfOut()];
        OPT_BasicBlockEnumeration e = bb.getOut();
        for (int i = 0; i < s.length; i++) {
          OPT_BasicBlock p = e.next();
          s[i] = findOrCreateCell(getKey(p));
        }
        return s;
      }
    } else {
      if (OPT_Dominators.DEBUG) {
        System.out.println("LOCATION #3 ...");
      }
      OPT_DF_LatticeCell[] s = new OPT_DF_LatticeCell[bb.getNumberOfIn()];
      OPT_BasicBlockEnumeration e = bb.getIn();
      for (int i = 0; i < s.length; i++) {
        OPT_BasicBlock p = e.next();
        s[i] = findOrCreateCell(getKey(p));
      }
      return s;
    }
  }
}
