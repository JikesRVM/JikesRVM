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
package org.jikesrvm.adaptive.measurements.listeners;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A simple listener to accumulate counts of total events
 * and the fraction of those events that occurred at loop backedges.
 * In effect, this provides a mechanism for estimating the
 * call density of the program.  If most yieldpoints are being taken at
 * backedges, then call density is low.
 */
@Uninterruptible
public final class CallDensityListener extends NullListener {

  private double numSamples = 0;
  private double numBackedgeSamples = 0;

  @Override
  public void update(int whereFrom) {
    numSamples++;
    if (whereFrom == RVMThread.BACKEDGE) numBackedgeSamples++;
  }

  public double callDensity() {
    return 1 - (numBackedgeSamples / numSamples);
  }

  @Override
  public void reset() {
    numSamples = 0;
    numBackedgeSamples = 0;
  }

  @Override
  public void report() {
    VM.sysWriteln("The call density of the program is ", callDensity());
  }
}
