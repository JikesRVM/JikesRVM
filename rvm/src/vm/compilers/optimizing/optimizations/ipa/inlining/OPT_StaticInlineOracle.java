/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Inlining oracle using static size heuristics during on-the-fly compilation.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
final class OPT_StaticInlineOracle extends OPT_GenericInlineOracle {

  OPT_StaticInlineOracle () {
  }

  /**
   * Check static size heuristics.
   *
   * @param caller the calling method
   * @param callee the callee method
   * @param state miscellaneous state of the compilation
   * @param inlinedSizeEstimate estimate of the size of callee if inlined
   * @return an inlining decision if it fails the size heuristics.
   *         null if it passes
   */
  private static OPT_InlineDecision sizeHeuristics(VM_Method caller, 
      VM_Method callee, OPT_CompilationState state, int inlinedSizeEstimate) {

    OPT_Options opts = state.getOptions();
    boolean needsGuard = needsGuard(callee);
    // If this is going to be a guarded inlining, 
    // then include the size of the guard code 
    // and the non-inlined call on the off branch in our inlinedSizeEstimate.
    if (needsGuard && state.getComputedTarget() == null) {
      inlinedSizeEstimate += VM_OptMethodSummary.CALL_COST + 
          3*VM_OptMethodSummary.SIMPLE_OPERATION_COST;
    }
    // Is the callee just too big to inline under any circumstance?
    if (inlinedSizeEstimate > opts.IC_MAX_TARGET_SIZE)
      return  OPT_InlineDecision.NO("Callee too big");
    // Now, we'll make a decision based on how much inlining we've done already.
    // (1) We have a hard limit on the depth of inlining.
    if (state.getInlineDepth() > opts.IC_MAX_INLINE_DEPTH)
      return  OPT_InlineDecision.NO("Inline depth limit exceeded");
    // (2) Check space limits.
    int totalMCGenerated = state.getMCSizeEstimate();
    int maxRootSize = getMaxRootSize(state);
    if (totalMCGenerated + inlinedSizeEstimate 
        - VM_OptMethodSummary.CALL_COST > maxRootSize)
      return  OPT_InlineDecision.NO("Inlining size limit exceeded");
    // (3) Don't allow the static inline oracle to inline recursive calls.
    // It isn't smart enough to do this effectively.
    OPT_InlineSequence seq = state.getSequence();
    if (seq.containsMethod(callee)) {
      return  OPT_InlineDecision.NO("recursive call");
    }
    // If got this far, all is OK, so return null.
    return null;
  }
  /**
   * The main routine whereby this oracle makes decisions whether or not
   * to inline.
   * @param caller the calling method
   * @param callee the callee method
   * @param state miscellaneous state of the compilation
   * @param inlinedSizeEstimate estimate of the size of callee if inlined
   * @return an inlining decisions
   */
  protected OPT_InlineDecision shouldInlineInternal (VM_Method caller, 
      VM_Method callee, OPT_CompilationState state, int inlinedSizeEstimate) {

    OPT_Options opts = state.getOptions();

    // eager inlining of invoke interface
    if (state.isInvokeInterface() && !opts.EAGER_INLINE_INTERFACE)
      return  OPT_InlineDecision.NO("invokeinterface");
    if (state.isInvokeInterface()) {
      return interfaceDecision(caller,callee,state);
    } 

    // inlining of statics and virtuals with size heuristics
    OPT_InlineDecision sizeCheck = sizeHeuristics(caller,callee,state,
                                                  inlinedSizeEstimate);
    if (sizeCheck != null) return sizeCheck;
    // Ok, the size looks good.  
    // Now figure out the details about what kind of guard (if any)
    // we are going to need and decide whether we can actually do the inlining.

    boolean needsGuard = needsGuard(callee);
    if (needsGuard) {
      if (state.getComputedTarget() != null) {
        return  OPT_InlineDecision.YES(callee, 
                                      "Computed-target passed size checks");
      } else {
        if (state.getIsExtant() && opts.PREEX_INLINE) {
          if (OPT_InlineTools.isCurrentlyFinal(callee, true)) {
            if (OPT_ClassLoadingDependencyManager.TRACE 
                || OPT_ClassLoadingDependencyManager.DEBUG) {
              VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "
                  + callee + " into " + caller + "\n");
            }
            VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
                state.getCompiledMethodId());
            return  OPT_InlineDecision.YES(callee, 
                "PREEX_INLINE passed size checks");
          }
        } else if (opts.EAGER_INLINE) {
          if (OPT_InlineTools.isCurrentlyFinal(callee, 
              opts.guardWithMethodTest())) {
            return  OPT_InlineDecision.unsafeYES(callee, 
                "static EAGER inline passsed size checks");
          }
        }
        return  OPT_InlineDecision.NO(callee, "non-final virtual method");
      }
    } else {
      return  OPT_InlineDecision.YES(callee, "Non-guarded passed size checks");
    }
  }

  /**
   * Having passed the size checks, generate an inline decision for an
   * invokeinterface instruction.
   * PRECONDITION: Only call this if options.EAGER_INLINE_INTERFACE.
   */
  private static OPT_InlineDecision interfaceDecision(VM_Method caller,
                                                      VM_Method callee,
                                            OPT_CompilationState state) {

    if (!callee.getDeclaringClass().isLoaded()) {
      return OPT_InlineDecision.NO("Cannot inline interface before it is loaded");
    } 
    VM_Method foo = OPT_InterfaceHierarchy.getUniqueImplementation(callee);
    if (foo != null) {
      // got a unique target in the current hierarchy. Attempt to inline it.

      // check legality of inlining
      if (!legalToInline(caller,foo))
        return OPT_InlineDecision.NO("Illegal interface inline");
      
      int inlinedSizeEstimate = OPT_InlineTools.inlinedSizeEstimate(foo,
                                                                    state);
      // check size heuristics
      OPT_InlineDecision sizeCheck = sizeHeuristics(caller,foo,state,
                                                    inlinedSizeEstimate);
      if (sizeCheck != null) return sizeCheck;

      // passed size heuristics. Do it.
      OPT_InlineDecision d = 
	OPT_InlineDecision.unsafeYES(foo, 
                "static EAGER interface inline passsed size checks");
      return d;
    } else {
        return OPT_InlineDecision.NO(callee, "non-final interface method");
    }
  }

  /**
   * Return the upper limit on the machine code instructions for the 
   * root method.
   * @param state compilation state
   * @return the upper limit on the machine code instructions for the 
   * root method.
   */
  private static int getMaxRootSize (OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    int rootSize = VM_OptMethodSummary.inlinedSizeEstimate(
                                                         state.getRootMethod());
    return  Math.min(opts.IC_MAX_INLINE_EXPANSION_FACTOR*rootSize, 
        opts.IC_MAX_METHOD_SIZE + rootSize);
  }
}



