/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;
import com.ibm.jikesrvm.opt.ir.*;
/**
 * Driver routine for register allocation
 *
 * @author Stephen Fink
 */
public final class OPT_RegisterAllocator extends OPT_OptimizationPlanCompositeElement {

  public OPT_RegisterAllocator() {
    super("Register Allocation", new OPT_OptimizationPlanElement[] {
      // 1. Prepare for the allocation
      new OPT_OptimizationPlanAtomicElement(new RegisterAllocPreparation()), 
      // 2. Perform the allocation, using the live information
      new OPT_LinearScan()
    });
  }
  
  public final boolean shouldPerform(OPT_Options options) { return true; }
  public final String getName() { return "RegAlloc"; }
  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return options.PRINT_REGALLOC;
  }

  private static class RegisterAllocPreparation extends OPT_CompilerPhase {
    public final boolean shouldPerform (OPT_Options options) {
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

    public final String getName () {
      return  "Register Allocation Preparation";
    }

    /**
     * create the stack manager
     */
    final public void perform (com.ibm.jikesrvm.opt.ir.OPT_IR ir) {
      ir.stackManager.prepare(ir);
    }
  }
}
