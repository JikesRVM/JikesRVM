/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class splits live ranges for certain special cases to ensure
 * correctness during IA32 register allocation.
 *
 * @author Stephen Fink
 */
class OPT_MIRSplitRanges extends OPT_CompilerPhase 
  implements OPT_Operators {

  /**
   * Return the name of this phase
   * @return "Live Range Splitting"
   */
  public final String getName () {
    return "MIR Range Splitting"; 
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
        //  (1) Some operands must be in registers
        switch (s.getOpcode()) {
          case MIR_LOWTABLESWITCH_opcode:
            {
              OPT_RegisterOperand rOp = MIR_LowTableSwitch.getIndex(s);
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
