/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;
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
final class OPT_StackManager extends OPT_GenericStackManager
  implements OPT_Operators {
  
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
    if (type == CONDITION_VALUE) {
      s.insertBack(MIR_Move.create(PPC_MFCR,
                                   R(phys.getTemp()),
                                   R(phys.getCR())));
      s.insertBack(nonPEIGC(MIR_Store.create(PPC_STW,
                                             R(phys.getTemp()), R(FP),
                                             I(location))));
    } else if (type == FLOAT_VALUE) {
      s.insertBack(nonPEIGC(MIR_Store.create(PPC_STFS, F(r), R(FP),
                                             I(location))));
    } else if (type == DOUBLE_VALUE) {
      s.insertBack(nonPEIGC(MIR_Store.create(PPC_STFD, D(r), R(FP),
                                             I(location))));
    } else if (type == INT_VALUE) {      // integer or half of long
      s.insertBack(nonPEIGC(MIR_Store.create(PPC_STW, R(r), R(FP),
                                             I(location))));
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
      return MIR_Move.create(PPC_MOVE, R(lhs), R(rhs));
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
      s.insertBack(nonPEIGC(MIR_Load.create(PPC_LWZ, R(temp), R(FP),
                                            I(location))));
      // CR2 is used by the thread scheduler
      s.insertBack(MIR_Move.create(PPC_MTCR,
                                   R(phys.getCR()), R(temp)));
    } else if (type == DOUBLE_VALUE) {
	s.insertBack(nonPEIGC(MIR_Load.create(PPC_LFD, D(r), R(FP),
                                              I(location))));
    } else if (type == FLOAT_VALUE) {
      s.insertBack(nonPEIGC(MIR_Load.create(PPC_LFS, F(r), R(FP),
                                            I(location))));
    } else if (type == INT_VALUE) { // integer or half of long
      s.insertBack(nonPEIGC(MIR_Load.create(PPC_LWZ, R(r), R(FP),
                                            I(location))));
    } else {
      throw new OPT_OptimizingCompilerException("insertUnspillBefore", 
						"unknown type:" + type);
    }
  }
  
  
  /**
   *
   * PROLOGUE/EPILOGUE. must be done after register allocation
   *
   */
  final void insertPrologueAndEpilogue() {
    correctInputArguments();

    // This call may set the allocFrame boolean
    frameSize = insertPrologueAndComputeFrameSize();
    
    ir.MIRInfo.FrameSize = frameSize;
    OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext();
    for (; inst != null; inst = inst.nextInstructionInCodeOrder()) {
      switch (inst.getOpcode()) {
	case PPC_BLR_opcode:
	  if (allocFrame) // restore non-volatile registers
	    restoreNonVolatileRegisters(inst);
	  break;
	case PPC_MOVE_opcode:
	case PPC_FMR_opcode:
	  // remove frivolous moves
	  if (MIR_Move.getResult(inst).register.number ==
	      MIR_Move.getValue(inst).register.number)
	    inst = inst.remove();
	  break;
	case PPC_LFD_opcode:
	case PPC_LFS_opcode:
	case PPC_LWZ_opcode:
	  // the following to handle spilled parameters
	  if (MIR_Load.getAddress(inst).register ==
              ir.regpool.getPhysicalRegisterSet().getFP()) {
	    OPT_Operand one = MIR_Load.getOffset(inst);
	    if (one instanceof OPT_IntConstantOperand) {
	      int offset = ((OPT_IntConstantOperand) one).value;
	      if (offset <= -256) {
		MIR_Load.setOffset(inst, I(frameSize - offset - 256));
	      }
	    }
	  }
	  break;
      } // switch
    } // for
    OPT_RegisterAllocatorState.resetPhysicalRegisters(ir);
  }
  
  
  /**
   * create space for non-volatile register needed to save / restore
   * NOTE: non-volatile registers are allocated backwards.
   *
   * @param s the instruction to insert new instructions before
   */
  final private void createNonVolatileArea(OPT_Instruction s) {
    // cache compiler info for conveniences
    VM_OptCompilerInfo info = ir.MIRInfo.info;
    int NonVolatileFrameOffset = info.getUnsignedNonVolatileOffset();
    int firstInteger = info.getFirstNonVolatileGPR();
    int firstFloat = info.getFirstNonVolatileFPR();
    
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    if (firstInteger != -1) {
      OPT_Register first = phys.get(firstInteger);
      if (FIRST_INT + LAST_NONVOLATILE_GPR -first.number <= MULTIPLE_CUTOFF) {
	// use a sequence of store instructions (more efficient than stm for
	// small number of stores)
	for (OPT_Register r = first; r != null; r = r.getNext()) {
	  s.insertBack(nonPEIGC(MIR_Store.create
                                (PPC_STW, R(r), R(FP), 
                                 I(OPT_RegisterAllocatorState.getSpill(r)))));
	}
      } else {
	// use a stm
	OPT_RegisterOperand range = R(first);
	range.setRange(FIRST_INT + LAST_NONVOLATILE_GPR -first.number);
	s.insertBack(nonPEIGC(MIR_Store.create
                              (PPC_STMW, range, R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill(first)))));
      }
    }
    if (info.isSaveVolatile()) {
      for (Enumeration e = phys.enumerateVolatileGPRs();
           e.hasMoreElements();) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
	s.insertBack(nonPEIGC(MIR_Store.create
                              (PPC_STW, R(realReg), R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill(realReg)))));
      }
    }
    if (firstFloat != -1) {
      for (OPT_Register realReg = phys.get(firstFloat + FIRST_DOUBLE);
	   realReg != null; realReg = realReg.getNext()) {
	s.insertBack(nonPEIGC(MIR_Store.create
                              (PPC_STFD, D(realReg), R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill(realReg)))));
      }
    }

    if (info.isSaveVolatile()) {
      for (Enumeration e = phys.enumerateVolatileFPRs();
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
	s.insertBack(nonPEIGC(MIR_Store.create
                              (PPC_STFD, D(realReg), R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill
                                 (realReg)))));
      }
      OPT_Register auxReg  = phys.getFirstVolatileGPR();
      s.insertBack(MIR_Move.create(PPC_MFCR, R(auxReg),
                                   R(phys.getCR())));
      s.insertBack(nonPEIGC(MIR_Store.create
                            (PPC_STW, R(auxReg), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getFirstVolatileConditionRegister())))));
      s.insertBack(MIR_Move.create(PPC_MFSPR,R(auxReg),
                                   R(phys.getXER())));
      s.insertBack(nonPEIGC(MIR_Store.create
                            (PPC_STW, R(auxReg), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getXER())))));
      s.insertBack(MIR_Move.create(PPC_MFSPR,R(auxReg),
                                   R(phys.getCTR())));
      s.insertBack(nonPEIGC(MIR_Store.create
                            (PPC_STW, R(auxReg), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getCTR())))));
      s.insertBack(nonPEIGC(MIR_Load.create
                            (PPC_LWZ, R(auxReg), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill(auxReg)))));
    }
  }
  
  /**
   *
   *
   */
  final private void restoreNonVolatileRegisters(OPT_Instruction s) {
    VM_OptCompilerInfo info = ir.MIRInfo.info;
    int firstInteger = info.getFirstNonVolatileGPR();
    int firstFloat   = info.getFirstNonVolatileFPR();
    short volatileArray[];
    boolean SaveVolatile = ir.MIRInfo.info.isSaveVolatile();
    
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    
    if (SaveVolatile) {
      OPT_Register temp = phys.getTemp();
      s.insertBack(nonPEIGC(MIR_Load.create
                            (PPC_LWZ, R(temp), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getFirstVolatileConditionRegister())))));
      // cr2 is used by the thread scheduler
      s.insertBack(MIR_Move.create(PPC_MTCR,
                                   R(phys.getCR()), R(temp)));
      s.insertBack(nonPEIGC(MIR_Load.create
                            (PPC_LWZ, R(temp), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getXER())))));
      s.insertBack(MIR_Move.create(PPC_MTSPR,
                                   R(phys.getXER()), R(temp)));
      s.insertBack(nonPEIGC(MIR_Load.create
                            (PPC_LWZ, R(temp), R(FP), 
                             I(OPT_RegisterAllocatorState.getSpill
                               (phys.getCTR())))));
      s.insertBack(MIR_Move.create(PPC_MTSPR,
                                   R(phys.getCTR()), R(temp)));
    }
    
    // restore return address
    if (ir.stackManager.frameIsRequired()) { 
      OPT_Register temp = phys.getTemp();
      s.insertBack(nonPEIGC(MIR_Load.create(PPC_LWZ, R(temp), R(FP),
		    I(STACKFRAME_NEXT_INSTRUCTION_OFFSET + frameSize))));
    }
    
    // restore non-volatile registers
    if (firstInteger != -1) {
      OPT_Register first = phys.get(firstInteger);
      if (FIRST_INT + LAST_NONVOLATILE_GPR -first.number <= MULTIPLE_CUTOFF) {
	// use a sequence of load instructions 
	// (more efficient than lm for small number of loads)
	for (OPT_Register r = first; r != null; r = r.getNext()) {
	  s.insertBack(nonPEIGC(MIR_Load.create
                                (PPC_LWZ, R(r), R(FP), 
                                 I(OPT_RegisterAllocatorState.getSpill(r)))));
	}
      } else {
	// use a lm
	OPT_RegisterOperand range = R(first);
	range.setRange(FIRST_INT + LAST_NONVOLATILE_GPR -first.number);
	s.insertBack(nonPEIGC(MIR_Load.create
                              (PPC_LMW, range, R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill(first)))));
      }
    }
    if (SaveVolatile) {
      for (Enumeration e = phys.enumerateVolatileGPRs();
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
	s.insertBack(nonPEIGC(MIR_Load.create
                              (PPC_LWZ, R(realReg), R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill
                                 (realReg)))));
      }
    }
    if (firstFloat != -1)
      for (OPT_Register realReg = phys.get(firstFloat + FIRST_DOUBLE);
	   realReg != null; realReg = realReg.getNext()) {
	s.insertBack(nonPEIGC(MIR_Load.create
                              (PPC_LFD, D(realReg), R(FP), 
                               I(OPT_RegisterAllocatorState.getSpill
                                 (realReg)))));
      }
    if (SaveVolatile) {
      for (Enumeration e = phys.enumerateVolatileFPRs();
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
	s.insertBack(nonPEIGC(MIR_Load.create(PPC_LFD, D(realReg), R(FP),
					      I(OPT_RegisterAllocatorState.
                                                getSpill(realReg)))));
      }
    }
    
    // LOAD RETURN ADDRESS INTO LR
    if (ir.stackManager.frameIsRequired()) { 
      s.insertBack(MIR_Move.create(PPC_MTSPR,
                                   R(phys.getLR()),
                                   R(phys.getTemp())));
    }
    
    // restore OLD FP
    s.insertBack(MIR_Binary.create(PPC_ADDI, R(FP), R(FP), I(frameSize)));
  }
  
  
  /**
   * Insert the prologue.
   * The available scratch registers are normally: R0, S0, S1
   * However, if this is the prologue for a 'save volatile' frame, 
   * then R0 is the only available scratch register.
   * The "normal" prologue must perform the following tasks:
   *    stack overflow check         
   *    set cr2 for the yieldpoint   
   *      (when !VM.BuildForThreadSwitchUsingControlRegisterBit && 
   *                            there is a prologue yieldpoint instruction)
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
   *  10   tlt     FP, S0                                     # stack overflow check
   */

  /**
   * Schedule prologue for 'normal' case (see above)
   */
  final void createNormalPrologue() {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_Register PR = phys.getPR();
    OPT_Register TSR = phys.getTSR();
    OPT_Register R0 = phys.getTemp();
    OPT_Register S0 = phys.getGPR(FIRST_SCRATCH_GPR);
    OPT_Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.getDeclaringClass().isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = !VM.BuildForThreadSwitchUsingControlRegisterBit && 
      ir.stackManager.hasPrologueYieldpoint();
    
    OPT_Instruction ptr = ir.firstInstructionInCodeOrder().getNext();
    if (VM.VerifyAssertions) VM.assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, R(R0),
                                     R(phys.getLR()))); // 1
    if (yp) {
      ptr.insertBefore(nonPEIGC(MIR_Load.create(PPC_LWZ, R(S1), R(PR),
			I(VM_Entrypoints.threadSwitchRequestedOffset)))); // 2
    }

    ptr.insertBefore(nonPEIGC(MIR_StoreUpdate.create(PPC_STWU, R(FP), R(FP),
  		        I(-frameSize)))); // 3

    if (stackOverflow) {
      ptr.insertBefore(nonPEIGC(MIR_Load.create(PPC_LWZ, R(S0),
                                                R(phys.getPR()), 
			I(VM_Entrypoints.activeThreadStackLimitOffset)))); // 4
    }

    // Now add any instructions to save the nonvolatiles (5)
    createNonVolatileArea(ptr);
    
    if (yp) {
      ptr.insertBefore(MIR_Binary.create(PPC_CMPI, R(TSR), R(S1), I(0))); // 6
    }
    int cmid = ir.compiledMethodId;
    if (cmid <= 0x7fff) {
      ptr.insertBefore(MIR_Unary.create(PPC_LDI, R(S1), I(cmid))); // 7
    } else {
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, R(S1),I(cmid>>>16))); // 7 (a)
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, R(S1), R(S1),
					 I(cmid&0xffff))); // 7 (b)
    }
    ptr.insertBefore(nonPEIGC(MIR_Store.create(PPC_STW, R(R0), R(FP), 
		 I(frameSize + STACKFRAME_NEXT_INSTRUCTION_OFFSET)))); // 8
    ptr.insertBefore(nonPEIGC(MIR_Store.create(PPC_STW, R(S1), R(FP), 
		       I(STACKFRAME_METHOD_ID_OFFSET)))); // 9

    if (stackOverflow) {
      // Mutate the Prologue instruction into the trap
      MIR_Trap.mutate(ptr, PPC_TW, OPT_PowerPCTrapOperand.LESS(), R(FP), R(S0),
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
  final void createExceptionalPrologue(){
    if (frameSize >= 0x7ff0) {
      throw new OPT_OptimizingCompilerException("Stackframe size exceeded!");
    }

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    OPT_Register FP = phys.getFP();
    OPT_Register PR = phys.getPR();
    OPT_Register TSR= phys.getTSR();
    OPT_Register R0 = phys.getTemp();
    OPT_Register S1 = phys.getGPR(LAST_SCRATCH_GPR);
    boolean interruptible = ir.method.getDeclaringClass().isInterruptible();
    boolean stackOverflow = interruptible;
    boolean yp = !VM.BuildForThreadSwitchUsingControlRegisterBit && 
      ir.stackManager.hasPrologueYieldpoint();

    OPT_Instruction ptr = ir.firstInstructionInCodeOrder().getNext();
    if (VM.VerifyAssertions) VM.assert(ptr.getOpcode() == IR_PROLOGUE_opcode);

    // Stack overflow check
    if (stackOverflow) {
      // R0 is fairly useless (can't be operand 1 of an addi or the base ptr
      // of a load) so, free up S1 for use by briefly saving its contents in the
      // return address slot of my caller's frame
      ptr.insertBefore(nonPEIGC(MIR_Store.create(PPC_STW, R(S1), R(FP), 
			I(STACKFRAME_NEXT_INSTRUCTION_OFFSET))));
      ptr.insertBefore(nonPEIGC(MIR_Load.create(PPC_LWZ, R(S1),
                                                R(phys.getPR()), 
			I(VM_Entrypoints.activeThreadStackLimitOffset))));
      ptr.insertBefore(MIR_Binary.create(PPC_ADDI, R(R0), R(S1), 
			I(frameSize)));
      ptr.insertBefore(nonPEIGC(MIR_Load.create(PPC_LWZ, R(S1), R(FP), 
			I(STACKFRAME_NEXT_INSTRUCTION_OFFSET))));

      // Mutate the Prologue holder instruction into the trap
      MIR_Trap.mutate(ptr, PPC_TW, OPT_PowerPCTrapOperand.LESS(), R(FP), R(R0),
		      OPT_TrapCodeOperand.StackOverflow()); 

      // advance ptr because we want the remaining instructions to come after
      // the trap
      ptr = ptr.getNext();

    } else {
      // no stack overflow test, so we must remove the IR_Prologue instruction
      OPT_Instruction next = ptr.getNext();
      ptr.remove();
      ptr = next;
    }
    
    // Buy stack frame, save LR, caller's FP 
    ptr.insertBefore(MIR_Move.create(PPC_MFSPR, R(R0),
                                     R(phys.getLR())));
    ptr.insertBefore(nonPEIGC(MIR_StoreUpdate.create(PPC_STWU, R(FP), R(FP),
						     I(-frameSize))));
    ptr.insertBefore(nonPEIGC(MIR_Store.create(PPC_STW, R(R0), R(FP), 
			I(frameSize+STACKFRAME_NEXT_INSTRUCTION_OFFSET))));
    
    // Store cmid
    int cmid = ir.compiledMethodId;
    if (cmid <= 0x7fff) {
      ptr.insertBefore(MIR_Unary.create(PPC_LDI, R(R0), I(cmid)));
    } else {
      ptr.insertBefore(MIR_Unary.create(PPC_LDIS, R(R0),I(cmid>>>16)));
      ptr.insertBefore(MIR_Binary.create(PPC_ORI, R(R0), R(R0),I(cmid&0xffff)));
    }
    ptr.insertBefore(nonPEIGC(MIR_Store.create(PPC_STW, R(R0), R(FP), 
		       I(STACKFRAME_METHOD_ID_OFFSET))));

    // Now add the non volatile save instructions
    createNonVolatileArea(ptr);
    
    // Threadswitch
    if (yp) {
      ptr.insertBefore(nonPEIGC(MIR_Load.create(PPC_LWZ, R(R0), R(PR), 
			I(VM_Entrypoints.threadSwitchRequestedOffset))));
      ptr.insertBefore(MIR_Binary.create(PPC_CMPI, R(TSR), R(R0), I(0)));
    }
  }

  /**
   * Return the number of GPRs that will hold parameters on entry to a
   * method.
   * @param ir the governing IR
   */
  final private int getNumberOfGPRParameters(OPT_IR ir) {
    int result = 0;
    
    // all non-static methods have arg[0] implicit this.
    if (!ir.method.isStatic()) result++;

    // enumerate all parameters, and count the number that will be
    // passed in GPRs
    VM_Type types[] = ir.method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      VM_Type t = types[i];
      if (t.isLongType()) {
        result += 2;
      } else if (!t.isFloatType() && !t.isDoubleType()) {
        // t is object, int, short, char, byte, or boolean 
        result++;
      }
    }

    // the maximum number of GPR parameters is constrained by the calling
    // convention
    return Math.min(result,NUMBER_INT_PARAM);
  }
  /**
   * Return the number of FPRs that will hold parameters on entry to a
   * method.
   * @param ir the governing IR
   */
  final private int getNumberOfFPRParameters(OPT_IR ir) {
    int result = 0;
    
    // enumerate all parameters, and count the number that will be
    // passed in FPRs
    VM_Type types[] = ir.method.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      VM_Type t = types[i];
      if (t.isDoubleType() || t.isFloatType()) {
        result++;
      }
    }

    // the maximum number of FPR parameters is constrained by the calling
    // convention
    return Math.min(result,NUMBER_DOUBLE_PARAM);
  }
    
  /**
   *
   *
   */
  final private void correctInputArguments() {
    
    int intArgumentRegisters    = getNumberOfGPRParameters(ir);
    int doubleArgumentRegisters = getNumberOfFPRParameters(ir);
    int intIndex    = 0;
    int doubleIndex = 0;
    
    if ((intIndex    >= (intArgumentRegisters-1)) &&
	(doubleIndex >= (doubleArgumentRegisters-1))) {
      return;
    }

    OPT_RegisterAllocatorState.resetPhysicalRegisters(ir);
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext();
	 inst != null; 
	 inst = inst.nextInstructionInCodeOrder()) {
      if ((intIndex    >= intArgumentRegisters) &&
	  (doubleIndex >= doubleArgumentRegisters))
	break;

      OPT_Operator operator = inst.operator();
      if (operator.isMove()) {
	OPT_RegisterOperand dst = MIR_Move.getResult(inst);
	OPT_Register DST = phys.get(dst.register.number);
	DST.allocateRegister();
	if (operator == PPC_MOVE) {
	  intIndex++;
	} else {
	  doubleIndex++;
	}
      }
    }
    
    intIndex    = 0;
    doubleIndex = 0;
    for (OPT_Instruction inst = ir.firstInstructionInCodeOrder().getNext();
	 inst != null; inst = inst.nextInstructionInCodeOrder()) {
      if ((intIndex    >= intArgumentRegisters) &&
	  (doubleIndex >= doubleArgumentRegisters))
	break;
      
      OPT_Operator operator = inst.operator();
      if (operator.isMove()) {
	OPT_RegisterOperand dst = MIR_Move.getResult(inst);
	OPT_RegisterOperand src = MIR_Move.getValue(inst);
	if (src.register.number >= phys.getSize()) {
	  VM.sysWrite("DST: " + dst + " SRC: " + src + "\n");
	  ir.printInstructions();
	}
	OPT_Register SRC = phys.get(src.register.number);
	OPT_Register DST = phys.get(dst.register.number);
	OPT_Register alloc;
	if ((alloc = OPT_RegisterAllocatorState.getMapping(SRC)) != null) {
	  src.register = alloc;
	  SRC.deallocateRegister();
	}
	if (operator == PPC_MOVE) {
	  int LAST_INT_PARAM = FIRST_INT_PARAM + intArgumentRegisters;
	  if ((DST.number > SRC.number) &&
	      (DST.number < LAST_INT_PARAM)) {
	    allocateStartingFromRegister(phys.get(LAST_INT_PARAM+1), DST);
	    inst.insertBack(MIR_Move.create(PPC_MOVE,
					    R(DST.getRegisterAllocated()), R(DST)));
	  }
	  intIndex++;
	} else {
	  int LAST_DOUBLE_PARAM = FIRST_DOUBLE_PARAM + doubleArgumentRegisters;
	  if ((DST.number > SRC.number) &&
	      (DST.number < LAST_DOUBLE_PARAM)) {
	    allocateStartingFromRegister(phys.get(LAST_DOUBLE_PARAM+1), DST);
	    inst.insertBack(MIR_Move.create(PPC_FMR, D(DST.getRegisterAllocated()), D(DST)));
	  }
	  doubleIndex++;
	}
      }
    }
  }

  /**
   *
   */
  void insertSpillStore(OPT_Instruction inst, OPT_RegisterOperand Reg){
    if (debug)
      VM.sysWrite("OPT_RegisterManager:insertSpillStore\n");
    OPT_Register v = null;

    OPT_Register symbReg = Reg.register;

    OPT_RegisterAllocatorState.clearOneToOne(symbReg);
  
    if (inst.isMove()) {
      OPT_Register n = MIR_Move.getValue(inst).register;
      if (n.isPhysical()) {
        Reg.register = storeSpillAndRecord(inst, n, symbReg);
        return;
      } else if (!n.isSpilled()) {
        Reg.register = storeSpillAndRecord(inst, n.getRegisterAllocated(), symbReg);
        return;
      }
    }

    VM_Type type = Reg.type;
    if ((type == VM_Type.FloatType) || (type == VM_Type.DoubleType))
      v = tmpFP[tmpFPCnt++];
    else
      v = tmpInt[tmpIntCnt++];
    v.touchRegister();
    Reg.register = storeSpillAndRecord(inst, v, symbReg);
  }

  /**
   *
   */
  private void insertSpillLoad(OPT_Instruction inst, OPT_RegisterOperand Reg, 
                               int position){
    if (debug)
      VM.sysWrite("OPT_RegisterManager:insertSpillLoad\n");

    OPT_Register v = null;
    OPT_Register symbReg = Reg.register;
    OPT_Register real = OPT_RegisterAllocatorState.getMapping(symbReg);

    if ((real != null) && ((real.number != 0))) {  // reuse spilled location
      Reg.register = real;
      reuse[reuseCnt++] = real;
      real.useCount = 1;
      if (verbose)
        System.out.println("SAVE "+real+" "+symbReg.scratch+" "+symbReg);
      return;
    }
    if (inst.isMove()) {
      OPT_Register n = MIR_Move.getResult(inst).register;
      if (n.isPhysical()) {
        Reg.register = loadSpillAndRecord(inst, n,  symbReg);
        return;
      } else if (!n.isSpilled()) {
        Reg.register = loadSpillAndRecord(inst, n.getRegisterAllocated(), symbReg);
        return;
      }
    }

    VM_Type type = Reg.type;
    if ((type == VM_Type.FloatType) || (type == VM_Type.DoubleType)) {
      do {
        v = tmpFP[tmpFPCnt++];
      } while (v.useCount != 0);
    } else {
      do {
        if (tmpIntCnt >= tmpInt.length) {

          OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
          for (Enumeration e = phys.enumerateVolatileGPRs();
               e.hasMoreElements(); ) {
            v = (OPT_Register)e.nextElement();
            if (!isAllocatedToInstruction(inst,v)) {
              insertSpillAfter(inst.getPrev(), v, 
                               getValueType(symbReg), -(tmpIntCnt<<2));
              insertUnspillBefore(inst.getNext(), v, 
                                  getValueType(symbReg), -(tmpIntCnt<<2));
              break;
            }
          }

          tmpIntCnt++;
          v.touchRegister();
          insertUnspillBefore(inst, v, getValueType(symbReg), 
                              OPT_RegisterAllocatorState.getSpill(symbReg));
          Reg.register = v; 
          if (verbose)
            System.out.println("AJA "+v+" "+symbReg);
          return;
        }
        v = tmpInt[tmpIntCnt++];
      } while (v.useCount != 0);
    }
    v.touchRegister();
    Reg.register = loadSpillAndRecord(inst, v, symbReg);
    //VM.sysWrite(inst+'\n');
  }

  /**
   *
   */
  void insertSpillCode() {
    if (debug) VM.sysWrite("SPILL\n");

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Enumeration e = phys.enumerateNonvolatileFPRsBackwards();
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (!realReg.isTouched()) {
        if (tmpFP[0] != realReg) {
          int real = realReg.number;
          //VM.sysWrite(realReg+" "+ir.method+" "+tmpFP[0]+'\n');
          tmpFP[0] = realReg;
          tmpFP[1] = ir.regpool.getPhysicalRegisterSet().get(real-1);
          tmpFP[2] = ir.regpool.getPhysicalRegisterSet().get(real-2);
        }
        break;
      }
    }

    clearMappings();
    if (verbose)
      System.out.println("***** "+ir.method);

    for (OPT_Instruction inst= ir.firstInstructionInCodeOrder();
         inst != null; inst = inst.nextInstructionInCodeOrder()) {

      if (verbose)
        System.out.println(inst);
      int numberDefs    = inst.getNumberOfDefs();
      int numberOperand = inst.getNumberOfOperands();
      int numberUses    = numberOperand - numberDefs;
      if ((inst.operator == LABEL) || (inst.isCall())) {
        if (cachedSpill)
          clearMappings();
        continue;
      }
      if (inst.isMove() && MIR_Move.getResult(inst).register.number ==
          MIR_Move.getValue(inst).asRegister().register.number) {
        inst = inst.remove();
        continue;
      }


      // USES
      int firstUse = numberDefs;
      // shift by 1 to start from forbidden position for r0
      if (firstUse == 0) firstUse = 1;

      tmpIntCnt = tmpFPCnt = reuseCnt = 0;
      for (int i = 0; i < numberUses; i++) {
        int n = (firstUse + i)%numberOperand;
        OPT_Operand op = inst.getOperand(n);
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand Reg = (OPT_RegisterOperand) op;
          OPT_Register        reg = Reg.register;
          if (reg.isPhysical()) {
            continue;
          }
          insertSpillLoad(inst, Reg, n);
        }
      }

      while (reuseCnt > 0) {
        reuse[--reuseCnt].useCount = 0;
      }

      // DEFS
      tmpIntCnt = tmpFPCnt = 0;
      for (OPT_OperandEnumeration defs = inst.getDefs();
           defs.hasMoreElements(); ) {
        OPT_Operand op = defs.next();
        if (op instanceof OPT_RegisterOperand) {
          OPT_RegisterOperand Reg = (OPT_RegisterOperand)op;
          OPT_Register reg = Reg.register;
          if (reg.isPhysical()) { // destroy any association of real 
                                  // with spilled symbolic
            // SJF: The following looks odd.  What's going on?
            reg = ir.regpool.getPhysicalRegisterSet().get(reg.number);
            OPT_RegisterAllocatorState.clearOneToOne(reg);
            continue;
          }
          insertSpillStore(inst, Reg);
        }
      }
      if (verbose)
        System.out.println("   "+inst);
    }
  }

  /**
   *
   * compute space for non-volatile register needed to save / restore
   *
   */
  final private boolean computeNonVolatileArea() {
    boolean shouldAllocFrame = false;

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (int i = 0; i < NUMBER_TYPE; i++)
      spillList[i] = null;
    int NonVolatileFrameOffset = 0;
    int firstInteger = -1;
    boolean SaveVolatile = ir.MIRInfo.info.isSaveVolatile();
    if (SaveVolatile) {
      OPT_Register realReg;
      for (Enumeration e = ir.regpool.getPhysicalRegisterSet().enumerateGPRs();
         e.hasMoreElements(); ) {
        OPT_Register reg = (OPT_Register)e.nextElement();
        OPT_RegisterAllocatorState.setSpill(reg, 
                                            getSpillLocationNumber(INT_REG));
      }
      shouldAllocFrame = true;
      realReg = phys.getFirstNonvolatileGPR();
      firstInteger = realReg.number;
      NonVolatileFrameOffset = OPT_RegisterAllocatorState.getSpill(realReg);
    } else {
      // find the first non-volatile used
      for (Enumeration e = phys.enumerateNonvolatileGPRs();
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
        if (realReg.isTouched()) {
          int spill = getSpillLocationNumber(INT_REG);
          OPT_RegisterAllocatorState.setSpill(realReg, spill);
          if (firstInteger < 0) {
            firstInteger = realReg.number;
            NonVolatileFrameOffset = spill;
            shouldAllocFrame = true;
          }
        }
      }
    }
    int firstFloat = -1;
    for (Enumeration e = phys.enumerateNonvolatileFPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isTouched()) {
        OPT_RegisterAllocatorState.setSpill(realReg, 
                                            getSpillLocationNumber(DOUBLE_REG));
        if (firstFloat < 0) {
          firstFloat = realReg.number - FIRST_DOUBLE;
          shouldAllocFrame = true;
        }
      }
    }
    if (SaveVolatile) {
      for (Enumeration e = phys.enumerateVolatileFPRs();
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
        OPT_RegisterAllocatorState.setSpill(realReg, 
                                            getSpillLocationNumber(DOUBLE_REG));
      }
      OPT_RegisterAllocatorState.setSpill
        (phys.getFirstVolatileConditionRegister(), 
         getSpillLocationNumber(CONDITION_REG));
      OPT_RegisterAllocatorState.setSpill(phys.getXER(), 
                                          getSpillLocationNumber(INT_REG));
      OPT_RegisterAllocatorState.setSpill(phys.getCTR(), 
                                          getSpillLocationNumber(INT_REG));
    }

    // update the Compiler info info
    VM_OptCompilerInfo info = ir.MIRInfo.info;
    info.setUnsignedNonVolatileOffset(NonVolatileFrameOffset);
    info.setFirstNonVolatileGPR(firstInteger);
    info.setFirstNonVolatileFPR(firstFloat);

    // align the activation frame
    frameSize = align(frameSize, STACKFRAME_ALIGNMENT);

    return shouldAllocFrame;
  }

  protected static final int MULTIPLE_CUTOFF = 4;

  final protected int insertPrologueAndComputeFrameSize() {
    allocFrame = ir.stackManager.frameIsRequired();

    // compute the non-volatile save area 
    if  (computeNonVolatileArea()) {
      allocFrame = true;
    }

    if (allocFrame) {
      if (frameSize < STACK_SIZE_GUARD && !ir.MIRInfo.info.isSaveVolatile()) {
        createNormalPrologue();
      } else {
        createExceptionalPrologue();
      }
      return frameSize;
    } else {
      // We aren't allocating a stack frame, so there's nothing to do
      // except remove the prologue instruction and return 0.
      OPT_Instruction s = ir.firstInstructionInCodeOrder().getNext();
      if (VM.VerifyAssertions) VM.assert(s.getOpcode() == IR_PROLOGUE_opcode);
      s.remove();
      return 0; 
    }
  }

  final void saveKilledVolatileRegisters(OPT_Instruction s) {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (int type = INT_REG; type <= CONDITION_REG; type++) {
      for (Enumeration e = phys.enumerateVolatiles(type);
           e.hasMoreElements(); ) {
        OPT_Register realReg = (OPT_Register)e.nextElement();
        if (realReg.isAllocated()) {
          if (debug)
            VM.sysWrite("Kill "+realReg+'\n');
          OPT_Register symbReg = realReg.mapsToRegister;
          OPT_Register newReg = allocateNonVolatileRegister(symbReg);
          if (newReg != null) {
            realReg.deallocateRegister();
            OPT_Instruction mv = makeMoveInstruction(newReg, realReg);
            s.insertBefore(mv);
          }
          else { // if everythig else fails, save to spill
            byte valueType = getValueType(symbReg);
            int location = OPT_RegisterAllocatorState.getSpill(symbReg); 
            OPT_RegisterAllocatorState.setSpill(realReg, location);
            realReg.deallocateRegister();
            insertSpillBefore(s, realReg, valueType, location);
          }
        }
      }
    }
  }

  /**
   * temporary integer registers
   */
  protected OPT_Register[] tmpInt;

  /**
   * temporary floating-point registers
   */
  protected OPT_Register[] tmpFP;

  /**
   * collection of registers that can be reused ??
   */
  protected OPT_Register[] reuse;

  // Counters for above arrays
  protected short tmpIntCnt;
  protected short tmpFPCnt;
  protected short reuseCnt;

  protected boolean cachedSpill;

  /**
   * Initializes the "tmp" regs for this object
   * @param ir the governing ir
   */
  final void initForArch(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    tmpInt = new OPT_Register[2];
    tmpFP  = new OPT_Register[3];
    reuse  = new OPT_Register[3];
    tmpInt[1] = phys.getGPR(0);
    tmpInt[0] = phys.getLastScratchGPR();
    tmpFP[2]  = phys.getFirstScratchFPR();
    tmpFP[1]  = phys.getFirstNonvolatileFPR();
    tmpFP[0]  = phys.get(tmpFP[1].number+1);
    tmpInt[0].reserveRegister();
    tmpInt[1].reserveRegister();
    tmpFP[0].reserveRegister();
    tmpFP[1].reserveRegister();
    tmpFP[2].reserveRegister();
    phys.getJTOC().reserveRegister();
  }

  /**
   *
   */
  final OPT_Register loadSpillAndRecord(OPT_Instruction s, OPT_Register realReg,
                                        OPT_Register symbReg) {
    // SJF: the following looks odd.  What's going on here?
    realReg = ir.regpool.getPhysicalRegisterSet().get(realReg.number);
    
    OPT_RegisterAllocatorState.mapOneToOne(realReg,symbReg); 

    cachedSpill = true;
    insertUnspillBefore(s, realReg, getValueType(symbReg),
                        OPT_RegisterAllocatorState.getSpill(symbReg));
    return realReg;
  }

  /**
   *
   */
  final OPT_Register storeSpillAndRecord(OPT_Instruction s, 
                                         OPT_Register realReg,
                                         OPT_Register symbReg) {
    // SJF: the following looks odd.  What's going on here?
    realReg = ir.regpool.getPhysicalRegisterSet().get(realReg.number);

    OPT_RegisterAllocatorState.mapOneToOne(realReg,symbReg);

    cachedSpill = true;
    insertSpillAfter(s, realReg, getValueType(symbReg),
                     OPT_RegisterAllocatorState.getSpill(symbReg));
    return realReg;
  }

  /**
   *
   */
  boolean isAllocatedToInstruction(OPT_Instruction inst, OPT_Register register) {
    for (OPT_OperandEnumeration ops = inst.getOperands();
         ops.hasMoreElements(); ) {
      OPT_Operand op = ops.next();
      if (op instanceof OPT_RegisterOperand &&
          ((OPT_RegisterOperand) op).register.number == register.number)
        return true;
    }
    return false;
  }
  /**
   *
   */
  void clearMappings() {
    cachedSpill = false;
    if (verbose)
      System.out.println("CLEAR");
    for (Enumeration e = ir.regpool.getPhysicalRegisterSet().enumerateAll();
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      realReg.useCount     = 0;
      OPT_RegisterAllocatorState.clearOneToOne(realReg);
    }
  }
  /**
   *  Find a nonvolatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
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
        if (debug) VM.sysWrite("  nonvol."+realReg+"to symb "+symbReg+'\n');
        return realReg;
      }
    }
    return null;
  }
  /**
   *  This version is used to support LinearScan, and is only called
   *  from OPT_LinearScanLiveInterval.java
   *
   *  TODO: refactor this some more.
   *
   *  @param symbReg the symbolic register to allocate
   *  @param li the linear scan live interval
   *  @return the allocated register or null
   */
  final OPT_Register allocateRegister(OPT_Register symbReg,
                                      OPT_LinearScanLiveInterval live) {

    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = phys.getPhysicalRegisterType(symbReg);

    // first attempt to allocate to the preferred register
    OPT_Register preferred = getAvailablePreference(pref,symbReg);
    if ((preferred != null) && preferred.isAvailable() &&
        !restrict.isForbidden(symbReg,preferred)) {
      OPT_LinearScanLiveInterval resurrect =
        OPT_RegisterAllocatorState.getPhysicalRegResurrectList(preferred);
      if ((resurrect == null) || !live.intersects(resurrect)) {
        if (resurrect != null)
          live.mergeWithNonIntersectingInterval(resurrect);
        preferred.allocateToRegister(symbReg);
        return preferred;
      }
    }

    // next attempt to allocate to a volatile
    for (Enumeration e = phys.enumerateVolatiles(type);
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isAvailable() && !restrict.isForbidden(symbReg,realReg)) {
        OPT_LinearScanLiveInterval resurrect =
          OPT_RegisterAllocatorState.getPhysicalRegResurrectList(realReg);
        if ((resurrect == null) || !live.intersects(resurrect)) {
          if (resurrect != null)
            live.mergeWithNonIntersectingInterval(resurrect);
          realReg.allocateToRegister(symbReg);
          if (debug)
            System.out.println(" volat."+realReg+" to symb "+symbReg);
          return realReg;
        }
      }
    }

    // next attempt to allocate to a nonvolatile.  We allocate the
    // novolatiles backwards.
    for (Enumeration e = phys.enumerateNonvolatilesBackwards(type);
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isAvailable() && !restrict.isForbidden(symbReg,realReg)) {
        OPT_LinearScanLiveInterval resurrect =
          OPT_RegisterAllocatorState.getPhysicalRegResurrectList(realReg);
        if ((resurrect == null) || !live.intersects(resurrect)) {
          if (resurrect != null)
            live.mergeWithNonIntersectingInterval(resurrect);
          realReg.allocateToRegister(symbReg);
          if (debug)
            System.out.println(" volat."+realReg+" to symb "+symbReg);
          return realReg;
        }
      }
    }
    return null;
  }

  /**
   * Return the available physical register to which a given symbolic 
   * register has
   * the highest affinity.
   */
  OPT_Register getAvailablePreference(OPT_GenericRegisterPreferences pref,
                                      OPT_Register r) {
    // a mapping from OPT_Register to Integer
    // (physical register to weight);
    java.util.HashMap map = new java.util.HashMap();

    OPT_CoalesceGraph graph = ir.stackManager.getPreferences().getGraph();
    OPT_SpaceEffGraphNode node = graph.findNode(r);

    // Return null if no affinities.
    if (node == null) return null;

    // walk through all in edges of the node, searching for affinity
    for (Enumeration in = node.inEdges(); in.hasMoreElements(); ) {
      OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
      OPT_CoalesceGraph.Node src = (OPT_CoalesceGraph.Node)edge.from();
      OPT_Register neighbor = src.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        int w = edge.getWeight();
        Integer oldW = (Integer)map.get(neighbor);
        if (oldW == null) {
          map.put(neighbor,new Integer(w));
        } else {
          map.put(neighbor,new Integer(oldW.intValue() + w));
        }
        break;
      }
    }
    // walk through all out edges of the node, searching for affinity
    for (Enumeration in = node.outEdges(); in.hasMoreElements(); ) {
      OPT_CoalesceGraph.Edge edge = (OPT_CoalesceGraph.Edge)in.nextElement();
      OPT_CoalesceGraph.Node dest = (OPT_CoalesceGraph.Node)edge.to();
      OPT_Register neighbor = dest.getRegister();
      if (neighbor.isSymbolic()) {
        // if r is assigned to a physical register r2, treat the
        // affinity as an affinity for r2
        OPT_Register r2 = OPT_RegisterAllocatorState.getMapping(r);
        if (r2 != null && r2.isPhysical()) {
          neighbor = r2;
        }
      }
      if (neighbor.isPhysical()) {
        // if this is a candidate interval, update its weight
        int w = edge.getWeight();
        Integer oldW = (Integer)map.get(neighbor);
        if (oldW == null) {
          map.put(neighbor,new Integer(w));
        } else {
          map.put(neighbor,new Integer(oldW.intValue() + w));
        }
        break;
      }
    }
    // OK, now find the highest preference. 
    OPT_Register result = null;
    int weight = -1;
    for (java.util.Iterator i = map.entrySet().iterator(); i.hasNext(); ) {
      java.util.Map.Entry entry = (java.util.Map.Entry)i.next();
      int w = ((Integer)entry.getValue()).intValue();
      if (w > weight) {
        weight = w;
        result = (OPT_Register)entry.getKey();
      }
    }
    return result;
  }


  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  int allocateNewSpillLocation(int type) {
    OPT_OptimizingCompilerException.TODO("allocateNewSpillLocation");
    return -1;
  }

}
