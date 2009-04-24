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

import java.util.Iterator;

import org.jikesrvm.compilers.opt.util.GraphNode;

/**
 * DF_LatticeCell.java
 *
 * Represents a single lattice cell in a dataflow equation system.
 */
public interface DF_LatticeCell extends GraphNode {

  /**
   * Returns an enumeration of the equations in which this
   * lattice cell is used.
   * @return an enumeration of the equations in which this
   * lattice cell is used
   */
  Iterator<DF_Equation> getUses();

  /**
   * Returns an enumeration of the equations in which this
   * lattice cell is defined.
   * @return an enumeration of the equations in which this
   * lattice cell is defined
   */
  Iterator<DF_Equation> getDefs();

  /**
   * Return a string representation of the cell
   * @return a string representation of the cell
   */
  String toString();

  /**
   * Note that this variable appears on the RHS of an equation
   *
   * @param eq the equation
   */
  void addUse(DF_Equation eq);

  /**
   * Note that this variable appears on the LHS of an equation
   *
   * @param eq the equation
   */
  void addDef(DF_Equation eq);
}



