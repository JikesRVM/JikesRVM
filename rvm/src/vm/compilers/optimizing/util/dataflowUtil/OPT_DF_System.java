/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;

/**
 * Represents a system of Data Flow equations
 *
 * <p> Implementation Note:
 *      The set of equations is internally represented as a graph
 *      (actually a OPT_SpaceEffGraph).  Each dataflow equation is a node in the
 *      graph.  If a dataflow equation produces a lattice cell value
 *      that is used by another equation, the graph has a directed edge
 *      from the producer to the consumer.  Fixed-point iteration proceeds
 *      in a topological order according to these edges.
 *
 *
 * @author Stephen Fink
 *
 */
public abstract class OPT_DF_System implements OPT_Solvable {
  static final boolean DEBUG = false;

  final boolean EAGER;

  OPT_DF_System() {
    EAGER = false;
  }


  OPT_DF_System(boolean eager) {
    EAGER = eager;
  }

  /** 
   * Solve the set of dataflow equations.
   * <p> PRECONDITION: equations are set up
   */
  public void solve() {
    // addGraphEdges();
    numberEquationsTopological();
    initializeLatticeCells();
    initializeWorkList();
    while (!workList.isEmpty()) {
      OPT_DF_Equation eq = (OPT_DF_Equation)workList.first();
      workList.remove(eq);
      boolean change = eq.evaluate();
      if (DEBUG)
        System.out.println("After evaluation " + eq);
      if (change)
        updateWorkList(eq);
    }
  }

  /** 
   * Return the solution of the dataflow equation system.
   * This is only valid after calling solve()
   *
   * @return the solution
   */
  public OPT_DF_Solution getSolution() {
    return  cells;
  }

  /** 
   * Return a string representation of the system 
   * @return a string representation of the system 
   */
  public String toString() {
    String result = "EQUATIONS:\n";
    Enumeration v = equations.enumerateNodes();
    for (int i = 0; i < equations.numberOfNodes(); i++) {
      result = result + i + " : " + v.nextElement() + "\n";
    }
    return  result;
  }

  /** 
   * Return an Enumeration over the equations in this system.
   * @return an Enumeration over the equations in this system 
   */
  public Enumeration getEquations() {
    return new OPT_FilterEnumerator(
                                    equations.enumerateNodes(),
                                    new OPT_FilterEnumerator.Filter() {
                                    public boolean isElement(Object x) {
                                    return x instanceof OPT_DF_Equation;
                                    }
                                    }
                                   );
  }

  /** 
   * Get the number of equations in this system
   * @return the number of equations in this system
   */
  public int getNumberOfEquations() {
    return  equations.numberOfNodes();
  }

  /** 
   * Add an equation to the work list.
   * @param eq the equation to add
   */
  public void addToWorkList(OPT_DF_Equation eq) {
    workList.add(eq);
  }

  /** 
   * Add all new equations to the work list. 
   */
  public void addNewEquationsToWorkList() {
    if (DEBUG)
      System.out.println("new equations:");
    for (java.util.Iterator e = newEquations.iterator(); e.hasNext();) {
      OPT_DF_Equation eq = (OPT_DF_Equation)e.next();
      if (DEBUG)
        System.out.println(eq.toString());
      addToWorkList(eq);
    }
    newEquations = new java.util.HashSet();
    if (DEBUG)
      System.out.println("end of new equations");
  }

  /**
   * Add all equations to the work list. 
   */
  public void addAllEquationsToWorkList() {
    for (Enumeration e = getEquations(); e.hasMoreElements();) {
      OPT_DF_Equation eq = (OPT_DF_Equation)e.nextElement();
      addToWorkList(eq);
    }
  }

  /**
   * Call this method when the contents of a lattice cell 
   * changes.  This routine adds all equations using this cell
   * to the set of new equations.
   * @param cell the lattice cell that has changed
   */
  public void changedCell(OPT_DF_LatticeCell cell) {
    java.util.Iterator e = cell.getUses();
    while (e.hasNext()) {
      newEquations.add((OPT_DF_Equation)e.next());
    }
  }

  /** 
   * Find the cell matching this key. If none found, create one.
   *
   * @param key the key for the lattice cell.
   */
  OPT_DF_LatticeCell findOrCreateCell(Object key) {
    OPT_DF_LatticeCell result = (OPT_DF_LatticeCell)cells.get(key);
    if (result == null) {
      result = makeCell(key);
      cells.put(key, result);
    }
    return  result;
  }

  /** 
   * Add an equation with one operand on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   */
  void newEquation(OPT_DF_LatticeCell lhs, OPT_DF_Operator operator, 
                   OPT_DF_LatticeCell op1) {
    // add to the list of equations
    OPT_DF_Equation eq = new OPT_DF_Equation(lhs, operator, op1);
    equations.addGraphNode(eq);
    equations.addGraphNode(lhs);
    equations.addGraphNode(op1);
    newEquations.add(eq);
    // add lattice cells for the operands to the working solution
    //       cells.put(lhs.getKey(),lhs);
    //       cells.put(op1.getKey(),op1);
    op1.addUse(eq);
    lhs.addDef(eq);
    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /** 
   * Add an equation with two operands on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   */
  void newEquation(OPT_DF_LatticeCell lhs, OPT_DF_Operator operator, 
                   OPT_DF_LatticeCell op1, OPT_DF_LatticeCell op2) {
    // add to the list of equations
    OPT_DF_Equation eq = new OPT_DF_Equation(lhs, operator, op1, op2);
    equations.addGraphNode(eq);
    equations.addGraphNode(lhs);
    equations.addGraphNode(op1);
    equations.addGraphNode(op2);
    newEquations.add(eq);
    // add lattice cells for the operands to the working solution
    //       cells.put(lhs.getKey(),lhs);
    //       cells.put(op1.getKey(),op1);
    op1.addUse(eq);
    //       cells.put(op2.getKey(),op2);
    op2.addUse(eq);
    lhs.addDef(eq);
    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /** 
   * Add an equation with three operands on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   * @param op2 second operand on the rhs
   * @param op3 third operand on the rhs
   */
  void newEquation(OPT_DF_LatticeCell lhs, OPT_DF_Operator operator, 
                   OPT_DF_LatticeCell op1, OPT_DF_LatticeCell op2, OPT_DF_LatticeCell op3) {
    // add to the list of equations
    OPT_DF_Equation eq = new OPT_DF_Equation(lhs, operator, op1, op2, 
                                             op3);
    equations.addGraphNode(eq);
    equations.addGraphNode(lhs);
    equations.addGraphNode(op1);
    equations.addGraphNode(op2);
    equations.addGraphNode(op3);
    newEquations.add(eq);
    // add lattice cells for the operands to the working solution
    //       cells.put(lhs.getKey(),lhs);
    //       cells.put(op1.getKey(),op1);
    op1.addUse(eq);
    //       cells.put(op2.getKey(),op2);
    op2.addUse(eq);
    //       cells.put(op3.getKey(),op3);
    op3.addUse(eq);
    lhs.addDef(eq);
    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /** 
   * Add an equation to the system with an arbitrary number of operands on
   * the right-hand side.
   *
   * @param lhs lattice cell set by this equation
   * @param operator the equation operator
   * @param rhs the operands on the rhs
   */
  void newEquation(OPT_DF_LatticeCell lhs, OPT_DF_Operator operator, 
                   OPT_DF_LatticeCell[] rhs) {
    // add to the list of equations
    OPT_DF_Equation eq = new OPT_DF_Equation(lhs, operator, rhs);
    equations.addGraphNode(eq);
    equations.addGraphNode(lhs);
    newEquations.add(eq);
    // add the operands to the working solution
    //       cells.put(lhs.getKey(),lhs);
    for (int i = 0; i < rhs.length; i++) {
      //        cells.put(rhs[i].getKey(),rhs[i]);
      rhs[i].addUse(eq);
      equations.addGraphNode(rhs[i]);
    }
    lhs.addDef(eq);
    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /** 
   * Add an existing equation to the system
   *
   * @param eq the equation
   */
  void addEquation(OPT_DF_Equation eq) {
    equations.addGraphNode(eq);
    newEquations.add(eq);

    OPT_DF_LatticeCell lhs = eq.getLHS();
    if (! (lhs.getDefs().hasNext()||lhs.getUses().hasNext())) {
      lhs.addDef(eq);
      equations.addGraphNode(lhs);
    } else
      lhs.addDef(eq);

    OPT_DF_LatticeCell operands[] = eq.getOperands();
    for(int i = 1; i < operands.length; i++) {
      OPT_DF_LatticeCell op = operands[i];
      if (! (op.getDefs().hasNext()||op.getUses().hasNext())) {
        op.addUse(eq);
        equations.addGraphNode(op);
      } else
        op.addUse(eq);
    }

    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /** 
   * Return the OPT_DF_LatticeCell corresponding to a key.
   *
   * @param key the key
   * @return the LatticeCell if found. null otherwise
   */
  public OPT_DF_LatticeCell getCell(Object key) {
    return  (OPT_DF_LatticeCell)cells.get(key);
  }

  /** 
   * Add all equations which contain a given cell to the work list.
   * @param cell the cell in question
   */
  public void addCellAppearancesToWorkList(OPT_DF_LatticeCell cell) {
    for (Enumeration e = getEquations(); e.hasMoreElements();) {
      OPT_DF_Equation eq = (OPT_DF_Equation)e.nextElement();
      if (eq.hasCell(cell)) {
        addToWorkList(eq);
      }
    }
  }

  /**
   * The equations that comprise this dataflow system.
   */
  OPT_Graph equations = new OPT_DF_Graph();

  /**
   * Set of equations pending evaluation
   */
  protected java.util.SortedSet workList = new java.util.TreeSet(
                                                                 new java.util.Comparator() {
                                                                 public int compare(Object o1, Object o2) {
                                                                 if (!(o1 instanceof OPT_DF_Equation))
                                                                 throw  new OPT_OptimizingCompilerException();
                                                                 if (!(o2 instanceof OPT_DF_Equation))
                                                                 throw  new OPT_OptimizingCompilerException();
                                                                 OPT_DF_Equation eq1 = (OPT_DF_Equation)o1;
                                                                 OPT_DF_Equation eq2 = (OPT_DF_Equation)o2;
                                                                 return  (eq1.topologicalNumber - eq2.topologicalNumber);
                                                                 }
                                                                 });

  /**
   * Set of equations considered "new"
   */
  java.util.Set newEquations = new java.util.HashSet();
  /**
   * The lattice cells of the system: Mapping from Object to OPT_DF_LatticeCell
   */
  protected OPT_DF_Solution cells = new OPT_DF_Solution();

  /**
   * Initialize all lattice cells in the system.
   */
  abstract protected void initializeLatticeCells();

  /**
   * Initialize the work list for iteration.j
   */
  abstract protected void initializeWorkList();

  /**
   * Create a new lattice cell, referenced by a given key
   * @param key key to look up the new cell with
   * @return the newly created lattice cell
   */
  abstract protected OPT_DF_LatticeCell makeCell(Object key);

  /** 
   * Update the worklist, assuming that a particular equation
   * has been re-evaluated
   *
   * @param eq the equation that has been re-evaluated.
   */
  protected void updateWorkList(OPT_DF_Equation eq) {
    // find each equation which uses this lattice cell, and
    // add it to the work list
    java.util.Iterator e = eq.getLHS().getUses();
    while (e.hasNext()) {
      workList.add(e.next());
    }
  }

  /**
   *  Number the equations in topological order.
   *
   *  <p> PRECONDITION: Already called addGraphEdges()
   * 
   *  <p>Algorithm: 
   *   <ul> 
   *   <li>     1. create a DAG of SCCs
   *   <li>     2. number this DAG topologically
   *   <li>     3. walk through the DAG and number nodes as they are
   *               encountered
   *    </ul>
   */
  private void numberEquationsTopological() {
    OPT_GraphNodeEnumeration topOrder = OPT_GraphUtilities.
      enumerateTopSort(equations);
    Enumeration rev = new OPT_ReverseDFSenumerateByFinish(equations, topOrder);
    int number = 0;
    while (rev.hasMoreElements()) {
      Object elt = rev.nextElement();
      if (elt instanceof OPT_DF_Equation) {
        OPT_DF_Equation eq = (OPT_DF_Equation)elt;
        eq.setTopologicalNumber(number++);
      }
    }
  }

  /**
   * Debugging aid: print statistics about the dataflow system.
   */
  void showGraphStats() {
    System.out.println("graph has " + equations.numberOfNodes() + " nodes");
    int count = 0;
    for (Enumeration e = equations.enumerateNodes(); e.hasMoreElements();) {
      OPT_GraphNode eq = (OPT_GraphNode)e.nextElement();
      OPT_GraphNodeEnumeration outs = eq.outNodes();
      while (outs.hasMoreElements()) {
        count++;
        outs.next();
      }
    }
    System.out.println("graph has " + count + " edges");
  }
}
