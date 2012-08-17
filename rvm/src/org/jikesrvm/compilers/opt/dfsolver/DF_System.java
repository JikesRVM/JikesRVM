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
package org.jikesrvm.compilers.opt.dfsolver;

import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import org.jikesrvm.compilers.opt.util.FilterEnumerator;
import org.jikesrvm.compilers.opt.util.Graph;
import org.jikesrvm.compilers.opt.util.GraphNode;
import org.jikesrvm.compilers.opt.util.GraphUtilities;
import org.jikesrvm.compilers.opt.util.ReverseDFSenumerateByFinish;

/**
 * Represents a system of Data Flow equations
 *
 * <p> Implementation Note:
 *      The set of equations is internally represented as a graph
 *      (actually a SpaceEffGraph).  Each dataflow equation is a node in the
 *      graph.  If a dataflow equation produces a lattice cell value
 *      that is used by another equation, the graph has a directed edge
 *      from the producer to the consumer.  Fixed-point iteration proceeds
 *      in a topological order according to these edges.
 */
public abstract class DF_System {
  private static final boolean DEBUG = false;

  private final boolean EAGER;

  /**
   * The equations that comprise this dataflow system.
   */
  private final Graph equations = new DF_Graph();

  /**
   * Set of equations pending evaluation
   */
  protected final TreeSet<DF_Equation> workList = new TreeSet<DF_Equation>(dfComparator);

  /**
   * Set of equations considered "new"
   */
  private final HashSet<DF_Equation> newEquations = new HashSet<DF_Equation>();

  /**
   * The lattice cells of the system: Mapping from Object to DF_LatticeCell
   */
  protected final DF_Solution cells = new DF_Solution();

  public DF_System() {
    EAGER = false;
  }

  public DF_System(boolean eager) {
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
      DF_Equation eq = workList.first();
      workList.remove(eq);
      boolean change = eq.evaluate();
      if (DEBUG) {
        System.out.println("After evaluation " + eq);
      }
      if (change) {
        updateWorkList(eq);
      }
    }
  }

  /**
   * Return the solution of the dataflow equation system.
   * This is only valid after calling solve()
   *
   * @return the solution
   */
  public DF_Solution getSolution() {
    return cells;
  }

  /**
   * Return a string representation of the system
   * @return a string representation of the system
   */
  @Override
  public String toString() {
    String result = "EQUATIONS:\n";
    Enumeration<GraphNode> v = equations.enumerateNodes();
    for (int i = 0; i < equations.numberOfNodes(); i++) {
      result = result + i + " : " + v.nextElement() + "\n";
    }
    return result;
  }

  /**
   * Return an Enumeration over the equations in this system.
   * @return an Enumeration over the equations in this system
   */
  public Enumeration<DF_Equation> getEquations() {
    return new FilterEnumerator<GraphNode, DF_Equation>(equations.enumerateNodes(),
        new FilterEnumerator.Filter<GraphNode, DF_Equation>() {
          @Override
          public boolean isElement(GraphNode x) {
            return x instanceof DF_Equation;
          }
        });
  }

  /**
   * Get the number of equations in this system
   * @return the number of equations in this system
   */
  public int getNumberOfEquations() {
    return equations.numberOfNodes();
  }

  /**
   * Add an equation to the work list.
   * @param eq the equation to add
   */
  public void addToWorkList(DF_Equation eq) {
    workList.add(eq);
  }

  /**
   * Add all new equations to the work list.
   */
  public void addNewEquationsToWorkList() {
    if (DEBUG) {
      System.out.println("new equations:");
    }
    for (DF_Equation eq : newEquations) {
      if (DEBUG) {
        System.out.println(eq.toString());
      }
      addToWorkList(eq);
    }
    newEquations.clear();
    if (DEBUG) {
      System.out.println("end of new equations");
    }
  }

  /**
   * Add all equations to the work list.
   */
  public void addAllEquationsToWorkList() {
    for (Enumeration<DF_Equation> e = getEquations(); e.hasMoreElements();) {
      DF_Equation eq = e.nextElement();
      addToWorkList(eq);
    }
  }

  /**
   * Call this method when the contents of a lattice cell
   * changes.  This routine adds all equations using this cell
   * to the set of new equations.
   * @param cell the lattice cell that has changed
   */
  public void changedCell(DF_LatticeCell cell) {
    Iterator<DF_Equation> e = cell.getUses();
    while (e.hasNext()) {
      newEquations.add(e.next());
    }
  }

  /**
   * Find the cell matching this key. If none found, create one.
   *
   * @param key the key for the lattice cell.
   */
  protected DF_LatticeCell findOrCreateCell(Object key) {
    DF_LatticeCell result = cells.get(key);
    if (result == null) {
      result = makeCell(key);
      cells.put(key, result);
    }
    return result;
  }

  /**
   * Add an equation with one operand on the right-hand side.
   *
   * @param lhs the lattice cell set by this equation
   * @param operator the equation operator
   * @param op1 first operand on the rhs
   */
  protected void newEquation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1) {
    // add to the list of equations
    DF_Equation eq = new DF_Equation(lhs, operator, op1);
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
  void newEquation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1, DF_LatticeCell op2) {
    // add to the list of equations
    DF_Equation eq = new DF_Equation(lhs, operator, op1, op2);
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
  void newEquation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell op1, DF_LatticeCell op2,
                   DF_LatticeCell op3) {
    // add to the list of equations
    DF_Equation eq = new DF_Equation(lhs, operator, op1, op2, op3);
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
  protected void newEquation(DF_LatticeCell lhs, DF_Operator operator, DF_LatticeCell[] rhs) {
    // add to the list of equations
    DF_Equation eq = new DF_Equation(lhs, operator, rhs);
    equations.addGraphNode(eq);
    equations.addGraphNode(lhs);
    newEquations.add(eq);
    // add the operands to the working solution
    //       cells.put(lhs.getKey(),lhs);
    for (DF_LatticeCell rh : rhs) {
      //        cells.put(rhs[i].getKey(),rhs[i]);
      rh.addUse(eq);
      equations.addGraphNode(rh);
    }
    lhs.addDef(eq);
    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /**
   * Add an existing equation to the system
   *
   * @param eq the equation
   */
  void addEquation(DF_Equation eq) {
    equations.addGraphNode(eq);
    newEquations.add(eq);

    DF_LatticeCell lhs = eq.getLHS();
    if (!(lhs.getDefs().hasNext() || lhs.getUses().hasNext())) {
      lhs.addDef(eq);
      equations.addGraphNode(lhs);
    } else {
      lhs.addDef(eq);
    }

    DF_LatticeCell[] operands = eq.getOperands();
    for (int i = 1; i < operands.length; i++) {
      DF_LatticeCell op = operands[i];
      if (!(op.getDefs().hasNext() || op.getUses().hasNext())) {
        op.addUse(eq);
        equations.addGraphNode(op);
      } else {
        op.addUse(eq);
      }
    }

    if (EAGER && eq.evaluate()) changedCell(lhs);
  }

  /**
   * Return the DF_LatticeCell corresponding to a key.
   *
   * @param key the key
   * @return the LatticeCell if found. null otherwise
   */
  public DF_LatticeCell getCell(Object key) {
    return cells.get(key);
  }

  /**
   * Add all equations which contain a given cell to the work list.
   * @param cell the cell in question
   */
  public void addCellAppearancesToWorkList(DF_LatticeCell cell) {
    for (Enumeration<DF_Equation> e = getEquations(); e.hasMoreElements();) {
      DF_Equation eq = e.nextElement();
      if (eq.hasCell(cell)) {
        addToWorkList(eq);
      }
    }
  }

  private static final Comparator<DF_Equation> dfComparator = new Comparator<DF_Equation>() {
    @Override
    public int compare(DF_Equation o1, DF_Equation o2) {
      DF_Equation eq1 = o1;
      DF_Equation eq2 = o2;
      return (eq1.topologicalNumber - eq2.topologicalNumber);
    }
  };

  /**
   * Initialize all lattice cells in the system.
   */
  protected abstract void initializeLatticeCells();

  /**
   * Initialize the work list for iteration.j
   */
  protected abstract void initializeWorkList();

  /**
   * Create a new lattice cell, referenced by a given key
   * @param key key to look up the new cell with
   * @return the newly created lattice cell
   */
  protected abstract DF_LatticeCell makeCell(Object key);

  /**
   * Update the worklist, assuming that a particular equation
   * has been re-evaluated
   *
   * @param eq the equation that has been re-evaluated.
   */
  protected void updateWorkList(DF_Equation eq) {
    // find each equation which uses this lattice cell, and
    // add it to the work list
    Iterator<DF_Equation> e = eq.getLHS().getUses();
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
    Enumeration<GraphNode> topOrder = GraphUtilities.
        enumerateTopSort(equations);
    Enumeration<GraphNode> rev = new ReverseDFSenumerateByFinish(equations, topOrder);
    int number = 0;
    while (rev.hasMoreElements()) {
      GraphNode elt = rev.nextElement();
      if (elt instanceof DF_Equation) {
        DF_Equation eq = (DF_Equation) elt;
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
    for (Enumeration<GraphNode> e = equations.enumerateNodes(); e.hasMoreElements();) {
      GraphNode eq = e.nextElement();
      Enumeration<GraphNode> outs = eq.outNodes();
      while (outs.hasMoreElements()) {
        count++;
        outs.nextElement();
      }
    }
    System.out.println("graph has " + count + " edges");
  }
}
