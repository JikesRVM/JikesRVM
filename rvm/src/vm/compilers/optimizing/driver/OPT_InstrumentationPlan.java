/*
 * (C) Copyright IBM Corp. 2001
 */
/* $Id$
 */
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;
/*
 * OPT_InstrumentationPlan.java
 *
 * @author Dave Grove
 * @author Michael Hind
 * @author Peter Sweeney
 * @author Matthew Arnold
 *
 * An instance of this class acts instructs the optimizing
 * compiler how to instrument a method to support the 
 * gathering of runtime measurement information
 *
 * Currently empty, but will gradually add function here 
 * as the Adaptive Optimization Subsystem evolves.
 */
public abstract class OPT_InstrumentationPlan {
  /**
   * Should the compiler insert basic block instrumentation 
   **/
  public boolean insert_basic_block_counters = false;
  /**
   * Should the compiler insert method invocation counters
   **/
  public boolean insert_method_invocation_counters = false;

  /**
   * Called before at the beginning of compilation
   */
  public abstract void initInstrumentation (VM_Method method);

  /**
   * Called after compilation completes, but before method is executed
   */
  public abstract void finalizeInstrumentation (VM_Method method);
}
