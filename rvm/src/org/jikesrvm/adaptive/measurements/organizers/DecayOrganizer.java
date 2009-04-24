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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.adaptive.measurements.RuntimeMeasurements;
import org.jikesrvm.adaptive.measurements.listeners.YieldCounterListener;
import org.vmmagic.pragma.NonMoving;

/**
 * An organizer that periodically decays runtime counters
 *
 **/
@NonMoving
public final class DecayOrganizer extends Organizer {

  /**
   * @param listener the associated listener
   */
  public DecayOrganizer(YieldCounterListener listener) {
    this.listener = listener;
    listener.setOrganizer(this);
    makeDaemon(true);
  }

  /**
   * Initialization: install and activate our listener.
   */
  @Override
  public void initialize() {
    RuntimeMeasurements.installTimerNullListener((YieldCounterListener) listener);
  }

  /**
   * Method that is called when the sampling threshold is reached
   * We decay the decayable objects and activate the listener again.
   */
  void thresholdReached() {
    RuntimeMeasurements.decayDecayableObjects();
  }
}



