/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Profile data for a branch instruction.
 * 
 * @author Dave Grove
 */
final class VM_ConditionalBranchProfile extends VM_BranchProfile {

  /**
   * The probability that the branch was taken.
   * A value in the range [0.0, 1.0]
   */
  protected final float probTaken;
  
  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param yea the number of times the branch was taken
   * @param nea the number of times the branch was not taken
   */
  VM_ConditionalBranchProfile(int _bci, int yea, int nea) {
    super(_bci, ((float)yea + (float)nea));
    if (freq > 0) {
      probTaken = (float)yea / freq;
    } else {
      probTaken = 0.5f; // Never executed, so 50/50 seems most plausible value?
    }
  }

  final public float getTakenProbability() { return probTaken; }

  final public String toString() {
    return bci + ": <" + (long)freq + ", " +probTaken + ">";
  }

}
