/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 *
 * @author Dave Grove, Mauricio J. Serrano, Martin Trapp
 */
abstract class OPT_NormalizeConstants extends OPT_IRTools {
  /**
   * lower bound on int immediate values in
   * an instruction (can use values down
   * to and including this immed)
   */
  static final int LOWER_IMMEDIATE = -(1 << 15);
  /** upper bound on int immediate values in
   * an instruction (can use values up
   * to and including this immed)
   */
  static final int UPPER_IMMEDIATE = (1 << 15) - 1;
  /** upper bound on unsigned int immediate values in
   * an instruction (can use values up
   * to and including this immed)
   * NOTE: used in INT_AND, INT_OR, INT_XOR
   */
  static final int UNSIGNED_UPPER_IMMEDIATE = (1 << 16) - 1;

  /**
   * Doit.
   *
   * @param ir IR to normalize
   */
  static void perform(OPT_IR ir) {

    // This code assumes that INT/LONG constant folding in OPT_Simplifier is enabled.
    // This greatly reduces the number of cases we have to worry about below.
    if (!(OPT_Simplifier.CF_INT && OPT_Simplifier.CF_LONG)) {
      throw  new OPT_OptimizingCompilerException("Unexpected config!");
    }
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); 
         s != null; 
         s = s.nextInstructionInCodeOrder()) {

      // STEP ONE: Get 'large' constants into a form that the PPC BURS rules
      //           are prepared to deal with. 
      // Constants can't appear as defs, so only scan the uses.
      //
      int numUses = s.getNumberOfUses();
      if (numUses > 0) {
        int numDefs = s.getNumberOfDefs();
        for (int idx = numDefs; idx < numUses + numDefs; idx++) {
          OPT_Operand use = s.getOperand(idx);
          if (use != null) {
            if (use instanceof OPT_StringConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.JavaLangString);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_StringConstantOperand sc = (OPT_StringConstantOperand)use;
              int offset = sc.index << 2;
              if (offset == 0)
                throw  new OPT_OptimizingCompilerException("String constant w/o valid JTOC offset");
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(INT_LOAD, rop, jtoc, asImmediateOrReg(I(offset), s, ir), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_LongConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Long);
              use.clear();
              s.insertBefore(Move.create(LONG_MOVE, rop, use));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_DoubleConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Double);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)use;
              int index = dc.index;
              if (index == 0) {
                index = VM_Statics.findOrCreateDoubleLiteral(Double.doubleToLongBits(dc.value));
              }
              int offset = index << 2;
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(DOUBLE_LOAD, rop, jtoc, asImmediateOrReg(I(offset), s, ir), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_FloatConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Float);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)use;
              int index = fc.index;
              if (index == 0) {
                index = VM_Statics.findOrCreateFloatLiteral(Float.floatToIntBits(fc.value));
              }
              int offset = index << 2;
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(FLOAT_LOAD, rop, jtoc, asImmediateOrReg(I(offset), s, ir), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_NullConstantOperand) {
              s.putOperand(idx, I(0));
            } else if (use instanceof OPT_AddressConstantOperand) {
              s.putOperand(idx, I(((OPT_AddressConstantOperand)use).value.toInt()));
            }
          }
        }
      }

      // Calling OPT_Simplifier.simplify ensures that the instruction is 
      // in normalized form. This reduces the number of cases we have to 
      // worry about (and does last minute constant folding on the off chance
      // we've missed an opportunity...)
      OPT_Simplifier.simplify(s);
      
      switch (s.getOpcode()) {
        //////////
        // LOAD/STORE
        //////////
      case REF_STORE_opcode:
        s.operator = INT_STORE;
        // fallthrough!
      case BYTE_STORE_opcode:case SHORT_STORE_opcode:case INT_STORE_opcode:
        // On PowerPC, the value being stored must be in a register
        Store.setValue(s, asReg(Store.getClearValue(s), s, ir));
        // Supported addressing modes are quite limited.
        Store.setAddress(s, asReg(Store.getClearAddress(s), s, ir));
        Store.setOffset(s, asImmediateOrReg(Store.getClearOffset(s), s, ir));
      break;

      case ATTEMPT_opcode:
        // On PowerPC, the value being stored must be in a register
        Attempt.setNewValue(s, asReg(Attempt.getClearNewValue(s), s, ir));
        Attempt.setOldValue(s, null);       // not used on powerpc.
        // Supported addressing modes are quite limited.
        Attempt.setAddress(s, asReg(Attempt.getClearAddress(s),s,ir));
        Attempt.setOffset(s, asReg(Attempt.getClearOffset(s),s,ir));
        break;

      case LONG_STORE_opcode:case FLOAT_STORE_opcode:case DOUBLE_STORE_opcode:
        // Supported addressing modes are quite limited.
        Store.setAddress(s, asImmediateOrReg(Store.getClearAddress(s), s, ir));
        Store.setOffset(s, asImmediateOrReg(Store.getClearOffset(s), s, ir));
        break;

      case REF_LOAD_opcode:
        s.operator = INT_LOAD;
        // fallthrough!
      case BYTE_LOAD_opcode:case UBYTE_LOAD_opcode:
      case SHORT_LOAD_opcode:case USHORT_LOAD_opcode:case INT_LOAD_opcode:
      case LONG_LOAD_opcode:case FLOAT_LOAD_opcode:case DOUBLE_LOAD_opcode:
        // Supported addressing modes are quite limited.
        Load.setAddress(s, asReg(Load.getClearAddress(s), s, ir));
        Load.setOffset(s, asImmediateOrReg(Load.getClearOffset(s), s, ir));
        break;

      case PREPARE_opcode:
        // Supported addressing modes are quite limited.
        Prepare.setAddress(s, asReg(Prepare.getAddress(s), s, ir));
        Prepare.setOffset(s, asReg(Prepare.getOffset(s), s, ir));
        break;

      case REF_MOVE_opcode:
        s.operator = INT_MOVE;
        break;

      case REF_COND_MOVE_opcode:
        s.operator = INT_COND_MOVE;
        break;
        
        //////////
        // INT ALU OPS
        //////////
        // There are some instructions for which LIR2MIR.rules doesn't
        // seem to expect constant operands at all. 
      case INT_REM_opcode:case INT_DIV_opcode:
        GuardedBinary.setVal1(s, asReg(GuardedBinary.getClearVal1(s), s, ir));
        GuardedBinary.setVal2(s, asReg(GuardedBinary.getClearVal2(s), s, ir));
        break;

      case REF_IFCMP_opcode:
        s.operator = INT_IFCMP;
        // fallthrough!
      case INT_IFCMP_opcode:
        // val1 can't be a constant, val2 must be small enough.
        IfCmp.setVal1(s, asReg(IfCmp.getClearVal1(s), s, ir));
        IfCmp.setVal2(s, asImmediateOrReg(IfCmp.getClearVal2(s), s, ir));
        break;

      case INT_IFCMP2_opcode:
        // val1 can't be a constant, val2 must be small enough.
        IfCmp2.setVal1(s, asReg(IfCmp2.getClearVal1(s), s, ir));
        IfCmp2.setVal2(s, asImmediateOrReg(IfCmp2.getClearVal2(s), s, ir));
        break;

      case BOOLEAN_CMP_opcode:
        // val2 must be small enough.
        BooleanCmp.setVal2(s, asImmediateOrReg(BooleanCmp.getClearVal2(s),s,ir));
        break;

      case INT_SUB_opcode:
        // val1 can't be a constant
        Binary.setVal1(s, asReg(Binary.getClearVal1(s), s, ir));
        // val2 isn't be constant (if it were, OPT_Simplifier would have
        // converted this into an ADD of -Val2).
        break;

      case INT_SHL_opcode:case INT_SHR_opcode:case INT_USHR_opcode:
        // Val2 could be a constant, but Val1 apparently can't be.
        Binary.setVal1(s, asReg(Binary.getClearVal1(s), s, ir));
        break;

      // There are other instructions for which LIR2MIR.rules may not
      // handle constant operands that won't fit in the immediate field.
      // TODO: Some of these actually might be ok, but for now we'll expand them all. 
      case INT_ADD_opcode:case INT_MUL_opcode:
        Binary.setVal2(s, asImmediateOrReg(Binary.getVal2(s), s, ir));
        break;

      case INT_AND_opcode:case INT_OR_opcode:case INT_XOR_opcode:
        {
          OPT_Operand val = Binary.getVal2(s);
          if (val instanceof OPT_IntConstantOperand) {
            OPT_IntConstantOperand ival = (OPT_IntConstantOperand)val;
            if ((ival.value < 0) || (ival.value > UNSIGNED_UPPER_IMMEDIATE)) {
              val.instruction = null;
              OPT_RegisterOperand rop = ir.regpool.makeTempInt();
              s.insertBefore(Move.create(INT_MOVE, rop, val));
              Binary.setVal2(s, rop.copyD2U());
            }
          }
        }
      break;

      // Deal with OPT_Simplifier.CF_FLOAT or OPT_Simplifier.CF_DOUBLE being false
      case INT_2DOUBLE_opcode:case INT_2FLOAT_opcode:
      case INT_BITS_AS_FLOAT_opcode:
        Unary.setVal(s, asReg(Unary.getVal(s), s, ir));
        break;

      case NULL_CHECK_opcode:
        NullCheck.setRef(s, asReg(NullCheck.getClearRef(s), s, ir));
        break;

      // Force all call parameters to be in registers
      case CALL_opcode:
        {
          int numArgs = Call.getNumberOfParams(s);
          for (int i = 0; i < numArgs; i++) {
            Call.setParam(s, i, asReg(Call.getClearParam(s, i), s, ir));
          }
        }
        break;

      case RETURN_opcode:
        if (Return.hasVal(s)) {
          Return.setVal(s, asReg(Return.getClearVal(s), s, ir));
        }
        break;
      } 
    }
  }

  public static boolean canBeImmediate(int val) {
    return (val >= LOWER_IMMEDIATE) && (val <= UPPER_IMMEDIATE);
  }

  static OPT_Operand asImmediateOrReg(OPT_Operand addr, 
                                      OPT_Instruction s, 
                                      OPT_IR ir) {
    if (addr instanceof OPT_IntConstantOperand) {
      if (!canBeImmediate(((OPT_IntConstantOperand)addr).value)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempInt();
        s.insertBefore(Move.create(INT_MOVE, rop, addr));
        return rop.copyD2U();
      }
    }
    // Operand was OK as is.
    return addr;
  }

  /**
   * Force addr to be a register operand
   * @param addr
   * @param s
   * @param ir
   */
  static OPT_Operand asReg(OPT_Operand addr,
                           OPT_Instruction s, 
                           OPT_IR ir) {
    if (addr instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempInt();
      s.insertBefore(Move.create(INT_MOVE, rop, addr));
      return rop.copyD2U();
    }
    // Operand was OK as is.
    return addr;
  }

  
  /**
   * Replace LongConstant uses by materializeConstants
   * @param inst
   * @param ir
   */
  static void exterminateLongConstants (OPT_Instruction s, OPT_IR ir) {
    
    int numUses = s.getNumberOfUses();
    if (numUses > 0) {
      int numDefs = s.getNumberOfDefs();
      for (int idx = numDefs; idx < numUses + numDefs; idx++) {
        OPT_Operand use = s.getOperand(idx);
        if (use != null) {
          if (use instanceof OPT_LongConstantOperand) {
            OPT_RegisterOperand rop = ir.regpool.makeTempLong();
            use.clear();
            s.insertBack(Move.create(LONG_MOVE, rop, use));
            s.putOperand(idx, rop.copyD2U());
          }
        }
      }
    }
  }
}

