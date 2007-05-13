/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OPT_CompilerPhase;
import org.jikesrvm.compilers.opt.OPT_Options;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.ia32.OPT_PhysicalRegisterSet;

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

final class OPT_ExpandFPRStackConvention extends OPT_CompilerPhase
  implements OPT_Operators{
  
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
  public OPT_CompilerPhase newExecution(OPT_IR ir) {
    return this;
  }

  public boolean printingEnabled (OPT_Options options, boolean before) {
    return  options.PRINT_CALLING_CONVENTIONS && !before;
  }

  public String getName() { 
    return "Expand Calling Convention"; 
  }

  /**
   * Insert the needed dummy defs and uses.
   */
  public void perform(OPT_IR ir)  {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (OPT_BasicBlockEnumeration b = ir.getBasicBlocks(); b.hasMoreElements(); ) {
      OPT_BasicBlock bb = b.nextElement();
      
      if (bb instanceof OPT_ExceptionHandlerBasicBlock) {
        // clear all floating-point state at the entry to a catch block
        for (int i=0; i<NUM_ALLOCATABLE_FPR; i++) {
          OPT_Register fpr = phys.getFPR(i);
          bb.prependInstruction(MIR_UnaryNoRes.create(DUMMY_USE,
                                                      OPT_IRTools.D(fpr)));
          bb.prependInstruction(MIR_Nullary.create(DUMMY_DEF,
                                                   OPT_IRTools.D(fpr)));
        }
      }
      
      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (OPT_InstructionEnumeration inst = bb.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = inst.nextElement();
        if (s.operator().isFpPop()) {
          // A pop instruction 'ends' a dummy live range.
          OPT_Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR-fpStackOffset);
          s.insertAfter(MIR_UnaryNoRes.create(DUMMY_USE,OPT_IRTools.D(fpr)));
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
          OPT_Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR-fpStackOffset);
          s.insertBefore(MIR_Nullary.create(DUMMY_DEF,OPT_IRTools.D(fpr)));
        }
        if (VM.VerifyAssertions) VM._assert(fpStackOffset >= 0);
        if (VM.VerifyAssertions) VM._assert(fpStackOffset <
                                           NUM_ALLOCATABLE_FPR);
      }
    }
  } 
}
