/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm;

/**
 * Profile data for a branch instruction.
 * 
 * @author Dave Grove
 */
public final class VM_ConditionalBranchProfile extends VM_BranchProfile {

  final float taken;
  final boolean backwards;
  
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

  public float getTakenProbability() {
    if (freq > 0) {
      return taken/freq;
    } else if (backwards) {
      return 0.9f;
    } else {
      return 0.5f;
    }
  }

  public String toString() {
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
