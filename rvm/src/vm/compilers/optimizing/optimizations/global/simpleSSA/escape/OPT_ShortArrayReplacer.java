/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Class that performs scalar replacement of short arrays
 *
 * @author Stephen Fink
 */
public class OPT_ShortArrayReplacer
    implements OPT_Operators, OPT_AggregateReplacer {
  final private static boolean DEBUG = false;

  /**
   * Arrays shorter than this length are candidates to be replaced by
   * scalar values.
   */
  public final static int SHORT_ARRAY_SIZE = 5;

  /** 
   * Return an object representing this transformation for a given
   * allocation site
   *
   * @param inst the allocation site
   * @param ir 
   * @return the object, or null if illegal
   */
  public static OPT_ShortArrayReplacer getReplacer (OPT_Instruction inst, 
      OPT_IR ir) {
    if (inst.operator != NEWARRAY)
      return  null;
    OPT_Operand size = NewArray.getSize(inst);
    if (!size.isIntConstant())
      return  null;
    int s = size.asIntConstant().value;
    if (s > SHORT_ARRAY_SIZE)
      return  null;
    if (s < 0)
      return  null;
    OPT_Register r = NewArray.getResult(inst).register;
    VM_Array a = NewArray.getType(inst).getVMType().asArray();
    // TODO :handle these cases
    if (containsUnsupportedUse(ir, r, s))
      return  null;
    return  new OPT_ShortArrayReplacer(r, a, s, ir);
  }

  /** 
   * Perform the transformation.
   */
  public void transform () {
    // first set up temporary scalars for the array elements
    // initialize them before the def.
    OPT_RegisterOperand scalars[] = new OPT_RegisterOperand[size];
    VM_Type elementType = vmArray.getElementType();
    OPT_RegisterOperand def = reg.defList;
    OPT_Instruction defI = def.instruction;
    OPT_Operand defaultValue = OPT_IRTools.getDefaultOperand(elementType.getTypeRef());
    for (int i = 0; i < size; i++) {
      scalars[i] = OPT_IRTools.moveIntoRegister(ir.regpool, defI, defaultValue);
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
   * number of elements in the array
   */
  private int size;            
  /**
   * type of the array
   */
  private VM_Array vmArray;     
  /**
   * the register holding the array reference
   */
  private OPT_Register reg;     
  /**
   * the governing IR
   */
  private OPT_IR ir;          

  /** 
   * @param r the register holding the array reference
   * @param a the type of the array to replace
   * @param s the size of the array to replace
   * @param i the IR
   */
  private OPT_ShortArrayReplacer (OPT_Register r, VM_Array a, int s, OPT_IR i) {
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
  private void scalarReplace (OPT_RegisterOperand use, 
      OPT_RegisterOperand[] scalars) {
    OPT_Instruction inst = use.instruction;
    VM_Type type = vmArray.getElementType();
    OPT_Operator moveOp = OPT_IRTools.getMoveOp(type.getTypeRef());
    switch (inst.getOpcode()) {
      case INT_ALOAD_opcode:case LONG_ALOAD_opcode:case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:case BYTE_ALOAD_opcode:case UBYTE_ALOAD_opcode:
      case USHORT_ALOAD_opcode:case SHORT_ALOAD_opcode:case REF_ALOAD_opcode:
        {
          int index = ALoad.getIndex(inst).asIntConstant().value;
          OPT_Instruction i = Move.create(moveOp, ALoad.getResult(inst), 
              scalars[index]);
          inst.insertBefore(i);
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
          OPT_DefUse.updateDUForNewInstruction(i);
        }
        break;
      case INT_ASTORE_opcode:case LONG_ASTORE_opcode:case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:case BYTE_ASTORE_opcode:case SHORT_ASTORE_opcode:
      case REF_ASTORE_opcode:
        {
          int index = AStore.getIndex(inst).asIntConstant().value;
          OPT_Instruction i2 = Move.create(moveOp, scalars[index], 
              AStore.getValue(inst));
          inst.insertBefore(i2);
          OPT_DefUse.removeInstructionAndUpdateDU(inst);
          OPT_DefUse.updateDUForNewInstruction(i2);
        }
        break;
      case BOUNDS_CHECK_opcode:
        OPT_DefUse.removeInstructionAndUpdateDU(inst);
        break;
      default:
        throw  new OPT_OptimizingCompilerException("Unexpected instruction: "
            + inst);
    }
  }

  /**
   * Some cases we don't handle yet. TODO: handle them.
   *
   * @param ir the governing IR
   * @param reg the register in question
   * @param size the size of the array to scalar replace.
   */
  private static boolean containsUnsupportedUse (OPT_IR ir, OPT_Register reg,
                                                 int size) {
    for (OPT_RegisterOperand use = reg.useList; use != null; 
         use = (OPT_RegisterOperand)use.getNext()) {
      switch (use.instruction.getOpcode()) {
        case NEWOBJMULTIARRAY_opcode:case OBJARRAY_STORE_CHECK_opcode:
        case OBJARRAY_STORE_CHECK_NOTNULL_opcode:
        case GET_OBJ_TIB_opcode:case NULL_CHECK_opcode:case INSTANCEOF_opcode:
        case INSTANCEOF_NOTNULL_opcode:
        case INSTANCEOF_UNRESOLVED_opcode:
          return  true;
        case INT_ASTORE_opcode:case LONG_ASTORE_opcode:
        case FLOAT_ASTORE_opcode:case DOUBLE_ASTORE_opcode:
        case BYTE_ASTORE_opcode:case SHORT_ASTORE_opcode:case REF_ASTORE_opcode:
          {
          if (!AStore.getIndex(use.instruction).isIntConstant())
            return  true;
          int index = AStore.getIndex(use.instruction).asIntConstant().value;
          // In the following case, we could instead unconditionally throw
          // an array index out-of-bounds exception.
          if (index >= size) return true;
          if (index < 0) return true;
          break;
          }
        case INT_ALOAD_opcode:case LONG_ALOAD_opcode:
        case FLOAT_ALOAD_opcode:case DOUBLE_ALOAD_opcode:
        case BYTE_ALOAD_opcode:case UBYTE_ALOAD_opcode:case USHORT_ALOAD_opcode:
        case SHORT_ALOAD_opcode:case REF_ALOAD_opcode:
          {
          if (!ALoad.getIndex(use.instruction).isIntConstant())
            return  true;
          int index = ALoad.getIndex(use.instruction).asIntConstant().value;
          // In the following case, we could instead unconditionally throw
          // an array index out-of-bounds exception.
          if (index >= size) return true;
          if (index < 0) return true;
          break;
          }
      }
    }
    return  false;
  }
}
