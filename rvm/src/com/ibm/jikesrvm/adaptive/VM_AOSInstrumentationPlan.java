/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.jikesrvm.adaptive;

import com.ibm.jikesrvm.opt.*;
import com.ibm.jikesrvm.classloader.VM_NormalMethod;

/**
 * An instance of this class is created for each method that is
 * instrumented by the adaptive system.  It serves as a place to put
 * information that is needed by the instrumentation phases.  Is is
 * different from an OPT_InstrumentationPlan because it contains
 * information that the non-adaptive opt-compiler can't see.
 *
 * @author Matthew Arnold
 */
public class VM_AOSInstrumentationPlan extends OPT_InstrumentationPlan {
  /**
   * Construct empty plan, must setup manually
   **/ 
  public VM_AOSInstrumentationPlan(VM_NormalMethod method) {
  }

  /**
   * Construct based on options
   **/ 
  public VM_AOSInstrumentationPlan(VM_AOSOptions options, VM_NormalMethod method) {
    // If we want to collect method invocation counts.
    if (options.INSERT_METHOD_COUNTERS_OPT) {
    }
  }

  /** 
   * Initialize instrumentation by the opt compiler immediately before
   * compilation begins.
   **/
  public void initInstrumentation(VM_NormalMethod method) {
  }

  /** 
   * Called after compilation is complete.  If instrumentation has
   * occurred, perform some cleanup/finalization
   **/

  public void finalizeInstrumentation(VM_NormalMethod method) {
  }
}

