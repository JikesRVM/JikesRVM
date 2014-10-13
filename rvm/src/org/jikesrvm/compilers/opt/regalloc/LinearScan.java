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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * Main driver for linear scan register allocation.
 */
public final class LinearScan extends OptimizationPlanCompositeElement {

  /**
   * Build this phase as a composite of others.
   */
  LinearScan() {
    super("Linear Scan Composite Phase",
          new OptimizationPlanElement[]{new OptimizationPlanAtomicElement(new IntervalAnalysis()),
                                            new OptimizationPlanAtomicElement(new RegisterRestrictionsPhase()),
                                            new OptimizationPlanAtomicElement(new LinearScanPhase()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps1()),
                                            new OptimizationPlanAtomicElement(new SpillCode()),
                                            new OptimizationPlanAtomicElement(new UpdateGCMaps2()),
                                            new OptimizationPlanAtomicElement(new UpdateOSRMaps()),});
  }

  /**
   * Mark FMOVs that end a live range?
   */
  static final boolean MUTATE_FMOV = VM.BuildForIA32;

  /*
   * debug flags
   */
  static final boolean DEBUG = false;
  static final boolean VERBOSE_DEBUG = false;
  static final boolean GC_DEBUG = false;
  static final boolean DEBUG_COALESCE = false;

  /**
   * Register allocation is required
   */
  @Override
  public boolean shouldPerform(OptOptions options) {
    return true;
  }

  @Override
  public String getName() {
    return "Linear Scan Composite Phase";
  }

  @Override
  public boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  static int getDFN(Instruction inst) {
    return inst.getScratch();
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  static void setDFN(Instruction inst, int dfn) {
    inst.setScratch(dfn);
  }

  /**
   *  Prints the DFN numbers associated with each instruction.
   *
   *  @param ir the IR that contains the instructions
   */
  static void printDfns(IR ir) {
    System.out.println("DFNS: **** " + ir.getMethod() + "****");
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      System.out.println(getDFN(inst) + " " + inst);
    }
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the end of the live interval. If the
   * interval is open-ended, the dfn for the end of the basic block will
   * be returned instead.
   */
  static int getDfnEnd(LiveIntervalElement live, BasicBlock bb) {
    Instruction end = live.getEnd();
    int dfnEnd;
    if (end != null) {
      dfnEnd = getDFN(end);
    } else {
      dfnEnd = getDFN(bb.lastInstruction());
    }
    return dfnEnd;
  }

  /**
   * @param live the live interval
   * @param bb the basic block for the live interval
   * @return the Depth-first-number of the beginning of the live interval. If the
   * interval is open-ended, the dfn for the beginning of the basic block will
   * be returned instead.
   */
  static int getDfnBegin(LiveIntervalElement live, BasicBlock bb) {
    Instruction begin = live.getBegin();
    int dfnBegin;
    if (begin != null) {
      dfnBegin = getDFN(begin);
    } else {
      dfnBegin = getDFN(bb.firstInstruction());
    }
    return dfnBegin;
  }
}
