/*
 * (C) Copyright IBM Corp. 2001
 */
///$Id$

import instructionFormats.*;
import java.util.Enumeration;
/**
 * Class to manage the allocation of the "compiler-specific" portion of 
 * the stackframe.  This class holds only the architecture-specific
 * functions.
 * <p>
 *
 * TODO: Much of this code could still be factored out as
 * architecture-independent.
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Stephen Fink
 * @author Julian Dolby
 */
final class OPT_StackManager extends OPT_GenericStackManager
  implements OPT_Operators {
  
  private static boolean verboseDebug = false;

  /**
   * Use stack overflow checks?  It is UNSAFE to turn this off.
   */
  private static boolean STACK_OVERFLOW = true;

  /**
   * Allow the stack pointer to move around, in order to avoid scratch
   * register usage?
   */
  private static boolean FLOATING_ESP = true;

  /**
   * Size of a word, in bytes
   */
  private static final int WORDSIZE = 4;

  /**
   * Holds the set of scratch registers currently free for use.
   */
  private java.util.HashSet scratchAvailable = new java.util.HashSet(10);

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
    if (s.isCall()) {
      // if s is a call instruction, then we've already changed the SP as
      // part of the calling sequence, and have restored the frame pointer
      // from memory.  So, instead of using the usual spill sequence with
      // an offset from the stack pointer, use the memory location as an
      // offset from the frame pointer.
      OPT_Register FP = phys.getFP();
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),-location,
                                                         size,null,null);
      s.insertBefore(nonPEIGC(MIR_Move.create(move, M, rOp)));
    } else {
      OPT_StackLocationOperand spill = new OPT_StackLocationOperand(-location,
                                                                    size);
      s.insertBefore(nonPEIGC(MIR_Move.create(move, spill, rOp)));
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
    if (s.isCall()) {
      // if s is a call instruction, then we've already changed the SP as
      // part of the calling sequence, and have restored the frame pointer
      // from memory.  So, instead of using the usual spill sequence with
      // an offset from the stack pointer, use the memory location as an
      // offset from the frame pointer.
      OPT_Register FP = phys.getFP();
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),-location,
                                                         size,null,null);
      s.insertBefore(nonPEIGC(MIR_Move.create(move, rOp, M)));
    } else {
      OPT_StackLocationOperand spill = new OPT_StackLocationOperand(-location,
                                                                    size);
      s.insertBefore(nonPEIGC(MIR_Move.create(move, rOp, spill )));
    }
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
   * Return the size of the fixed poriton of the stack.
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
          // SJF: for some unknown reason, the following code doesn't
          // work. It appears that we're actually getting two different
          // OPT_Register objects that both represent EAX.  How does this
          // happen?  TODO: figure this out.
          // if (result.similar(val)) {
          //  inst = inst.remove();
          // }
          // In the meantime, use the following hack instead.
          if (result.isRegister() && val.isRegister() &&
              result.asRegister().register.number ==
              val.asRegister().register.number) {
            inst = inst.remove();
          }
          break;
        case IA32_FMOV_opcode:
          // remove frivolous moves
          result = MIR_Move.getResult(inst);
          val = MIR_Move.getValue(inst);
          // SJF: for some unknown reason, the following code doesn't
          // work. It appears that we're actually getting two different
          // OPT_Register objects that both represent EAX.  How does this
          // happen?  TODO: figure this out.
          // if (result.similar(val)) {
          //  inst = inst.remove();
          // }
          // In the meantime, use the following hack instead.
          if (result.isRegister() && val.isRegister() &&
              result.asRegister().register.number ==
              val.asRegister().register.number) {
            inst = inst.remove();
          }
          break;
        case LOAD_SPILLED_GPR_PARAM_opcode:
          OPT_Operand dest = MIR_Unary.getResult(inst);
          int param = MIR_Unary.getVal(inst).asIntConstant().value;
          int offset = getOffsetForSpilledParameter(param);
          OPT_StackLocationOperand location = new
            OPT_StackLocationOperand(offset, (byte)WORDSIZE);
          inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, dest,
                                                     location)));
          inst = inst.remove();
          break; 
        case LOAD_SPILLED_FLOAT_PARAM_opcode:
          dest = MIR_Unary.getResult(inst);
          param = MIR_Unary.getVal(inst).asIntConstant().value;
          offset = getOffsetForSpilledParameter(param);
          location = new OPT_StackLocationOperand(offset, (byte)(WORDSIZE));
          inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_FMOV, dest,
                                                     location)));
          inst = inst.remove();
          break; 
        case LOAD_SPILLED_DOUBLE_PARAM_opcode:
          dest = MIR_Unary.getResult(inst);
          param = MIR_Unary.getVal(inst).asIntConstant().value;
          offset = getOffsetForSpilledParameter(param);
          // For a double, we actually want to load 2 words
          // So: load from the word <em>below</em> the reported offset
          offset -= WORDSIZE;

          location = new OPT_StackLocationOperand(offset, (byte)(2*WORDSIZE));
          inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_FMOV, dest,
                                                     location)));
          inst = inst.remove();
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
   * Given an integer n, which corresponds to the nth word of parameter
   * data, return the offset from the frame pointer corresponding to 
   * the location of this parameter in the caller's stack frame.
   *
   * Assumption: FP points two words past the last parameter.
   */
  private int getOffsetForSpilledParameter(int n) {
    int nWords= ir.method.getParameterWords();
    if (!ir.method.isStatic()) {
      // add an extra word for the 'this' parameter
      nWords++;
    }
    int offset = (nWords-n+1)*WORDSIZE;
    return offset;
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
    
    if (!STACK_OVERFLOW) {
      plg.remove();
      return;
    }

    if (!ir.method.getDeclaringClass().isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.MIRInfo.info.isSaveVolatile()) {
      OPT_OptimizingCompilerException.UNREACHABLE(
                               "SaveVolatile should be uninterruptible");
      return;
    }
    
    // TODO: cache the stack limit offset in the processor object to save
    // a load.
    //
    // it's OK to use ECX as a scratch here:
    //    ECX := active Thread Object
    //    Trap if ESP <= active Thread Stack Limit

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_Register ECX = phys.getECX();
    
    //    ECX := active Thread Object
    OPT_MemoryOperand M = OPT_MemoryOperand.BD
      (R(PR), VM_Entrypoints.activeThreadOffset, (byte)WORDSIZE, null, null);
    plg.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(ECX), M)));

    //    Trap if ESP <= active Thread Stack Limit
    M = OPT_MemoryOperand.BD(R(ECX), VM_Entrypoints.stackLimitOffset,
                                 (byte)WORDSIZE, null, null);
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
    
    if (!STACK_OVERFLOW) {
      plg.remove();
      return;
    }

    if (!ir.method.getDeclaringClass().isInterruptible()) {
      plg.remove();
      return;
    }

    if (ir.MIRInfo.info.isSaveVolatile()) {
      OPT_OptimizingCompilerException.UNREACHABLE(
                               "SaveVolatile should be uninterruptible");
      return;
    }
    
    // TODO: cache the stack limit offset in the processor object to save
    // a load.
    //
    // it's OK to use ECX as a scratch here:
    //    ECX := active Thread Object
    //    ECX := active Thread Stack Limit
    //    ECX += frame Size
    //    Trap if ESP <= ECX

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register PR = phys.getPR();
    OPT_Register ESP = phys.getESP();
    OPT_Register ECX = phys.getECX();
    
    //    ECX := active Thread Object
    OPT_MemoryOperand M = OPT_MemoryOperand.BD
      (R(PR), VM_Entrypoints.activeThreadOffset, (byte)WORDSIZE, null, null);
    plg.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(ECX), M)));

    //    ECX := active Thread Stack Limit
    M = OPT_MemoryOperand.BD(R(ECX), VM_Entrypoints.stackLimitOffset,
                                 (byte)WORDSIZE, null, null);
    plg.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(ECX), M)));

    //    ECX += frame Size
    int frameSize = getFrameFixedSize();
    plg.insertBefore(nonPEIGC(MIR_BinaryAcc.create(IA32_ADD, R(ECX),
                                                   I(frameSize))));
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
    OPT_Register FP = phys.getFP(); 
    OPT_Register ESP = phys.getESP(); 
    OPT_Register PR = phys.getPR();
    
    // inst is the instruction immediately after the IR_PROLOGUE
    // instruction
    OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext().getNext();
    OPT_Instruction plg = inst.getPrev();

    int frameFixedSize = getFrameFixedSize();
    ir.MIRInfo.info.setFrameFixedSize(frameFixedSize);

    // I. Buy a stackframe (including overflow check)
    if (frameFixedSize >= STACK_SIZE_GUARD) {
      // 1. Insert Stack overflow check.  
      insertBigFrameStackOverflowCheck(plg);

      // 2. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, R(FP)));

      // 3. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, R(FP), R(ESP)));

      // 4. Store my compiled method id
      int cmid = ir.compiledMethodId;
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, I(cmid)));

      // 5. Mark in the IR that ESP is as positioned by the prologue.
      inst.insertBefore(MIR_Empty.create(MIR_ESP_PROLOGUE));

    } else {
      // 1. Save caller's frame pointer
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, R(FP)));

      // 2. Set my frame pointer to current value of stackpointer
      inst.insertBefore(MIR_Move.create(IA32_MOV, R(FP), R(ESP)));

      // 3. Store my compiled method id
      int cmid = ir.compiledMethodId;
      inst.insertBefore(MIR_UnaryNoRes.create(IA32_PUSH, I(cmid)));

      // 4. Insert Stack overflow check.  
      insertNormalStackOverflowCheck(plg);
      
      // 5. Mark in the IR that ESP is as positioned by the prologue.
      inst.insertBefore(MIR_Empty.create(MIR_ESP_PROLOGUE));
    }

    // II. Save any used volatile and non-volatile registers
    if (ir.MIRInfo.info.isSaveVolatile())  {
      saveVolatiles(inst);
      saveFloatingPointState(inst);
    }

    saveNonVolatiles(inst);

    // III. Store the frame pointer in the processor object.
    int fpOffset = VM_Entrypoints.framePointerOffset;
    OPT_MemoryOperand fpHome = OPT_MemoryOperand.BD(R(PR),
                                    fpOffset, (byte)WORDSIZE, null, null);
    inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, fpHome, R(FP))));

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
    OPT_Register FP = phys.getFP();

    // Save each non-volatile GPR used by this method. 
    int n = nNonvolatileGPRS - 1;
    for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
         e.hasMoreElements() && n >= 0 ; n--) {
      OPT_Register nv = (OPT_Register)e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                            -offset, (byte)WORDSIZE, 
                                            null, null);
      inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, M, R(nv))));
    }
  }

  /**
   * Insert code into the prologue to save the floating point state.
   *
   * @param inst the first instruction after the prologue.  
   */
  private void saveFloatingPointState(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                                       -fsaveLocation, 
                                                       (byte)WORDSIZE,
                                                       null, null);
    inst.insertBefore(nonPEIGC(MIR_FSave.create(IA32_FNSAVE, M)));
  }

  /**
   * Insert code into the epilogue to restore the floating point state.
   *
   * @param inst the return instruction after the epilogue.  
   */
  private void restoreFloatingPointState(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                                       -fsaveLocation, 
                                                       (byte)WORDSIZE,
                                                       null, null);
    inst.insertBefore(nonPEIGC(MIR_FSave.create(IA32_FRSTOR, M)));
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
    OPT_Register FP = phys.getFP();

    // Save each GPR. 
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs();
      e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileLocation[i];
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                            -location, (byte)WORDSIZE, 
                                            null, null);
      inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, M, R(r))));
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
    OPT_Register FP = phys.getFP();

    // Restore every GPR
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs(); 
      e.hasMoreElements(); i++){
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileLocation[i];
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                            -location, (byte)WORDSIZE, 
                                            null, null);
      inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(r), M)));
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
    OPT_Register FP = phys.getFP();

    int n = nNonvolatileGPRS - 1;
    for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
         e.hasMoreElements() && n >= 0 ; n--) {
      OPT_Register nv = (OPT_Register)e.nextElement();
      int offset = getNonvolatileGPROffset(n);
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(FP),
                                            -offset, (byte)WORDSIZE, 
                                            null, null);
      inst.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(nv), M)));
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
    OPT_Register FP = phys.getFP();
    OPT_Register ESP = phys.getESP(); 
    OPT_Register PR = phys.getPR();
    
    // 1. Restore the frame pointer before ripping off the stack frame
    int fpOffset = VM_Entrypoints.framePointerOffset;
    OPT_MemoryOperand fpHome = OPT_MemoryOperand.BD(R(PR),
                                    fpOffset, (byte)WORDSIZE, null, null);
    ret.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, R(FP), fpHome)));
    
    // 2. Restore any saved registers
    //    TODO: We wouldn't care about restoring our FP
    //          if this code only used ESP to 
    //          restore the nonVolatile registers. --dave
    if (ir.MIRInfo.info.isSaveVolatile())  {
      restoreVolatileRegisters(ret);
      restoreFloatingPointState(ret);
    }
    restoreNonVolatiles(ret);

    // 3. Restore caller's stackpointer and framepointer
    ret.insertBefore(MIR_Move.create(IA32_MOV, R(ESP), R(FP)));
    ret.insertBefore(MIR_Nullary.create(IA32_POP, R(FP)));
    
    // 4. Store the caller's frame pointer in the processor object.
    ret.insertBefore(nonPEIGC(MIR_Move.create(IA32_MOV, fpHome, R(FP))));
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

    if (s.isCall()) {
      // if s is a call instruction, then we've already changed the SP as
      // part of the calling sequence, and have restored the frame pointer
      // from memory.  So, instead of using the usual spill sequence with
      // an offset from the stack pointer, use the memory location as an
      // offset from the frame pointer.
      OPT_Register FP = phys.getFP();
      M = OPT_MemoryOperand.BD(R(FP),-location, (byte)size, null,null);
    } else {
      M = new OPT_StackLocationOperand(-location, (byte)size);
    }

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
    
    // If we return false, then we ensure that all relevant moves use a
    // scratch register.  This results in ESP never being adjusted.
    if (!FLOATING_ESP) return false;

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

    // We may need to introduce scratch registers for PEIs, since at a
    // PEI, we may need to force ESP to its home location for use in a
    // catch block.  
    if (isPEIWithCatch(s)) {
      int nMemory = 0;
      if (result.isMemory() || result.isStackLocation()) nMemory++;
      if (value.isMemory() || value.isStackLocation()) nMemory++;
      if (nMemory == 2) {
        return false;
      }
    }

    // If we get here, all is kosher.
    return true;
  }

  /**
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   *
   * TODO: this code should be architecture-independent.  Try to use this
   * code for PowerPC.
   */
  void insertSpillCode() {

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
      if (s.operator == BBEND || s.isPEI() || s.isBranch() || 
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
            ScratchRegister scratch = findScratchHome(r);
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
              if (!isScratchFreeMove(s) && (s.hasMemoryOperand())) {
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
      // Note that IA32_ADD sets the condition codes, and so is dangerous
      // to use in this context.  Instead, use the LEA instruction, which
      // does not set the condition codes.
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(ESP),delta, (byte)4, 
                                                 null, null); 
      s.insertBefore(nonPEIGC(MIR_Lea.create(IA32_LEA, R(ESP), M)));
      ESPOffset = desiredOffset;
    }
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
   *
   * @return true iff the move instruction was mutated to a PUSH
   * instruction (used for computation of effective addresses).
   */
  private boolean rewriteMoveInstruction(OPT_Instruction s) {

    // first attempt to mutate the move into a noop
    if (mutateMoveToNop(s)) {
      return false;
    }

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
        
        // if val uses ESP, fix it up. Note that PUSH computes the
        // effective address BEFORE decrementing ESP
        if (val instanceof OPT_MemoryOperand) {
          OPT_RegisterOperand base = val.asMemory().base;
          if (base != null && base.register == ESP) {
            val.asMemory().disp -= ESPOffset;
          }
        }

        return true;
      }
    } else {
      if (result instanceof OPT_MemoryOperand) {
        if (val instanceof OPT_StackLocationOperand) {
          int offset = ((OPT_StackLocationOperand)val).getOffset();
          offset = FPOffset2SPOffset(offset);
          moveESPBefore(s,offset);
          MIR_Nullary.mutate(s,IA32_POP,result);
          ESPOffset += 4;
        }
        // if result uses ESP, fix it up. Note that POP computes the
        // effective address AFTER incrementing ESP.
        OPT_RegisterOperand base = result.asMemory().base;
        if (base != null && base.register == ESP) {
          result.asMemory().disp -= ESPOffset;
        }
      }
    }
    return false;
  }

  /**
   * Update the current offset of ESP to account for a particular PUSH
   * instruction.
   */
  private void updateESPOffset(OPT_Instruction s) {
    if (VM.VerifyAssertions) VM.assert (s.operator() == IA32_PUSH);
    ESPOffset -= 4;
  }

  /**
   * For each OPT_StackLocationOperand in a basic block, replace the
   * operand with the appropriate OPT_MemoryOperand.
   */
  private void rewriteStackLocations(OPT_BasicBlock bb) {
    OPT_Register ESP = ir.regpool.getPhysicalRegisterSet().getESP();

    for (Enumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();

      // After the prologue, ESP is set to the top of the frame.
      if (s.operator() == MIR_ESP_PROLOGUE) {
        ESPOffset = getFrameFixedSize() - WORDSIZE;
        continue;
      }
      
      // with a PUSH instruction, the effective address for other operands
      // is computed BEFORE the adjustment to ESP.  So, delay updating the
      // ESP offset for a PUSH instruction until all operands are
      // processed.
      boolean delayPushESP = false;
      if (s.operator() == IA32_MOV && !isPEIWithCatch(s)) {
        delayPushESP = rewriteMoveInstruction(s);
      } else if (appearsIn(ESP,s) || isPEIWithCatch(s) || s.isCall()) {
        // The instruction uses the fixed value of ESP.  Reset ESP before
        // s.
        moveESPBefore(s,0);
      }

      for (Enumeration ops = s.getOperands(); ops.hasMoreElements(); ) {
        OPT_Operand op = (OPT_Operand)ops.nextElement();

        if (op instanceof OPT_StackLocationOperand) {
          int offset = ((OPT_StackLocationOperand)op).getOffset();
          byte size = ((OPT_StackLocationOperand)op).getSize();
          offset = FPOffset2SPOffset(offset);
          offset -= ESPOffset;
          OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(ESP),offset,
                                                     size, null, null); 
          s.replaceOperand(op,M);
        }
      }
      if (delayPushESP) {
        updateESPOffset(s);
      }
    }
    // reset the SP to its home location at the end of the basic block.
    if (ESPOffset != 0) {
      OPT_MemoryOperand M = OPT_MemoryOperand.BD(R(ESP),-ESPOffset, (byte)4, 
                                                 null, null); 
      OPT_Instruction s = nonPEIGC(MIR_Lea.create(IA32_LEA, R(ESP), M));
      bb.appendInstructionRespectingTerminalBranch(s);
      ESPOffset = 0;
    }
  }
  /**
   * Walk through the IR.  For each OPT_StackLocationOperand, replace the
   * operand with the appropriate OPT_MemoryOperand.
   */
  private void rewriteStackLocations() {
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock b = (OPT_BasicBlock)e.nextElement();
      rewriteStackLocations(b);
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
   * Return a FPR that does not appear in instruction s.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getFPRNotUsedIn(OPT_Instruction s,
                                       java.util.HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      if (!appearsIn(r,s) && !r.isPinned() && !reserved.contains(r)) {
        return r;
      }
    }

    OPT_OptimizingCompilerException.TODO(
           "Could not find a free FPR in spill situation");
    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s.  
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getGPRNotUsedIn(OPT_Instruction s, 
                                       java.util.HashSet reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      if (!appearsIn(r,s) && !r.isPinned() && !reserved.contains(r)) {
        return r;
      }
    }
    // next try the non-volatiles
    for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      if (!appearsIn(r,s) && !r.isPinned() && !reserved.contains(r)) {
        return r;
      }
    }
    OPT_OptimizingCompilerException.TODO(
           "Could not find a free GPR in spill situation");
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

    // All call instructions use ebp
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (s.isCall() && r == phys.getEBP()) {
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
    ScratchRegister sr = findAvailableScratchRegister(symb,s);
    if (sr == null) {
      // No scratch register is currently available.  Find a physical
      // register that is not used in the instruction, and spill it
      // in order to free it for use.
      java.util.HashSet reservedScratch = getReservedScratchRegisters(s);

      OPT_Register phys = null;
      if (symb.isFloatingPoint()) {
         phys = getFPRNotUsedIn(s,reservedScratch);
      } else {
         phys = getGPRNotUsedIn(s,reservedScratch);
      }
      sr = createScratchBefore(s,phys,symb);

      // update mapping information
      scratchMap.beginSymbolicInterval(symb,sr.scratch,s);
    } else {
      // make the scratch register available to hold the new 
      // symbolic register
      OPT_Register current = sr.currentContents;
      
      if (current != null && current != symb) {
        // record that the current symbolic register is no longer assigned
        // to a scratch.
        scratchMap.endSymbolicInterval(current,s);
        scratchMap.beginSymbolicInterval(symb,sr.scratch,s);

        int location = OPT_RegisterAllocatorState.getSpill(current);
        int location2 = OPT_RegisterAllocatorState.getSpill(symb);
        if (location != location2) {
          insertSpillBefore(s,sr.scratch,getValueType(current),location);
        }
      }
    }
    
    // Record the new contents of the scratch register
    sr.currentContents = symb;

    return sr;
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
    ScratchRegister sr = findAvailableScratchRegister(symb,s);
    if (sr == null) {
      // No scratch register is currently available.  Find a physical
      // register that is not used in the instruction, and spill it
      // in order to free it for use.
      java.util.HashSet reservedScratch = getReservedScratchRegisters(s);

      OPT_Register phys = null;
      if (symb.isFloatingPoint()) {
         phys = getFPRNotUsedIn(s,reservedScratch);
      } else {
         phys = getGPRNotUsedIn(s,reservedScratch);
      }
      sr = createScratchBefore(s,phys,symb);
    }

    OPT_Register scratchContents = sr.currentContents;
    if (scratchContents != symb) {
      if (scratchContents != null) {
        // the scratch register currently holds a different 
        // symbolic register.
        // spill the contents of the scratch register to free it up.
        unloadScratchRegisterBefore(s,sr);

        // Start a new interval for the scratch register.
        scratchMap.beginScratchInterval(sr.scratch,s);
      }

      // Now load up the scratch register.
      // since symbReg must have been previously spilled, get the spill
      // location previous assigned to symbReg
      int location = OPT_RegisterAllocatorState.getSpill(symb);
      insertUnspillBefore(s,sr.scratch,getValueType(symb),location);
      
      // we have not yet written to sr, so mark it 'clean'
      sr.setDirty(false);

      // update mapping information
      scratchMap.beginSymbolicInterval(symb,sr.scratch,s);

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

    insertSpillBefore(s, r, (byte)type, spillLocation);

    ScratchRegister sr = new ScratchRegister(r,null);
    
    // record that register r is currently available as a scratch register
    scratchAvailable.add(sr);

    // update mapping information
    scratchMap.beginScratchInterval(r,s);

    return sr;
  }

  /**
   * Walk over the currently available scratch registers. 
   *
   * For any scratch register r which is def'ed by instruction s, 
   * spill r before s and remove r from the pool of available scratch 
   * registers.  
   *
   * For any scratch register r which is used by instruction s, 
   * restore r before s and remove r from the pool of available scratch 
   * registers.  
   *
   * For any scratch register r which has current contents symb, and 
   * symb is spilled to location M, and s defs M: the old value of symb is
   * dead.  Mark this.
   */
  private void restoreScratchRegistersBefore(OPT_Instruction s) {
    for (java.util.Iterator i = scratchAvailable.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();
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
        i.remove();
        removed = true;
        unloaded = true;
      }

      if (usedIn(scratch.scratch,s) ||
          (s.operator == IA32_FCLEAR && scratch.scratch.isFloatingPoint())) {
        // first spill the currents contents of the scratch register to 
        // memory 
        if (!unloaded) {
          if (verboseDebug) {
            System.out.println("RESTORE : unload because used " + scratch);
          }
          unloadScratchRegisterBefore(s,scratch);
        }
        // s uses the scratch register, so restore the correct contents.
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
    for (java.util.Iterator i = scratchAvailable.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();

      // SPECIAL CASE: If s is a return instruction, only restore the 
      // scratch
      // registers that are used by s.  The others are dead.
      if (!s.isReturn() || usedIn(scratch.scratch,s)) {
        unloadScratchRegisterBefore(s,scratch);
        reloadScratchRegisterBefore(s,scratch);
      } else {
        // update the scratch maps, even if the scratch registers are now
        // dead.
        scratchMap.endScratchInterval(scratch.scratch,s);
        OPT_Register scratchContents = scratch.currentContents;
        if (scratchContents != null) {
          scratchMap.endSymbolicInterval(scratchContents,s);
        } 
      }

      // remove the scratch register from the pool of available scratch
      // registers.  
      i.remove();
    }
  }


  /**
   * Spill the contents of a scratch register to memory before 
   * instruction s.  
   */
  private void unloadScratchRegisterBefore(OPT_Instruction s, 
                                            ScratchRegister scratch) {
    
    // update mapping information
    scratchMap.endScratchInterval(scratch.scratch,s);
    OPT_Register scratchContents = scratch.currentContents;
    if (scratchContents != null) {
      scratchMap.endSymbolicInterval(scratchContents,s);
    } 
    
    // if the scratch register is not dirty, don't need to write anything, 
    // since the stack holds the current value
    if (!scratch.isDirty()) return;
    
    // spill the contents of the scratch register 
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
    // Restore the live contents into the scratch register.
    int location = OPT_RegisterAllocatorState.getSpill(scratch.scratch);
    insertUnspillBefore(s,scratch.scratch,getValueType(scratch.scratch),
                        location);
  }

  /**
   * If any of the currently live scratch registers holds the value of
   * symbolic register r, then return the scratch register.  Else return
   * null.
   */
  private ScratchRegister findScratchHome(OPT_Register r) {

    int location1 = OPT_RegisterAllocatorState.getSpill(r);
    for (java.util.Iterator i = scratchAvailable.iterator(); i.hasNext(); ) {
      ScratchRegister s = (ScratchRegister)i.next();
      OPT_Register current = s.currentContents;
      if (current == r) {
        return s;
      }
      // If a scratch register currently holds a register that is mapped
      // to the same spill location as r, then we assume r and s.current
      // can be coalesced.  So, return this case as a match.
      if (OPT_RegisterAllocatorState.getSpill(current) == location1) {
        return s;
      }
    }
    return null;
  }
  /**
   * Return the set of scratch registers which are currently reserved
   * for use in instruction s.
   */
  private java.util.HashSet getReservedScratchRegisters(OPT_Instruction s) {
    java.util.HashSet result = new java.util.HashSet(3);

    for (java.util.Iterator i = scratchAvailable.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.currentContents != null && appearsIn(sr.currentContents,s)) {
        result.add(sr.scratch);
      }
    }
    return result;
  }
  /**
   * Return a scratch physical register that is available to hold a
   * the value of the symbolic register r in instruction s.  
   * Return null if no scratch physical register is available. 
   *
   * If there is a scratch register available which currently holds the
   * value of symbolic register r, then return that scratch register.
   */
  private ScratchRegister findAvailableScratchRegister(OPT_Register r, 
                                                 OPT_Instruction s) {
    ScratchRegister result = null;

    for (java.util.Iterator i = scratchAvailable.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (appearsIn(sr.scratch,s)) {
        // The physical scratch register already appears in s, so we can't 
        // use it as a scratch register for another value.
        continue;
      } else if (sr.currentContents != null) {
        // The scratch register currently holds another value.
        int location = OPT_RegisterAllocatorState.getSpill(sr.currentContents);
        int location2 = OPT_RegisterAllocatorState.getSpill(r); 
        if (location == location2) {
          // r and the scratch register are mapped to the same spill
          // location.  So, the two cannot be simultaneously live, so 
          // it's OK to use the scratch location to hold the value of r.
          return sr;
        } else if (sr.currentContents == r) {
          OPT_OptimizingCompilerException.UNREACHABLE("Should be covered by previous case");
          return sr;
        } else if (appearsIn(sr.currentContents,s)) {
          // The current value of the scratch register already appears in
          // s, so we must continue to use the scratch register for its
          // current purpose.
          continue;
        }
      }
      if (r.isFloatingPoint() ^ sr.scratch.isFloatingPoint()) {
        // the type of the scratch register cannot hold r's value.
        continue;
      }
      result = sr;
    }
    return result;
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
   * scratch register
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
