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

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.database.AOSDatabase;
import org.jikesrvm.adaptive.measurements.instrumentation.Instrumentation;
import org.jikesrvm.adaptive.measurements.instrumentation.YieldpointCounterData;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_BACKEDGE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_EPILOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_PROLOGUE;

/**
 * An opt compiler phase that inserts yieldpoint counters.  Searches
 * for all yieldpoint instructions and inserts an increment after
 * them, using the CounterArrayManager counter manager to implement
 * the counters.
 */
public class InsertYieldpointCounters extends CompilerPhase {

  static final boolean DEBUG = false;

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
    return Controller.options.INSERT_YIELDPOINT_COUNTERS;
  }

  @Override
  public final String getName() { return "InsertYieldpointCounters"; }

  /**
   * counters after all yieldpoint instructions
   *
   * @param ir the governing IR
   */
  @Override
  public final void perform(IR ir) {

    // Don't insert counters in uninterruptible methods,
    // the boot image, or when instrumentation is disabled
    if (!ir.method.isInterruptible() ||
        ir.method.getDeclaringClass().isInBootImage() ||
        !Instrumentation.instrumentationEnabled()) {
      return;
    }

    YieldpointCounterData data = AOSDatabase.yieldpointCounterData;

    if (InsertYieldpointCounters.DEBUG) {
      VM.sysWrite("InsertYieldpointCounters.perform() " + ir.method + "\n");
    }
    // For each yieldpoint, insert a counter.
    for (BasicBlockEnumeration bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      BasicBlock bb = bbe.next();

      if (InsertYieldpointCounters.DEBUG) {
        VM.sysWrite("Considering basic block " + bb.toString() + "\n");
        bb.printExtended();
      }

      Instruction i = bb.firstInstruction();
      while (i != null && i != bb.lastInstruction()) {

        if (i.operator() == YIELDPOINT_PROLOGUE ||
            i.operator() == YIELDPOINT_EPILOGUE ||
            i.operator() == YIELDPOINT_BACKEDGE) {
          String prefix = yieldpointPrefix(i.operator());
          double incrementValue = 1.0;

          if (i.operator() == YIELDPOINT_EPILOGUE) {
            prefix = "METHOD ENTRY ";
          } else if (i.operator() == YIELDPOINT_PROLOGUE) {
            prefix = "METHOD EXIT ";
          } else {
            prefix = "BACKEDGE ";
            incrementValue = 1.0;
          }

          // Create an instruction to increment the counter for this
          // method.  By appending the prefix and method name, it
          // maintains a separate counter for each method, and
          // separates between method entry and backedges.
          Instruction counterInst = data.
              getCounterInstructionForEvent(prefix + ir.method.toString(), incrementValue);

          // Insert the new instruction into the code order
          i.insertAfter(counterInst);
        }

        i = i.nextInstructionInCodeOrder();
      }
    }
  }

  /**
   * Return a string based version of the passed yieldpoint operator
   * @param op the yieldpoint operator
   * @return a string based on the type of yieldpoint operator
   */
  private static String yieldpointPrefix(Operator op) {
    if (op == YIELDPOINT_PROLOGUE) return "Prologue";
    if (op == YIELDPOINT_EPILOGUE) return "Epilogue";
    if (op == YIELDPOINT_BACKEDGE) return "Backedge";
    return "ERROR";
  }
}

