/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class expands yield points into an explicit counter decrement
 * and check to allow deterministic thread switching (on a single
 * processor).  The is performed late so that HIR and LIR global
 * optizations are not hindered.  It is performed before reordering
 * phase so that cold basic blocks are moved out of the way.
 *
 * @author Matthew Arnold 
 */
class OPT_DeterministicYieldpoints extends OPT_CompilerPhase
    implements OPT_Operators, OPT_Constants {

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  public final boolean shouldPerform (OPT_Options options) {
    // For now it's a compile-time flag only.
    return VM.BuildForDeterministicThreadSwitching;
  }

  /**
   * Return the name of this phase
   * @return "Deterministic Yieldpoint Expansion"
   */
  public final String getName () {
    return  "Deterministic Yield Point Expansion";
  }

  /**
   * Transform yieldpoint instructions into determinstic sequence. A
   * yieldpoint becomes:
   *
   *     load VM.deterministicThreadSwitchCount into tempReg
   *     if (tempReg <= 0)
   *       call VM_Thread.threadSwitch()
   *       load tempReg // To get reset value
   *     tempReg --;
   *     store tempReg into VM.deterministicThreadSwitchCount
   *
   * The decrement comes after the use because it is more efficient.
   * 
   * @param ir the governing IR 
   */
  final public void perform (OPT_IR ir) {
    OPT_RegisterOperand tempReg = ir.regpool.makeTempInt();

    // For each basic block
    for (OPT_BasicBlock bb = ir.firstBasicBlockInCodeOrder(); 
         bb != null; bb = bb.nextBasicBlockInCodeOrder()) {

      boolean inPrologue = (bb == ir.cfg.entry());

      // For each instruction
      for (OPT_InstructionEnumeration ie
             = bb.forwardInstrEnumerator();
           ie.hasMoreElements();) {
        OPT_Instruction i = ie.next();          

        // If it's an instrumentation operation, expand it
        if (i.operator() == YIELDPOINT_PROLOGUE ||
            i.operator() == YIELDPOINT_EPILOGUE ||
            i.operator() == YIELDPOINT_BACKEDGE) {

          OPT_Instruction instBeforeYP = i.prevInstructionInCodeOrder();
          OPT_BasicBlock beforeYP = bb;

          // Split the basic block into three blocks (beforeYP, ypBB,
          // and afterYP) so the yieldpoint is isolated and can be
          // branched around.
          OPT_BasicBlock ypBB = bb.splitNodeWithLinksAt(instBeforeYP,ir); 
          ypBB.recomputeNormalOut(ir);
          bb.recomputeNormalOut(ir);

          // Split block to separate the 
          OPT_BasicBlock afterYP = ypBB.splitNodeWithLinksAt(i,ir);
          afterYP.recomputeNormalOut(ir);
          ypBB.recomputeNormalOut(ir);

          // Replace the yieldpoint instruction with a call to threadswitch 
          VM_Method m = null;
          if (inPrologue)
            m = VM_Entrypoints.threadSwitchFromPrologueMethod;
          else
            m = VM_Entrypoints.threadSwitchFromBackedgeMethod;
          
          OPT_Instruction c = 
            Call.create0(CALL, null, new OPT_IntConstantOperand(m.getOffset()), OPT_MethodOperand.STATIC(m));
          c.position = ir.gc.inlineSequence;
          c.bcIndex = INSTRUMENTATION_BCI;

          // Insert load after call to threadswitch
          OPT_Instruction load = 
            Load.create(INT_LOAD, tempReg.copyRO(), 
                        ir.regpool.makePROp(),
                        OPT_IRTools.I(VM_Entrypoints.deterministicThreadSwitchCountField.getOffset()),
                        new OPT_LocationOperand(VM_Entrypoints.
                                                deterministicThreadSwitchCountField.getOffset()));
          // Insert the call followed by the load
          i.insertBefore(c);
          c.insertAfter(load);

          // Remove yieldpoint
          i.remove();

          // We're in LIR so lower the new call
          OPT_ConvertToLowLevelIR.callHelper(c,ir);

          // pfs: need insert before instruction for Intel.
          OPT_Instruction dummy = new OPT_Instruction(OPT_Operator.OperatorArray[1], 4);
          beforeYP.appendInstruction(dummy);

          // Create the initial load (for the conditional check)
          load = 
            Load.create(INT_LOAD, tempReg.copyRO(), 
                        ir.regpool.makePROp(),
                        OPT_IRTools.I(VM_Entrypoints.deterministicThreadSwitchCountField.getOffset()),
                        new OPT_LocationOperand(VM_Entrypoints.
                                                deterministicThreadSwitchCountField.getOffset()));
          
          
          //      beforeYP.appendInstruction(load);
          dummy.insertBefore(load);
          dummy.remove();

          // make the branch jump to the basic block afterYP
          OPT_ConditionOperand cond = OPT_ConditionOperand.GREATER();
          OPT_BranchOperand target = afterYP.makeJumpTarget();

          OPT_RegisterOperand guard = ir.regpool.makeTempValidation();
          beforeYP.appendInstruction(IfCmp.create(INT_IFCMP, 
                                                  guard,
                                                  tempReg.copyRO(),
                                                  new OPT_IntConstantOperand(0),
                                                  cond,
                                                  target,
                                                  OPT_BranchProfileOperand.always()));
          beforeYP.recomputeNormalOut(ir);

          // Insert the increment and store in block afterYP
          // Prepend store
          // pfs: cludge to get instruction to insert before.
          afterYP.prependInstruction(dummy);

          OPT_Instruction store = 
            Store.create(INT_STORE, 
                         tempReg.copyRO(),
                         ir.regpool.makePROp(),
                         OPT_IRTools.I(VM_Entrypoints.
                                       deterministicThreadSwitchCountField.getOffset()),
                         new OPT_LocationOperand(VM_Entrypoints.
                                                 deterministicThreadSwitchCountField.getOffset()));

          //      afterYP.prependInstruction(store);
          dummy.insertBefore(store);
          dummy.remove();

          // prependDecrement(afterYP,ir);
          OPT_RegisterOperand use = tempReg.copyD2U();
          OPT_RegisterOperand def = use.copyU2D();
          OPT_Instruction inc = Binary.create(INT_ADD,
                                              def,use, 
                                              OPT_IRTools.I(-1));
          afterYP.prependInstruction(inc);    

          // Matt, don't you need a recomputeNormalOut?
          afterYP.recomputeNormalOut(ir);

          bb = ypBB;                    // will resume with afterYP BB
          break;
        }
      }
    }
  }
}



