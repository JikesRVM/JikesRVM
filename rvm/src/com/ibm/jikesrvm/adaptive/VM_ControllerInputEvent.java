/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.adaptive;

/**
 * Abstract parent class for events from organizers to the controller. 
 *
 * @author Stephen Fink 
 */
interface VM_ControllerInputEvent {

   /** 
    * This method is called by the controller upon dequeuing this
    * event from the controller input queue
    */
   void process();
}
