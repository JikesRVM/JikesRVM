/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import java.util.HashMap;

/**
 * OPT_DF_Solution.java
 *
 * Represents the solution to a system of Data Flow equations.
 * Namely, a function mapping Objects to OPT_DF_LatticeCells
 *
 * @author Stephen Fink
 */
public class OPT_DF_Solution extends HashMap<Object, OPT_DF_LatticeCell> {
  /** Support for serialization */
  static final long serialVersionUID = -335649266901802532L;
  /** 
   * Return a string representation of the dataflow solution
   * @return a string representation of the dataflow solution
   */
  public String toString () {
    String result = "";
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



