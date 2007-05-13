/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.util.HashSet;
import java.util.Iterator;

/**
 * OPT_DF_LatticeCell.java
 *
 * Represents a single lattice cell in a dataflow system.
 */
abstract class OPT_DF_AbstractCell
    implements OPT_DF_LatticeCell {

  /**
   * Default Constructor 
   */
  OPT_DF_AbstractCell () {
    uses = new HashSet<OPT_DF_Equation>(1);
    defs = new HashSet<OPT_DF_Equation>(1);
  }

  /** 
   * This constructor bounds the initial capacity to save space.
   * @param capacity the initial capacity of the "uses" set
   */
  OPT_DF_AbstractCell (int capacity) {
    uses = new HashSet<OPT_DF_Equation>(capacity);
    defs = new HashSet<OPT_DF_Equation>(capacity);
  }

  /** 
   * Returns an enumeration of the equations in which this
   * lattice cell is used.
   * @return an enumeration of the equations in which this
   * lattice cell is used
   */
  public Iterator<OPT_DF_Equation> getUses () {
    return  uses.iterator();
  }

  /** 
   * Return an enumeration of the equations in which this
   * lattice cell is defined.
   * @return an enumeration of the equations in which this
   * lattice cell is defined
   */
  public Iterator<OPT_DF_Equation> getDefs () {
    return  defs.iterator();
  }

  /** 
   * Return a string representation of the cell
   * @return a string representation of the cell
   */
  public abstract String toString ();

  /** 
   * Note that this variable appears on the RHS of an equation.
   *
   * @param eq the equation
   */
  public void addUse (OPT_DF_Equation eq) {
    uses.add(eq);
  }

  /** 
   * Note that this variable appears on the LHS of an equation. 
   *
   * @param eq the equation
   */
  public void addDef (OPT_DF_Equation eq) {
    defs.add(eq);
  }

  /**
   * Set of OPT_DF_Equations which use this lattice cell.
   */
  private final HashSet<OPT_DF_Equation> uses; 
  /**
   * Set of OPT_DF_Equations which define this lattice cell.
   */
  private final HashSet<OPT_DF_Equation> defs; 

  public OPT_GraphNodeEnumeration inNodes() {
      return new OPT_GraphNodeEnumeration() {
              private Iterator<OPT_DF_Equation> i = defs.iterator();
              public boolean hasMoreElements() { return i.hasNext(); }
              public OPT_GraphNode next() { return i.next(); }
              public OPT_GraphNode nextElement() { return next(); }
          };
  }

  public OPT_GraphNodeEnumeration outNodes() {
      return new OPT_GraphNodeEnumeration() {
              private Iterator<OPT_DF_Equation> i = uses.iterator();
              public boolean hasMoreElements() { return i.hasNext(); }
              public OPT_GraphNode next() { return i.next(); }
              public OPT_GraphNode nextElement() { return next(); }
          };
  }

  /**
   * Field used for OPT_GraphNode interface.  TODO: is this needed?
   */
  private int index;

  /**
   * Implementation of OPT_GraphNode interface.  
   */
  public void setIndex (int i) {
    index = i;
  }

  /**
   * Implementation of OPT_GraphNode interface.  
   */
  public int getIndex () {
    return  index;
  }

  private int scratch;

  public int getScratch () {
    return  scratch;
  }
  public int setScratch (int o) {
    return  (scratch = o);
  }

}



