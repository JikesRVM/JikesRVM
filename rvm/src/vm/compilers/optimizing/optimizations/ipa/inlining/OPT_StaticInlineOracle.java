/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Inlining oracle using static size heuristics during on-the-fly compilation.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
final class OPT_StaticInlineOracle extends OPT_GenericInlineOracle {

  OPT_StaticInlineOracle () {}

  /**
   * The main routine whereby this oracle makes decisions whether or not
   * to inline invokevirtual, invokestatic, and invokespecial.
   * 
   * @param caller the calling method
   * @param callee the callee method
   * @param state miscellaneous state of the compilation
   * @param inlinedSizeEstimate estimate of the size of callee if inlined
   * @return an inlining decisions
   */
  protected OPT_InlineDecision shouldInlineInternal(VM_Method caller, 
						    VM_Method callee, 
						    OPT_CompilationState state, 
						    int inlinedSizeEstimate) {
    // Don't allow the static inline oracle to inline recursive calls.
    // It isn't smart enough to do this effectively.
    OPT_InlineSequence seq = state.getSequence();
    if (seq.containsMethod(callee)) {
      return OPT_InlineDecision.NO("recursive call");
    }

    OPT_Options opts = state.getOptions();
    // more or less figure out the guard situation early -- impacts size estimate.
    boolean needsGuard = state.getComputedTarget() == null && needsGuard(callee);
    boolean preEx = 
      needsGuard && state.getIsExtant() && opts.PREEX_INLINE && isCurrentlyFinal(callee, true);
    
    // See if inlining action passes simple size heuristics
    int cost = inliningActionCost(inlinedSizeEstimate, needsGuard, preEx, opts);
    OPT_InlineDecision sizeCheck = sizeHeuristics(caller, callee, state, cost);
    if (sizeCheck != null) return sizeCheck;

    // Ok, the size looks good, attempt to do it.
    if (needsGuard) {

      if (preEx) {
	if (OPT_ClassLoadingDependencyManager.TRACE || 
	    OPT_ClassLoadingDependencyManager.DEBUG) {
	  VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "
					  + callee + " into " + caller + "\n");
	}
	VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
							    state.getCompiledMethodId());
	return OPT_InlineDecision.YES(callee, "PREEX_INLINE passed size checks");
      } else 

	  if (opts.GUARDED_INLINE && isCurrentlyFinal(callee, !opts.guardWithClassTest())) {
	return OPT_InlineDecision.guardedYES(callee, 
					     chooseGuard(caller, callee, state, true), 
					     "static guarded inline passsed size checks");
      }
      return OPT_InlineDecision.NO(callee, "non-final virtual method");
    } else {
      return OPT_InlineDecision.YES(callee, "Non-guarded passed size checks");
    }
  }


  /**
   * The main routine whereby this oracle makes decisions whether or not
   * to inline invokeinterface.
   * 
   * @param caller the calling method
   * @param callee the callee method
   * @param state miscellaneous state of the compilation
   * @return an inlining decisions
   */
  protected OPT_InlineDecision shouldInlineInterfaceInternal(VM_Method caller,
							     VM_Method callee,
							     OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    if (!opts.GUARDED_INLINE_INTERFACE)
      return OPT_InlineDecision.NO("invokeinterface");
      
    callee = OPT_InterfaceHierarchy.getUniqueImplementation(callee);
    if (callee != null) {
      // Don't allow the static inline oracle to inline recursive calls.
      // It isn't smart enough to do this effectively.
      OPT_InlineSequence seq = state.getSequence();
      if (seq.containsMethod(callee)) {
	return OPT_InlineDecision.NO("recursive call");
      }

      // got a unique target in the current hierarchy. Attempt to inline it.
      if (!legalToInline(caller,callee))
        return OPT_InlineDecision.NO("Illegal interface inline");
      
      int inlinedSizeEstimate = inlinedSizeEstimate(callee, state);
      int cost = inliningActionCost(inlinedSizeEstimate, true, false, opts);

      OPT_InlineDecision sizeCheck = sizeHeuristics(caller, callee, state, cost);
      if (sizeCheck != null) return sizeCheck;

      // passed size heuristics. Do it.
      OPT_InlineDecision d = 
	OPT_InlineDecision.guardedYES(callee,
				      chooseGuard(caller, callee, state, false), 
				      "static GUARDED interface inline passsed size checks");
      return d;
    } else {
      return OPT_InlineDecision.NO(callee, "non-final interface method");
    }
  }


  /**
   * Check static size heuristics.
   *
   * @param caller the calling method
   * @param callee the callee method
   * @param state  miscellaneous state of the compilation
   * @param cost   estimate of the total space cost of the inlining action.
   * @return an inlining decision if it fails the size heuristics.
   *         null if it passes
   */
  private OPT_InlineDecision sizeHeuristics(VM_Method caller, 
					    VM_Method callee, 
					    OPT_CompilationState state, 
					    int cost) {
    OPT_Options opts = state.getOptions();
    // Is the callee just too big to inline under any circumstance?
    if (cost > opts.IC_MAX_TARGET_SIZE)
      return OPT_InlineDecision.NO("Callee too big");
    // Is the inlining action small enough that we should inline
    // under any circumstances?
    // We need to repeat this test again because the generic inline oracle
    // only applies it to unguarded inlines.
    if (cost < state.getOptions().IC_MAX_ALWAYS_INLINE_TARGET_SIZE)
      return null; // size check passes
    
    // Now, we'll make a decision based on how much inlining we've done already.
    // (1) We have a hard limit on the depth of inlining.
    if (state.getInlineDepth() > opts.IC_MAX_INLINE_DEPTH)
      return OPT_InlineDecision.NO("Inline depth limit exceeded");

    // (2) Check space limits.
    int totalMCGenerated = state.getMCSizeEstimate();
    int maxRootSize = getMaxRootSize(state);
    if ((totalMCGenerated + cost - VM_OptMethodSummary.CALL_COST) > maxRootSize)
      return OPT_InlineDecision.NO("Inlining size limit exceeded");

    return null; // size check passes
  }

  /**
   * Return the upper limit on the machine code instructions for the 
   * root method.
   * @param state compilation state
   * @return the upper limit on the machine code instructions for the 
   * root method.
   */
  private int getMaxRootSize (OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    int rootSize = VM_OptMethodSummary.inlinedSizeEstimate(state.getRootMethod());
    return Math.min(opts.IC_MAX_INLINE_EXPANSION_FACTOR*rootSize, 
		    opts.IC_MAX_METHOD_SIZE + rootSize);
  }
}



