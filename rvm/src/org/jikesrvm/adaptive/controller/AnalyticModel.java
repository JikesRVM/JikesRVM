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
package org.jikesrvm.adaptive.controller;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.recompilation.CompilerDNA;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.compilers.common.CompiledMethod;

/**
 * This class encapsulates the analytic model used by the controller
 * to guide multi-level recompilation decisions.  An early version of
 * this model is described in the OOPSLA'2000 paper, but we've made
 * some improvements since then...
 *
 * @see MultiLevelAdaptiveModel
 */
abstract class AnalyticModel extends RecompilationStrategy {

  //---- Interface ------
  // Code that inherits from AnalyticModel must define the
  // following behavior

  /**
   * Initialize the set of "optimization choices" that the
   * cost-benefit model will consider when using will consider when
   * using adaptive compilation.
   */
  abstract void populateRecompilationChoices();

  /**
   * Compute the set of optimization choices that should be
   * considered by the cost-benefit model, given the previous compiler.
   *
   * @param prevCompiler The compiler compiler that was used to
   *                     compile cmpMethod
   * @param cmpMethod The compiled method being considered
   */
  abstract RecompilationChoice[] getViableRecompilationChoices(int prevCompiler, CompiledMethod cmpMethod);

  // -----------------------------------------------------
  // Below code that is (currently) common to all recompilation
  // strategies that use the analytic model.

  /**
   * Initialize the analytic model:
   *
   *  NOTE: The call to super.init() uses the command line options to
   *  set up the optimization plans, so this must be run after the
   *  command line options are available.
   */
  @Override
  void init() {
    // Do the common initialization first
    super.init();

    // setup the recompilation choices that are available to the
    // analytic model
    populateRecompilationChoices();
  }

  /**
   * This method is the main decision making loop for all
   * recompilation strategies that use the analytic model.
   * <p>
   * Given a HotMethodRecompilationEvent, this code will determine
   * IF the method should be recompiled, and if so, HOW to perform
   * the recompilation, i.e., what compilation plan should be used.
   * The method returns a controller plan, which contains the compilation
   * plan and other goodies.
   *
   * @param cmpMethod the compiled method of interest
   * @param hme       the HotMethodRecompilationEvent
   * @return the controller plan to be used or NULL, if no
   *                   compilation is to be performed.  */
  @Override
  ControllerPlan considerHotMethod(CompiledMethod cmpMethod, HotMethodEvent hme) {
    // Compiler used for the previous compilation
    int prevCompiler = getPreviousCompiler(cmpMethod);
    if (prevCompiler == -1) {
      return null; // Not a method that we can recompile (trap, JNI).
    }

    ControllerPlan plan = ControllerMemory.findMatchingPlan(cmpMethod);

    // for a outdated hot method from baseline, we consider OSR,
    // and execute plan in the routine, no more action here
    if (considerOSRRecompilation(cmpMethod, hme, plan)) return null;

    if (!considerForRecompilation(hme, plan)) return null;

    // Now we know the compiler that generated the method (prevCompiler) and
    // that the method is a potential candidate for additional recompilation.
    // So, next decide what, if anything, should be done now.
    // We consider doing nothing (ie leaving the method at the current
    // opt level, which incurs no  compilation cost), and recompiling the
    // method at each greater compilation level.
    double futureTimeForMethod = futureTimeForMethod(hme);

    // initialize bestAction as doing nothing, which means we'll
    // spend just as much time in the method in the future as we have so far.
    RecompilationChoice bestActionChoice = null;
    double bestActionTime = futureTimeForMethod;
    double bestCost = 0.0;

    AOSLogging.logger.recordControllerEstimateCostDoNothing(cmpMethod.getMethod(),
                                                        CompilerDNA.getOptLevel(prevCompiler),
                                                        bestActionTime);

    // Get a vector of optimization choices to consider
    RecompilationChoice[] recompilationChoices = getViableRecompilationChoices(prevCompiler, cmpMethod);

    // Consider all choices in the vector of possibilities
    NormalMethod meth = (NormalMethod) hme.getMethod();
    for (RecompilationChoice choice : recompilationChoices) {
      // Get the cost and benefit of this choice
      double cost = choice.getCost(meth);
      double futureExecutionTime = choice.getFutureExecutionTime(prevCompiler, futureTimeForMethod);

      double curActionTime = cost + futureExecutionTime;

      AOSLogging.logger.recordControllerEstimateCostOpt(cmpMethod.getMethod(), choice.toString(), cost, curActionTime);

      if (curActionTime < bestActionTime) {
        bestActionTime = curActionTime;
        bestActionChoice = choice;
        bestCost = cost;
      }
    }

    // if the best action is the previous than we don't need to recompile
    if (bestActionChoice == null) {
      plan = null;
    } else {
      plan =
          bestActionChoice.makeControllerPlan(cmpMethod, prevCompiler, futureTimeForMethod, bestActionTime, bestCost);
    }
    return plan;
  }

  /* check if a compiled method is outdated, then decide if it needs OSR from BASE to OPT
   */
  boolean considerOSRRecompilation(CompiledMethod cmpMethod, HotMethodEvent hme, ControllerPlan plan) {
    boolean outdatedBaseline = false;
    if (plan == null) {
      // if plan is null, this method was not compiled by AOS; it was
      // either in the boot image or compiled by the initial baseline
      // compiler.  In either case, if we've completed any recompilation
      // then the compiled method is outdated.
      outdatedBaseline =
          ControllerMemory.planWithStatus(cmpMethod.getMethod(), ControllerPlan.COMPLETED) &&
          cmpMethod.getCompilerType() == CompiledMethod.BASELINE;
      if (outdatedBaseline) {
        AOSLogging.logger.debug("outdated Baseline " + cmpMethod.getMethod() + "(" + cmpMethod.getId() + ")");
      }
    }

    // consider OSR option for old baseline-compiled activation
    if (outdatedBaseline) {
      if (!hme.getCompiledMethod().getSamplesReset()) {
        // the first time we see an outdated event, we clear the samples
        // associated with the cmid.
        hme.getCompiledMethod().setSamplesReset();
        Controller.methodSamples.reset(hme.getCMID());
        AOSLogging.logger.debug(" Resetting method samples " + hme);
        return true;
      } else {
        plan = chooseOSRRecompilation(hme);
        // insert the plan to memory, which sets up state in the system to trigger
        // the OSR promotion
        if (plan != null) {
          ControllerMemory.insert(plan);
          // to coordinate with OSRListener, it marks cmpMethod as outdated
          if (VM.VerifyAssertions) {
            VM._assert(cmpMethod.getCompilerType() == CompiledMethod.BASELINE);
          }
          cmpMethod.setOutdated();
        }
        // we don't do any more action on the controller side.
        return true;
      }
    }
    return false;
  }

  /**
   * @param hme sample data for an outdated cmid
   * @return a plan representing recompilation with OSR, null if OSR not
   * justified.
   */
  private ControllerPlan chooseOSRRecompilation(HotMethodEvent hme) {
    if (!Controller.options.OSR_PROMOTION) return null;

    AOSLogging.logger.debug(" Consider OSR for " + hme);

    ControllerPlan prev = ControllerMemory.findLatestPlan(hme.getMethod());

    if (prev.getStatus() == ControllerPlan.OSR_BASE_2_OPT) {
      AOSLogging.logger.debug(" Already have an OSR promotion plan for this method");
      return null;
    }

    double millis = prev.getTimeCompleted() - prev.getTimeInitiated();
    double speedup = prev.getExpectedSpeedup();
    double futureTimeForMethod = futureTimeForMethod(hme);

    double futureTimeOptimized = futureTimeForMethod / speedup;

    AOSLogging.logger.debug(" Estimated future time for method " + hme + " is " + futureTimeForMethod);
    AOSLogging.logger.debug(" Estimated future time optimized " + hme + " is " + (futureTimeOptimized + millis));

    if (futureTimeForMethod > futureTimeOptimized + millis) {
      AOSLogging.logger.recordOSRRecompilationDecision(prev);
      ControllerPlan p =
          new ControllerPlan(prev.getCompPlan(),
                                prev.getTimeCreated(),
                                hme.getCMID(),
                                prev.getExpectedSpeedup(),
                                millis,
                                prev.getPriority());
      // set up state to trigger osr
      p.setStatus(ControllerPlan.OSR_BASE_2_OPT);
      return p;
    } else {
      return null;
    }
  }

  /**
   * This function defines how the analytic model handles a
   * AINewHotEdgeEvent.  The basic idea is to use the model to
   * evaluate whether it would be better to do nothing or to recompile
   * at the same opt level, assuming there would be some "boost" after
   * performing inlining.
   */
  @Override
  void considerHotCallEdge(CompiledMethod cmpMethod, AINewHotEdgeEvent event) {

    // Compiler used for the previous compilation
    int prevCompiler = getPreviousCompiler(cmpMethod);
    if (prevCompiler == -1) {
      return; // Not a method we can recompile (trap, JNI).
    }

    ControllerPlan plan = ControllerMemory.findMatchingPlan(cmpMethod);
    if (!considerForRecompilation(event, plan)) return;
    double prevCompileTime = cmpMethod.getCompilationTime();

    // Use the model to calculate expected cost of (1) doing nothing
    // and (2) recompiling at the same opt level with the FDO boost
    double futureTimeForMethod = futureTimeForMethod(event);
    double futureTimeForFDOMethod = prevCompileTime + (futureTimeForMethod / event.getBoostFactor());

    int prevOptLevel = CompilerDNA.getOptLevel(prevCompiler);
    AOSLogging.logger.recordControllerEstimateCostDoNothing(cmpMethod.getMethod(), prevOptLevel, futureTimeForMethod);
    AOSLogging.logger.recordControllerEstimateCostOpt(cmpMethod.getMethod(),
                                                  "O" + prevOptLevel + "AI",
                                                  prevCompileTime,
                                                  futureTimeForFDOMethod);

    if (futureTimeForFDOMethod < futureTimeForMethod) {
      // Profitable to recompile with FDO, so do it.
      int optLevel = CompilerDNA.getOptLevel(prevCompiler);
      double priority = futureTimeForMethod - futureTimeForFDOMethod;
      plan =
          createControllerPlan(cmpMethod.getMethod(),
                               optLevel,
                               null,
                               cmpMethod.getId(),
                               event.getBoostFactor(),
                               futureTimeForFDOMethod,
                               priority);
      plan.execute();
    }
  }

  /**
   * How much time do we expect to spend in the method in the future if
   * we take no recompilation action?
   * The key assumption is that we'll spend just as much time
   * executing in the the method in the future as we have done so far
   * in the past.
   *
   * @param hme The HotMethodEvent in question
   * @return estimate of future execution time to be spent in this method
   */
  double futureTimeForMethod(HotMethodEvent hme) {
    double numSamples = hme.getNumSamples();
    double timePerSample = VM.interruptQuantum;
    if (!VM.UseEpilogueYieldPoints) {
      // NOTE: we take two samples per timer interrupt, so we have to
      // adjust here (otherwise we'd give the method twice as much time
      // as it actually deserves).
      timePerSample /= 2.0;
    }
    if (Controller.options.mlCBS()) {
      // multiple method samples per timer interrupt. Divide accordingly.
      timePerSample /= VM.CBSMethodSamplesPerTick;
    }
    double timeInMethodSoFar = numSamples * timePerSample;
    return timeInMethodSoFar;
  }
}
