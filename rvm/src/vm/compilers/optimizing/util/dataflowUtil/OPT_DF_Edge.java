/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * OPT_DF_Edge.java
 *
 * Represents an edge in the control flow graph. 
 *
 * @author Stephen Fink
 */
class OPT_DF_Edge {
  /**
   * edge source
   */
  OPT_BasicBlock from;
  /**
   * edge sink
   */
  OPT_BasicBlock to;

  /**
   * @param   f source 
   * @param   t sink
   */
  OPT_DF_Edge (OPT_BasicBlock f, OPT_BasicBlock t) {
    from = f;
    to = t;
  }

  /**
   * Return a string representation
   * @return a string representation
   */
  public String toString () {
    return  from + "->" + to;
  }

  /** 
   * Equality relation.
   * @param obj object to compare with
   * @return true iff the two objects represent the same edge in the cfg
   */
  public boolean equals (Object obj) {
    OPT_DF_Edge c = (OPT_DF_Edge)obj;
    return  ((from == c.from) && (to == c.to));
  }

  /** 
   * Hashcode so that equal keys map to same bucket.
   * @return the hash code
   */
  public int hashCode () {
    int result = from.hashCode();
    if (to != null)
      result += to.hashCode();
    return  result;
  }
}



