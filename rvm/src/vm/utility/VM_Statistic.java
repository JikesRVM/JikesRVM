/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Abstraction for a statistic.
 *
 * @author Perry Cheng
 */
public class VM_Statistic implements Uninterruptible {

  protected int    count;
  protected double last;
  protected double sum;
  protected double max;
    
  public void addSample(double x) {
    last = x;
    if (count == 0) max = x;
    if (x > max) max = x;
    sum += x;
    count++;
  }

  public final int count() { return count; }
  public final double last() { if (VM.VerifyAssertions) VM._assert(count > 0); return last; }
  public final double sum()  { return sum; }
  public final double max()  { if (VM.VerifyAssertions) VM._assert(count > 0); return max; }
  public final double avg()  { if (VM.VerifyAssertions) VM._assert(count > 0); return sum / count; }

}
