/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.runtimeMeasurements.organizers;

import org.jikesrvm.adaptive.runtimeMeasurements.VM_RuntimeMeasurements;
import org.jikesrvm.adaptive.runtimeMeasurements.listeners.VM_YieldCounterListener;

/**
 * An organizer that periodically decays runtime counters
 *
 * @author Michael Hind
 **/
public final class VM_DecayOrganizer extends VM_Organizer {

  /**
   * @param listener the associated listener
   */
  public VM_DecayOrganizer(VM_YieldCounterListener listener) {
    this.listener   = listener;
    listener.setOrganizer(this);
    makeDaemon(true);
  }

  /**
   * Initialization: install and activate our listener.
   */
  public void initialize() {
    VM_RuntimeMeasurements.installTimerNullListener((VM_YieldCounterListener)listener);
  }

  /**
   * Method that is called when the sampling threshold is reached
   * We decay the decayable objects and activate the listener again.
   */
  void thresholdReached() {
    VM_RuntimeMeasurements.decayDecayableObjects();
  }  
}



