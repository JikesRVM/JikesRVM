/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.ArrayList;
import java.util.Iterator;
import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.VM_AdaptiveInlining;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.database.callgraph.VM_WeightedCallTargets;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.opt.ir.CompilationState;
import org.jikesrvm.compilers.opt.ir.InlineSequence;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.scheduler.VM_Scheduler;

/**
 * The default inlining oracle used by the optimizing compiler.
 * The basic strategy is as follows:
 *  (1) Always inline trivial methods that can be inlined without a guard
 *  (2) At O1 and greater use a mix of profile information and static heuristics
 *      to inline larger methods and methods that require guards.
 */
public final class DefaultInlineOracle extends InlineTools implements InlineOracle {

  /**
   * Should we inline a particular call site?
   *
   * @param state information needed to make the inlining decision
   * @return an InlineDecision with the result
   */
  public InlineDecision shouldInline(final CompilationState state) {
    final Options opts = state.getOptions();
    final boolean verbose = opts.PRINT_DETAILED_INLINE_REPORT;
    if (!opts.INLINE) {
      return InlineDecision.NO("inlining not enabled");
    }

    final VM_Method staticCallee = state.obtainTarget();
    final VM_NormalMethod rootMethod = state.getRootMethod();
    final VM_Method caller = state.getMethod();
    final int bcIndex = state.getBytecodeIndex();

    if (verbose) VM.sysWriteln("Begin inline decision for " + "<" + caller + "," + bcIndex + "," + staticCallee + ">");

    // Stage 1: At all optimization levels we should attempt to inline
    //          trivial methods. Even if the inline code is never executed,
    //          inlining a trivial method is a no cost operation as the impact
    //          on code size should be negligible and compile time usually is
    //          reduced since we expect to eliminate the call instruction (or
    //          at worse replace one call instruction with another one).
    if (!state.isInvokeInterface()) {
      if (staticCallee.isNative()) {
        if (verbose) VM.sysWriteln("\tNO: native method\n");
        return InlineDecision.NO("native method");
      }
      if (hasNoInlinePragma(staticCallee, state)) {
        if (verbose) VM.sysWriteln("\tNO: pragmaNoInline\n");
        return InlineDecision.NO("pragmaNoInline");
      }
      if (// are we calling a throwable constructor
          staticCallee.isObjectInitializer() &&
          staticCallee.getDeclaringClass().isThrowable() &&
          // and not from a throwable constructor
          !(caller.isObjectInitializer() &&
              caller.getDeclaringClass().isThrowable())) {
        // We need throwable constructors to have their own compiled method IDs
        // to correctly elide stack frames when generating stack traces (see
        // VM_StackTrace).
        if (verbose) VM.sysWriteln("\tNO: throwable constructor\n");
        return InlineDecision.NO("throwable constructor");
      }

      if (!staticCallee.isAbstract()) {
        int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod) staticCallee, state);
        boolean guardless = state.getHasPreciseTarget() || !needsGuard(staticCallee);
        if (inlinedSizeEstimate < opts.IC_MAX_ALWAYS_INLINE_TARGET_SIZE &&
            guardless &&
            !state.getSequence().containsMethod(staticCallee)) {
          if (verbose) VM.sysWriteln("\tYES: trivial guardless inline\n");
          return InlineDecision.YES(staticCallee, "trivial inline");
        }
      }
    }

    if (opts.getOptLevel() == 0) {
      // at opt level 0, trivial unguarded inlines are the only kind we consider
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines at O0\n");
      return InlineDecision.NO("Only do trivial inlines at O0");
    }

    if (rootMethod.inlinedSizeEstimate() > opts.IC_MASSIVE_METHOD_SIZE) {
      // In massive methods, we do not do any additional inlining to
      // avoid completely blowing out compile time by making a bad situation worse
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines into massive methods\n");
      return InlineDecision.NO("Root method is massive; no non-trivial inlines");
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
    synchronized (VM_Class.classLoadListener) {

      boolean guardOverrideOnStaticCallee = false;
      if (targets == null) {
        if (verbose) VM.sysWriteln("\tNo profile data");
        // No profile information.
        // Fake up "profile data" based on static information to
        // be able to share all the decision making logic.
        if (state.isInvokeInterface()) {
          if (opts.GUARDED_INLINE_INTERFACE) {
            VM_Method singleImpl = InterfaceHierarchy.getUniqueImplementation(staticCallee);
            if (singleImpl != null && hasBody(singleImpl)) {
              if (verbose) {
                VM.sysWriteln("\tFound a single implementation " +
                              singleImpl +
                              " of an interface method " +
                              staticCallee);
              }
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
              VM_Method singleImpl =
                  subClasses[0].findDeclaredMethod(staticCallee.getName(), staticCallee.getDescriptor());
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
      if (targets == null) return InlineDecision.NO("No potential targets identified");

      // Stage 3: We have one or more targets.  Determine what if anything should be done with them.
      final ArrayList<VM_Method> methodsToInline = new ArrayList<VM_Method>();
      final ArrayList<Boolean> methodsNeedGuard = new ArrayList<Boolean>();
      final double callSiteWeight = targets.totalWeight();
      final boolean goosc = guardOverrideOnStaticCallee; // real closures anyone?
      final boolean ps = purelyStatic;                   // real closures anyone?
      targets.visitTargets(new VM_WeightedCallTargets.Visitor() {
        public void visit(VM_Method callee, double weight) {
          if (hasBody(callee)) {
            if (verbose) {
              VM.sysWriteln("\tEvaluating target " +
                            callee +
                            " with " +
                            weight +
                            " samples (" +
                            (100 * VM_AdaptiveInlining.adjustedWeight(weight)) +
                            "%)");
            }
            // Don't inline recursively and respect no inline pragmas
            InlineSequence seq = state.getSequence();
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
                (goosc || (staticCallee == callee)) && isCurrentlyFinal(callee, !opts.guardWithClassTest());
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
                if (((VM_OptCompiledMethod) prev).getMCMap().hasInlinedEdge(caller, bcIndex, callee)) {
                  if (verbose) VM.sysWriteln("\t\tSelect: Previously inlined");
                  decideYes = true;
                }
              }

              if (!decideYes) {
                int inlinedSizeEstimate = inlinedSizeEstimate((VM_NormalMethod) callee, state);
                int cost = inliningActionCost(inlinedSizeEstimate, needsGuard, preEx, opts);
                int maxCost = opts.IC_MAX_TARGET_SIZE;

                if (callSiteWeight > VM_Controller.options.AI_SEED_MULTIPLIER) {
                  // real profile data with enough samples for us to trust it.
                  // Use weight and shape of call site distribution to compute
                  // a higher maxCost.
                  double fractionOfSample = weight / callSiteWeight;
                  if (needsGuard && fractionOfSample < opts.AI_MIN_CALLSITE_FRACTION) {
                    // This call accounts for less than AI_MIN_CALLSITE_FRACTION
                    // of the profiled targets at this call site.
                    // It is highly unlikely to be profitable to inline it.
                    if (verbose) VM.sysWriteln("\t\tReject: less than AI_MIN_CALLSITE_FRACTION of distribution");
                    maxCost = 0;
                  } else {
                    if (cost > maxCost) {
                      /* We're going to increase the maximum callee size (maxCost) we're willing
                       * to inline based on how "hot" (what % of the total weight in the
                       * dynamic call graph) the edge is.
                       */
                      double adjustedWeight = VM_AdaptiveInlining.adjustedWeight(weight);
                      if (adjustedWeight > VM_Controller.options.AI_HOT_CALLSITE_THRESHOLD) {
                        /* A truly hot edge; use the max allowable callee size */
                        maxCost = opts.AI_MAX_TARGET_SIZE;
                      } else {
                        /* A warm edge, we will use a value between the static default and the max allowable.
                         * The code below simply does a linear interpolation between 2x static default
                         * and max allowable.
                         * Other alternatives would be to do a log interpolation or some other step function.
                         */
                        int range = opts.AI_MAX_TARGET_SIZE -  2*opts.IC_MAX_TARGET_SIZE;
                        double slope = ((double) range) / VM_Controller.options.AI_HOT_CALLSITE_THRESHOLD;
                        int scaledAdj = (int) (slope * adjustedWeight);
                        maxCost += opts.IC_MAX_TARGET_SIZE + scaledAdj;
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
                    VM.sysWriteln("\t\tAccept: cost of " + cost + " was below threshold " + maxCost);
                  } else {
                    VM.sysWriteln("\t\tReject: cost of " + cost + " was above threshold " + maxCost);
                  }
                }
              }
            }

            if (decideYes) {
              // Ok, we're going to inline it.
              // Record that and also whether or not we think it needs a guard.
              methodsToInline.add(callee);
              if (preEx) {
                ClassLoadingDependencyManager cldm = (ClassLoadingDependencyManager) VM_Class.classLoadListener;
                if (ClassLoadingDependencyManager.TRACE || ClassLoadingDependencyManager.DEBUG) {
                  cldm.report("PREEX_INLINE: Inlined " + callee + " into " + caller + "\n");
                }
                cldm.addNotOverriddenDependency(callee, state.getCompiledMethod());
                if (goosc) {
                  cldm.addNotOverriddenDependency(staticCallee, state.getCompiledMethod());
                }
                methodsNeedGuard.add(Boolean.FALSE);
              } else {
                methodsNeedGuard.add(needsGuard);
              }
            }
          }
        }
      });

      // Stage 4: Choose guards and package up the results in an InlineDecision object
      if (methodsToInline.isEmpty()) {
        InlineDecision d = InlineDecision.NO("No desirable targets");
        if (verbose) VM.sysWriteln("\tDecide: " + d);
        return d;
      } else if (methodsToInline.size() == 1) {
        VM_Method target = methodsToInline.get(0);
        boolean needsGuard = methodsNeedGuard.get(0);
        if (needsGuard) {
          if ((guardOverrideOnStaticCallee || target == staticCallee) &&
              isCurrentlyFinal(target, !opts.guardWithClassTest())) {
            InlineDecision d =
                InlineDecision.guardedYES(target,
                                              chooseGuard(caller, target, staticCallee, state, true),
                                              "Guarded inline of single static target");
            /*
             * Determine if it is allowable to put an OSR point in the failed case of
             * the guarded inline instead of generating a real call instruction.
             * There are several conditions that must be met for this to be allowable:
             *   (1) OSR guarded inlining and recompilation must both be enabled
             *   (2) The current context must be an interruptible method
             *   (3) The application must be started.  This is a rough proxy for the VM
             *       being fully booted so we can actually get through the OSR process.
             *       Note: One implication of this requirement is that we will
             *       never put an OSR on an off-branch of a guarded inline in bootimage
             *       code.
             */
            if (opts.OSR_GUARDED_INLINING && VM_Controller.options.ENABLE_RECOMPILATION &&
                caller.isInterruptible() &&
                Compiler.getAppStarted()) {
                if (VM.VerifyAssertions) VM._assert(VM.runningVM);
                d.setOSRTestFailed();
            }
            if (verbose) VM.sysWriteln("\tDecide: " + d);
            return d;
          } else {
            InlineDecision d =
                InlineDecision.guardedYES(target,
                                              chooseGuard(caller, target, staticCallee, state, false),
                                              "Guarded inlining of one potential target");
            if (verbose) VM.sysWriteln("\tDecide: " + d);
            return d;
          }
        } else {
          InlineDecision d = InlineDecision.YES(target, "Unique and desirable target");
          if (verbose) VM.sysWriteln("\tDecide: " + d);
          return d;
        }
      } else {
        VM_Method[] methods = new VM_Method[methodsNeedGuard.size()];
        byte[] guards = new byte[methods.length];
        int idx = 0;
        Iterator<VM_Method> methodIterator = methodsToInline.iterator();
        Iterator<Boolean> guardIterator = methodsNeedGuard.iterator();
        while (methodIterator.hasNext()) {
          VM_Method target = methodIterator.next();
          boolean needsGuard = guardIterator.next();
          if (VM.VerifyAssertions) VM._assert(needsGuard);
          methods[idx] = target;
          guards[idx] = chooseGuard(caller, target, staticCallee, state, false);
          idx++;
        }
        InlineDecision d = InlineDecision.guardedYES(methods, guards, "Inline multiple targets");
        if (verbose) VM.sysWriteln("\tDecide: " + d);
        return d;
      }
    }
  }

  /**
   * Logic to select the appropriate guarding mechanism for the edge
   * from caller to callee according to the controlling {@link Options}.
   * If we are using IG_CODE_PATCH, then this method also records
   * the required dependency.
   * Precondition: lock on {@link VM_Class#classLoadListener} is held.
   *
   * @param caller The caller method
   * @param callee The callee method
   * @param codePatchSupported   Can we use code patching at this call site?
   */
  private byte chooseGuard(VM_Method caller, VM_Method singleImpl, VM_Method callee, CompilationState state,
                           boolean codePatchSupported) {
    byte guard = state.getOptions().INLINING_GUARD;
    if (codePatchSupported) {
      if (VM.VerifyAssertions && VM.runningVM) {
        VM._assert(VM_ObjectModel.holdsLock(VM_Class.classLoadListener, VM_Scheduler.getCurrentThread()));
      }
      if (guard == Options.IG_CODE_PATCH) {
        ClassLoadingDependencyManager cldm = (ClassLoadingDependencyManager) VM_Class.classLoadListener;
        if (ClassLoadingDependencyManager.TRACE || ClassLoadingDependencyManager.DEBUG) {
          cldm.report("CODE PATCH: Inlined " + singleImpl + " into " + caller + "\n");
        }
        cldm.addNotOverriddenDependency(callee, state.getCompiledMethod());
      }
    } else if (guard == Options.IG_CODE_PATCH) {
      guard = Options.IG_METHOD_TEST;
    }

    if (guard == Options.IG_METHOD_TEST && singleImpl.getDeclaringClass().isFinal()) {
      // class test is more efficient and just as effective
      guard = Options.IG_CLASS_TEST;
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
  private int inliningActionCost(int inlinedBodyEstimate, boolean needsGuard, boolean preEx, Options opts) {
    int guardCost = 0;
    if (needsGuard & !preEx) {
      guardCost += VM_NormalMethod.CALL_COST;
      if (opts.guardWithMethodTest()) {
        guardCost += 3 * VM_NormalMethod.SIMPLE_OPERATION_COST;
      } else if (opts.guardWithCodePatch()) {
        guardCost += VM_NormalMethod.SIMPLE_OPERATION_COST;
      } else { // opts.guardWithClassTest()
        guardCost += 2 * VM_NormalMethod.SIMPLE_OPERATION_COST;
      }
    }
    return guardCost + inlinedBodyEstimate;
  }
}



