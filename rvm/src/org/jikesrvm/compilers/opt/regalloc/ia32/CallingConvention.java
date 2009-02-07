/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.regalloc.ia32;

import static org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS;

import java.util.Enumeration;
import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.InterfaceMethodSignature;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.MIR_Call;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Return;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterTools;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StackLocationOperand;
import org.jikesrvm.ia32.ArchConstants;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;

/**
 * This class contains IA32 calling conventions
 * The two public methods are:
 *  (1) expandCallingConventions(IR) which is called by the
 *  register allocator immediately before allocation to make manifest the
 *  use of registers by the calling convention.
 *  (2) expandSysCall(Instruction, IR) which is called to expand
 *  a SYSCALL HIR instruction into the appropriate sequence of
 *  LIR instructions.
 *
 * TODO: Much of this code could still be factored out as
 * architecture-independent.
 */
public abstract class CallingConvention extends IRTools
    implements Operators, PhysicalRegisterConstants {

  /**
   * Size of a word, in bytes
   */
  private static final int WORDSIZE = BYTES_IN_ADDRESS;

  /**
   * Expand calling conventions to make physical registers explicit in the
   * IR when required for calls, returns, and the prologue.
   */
  public static void expandCallingConventions(IR ir) {
    // expand each call and return instruction
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      if (inst.isCall()) {
        callExpand(inst, ir);
      } else if (inst.isReturn()) {
        returnExpand(inst, ir);
      }
    }

    // expand the prologue instruction
    expandPrologue(ir);
  }

  /**
   * Expand the calling convention for a particular call instruction
   */
  private static void callExpand(Instruction call, IR ir) {
    boolean isSysCall = call.operator() == IA32_SYSCALL;

    // 0. Handle the parameters
    int parameterBytes = isSysCall ? expandParametersToSysCall(call, ir) : expandParametersToCall(call, ir);

    // 1. Clear the floating-point stack if dirty.
    if (!ArchConstants.SSE2_FULL) {
      if (call.operator != CALL_SAVE_VOLATILE) {
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
    call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes)));
    call.insertAfter(MIR_UnaryNoRes.create(ADVISE_ESP, IC(isSysCall ? parameterBytes : 0)));
  }

  /**
   * Expand the calling convention for a particular return instruction
   */
  private static void returnExpand(Instruction ret, IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    if (MIR_Return.hasVal(ret)) {
      Operand symb1 = MIR_Return.getClearVal(ret);
      MIR_Return.setVal(ret, null);
      TypeReference type = symb1.getType();
      if (type.isFloatType() || type.isDoubleType()) {
        Register r = phys.getReturnFPR();
        RegisterOperand rOp = new RegisterOperand(r, type);
        if (ArchConstants.SSE2_FULL) {
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
      Operand symb2 = MIR_Return.getClearVal2(ret);
      MIR_Return.setVal2(ret, null);
      TypeReference type = symb2.getType();
      Register r = phys.getSecondReturnGPR();
      RegisterOperand rOp = new RegisterOperand(r, type);
      ret.insertBefore(MIR_Move.create(IA32_MOV, rOp, symb2));
      MIR_Return.setVal2(ret, rOp.copyD2U());
    }

    // Clear the floating-point stack if dirty.
    if (!ArchConstants.SSE2_FULL) {
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
   */
  private static void expandResultOfCall(Instruction call, boolean isSysCall, IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // copy the first result parameter
    if (MIR_Call.hasResult(call)) {
      RegisterOperand result1 = MIR_Call.getClearResult(call);
      if (result1.getType().isFloatType() || result1.getType().isDoubleType()) {
        if (ArchConstants.SSE2_FULL && isSysCall) {
          byte size = (byte)(result1.getType().isFloatType() ? 4 : 8);
          RegisterOperand st0 = new RegisterOperand(phys.getST0(), result1.getType());
          MIR_Call.setResult(call, st0); // result is in st0, set it to avoid extending the live range of st0
          RegisterOperand pr = ir.regpool.makeTROp();
          MemoryOperand scratch = new MemoryOperand(pr, null, (byte)0, Entrypoints.scratchStorageField.getOffset(), size, new LocationOperand(Entrypoints.scratchStorageField), null);

          Instruction pop = MIR_Move.create(IA32_FSTP, scratch, st0.copyRO());
          call.insertAfter(pop);
          if (result1.getType().isFloatType()) {
            pop.insertAfter(MIR_Move.create(IA32_MOVSS, result1, scratch.copy()));
          } else /* if (result1.type.isDoubleType()) */ {
            pop.insertAfter(MIR_Move.create(IA32_MOVSD, result1, scratch.copy()));
          }
        } else {
          Register r = phys.getReturnFPR();
          RegisterOperand physical = new RegisterOperand(r, result1.getType());
          MIR_Call.setResult(call, physical.copyRO()); // result is in physical, set it to avoid extending its live range
          Instruction tmp;
          if (ArchConstants.SSE2_FULL) {
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
   * registers as defined by the calling convention.
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.
   */
  private static int expandParametersToCall(Instruction call, IR ir) {
    int nGPRParams = 0;
    int nFPRParams = 0;

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
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
      if (paramType.isFloatType() || paramType.isDoubleType()) {
        nFPRParams++;
        int size = paramType.isFloatType() ? 4 : 8;
        parameterBytes -= size;
        if (nFPRParams > PhysicalRegisterSet.getNumberOfFPRParams()) {
          // pass the FP parameter on the stack
          Operand M = new StackLocationOperand(false, parameterBytes, size);
          if (ArchConstants.SSE2_FULL) {
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
          if (ArchConstants.SSE2_FULL) {
            real = new RegisterOperand(phys.getFPRParam(nFPRParams-1), paramType);
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
        parameterBytes -= 4;
        if (nGPRParams > PhysicalRegisterSet.getNumberOfGPRParams()) {
          // Too many parameters to pass in registers.  Write the
          // parameter into the appropriate stack frame location.
          call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + 4)));
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

  /**
   * Save and restore all nonvolatile registers around a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * Side effect: changes the operator of the call instruction to
   * IA32_CALL.
   *
   * @param call the sys call
   */
  public static void saveNonvolatilesAroundSysCall(Instruction call, IR ir) {
    saveNonvolatilesBeforeSysCall(call, ir);
    restoreNonvolatilesAfterSysCall(call, ir);
    call.operator = IA32_CALL;
  }

  /**
   * Save all nonvolatile registers before a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   */
  static void saveNonvolatilesBeforeSysCall(Instruction call, IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    StackManager sm = (StackManager) ir.stackManager;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();

    // save each non-volatile
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertBefore(MIR_Move.create(IA32_MOV, M, new RegisterOperand(r, TypeReference.Int)));
      location += WORDSIZE;
    }

    // save the thread register
    Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
    call.insertBefore(MIR_Move.create(IA32_MOV, M, ir.regpool.makeTROp()));
  }

  /**
   * Restore all nonvolatile registers after a syscall.
   * We do this in case the sys call does not respect our
   * register conventions.
   *
   * We save/restore all nonvolatiles and the PR, whether
   * or not this routine uses them.  This may be a tad inefficient, but if
   * you're making a system call, you probably don't care.
   *
   * @param call the sys call
   */
  static void restoreNonvolatilesAfterSysCall(Instruction call, IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    StackManager sm = (StackManager) ir.stackManager;

    // get the offset into the stack frame of where to stash the first
    // nonvolatile for this case.
    int location = sm.getOffsetForSysCall();

    // restore each non-volatile
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
      call.insertAfter(MIR_Move.create(IA32_MOV, new RegisterOperand(r, TypeReference.Int), M));
      location += WORDSIZE;
    }

    // restore the thread register
    Operand M = new StackLocationOperand(true, -location, (byte) WORDSIZE);
    call.insertAfter(MIR_Move.create(IA32_MOV, ir.regpool.makeTROp(), M));
  }

  /**
   * Explicitly copy parameters to a system call into the appropriate physical
   * registers as defined by the calling convention.  Note that for a system
   * call (ie., a call to C), the order of parameters on the stack is
   * <em> reversed </em> compared to the normal RVM calling convention
   *
   * TODO: much of this code is exactly the same as in expandParametersToCall().
   *       factor out the common code.
   *
   * Note: Assumes that ESP points to the word before the slot where the
   * first parameter should be stored.
   */
  private static int expandParametersToSysCall(Instruction call, IR ir) {
    int nGPRParams = 0;
    int nFPRParams = 0;
    int parameterBytes = 0;

    // walk over the parameters in reverse order
    // NOTE: All params to syscall are passed on the stack!
    int numParams = MIR_Call.getNumberOfParams(call);
    for (int i = numParams - 1; i >= 0; i--) {
      Operand param = MIR_Call.getClearParam(call, i);
      MIR_Call.setParam(call, i, null);
      TypeReference paramType = param.getType();
      if (paramType.isFloatType() || paramType.isDoubleType()) {
        nFPRParams++;
        int size = paramType.isFloatType() ? 4 : 8;
        parameterBytes -= size;
        Operand M = new StackLocationOperand(false, parameterBytes, size);
        if (ArchConstants.SSE2_FULL) {
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
        parameterBytes -= 4;
        call.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(parameterBytes + 4)));
        call.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, param));
      }
    }
    return parameterBytes;
  }

  /**
   * We have to save/restore the non-volatile registers around syscalls,
   * to protect ourselves from malicious C compilers and Linux kernels.
   *
   * Although the register allocator is not yet ready to insert these
   * spills, allocate space on the stack in preparation.
   *
   * For now, we naively save/restore all nonvolatiles.
   */
  public static void allocateSpaceForSysCall(IR ir) {
    StackManager sm = (StackManager) ir.stackManager;

    // add one to account for the processor register.
    int nToSave = PhysicalRegisterSet.getNumberOfNonvolatileGPRs() + 1;

    sm.allocateSpaceForSysCall(nToSave);
  }

  /**
   * Calling convention to implement calls to native (C) routines
   * using the Linux linkage conventions.
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
    // allocate space for each parameter, plus one word on the stack to
    // hold the address of the callee.
    ir.stackManager.allocateParameterSpace((1 + parameterWords) * 4);

    // Convert to a SYSCALL instruction with a null method operand.
    Call.mutate0(s, SYSCALL, Call.getClearResult(s), ip, null);
  }

  /**
   * Count the number of FPR parameters in a call instruction.
   */
  private static int countFPRParams(Instruction call) {
    int result = 0;
    // walk over the parameters
    int numParams = MIR_Call.getNumberOfParams(call);
    for (int i = 0; i < numParams; i++) {
      Operand param = MIR_Call.getParam(call, i);
      if (param.isRegister()) {
        RegisterOperand symb = (RegisterOperand) param;
        if (symb.getType().isFloatType() || symb.getType().isDoubleType()) {
          result++;
        }
      }
    }
    return result;
  }

  /**
   * Count the number of FPR parameters in a prologue instruction.
   */
  private static int countFPRParamsInPrologue(Instruction p) {
    int result = 0;
    // walk over the parameters
    for (OperandEnumeration e = p.getDefs(); e.hasMoreElements();) {
      Operand param = e.nextElement();
      if (param.isRegister()) {
        RegisterOperand symb = (RegisterOperand) param;
        if (symb.getType().isFloatType() || symb.getType().isDoubleType()) {
          result++;
        }
      }
    }
    return result;
  }

  /**
   * Expand the prologue instruction.
   */
  private static void expandPrologue(IR ir) {
    boolean useDU = ir.options.getOptLevel() >= 1;
    if (useDU) {
      // set up register lists for dead code elimination.
      DefUse.computeDU(ir);
    }

    Instruction p = ir.firstInstructionInCodeOrder().
        nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(p.operator == IR_PROLOGUE);
    Instruction start = p.nextInstructionInCodeOrder();
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    int gprIndex = 0;
    int fprIndex = 0;
    int paramByteOffset = ir.incomingParameterBytes() + 8;

    // count the number of FPR params in a pre-pass
    int FPRRegisterParams = countFPRParamsInPrologue(p);
    FPRRegisterParams = Math.min(FPRRegisterParams, PhysicalRegisterSet.getNumberOfFPRParams());
    ir.MIRInfo.fpStackHeight = Math.max(ir.MIRInfo.fpStackHeight, FPRRegisterParams);

    // deal with each parameter
    for (OperandEnumeration e = p.getDefs(); e.hasMoreElements();) {
      RegisterOperand symbOp = (RegisterOperand) e.nextElement();
      TypeReference rType = symbOp.getType();
      if (rType.isFloatType() || rType.isDoubleType()) {
        int size = rType.isFloatType() ? 4 : 8;
        paramByteOffset -= size;
        // if optimizing, only define the register if it has uses
        if (!useDU || symbOp.getRegister().useList != null) {
          if (fprIndex < PhysicalRegisterSet.getNumberOfFPRParams()) {
            // insert a MOVE symbolic register = parameter
            // Note that if k FPRs are passed in registers,
            // the 1st goes in F(k-1),
            // the 2nd goes in F(k-2), etc...
            if (ArchConstants.SSE2_FULL) {
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
            if (ArchConstants.SSE2_FULL) {
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
        paramByteOffset -= 4;
        if (!useDU || symbOp.getRegister().useList != null) {
          // t is object, 1/2 of a long, int, short, char, byte, or boolean
          if (gprIndex < PhysicalRegisterSet.getNumberOfGPRParams()) {
            // to give the register allocator more freedom, we
            // insert two move instructions to get the physical into
            // the symbolic.  First a move from the physical to a fresh temp
            // before start and second a move from the temp to the
            // 'real' parameter symbolic after start.
            RegisterOperand tmp = ir.regpool.makeTemp(TypeReference.Int);
            Register param = phys.getGPRParam(gprIndex);
            RegisterOperand pOp = new RegisterOperand(param, rType);
            start.insertBefore(PhysicalRegisterTools.makeMoveInstruction(tmp, pOp));
            Instruction m2 = PhysicalRegisterTools.makeMoveInstruction(symbOp.copyRO(), tmp.copyD2U());
            start.insertBefore(m2);
            start = m2;
          } else {
            Operand M = new StackLocationOperand(true, paramByteOffset, 4);
            start.insertBefore(MIR_Move.create(IA32_MOV, symbOp.copyRO(), M));
          }
        }
        gprIndex++;
      }
    }

    if (VM.VerifyAssertions && paramByteOffset != 8) {
      VM._assert(false, "pb = " + paramByteOffset + "; expected 8");
    }

    // Now that we've made the calling convention explicit in the prologue,
    // set IR_PROLOGUE to have no defs.
    p.replace(Prologue.create(IR_PROLOGUE, 0));
  }
}
