/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Oracle interface: the compiler queries this oracle to decide whether
 * to inline a call site.
 *
 * @author Stephen Fink
 */
interface OPT_InlineOracle {

  /**
   * Should we inline a particular call site?
   * @param state information needed to make the inlining decision
   * @return an OPT_InlineDecision with the result
   */
  public OPT_InlineDecision shouldInline (OPT_CompilationState state);
}



