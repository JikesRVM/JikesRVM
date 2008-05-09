/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.HashSet;
import java.util.Set;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Trap;
import static org.jikesrvm.compilers.opt.ir.Operators.BOUNDS_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_OBJ_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWOBJMULTIARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;

/**
 * Class that performs scalar replacement of short arrays
 */
public final class ShortArrayReplacer implements AggregateReplacer {
  private static final boolean DEBUG = false;

  /**
   * Arrays shorter than this length are candidates to be replaced by
   * scalar values.
   */
  public static final int SHORT_ARRAY_SIZE = 5;

  /**
   * Return an object representing this transformation for a given
   * allocation site
   *
   * @param inst the allocation site
   * @param ir
   * @return the object, or null if illegal
   */
  public static ShortArrayReplacer getReplacer(Instruction inst, IR ir) {
    if (inst.operator != NEWARRAY) {
      return null;
    }
    Operand size = NewArray.getSize(inst);
    if (!size.isIntConstant()) {
      return null;
    }
    int s = size.asIntConstant().value;
    if (s > SHORT_ARRAY_SIZE) {
      return null;
    }
    if (s < 0) {
      return null;
    }
    Register r = NewArray.getResult(inst).getRegister();
    VM_Array a = NewArray.getType(inst).getVMType().asArray();
    // TODO :handle these cases
    if (containsUnsupportedUse(ir, r, s, null)) {
      return null;
    }
    return new ShortArrayReplacer(r, a, s, ir);
  }

  /**
   * Perform the transformation.
   */
  public void transform() {
    // first set up temporary scalars for the array elements
    // initialize them before the def.
    RegisterOperand[] scalars = new RegisterOperand[size];
    VM_TypeReference elementType = vmArray.getElementType().getTypeRef();
    RegisterOperand def = reg.defList;
    Instruction defI = def.instruction;
    Operand defaultValue = IRTools.getDefaultOperand(elementType);
    for (int i = 0; i < size; i++) {
      scalars[i] = IRTools.moveIntoRegister(elementType, IRTools.getMoveOp(elementType), ir.regpool, defI, defaultValue.copy());
    }
    transform2(this.reg, defI, scalars);
  }
  private void transform2(Register reg, Instruction defI, RegisterOperand[] scalars) {
    // now remove the def
    if (DEBUG) {
      System.out.println("Removing " + defI);
    }
    DefUse.removeInstructionAndUpdateDU(defI);
    // now handle the uses
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      scalarReplace(use, scalars, null);
    }
  }

  /**
   * number of elements in the array
   */
  private final int size;
  /**
   * type of the array
   */
  private final VM_Array vmArray;
  /**
   * the register holding the array reference
   */
  private final Register reg;
  /**
   * the governing IR
   */
  private final IR ir;

  /**
   * @param r the register holding the array reference
   * @param a the type of the array to replace
   * @param s the size of the array to replace
   * @param i the IR
   */
  private ShortArrayReplacer(Register r, VM_Array a, int s, IR i) {
    reg = r;
    vmArray = a;
    size = s;
    ir = i;
  }

  /**
   * Replace a given use of an array with its scalar equivalent.
   *
   * @param use the use to replace
   * @param scalars an array of scalar register operands to replace
   *                  the array with
   */
  private void scalarReplace(RegisterOperand use, RegisterOperand[] scalars, Set<Register> visited) {
    Instruction inst = use.instruction;
    VM_Type type = vmArray.getElementType();
    Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
    switch (inst.getOpcode()) {
      case INT_ALOAD_opcode:
      case LONG_ALOAD_opcode:
      case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:
      case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:
      case USHORT_ALOAD_opcode:
      case SHORT_ALOAD_opcode:
      case REF_ALOAD_opcode: {
        // Create use of scalar or eliminate unreachable instruction because
        // of a trap
        int index = ALoad.getIndex(inst).asIntConstant().value;
        if (index >= 0 && index < size) {
          Instruction i2 = Move.create(moveOp, ALoad.getClearResult(inst), scalars[index].copyRO());
          DefUse.replaceInstructionAndUpdateDU(inst, i2);
        } else {
          DefUse.removeInstructionAndUpdateDU(inst);
        }
      }
      break;
      case INT_ASTORE_opcode:
      case LONG_ASTORE_opcode:
      case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:
      case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode: {
        // Create move to scalar or eliminate unreachable instruction because
        // of a trap
        int index = AStore.getIndex(inst).asIntConstant().value;
        if (index >= 0 && index < size) {
          Instruction i2 = Move.create(moveOp, scalars[index].copyRO(), AStore.getClearValue(inst));
          DefUse.replaceInstructionAndUpdateDU(inst, i2);
        } else {
          DefUse.removeInstructionAndUpdateDU(inst);
        }
      }
      break;
      case NULL_CHECK_opcode: {
        // Null check on result of new array must succeed
        Instruction i2 = Move.create(GUARD_MOVE, NullCheck.getClearGuardResult(inst), new TrueGuardOperand());
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case BOUNDS_CHECK_opcode: {
        // Remove or create trap as appropriate
        int index = BoundsCheck.getIndex(inst).asIntConstant().value;
        Instruction i2;
        if (index >= 0 && index < size) {
          i2 = Move.create(GUARD_MOVE, BoundsCheck.getClearGuardResult(inst), new TrueGuardOperand());
        } else {
          i2 = Trap.create(TRAP, BoundsCheck.getClearGuardResult(inst), TrapCodeOperand.ArrayBounds());
        }
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case REF_MOVE_opcode:
        if (visited == null) {
          visited = new HashSet<Register>();
        }
        Register copy = Move.getResult(use.instruction).getRegister();
        if(!visited.contains(copy)) {
          visited.add(copy);
          transform2(copy, inst, scalars);
        }
        break;
      default:
        throw new OptimizingCompilerException("Unexpected instruction: " + inst);
    }
  }

  /**
   * Some cases we don't handle yet. TODO: handle them.
   *
   * @param ir the governing IR
   * @param reg the register in question
   * @param size the size of the array to scalar replace.
   */
  private static boolean containsUnsupportedUse(IR ir, Register reg, int size, Set<Register> visited) {
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      switch (use.instruction.getOpcode()) {
        case NEWOBJMULTIARRAY_opcode:
        case OBJARRAY_STORE_CHECK_opcode:
        case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
        case GET_OBJ_TIB_opcode:
        case INSTANCEOF_opcode:
        case INSTANCEOF_NOTNULL_opcode:
        case INSTANCEOF_UNRESOLVED_opcode:
        case CHECKCAST_opcode:
        case CHECKCAST_NOTNULL_opcode:
        case CHECKCAST_UNRESOLVED_opcode:
          return true;
        case INT_ASTORE_opcode:
        case LONG_ASTORE_opcode:
        case FLOAT_ASTORE_opcode:
        case DOUBLE_ASTORE_opcode:
        case BYTE_ASTORE_opcode:
        case SHORT_ASTORE_opcode:
        case REF_ASTORE_opcode: {
          if (!AStore.getIndex(use.instruction).isIntConstant()) {
            return true;
          }
          break;
        }
        case INT_ALOAD_opcode:
        case LONG_ALOAD_opcode:
        case FLOAT_ALOAD_opcode:
        case DOUBLE_ALOAD_opcode:
        case BYTE_ALOAD_opcode:
        case UBYTE_ALOAD_opcode:
        case USHORT_ALOAD_opcode:
        case SHORT_ALOAD_opcode:
        case REF_ALOAD_opcode: {
          if (!ALoad.getIndex(use.instruction).isIntConstant()) {
            return true;
          }
          break;
        }
        case REF_MOVE_opcode:
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          Register copy = Move.getResult(use.instruction).getRegister();
          if(!visited.contains(copy)) {
            visited.add(copy);
            if(containsUnsupportedUse(ir, copy, size, visited)) {
              return true;
            }
          }
          break;
      }
    }
    return false;
  }
}
