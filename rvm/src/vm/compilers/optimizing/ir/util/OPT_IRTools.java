/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;
import org.vmmagic.unboxed.Address;

/**
 * This abstract class contains a bunch of useful static methods for
 * performing operations on IR.
 *
 * @author Jong-Deok Choi
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 */
public abstract class OPT_IRTools implements OPT_Operators, VM_Constants {

  /**
   * Create an integer register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, I(r2), A(r1), IC(4)) ...
   * </pre>
   *
   * @param reg the given register
   * @return integer register operand
   */
  public static final OPT_RegisterOperand A(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Address);
  }

  /**
   * Create an integer register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, I(r2), A(r1), IC(4)) ...
   * </pre>
   * @deprecated : use I(OPT_Register) instead
   * @param reg the given register
   * @return integer register operand
   */
  public static final OPT_RegisterOperand R(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Int);
  }

  public static final OPT_RegisterOperand I(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Int);
  }

  /**
   * Create a float register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(FLOAT_LOAD, F(r2), A(r1), IC(4)) ...
   * </pre>
   *
   * @param reg the given register
   * @return float register operand
   */
  public static final OPT_RegisterOperand F(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Float);
  }

  /**
   * Create a double register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(DOUBLE_LOAD, D(r2), A(r1), IC(4)) ...
   * </pre>
   *
   * @param reg the given register
   * @return double register operand
   */
  public static final OPT_RegisterOperand D(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Double);
  }

  /**
   * Create a long register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Binary.create(LONG_LOAD, L(r2), A(r1), IC(4)) ...
   * </pre>
   *
   * @param reg the given register
   * @return long register operand
   */
  public static final OPT_RegisterOperand L(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Long);
  }

  /**
   * Create a condition register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Binary.create(INT_CMP, CR(c2), I(r1), IC(4)) ...
   * </pre>
   *
   * @param reg the given register
   * @return condition register operand
   */
  public static final OPT_RegisterOperand CR(OPT_Register reg) {
    return new OPT_RegisterOperand(reg, VM_TypeReference.Int);
  }

  /**
   * Create an address constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., AC(Address.zero()) ...
   * </pre>
   *
   * @param value, the address constant
   * @return address constant operand
   */
  public static final OPT_AddressConstantOperand AC(Address value) {
    return new OPT_AddressConstantOperand(value);
  }
  
  /**
   * Create an integer constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., IC(0) ...
   * </pre>
   *
   * @param value, the int constant
   * @return integer constant operand
   */
  public static final OPT_IntConstantOperand IC(int value) {
    return new OPT_IntConstantOperand(value);
  }

  /**
   * Create a long constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., LC(0L) ...
   * </pre>
   *
   * @param value the long value
   * @return long constant operand
   */
  public static final OPT_LongConstantOperand LC(long value) {
    return new OPT_LongConstantOperand(value);
  }

  /**
   * Create a long constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., FC(0L) ...
   * </pre>
   *
   * @param value the float value
   * @return float constant operand
   */
  public static final OPT_FloatConstantOperand FC(float value) {
    return new OPT_FloatConstantOperand(value);
  }

  /**
   * Create a long constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., DC(0L) ...
   * </pre>
   *
   * @param value the double value
   * @return double constant operand
   */
  public static final OPT_DoubleConstantOperand DC(double value) {
    return new OPT_DoubleConstantOperand(value);
  }

  /**
   * Create a new OPT_TrueGuardOperand.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., TG() ...
   * </pre>
   * 
   * @return true guard operand
   */
  public static final OPT_TrueGuardOperand TG() {
    return new OPT_TrueGuardOperand();
  }


  /**
   * Copy the position information from the source instruction to
   * the destination instruction, returning the source instruction.
   * To be used in passthrough expressions like
   * <pre>
   *    instr.insertBack(CPOS(instr, Load.create(...)));
   * </pre>
   *
   * @param src the instruction to copy position information from
   * @param dst the instruction to copy position information to
   * @return dest
   */
  public static final OPT_Instruction CPOS(OPT_Instruction src, 
                                           OPT_Instruction dst) {
    dst.copyPosition(src);
    return dst;
  }

  /**
   * Returns a constant operand with a default value for a given type
   *
   * @param type desired type
   * @return a constant operand with the default value for type
   */
  public static final OPT_Operand getDefaultOperand(VM_TypeReference type) {
    if (type.isBooleanType()) return new OPT_IntConstantOperand(0);
    if (type.isByteType())    return new OPT_IntConstantOperand(0);
    if (type.isCharType())    return new OPT_IntConstantOperand(0);
    if (type.isIntType())     return new OPT_IntConstantOperand(0);
    if (type.isShortType())   return new OPT_IntConstantOperand(0);
    if (type.isLongType())    return new OPT_LongConstantOperand(0);
    if (type.isFloatType())   return new OPT_FloatConstantOperand(0f);
    if (type.isDoubleType())  return new OPT_DoubleConstantOperand(0.0);
    return new OPT_NullConstantOperand();
  }

  /**
   * Returns the correct operator for moving the given data type.
   *
   * @param type desired type to move
   * @return the OPT_Operator to use for moving a value of the given type
   */
  public static final OPT_Operator getMoveOp(VM_TypeReference type) {
    if (type.isLongType())    return LONG_MOVE;
    if (type.isFloatType())   return FLOAT_MOVE;
    if (type.isDoubleType())  return DOUBLE_MOVE;
    if (type == VM_TypeReference.VALIDATION_TYPE) return GUARD_MOVE;
    if (type.isReferenceType() || type.isWordType()) return REF_MOVE;
    return INT_MOVE;
  }


  /**
   * Returns the correct operator for a conditional move with the given data 
   * type.
   *
   * @param type desired type to move
   * @return the OPT_Operator to use for moving a value of the given type
   */
  public static final OPT_Operator getCondMoveOp(VM_TypeReference type) {
    if (type.isLongType())    return LONG_COND_MOVE;
    if (type.isFloatType())   return FLOAT_COND_MOVE;
    if (type.isDoubleType())  return DOUBLE_COND_MOVE;
    if (type == VM_TypeReference.VALIDATION_TYPE) return GUARD_COND_MOVE;
    if (type.isReferenceType() || type.isWordType()) return REF_COND_MOVE;
    return INT_COND_MOVE;
  }


  /**
   * Returns the correct operator for loading from the given field
   *
   * @param field field to load from
   * @return the OPT_Operator to use when loading the given field
   */
  public static final OPT_Operator getLoadOp(VM_FieldReference field) {
    return getLoadOp(field.getFieldContentsType());
  }

  /**
   * Returns the correct operator for loading a value of the given type
   *
   * @param type type of value to load
   * @return the OPT_Operator to use when loading the given field
   */
  public static final OPT_Operator getLoadOp(VM_TypeReference type) {
    // TODO: Until we pack subword fields, there is no reason to
    //       use the sub-word load operators because it only forces us 
    //       into doing useless sign extension.
    if (false) {
      if (type.isByteType())      return BYTE_LOAD;
      if (type.isBooleanType())   return UBYTE_LOAD;
      if (type.isCharType())      return USHORT_LOAD;
      if (type.isShortType())     return SHORT_LOAD;
    }
    if (type.isLongType())      return LONG_LOAD;
    if (type.isFloatType())     return FLOAT_LOAD;
    if (type.isDoubleType())    return DOUBLE_LOAD;
    if (type.isReferenceType()) return REF_LOAD;
    if (type.isWordType())      return REF_LOAD;
    return INT_LOAD;
  }

  /**
   * Returns the correct operator for storing to the given field.
   *
   * @param type desired type to store
   * @return the OPT_Operator to use when storing to the given field
   */
  public static final OPT_Operator getStoreOp(VM_FieldReference field) {
    return getStoreOp(field.getFieldContentsType());
  }

  /**
   * Returns the correct operator for storing a value of the given type
   *
   * @param type desired type to store
   * @return the OPT_Operator to use when storing to the given field
   */
  public static final OPT_Operator getStoreOp(VM_TypeReference type) {
    // TODO: Until we pack subword fields, there is no reason to
    //       use the sub-word load operators because it only forces us 
    //       into doing useless sign extension.
    if (false) {
      if (type.isByteType())      return BYTE_STORE;
      if (type.isBooleanType())   return BYTE_STORE;
      if (type.isCharType())      return SHORT_STORE;
      if (type.isShortType())     return SHORT_STORE;
    }
    if (type.isLongType())       return LONG_STORE;
    if (type.isFloatType())      return FLOAT_STORE;
    if (type.isDoubleType())     return DOUBLE_STORE;
    if (type.isReferenceType())  return REF_STORE;
    if (type.isWordType())       return REF_STORE;
    return INT_STORE;
  }


  /**
   * Generates an instruction to move the given operand into a register, and
   * inserts it before the given instruction.
   *
   * @param pool register pool to allocate from
   * @param s instruction to insert before
   * @param op operand to copy to a register
   * @return register operand that we copied into
   */
  public static final OPT_RegisterOperand moveIntoRegister(OPT_RegisterPool pool,
                                                           OPT_Instruction s,
                                                           OPT_Operand op) {
    if (op instanceof OPT_RegisterOperand) {
      return (OPT_RegisterOperand) op;
    }
    VM_TypeReference type = op.getType();
    OPT_Operator move_op = OPT_IRTools.getMoveOp(type);
    return moveIntoRegister(type, move_op, pool, s, op);
  }


  /**
   * Generates an instruction to move the given operand into a register, and
   * inserts it before the given instruction.
   *
   * @param type type to move
   * @param move_op move operator to use
   * @param pool register pool to allocate from
   * @param s instruction to insert before
   * @param op operand to copy to a register
   * @return last use register operand that we copied into
   */
  public static final OPT_RegisterOperand moveIntoRegister(VM_TypeReference type,
                                                           OPT_Operator move_op,
                                                           OPT_RegisterPool pool,
                                                           OPT_Instruction s,
                                                           OPT_Operand op) {
    OPT_RegisterOperand rop = pool.makeTemp(type);
    s.insertBack(Move.create(move_op, rop, op));
    rop = rop.copyD2U();
    return rop;
  }


  /**
   * Moves the 'from' instruction to immediately before the 'to' instruction.
   *
   * @param from instruction to move
   * @param to instruction after where you want it moved
   */
  public static final void moveInstruction(OPT_Instruction from, OPT_Instruction to) {
    from.remove();
    to.insertBack(from);
  }


  /**
   * Inserts the instructions in the given basic block after the given
   * instruction.
   *
   * @param after instruction after where you want it inserted
   * @param temp basic block which contains the instructions to be inserted.
   */
  public static final void insertInstructionsAfter(OPT_Instruction after,
                                                   OPT_BasicBlock temp) {
    if (temp.isEmpty()) return;
    OPT_Instruction after_after = after.getNext();
    after.linkWithNext(temp.firstRealInstruction());
    if (after_after == null) {
      temp.lastRealInstruction().setNext(null);
    } else {
      temp.lastRealInstruction().linkWithNext(after_after);
    }
  }
  /**
   * Make an empty basic block on an edge in the control flow graph,
   * and fix up the control flow graph and IR instructions accordingly.
   *
   * This routine will create the control struture
   * <pre>
   * in -> bb -> out.
   * </pre>
   * <em> Precondition </em>: There is an edge in the control flow graph 
   * from * in -> out.
   *
   * <p> TODO: write a cleaner version of this and put it in OPT_IRTools.java
   * Once we explicitly link phi operands to basic blocks, we won't have
   * to muck with the internals of the control flow graph to implement
   * this function.
   *
   * @param in the source of the control flow edge
   * @param out the sink of the control flow edge
   * @param ir the governing IR
   * @return the new basic block bb
   */
  public static OPT_BasicBlock makeBlockOnEdge (OPT_BasicBlock in, 
                                                OPT_BasicBlock out, 
                                                OPT_IR ir) {
    // 1. Create the new basic block
    OPT_BasicBlock bb = in.createSubBlock(-1, ir);
    
    // 2. Splice the new basic block into the code order
    OPT_BasicBlock next = in.nextBasicBlockInCodeOrder();
    if (next == null ) {
      ir.cfg.addLastInCodeOrder(bb);
    } else {
      ir.cfg.breakCodeOrder(in, next);
      ir.cfg.linkInCodeOrder(in, bb);
      ir.cfg.linkInCodeOrder(bb, next);
    }
  
    // 3. update in's branch instructions
    boolean foundGoto = false;
    OPT_BranchOperand target = bb.makeJumpTarget();
    OPT_BranchOperand outTarget = out.makeJumpTarget();
    for (OPT_InstructionEnumeration e = in.reverseRealInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (IfCmp2.conforms(s)) {
        if (IfCmp2.getTarget1(s).similar(outTarget))
          IfCmp2.setTarget1(s, (OPT_BranchOperand)target.copy());
        if (IfCmp2.getTarget2(s).similar(outTarget))
          IfCmp2.setTarget2(s, (OPT_BranchOperand)target.copy());
      } else if (IfCmp.conforms(s)) {
        if (IfCmp.getTarget(s).similar(outTarget)) {
          IfCmp.setTarget(s, (OPT_BranchOperand)target.copy());
        }
      } else if (InlineGuard.conforms(s)) {
        if (InlineGuard.getTarget(s).similar(outTarget))
          InlineGuard.setTarget(s, (OPT_BranchOperand)target.copy());
      } else if (Goto.conforms(s)) {
        foundGoto = true;
        if (Goto.getTarget(s).similar(outTarget))
          Goto.setTarget(s, (OPT_BranchOperand)target.copy());
      } else if (TableSwitch.conforms(s)) {
        foundGoto = true;
        if (TableSwitch.getDefault(s).similar(outTarget))
          TableSwitch.setDefault(s, (OPT_BranchOperand)target.copy());
        for (int i = 0; i < TableSwitch.getNumberOfTargets(s); i++)
          if (TableSwitch.getTarget(s, i).similar(outTarget))
            TableSwitch.setTarget(s, i, (OPT_BranchOperand)target.copy());
      } else if (LowTableSwitch.conforms(s)) {
        foundGoto = true;
        for (int i = 0; i < LowTableSwitch.getNumberOfTargets(s); i++)
          if (LowTableSwitch.getTarget(s, i).similar(outTarget))
            LowTableSwitch.setTarget(s, i, (OPT_BranchOperand)target.copy());
      } else if (LookupSwitch.conforms(s)) {
        foundGoto = true;
        if (LookupSwitch.getDefault(s).similar(outTarget))
          LookupSwitch.setDefault(s, (OPT_BranchOperand)target.copy());
        for (int i = 0; i < LookupSwitch.getNumberOfTargets(s); i++)
          if (LookupSwitch.getTarget(s, i).similar(outTarget))
            LookupSwitch.setTarget(s, i, (OPT_BranchOperand)target.copy());
      } else {
        // done processing all branches
        break;
      }
    }
    
    // 4. Add a goto bb->out 
    OPT_Instruction s = Goto.create(GOTO, out.makeJumpTarget());
    bb.appendInstruction(s);
    // add goto in->next
    // if out was not the fallthrough, add a GOTO to preserve this
    // control flow
    if (out != next) {
      // if there's already a GOTO, there's no fall through
      if (!foundGoto) {
        s = Goto.create(GOTO, next.makeJumpTarget());
        in.appendInstruction(s);
      }
    }

    // 5. Update the CFG
    in.recomputeNormalOut(ir);
    bb.recomputeNormalOut(ir);

    return  bb;
  }
  /**
   * Is the operand u, which is a use in instruction s, also a def
   * in instruction s?  That is, is this operand defined as a DU operand
   * in InstructionFormatList.dat.
   *
   * TODO!!: This implementation is slow.  Think about adding
   * some IR support for this functionality; possibly add methods like
   * enumeratePureDefs(), enumerateImpureUses(), etc ..., and restructure
   * the caller to avoid having to call this function.  Not going
   * to put effort into this now, as the whole scratch register
   * architecture has a questionable future.
   */
  public static boolean useDoublesAsDef(OPT_Operand u, 
                                        OPT_Instruction s) {
    for (Enumeration d = s.getDefs(); d.hasMoreElements(); ) {
      OPT_Operand def = (OPT_Operand)d.nextElement();
      if (def != null) {
        if (def == u) return true;
      }
    }
    return false;
  }
  /**
   * Is the operand d, which is a def in instruction s, also a def
   * in instruction s?  That is, is this operand defined as a DU operand
   * in InstructionFormatList.dat.
   *
   * TODO!!: This implementation is slow.  Think about adding
   * some IR support for this functionality; possibly add methods like
   * enumeratePureDefs(), enumerateImpureUses(), etc ..., and restructure
   * the caller to avoid having to call this function.  Not going
   * to put effort into this now, as the whole scratch register
   * architecture has a questionable future.
   */
  public static boolean defDoublesAsUse(OPT_Operand d, 
                                        OPT_Instruction s) {
    for (Enumeration u = s.getUses(); u.hasMoreElements(); ) {
      OPT_Operand use = (OPT_Operand)u.nextElement();
      if (use != null) {
        if (use.similar(d)) return true;
      }
    }
    return false;
  }

  /**
   * Does instruction s define register r?
   */
  public static boolean definedIn(OPT_Register r, OPT_Instruction s) {
    for (Enumeration e = s.getDefs(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().register.number == r.number) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Does instruction s use register r?
   */
  public static boolean usedIn(OPT_Register r, OPT_Instruction s) {
    for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().register.number == r.number) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Mark the parameter as nonGC and nonPEI and return it.
   * To be used in passthrough expressions like
   * <pre>
   *    instr.insertBack(notPEIGC(Load.create(...)));
   * </pre>
   *
   * @param instr the given instruction
   * @return the given instruction
   */
  public static final OPT_Instruction nonPEIGC(OPT_Instruction instr) {
    instr.markAsNonPEINonGCPoint();
    return instr;
  }

  /**
   * Might this instruction be a load from a field that is declared 
   * to be volatile?
   *
   * @param s the insruction to check
   * @return <code>true</code> if the instruction might be a load
   *         from a volatile field or <code>false</code> if it 
   *         cannot be a load from a volatile field
   */
  public static boolean mayBeVolatileFieldLoad(OPT_Instruction s) {
    return s.mayBeVolatileFieldLoad();
  }
}

