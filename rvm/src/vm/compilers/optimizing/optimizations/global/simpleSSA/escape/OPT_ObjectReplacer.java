/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * Class that performs scalar replacement of aggregates for non-array
 * objects
 *
 * @author Stephen Fink
 *
 */
public class OPT_ObjectReplacer
    implements OPT_Operators, OPT_AggregateReplacer {
  final static boolean DEBUG = false;

  /** 
   * Return an object representing this transformation for a given
   * allocation site
   *
   * @param inst the allocation site
   * @param ir 
   * @return the object, or null if illegal
   */
  public static OPT_ObjectReplacer getReplacer (OPT_Instruction inst, 
      OPT_IR ir) {
    OPT_Register r = New.getResult(inst).register;
    // TODO :handle these cases
    if (containsUnsupportedUse(ir, r))
      return  null;
    VM_Class klass = New.getType(inst).getVMType().asClass();
    return  new OPT_ObjectReplacer(r, klass, ir);
  }

  /** 
   * Perform the transformation
   */
  public void transform () {
    // store the object's fields in a ArrayList
    fields = getFieldsAsArrayList(klass);
    // create a scalar for each field. initialize the scalar to 
    // default values before the object's def
    OPT_RegisterOperand[] scalars = new OPT_RegisterOperand[fields.size()];
    OPT_RegisterOperand def = reg.defList;
    OPT_Instruction defI = def.instruction;
    for (int i = 0; i < fields.size(); i++) {
      VM_Field f = (VM_Field)fields.get(i);
      OPT_Operand defaultValue = OPT_IRTools.getDefaultOperand(f.getType());
      scalars[i] = OPT_IRTools.moveIntoRegister(ir.regpool, defI, defaultValue);
      scalars[i].type = f.getType();
    }
    // now remove the def
    if (DEBUG)
      System.out.println("Removing " + defI);
    OPT_DefUse.removeInstructionAndUpdateDU(defI);
    // now handle the uses
    for (OPT_RegisterOperand use = reg.useList; use != null; 
         use = (OPT_RegisterOperand)use.getNext()) {
      scalarReplace(use, scalars);
    }
  }

  /**
   * type of the object
   */
  private VM_Class klass;       
  /**
   * the IR
   */
  private OPT_IR ir;            
  /**
   * the register holding the object reference
   */
  private OPT_Register reg;     
  /**
   * the fields of the object
   */
  private ArrayList fields;        

  /** 
   * Returns a ArrayList<VM_Field>, holding the fields of the object
   * @param klass the type of the object
   */
  private static ArrayList getFieldsAsArrayList (VM_Class klass) {
    VM_Field[] f = klass.getInstanceFields();
    ArrayList v = new ArrayList();
    for (int i = 0; i < f.length; i++) {
      v.add(f[i]);
    }
    return  v;
  }

  /** 
   * @param r the register holding the object reference
   * @param _klass the type of the object to replace
   * @param i the IR
   */
  private OPT_ObjectReplacer (OPT_Register r, VM_Class _klass, OPT_IR i) {
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
  private void scalarReplace (OPT_RegisterOperand use, 
                              OPT_RegisterOperand[] scalars) {
    OPT_Instruction inst = use.instruction;
    switch (inst.getOpcode()) {
      case PUTFIELD_opcode:
        {
          VM_FieldReference fr = PutField.getLocation(inst).getFieldRef();
          if (VM.VerifyAssertions) VM._assert(fr.isResolved());
          VM_Field f = fr.peekResolvedField();
          int index = fields.indexOf(f);
          VM_TypeReference type = scalars[index].type;
          OPT_Operator moveOp = OPT_IRTools.getMoveOp(type);
          OPT_Instruction i = Move.create(moveOp, scalars[index], 
              PutField.getValue(inst));
          inst.insertBefore(i);
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
          OPT_DefUse.updateDUForNewInstruction(i);
        }
        break;
      case GETFIELD_opcode:
        {
          VM_FieldReference fr = GetField.getLocation(inst).getFieldRef();
          if (VM.VerifyAssertions) VM._assert(fr.isResolved());
          VM_Field f = fr.peekResolvedField();
          int index = fields.indexOf(f);
          VM_TypeReference type = scalars[index].type;
          OPT_Operator moveOp = OPT_IRTools.getMoveOp(type);
          OPT_Instruction i = Move.create(moveOp, GetField.getClearResult(inst), 
              scalars[index]);
          inst.insertBefore(i);
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
          OPT_DefUse.updateDUForNewInstruction(i);
        }
        break;
      case MONITORENTER_opcode:
        if (ir.options.NO_CACHE_FLUSH)
          OPT_DefUse.removeInstructionAndUpdateDU(inst); 
        else {
          inst.insertBefore(Empty.create(READ_CEILING));
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
        }
        break;
      case MONITOREXIT_opcode:
        if (ir.options.NO_CACHE_FLUSH)
          OPT_DefUse.removeInstructionAndUpdateDU(inst); 
        else {
          inst.insertBefore(Empty.create(WRITE_FLOOR));
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
        }
        break;
      case NULL_CHECK_opcode:
        // (SJF) TODO: Why wasn't this caught by BC2IR for
        //      java.lang.Double.<init> (Ljava/lang/String;)V ?
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      default:
        throw  new OPT_OptimizingCompilerException(
            "OPT_ObjectReplacer: unexpected use "
            + inst);
    }
  }

  /**
   * Some cases we don't handle yet. TODO: handle them.
   */
  private static boolean containsUnsupportedUse (OPT_IR ir, OPT_Register reg) {
    for (OPT_RegisterOperand use = reg.useList; use != null; 
        use = (OPT_RegisterOperand)use.getNext()) {
      switch (use.instruction.getOpcode()) {
        case CHECKCAST_opcode:
        case CHECKCAST_UNRESOLVED_opcode:
        case MUST_IMPLEMENT_INTERFACE_opcode:
        case CHECKCAST_NOTNULL_opcode:case GET_OBJ_TIB_opcode:
        case INSTANCEOF_opcode:case INSTANCEOF_NOTNULL_opcode:
        case INSTANCEOF_UNRESOLVED_opcode:
        case REF_IFCMP_opcode:case BOOLEAN_CMP_INT_opcode:case BOOLEAN_CMP_ADDR_opcode:
          return  true;
      }
    }
    return  false;
  }
}



