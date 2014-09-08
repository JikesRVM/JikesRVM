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
import org.jikesrvm.compilers.opt.ir.Register;

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
   *  Associates the passed live interval with the passed register, using
   *  the scratchObject field of Register.
   *
   *  @param reg the register
   *  @param interval the live interval
   */
  static void setInterval(Register reg, CompoundInterval interval) {
    reg.scratchObject = interval;
  }

  /**
   *  Returns the interval associated with the passed register.
   *  @param reg the register
   *  @return the live interval or {@code null}
   */
  static CompoundInterval getInterval(Register reg) {
    return (CompoundInterval) reg.scratchObject;
  }

  /**
   *  returns the dfn associated with the passed instruction
   *  @param inst the instruction
   *  @return the associated dfn
   */
  static int getDFN(Instruction inst) {
    return inst.scratch;
  }

  /**
   *  Associates the passed dfn number with the instruction
   *  @param inst the instruction
   *  @param dfn the dfn number
   */
  static void setDFN(Instruction inst, int dfn) {
    inst.scratch = dfn;
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

  /**
   * Implements a basic live interval (no holes), which is a pair
   * <pre>
   *   begin    - the starting point of the interval
   *   end      - the ending point of the interval
   * </pre>
   *
   * <p> Begin and end are numbers given to each instruction by a numbering pass.
   */
  static class BasicInterval {

    /**
     * DFN of the beginning instruction of this interval
     */
    private final int begin;
    /**
     * DFN of the last instruction of this interval
     */
    private int end;

    BasicInterval(int begin, int end) {
      this.begin = begin;
      this.end = end;
    }

    /**
     * @return the DFN signifying the beginning of this basic interval
     */
    final int getBegin() {
      return begin;
    }

    /**
     * @return the DFN signifying the end of this basic interval
     */
    final int getEnd() {
      return end;
    }

    /**
     * Extends a live interval to a new endpoint.
     *
     * @param newEnd the new end point
     */
    final void setEnd(int newEnd) {
      end = newEnd;
    }

    final boolean startsAfter(int dfn) {
      return begin > dfn;
    }

    final boolean startsBefore(int dfn) {
      return begin < dfn;
    }

    final boolean contains(int dfn) {
      return begin <= dfn && end >= dfn;
    }

    final boolean startsBefore(BasicInterval i) {
      return begin < i.begin;
    }

    final boolean endsAfter(BasicInterval i) {
      return end > i.end;
    }

    final boolean sameRange(BasicInterval i) {
      return begin == i.begin && end == i.end;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof BasicInterval)) return false;

      BasicInterval i = (BasicInterval) o;
      return sameRange(i);
    }

    final boolean endsBefore(int dfn) {
      return end < dfn;
    }

    final boolean endsAfter(int dfn) {
      return end > dfn;
    }

    final boolean intersects(BasicInterval i) {
      int iBegin = i.getBegin();
      int iEnd = i.getEnd();
      return !(endsBefore(iBegin + 1) || startsAfter(iEnd - 1));
    }

    @Override
    public String toString() {
      String s = "[ " + begin + ", " + end + " ] ";
      return s;
    }
  }

  /**
   * A basic interval contained in a CompoundInterval.
   */
  static class MappedBasicInterval extends BasicInterval {
    final CompoundInterval container;

    MappedBasicInterval(BasicInterval b, CompoundInterval c) {
      super(b.begin, b.end);
      this.container = c;
    }

    MappedBasicInterval(int begin, int end, CompoundInterval c) {
      super(begin, end);
      this.container = c;
    }

    @Override
    public boolean equals(Object o) {
      if (super.equals(o)) {
        MappedBasicInterval i = (MappedBasicInterval) o;
        return container == i.container;
      } else {
        return false;
      }
    }

    @Override
    public String toString() {
      return "<" + container.getRegister() + ">:" + super.toString();
    }

  }
}
