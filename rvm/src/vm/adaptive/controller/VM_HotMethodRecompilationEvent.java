/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
   * @param _cmid the compiled method id
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodRecompilationEvent(int _cmid, double _numSamples) {
    super(_cmid, _numSamples);
  }

  /**
   * @param _cmid the compiled method id
   * @param _numSamples the number of samples attributed to the method
   */
  VM_HotMethodRecompilationEvent(int _cmid, int _numSamples) {
    this(_cmid, (double)_numSamples);
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
    VM_ControllerPlan plan = null;
    VM_CompiledMethod cmpMethod = getCompiledMethod();
    if (cmpMethod != null) {
      plan = VM_Controller.recompilationStrategy.
	considerHotMethod(cmpMethod, this);
    }

    if (VM.LogAOSEvents) VM_ControllerMemory.incrementNumMethodsConsidered();

    // If plan is still null we decided not to recompile.
    if (plan != null) {
      plan.execute();
    }
  }
}
