/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.adaptive.VM_Controller;
//-#endif
/**
 * Inlining oracle using static size heuristics during on-the-fly compilation.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public final class OPT_StaticInlineOracle extends OPT_GenericInlineOracle {

  public OPT_StaticInlineOracle () {}

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

    // Critical section: must prevent class hierarchy from changing while
    // we are inspecting it to determine how/whether to do the inline guard.
    synchronized(VM_Class.OptCLDepManager) {
      OPT_Options opts = state.getOptions();
      // more or less figure out the guard situation early -- impacts size estimate.
      boolean needsGuard = !state.getHasPreciseTarget() && needsGuard(callee);
      boolean preEx = 
        needsGuard && state.getIsExtant() && opts.PREEX_INLINE && isCurrentlyFinal(callee, true);
    
      // See if inlining action passes simple size heuristics
      int cost = inliningActionCost(inlinedSizeEstimate, needsGuard, preEx, opts);
      OPT_InlineDecision sizeCheck = sizeHeuristics(caller, callee, state, cost);
      if (sizeCheck != null) return sizeCheck;

      // Ok, the size looks good, attempt to do it.
      if (needsGuard) {
        if (isForbiddenSpeculation(state.getRootMethod(), callee)) {
          return OPT_InlineDecision.NO("Forbidden speculation");
        }

        if (preEx) {
          if (OPT_ClassLoadingDependencyManager.TRACE || 
              OPT_ClassLoadingDependencyManager.DEBUG) {
            VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "
                                            + callee + " into " + caller + "\n");
          }
          VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
                                                              state.getCompiledMethod());
          return OPT_InlineDecision.YES(callee, "PREEX_INLINE passed size checks");
        } else if (opts.GUARDED_INLINE && isCurrentlyFinal(callee, !opts.guardWithClassTest())) {
          OPT_InlineDecision YES = OPT_InlineDecision.guardedYES(callee, 
                                                                 chooseGuard(caller, callee, callee, state, true), 
                                                                 "static guarded inline passsed size checks");
          //-#if RVM_WITH_OSR
          if (opts.OSR_GUARDED_INLINING && 
              OPT_Compiler.getAppStarted() &&
              (VM_Controller.options != null) &&
              VM_Controller.options.ENABLE_RECOMPILATION) {
            // note that we will OSR the failed case.
            YES.setOSRTestFailed();
          }
          //-#endif
          return YES;
        }
        return OPT_InlineDecision.NO(callee, "non-final virtual method");
      } else {
        return OPT_InlineDecision.YES(callee, "Non-guarded passed size checks");
      }
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
      
    if (isForbiddenSpeculation(state.getRootMethod(), callee)) {
      return OPT_InlineDecision.NO("Forbidden speculation");
    }

    VM_Method singleImpl = 
      OPT_InterfaceHierarchy.getUniqueImplementation(callee);
    if (singleImpl != null) {
      // Don't allow the static inline oracle to inline recursive calls.
      // It isn't smart enough to do this effectively.
      OPT_InlineSequence seq = state.getSequence();
      if (seq.containsMethod(callee)) {
	return OPT_InlineDecision.NO("recursive call");
      }

      // got a unique target in the current hierarchy. Attempt to inline it.
      if (!hasBody(singleImpl))
        return OPT_InlineDecision.NO("Illegal interface inline");
      
      int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)singleImpl, state);
      int cost = inliningActionCost(inlinedSizeEstimate, true, false, opts);

      OPT_InlineDecision sizeCheck = sizeHeuristics(caller, singleImpl, state, cost);
      if (sizeCheck != null) return sizeCheck;

      // passed size heuristics. Do it.
      OPT_InlineDecision d = 
	OPT_InlineDecision.guardedYES(singleImpl,
				      chooseGuard(caller, singleImpl, callee, state, false), 
				      "static GUARDED interface inline passsed size checks");
      return d;
    } else {
      return OPT_InlineDecision.NO(callee, "non-final interface method");
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
  protected OPT_InlineDecision shouldInlineAbstractMethodInternal(VM_Method caller,
								  VM_Method callee,
								  OPT_CompilationState state) {
    // Critical section: must prevent class hierarchy from changing while
    // we are inspecting it to determine how/whether to do the inlining
    synchronized(VM_Class.OptCLDepManager) {
      // see if there is only one implementation of the abstract method
      VM_Class klass = callee.getDeclaringClass();
      VM_Class[] subClasses = klass.getSubClasses();
      if (subClasses.length != 1) return OPT_InlineDecision.NO("abstract class doesn't have exactly one subclass");
      VM_Method singleImpl = 
        subClasses[0].findDeclaredMethod(callee.getName(), callee.getDescriptor());
      if (singleImpl == null || !hasBody(singleImpl)) {
        return OPT_InlineDecision.NO("Implementation of abstract method is illegal candidate");
      }
      if (hasNoInlinePragma(singleImpl, state))
        return OPT_InlineDecision.NO("pragmaNoInline");
    
      // Don't allow the static inline oracle to inline recursive calls.
      // It isn't smart enough to do this effectively.
      OPT_InlineSequence seq = state.getSequence();
      if (seq.containsMethod(singleImpl)) {
        return OPT_InlineDecision.NO("recursive call");
      }

      OPT_Options opts = state.getOptions();
      // more or less figure out the guard situation early -- impacts size estimate.
      boolean preEx = state.getIsExtant() && opts.PREEX_INLINE && isCurrentlyFinal(singleImpl, true);
    
      // See if inlining action passes simple size heuristics
      int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)singleImpl, state);
      int cost = inliningActionCost(inlinedSizeEstimate, true, preEx, opts);
      OPT_InlineDecision sizeCheck = sizeHeuristics(caller, singleImpl, state, cost);
      if (sizeCheck != null) return sizeCheck;

      if (isForbiddenSpeculation(state.getRootMethod(), callee)) {
        return OPT_InlineDecision.NO("Forbidden speculation");
      }

      // Ok, the size looks good, attempt to do it.
      if (preEx) {
        if (OPT_ClassLoadingDependencyManager.TRACE || 
            OPT_ClassLoadingDependencyManager.DEBUG) {
          VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "
                                          + singleImpl + " into " + caller + "\n");
        }
        VM_Class.OptCLDepManager.addNotOverriddenDependency(singleImpl, 
                                                            state.getCompiledMethod());
        VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
                                                            state.getCompiledMethod());
        return OPT_InlineDecision.YES(singleImpl, "PREEX_INLINE passed size checks");
      } else if (opts.GUARDED_INLINE && isCurrentlyFinal(singleImpl, !opts.guardWithClassTest())) {
        return OPT_InlineDecision.guardedYES(singleImpl, 
                                             chooseGuard(caller, singleImpl, callee, state, true), 
                                             "static guarded inline passsed size checks");
      }
      return OPT_InlineDecision.NO(callee, "abstract method with multiple implementations");
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
    if (cost < opts.IC_MAX_ALWAYS_INLINE_TARGET_SIZE)
      return null; // size check passes
    
    // Now, we'll make a decision based on how much inlining we've done already.
    // (1) We have a hard limit on the depth of inlining.
    if (state.getInlineDepth() > opts.IC_MAX_INLINE_DEPTH)
      return OPT_InlineDecision.NO("Inline depth limit exceeded");

    // (2) We only do trivial inlining in massive methods to avoid
    //     completely blowing out compile time by making the method even worse
    VM_NormalMethod rootMethod = state.getRootMethod();
    if (rootMethod.inlinedSizeEstimate() > opts.IC_MASSIVE_METHOD_SIZE) {
      return OPT_InlineDecision.NO("Root method is massive; no non-trivial inlines");
    }
    
    return null; // size check passes
  }
}
