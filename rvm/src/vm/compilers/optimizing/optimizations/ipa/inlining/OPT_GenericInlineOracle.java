/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Common code for inline oracles.
 * This class's shouldInline method factors out the basic logic
 * and then delegates to the subclass method to make all non-trivial decisions.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
abstract class OPT_GenericInlineOracle extends OPT_InlineTools
    implements OPT_InlineOracle {

  /**
   * Should we inline a particular call site?
   *
   * @param state information needed to make the inlining decision
   * @eturns an OPT_InlineDecision with the result
   *
   */
  public OPT_InlineDecision shouldInline (OPT_CompilationState state) {
    if (!state.getOptions().INLINE) {
      return  OPT_InlineDecision.NO("inlining not enabled");
    }
    VM_Method caller = state.getMethod();
    VM_Method callee = state.obtainTarget();
    // perform generic checks to test common inlining cases.
    // These tests do not apply for invokeinterface
    int inlinedSizeEstimate = 0;
    if (!state.isInvokeInterface()) {
      // Check legality of inlining.
      if (!legalToInline(caller, callee))
        return  OPT_InlineDecision.NO("illegal inlining");
      // Check inline pragmas
      if (OPT_InlineTools.hasInlinePragma(callee, state))
        return  OPT_InlineDecision.YES(callee, "pragmaInline");
      if (OPT_InlineTools.hasNoInlinePragma(callee, state))
        return  OPT_InlineDecision.NO("pragmaNoInline");
      inlinedSizeEstimate = OPT_InlineTools.inlinedSizeEstimate(callee, 
                                                                state);
      // Some callee methods should always be inlined, 
      // even if dynamically this call site
      // is never executed.  If the callee is sufficiently small and 
      // can be inlined without a guard
      // then we save compile time and code space by inlining it.
      if (inlinedSizeEstimate < 
          state.getOptions().IC_MAX_ALWAYS_INLINE_TARGET_SIZE
          && (!needsGuard(callee) || state.getComputedTarget() != null) 
          && // guard?
          !state.getSequence().containsMethod(callee)) { // recursive?
        return  OPT_InlineDecision.YES(callee, "trivial inline");
      }
    } 
    // At this point, we know that it is legal to inline the call,
    // but we don't know whether it is desirable.  Invoke the "real"
    // inline oracle (a subclass) to make the tough decisions.
    return  shouldInlineInternal(caller, callee, state, inlinedSizeEstimate);
  }

  /**
   * Children must implement this method.
   * It contains the non-generic decision making portion of the oracle.
   */
  protected abstract OPT_InlineDecision shouldInlineInternal (VM_Method caller, 
      VM_Method callee, OPT_CompilationState state, int inlinedSizeEstimate);
}



