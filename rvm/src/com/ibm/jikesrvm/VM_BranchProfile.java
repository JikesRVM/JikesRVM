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
public abstract class VM_BranchProfile {
  /**
   * The bytecode index of the branch instruction
   */
  protected final int bci;

  /**
   * The number of times the branch was executed.
   */
  protected final float freq;

  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param _freq the number of times the branch was executed
   */
  VM_BranchProfile(int _bci, float _freq) {
    bci = _bci;
    freq = _freq;
  }

  public final int getBytecodeIndex() { return bci; }
  public final float getFrequency() { return freq; }
  
}
