/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Event used by the Adaptive Inlining Organizer
 * to notify the controller that a call arc 
 * originating in a hot method has become hot
 * and therefore recompilation of the method should
 * be considered to enable additional profile-directed inlining.
 *
 * @author Dave Grove 
 * @author Matthew Arnold
 */
public final class VM_AINewHotEdgeEvent extends VM_HotMethodEvent 
  implements VM_ControllerInputEvent {

  /**
   * Estimate of the expected benefit if the method is 
   * recompiled AT THE SAME OPT LEVEL with the newly
   * enabled profile-directed inlining.
   * <p>
   * TODO: Think about reasonable ways to encode the expected 
   * boost factor for recompiling at higher opt levels.
   * In the short run, this is academic, since we only plan to
   * create an instance of this event for methods already compiled
   * at max opt level, but it may be required later.
   * <p>
   * NB: Boost factor is a value >= 1.0!
   * (1.0 means no boost, 1.1 means a 10% improvement, etc).
   */
  private double boostFactor;
  public final double getBoostFactor() { return boostFactor; }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   * @param _boostFactor improvement expected by applying FDO
   */
  VM_AINewHotEdgeEvent(VM_CompiledMethod _cm, double _numSamples, double _boostFactor) {
    super(_cm, _numSamples);
    if (VM.VerifyAssertions) VM.assert(_boostFactor >= 1.0);
    boostFactor = _boostFactor;
  }

  /**
   * @param _cm the compiled method
   * @param _numSamples the number of samples attributed to the method
   * @param _boostFactor improvement expected by applying FDO
   */
  VM_AINewHotEdgeEvent(VM_CompiledMethod _cm, int _numSamples, double _boostFactor) {
    this(_cm, (double)_numSamples, _boostFactor);
  }


  public final String toString() {
    return "NewHotEdgeEvent: "+super.toString()+
      ", boost factor = "+getBoostFactor();
  }


  /**
   * Called when the controller is ready to process this event.
   * Simply passes itself to the recompilation strategy.
   */
  public void process() {
    VM_CompiledMethod cmpMethod = getCompiledMethod();
    VM_Controller.recompilationStrategy.considerHotCallEdge(cmpMethod,this);
  }

}
