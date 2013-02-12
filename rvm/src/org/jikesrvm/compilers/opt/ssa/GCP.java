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
package org.jikesrvm.compilers.opt.ssa;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;

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
 */
public final class GCP extends OptimizationPlanCompositeElement {

  /**
   * Makes sure we are in SSA and have global value numbers at hand.
   * Then execute the transformations.
   */
  public GCP() {
    super("Global Code Placement", new OptimizationPlanElement[]{
        // 1. Set up IR state to control SSA translation as needed
        new OptimizationPlanAtomicElement(new GCPPreparation()),
        // 2. Get the desired SSA form
        new OptimizationPlanAtomicElement(new EnterSSA()),
        // 3. Perform global CSE
        new OptimizationPlanAtomicElement(new GlobalCSE()),
        // 4. Repair SSA
        new OptimizationPlanAtomicElement(new EnterSSA()),
        // 5. Perform Loop Invariant Code Motion
        new OptimizationPlanAtomicElement(new LICM()),
        // 6. Finalize GCP
        new OptimizationPlanAtomicElement(new GCPFinalization())});
  }

  /**
   * Redefine shouldPerform so that none of the subphases will occur
   * unless we pass through this test.
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    if (options.getOptLevel() < 2) {
      return false;
    }
    return options.SSA_GCP || options.SSA_GCSE;
  }

  static boolean tooBig(IR ir) {
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
  private static class GCPPreparation extends CompilerPhase {
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

    /**
     * Should this phase perform?
     * <p>
     * @return <code>true</code> if SSA-based global code placement
     *  or SSA-based global common subexpression elimination are
     *  enabled
     */
    @Override
    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_GCP || options.SSA_GCSE;
    }

    /**
     * Return the name of the phase
     */
    @Override
    public final String getName() {
      return "GCP Preparation";
    }

    @Override
    public final void perform(IR ir) {
      boolean dont = false;
      //VM.sysWrite("> " + ir.method + "\n");

      if (ir.hasReachableExceptionHandlers()) {
        //VM.sysWrite("has exceptionhandlers\n");
        dont = true;
      }
      if (GCP.tooBig(ir)) {
        dont = true;
      }
      if (dont) {
        ir.options.SSA = false;
        return;
      }
      ir.desiredSSAOptions = new SSAOptions();
      // register in the IR the SSA properties we need for GCP
      if (ir.IRStage == IR.LIR) {
        ir.desiredSSAOptions.setScalarsOnly(true);
        ir.desiredSSAOptions.setBackwards(false);
        ir.desiredSSAOptions.setInsertUsePhis(false);
        ir.desiredSSAOptions.setInsertPEIDeps(false);
        ir.desiredSSAOptions.setHeapTypes(null);
      } else {
        // HIR options
        ir.desiredSSAOptions.setScalarsOnly(false);
        ir.desiredSSAOptions.setBackwards(true);
        ir.desiredSSAOptions.setInsertUsePhis(true);
        ir.desiredSSAOptions.setInsertPEIDeps(!ir.options.SSA_LICM_IGNORE_PEI);
        ir.desiredSSAOptions.setHeapTypes(null);
      }
    }
  }

  /**
   * This class sets up the IR state prior to entering SSA for GCP
   */
  private static class GCPFinalization extends CompilerPhase {
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

    /**
     * Should this phase perform?
     * <p>
     * Perform only if global code placement
     * or global common subexpression elimination are performed.
     */
    @Override
    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_GCP || options.SSA_GCSE;
    }

    /**
     * Return the name of the phase
     */
    @Override
    public final String getName() {
      return "GCP Finalization";
    }

    @Override
    public final void perform(IR ir) {
      ir.options.SSA = true;
      //VM.sysWrite("< " + ir.method + "\n");
      // register in the IR the SSA properties GCP preserves
      if (!GCP.tooBig(ir) && !ir.hasReachableExceptionHandlers() && ir.actualSSAOptions != null) {
        if (ir.IRStage == IR.LIR) {
          ir.actualSSAOptions.setScalarsOnly(true);
          ir.actualSSAOptions.setBackwards(false);
          ir.actualSSAOptions.setInsertUsePhis(false);
          ir.actualSSAOptions.setInsertPEIDeps(false);
          ir.actualSSAOptions.setHeapTypes(null);
        } else {
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

  public static boolean usesOrDefsPhysicalRegisterOrAddressType(Instruction inst) {

    for (int i = inst.getNumberOfOperands() - 1; i >= 0; --i) {
      Operand op = inst.getOperand(i);
      if (op instanceof RegisterOperand) {
        if (op.asRegister().getType().isWordLikeType() || op.asRegister().getRegister().isPhysical()) {
          return true;
        }
      }
    }
    return false;
  }
}
