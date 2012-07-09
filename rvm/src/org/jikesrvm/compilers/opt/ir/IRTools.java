/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
import org.jikesrvm.ArchitectureSpecificOpt.RegisterPool;
import org.jikesrvm.Configuration;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.BranchOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;

import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.GOTO;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_STORE;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_LOAD;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_LOAD;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * This abstract class contains a bunch of useful static methods for
 * performing operations on IR.
 */
public abstract class IRTools {

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
  public static RegisterOperand A(Register reg) {
    return new RegisterOperand(reg, TypeReference.Address);
  }

  /**
   * Create an integer register operand for a given register.
   * To be used in passthrough expressions like
   * <pre>
   *    ... Load.create(INT_LOAD, I(r2), A(r1), IC(4)) ...
   * </pre>
   * @param reg the given register
   * @return integer register operand
   */
  public static RegisterOperand I(Register reg) {
    return new RegisterOperand(reg, TypeReference.Int);
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
  public static RegisterOperand F(Register reg) {
    return new RegisterOperand(reg, TypeReference.Float);
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
  public static RegisterOperand D(Register reg) {
    return new RegisterOperand(reg, TypeReference.Double);
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
  public static RegisterOperand L(Register reg) {
    return new RegisterOperand(reg, TypeReference.Long);
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
  public static RegisterOperand CR(Register reg) {
    return new RegisterOperand(reg, TypeReference.Int);
  }

  /**
   * Create an address constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., AC(Address.zero()) ...
   * </pre>
   *
   * @param value    The address constant
   * @return address constant operand
   */
  public static AddressConstantOperand AC(Address value) {
    return new AddressConstantOperand(value);
  }

  public static AddressConstantOperand AC(Offset value) {
    return new AddressConstantOperand(value);
  }

  /**
   * Create an integer constant operand with a given value.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., IC(0) ...
   * </pre>
   *
   * @param value   The int constant
   * @return integer constant operand
   */
  public static IntConstantOperand IC(int value) {
    return new IntConstantOperand(value);
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
  public static LongConstantOperand LC(long value) {
    return new LongConstantOperand(value);
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
  public static FloatConstantOperand FC(float value) {
    return new FloatConstantOperand(value);
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
  public static DoubleConstantOperand DC(double value) {
    return new DoubleConstantOperand(value);
  }

  /**
   * Create a new TrueGuardOperand.
   * To be used in passthrough expressions like
   * <pre>
   *    ...<op>.create(...., TG() ...
   * </pre>
   *
   * @return true guard operand
   */
  public static TrueGuardOperand TG() {
    return new TrueGuardOperand();
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
  public static Instruction CPOS(Instruction src, Instruction dst) {
    dst.copyPosition(src);
    return dst;
  }

  /**
   * Returns a constant operand with a default value for a given type
   *
   * @param type desired type
   * @return a constant operand with the default value for type
   */
  public static Operand getDefaultOperand(TypeReference type) {
    if (type.isBooleanType()) return new IntConstantOperand(0);
    if (type.isByteType()) return new IntConstantOperand(0);
    if (type.isCharType()) return new IntConstantOperand(0);
    if (type.isIntType()) return new IntConstantOperand(0);
    if (type.isShortType()) return new IntConstantOperand(0);
    if (type.isLongType()) return new LongConstantOperand(0);
    if (type.isFloatType()) return new FloatConstantOperand(0f);
    if (type.isDoubleType()) return new DoubleConstantOperand(0.0);
    return new NullConstantOperand();
  }

  /**
   * Returns the correct operator for moving the given data type.
   *
   * @param type desired type to move
   * @return the Operator to use for moving a value of the given type
   */
  public static Operator getMoveOp(TypeReference type) {
    if (type.isLongType()) return LONG_MOVE;
    if (type.isFloatType()) return FLOAT_MOVE;
    if (type.isDoubleType()) return DOUBLE_MOVE;
    if (type == TypeReference.VALIDATION_TYPE) return GUARD_MOVE;
    if (type.isReferenceType() || type.isWordLikeType()) return REF_MOVE;
    return INT_MOVE;
  }

  /**
   * Returns the correct operator for a conditional move with the given data
   * type.
   *
   * @param type desired type to move
   * @return the Operator to use for moving a value of the given type
   */
  public static Operator getCondMoveOp(TypeReference type) {
    if (type.isLongType()) return LONG_COND_MOVE;
    if (type.isFloatType()) return FLOAT_COND_MOVE;
    if (type.isDoubleType()) return DOUBLE_COND_MOVE;
    if (type == TypeReference.VALIDATION_TYPE) return GUARD_COND_MOVE;
    if (type.isReferenceType() || type.isWordLikeType()) return REF_COND_MOVE;
    return INT_COND_MOVE;
  }

  /**
   * Returns the correct operator for loading from the given field
   *
   * @param field field to load from
   * @param isStatic is the field static
   * @return the Operator to use when loading the given field
   */
  public static Operator getLoadOp(FieldReference field, boolean isStatic) {
    return getLoadOp(field.getFieldContentsType(), isStatic);
  }

  /**
   * Returns the correct operator for loading a value of the given type
   *
   * @param type type of value to load
   * @param isStatic is the field static
   * @return the Operator to use when loading the given field
   */
  public static Operator getLoadOp(TypeReference type, boolean isStatic) {
    if (!Configuration.LittleEndian && isStatic) {
      // Handle the statics table hold subword values in ints
      if (type.isByteType()) return INT_LOAD;
      if (type.isBooleanType()) return INT_LOAD;
      if (type.isCharType()) return INT_LOAD;
      if (type.isShortType()) return INT_LOAD;
    }
    if (type.isByteType()) return BYTE_LOAD;
    if (type.isBooleanType()) return UBYTE_LOAD;
    if (type.isCharType()) return USHORT_LOAD;
    if (type.isShortType()) return SHORT_LOAD;
    if (type.isLongType()) return LONG_LOAD;
    if (type.isFloatType()) return FLOAT_LOAD;
    if (type.isDoubleType()) return DOUBLE_LOAD;
    if (type.isReferenceType()) return REF_LOAD;
    if (type.isWordLikeType()) return REF_LOAD;
    return INT_LOAD;
  }

  /**
   * Returns the correct operator for storing to the given field.
   *
   * @param field  The field we're asking about
   * @param isStatic is the field static
   * @return the Operator to use when storing to the given field
   */
  public static Operator getStoreOp(FieldReference field, boolean isStatic) {
    return getStoreOp(field.getFieldContentsType(), isStatic);
  }

  /**
   * Returns the correct operator for storing a value of the given type
   *
   * @param type desired type to store
   * @param isStatic is the field static
   * @return the Operator to use when storing to the given field
   */
  public static Operator getStoreOp(TypeReference type, boolean isStatic) {
    if (!Configuration.LittleEndian && isStatic) {
      // Handle the statics table hold subword values in ints
      if (type.isByteType()) return INT_STORE;
      if (type.isBooleanType()) return INT_STORE;
      if (type.isCharType()) return INT_STORE;
      if (type.isShortType()) return INT_STORE;
    }
    if (type.isByteType()) return BYTE_STORE;
    if (type.isBooleanType()) return BYTE_STORE;
    if (type.isCharType()) return SHORT_STORE;
    if (type.isShortType()) return SHORT_STORE;
    if (type.isLongType()) return LONG_STORE;
    if (type.isFloatType()) return FLOAT_STORE;
    if (type.isDoubleType()) return DOUBLE_STORE;
    if (type.isReferenceType()) return REF_STORE;
    if (type.isWordLikeType()) return REF_STORE;
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
  public static RegisterOperand moveIntoRegister(RegisterPool pool, Instruction s, Operand op) {
    if (op instanceof RegisterOperand) {
      return (RegisterOperand) op;
    }
    TypeReference type = op.getType();
    Operator move_op = IRTools.getMoveOp(type);
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
  public static RegisterOperand moveIntoRegister(TypeReference type, Operator move_op, RegisterPool pool,
                                                     Instruction s, Operand op) {
    RegisterOperand rop = pool.makeTemp(type);
    s.insertBefore(Move.create(move_op, rop, op));
    rop = rop.copyD2U();
    return rop;
  }

  /**
   * Moves the 'from' instruction to immediately before the 'to' instruction.
   *
   * @param from instruction to move
   * @param to instruction after where you want it moved
   */
  public static void moveInstruction(Instruction from, Instruction to) {
    from.remove();
    to.insertBefore(from);
  }

  /**
   * Inserts the instructions in the given basic block after the given
   * instruction.
   *
   * @param after instruction after where you want it inserted
   * @param temp basic block which contains the instructions to be inserted.
   */
  public static void insertInstructionsAfter(Instruction after, BasicBlock temp) {
    if (temp.isEmpty()) return;
    Instruction after_after = after.getNext();
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
   * @param in the source of the control flow edge
   * @param out the sink of the control flow edge
   * @param ir the governing IR
   * @return the new basic block bb
   */
  public static BasicBlock makeBlockOnEdge(BasicBlock in, BasicBlock out, IR ir) {
    // 1. Create the new basic block
    BasicBlock bb = in.createSubBlock(out.firstInstruction().bcIndex, ir);

    // 2. Splice the new basic block into the code order
    BasicBlock next = in.nextBasicBlockInCodeOrder();
    if (next == null) {
      ir.cfg.addLastInCodeOrder(bb);
    } else {
      ir.cfg.breakCodeOrder(in, next);
      ir.cfg.linkInCodeOrder(in, bb);
      ir.cfg.linkInCodeOrder(bb, next);
    }

    // 3. update in's branch instructions
    boolean foundGoto = false;
    BranchOperand target = bb.makeJumpTarget();
    BranchOperand outTarget = out.makeJumpTarget();
    for (InstructionEnumeration e = in.reverseRealInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.next();
      if (IfCmp2.conforms(s)) {
        if (IfCmp2.getTarget1(s).similar(outTarget)) {
          IfCmp2.setTarget1(s, (BranchOperand) target.copy());
        }
        if (IfCmp2.getTarget2(s).similar(outTarget)) {
          IfCmp2.setTarget2(s, (BranchOperand) target.copy());
        }
      } else if (IfCmp.conforms(s)) {
        if (IfCmp.getTarget(s).similar(outTarget)) {
          IfCmp.setTarget(s, (BranchOperand) target.copy());
        }
      } else if (InlineGuard.conforms(s)) {
        if (InlineGuard.getTarget(s).similar(outTarget)) {
          InlineGuard.setTarget(s, (BranchOperand) target.copy());
        }
      } else if (Goto.conforms(s)) {
        foundGoto = true;
        if (Goto.getTarget(s).similar(outTarget)) {
          Goto.setTarget(s, (BranchOperand) target.copy());
        }
      } else if (TableSwitch.conforms(s)) {
        foundGoto = true;
        if (TableSwitch.getDefault(s).similar(outTarget)) {
          TableSwitch.setDefault(s, (BranchOperand) target.copy());
        }
        for (int i = 0; i < TableSwitch.getNumberOfTargets(s); i++) {
          if (TableSwitch.getTarget(s, i).similar(outTarget)) {
            TableSwitch.setTarget(s, i, (BranchOperand) target.copy());
          }
        }
      } else if (LowTableSwitch.conforms(s)) {
        foundGoto = true;
        for (int i = 0; i < LowTableSwitch.getNumberOfTargets(s); i++) {
          if (LowTableSwitch.getTarget(s, i).similar(outTarget)) {
            LowTableSwitch.setTarget(s, i, (BranchOperand) target.copy());
          }
        }
      } else if (LookupSwitch.conforms(s)) {
        foundGoto = true;
        if (LookupSwitch.getDefault(s).similar(outTarget)) {
          LookupSwitch.setDefault(s, (BranchOperand) target.copy());
        }
        for (int i = 0; i < LookupSwitch.getNumberOfTargets(s); i++) {
          if (LookupSwitch.getTarget(s, i).similar(outTarget)) {
            LookupSwitch.setTarget(s, i, (BranchOperand) target.copy());
          }
        }
      } else {
        // done processing all branches
        break;
      }
    }

    // 4. Add a goto bb->out
    Instruction s = Goto.create(GOTO, out.makeJumpTarget());
    bb.appendInstruction(s);
    // add goto in->next
    // if out was not the fallthrough, add a GOTO to preserve this
    // control flow
    if (out != next) {
      // if there's already a GOTO, there's no fall through
      if (!foundGoto) {
        /*
         * TODO: come up with a better fix (?).
         *
         * This is a fix to a particular problem in dacapo xalan.
         *
         * We have a loop inside an exception handler, and the exception handler
         * is empty.  The loop termination condition simply falls through the
         * exception handler to the next block.  This works fine until LeaveSSA,
         * when we split the final block and insert a GOTO to the exception handler
         * block.  When we reassemble the IR afterwards, kaboom.
         *
         * I would have though it better not to fall through empty exception handlers
         * at all, and explicitly GOTO past them from the get go.   RJG 4/2/7
         */
        BasicBlock jumpTarget = next;
        while (jumpTarget.isEmpty() && jumpTarget.isExceptionHandlerBasicBlock()) {
          jumpTarget = jumpTarget.nextBasicBlockInCodeOrder();
        }
        s = Goto.create(GOTO, jumpTarget.makeJumpTarget());
        in.appendInstruction(s);
      }
    }

    // 5. Update the CFG
    in.recomputeNormalOut(ir);
    bb.recomputeNormalOut(ir);

    return bb;
  }

  /**
   * Is the operand u, which is a use in instruction s, also a def
   * in instruction s?  That is, is this operand defined as a DU operand
   * in InstructionFormatList.dat.
   * <p>
   * TODO!!: This implementation is slow.  Think about adding
   * some IR support for this functionality; possibly add methods like
   * enumeratePureDefs(), enumerateImpureUses(), etc ..., and restructure
   * the caller to avoid having to call this function.  Not going
   * to put effort into this now, as the whole scratch register
   * architecture has a questionable future.
   */
  public static boolean useDoublesAsDef(Operand u, Instruction s) {
    for (Enumeration<Operand> d = s.getDefs(); d.hasMoreElements();) {
      Operand def = d.nextElement();
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
   * <p>
   * TODO!!: This implementation is slow.  Think about adding
   * some IR support for this functionality; possibly add methods like
   * enumeratePureDefs(), enumerateImpureUses(), etc ..., and restructure
   * the caller to avoid having to call this function.  Not going
   * to put effort into this now, as the whole scratch register
   * architecture has a questionable future.
   */
  public static boolean defDoublesAsUse(Operand d, Instruction s) {
    for (Enumeration<Operand> u = s.getUses(); u.hasMoreElements();) {
      Operand use = u.nextElement();
      if (use != null) {
        if (use.similar(d)) return true;
      }
    }
    return false;
  }

  /**
   * Does instruction s define register r?
   */
  public static boolean definedIn(Register r, Instruction s) {
    for (Enumeration<Operand> e = s.getDefs(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().getRegister().number == r.number) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Does instruction s use register r?
   */
  public static boolean usedIn(Register r, Instruction s) {
    for (Enumeration<Operand> e = s.getUses(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().getRegister().number == r.number) {
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
  public static Instruction nonPEIGC(Instruction instr) {
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
  public static boolean mayBeVolatileFieldLoad(Instruction s) {
    return s.mayBeVolatileFieldLoad();
  }
}
