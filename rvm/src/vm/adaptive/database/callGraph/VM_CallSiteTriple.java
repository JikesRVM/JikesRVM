/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Method;

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
   * Bytecode index of callsite in caller.
   * @return call site's bytecode index in caller
   */
  int bcIndex;		// bytecode index (in caller) of call site
  /**
   * Get call site's bytecode index
   * @ return call site's bytecode index
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
    if (!(obj instanceof VM_CallSiteTriple)) return false;
    if (obj == null) return false;
    VM_CallSiteTriple triple = (VM_CallSiteTriple)obj;
    boolean returnValue = false;
    try {
      returnValue = (caller.toString().compareTo(triple.caller.toString())==0);
      if (returnValue == true) {
	returnValue = (callee.toString().compareTo(triple.callee.toString())==0);
	if (returnValue == true) {
	  if (bcIndex != triple.bcIndex && bcIndex != -1 && triple.bcIndex != -1) {
	    returnValue = false;
	  }
	}
      }
    } catch (NullPointerException e) {
      VM.sysWrite("***VM_CallSiteTriple.equals("+obj+
		  ") compareTo of names failed!\n");
      returnValue = false;
    }
    return returnValue;
  }
   
  /**
   * Compute a call site's hash code
   *
   * @return hash code
   */
  public int hashCode() {
    int result = 7;
    if (caller != null) result += caller.hashCode();
    if (callee != null) result += callee.hashCode();
    result += bcIndex;
    return result;
  }
}
