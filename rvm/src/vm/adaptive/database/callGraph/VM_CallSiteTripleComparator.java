/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;

/**
  * This class implements the Comparator comparing two 
  * VM_CallSiteTriple objects.
  *
  * We can either choose to implement a partial order based on
  * the String representation, or by weight.
  *
  * @author Peter Sweeney
  * @modified Stephen Fink
  * @modified Michael Hind
  * @date   25 May 2000
  */

import java.util.*;

class VM_CallSiteTripleComparator implements java.util.Comparator {

  public static boolean debug = false;
  
  /**
   * Boolean flag that determines if the comparison of two call sites
   * takes into account their weights.
   */
  private boolean byWeight = false;

  /** Interface */
    
  /**
   * Constructor
   */
  VM_CallSiteTripleComparator(boolean byWeight) {
    this.byWeight = byWeight;
  }
  /**
   * Constructor
   */
  VM_CallSiteTripleComparator() {
    this.byWeight = false;
  }

  /** 
   * Compare the string representation of two triples
   *
   * @return -1 iff o1 < o2
   * @return +1 iff o1 > o2
   * @return 0 if o1 and o2 are the same callsites or 
   *            if they only differ by their bytecode indices and 
   *            one index is -1.
   */
  private int compareByName(VM_CallSiteTriple t1, VM_CallSiteTriple t2) 
  {
    String s1 = t1.toString();
    String s2 = t2.toString();

    return s1.compareTo(s2);
  }
  /** 
   * Compare the weight of two triples
   *
   * @return -1 iff w(t1) < w(t2)
   * @return +1 iff w(t1) > w(t2)
   * @return compareByName(t1,t2) otherwise
   */
  private int compareByWeight(VM_CallSiteTriple t1, VM_CallSiteTriple t2) 
  {
    double w1 = t1.getWeight();
    double w2 = t2.getWeight();

    if (w1 < w2) return -1;
    if (w1 > w2) return 1;
    return compareByName(t1,t2);
  }
  /** 
   * @param o1, o2 two VM_CallSiteTriple to be compared.
   * @return -1 iff o1 < o2
   * @return +1 iff o1 > o2
   * @return 0 if o1 and o2 are the same callsites or 
   *            if they only differ by their bytecode indices and 
   *            one index is -1.
   */
  public int compare(Object o1, Object o2) 
  {
    // This should never happen!
    if (!(o1 instanceof VM_CallSiteTriple) || 
        !(o2 instanceof VM_CallSiteTriple)) {
      VM.sysWrite("***VM_CallSiteTripleComparator.compare():"+
                  " one object is not of type VM_CallSiteTriple\n");
      VM.sysExit(-1);
    }

    if (o1.equals(o2)) return 0;

    VM_CallSiteTriple t1 = (VM_CallSiteTriple)o1;
    VM_CallSiteTriple t2 = (VM_CallSiteTriple)o2;
    if (byWeight) return compareByWeight(t1,t2);
    else return compareByName(t1,t2);
  }
}
