/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.recompilation.instrumentation;

import java.util.ArrayList;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.measurements.instrumentation.VM_Instrumentation;
import org.jikesrvm.adaptive.util.VM_AOSOptions;
import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_InstrumentedEventCounterManager;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INSTRUMENTED_EVENT_COUNTER;

/** 
 *  This phase takes converts "instrumentation instructions" that were
 *  inserted by previous instrumentation phases and "lowers" them,
 *  converting them to the actual instructions that perform the
 *  instrumentation.
 */
public class OPT_LowerInstrumentation  extends OPT_CompilerPhase {

   static final boolean DEBUG = false;
	 
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
    VM_AOSOptions opts = VM_Controller.options;
    return opts.INSERT_INSTRUCTION_COUNTERS ||
      opts.INSERT_METHOD_COUNTERS_OPT ||
      opts.INSERT_DEBUGGING_COUNTERS ||
      opts.INSERT_YIELDPOINT_COUNTERS;
  }

  public final String getName() { return "LowerInstrumentation"; }

   /**
    * Finds all instrumented instructions and calls the appropriate code to 
    * convert it into the real sequence of instrumentation instructions.
    *
    * @param ir the governing IR
    */
   public final void perform(OPT_IR ir) {
     // Convert all instrumentation instructions into actual counter code
     lowerInstrumentation(ir);

     // TODO: For efficiency, should proably call OPT_Simple, or
     // branch optimizations or something.
   }
  
  /**
   * Actually perform the lowering
   *
    * @param ir the governing IR
   */ 
  static void lowerInstrumentation(OPT_IR ir) {
    /*
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      bb.printExtended();
    }
    */

    ArrayList<OPT_Instruction> instrumentedInstructions =
      new ArrayList<OPT_Instruction>();
    
    // Go through all instructions and find the instrumented ones.  We
    // put them in instrumentedInstructions and expand them later
    // because if we expanded them on the fly we mess up the
    // enumeration.
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      
      OPT_Instruction i = bb.firstInstruction();
      while (i!=null && i!=bb.lastInstruction()) {
        
        if (i.operator() == INSTRUMENTED_EVENT_COUNTER) {
          instrumentedInstructions.add(i);
        }
        i = i.nextInstructionInCodeOrder();
      }
    }
    
    // Now go through the instructions and "lower" them by calling
    // the counter manager to convert them into real instructions
    for (final OPT_Instruction i : instrumentedInstructions) {
      // Have the counter manager for this data convert this into the
      // actual counting code.  For now, we'll hard code the counter
      // manager.  Ideally it should be stored in the instruction,
      // (to allow multiple counter managers.  It would also make this
      // code independant of the adaptive system..)
      OPT_InstrumentedEventCounterManager counterManager =
          VM_Instrumentation.eventCounterManager;

      counterManager.mutateOptEventCounterInstruction(i, ir);
    }

    /*
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      bb.printExtended();
    }
    */
  } // end of lowerInstrumentation
  
}
