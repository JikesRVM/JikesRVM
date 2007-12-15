/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.recompilation.instrumentation;

import java.util.ArrayList;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_Instrumentation;
import org.jikesrvm.adaptive.util.VM_AOSOptions;
import org.jikesrvm.compilers.opt.CompilerPhase;
import org.jikesrvm.compilers.opt.InstrumentedEventCounterManager;
import org.jikesrvm.compilers.opt.Options;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTRUMENTED_EVENT_COUNTER;

/**
 *  This phase takes converts "instrumentation instructions" that were
 *  inserted by previous instrumentation phases and "lowers" them,
 *  converting them to the actual instructions that perform the
 *  instrumentation.
 */
public class LowerInstrumentation extends CompilerPhase {

  static final boolean DEBUG = false;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public final boolean shouldPerform(Options options) {
    VM_AOSOptions opts = VM_Controller.options;
    return opts
        .INSERT_INSTRUCTION_COUNTERS ||
                                     opts
                                         .INSERT_METHOD_COUNTERS_OPT ||
                                                                     opts
                                                                         .INSERT_DEBUGGING_COUNTERS ||
                                                                                                    opts
                                                                                                        .INSERT_YIELDPOINT_COUNTERS;
  }

  public final String getName() { return "LowerInstrumentation"; }

  /**
   * Finds all instrumented instructions and calls the appropriate code to
   * convert it into the real sequence of instrumentation instructions.
   *
   * @param ir the governing IR
   */
  public final void perform(IR ir) {
    // Convert all instrumentation instructions into actual counter code
    lowerInstrumentation(ir);

    // TODO: For efficiency, should proably call Simple, or
    // branch optimizations or something.
  }

  /**
   * Actually perform the lowering
   *
   * @param ir the governing IR
   */
  static void lowerInstrumentation(IR ir) {
    /*
    for (BasicBlockEnumeration bbe = ir.getBasicBlocks();
         bbe.hasMoreElements(); ) {
      BasicBlock bb = bbe.next();
      bb.printExtended();
    }
    */

    ArrayList<Instruction> instrumentedInstructions = new ArrayList<Instruction>();

    // Go through all instructions and find the instrumented ones.  We
    // put them in instrumentedInstructions and expand them later
    // because if we expanded them on the fly we mess up the
    // enumeration.
    for (BasicBlockEnumeration bbe = ir.getBasicBlocks(); bbe.hasMoreElements();) {
      BasicBlock bb = bbe.next();

      Instruction i = bb.firstInstruction();
      while (i != null && i != bb.lastInstruction()) {

        if (i.operator() == INSTRUMENTED_EVENT_COUNTER) {
          instrumentedInstructions.add(i);
        }
        i = i.nextInstructionInCodeOrder();
      }
    }

    // Now go through the instructions and "lower" them by calling
    // the counter manager to convert them into real instructions
    for (final Instruction i : instrumentedInstructions) {
      // Have the counter manager for this data convert this into the
      // actual counting code.  For now, we'll hard code the counter
      // manager.  Ideally it should be stored in the instruction,
      // (to allow multiple counter managers.  It would also make this
      // code independant of the adaptive system..)
      InstrumentedEventCounterManager counterManager = VM_Instrumentation.eventCounterManager;

      counterManager.mutateOptEventCounterInstruction(i, ir);
    }

    /*
    for (BasicBlockEnumeration bbe = ir.getBasicBlocks();
         bbe.hasMoreElements(); ) {
      BasicBlock bb = bbe.next();
      bb.printExtended();
    }
    */
  } // end of lowerInstrumentation

}
