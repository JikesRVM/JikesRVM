/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

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
 *
 * @author Stephen Fink
 *
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
  public static void perform (OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers())
      throw  new OPT_OperationNotImplementedException(
          "IR with exception handlers");
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
    if (DEBUG)
      System.out.print("Solving...");
    if (DEBUG)
      System.out.println(system);
    system.solve();
    if (DEBUG)
      System.out.println("done");
    OPT_DF_Solution solution = system.getSolution();
    if (DEBUG)
      System.out.println("Dominator Solution :" + solution);
    if (DEBUG)
      System.out.print("Updating blocks ...");
    updateBlocks(solution);
    if (DEBUG)
      System.out.println("done.");
    if (ir.options.PRINT_DOMINATORS)
      printDominators(ir);
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
  public static void computeApproxDominators (OPT_IR ir) {
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
    if (DEBUG)
      System.out.print("Solving...");
    if (DEBUG)
      System.out.println(system);
    system.solve();
    if (DEBUG)
      System.out.println("done");
    OPT_DF_Solution solution = system.getSolution();
    if (DEBUG)
      System.out.println("Dominator Solution :" + solution);
    if (DEBUG)
      System.out.print("Updating blocks ...");
    updateBlocks(solution);
    if (DEBUG)
      System.out.println("done.");
    if (ir.options.PRINT_DOMINATORS)
      printDominators(ir);
  }

  /** 
   * Calculate the postdominators for an IR.
   * <p> After this pass, each basic block's scrach field points to
   * an OPT_DominatorInfo object, which holds the postdominators
   * of the basic block.
   *
   * @param ir the IR in question
   */
  public static void computeApproxPostdominators (OPT_IR ir) {
    OPT_Dominators.COMPUTE_POST_DOMINATORS = true;
    OPT_DominatorSystem system = new OPT_DominatorSystem(ir);
    if (DEBUG)
      System.out.print("Solving...");
    if (DEBUG)
      System.out.println(system);
    system.solve();
    if (DEBUG)
      System.out.println("done");
    OPT_DF_Solution solution = system.getSolution();
    if (DEBUG)
      System.out.println("Postdominator Solution :" + solution);
    if (DEBUG)
      System.out.print("Updating blocks ...");
    updateBlocks(solution);
    if (DEBUG)
      System.out.println("done.");
    if (ir.options.PRINT_DOMINATORS)
      printDominators(ir);
    OPT_Dominators.COMPUTE_POST_DOMINATORS = false;
  }

  /** 
   * For each basic block in the data flow system solution,
   * create an <code> OPT_DominatorInfo </code> and store it in the basic
   * blocks scratchObject
   *
   * @param solution the solution to the Dominators equations
   */
  public static void updateBlocks (OPT_DF_Solution solution) {
    for (java.util.Iterator e = solution.values().iterator(); e.hasNext();) {
      OPT_DominatorCell cell = (OPT_DominatorCell)e.next();
      OPT_BasicBlock b = cell.block;
      b.scratchObject = new OPT_DominatorInfo(cell.dominators);
      if (DEBUG)
        System.out.println("Dominators of " + b + ":" + cell.dominators);
    }
  }

  /** 
   * Print the (already calculated) dominators.
   * @param ir the IR
   */
  public static void printDominators (OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
        e.hasMoreElements();) {
      OPT_BasicBlock b = e.next();
      OPT_DominatorInfo i = (OPT_DominatorInfo)b.scratchObject;
      System.out.println("Dominators of " + b + ":" + i.dominators);
    }
  }
}


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
  public OPT_DominatorSystem (OPT_IR ir) {
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
  void setupEquations () {
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
  protected void initializeLatticeCells () {
    if (OPT_Dominators.COMPUTE_POST_DOMINATORS) {
      OPT_BasicBlock exit = ir.cfg.exit();
      OPT_DominatorCell last = (OPT_DominatorCell)getCell(exit);
      for (java.util.Iterator e = cells.values().iterator(); e.hasNext();) {
        OPT_DominatorCell cell = (OPT_DominatorCell)e.next();
        if (cell == last)
          cell.addSingleBlock(cell.block); 
        else 
          cell.setTOP(ir);
      }
    } 
    else {
      OPT_BasicBlock start = ir.cfg.entry();
      OPT_DominatorCell first = (OPT_DominatorCell)getCell(start);
      for (java.util.Iterator e = cells.values().iterator(); e.hasNext();) {
        OPT_DominatorCell cell = (OPT_DominatorCell)e.next();
        if (cell == first)
          cell.addSingleBlock(cell.block); 
        else 
          cell.setTOP(ir);
      }
    }
  }

  /**
   * Initialize the work list for the dataflow equation system.
   * <p> The initial work list is every equation containing the start
   * node.
   */
  protected void initializeWorkList () {
    if (OPT_Dominators.COMPUTE_POST_DOMINATORS) {
      // Add every equation to work list (to be safe)
      // WARNING: an "end node" may be part of a cycle
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
          e.hasMoreElements();) {
        OPT_BasicBlock bb = e.next();
        addCellAppearancesToWorkList((OPT_DominatorCell)getCell(bb));
      }
    } 
    else {
      OPT_DominatorCell first = (OPT_DominatorCell)getCell(ir.cfg.entry());
      addCellAppearancesToWorkList(first);
    }
  }

  /** 
   * Get the OPT_DF_LatticeCell key corresponding to a basic block
   * @param bb the basic block 
   * @return the key (just the block itself)
   */
  Object getKey (OPT_BasicBlock bb) {
    return  bb;
  }

  /** 
   * Make a new OPT_DF_LatticeCell key corresponding to a basic block
   * @param key the basic block 
   * @return the new cell
   */
  protected OPT_DF_LatticeCell makeCell (Object key) {
    return  new OPT_DominatorCell((OPT_BasicBlock)key,ir);
  }

  /** 
   * Return a list of lattice cells corresponding to the 
   * predecessors of a basic block.
   * @param bb the basic block
   */
  OPT_DF_LatticeCell[] getCellsForPredecessors (OPT_BasicBlock bb) {
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
        if (OPT_Dominators.DEBUG)
          VM.sysWrite("LOCATION #2 ...\n");
        OPT_DF_LatticeCell s[] = new OPT_DF_LatticeCell[bb.getNumberOfOut()];
        OPT_BasicBlockEnumeration e = bb.getOut();
        for (int i = 0; i < s.length; i++) {
          OPT_BasicBlock p = e.next();
          s[i] = findOrCreateCell(getKey(p));
        }
        return  s;
      }
    } 
    else {
      if (OPT_Dominators.DEBUG)
        System.out.println("LOCATION #3 ...");
      OPT_DF_LatticeCell s[] = new OPT_DF_LatticeCell[bb.getNumberOfIn()];
      OPT_BasicBlockEnumeration e = bb.getIn();
      for (int i = 0; i < s.length; i++) {
        OPT_BasicBlock p = e.next();
        s[i] = findOrCreateCell(getKey(p));
      }
      return  s;
    }
  }
}


/** 
 * OPT_DominatorCell represents a set of basic blocks, used in
 * the dataflow calculation
 */
class OPT_DominatorCell extends OPT_DF_AbstractCell {

  /**
   * Pointer to the governing IR.
   */
  OPT_IR ir;
  /**
   * The basic block corresponding to this lattice cell.
   */
  OPT_BasicBlock block;
  /**
   * Bit set representation of the dominators for this basic block.
   */
  OPT_BitVector dominators;
  /**
   * A guess of the upper bound on the number of out edges for most basic
   * blocks.
   */
  static final int CAPACITY = 5;  

  /** 
   * Make a bit set for a basic block 
   * @param bb the basic block
   * @param ir the governing IR
   */
  public OPT_DominatorCell (OPT_BasicBlock bb, OPT_IR ir) {
    super(CAPACITY);
    block = bb;
    dominators = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
    this.ir = ir;
  }

  /** 
   * Return a String representation of this cell.
   * @return a String representation of this cell.
   */
  public String toString () {
    return  block + ":" + dominators;
  }

  /** 
   * Include a single basic block in this set.
   * @param bb the basic block
   */
  public void addSingleBlock (OPT_BasicBlock bb) {
    dominators.set(bb.getNumber());
  }

  /** 
   * Include all basic blocks in this set.
   * <p> TODO: make this more efficient.
   * @param ir the governing ir
   */
  public void setTOP (OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); 
        e.hasMoreElements();) {
      OPT_BasicBlock b = e.next();
      dominators.set(b.getNumber());
    }
  }
}


/**
 * This class implements the MEET operation for the 
 * dataflow equations for the dominator calculation.
 */
class OPT_DominatorOperator extends OPT_DF_Operator {

  /** 
   * Evaluate an equation with the MEET operation 
   * @param operands the lhs(operands[0]) and rhs(operands[1])
   *       of the equation.
   * @return true if the value of the lhs changes. false otherwise
   */
  boolean evaluate (OPT_DF_LatticeCell[] operands) {
    OPT_DominatorCell lhs = (OPT_DominatorCell)operands[0];
    OPT_IR ir = lhs.ir;
    OPT_BasicBlock bb = lhs.block;
    OPT_BitVector oldSet = (OPT_BitVector)lhs.dominators.clone();
    OPT_BitVector newDominators = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
    if (operands.length > 1) {
      if (operands[1] != null) {
        newDominators.or(((OPT_DominatorCell)operands[1]).dominators);
      }
    }
    for (int i = 2; i < operands.length; i++) {
      newDominators.and(((OPT_DominatorCell)operands[i]).dominators);
    }
    newDominators.set(bb.getNumber());
    lhs.dominators = newDominators;
    if (lhs.dominators.equals(oldSet))
      return  false; 
    else 
      return  true;
  }

  /** 
   * Return a String representation of the operator
   * @return "MEET"
   */
  public String toString () {
    return  "MEET";
  }
  /**
   * A singleton instance of this class.
   */
  final static OPT_DominatorOperator MEET = new OPT_DominatorOperator();
}



