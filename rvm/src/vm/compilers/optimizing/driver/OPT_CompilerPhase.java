/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.OPT_IR;

/**
 * Compiler phases all extend this abstract class.
 * All compiler phases must provide implementations of 
 * two abstract methods:
 * <ul>
 *  <li> getName:  return a String that is the name of the phase
 *  <li> perform:  actually do the work of the phase
 * </ul>
 * 
 * <p> By default, a new instance of the phase is created each time
 * shouldPerform is called.  This instance is discarded as soon
 * as shouldPerform completes. Therefore, it is allowable 
 * (and is suggested when necessary) for subclasses 
 * to use their instance fields to hold per-compilation state.
 * To be more concrete, the pattern of use is:
 * <pre>
 *  newExecution(ir).performPhase(ir).
 * </pre>
 * @see OPT_OptimizationPlanAtomicElement#perform
 *
 * <p> NOTE: compiler phases that do not need to use instance
 * fields to hold per-compilation state may override 
 * <code> newExecution() </code> to return this.  Doing so may lead to
 * memory leaks and concurrent access problems, so this should be done
 * with great care!
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Michael Hind
 */
public abstract class OPT_CompilerPhase implements Cloneable {

  /**
   * The plan element that contains this phase.
   * Only useful if the phase wants to gather additional statistics
   * for a measure compilation report.
   */
  OPT_OptimizationPlanAtomicElement container;


  /**
   * @return a String which is the name of the phase.
   */
  public abstract String getName ();

  /**
   * This is the method that actually does the work of the phase.
   *
   * @param OPT_IR the IR on which to apply the phase
   */
  public abstract void perform (OPT_IR ir);

  /**
   * This method determines if the phase should be run, based on the
   * OPT_Options object it is passed.
   * By default, phases are always performed.
   * Subclasses should override this method if they only want
   * to be performed conditionally.
   *
   * @param options the compiler options for the compilation
   * @return true if the phase should be performed
   */
  public boolean shouldPerform (OPT_Options options) {
    return true;
  }

  /**
   * Returns true if the phase wants the IR dumped before and/or after it runs.
   * By default, printing is not enabled.
   * Subclasses should overide this method if they want to provide IR dumping.
   * 
   * @param options the compiler options for the compilation
   * @param before true when invoked before perform, false otherwise.
   * @return true if the IR should be printed, false otherwise.
   */
  public boolean printingEnabled (OPT_Options options, boolean before) {
    return false;
  }

  /**
   * Called when printing a measure compilation report to enable a phase
   * to report additional phase-specific statistics.
   */
  public void reportAdditionalStats() {}

  /**
   * This method is called immediately before performPhase.
   * Phases that do not need to create a new instance for each execution
   * may override this method to return this, but this must be done carefully!
   *
   * @param ir the OPT_IR that is about to be passed to performPhase
   * @return an opt compiler phase on which performPhase may be invoked.
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    try {
      return (OPT_CompilerPhase)this.clone();
    } catch (CloneNotSupportedException e) {
      // we implement Cloneable, so of course we can't reach here.
      return null;
    }
  }

  /**
   * Runs a phase by calling perform on the supplied IR surrounded by 
   * printing/messaging/debugging glue.
   * @param ir the OPT_IR object on which to do the work of the phase.
   */
  public final void performPhase (OPT_IR ir) {
    if (printingEnabled(ir.options, true)) {
      if (!ir.options.hasMETHOD_TO_PRINT() ||
          ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        // only print above centain opt level.
        if (ir.options.getOptLevel() >= ir.options.IR_PRINT_LEVEL) {
          dumpIR(ir, "Before " + getName());
        }
      }
    }
    if (ir.options.PRINT_PHASES) VM.sysWrite(getName());

    perform(ir);                // DOIT!!

    if (ir.options.PRINT_PHASES) VM.sysWrite(" done\n");
    if (ir.options.PRINT_ALL_IR || printingEnabled(ir.options, false)) {
      if (!ir.options.hasMETHOD_TO_PRINT() ||
          ir.options.fuzzyMatchMETHOD_TO_PRINT(ir.method.toString())) {
        // only print when above certain opt level
        if (ir.options.getOptLevel() >= ir.options.IR_PRINT_LEVEL) {
          dumpIR(ir, "After " + getName());
        }
      }
    }

    if (OPT_IR.PARANOID) verify(ir);
  }

  /**
   * Prints the IR, optionally including the CFG
   * 
   * @param ir the IR to print
   * @param tag a String to use in the start/end message of the IR dump
   */
  public static void dumpIR (OPT_IR ir, String tag) {
    System.out.println("********* START OF IR DUMP  " + tag + "   FOR "
                       + ir.method);
    ir.printInstructions();
    if (ir.options.PRINT_CFG) {
      ir.cfg.printDepthFirst();
    }
    System.out.println("*********   END OF IR DUMP  " + tag + "   FOR "
                       + ir.method);
  }

  /**
   * Verify the IR.  
   * Written as a non-final virtual method to allow late stages in the 
   * compilation pipeline (eg ConvertMIR2MC) to skip verification.
   * 
   * @param ir the IR to verify
   */
  public void verify (OPT_IR ir) {
    ir.verify(getName(), true);
  }
}
