/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.inlining;

import java.util.ArrayList;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.AdaptiveInlining;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.callgraph.WeightedCallTargets;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.OptimizingCompiler;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.scheduler.RVMThread;

/**
 * The default inlining oracle used by the optimizing compiler.
 * The basic strategy is as follows:
 * <ol>
 *  <li>Always inline trivial methods that can be inlined without a guard
 *  <li>At O1 and greater use a mix of profile information and static heuristics
 *      to inline larger methods and methods that require guards.
 * </ol>
 */
public final class DefaultInlineOracle extends InlineTools implements InlineOracle {

  @Override
  public InlineDecision shouldInline(final CompilationState state) {
    final OptOptions opts = state.getOptions();
    final boolean verbose = opts.PRINT_DETAILED_INLINE_REPORT;
    if (!opts.INLINE) {
      return InlineDecision.NO("inlining not enabled");
    }

    final RVMMethod staticCallee = state.obtainTarget();
    final NormalMethod rootMethod = state.getRootMethod();
    final RVMMethod caller = state.getMethod();
    final int bcIndex = state.getRealBytecodeIndex();

    if (verbose) VM.sysWriteln("Begin inline decision for " + "<" + caller + "," + bcIndex + "," + staticCallee + ">");

    // Stage 1: We definitely don't inline certain methods
    if (!state.isInvokeInterface()) {
      if (staticCallee.isNative()) {
        if (verbose) VM.sysWriteln("\tNO: native method\n");
        return InlineDecision.NO("native method");
      }
      if (hasNoInlinePragma(staticCallee, state)) {
        if (verbose) VM.sysWriteln("\tNO: pragmaNoInline\n");
        return InlineDecision.NO("pragmaNoInline");
      }
      // We need throwable constructors to have their own compiled method IDs
      // to correctly elide stack frames when generating stack traces (see
      // StackTrace).
      if (// are we calling the throwable constructor?
          staticCallee.isObjectInitializer() &&
          (staticCallee.getDeclaringClass().getClassForType() == Throwable.class) &&
          // and not from a throwable constructor
          !(caller.isObjectInitializer() &&
           (caller.getDeclaringClass().getClassForType() == Throwable.class))) {
        if (verbose) VM.sysWriteln("\tNO: throwable constructor\n");
        return InlineDecision.NO("throwable constructor");
      }
    }
    // Stage 2: At all optimization levels we should attempt to inline
    //          trivial methods. Even if the inline code is never executed,
    //          inlining a trivial method is a no cost operation as the impact
    //          on code size should be negligible and compile time usually is
    //          reduced since we expect to eliminate the call instruction (or
    //          at worse replace one call instruction with another one).
    if (!state.isInvokeInterface() && !staticCallee.isAbstract()) {
      // NB when the destination is known we will have refined the target so the
      // above test passes
      if (state.getHasPreciseTarget() || !needsGuard(staticCallee)) {
        // call is guardless
        int inlinedSizeEstimate = inlinedSizeEstimate((NormalMethod) staticCallee, state);
        if (inlinedSizeEstimate < opts.INLINE_MAX_ALWAYS_INLINE_TARGET_SIZE) {
          // inlining is desirable
          if (!state.getSequence().containsMethod(staticCallee)) {
            // not recursive
            if (verbose) VM.sysWriteln("\tYES: trivial guardless inline\n");
            return InlineDecision.YES(staticCallee, "trivial inline");
          }
        }
        if (hasInlinePragma(staticCallee, state)) {
          // inlining is desirable
          if (!state.getSequence().containsMethod(staticCallee)) {
            // not recursive
            if (verbose) VM.sysWriteln("\tYES: pragma inline\n");
            return InlineDecision.YES(staticCallee, "pragma inline");
          }
        }
      }
    }

    if (opts.getOptLevel() == 0) {
      // at opt level 0, trivial unguarded inlines are the only kind we consider
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines at O0\n");
      return InlineDecision.NO("Only do trivial inlines at O0");
    }

    if (rootMethod.inlinedSizeEstimate() > opts.INLINE_MASSIVE_METHOD_SIZE) {
      // In massive methods, we do not do any additional inlining to
      // avoid completely blowing out compile time by making a bad situation worse
      if (verbose) VM.sysWriteln("\tNO: only do trivial inlines into massive methods\n");
      return InlineDecision.NO("Root method is massive; no non-trivial inlines");
    }

    // Stage 3: Determine based on profile data and static information
    //          what are the possible targets of this call.
    WeightedCallTargets targets = null;
    boolean purelyStatic = true;
    if (Controller.dcg != null && Controller.options.ADAPTIVE_INLINING) {
      targets = Controller.dcg.getCallTargets(caller, bcIndex);
      if (targets != null) {
        if (verbose) VM.sysWriteln("\tFound profile data");
        purelyStatic = false;
        WeightedCallTargets filteredTargets = targets.filter(staticCallee, state.getHasPreciseTarget());
        if (targets != filteredTargets) {
          if (verbose) VM.sysWriteln("\tProfiled callees filtered based on static information");
          targets = filteredTargets;
          if (targets == null) {
            if (verbose) VM.sysWriteln("\tAfter filterting no profile data...");
            // After filtering, no matching profile data, fall back to
            // static information to avoid degradations
            targets = WeightedCallTargets.create(staticCallee, 0);
            purelyStatic = true;
          }
        }
      }
    }

    // Critical section: must prevent class hierarchy from changing while
    // we are inspecting it to determine how/whether to do the inline guard.
    synchronized (RVMClass.classLoadListener) {

      boolean guardOverrideOnStaticCallee = false;
      if (targets == null) {
        if (verbose) VM.sysWriteln("\tNo profile data");
        // No profile information.
        // Fake up "profile data" based on static information to
        // be able to share all the decision making logic.
        if (state.isInvokeInterface()) {
          if (opts.INLINE_GUARDED_INTERFACES) {
            RVMMethod singleImpl = InterfaceHierarchy.getUniqueImplementation(staticCallee);
            if (singleImpl != null && hasBody(singleImpl)) {
              if (verbose) {
                VM.sysWriteln("\tFound a single implementation " +
                              singleImpl +
                              " of an interface method " +
                              staticCallee);
              }
              targets = WeightedCallTargets.create(singleImpl, 0);
              guardOverrideOnStaticCallee = true;
            }
          }
        } else {
          // invokestatic, invokevirtual, invokespecial
          if (staticCallee.isAbstract()) {
            // look for single non-abstract implementation of the abstract method
            RVMClass klass = staticCallee.getDeclaringClass();
            while (true) {
              RVMClass[] subClasses = klass.getSubClasses();
              if (subClasses.length != 1) break; // multiple subclasses => multiple targets
              RVMMethod singleImpl =
                  subClasses[0].findDeclaredMethod(staticCallee.getName(), staticCallee.getDescriptor());
              if (singleImpl != null && !singleImpl.isAbstract()) {
                // found something
                if (verbose) VM.sysWriteln("\tsingle impl of abstract method");
                targets = WeightedCallTargets.create(singleImpl, 0);
                guardOverrideOnStaticCallee = true;
                break;
              }
              klass = subClasses[0]; // keep crawling down the hierarchy
            }
          } else {
            targets = WeightedCallTargets.create(staticCallee, 0);
          }
        }
      }

      // At this point targets is either null, or accurately represents what we
      // think are the likely target(s) of the call site.
      // This information may be either derived from profile information or
      // from static heuristics. To the first approximation, we don't care which.
      // If there is a precise target, then targets contains exactly that target method.
      if (targets == null) return InlineDecision.NO("No potential targets identified");

      // Stage 4: We have one or more targets.  Determine what if anything should be done with them.
      final ArrayList<RVMMethod> methodsToInline = new ArrayList<RVMMethod>();
      final ArrayList<Boolean> methodsNeedGuard = new ArrayList<Boolean>();
      final double callSiteWeight = targets.totalWeight();
      final boolean goosc = guardOverrideOnStaticCallee; // real closures anyone?
      final boolean ps = purelyStatic;                   // real closures anyone?
      targets.visitTargets(new WeightedCallTargets.Visitor() {
        @Override
        public void visit(RVMMethod callee, double weight) {
          if (hasBody(callee)) {
            if (verbose) {
              VM.sysWriteln("\tEvaluating target " +
                            callee +
                            " with " +
                            weight +
                            " samples (" +
                            (100 * AdaptiveInlining.adjustedWeight(weight)) +
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
            boolean preEx = needsGuard && state.getIsExtant() && opts.INLINE_PREEX && currentlyFinal;
            if (needsGuard && !preEx) {
              if (!opts.INLINE_GUARDED) {
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
              CompiledMethod prev = state.getRootMethod().getCurrentCompiledMethod();
              if (prev != null && prev.getCompilerType() == CompiledMethod.OPT) {
                if (((OptCompiledMethod)prev).getMCMap().hasInlinedEdge(caller, bcIndex, callee)) {
                  if (verbose) VM.sysWriteln("\t\tSelect: Previously inlined");
                  decideYes = true;
                }
              }

              if (!decideYes) {
                int inlinedSizeEstimate = inlinedSizeEstimate((NormalMethod) callee, state);
                int cost = inliningActionCost(inlinedSizeEstimate, needsGuard, preEx, opts);
                int maxCost = opts.INLINE_MAX_TARGET_SIZE;

                if (callSiteWeight > Controller.options.INLINE_AI_SEED_MULTIPLIER) {
                  // real profile data with enough samples for us to trust it.
                  // Use weight and shape of call site distribution to compute
                  // a higher maxCost.
                  double fractionOfSample = weight / callSiteWeight;
                  if (needsGuard && fractionOfSample < opts.INLINE_AI_MIN_CALLSITE_FRACTION) {
                    // This call accounts for less than INLINE_AI_MIN_CALLSITE_FRACTION
                    // of the profiled targets at this call site.
                    // It is highly unlikely to be profitable to inline it.
                    if (verbose) VM.sysWriteln("\t\tReject: less than INLINE_AI_MIN_CALLSITE_FRACTION of distribution");
                    maxCost = 0;
                  } else {
                    if (cost > maxCost) {
                      /* We're going to increase the maximum callee size (maxCost) we're willing
                       * to inline based on how "hot" (what % of the total weight in the
                       * dynamic call graph) the edge is.
                       */
                      double adjustedWeight = AdaptiveInlining.adjustedWeight(weight);
                      if (adjustedWeight > Controller.options.INLINE_AI_HOT_CALLSITE_THRESHOLD) {
                        /* A truly hot edge; use the max allowable callee size */
                        maxCost = opts.INLINE_AI_MAX_TARGET_SIZE;
                      } else {
                        /* A warm edge, we will use a value between the static default and the max allowable.
                         * The code below simply does a linear interpolation between 2x static default
                         * and max allowable.
                         * Other alternatives would be to do a log interpolation or some other step function.
                         */
                        int range = opts.INLINE_AI_MAX_TARGET_SIZE -  2*opts.INLINE_MAX_TARGET_SIZE;
                        double slope = ((double) range) / Controller.options.INLINE_AI_HOT_CALLSITE_THRESHOLD;
                        int scaledAdj = (int) (slope * adjustedWeight);
                        maxCost += opts.INLINE_MAX_TARGET_SIZE + scaledAdj;
                      }
                    }
                  }
                }

                // Somewhat bogus, but if we get really deeply inlined we start backing off.
                int curDepth = state.getInlineDepth();
                if (curDepth > opts.INLINE_MAX_INLINE_DEPTH) {
                  maxCost /= (curDepth - opts.INLINE_MAX_INLINE_DEPTH + 1);
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
                ClassLoadingDependencyManager cldm = (ClassLoadingDependencyManager) RVMClass.classLoadListener;
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

      // Stage 5: Choose guards and package up the results in an InlineDecision object
      if (methodsToInline.isEmpty()) {
        InlineDecision d = InlineDecision.NO("No desirable targets");
        if (verbose) VM.sysWriteln("\tDecide: " + d);
        return d;
      } else if (methodsToInline.size() == 1) {
        RVMMethod target = methodsToInline.get(0);
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
            if (opts.OSR_GUARDED_INLINING && Controller.options.ENABLE_RECOMPILATION &&
                caller.isInterruptible() &&
                OptimizingCompiler.getAppStarted()) {
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
        RVMMethod[] methods = new RVMMethod[methodsNeedGuard.size()];
        byte[] guards = new byte[methods.length];
        int idx = 0;
        Iterator<RVMMethod> methodIterator = methodsToInline.iterator();
        Iterator<Boolean> guardIterator = methodsNeedGuard.iterator();
        while (methodIterator.hasNext()) {
          RVMMethod target = methodIterator.next();
          boolean needsGuard = guardIterator.next();
          if (VM.VerifyAssertions) {
            if (!needsGuard) {
              VM.sysWriteln("Error, inlining for " + methodsToInline.size() + " targets");
              VM.sysWriteln("Inlining into " + rootMethod + " at bytecode index " + bcIndex);
              VM.sysWriteln("Method: " + target + " doesn't need a guard");
              for (int i=0; i < methodsToInline.size(); i++) {
                VM.sysWriteln("  Method " + i + ": " + methodsToInline.get(i));
                VM.sysWriteln("  NeedsGuard: " + methodsNeedGuard.get(i));
              }
              VM._assert(VM.NOT_REACHED);
            }
          }
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
   * from caller to callee according to the controlling {@link OptOptions}.
   * If we are using IG_CODE_PATCH, then this method also records
   * the required dependency.
   * Precondition: lock on {@link RVMClass#classLoadListener} is held.
   *
   * @param caller The caller method
   * @param callee The callee method
   * @param codePatchSupported   Can we use code patching at this call site?
   */
  private byte chooseGuard(RVMMethod caller, RVMMethod singleImpl, RVMMethod callee, CompilationState state,
                           boolean codePatchSupported) {
    byte guard = state.getOptions().INLINE_GUARD_KIND;
    if (codePatchSupported) {
      if (VM.VerifyAssertions && VM.runningVM) {
        VM._assert(ObjectModel.holdsLock(RVMClass.classLoadListener, RVMThread.getCurrentThread()));
      }
      if (guard == OptOptions.INLINE_GUARD_CODE_PATCH) {
        ClassLoadingDependencyManager cldm = (ClassLoadingDependencyManager) RVMClass.classLoadListener;
        if (ClassLoadingDependencyManager.TRACE || ClassLoadingDependencyManager.DEBUG) {
          cldm.report("CODE PATCH: Inlined " + singleImpl + " into " + caller + "\n");
        }
        cldm.addNotOverriddenDependency(callee, state.getCompiledMethod());
      }
    } else if (guard == OptOptions.INLINE_GUARD_CODE_PATCH) {
      guard = OptOptions.INLINE_GUARD_METHOD_TEST;
    }

    if (guard == OptOptions.INLINE_GUARD_METHOD_TEST && singleImpl.getDeclaringClass().isFinal()) {
      // class test is more efficient and just as effective
      guard = OptOptions.INLINE_GUARD_CLASS_TEST;
    }
    return guard;
  }

  /**
   * Estimate the expected cost of the inlining action
   * (includes both the inline body and the guard/off-branch code).
   *
   * @param inlinedBodyEstimate size estimate for inlined body
   * @param needsGuard is it going to be a guarded inline?
   * @param preEx      can preEx inlining be used to avoid the guard?
   * @param opts       controlling options object
   * @return the estimated cost of the inlining action
   */
  private int inliningActionCost(int inlinedBodyEstimate, boolean needsGuard, boolean preEx, OptOptions opts) {
    int guardCost = 0;
    if (needsGuard & !preEx) {
      guardCost += NormalMethod.CALL_COST;
      if (opts.guardWithMethodTest()) {
        guardCost += 3 * NormalMethod.SIMPLE_OPERATION_COST;
      } else if (opts.guardWithCodePatch()) {
        guardCost += NormalMethod.SIMPLE_OPERATION_COST;
      } else { // opts.guardWithClassTest()
        guardCost += 2 * NormalMethod.SIMPLE_OPERATION_COST;
      }
    }
    return guardCost + inlinedBodyEstimate;
  }
}
