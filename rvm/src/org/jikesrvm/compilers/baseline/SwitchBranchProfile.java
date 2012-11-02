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
public final class SwitchBranchProfile extends BranchProfile {

  /**
   * The number of times that the different arms of a switch were
   * taken. By convention, the default case is the last entry.
   */
  final float[] counts;

  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param cs counts
   * @param start idx of first entry in cs
   * @param numEntries number of entries in cs for this switch
   */
  SwitchBranchProfile(int _bci, int[] cs, int start, int numEntries) {
    super(_bci, sumCounts(cs, start, numEntries));
    counts = new float[numEntries];
    for (int i = 0; i < numEntries; i++) {
      counts[i] = cs[start + i];
    }
  }

  public float getDefaultProbability() {
    return getProbability(counts.length - 1);
  }

  public float getCaseProbability(int n) {
    return getProbability(n);
  }

  float getProbability(int n) {
    if (freq > 0) {
      return counts[n] / freq;
    } else {
      return 1.0f / counts.length;
    }
  }

  @Override
  public String toString() {
    String res = bci + "\tswitch     < " + (int) counts[0];
    for (int i = 1; i < counts.length; i++) {
      res += ", " + (int) counts[i];
    }
    return res + " >";
  }

  private static float sumCounts(int[] counts, int start, int numEntries) {
    float sum = 0.0f;
    for (int i = start; i < start + numEntries; i++) {
      sum += counts[i];
    }
    return sum;
  }
}
