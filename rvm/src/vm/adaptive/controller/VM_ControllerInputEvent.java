/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

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
