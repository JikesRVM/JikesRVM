/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.*;
import java.util.*;

/**
  * This class implements the OPT_InlineOracle interface with an adaptive
  * (ie profile-directed) inlining strategy.
  *
  * @author Stephen Fink
  * @author Dave Grove
  * @modified Michael Hind
  */
final class OPT_AdaptiveInlineOracle extends OPT_GenericInlineOracle {

  // So much for sharing code with the generic guy.
  // The issue is that the callee extracted from the state may not
  // be the method that we want to inline (allow profile-directed guarded 
  // inlining of virtual calls to work as expected).
  public OPT_InlineDecision shouldInline(OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    
    if (!opts.INLINE) return OPT_InlineDecision.NO("inlining not enabled");
    
    // (1) If the static heuristics will inline this call, we're done.
    OPT_InlineDecision d = staticOracle.shouldInline(state);
    if (!d.isNO()) return d;

    // (2) Now see if this call site appears in the profile data.
    VM_Method caller = state.getMethod();
    int bcX = state.getBytecodeIndex();
    VM_Method[] targets = plan.getTargets(caller,bcX);
    if (targets == null) {
      // (2a) The profile data doesn't tell us anything, 
      // so go with the static oracle's answer.
      return d;
    } else if (targets.length == 1) {
      // (2b) We have a single hot edge in the profile data for this call site
      VM_Method callee = targets[0];
      VM_Method computedTarget = state.getComputedTarget();
      if (computedTarget != null && callee != computedTarget) {
	VM_AdaptiveInlining.recordRefusalToInlineHotEdge
	  (state.getCompiledMethodId(), caller, bcX, callee);
	return OPT_InlineDecision.NO
	  ("AI: mismatch between computed target and profile data");
      }
      if (!viableCandidate(caller, callee, state)) {
	VM_AdaptiveInlining.recordRefusalToInlineHotEdge
	  (state.getCompiledMethodId(), caller, bcX, callee);
	return OPT_InlineDecision.NO("AI: candidate judged to be nonviable");
      }
      if (computedTarget != null) {
	return OPT_InlineDecision.YES
	  (computedTarget, "AI: hot edge matches computed target");
      } 
      VM_Method staticCallee = state.obtainTarget();
      if (candidateNeedsGuard(caller, staticCallee, state)) {
	if (opts.EAGER_INLINE) {
	  return OPT_InlineDecision.unsafeYES
	    (callee, "AI: guarded inline of hot edge");
	} else {
	  VM_AdaptiveInlining.recordRefusalToInlineHotEdge
	    (state.getCompiledMethodId(), caller, bcX, callee);
	  return OPT_InlineDecision.NO("AI: eager inlining disabled");
	}
      } else {
        // TODO: the following exception test is extraneous ..
	if (staticCallee != callee) throw new OPT_OptimizingCompilerException
	  ("Profile target "+callee+
	   " doesn't match sole static target "+staticCallee);
	return OPT_InlineDecision.YES(callee, "AI: hot edge");
      }
    } else {
      // (2c) We have multiple hot edges to consider.
      VM_Method computedTarget = state.getComputedTarget();
      if (computedTarget != null) {
	for (int i=0; i<targets.length; i++) {
	  if (targets[i] == computedTarget) {
	    if (viableCandidate(caller, targets[i], state)) {
	      return OPT_InlineDecision.YES
		(computedTarget, "AI: hot edge matches computed target");
	    }
	  }
	}
	for (int i=0; i<targets.length; i++) {
	  VM_AdaptiveInlining.recordRefusalToInlineHotEdge
	    (state.getCompiledMethodId(), caller, bcX, targets[i]);
	}
	return OPT_InlineDecision.NO
	  ("AI: multiple hot edges, but none match computed target");
      } else {
	if (!opts.EAGER_INLINE) 
	  return OPT_InlineDecision.NO("AI: eager inlining disabled");
	int viable = 0;
	for (int i=0; i<targets.length; i++) {
	  if (viableCandidate(caller, targets[i], state)) {
	    viable++;
	  } else {
	    VM_AdaptiveInlining.recordRefusalToInlineHotEdge
	      (state.getCompiledMethodId(), caller, bcX, targets[i]);
	    targets[i] = null;
	  }
	}
	if (viable > 0) {
	  VM_Method[] viableTargets = new VM_Method[viable];
	  viable = 0;
	  for (int i=0; i<targets.length; i++) {
	    if (targets[i] != null) {
	      viableTargets[viable++] = targets[i];
	    }
	  }
	  if (viable == 1) {
	    return OPT_InlineDecision.unsafeYES(viableTargets[0], 
					"AI: one viable hot edge found");
	  } else {
	    return OPT_InlineDecision.unsafeYES(viableTargets, 
					"AI: multiple viable hot edges found");
	  }
	} else {
	  return OPT_InlineDecision.NO(
			       "AI: all candidates judged to be nonviable");
	}
      }
    }
  }
	

  private boolean viableCandidate(VM_Method caller, VM_Method callee, 
				  OPT_CompilationState state) {
    if (!legalToInline(caller, callee)) return false;

    // TODO: for now, don't inline recursively
    OPT_InlineSequence seq = state.getSequence();
    if (seq.containsMethod(callee)) return false;

    // Check inline pragmas
    if (OPT_InlineTools.hasInlinePragma(callee, state)) return true;
    if (OPT_InlineTools.hasNoInlinePragma(callee, state)) return false;

    int inlinedSizeEstimate = OPT_InlineTools.inlinedSizeEstimate
      (callee, state);
    
    // Callees above a certain size are too big to be considered 
    // even if the call arc is hot.
    if (inlinedSizeEstimate > state.getOptions().AI_MAX_TARGET_SIZE)  
      return false;

    // Make sure that inlining the callee won't push the caller 
    // over its upper space bounds.
    int totalMCGenerated = state.getMCSizeEstimate();
    if (totalMCGenerated + inlinedSizeEstimate - 
        VM_OptMethodSummary.CALL_COST > getMaxRootSize(state)) return false;

    return true;
  }


  // note: state.getComputedTarget() is known to be null.
  private boolean candidateNeedsGuard(VM_Method caller, VM_Method callee, 
				      OPT_CompilationState state) {
    // for now, guard all inlined interface invocations.
    // TODO: is this too strict? Does pre-existance apply?
    if (state.isInvokeInterface()) return true;
    
    if (needsGuard(callee)) {
      // check pre-existance
      if (state.getIsExtant() && state.getOptions().PREEX_INLINE) {
	if (OPT_InlineTools.isCurrentlyFinal(callee, true)) {
	  // use pre-existence !!
	  if (OPT_ClassLoadingDependencyManager.TRACE || OPT_ClassLoadingDependencyManager.DEBUG) {
	    VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "+callee+
					    " into "+caller+"\n");
	  }
	  VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
					      state.getCompiledMethodId());
	  return false;
	}
      }
      return true;
    }
    return false;
  }
	      

  
  protected OPT_InlineDecision shouldInlineInternal(VM_Method caller, 
						    VM_Method callee, 
						    OPT_CompilationState state,
						    int inlinedSizeEstimate)  {
    OPT_OptimizingCompilerException.UNREACHABLE();
    return null; // placate jikes.
  }

  /** Implementation */
  // repository for inlining decision information, stored as a tuple-space 
  private OPT_ContextFreeInlinePlan plan; 
  private OPT_StaticInlineOracle staticOracle = new OPT_StaticInlineOracle(); 

  /** 
   * construct an oracle that interfaces to a plan 
   */
  OPT_AdaptiveInlineOracle(OPT_ContextFreeInlinePlan plan) {
    this.plan = plan;
  }
  // TODO: eliminate the need for the following, which is needed by
  // OPT_InlineOracleDictionary
  public OPT_AdaptiveInlineOracle() {
    this.plan = null;
  }


  /**
   * Return the upper limit on the machine code instructions for the 
   * root method.
   * @param state compilation state
   */
  private int getMaxRootSize(OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    int rootSize = 
      VM_OptMethodSummary.inlinedSizeEstimate(state.getRootMethod());
    return Math.min(opts.AI_MAX_INLINE_EXPANSION_FACTOR * rootSize, 
		    opts.AI_MAX_METHOD_SIZE+rootSize);
  }
}
