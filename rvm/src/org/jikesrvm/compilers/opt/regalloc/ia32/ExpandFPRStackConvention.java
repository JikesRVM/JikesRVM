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
package org.jikesrvm.compilers.opt.regalloc.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet;
import org.jikesrvm.ia32.ArchConstants;

/**
 * At the beginning of each basic block, the register allocator expects
 * all floating-point stack locations to be available, and named
 * FPi, 0 < i < 7
 *
 * <p>However, BURS may consume FP stack locations by inserting instructions
 * that push or pop the floating-point stack.  This phase inserts dummy
 * definitions and uses to indicate when symbolic FP registers are not
 * available for register allocation since BURS has consumed a stack slot.
 *
 * For example,
 * <pre>
 *    FLD t1
 *    ...
 *    FSTP M, t1
 * </pre>
 *
 * will be modified by this phase to indicate that FP6 is not available
 * for allocation in the interval:
 *
 * <pre>
 *   DUMMY_DEF FP6
 *   FLD t1
 *   .....
 *   FSTP M, t1
 *   DUMMY_USE FP6
 * </pre>
 *
 * <p> Additionally, by convention, we will always clear the
 * floating-point stack when delivering an exception.  To model this, we
 * insert dummy defs and uses for each floating-point register at the
 * beginning of each catch block.
 */

public final class ExpandFPRStackConvention extends CompilerPhase implements Operators {

  /**
   * The number of FPRs available for allocation.
   * Normally 7: we reserve one for final MIR expansion.
   */
  private static final int NUM_ALLOCATABLE_FPR = 7;

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  public boolean printingEnabled(OptOptions options, boolean before) {
    return options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public String getName() {
    return "Expand Calling Convention";
  }

  /**
   * Insert the needed dummy defs and uses.
   */
  public void perform(IR ir) {
    if (ArchConstants.SSE2_FULL) {
      return;
    }
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (BasicBlockEnumeration b = ir.getBasicBlocks(); b.hasMoreElements();) {
      BasicBlock bb = b.nextElement();

      if (bb instanceof ExceptionHandlerBasicBlock) {
        // clear all floating-point state at the entry to a catch block
        for (int i = 0; i < NUM_ALLOCATABLE_FPR; i++) {
          Register fpr = phys.getFPR(i);
          bb.prependInstruction(MIR_UnaryNoRes.create(DUMMY_USE, IRTools.D(fpr)));
          bb.prependInstruction(MIR_Nullary.create(DUMMY_DEF, IRTools.D(fpr)));
        }
      }

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (InstructionEnumeration inst = bb.forwardInstrEnumerator(); inst.hasMoreElements();) {
        Instruction s = inst.nextElement();
        if (s.operator().isFpPop()) {
          // A pop instruction 'ends' a dummy live range.
          Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR - fpStackOffset);
          s.insertAfter(MIR_UnaryNoRes.create(DUMMY_USE, IRTools.D(fpr)));
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
          Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR - fpStackOffset);
          s.insertBefore(MIR_Nullary.create(DUMMY_DEF, IRTools.D(fpr)));
        }
        if (VM.VerifyAssertions) VM._assert(fpStackOffset >= 0);
        if (VM.VerifyAssertions) {
          VM._assert(fpStackOffset < NUM_ALLOCATABLE_FPR);
        }
      }
    }
  }
}
