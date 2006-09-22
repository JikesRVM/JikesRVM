/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CompiledMethod;

/**
 * Event used by the basic recompilation organizer
 * to notify the controller that a method is hot.
 *
 * @author Dave Grove 
 * @author Stephen Fink
 */
public final class VM_HotMethodRecompilationEvent extends VM_HotMethodEvent 
  implements VM_ControllerInputEvent {

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodRecompilationEvent(VM_CompiledMethod _cm, double _numSamples) {
    super(_cm, _numSamples);
  }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodRecompilationEvent(VM_CompiledMethod _cm, int _numSamples) {
    this(_cm, (double)_numSamples);
  }

  public String toString() {
    return "HotMethodRecompilationEvent: "+super.toString();
  }


  /**
   * This function defines how the controller handles a
   * VM_HotMethodRecompilationEvent.  Simply passes the event to the
   * recompilation strategy.
   */
  public void process() {
    VM_ControllerPlan plan =
      VM_Controller.recompilationStrategy.considerHotMethod(getCompiledMethod(), this);

    if (VM.LogAOSEvents) VM_ControllerMemory.incrementNumMethodsConsidered();

    // If plan is still null we decided not to recompile.
    if (plan != null) {
      plan.execute();
    }
  }
}
