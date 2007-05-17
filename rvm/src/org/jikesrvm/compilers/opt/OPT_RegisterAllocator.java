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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.compilers.opt.ir.OPT_IR;

/**
 * Driver routine for register allocation
 */
public final class OPT_RegisterAllocator extends OPT_OptimizationPlanCompositeElement {

  public OPT_RegisterAllocator() {
    super("Register Allocation", new OPT_OptimizationPlanElement[]{
        // 1. Prepare for the allocation
        new OPT_OptimizationPlanAtomicElement(new RegisterAllocPreparation()),
        // 2. Perform the allocation, using the live information
        new OPT_LinearScan()});
  }

  public boolean shouldPerform(OPT_Options options) { return true; }

  public String getName() { return "RegAlloc"; }

  public boolean printingEnabled(OPT_Options options, boolean before) {
    return options.PRINT_REGALLOC;
  }

  private static class RegisterAllocPreparation extends OPT_CompilerPhase {
    public final boolean shouldPerform(OPT_Options options) {
      return true;
    }

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public OPT_CompilerPhase newExecution(OPT_IR ir) {
      return this;
    }

    public final String getName() {
      return "Register Allocation Preparation";
    }

    /**
     * create the stack manager
     */
    public final void perform(org.jikesrvm.compilers.opt.ir.OPT_IR ir) {
      ir.stackManager.prepare(ir);
    }
  }
}
