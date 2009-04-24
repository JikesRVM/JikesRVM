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

import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * Event used by the basic recompilation organizer
 * to notify the controller that a method is hot.
 */
public final class HotMethodRecompilationEvent extends HotMethodEvent implements ControllerInputEvent {

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  public HotMethodRecompilationEvent(CompiledMethod _cm, double _numSamples) {
    super(_cm, _numSamples);
  }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  HotMethodRecompilationEvent(CompiledMethod _cm, int _numSamples) {
    this(_cm, (double) _numSamples);
  }

  public String toString() {
    return "HotMethodRecompilationEvent: " + super.toString();
  }

  /**
   * This function defines how the controller handles a
   * HotMethodRecompilationEvent.  Simply passes the event to the
   * recompilation strategy.
   */
  public void process() {
    ControllerPlan plan = Controller.recompilationStrategy.considerHotMethod(getCompiledMethod(), this);

    ControllerMemory.incrementNumMethodsConsidered();

    // If plan is still null we decided not to recompile.
    if (plan != null) {
      plan.execute();
    }
  }
}
