/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;

/**
 * OPT_DF_Solution.java
 *
 * Represents the solution to a system of Data Flow equations.
 * Namely, a function mapping Objects to OPT_DF_LatticeCells
 *
 * @author Stephen Fink
 */
public class OPT_DF_Solution extends java.util.HashMap {

  /** 
   * Return a string representation of the dataflow solution
   * @return a string representation of the dataflow solution
   */
  public String toString () {
    String result = new String();
    for (java.util.Iterator e = values().iterator(); e.hasNext();) {
      OPT_DF_LatticeCell cell = (OPT_DF_LatticeCell)e.next();
      result = result + cell + "\n";
    }
    return  result;
  }

  /**
   * Return the lattice cell corresponding to an object
   * @param k the object to look up
   * @return its lattice cell
   */
  public Object lookup (Object k) {
    return  get(k);
  }
}



