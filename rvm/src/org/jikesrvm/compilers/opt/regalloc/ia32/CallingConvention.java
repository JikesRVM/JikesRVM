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

import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.LABEL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SYSCALL;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.ADVISE_ESP;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.ADVISE_ESP_opcode;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_CALL;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FCLEAR;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FSTP;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_JCC;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_JMP;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOVSS;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_PUSH;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_SYSCALL;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_TEST;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.REQUIRE_ESP;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.REQUIRE_ESP_opcode;
import static org.jikesrvm.ia32.ArchConstants.SSE2_FULL;
import static org.jikesrvm.ia32.RegisterConstants.JTOC_REGISTER;
import static org.jikesrvm.ia32.RegisterConstants.R13;
import static org.jikesrvm.ia32.RegisterConstants.R14;
import static org.jikesrvm.ia32.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import java.util.Enumeration;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Branch;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Call;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_CondBranch;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Move;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Return;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Test;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.operand.BranchProfileOperand;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StackLocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.util.Queue;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;

/**
 * This class contains IA32 calling conventions
 * The two public methods are:
 * <ol>
 *  <li>expandCallingConventions(IR) which is called by the
 *  register allocator immediately before allocation to make manifest the
 *  use of registers by the calling convention.
 *  <li>expandSysCall(Instruction, IR) which is called to expand
 *  a SYSCALL HIR instruction into the appropriate sequence of
 *  LIR instructions.
 * </ol>
 * <p>
 * TODO: Much of this code could still be factored out as
 * architecture-independent.
 */
public abstract class CallingConvention extends IRTools {

  /**
   * marker value for splitting blocks for x64 sys calls:
   * the block will be split at an require_esp
   * instruction that contains an int constant with that value
   */
  private static final int MARKER = Integer.MAX_VALUE;

  /**
   * Size of a word, in bytes
   */
  private static final int WORDSIZE = BYTES_IN_ADDRESS;
  private static final TypeReference wordType = VM.BuildFor32Addr ? TypeReference.Int :
     TypeReference.Long;

  /**
   * Expands calling conventions to make physical registers explicit in the
   * IR when required for calls, returns, and the prologue.
   *
   * @param ir the governing IR
   */
  public static void expandCallingConventions(IR ir) {
    Queue<Instruction> calls = new Queue<Instruction>();

    // expand each return instruction and remember calls for later.
    // Calls aren't processed immediately because x64 syscalls need code for stack
    // alignment. The stack alignment code requires duplicating the block with the
    // syscall and if we were to process calls immediately, an infinite loop would
    // occur because the newly copied syscalls would be processed, too.
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        calls.insert(inst);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }

    for (Instruction call : calls) {
      callExpand(call, ir);
    }

    // expand the prologue instruction
    expandPrologue(ir);

    if (VM.BuildFor64Addr && ir.stackManager.hasSysCall()) {
      // Recompute def-use data structures due to added blocks
      // for syscall expansion.
      DefUse.computeDU(ir);
      // Temporaries used for parameters might now be in two blocks.
      DefUse.recomputeSpansBasicBlock(ir);
      // The SSA property might be invalidated by two definitions
      // for a temporary used for a return value.
      DefUse.recomputeSSA(ir);
    }
  }

  /**
   * Expands the calling convention for a particular call instruction.
   *
   * @param call the call instruction
   * @param ir the IR that contains the call instruction
   */
  private static void callExpand(Instruction call, IR ir) {
    boolean isSysCall = call.operator() == IA32_SYSCALL;

    // 0. Handle the parameters
    int parameterBytes = isSysCall ? expandParametersToSysCall(call, ir) : expandParametersToCall(call, ir);

    // 1. Clear the floating-point stack if dirty.
    if (!SSE2_FULL) {
      if (!call.operator().isCallSaveVolatile()) {
        int FPRRegisterParams = countFPRParams(call);
        FPRRegisterParams = Math.min(FPRRegisterParams, PhysicalRegisterSet.getNumberOfFPRParams());
        call.insertBefore(MIR_UnaryNoRes.create(IA32_FCLEAR, IC(FPRRegisterParams)));
      }
    }

    // 2. Move the return value into a register
    expandResultOfCall(call, isSysCall, ir);

    // 3. If this is an interface invocation, set up the hidden parameter
    //    in the processor object to hold the interface signature id.
    if (VM.BuildForIMTInterfaceInvocation) {
      if (MIR_Call.hasMethod(call)) {
        MethodOperand mo = MIR_Call.getMethod(call);
        if (mo.isInterface()) {
          InterfaceMethodSignature sig = InterfaceMethodSignature.findOrCreate(mo.getMemberRef());
          MemoryOperand M =
              MemoryOperand.BD(ir.regpool.makeTROp(),
                                   ArchEntrypoints.hiddenSignatureIdField.getOffset(),
                                   (byte) WORDSIZE,
                                   null,
                                   null);
          call.insertBefore(MIR_Move.create(IA32_MOV, M, IC(sig.getId())));
        }
      }
    }

    // 4. ESP must be parameterBytes before call, will be at either parameterBytes
    //    or 0 afterwards depending on whether or it is an RVM method or a sysCall.
    Instruction requireESP = MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes));
    call.insertBefore(requireESP);
    Instruction adviseESP = MIR_UnaryNoRes.create(ADVISE_ESP, IC(isSysCall ? parameterBytes : 0));
    call.insertAfter(adviseESP);

    // 5. For x64 syscalls, the ABI requires that the stack pointer is divisible
    // by 16 at the call.
    if (VM.BuildFor64Addr && isSysCall) {
      alignStackForX64SysCall(call, ir, parameterBytes, requireESP, adviseESP);
    }
  }

  public static void alignStackForX64SysCall(Instruction call, IR ir,
      int parameterBytes, Instruction requireESP, Instruction adviseESP) {
    BasicBlock originalBlockForCall = call.getBasicBlock();

    // Search marker instruction
    Instruction currentInst = requireESP;
    do {
      currentInst = currentInst.prevInstructionInCodeOrder();
      if (currentInst.getOpcode() == REQUIRE_ESP_opcode &&
          MIR_UnaryNoRes.getVal(currentInst).asIntConstant().value == MARKER) {
        break;
      }
    } while (!(currentInst.getOpcode() == LABEL_opcode));

    if (VM.VerifyAssertions) VM._assert(currentInst != null);
    if (VM.VerifyAssertions) VM._assert(currentInst.getBasicBlock() == originalBlockForCall);
    if (VM.VerifyAssertions) VM._assert(currentInst.getOpcode() == REQUIRE_ESP_opcode);
    Instruction marker = currentInst;

    // Leave everything before the marker in the original block and move the rest to test block
    BasicBlock testBlock = originalBlockForCall.splitNodeWithLinksAt(marker, ir);
    // originalBlockForCall now has only code preceding the call,
    // testBlock has call code and subsequent code
    marker.remove();

    // make testBlock empty
    BasicBlock newBlockForCall = testBlock.splitNodeWithLinksAt(testBlock.firstInstruction(), ir);


    // testBlock is now empty, newBlockForCall has the call and subsequent code
    // Move everything after the call to a new block
    BasicBlock callBlockRest = newBlockForCall.splitNodeWithLinksAt(adviseESP, ir);

    // newBlockForCall now has the call and advise_esp / require_esp, subsequent code is in callBlockRest
    BasicBlock copiedBlock = newBlockForCall.copyWithoutLinks(ir);
    ir.cfg.addLastInCodeOrder(copiedBlock);
    copiedBlock.appendInstruction(MIR_Branch.create(IA32_JMP, callBlockRest.makeJumpTarget()));
    copiedBlock.recomputeNormalOut(ir);

    // Set up test block for checking stack alignment before the call
    Register espReg = ir.regpool.getPhysicalRegisterSet().asIA32().getESP();
    Instruction requireEspCheck = MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes));
    testBlock.appendInstruction(requireEspCheck);
    Instruction spTest = MIR_Test.create(IA32_TEST,
        new RegisterOperand(espReg, TypeReference.Word), IC(8));
    testBlock.appendInstruction(spTest);
    Instruction jcc = MIR_CondBranch.create(IA32_JCC,
                                            IA32ConditionOperand.NE(),
                                            copiedBlock.makeJumpTarget(),
                                            new BranchProfileOperand());
    testBlock.appendInstruction(jcc);
    testBlock.recomputeNormalOut(ir);

    // modify ESP in the copied block to ensure correct alignment
    // when the original alignment would be incorrect. That's accomplished
    // by adjusting the ESP downwards (i.e. towards the top of the stack, growing the stack).
    Enumeration<Instruction> copiedInsts = copiedBlock.forwardRealInstrEnumerator();
    while (copiedInsts.hasMoreElements()) {
      Instruction inst = copiedInsts.nextElement();
      if (inst.getOpcode() == REQUIRE_ESP_opcode ||
          inst.getOpcode() == ADVISE_ESP_opcode) {
        int val = MIR_UnaryNoRes.getVal(inst).asIntConstant().value;
        MIR_UnaryNoRes.setVal(inst, IC(val - WORDSIZE));
      }
    }
  }

  /**
   * Expands the calling convention for a particular return instruction.
   *
   * @param ret the return instruction
   * @param ir the IR that contains the return instruction
   */
  private static void returnExpand(Instruction ret, IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet().asIA32();

    if (MIR_Return.hasVal(ret)) {
      Operand symb1 = MIR_Return.getClearVal(ret);
      TypeReference type = symb1.getType();
      if (type.isFloatType() || type.isDoubleType()) {
        Register r = phys.getReturnFPR();
        RegisterOperand rOp = new RegisterOperand(r, type);
        if (SSE2_FULL) {
          if (type.isFloatType()) {
            ret.insertBefore(MIR_Move.create(IA32_MOVSS, rOp, symb1));
          } else {
            ret.insertBefore(MIR_Move.create(IA32_MOVSD, rOp, symb1));
          }
        } else {
          ret.insertBefore(MIR_Move.create(IA32_FMOV, rOp, symb1));
        }
        MIR_Return.setVal(ret, rOp.copyD2U());
      } else {
        Register r = phys.getFirstReturnGPR();
        RegisterOperand rOp = new RegisterOperand(r, type);
        ret.insertBefore(MIR_Move.create(IA32_MOV, rOp, symb1));
        MIR_Return.setVal(ret, rOp.copyD2U());
      }
    }

    if (MIR_Return.hasVal2(ret)) {
      if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
      Operand symb2 = MIR_Return.getClearVal2(ret);
      TypeReference type = symb2.getType();
      Register r = phys.getSecondReturnGPR();
      RegisterOperand rOp = new RegisterOperand(r, type);
      ret.insertBefore(MIR_Move.create(IA32_MOV, rOp, symb2));
      MIR_Return.setVal2(ret, rOp.copyD2U());
    }

    // Clear the floating-point stack if dirty.
    if (!SSE2_FULL) {
      int nSave = 0;
      if (MIR_Return.hasVal(ret)) {
        Operand symb1 = MIR_Return.getClearVal(ret);
        TypeReference type = symb1.getType();
        if (type.isFloatType() || type.isDoubleType()) {
          nSave = 1;
        }
      }
      ret.insertBefore(MIR_UnaryNoRes.create(IA32_FCLEAR, IC(nSave)));
    }

    // Set the first 'Val' in the return instruction to hold an integer
    // constant which is the number of words to pop from the stack while
    // returning from this method.
    MIR_Return.setPopBytes(ret, IC(ir.incomingParameterBytes()));
  }

  /**
   * Explicitly copy the result of a call instruction from the result
   * register to the appropriate symbolic register,
   * as defined by the calling convention.
   *
   * @param call the call instruction
   * @param isSysCall whether the call is a SysCall
   * @param ir the IR that contains the call
   */
  private static void expandResultOfCall(Instruction call, boolean isSysCall, IR ir) {
    PhysicalRegisterSet phys = (PhysicalRegisterSet)ir.regpool.getPhysicalRegisterSet();

    // copy the first result parameter
    if (MIR_Call.hasResult(call)) {
      RegisterOperand result1 = MIR_Call.getClearResult(call);
      if (result1.getType().isFloatType() || result1.getType().isDoubleType()) {
        if (VM.BuildFor32Addr && SSE2_FULL && isSysCall) {
          byte size = (byte)(result1.getType().isFloatType() ? 4 : 8);
          RegisterOperand st0 = new RegisterOperand(phys.getST0(), result1.getType());
          MIR_Call.setResult(call, st0); // result is in st0, set it to avoid extending the live range of st0
          RegisterOperand pr = ir.regpool.makeTROp();
          MemoryOperand scratch = new MemoryOperand(pr, null, (byte)0, Entrypoints.scratchStorageField.getOffset(), size, new LocationOperand(Entrypoints.scratchStorageField), null);

          Instruction pop = MIR_Move.create(IA32_FSTP, scratch, st0.copyRO());
          call.insertAfter(pop);
          if (result1.getType().isFloatType()) {
            pop.insertAfter(MIR_Move.create(IA32_MOVSS, result1, scratch.copy()));
          } else {
            if (VM.VerifyAssertions) VM._assert(result1.getType().isDoubleType());
            pop.insertAfter(MIR_Move.create(IA32_MOVSD, result1, scratch.copy()));
          }
        } else {
          Register r = phys.getReturnFPR();
          RegisterOperand physical = new RegisterOperand(r, result1.getType());
          MIR_Call.setResult(call, physical.copyRO()); // result is in physical, set it to avoid extending its live range
          Instruction tmp;
          if (SSE2_FULL) {
            if (result1.getType().isFloatType()) {
              tmp = MIR_Move.create(IA32_MOVSS, result1, physical);
            } else {
              tmp = MIR_Move.create(IA32_MOVSD, result1, physical);
            }
          } else {
            tmp = MIR_Move.create(IA32_FMOV, result1, physical);
          }
          call.insertAfter(tmp);
        }
      } else {
        // first GPR result register
        Register r = phys.getFirstReturnGPR();
        RegisterOperand physical = new RegisterOperand(r, result1.getType());
        Instruction tmp = MIR_Move.create(IA32_MOV, result1, physical);
        call.insertAfter(tmp);
        MIR_Call.setResult(call, physical.copyRO());  // result is in physical, set it to avoid extending its live range
      }
    }

    // copy the second result parameter
    if (MIR_Call.hasResult2(call)) {
      if (VM.VerifyAssertions) VM._assert(VM.BuildFor32Addr);
      RegisterOperand result2 = MIR_Call.getClearResult2(call);
      // second GPR result register
      Register r = phys.getSecondReturnGPR();
      RegisterOperand physical = new RegisterOperand(r, result2.getType());
      Instruction tmp = MIR_Move.create(IA32_MOV, result2, physical);
      call.insertAfter(tmp);
      MIR_Call.setResult2(call, physical.copyRO());  // result is in physical, set it to avoid extending its live range
    }
  }

  /**
   * Explicitly copy parameters to a call into the appropriate physical
   * registers as defined by the calling convention.<p>
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.
   *
   * @param call the call instruction
   * @param ir the IR that contains the call
   * @return number of bytes necessary to hold the parameters
   */
  private static int expandParametersToCall(Instruction call, IR ir) {
    int nGPRParams = 0;
    int nFPRParams = 0;

    PhysicalRegisterSet phys = (PhysicalRegisterSet)ir.regpool.getPhysicalRegisterSet();
    // count the number FPR parameters in a pre-pass
    int FPRRegisterParams = countFPRParams(call);
    FPRRegisterParams = Math.min(FPRRegisterParams, PhysicalRegisterSet.getNumberOfFPRParams());

    // offset, in bytes, from the SP, for the next parameter slot on the
    // stack
    int parameterBytes = 0;

    // Require ESP to be at bottom of frame before a call,
    call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(0)));

    // walk over each parameter
    // must count then before we start nulling them out!
    int numParams = MIR_Call.getNumberOfParams(call);
    int nParamsInRegisters = 0;
    for (int i = 0; i < numParams; i++) {
      Operand param = MIR_Call.getClearParam(call, i);
      MIR_Call.setParam(call, i, null);
      TypeReference paramType = param.getType();
      if (paramType.isFloatingPointType()) {
        nFPRParams++;
        int size;
        if (paramType.isFloatType()) {
          size = BYTES_IN_FLOAT;
          parameterBytes -= WORDSIZE;
        } else {
          size = BYTES_IN_DOUBLE;
          parameterBytes -= 2 * WORDSIZE;
        }
        if (nFPRParams > PhysicalRegisterSet.getNumberOfFPRParams()) {
          // pass the FP parameter on the stack
          Operand M = new StackLocationOperand(false, parameterBytes, size);
          if (SSE2_FULL) {
            if (paramType.isFloatType()) {
              call.insertBefore(MIR_Move.create(IA32_MOVSS, M, param));
            } else {
              call.insertBefore(MIR_Move.create(IA32_MOVSD, M, param));
            }
          } else {
            call.insertBefore(MIR_Move.create(IA32_FMOV, M, param));
          }
        } else {
          // Pass the parameter in a register.
          RegisterOperand real;
          if (SSE2_FULL) {
            real = new RegisterOperand(phys.getFPRParam(nFPRParams - 1), paramType);
            if (paramType.isFloatType()) {
              call.insertBefore(MIR_Move.create(IA32_MOVSS, real, param));
            } else {
              call.insertBefore(MIR_Move.create(IA32_MOVSD, real, param));
            }
          } else {
            // Note that if k FPRs are passed in registers,
            // the 1st goes in F(k-1),
            // the 2nd goes in F(k-2), etc...
            real = new RegisterOperand(phys.getFPRParam(FPRRegisterParams - nFPRParams), paramType);
            call.insertBefore(MIR_Move.create(IA32_FMOV, real, param));
          }
          // Record that the call now has a use of the real register.
          MIR_Call.setParam(call, nParamsInRegisters++, real.copy());
        }
      } else {
        nGPRParams++;
        parameterBytes -= WORDSIZE;
        if (paramIsNativeLongOn64Bit(param)) {
          parameterBytes -= WORDSIZE;
        }
        if (nGPRParams > PhysicalRegisterSet.getNumberOfGPRParams()) {
          // Too many parameters to pass in registers.  Write the
          // parameter into the appropriate stack frame location.
          if (paramIsNativeLongOn64Bit(param)) {
            call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + WORDSIZE * 2)));
            call.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, IC(0)));
          } else {
            call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + WORDSIZE)));
          }
          call.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, param));
        } else {
          // Pass the parameter in a register.
          Register phy = phys.getGPRParam(nGPRParams - 1);
          RegisterOperand real = new RegisterOperand(phy, paramType);
          call.insertBefore(MIR_Move.create(IA32_MOV, real, param));
          // Record that the call now has a use of the real register.
          MIR_Call.setParam(call, nParamsInRegisters++, real.copy());
        }
      }
    }
    return parameterBytes;
  }

  private static boolean paramIsNativeLongOn64Bit(Operand param) {
    return VM.BuildFor64Addr && param.isLong() &&
      ((param.isRegister() && !param.asRegister().convertedFromRef()) ||
        (param.isLongConstant() && !param.asLongConstant().convertedFromRef()));
  }

  /**
   * Save and restore all nonvolatile registers around a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.<p>
   *
   * We save/restore all nonvolatiles and the thread register as
   * well as the JTOC (if present), whether or not this routine
   * uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.<p>
   *
   * Side effect: changes the operator of the call instruction to
   * IA32_CALL.
   *
   * @param call the sys call
   * @param ir the IR that contains the call
   */
  public static void saveNonvolatilesAroundSysCall(Instruction call, IR ir) {
    saveNonvolatilesBeforeSysCall(call, ir);
    restoreNonvolatilesAfterSysCall(call, ir);
    call.changeOperatorTo(IA32_CALL);
  }

  /**
   * Save all nonvolatile registers before a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.<p>
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   * @param ir the IR that contains the call
   */
  static void saveNonvolatilesBeforeSysCall(Instruction call, IR ir) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    StackManager sm = (StackManager) ir.stackManager;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();

    // save each non-volatile
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertBefore(MIR_Move.create(IA32_MOV, M, new RegisterOperand(r, wordType)));
      location += WORDSIZE;
    }

    // save the thread register
    Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
    call.insertBefore(MIR_Move.create(IA32_MOV, M, ir.regpool.makeTROp()));
    // save the JTOC, if present
    if (JTOC_REGISTER != null) {
      location += WORDSIZE;
      Operand jtocSave = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertBefore(MIR_Move.create(IA32_MOV, jtocSave, ir.regpool.makeTocOp()));
    }
  }

  /**
   * Restore all nonvolatile registers after a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.<p>
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   * @param ir the IR that contains the call
   */
  static void restoreNonvolatilesAfterSysCall(Instruction call, IR ir) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    StackManager sm = (StackManager) ir.stackManager;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();

    // restore each non-volatile
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertAfter(MIR_Move.create(IA32_MOV, new RegisterOperand(r, wordType), M));
      location += WORDSIZE;
    }

    // restore the thread register
    Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
    call.insertAfter(MIR_Move.create(IA32_MOV, ir.regpool.makeTROp(), M));
    // restore the JTOC, if applicable
    if (JTOC_REGISTER != null) {
      location += WORDSIZE;
      Operand jtocSave = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertAfter(MIR_Move.create(IA32_MOV, ir.regpool.makeTocOp(), jtocSave));
    }
  }

  /**
   * Explicitly copy parameters to a system call into the appropriate physical
   * registers as defined by the calling convention.  Note that for a system
   * call (ie., a call to C), the order of parameters on the stack is
   * <em> reversed </em> compared to the normal RVM calling convention<p>
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.<p>
   *
   * TODO: much of this code is exactly the same as in expandParametersToCall().
   *       factor out the common code.
   *
   * @param call the call instruction
   * @param ir the IR that contains the call
   * @return the number of bytes necessary to hold the parameters
   */
  private static int expandParametersToSysCall(Instruction call, IR ir) {
    int nGPRParams = 0;
    int nFPRParams = 0;
    int parameterBytes = 0;
    int numParams = MIR_Call.getNumberOfParams(call);

    if (VM.BuildFor32Addr) {
      // walk over the parameters in reverse order
      // NOTE: All params to syscall are passed on the stack!
      for (int i = numParams - 1; i >= 0; i--) {
        Operand param = MIR_Call.getClearParam(call, i);
        MIR_Call.setParam(call, i, null);
        TypeReference paramType = param.getType();
        if (paramType.isFloatingPointType()) {
          nFPRParams++;
          int size;
          if (paramType.isFloatType()) {
            size = BYTES_IN_FLOAT;
            parameterBytes -= WORDSIZE;
          } else {
            size = BYTES_IN_DOUBLE;
            parameterBytes -= 2 * WORDSIZE;
          }
          Operand M = new StackLocationOperand(false, parameterBytes, size);
          if (SSE2_FULL) {
            if (paramType.isFloatType()) {
              call.insertBefore(MIR_Move.create(IA32_MOVSS, M, param));
            } else {
              call.insertBefore(MIR_Move.create(IA32_MOVSD, M, param));
            }
          } else {
            call.insertBefore(MIR_Move.create(IA32_FMOV, M, param));
          }
        } else {
          nGPRParams++;
          parameterBytes -= WORDSIZE;
          call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + WORDSIZE)));
          call.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, param));
        }
      }
      return parameterBytes;
    } else {
      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet().asIA32();
      // count the number FPR parameters in a pre-pass
      int FPRRegisterParams = countFPRParams(call);
      FPRRegisterParams = Math.min(FPRRegisterParams, PhysicalRegisterSet.getNumberOfNativeFPRParams());

      // offset, in bytes, from the SP, for the next parameter slot on the
      // stack
      parameterBytes = -2 * WORDSIZE;
      RegisterOperand fpCount = new RegisterOperand(phys.getEAX(), TypeReference.Int);
      // Save count of vector parameters (= XMM) in EAX as defined by
      // the ABI for varargs convention
      call.insertBefore(MIR_Move.create(IA32_MOV, fpCount, IC(FPRRegisterParams)));
      // Save volatiles to non-volatiles that are currently not used
      call.insertBefore(MIR_Move.create(IA32_MOV, new RegisterOperand(phys.getGPR(R14), TypeReference.Long),new RegisterOperand(phys.getESI(), TypeReference.Long)));
      call.insertBefore(MIR_Move.create(IA32_MOV, new RegisterOperand(phys.getGPR(R13), TypeReference.Long),new RegisterOperand(phys.getEDI(), TypeReference.Long)));
      // Restore volatiles from non-volatiles
      call.insertAfter(MIR_Move.create(IA32_MOV,new RegisterOperand(phys.getESI(), TypeReference.Long), new RegisterOperand(phys.getGPR(R14), TypeReference.Long)));
      call.insertAfter(MIR_Move.create(IA32_MOV,new RegisterOperand(phys.getEDI(), TypeReference.Long), new RegisterOperand(phys.getGPR(R13), TypeReference.Long)));

      if (VM.BuildFor64Addr) {
        // Add a marker instruction. When processing x64 syscalls, the block of the syscall
        // needs to be split up to copy the code for the call. Copying has to occur
        // to be able to ensure stack alignment for the x64 ABI. This instruction
        // marks the border for the copy: everything before this instruction isn't duplicated.
        call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(MARKER)));
      }

      // Require ESP to be at bottom of frame before a call,
      call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(0)));

      // walk over each parameter
      // must count the, before we start nulling them out!
      int nParamsInRegisters = 0;
      for (int i = 0; i < numParams; i++) {
        Operand param = MIR_Call.getClearParam(call, i);
        MIR_Call.setParam(call, i, null);
        TypeReference paramType = param.getType();
        if (paramType.isFloatingPointType()) {
          nFPRParams++;
          int size;
          size = BYTES_IN_STACKSLOT;
          parameterBytes -= WORDSIZE;
          if (nFPRParams > PhysicalRegisterSet.getNumberOfNativeFPRParams()) {
            // pass the FP parameter on the stack
            Operand M = new StackLocationOperand(false, parameterBytes, size);
            if (SSE2_FULL) {
              if (paramType.isFloatType()) {
                call.insertBefore(MIR_Move.create(IA32_MOVSS, M, param));
              } else {
                call.insertBefore(MIR_Move.create(IA32_MOVSD, M, param));
              }
            } else {
              call.insertBefore(MIR_Move.create(IA32_FMOV, M, param));
            }
          } else {
            // Pass the parameter in a register.
            RegisterOperand real;
            if (SSE2_FULL) {
              real = new RegisterOperand(phys.getNativeFPRParam(nFPRParams - 1), paramType);
              if (paramType.isFloatType()) {
                call.insertBefore(MIR_Move.create(IA32_MOVSS, real, param));
              } else {
                call.insertBefore(MIR_Move.create(IA32_MOVSD, real, param));
              }
            } else {
              // Note that if k FPRs are passed in registers,
              // the 1st goes in F(k-1),
              // the 2nd goes in F(k-2), etc...
              real = new RegisterOperand(phys.getNativeFPRParam(FPRRegisterParams - nFPRParams), paramType);
              call.insertBefore(MIR_Move.create(IA32_FMOV, real, param));
            }
            // Record that the call now has a use of the real register.
            MIR_Call.setParam(call, nParamsInRegisters++, real.copy());
          }
        } else {
          nGPRParams++;
          parameterBytes -= WORDSIZE;
          if (nGPRParams > PhysicalRegisterSet.getNumberOfNativeGPRParams()) {
            // Too many parameters to pass in registers.  Write the
            // parameter into the appropriate stack frame location.
            call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + WORDSIZE)));
            call.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, param));
          } else {
            // Pass the parameter in a register.
            Register phy = phys.getNativeGPRParam(nGPRParams - 1);
            RegisterOperand real = new RegisterOperand(phy, paramType);
            call.insertBefore(MIR_Move.create(IA32_MOV, real, param));
            // Record that the call now has a use of the real register.
            MIR_Call.setParam(call, nParamsInRegisters++, real.copy());
          }
        }
      }
      return parameterBytes;
    }
  }

  /**
   * We have to save/restore the non-volatile registers around syscalls,
   * to protect ourselves from malicious C compilers and Linux kernels.<p>
   *
   * Although the register allocator is not yet ready to insert these
   * spills, allocate space on the stack in preparation.<p>
   *
   * For now, we naively save/restore all nonvolatiles.
   *
   * @param ir the governing IR
   */
  public static void allocateSpaceForSysCall(IR ir) {
    StackManager sm = (StackManager) ir.stackManager;

    // add one to account for the thread register.
    int nToSave = PhysicalRegisterSet.getNumberOfNonvolatileGPRs() + 1;
    // add one for JTOC
    if (JTOC_REGISTER != null) nToSave++;

    sm.allocateSpaceForSysCall(nToSave);
  }

  /**
   * Calling convention to implement calls to native (C) routines
   * using the Linux linkage conventions.<p>
   *
   * @param s the call instruction
   * @param ir the IR that contains the call
   */
  public static void expandSysCall(Instruction s, IR ir) {
    Operand ip = Call.getClearAddress(s);

    // Allocate space to save non-volatiles.
    allocateSpaceForSysCall(ir);

    // Make sure we allocate enough space for the parameters to this call.
    int numberParams = Call.getNumberOfParams(s);
    int parameterWords = 0;
    for (int i = 0; i < numberParams; i++) {
      parameterWords++;
      Operand op = Call.getParam(s, i);
      parameterWords += op.getType().getStackWords();
    }
    // allocate space for each parameter,
    // plus one word on the stack to hold the address of the callee,
    // plus one word on stack for alignment of x64 syscalls
    int alignWords = VM.BuildFor64Addr ? 1 : 0;
    int neededWords = parameterWords + alignWords + 1;
    ir.stackManager.allocateParameterSpace(neededWords * WORDSIZE);

    // Convert to a SYSCALL instruction with a null method operand.
    Call.mutate0(s, SYSCALL, Call.getClearResult(s), ip, null);
  }

  private static int countFPRParams(Instruction call) {
    int result = 0;
    // walk over the parameters
    int numParams = MIR_Call.getNumberOfParams(call);
    for (int i = 0; i < numParams; i++) {
      Operand param = MIR_Call.getParam(call, i);
      if (param.isRegister()) {
        RegisterOperand symb = (RegisterOperand) param;
        if (symb.getType().isFloatingPointType()) {
          result++;
        }
      }
    }
    return result;
  }

  private static int countFPRParamsInPrologue(Instruction p) {
    int result = 0;
    // walk over the parameters
    for (Enumeration<Operand> e = p.getDefs(); e.hasMoreElements();) {
      Operand param = e.nextElement();
      if (param.isRegister()) {
        RegisterOperand symb = (RegisterOperand) param;
        if (symb.getType().isFloatingPointType()) {
          result++;
        }
      }
    }
    return result;
  }

  private static void expandPrologue(IR ir) {
    boolean useDU = ir.options.getOptLevel() >= 1;
    if (useDU) {
      // set up register lists for dead code elimination.
      DefUse.computeDU(ir);
    }

    Instruction p = ir.firstInstructionInCodeOrder().
        nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(p.operator() == IR_PROLOGUE);
    Instruction start = p.nextInstructionInCodeOrder();
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet().asIA32();

    int gprIndex = 0;
    int fprIndex = 0;
    int paramByteOffset = ir.incomingParameterBytes() + 2 * WORDSIZE;

    // count the number of FPR params in a pre-pass
    int FPRRegisterParams = countFPRParamsInPrologue(p);
    FPRRegisterParams = Math.min(FPRRegisterParams, PhysicalRegisterSet.getNumberOfFPRParams());
    ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight, FPRRegisterParams);

    // deal with each parameter
    for (Enumeration<Operand> e = p.getDefs(); e.hasMoreElements();) {
      RegisterOperand symbOp = (RegisterOperand) e.nextElement();
      TypeReference rType = symbOp.getType();
      if (rType.isFloatingPointType()) {
        int size;
        if (rType.isFloatType()) {
          size = BYTES_IN_FLOAT;
          paramByteOffset -= WORDSIZE;
        } else {
          size = BYTES_IN_DOUBLE;
          paramByteOffset -= 2 * WORDSIZE;
        }
        // if optimizing, only define the register if it has uses
        if (!useDU || symbOp.getRegister().useList != null) {
          if (fprIndex < PhysicalRegisterSet.getNumberOfFPRParams()) {
            // insert a MOVE symbolic register = parameter
            // Note that if k FPRs are passed in registers,
            // the 1st goes in F(k-1),
            // the 2nd goes in F(k-2), etc...
            if (SSE2_FULL) {
              Register param = phys.getFPRParam(fprIndex);
              if (rType.isFloatType()) {
                start.insertBefore(MIR_Move.create(IA32_MOVSS, symbOp.copyRO(), F(param)));
              } else {
                start.insertBefore(MIR_Move.create(IA32_MOVSD, symbOp.copyRO(), D(param)));
              }
            } else {
              Register param = phys.getFPRParam(FPRRegisterParams - fprIndex - 1);
              start.insertBefore(MIR_Move.create(IA32_FMOV, symbOp.copyRO(), D(param)));
            }
          } else {
            Operand M = new StackLocationOperand(true, paramByteOffset, size);
            if (SSE2_FULL) {
              if (rType.isFloatType()) {
                start.insertBefore(MIR_Move.create(IA32_MOVSS, symbOp.copyRO(), M));
              } else {
                start.insertBefore(MIR_Move.create(IA32_MOVSD, symbOp.copyRO(), M));
              }
            } else {
              start.insertBefore(MIR_Move.create(IA32_FMOV, symbOp.copyRO(), M));
            }
          }
        }
        fprIndex++;
      } else {
        // if optimizing, only define the register if it has uses
        paramByteOffset -= WORDSIZE;
        if (paramIsNativeLongOn64Bit(symbOp)) {
          paramByteOffset -= WORDSIZE;
        }
        if (!useDU || symbOp.getRegister().useList != null) {
          // t is object, 1/2 of a long, int, short, char, byte, or boolean
          if (gprIndex < PhysicalRegisterSet.getNumberOfGPRParams()) {
            // to give the register allocator more freedom, we
            // insert two move instructions to get the physical into
            // the symbolic.  First a move from the physical to a fresh temp
            // before start and second a move from the temp to the
            // 'real' parameter symbolic after start.
            RegisterOperand tmp = ir.regpool.makeTemp(rType);
            Register param = phys.getGPRParam(gprIndex);
            RegisterOperand pOp = new RegisterOperand(param, rType);
            start.insertBefore(PhysicalRegisterTools.makeMoveInstruction(tmp, pOp));
            Instruction m2 = PhysicalRegisterTools.makeMoveInstruction(symbOp.copyRO(), tmp.copyD2U());
            start.insertBefore(m2);
            start = m2;
          } else {
            int stackLocSize = WORDSIZE;
            if (VM.BuildFor64Addr && rType.getMemoryBytes() <= BYTES_IN_INT) {
              stackLocSize = BYTES_IN_INT;
            }
            Operand M = new StackLocationOperand(true, paramByteOffset, stackLocSize);
            start.insertBefore(MIR_Move.create(IA32_MOV, symbOp.copyRO(), M));
          }
        }
        gprIndex++;
      }
    }

    if (VM.VerifyAssertions && paramByteOffset != 2 * WORDSIZE) {
      String msg = "pb = " + paramByteOffset + "; expected " + 2 * WORDSIZE;
      VM._assert(VM.NOT_REACHED, msg);
    }

    removeDefsFromPrologue(p);
  }

}
