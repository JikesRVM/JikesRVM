/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_RuntimeCompiler;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;

/**
 * This class encapsulates the analytic model used by the controller
 * to guide multi-level recompilation decisions.  An early version of
 * this model is described in the OOPSLA'2000 paper, but we've made
 * some improvements since then...
 *
 * @see VM_MultiLevelAdaptiveModel
 *
 * @author Mike Hind
 * @author Dave Grove
 * @author Peter Sweeney
 * @author Stephen Fink
 * @author Matthew Arnold 
 */
abstract class VM_AnalyticModel extends VM_RecompilationStrategy {

  //---- Interface ------
  // Code that inherits from VM_AnalyticModel must define the
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
   *                     comile cmpMethod
   * @param cmpMethod The compiled method being considered
   */
  abstract VM_RecompilationChoice[] 
    getViableRecompilationChoices(int prevCompiler, 
                                  VM_CompiledMethod cmpMethod);


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
   * @param hme       the VM_HotMethodRecompilationEvent
   * @return the controller plan to be used or NULL, if no 
   *                   compilation is to be performed.  */
  VM_ControllerPlan considerHotMethod(VM_CompiledMethod cmpMethod,
                                      VM_HotMethodEvent hme) {
    // Compiler used for the previous compilation
    int prevCompiler = getPreviousCompiler(cmpMethod); 
    if (prevCompiler == -1) {
      return null; // Not a method that we can recompile (trap, JNI).
    }
    
    VM_ControllerPlan plan = VM_ControllerMemory.findMatchingPlan(cmpMethod);

    //-#if RVM_WITH_OSR
    // for a outdated hot method from baseline, we consider OSR, and execute plan
    // in the routine, no more action here
    if (considerOSRRecompilation(cmpMethod, hme, plan)) return null;
    //-#endif

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
    VM_RecompilationChoice bestActionChoice = null;
    double bestActionTime = futureTimeForMethod;
    double bestCost = 0.0;
    
    if (VM.LogAOSEvents) { 
      VM_AOSLogging.recordControllerEstimateCostDoNothing
        (cmpMethod.getMethod(),
         VM_CompilerDNA.getOptLevel(prevCompiler),
         bestActionTime);
    }
    
    // Get a vector of optimization choices to consider
    VM_RecompilationChoice[] recompilationChoices =
      getViableRecompilationChoices(prevCompiler,cmpMethod);
    
    // Consider all choices in the vector of possibilities
    VM_NormalMethod meth = (VM_NormalMethod)hme.getMethod();
    for (int i=0; i<recompilationChoices.length; i++) {
      VM_RecompilationChoice choice = recompilationChoices[i];

      // Get the cost and benefit of this choice
      double cost = choice.getCost(meth);
      double futureExecutionTime = 
        choice.getFutureExecutionTime(prevCompiler,futureTimeForMethod);
      
      double curActionTime = cost + futureExecutionTime;
      
      if (VM.LogAOSEvents) { 
        VM_AOSLogging.recordControllerEstimateCostOpt
          (cmpMethod.getMethod(),
           choice.toString(),
           cost,
           curActionTime);
      }
      
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
      plan = bestActionChoice.makeControllerPlan(cmpMethod, prevCompiler,
                                                 futureTimeForMethod,
                                                 bestActionTime,
                                                 bestCost);
    }
    return plan;
  }

  //-#if RVM_WITH_OSR
  /* check if a compiled method is outdated, then decide if it needs OSR from BASE to OPT
   */
  boolean considerOSRRecompilation(VM_CompiledMethod cmpMethod, 
                                   VM_HotMethodEvent hme,
                                   VM_ControllerPlan plan) {
    boolean outdatedBaseline = false;
    if (plan == null) {
      // if plan is null, this method was not compiled by AOS; it was
      // either in the boot image or compiled by the initial baseline
      // compiler.  In either case, if we've completed any recompilation
      // then the compiled method is outdated.
      outdatedBaseline = VM_ControllerMemory.planWithStatus(cmpMethod.getMethod(), 
                                                      VM_ControllerPlan.COMPLETED)
                        && cmpMethod.getCompilerType() == VM_CompiledMethod.BASELINE;
      if (VM.LogAOSEvents && outdatedBaseline)
        VM_AOSLogging.debug("outdated Baseline " +  cmpMethod.getMethod() + 
                            "(" + cmpMethod.getId() + ")");
    }

    // consider OSR option for old baseline-compiled activation
    if (outdatedBaseline) {
      if (!hme.getCompiledMethod().getSamplesReset()) {
        // the first time we see an outdated event, we clear the samples
        // associated with the cmid.
        hme.getCompiledMethod().setSamplesReset();
        VM_Controller.methodSamples.reset(hme.getCMID());
        if (VM.LogAOSEvents) VM_AOSLogging.debug(" Resetting method samples " + hme); 
        return true;
      } else {

        plan = chooseOSRRecompilation(hme);
        // insert the plan to memory, which sets up state in the system to trigger
        // the OSR promotion
        if (plan != null) {
                  VM_ControllerMemory.insert(plan);
                  // to work with VM_Thread, it flags the compiled method 
                  // that it is outdated
                  if (VM.VerifyAssertions) {
                        VM._assert(cmpMethod.getCompilerType() == VM_CompiledMethod.BASELINE);
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
  private VM_ControllerPlan chooseOSRRecompilation(VM_HotMethodEvent hme) { 
    if (!VM_Controller.options.OSR_PROMOTION) return null;

    if (VM.LogAOSEvents) VM_AOSLogging.debug(" Consider OSR" + hme); 

    VM_ControllerPlan prev = VM_ControllerMemory.findLatestPlan(hme.getMethod());
    double millis = (double)(prev.getTimeCompleted() - prev.getTimeInitiated());
    double speedup = prev.getExpectedSpeedup();
    double futureTimeForMethod = futureTimeForMethod(hme);

    double futureTimeOptimized = futureTimeForMethod / speedup;

    if (VM.LogAOSEvents) VM_AOSLogging.debug(" Estimated future time for method " + hme + " is " +
                                             futureTimeForMethod); 
    if (VM.LogAOSEvents) VM_AOSLogging.debug(" Estimated future time optimized " + hme + " is " +
                                             (futureTimeOptimized+millis)); 

    if (futureTimeForMethod > futureTimeOptimized + millis) {
      VM_AOSLogging.recordOSRRecompilationDecision(prev);
      VM_ControllerPlan p = new VM_ControllerPlan(prev.getCompPlan(), 
                                                  prev.getTimeCreated(),
                                                  hme.getCMID(),
                                                  prev.getExpectedSpeedup(),
                                                  millis,
                                                  prev.getPriority());
          // set up state to trigger osr
          p.setStatus(VM_ControllerPlan.OSR_BASE_2_OPT);
      return p;
    } else {
      return null;
    }
  }
  //-#endif

  /**
   * This function defines how the analytic model handles a
   * VM_AINewHotEdgeEvent.  The basic idea is to use the model to
   * evaluate whether it would be better to do nothing or to recompile
   * at the same opt level, assuming there would be some "boost" after
   * performing inlining.  
   */
  void considerHotCallEdge(VM_CompiledMethod cmpMethod, 
                           VM_AINewHotEdgeEvent event) {

    // Compiler used for the previous compilation
    int prevCompiler = getPreviousCompiler(cmpMethod); 
    if (prevCompiler == -1) {
      return; // Not a method we can recompile (trap, JNI).
    }

    VM_ControllerPlan plan = VM_ControllerMemory.findMatchingPlan(cmpMethod);
    if (!considerForRecompilation(event, plan)) return;
    double prevCompileTime = cmpMethod.getCompilationTime();

    // Use the model to caclulate expected cost of (1) doing nothing
    // and (2) recompiling at the same opt level with the FDO boost
    double futureTimeForMethod = futureTimeForMethod(event);
    double futureTimeForFDOMethod = 
      prevCompileTime + (futureTimeForMethod/event.getBoostFactor());
    
    if (VM.LogAOSEvents) { 
      int prevOptLevel = VM_CompilerDNA.getOptLevel(prevCompiler);
      VM_AOSLogging.recordControllerEstimateCostDoNothing(cmpMethod.getMethod(),
                                                          prevOptLevel,
                                                          futureTimeForMethod);
      VM_AOSLogging.recordControllerEstimateCostOpt(cmpMethod.getMethod(),
                                                    "O"+prevOptLevel+"AI",
                                                    prevCompileTime,
                                                    futureTimeForFDOMethod);
    }

    if (futureTimeForFDOMethod < futureTimeForMethod) {
      // Profitable to recompile with FDO, so do it.
      int optLevel = VM_CompilerDNA.getOptLevel(prevCompiler);
      double priority = futureTimeForMethod - futureTimeForFDOMethod;
      plan = createControllerPlan(cmpMethod.getMethod(), 
                                  optLevel, null, 
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
   * @param hme The VM_HotMethodEvent in question
   * @return estimate of future execution time to be spent in this method
   */
  double futureTimeForMethod(VM_HotMethodEvent hme) {
    VM_AOSOptions opts = VM_Controller.options;
    double numSamples = hme.getNumSamples();
    double timePerSample = (double)VM.interruptQuantum;
    if (!VM.UseEpilogueYieldPoints) {
      // NOTE: we take two samples per timer interrupt, so we have to
      // adjust here (otherwise we'd give the method twice as much time
      // as it actually deserves).
      timePerSample /=  2.0;
    }
    double timeInMethodSoFar = numSamples * timePerSample;
    return timeInMethodSoFar;
  }
}
