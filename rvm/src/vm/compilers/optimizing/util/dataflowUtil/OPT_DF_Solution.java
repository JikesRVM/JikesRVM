/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.*;

/**
 * OPT_DF_Solution.java
 *
 * Represents the solution to a system of Data Flow equations.
 * Namely, a function mapping Objects to OPT_DF_LatticeCells
 *
 * @author Stephen Fink
 */
public class OPT_DF_Solution extends JDK2_HashMap {

  /** 
   * Return a string representation of the dataflow solution
   * @return a string representation of the dataflow solution
   */
  public String toString () {
    String result = new String();
    for (JDK2_Iterator e = values().iterator(); e.hasNext();) {
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



