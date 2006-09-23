/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.adaptive.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * An OPT_CompilerPhase that inserts a method invocation counter on the first
 * basic block of the method.  It uses a
 * VM_InstrumentedEventCounterManager to obtain the space to put the
 * counters.
 *
 * Note: one counter data, (VM_MethodInvocationCounterData) is shared
 * across all methods, and is initialized at boot time.  This is
 * unlike other kinds of instrumentation (such as basic block
 * counters) where a separate data object is maintained for each
 * method.
 *
 * @author Matthew Arnold 
 */
class OPT_InsertMethodInvocationCounter  extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this 
   */
  public OPT_CompilerPhase newExecution (OPT_IR ir) {
    return this;
  }

  public final boolean shouldPerform(OPT_Options options) {
    return VM_Controller.options.INSERT_METHOD_COUNTERS_OPT;
  }

  public final String getName() { return "InsertMethodInvocationCounters"; }
  
  /**
   * Insert basic block counters
   * 
   * @param ir the governing IR
   */
  final public void perform(OPT_IR ir) {
    // Don't insert counters in uninterruptible or
    // save volatile methods, or when instrumentation is disabled
    if (!ir.method.isInterruptible() ||
        !VM_Instrumentation.instrumentationEnabled() ||
        ir.method.getDeclaringClass().isSaveVolatile())
      return;
    
    OPT_BasicBlock firstBB = ir.cfg.entry();

    VM_MethodInvocationCounterData data = 
      VM_AOSDatabase.methodInvocationCounterData;

    int cmid = ir.compiledMethod.getId();

    // Create a dummy instruction that is later converted into an
    // increment of the appropriate VM_CounterArray element.
    OPT_Instruction c = data.createEventCounterInstruction(cmid);

    // Insert it at the beginning of the basic block
    firstBB.prependInstructionRespectingPrologue(c);
  }
}
