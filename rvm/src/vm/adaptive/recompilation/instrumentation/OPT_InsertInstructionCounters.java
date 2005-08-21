/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.adaptive.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.ArrayList;
import java.util.Iterator;

/** 
 * The following OPT phase inserts counters on all instructions in the
 * IR.  It maintians one counter for each operand type, so it output
 * how many loads were executed, how many int_add's etc.  This is
 * useful for debugging and assessing the accuracy of optimizations.
 *
 * Note: The counters are added at the end of HIR, so the counts will
 * NOT reflect any changes to the code that occur after HIR.
 * 
 * @author Matthew Arnold 
 */
class OPT_InsertInstructionCounters  extends OPT_CompilerPhase
  implements OPT_Operators, VM_Constants, OPT_Constants {

  public final boolean shouldPerform(OPT_Options options) {
    return VM_Controller.options.INSERT_INSTRUCTION_COUNTERS;
  }

  public final String getName() { return "InsertInstructionCounters"; }

   /**
    * Insert a counter on every instruction, and group counts by
    * opcode type.  
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

     // Get the data object that handles the counters
     VM_StringEventCounterData data = 
       VM_AOSDatabase.instructionCounterData;

     // Create a vector of basic blocks up front because the blocks
     // are modified as we iterate below.
     ArrayList bbList = new ArrayList();
     for (OPT_BasicBlockEnumeration bbe = ir.getBasicBlocks(); 
          bbe.hasMoreElements(); ) {
       OPT_BasicBlock bb = bbe.next();
       bbList.add(bb);
     }
     
     // Iterate through the basic blocks
     for (Iterator itr = bbList.iterator();
          itr.hasNext(); ) {
       OPT_BasicBlock bb = (OPT_BasicBlock) itr.next();
       
       // Add instructions to vector so enumeration doesn't mess
       // things up.  There is probably a better way to do this, but
       // it doesn't matter because this is a debugging phase.
       ArrayList iList = new ArrayList();
       OPT_Instruction i = bb.firstInstruction();
       while (i!=null && i!=bb.lastInstruction()) {
         iList.add(i);
         i = i.nextInstructionInCodeOrder();
       }
       
       // Iterate through all the instructions in this block.
       for (Iterator instructions = iList.iterator();
            instructions.hasNext();) {
         i = (OPT_Instruction) instructions.next();

         // Skip dangerous instructions
         if (i.operator() == LABEL ||
             Prologue.conforms(i))
           continue;
         
         if (i.isBranch() || 
             i.operator() == RETURN) {
           
           // It's a branch, so you need to be careful how you insert the 
           // counter.
           OPT_Instruction prev = i.prevInstructionInCodeOrder();
           
           // If the instruction above this branch is also a branch,
           // then we can't instrument as-is because a basic block
           // must end with branches only.  Solve by splitting block.
           if (prev.isBranch()) {
             OPT_BasicBlock newBlock = bb.splitNodeWithLinksAt(prev,ir);
             bb.recomputeNormalOut(ir);
           }
           
           // Use the name of the operator as the name of the event
           OPT_Instruction counterInst = data.
             getCounterInstructionForEvent(i.operator().toString());
           
           // Insert the new instruction into the code order
           i.insertBefore(counterInst);      
         }
         else {
           // It's a non-branching instruction.  Insert counter before
           // the instruction.
           
           // Use the name of the operator as the name of the event
           OPT_Instruction counterInst = data.
             getCounterInstructionForEvent(i.operator().toString());

             i.insertBefore(counterInst);
         }
       }
     }
   }
}
