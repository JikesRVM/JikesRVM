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

import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.compilers.opt.util.GraphNode;
import org.jikesrvm.compilers.opt.util.GraphNodeEnumeration;

/**
 * DF_LatticeCell.java
 *
 * Represents a single lattice cell in a dataflow system.
 */
public abstract class DF_AbstractCell implements DF_LatticeCell {
  /**
   * Set of DF_Equations which use this lattice cell.
   */
  private final HashSet<DF_Equation> uses;
  /**
   * Set of DF_Equations which define this lattice cell.
   */
  private final HashSet<DF_Equation> defs;

  /**
   * Default Constructor
   */
  public DF_AbstractCell() {
    uses = new HashSet<DF_Equation>(1);
    defs = new HashSet<DF_Equation>(1);
  }

  /**
   * This constructor bounds the initial capacity to save space.
   * @param capacity the initial capacity of the "uses" set
   */
  public DF_AbstractCell(int capacity) {
    uses = new HashSet<DF_Equation>(capacity);
    defs = new HashSet<DF_Equation>(capacity);
  }

  /**
   * Returns an enumeration of the equations in which this
   * lattice cell is used.
   * @return an enumeration of the equations in which this
   * lattice cell is used
   */
  public Iterator<DF_Equation> getUses() {
    return uses.iterator();
  }

  /**
   * Return an enumeration of the equations in which this
   * lattice cell is defined.
   * @return an enumeration of the equations in which this
   * lattice cell is defined
   */
  public Iterator<DF_Equation> getDefs() {
    return defs.iterator();
  }

  /**
   * Return a string representation of the cell
   * @return a string representation of the cell
   */
  public abstract String toString();

  /**
   * Note that this variable appears on the RHS of an equation.
   *
   * @param eq the equation
   */
  public void addUse(DF_Equation eq) {
    uses.add(eq);
  }

  /**
   * Note that this variable appears on the LHS of an equation.
   *
   * @param eq the equation
   */
  public void addDef(DF_Equation eq) {
    defs.add(eq);
  }

  public GraphNodeEnumeration inNodes() {
    return new GraphNodeEnumeration() {
      private final Iterator<DF_Equation> i = defs.iterator();

      public boolean hasMoreElements() { return i.hasNext(); }

      public GraphNode next() { return i.next(); }

      public GraphNode nextElement() { return next(); }
    };
  }

  public GraphNodeEnumeration outNodes() {
    return new GraphNodeEnumeration() {
      private final Iterator<DF_Equation> i = uses.iterator();

      public boolean hasMoreElements() { return i.hasNext(); }

      public GraphNode next() { return i.next(); }

      public GraphNode nextElement() { return next(); }
    };
  }

  /**
   * Field used for GraphNode interface.  TODO: is this needed?
   */
  private int index;

  /**
   * Implementation of GraphNode interface.
   */
  public void setIndex(int i) {
    index = i;
  }

  /**
   * Implementation of GraphNode interface.
   */
  public int getIndex() {
    return index;
  }

  private int scratch;

  public int getScratch() {
    return scratch;
  }

  public int setScratch(int o) {
    return (scratch = o);
  }
}



