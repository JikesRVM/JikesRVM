/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
  * This class implements the OPT_InlineOracle interface with a
  * profile-directed inlining strategy.
  *
  * @author Stephen Fink
  * @author Dave Grove
  * @modified Michael Hind
  */
public class OPT_ProfileDirectedInlineOracle extends OPT_GenericInlineOracle {

  // So much for sharing code with the generic guy.
  // The issue is that the callee extracted from the state may not
  // be the method that we want to inline (allow profile-directed guarded 
  // inlining of virtual calls to work as expected).
  // We also don't need to make as sharp a distinction between invokeinterface
  // and other invokes when using profile information to identify the hot targets.
  public OPT_InlineDecision shouldInline(OPT_CompilationState state) {
    OPT_Options opts = state.getOptions();
    VM_Method originalCallee = state.obtainTarget();

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
      if (state.getHasPreciseTarget() && callee != originalCallee) {
        recordRefusalToInlineHotEdge(state.getCompiledMethod(), caller, bcX, callee);
        return OPT_InlineDecision.NO("AI: mismatch between computed target and profile data");
      }
      if (!viableCandidate(caller, callee, state)) {
        recordRefusalToInlineHotEdge(state.getCompiledMethod(), caller, bcX, callee);
        return OPT_InlineDecision.NO("AI: candidate judged to be nonviable");
      }
      if (state.getHasPreciseTarget()) {
        return OPT_InlineDecision.YES(originalCallee, "AI: hot edge matches computed target");
      } 
      VM_Method staticCallee = state.obtainTarget();
      // Critical section: must prevent class hierarchy from changing while
      // we are inspecting it to determine how/whether to do the guard
      synchronized(VM_Class.OptCLDepManager) {
        if (candidateNeedsGuard(caller, staticCallee, state)) {
          if (opts.GUARDED_INLINE) {
            boolean codePatch = opts.guardWithCodePatch() && !state.isInvokeInterface() &&
              isCurrentlyFinal(staticCallee, true);
            byte guard = chooseGuard(caller, staticCallee, originalCallee, state, codePatch);
            if (guard == OPT_Options.IG_METHOD_TEST) {
              // see if we can get away with the cheaper class test on the actual target 
              guard = chooseGuard(caller, callee, originalCallee, state, false);
            }
            return OPT_InlineDecision.guardedYES(callee, guard,
                                                 "AI: guarded inline of hot edge");
          } else {
            recordRefusalToInlineHotEdge(state.getCompiledMethod(), caller, bcX, callee);
            return OPT_InlineDecision.NO("AI: guarded inlining disabled");
          }
        } else {
          return OPT_InlineDecision.YES(callee, "AI: hot edge");
        }
      }
    } else {
      // (2c) We have multiple hot edges to consider.
      if (state.getHasPreciseTarget()) {
        for (int i=0; i<targets.length; i++) {
          if (targets[i] == originalCallee) {
            if (viableCandidate(caller, targets[i], state)) {
              return OPT_InlineDecision.YES(originalCallee, "AI: hot edge matches computed target");
            }
          }
        }
        for (int i=0; i<targets.length; i++) {
          recordRefusalToInlineHotEdge(state.getCompiledMethod(), caller, bcX, targets[i]);
        }
        return OPT_InlineDecision.NO("AI: multiple hot edges, but none match computed target");
      } else {
        if (!opts.GUARDED_INLINE) 
          return OPT_InlineDecision.NO("AI: guarded inlining disabled");
        int viable = 0;
        for (int i=0; i<targets.length; i++) {
          if (viableCandidate(caller, targets[i], state)) {
            viable++;
          } else {
            recordRefusalToInlineHotEdge(state.getCompiledMethod(), caller, bcX, targets[i]);
            targets[i] = null;
          }
        }
        if (viable > 0) {
          VM_Method[] viableTargets = new VM_Method[viable];
          byte[] guards = new byte[viable];
          viable = 0;
          // Critical section: must prevent class hierarchy from changing while
          // we are inspecting it to determine how/whether to do the guard
          synchronized(VM_Class.OptCLDepManager) {
            for (int i=0; i<targets.length; i++) {
              if (targets[i] != null) {
                viableTargets[viable] = targets[i];
                guards[viable++] = chooseGuard(caller, targets[i], originalCallee, state, false);
              }
            }
          }
          return OPT_InlineDecision.guardedYES(viableTargets, 
                                               guards,
                                               "AI: viable hot edge(s) found");
        } else {
          return OPT_InlineDecision.NO("AI: all candidates judged to be nonviable");
        }
      }
    }
  }

  protected boolean viableCandidate(VM_Method caller, VM_Method callee, 
                                    OPT_CompilationState state) {
    // TODO: for now, don't inline recursively
    OPT_InlineSequence seq = state.getSequence();
    if (seq.containsMethod(callee)) return false;

    // Check inline pragmas
    if (hasInlinePragma(callee, state)) return true;
    if (hasNoInlinePragma(callee, state)) return false;

    int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)callee, state);
    
    // Callees above a certain size are too big to be considered 
    // even if the call arc is hot.
    if (inlinedSizeEstimate > state.getOptions().AI_MAX_TARGET_SIZE)  
      return false;
    
    return true;
  }


  // note: !state.getHasPreciseTarget is known to be null.
  protected boolean candidateNeedsGuard(VM_Method caller, VM_Method callee, 
                                        OPT_CompilationState state) {
    // for now, guard all inlined interface invocations.
    // TODO: is this too strict? Does pre-existance apply?
    if (state.isInvokeInterface()) return true;
    
    if (VM.VerifyAssertions && VM.runningVM) {
      VM._assert(VM_Lock.owns(VM_Class.OptCLDepManager));
    }
    if (needsGuard(callee)) {
      // check pre-existance
      if (state.getIsExtant() && state.getOptions().PREEX_INLINE) {
        if (isCurrentlyFinal(callee, true)) {
          // use pre-existence !!
          if (OPT_ClassLoadingDependencyManager.TRACE || OPT_ClassLoadingDependencyManager.DEBUG) {
            VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "+callee+
                                            " into "+caller+"\n");
          }
          VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
                                                              state.getCompiledMethod());
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

  protected OPT_InlineDecision shouldInlineInterfaceInternal(VM_Method caller, 
                                                             VM_Method callee, 
                                                             OPT_CompilationState state) {
    OPT_OptimizingCompilerException.UNREACHABLE();
    return null; // placate jikes.
  }

  protected OPT_InlineDecision shouldInlineAbstractMethodInternal(VM_Method caller, 
                                                                  VM_Method callee, 
                                                                  OPT_CompilationState state) {
    OPT_OptimizingCompilerException.UNREACHABLE();
    return null; // placate jikes.
  }

  protected void recordRefusalToInlineHotEdge(VM_CompiledMethod cm, VM_Method caller, int bcX, VM_Method callee) {
    // by default; nothing to do
  }

  /** Implementation */
  // repository for inlining decision information, stored as a tuple-space 
  protected OPT_InlinePlan plan; 
  protected OPT_StaticInlineOracle staticOracle = new OPT_StaticInlineOracle(); 

  /** 
   * construct an oracle that interfaces to a plan 
   */
  public OPT_ProfileDirectedInlineOracle(OPT_InlinePlan plan) {
    this.plan = plan;
  }
}
