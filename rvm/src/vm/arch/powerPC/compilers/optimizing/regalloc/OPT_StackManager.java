/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * Class to manage the allocation of the "compiler-specific" portion of 
 * the stackframe.  This class holds only the architecture-specific
 * functions.
 * <p>
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Stephen Fink
 */
public final class OPT_StackManager extends OPT_GenericStackManager
  implements OPT_Operators {
  
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

    // increment by the spill size
    spillPointer += OPT_PhysicalRegisterSet.getSpillSize(type);

    if (spillPointer > frameSize) {
      frameSize = spillPointer;
    }
    return spillPointer - OPT_PhysicalRegisterSet.getSpillSize(type);
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
        case PPC_MOVE_opcode:
        case PPC_FMR_opcode:
          // remove frivolous moves
          if (MIR_Move.getResult(inst).register.number ==
              MIR_Move.getValue(inst).register.number)
            inst = inst.remove();
          break;
        case PPC_BLR_opcode:
          if (frameIsRequired()) 
            insertEpilogue(inst);
          break;
        case PPC_LFD_opcode:
        case PPC_LFS_opcode:
        case PPC_LAddr_opcode:
        case PPC_LInt_opcode:
        case PPC_LWZ_opcode:
          // the following to handle spilled parameters
          // SJF: this is ugly.  clean it up someday.
          if (MIR_Load.getAddress(inst).register ==
              ir.regpool.getPhysicalRegisterSet().getFP()) {
            OPT_Operand one = MIR_Load.getOffset(inst);
            if (one instanceof OPT_IntConstantOperand) {
              int offset = ((OPT_IntConstantOperand) one).value;
              if (offset <= -256) {
                MIR_Load.setOffset(inst, IC(frameSize - offset - 256));
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
  final void insertSpillBefore(OPT_Instruction s, OPT_Register r,
                               byte type, int location) {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    if (type == FLOAT_VALUE) {
      s.insertBack(MIR_Store.create(PPC_STFS, F(r), A(FP),
                                    IC(location)));
    } else if (type == DOUBLE_VALUE) {
      s.insertBack(MIR_Store.create(PPC_STFD, D(r), A(FP),
                                    IC(location)));
    } else if (type == INT_VALUE) {      // integer or half of long
      s.insertBack(MIR_Store.create(PPC_STAddr, A(r), A(FP),
                                    IC(location)));
    //-#if RVM_FOR_64_ADDR
    } else if (type == LONG_VALUE) {     // long
      s.insertBack(MIR_Store.create(PPC_STAddr, L(r), A(FP),
                                    IC(location)));
    //-#endif
    } else
      throw new OPT_OptimizingCompilerException("insertSpillBefore", 
                                                "unsupported type " +
                                                type);
  }
  
  /**
   * Create an MIR instruction to move rhs into lhs
   */
  final OPT_Instruction makeMoveInstruction(OPT_Register lhs, 
                                            OPT_Register rhs) {
    if (rhs.isFloatingPoint() && lhs.isFloatingPoint()) {
      return MIR_Move.create(PPC_FMR, D(lhs), D(rhs));
    } else if (rhs.isInteger() && lhs.isInteger()) { // integer
      return MIR_Move.create(PPC_MOVE, A(lhs), A(rhs));
    } else
      throw new OPT_OptimizingCompilerException("RegAlloc", 
                                                "unknown register:", 
                                                lhs.toString());
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
    OPT_Register FP = phys.getFP();
    if (type == CONDITION_VALUE) {
      OPT_Register temp = phys.getTemp();
      s.insertBack(MIR_Load.create(PPC_LWZ, I(temp), A(FP),
                                   IC(location)));
    } else if (type == DOUBLE_VALUE) {
        s.insertBack(MIR_Load.create(PPC_LFD, D(r), A(FP), IC(location)));
    } else if (type == FLOAT_VALUE) {
      s.insertBack(MIR_Load.create(PPC_LFS, F(r), A(FP), IC(location)));
    } else if (type == INT_VALUE) { // integer or half of long
      s.insertBack(MIR_Load.create(PPC_LAddr, A(r), A(FP), IC(location)));
    //-#if RVM_FOR_64_ADDR
    } else if (type == LONG_VALUE) { // long
      s.insertBack(MIR_Load.create(PPC_LAddr, L(r), A(FP), IC(location)));
    //-#endif
    } else {
      throw new OPT_OptimizingCompilerException("insertUnspillBefore", 
                                                "unknown type:" + type);
    }
  }
  
  /**
   * Insert the epilogue before a particular return instruction.
   *
   * @param ret the return instruction.
   */
  final private void insertEpilogue(OPT_Instruction ret) {

    // 1. Restore any saved registers
    if (ir.compiledMethod.isSaveVolatile()) {
      restoreVolatileRegisters(ret);
    }
    restoreNonVolatiles(ret);

    // 2. Restore return address
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register temp = phys.getTemp();
    OPT_Register FP = phys.getFP();
    ret.insertBack(MIR_Load.create(PPC_LAddr, A(temp), A(FP),
                                   IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET + frameSize)));

    // 3. Load return address into LR
    ret.insertBack(MIR_Move.create(PPC_MTSPR, A(phys.getLR()),
                                   A(phys.getTemp())));

    // 4. Restore old FP
    ret.insertBack(MIR_Binary.create(PPC_ADDI, A(FP), A(FP), IC(frameSize)));

  }
  
  /**
   * Insert code in the prologue to save the 
   * volatile registers.
   *
   * @param inst 
   */
  private void saveVolatiles(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();

    // 1. save the volatile GPRs
    OPT_Register FP = phys.getFP();
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileGPRLocation[i];
      inst.insertBefore(MIR_Store.create(PPC_STAddr, A(r), A(FP), IC(location)));
    }
    // 2. save the volatile FPRs
    i = 0;
    for (Enumeration e = phys.enumerateVolatileFPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileFPRLocation[i];
      inst.insertBefore(MIR_Store.create(PPC_STFD, D(r), A(FP), IC(location)));
    }
    
    // 3. Save some special registers
    OPT_Register temp = phys.getTemp();
    
    inst.insertBack(MIR_Move.create(PPC_MFSPR, I(temp), I(phys.getXER()) ));
    inst.insertBack(MIR_Store.create(PPC_STW, I(temp), A(FP), IC(saveXERLocation)));

    inst.insertBack(MIR_Move.create(PPC_MFSPR, A(temp), A(phys.getCTR())));
    inst.insertBack(MIR_Store.create(PPC_STAddr, A(temp), A(FP), IC(saveCTRLocation)));

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
    if (ir.compiledMethod.isSaveVolatile()) {
      // pretend we use all non-volatiles
      nNonvolatileGPRS = phys.getNumberOfNonvolatileGPRs();
    }

    // 1. save the nonvolatile GPRs
    int n = nNonvolatileGPRS - 1;
    OPT_Register FP = phys.getFP();
    //-#if RVM_FOR_32_ADDR
    if (n > MULTIPLE_CUTOFF) {
      // use a stm
      OPT_Register nv = null;
      for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
           e.hasMoreElements() && n >= 0 ; n--) {
        nv = (OPT_Register)e.nextElement();
      }
      n++;
      OPT_RegisterOperand range = I(nv);
      // YUCK!!! Why is this crap in register operand??
      range.setRange(FIRST_INT + LAST_NONVOLATILE_GPR - nv.number);
      int offset = getNonvolatileGPROffset(n);
      inst.insertBack(MIR_Store.create(PPC_STMW, range, A(FP), IC(offset)));
    } else
    //-#endif
    {
      // use a sequence of load instructions
    for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards();
           e.hasMoreElements() && n >= 0 ; n--) {
        OPT_Register nv = (OPT_Register)e.nextElement();
        int offset = getNonvolatileGPROffset(n);
        inst.insertBack(MIR_Store.create (PPC_STAddr, A(nv), A(FP), IC(offset)));
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
      for (Enumeration e = phys.enumerateNonvolatileFPRsBackwards(); 
         e.hasMoreElements() && n >= 0 ; n--) {
        OPT_Register nv = (OPT_Register)e.nextElement();
        int offset = getNonvolatileFPROffset(n);
        inst.insertBack(MIR_Store.create(PPC_STFD, D(nv), A(FP), IC(offset)));
      }
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

    // 1. restore the nonvolatile GPRs
    int n = nNonvolatileGPRS - 1;
    OPT_Register FP = phys.getFP();
    //-#if RVM_FOR_32_ADDR
    if (n > MULTIPLE_CUTOFF) {
      // use an lm
      OPT_Register nv = null;
      for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards(); 
           e.hasMoreElements() && n >= 0 ; n--) {
        nv = (OPT_Register)e.nextElement();
      }
      n++;
      OPT_RegisterOperand range = I(nv);
      // YUCK!!! Why is this crap in register operand??
      range.setRange(FIRST_INT + LAST_NONVOLATILE_GPR - nv.number);
      int offset = getNonvolatileGPROffset(n);
      inst.insertBack(MIR_Load.create(PPC_LMW, range, A(FP), IC(offset)));
    } else
    //-#endif
    {
      for (Enumeration e = phys.enumerateNonvolatileGPRsBackwards();
           e.hasMoreElements() && n >= 0 ; n--) {
        OPT_Register nv = (OPT_Register)e.nextElement();
        int offset = getNonvolatileGPROffset(n);
        inst.insertBack(MIR_Load.create (PPC_LAddr, A(nv), A(FP), IC(offset)));
      }
    }
    // Note that save-volatiles are forbidden from using nonvolatile FPRs.
    if (!ir.compiledMethod.isSaveVolatile()) {
      // 1. restore the nonvolatile FPRs
      int nNonvolatileFPRS = ir.compiledMethod.getNumberOfNonvolatileFPRs();
      n = nNonvolatileFPRS - 1;
      // use a sequence of load instructions
      for (Enumeration e = phys.enumerateNonvolatileFPRsBackwards(); 
           e.hasMoreElements() && n >= 0 ; n--) {
        OPT_Register nv = (OPT_Register)e.nextElement();
        int offset = getNonvolatileFPROffset(n);
        inst.insertBack(MIR_Load.create (PPC_LFD, D(nv), A(FP), IC(offset)));
      }
    }
  }

  /**
   * Insert code before a return instruction to restore the 
   * volatile registers.
   *
   * @param inst the return instruction
   */
  private void restoreVolatileRegisters(OPT_Instruction inst) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int nNonvolatileGPRS = ir.compiledMethod.getNumberOfNonvolatileGPRs();

    // 1. restore the volatile GPRs
    OPT_Register FP = phys.getFP();
    int i = 0;
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileGPRLocation[i];
      inst.insertBefore(MIR_Load.create(PPC_LAddr, A(r), A(FP), IC(location)));
    }
    // 2. restore the volatile FPRs
    i = 0;
    for (Enumeration e = phys.enumerateVolatileFPRs();
         e.hasMoreElements(); i++) {
      OPT_Register r = (OPT_Register)e.nextElement();
      int location = saveVolatileFPRLocation[i];
      inst.insertBefore(MIR_Load.create(PPC_LFD, D(r), A(FP), IC(location)));
    }
    // 3. Restore some special registers
    OPT_Register temp = phys.getTemp();
    inst.insertBack(MIR_Load.create(PPC_LInt, I(temp), A(FP), IC(saveXERLocation)));
    inst.insertBack(MIR_Move.create(PPC_MTSPR,
                                 I(phys.getXER()), I(temp)));

    inst.insertBack(MIR_Load.create(PPC_LAddr, A(temp), A(FP), 
                                    IC(saveCTRLocation)));
    inst.insertBack(MIR_Move.create(PPC_MTSPR,
                                 A(phys.getCTR()), A(temp)));
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
   *  2    l       S1 threadSwitchRequestedOffset(PR)         # setting cr2 for yield point
   *  3    stu     FP -frameSize(FP)                          # buy frame, save caller's fp
   *  4    l       S0 stackLimitOffset(S0)                    # stack overflow check
   *  5    <save used non volatiles>
   *  6    cmpi    cr2 S1 0x0                                 # setting cr2 for yield point (S1 is now free)
   *  7    lil     S1 CMID                                    # cmid
   *  8    st      00 STACKFRAME_NEXT_INSTRUCTION_OFFSET(FP)  # return addr (00 is now free)
   *  9    st      S1 STACKFRAME_METHOD_ID_OFFSET(FP)         # cmid
   *  10   tgt     S0, FP                                     # stack overflow check (already bought frame)
   */

  /**
   * Schedule prologue for 'normal' case (see above)
   */
  final void insertNormalPrologue() {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_Register PR = phys.getPR();
    OPT_Register TSR = phys.getTSR();
    OPT_Register R0 = phys.getTemp();
    OPT_Register S0 = phys.getGPR(FIRST_SCRATCH_GPR);
    OPT_Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = hasPrologueYieldpoint();

    int frameFixedSize = getFrameFixedSize();
    ir.compiledMethod.setFrameFixedSize(frameFixedSize);
    
    if (frameFixedSize >= STACK_SIZE_GUARD || ir.compiledMethod.isSaveVolatile()) {
      insertExceptionalPrologue();
      return;
    }

    OPT_Instruction ptr = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, A(R0),
                                     A(phys.getLR()))); // 1
 
    if (yp) {
      ptr.insertBefore(MIR_Load.create(PPC_LInt, I(S1), A(PR),
                                       IC(VM_Entrypoints.threadSwitchRequestedField.getOffset()))); // 2
    }

    ptr.insertBefore(MIR_StoreUpdate.create(PPC_STAddrU, A(FP), A(FP),
                                            IC(-frameSize))); // 3

    if (stackOverflow) {
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S0),
                                       A(phys.getPR()), 
                                       IC(VM_Entrypoints.activeThreadStackLimitField.getOffset()))); // 4
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
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, I(S1),IC(cmid>>>16))); // 7 (a)
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, I(S1), I(S1),
                                         IC(cmid&0xffff))); // 7 (b)
    }
    ptr.insertBefore(MIR_Store.create(PPC_STAddr, A(R0), A(FP), 
                                      IC(frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET))); // 8
    ptr.insertBefore(MIR_Store.create(PPC_STW, I(S1), A(FP), 
                                      IC(STACKFRAME_METHOD_ID_OFFSET))); // 9

    ptr.insertBefore(Empty.create(IR_ENDPROLOGUE));

    if (stackOverflow) {
      // Mutate the Prologue instruction into the trap
      MIR_Trap.mutate(ptr, PPC_TAddr, OPT_PowerPCTrapOperand.GREATER(), A(S0), A(FP),
                      OPT_TrapCodeOperand.StackOverflow()); // 10
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
  final void insertExceptionalPrologue () {
    if (frameSize >= 0x7ff0) {
      throw new OPT_OptimizingCompilerException("Stackframe size exceeded!");
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_Register PR = phys.getPR();
    OPT_Register TSR= phys.getTSR();
    OPT_Register R0 = phys.getTemp();
    OPT_Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = hasPrologueYieldpoint();

    OPT_Instruction ptr = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    // Stack overflow check
    if (stackOverflow) {
      // R0 is fairly useless (can't be operand 1 of an addi or the base ptr
      // of a load) so, free up S1 for use by briefly saving its contents in the
      // return address slot of my caller's frame
      ptr.insertBefore(MIR_Store.create(PPC_STAddr, A(S1), A(FP), 
                                        IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S1), A(phys.getPR()), 
                                       IC(VM_Entrypoints.activeThreadStackLimitField.getOffset())));
      ptr.insertBefore(MIR_Binary.create(PPC_ADDI, A(R0), A(S1), 
                        IC(frameSize)));
      ptr.insertBefore(MIR_Load.create(PPC_LAddr, A(S1), A(FP), 
                                       IC(STACKFRAME_NEXT_INSTRUCTION_OFFSET)));


      // Mutate the Prologue holder instruction into the trap
      MIR_Trap.mutate(ptr, PPC_TAddr, OPT_PowerPCTrapOperand.LESS(), A(FP), A(R0),
                      OPT_TrapCodeOperand.StackOverflow()); 

      // advance ptr because we want the remaining instructions to come after
      // the trap
      ptr = ptr.nextInstructionInCodeOrder();

    } else {
      // no stack overflow test, so we must remove the IR_Prologue instruction
      OPT_Instruction next = ptr.nextInstructionInCodeOrder();
      ptr.remove();
      ptr = next;
    }
    
    // Buy stack frame, save LR, caller's FP 
    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, A(R0),
                                     A(phys.getLR())));
    ptr.insertBefore(MIR_StoreUpdate.create(PPC_STAddrU, A(FP), A(FP),
                                            IC(-frameSize)));
    ptr.insertBefore(MIR_Store.create(PPC_STAddr, A(R0), A(FP),
                                      IC(frameSize+STACKFRAME_NEXT_INSTRUCTION_OFFSET)));

    
    // Store cmid
    int cmid = ir.compiledMethod.getId();
    if (cmid <= 0x7fff) {
      ptr.insertBefore(MIR_Unary.create(PPC_LDI, I(R0), IC(cmid)));
    } else {
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, I(R0),IC(cmid>>>16)));
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, I(R0), I(R0),IC(cmid&0xffff)));
    }
    ptr.insertBefore(MIR_Store.create(PPC_STW, I(R0), A(FP), 
                                      IC(STACKFRAME_METHOD_ID_OFFSET)));

    // Now save the volatile/nonvolatile registers
    if (ir.compiledMethod.isSaveVolatile()) {
      saveVolatiles(ptr);
    }
    saveNonVolatiles(ptr);
    
    // Threadswitch
    if (yp) {
      ptr.insertBefore(MIR_Load.create(PPC_LInt, I(R0), A(PR), 
                                       IC(VM_Entrypoints.threadSwitchRequestedField.getOffset())));
      ptr.insertBefore(MIR_Binary.create(PPC_CMPI, I(TSR), I(R0), IC(0)));
    }
    ptr.insertBefore(Empty.create(IR_ENDPROLOGUE));
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
      frameSize += numGprNv * BYTES_IN_ADDRESS;

      int numFprNv = phys.getNumberOfNonvolatileFPRs();
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short)numFprNv);
      frameSize += numFprNv * BYTES_IN_DOUBLE;

      frameSize = align(frameSize, STACKFRAME_ALIGNMENT);

      // Record that we need a stack frame.
      setFrameRequired();

      // Map each volatile GPR to a spill location.
      int i = 0;
      for (Enumeration e = phys.enumerateVolatileGPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        saveVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);      
      }

      // Map each non-volatile GPR register to a spill location.
      i=0;
      for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
        // Note that as a side effect, the following call bumps up the
        // frame size.
        nonVolatileGPRLocation[i] = allocateNewSpillLocation(INT_REG);      
      }
      
      // Map some special registers to spill locations.
      saveXERLocation = allocateNewSpillLocation(INT_REG);
      saveCTRLocation = allocateNewSpillLocation(INT_REG);
      i=0;

      for (Enumeration e = phys.enumerateVolatileFPRs(); 
           e.hasMoreElements(); i++)  {
        OPT_Register r = (OPT_Register)e.nextElement();
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
      i = 0;
      int numFprNv = 0;
      for (Enumeration e = phys.enumerateNonvolatileFPRs();
           e.hasMoreElements(); ) {
        OPT_Register r = (OPT_Register)e.nextElement();
        if (r.isTouched() ) {
          // Note that as a side effect, the following call bumps up the
          // frame size.
          nonVolatileFPRLocation[i++] = allocateNewSpillLocation(DOUBLE_REG);
          numFprNv++;
        }
      }
      // Update the VM_OptCompiledMethod object.
      ir.compiledMethod.setNumberOfNonvolatileGPRs((short)numGprNv);
      ir.compiledMethod.setNumberOfNonvolatileFPRs((short)numFprNv);
      if (numGprNv > 0 || numFprNv > 0) {
        int gprOffset = getNonvolatileGPROffset(0);
        ir.compiledMethod.setUnsignedNonVolatileOffset(gprOffset);
        // record that we need a stack frame
        setFrameRequired();
      } else {
        ir.compiledMethod.setUnsignedNonVolatileOffset(0);
      }
      frameSize = align(frameSize, STACKFRAME_ALIGNMENT);
    }
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
              && scratch.scratch.isVolatile())) {
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
          !isLegal(scratch.currentContents,scratch.scratch,s)) {
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
  protected static final int MULTIPLE_CUTOFF = 4;

  /**
   * Initializes the "tmp" regs for this object
   * @param ir the governing ir
   */
  final void initForArch(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    phys.getJTOC().reserveRegister();
    phys.getFirstConditionRegister().reserveRegister();
  }

  /**
   * Is a particular instruction a system call?
   */
  boolean isSysCall(OPT_Instruction s) {
    return s.operator == PPC_BCTRL_SYS || s.operator == PPC_BL_SYS;
  } 

  /**
   * Given symbolic register r in instruction s, do we need to ensure that
   * r is in a scratch register is s (as opposed to a memory operand)
   */
  boolean needScratch(OPT_Register r, OPT_Instruction s) {
    //-#if RVM_WITH_OSR
    if (s.operator == YIELDPOINT_OSR) return false; 
    //-#endif
    
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
  void replaceOperandWithSpillLocation(OPT_Instruction s, 
                                       OPT_RegisterOperand symb) {
    // PowerPC does not support memory operands.
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }
}
