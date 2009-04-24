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
package org.jikesrvm.adaptive.controller;

/**
 * Abstract parent class for events from organizers to the controller.
 */
public interface ControllerInputEvent {

  /**
   * This method is called by the controller upon dequeuing this
   * event from the controller input queue
   */
  void process();
}
