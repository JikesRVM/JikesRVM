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
package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.Callbacks;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerMemory;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.recompilation.InvocationCounts;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.adaptive.util.CompilerAdviceAttribute;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.RuntimeCompiler;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;

/**
 * Maintain statistic information about on stack replacement events
 */
public class OSRProfiler implements Callbacks.ExitMonitor {

  private static int invalidations = 0;
  private static boolean registered = false;

  @Override
  public void notifyExit(int value) {
    VM.sysWriteln("OSR invalidations " + invalidations);
  }

  // we know which assumption is invalidated
  // current we only reset the root caller method to be recompiled.
  public static void notifyInvalidation(ExecutionState state) {

    if (!registered && VM.MeasureCompilation) {
      registered = true;
      Callbacks.addExitMonitor(new OSRProfiler());
    }

    if (VM.TraceOnStackReplacement || VM.MeasureCompilation) {
      OSRProfiler.invalidations++;
    }

    // find the root state
    while (state.callerState != null) {
      state = state.callerState;
    }

    // only invalidate the root state
    invalidateState(state);
  }

  // invalidate an execution state
  private static synchronized void invalidateState(ExecutionState state) {
    // step 1: invalidate the compiled method with this OSR assumption
    //         how does this affect the performance?
    CompiledMethod mostRecentlyCompiledMethod = CompiledMethods.getCompiledMethod(state.cmid);

    if (VM.VerifyAssertions) {
      VM._assert(mostRecentlyCompiledMethod.getMethod() == state.meth);
    }

    // check if the compiled method is the latest still the latest one
    // this is necessary to check because the same compiled method may
    // be invalidated in more than one thread at the same time
    if (mostRecentlyCompiledMethod != state.meth.getCurrentCompiledMethod()) {
      return;
    }

    // make sure the compiled method is an opt one
    if (!(mostRecentlyCompiledMethod instanceof OptCompiledMethod)) {
      return;
    }

    // reset the compiled method to null first, if other thread invokes
    // this method before following opt recompilation, it can avoid OSR
    state.meth.invalidateCompiledMethod(mostRecentlyCompiledMethod);

    // a list of state from callee -> caller
    if (VM.TraceOnStackReplacement) {
      VM.sysWriteln("OSR " + OSRProfiler.invalidations + " : " + state.bcIndex + "@" + state.meth);
    }

    // simply reset the compiled method to null is not good
    // for long run loops, because invalidate may cause
    // the method falls back to the baseline again...
    // NOW, we look for the previous compilation plan, and reuse
    // the compilation plan.
    boolean recmplsucc = false;
    if (Controller.enabled) {
      CompilationPlan cmplplan = null;
      if (Controller.options.ENABLE_PRECOMPILE && CompilerAdviceAttribute.hasAdvice()) {
        CompilerAdviceAttribute attr = CompilerAdviceAttribute.getCompilerAdviceInfo(state.meth);
        if (VM.VerifyAssertions) {
          VM._assert(attr.getCompiler() == CompiledMethod.OPT);
        }
        if (Controller.options.counters()) {
          // for invocation counter, we only use one optimization level
          cmplplan = InvocationCounts.createCompilationPlan(state.meth);
        } else {
          // for now there is not two options for sampling, so
          // we don't have to use: if (Controller.options.sampling())
          cmplplan = Controller.recompilationStrategy.createCompilationPlan(state.meth, attr.getOptLevel(), null);
        }
      } else {
        ControllerPlan ctrlplan = ControllerMemory.findMatchingPlan(mostRecentlyCompiledMethod);
        if (ctrlplan != null) {
          cmplplan = ctrlplan.getCompPlan();
        }
      }
      if (cmplplan != null) {
        if (VM.VerifyAssertions) {VM._assert(cmplplan.getMethod() == state.meth);}

        // for invalidated method, we donot perform OSR guarded inlining anymore.
        // the Options object may be shared by several methods,
        // we have to reset it back
        boolean savedOsr = cmplplan.options.OSR_GUARDED_INLINING;
        cmplplan.options.OSR_GUARDED_INLINING = false;
        int newcmid = RuntimeCompiler.recompileWithOpt(cmplplan);
        cmplplan.options.OSR_GUARDED_INLINING = savedOsr;

        if (newcmid != -1) {
          AOSLogging.logger.debug("recompiling state with opt succeeded " + state.cmid);
          AOSLogging.logger.debug("new cmid " + newcmid);

          // transfer hotness to the new cmid
          double oldSamples = Controller.methodSamples.getData(state.cmid);
          Controller.methodSamples.reset(state.cmid);
          Controller.methodSamples.augmentData(newcmid, oldSamples);

          recmplsucc = true;
          if (VM.TraceOnStackReplacement) {
            VM.sysWriteln("  recompile " + state.meth + " at -O" + cmplplan.options.getOptLevel());
          }
        }
      }
    }

    if (!recmplsucc) {
      int newcmid = RuntimeCompiler.recompileWithOpt(state.meth);
      if (newcmid == -1) {
        if (VM.TraceOnStackReplacement) {VM.sysWriteln("  opt recompilation failed!");}
        state.meth.invalidateCompiledMethod(mostRecentlyCompiledMethod);
      }
    }

    if (VM.TraceOnStackReplacement) {VM.sysWriteln("  opt recompilation done!");}
  }
}
