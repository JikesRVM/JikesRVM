/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */

//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.classloader.VM_MethodReference;

/**
 * A unresolved call site is a pair: <VM_MethodReference, bcIndex>
 *
 * @author Xianglong Huang
 */
public final class VM_UnResolvedCallSite {

  /**
   * Caller method
   */
  private final VM_MethodReference methodRef;

  /**
   * bytecode index of callsite in caller method
   */
  private final int bcIndex;

  /**
   * @param m the VM_MethodReference containing the callsite
   * @param bci the bytecode index of the callsite within m
   */
  public VM_UnResolvedCallSite(VM_MethodReference m, int bci) {
    if (com.ibm.JikesRVM.VM.VerifyAssertions) com.ibm.JikesRVM.VM._assert(m != null);
    methodRef = m;
    bcIndex = bci;
  }

  /**
   * @return method containing the callsite
   */
  public VM_MethodReference getMethodRef() { return methodRef; }

  /**
   * @return call site's bytecode index in its method
   */
  public int getBytecodeIndex() {return bcIndex;}
  
  /**
   * @return string representation of call site
   */
  public String toString() {
    return "<"+methodRef+", "+bcIndex+">";
  }

  /**
   * Determine if two call sites are the same.  Exact match: no wild cards.
   *
   * @param obj call site to compare to
   * @return true if call sites are the same; otherwise, return false
   */
  public boolean equals(Object obj) {
    if (obj instanceof VM_UnResolvedCallSite) { 
      VM_UnResolvedCallSite cs = (VM_UnResolvedCallSite)obj;
      return methodRef.equals(cs.methodRef) && bcIndex == cs.bcIndex;
    } else {
      return false;
    }
  }

  /**
   * @return hash code
   */
  public int hashCode() {
    return bcIndex + methodRef.hashCode();
  }
}
