/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_CompiledMethod;

/**
 * Wrapper around a pair of parallel arrays:
 *  (1) an array of compiled method id's
 *  (2) an array of counts: how many times each compiled method id is counted
 *
 * @author Dave Grove 
 * @modified Peter Sweeney
 */
public final class VM_MethodCountSet {
  /**
   * array of compiled methods
   */
  VM_CompiledMethod[] cms;
  /**
   * array of counts
   */
  double[] counters;

  /**
   * Constructor
   *
   * @param _cms array of compiled method ids
   * @param _counters array of counters
   */
  VM_MethodCountSet(VM_CompiledMethod[] _cms, double[] _counters) {
    if (VM.VerifyAssertions) VM._assert(_cms.length == _counters.length);
    cms = _cms;
    counters= _counters;
  }

  /**
   * String representation of fields
   * 
   * @return string representation of compiled method id's and thier counts
   */
  public String toString() {
    String ans = "";
    for (int i=0; i<cms.length; i++) {
      ans += cms[i] + " = " + counters[i] + "\n";
    }
    return ans;
  }
}
