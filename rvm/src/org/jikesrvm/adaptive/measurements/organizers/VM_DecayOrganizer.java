/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.adaptive.measurements.VM_RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.VM_YieldCounterListener;

/**
 * An organizer that periodically decays runtime counters
 *
 **/
public final class VM_DecayOrganizer extends VM_Organizer {

  /**
   * @param listener the associated listener
   */
  public VM_DecayOrganizer(VM_YieldCounterListener listener) {
    this.listener = listener;
    listener.setOrganizer(this);
    makeDaemon(true);
  }

  /**
   * Initialization: install and activate our listener.
   */
  public void initialize() {
    VM_RuntimeMeasurements.installTimerNullListener((VM_YieldCounterListener) listener);
  }

  /**
   * Method that is called when the sampling threshold is reached
   * We decay the decayable objects and activate the listener again.
   */
  void thresholdReached() {
    VM_RuntimeMeasurements.decayDecayableObjects();
  }
}



