/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Final acts of MIR expansion for the PowerPC architecture.
 * Things that are expanded here (immediately before final assembly)
 * should only be those sequences that cannot be expanded earlier
 * due to difficulty in keeping optimizations from interfering with them.
 *
 * @author Mauricio J. Serrano
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Igor Pechtchanski
 */
abstract class OPT_FinalMIRExpansion extends OPT_IRTools
  implements VM_BytecodeConstants {

  /**
   * @param ir the IR to expand
   * @return upperbound on number of machine code instructions 
   * that will be generated for this IR
   */
  public final static int expand (OPT_IR ir) {
    int instructionCount = 0;
    int conditionalBranchCount = 0;
    int machinecodeLength = 0;
    boolean frameCreated = false;

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
         p != null; 
         p = p.nextInstructionInCodeOrder()) {
      p.setmcOffset(-1);
      p.scratchObject = null;
      switch (p.getOpcode()) {
      case MIR_LOWTABLESWITCH_opcode:
        {
          
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
          p.insertBack(MIR_Move.create(PPC_MFSPR, R(temp), R(phys.getLR())));
          p.insertBack(MIR_Binary.create(PPC_SLWI, R(regI), R(regI), I(2)));
          p.insertBack(MIR_LoadUpdate.create(PPC_LWZUX, R(temp), R(regI), R(temp)));
          p.insertBack(MIR_Binary.create(PPC_ADD, R(regI), R(regI), R(temp)));
          p.insertBack(MIR_Move.create(PPC_MTSPR, R(phys.getCTR()), R(regI)));
          MIR_Branch.mutate(p, PPC_BCTR);
          instructionCount += NumTargets + 7;
        }
        break;
      case PPC_BCOND2_opcode:
        {
          OPT_RegisterOperand cond = MIR_CondBranch2.getClearValue(p);
          p.insertFront(MIR_CondBranch.create(PPC_BCOND, cond.copyU2U(), 
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
      case PPC_BLRL_opcode:case PPC_BCTRL_opcode:
        {
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
                                       R(phys.getGPR(LAST_SCRATCH_GPR)), 
                                       I(signatureId)); p.insertBack(s);
                  instructionCount++;
                } else {
                  s = MIR_Unary.create(PPC_LDIS, 
                                       R(phys.getGPR(LAST_SCRATCH_GPR)), 
                                       I(OPT_Bits.PPCMaskUpper16(signatureId)));
                  p.insertBack(s);
                  s = MIR_Binary.create(PPC_ADDI, 
                                        R(phys.getGPR(LAST_SCRATCH_GPR)), 
                                        R(phys.getGPR(LAST_SCRATCH_GPR)), 
                                        I(OPT_Bits.PPCMaskLower16(signatureId)));
                  p.insertBack(s);
                  instructionCount += 2;
                }
              }
            }
          }
          instructionCount++;
        }
        break;
      case LABEL_opcode:case BBEND_opcode:case UNINT_BEGIN_opcode:
      case UNINT_END_opcode:
        // These generate no code, so don't count them.
        break;
      case RESOLVE_opcode:
        {
          OPT_Register zero = phys.getGPR(0);
          OPT_Register JTOC = phys.getJTOC();
          OPT_Register CTR = phys.getCTR();
          if (VM.VerifyAssertions) 
            VM._assert(p.bcIndex >= 0 && p.position != null);
          int offset = VM_Entrypoints.optResolveMethod.getOffset();
          if (OPT_Bits.fits(offset, 16)) {
            p.insertBefore(MIR_Load.create(PPC_LWZ, R(zero), R(JTOC), I(offset)));
          } else {
            p.insertBefore(MIR_Unary.create(PPC_LDIS, R(zero), 
                                            I(offset >>> 16)));
            p.insertBefore(MIR_Binary.create(PPC_ORI, R(zero), R(zero), 
                                             I(offset & 0xffff)));
            p.insertBefore(MIR_Load.create(PPC_LWZX, R(zero), R(JTOC), R(zero)));
            instructionCount += 2;
          }
          p.insertBefore(MIR_Move.create(PPC_MTSPR, R(CTR), R(zero)));
          instructionCount += 3;
          // Because the GC Map code holds a reference to the original
          // instruction, it is important that we mutate the last instruction
          // because this will be the GC point.
          MIR_Call.mutate0(p, PPC_BCTRL, null, null);
          break;
        }
        case YIELDPOINT_PROLOGUE_opcode:
          {
            OPT_Register TSR = phys.getTSR();
            OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                        VM_Thread.PROLOGUE);                    
            // Because the GC Map code holds a reference to the original
            // instruction, it is important that we mutate the last instruction
            // because this will be the GC point.
            MIR_CondCall.mutate0(p, PPC_BCL, null, null, R(TSR), 
                                 OPT_PowerPCConditionOperand.THREAD_SWITCH(), 
                                 yieldpoint.makeJumpTarget());
            conditionalBranchCount++;
          }
          break;
        case YIELDPOINT_BACKEDGE_opcode:
          {
            OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                        VM_Thread.BACKEDGE);                    
            OPT_Register zero = phys.getGPR(0);
            OPT_Register TSR = phys.getTSR();
            OPT_Register PR = phys.getPR();
            p.insertBefore(MIR_Load.create(PPC_LWZ, R(zero), 
                                           R(PR), 
                                           I(VM_Entrypoints.threadSwitchRequestedField.getOffset())));
            p.insertBefore(MIR_Binary.create(PPC_CMPI, R(TSR), R(zero), I(0)));
            instructionCount += 2;
            // Because the GC Map code holds a reference to the original
            // instruction, it is important that we mutate the last instruction
            // because this will be the GC point.
            MIR_CondCall.mutate0(p, PPC_BCL, null, null, R(TSR), 
                                 OPT_PowerPCConditionOperand.THREAD_SWITCH(), 
                                 yieldpoint.makeJumpTarget());
            conditionalBranchCount++;
          }
          break;
        case YIELDPOINT_EPILOGUE_opcode:
          {
            OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                        VM_Thread.EPILOGUE);                    
            OPT_Register zero = phys.getGPR(0);
            OPT_Register TSR = phys.getTSR();
            OPT_Register PR = phys.getPR();
            p.insertBefore(MIR_Load.create(PPC_LWZ, R(zero), 
                                           R(PR), 
                                           I(VM_Entrypoints.threadSwitchRequestedField.getOffset())));
            p.insertBefore(MIR_Binary.create(PPC_CMPI, R(TSR), R(zero), I(0)));
            instructionCount += 2;
            // Because the GC Map code holds a reference to the original
            // instruction, it is important that we mutate the last instruction
            // because this will be the GC point.
            MIR_CondCall.mutate0(p, PPC_BCL, null, null, R(TSR), 
                OPT_PowerPCConditionOperand.THREAD_SWITCH(), 
                yieldpoint.makeJumpTarget());
            conditionalBranchCount++;
          }
          break;
        //-#if RVM_WITH_OSR
        case YIELDPOINT_OSR_opcode:
          {
            // unconditionally branch to yield point.
            OPT_BasicBlock yieldpoint = findOrCreateYieldpointBlock(ir,
                                        VM_Thread.OSROPT);
            // Because the GC Map code holds a reference to the original
            // instruction, it is important that we mutate the last instruction
            // because this will be the GC point.
            MIR_Call.mutate0(p, PPC_BL, null, null, 
                             yieldpoint.makeJumpTarget());
          }
          instructionCount++;
          break;
        //-#endif
        case IR_ENDPROLOGUE_opcode:
          {
            // Remember where the end of prologue is for debugger
            OPT_Instruction next = p.nextInstructionInCodeOrder();
            ir.MIRInfo.instAfterPrologue = next;
            p.remove();
            p = next.prevInstructionInCodeOrder();
          }
          break;

      default:
        if (p.operator().isConditionalBranch())
          conditionalBranchCount++; 
        else 
          instructionCount++;
        break;
      }
    }
    
    // this is conservative but pretty close, especially for 
    // reasonably sized methods 
    if ((instructionCount + conditionalBranchCount) > OPT_Assembler.MAX_COND_DISPL)
      machinecodeLength = instructionCount + 2*conditionalBranchCount; 
    else 
      machinecodeLength = instructionCount + conditionalBranchCount;

    if ((machinecodeLength & ~OPT_Assembler.MAX_24_BITS) != 0)
      throw new OPT_OptimizingCompilerException("CodeGen", 
                                                "method too large to compile:", 
                                                OPT_Assembler.MAX_24_BITS);
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
  final static OPT_BasicBlock findOrCreateYieldpointBlock(OPT_IR ir, 
                                                          int whereFrom) {
    VM_Method meth = null;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register zero = phys.getGPR(0);
    
    // first see if the requested block exists. If not, set up some
    // state for creating the block
    if (whereFrom == VM_Thread.PROLOGUE) {
      if (ir.MIRInfo.prologueYieldpointBlock != null) 
        return ir.MIRInfo.prologueYieldpointBlock;
      else 
        meth = VM_Entrypoints.optThreadSwitchFromPrologueMethod;
    } else if (whereFrom == VM_Thread.BACKEDGE) {
      if (ir.MIRInfo.backedgeYieldpointBlock != null) 
        return ir.MIRInfo.backedgeYieldpointBlock;
      else 
        meth = VM_Entrypoints.optThreadSwitchFromBackedgeMethod;
    } else if (whereFrom == VM_Thread.EPILOGUE) {
      if (ir.MIRInfo.epilogueYieldpointBlock != null) 
        return ir.MIRInfo.epilogueYieldpointBlock;
      else 
        meth = VM_Entrypoints.optThreadSwitchFromEpilogueMethod;
    }
    //-#if RVM_WITH_OSR
    else if (whereFrom == VM_Thread.OSROPT) {
      if (ir.MIRInfo.osrYieldpointBlock != null)
        return ir.MIRInfo.osrYieldpointBlock;
      else
        meth = VM_Entrypoints.optThreadSwitchFromOsrOptMethod;
    }
    //-#endif 

    // Not found.  create new basic block holding the requested yieldpoint
    // method
    OPT_BasicBlock result = new OPT_BasicBlock(-1, null, ir.cfg);
    ir.cfg.addLastInCodeOrder(result);
    OPT_Register JTOC = phys.getJTOC();
    OPT_Register CTR = phys.getCTR();
    int offset = meth.getOffset();
    if (OPT_Bits.fits(offset, 16)) {
      result.appendInstruction(MIR_Load.create(PPC_LWZ, R(zero), R(JTOC), I(offset)));
    } else {
      result.appendInstruction(MIR_Unary.create(PPC_LDIS, 
                               R(zero), I(offset >>> 16)));
      result.appendInstruction(MIR_Binary.create(PPC_ORI, 
                               R(zero), R(zero), I(offset & 0xffff)));
      result.appendInstruction(MIR_Load.create(PPC_LWZX, R(zero), R(JTOC), R(zero)));
    }
    result.appendInstruction(MIR_Move.create(PPC_MTSPR, R(CTR), R(zero)));
    result.appendInstruction(MIR_Branch.create(PPC_BCTR));

    // cache the create block and then return it
    if (whereFrom == VM_Thread.PROLOGUE)      
      ir.MIRInfo.prologueYieldpointBlock = result;
    else if (whereFrom == VM_Thread.BACKEDGE) 
      ir.MIRInfo.backedgeYieldpointBlock = result;
    else if (whereFrom == VM_Thread.EPILOGUE) 
      ir.MIRInfo.epilogueYieldpointBlock = result;
    //-#if RVM_WITH_OSR
    else if (whereFrom == VM_Thread.OSROPT)
      ir.MIRInfo.osrYieldpointBlock = result;
    //-#endif 

    return result;
  }
}



