/*
 * (C) Copyright IBM Corp. 2001
 */
///$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;

/**
 * Class to manage the allocation of the "compiler-specific" portion of 
 * the stackframe.  This class holds only the architecture-specific
 * functions.
 * <p>
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Julian Dolby
 */
public final class OPT_StackManager extends OPT_GenericStackManager
  implements OPT_Operators {


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

  /**
   * Return the size of the fixed portion of the stack.
   * (in other words, the difference between the framepointer and
   * the stackpointer after the prologue of the method completes).
   * @return size in bytes of the fixed portion of the stackframe
   */
  final int getFrameFixedSize() {
    return frameSize-WORDSIZE;
  }

  /**
   * Return the size of a type of value, in bytes.
   * NOTE: For the purpose of register allocation, a FLOAT_VALUE is 64 bits!
   *
   * @param type one of INT_VALUE, FLOAT_VALUE, or DOUBLE_VALUE
   */
  private static byte getSizeOfType(byte type) {
    switch(type) {
      case INT_VALUE:
        return (byte)(WORDSIZE);
      case FLOAT_VALUE: case DOUBLE_VALUE:
        return (byte)(2 * WORDSIZE);
      default:
        OPT_OptimizingCompilerException.TODO("getSizeOfValue: unsupported");
        return 0;
    }
  }

  /**
   * Return the move operator for a type of value.
   *
   * @param type one of INT_VALUE, FLOAT_VALUE, or DOUBLE_VALUE
   */
  private static OPT_Operator getMoveOperator(byte type) {
    switch(type) {
      case INT_VALUE:
        return IA32_MOV;
      case DOUBLE_VALUE:
      case FLOAT_VALUE:
        return IA32_FMOV;
      default:
        OPT_OptimizingCompilerException.TODO("getMoveOperator: unsupported");
        return null;
    }
  }

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  final int allocateNewSpillLocation(int type) {

    // increment by the spill size
    spillPointer += OPT_PhysicalRegisterSet.getSpillSize(type);

    if (spillPointer + WORDSIZE > frameSize) {
      frameSize = spillPointer + WORDSIZE;
    }
    return spillPointer;
  }


  /**
   * Insert a spill of a physical register before instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location, as an offset from the frame
   * pointer
   */
  final void insertSpillBefore(OPT_Instruction s, OPT_Register r,
                               byte type, int location) {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Operator move = getMoveOperator(type);
    byte size = getSizeOfType(type);
    OPT_RegisterOperand rOp;
    switch(type) {
      case FLOAT_VALUE: rOp = F(r); break;
      case DOUBLE_VALUE: rOp = D(r); break;
      default: rOp = R(r); break;
    }
    OPT_StackLocationOperand spill = 
      new OPT_StackLocationOperand(true, -location, size);
    s.insertBefore(MIR_Move.create(move, spill, rOp));
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
  final void insertUnspillBefore(OPT_Instruction s, OPT_Register r, 
                                 byte type, int location) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Operator move = getMoveOperator(type);
    byte size = getSizeOfType(type);
    OPT_RegisterOperand rOp;
    switch(type) {
      case FLOAT_VALUE: rOp = F(r); break;
      case DOUBLE_VALUE: rOp = D(r); break;
      default: rOp = R(r); break;
    }
    OPT_StackLocationOperand spill = 
      new OPT_StackLocationOperand(true, -location, size);
    s.insertBefore(MIR_Move.create(move, rOp, spill ));
  }

  /**
   * Compute the number of stack words needed to hold nonvolatile
   * registers.
   *
   * Side effects: 
   * <ul>
   * <li> updates the VM_OptCompiler structure 
   * <li> updates the <code>frameSize</code> field of this object
   * <li> updates the <code>frameRequired</code> field of this object
   * </ul>
   */
  void computeNonVolatileArea() {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    if (ir.compiledMethod.isSaveVolatile()) {
      // Record that we use every nonvolatile GPR
      int numGprNv = phys.getNumberOfNonvolatileGPRs();
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short)numGprNv);

      // set the frame size
      frameSize += numGprNv * WORDSIZE;
      frameSize = align(frameSize, STACKFRAME_ALIGNMENT);

      // TODO!!
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short)0);

      // Record that we need a stack frame.
      setFrameRequired();

      // Grab 108 bytes (same as 27 4-byte spills) in the stack
      // frame, as a place to store the floating-point state with FSAVE
      for (int i=0; i<27; i++) {
        fsaveLocation = allocateNewSpillLocation(INT_REG);
      }

      // Map each volatile register to a spill location.
      int i = 0;
      for (Enumeration e = phys.enumerateVolatileGPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        saveVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);      
      }

      // Map each non-volatile register to a spill location.
      i=0;
      for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
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
      for (Enumeration e = phys.enumerateNonvolatileGPRs();
           e.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)e.nextElement();
        if (r.isTouched() ) {
          // Note that as a side effect, the following call bumps up the
          // frame size.
          nonVolatileGPRLocation[i++] = allocateNewSpillLocation(INT_REG);
          numGprNv++;
        }
      }
      // Update the VM_OptCompiledMethod object.
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short)numGprNv);
      if (numGprNv > 0) {
        int gprOffset = getNonvolatileGPROffset(0);
        ir.compiledMethod.setUnsignedNonVolatileOffset(gprOffset);
        // record that we need a stack frame
        setFrameRequired();
      } else {
        ir.compiledMethod.setUnsignedNonVolatileOffset(0);
      }

      ir.compiledMethod.setNumberOfNonvolatileFPRs((short)0);

    }
  }



  /**
   * Clean up some junk that's left in the IR after register allocation,
   * and add epilogue code.
   */ 
  void cleanUpAndInsertEpilogue() {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    OPT_Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    for (; inst != null; inst = inst.nextInstructionInCodeOrder()) {
      switch (inst.getOpcode()) {
        case IA32_MOV_opcode:
          // remove frivolous moves
          OPT_Operand result = MIR_Move.getResult(inst);
          OPT_Operand val = MIR_Move.getValue(inst);
          if (result.similar(val)) {
            inst = inst.remove();
          }
          break;
        case IA32_FMOV_opcode:
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
   * buying the stack frame.
   * SIDE EFFECT: mutates the plg into a trap instruction.  We need to
   * mutate so that the trap instruction is in the GC map data structures.
   *
   * @param plg the prologue instruction
   */
  private void insertNormalStackOverflowCheck(OPT_Instruction plg) {
    if (!ir.method.isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.compiledMethod.isSaveVolatile()) {
      return;
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_MemoryOperand M = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.activeThreadStackLimitField.getOffset(), 
                           (byte)WORDSIZE, null, null);

    //    Trap if ESP <= active Thread Stack Limit
    MIR_TrapIf.mutate(plg,IA32_TRAPIF,null,R(ESP),M,
                      OPT_IA32ConditionOperand.LE(),
                      OPT_TrapCodeOperand.StackOverflow());
  }

  /**
   * Insert an explicit stack overflow check in the prologue <em>before</em>
   * buying the stack frame.
   * SIDE EFFECT: mutates the plg into a trap instruction.  We need to
   * mutate so that the trap instruction is in the GC map data structures.
   *
   * @param plg the prologue instruction
   */
  private void insertBigFrameStackOverflowCheck(OPT_Instruction plg) {
    if (!ir.method.isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.compiledMethod.isSaveVolatile()) {
      return;
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_Register ECX = phys.getECX();

    //    ECX := active Thread Stack Limit
    OPT_MemoryOperand M = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.activeThreadStackLimitField.getOffset(), 
                           (byte)WORDSIZE, null, null);
    plg.insertBefore(MIR_Move.create(IA32_MOV, R(ECX), M));

    //    ECX += frame Size
    int frameSize = getFrameFixedSize();
    plg.insertBefore(MIR_BinaryAcc.create(IA32_ADD, R(ECX), IC(frameSize)));
    //    Trap if ESP <= ECX
    MIR_TrapIf.mutate(plg,IA32_TRAPIF,null,R(ESP),R(ECX),
                      OPT_IA32ConditionOperand.LE(),
                      OPT_TrapCodeOperand.StackOverflow());
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
  void insertNormalPrologue() {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register ESP = phys.getESP(); 
    OPT_Register PR = phys.getPR();
    OPT_MemoryOperand fpHome = 
      OPT_MemoryOperand.BD(R(PR),
                           VM_Entrypoints.framePointerField.getOffset(),
                           (byte)WORDSIZE, null, null);

    // inst is the instruction immediately after the IR_PROLOGUE
    // instruction
    OPT_Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder().nextInstructionInCodeOrder();
    OPT_Instruction plg = inst.getPrev();

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
    //       See libjvm.C.
    if (frameFixedSize >= 256) {
      // 1. Insert Stack overflow check.  
      insertBigFrameStackOverflowCheck(plg);

      // 2. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, fpHome));

      // 3. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, fpHome.copy(), R(ESP)));

      // 4. Store my compiled method id
      int cmid = ir.compiledMethod.getId();
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, IC(cmid)));
    } else {
      // 1. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, fpHome));

      // 2. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, fpHome.copy(), R(ESP)));

      // 3. Store my compiled method id
      int cmid = ir.compiledMethod.getId();
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, IC(cmid)));

      // 4. Insert Stack overflow check.  
      insertNormalStackOverflowCheck(plg);
    }

    // II. Save any used volatile and non-volatile registers
    if (ir.compiledMethod.isSaveVolatile())  {
      saveVolatiles(inst);
      saveFloatingPointState(inst);
    }
    saveNonVolatiles(inst);
    inst.insertBefore(Empty.create(IR_ENDPROLOGUE));
  }

  /**
   * Insert code into the prologue to save any used non-volatile
   * registers.  
   *
   * @param inst the first instruction after the prologue.  
   */
  private void saveNonVolatiles(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();

    // Save each non-volatile GPR used by this method. 
    int n = nNonvolatileGPRS - 1;
    for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
         e.hasMoreElements() && n >= 0 ; n--) {
      OPT_Register nv = (OPT_Register)e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      OPT_Operand M = new OPT_StackLocationOperand(true, -offset, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, M, R(nv)));
    }
  }

  /**
   * Insert code before a return instruction to restore the nonvolatile 
   * registers.
   *
   * @param inst the return instruction
   */
  private void restoreNonVolatiles(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();

    int n = nNonvolatileGPRS - 1;
    for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
         e.hasMoreElements() && n >= 0 ; n--) {
      OPT_Register nv = (OPT_Register)e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      OPT_Operand M = new OPT_StackLocationOperand(true, -offset, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, R(nv), M));
    }
  }

  /**
   * Insert code into the prologue to save the floating point state.
   *
   * @param inst the first instruction after the prologue.  
   */
  private void saveFloatingPointState(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Operand M = new OPT_StackLocationOperand(true, -fsaveLocation, 4);
    inst.insertBefore(MIR_FSave.create(IA32_FNSAVE, M));
  }

  /**
   * Insert code into the epilogue to restore the floating point state.
   *
   * @param inst the return instruction after the epilogue.  
   */
  private void restoreFloatingPointState(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Operand M = new OPT_StackLocationOperand(true, -fsaveLocation, 4);
    inst.insertBefore(MIR_FSave.create(IA32_FRSTOR, M));
  }

  /**
   * Insert code into the prologue to save all volatile
   * registers.  
   *
   * @param inst the first instruction after the prologue.  
   */
  private void saveVolatiles(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // Save each GPR. 
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileGPRLocation[i];
      OPT_Operand M = new OPT_StackLocationOperand(true, -location, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, M, R(r)));
    }
  }
  /**
   * Insert code before a return instruction to restore the volatile 
   * and volatile registers.
   *
   * @param inst the return instruction
   */
  private void restoreVolatileRegisters(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // Restore every GPR
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs(); 
         e.hasMoreElements(); i++){
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileGPRLocation[i];
      OPT_Operand M = new OPT_StackLocationOperand(true, -location, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, R(r), M));
    }
  }
  /**
   * Insert the epilogue before a particular return instruction.
   *
   * @param ret the return instruction.
   */
  private void insertEpilogue(OPT_Instruction ret) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet(); 
    OPT_Register ESP = phys.getESP(); 
    OPT_Register PR = phys.getPR();

    // 1. Restore any saved registers
    if (ir.compiledMethod.isSaveVolatile())  {
      restoreVolatileRegisters(ret);
      restoreFloatingPointState(ret);
    }
    restoreNonVolatiles(ret);

    // 2. Restore caller's stackpointer and framepointer
    int frameSize = getFrameFixedSize();
    ret.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, IC(frameSize)));
    OPT_MemoryOperand fpHome = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.framePointerField.getOffset(),
                           (byte)WORDSIZE, null, null);
    ret.insertBefore(MIR_Nullary.create(IA32_POP, fpHome));
  }

  /**
   * In instruction s, replace all appearances of a symbolic register 
   * operand with uses of the appropriate spill location, as cached by the
   * register allocator.
   *
   * @param s the instruction to mutate.
   * @param symb the symbolic register operand to replace
   */
  void replaceOperandWithSpillLocation(OPT_Instruction s, 
                                               OPT_RegisterOperand symb) {

    // Get the spill location previously assigned to the symbolic
    // register.
    int location = OPT_RegisterAllocatorState.getSpill(symb.register);

    // Create a memory operand M representing the spill location.
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Operand M = null;
    int type = phys.getPhysicalRegisterType(symb.register);
    int size = phys.getSpillSize(type);

    M = new OPT_StackLocationOperand(true, -location, (byte)size);

    // replace the register operand with the memory operand
    s.replaceOperand(symb,M);
  }

  /**
   * Does a memory operand hold a symbolic register?
   */
  private boolean hasSymbolicRegister(OPT_MemoryOperand M) {
    if (M.base != null && !M.base.register.isPhysical()) return true;
    if (M.index != null && !M.index.register.isPhysical()) return true;
    return false;
  }

  /**
   * Is s a MOVE instruction that can be generated without resorting to
   * scratch registers?
   */
  private boolean isScratchFreeMove(OPT_Instruction s) {
    if (s.operator() != IA32_MOV) return false;

    // if we don't allow ESP to float, we will always use scratch
    // registers in these move instructions.
    if (!FLOAT_ESP) return false;

    OPT_Operand result = MIR_Move.getResult(s);
    OPT_Operand value = MIR_Move.getValue(s);

    // We need scratch registers for spilled registers that appear in
    // memory operands.
    if (result.isMemory()) {
      OPT_MemoryOperand M = result.asMemory();
      if (hasSymbolicRegister(M)) return false;
      // We will perform this transformation by changing the MOV to a PUSH
      // or POP.  Note that IA32 cannot PUSH/POP 8-bit quantities, so
      // disable the transformation for that case.  Also, (TODO), our
      // assembler does not emit the prefix to allow 16-bit push/pops, so
      // disable these too.  What's left?  32-bit only.
      if (M.size != 4) return false;
    }
    if (value.isMemory()) {
      OPT_MemoryOperand M = value.asMemory();
      if (hasSymbolicRegister(M)) return false;
      // We will perform this transformation by changing the MOV to a PUSH
      // or POP.  Note that IA32 cannot PUSH/POP 8-bit quantities, so
      // disable the transformation for that case.  Also, (TODO), our
      // assembler does not emit the prefix to allow 16-bit push/pops, so
      // disable these too.  What's left?  32-bit only.
      if (M.size != 4) return false;
    }
    // If we get here, all is kosher.
    return true;
  }

  /**
   * Given symbolic register r in instruction s, do we need to ensure that
   * r is in a scratch register is s (as opposed to a memory operand)
   */
  boolean needScratch(OPT_Register r, OPT_Instruction s) {
    // We never need a scratch register for a floating point value in an
    // FMOV instruction.
    if (r.isFloatingPoint() && s.operator==IA32_FMOV) return false;

    //-#if RVM_WITH_OSR
    // never need a scratch register for a YIELDPOINT_OSR
    if (s.operator == YIELDPOINT_OSR) return false;
    //-#endif

    // Some MOVEs never need scratch registers
    if (isScratchFreeMove(s)) return false;

    // If s already has a memory operand, it is illegal to introduce
    // another.
    if (s.hasMemoryOperand()) return true;

    // Check the architecture restrictions.
    if (getRestrictions().mustBeInRegister(r,s)) return true;
    
    // Otherwise, everything is OK.
    return false;
  }

  /**
   * Before instruction s, insert code to adjust ESP so that it lies at a
   * particular offset from its usual location.
   */
  private void moveESPBefore(OPT_Instruction s, int desiredOffset) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet(); 
    OPT_Register ESP = phys.getESP(); 
    int delta = desiredOffset - ESPOffset;
    if (delta != 0) {
      if (canModifyEFLAGS(s)) {
        s.insertBefore(MIR_BinaryAcc.create(IA32_ADD, R(ESP), IC(delta)));
      } else {
        OPT_MemoryOperand M = 
          OPT_MemoryOperand.BD(R(ESP),delta, (byte)4, null, null); 
        s.insertBefore(MIR_Lea.create(IA32_LEA, R(ESP), M));
      }
      ESPOffset = desiredOffset;
    }
  }
  private boolean canModifyEFLAGS(OPT_Instruction s) {
    if (OPT_PhysicalDefUse.usesEFLAGS(s.operator()))
      return false;
    if (OPT_PhysicalDefUse.definesEFLAGS(s.operator()))
      return true;
    if (s.operator == BBEND) return true;
    return canModifyEFLAGS(s.nextInstructionInCodeOrder());
  }

  /**
   * Attempt to rewrite a move instruction to a NOP.
   *
   * @return true iff the transformation applies
   */
  private boolean mutateMoveToNop(OPT_Instruction s) {
    OPT_Operand result = MIR_Move.getResult(s);
    OPT_Operand val = MIR_Move.getValue(s);
    if (result.isStackLocation() && val.isStackLocation()) {
      if (result.similar(val)) {
        Empty.mutate(s,NOP);
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
  private void rewriteMoveInstruction(OPT_Instruction s) {
    // first attempt to mutate the move into a noop
    if (mutateMoveToNop(s)) return;

    OPT_Register ESP = ir.regpool.getPhysicalRegisterSet().getESP();
    OPT_Operand result = MIR_Move.getResult(s);
    OPT_Operand val = MIR_Move.getValue(s);
    if (result instanceof OPT_StackLocationOperand) {
      if (val instanceof OPT_MemoryOperand || 
          val instanceof OPT_StackLocationOperand) {
        int offset = ((OPT_StackLocationOperand)result).getOffset();
        byte size = ((OPT_StackLocationOperand)result).getSize();
        offset = FPOffset2SPOffset(offset) + size;
        moveESPBefore(s,offset);
        MIR_UnaryNoRes.mutate(s,IA32_PUSH,val);
      }
    } else {
      if (result instanceof OPT_MemoryOperand) {
        if (val instanceof OPT_StackLocationOperand) {
          int offset = ((OPT_StackLocationOperand)val).getOffset();
          offset = FPOffset2SPOffset(offset);
          moveESPBefore(s,offset);
          MIR_Nullary.mutate(s,IA32_POP,result);
        }
      }
    }
  }

  /**
   * Walk through the IR.  For each OPT_StackLocationOperand, replace the
   * operand with the appropriate OPT_MemoryOperand.
   */
  private void rewriteStackLocations() {
    // ESP is initially 4 bytes above where the framepointer is going to be.
    ESPOffset = getFrameFixedSize() + 4;
    OPT_Register ESP = ir.regpool.getPhysicalRegisterSet().getESP();

    boolean seenReturn = false;
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
         e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      
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
        ESPOffset  += 4; 
      } 

      for (OPT_OperandEnumeration ops = s.getOperands(); ops.hasMoreElements(); ) {
        OPT_Operand op = ops.next();
        if (op instanceof OPT_StackLocationOperand) {
          OPT_StackLocationOperand sop = (OPT_StackLocationOperand)op;
          int offset = sop.getOffset();
          if (sop.isFromTop()) {
            offset = FPOffset2SPOffset(offset);
          }
          offset -= ESPOffset;
          byte size = sop.getSize();
          OPT_MemoryOperand M = 
            OPT_MemoryOperand.BD(R(ESP),offset,
                                 size, null, null); 
          s.replaceOperand(op, M);
        } else if (op instanceof OPT_MemoryOperand) {
          OPT_MemoryOperand M = op.asMemory();
          if ((M.base != null && M.base.register == ESP) ||
              (M.index != null && M.index.register == ESP)) {
            M.disp -= ESPOffset;
          }
        }
      }

      // push computes the effective address of its operand after ESP
      // is decremented.  Therefore update ESPOffset after rewriting 
      // stacklocation and memory operands.
      if (s.operator() == IA32_PUSH) {
        ESPOffset -= 4;
      }
    }
  }

  /**
   * @param fpOffset offset in bytes from the top of the stack frame
   * @return offset in bytes from the stack pointer.
   *
   * PRECONDITION: The final frameSize is calculated before calling this
   * routine.
   */
  private int FPOffset2SPOffset(int fpOffset) {
    // Note that SP = FP - frameSize + WORDSIZE;  
    // So, FP + fpOffset = SP + frameSize - WORDSIZE
    // + fpOffset
    return frameSize + fpOffset - WORDSIZE;
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
  void restoreScratchRegistersBefore(OPT_Instruction s) {
    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();

      if (scratch.currentContents == null) continue;
      if (verboseDebug) {
        System.out.println("RESTORE: consider " + scratch);
      }
      boolean removed = false;
      boolean unloaded = false;
      if (definedIn(scratch.scratch,s) 
          || (s.isCall() && s.operator != CALL_SAVE_VOLATILE 
              && scratch.scratch.isVolatile()) 
          || (s.operator == IA32_FNINIT && scratch.scratch.isFloatingPoint())
          || (s.operator == IA32_FCLEAR && scratch.scratch.isFloatingPoint())) {
        // s defines the scratch register, so save its contents before they
        // are killed.
        if (verboseDebug) {
          System.out.println("RESTORE : unload because defined " + scratch);
        }
        unloadScratchRegisterBefore(s,scratch);

        // update mapping information
        if (verboseDebug) System.out.println("RSRB: End scratch interval " + 
                                             scratch.scratch + " " + s);
        scratchMap.endScratchInterval(scratch.scratch,s);
        OPT_Register scratchContents = scratch.currentContents;
        if (scratchContents != null) {
          if (verboseDebug) System.out.println("RSRB: End symbolic interval " + 
                                               scratch.currentContents + " " 
                                               + s);
          scratchMap.endSymbolicInterval(scratch.currentContents,s);
        } 

        i.remove();
        removed = true;
        unloaded = true;
      }

      if (usedIn(scratch.scratch,s) ||
          !isLegal(scratch.currentContents,scratch.scratch,s) ||
          (s.operator == IA32_FCLEAR && scratch.scratch.isFloatingPoint())) {
        // first spill the currents contents of the scratch register to 
        // memory 
        if (!unloaded) {
          if (verboseDebug) {
            System.out.println("RESTORE : unload because used " + scratch);
          }
          unloadScratchRegisterBefore(s,scratch);

          // update mapping information
          if (verboseDebug) System.out.println("RSRB2: End scratch interval " + 
                                               scratch.scratch + " " + s);
          scratchMap.endScratchInterval(scratch.scratch,s);
          OPT_Register scratchContents = scratch.currentContents;
          if (scratchContents != null) {
            if (verboseDebug) System.out.println("RSRB2: End symbolic interval " + 
                                                 scratch.currentContents + " " 
                                                 + s);
            scratchMap.endSymbolicInterval(scratch.currentContents,s);
          } 

        }
        // s or some future instruction uses the scratch register, 
        // so restore the correct contents.
        if (verboseDebug) {
          System.out.println("RESTORE : reload because used " + scratch);
        }
        reloadScratchRegisterBefore(s,scratch);

        if (!removed) {
          i.remove();
          removed=true;
        }
      }
    }
  }
  /**
   * Initialize some architecture-specific state needed for register
   * allocation.
   */
  void initForArch(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // We reserve the last (bottom) slot in the FPR stack as a scratch register.
    // This allows us to do one push/pop sequence in order to use the
    // top of the stack as a scratch location
    phys.getFPR(7).reserveRegister();
  }

  /**
   * Is a particular instruction a system call?
   */
  boolean isSysCall(OPT_Instruction s) {
    return s.operator == IA32_SYSCALL;
  } 
} 
