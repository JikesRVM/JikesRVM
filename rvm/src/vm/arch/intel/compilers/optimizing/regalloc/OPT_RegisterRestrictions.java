/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;
import instructionFormats.*;
import java.util.Vector;

/**
 * An instance of this class encapsulates restrictions on register
 * assignment.
 * 
 * @author Stephen Fink
 */
final class OPT_RegisterRestrictions extends OPT_GenericRegisterRestrictions implements OPT_Operators, OPT_PhysicalRegisterConstants {

  /**
   * Allocate EBP as a general purpose register?
   */
  private final static boolean ALLOCATE_EBP = true;

  /**
   * Allow scratch registers in PEIs?
   */
  final static boolean SCRATCH_IN_PEI = true;

  /**
   * Default Constructor
   */
  OPT_RegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    super(phys);
  }

  /**
   * Add architecture-specific register restrictions for a basic block.
   * Override as needed.
   *
   * @param bb the basic block 
   * @param symbolics the live intervals for symbolic registers on this
   * block
   */
  void addArchRestrictions(OPT_BasicBlock bb, Vector symbolics) {
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.operator == IA32_FNINIT) {
        // No floating point register survives across an FNINIT
        for (Enumeration sym = symbolics.elements(); sym.hasMoreElements(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)
            sym.nextElement();
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              addRestrictions(symb.getRegister(),phys.getFPRs());
            }
          }
        }
      } else if (s.operator == IA32_FCLEAR) {
        // Only some FPRs survive across an FCLEAR
        for (Enumeration sym = symbolics.elements(); sym.hasMoreElements(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)
            sym.nextElement();
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              int nSave = MIR_UnaryNoRes.getVal(s).asIntConstant().value;
              for (int i = nSave; i < NUM_FPRS; i++) {
                addRestriction(symb.getRegister(), phys.getFPR(i));
              }
            }
          }
        }
      }
    }
  }
  /**
   * Record all the register restrictions dictated by live ranges on a
   * particular basic block.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  protected void processBlock(OPT_BasicBlock bb) {
    // first process the default register restrictions
    super.processBlock(bb);
    
    // If there are any registers used in catch blocks, we want to ensure
    // that these registers are not used or evicted from scratch registers
    // at a relevant PEI, so that the assumptions of register homes in the
    // catch block remain valid.  For now, we do this by forcing any
    // register used in such a PEI as not spilled.  TODO: relax this
    // restriction for better code.
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.isPEI() && s.operator != IR_PROLOGUE) {
        if (bb.hasApplicableExceptionalOut(s) || !SCRATCH_IN_PEI) {
          for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
            OPT_Operand op = (OPT_Operand)e.nextElement();
            if (op != null && op.isRegister()) {
              noteMustNotSpill(op.asRegister().register);
            }
          }
        }
      }

      // handle special cases for IA32
      // These fall into two classes:
      //  (1) Some operands must be in registers
      //  (2) Some register operands must be in eax,ebx,ecx,or edx
      //      because they are really 8 bit registers (al,bl,cl,dl).
      //      This happens in a few special cases (MOVZX/MOZSX/SET)
      //      and in any other operator that has an 8 bit memory operand.
      switch (s.getOpcode()) {
      case IA32_SHRD_opcode: case IA32_SHLD_opcode:
	{
	  OPT_RegisterOperand op = MIR_DoubleShift.getSource(s);
	  noteMustNotSpill(op.register);
	}
	break;
      case IA32_FCOMI_opcode: case IA32_FCOMIP_opcode:
	{
	  OPT_Operand op = MIR_Compare.getVal2(s);
	  if (!(op instanceof OPT_BURSManagedFPROperand)) {
	    noteMustNotSpill(op.asRegister().register);
	  }
	}
	break;
      case IA32_IMUL2_opcode:
	{ 
	  OPT_RegisterOperand op = MIR_BinaryAcc.getResult(s).asRegister();
	  noteMustNotSpill(op.register);
	}
	break;
      case IA32_LOWTABLESWITCH_opcode:
	{
	  OPT_RegisterOperand op = MIR_LowTableSwitch.getIndex(s);
	  noteMustNotSpill(op.register);
	}
	break;
      case IA32_CMOV_opcode: case IA32_FCMOV_opcode:
	{
	  OPT_RegisterOperand op = MIR_CondMove.getResult(s).asRegister();
	  noteMustNotSpill(op.register);
	}
	break;
      case IA32_MOVZX$B_opcode: case IA32_MOVSX$B_opcode:
	{
	  OPT_RegisterOperand op = MIR_Unary.getResult(s).asRegister();
	  noteMustNotSpill(op.register);
	  if (MIR_Unary.getVal(s).isRegister()) {
	    OPT_RegisterOperand val = MIR_Unary.getVal(s).asRegister();
	    restrictTo8Bits(val.register);
	  }
	}
	break;
      case IA32_MOVZX$W_opcode: case IA32_MOVSX$W_opcode:
	{ 
	  OPT_RegisterOperand op = MIR_Unary.getResult(s).asRegister();
	  noteMustNotSpill(op.register);
	}
	break;
      case IA32_SET$B_opcode:
	{ 
	  if (MIR_Set.getResult(s).isRegister()) {
	    OPT_RegisterOperand op = MIR_Set.getResult(s).asRegister();
	    restrictTo8Bits(op.register);
	  }
	}
	break;
      case IA32_TEST_opcode:
	{
	  // at least 1 of the two operands must be in a register
	  if (!MIR_Test.getVal2(s).isConstant()) {
	    if (MIR_Test.getVal1(s).isRegister()) {
	      noteMustNotSpill(MIR_Test.getVal1(s).asRegister().register);
	    } else if (MIR_Test.getVal2(s).isRegister()) {
	      noteMustNotSpill(MIR_Test.getVal2(s).asRegister().register);
	    }
	  }
	}
	handle8BitRestrictions(s);
	break;

      default:
	  handle8BitRestrictions(s);
	break;
      }
    }
  }


  /**
   * Ensure that if an operand has an 8 bit memory operand that
   * all of its register operands are in 8 bit registers.
   * @param s the instruction to restrict
   */
  final void handle8BitRestrictions(OPT_Instruction s) {
    for (OPT_OperandEnumeration me = s.getMemoryOperands(); 
	 me.hasMoreElements(); ) {
      OPT_MemoryOperand mop = (OPT_MemoryOperand)me.next();
      if (mop.size == 1) {
	for (OPT_OperandEnumeration e2 = s.getRootOperands(); 
	     e2.hasMoreElements(); ) {
	  OPT_Operand rootOp = e2.next();
	  if (rootOp.isRegister()) {
	    restrictTo8Bits(rootOp.asRegister().register);
	  }
	}
      }
    }
  }


  /**
   * Ensure that a particular register is only assigned to AL, BL, CL, or
   * DL, since these are the only 8-bit registers we normally address.
   */
  final void restrictTo8Bits(OPT_Register r) {
    OPT_Register ESP = phys.getESP();        
    OPT_Register EBP = phys.getEBP();        
    OPT_Register ESI = phys.getESI();        
    OPT_Register EDI = phys.getEDI();        
    addRestriction(r,ESP);
    addRestriction(r,EBP);
    addRestriction(r,ESI);
    addRestriction(r,EDI);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register
   * physical?
   */
  boolean isForbidden(OPT_Register symb, OPT_Register physical) {
    if (!ALLOCATE_EBP && physical == phys.getEBP()) {
      return true;
    }
    return super.isForbidden(symb,physical);
  }
}
