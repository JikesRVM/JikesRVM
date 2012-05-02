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
package org.jikesrvm.compilers.opt.mir2mc.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
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
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LABEL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MIR_LOWTABLESWITCH_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_ADDI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_ADDIS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCL;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCOND;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCOND2_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCTR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCTRL;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCTRL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BL;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BLRL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_CMPI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_DATA_LABEL;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LAddr;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LDI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LDIS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LInt;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LIntUX;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MFSPR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MTSPR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_SLWI;
import static org.jikesrvm.compilers.opt.ir.Operators.RESOLVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_BEGIN_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_END_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_BACKEDGE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_EPILOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_PROLOGUE_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCConditionOperand;
import org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.LAST_SCRATCH_GPR;
import org.jikesrvm.compilers.opt.util.Bits;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Offset;

/**
 * Final acts of MIR expansion for the PowerPC architecture.
 * Things that are expanded here (immediately before final assembly)
 * should only be those sequences that cannot be expanded earlier
 * due to difficulty in keeping optimizations from interfering with them.
 */
public abstract class FinalMIRExpansion extends IRTools {

  /**
   * @param ir the IR to expand
   * @return upperbound on number of machine code instructions
   * that will be generated for this IR
   */
  public static int expand(IR ir) {
    int instructionCount = 0;
    int conditionalBranchCount = 0;
    int machinecodeLength = 0;

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Instruction p = ir.firstInstructionInCodeOrder(); p != null; p = p.nextInstructionInCodeOrder()) {
      p.setmcOffset(-1);
      p.scratchObject = null;
      switch (p.getOpcode()) {
        case MIR_LOWTABLESWITCH_opcode: {

          BasicBlock tableBlock = p.getBasicBlock();
          BasicBlock nextBlock = tableBlock.splitNodeWithLinksAt(p.prevInstructionInCodeOrder(), ir);
          nextBlock.firstInstruction().setmcOffset(-1);
          Register regI = MIR_LowTableSwitch.getIndex(p).getRegister();
          int NumTargets = MIR_LowTableSwitch.getNumberOfTargets(p);
          tableBlock.appendInstruction(MIR_Call.create0(PPC_BL, null, null, nextBlock.makeJumpTarget()));

          for (int i = 0; i < NumTargets; i++) {
            tableBlock.appendInstruction(MIR_DataLabel.create(PPC_DATA_LABEL, MIR_LowTableSwitch.getClearTarget(p, i)));
          }
          Register temp = phys.getGPR(0);
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
          RegisterOperand cond = MIR_CondBranch2.getClearValue(p);
          p.insertAfter(MIR_CondBranch.create(PPC_BCOND,
                                              cond.copyU2U(),
                                              MIR_CondBranch2.getClearCond2(p),
                                              MIR_CondBranch2.getClearTarget2(p),
                                              MIR_CondBranch2.getClearBranchProfile2(p)));
          MIR_CondBranch.mutate(p,
                                PPC_BCOND,
                                cond,
                                MIR_CondBranch2.getClearCond1(p),
                                MIR_CondBranch2.getClearTarget1(p),
                                MIR_CondBranch2.getClearBranchProfile1(p));
          conditionalBranchCount++;
        }
        break;
        case PPC_BLRL_opcode:
        case PPC_BCTRL_opcode: {
          // indirect calls.  Second part of fast interface invoker expansion
          // See also ConvertToLowlevelIR.java
          if (VM.BuildForIMTInterfaceInvocation) {
            if (MIR_Call.hasMethod(p)) {
              MethodOperand mo = MIR_Call.getMethod(p);
              if (mo.isInterface()) {
                InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(mo.getMemberRef());
                int signatureId = sig.getId();
                Instruction s;
                if (Bits.fits(signatureId, 16)) {
                  s = MIR_Unary.create(PPC_LDI, I(phys.getGPR(LAST_SCRATCH_GPR)), IC(signatureId));
                  p.insertBefore(s);
                  instructionCount++;
                } else {
                  s =
                      MIR_Unary.create(PPC_LDIS,
                                       I(phys.getGPR(LAST_SCRATCH_GPR)),
                                       IC(Bits.PPCMaskUpper16(signatureId)));
                  p.insertBefore(s);
                  s =
                      MIR_Binary.create(PPC_ADDI,
                                        I(phys.getGPR(LAST_SCRATCH_GPR)),
                                        I(phys.getGPR(LAST_SCRATCH_GPR)),
                                        IC(Bits.PPCMaskLower16(signatureId)));
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
          Register zero = phys.getGPR(0);
          Register JTOC = phys.getJTOC();
          Register CTR = phys.getCTR();
          if (VM.VerifyAssertions) {
            VM._assert(p.bcIndex >= 0 && p.position != null);
          }
          Offset offset = Entrypoints.optResolveMethod.getOffset();
          if (Bits.fits(offset, 16)) {
            p.insertBefore(MIR_Load.create(PPC_LAddr, A(zero), A(JTOC), IC(Bits.PPCMaskLower16(offset))));
          } else {
            if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 32)); //not implemented
            p.insertBefore(MIR_Binary.create(PPC_ADDIS, A(zero), A(JTOC), IC(Bits.PPCMaskUpper16(offset))));
            p.insertBefore(MIR_Load.create(PPC_LAddr, A(zero), A(zero), IC(Bits.PPCMaskLower16(offset))));
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
          Register TSR = phys.getTSR();
          BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir, RVMThread.PROLOGUE);
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p,
                               PPC_BCL,
                               null,
                               null,
                               I(TSR),
                               PowerPCConditionOperand.NOT_EQUAL(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_BACKEDGE_opcode: {
          BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir, RVMThread.BACKEDGE);
          Register zero = phys.getGPR(0);
          Register TSR = phys.getTSR();
          Register TR = phys.getTR();
          Offset offset = Entrypoints.takeYieldpointField.getOffset();
          if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
          p.insertBefore(MIR_Load.create(PPC_LInt, I(zero), A(TR), IC(Bits.PPCMaskLower16(offset))));
          p.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(zero), IC(0)));
          instructionCount += 2;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p,
                               PPC_BCL,
                               null,
                               null,
                               I(TSR),
                               PowerPCConditionOperand.GREATER(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_EPILOGUE_opcode: {
          BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir, RVMThread.EPILOGUE);
          Register zero = phys.getGPR(0);
          Register TSR = phys.getTSR();
          Register TR = phys.getTR();
          Offset offset = Entrypoints.takeYieldpointField.getOffset();
          if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
          p.insertBefore(MIR_Load.create(PPC_LInt, I(zero), A(TR), IC(Bits.PPCMaskLower16(offset))));
          p.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(zero), IC(0)));
          instructionCount += 2;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_CondCall.mutate0(p,
                               PPC_BCL,
                               null,
                               null,
                               I(TSR),
                               PowerPCConditionOperand.NOT_EQUAL(),
                               yieldpoint.makeJumpTarget());
          p.getBasicBlock().insertOut(yieldpoint);
          conditionalBranchCount++;
        }
        break;
        case YIELDPOINT_OSR_opcode: {
          // unconditionally branch to yield point.
          BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir, RVMThread.OSROPT);
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_Call.mutate0(p, PPC_BL, null, null, yieldpoint.makeJumpTarget());
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
    if ((instructionCount + conditionalBranchCount) > AssemblerOpt.MAX_COND_DISPL) {
      machinecodeLength = instructionCount + 2 * conditionalBranchCount;
    } else {
      machinecodeLength = instructionCount + conditionalBranchCount;
    }

    if ((machinecodeLength & ~AssemblerOpt.MAX_24_BITS) != 0) {
      throw new OptimizingCompilerException("CodeGen", "method too large to compile:", AssemblerOpt.MAX_24_BITS);
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
  static BasicBlock findOrCreateYieldpointBlock(IR ir, int whereFrom) {
    RVMMethod meth = null;
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register zero = phys.getGPR(0);

    // first see if the requested block exists. If not, set up some
    // state for creating the block
    if (whereFrom == RVMThread.PROLOGUE) {
      if (ir.MIRInfo.prologueYieldpointBlock != null) {
        return ir.MIRInfo.prologueYieldpointBlock;
      } else {
        meth = Entrypoints.optThreadSwitchFromPrologueMethod;
      }
    } else if (whereFrom == RVMThread.BACKEDGE) {
      if (ir.MIRInfo.backedgeYieldpointBlock != null) {
        return ir.MIRInfo.backedgeYieldpointBlock;
      } else {
        meth = Entrypoints.optThreadSwitchFromBackedgeMethod;
      }
    } else if (whereFrom == RVMThread.EPILOGUE) {
      if (ir.MIRInfo.epilogueYieldpointBlock != null) {
        return ir.MIRInfo.epilogueYieldpointBlock;
      } else {
        meth = Entrypoints.optThreadSwitchFromEpilogueMethod;
      }
    } else if (whereFrom == RVMThread.OSROPT) {
      if (ir.MIRInfo.osrYieldpointBlock != null) {
        return ir.MIRInfo.osrYieldpointBlock;
      } else {
        meth = Entrypoints.optThreadSwitchFromOsrOptMethod;
      }
    }

    // Not found.  create new basic block holding the requested yieldpoint
    // method
    BasicBlock result = new BasicBlock(-1, null, ir.cfg);
    ir.cfg.addLastInCodeOrder(result);
    Register JTOC = phys.getJTOC();
    Register CTR = phys.getCTR();
    Offset offset = meth.getOffset();
    if (Bits.fits(offset, 16)) {
      result.appendInstruction(MIR_Load.create(PPC_LAddr, A(zero), A(JTOC), IC(Bits.PPCMaskLower16(offset))));
    } else {
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 32)); //not implemented
      result.appendInstruction(MIR_Binary.create(PPC_ADDIS, A(zero), A(JTOC), IC(Bits.PPCMaskUpper16(offset))));
      result.appendInstruction(MIR_Load.create(PPC_LAddr, A(zero), A(zero), IC(Bits.PPCMaskLower16(offset))));
    }
    result.appendInstruction(MIR_Move.create(PPC_MTSPR, A(CTR), A(zero)));
    result.appendInstruction(MIR_Branch.create(PPC_BCTR));

    // cache the create block and then return it
    if (whereFrom == RVMThread.PROLOGUE) {
      ir.MIRInfo.prologueYieldpointBlock = result;
    } else if (whereFrom == RVMThread.BACKEDGE) {
      ir.MIRInfo.backedgeYieldpointBlock = result;
    } else if (whereFrom == RVMThread.EPILOGUE) {
      ir.MIRInfo.epilogueYieldpointBlock = result;
    } else if (whereFrom == RVMThread.OSROPT) {
      ir.MIRInfo.osrYieldpointBlock = result;
    }

    return result;
  }
}



