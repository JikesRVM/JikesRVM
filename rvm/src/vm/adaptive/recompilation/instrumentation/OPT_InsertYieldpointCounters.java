/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import com.ibm.JikesRVM.adaptive.*;

/** 
 * An opt compiler phase that inserts yieldpoint counters.  Searches
 * for all yieldpoint instructions and inserts an increment after
 * them, using the VM_CounterArrayManager counter manager to implement
 * the counters.
 *
 * @author Matthew Arnold 
 */
class OPT_InsertYieldpointCounters  extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

   static final boolean DEBUG = false;

   public final boolean shouldPerform(OPT_Options options) {
     return VM_Controller.options.INSERT_YIELDPOINT_COUNTERS;
   }

   public final String getName() { return "InsertYieldpointCounters"; }

   /**
    * counters after all yieldpoint instructions
    *
    * @param ir the governing IR
    */
   final public void perform(OPT_IR ir) {

     // Don't insert counters in uninterruptible methods, 
     // the boot image, or when instrumentation is disabled
     if (!ir.method.isInterruptible() ||
         ir.method.getDeclaringClass().isInBootImage() ||
         !VM_Instrumentation.instrumentationEnabled())
       return;

     VM_YieldpointCounterData data = 
       VM_AOSDatabase.yieldpointCounterData;

     if (OPT_InsertYieldpointCounters.DEBUG) {
       VM.sysWrite("OPT_InsertYieldpointCounters.perform() " + 
                   ir.method + "\n");
     }
     // For each yieldpoint, insert a counter.
     for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
          bbe.hasMoreElements(); ) {
       OPT_BasicBlock bb = bbe.next();

       if (OPT_InsertYieldpointCounters.DEBUG) {
         VM.sysWrite("Considering basic block " + bb.toString() + "\n");
          bb.printExtended();
       }
       
       OPT_Instruction i =  bb.firstInstruction();
       while (i!=null && i!=bb.lastInstruction()) {
         
         if (i.operator() == YIELDPOINT_PROLOGUE ||
             i.operator() == YIELDPOINT_EPILOGUE ||
             i.operator() == YIELDPOINT_BACKEDGE) {
           String prefix = yieldpointPrefix(i.operator());
           double incrementValue = 1.0;
           
           if (i.operator() == YIELDPOINT_EPILOGUE) {
             prefix = "METHOD ENTRY ";
           }
           else if (i.operator() == YIELDPOINT_PROLOGUE) {
             prefix = "METHOD EXIT ";
           }
           else {
             prefix = "BACKEDGE ";
             incrementValue=1.0;  
           }
           
           // Create an instruction to increment the counter for this
           // method.  By appending the prefix and method name, it
           // maintains a separate counter for each method, and
           // separates between method entry and backedges.
           OPT_Instruction counterInst = data.
             getCounterInstructionForEvent(prefix+ir.method.toString(),
                                           incrementValue);
           
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
  private static String yieldpointPrefix(OPT_Operator op) {
    if (op == YIELDPOINT_PROLOGUE) return "Prologue";
    if (op == YIELDPOINT_EPILOGUE) return "Epilogue";
    if (op == YIELDPOINT_BACKEDGE) return "Backedge";
    return "ERROR";
  }
}

