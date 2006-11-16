/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.adaptive;

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





