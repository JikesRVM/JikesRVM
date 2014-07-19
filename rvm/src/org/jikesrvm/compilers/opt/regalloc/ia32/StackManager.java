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

import java.util.Enumeration;
import java.util.Iterator;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_FSave;
import org.jikesrvm.compilers.opt.ir.MIR_Lea;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Nullary;
import org.jikesrvm.compilers.opt.ir.MIR_TrapIf;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import static org.jikesrvm.SizeConstants.*;
import static org.jikesrvm.compilers.opt.ir.Operators.ADVISE_ESP;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_SAVE_VOLATILE;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCLEAR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FMOV_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FNINIT;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FNSAVE;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FRSTOR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_LEA;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVQ;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSD;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSS;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOV_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_POP;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_PUSH;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_RET_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SYSCALL;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_TRAPIF;
import static org.jikesrvm.compilers.opt.ir.Operators.NOP;
import static org.jikesrvm.compilers.opt.ir.Operators.REQUIRE_ESP;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_BACKEDGE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_EPILOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_PROLOGUE;
import static org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants.DOUBLE_REG;
import static org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants.DOUBLE_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants.FLOAT_VALUE;
import static org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants.INT_REG;
import static org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants.INT_VALUE;

import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse;
import org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.StackLocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.IA32ConditionOperand;
import org.jikesrvm.compilers.opt.regalloc.GenericStackManager;
import org.jikesrvm.compilers.opt.regalloc.RegisterAllocatorState;
import org.jikesrvm.ia32.ArchConstants;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_ALIGNMENT;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Class to manage the allocation of the "compiler-specific" portion of
 * the stackframe.  This class holds only the architecture-specific
 * functions.
 */
public abstract class StackManager extends GenericStackManager {

  /**
   * A frame offset for 108 bytes of stack space to store the
   * floating point state in the SaveVolatile protocol.
   */
  private int fsaveLocation;

  /**
   * We allow the stack pointer to float from its normal position at the
   * bottom of the frame.  This field holds the 'current' offset of the
   * SP.
   */
  private int ESPOffset = 0;

  /**
   * Should we allow the stack pointer to float in order to avoid scratch
   * registers in move instructions.  Note: as of Feb. 02, we think this
   * is a bad idea.
   */
  private static boolean FLOAT_ESP = false;

  @Override
  public final int getFrameFixedSize() {
    return frameSize - WORDSIZE;
  }

  /**
   * Return the size of a type of value, in bytes.
   * NOTE: For the purpose of register allocation, an x87 FLOAT_VALUE is 64 bits!
   *
   * @param type one of INT_VALUE, FLOAT_VALUE, or DOUBLE_VALUE
   */
  private static byte getSizeOfType(byte type) {
    switch (type) {
      case INT_VALUE:
        return (byte) (WORDSIZE);
      case FLOAT_VALUE:
        if (ArchConstants.SSE2_FULL) return (byte) BYTES_IN_FLOAT;
      case DOUBLE_VALUE:
        return (byte) BYTES_IN_DOUBLE;
      default:
        OptimizingCompilerException.TODO("getSizeOfValue: unsupported");
        return 0;
    }
  }

  /**
   * Return the move operator for a type of value.
   *
   * @param type one of INT_VALUE, FLOAT_VALUE, or DOUBLE_VALUE
   */
  private static Operator getMoveOperator(byte type) {
    switch (type) {
      case INT_VALUE:
        return IA32_MOV;
      case DOUBLE_VALUE:
        if (ArchConstants.SSE2_FULL) return IA32_MOVSD;
      case FLOAT_VALUE:
        if (ArchConstants.SSE2_FULL) return IA32_MOVSS;
        return IA32_FMOV;
      default:
        OptimizingCompilerException.TODO("getMoveOperator: unsupported");
        return null;
    }
  }

  @Override
  public final int allocateNewSpillLocation(int type) {

    // increment by the spill size
    spillPointer += PhysicalRegisterSet.getSpillSize(type);

    if (spillPointer + WORDSIZE > frameSize) {
      frameSize = spillPointer + WORDSIZE;
    }
    return spillPointer;
  }

  @Override
  public final void insertSpillBefore(Instruction s, Register r, byte type, int location) {

    Operator move = getMoveOperator(type);
    byte size = getSizeOfType(type);
    RegisterOperand rOp;
    switch (type) {
      case FLOAT_VALUE:
        rOp = F(r);
        break;
      case DOUBLE_VALUE:
        rOp = D(r);
        break;
      default:
        rOp = new RegisterOperand(r, TypeReference.Int);
        break;
    }
    StackLocationOperand spill = new StackLocationOperand(true, -location, size);
    s.insertBefore(MIR_Move.create(move, spill, rOp));
  }

  @Override
  public final void insertUnspillBefore(Instruction s, Register r, byte type, int location) {
    Operator move = getMoveOperator(type);
    byte size = getSizeOfType(type);
    RegisterOperand rOp;
    switch (type) {
      case FLOAT_VALUE:
        rOp = F(r);
        break;
      case DOUBLE_VALUE:
        rOp = D(r);
        break;
      default:
        rOp = new RegisterOperand(r, TypeReference.Int);
        break;
    }
    StackLocationOperand spill = new StackLocationOperand(true, -location, size);
    s.insertBefore(MIR_Move.create(move, rOp, spill));
  }

  @Override
  public void computeNonVolatileArea() {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    if (ir.compiledMethod.isSaveVolatile()) {
      // Record that we use every nonvolatile GPR
      int numGprNv = PhysicalRegisterSet.getNumberOfNonvolatileGPRs();
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short) numGprNv);

      // set the frame size
      frameSize += numGprNv * WORDSIZE;
      frameSize = align(frameSize, STACKFRAME_ALIGNMENT);

      // TODO!!
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short) 0);

      // Record that we need a stack frame.
      setFrameRequired();

      if (ArchConstants.SSE2_FULL) {
        for(int i=0; i < 8; i++) {
          fsaveLocation = allocateNewSpillLocation(DOUBLE_REG);
        }
      } else {
        // Grab 108 bytes (same as 27 4-byte spills) in the stack
        // frame, as a place to store the floating-point state with FSAVE
        for (int i = 0; i < 27; i++) {
          fsaveLocation = allocateNewSpillLocation(INT_REG);
        }
      }

      // Map each volatile register to a spill location.
      int i = 0;
      for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
        e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        saveVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);
      }

      // Map each non-volatile register to a spill location.
      i = 0;
      for (Enumeration<Register> e = phys.enumerateNonvolatileGPRs(); e.hasMoreElements(); i++) {
        e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        nonVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);
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
      // Update the OptCompiledMethod object.
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short) numGprNv);
      if (numGprNv > 0) {
        int gprOffset = getNonvolatileGPROffset(0);
        ir.compiledMethod.setUnsignedNonVolatileOffset(gprOffset);
        // record that we need a stack frame
        setFrameRequired();
      } else {
        ir.compiledMethod.setUnsignedNonVolatileOffset(0);
      }

      ir.compiledMethod.setNumberOfNonvolatileFPRs((short) 0);

    }
  }

  @Override
  public void cleanUpAndInsertEpilogue() {

    Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    for (; inst != null; inst = inst.nextInstructionInCodeOrder()) {
      switch (inst.getOpcode()) {
        case IA32_MOV_opcode:
          // remove frivolous moves
          Operand result = MIR_Move.getResult(inst);
          Operand val = MIR_Move.getValue(inst);
          if (result.similar(val)) {
            inst = inst.remove();
          }
          break;
        case IA32_FMOV_opcode:
        case IA32_MOVSS_opcode:
        case IA32_MOVSD_opcode:
          // remove frivolous moves
          result = MIR_Move.getResult(inst);
          val = MIR_Move.getValue(inst);
          if (result.similar(val)) {
            inst = inst.remove();
          }
          break;
        case IA32_RET_opcode:
          if (frameIsRequired()) {
            insertEpilogue(inst);
          }
        default:
          break;
      }
    }
    // now that the frame size is fixed, fix up the spill location code
    rewriteStackLocations();
  }

  /**
   * Insert an explicit stack overflow check in the prologue <em>after</em>
   * buying the stack frame.<p>
   *
   * SIDE EFFECT: mutates the plg into a trap instruction.  We need to
   * mutate so that the trap instruction is in the GC map data structures.
   *
   * @param plg the prologue instruction
   */
  private void insertNormalStackOverflowCheck(Instruction plg) {
    if (!ir.method.isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.compiledMethod.isSaveVolatile()) {
      return;
    }

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register ESP = phys.getESP();
    MemoryOperand M =
        MemoryOperand.BD(ir.regpool.makeTROp(),
                             Entrypoints.stackLimitField.getOffset(),
                             (byte) WORDSIZE,
                             null,
                             null);

    //    Trap if ESP <= active Thread Stack Limit
    MIR_TrapIf.mutate(plg,
                      IA32_TRAPIF,
                      null,
                      new RegisterOperand(ESP, TypeReference.Int),
                      M,
                      IA32ConditionOperand.LE(),
                      TrapCodeOperand.StackOverflow());
  }

  /**
   * Insert an explicit stack overflow check in the prologue <em>before</em>
   * buying the stack frame.
   * SIDE EFFECT: mutates the plg into a trap instruction.  We need to
   * mutate so that the trap instruction is in the GC map data structures.
   *
   * @param plg the prologue instruction
   */
  private void insertBigFrameStackOverflowCheck(Instruction plg) {
    if (!ir.method.isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.compiledMethod.isSaveVolatile()) {
      return;
    }

    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register ESP = phys.getESP();
    Register ECX = phys.getECX();

    //    ECX := active Thread Stack Limit
    MemoryOperand M =
        MemoryOperand.BD(ir.regpool.makeTROp(),
                             Entrypoints.stackLimitField.getOffset(),
                             (byte) WORDSIZE,
                             null,
                             null);
    plg.insertBefore(MIR_Move.create(IA32_MOV, new RegisterOperand((ECX), TypeReference.Int), M));

    //    ECX += frame Size
    int frameSize = getFrameFixedSize();
    plg.insertBefore(MIR_BinaryAcc.create(IA32_ADD, new RegisterOperand(ECX, TypeReference.Int), IC(frameSize)));
    //    Trap if ESP <= ECX
    MIR_TrapIf.mutate(plg,
                      IA32_TRAPIF,
                      null,
                      new RegisterOperand(ESP, TypeReference.Int),
                      new RegisterOperand(ECX, TypeReference.Int),
                      IA32ConditionOperand.LE(),
                      TrapCodeOperand.StackOverflow());
  }

  /**
   * Insert the prologue for a normal method.
   *
   * Assume we are inserting the prologue for method B called from method
   * A.
   *    <ul>
   *    <li> Perform a stack overflow check.
   *    <li> Store a back pointer to A's frame
   *    <li> Store B's compiled method id
   *    <li> Adjust frame pointer to point to B's frame
   *    <li> Save any used non-volatile registers
   *    </ul>
   */
  @Override
  public void insertNormalPrologue() {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register ESP = phys.getESP();
    MemoryOperand fpHome =
        MemoryOperand.BD(ir.regpool.makeTROp(),
                             ArchEntrypoints.framePointerField.getOffset(),
                             (byte) WORDSIZE,
                             null,
                             null);

    // the prologue instruction
    Instruction plg = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    // inst is the instruction immediately after the IR_PROLOGUE
    // instruction
    Instruction inst = plg.nextInstructionInCodeOrder();

    int frameFixedSize = getFrameFixedSize();
    ir.compiledMethod.setFrameFixedSize(frameFixedSize);

    // I. Buy a stackframe (including overflow check)
    // NOTE: We play a little game here.  If the frame we are buying is
    //       very small (less than 256) then we can be sloppy with the
    //       stackoverflow check and actually allocate the frame in the guard
    //       region.  We'll notice when this frame calls someone and take the
    //       stackoverflow in the callee. We can't do this if the frame is too big,
    //       because growing the stack in the callee and/or handling a hardware trap
    //       in this frame will require most of the guard region to complete.
    //       See libvm.C.
    if (frameFixedSize >= 256) {
      // 1. Insert Stack overflow check.
      insertBigFrameStackOverflowCheck(plg);

      // 2. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, fpHome));

      // 3. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, fpHome.copy(), new RegisterOperand(ESP, TypeReference.Int)));

      // 4. Store my compiled method id
      int cmid = ir.compiledMethod.getId();
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, IC(cmid)));
    } else {
      // 1. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, fpHome));

      // 2. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, fpHome.copy(), new RegisterOperand(ESP, TypeReference.Int)));

      // 3. Store my compiled method id
      int cmid = ir.compiledMethod.getId();
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, IC(cmid)));

      // 4. Insert Stack overflow check.
      insertNormalStackOverflowCheck(plg);
    }

    // II. Save any used volatile and non-volatile registers
    if (ir.compiledMethod.isSaveVolatile()) {
      saveVolatiles(inst);
      saveFloatingPointState(inst);
    }
    saveNonVolatiles(inst);
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

    // Save each non-volatile GPR used by this method.
    int n = nNonvolatileGPRS - 1;
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
      Register nv = e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      Operand M = new StackLocationOperand(true, -offset, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, M, new RegisterOperand(nv, TypeReference.Int)));
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

    int n = nNonvolatileGPRS - 1;
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements() && n >= 0; n--) {
      Register nv = e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      Operand M = new StackLocationOperand(true, -offset, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, new RegisterOperand(nv, TypeReference.Int), M));
    }
  }

  /**
   * Insert code into the prologue to save the floating point state.
   *
   * @param inst the first instruction after the prologue.
   */
  private void saveFloatingPointState(Instruction inst) {

    if (ArchConstants.SSE2_FULL) {
      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      for (int i=0; i < 8; i++) {
        inst.insertBefore(MIR_Move.create(IA32_MOVQ,
            new StackLocationOperand(true, -fsaveLocation + (i * BYTES_IN_DOUBLE), BYTES_IN_DOUBLE),
            new RegisterOperand(phys.getFPR(i), TypeReference.Double)));
      }
    } else {
      Operand M = new StackLocationOperand(true, -fsaveLocation, 4);
      inst.insertBefore(MIR_FSave.create(IA32_FNSAVE, M));
    }
  }

  /**
   * Insert code into the epilogue to restore the floating point state.
   *
   * @param inst the return instruction after the epilogue.
   */
  private void restoreFloatingPointState(Instruction inst) {
    if (ArchConstants.SSE2_FULL) {
      PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
      for (int i=0; i < 8; i++) {
        inst.insertBefore(MIR_Move.create(IA32_MOVQ,
            new RegisterOperand(phys.getFPR(i), TypeReference.Double),
            new StackLocationOperand(true, -fsaveLocation + (i * BYTES_IN_DOUBLE), BYTES_IN_DOUBLE)));
      }
    } else {
      Operand M = new StackLocationOperand(true, -fsaveLocation, 4);
      inst.insertBefore(MIR_FSave.create(IA32_FRSTOR, M));
    }
  }

  /**
   * Insert code into the prologue to save all volatile
   * registers.
   *
   * @param inst the first instruction after the prologue.
   */
  private void saveVolatiles(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // Save each GPR.
    int i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileGPRLocation[i];
      Operand M = new StackLocationOperand(true, -location, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, M, new RegisterOperand(r, TypeReference.Int)));
    }
  }

  /**
   * Insert code before a return instruction to restore the volatile
   * and volatile registers.
   *
   * @param inst the return instruction
   */
  private void restoreVolatileRegisters(Instruction inst) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // Restore every GPR
    int i = 0;
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements(); i++) {
      Register r = e.nextElement();
      int location = saveVolatileGPRLocation[i];
      Operand M = new StackLocationOperand(true, -location, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, new RegisterOperand(r, TypeReference.Int), M));
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
      restoreFloatingPointState(ret);
    }
    restoreNonVolatiles(ret);

    // 2. Restore caller's stackpointer and framepointer
    int frameSize = getFrameFixedSize();
    ret.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(frameSize)));
    MemoryOperand fpHome =
        MemoryOperand.BD(ir.regpool.makeTROp(),
                             ArchEntrypoints.framePointerField.getOffset(),
                             (byte) WORDSIZE,
                             null,
                             null);
    ret.insertBefore(MIR_Nullary.create(IA32_POP, fpHome));
  }

  @Override
  public void replaceOperandWithSpillLocation(Instruction s, RegisterOperand symb) {

    // Get the spill location previously assigned to the symbolic
    // register.
    int location = RegisterAllocatorState.getSpill(symb.getRegister());

    // Create a memory operand M representing the spill location.
    int size;
    if (ArchConstants.SSE2_FULL) {
      size = symb.getType().getMemoryBytes();
      if (size < 4)
        size = 4;
    } else {
      int type = PhysicalRegisterSet.getPhysicalRegisterType(symb.getRegister());
      size = PhysicalRegisterSet.getSpillSize(type);
    }
    StackLocationOperand M = new StackLocationOperand(true, -location, (byte) size);

    M = new StackLocationOperand(true, -location, (byte) size);

    // replace the register operand with the memory operand
    s.replaceOperand(symb, M);
  }

  /**
   * Does a memory operand hold a symbolic register?
   */
  private boolean hasSymbolicRegister(MemoryOperand M) {
    if (M.base != null && !M.base.getRegister().isPhysical()) return true;
    if (M.index != null && !M.index.getRegister().isPhysical()) return true;
    return false;
  }

  /**
   * Is s a MOVE instruction that can be generated without resorting to
   * scratch registers?
   */
  private boolean isScratchFreeMove(Instruction s) {
    if (s.operator() != IA32_MOV) return false;

    // if we don't allow ESP to float, we will always use scratch
    // registers in these move instructions.
    if (!FLOAT_ESP) return false;

    Operand result = MIR_Move.getResult(s);
    Operand value = MIR_Move.getValue(s);

    // TODO Remove duplication and check if code and documentation
    // are correct.

    // We need scratch registers for spilled registers that appear in
    // memory operands.
    if (result.isMemory()) {
      MemoryOperand M = result.asMemory();
      if (hasSymbolicRegister(M)) return false;
      // We will perform this transformation by changing the MOV to a PUSH
      // or POP.  Note that IA32 cannot PUSH/POP >WORDSIZE quantities, so
      // disable the transformation for that case.  Also, (TODO), our
      // assembler does not emit the prefix to allow 16-bit push/pops, so
      // disable these too.  What's left?  WORDSIZE only.
      if (M.size != WORDSIZE) return false;
    }
    if (value.isMemory()) {
      MemoryOperand M = value.asMemory();
      if (hasSymbolicRegister(M)) return false;
      // We will perform this transformation by changing the MOV to a PUSH
      // or POP.  Note that IA32 cannot PUSH/POP >WORDSIZE quantities, so
      // disable the transformation for that case.  Also, (TODO), our
      // assembler does not emit the prefix to allow 16-bit push/pops, so
      // disable these too.  What's left?  WORDSIZE only.
      if (M.size != WORDSIZE) return false;
    }
    // If we get here, all is kosher.
    return true;
  }

  @Override
  public boolean needScratch(Register r, Instruction s) {
    // We never need a scratch register for a floating point value in an
    // FMOV instruction.
    if (r.isFloatingPoint() && s.operator == IA32_FMOV) return false;

    // never need a scratch register for a YIELDPOINT_OSR
    if (s.operator == YIELDPOINT_OSR) return false;

    // Some MOVEs never need scratch registers
    if (isScratchFreeMove(s)) return false;

    // If s already has a memory operand, it is illegal to introduce
    // another.
    if (s.hasMemoryOperand()) return true;

    // Check the architecture restrictions.
    if (RegisterRestrictions.mustBeInRegister(r, s)) return true;

    // Otherwise, everything is OK.
    return false;
  }

  /**
   * Before instruction s, insert code to adjust ESP so that it lies at a
   * particular offset from its usual location.
   */
  private void moveESPBefore(Instruction s, int desiredOffset) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    Register ESP = phys.getESP();
    int delta = desiredOffset - ESPOffset;
    if (delta != 0) {
      if (canModifyEFLAGS(s)) {
        s.insertBefore(MIR_BinaryAcc.create(IA32_ADD, new RegisterOperand(ESP, TypeReference.Int), IC(delta)));
      } else {
        MemoryOperand M =
            MemoryOperand.BD(new RegisterOperand(ESP, TypeReference.Int),
                                 Offset.fromIntSignExtend(delta),
                                 (byte) 4,
                                 null,
                                 null);
        s.insertBefore(MIR_Lea.create(IA32_LEA, new RegisterOperand(ESP, TypeReference.Int), M));
      }
      ESPOffset = desiredOffset;
    }
  }

  private boolean canModifyEFLAGS(Instruction s) {
    if (PhysicalDefUse.usesEFLAGS(s.operator())) {
      return false;
    }
    if (PhysicalDefUse.definesEFLAGS(s.operator())) {
      return true;
    }
    if (s.operator == BBEND) return true;
    return canModifyEFLAGS(s.nextInstructionInCodeOrder());
  }

  /**
   * Attempt to rewrite a move instruction to a NOP.
   *
   * @return true iff the transformation applies
   */
  private boolean mutateMoveToNop(Instruction s) {
    Operand result = MIR_Move.getResult(s);
    Operand val = MIR_Move.getValue(s);
    if (result.isStackLocation() && val.isStackLocation()) {
      if (result.similar(val)) {
        Empty.mutate(s, NOP);
        return true;
      }
    }
    return false;
  }

  /**
   * Rewrite a move instruction if it has 2 memory operands.
   * One of the 2 memory operands must be a stack location operand.  Move
   * the SP to the appropriate location and use a push or pop instruction.
   */
  private void rewriteMoveInstruction(Instruction s) {
    // first attempt to mutate the move into a noop
    if (mutateMoveToNop(s)) return;

    Operand result = MIR_Move.getResult(s);
    Operand val = MIR_Move.getValue(s);
    if (result instanceof StackLocationOperand) {
      if (val instanceof MemoryOperand || val instanceof StackLocationOperand) {
        int offset = ((StackLocationOperand) result).getOffset();
        byte size = ((StackLocationOperand) result).getSize();
        offset = FPOffset2SPOffset(offset) + size;
        moveESPBefore(s, offset);
        MIR_UnaryNoRes.mutate(s, IA32_PUSH, val);
      }
    } else {
      if (result instanceof MemoryOperand) {
        if (val instanceof StackLocationOperand) {
          int offset = ((StackLocationOperand) val).getOffset();
          offset = FPOffset2SPOffset(offset);
          moveESPBefore(s, offset);
          MIR_Nullary.mutate(s, IA32_POP, result);
        }
      }
    }
  }

  /**
   * Walk through the IR.  For each StackLocationOperand, replace the
   * operand with the appropriate MemoryOperand.
   */
  private void rewriteStackLocations() {
    // ESP is initially WORDSIZE above where the framepointer is going to be.
    ESPOffset = getFrameFixedSize() + WORDSIZE;
    Register ESP = ir.regpool.getPhysicalRegisterSet().getESP();

    boolean seenReturn = false;
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();

      if (s.isReturn()) {
        seenReturn = true;
        continue;
      }

      if (s.isBranch()) {
        // restore ESP to home location at end of basic block.
        moveESPBefore(s, 0);
        continue;
      }

      if (s.operator() == BBEND) {
        if (seenReturn) {
          // at a return ESP will be at FrameFixedSize,
          seenReturn = false;
          ESPOffset = 0;
        } else {
          moveESPBefore(s, 0);
        }
        continue;
      }

      if (s.operator() == ADVISE_ESP) {
        ESPOffset = MIR_UnaryNoRes.getVal(s).asIntConstant().value;
        continue;
      }

      if (s.operator() == REQUIRE_ESP) {
        // ESP is required to be at the given offset from the bottom of the frame
        moveESPBefore(s, MIR_UnaryNoRes.getVal(s).asIntConstant().value);
        continue;
      }

      if (s.operator() == YIELDPOINT_PROLOGUE ||
          s.operator() == YIELDPOINT_BACKEDGE ||
          s.operator() == YIELDPOINT_EPILOGUE) {
        moveESPBefore(s, 0);
        continue;
      }

      if (s.operator() == IA32_MOV) {
        rewriteMoveInstruction(s);
      }

      // pop computes the effective address of its operand after ESP
      // is incremented.  Therefore update ESPOffset before rewriting
      // stacklocation and memory operands.
      if (s.operator() == IA32_POP) {
        ESPOffset += WORDSIZE;
      }

      for (Enumeration<Operand> ops = s.getOperands(); ops.hasMoreElements();) {
        Operand op = ops.nextElement();
        if (op instanceof StackLocationOperand) {
          StackLocationOperand sop = (StackLocationOperand) op;
          int offset = sop.getOffset();
          if (sop.isFromTop()) {
            offset = FPOffset2SPOffset(offset);
          }
          offset -= ESPOffset;
          byte size = sop.getSize();
          MemoryOperand M =
              MemoryOperand.BD(new RegisterOperand(ESP, TypeReference.Int),
                                   Offset.fromIntSignExtend(offset),
                                   size,
                                   null,
                                   null);
          s.replaceOperand(op, M);
        } else if (op instanceof MemoryOperand) {
          MemoryOperand M = op.asMemory();
          if ((M.base != null && M.base.getRegister() == ESP) || (M.index != null && M.index.getRegister() == ESP)) {
            M.disp = M.disp.minus(ESPOffset);
          }
        }
      }

      // push computes the effective address of its operand after ESP
      // is decremented.  Therefore update ESPOffset after rewriting
      // stacklocation and memory operands.
      if (s.operator() == IA32_PUSH) {
        ESPOffset -= WORDSIZE;
      }
    }
  }

  /**
   * PRECONDITION: The final frameSize is calculated before calling this
   * routine.
   *
   * @param fpOffset offset in bytes from the top of the stack frame
   * @return offset in bytes from the stack pointer.
   */
  private int FPOffset2SPOffset(int fpOffset) {
    // Note that SP = FP - frameSize + WORDSIZE;
    // So, FP + fpOffset = SP + frameSize - WORDSIZE
    // + fpOffset
    return frameSize + fpOffset - WORDSIZE;
  }

  @Override
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
          (s.isCall() && s.operator != CALL_SAVE_VOLATILE && scratch.scratch.isVolatile()) ||
          (s.operator == IA32_FNINIT && scratch.scratch.isFloatingPoint()) ||
          (s.operator == IA32_FCLEAR && scratch.scratch.isFloatingPoint())) {
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

      if (usedIn(scratch.scratch, s) ||
          !isLegal(scratch.currentContents, scratch.scratch, s) ||
          (s.operator == IA32_FCLEAR && scratch.scratch.isFloatingPoint())) {
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

  /**
   * Initialize some architecture-specific state needed for register
   * allocation.
   */
  @Override
  public void initForArch(IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // We reserve the last (bottom) slot in the FPR stack as a scratch register.
    // This allows us to do one push/pop sequence in order to use the
    // top of the stack as a scratch location
    phys.getFPR(7).reserveRegister();
  }

  @Override
  public boolean isSysCall(Instruction s) {
    return s.operator == IA32_SYSCALL;
  }
}
