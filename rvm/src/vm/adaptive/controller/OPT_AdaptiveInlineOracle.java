/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
  * Extend the generic OPT_ProfileDirectedInlineOracle
  * with a few minor hooks back into the adaptive system.
  *
  * @author Stephen Fink
  * @author Dave Grove
  */
final class OPT_AdaptiveInlineOracle extends OPT_ProfileDirectedInlineOracle {

  OPT_AdaptiveInlineOracle(OPT_InlinePlan plan) {
    super(plan);
  }

  /*
   * In an adaptive system, we need to record that the opt compiler
   * didn't want to inline a hot edge to avoid triggering a 
   * recompilation for the sole purpose of attempting to inline said edge.
   */
  protected void recordRefusalToInlineHotEdge(int cmid, VM_Method caller, int bcX, VM_Method callee) {
    VM_AdaptiveInlining.recordRefusalToInlineHotEdge(cmid, caller, bcX, callee);
  }
  
}
