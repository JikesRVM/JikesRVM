/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An element in the opt compiler's optimization plan.
 * 
 * NOTE: Instances of subclasses of this class are
 *       held in OPT_OptimizationPlanner.masterPlan
 *       and thus represent global state.
 *       It is therefore incorrect for any per-compilation
 *       state to be stored in an instance field of
 *       one of these objects.
 *
 * TODO: refactor the optimization plan elements and compiler phases
 *
 * @author Steve Fink
 * @author Dave Grove
 * @author Michael Hind
 */
public abstract class OPT_OptimizationPlanElement {

  /**
   * Determine, possibly by consulting the passed options object,
   * if this optimization plan element should be performed.
   * 
   * @param options The OPT_Options object for the current compilation.
   * @return true if the plan element should be performed.
   */
  abstract boolean shouldPerform (OPT_Options options);

  /**
   * Do the work represented by this element in the optimization plan.
   * The assumption is that the work will modify the IR in some way.
   * 
   * @param ir The OPT_IR object to work with.
   */
  abstract void perform (OPT_IR ir);

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   * By default, printing is not enabled.
   * Subclasses should overide this method if they want to provide IR dumping.
   * 
   * @param options the compiler options for the compilation
   * @param before true when invoked before perform, false otherwise.
   * @return true if the IR should be printed, false otherwise.
   */
  boolean printingEnabled (OPT_Options options, boolean before) {
    return  false;
  }

  /**
   * This method is called to initialize the optimization plan support
   *  measuring compilation.
   */
  abstract void initializeForMeasureCompilation();

  /**
   * Generate (to the sysWrite stream) a report of the
   * time spent performing this element of the optimization plan. 
   *
   * @see OPT_OptimizationPlanner.generateOptimizingCompilerSubsystemReport
   * @param indent Number of spaces to indent report.
   * @param timeCol Column number of time portion of report.
   * @param totalTime Total opt compilation time in seconds.
   */
  abstract void reportStats (int indent, int timeCol, double totalTime);

  /**
   * Report the elapsed time spent in the PlanElement
   * @return time spend in the plan (in seconds)
   */
  abstract double elapsedTime ();

  /**
   * Helper function for <code> reportStats </code>
   */
  protected void prettyPrintTime (double time, double totalTime) {
    int t = VM_Time.toMilliSecs(time);
    if (t < 1000000)
      VM.sysWrite(" ");
    if (t < 100000)
      VM.sysWrite(" ");
    if (t < 10000)
      VM.sysWrite(" ");
    if (t < 1000)
      VM.sysWrite(" ");
    if (t < 100)
      VM.sysWrite(" ");
    if (t < 10)
      VM.sysWrite(" ");
    VM.sysWrite(t, false);
    if (time/totalTime > 0.10) {
      VM.sysWrite("    ");
    } 
    else {
      VM.sysWrite("     ");
    }
    VM_RuntimeCompilerInfrastructure.printPercentage(time, totalTime);
    VM.sysWrite("%\n");
  }
}
