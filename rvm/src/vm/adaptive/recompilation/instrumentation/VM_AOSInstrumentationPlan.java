/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.VM_Method;
import java.util.Vector;
import java.util.Enumeration;

/**
 * VM_AOSInstrumentationPlan.java
 *
 * Defines: 
 * class VM_AOSInstrumentationPlan
 *
 * An instance of this class is created for each method that is
 * instrumented by the adaptive system.  It serves as a place to put
 * information that is needed by the instrumentation phases.  Is is
 * different from an OPT_InstrumentationPlan because it contains
 * information that the non-adaptive opt-compiler can't see.
 *
 *
 * @author Matthew Arnold
 *
 **/

public class VM_AOSInstrumentationPlan extends OPT_InstrumentationPlan {

  
  /**
   * Construct empty plan, must setup manually
   **/ 
  public VM_AOSInstrumentationPlan(VM_Method method) {
    this.method = method;
  }

  /**
   * Construct based on options
   **/ 
  public VM_AOSInstrumentationPlan(VM_AOSOptions options, VM_Method method) {
    // If we want to collect method invocation counts.
    if (options.INSERT_METHOD_COUNTERS_OPT) {
    }
  }

  /** 
   * Initialize instrumentation by the opt compiler immediately before
   * compilation begins.
   **/
  public void initInstrumentation(VM_Method method)
  {
  }

  /** 
   * Called after compilation is complete.  If instrumentation has
   * occured, perform some cleanup/finalization
   **/

  public void finalizeInstrumentation(VM_Method method)
  {

  }

  /** The method that this plan is for */
  private VM_Method method;
}

