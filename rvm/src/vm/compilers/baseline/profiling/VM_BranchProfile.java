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
abstract class VM_BranchProfile {
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
