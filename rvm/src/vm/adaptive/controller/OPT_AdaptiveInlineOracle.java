/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;

/**
 * Extend the generic OPT_ProfileDirectedInlineOracle
 * with a few minor hooks back into the adaptive system.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */

public final class OPT_AdaptiveInlineOracle extends OPT_ProfileDirectedInlineOracle {

  public OPT_AdaptiveInlineOracle(OPT_InlinePlan plan) {
    super(plan);
  }

  /*
   * In an adaptive system, we need to record that the opt compiler
   * didn't want to inline a hot edge to avoid triggering a 
   * recompilation for the sole purpose of attempting to inline said edge.
   */
  protected void recordRefusalToInlineHotEdge(VM_CompiledMethod cm, VM_Method caller, int bcX, VM_Method callee) {
    VM_AdaptiveInlining.recordRefusalToInlineHotEdge(cm.getId(), caller, bcX, callee);
  }
}
