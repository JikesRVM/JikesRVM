/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An organizer that periodically decays runtime counters
 *
 * @author Michael Hind
 **/
final class VM_DecayOrganizer extends VM_Organizer {

  // Yield point listener: will wake up this organizer periodically
  private VM_YieldCounterListener listener;

  /**
   * @param listener the associated listener
   */
  VM_DecayOrganizer(VM_YieldCounterListener listener) {
    this.listener   = listener;
    listener.setOrganizer(this);
  }

  /**
   * Initialization: install and activate our listener.
   */
  public void initialize() {
    VM_RuntimeMeasurements.installNullListener(listener);
    listener.activate();
  }

  /**
   * Method that is called when the sampling threshold is reached
   * We decay the decayable objects and activate the listener again.
   */
  void thresholdReached() {
    VM_RuntimeMeasurements.decayDecayableObjects();
    listener.activate();
  }  
}



