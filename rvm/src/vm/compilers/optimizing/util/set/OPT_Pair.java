/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_Pair {
  Object first;
  Object second;

  /**
   * Constructor
   * @param    f  The first element in the pair.
   * @param    s  The second element in the pair.
   */
  OPT_Pair (Object f, Object s) {
    first = f;
    second = s;
  }

  /**
   */
  public int hashCode () {
    return  (first.hashCode() | second.hashCode());
  }

  /**
   */
  public boolean equals (Object o) {
    return  (o instanceof OPT_Pair) && first == ((OPT_Pair)o).first && 
        second == ((OPT_Pair)o).second;
  }
}



