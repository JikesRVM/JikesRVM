/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class contains PowerPC Calling conventions.
 * The two public methods are:
 * <ul> 
 *  <li> expandCallingConventions(OPT_IR) which is called by 
 *      the register allocator immediately before allocation to make 
 *      manifest the use of registers by the calling convention.
 *  <li> expandSysCall(OPT_Instruction, OPT_IR) which is called to 
 *      expand a SYSCALL HIR instruction into the appropriate 
 *      sequence of LIR instructions.
 *  </ul>
 *
 * @author Mauricio J. Serrano
 * @author Dave Grove
 * @author Stephen Fink
 */
final class OPT_CallingConvention extends OPT_IRTools 
implements OPT_PhysicalRegisterConstants {

  /**
   * Expand calls, returns, and add initialize code for arguments/parms.
   * @param ir
   */
  public static void expandCallingConventions(OPT_IR ir) {
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder(); 
         inst != null; inst = inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }
    prologueExpand(ir);
  }


  /**
   * This is just called for instructions that were added by 
   * instrumentation during register allocation
   */
  public static void expandCallingConventionsForInstrumentation(OPT_IR ir, 
                                                                OPT_Instruction 
                                                                from, 
                                                                OPT_Instruction 
                                                                to) {
    for (OPT_Instruction inst = from; inst != to; 
         inst = inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }
  }

  /**
   * Calling convention to implement calls to 
   * native (C) routines using the AIX linkage conventions
   */
  public static void expandSysCall(OPT_Instruction s, OPT_IR ir) {
    OPT_RegisterOperand ip = null;
    OPT_RegisterOperand t1 = 
      OPT_ConvertToLowLevelIR.getStatic(s, ir, VM_Entrypoints.the_boot_recordField);
    OPT_RegisterOperand toc = OPT_ConvertToLowLevelIR.getField(s, ir, t1, VM_Entrypoints.sysTOCField);
    if (Call.getMethod(s) != null) {
      OPT_MethodOperand nat = Call.getClearMethod(s);
      VM_Field target = null;
      target = nat.getMemberRef().asFieldReference().resolve();
      ip = OPT_ConvertToLowLevelIR.getField(s, ir, t1.copyRO(), target);
    } else {
      ip = (OPT_RegisterOperand)Call.getClearAddress(s);
    }

    /* compute the parameter space */
    int numberParams = Call.getNumberOfParams(s);
    int parameterWords = 0;
    for (int i = 0; i < numberParams; i++) {
      parameterWords++;
      OPT_Operand op = Call.getParam(s, i);
      if (op instanceof OPT_RegisterOperand) {
        OPT_RegisterOperand reg = (OPT_RegisterOperand)op;
        if (reg.type.isLongType() || reg.type.isDoubleType())
          parameterWords++;
      } else if ((op instanceof OPT_LongConstantOperand) || 
                 (op instanceof OPT_DoubleConstantOperand))
        parameterWords++;
    }
    // see PowerPC Compiler Writer's Guide, pp. 162
    ir.stackManager.allocateParameterSpace((6 + parameterWords)*4);
    // IMPORTANT WARNING: as the callee C routine may destroy the cmid field
    // (it is the saved CR field of the callee in C convention) 
    // we are restoring the methodID after a sysCall. 
    OPT_Instruction s2 = Store.create(INT_STORE, ir.regpool.makeJTOCOp(ir,s), 
                                      ir.regpool.makeFPOp(), 
                                      I(20), null);         // TODO: valid location?
    s.insertBack(s2);
    s.insertBack(Move.create(INT_MOVE, ir.regpool.makeJTOCOp(ir,s), toc));
    Call.mutate0(s, SYSCALL, Call.getClearResult(s), ip, null);
    s2 = Load.create(INT_LOAD, ir.regpool.makeJTOCOp(ir,s), ir.regpool.makeFPOp(),
                     I(20), null);         // TODO: valid location?
    s.insertFront(s2);
    OPT_RegisterOperand temp = ir.regpool.makeTempInt();
    s2 = Move.create(INT_MOVE, temp, I(ir.compiledMethod.getId()));
    OPT_Instruction s3 = Store.create(INT_STORE, temp.copy(), 
                                      ir.regpool.makeFPOp(), 
                                      I(STACKFRAME_METHOD_ID_OFFSET), null);  // TODO: valid location?
    s.insertFront(s3);
    s.insertFront(s2);
  }


  /////////////////////
  // Implementation
  /////////////////////

  /**
   * Expand the prologue instruction to make calling convention explicit.
   */
  private static void prologueExpand(OPT_IR ir) {
    
    // set up register lists for dead code elimination.
    boolean useDU = ir.options.getOptLevel() >= 1;
    if (useDU) {
      OPT_DefUse.computeDU(ir);
    }

    OPT_Instruction prologueInstr = 
      ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(prologueInstr.operator == IR_PROLOGUE);
    OPT_Instruction start = prologueInstr.nextInstructionInCodeOrder();

    int int_index = 0;
    int double_index = 0;
    int spilledArgumentCounter = 
      (-256 - VM_Constants.STACKFRAME_HEADER_SIZE) >> 2;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    for (OPT_OperandEnumeration symParams = prologueInstr.getDefs();
         symParams.hasMoreElements(); ) {
      OPT_RegisterOperand symParamOp = (OPT_RegisterOperand)symParams.next();
      OPT_Register symParam = symParamOp.register;
      VM_TypeReference t = symParamOp.type;
      if (t.isFloatType()) {
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {

          if (double_index < NUMBER_DOUBLE_PARAM) {
            OPT_Register param = phys.get(FIRST_DOUBLE_PARAM + (double_index));
            start.insertBack(MIR_Move.create(PPC_FMR, F(symParam), F(param)));
          } else {                  // spilled parameter
            start.insertBack(MIR_Load.create(PPC_LFS, F(symParam), 
                                             R(FP), 
                                             I(spilledArgumentCounter << 2)));
            spilledArgumentCounter--;
          }
        }
        double_index++;
      } else if (t.isDoubleType()) {
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {
          if (double_index < NUMBER_DOUBLE_PARAM) {
            OPT_Register param = phys.get(FIRST_DOUBLE_PARAM + (double_index));
            start.insertBack(MIR_Move.create(PPC_FMR, D(symParam), D(param)));
          } else {                  // spilled parameter
            start.insertBack(MIR_Load.create(PPC_LFD, D(symParam), 
                                             R(FP), 
                                             I(spilledArgumentCounter << 2)));
            spilledArgumentCounter -= 2;
          }
        }
        double_index++;
      } else { // t is object, 1/2 of a long, int, short, char, byte, or boolean
        // if optimizing, skip dead parameters
        // SJF: This optimization current breaks the paranoid sanity test.
        // Why? TODO: figure this out and remove the 'true' case below
        if (true || !useDU || symParam.useList != null) {
          if (int_index < NUMBER_INT_PARAM) {
            OPT_Register param = phys.get(FIRST_INT_PARAM + (int_index));
            start.insertBack(MIR_Move.create(PPC_MOVE, 
                                             new OPT_RegisterOperand
                                             (symParam, t),
                                             R(param)));
          } else {                  // spilled parameter
            start.insertBack(MIR_Load.create(PPC_LWZ, new OPT_RegisterOperand(symParam, t), 
                                             R(FP), I(spilledArgumentCounter << 2)));
            spilledArgumentCounter--;
          }
        }
        int_index++;
      }
    }

    // Now that we've made the calling convention explicit in the prologue,
    // set IR_PROLOGUE to have no defs.
    prologueInstr.replace(Prologue.create(IR_PROLOGUE, 0));
  }


  /**
   * Expand the call as appropriate
   * @param s the call instruction
   * @param ir the ir
   */
  private static void callExpand(OPT_Instruction s, OPT_IR ir) {
    int NumberParams = MIR_Call.getNumberOfParams(s);
    int int_index = 0;          // points to the first integer volatile
    int double_index = 0;       // poinst to the first f.p.    volatile
    int callSpillLoc = STACKFRAME_HEADER_SIZE;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Instruction prev = s.prevInstructionInCodeOrder();
    OPT_Register FP = phys.getFP();
    OPT_Register JTOC = phys.getJTOC();
    boolean isSysCall = ir.stackManager.isSysCall(s);
    boolean firstLongHalf = false;

    // (1) Expand parameters
    for (int opNum = 0; opNum < NumberParams; opNum++) {
      OPT_Operand param = MIR_Call.getClearParam(s, opNum);
      OPT_RegisterOperand Reg = (OPT_RegisterOperand)param;
      // as part of getting into MIR, we make sure all params are in registers.
      OPT_Register reg = Reg.register;
      if (Reg.type.isFloatType()) {
        if (double_index < NUMBER_DOUBLE_PARAM) {       // register copy
          OPT_Register real = phys.get(FIRST_DOUBLE_PARAM + (double_index++));
          s.insertBack(MIR_Move.create(PPC_FMR, F(real), Reg));
          Reg = F(real);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          p.insertBack(MIR_Store.create(PPC_STFS, F(reg), R(FP), I(callSpillLoc)));
          callSpillLoc += 4;
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      } else if (Reg.type.isDoubleType()) {
        if (double_index < NUMBER_DOUBLE_PARAM) {     // register copy
          OPT_Register real = phys.get(FIRST_DOUBLE_PARAM + (double_index++));
          s.insertBack(MIR_Move.create(PPC_FMR, D(real), Reg));
          Reg = D(real);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          p.insertBack(MIR_Store.create(PPC_STFD, D(reg), R(FP), I(callSpillLoc)));
          callSpillLoc += 8;
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      } else {                    // IntType (or half of long) or reference
        //-#if RVM_FOR_LINUX
        /* NOTE: following adjustment is not stated in SVR4 ABI, but 
         * was implemented in GCC.
         */
        if (isSysCall && Reg.type.isLongType()) {
          if (firstLongHalf)
            firstLongHalf = false;
          else {
            int true_index = FIRST_INT_PARAM + int_index;
            int_index += (true_index + 1) & 0x01; // if gpr is even, gpr += 1
            firstLongHalf = true;
          }
        }
        //-#endif
        if (int_index < NUMBER_INT_PARAM) {             // register copy
          OPT_Register real = phys.get(FIRST_INT_PARAM + (int_index++));
          OPT_RegisterOperand Real = new OPT_RegisterOperand(real, Reg.type);
          s.insertBack(MIR_Move.create(PPC_MOVE, Real, Reg));
          Reg = new OPT_RegisterOperand(real, Reg.type);
          // Record that the call now has a use of the real reg
          // This is to ensure liveness is correct
          MIR_Call.setParam(s, opNum, Reg);
        } else {                  // spill to memory
          OPT_Instruction p = prev.nextInstructionInCodeOrder();
          p.insertBack(MIR_Store.create(PPC_STW, 
                                        new OPT_RegisterOperand(reg, Reg.type), 
                                        R(FP), I(callSpillLoc)));
          callSpillLoc += 4;
          // We don't have uses of the heap at MIR, so null it out
          MIR_Call.setParam(s, opNum, null);
        }
      }
    }
    // If we needed to pass arguments on the stack, 
    // then make sure we have a big enough stack 
    if (callSpillLoc != STACKFRAME_HEADER_SIZE)
      ir.stackManager.allocateParameterSpace(callSpillLoc);
    // (2) expand result
    OPT_RegisterOperand callResult = null;
    OPT_Instruction lastCallSeqInstr = s;
    if (MIR_Call.hasResult2(s)) {
      OPT_RegisterOperand result2 = MIR_Call.getClearResult2(s);
      OPT_RegisterOperand physical = new OPT_RegisterOperand(phys.get
                                                             (FIRST_INT_RETURN 
                                                              + 1), 
                                                             result2.type);
      OPT_Instruction tmp = MIR_Move.create(PPC_MOVE, result2, physical);
      lastCallSeqInstr.insertFront(tmp);
      lastCallSeqInstr = tmp;
      MIR_Call.setResult2(s, null);
    }
    if (MIR_Call.hasResult(s)) {
      OPT_RegisterOperand result1 = MIR_Call.getClearResult(s);
      callResult = result1;
      if (result1.type.isFloatType() || result1.type.isDoubleType()) {
        OPT_RegisterOperand physical = new 
          OPT_RegisterOperand(phys.get(FIRST_DOUBLE_RETURN), 
                              result1.type);
        OPT_Instruction tmp = MIR_Move.create(PPC_FMR, result1, physical);
        lastCallSeqInstr.insertFront(tmp);
        lastCallSeqInstr = tmp;
        MIR_Call.setResult(s, null);
      } else {
        OPT_RegisterOperand physical = new 
          OPT_RegisterOperand(phys.get(FIRST_INT_RETURN), 
                              result1.type);
        OPT_Instruction tmp = MIR_Move.create(PPC_MOVE, result1, physical);
        lastCallSeqInstr.insertFront(tmp);
        lastCallSeqInstr = tmp;
        MIR_Call.setResult(s, null);
      }
    }
  }

  /**
   * Expand return statements.
   * @param s the return instruction
   * @param ir the ir
   */
  private static void returnExpand (OPT_Instruction s, OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (MIR_Return.hasVal(s)) {
      OPT_RegisterOperand symb1 = MIR_Return.getClearVal(s);
      OPT_RegisterOperand phys1;
      if (symb1.type.isFloatType() || symb1.type.isDoubleType()) {
        phys1 = D(phys.get(FIRST_DOUBLE_RETURN));
        s.insertBack(MIR_Move.create(PPC_FMR, phys1, symb1));
      } else {
        phys1 = new OPT_RegisterOperand(phys.get(FIRST_INT_RETURN), symb1.type);
        s.insertBack(MIR_Move.create(PPC_MOVE, phys1, symb1));
      }
      MIR_Return.setVal(s, phys1.copyD2U());
    }
    if (MIR_Return.hasVal2(s)) {
      OPT_RegisterOperand symb2 = MIR_Return.getClearVal2(s);
      OPT_RegisterOperand phys2 = R(phys.get(FIRST_INT_RETURN + 1));
      s.insertBack(MIR_Move.create(PPC_MOVE, phys2, symb2));
      MIR_Return.setVal2(s, phys2.copyD2U());
    }
  }
}
