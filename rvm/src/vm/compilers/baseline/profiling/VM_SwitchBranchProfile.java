/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Profile data for a branch instruction.
 * 
 * @author Dave Grove
 */
final class VM_SwitchBranchProfile extends VM_BranchProfile {

  /**
   * The probabilities that the difference arms of a switch were
   * taken. By convention, the default case is the last entry.
   */
  protected final float[] probs;
  
  /**
   * @param _bci the bytecode index of the source branch instruction
   * @param yea the number of times the branch was taken
   * @param nea the number of times the branch was not taken
   */
  VM_SwitchBranchProfile(int _bci, int[] counts, int start, int numEntries) {
    super(_bci, sumCounts(counts, start, numEntries));
    probs = new float[numEntries];
    if (freq > 0) {
      for (int i=0; i<numEntries; i++) {
	probs[i] = (float)counts[start+i] / freq;
      }
    } else {
      float p = 1.0f / (float)numEntries; // Never executed so even distribution seems most plausible?
      for (int i=0; i<numEntries; i++) {
	probs[i] = p;
      }
    }
  }

  public final float getDefaultProbability() {
    return probs[probs.length-1];
  }

  public final float getCaseProbability(int n) {
    return probs[n];
  }

  final public String toString() {
    String res = bci + ":\t[ " + (long)freq;
    for (int i=0; i<probs.length; i++) {
      res += ", "+probs[i];
    }
    return res + " ] switch";
  }

  private static float sumCounts(int[] counts, int start, int numEntries) {
    float sum = 0.0f;
    for (int i=start; i<start+numEntries; i++) {
      sum += (float)counts[i];
    }
    return sum;
  }
}
