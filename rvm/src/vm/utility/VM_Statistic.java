/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Abstraction for a statistic.
 *
 * @author Perry Cheng
 */
class VM_Statistic implements VM_Uninterruptible {

  protected int    count;
  protected double last;
  protected double sum;
  protected double max;
    
  void addSample(double x) {
    last = x;
    if (count == 0) max = x;
    if (x > max) max = x;
    sum += x;
    count++;
  }

  final int count() { return count; }
  final double last() { if (VM.VerifyAssertions) VM.assert(count > 0); return last; }
  final double sum()  { return sum; }
  final double max()  { if (VM.VerifyAssertions) VM.assert(count > 0); return max; }
  final double avg()  { if (VM.VerifyAssertions) VM.assert(count > 0); return sum / count; }

}
