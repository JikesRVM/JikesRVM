/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Global code placement comes in two flavours. The first is loop
 * invariant code motion (LICM), the second is global common sub
 * expression elimination (GCSE).<p>
 *
 * LICM is applied to HIR and LIR, GCSE only to LIR and before
 * LICM.<p>
 *
 * Both algorithms run on SSA and use the dominator tree to determine
 * positions for operations. That's why these operations are called
 * code placement instead of code motion. <p>
 *
 * There is no code yet to deal with partial redundancies.
 *
 * @author Martin Trapp
 * @modified Stephen Fink
 */
final class OPT_GCP extends OPT_OptimizationPlanCompositeElement {

  /**
   * Makes sure we are in SSA and have global value numbers at hand.
   * Then execute the transformations.
   */
  OPT_GCP () {
    super("Global Code Placement",
      new OPT_OptimizationPlanElement[] {
      // 1. Set up IR state to control SSA translation as needed
      new OPT_OptimizationPlanAtomicElement(new GCPPreparation()),   
      // 2. Get the desired SSA form
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()),
      // 3. Perform global CSE
      new OPT_OptimizationPlanAtomicElement(new OPT_GlobalCSE()),
      // 4. Repair SSA
      new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()),
      // 5. Perform Loop Invariant Code Motion
      new OPT_OptimizationPlanAtomicElement(new OPT_LICM()),
      // 6. Finalize GCP
      new OPT_OptimizationPlanAtomicElement(new GCPFinalization())
    });
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  public boolean shouldPerform (OPT_Options options) {
    if (options.getOptLevel() < 2)
      return  false;
    return  options.GCP || options.VERBOSE_GCP || options.GCSE;
  }


  static boolean tooBig(OPT_IR ir){
    boolean res = false;
    if (ir.getMaxBasicBlockNumber() > 1000) {
      //VM.sysWrite (ir.method.toString() + " is too large\n");
      res = true;
    }
    return res;
  }
  
  /**
   * This class sets up the IR state prior to entering SSA for GCP
   */
  private static class GCPPreparation extends OPT_CompilerPhase {

    /**
     * Should this phase perform?
     * @param options
     */
    public final boolean shouldPerform (OPT_Options options) {
      return  options.GCP || options.VERBOSE_GCP || options.GCSE;
    }

    /**
     * Return the name of the phase
     */
    public final String getName () {
      return  "GCP Preparation";
    }

    /**
     * perform the phase
     * @param ir
     */
    final public void perform (OPT_IR ir) {
      boolean dont = false;
      //VM.sysWrite("> " + ir.method + "\n");
      
      if (ir.hasReachableExceptionHandlers()) {
        //VM.sysWrite("has exceptionhandlers\n");
        dont = true;
      }
      if (OPT_GCP.tooBig(ir)) {
        dont = true;
      }
      if (dont) {
        ir.options.SSA = false;
        return;
      }
      ir.desiredSSAOptions = new OPT_SSAOptions();
      // register in the IR the SSA properties we need for GCP
      if (ir.IRStage == ir.LIR) {
        ir.desiredSSAOptions.setScalarsOnly(true);
        ir.desiredSSAOptions.setBackwards(false);
        ir.desiredSSAOptions.setInsertUsePhis(false);
        ir.desiredSSAOptions.setInsertPEIDeps(false);
        ir.desiredSSAOptions.setHeapTypes(null);
      } 
      else {
        // HIR options 
        ir.desiredSSAOptions.setScalarsOnly(false);
        ir.desiredSSAOptions.setBackwards(true);
        ir.desiredSSAOptions.setInsertUsePhis(true);
        ir.desiredSSAOptions.setInsertPEIDeps(!ir.options.LICM_IGNORE_PEI);
        ir.desiredSSAOptions.setHeapTypes(null);
      }
    }
  }


  /**
   * This class sets up the IR state prior to entering SSA for GCP
   */
  private static class GCPFinalization extends OPT_CompilerPhase {

    /**
     * Should this phase perform?
     * @param options
     */
    public final boolean shouldPerform (OPT_Options options) {
      return  options.GCP || options.VERBOSE_GCP || options.GCSE;
    }

    /**
     * Return the name of the phase
     */
    public final String getName () {
      return  "GCP Finalization";
    }

    /**
     * perform the phase
     * @param ir
     */
    final public void perform (OPT_IR ir) {
      ir.options.SSA = true;
      //VM.sysWrite("< " + ir.method + "\n");
      // register in the IR the SSA properties GCP preserves
      if (ir != null && !OPT_GCP.tooBig(ir)
          && !ir.hasReachableExceptionHandlers()
          && ir.actualSSAOptions != null) {
        if (ir.IRStage == ir.LIR) {
          ir.actualSSAOptions.setScalarsOnly(true);
          ir.actualSSAOptions.setBackwards(false);
          ir.actualSSAOptions.setInsertUsePhis(false);
          ir.actualSSAOptions.setInsertPEIDeps(false);
          ir.actualSSAOptions.setHeapTypes(null);
        }
        else {
          // HIR options 
          ir.actualSSAOptions.setScalarsOnly(false);
          ir.actualSSAOptions.setBackwards(true);
          ir.actualSSAOptions.setInsertUsePhis(true);
          ir.actualSSAOptions.setInsertPEIDeps(true);
          ir.actualSSAOptions.setHeapTypes(null);
        }
      }
    }
  }
  
  static boolean usesOrDefsPhysicalRegisterOrAddressType (OPT_Instruction inst) {

    for (int i = inst.getNumberOfOperands() - 1; i >= 0;  --i) {
      OPT_Operand op = inst.getOperand(i);
      if (op instanceof OPT_RegisterOperand)
          if (op.asRegister().type.isWordType() ||
              op.asRegister().register.isPhysical()) return true;
    }
    return false;
  }  
}



