/*
 * (C) Copyright IBM Corp. 2001
 */
///$Id$

import instructionFormats.*;
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
 * TODO: Much of this code could still be factored out as
 * architecture-independent.
 *
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Julian Dolby
 */
final class OPT_StackManager extends OPT_GenericStackManager
implements OPT_Operators {

  private static boolean verboseDebug = false;

  /**
   * Size of a word, in bytes
   */
  private static final int WORDSIZE = 4;

  /**
   * For each physical register, holds a ScratchRegister which records
   * the current scratch assignment for the physical register.
   */
  private HashSet scratchInUse = new HashSet(20);

  /**
   * An array which holds the spill location number used to stash volatile
   * registers in the SaveVolatile protocol.
   */
  private int[] saveVolatileLocation = new int[NUM_GPRS];

  /**
   * A frame offset for 108 bytes of stack space to store the 
   * floating point state in the SaveVolatile protocol.
   */
  private int fsaveLocation;

  /**
   * An array which holds the spill location number used to stash nonvolatile
   * registers. 
   */
  private int[] nonVolatileLocation = new int[NUM_GPRS];

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.  
   *
   * This field, when non-zero,  holds the stack-frame offset reserved to
   * hold this data.
   */
  private int sysCallOffset = 0;

  /**
   * We allow the stack pointer to float from its normal position at the
   * bottom of the frame.  This field holds the 'current' offset of the
   * SP.
   */
  private int ESPOffset = 0;

  /**
   * Should we use information from linear scan in choosing scratch
   * registers?
   */
  private static boolean USE_LINEAR_SCAN = true;

  /**
   * Should we allow the stack pointer to float in order to avoid scratch
   * registers in move instructions.  Note: as of Feb. 02, we think this
   * is a bad idea.
   */
  private static boolean FLOAT_ESP = false;

  /**
   * We may rely on information from linear scan to choose scratch registers.
   * If so, the following holds a pointer to some information from linear
   * scan analysis.
   */
  private OPT_NewLinearScan.ActiveSet activeSet = null;


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
   * PROLOGUE/EPILOGUE. must be done after register allocation
   */
  final void insertPrologueAndEpilogue() {
    insertPrologue();
    cleanUpAndInsertEpilogue();
  }

  /**
   * Insert the prologue.
   */
  private void insertPrologue() {

    // compute the number of stack words needed to hold nonvolatile
    // registers
    computeNonVolatileArea();

    if (frameIsRequired()) {
      insertNormalPrologue();
    }
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
  private void computeNonVolatileArea() {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    VM_OptCompilerInfo info = ir.MIRInfo.info;

    if (ir.MIRInfo.info.isSaveVolatile()) {
      // Record that we use every nonvolatile GPR
      int numGprNv = phys.getNumberOfNonvolatileGPRs();
      info.setNumberOfNonvolatileGPRs((short)numGprNv);

      // set the frame size
      frameSize += numGprNv * WORDSIZE;
      frameSize = align(frameSize, STACKFRAME_ALIGNMENT);

      // TODO!!
      info.setNumberOfNonvolatileFPRs((short)0);

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
        saveVolatileLocation[i] = allocateNewSpillLocation(INT_REG);      
      }

      // Map each non-volatile register to a spill location.
      i=0;
      for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        nonVolatileLocation[i] = allocateNewSpillLocation(INT_REG);      
      }

      // Set the offset to find non-volatiles.
      int gprOffset = getNonvolatileGPROffset(0);
      info.setUnsignedNonVolatileOffset(gprOffset);

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
          nonVolatileLocation[i++] = allocateNewSpillLocation(INT_REG);
          numGprNv++;
        }
      }
      // Update the VM_OptCompilerInfo object.
      info.setNumberOfNonvolatileGPRs((short)numGprNv);
      if (numGprNv > 0) {
        int gprOffset = getNonvolatileGPROffset(0);
        info.setUnsignedNonVolatileOffset(gprOffset);
        // record that we need a stack frame
        setFrameRequired();
      } else {
        info.setUnsignedNonVolatileOffset(0);
      }

      info.setNumberOfNonvolatileFPRs((short)0);

    }
  }


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
   * Clean up some junk that's left in the IR after register allocation,
   * and add epilogue code.
   */ 
  private void cleanUpAndInsertEpilogue() {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext();
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
    if (!ir.method.getDeclaringClass().isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.MIRInfo.info.isSaveVolatile()) {
      OPT_OptimizingCompilerException.UNREACHABLE(
                                                  "SaveVolatile should be uninterruptible");
      return;
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_MemoryOperand M = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.activeThreadStackLimitOffset, 
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
    if (!ir.method.getDeclaringClass().isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.MIRInfo.info.isSaveVolatile()) {
      OPT_OptimizingCompilerException.UNREACHABLE(
                                                  "SaveVolatile should be uninterruptible");
      return;
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_Register ECX = phys.getECX();

    //    ECX := active Thread Stack Limit
    OPT_MemoryOperand M = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.activeThreadStackLimitOffset, 
			   (byte)WORDSIZE, null, null);
    plg.insertBefore(MIR_Move.create(IA32_MOV, R(ECX), M));

    //    ECX += frame Size
    int frameSize = getFrameFixedSize();
    plg.insertBefore(MIR_BinaryAcc.create(IA32_ADD, R(ECX), I(frameSize)));
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
  private void insertNormalPrologue() {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register ESP = phys.getESP(); 
    OPT_Register PR = phys.getPR();
    OPT_MemoryOperand fpHome = 
      OPT_MemoryOperand.BD(R(PR),
			   VM_Entrypoints.framePointerOffset,
			   (byte)WORDSIZE, null, null);

    // inst is the instruction immediately after the IR_PROLOGUE
    // instruction
    OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext().getNext();
    OPT_Instruction plg = inst.getPrev();

    int frameFixedSize = getFrameFixedSize();
    ir.MIRInfo.info.setFrameFixedSize(frameFixedSize);

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
      int cmid = ir.compiledMethodId;
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, I(cmid)));
    } else {
      // 1. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, fpHome));

      // 2. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, fpHome.copy(), R(ESP)));

      // 3. Store my compiled method id
      int cmid = ir.compiledMethodId;
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, I(cmid)));

      // 4. Insert Stack overflow check.  
      insertNormalStackOverflowCheck(plg);
    }

    // II. Save any used volatile and non-volatile registers
    if (ir.MIRInfo.info.isSaveVolatile())  {
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
  private void saveNonVolatiles(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    VM_OptCompilerInfo info = ir.MIRInfo.info;
    int nNonvolatileGPRS = info.getNumberOfNonvolatileGPRs();

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
    VM_OptCompilerInfo info = ir.MIRInfo.info;
    int nNonvolatileGPRS = info.getNumberOfNonvolatileGPRs();

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
    VM_OptCompilerInfo info = ir.MIRInfo.info;

    // Save each GPR. 
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileLocation[i];
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
      int location = saveVolatileLocation[i];
      OPT_Operand M = new OPT_StackLocationOperand(true, -location, 4);
      inst.insertBefore(MIR_Move.create(IA32_MOV, R(r), M));
    }
  }

  /**
   * Return the offset from the frame pointer for the place to store the
   * nth nonvolatile GPR.
   */
  private int getNonvolatileGPROffset(int n) {
    return nonVolatileLocation[n];
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
    if (ir.MIRInfo.info.isSaveVolatile())  {
      restoreVolatileRegisters(ret);
      restoreFloatingPointState(ret);
    }
    restoreNonVolatiles(ret);

    // 2. Restore caller's stackpointer and framepointer
    int frameSize = getFrameFixedSize();
    ret.insertBefore(MIR_UnaryNoRes.create(REQUIRE_ESP, I(frameSize)));
    OPT_MemoryOperand fpHome = 
      OPT_MemoryOperand.BD(R(PR), VM_Entrypoints.framePointerOffset,
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
  private void replaceOperandWithSpillLocation(OPT_Instruction s, 
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
   * Is s a PEI with a reachable catch block?
   */
  private boolean isPEIWithCatch(OPT_Instruction s) {
    if (s.isPEI())  {
      // TODO: optimize this away by passing the basic block in.
      OPT_BasicBlock b = s.getBasicBlock();

      // TODO: add a more efficient accessor on OPT_BasicBlock to
      // determine whether there's a catch block for a particular
      // instruction.
      if (b.getApplicableExceptionalOut(s).hasMoreElements()) {
        return true;
      }
    }
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
  private boolean needScratch(OPT_Register r, OPT_Instruction s) {
    // We never need a scratch register for a floating point value in an
    // FMOV instruction.
    if (r.isFloatingPoint() && s.operator==IA32_FMOV) return false;

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
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   *
   * TODO: this code should be architecture-independent.  Try to use this
   * code for PowerPC.
   */
  void insertSpillCode() {
    insertSpillCode(null);
  }

  void insertSpillCode(OPT_NewLinearScan.ActiveSet set) {

    if (USE_LINEAR_SCAN) {
      activeSet = set;
    }

    if (verboseDebug) {
      System.out.println("INSERT SPILL CODE:");
    }

    // walk over each instruction in the IR
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (verboseDebug) {
        System.out.println(s);
      }

      // If any scratch registers are currently in use, but use physical
      // registers that appear in s, then free the scratch register.
      restoreScratchRegistersBefore(s);  

      // we must spill all scratch registers before leaving this basic block
      if (s.operator == BBEND || isPEIWithCatch(s) || s.isBranch() || 
          s.isReturn()) {
        restoreAllScratchRegistersBefore(s);
      }

      // Walk over each operand and insert the appropriate spill code.
      // for the operand.
      for (Enumeration ops = s.getOperands(); ops.hasMoreElements(); ) {
        OPT_Operand op = (OPT_Operand)ops.nextElement();
        if (op != null && op.isRegister()) {
          OPT_Register r = op.asRegister().register;
          if (!r.isPhysical()) {
            // Is r currently assigned to a scratch register?
            ScratchRegister scratch = getCurrentScratchRegister(r);
            if (verboseDebug) {
              System.out.println(r + " SCRATCH " + scratch);
            }
            if (scratch != null) {
              // r is currently assigned to a scratch register.  Continue to
              // use the same scratch register.
              boolean defined = definedIn(r,s) || definesSpillLocation(r,s);
              if (defined) {
                scratch.setDirty(true);
              }
              replaceRegisterWithScratch(s,r,scratch.scratch);
            } else {
              // r is currently NOT assigned to a scratch register.
              // Do we need to create a new scratch register to hold r?
              // Note that we never need scratch floating point register
              // for FMOVs, since we already have a scratch stack location
              // reserved.
              if (needScratch(r,s)) {
                // We must create a new scratch register.
                boolean used = usedIn(r,s) || usesSpillLocation(r,s);
                boolean defined = definedIn(r,s) || definesSpillLocation(r,s);
                if (used) {
                  if (!usedIn(r,s)) {
                    OPT_Register r2 = spillLocationUse(r,s);
                    scratch = moveToScratchBefore(s,r2);
                    if (verboseDebug) {
                      System.out.println("MOVED TO SCRATCH BEFORE " + r2 + 
                                         " " + scratch);
                    }
                  } else {
                    scratch = moveToScratchBefore(s,r);
                    if (verboseDebug) {
                      System.out.println("MOVED TO SCRATCH BEFORE " + r + 
                                         " " + scratch);
                    }
                  }
                }   
                if (defined) {
                  scratch = holdInScratchAfter(s,r);
                  scratch.setDirty(true);
                  if (verboseDebug) {
                    System.out.println("HELD IN SCRATCH AFTER" + r + 
                                       " " + scratch);
                  }
                }
                // replace the register in the target instruction.
                replaceRegisterWithScratch(s,r,scratch.scratch);
              } else {
                // No need to use a scratch register here.
                replaceOperandWithSpillLocation(s,op.asRegister());
              }
            }
          }
        }
      }

      // deal with sys calls that may bash non-volatiles
      if (s.operator == IA32_SYSCALL) {
        OPT_CallingConvention.saveNonvolatilesAroundSysCall(s,ir);
      }

    }
  }

  /**
   * Replace all occurences of register r1 in an instruction with register
   * r2.
   *
   * Also, for any register r3 that is spilled to the same location as
   * r1, replace r3 with r2.
   */
  private void replaceRegisterWithScratch(OPT_Instruction s, 
                                          OPT_Register r1, OPT_Register r2) {
    int spill1 = OPT_RegisterAllocatorState.getSpill(r1);
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null) {
        if (op.isRegister()) {
          OPT_Register r3 = op.asRegister().register;
          if (r3 == r1) {
            op.asRegister().register = r2;
          } else if (OPT_RegisterAllocatorState.getSpill(r3) == spill1) {
            op.asRegister().register = r2;
          }
        }
      }
    }
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
	s.insertBefore(MIR_BinaryAcc.create(IA32_ADD, R(ESP), I(delta)));
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
   * Return a FPR that does not appear in instruction s, to be used as a
   * scratch register to hold register r
   * Except, do NOT return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getFirstFPRNotUsedIn(OPT_Register r, 
                                            OPT_Instruction s, 
                                            HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() &&
          !reserved.contains(p) && isLegal(r,p,s)) {
        return p;
      }
    }

    OPT_OptimizingCompilerException.TODO(
                        "Could not find a free FPR in spill situation");
    return null;
  }

  /**
   * Return a FPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Return null if none found
   */ 
  private OPT_Register getFirstDeadFPRNotUsedIn(OPT_Register r, 
                                                OPT_Instruction s, 
                                                HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }

    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, to hold symbolic
   * register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getFirstGPRNotUsedIn(OPT_Register r, 
                                            OPT_Instruction s, 
                                            HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register p= (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)
          && isLegal(r,p,s)) {
        return p;
      }
    }
    // next try the non-volatiles
    for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p) && 
          isLegal(r,p,s)) {
        return p;
      }
    }
    OPT_OptimizingCompilerException.TODO(
                             "Could not find a free GPR in spill situation");
    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r. 
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * return null if none found
   */ 
  private OPT_Register getFirstDeadGPRNotUsedIn(OPT_Register r,
                                                OPT_Instruction s, 
                                                HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }
    // next try the non-volatiles
    for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }
    return null;
  }

  /**
   * Does register r appear in instruction s?
   */
  private boolean appearsIn(OPT_Register r, OPT_Instruction s) {
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().register.number == r.number) {
          return true;
        }
      }
    }
    // FNINIT and FCLEAR use/kill all floating points 
    if (r.isFloatingPoint() && 
        (s.operator == IA32_FNINIT || s.operator == IA32_FCLEAR)) {
      return true;
    }

    // Assume that all volatile registers 'appear' in all call 
    // instructions
    if (s.isCall() && s.operator != CALL_SAVE_VOLATILE && r.isVolatile()) {
      return true;
    }

    return false;
  }

  /**
   * Does instruction s use the spill location for a given register?
   */
  private boolean usesSpillLocation(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return usesSpillLocation(location,s);
  }

  /**
   * Assuming instruction s uses the spill location for a given register, 
   * return the symbolic register that embodies that use.
   */
  private OPT_Register spillLocationUse(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return spillLocationUse(location,s);
  }

  /**
   * Does instruction s define the spill location for a given register?
   */
  private boolean definesSpillLocation(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return definesSpillLocation(location,s);
  }
  /**
   * Does instruction s define spill location loc?
   */
  private boolean definesSpillLocation(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getDefs(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }
  /**
   * Does instruction s use spill location loc?
   */
  private boolean usesSpillLocation(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Assuming instruction s uses the spill location loc,
   * return the symbolic register that embodies that use.
   * Note that at most one such register can be used, since at most one
   * live register can use a given spill location.
   */
  private OPT_Register spillLocationUse(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return r;
        }
      }
    }
    OPT_OptimizingCompilerException.UNREACHABLE("NO Matching use");
    return null;
  }

  /**
   * Insert code as needed so that after instruction s, the value of
   * a symbolic register will be held in a particular scratch physical
   * register.
   * 
   * @return the physical scratch register that holds the value 
   *         after instruction s
   */
  private ScratchRegister holdInScratchAfter(OPT_Instruction s, 
                                             OPT_Register symb) {

    // Get a scratch register.
    ScratchRegister sr = getScratchRegister(symb,s);

    // make the scratch register available to hold the new 
    // symbolic register
    OPT_Register current = sr.currentContents;

    if (current != null && current != symb) {
      int location = OPT_RegisterAllocatorState.getSpill(current);
      int location2 = OPT_RegisterAllocatorState.getSpill(symb);
      if (location != location2) {
        insertSpillBefore(s,sr.scratch,getValueType(current),location);
      }
    }

    // Record the new contents of the scratch register
    sr.currentContents = symb;

    return sr;
  }

  /**
   * Is it legal to assign symbolic register symb to scratch register phys
   * in instruction s?
   */
  boolean isLegal(OPT_Register symb, OPT_Register phys, OPT_Instruction s) {
    // If the physical scratch register already appears in s, so we can't 
    // use it as a scratch register for another value.
    if (appearsIn(phys,s)) return false;

    // Check register restrictions for symb.
    if (getRestrictions().isForbidden(symb,phys,s)) return false;

    // Further assure legality for all other symbolic registers in symb
    // which are mapped to the same spill location as symb.
    int location = OPT_RegisterAllocatorState.getSpill(symb);
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op.isRegister()) {
        OPT_Register r = op.asRegister().register;
        if (r.isSymbolic()) {
          if (location == OPT_RegisterAllocatorState.getSpill(r)) {
            if (getRestrictions().isForbidden(r,phys,s)) 
              return false;
          }
        }
      }
    }

    // Otherwise, all is kosher.
    return true;
  }
  /**
   * Get a scratch register to hold symbolic register symb in instruction
   * s.
   */
  private ScratchRegister getScratchRegister(OPT_Register symb,
                                             OPT_Instruction s) {

    ScratchRegister r = getCurrentScratchRegister(symb);
    if (r != null) {
      // symb is currently assigned to scratch register r
      if (isLegal(symb,r.scratch,s)) {
        if (r.currentContents != symb) {
          // we're reusing a scratch register based on the fact that symb
          // shares a spill location with r.currentContents.  However,
          // update the mapping information.
          if (r.currentContents != null) {
            if (verboseDebug) System.out.println("GSR: End symbolic interval " + 
                                                 r.currentContents + " " 
                                                 + s);
            scratchMap.endSymbolicInterval(r.currentContents,s);
          } 
          if (verboseDebug) System.out.println("GSR: Begin symbolic interval " + 
                                               symb + " " + r.scratch + 
                                               " " + s);
          scratchMap.beginSymbolicInterval(symb,r.scratch,s);
        }
        return r;
      }
    }

    // if we get here, either there is no current scratch assignment, or
    // the current assignment is illegal.  Find a new scratch register.
    ScratchRegister result = null;
    if (activeSet == null) {
      result = getFirstAvailableScratchRegister(symb,s);
    } else {
      result = getScratchRegisterUsingIntervals(symb,s);
    }
    
    // Record that we will touch the scratch register.
    result.scratch.touchRegister(); 
    return result;
  }

  /**
   * Find a register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Insert spills if necessary to ensure that the returned scratch
   * register is free for use.
   */
  private ScratchRegister getScratchRegisterUsingIntervals(OPT_Register r,
                                                           OPT_Instruction s){
    HashSet reservedScratch = getReservedScratchRegisters(s);

    OPT_Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstDeadFPRNotUsedIn(r,s,reservedScratch);
    } else {
      phys = getFirstDeadGPRNotUsedIn(r,s,reservedScratch);
    }

    // if the version above failed, default to the dumber heuristics
    if (phys == null) {
      if (r.isFloatingPoint()) {
        phys = getFirstFPRNotUsedIn(r,s,reservedScratch);
      } else {
        phys = getFirstGPRNotUsedIn(r,s,reservedScratch);
      }
    }
    return createScratchBefore(s,phys,r);
  }

  /**
   * Find the first available register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Insert spills if necessary to ensure that the returned scratch
   * register is free for use.
   */
  private ScratchRegister getFirstAvailableScratchRegister(OPT_Register r,
                                                           OPT_Instruction s){
    HashSet reservedScratch = getReservedScratchRegisters(s);

    OPT_Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstFPRNotUsedIn(r,s,reservedScratch);
    } else {
      phys = getFirstGPRNotUsedIn(r,s,reservedScratch);
    }
    return createScratchBefore(s,phys,r);
  }

  /**
   * Assign symbolic register symb to a physical register, and insert code
   * before instruction s to load the register from the appropriate stack
   * location.
   *
   * @return the physical register used to hold the value when it is
   * loaded from the spill location
   */
  private ScratchRegister moveToScratchBefore(OPT_Instruction s, 
                                              OPT_Register symb) {

    ScratchRegister sr = getScratchRegister(symb,s);

    OPT_Register scratchContents = sr.currentContents;
    if (scratchContents != symb) {
      if (scratchContents != null) {
        // the scratch register currently holds a different 
        // symbolic register.
        // spill the contents of the scratch register to free it up.
        unloadScratchRegisterBefore(s,sr);
      }

      // Now load up the scratch register.
      // since symbReg must have been previously spilled, get the spill
      // location previous assigned to symbReg
      int location = OPT_RegisterAllocatorState.getSpill(symb);
      insertUnspillBefore(s,sr.scratch,getValueType(symb),location);

      // we have not yet written to sr, so mark it 'clean'
      sr.setDirty(false);

    } else { 
      // In this case the scratch register already holds the desired
      // symbolic register.  So: do nothing. 
    }    

    // Record the current contents of the scratch register
    sr.currentContents = symb;

    return sr;
  }

  /**
   * Make physical register r available to be used as a scratch register
   * before instruction s.  In instruction s, r will hold the value of
   * register symb.
   */
  private ScratchRegister createScratchBefore(OPT_Instruction s, 
                                              OPT_Register r,
                                              OPT_Register symb) {
    OPT_PhysicalRegisterSet pool = ir.regpool.getPhysicalRegisterSet();
    int type = pool.getPhysicalRegisterType(r);
    int spillLocation = OPT_RegisterAllocatorState.getSpill(r);
    if (spillLocation <= 0) {
      // no spillLocation yet assigned to the physical register.
      // allocate a new location and assign it for the physical register
      spillLocation = allocateNewSpillLocation(type);      
      OPT_RegisterAllocatorState.setSpill(r,spillLocation);
    }

    ScratchRegister sr = getPhysicalScratchRegister(r);
    if (sr == null) {
      sr = new ScratchRegister(r,null);
      scratchInUse.add(sr);
      // Since this is a new scratch register, spill the old contents of
      // r if necessary.
      if (activeSet == null) {
        insertSpillBefore(s, r, (byte)type, spillLocation);
        sr.setHadToSpill(true);
      } else {
        if (!isDeadBefore(r,s)) {
          insertSpillBefore(s, r, (byte)type, spillLocation);
          sr.setHadToSpill(true);
        }
      }
    } else {
      // update mapping information
      if (verboseDebug) System.out.println("CSB: " + 
                                           " End scratch interval " + 
                                           sr.scratch + " " + s);
      scratchMap.endScratchInterval(sr.scratch,s);
      OPT_Register scratchContents = sr.currentContents;
      if (scratchContents != null) {
        if (verboseDebug) System.out.println("CSB: " + 
                                             " End symbolic interval " + 
                                             sr.currentContents + " " 
                                             + s);
        scratchMap.endSymbolicInterval(sr.currentContents,s);
      } 
    }

    // update mapping information
    if (verboseDebug) System.out.println("CSB: Begin scratch interval " + r + 
                                         " " + s);
    scratchMap.beginScratchInterval(r,s);

    if (verboseDebug) System.out.println("CSB: Begin symbolic interval " + 
                                         symb + " " + r + 
                                         " " + s);
    scratchMap.beginSymbolicInterval(symb,r,s);

    return sr;
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
  private void restoreScratchRegistersBefore(OPT_Instruction s) {
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
   * Walk over the currently available scratch registers, and spill their 
   * contents to memory before instruction s.  Also restore the correct live
   * value for each scratch register. Normally, s should end a 
   * basic block. 
   *
   * SPECIAL CASE: If s is a return instruction, only restore the scratch
   * registers that are used by s.  The others are dead.
   */
  private void restoreAllScratchRegistersBefore(OPT_Instruction s) {
    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();

      // SPECIAL CASE: If s is a return instruction, only restore the 
      // scratch
      // registers that are used by s.  The others are dead.
      if (!s.isReturn() || usedIn(scratch.scratch,s)) {
        unloadScratchRegisterBefore(s,scratch);
        reloadScratchRegisterBefore(s,scratch);
      }
      // update the scratch maps, even if the scratch registers are now
      // dead.
      if (verboseDebug) System.out.println("RALL: End scratch interval " + 
                                           scratch.scratch + " " + s);
      i.remove();
      scratchMap.endScratchInterval(scratch.scratch,s);
      OPT_Register scratchContents = scratch.currentContents;
      if (scratchContents != null) {
        if (verboseDebug) System.out.println("RALL: End symbolic interval " + 
                                             scratchContents + " " + s);
        scratchMap.endSymbolicInterval(scratchContents,s);
      } 
    }
  }

  /**
   * Is a particular register dead immediately before instruction s.
   */
  boolean isDeadBefore(OPT_Register r, OPT_Instruction s) {

    OPT_NewLinearScan.BasicInterval bi = activeSet.getBasicInterval(r,s);
    // If there is no basic interval containing s, then r is dead before
    // s.
    if (bi == null) return true;
    // If the basic interval begins at s, then r is dead before
    // s.
    else if (bi.getBegin() == OPT_NewLinearScan.getDFN(s)) return true;
    else return false;
  }


  /**
   * Spill the contents of a scratch register to memory before 
   * instruction s.  
   */
  private void unloadScratchRegisterBefore(OPT_Instruction s, 
                                           ScratchRegister scratch) {
    // if the scratch register is not dirty, don't need to write anything, 
    // since the stack holds the current value
    if (!scratch.isDirty()) return;

    // spill the contents of the scratch register 
    OPT_Register scratchContents = scratch.currentContents;
    if (scratchContents != null) {
      int location = OPT_RegisterAllocatorState.getSpill(scratchContents);
      insertSpillBefore(s,scratch.scratch,getValueType(scratchContents),
                        location);
    }

  }
  /**
   * Restore the contents of a scratch register before instruction s.  
   */
  private void reloadScratchRegisterBefore(OPT_Instruction s, 
                                           ScratchRegister scratch) {
    if (scratch.hadToSpill()) {
      // Restore the live contents into the scratch register.
      int location = OPT_RegisterAllocatorState.getSpill(scratch.scratch);
      insertUnspillBefore(s,scratch.scratch,getValueType(scratch.scratch),
                          location);
    }
  }

  /**
   * Return the set of scratch registers which are currently reserved
   * for use in instruction s.
   */
  private HashSet getReservedScratchRegisters(OPT_Instruction s) {
    HashSet result = new HashSet(3);

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.currentContents != null && appearsIn(sr.currentContents,s)) {
        result.add(sr.scratch);
      }
    }
    return result;
  }

  /**
   * If there is a scratch register available which currently holds the
   * value of symbolic register r, then return that scratch register.
   *
   * Additionally, if there is a scratch register available which is
   * mapped to the same stack location as r, then return that scratch
   * register.
   *
   * Else return null.
   */
  private ScratchRegister getCurrentScratchRegister(OPT_Register r) {
    ScratchRegister result = null;

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.currentContents == r) {
        return sr;
      }
      int location = OPT_RegisterAllocatorState.getSpill(sr.currentContents);
      int location2 = OPT_RegisterAllocatorState.getSpill(r); 
      if (location == location2) {
        return sr;
      }
    }
    return null;
  }
  /**
   * If register r is currently in use as a scratch register, 
   * then return that scratch register.
   * Else return null.
   */
  private ScratchRegister getPhysicalScratchRegister(OPT_Register r) {
    ScratchRegister result = null;

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.scratch == r) {
        return sr;
      }
    }
    return null;
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to allocate space on the stack frame to store these
   * registers.
   *
   * @param n the number of GPR registers to save and restore.
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  int allocateSpaceForSysCall(int n) {
    int bytes = n * WORDSIZE;
    if (sysCallOffset == 0) {
      sysCallOffset = allocateOnStackFrame(bytes);
    }
    return sysCallOffset;
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to get the stack-frame offset previously reserved for this
   * data.
   *
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  int getOffsetForSysCall() {
    return sysCallOffset;
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
   *  Find a nonvolatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *
   *  TODO: Clean up this interface.
   *
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  final OPT_Register allocateNonVolatileRegister(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int physType = phys.getPhysicalRegisterType(symbReg);
    for (Enumeration e = phys.enumerateNonvolatilesBackwards(physType);
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isAvailable()) {
        realReg.allocateToRegister(symbReg);
        return realReg;
      }
    }
    return null;
  }

  /**
   *  This version is used to support LinearScan, and is only called
   *  from OPT_LinearScanLiveInterval.java
   *
   *  @param symbReg the symbolic register to allocate
   *  @param li the linear scan live interval
   *  @return the allocated register or null
   */
  final OPT_Register allocateRegister(OPT_Register symbReg,
                                      OPT_LinearScanLiveInterval live) {
    OPT_OptimizingCompilerException.UNREACHABLE("not used by new linear scan");
    return null;
  }

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * TODO: clean up the whole treatment of spill locations.
   *
   * @param type the type to spill
   * @return the spill location
   */
  final int allocateNewSpillLocation(int type) {

    // increment by the spill size
    spillLocNumber += OPT_PhysicalRegisterSet.getSpillSize(type);

    if (spillLocNumber + WORDSIZE > frameSize) {
      frameSize = spillLocNumber + WORDSIZE;
    }
    return spillLocNumber;
  }

  /**
   * Class to represent a physical register currently allocated as a
   * scratch register.
   */
  private static class ScratchRegister {
    /**
     * The physical register used as scratch.
     */
    OPT_Register scratch;

    /**
     * The current contents of scratch
     */
    OPT_Register currentContents;

    /**
     * Is this physical register currently dirty? (Must be written back to
     * memory?)
     */
    private boolean dirty = false;

    boolean isDirty() { return dirty; }
    void setDirty(boolean b) { dirty = b; }

    /**
     * Did we spill a value in order to free up this scratch register?
     */
    private boolean spilledIt = false;
    boolean hadToSpill() { return spilledIt; }
    void setHadToSpill(boolean b) { spilledIt = b; }


    ScratchRegister(OPT_Register scratch, OPT_Register currentContents) {
      this.scratch = scratch;
      this.currentContents = currentContents;
    }

    public String toString() { 
      String dirtyString = dirty ? "D" : "C";
      return "SCRATCH<" + scratch + "," + currentContents + "," +
        dirtyString + ">";
    }

  }
} 
