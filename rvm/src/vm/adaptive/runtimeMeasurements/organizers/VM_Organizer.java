/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * An VM_Organizer acts an an intermediary between the low level 
 * online measurements and the controller.  An organizer may perform
 * simple or complex tasks, but it is always simply following the 
 * instructions given by the controller.
 * 
 * @author Matthew Arnold
 * @author Stephen Fink
 */
abstract class VM_Organizer extends VM_Thread {

  /**
   * Called when thread is scheduled.
   */
  public void run() {

    initialize();

    while (true) {
      // sleep until awoken
      synchronized(this) {
        try {
	  wait();
        }
        catch (InterruptedException e) {
	  e.printStackTrace();
        }
      }
      // we've been awoken, process the information
      try {
	thresholdReached();
      }
      catch (Exception e) {
	VM.sysWrite("AOS: WARNING: exception in organizer "+this+"\n");
	e.printStackTrace();

	// Is there a more elegant way to make this exception fatal to
	// the application?
	System.exit(-1);
      }
    } 
  }

  /**
   * Last opportunity to say something.
   */
  public void report() {}

  /**
   * Method that is called when the sampling threshold is reached
   */
  abstract void thresholdReached();

  /**
   * Organizer specific setup.  
   * A good place to install and activate any listeners.
   */
  abstract protected void initialize();

}
