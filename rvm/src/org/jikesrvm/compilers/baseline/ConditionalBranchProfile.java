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
package org.jikesrvm.compilers.baseline;

/**
 * Profile data for a branch instruction.
 */
public final class ConditionalBranchProfile extends BranchProfile {

  final float taken;
  final boolean backwards;

  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param yea the number of times the branch was taken
   * @param nea the number of times the branch was not taken
   * @param bw is this a backwards branch?
   */
  ConditionalBranchProfile(int _bci, int yea, int nea, boolean bw) {
    super(_bci, ((float) yea + (float) nea));
    taken = (float) yea;
    backwards = bw;
  }

  public float getTakenProbability() {
    if (freq > 0) {
      return taken / freq;
    } else if (backwards) {
      return 0.9f;
    } else {
      return 0.5f;
    }
  }

  public String toString() {
    String ans = bci + (backwards ? "\tbackbranch" : "\tforwbranch");
    ans += " < " + (int) taken + ", " + (int) (freq - taken) + " > ";
    if (freq > 0) {
      ans += (100.0f * taken / freq) + "% taken";
    } else {
      ans += "Never Executed";
    }
    return ans;
  }

}
