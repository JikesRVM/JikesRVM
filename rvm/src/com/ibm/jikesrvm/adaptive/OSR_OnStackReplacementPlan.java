/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.ArchitectureSpecific.OSR_CodeInstaller;
import com.ibm.jikesrvm.ArchitectureSpecific.OSR_BaselineExecStateExtractor;
import com.ibm.jikesrvm.ArchitectureSpecific.OSR_OptExecStateExtractor;
import com.ibm.jikesrvm.opt.*;
import com.ibm.jikesrvm.osr.*;
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
 *
 * @author Feng Qian
 */
public class OSR_OnStackReplacementPlan implements VM_Constants {
  private int CMID;
  private Offset tsFromFPoff;
  private Offset ypTakenFPoff;

  /* Status is write-only at the moment, but I suspect it comes in
   * handy for debugging -- RJG
   */
  @SuppressWarnings("unused")
  private byte status;
  
  private VM_Thread suspendedThread;
  private OPT_CompilationPlan compPlan;

  private int timeInitiated = -1;
  private int timeCompleted = -1;

  public OSR_OnStackReplacementPlan(VM_Thread thread,
                                    OPT_CompilationPlan cp,
                                    int cmid,
                                    int source,
                                    Offset tsoff,
                                    Offset ypoff,
                                    double priority) {
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

  /* override the normal method. */
  public void execute() {
    // 1. extract stack state
    // 2. recompile the specialized method
    // 3. install the code
    // 4. reschedule the thread to new code.

    if (VM.LogAOSEvents)  {
      VM_AOSLogging.logOsrEvent("OSR compiling "+compPlan.method);
    }

    VM_Thread cpThread = VM_Thread.getCurrentThread();

    setTimeInitiated(VM_Controller.controllerClock);
    
    {
      // we will reuse the compilation plan before
      cpThread.accumulateCycles();
      
      OSR_ExecStateExtractor extractor = null;

      VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(this.CMID);

      boolean invalidate = true;
      if (cm.getCompilerType() == VM_CompiledMethod.BASELINE) {
        extractor = new OSR_BaselineExecStateExtractor();
        // don't need to invalidate when transitioning from baseline 
        invalidate = false;
      } else
      if (cm.getCompilerType() == VM_CompiledMethod.OPT) {
        extractor = new OSR_OptExecStateExtractor();
      } else {
        if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
        return;
      }


      ////////
      // states is a list of state: callee -> caller -> caller
      OSR_ExecutionState state = extractor.extractState(suspendedThread, 
                                                        this.tsFromFPoff,
                                                        this.ypTakenFPoff,
                                                        CMID);

      if (invalidate) {
        VM_AOSLogging.debug("Invalidate cmid " + CMID);
        OSR_Profiler.notifyInvalidation(state);
      }

      // compile from callee to caller 
      VM_CompiledMethod newCM = OSR_SpecialCompiler.recompileState(state,invalidate);
      
      cpThread.accumulateCycles();
     
      setTimeCompleted(VM_Controller.controllerClock);

      if (newCM == null) {
        setStatus(VM_ControllerPlan.ABORTED_COMPILATION_ERROR);
        if (VM.LogAOSEvents) 
          VM_AOSLogging.logOsrEvent("OSR compilation failed!");
      } else {
        setStatus(VM_ControllerPlan.COMPLETED);
        // now let OSR_CodeInstaller generate a code stub, 
        // and OSR_PostThreadSwitch will install the stub to run.      
        OSR_CodeInstaller.install(state, newCM);
        if (VM.LogAOSEvents) 
          VM_AOSLogging.logOsrEvent("OSR compilation succeded! " + compPlan.method);
      }
    }

    suspendedThread.resume();
    suspendedThread = null;
    compPlan = null;
    CMID = 0;
  }
}
