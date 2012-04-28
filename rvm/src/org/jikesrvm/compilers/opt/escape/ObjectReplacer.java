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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.InstanceOf;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Trap;
import org.jikesrvm.compilers.opt.ir.TypeCheck;

import static org.jikesrvm.compilers.opt.ir.IRTools.IC;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BOOLEAN_CMP_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.CHECKCAST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_OBJ_TIB_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_NOTNULL_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_UNRESOLVED_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INSTANCEOF_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.MUST_IMPLEMENT_INTERFACE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.READ_CEILING;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.TRAP;
import static org.jikesrvm.compilers.opt.ir.Operators.WRITE_FLOOR;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrapCodeOperand;

/**
 * Class that performs scalar replacement of aggregates for non-array
 * objects
 */
final class ObjectReplacer implements AggregateReplacer {
  /**
   * type of the object
   */
  private final RVMClass klass;
  /**
   * the IR
   */
  private final IR ir;
  /**
   * the register holding the object reference
   */
  private final Register reg;

  /**
   * Return an object representing this transformation for a given
   * allocation site
   *
   * @param inst the allocation site
   * @param ir
   * @return the object, or null if illegal
   */
  public static ObjectReplacer getReplacer(Instruction inst, IR ir) {
    Register r = New.getResult(inst).getRegister();
    RVMClass klass = New.getType(inst).getVMType().asClass();
    // TODO :handle these cases
    if (klass.hasFinalizer() || containsUnsupportedUse(ir, r, klass, null)) {
      return null;
    }
    return new ObjectReplacer(r, klass, ir);
  }

  @Override
  public void transform() {
    // store the object's fields in a ArrayList
    ArrayList<RVMField> fields = getFieldsAsArrayList(klass);
    // create a scalar for each field. initialize the scalar to
    // default values before the object's def
    RegisterOperand[] scalars = new RegisterOperand[fields.size()];
    RegisterOperand def = reg.defList;
    Instruction defI = def.instruction;
    for (int i = 0; i < fields.size(); i++) {
      RVMField f = fields.get(i);
      Operand defaultValue = IRTools.getDefaultOperand(f.getType());
      scalars[i] = IRTools.moveIntoRegister(ir.regpool, defI, defaultValue);
      scalars[i].setType(f.getType());
    }
    transform2(this.reg, defI, scalars, fields, null);
  }

  private void transform2(Register reg, Instruction defI, RegisterOperand[] scalars, ArrayList<RVMField> fields, Set<Register> visited) {
    final boolean DEBUG = false;

    // now remove the def
    if (DEBUG) {
      System.out.println("Removing " + defI);
    }
    DefUse.removeInstructionAndUpdateDU(defI);
    // now handle the uses
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      scalarReplace(use, scalars, fields, visited);
    }
  }

  /**
   * Returns a ArrayList<RVMField>, holding the fields of the object
   * @param klass the type of the object
   */
  private static ArrayList<RVMField> getFieldsAsArrayList(RVMClass klass) {
    ArrayList<RVMField> v = new ArrayList<RVMField>();
    for (RVMField field : klass.getInstanceFields()) {
      v.add(field);
    }
    return v;
  }

  /**
   * @param r the register holding the object reference
   * @param _klass the type of the object to replace
   * @param i the IR
   */
  private ObjectReplacer(Register r, RVMClass _klass, IR i) {
    reg = r;
    klass = _klass;
    ir = i;
  }

  /**
   * Replace a given use of a object with its scalar equivalent
   *
   * @param use the use to replace
   * @param scalars an array of scalar register operands to replace
   *                  the object's fields with
   */
  private void scalarReplace(RegisterOperand use, RegisterOperand[] scalars, ArrayList<RVMField> fields, Set<Register> visited) {
    Instruction inst = use.instruction;
    try{
      switch (inst.getOpcode()) {
      case PUTFIELD_opcode: {
        FieldReference fr = PutField.getLocation(inst).getFieldRef();
        if (VM.VerifyAssertions) VM._assert(fr.isResolved());
        RVMField f = fr.peekResolvedField();
        int index = fields.indexOf(f);
        TypeReference type = scalars[index].getType();
        Operator moveOp = IRTools.getMoveOp(type);
        Instruction i = Move.create(moveOp, scalars[index].copyRO(), PutField.getClearValue(inst));
        inst.insertBefore(i);
        DefUse.removeInstructionAndUpdateDU(inst);
        DefUse.updateDUForNewInstruction(i);
      }
      break;
      case GETFIELD_opcode: {
        FieldReference fr = GetField.getLocation(inst).getFieldRef();
        if (VM.VerifyAssertions) VM._assert(fr.isResolved());
        RVMField f = fr.peekResolvedField();
        int index = fields.indexOf(f);
        TypeReference type = scalars[index].getType();
        Operator moveOp = IRTools.getMoveOp(type);
        Instruction i = Move.create(moveOp, GetField.getClearResult(inst), scalars[index].copyRO());
        inst.insertBefore(i);
        DefUse.removeInstructionAndUpdateDU(inst);
        DefUse.updateDUForNewInstruction(i);
      }
      break;
      case MONITORENTER_opcode:
        inst.insertBefore(Empty.create(READ_CEILING));
        DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case MONITOREXIT_opcode:
        inst.insertBefore(Empty.create(WRITE_FLOOR));
        DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case CALL_opcode:
      case NULL_CHECK_opcode:
        // (SJF) TODO: Why wasn't this caught by BC2IR for
        //      java.lang.Double.<init> (Ljava/lang/String;)V ?
        DefUse.removeInstructionAndUpdateDU(inst);
        break;
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode: {
        // We cannot handle removing the checkcast if the result of the
        // checkcast test is unknown
        TypeReference lhsType = TypeCheck.getType(inst).getTypeRef();
        if (ClassLoaderProxy.includesType(lhsType, klass.getTypeRef()) == OptConstants.YES) {
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          Register copy = TypeCheck.getResult(inst).getRegister();
          if(!visited.contains(copy)) {
            visited.add(copy);
            transform2(copy, inst, scalars, fields, visited);
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
        if (ClassLoaderProxy.includesType(lhsType, klass.getTypeRef()) == OptConstants.YES) {
          i2 = Move.create(INT_MOVE, InstanceOf.getClearResult(inst), IC(1));
        } else {
          i2 = Move.create(INT_MOVE, InstanceOf.getClearResult(inst), IC(0));
        }
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case GET_OBJ_TIB_opcode: {
        Instruction i2 = Move.create(REF_MOVE, GuardedUnary.getClearResult(inst), new TIBConstantOperand(klass));
        DefUse.replaceInstructionAndUpdateDU(inst, i2);
      }
      break;
      case REF_MOVE_opcode: {
        if (visited == null) {
          visited = new HashSet<Register>();
        }
        Register copy = Move.getResult(use.instruction).getRegister();
        if(!visited.contains(copy)) {
          visited.add(copy);
          transform2(copy, inst, scalars, fields, visited);
          // NB will remove inst
        } else {
          DefUse.removeInstructionAndUpdateDU(inst);
        }
      }
      break;
      default:
        throw new OptimizingCompilerException("ObjectReplacer: unexpected use " + inst);
      }
    } catch (Exception e) {
      OptimizingCompilerException oe = new OptimizingCompilerException("Error handling use ("+ use +") of: "+ inst);
      oe.initCause(e);
      throw oe;
    }
  }

  /**
   * Some cases we don't handle yet. TODO: handle them.
   */
  private static boolean containsUnsupportedUse(IR ir, Register reg, RVMClass klass, Set<Register> visited) {
    for (RegisterOperand use = reg.useList; use != null; use = use.getNext()) {
      switch (use.instruction.getOpcode()) {
        case MUST_IMPLEMENT_INTERFACE_opcode:
        case REF_IFCMP_opcode:
          return true;
        case CHECKCAST_opcode:
        case CHECKCAST_NOTNULL_opcode:
        case CHECKCAST_UNRESOLVED_opcode: {
          // We cannot handle removing the checkcast if the result of the
          // checkcast test is unknown
          TypeReference lhsType = TypeCheck.getType(use.instruction).getTypeRef();
          byte ans = ClassLoaderProxy.includesType(lhsType, klass.getTypeRef());
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
              if(containsUnsupportedUse(ir, copy, klass, visited)) {
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
          if (ClassLoaderProxy.includesType(lhsType, klass.getTypeRef()) == OptConstants.MAYBE) {
            return true;
          }
        }
        break;
        case REF_MOVE_opcode:
          if (visited == null) {
            visited = new HashSet<Register>();
          }
          Register copy = Move.getResult(use.instruction).getRegister();
          if(!visited.contains(copy)) {
            visited.add(copy);
            if(containsUnsupportedUse(ir, copy, klass, visited)) {
              return true;
            }
          }
          break;
        case BOOLEAN_CMP_INT_opcode:
        case BOOLEAN_CMP_ADDR_opcode:
        case LONG_STORE_opcode:
          throw new OptimizingCompilerException("Unexpected use of reference considered for replacement: " + use.instruction + " in " + ir.method);
      }
    }
    return false;
  }
}
