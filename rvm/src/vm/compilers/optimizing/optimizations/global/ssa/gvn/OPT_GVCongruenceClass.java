/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;

/** 
 * This class represents a congruence class for
 * global value numbering.
 * @author Steve Fink
 */
final class OPT_GVCongruenceClass {
  /**
   * A label representing a property of this congruence class.
   */
  Object label;
  /**
   * A value number representing this congruence class.
   */
  int valueNumber;
  /**
   * How many vertices in this congruence class represent parameters?
   */
  int nParameter;            
  /**
   * The set of vertices in this congruence class
   */
  HashSet vertices = new HashSet(1);

  /**
   * A representative of the congruence class
   *   - saves having to find one
   */
  OPT_ValueGraphVertex  representativeV;
  
  /**
   * Create a congruence class with a given representative value number
   * and label.
   * @param     valueNumber the value number of the class
   * @param     label the label of the class
   */
  OPT_GVCongruenceClass (int valueNumber, Object label) {
    this.valueNumber = valueNumber;
    this.label = label;
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
    ;
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
  public Iterator iterator () {
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
