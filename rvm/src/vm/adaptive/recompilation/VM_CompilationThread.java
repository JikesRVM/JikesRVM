/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *  This class is a separate thread whose job is to monitor a (priority)
 *  queue of compilation plans.  Whenever the queue is nonempty, this
 *  thread will pick the highest priority compilation plan from the queue
 *  and invoke the OPT compiler to perform the plan.
 *
 *  No intelligence is contained in this class.  All policy decisions are
 *  made by the controllerThread.
 *
 *  @author Michael Hind
 *  @author David Grove
 */
class VM_CompilationThread extends VM_Thread {

  /**
   * This is the main loop of the compilation thread. It's job is to 
   * remove controller plans from the compilation queue and perform
   * them.
   */
  public void run() {
    if (VM.LogAOSEvents) VM_AOSLogging.compilationThreadStarted();

    // Make a blocking call to deleteMin to get a plan and then execute it. 
    // Repeat...
    while (true) {
      VM_ControllerPlan plan = 
	(VM_ControllerPlan)VM_Controller.compilationQueue.deleteMin();
      recompile(plan);
    }
  }

  /**
   * This method will recompile the method designated by the passed 
   * controller plan.  It also 
   *  1) credits the samples associated with the old compiled method
   *     ID to the new method ID and clears the old value.
   *  2) clears inlining information
   *  3) updates the status of the controller plan
   * @param plan the controller plan to use for the recompilation
   */
  private void recompile(VM_ControllerPlan plan) {
    OPT_CompilationPlan cp = plan.getCompPlan();

    plan.setTimeInitiated(VM_Controller.controllerClock);
    if (VM.LogAOSEvents) VM_AOSLogging.recompilationStarted(cp); 

    double compileTime; // compile time in milliseconds 
    int newCMID;
    synchronized (VM_ClassLoader.lock) { 
      // must hold classloader lock while compiling.
      // Update compilation thread timing information to prepare for new run.
      double now = VM_Time.now();
      cpuTotalTime += (now - cpuStartTime);
      cpuStartTime = now;
      double start = cpuTotalTime;

      // Compile the method.
      newCMID = VM_RuntimeOptCompilerInfrastructure.recompileWithOpt(cp);

      // Update compilation thread timing information and compute time 
      // taken during this compilation.
      now = VM_Time.now();
      cpuTotalTime += (now - cpuStartTime);
      cpuStartTime = now;
      double end = cpuTotalTime;
      compileTime = (end - start) * 1000.0; // Convert seconds to milliseconds.
    }
      
    // transfer the samples from the old CMID to the new CMID.
    // scale the number of samples down by the expected speedup 
    // in the newly compiled method.
    int prevCMID = plan.getPrevCMID();
    double expectedSpeedup = plan.getExpectedSpeedup();
    double oldNumSamples = VM_Controller.methodSamples.getData(prevCMID);
    double newNumSamples = oldNumSamples / expectedSpeedup;
    VM_Controller.methodSamples.reset(prevCMID);
    if (newCMID > -1) {
      VM_Controller.methodSamples.setData(newCMID, newNumSamples);
    }

    // set the status of the plan accordingly
    if (newCMID != -1) {
      plan.setStatus(VM_ControllerPlan.COMPLETED);
      VM_AdaptiveInlining.clearNonInlinedEdges(prevCMID);
    } else {
      plan.setStatus(VM_ControllerPlan.ABORTED_COMPILATION_ERROR);
    }

    plan.setCMID(newCMID);
    plan.setCompilationCPUTime(compileTime);
    plan.setTimeCompleted(VM_Controller.controllerClock);
    if (VM.LogAOSEvents) {
      if (newCMID == -1) {
	VM_AOSLogging.recompilationAborted(cp);
      } else {
	VM_AOSLogging.recompilationCompleted(cp);
      }
    }
  }
}
