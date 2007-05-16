/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_InterfaceMethodSignature;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.compilers.opt.OPT_Bits;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_Binary;
import org.jikesrvm.compilers.opt.ir.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.MIR_CondBranch2;
import org.jikesrvm.compilers.opt.ir.MIR_CondCall;
import org.jikesrvm.compilers.opt.ir.MIR_DataLabel;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.MIR_LoadUpdate;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.OPT_BasicBlock;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_MethodOperand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BBEND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LABEL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.MIR_LOWTABLESWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_ADDI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_ADDIS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCOND;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCOND2_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCTR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCTRL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BCTRL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_BLRL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_CMPI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_DATA_LABEL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LAddr;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LDI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LDIS;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LInt;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_LIntUX;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MFSPR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_MTSPR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PPC_SLWI;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.RESOLVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.UNINT_BEGIN_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.UNINT_END_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_BACKEDGE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_EPILOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_OSR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.YIELDPOINT_PROLOGUE_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_Register;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.ppc.OPT_PowerPCConditionOperand;
import static org.jikesrvm.compilers.opt.ppc.OPT_PhysicalRegisterConstants.LAST_SCRATCH_GPR;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.unboxed.Offset;

/**
 * Final acts of MIR expansion for the PowerPC architecture.
 * Things that are expanded here (immediately before final assembly)
 * should only be those sequences that cannot be expanded earlier
 * due to difficulty in keeping optimizations from interfering with them.
 */
public abstract class OPT_FinalMIRExpansion extends OPT_IRTools {

  /**
   * @param ir the IR to expand
   * @return upperbound on number of machine code instructions
   * that will be generated for this IR
   */
  public static int expand(OPT_IR ir) {
    int instructionCount = 0;
    int conditionalBranchCount = 0;
    int machinecodeLength = 0;

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (OPT_Instruction p = ir.firstInstructionInCodeOrder();
         p != null;
         p = p.nextInstructionInCodeOrder()) {
      p.setmcOffset(-1);
      p.scratchObject = null;
      switch (p.getOpcode()) {
        case MIR_LOWTABLESWITCH_opcode: {

          OPT_BasicBlock tableBlock = p.getBasicBlock();
          OPT_BasicBlock nextBlock =
              tableBlock.splitNodeWithLinksAt(p.prevInstructionInCodeOrder(), ir);
          nextBlock.firstInstruction().setmcOffset(-1);
          OPT_Register regI = MIR_LowTableSwitch.getIndex(p).register;
          int NumTargets = MIR_LowTableSwitch.getNumberOfTargets(p);
          tableBlock.appendInstruction(MIR_Call.create0(PPC_BL, null, null,
                                                        nextBlock.makeJumpTarget()));

          for (int i = 0; i < NumTargets; i++) {
            tableBlock.appendInstruction(MIR_DataLabel.create(PPC_DATA_LABEL,
                                                              MIR_LowTableSwitch.getClearTarget(p, i)));
          }
          OPT_Register temp = phys.getGPR(0);
          p.insertBefore(MIR_Move.create(PPC_MFSPR, A(temp), A(phys.getLR())));
          p.insertBefore(MIR_Binary.create(PPC_SLWI, I(regI), I(regI), IC(2)));
          p.insertBefore(MIR_LoadUpdate.create(PPC_LIntUX, I(temp), I(regI), A(temp)));
          p.insertBefore(MIR_Binary.create(PPC_ADD, A(regI), A(regI), I(temp)));
          p.insertBefore(MIR_Move.create(PPC_MTSPR, A(phys.getCTR()), A(regI)));
          MIR_Branch.mutate(p, PPC_BCTR);
          instructionCount += NumTargets + 7;
        }
        break;
        case PPC_BCOND2_opcode: {
          OPT_RegisterOperand cond = MIR_CondBranch2.getClearValue(p);
          p.insertAfter(MIR_CondBranch.create(PPC_BCOND, cond.copyU2U(),
                                              MIR_CondBranch2.getClearCond2(p),
                                              MIR_CondBranch2.getClearTarget2(p),
                                              MIR_CondBranch2.getClearBranchProfile2(p)));
          MIR_CondBranch.mutate(p, PPC_BCOND, cond,
                                MIR_CondBranch2.getClearCond1(p),
                                MIR_CondBranch2.getClearTarget1(p),
                                MIR_CondBranch2.getClearBranchProfile1(p));
          conditionalBranchCount++;
        }
        break;
        case PPC_BLRL_opcode:
        case PPC_BCTRL_opcode: {
          // indirect calls.  Second part of fast interface invoker expansion
          // See also OPT_ConvertToLowlevelIR.java
          if (VM.BuildForIMTInterfaceInvocation) {
            if (MIR_Call.hasMethod(p)) {
              OPT_MethodOperand mo = MIR_Call.getMethod(p);
              if (mo.isInterface()) {
                VM_InterfaceMethodSignature sig = VM_InterfaceMethodSignature.findOrCreate(mo.getMemberRef());
                int signatureId = sig.getId();
                OPT_Instruction s;
                if (OPT_Bits.fits(signatureId, 16)) {
                  s = MIR_Unary.create(PPC_LDI,
                                       I(phys.getGPR(LAST_SCRATCH_GPR)),
                                       IC(signatureId));
                  p.insertBefore(s);
                  instructionCount++;
                } else {
                  s = MIR_Unary.create(PPC_LDIS,
                                       I(phys.getGPR(LAST_SCRATCH_GPR)),
                                       IC(OPT_Bits.PPCMaskUpper16(signatureId)));
                  p.insertBefore(s);
                  s = MIR_Binary.create(PPC_ADDI,
                                        I(phys.getGPR(LAST_SCRATCH_GPR)),
                                        I(phys.getGPR(LAST_SCRATCH_GPR)),
                                        IC(OPT_Bits.PPCMaskLower16(signatureId)));
                  p.insertBefore(s);
                  instructionCount += 2;
                }
              }
            }
          }
          instructionCount++;
        }
        break;
        case LABEL_opcode:
        case BBEND_opcode:
        case UNINT_BEGIN_opcode:
        case UNINT_END_opcode:
          // These generate no code, so don't count them.
          break;
        case RESOLVE_opcode: {
          OPT_Register zero = phys.getGPR(0);
          OPT_Register JTOC = phys.getJTOC();
          OPT_Register CTR = phys.getCTR();
          if (VM.VerifyAssertions) {
            VM._assert(p.bcIndex >= 0 && p.position != null);
          }
          Offset offset = VM_Entrypoints.optResolveMethod.getOffset();
          if (OPT_Bits.fits(offset, 16)) {
            p.insertBefore(MIR_Load.create(PPC_LAddr, A(zero), A(JTOC), IC(OPT_Bits.PPCMaskLower16(offset))));
          } else {
            if (VM.VerifyAssertions) VM._assert(OPT_Bits.fits(offset, 32)); //not implemented
            p.insertBefore(MIR_Binary.create(PPC_ADDIS, A(zero), A(JTOC),
                                             IC(OPT_Bits.PPCMaskUpper16(offset))));
            p.insertBefore(MIR_Load.create(PPC_LAddr, A(zero), A(zero), IC(OPT_Bits.PPCMaskLower16(offset))));
            instructionCount += 1;
          }
          p.insertBefore(MIR_Move.create(PPC_MTSPR, A(CTR), A(zero)));
          instructionCount += 3;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_Call.mutate0(p, PPC_BCTRL, null, null);
          break;
        }
        case YIELDPOINT_PROLOGUE_opcode: {
          OPT_Register TSR = phys.getTSR();
          OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                                                  VM_Thread.PROLOGUE);
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p, PPC_BCL, null, null, I(TSR),
                               OPT_PowerPCConditionOperand.NOT_EQUAL(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_BACKEDGE_opcode: {
          OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                                                  VM_Thread.BACKEDGE);
          OPT_Register zero = phys.getGPR(0);
          OPT_Register TSR = phys.getTSR();
          OPT_Register PR = phys.getPR();
          Offset offset = VM_Entrypoints.takeYieldpointField.getOffset();
          if (VM.VerifyAssertions) VM._assert(OPT_Bits.fits(offset, 16));
          p.insertBefore(MIR_Load.create(PPC_LInt, I(zero), A(PR),
                                         IC(OPT_Bits.PPCMaskLower16(offset))));
          p.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(zero), IC(0)));
          instructionCount += 2;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p, PPC_BCL, null, null, I(TSR),
                               OPT_PowerPCConditionOperand.GREATER(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_EPILOGUE_opcode: {
          OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                                                  VM_Thread.EPILOGUE);
          OPT_Register zero = phys.getGPR(0);
          OPT_Register TSR = phys.getTSR();
          OPT_Register PR = phys.getPR();
          Offset offset = VM_Entrypoints.takeYieldpointField.getOffset();
          if (VM.VerifyAssertions) VM._assert(OPT_Bits.fits(offset, 16));
          p.insertBefore(MIR_Load.create(PPC_LInt, I(zero), A(PR),
                                         IC(OPT_Bits.PPCMaskLower16(offset))));
          p.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(zero), IC(0)));
          instructionCount += 2;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p, PPC_BCL, null, null, I(TSR),
                               OPT_PowerPCConditionOperand.NOT_EQUAL(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_OSR_opcode: {
          // unconditionally branch to yield point.
          OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                                                  VM_Thread.OSROPT);
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_Call.mutate0(p, PPC_BL, null, null,
                           yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
        }
        instructionCount++;
        break;

        default:
          if (p.operator().isConditionalBranch()) {
            conditionalBranchCount++;
          } else {
            instructionCount++;
          }
          break;
      }
    }

    // this is conservative but pretty close, especially for
    // reasonably sized methods
    if ((instructionCount + conditionalBranchCount) > OPT_Assembler.MAX_COND_DISPL) {
      machinecodeLength = instructionCount + 2 * conditionalBranchCount;
    } else {
      machinecodeLength = instructionCount + conditionalBranchCount;
    }

    if ((machinecodeLength & ~OPT_Assembler.MAX_24_BITS) != 0) {
      throw new OPT_OptimizingCompilerException("CodeGen",
                                                "method too large to compile:",
                                                OPT_Assembler.MAX_24_BITS);
    }
    return machinecodeLength;
  }

  /**
   * Return a basic block holding the call to a runtime yield service.
   * Create a new basic block at the end of the code order if necessary.
   *
   * @param ir the governing IR
   * @param whereFrom is this yieldpoint from the PROLOGUE, EPILOGUE, or a
   * BACKEDGE?
   */
  static OPT_BasicBlock findOrCreateYieldpointBlock(OPT_IR ir,
                                                    int whereFrom) {
    VM_Method meth = null;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register zero = phys.getGPR(0);

    // first see if the requested block exists. If not, set up some
    // state for creating the block
    if (whereFrom == VM_Thread.PROLOGUE) {
      if (ir.MIRInfo.prologueYieldpointBlock != null) {
        return ir.MIRInfo.prologueYieldpointBlock;
      } else {
        meth = VM_Entrypoints.optThreadSwitchFromPrologueMethod;
      }
    } else if (whereFrom == VM_Thread.BACKEDGE) {
      if (ir.MIRInfo.backedgeYieldpointBlock != null) {
        return ir.MIRInfo.backedgeYieldpointBlock;
      } else {
        meth = VM_Entrypoints.optThreadSwitchFromBackedgeMethod;
      }
    } else if (whereFrom == VM_Thread.EPILOGUE) {
      if (ir.MIRInfo.epilogueYieldpointBlock != null) {
        return ir.MIRInfo.epilogueYieldpointBlock;
      } else {
        meth = VM_Entrypoints.optThreadSwitchFromEpilogueMethod;
      }
    } else if (whereFrom == VM_Thread.OSROPT) {
      if (ir.MIRInfo.osrYieldpointBlock != null) {
        return ir.MIRInfo.osrYieldpointBlock;
      } else {
        meth = VM_Entrypoints.optThreadSwitchFromOsrOptMethod;
      }
    }

    // Not found.  create new basic block holding the requested yieldpoint
    // method
    OPT_BasicBlock result = new OPT_BasicBlock(-1, null, ir.cfg);
    ir.cfg.addLastInCodeOrder(result);
    OPT_Register JTOC = phys.getJTOC();
    OPT_Register CTR = phys.getCTR();
    Offset offset = meth.getOffset();
    if (OPT_Bits.fits(offset, 16)) {
      result.appendInstruction(MIR_Load.create(PPC_LAddr, A(zero), A(JTOC), IC(OPT_Bits.PPCMaskLower16(offset))));
    } else {
      if (VM.VerifyAssertions) VM._assert(OPT_Bits.fits(offset, 32)); //not implemented
      result.appendInstruction(MIR_Binary.create(PPC_ADDIS, A(zero), A(JTOC),
                                                 IC(OPT_Bits.PPCMaskUpper16(offset))));
      result.appendInstruction(MIR_Load.create(PPC_LAddr, A(zero), A(zero), IC(OPT_Bits.PPCMaskLower16(offset))));
    }
    result.appendInstruction(MIR_Move.create(PPC_MTSPR, A(CTR), A(zero)));
    result.appendInstruction(MIR_Branch.create(PPC_BCTR));

    // cache the create block and then return it
    if (whereFrom == VM_Thread.PROLOGUE) {
      ir.MIRInfo.prologueYieldpointBlock = result;
    } else if (whereFrom == VM_Thread.BACKEDGE) {
      ir.MIRInfo.backedgeYieldpointBlock = result;
    } else if (whereFrom == VM_Thread.EPILOGUE) {
      ir.MIRInfo.epilogueYieldpointBlock = result;
    } else if (whereFrom == VM_Thread.OSROPT) {
      ir.MIRInfo.osrYieldpointBlock = result;
    }

    return result;
  }
}



