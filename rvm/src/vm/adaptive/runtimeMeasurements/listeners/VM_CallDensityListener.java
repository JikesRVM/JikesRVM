/*
 * (C) Copyright IBM Corp. 2004
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;
import org.vmmagic.pragma.*;

/**
 * A simple listener to accumulate counts of total events
 * and the fraction of those events that occured at loop backedges.
 * In effect, this provides a mechanism for estimating the
 * call density of the program.  If most yieldpoints are being taken at
 * backedges, then call density is low.
 * 
 * @author Dave Grove
 */
final class VM_CallDensityListener extends VM_NullListener
                                   implements Uninterruptible {

  private double numSamples = 0;
  private double numBackedgeSamples = 0;
  
  /** 
   * This method is called when its time to record that a 
   * yield point has occurred.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *             EPILOGUE?
   */
  public void update(int whereFrom) {
    numSamples++;
    if (whereFrom == VM_Thread.BACKEDGE) numBackedgeSamples++;
  }

  public double callDensity() {
    return 1 - (numBackedgeSamples/numSamples);
  }

  public void reset() {
    numSamples = 0;
    numBackedgeSamples = 0;
  }

  public void report() {
    VM.sysWriteln("The call density of the program is ", callDensity());
  }
}
