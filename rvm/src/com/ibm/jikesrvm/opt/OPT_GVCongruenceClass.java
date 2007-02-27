/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

import  java.util.*;

/** 
 * This class represents a congruence class for
 * global value numbering.
 * @author Steve Fink
 */
final class OPT_GVCongruenceClass implements Iterable<OPT_ValueGraphVertex> {
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
  private final HashSet<OPT_ValueGraphVertex> vertices;

  /**
   * A representative of the congruence class
   *   - saves having to find one
   */
  private OPT_ValueGraphVertex  representativeV;
  
  /**
   * Create a congruence class with a given representative value number
   * and label.
   * @param     valueNumber the value number of the class
   * @param     label the label of the class
   */
  OPT_GVCongruenceClass (int valueNumber, Object label) {
    this.valueNumber = valueNumber;
    this.label = label;
    vertices = new HashSet<OPT_ValueGraphVertex>(1);
  }

  public Object getLabel () {
    return  label;
  }

  public int getValueNumber () {
    return  valueNumber;
  }

  /** 
   * Do any of the vertices in this set represent a parameter? 
   * <p> TODO: for efficiency, keep this information incrementally
   * @return true or false
   */
  public boolean containsParameter () {
    return  (nParameter > 0);
  }

  /** 
   * Add a vertex to this congruence class.
   * @param v the vertex to add
   */
  public void addVertex (OPT_ValueGraphVertex v) {
    if (vertices.add(v)) {
      if (v.representsParameter())
        nParameter++;
      if (representativeV == null)
        representativeV = v;
    }
  }

  /** 
   * Remove a vertex from this congruence class.
   * @param v the vertex to remove
   */
  public void removeVertex (OPT_ValueGraphVertex v) {
    if (vertices.remove(v)) {
      if (v.representsParameter())
        nParameter--;
      if (representativeV == v) {
        // Try to find an alternate representative
        representativeV = (OPT_ValueGraphVertex)vertices.iterator().next();
      }
    }
  }

  /**
   * Return a representative vertex for this congruence class.
   * @return a representative vertex for this congruence class.
   */
  public OPT_ValueGraphVertex getRepresentative () {
    return representativeV;
  }

  /** 
   * Return an iterator over the vertices in this congruence class
   * @return an iterator over the vertices in this congruence class
   */
  public Iterator<OPT_ValueGraphVertex> iterator () {
    return vertices.iterator();
  }

  /** 
   * Return the number of vertices in this class
   * @return the number of vertices in this class
   */
  public int size () {
    return  vertices.size();
  }
}
