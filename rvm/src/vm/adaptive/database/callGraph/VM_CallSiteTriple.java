/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Method;

/**
 * The representation of a call site as a triple: a caller, 
 * call site (as bytecode offset), and callee.
 *
 * SJF: I've modified this class to also hold an integer edge weight, rather
 * than keeping a lookaside hash table.  This may compromise OO design
 * a little, but should be more efficient for current clients.
 *
 * @author Peter F. Sweeney
 * @author Stephen Fink
 * @date   23 May 2000
 */

public final class VM_CallSiteTriple {

  public static final boolean DEBUG = false;
  
  /**
   * Current method.
   */
  VM_Method caller;
  /**
   * Get caller
   * @return call site's caller
   */
  public VM_Method getCaller() { return caller; }
  /**
   * Who is called.  null if not known.
   */
  VM_Method callee;
  /**
   * Get callee
   * @return call site's callee
   */
  public VM_Method getCallee() { return callee; }
  /**
   * Call site's bytecode index in caller.
   */
  int bcIndex;          // bytecode index (in caller) of call site
  /**
   * Get call site's bytecode index (in caller).
   * @return call site's bytecode index (in caller)
   */
  public int getBytecodeIndex() {return bcIndex;}
  
  /**
   * Edge weight
   */
  double weight;
  public double getWeight() { return weight; }
  public void setWeight(double w) { weight = w; }
  public void incrementWeight() { weight+=1.0; }
  
  /**
   * Decay the weight
   * @param rate the value to decay by
   */
  public void decayWeight(double rate) { 
    weight /= rate; 
  }
  
  /**
   * Constructor
   * @param caller call site caller
   * @param bcIndex bytecode index of call site
   * @param callee call site callee
   */
  VM_CallSiteTriple(VM_Method caller, int bcIndex, VM_Method callee) 
  {
    this.caller  = caller;
    this.bcIndex = bcIndex;
    this.callee  = callee;
  }
   
  /**
   * Generate string representation of a call site
   * @return string representation of call site
   */
  public String toString() {
    return " <"+caller+", "+bcIndex+", "+callee+">"+"(wt:"+weight+")";
  }

  /**
   * Determine if two call sites are the same.  Exact match: no wild cards.
   *
   * @param obj call site to compare to
   * @return true if call sites are the same; otherwise, return false
   */
  public boolean equals(Object obj) {
    if (obj instanceof VM_CallSiteTriple) { 
      VM_CallSiteTriple triple = (VM_CallSiteTriple)obj;
      return caller == triple.caller &&
        callee == triple.callee && 
        bcIndex == triple.bcIndex;
    }
    return false;
  }

  /**
   * Compute a call site's hash code
   *
   * @return hash code
   */
  public int hashCode() {
    int result = bcIndex;
    result += caller.hashCode();
    result += callee.hashCode();
    return result;
  }
}
