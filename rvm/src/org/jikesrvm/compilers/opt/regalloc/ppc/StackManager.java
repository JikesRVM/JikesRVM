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
package org.jikesrvm.compilers.opt.regalloc.ppc;

import java.util.Enumeration;
import java.util.Iterator;
import org.jikesrvm.VM;
import static org.jikesrvm.Constants.NOT_REACHED;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.MIR_Binary;
import org.jikesrvm.compilers.opt.ir.MIR_Load;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Store;
import org.jikesrvm.compilers.opt.ir.MIR_StoreUpdate;
import org.jikesrvm.compilers.opt.ir.MIR_Trap;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_SAVE_VOLATILE;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_ADDI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BCTRL_SYS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BLR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_BL_SYS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_CMPI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_FMR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_FMR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LAddr;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LAddr_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LDI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LDIS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LFD;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LFD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LFS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LFS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LInt;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LInt_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LMW;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LWZ;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_LWZ_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MFSPR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_MTSPR;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_ORI;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STAddr;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STAddrU;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STFD;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STFS;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STMW;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_STW;
import static org.jikesrvm.compilers.opt.ir.Operators.PPC_TAddr;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.ppc.PowerPCTrapOperand;
import org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.BYTES_IN_FLOAT;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.BYTES_IN_INT;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.CONDITION_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.DOUBLE_REG;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.DOUBLE_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.FIRST_SCRATCH_GPR;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.FLOAT_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.INT_REG;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.INT_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ppc.PhysicalRegisterConstants.LAST_SCRATCH_GPR;
import org.jikesrvm.compilers.opt.regalloc.GenericStackManager;
import org.jikesrvm.compilers.opt.util.Bits;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_ALIGNMENT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GUARD;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Class to manage the allocation of the "compiler-specific" portion of
 * the stackframe.  This class holds only the architecture-specific
 * functions.
 * <p>
 */
public abstract class StackManager extends GenericStackManager {

  /**
   * stack locaiton to save the XER register
   */
  private int saveXERLocation;
  /**
   * stack locaiton to save the CTR register
   */
  private int saveCTRLocation;

  /**
   * Return the size of the fixed portion of the stack.
   * @return size in bytes of the fixed portion of the stackframe
   */
  public final int getFrameFixedSize() {
    return frameSize;
  }

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  public final int allocateNewSpillLocation(int type) {
    int spillSize = PhysicalRegisterSet.getSpillSize(type);

    // Naturally align the spill pointer
    spillPointer = align(spillPointer, spillSize);

    // increment by the spill size
    spillPointer += spillSize;

    if (spillPointer > frameSize) {
      frameSize = spillPointer;
    }
    return spillPointer - spillSize;
  }

  /**
   * Clean up some junk that's left in the IR after register allocation,
   * and add epilogue code.
   */
  public void cleanUpAndInsertEpilogue() {
    Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    for (; inst != null; inst = inst.nextInstructionInCodeOrder()) {
      switch (inst.getOpcode()) {
        case PPC_MOVE_opcode:
        case PPC_FMR_opcode:
          // remove frivolous moves
          if (MIR_Move.getResult(inst).register.number == MIR_Move.getValue(inst).register.number) {
            inst = inst.remove();
          }
          break;
        case PPC_BLR_opcode:
          if (frameIsRequired()) {
            insertEpilogue(inst);
          }
          break;
        case PPC_LFS_opcode:
        case PPC_LFD_opcode:
        case PPC_LInt_opcode:
        case PPC_LWZ_opcode:
        case PPC_LAddr_opcode:
          // the following to handle spilled parameters
          // SJF: this is ugly.  clean it up someday.
          if (MIR_Load.getAddress(inst).register == ir.regpool.getPhysicalRegisterSet().getFP()) {
            Operand one = MIR_Load.getOffset(inst);
            if (one instanceof IntConstantOperand) {
              int offset = ((IntConstantOperand) one).value;
              if (offset <= -256) {
                if (frameIsRequired()) {
                  MIR_Load.setOffset(inst, IC(frameSize - offset - 256));
                } else {
                  MIR_Load.setOffset(inst, IC(-offset - 256));
                }
              }
            }
          }
        default:
          break;
      }
    }
  }

  /**
   * Insert a spill of a physical register before instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  public final void insertSpillBefore(Instruction s, Register r, byte type, int location) {

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register FP = phys.getFP();
    if (type == FLOAT_VALUE) {
      s.insertBefore(MIR_Store.create(PPC_STFS, F(r), A(FP), IC(location + BYTES_IN_ADDRESS - BYTES_IN_FLOAT)));
    } else if (type == DOUBLE_VALUE) {
      s.insertBefore(MIR_Store.create(PPC_STFD, D(r), A(FP), IC(location)));
    } else if (type == INT_VALUE) {      // integer or half of long
      s.insertBefore(MIR_Store.create(PPC_STAddr, A(r), A(FP), IC(location)));
    } else {
      throw new OptimizingCompilerException("insertSpillBefore", "unsupported type " + type);
    }
  }

  /**
   * Create an MIR instruction to move rhs into lhs
   */
  final Instruction makeMoveInstruction(Register lhs, Register rhs) {
    if (rhs.isFloatingPoint() && lhs.isFloatingPoint()) {
      return MIR_Move.create(PPC_FMR, D(lhs), D(rhs));
      //} else if (rhs.isInteger() && lhs.isInteger()) { // integer
    } else if (rhs.isAddress() && lhs.isAddress()) { // integer
      return MIR_Move.create(PPC_MOVE, A(lhs), A(rhs));
    } else {
      throw new OptimizingCompilerException("RegAlloc", "unknown register:", lhs.toString());
    }
  }

  /**
   * Insert a load of a physical register from a spill location before
   * instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  public final void insertUnspillBefore(Instruction s, Register r, byte type, int location) {

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register FP = phys.getFP();
    if (type == CONDITION_VALUE) {
      Register temp = phys.getTemp();
      s.insertBefore(MIR_Load.create(PPC_LWZ, I(temp), A(FP), IC(location + BYTES_IN_ADDRESS - BYTES_IN_INT)));
    } else if (type == DOUBLE_VALUE) {
      s.insertBefore(MIR_Load.create(PPC_LFD, D(r), A(FP), IC(location)));
    } else if (type == FLOAT_VALUE) {
      s.insertBefore(MIR_Load.create(PPC_LFS, F(r), A(FP), IC(location + BYTES_IN_ADDRESS - BYTES_IN_FLOAT)));
    } else if (type == INT_VALUE) { // integer or half of long
      s.insertBefore(MIR_Load.create(PPC_LAddr, A(r), A(FP), IC(location)));
    } else {
      throw new OptimizingCompilerException("insertUnspillBefore", "unknown type:" + type);
    }
  }

  /**
   * Insert the epilogue before a particular return instruction.
   *
   * @param ret the return instruction.
   */
  private void insertEpilogue(Instruction ret) {

    // 1. Restore any saved registers
    if (ir.compiledMethod.isSaveVolatile()) {
      restoreVolatileRegisters(ret);
    }
    restoreNonVolatiles(ret);

    // 2. Restore return address
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register temp = phys.getTemp();
    Register FP = phys.getFP();
    ret.insertBefore(MIR_Load.create(PPC_LAddr, A(temp), A(FP), IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET + frameSize)));

    // 3. Load return address into LR
    ret.insertBefore(MIR_Move.create(PPC_MTSPR, A(phys.getLR()), A(phys.getTemp())));

    // 4. Restore old FP
    ret.insertBefore(MIR_Binary.create(PPC_ADDI, A(FP), A(FP), IC(frameSize)));

  }

  /**
   * Insert code in the prologue to save the
   * volatile registers.
   *
   * @param inst
   */
  private void saveVolatiles(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // 1. save the volatile GPRs
    Register FP = phys.getFP();
    int i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileGPRLocation[i];
      inst.insertBefore(MIR_Store.create(PPC_STAddr, A(r), A(FP), IC(location)));
    }
    // 2. save the volatile FPRs
    i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileFPRLocation[i];
      inst.insertBefore(MIR_Store.create(PPC_STFD, D(r), A(FP), IC(location)));
    }

    // 3. Save some special registers
    Register temp = phys.getTemp();

    inst.insertBefore(MIR_Move.create(PPC_MFSPR, I(temp), I(phys.getXER())));
    inst.insertBefore(MIR_Store.create(PPC_STW, I(temp), A(FP), IC(saveXERLocation)));

    inst.insertBefore(MIR_Move.create(PPC_MFSPR, A(temp), A(phys.getCTR())));
    inst.insertBefore(MIR_Store.create(PPC_STAddr, A(temp), A(FP), IC(saveCTRLocation)));

  }

  /**
   * Insert code into the prologue to save any used non-volatile
   * registers.
   *
   * @param inst the first instruction after the prologue.
   */
  private void saveNonVolatiles(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();
    if (ir.compiledMethod.isSaveVolatile()) {
      // pretend we use all non-volatiles
      nNonvolatileGPRS = PhysicalRegisterSet.getNumberOfNonvolatileGPRs();
    }

    // 1. save the nonvolatile GPRs
    int n = nNonvolatileGPRS - 1;
    Register FP = phys.getFP();
    if (VM.BuildFor32Addr && n > MULTIPLE_CUTOFF) {
      // use a stm
      Register nv = null;
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        nv = e.nextElement();
      }
      n++;
      RegisterOperand range = I(nv);
      // YUCK!!! Why is this crap in register operand??
      int offset = getNonvolatileGPROffset(n);
      inst.insertBefore(MIR_Store.create(PPC_STMW, range, A(FP), IC(offset)));
    } else {
      // use a sequence of load instructions
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        Register nv = e.nextElement();
        int offset = getNonvolatileGPROffset(n);
        inst.insertBefore(MIR_Store.create(PPC_STAddr, A(nv), A(FP), IC(offset)));
      }
    }
    // 1. save the nonvolatile FPRs
    if (ir.compiledMethod.isSaveVolatile()) {
      // DANGER: as an optimization, we assume that a SaveVolatile method
      // will never use nonvolatile FPRs.
      // this invariant is not checked!!!!!
      // TODO: We really need some way to verify that this is true.
    } else {
      int nNonvolatileFPRS = ir.compiledMethod.getNumberOfNonvolatileFPRs();
      n = nNonvolatileFPRS - 1;
      // use a sequence of load instructions
      for (Enumeration<Register> e = phys.enumerateNonvolatileFPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        Register nv = e.nextElement();
        int offset = getNonvolatileFPROffset(n);
        inst.insertBefore(MIR_Store.create(PPC_STFD, D(nv), A(FP), IC(offset)));
      }
    }
  }

  /**
   * Insert code before a return instruction to restore the nonvolatile
   * registers.
   *
   * @param inst the return instruction
   */
  private void restoreNonVolatiles(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();

    // 1. restore the nonvolatile GPRs
    int n = nNonvolatileGPRS - 1;
    Register FP = phys.getFP();
    if (VM.BuildFor32Addr && n > MULTIPLE_CUTOFF) {
      // use an lm
      Register nv = null;
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        nv = e.nextElement();
      }
      n++;
      RegisterOperand range = I(nv);
      // YUCK!!! Why is this crap in register operand??
      int offset = getNonvolatileGPROffset(n);
      inst.insertBefore(MIR_Load.create(PPC_LMW, range, A(FP), IC(offset)));
    } else {
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        Register nv = e.nextElement();
        int offset = getNonvolatileGPROffset(n);
        inst.insertBefore(MIR_Load.create(PPC_LAddr, A(nv), A(FP), IC(offset)));
      }
    }
    // Note that save-volatiles are forbidden from using nonvolatile FPRs.
    if (!ir.compiledMethod.isSaveVolatile()) {
      // 1. restore the nonvolatile FPRs
      int nNonvolatileFPRS = ir.compiledMethod.getNumberOfNonvolatileFPRs();
      n = nNonvolatileFPRS - 1;
      // use a sequence of load instructions
      for (Enumeration<Register> e = phys.enumerateNonvolatileFPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
        Register nv = e.nextElement();
        int offset = getNonvolatileFPROffset(n);
        inst.insertBefore(MIR_Load.create(PPC_LFD, D(nv), A(FP), IC(offset)));
      }
    }
  }

  /**
   * Insert code before a return instruction to restore the
   * volatile registers.
   *
   * @param inst the return instruction
   */
  private void restoreVolatileRegisters(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // 1. restore the volatile GPRs
    Register FP = phys.getFP();
    int i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileGPRLocation[i];
      inst.insertBefore(MIR_Load.create(PPC_LAddr, A(r), A(FP), IC(location)));
    }
    // 2. restore the volatile FPRs
    i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileFPRLocation[i];
      inst.insertBefore(MIR_Load.create(PPC_LFD, D(r), A(FP), IC(location)));
    }
    // 3. Restore some special registers
    Register temp = phys.getTemp();
    inst.insertBefore(MIR_Load.create(PPC_LInt, I(temp), A(FP), IC(saveXERLocation)));
    inst.insertBefore(MIR_Move.create(PPC_MTSPR, I(phys.getXER()), I(temp)));

    inst.insertBefore(MIR_Load.create(PPC_LAddr, A(temp), A(FP), IC(saveCTRLocation)));
    inst.insertBefore(MIR_Move.create(PPC_MTSPR, A(phys.getCTR()), A(temp)));
  }

  /*
  * Insert the prologue.
  * The available scratch registers are normally: R0, S0, S1
  * However, if this is the prologue for a 'save volatile' frame,
  * then R0 is the only available scratch register.
  * The "normal" prologue must perform the following tasks:
  *    stack overflow check
  *    set TSR for the yieldpoint if there is a prologue yieldpoint instruction
  *    save lr
  *    store cmid
  *    buy stack frame
  *    store any used non volatiles
  * We schedule the prologue for this combination of operations,
  * since it is currently the common case.
  * When this changes, this code should be modifed accordingly.
  * The desired sequence is:
  *  1    mflr    00  # return addr
  *  2    l       S1 takeYieldpointOffset(PR)                # setting TSR for yield point
  *  3    stu     FP -frameSize(FP)                          # buy frame, save caller's fp
  *  4    l       S0 stackLimitOffset(S0)                    # stack overflow check
  *  5    <save used non volatiles>
  *  6    cmpi    TSR S1 0x0                                 # setting TSR for yield point (S1 is now free)
  *  7    lil     S1 CMID                                    # cmid
  *  8    st      00 STACKFRAME_NEXT_INSTRUCTION_OFFSET(FP)  # return addr (00 is now free)
  *  9    st      S1 STACKFRAME_METHOD_ID_OFFSET(FP)         # cmid
  *  10   tgt     S0, FP                                     # stack overflow check (already bought frame)
  */

  /**
   * Schedule prologue for 'normal' case (see above)
   */
  public final void insertNormalPrologue() {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register FP = phys.getFP();
    Register TR = phys.getTR();
    Register TSR = phys.getTSR();
    Register R0 = phys.getTemp();
    Register S0 = phys.getGPR(FIRST_SCRATCH_GPR);
    Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = hasPrologueYieldpoint();

    int frameFixedSize = getFrameFixedSize();
    ir.compiledMethod.setFrameFixedSize(frameFixedSize);

    if (frameFixedSize >= STACK_SIZE_GUARD || ir.compiledMethod.isSaveVolatile()) {
      insertExceptionalPrologue();
      return;
    }

    Instruction ptr = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, A(R0), A(phys.getLR()))); // 1

    if (yp) {
      Offset offset = Entrypoints.takeYieldpointField.getOffset();
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
      ptr.insertBefore(MIR_Load.create(PPC_LInt, I(S1), A(TR), IC(Bits.PPCMaskLower16(offset)))); // 2
    }

    ptr.insertBefore(MIR_StoreUpdate.create(PPC_STAddrU, A(FP), A(FP), IC(-frameSize))); // 3

    if (stackOverflow) {
      Offset offset = Entrypoints.stackLimitField.getOffset();
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S0), A(phys.getTR()), IC(Bits.PPCMaskLower16(offset)))); // 4
    }

    // Now add any instructions to save the volatiles and nonvolatiles (5)
    saveNonVolatiles(ptr);

    if (yp) {
      ptr.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(S1), IC(0))); // 6
    }
    int cmid = ir.compiledMethod.getId();
    if (cmid <= 0x7fff) {
      ptr.insertBefore(MIR_Unary.create(PPC_LDI, I(S1), IC(cmid))); // 7
    } else {
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, I(S1), IC(cmid >>> 16))); // 7 (a)
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, I(S1), I(S1), IC(cmid & 0xffff))); // 7 (b)
    }
    ptr.insertBefore(MIR_Store.create(PPC_STAddr,
                                      A(R0),
                                      A(FP),
                                      IC(frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET))); // 8
    ptr.insertBefore(MIR_Store.create(PPC_STW, I(S1), A(FP), IC(STACKFRAME_METHOD_ID_OFFSET))); // 9

    if (stackOverflow) {
      // Mutate the Prologue instruction into the trap
      MIR_Trap.mutate(ptr,
                      PPC_TAddr,
                      PowerPCTrapOperand.GREATER(),
                      A(S0),
                      A(FP),
                      TrapCodeOperand.StackOverflow()); // 10
    } else {
      // no stack overflow test, so we remove the IR_Prologue instruction
      ptr.remove();
    }
  }

  /**
   * prologue for the exceptional case.
   * (1) R0 is the only available scratch register.
   * (2) stack overflow check has to come first.
   */
  final void insertExceptionalPrologue() {
    if (VM.VerifyAssertions) {
      VM._assert((frameSize & (STACKFRAME_ALIGNMENT - 1)) == 0, "Stack frame alignment error");
    }
    if (frameSize >= 0x7ff0) {
      throw new OptimizingCompilerException("Stackframe size exceeded!");
    }

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register FP = phys.getFP();
    Register TR = phys.getTR();
    Register TSR = phys.getTSR();
    Register R0 = phys.getTemp();
    Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = hasPrologueYieldpoint();

    Instruction ptr = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    // Stack overflow check
    if (stackOverflow) {
      // R0 is fairly useless (can't be operand 1 of an addi or the base ptr
      // of a load) so, free up S1 for use by briefly saving its contents in the
      // return address slot of my caller's frame
      ptr.insertBefore(MIR_Store.create(PPC_STAddr, A(S1), A(FP), IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));
      Offset offset = Entrypoints.stackLimitField.getOffset();
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S1), A(phys.getTR()), IC(Bits.PPCMaskLower16(offset))));
      ptr.insertBefore(MIR_Binary.create(PPC_ADDI, A(R0), A(S1), IC(frameSize)));
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S1), A(FP), IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));

      // Mutate the Prologue holder instruction into the trap
      MIR_Trap.mutate(ptr, PPC_TAddr, PowerPCTrapOperand.LESS(), A(FP), A(R0), TrapCodeOperand.StackOverflow());

      // advance ptr because we want the remaining instructions to come after
      // the trap
      ptr = ptr.nextInstructionInCodeOrder();

    } else {
      // no stack overflow test, so we must remove the IR_Prologue instruction
      Instruction next = ptr.nextInstructionInCodeOrder();
      ptr.remove();
      ptr = next;
    }

    // Buy stack frame, save LR, caller's FP
    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, A(R0), A(phys.getLR())));
    ptr.insertBefore(MIR_StoreUpdate.create(PPC_STAddrU, A(FP), A(FP), IC(-frameSize)));
    ptr.insertBefore(MIR_Store.create(PPC_STAddr, A(R0), A(FP), IC(frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET)));

    // Store cmid
    int cmid = ir.compiledMethod.getId();
    if (cmid <= 0x7fff) {
      ptr.insertBefore(MIR_Unary.create(PPC_LDI, I(R0), IC(cmid)));
    } else {
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, I(R0), IC(cmid >>> 16)));
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, I(R0), I(R0), IC(cmid & 0xffff)));
    }
    ptr.insertBefore(MIR_Store.create(PPC_STW, I(R0), A(FP), IC(STACKFRAME_METHOD_ID_OFFSET)));

    // Now save the volatile/nonvolatile registers
    if (ir.compiledMethod.isSaveVolatile()) {
      saveVolatiles(ptr);
    }
    saveNonVolatiles(ptr);

    // Threadswitch
    if (yp) {
      Offset offset = Entrypoints.takeYieldpointField.getOffset();
      if (VM.VerifyAssertions) VM._assert(Bits.fits(offset, 16));
      ptr.insertBefore(MIR_Load.create(PPC_LInt, I(R0), A(TR), IC(Bits.PPCMaskLower16(offset))));
      ptr.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(R0), IC(0)));
    }
  }

  /**
   * Compute the number of stack words needed to hold nonvolatile
   * registers.
   *
   * Side effects:
   * <ul>
   * <li> updates the OptCompiler structure
   * <li> updates the <code>frameSize</code> field of this object
   * <li> updates the <code>frameRequired</code> field of this object
   * </ul>
   */
  public void computeNonVolatileArea() {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    if (ir.compiledMethod.isSaveVolatile()) {
      // Record that we use every nonvolatile GPR
      int numGprNv = PhysicalRegisterSet.getNumberOfNonvolatileGPRs();
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short) numGprNv);

      // set the frame size
      frameSize += numGprNv * BYTES_IN_ADDRESS;

      int numFprNv = PhysicalRegisterSet.getNumberOfNonvolatileFPRs();
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short) numFprNv);
      frameSize += numFprNv * BYTES_IN_DOUBLE;

      // Record that we need a stack frame.
      setFrameRequired();

      // Map each volatile GPR to a spill location.
      int i = 0;
      for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
        e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        saveVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);
      }

      // Map each non-volatile GPR register to a spill location.
      i = 0;
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements(); i++) {
        e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        nonVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);
      }

      // Map some special registers to spill locations.
      saveXERLocation = allocateNewSpillLocation(INT_REG);
      saveCTRLocation = allocateNewSpillLocation(INT_REG);
      i = 0;

      for (Enumeration<Register> e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); i++) {
        e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        saveVolatileFPRLocation[i] = allocateNewSpillLocation(DOUBLE_REG);
      }

      // Set the offset to find non-volatiles.
      int gprOffset = getNonvolatileGPROffset(0);
      ir.compiledMethod.setUnsignedNonVolatileOffset(gprOffset);
    } else {
      // Count the number of nonvolatiles used.
      int numGprNv = 0;
      int i = 0;
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements();) {
        Register r = e.nextElement();
        if (r.isTouched()) {
          // Note that as a side effect, the following call bumps up the
          // frame size.
          nonVolatileGPRLocation[i++] = allocateNewSpillLocation(INT_REG);
          numGprNv++;
        }
      }
      i = 0;
      int numFprNv = 0;
      for (Enumeration<Register> e = phys.enumerateNonvolatileFPRs(); e.hasMoreElements();) {
        Register r = e.nextElement();
        if (r.isTouched()) {
          // Note that as a side effect, the following call bumps up the
          // frame size.
          nonVolatileFPRLocation[i++] = allocateNewSpillLocation(DOUBLE_REG);
          numFprNv++;
        }
      }
      // Update the OptCompiledMethod object.
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short) numGprNv);
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short) numFprNv);
      if (numGprNv > 0 || numFprNv > 0) {
        int gprOffset = getNonvolatileGPROffset(0);
        ir.compiledMethod.setUnsignedNonVolatileOffset(gprOffset);
        // record that we need a stack frame
        setFrameRequired();
      } else {
        ir.compiledMethod.setUnsignedNonVolatileOffset(0);
      }
    }
    frameSize = align(frameSize, STACKFRAME_ALIGNMENT);
  }

  /**
   * Walk over the currently available scratch registers.
   *
   * <p>For any scratch register r which is def'ed by instruction s,
   * spill r before s and remove r from the pool of available scratch
   * registers.
   *
   * <p>For any scratch register r which is used by instruction s,
   * restore r before s and remove r from the pool of available scratch
   * registers.
   *
   * <p>For any scratch register r which has current contents symb, and
   * symb is spilled to location M, and s defs M: the old value of symb is
   * dead.  Mark this.
   *
   * <p>Invalidate any scratch register assignments that are illegal in s.
   */
  public void restoreScratchRegistersBefore(Instruction s) {
    for (Iterator<ScratchRegister> i = scratchInUse.iterator(); i.hasNext();) {
      ScratchRegister scratch = i.next();

      if (scratch.currentContents == null) continue;
      if (VERBOSE_DEBUG) {
        System.out.println("RESTORE: consider " + scratch);
      }
      boolean removed = false;
      boolean unloaded = false;
      if (definedIn(scratch.scratch, s) ||
          (s.isCall() && s.operator != CALL_SAVE_VOLATILE && scratch.scratch.isVolatile())) {
        // s defines the scratch register, so save its contents before they
        // are killed.
        if (VERBOSE_DEBUG) {
          System.out.println("RESTORE : unload because defined " + scratch);
        }
        unloadScratchRegisterBefore(s, scratch);

        // update mapping information
        if (VERBOSE_DEBUG) {
          System.out.println("RSRB: End scratch interval " + scratch.scratch + " " + s);
        }
        scratchMap.endScratchInterval(scratch.scratch, s);
        Register scratchContents = scratch.currentContents;
        if (scratchContents != null) {
          if (VERBOSE_DEBUG) {
            System.out.println("RSRB: End symbolic interval " + scratch.currentContents + " " + s);
          }
          scratchMap.endSymbolicInterval(scratch.currentContents, s);
        }

        i.remove();
        removed = true;
        unloaded = true;
      }

      if (usedIn(scratch.scratch, s) || !isLegal(scratch.currentContents, scratch.scratch, s)) {
        // first spill the currents contents of the scratch register to
        // memory
        if (!unloaded) {
          if (VERBOSE_DEBUG) {
            System.out.println("RESTORE : unload because used " + scratch);
          }
          unloadScratchRegisterBefore(s, scratch);

          // update mapping information
          if (VERBOSE_DEBUG) {
            System.out.println("RSRB2: End scratch interval " + scratch.scratch + " " + s);
          }
          scratchMap.endScratchInterval(scratch.scratch, s);
          Register scratchContents = scratch.currentContents;
          if (scratchContents != null) {
            if (VERBOSE_DEBUG) {
              System.out.println("RSRB2: End symbolic interval " + scratch.currentContents + " " + s);
            }
            scratchMap.endSymbolicInterval(scratch.currentContents, s);
          }

        }
        // s or some future instruction uses the scratch register,
        // so restore the correct contents.
        if (VERBOSE_DEBUG) {
          System.out.println("RESTORE : reload because used " + scratch);
        }
        reloadScratchRegisterBefore(s, scratch);

        if (!removed) {
          i.remove();
          removed = true;
        }
      }
    }
  }

  protected static final int MULTIPLE_CUTOFF = 4;

  /**
   * Initializes the "tmp" regs for this object
   * @param ir the governing ir
   */
  public final void initForArch(IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    phys.getJTOC().reserveRegister();
    phys.getFirstConditionRegister().reserveRegister();
  }

  /**
   * Is a particular instruction a system call?
   */
  public boolean isSysCall(Instruction s) {
    return s.operator == PPC_BCTRL_SYS || s.operator == PPC_BL_SYS;
  }

  /**
   * Given symbolic register r in instruction s, do we need to ensure that
   * r is in a scratch register is s (as opposed to a memory operand)
   */
  public boolean needScratch(Register r, Instruction s) {
    if (s.operator == YIELDPOINT_OSR) return false;

    // PowerPC does not support memory operands.
    return true;
  }

  /**
   * In instruction s, replace all appearances of a symbolic register
   * operand with uses of the appropriate spill location, as cached by the
   * register allocator.
   *
   * @param s the instruction to mutate.
   * @param symb the symbolic register operand to replace
   */
  public void replaceOperandWithSpillLocation(Instruction s, RegisterOperand symb) {
    // PowerPC does not support memory operands.
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }
}
