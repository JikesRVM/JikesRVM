/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

/**
 * An organizer that periodically decays runtime counters
 *
 * @author Michael Hind
 **/
final class VM_DecayOrganizer extends VM_Organizer {

  /**
   * @param listener the associated listener
   */
  VM_DecayOrganizer(VM_YieldCounterListener listener) {
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



