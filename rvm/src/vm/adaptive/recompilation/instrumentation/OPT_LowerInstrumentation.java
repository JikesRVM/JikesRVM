/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.adaptive.*;

import java.util.Enumeration;
import java.util.Vector;

/** 
 *  This phase takes converts "instrumentation instructions" that were
 *  inserted by previous instrumentation phases and "lowers" them,
 *  converting them to the actual instructions that perform the
 *  instrumentation.
 *
 *  @author Matthew Arnold
 */
class OPT_LowerInstrumentation  extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

   static final boolean DEBUG = false;

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
   final public void perform(OPT_IR ir) {
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
  static final void lowerInstrumentation(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      //bb.printExtended();
    }
    
    Vector vector = new Vector();
    
    // Go through all instructions and find the instrumented ones.
    // We put them in a vector and expand them later because if we
    // expanded them on the fly we mess up the enumeration.
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      
      OPT_Instruction i = bb.firstInstruction();
      while (i!=null && i!=bb.lastInstruction()) {
        
        if (i.operator() == INSTRUMENTED_EVENT_COUNTER) {
          vector.add(i);
        }
        i = i.nextInstructionInCodeOrder();
      }
    }
    
    // Now go through the instructions and "lower" them by calling
    // the counter manager to convert them into real instructions
    Enumeration e = vector.elements();
    while (e.hasMoreElements()) {
      OPT_Instruction i = (OPT_Instruction) e.nextElement();
      
      // Have the counter manager for this data convert this into the
      // actual counting code.  For now, we'll hard code the counter
      // manager.  Ideally it should be stored in the instruction,
      // (to allow multiple counter managers.  It would also make this
      // code independant of the adaptive system..)
      OPT_InstrumentedEventCounterManager counterManager = 
        VM_Instrumentation.eventCounterManager;
      
      counterManager.mutateOptEventCounterInstruction(i,ir);
    }
    
    for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
         bbe.hasMoreElements(); ) {
      OPT_BasicBlock bb = bbe.next();
      //       bb.printExtended();
    }
  } // end of lowerInstrumentation
  
}
