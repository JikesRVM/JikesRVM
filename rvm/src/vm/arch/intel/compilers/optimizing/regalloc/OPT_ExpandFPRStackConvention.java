/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$ 

import java.util.Enumeration;
import instructionFormats.*;

/**
 * At the beginning of each basic block, the register allocator expects
 * all floating-point stack locations to be available, and named
 * FPi, 0 < i < 7
 *
 * However, BURS may consume FP stack locations by inserting instructions
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
 * @author Stephen Fink
 */

final class OPT_ExpandFPRStackConvention extends OPT_CompilerPhase
implements OPT_Operators{

  // The number of FPRs available for allocation.
  // Normally 7: we reserve one for final MIR expansion.
  int NUM_ALLOCATABLE_FPR = 7;

  boolean printingEnabled (OPT_Options options, boolean before) {
    return  options.PRINT_CALLING_CONVENTIONS && !before;
  }

  final boolean shouldPerform(OPT_Options options) { 
    return true; 
  }

  final String getName() { 
    return "Expand Calling Convention"; 
  }

  /**
   * Insert the needed dummy defs and uses.
   */
  final void perform(OPT_IR ir)  {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    for (Enumeration b = ir.getBasicBlocks(); b.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)b.nextElement();
      
      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (Enumeration inst = bb.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = (OPT_Instruction)inst.nextElement();
        if (s.operator().isFpPop()) {
          // A pop instruction 'ends' a dummy live range.
          OPT_Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR-fpStackOffset);
          s.insertAfter(MIR_UnaryNoRes.create(DUMMY_USE,OPT_IRTools.D(fpr)));
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          OPT_Register fpr = phys.getFPR(NUM_ALLOCATABLE_FPR-fpStackOffset);
          s.insertBefore(MIR_Nullary.create(DUMMY_DEF,OPT_IRTools.D(fpr)));
          fpStackOffset++;
        }
        if (VM.VerifyAssertions) VM.assert(fpStackOffset >= 0);
        if (VM.VerifyAssertions) VM.assert(fpStackOffset <
                                           NUM_ALLOCATABLE_FPR);
      }
    }
  } 
}
