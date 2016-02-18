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

import org.vmmagic.pragma.Pure;

/**
 * Profile data for a branch instruction.
 */
public abstract class BranchProfile {
  /** The bytecode index of the branch instruction */
  protected final int bci;

  /** The number of times the branch was executed. */
  protected final float freq;

  /**
   * @param bci the bytecode index of the source branch instruction
   * @param freq the number of times the branch was executed
   */
  BranchProfile(int bci, float freq) {
    this.bci = bci;
    this.freq = freq;
  }

  public final int getBytecodeIndex() {
    return bci;
  }

  public final float getFrequency() {
    return freq;
  }

  /**
   * Converts integer count to float handling overflow
   * @param count integer count
   * @return floating point count
   */
  @Pure
  static float countToFloat(int count) {
    if (count < 0) {
      final float MAX_UNSIGNED_INT = 2147483648f;
      return MAX_UNSIGNED_INT + (count & 0x7FFFFFFF);
    } else {
      return count;
    }
  }

}
