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
  /** Probability of being taken */
  private final float taken;
  /** Backward branch */
  private final boolean backwards;

  /**
   * @param bci the bytecode index of the source branch instruction
   * @param taken the number of times the branch was taken
   * @param notTaken the number of times the branch was not taken
   * @param bw is this a backwards branch?
   */
  ConditionalBranchProfile(int bci, int taken, int notTaken, boolean bw) {
    super(bci, countToFloat(taken) + countToFloat(notTaken));
    this.taken = countToFloat(taken);
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

  @Override
  public String toString() {
    String ans = bci + (backwards ? "\tbackbranch" : "\tforwbranch");
    ans += " < " + (long) taken + ", " + (long) (freq - taken) + " > ";
    if (freq > 0) {
      ans += (100.0f * taken / freq) + "% taken";
    } else {
      ans += "Never Executed";
    }
    return ans;
  }

}
