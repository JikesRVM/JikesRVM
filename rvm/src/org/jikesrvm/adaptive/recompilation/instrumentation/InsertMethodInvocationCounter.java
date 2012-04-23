/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.recompilation.instrumentation;

import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.AOSDatabase;
import org.jikesrvm.adaptive.measurements.instrumentation.Instrumentation;
import org.jikesrvm.adaptive.measurements.instrumentation.MethodInvocationCounterData;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * An CompilerPhase that inserts a method invocation counter on the first
 * basic block of the method.  It uses a
 * InstrumentedEventCounterManager to obtain the space to put the
 * counters.
 *
 * Note: one counter data, (MethodInvocationCounterData) is shared
 * across all methods, and is initialized at boot time.  This is
 * unlike other kinds of instrumentation (such as basic block
 * counters) where a separate data object is maintained for each
 * method.
 */
public class InsertMethodInvocationCounter extends CompilerPhase {

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return Controller.options.INSERT_METHOD_COUNTERS_OPT;
  }

  @Override
  public final String getName() { return "InsertMethodInvocationCounters"; }

  /**
   * Insert basic block counters
   *
   * @param ir the governing IR
   */
  @Override
  public final void perform(IR ir) {
    // Don't insert counters in uninterruptible or
    // save volatile methods, or when instrumentation is disabled
    if (!ir.method.isInterruptible() ||
        !Instrumentation.instrumentationEnabled() ||
        ir.method.getDeclaringClass().hasSaveVolatileAnnotation()) {
      return;
    }

    BasicBlock firstBB = ir.cfg.entry();

    MethodInvocationCounterData data = AOSDatabase.methodInvocationCounterData;

    int cmid = ir.compiledMethod.getId();

    // Create a dummy instruction that is later converted into an
    // increment of the appropriate CounterArray element.
    Instruction c = data.createEventCounterInstruction(cmid);

    // Insert it at the beginning of the basic block
    firstBB.prependInstructionRespectingPrologue(c);
  }
}
