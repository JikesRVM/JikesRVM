/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *
 * @author Julian Dolby
 */
import instructionFormats.*;

class OPT_AssemblerBase 
    extends VM_Assembler 
    implements OPT_Operators, VM_Constants, OPT_PhysicalRegisterConstants
{
    OPT_AssemblerBase(int bytecodeSize, boolean shouldPrint) {
	super(bytecodeSize, shouldPrint);
    }

    boolean isImm(OPT_Operand op) {
	return 
	    (op instanceof OPT_IntConstantOperand)
	                   ||
	    (op instanceof OPT_TrapCodeOperand)
	                   ||
	    (op instanceof OPT_BranchOperand 
                           &&
	     op.asBranch().target.getmcOffset() >= 0);
    }

    int getImm(OPT_Operand op) {
	if (op instanceof OPT_BranchOperand) {
	    // used by ImmOrLabel stuff
	    return op.asBranch().target.getmcOffset();
	}
	else if (op instanceof OPT_TrapCodeOperand) 
	    return ((OPT_TrapCodeOperand)op).getTrapCode() 
		                  +
		   VM_TrapConstants.RVM_TRAP_BASE;
	else
	    return op.asIntConstant().value;
    }

    boolean isReg(OPT_Operand op) {
	return (op instanceof OPT_RegisterOperand);
    }

    private byte getMachineRegister(OPT_Register reg) {
	int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(reg);
	if (type == INT_REG) 
	    return (byte) (reg.number - FIRST_INT);
	else if (type == DOUBLE_REG)
	    return (byte) (reg.number - FIRST_DOUBLE);
	else
	    throw new OPT_OptimizingCompilerException("unexpected register type " + type);
    }
	
    byte getReg(OPT_Operand op) {
	return getMachineRegister( op.asRegister().register );
    }

    byte getBase(OPT_Operand op) {
	return getMachineRegister( ((OPT_MemoryOperand)op).base.register );
    }

    byte getIndex(OPT_Operand op) {
	return getMachineRegister(((OPT_MemoryOperand)op).index.register);
    }

    short getScale(OPT_Operand op) {
	return ((OPT_MemoryOperand)op).scale;
    }

    int getDisp(OPT_Operand op) {
	return ((OPT_MemoryOperand)op).disp;
    }

    boolean isRegDisp(OPT_Operand op) {
	if (op instanceof OPT_MemoryOperand) {
	    OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
	    return (mop.base != null) &&
		(mop.index == null) &&
		(mop.disp != 0) &&
		(mop.scale == 0);
	} else
	    return false;
    }

    boolean isRegInd(OPT_Operand op) {
	if (op instanceof OPT_MemoryOperand) {
	    OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
	    return (mop.base != null) &&
		(mop.index == null) &&
		(mop.disp == 0) &&
		(mop.scale == 0);
	} else
	    return false;
    }

    boolean isRegOff(OPT_Operand op) {
	if (op instanceof OPT_MemoryOperand) {
	    OPT_MemoryOperand mop = (OPT_MemoryOperand) op;
	    return (mop.base == null) &&
		(mop.index != null);
	} else
	    return false;
    }

    boolean isRegIdx(OPT_Operand op) {
	if (op instanceof OPT_MemoryOperand) 
	    return !(isRegInd(op) || isRegDisp(op) || isRegOff(op));
	else
	    return false;
    }

    byte getCond(OPT_Operand op) {
	return ((OPT_IA32ConditionOperand)op).value;
    }

    boolean isCond(OPT_Operand op) {
	return (op instanceof OPT_IA32ConditionOperand);
    }

    int getLabel(OPT_Operand op) {
	if (op instanceof OPT_IntConstantOperand)
	    // used by ImmOrLabel stuff
	    return 0;
	else {
	    if (op.asBranch().target.getmcOffset() < 0)
		return - op.asBranch().target.getmcOffset();
	    else
		return -1;
	}
    }

    boolean isLabel(OPT_Operand op) {
	return (op instanceof OPT_BranchOperand
		                &&
		op.asBranch().target.getmcOffset() < 0);
    }
    
    boolean isImmOrLabel(OPT_Operand op) {
	return (isImm(op) || isLabel(op));
    }

    boolean isByte(OPT_Instruction inst) {
	if (inst.operator.toString().indexOf("$b") != -1)
	    return true;

	for(int i = 0; i < inst.getNumberOfOperands(); i++) {
	    OPT_Operand op = inst.getOperand(i);
	    if (op instanceof OPT_MemoryOperand)
		return (((OPT_MemoryOperand)op).size == 1);
	}

	return false;
    }

    boolean isWord(OPT_Instruction inst) {
	if (inst.operator.toString().indexOf("$w") != -1)
	    return true;

	for(int i = 0; i < inst.getNumberOfOperands(); i++) {
	    OPT_Operand op = inst.getOperand(i);
	    if (op instanceof OPT_MemoryOperand)
		return (((OPT_MemoryOperand)op).size == 2);
	}

	return false;
    }

    boolean isQuad(OPT_Instruction inst) {
	if (inst.operator.toString().indexOf("$q") != -1)
	    return true;

	for(int i = 0; i < inst.getNumberOfOperands(); i++) {
	    OPT_Operand op = inst.getOperand(i);
	    if (op instanceof OPT_MemoryOperand)
		return (((OPT_MemoryOperand)op).size == 8);
	}

	return false;
    }

  /**
   * Debugging support (return a printable representation of the machine code).
   *
   * @param instr, an integer to be interpreted as a PowerPC instruction
   * @param offset the mcoffset (in bytes) of the instruction
   *
   */
  public static String disasm(int instr, int offset) {
    OPT_OptimizingCompilerException.TODO("OPT_Assembler: disassembler");
    return null;
  }

  /**
   * generate machine code into ir.machinecode
   * @param ir the IR to generate
   * @return   the number of machinecode instructions generated
   */
  public static final int generateCode(OPT_IR ir, boolean shouldPrint) {
      int count = 0;

      for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
	   p != null; p = p.nextInstructionInCodeOrder()) 
      {
	  p.setmcOffset( - ++count);
      }
      
      OPT_Assembler asm = new OPT_Assembler(count, shouldPrint);

      for (OPT_Instruction p = ir.firstInstructionInCodeOrder(); 
	   p != null; p = p.nextInstructionInCodeOrder()) 
      {
	  asm.doInst(p);
      }
      
    ir.MIRInfo.machinecode = asm.getMachineCodes();

    return ir.MIRInfo.machinecode.length;
  }

}
