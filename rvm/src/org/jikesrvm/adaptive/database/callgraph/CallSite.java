/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.database.callgraph;

import org.jikesrvm.classloader.RVMMethod;

/**
 * A call site is a pair: <RVMMethod, bcIndex>
 */
public final class CallSite {

  /**
   * Caller method
   */
  private final RVMMethod method;

  /**
   * bytecode index of callsite in caller method
   */
  private final int bcIndex;

  /**
   * @param m the RVMMethod containing the callsite
   * @param bci the bytecode index of the callsite within m
   */
  public CallSite(RVMMethod m, int bci) {
    if (org.jikesrvm.VM.VerifyAssertions) org.jikesrvm.VM._assert(m != null);
    method = m;
    bcIndex = bci;
  }

  /**
   * @return method containing the callsite
   */
  public RVMMethod getMethod() { return method; }

  /**
   * @return call site's bytecode index in its method
   */
  public int getBytecodeIndex() {return bcIndex;}

  /**
   * @return string representation of call site
   */
  public String toString() {
    return "<" + method + ", " + bcIndex + ">";
  }

  /**
   * Determine if two call sites are the same.  Exact match: no wild cards.
   *
   * @param obj call site to compare to
   * @return true if call sites are the same; otherwise, return false
   */
  public boolean equals(Object obj) {
    if (obj instanceof CallSite) {
      CallSite cs = (CallSite) obj;
      return method.equals(cs.method) && bcIndex == cs.bcIndex;
    } else {
      return false;
    }
  }

  /**
   * @return hash code
   */
  public int hashCode() {
    return bcIndex + method.hashCode();
  }
}
