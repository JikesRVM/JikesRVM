/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.io.*;
import  java.util.Enumeration;

/**
 * Pre-pass Instruction Scheduling Phase
 *
 * This class is declared as "final" which implies that all its methods
 * are "final" too.      
 *
 * @author Igor Pechtchanski
 */
final class OPT_PrePassScheduler extends OPT_CompilerPhase {

  public final boolean shouldPerform(OPT_Options options) {
    return  options.SCHEDULE_PREPASS;
  }

  public final String getName() {
    return  "InstrSched (pre-pass)";
  }

  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return  !before &&          // old interface only printed afterwards
    options.PRINT_SCHEDULE_PRE;
  }

  /**
   * Perform instruction scheduling for a method.
   * This is an MIR to MIR transformation.
   *
   * @param ir the IR in question 
   */
  public final void perform(com.ibm.JikesRVM.opt.ir.OPT_IR ir) {
    new OPT_Scheduler(OPT_Scheduler.PREPASS).perform(ir);
  }

  /**
   * Initialize pre-pass scheduler
   */
  OPT_PrePassScheduler() {
  }
}
