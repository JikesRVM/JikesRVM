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
package org.jikesrvm.compilers.opt.ssa;

import java.util.HashSet;
import java.util.Iterator;


/**
 * This class represents a congruence class for
 * global value numbering.
 */
final class GVCongruenceClass implements Iterable<ValueGraphVertex> {
  /**
   * A label representing a property of this congruence class.
   */
  private final Object label;
  /**
   * A value number representing this congruence class.
   */
  private final int valueNumber;
  /**
   * How many vertices in this congruence class represent parameters?
   */
  private int nParameter;
  /**
   * The set of vertices in this congruence class
   */
  private final HashSet<ValueGraphVertex> vertices;

  /**
   * A representative of the congruence class
   *   - saves having to find one
   */
  private ValueGraphVertex representativeV;

  /**
   * Create a congruence class with a given representative value number
   * and label.
   * @param     valueNumber the value number of the class
   * @param     label the label of the class
   */
  GVCongruenceClass(int valueNumber, Object label) {
    this.valueNumber = valueNumber;
    this.label = label;
    vertices = new HashSet<ValueGraphVertex>(1);
  }

  public Object getLabel() {
    return label;
  }

  public int getValueNumber() {
    return valueNumber;
  }

  /**
   * Do any of the vertices in this set represent a parameter?
   * <p> TODO: for efficiency, keep this information incrementally
   * @return true or false
   */
  public boolean containsParameter() {
    return (nParameter > 0);
  }

  /**
   * Add a vertex to this congruence class.
   * @param v the vertex to add
   */
  public void addVertex(ValueGraphVertex v) {
    if (vertices.add(v)) {
      if (v.representsParameter()) {
        nParameter++;
      }
      if (representativeV == null) {
        representativeV = v;
      }
    }
  }

  /**
   * Remove a vertex from this congruence class.
   * @param v the vertex to remove
   */
  public void removeVertex(ValueGraphVertex v) {
    if (vertices.remove(v)) {
      if (v.representsParameter()) {
        nParameter--;
      }
      if (representativeV == v) {
        // Try to find an alternate representative
        representativeV = vertices.iterator().next();
      }
    }
  }

  /**
   * Return a representative vertex for this congruence class.
   * @return a representative vertex for this congruence class.
   */
  public ValueGraphVertex getRepresentative() {
    return representativeV;
  }

  /**
   * Return an iterator over the vertices in this congruence class
   * @return an iterator over the vertices in this congruence class
   */
  @Override
  public Iterator<ValueGraphVertex> iterator() {
    return vertices.iterator();
  }

  /**
   * Return the number of vertices in this class
   * @return the number of vertices in this class
   */
  public int size() {
    return vertices.size();
  }
}
