/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.adaptive.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * The default inlining oracle used by the optimizing compiler.
 * The basic strategy is as follows:
 *  (1) Always inline trivial methods that can be inlined without a guard
 *  (2) At O1 and greater use a mix of profile information and static heuristics
 *      to inline larger methods and methods that require guards.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public final class OPT_DefaultInlineOracle extends OPT_InlineTools
  implements OPT_InlineOracle {

  /**
   * Should we inline a particular call site?
   *
   * @param state information needed to make the inlining decision
   * @return an OPT_InlineDecision with the result
   */
  public OPT_InlineDecision shouldInline (final OPT_CompilationState state) {
    final OPT_Options opts = state.getOptions();
    final boolean verbose = opts.PRINT_DETAILED_INLINE_REPORT;
    if (!opts.INLINE) {
      return OPT_InlineDecision.NO("inlining not enabled");
    }

    final VM_Method staticCallee = state.obtainTarget();
    final VM_NormalMethod rootMethod = state.getRootMethod();
    final VM_Method caller = state.getMethod();
    final int bcIndex = state.getBytecodeIndex();

    if (verbose) VM.sysWriteln("Begin inline decision for "+"<"+caller+","+bcIndex+","+staticCallee+">");
    
    // Stage 1: At all optimization levels we should attempt to inline
    //          trivial methods. Even if the inline code is never executed,
    //          inlining a trivial method is a no cost operation as the impact
    //          on code size should be negligible and compile time usually is
    //          reduced since we expect to eliminate the call instruction (or
    //          at worse replace one call instruction with another one).
    if (!state.isInvokeInterface()) {
      if (staticCallee.isNative()) {
        if (verbose) VM.sysWriteln("\tNO: native method\n");
        return OPT_InlineDecision.NO("native method");
      }
      if (hasNoInlinePragma(staticCallee, state)) {
        if (verbose) VM.sysWriteln("\tNO: pragmaNoInline\n");
        return OPT_InlineDecision.NO("pragmaNoInline");
      }
      if (!staticCallee.isAbstract()) {
        int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)staticCallee, state);
        boolean guardless = state.getHasPreciseTarget() || !needsGuard(staticCallee);
        if (inlinedSizeEstimate < opts.IC_MAX_ALWAYS_INLINE_TARGET_SIZE && 
            guardless &&
            !state.getSequence().containsMethod(staticCallee)) {
          if (verbose) VM.sysWriteln("\tYES: trivial guardless inline\n");
          return OPT_InlineDecision.YES(staticCallee, "trivial inline");
        }
      }
    }

    if (opts.getOptLevel() == 0) {
      // at opt level 0, trivial unguarded inlines are the only kind we consider
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines at O0\n");
      return OPT_InlineDecision.NO("Only do trivial inlines at O0");
    }

    if (rootMethod.inlinedSizeEstimate() > opts.IC_MASSIVE_METHOD_SIZE) {
      // In massive methods, we do not do any additional inlining to
      // avoid completely blowing out compile time by making a bad situation worse
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines into massive methods\n");
      return OPT_InlineDecision.NO("Root method is massive; no non-trivial inlines");
    }

    // Stage 2: Determine based on profile data and static information
    //          what are the possible targets of this call.
    //
    VM_WeightedCallTargets targets = null;
    boolean purelyStatic = true;
    if (VM_Controller.dcg != null && VM_Controller.options.ADAPTIVE_INLINING) {
      targets = VM_Controller.dcg.getCallTargets(caller, bcIndex);
      if (targets != null) {
        if (verbose) VM.sysWriteln("\tFound profile data");
        purelyStatic = false;
        if (state.getHasPreciseTarget()) {
          // static analysis tells us that there is only one possible target.
          // Filter the profile information accordingly.
          targets = targets.filter(staticCallee);
          if (verbose) VM.sysWriteln("\tFiltered to match precise target");
          if (targets == null) {
            if (verbose) VM.sysWriteln("\tNow no profile data...");
            // After filtering, no matching profile data, fall back to
            // static information to avoid degradations
            targets = VM_WeightedCallTargets.create(staticCallee, 0);
            purelyStatic = true;
          }
        }
      }
    }

    // Critical section: must prevent class hierarchy from changing while
    // we are inspecting it to determine how/whether to do the inline guard.
    synchronized(VM_Class.OptCLDepManager) {

      boolean guardOverrideOnStaticCallee = false;
      if (targets == null) {
        if (verbose) VM.sysWriteln("\tNo profile data");
        // No profile information.
        // Fake up "profile data" based on static information to
        // be able to share all the decision making logic.
        if (state.isInvokeInterface()) {
          if (opts.GUARDED_INLINE_INTERFACE) {
            VM_Method singleImpl = OPT_InterfaceHierarchy.getUniqueImplementation(staticCallee);
            if (singleImpl != null && hasBody(singleImpl)) {
              if (verbose) VM.sysWriteln("\tFound a single implementation "+singleImpl+" of an interface method "+staticCallee);
              targets = VM_WeightedCallTargets.create(singleImpl, 0);
              guardOverrideOnStaticCallee = true;
            }
          }
        } else {
          // invokestatic, invokevirtual, invokespecial
          if (staticCallee.isAbstract()) {
            // look for single non-abstract implementation of the abstract method
            VM_Class klass = staticCallee.getDeclaringClass();
            while (true) {
              VM_Class[] subClasses = klass.getSubClasses();
              if (subClasses.length != 1) break; // multiple subclasses => multiple targets
              VM_Method singleImpl = subClasses[0].findDeclaredMethod(staticCallee.getName(),
                                                                      staticCallee.getDescriptor());
              if (singleImpl != null && !singleImpl.isAbstract()) {
                // found something
                if (verbose) VM.sysWriteln("\tsingle impl of abstract method");
                targets = VM_WeightedCallTargets.create(singleImpl, 0);
                guardOverrideOnStaticCallee = true;
                break;
              }
              klass = subClasses[0]; // keep crawling down the hierarchy
            }
          } else {
            targets = VM_WeightedCallTargets.create(staticCallee, 0);
          }
        }
      }
      
      // At this point targets is either null, or accurately represents what we
      // think are the likely target(s) of the call site.
      // This information may be either derived from profile information or
      // from static heuristics. To the first approximation, we don't care which.
      // If there is a precise target, then targets contains exactly that target method.
      if (targets == null) return OPT_InlineDecision.NO("No potential targets identified");
      
      // Stage 3: We have one or more targets.  Determine what if anything should be done with them.
      final ArrayList methodsToInline = new ArrayList(); 
      final ArrayList methodsNeedGuard = new ArrayList();
      final double callSiteWeight = targets.totalWeight();
      final boolean goosc = guardOverrideOnStaticCallee; // real closures anyone?
      final boolean ps = purelyStatic;                   // real closures anyone?
      targets.visitTargets(new VM_WeightedCallTargets.Visitor() {
          public void visit(VM_Method callee, double weight) {
            if (hasBody(callee)) {
              if (verbose) {
                VM.sysWriteln("\tEvaluating target "+callee+" with "+weight+
                              " samples ("+(100*VM_AdaptiveInlining.adjustedWeight(weight))+"%)");
              }
              // Don't inline recursively and respect no inline pragmas
              OPT_InlineSequence seq = state.getSequence();
              if (seq.containsMethod(callee)) {
                if (verbose) VM.sysWriteln("\t\tReject: recursive");
                return;
              }
              if (hasNoInlinePragma(callee, state)) {
                if (verbose) VM.sysWriteln("\t\tReject: noinline pragma");
                return;
              }

              // more or less figure out the guard situation early -- impacts size estimate.
              boolean needsGuard = !state.getHasPreciseTarget() && (staticCallee != callee || needsGuard(staticCallee));
              if (needsGuard && isForbiddenSpeculation(state.getRootMethod(), callee)) {
                if (verbose) VM.sysWriteln("\t\tReject: forbidden speculation");
                return;
              }
              boolean currentlyFinal =
                (goosc || (staticCallee == callee)) && isCurrentlyFinal(callee,
                                                                        !opts.guardWithClassTest());
              boolean preEx = needsGuard && state.getIsExtant() && opts.PREEX_INLINE && currentlyFinal;
              if (needsGuard && !preEx) {
                if (!opts.GUARDED_INLINE) {
                  if (verbose) VM.sysWriteln("\t\tReject: guarded inlining disabled");
                  return;
                }
                if (!currentlyFinal && ps) {
                  if (verbose) VM.sysWriteln("\t\tReject: multiple targets and no profile data");
                  return;
                }
              }
              
              // Estimate cost of performing this inlining action.
              // Includes cost of guard & off-branch call if they are going to be generated.
              boolean decideYes = false;
              if (hasInlinePragma(callee, state)) {
                if (verbose) VM.sysWriteln("\t\tSelect: pragma inline");
                decideYes = true;
              } else {
                // Preserve previous inlining decisions
                // Not the best thing in the world due to phase shifts, but
                // it does buy some degree of stability. So, it is probably the lesser
                // of two evils.
                VM_CompiledMethod prev = state.getRootMethod().getCurrentCompiledMethod();
                if (prev != null && prev.getCompilerType() == VM_CompiledMethod.OPT) {
                  if (((VM_OptCompiledMethod)prev).getMCMap().hasInlinedEdge(caller, bcIndex, callee)) {
                    if (verbose) VM.sysWriteln("\t\tSelect: Previously inlined");
                    decideYes = true;
                  }
                }

                if (!decideYes) {
                  int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod)callee, state);
                  int cost = inliningActionCost(inlinedSizeEstimate, needsGuard, preEx, opts);
                  int maxCost = opts.IC_MAX_TARGET_SIZE;

                  if (callSiteWeight > VM_Controller.options.AI_SEED_MULTIPLIER) {
                    // real profile data with enough samples for us to trust it.
                    // Use weight and shape of call site distrubution to compute
                    // a higher maxCost.
                    double fractionOfSample = weight/callSiteWeight;
                    if (needsGuard && fractionOfSample < opts.AI_MIN_CALLSITE_FRACTION) {
                      // This call accounts for less than AI_MIN_CALLSITE_FRACTION
                      // of the profiled targets at this call site.
                      // It is highly unlikely to be profitable to inline it.
                      if (verbose) VM.sysWriteln("\t\tReject: less than AI_MIN_CALLSITE_FRACTION of distribution");
                      maxCost = 0;
                    } else {
                      if (cost > maxCost) {
                        // adjust up based on weight of callsite
                        double adjustedWeight = VM_AdaptiveInlining.adjustedWeight(weight);
                        if (adjustedWeight > VM_Controller.options.AI_CONTROL_POINT) {
                          maxCost = opts.AI_MAX_TARGET_SIZE;
                        } else {
                          int range = opts.AI_MAX_TARGET_SIZE - opts.IC_MAX_TARGET_SIZE;
                          double slope = ((double)range) / VM_Controller.options.AI_CONTROL_POINT;
                          int sizeAdj = (int) (slope * adjustedWeight);
                          maxCost += sizeAdj;
                        }
                      }
                    }
                  }

                  // Somewhat bogus, but if we get really deeply inlined we start backing off.
                  int curDepth = state.getInlineDepth();
                  if (curDepth > opts.IC_MAX_INLINE_DEPTH) {
                    maxCost /= (curDepth - opts.IC_MAX_INLINE_DEPTH + 1);
                  }
                  
                  decideYes = cost <= maxCost;
                  if (verbose) {
                    if (decideYes) {
                      VM.sysWriteln("\t\tAccept: cost of "+cost+" was below threshold "+maxCost);
                    } else {
                      VM.sysWriteln("\t\tReject: cost of "+cost+" was above threshold "+maxCost);
                    }
                  }
                }
              }

              if (decideYes) {
                // Ok, we're going to inline it.
                // Record that and also whether or not we think it needs a guard.
                methodsToInline.add(callee);
                if (preEx) {
                  if (OPT_ClassLoadingDependencyManager.TRACE || 
                      OPT_ClassLoadingDependencyManager.DEBUG) {
                    VM_Class.OptCLDepManager.report("PREEX_INLINE: Inlined "
                                                    + callee + " into " + caller + "\n");
                  }
                  VM_Class.OptCLDepManager.addNotOverriddenDependency(callee, state.getCompiledMethod());
                  if (goosc) {
                    VM_Class.OptCLDepManager.addNotOverriddenDependency(staticCallee, state.getCompiledMethod());
                  }
                  methodsNeedGuard.add(Boolean.FALSE);
                } else {
                  methodsNeedGuard.add(Boolean.valueOf(needsGuard));
                }
              }
            }
          }
        });

      // Stage 4: Choose guards and package up the results in an InlineDecision object
      if (methodsToInline.size() == 0) {
        OPT_InlineDecision d = OPT_InlineDecision.NO("No desirable targets");
        if (verbose) VM.sysWriteln("\tDecide: "+d);
        return d;
      } else if (methodsToInline.size() == 1) {
        VM_Method target = (VM_Method)methodsToInline.get(0);
        boolean needsGuard = ((Boolean)methodsNeedGuard.get(0)).booleanValue();
        if (needsGuard) {
          if ((guardOverrideOnStaticCallee || target == staticCallee) &&
              isCurrentlyFinal(target, !opts.guardWithClassTest())) {
            OPT_InlineDecision d = OPT_InlineDecision.guardedYES(target, 
                                                                 chooseGuard(caller, target, staticCallee, state, true), 
                                                                 "Guarded inline of single static target");
            //-#if RVM_WITH_OSR
            if (opts.OSR_GUARDED_INLINING && 
                OPT_Compiler.getAppStarted() &&
                VM_Controller.options.ENABLE_RECOMPILATION) {
              // note that we will OSR the failed case.
              d.setOSRTestFailed();
            }
            //-#endif
            if (verbose) VM.sysWriteln("\tDecide: "+d);
            return d;
          } else {
            OPT_InlineDecision d = OPT_InlineDecision.guardedYES(target,
                                                                 chooseGuard(caller, target, staticCallee, state, false), 
                                                                 "Guarded inlining of one potential target");
            if (verbose) VM.sysWriteln("\tDecide: "+d);
            return d;
          }
        } else {
          OPT_InlineDecision d = OPT_InlineDecision.YES(target, "Unique and desirable target");
          if (verbose) VM.sysWriteln("\tDecide: "+d);
          return d;
        }        
      } else {
        VM_Method[] methods = new VM_Method[methodsNeedGuard.size()];
        byte[] guards = new byte[methods.length];
        int idx = 0;
        for (Iterator methodIterator = methodsToInline.iterator(), guardIterator = methodsNeedGuard.iterator();
             methodIterator.hasNext();) {
          VM_Method target = (VM_Method)methodIterator.next();
          boolean needsGuard = ((Boolean)guardIterator.next()).booleanValue();
          if (VM.VerifyAssertions) VM._assert(needsGuard);
          methods[idx] = target;
          guards[idx] = chooseGuard(caller, target, staticCallee, state, false);
          idx++;
        } 
        OPT_InlineDecision d = OPT_InlineDecision.guardedYES(methods, guards, "Inline multiple targets");
        if (verbose) VM.sysWriteln("\tDecide: "+d);
        return d;
      }
    }
  }

  /**
   * Logic to select the appropriate guarding mechanism for the edge
   * from caller to callee according to the controlling {@link OPT_Options}.
   * If we are using IG_CODE_PATCH, then this method also records 
   * the required dependency.
   * Precondition: lock on {@link VM_Class#OptCLDepManager} is held.
   *
   * @param caller The caller method
   * @param callee The callee method
   * @param codePatchSupported   Can we use code patching at this call site?
   */
  private byte chooseGuard(VM_Method caller,
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
  private int inliningActionCost(int inlinedBodyEstimate, 
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



