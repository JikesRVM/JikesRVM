/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

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
   * @return an OPT_InlineDecision with the result
   */
  public OPT_InlineDecision shouldInline (OPT_CompilationState state) {
    if (!state.getOptions().INLINE) {
      return OPT_InlineDecision.NO("inlining not enabled");
    }

    VM_Method caller = state.getMethod();
    VM_Method callee = state.obtainTarget();
    
    if (state.isInvokeInterface()) {
      return shouldInlineInterfaceInternal(caller, callee, state);
    } else {
      // invokestatic, invokevirtual, invokespecial
      // perform generic checks to test common inlining cases.
      
      // Is inlining forbidden?
      if (callee.isNative())
        return OPT_InlineDecision.NO("native method");
      if (hasNoInlinePragma(callee, state))
        return OPT_InlineDecision.NO("pragmaNoInline");

      if (callee.isAbstract()) {
        return shouldInlineAbstractMethodInternal(caller, callee, state);
      }
      
      // Some callee methods should always be inlined, even if 
      // dynamically this call site is never executed.  If the 
      // callee is sufficiently small and can be inlined without a guard
      // then we save compile time and code space by inlining it.
      int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)callee, state);
      boolean guardless = state.getHasPreciseTarget() || !needsGuard(callee);
      if (inlinedSizeEstimate < state.getOptions().IC_MAX_ALWAYS_INLINE_TARGET_SIZE && 
          guardless &&
          !state.getSequence().containsMethod(callee)) { 
        return OPT_InlineDecision.YES(callee, "trivial inline");
      }
        
      if (state.getOptions().getOptLevel() == 0) {
        // at opt level 0, trivial inlines are the only kind we consider
        return OPT_InlineDecision.NO(callee, "Only do trivial inlines at O0");
      }

      if (guardless && hasInlinePragma(callee, state))
        return OPT_InlineDecision.YES(callee, "pragmaInline");

      // At this point, we know that it is legal to inline the call,
      // but we don't know whether it is desirable.  Invoke the "real"
      // inline oracle (a subclass) to make the tough decisions.
      return shouldInlineInternal(caller, callee, state, inlinedSizeEstimate);
    }
  }

  /**
   * Children must implement this method.
   * It contains the non-generic decision making portion of the oracle for
   * <b>invokevirtual</b>, <b>invokespecial</b>, and <b>invokestatic</b>.
   */
  protected abstract OPT_InlineDecision shouldInlineInternal(VM_Method caller, 
                                                             VM_Method callee, 
                                                             OPT_CompilationState state, 
                                                             int inlinedSizeEstimate);

  /**
   * Children must implement this method.
   * It contains the non-generic decision making portion of the oracle for
   * <b>invokeinterface</b>. 
   */
  protected abstract OPT_InlineDecision shouldInlineInterfaceInternal(VM_Method caller, 
                                                                      VM_Method callee, 
                                                                      OPT_CompilationState state);
  
  /**
   * Children must implement this method.
   * It contains the non-generic decision making portion of the oracle for invokeinterface.
   */
  protected abstract OPT_InlineDecision shouldInlineAbstractMethodInternal(VM_Method caller, 
                                                                           VM_Method callee, 
                                                                           OPT_CompilationState state);
  
  /**
   * Logic to select the appropriate guarding mechanism for the edge
   * from caller to callee according to the controlling OPT_Options.
   * If we are using IG_CODE_PATCH, then these method also records 
   * the required dependency.
   * Precondition: lock on VM_Class.OptCLDepManager is held.
   *
   * @param caller the caller method
   * @param callee the callee method
   * @param opts the opt options
   * @param codePatchSupported can we use code patching at this call site?
   */
  protected byte chooseGuard(VM_Method caller,
                             VM_Method singleImpl,
                             VM_Method callee,
                             OPT_CompilationState state,
                             boolean codePatchSupported) {
    byte guard = state.getOptions().INLINING_GUARD;
    if (codePatchSupported) {
      if (VM.VerifyAssertions && VM.runningVM) {
        VM._assert(VM_Lock.owns(VM_Class.OptCLDepManager));
      }
      if (guard == OPT_Options.IG_CODE_PATCH) {
        if (OPT_ClassLoadingDependencyManager.TRACE || 
            OPT_ClassLoadingDependencyManager.DEBUG) {
          VM_Class.OptCLDepManager.report("CODE PATCH: Inlined "
                                          + singleImpl + " into " + caller + "\n");
        }
        VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, 
                                                            state.getCompiledMethod());
      }
    } else if (guard == OPT_Options.IG_CODE_PATCH) {
      guard = OPT_Options.IG_METHOD_TEST;
    }

    if (guard == OPT_Options.IG_METHOD_TEST && 
        singleImpl.getDeclaringClass().isFinal()) {
      // class test is more efficient and just as effective
      guard = OPT_Options.IG_CLASS_TEST;
    }
    return guard;
  }

  /**
   * Estimate the expected cost of the inlining action
   * (inclues both the inline body and the guard/off-branch code).
   *
   * @param inlinedBodyEstimate size estimate for inlined body
   * @param needsGuard is it going to be a guarded inline?
   * @param preEx      can preEx inlining be used to avoid the guard?
   * @param opts       controlling options object
   * @return the estimated cost of the inlining action
   */
  protected int inliningActionCost(int inlinedBodyEstimate, 
                                   boolean needsGuard, 
                                   boolean preEx, 
                                   OPT_Options opts) {
    int guardCost = 0;
    if (needsGuard & !preEx) {
      guardCost += VM_NormalMethod.CALL_COST;
      if (opts.guardWithMethodTest()) {
        guardCost += 3*VM_NormalMethod.SIMPLE_OPERATION_COST;
      } else if (opts.guardWithCodePatch()) {
        guardCost += VM_NormalMethod.SIMPLE_OPERATION_COST;
      } else { // opts.guardWithClassTest()
        guardCost += 2*VM_NormalMethod.SIMPLE_OPERATION_COST;
      }
    }
    return guardCost + inlinedBodyEstimate;
  }
}



