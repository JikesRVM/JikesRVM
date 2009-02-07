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
package org.jikesrvm.adaptive;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.ArchitectureSpecificOpt.BaselineExecutionStateExtractor;
import org.jikesrvm.ArchitectureSpecificOpt.CodeInstaller;
import org.jikesrvm.ArchitectureSpecificOpt.OptExecutionStateExtractor;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.osr.ExecutionStateExtractor;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.osr.OSRProfiler;
import org.jikesrvm.osr.SpecialCompiler;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Offset;

/**
 * A OSR_ControllerOnStackReplacementPlan is scheduled by ControllerThread,
 * and executed by the RecompilationThread.
 *
 * It has the suspended thread whose activation being replaced,
 * and a compilation plan.
 *
 * The execution of this plan compiles the method, installs the new
 * code, and reschedule the thread.
 */
public class OnStackReplacementPlan implements Constants {
  private final int CMID;
  private final Offset tsFromFPoff;
  private final Offset ypTakenFPoff;

  /* Status is write-only at the moment, but I suspect it comes in
   * handy for debugging -- RJG
   */
  @SuppressWarnings("unused")
  private byte status;

  private final RVMThread suspendedThread;
  private final CompilationPlan compPlan;

  private int timeInitiated = 0;
  private int timeCompleted = 0;

  public OnStackReplacementPlan(RVMThread thread, CompilationPlan cp, int cmid, int source, Offset tsoff,
                                Offset ypoff, double priority) {
    this.suspendedThread = thread;
    this.compPlan = cp;
    this.CMID = cmid;
    this.tsFromFPoff = tsoff;
    this.ypTakenFPoff = ypoff;
    this.status = ControllerPlan.UNINITIALIZED;
  }

  public int getTimeInitiated() { return timeInitiated; }

  public void setTimeInitiated(int t) { timeInitiated = t; }

  public int getTimeCompleted() { return timeCompleted; }

  public void setTimeCompleted(int t) { timeCompleted = t; }

  public void setStatus(byte newStatus) {
    status = newStatus;
  }

  public void execute() {
    // 1. extract stack state
    // 2. recompile the specialized method
    // 3. install the code
    // 4. reschedule the thread to new code.

    AOSLogging.logger.logOsrEvent("OSR compiling " + compPlan.method);

    setTimeInitiated(Controller.controllerClock);

    {
      ExecutionStateExtractor extractor = null;

      CompiledMethod cm = CompiledMethods.getCompiledMethod(this.CMID);

      boolean invalidate = true;
      if (cm.getCompilerType() == CompiledMethod.BASELINE) {
        extractor = new BaselineExecutionStateExtractor();
        // don't need to invalidate when transitioning from baseline
        invalidate = false;
      } else if (cm.getCompilerType() == CompiledMethod.OPT) {
        extractor = new OptExecutionStateExtractor();
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        return;
      }

      ////////
      // states is a list of state: callee -> caller -> caller
      ExecutionState state = extractor.extractState(suspendedThread, this.tsFromFPoff, this.ypTakenFPoff, CMID);

      if (invalidate) {
        AOSLogging.logger.debug("Invalidate cmid " + CMID);
        OSRProfiler.notifyInvalidation(state);
      }

      // compile from callee to caller
      CompiledMethod newCM = SpecialCompiler.recompileState(state, invalidate);

      setTimeCompleted(Controller.controllerClock);

      if (newCM == null) {
        setStatus(ControllerPlan.ABORTED_COMPILATION_ERROR);
        AOSLogging.logger.logOsrEvent("OSR compilation failed!");
      } else {
        setStatus(ControllerPlan.COMPLETED);
        // now let CodeInstaller generate a code stub,
        // and PostThreadSwitch will install the stub to run.
        CodeInstaller.install(state, newCM);
        AOSLogging.logger.logOsrEvent("OSR compilation succeeded! " + compPlan.method);
      }
    }

    suspendedThread.monitor().lock();
    suspendedThread.osr_done=true;
    suspendedThread.monitor().broadcast();
    suspendedThread.monitor().unlock();
  }
}
