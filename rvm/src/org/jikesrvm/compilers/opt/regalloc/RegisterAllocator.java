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
package org.jikesrvm.compilers.opt.regalloc;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.IR;

/**
 * Driver routine for register allocation
 */
public final class RegisterAllocator extends OptimizationPlanCompositeElement {

  public RegisterAllocator() {
    super("Register Allocation", new OptimizationPlanElement[]{
        // 1. Prepare for the allocation
        new OptimizationPlanAtomicElement(new RegisterAllocPreparation()),
        // 2. Perform the allocation, using the live information
        new LinearScan()});
  }

  @Override
  public boolean shouldPerform(OptOptions options) { return true; }

  @Override
  public String getName() { return "RegAlloc"; }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return options.PRINT_REGALLOC;
  }

  private static class RegisterAllocPreparation extends CompilerPhase {
    @Override
    public final boolean shouldPerform(OptOptions options) {
      return true;
    }

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
    public final String getName() {
      return "Register Allocation Preparation";
    }

    /**
     * create the stack manager
     */
    @Override
    public final void perform(org.jikesrvm.compilers.opt.ir.IR ir) {
      ir.stackManager.prepare(ir);
    }
  }
}
