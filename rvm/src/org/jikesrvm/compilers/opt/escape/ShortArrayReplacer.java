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
package org.jikesrvm.compilers.opt.escape;

import java.util.HashSet;
import java.util.Set;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BoundsCheck;
import org.jikesrvm.compilers.opt.ir.CondMove;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TrapIf;
import org.jikesrvm.compilers.opt.ir.TypeCheck;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import static org.jikesrvm.compilers.opt.ir.IRTools.IC;
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
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWARRAY;
import static org.jikesrvm.compilers.opt.ir.Operators.NEWOBJMULTIARRAY_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.OBJARRAY_STORE_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP_IF;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD_opcode;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;

/**
 * Class that performs scalar replacement of short arrays
 */
final class ShortArrayReplacer implements AggregateReplacer {
  /**
   * number of elements in the array
   */
  private final int size;
  /**
   * type of the array
   */
  private final RVMArray vmArray;
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
  private ShortArrayReplacer(Register r, RVMArray a, int s, IR i) {
    reg = r;
    vmArray = a;
    size = s;
    ir = i;
  }

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
    if (s > ir.options.ESCAPE_MAX_ARRAY_SIZE) {
      return null;
    }
    if (s < 0) {
      return null;
    }
    Register r = NewArray.getResult(inst).getRegister();
    RVMArray a = NewArray.getType(inst).getVMType().asArray();
    // TODO :handle these cases
    if (containsUnsupportedUse(ir, r, s, a, null)) {
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
    TypeReference elementType = vmArray.getElementType().getTypeRef();
    RegisterOperand def = reg.defList;
    Instruction defI = def.instruction;
    Operand defaultValue = IRTools.getDefaultOperand(elementType);
    for (int i = 0; i < size; i++) {
      scalars[i] = IRTools.moveIntoRegister(elementType, IRTools.getMoveOp(elementType), ir.regpool, defI, defaultValue.copy());
    }
    transform2(this.reg, defI, scalars);
  }
  private void transform2(Register reg, Instruction defI, RegisterOperand[] scalars) {
    final boolean DEBUG = false;

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
   * Replace a given use of an array with its scalar equivalent.
   *
   * @param use the use to replace
   * @param scalars an array of scalar register operands to replace
   *                  the array with
   */
  private void scalarReplace(RegisterOperand use, RegisterOperand[] scalars, Set<Register> visited) {
    Instruction inst = use.instruction;
    RVMType type = vmArray.getElementType();
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
        if (ALoad.getIndex(inst).isIntConstant()) {
          Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
          int index = ALoad.getIndex(inst).asIntConstant().value;
          if (index >= 0 && index < size) {
            Instruction i2 = Move.create(moveOp, ALoad.getClearResult(inst), scalars[index].copyRO());
            DefUse.replaceInstructionAndUpdateDU(inst, i2);
          } else {
            DefUse.removeInstructionAndUpdateDU(inst);
          }
        } else {
          if (VM.BuildForIA32) {
            if (size == 0) {
              DefUse.removeInstructionAndUpdateDU(inst);
            } else if (size == 1) {
              int index = 0;
              Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
              Instruction i2 =  Move.create(moveOp, ALoad.getClearResult(inst), scalars[index].copyRO());
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
            } else {
              Operator moveOp = IRTools.getCondMoveOp(type.getTypeRef());
              Instruction i2 = CondMove.create(moveOp, ALoad.getClearResult(inst),
                  ALoad.getIndex(inst), IC(0), ConditionOperand.EQUAL(),
                  scalars[0].copyRO(), scalars[1].copyRO());
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
            }
          } else {
            if (size == 1) {
              int index = 0;
              Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
              Instruction i2 =  Move.create(moveOp, ALoad.getClearResult(inst), scalars[index].copyRO());
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
            } else {
              DefUse.removeInstructionAndUpdateDU(inst);
            }
          }
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
        if (AStore.getIndex(inst).isIntConstant()) {
          int index = AStore.getIndex(inst).asIntConstant().value;
          if (index >= 0 && index < size) {
            Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
            Instruction i2 =  Move.create(moveOp, scalars[index].copyRO(), AStore.getClearValue(inst));
            DefUse.replaceInstructionAndUpdateDU(inst, i2);
          } else {
            DefUse.removeInstructionAndUpdateDU(inst);
          }
        } else {
          if (VM.BuildForIA32) {
            if (size == 0) {
              DefUse.removeInstructionAndUpdateDU(inst);
            } else if (size == 1) {
              int index = 0;
              Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
              Instruction i2 =  Move.create(moveOp, scalars[index].copyRO(), AStore.getClearValue(inst));
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
            } else {
              Operator moveOp = IRTools.getCondMoveOp(type.getTypeRef());
              Operand value = AStore.getClearValue(inst);
              Instruction i2 = CondMove.create(moveOp, scalars[0].copyRO(),
                  AStore.getIndex(inst), IC(0), ConditionOperand.EQUAL(),
                  value, scalars[0].copyRO());
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
              Instruction i3 = CondMove.create(moveOp, scalars[1].copyRO(),
                  AStore.getIndex(inst), IC(0), ConditionOperand.NOT_EQUAL(),
                  value, scalars[1].copyRO());
              i2.insertAfter(i3);
              DefUse.updateDUForNewInstruction(i3);
            }
          } else {
            if (size == 1) {
              int index = 0;
              Operator moveOp = IRTools.getMoveOp(type.getTypeRef());
              Instruction i2 =  Move.create(moveOp, scalars[index].copyRO(), AStore.getClearValue(inst));
              DefUse.replaceInstructionAndUpdateDU(inst, i2);
            } else {
              DefUse.removeInstructionAndUpdateDU(inst);
            }
          }
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
        Instruction i2 = TrapIf.create(TRAP_IF, BoundsCheck.getClearGuardResult(inst),
            IC(size), BoundsCheck.getClearIndex(inst), ConditionOperand.LOWER_EQUAL(),
            TrapCodeOperand.ArrayBounds());
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode: {
        // We cannot handle removing the checkcast if the result of the
        // checkcast test is unknown
        TypeReference lhsType = TypeCheck.getType(inst).getTypeRef();
        if (ClassLoaderProxy.includesType(lhsType, vmArray.getTypeRef()) == OptConstants.YES) {
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          Register copy = TypeCheck.getResult(inst).getRegister();
          if(!visited.contains(copy)) {
            visited.add(copy);
            transform2(copy, inst, scalars);
            // NB will remove inst
          } else {
            DefUse.removeInstructionAndUpdateDU(inst);
          }
        } else {
          Instruction i2 = Trap.create(TRAP, null, TrapCodeOperand.CheckCast());
          DefUse.replaceInstructionAndUpdateDU(inst, i2);
        }
      }
      break;
      case INSTANCEOF_opcode:
      case INSTANCEOF_NOTNULL_opcode:
      case INSTANCEOF_UNRESOLVED_opcode: {
        // We cannot handle removing the instanceof if the result of the
        // instanceof test is unknown
        TypeReference lhsType = InstanceOf.getType(inst).getTypeRef();
        Instruction i2;
        if (ClassLoaderProxy.includesType(lhsType, vmArray.getTypeRef()) == OptConstants.YES) {
          i2 = Move.create(INT_MOVE, InstanceOf.getClearResult(inst), IC(1));
        } else {
          i2 = Move.create(INT_MOVE, InstanceOf.getClearResult(inst), IC(0));
        }
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case GET_OBJ_TIB_opcode: {
        Instruction i2 = Move.create(REF_MOVE, GuardedUnary.getClearResult(inst), new TIBConstantOperand(vmArray));
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case REF_MOVE_opcode: {
        if (visited == null) {
          visited = new HashSet<Register>();
        }
        Register copy = Move.getResult(inst).getRegister();
        if(!visited.contains(copy)) {
          visited.add(copy);
          transform2(copy, inst, scalars);
          // NB will remove inst
        } else {
          DefUse.removeInstructionAndUpdateDU(inst);
        }
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
  private static boolean containsUnsupportedUse(IR ir, Register reg, int size, RVMArray vmArray, Set<Register> visited) {
    // If an array is accessed by a non-constant integer, what's the maximum size of support array?
    final int MAX_SIZE_FOR_VARIABLE_LOAD_STORE = VM.BuildForIA32 ? 2 : 1;
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      switch (use.instruction.getOpcode()) {
        case REF_IFCMP_opcode:
          // Comparison between the array reference we want to replace and
          // another. TODO: this case is either always true or always false,
          // we should optimize
        case NEWOBJMULTIARRAY_opcode:
          // dimensions array must be passed as an array argument to
          // newobjmultiarray, common case of 2 arguments is handled without a
          // dimensions array
        case OBJARRAY_STORE_CHECK_opcode:
        case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
          // TODO: create a store check that doesn't need an array argument
          return true;
        case CHECKCAST_opcode:
        case CHECKCAST_NOTNULL_opcode:
        case CHECKCAST_UNRESOLVED_opcode: {
          // We cannot handle removing the checkcast if the result of the
          // checkcast test is unknown
          TypeReference lhsType = TypeCheck.getType(use.instruction).getTypeRef();
          byte ans = ClassLoaderProxy.includesType(lhsType, vmArray.getTypeRef());
          if (ans == OptConstants.MAYBE) {
            return true;
          } else if (ans == OptConstants.YES) {
            // handle as a move
            if (visited == null) {
              visited = new HashSet<Register>();
            }
            Register copy = TypeCheck.getResult(use.instruction).getRegister();
            if(!visited.contains(copy)) {
              visited.add(copy);
              if(containsUnsupportedUse(ir, copy, size, vmArray, visited)) {
                return true;
              }
            }
          }
        }
        break;
        case INSTANCEOF_opcode:
        case INSTANCEOF_NOTNULL_opcode:
        case INSTANCEOF_UNRESOLVED_opcode: {
          // We cannot handle removing the instanceof if the result of the
          // instanceof test is unknown
          TypeReference lhsType = InstanceOf.getType(use.instruction).getTypeRef();
          if (ClassLoaderProxy.includesType(lhsType, vmArray.getTypeRef()) == OptConstants.MAYBE) {
            return true;
          }
        }
        break;
        case INT_ASTORE_opcode:
        case LONG_ASTORE_opcode:
        case FLOAT_ASTORE_opcode:
        case DOUBLE_ASTORE_opcode:
        case BYTE_ASTORE_opcode:
        case SHORT_ASTORE_opcode:
        case REF_ASTORE_opcode:
          // Don't handle registers as indexes
          // TODO: support for registers if the size of the array is small (e.g. 1)
          if (!AStore.getIndex(use.instruction).isIntConstant() && size > MAX_SIZE_FOR_VARIABLE_LOAD_STORE) {
            return true;
          }
          break;
        case INT_ALOAD_opcode:
        case LONG_ALOAD_opcode:
        case FLOAT_ALOAD_opcode:
        case DOUBLE_ALOAD_opcode:
        case BYTE_ALOAD_opcode:
        case UBYTE_ALOAD_opcode:
        case USHORT_ALOAD_opcode:
        case SHORT_ALOAD_opcode:
        case REF_ALOAD_opcode:
          // Don't handle registers as indexes
          // TODO: support for registers if the size of the array is small (e.g. 1)
          if (!ALoad.getIndex(use.instruction).isIntConstant() && size > MAX_SIZE_FOR_VARIABLE_LOAD_STORE) {
            return true;
          }
          break;
        case REF_MOVE_opcode:
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          Register copy = Move.getResult(use.instruction).getRegister();
          if(!visited.contains(copy)) {
            visited.add(copy);
            if(containsUnsupportedUse(ir, copy, size, vmArray, visited)) {
              return true;
            }
          }
          break;
      }
    }
    return false;
  }
}
