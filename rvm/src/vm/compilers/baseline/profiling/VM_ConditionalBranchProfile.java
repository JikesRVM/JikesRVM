/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Profile data for a branch instruction.
 * 
 * @author Dave Grove
 */
public final class VM_ConditionalBranchProfile extends VM_BranchProfile {

  protected final float taken;
  protected final boolean backwards;
  
  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param yea the number of times the branch was taken
   * @param nea the number of times the branch was not taken
   * @param bw is this a backwards branch?
   */
  VM_ConditionalBranchProfile(int _bci, int yea, int nea, boolean bw) {
    super(_bci, ((float)yea + (float)nea));
    taken = (float)yea;
    backwards = bw;
  }

  final public float getTakenProbability() { 
    if (freq > 0) {
      return taken/freq;
    } else if (backwards) {
      return 0.9f;
    } else {
      return 0.5f;
    }
  }

  final public String toString() {
    String ans = bci + (backwards ? "\tbackbranch" : "\tforwbranch");
    ans += " < " + (int)taken + ", " +(int)(freq-taken) + " > ";
    if (freq>0) {
      ans += (100.0f*taken/freq) + "% taken";
    } else {
      ans += "Never Executed";
    }
    return ans;
  }

}
