/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.opt.ir.instructionFormats.*;

/**
 *
 * OPT_InsertMethodInvocationCounter.java
 *
 * An OPT_Phase that inserts a method invocation counter on the first
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
 *
 **/

class OPT_InsertMethodInvocationCounter  extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {
   static final boolean DEBUG = false;

   final boolean shouldPerform(OPT_Options options) {
     return options.INSERT_METHOD_COUNTERS_OPT;
   }

   final String getName() { return "InsertMethodInvocationCounters"; }

   /**
    * Insert basic block counters
    * 
    * @param ir the governing IR
    */
   final public void perform(OPT_IR ir) {

     // Don't insert counters in uninterruptible methods, 
     // or when instrumentation is disabled
     if (!ir.method.isInterruptible() ||
	 !VM_Instrumentation.instrumentationEnabled())
       return;

     OPT_BasicBlock firstBB = ir.cfg.entry();

     VM_MethodInvocationCounterData data = 
       VM_AOSDatabase.methodInvocationCounterData;

     int cmid = ir.compiledMethod.getId();

     // Create a dummy instruction that is later converted into an
     // increment of the appropriate VM_CounterArray element.
     OPT_Instruction c = data.createEventCounterInstruction(cmid);

     // Insert it at the beginnging of the basic block
     firstBB.prependInstructionRespectingPrologue(c);
   }
}

