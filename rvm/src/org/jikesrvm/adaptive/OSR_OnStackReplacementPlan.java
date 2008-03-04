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
import org.jikesrvm.VM_Constants;
import org.jikesrvm.ArchitectureSpecific.OSR_BaselineExecStateExtractor;
import org.jikesrvm.ArchitectureSpecific.OSR_CodeInstaller;
import org.jikesrvm.ArchitectureSpecific.OSR_OptExecStateExtractor;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.controller.VM_ControllerPlan;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.compilers.opt.driver.CompilationPlan;
import org.jikesrvm.osr.OSR_ExecStateExtractor;
import org.jikesrvm.osr.OSR_ExecutionState;
import org.jikesrvm.osr.OSR_Profiler;
import org.jikesrvm.osr.OSR_SpecialCompiler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.unboxed.Offset;

/**
 * A OSR_ControllerOnStackReplacementPlan is scheduled by VM_ControllerThread,
 * and executed by the VM_RecompilationThread.
 *
 * It has the suspended thread whose activation being replaced,
 * and a compilation plan.
 *
 * The execution of this plan compiles the method, installs the new
 * code, and reschedule the thread.
 */
public class OSR_OnStackReplacementPlan implements VM_Constants {
  private final int CMID;
  private final Offset tsFromFPoff;
  private final Offset ypTakenFPoff;

  /* Status is write-only at the moment, but I suspect it comes in
   * handy for debugging -- RJG
   */
  @SuppressWarnings("unused")
  private byte status;

  private final VM_Thread suspendedThread;
  private final CompilationPlan compPlan;

  private int timeInitiated = 0;
  private int timeCompleted = 0;

  public OSR_OnStackReplacementPlan(VM_Thread thread, CompilationPlan cp, int cmid, int source, Offset tsoff,
                                    Offset ypoff, double priority) {
    this.suspendedThread = thread;
    this.compPlan = cp;
    this.CMID = cmid;
    this.tsFromFPoff = tsoff;
    this.ypTakenFPoff = ypoff;
    this.status = VM_ControllerPlan.UNINITIALIZED;
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

    VM_AOSLogging.logOsrEvent("OSR compiling " + compPlan.method);

    setTimeInitiated(VM_Controller.controllerClock);

    {
      OSR_ExecStateExtractor extractor = null;

      VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.CMID);

      boolean invalidate = true;
      if (cm.getCompilerType() == VM_CompiledMethod.BASELINE) {
        extractor = new OSR_BaselineExecStateExtractor();
        // don't need to invalidate when transitioning from baseline
        invalidate = false;
      } else if (cm.getCompilerType() == VM_CompiledMethod.OPT) {
        extractor = new OSR_OptExecStateExtractor();
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        return;
      }

      ////////
      // states is a list of state: callee -> caller -> caller
      OSR_ExecutionState state = extractor.extractState(suspendedThread, this.tsFromFPoff, this.ypTakenFPoff, CMID);

      if (invalidate) {
        VM_AOSLogging.debug("Invalidate cmid " + CMID);
        OSR_Profiler.notifyInvalidation(state);
      }

      // compile from callee to caller
      VM_CompiledMethod newCM = OSR_SpecialCompiler.recompileState(state, invalidate);

      setTimeCompleted(VM_Controller.controllerClock);

      if (newCM == null) {
        setStatus(VM_ControllerPlan.ABORTED_COMPILATION_ERROR);
        VM_AOSLogging.logOsrEvent("OSR compilation failed!");
      } else {
        setStatus(VM_ControllerPlan.COMPLETED);
        // now let OSR_CodeInstaller generate a code stub,
        // and OSR_PostThreadSwitch will install the stub to run.
        OSR_CodeInstaller.install(state, newCM);
        VM_AOSLogging.logOsrEvent("OSR compilation succeeded! " + compPlan.method);
      }
    }

    suspendedThread.osrResume();
  }
}
