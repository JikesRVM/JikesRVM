/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;
import instructionFormats.*;

/**
 * This class splits live ranges for certain special cases to ensure
 * correctness during IA32 register allocation.
 *
 * @author Stephen Fink
 */
class OPT_SplitLiveRanges extends OPT_CompilerPhase 
implements OPT_Operators {

  /**
   * Should we split live ranges for parameters, in order to give the
   * register allocator more freedom?
   */
  private final static boolean SPLIT_PARAMS = true;

  /**
   * Should this phase be performed?
   * @param options controlling compiler options
   * @return true or false
   */
  final boolean shouldPerform (OPT_Options options) {
    return true;
  }

  /**
   * Return the name of this phase
   * @return "Live Range Splitting"
   */
  final String getName () {
    return "Live Range Splitting"; 
  }

  public boolean printingEnabled(OPT_Options options, boolean before) {
    return false;
  } 

  /**
   * The main method.
   * 
   * We split live ranges for registers around PEIs which have catch
   * blocks.  Suppose we have a
   * PEI s which uses a symbolic register r1.  We must ensure that after
   * register allocation, r1 is NOT assigned to a scratch location in s,
   * since this would mess up code in the catch block tghat uses r1.
   *
   * So, instead, we introduce a new temporary r2 which holds the value of
   * r1.  The live range for r2 spans only the instruction s.  Later, we
   * will ensure that r2 is never spilled.
   * 
   * TODO: This could be implemented more efficiently.
   *
   * @param ir the governing IR
   */
  final public void perform (OPT_IR ir) {

    java.util.HashMap newMap = new java.util.HashMap(5);

    for (Enumeration be = ir.getBasicBlocks(); be.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)be.nextElement();
      for (OPT_InstructionEnumeration ie  = bb.forwardInstrEnumerator(); 
           ie.hasMoreElements(); ) {
        OPT_Instruction s = ie.next();

        // clear the cache of register assignments
        newMap.clear();

        // Split live ranges at PEIs and a few special cases to 
        // make sure we can pin values that must be in registers.
        // NOTE: Any operator that is an IA32 special case that must have
        //       a particular operand in a register must be mentioned both
        //       here and in OPT_RegisterRestrictions!
        if (s.isPEI() && s.operator != IR_PROLOGUE) {
          if (bb.hasApplicableExceptionalOut(s) ||
              !OPT_RegisterRestrictions.SCRATCH_IN_PEI) {
            splitAllLiveRanges(s, newMap, ir, false);
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
              OPT_RegisterOperand rOp = MIR_DoubleShift.getSource(s);
              OPT_RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
              // move r into 'temp' before s
              insertMoveBefore(temp, rOp.copyRO(), s);
              rOp.register = temp.register;
            }
            break;
          case IA32_FCOMI_opcode: case IA32_FCOMIP_opcode:
            {
	      OPT_Operand op = MIR_Compare.getVal2(s);
	      if (!(op instanceof OPT_BURSManagedFPROperand)) {
		OPT_RegisterOperand rOp = op.asRegister();
		OPT_RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
		// move r into 'temp' before s
		insertMoveBefore(temp, rOp.copyRO(), s);
		rOp.register = temp.register;
	      }
            }
            break;
          case IA32_IMUL2_opcode:
            {
              OPT_RegisterOperand rOp = MIR_BinaryAcc.getResult(s).asRegister();
              OPT_RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
              // move r into 'temp' before s
              insertMoveBefore(temp, rOp.copyRO(), s);
              // move 'temp' into r after s
              insertMoveAfter(rOp.copyRO(), temp.copyRO(), s);
              rOp.register = temp.register;
            }
            break;
          case LOWTABLESWITCH_opcode:
            {
              OPT_RegisterOperand rOp = LowTableSwitch.getIndex(s);
              OPT_RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
              // NOTE: Index as marked as a DU because LowTableSwitch is 
              //       going to destroy the value in the register.
              //       By construction (see ConvertToLowLevelIR), no one will
              //       every read the value computed by a LowTableSwitch.
              //       Therefore, don't insert a move instruction after the
              //       LowTableSwitch (which would cause IR verification 
              //       problems anyways, since LowTableSwitch is a branch).
              insertMoveBefore(temp, rOp.copyRO(), s); // move r into 'temp' before s
              rOp.register = temp.register;
            }
            break;
          case IA32_MOVZX$B_opcode: case IA32_MOVSX$B_opcode:
            {
              OPT_RegisterOperand op = (OPT_RegisterOperand)MIR_Unary.getResult(s);
              OPT_RegisterOperand temp = findOrCreateTemp(op, newMap, ir);
              // move 'temp' into r after s
              insertMoveAfter(op.copyRO(), temp.copyRO(), s);
              op.register = temp.register;

              // also add restrictions on the rhs (val), if it is a register.
              OPT_Operand val = MIR_Unary.getVal(s);
              if (val.isRegister()) {
                OPT_RegisterOperand rval = (OPT_RegisterOperand)val;
                OPT_RegisterOperand tempval = findOrCreateTemp(rval, newMap, ir);
                // move 'temp' into rval before s
                insertMoveBefore(tempval, rval.copyRO(), s);
                rval.register = tempval.register;
              }
            }
            break;
          case IA32_CMOV_opcode: case IA32_FCMOV_opcode:
            {
              OPT_RegisterOperand op = (OPT_RegisterOperand)MIR_CondMove.
                                       getResult(s);
              OPT_RegisterOperand temp = findOrCreateTemp(op, newMap, ir);
              // move r into 'temp' before s
              insertMoveBefore(temp.copyRO(), op.copyRO(), s);
              // move 'temp' into r after s
              insertMoveAfter(op.copyRO(), temp.copyRO(), s);
              op.register = temp.register;
            }
            break;
          case IA32_MOVZX$W_opcode: case IA32_MOVSX$W_opcode:
            {
              OPT_RegisterOperand op = (OPT_RegisterOperand)MIR_Unary.getResult(s);
              OPT_RegisterOperand temp = findOrCreateTemp(op, newMap, ir);
              // move 'temp' into r after s
              insertMoveAfter(op.copyRO(), temp.copyRO(), s);
              op.register = temp.register;
            }
            break;
          case IA32_SET$B_opcode:
            {
              OPT_Operand op = MIR_Set.getResult(s);
              if (op.isRegister()) {
                OPT_RegisterOperand rOp = MIR_Set.getResult(s).asRegister();
                OPT_RegisterOperand temp = findOrCreateTemp(rOp, newMap, ir);
                // move 'temp' into r after s
                insertMoveAfter(rOp.copyRO(), temp, s);
                rOp.register = temp.register;
              }
            }
            break;
          case IR_PROLOGUE_opcode: 
            {
              if (SPLIT_PARAMS) {
                splitAllLiveRanges(s, newMap, ir, false);
              }
            }
          default:
            {
              for (OPT_OperandEnumeration e = s.getMemoryOperands(); 
                   e.hasMoreElements(); ) {
                OPT_MemoryOperand op = (OPT_MemoryOperand)e.next();
                if (op.size == 1) {
                  splitAllLiveRanges(s, newMap, ir, true);
                  break;
                }
              }
            }
            break;
        }
      }
    }
  }

  /**
   * Split the live ranges of all register operands of an instruction
   * @param s      the instruction to process
   * @param map a mapping from symbolics to temporaries
   * @param ir  the containing IR
   * @param rootOnly only consider root operands?
   */
  private static void splitAllLiveRanges(OPT_Instruction s, 
                                         java.util.HashMap newMap,
                                         OPT_IR ir,
					 boolean rootOnly) {
    // walk over each USE
    for (OPT_OperandEnumeration u = rootOnly?s.getRootUses():s.getUses(); 
	 u.hasMoreElements(); ) {
      OPT_Operand use = u.next();
      if (use.isRegister()) {
	OPT_RegisterOperand rUse = use.asRegister();
	OPT_RegisterOperand temp = findOrCreateTemp(rUse, newMap, ir);
	// move 'use' into 'temp' before s
	insertMoveBefore(temp, rUse.copyRO(), s);
      }
    }
    // walk over each DEF (by defintion defs == root defs)
    for (OPT_OperandEnumeration d = s.getDefs(); d.hasMoreElements(); ) {
      OPT_Operand def = d.next();
      if (def.isRegister()) {
	OPT_RegisterOperand rDef = def.asRegister();
	OPT_RegisterOperand temp = findOrCreateTemp(rDef ,newMap, ir);
	// move 'temp' into 'r' after s
	insertMoveAfter(rDef.copyRO(), temp, s);
      }
    }
    // Now go back and replace the registers.
    for (OPT_OperandEnumeration ops = rootOnly?s.getRootOperands():s.getOperands(); 
	 ops.hasMoreElements(); ) {
      OPT_Operand op = ops.next();
      if (op.isRegister()) {
	OPT_RegisterOperand rOp = op.asRegister();
	OPT_Register r = rOp.register;
	OPT_Register newR = (OPT_Register)newMap.get(r); 
	if (newR != null) {
	  rOp.register = newR;
	}
      }
    }
  }

  /**
   * Find or create a temporary register to cache a symbolic register.
   *
   * @param r the symbolic register
   * @param map a mapping from symbolics to temporaries
   * @param ir the governing IR
   */
  private static OPT_RegisterOperand findOrCreateTemp(OPT_RegisterOperand rOp,
						      java.util.HashMap map,
						      OPT_IR ir) {
    OPT_Register tReg = (OPT_Register)map.get(rOp.register);
    if (tReg == null) {
      OPT_RegisterOperand tOp = ir.regpool.makeTemp(rOp.type);
      map.put(rOp.register, tOp.register);
      return tOp;
    } else {
      return new OPT_RegisterOperand(tReg, rOp.type);
    }
  }

  /**
   * Insert an instruction to move r1 into r2 before instruction s
   */
  private static void insertMoveBefore(OPT_RegisterOperand r2, 
				       OPT_RegisterOperand r1,
                                       OPT_Instruction s) {
    OPT_Instruction m = OPT_PhysicalRegisterTools.makeMoveInstruction(r2,r1);
    s.insertBefore(m);
  }
  /**
   * Insert an instruction to move r1 into r2 after instruction s
   */
  private static void insertMoveAfter(OPT_RegisterOperand r2, 
				      OPT_RegisterOperand r1,
				      OPT_Instruction s) {
    OPT_Instruction m = OPT_PhysicalRegisterTools.makeMoveInstruction(r2,r1);
    s.insertAfter(m);
  }
}
