/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 *  This interface defines the decay method.  Implementors are 
 *  eligible for decay if they register with the 
 *  VM_RuntimeMeasurements class.
 *
 *  @author Michael Hind
 */

interface VM_Decayable {

  /**
   *  Called periodically when it is time to decay runtime mesaurment data
   */
  public void decay();

}





