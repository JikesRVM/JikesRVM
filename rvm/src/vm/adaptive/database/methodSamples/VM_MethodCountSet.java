/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
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
   * array of compiled method id's
   */
  int[] cmids;
  /**
   * array of counts
   */
  double[] counters;

  /**
   * Constructor
   *
   * @param _cmids array of compiled method ids
   * @param _counters array of counters
   */
  VM_MethodCountSet(int[] _cmids, double[] _counters) {
    if (VM.VerifyAssertions) VM.assert(_cmids.length == _counters.length);
    cmids = _cmids;
    counters= _counters;
  }

  /**
   * String representation of fields
   * 
   * @return string representation of compiled method id's and thier counts
   */
  public String toString() {
    String ans = "";
    for (int i=0; i< cmids.length; i++) {
      ans += VM_ClassLoader.getCompiledMethod(cmids[i]).getMethod() + 
	" = " + counters[i] + "\n";
    }
    return ans;
  }
}
