/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import org.vmmagic.unboxed.*;
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

    // This code assumes that INT/LONG/ADDR constant folding in OPT_Simplifier is enabled.
    // This greatly reduces the number of cases we have to worry about below.
    if (!(OPT_Simplifier.CF_INT && OPT_Simplifier.CF_LONG && OPT_Simplifier.CF_ADDR)) {
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
              Offset offset = Offset.fromIntZeroExtend(sc.index << LOG_BYTES_IN_INT);
              if (offset.isZero())
                throw  new OPT_OptimizingCompilerException("String constant w/o valid JTOC offset");
              OPT_LocationOperand loc = new OPT_LocationOperand(offset.toInt());
              s.insertBefore(Load.create(VM.BuildFor32Addr? INT_LOAD: LONG_LOAD, rop, jtoc, asImmediateOrRegOffset(AC(offset), s, ir, true), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_DoubleConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Double);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand)use;
              int index = dc.index;
              if (index == 0) {
                index = VM_Statics.findOrCreateDoubleLiteral(Double.doubleToLongBits(dc.value));
              }
              Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_INT);
              OPT_LocationOperand loc = new OPT_LocationOperand(offset.toInt());
              s.insertBefore(Load.create(DOUBLE_LOAD, rop, jtoc, asImmediateOrRegOffset(AC(offset), s, ir, true), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_FloatConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Float);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir,s);
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand)use;
              int index = fc.index;
              if (index == 0) {
                index = VM_Statics.findOrCreateFloatLiteral(Float.floatToIntBits(fc.value));
              }
              Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_INT);
              OPT_LocationOperand loc = new OPT_LocationOperand(offset.toInt());
              s.insertBefore(Load.create(FLOAT_LOAD, rop, jtoc, asImmediateOrRegOffset(AC(offset), s, ir, true), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_LongConstantOperand) {
              if ((!VM.BuildFor64Addr)){
                OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Long);
                use.clear();
                s.insertBefore(Move.create(LONG_MOVE, rop, use));
                s.putOperand(idx, rop.copyD2U());
              }
            } else if (use instanceof OPT_NullConstantOperand) {
                s.putOperand(idx, AC(Address.zero()));
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
        s.operator = VM.BuildFor32Addr?INT_STORE : LONG_STORE;
        // fallthrough!
      case BYTE_STORE_opcode:case SHORT_STORE_opcode:case INT_STORE_opcode:
      case LONG_STORE_opcode:
        // On PowerPC, the value being stored must be in a register
        Store.setValue(s, asRegPolymorphic(Store.getClearValue(s), s, ir));
        // Supported addressing modes are quite limited.
        Store.setAddress(s, asRegAddress(Store.getClearAddress(s), s, ir));
        Store.setOffset(s, asImmediateOrRegOffset(Store.getClearOffset(s), s, ir, true));
      break;
      
      case FLOAT_STORE_opcode:case DOUBLE_STORE_opcode:
        // Supported addressing modes are quite limited.
        Store.setAddress(s, asRegAddress(Store.getClearAddress(s), s, ir));
        Store.setOffset(s, asImmediateOrRegOffset(Store.getClearOffset(s), s, ir, true));
        break;

      case REF_LOAD_opcode:
        s.operator = VM.BuildFor32Addr?INT_LOAD : LONG_LOAD;
        // fallthrough!
      case BYTE_LOAD_opcode:case UBYTE_LOAD_opcode:
      case SHORT_LOAD_opcode:case USHORT_LOAD_opcode:case INT_LOAD_opcode:
      case LONG_LOAD_opcode:case FLOAT_LOAD_opcode:case DOUBLE_LOAD_opcode:
        // Supported addressing modes are quite limited.
        Load.setAddress(s, asRegAddress(Load.getClearAddress(s), s, ir));
        Load.setOffset(s, asImmediateOrRegOffset(Load.getClearOffset(s), s, ir, true));
        break;

      case ATTEMPT_INT_opcode:
      case ATTEMPT_ADDR_opcode:
        // On PowerPC, the value being stored must be in a register
        Attempt.setNewValue(s, asRegPolymorphic(Attempt.getClearNewValue(s), s, ir));
        Attempt.setOldValue(s, null);       // not used on powerpc.
        // Supported addressing modes are quite limited.
        Attempt.setAddress(s, asRegAddress(Attempt.getClearAddress(s),s,ir));
        Attempt.setOffset(s, asRegOffset(Attempt.getClearOffset(s),s,ir));
        break;

      case PREPARE_INT_opcode:
      case PREPARE_ADDR_opcode:
        // Supported addressing modes are quite limited.
        Prepare.setAddress(s, asRegAddress(Prepare.getAddress(s), s, ir));
        Prepare.setOffset(s, asRegOffset(Prepare.getOffset(s), s, ir));
        break;

      //-#if RVM_FOR_64_ADDR
      case LONG_MOVE_opcode:
        // fallthrough!
      //-#endif
      case INT_MOVE_opcode:
        s.operator = REF_MOVE;
        break;

      case REF_COND_MOVE_opcode:
        s.operator = VM.BuildFor32Addr?INT_COND_MOVE : LONG_COND_MOVE;
        break;
        
      case REF_IFCMP_opcode:
        s.operator = VM.BuildFor32Addr? INT_IFCMP : LONG_IFCMP;
        // fallthrough!
      //-#if RVM_FOR_64_ADDR
      case LONG_IFCMP_opcode:
        // fallthrough!
      //-#endif
      case INT_IFCMP_opcode:
        // val1 can't be a constant, val2 must be small enough.
        IfCmp.setVal1(s, asRegPolymorphic(IfCmp.getClearVal1(s), s, ir));
        IfCmp.setVal2(s, asImmediateOrRegPolymorphic(IfCmp.getClearVal2(s), s, ir, true));
        break;

      case INT_IFCMP2_opcode:
        // val1 can't be a constant, val2 must be small enough.
        IfCmp2.setVal1(s, asRegInt(IfCmp2.getClearVal1(s), s, ir));
        IfCmp2.setVal2(s, asImmediateOrRegInt(IfCmp2.getClearVal2(s), s, ir, true));
        break;

      case BOOLEAN_CMP_INT_opcode:
      case BOOLEAN_CMP_ADDR_opcode:
        // val2 must be small enough.
        BooleanCmp.setVal2(s, asImmediateOrRegPolymorphic(BooleanCmp.getClearVal2(s),s,ir, true));
        break;

        //////////
        // INT ALU OPS
        //////////
      //-#if RVM_FOR_64_ADDR
      case LONG_ADD_opcode:
        // fallthrough!
      //-#endif
      case INT_ADD_opcode:
        s.operator = REF_ADD;
        // fallthrough!
      case REF_ADD_opcode:
        Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getVal2(s), s, ir, true));
        break;
      
      //-#if RVM_FOR_64_ADDR
      case LONG_SUB_opcode:
        // fallthrough!
      //-#endif
      case INT_SUB_opcode:
        s.operator = REF_SUB;
        // fallthrough!
      case REF_SUB_opcode:
        Binary.setVal1(s, asImmediateOrRegPolymorphic(Binary.getClearVal1(s), s, ir, true));
        // val2 isn't be constant (if it were, OPT_Simplifier would have
        // converted this into an ADD of -Val2).
        break;

      case INT_MUL_opcode:
        Binary.setVal2(s, asImmediateOrRegInt(Binary.getVal2(s), s, ir, true));
        break;

      //-#if RVM_FOR_64_ADDR
      case LONG_MUL_opcode:
        Binary.setVal2(s, asImmediateOrRegLong(Binary.getVal2(s), s, ir, true));
        break;
      //-#endif

        // There are some instructions for which LIR2MIR.rules doesn't
        // seem to expect constant operands at all. 
      case INT_REM_opcode:case INT_DIV_opcode:
        GuardedBinary.setVal1(s, asRegInt(GuardedBinary.getClearVal1(s), s, ir));
        GuardedBinary.setVal2(s, asRegInt(GuardedBinary.getClearVal2(s), s, ir));
        break;

      //-#if RVM_FOR_64_ADDR
      case LONG_REM_opcode:case LONG_DIV_opcode:
        GuardedBinary.setVal1(s, asRegLong(GuardedBinary.getClearVal1(s), s, ir));
        GuardedBinary.setVal2(s, asRegLong(GuardedBinary.getClearVal2(s), s, ir));
        break;
      //-#endif

      //-#if RVM_FOR_64_ADDR
      case LONG_NEG_opcode:
        // fallthrough!
      //-#endif
      case INT_NEG_opcode:
        s.operator = REF_NEG;
        break;

      //-#if RVM_FOR_64_ADDR
      case LONG_NOT_opcode:
        // fallthrough!
      //-#endif
      case INT_NOT_opcode:
       s.operator = REF_NOT;
       break;
      
      //-#if RVM_FOR_64_ADDR
      case LONG_AND_opcode:
        // fallthrough!
      //-#endif
      case INT_AND_opcode:
        s.operator = REF_AND;
        // fallthrough!
      case REF_AND_opcode:
        Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
      break;
      
      //-#if RVM_FOR_64_ADDR
      case LONG_OR_opcode:
        // fallthrough!
      //-#endif
      case INT_OR_opcode:
        s.operator = REF_OR;
        // fallthrough!
      case REF_OR_opcode:
        Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
      break;
      
      //-#if RVM_FOR_64_ADDR
      case LONG_XOR_opcode:
        // fallthrough!
      //-#endif
      case INT_XOR_opcode:
        s.operator = REF_XOR;
        // fallthrough!
      case REF_XOR_opcode:
        Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
      break;

      case REF_SHL_opcode:
        s.operator = (VM.BuildFor32Addr? INT_SHL : LONG_SHL);
        // fallthrough!
      //-#if RVM_FOR_64_ADDR
      case LONG_SHL_opcode:
        // fallthrough!
      //-#endif
      case INT_SHL_opcode:
        // Val2 could be a constant, but Val1 apparently can't be.
        Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
        break;

      case REF_SHR_opcode:
        s.operator = (VM.BuildFor32Addr? INT_SHR : LONG_SHR);
        // fallthrough!
      //-#if RVM_FOR_64_ADDR
      case LONG_SHR_opcode:
        // fallthrough!
      //-#endif
      case INT_SHR_opcode:
        // Val2 could be a constant, but Val1 apparently can't be.
        Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
        break;

      case REF_USHR_opcode:
        s.operator = (VM.BuildFor32Addr? INT_USHR : LONG_USHR);
        // fallthrough!
      //-#if RVM_FOR_64_ADDR
      case LONG_USHR_opcode:
        // fallthrough!
      //-#endif
      case INT_USHR_opcode:
        // Val2 could be a constant, but Val1 apparently can't be.
        Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
        break;

      // Deal with OPT_Simplifier.CF_FLOAT or OPT_Simplifier.CF_DOUBLE being false
      case INT_2DOUBLE_opcode:case INT_2FLOAT_opcode:
      case INT_BITS_AS_FLOAT_opcode:
        Unary.setVal(s, asRegInt(Unary.getVal(s), s, ir));
        break;

      case ADDR_2INT_opcode:
        s.operator = (VM.BuildFor32Addr? REF_MOVE : LONG_2INT);
        break;
                case ADDR_2LONG_opcode:
        s.operator = (VM.BuildFor32Addr? INT_2LONG : REF_MOVE);
        break;
                case INT_2ADDRSigExt_opcode:
        s.operator = (VM.BuildFor32Addr? REF_MOVE : INT_2LONG);
        break;
      //-#if RVM_FOR_32_ADDR
      case INT_2ADDRZerExt_opcode:
        s.operator = REF_MOVE;
        break;
      //-#endif
      //-#if RVM_FOR_64_ADDR
      case LONG_2ADDR_opcode: 
        s.operator = REF_MOVE;
        break;
      //-#endif

      case NULL_CHECK_opcode:
        NullCheck.setRef(s, asRegAddress(NullCheck.getClearRef(s), s, ir));
        break;

      // Force all call parameters to be in registers
      case SYSCALL_opcode:
      case CALL_opcode:
        {
          int numArgs = Call.getNumberOfParams(s);
          for (int i = 0; i < numArgs; i++) {
            Call.setParam(s, i, asRegPolymorphic(Call.getClearParam(s, i), s, ir));
          }
        }
        break;

      case RETURN_opcode:
        if (Return.hasVal(s)) {
          Return.setVal(s, asRegPolymorphic(Return.getClearVal(s), s, ir));
        }
        break;
      } 
    }
  }

  public static boolean canBeImmediate(int val, boolean signed) {
    if (signed) return (val >= LOWER_IMMEDIATE) && (val <= UPPER_IMMEDIATE);
    else return (val >= 0) && (val <= UNSIGNED_UPPER_IMMEDIATE);
  }

  public static boolean canBeImmediate(long val, boolean signed) {
    if (signed) return (val >= (long) LOWER_IMMEDIATE) && (val <= (long) UPPER_IMMEDIATE);
    else return (val >= 0L) && (val <= (long) UNSIGNED_UPPER_IMMEDIATE);
  }

  public static boolean canBeImmediate(Address val, boolean signed) {
    return (VM.BuildFor32Addr? canBeImmediate(val.toInt(), signed) : canBeImmediate(val.toLong(), signed));
  }

  static OPT_Operand asImmediateOrRegInt(OPT_Operand addr, 
                                      OPT_Instruction s, 
                                      OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_IntConstantOperand) {
      if (!canBeImmediate(((OPT_IntConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempInt();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      }
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegOffset(OPT_Operand addr, 
                                      OPT_Instruction s, 
                                      OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_AddressConstantOperand) {
      if (!canBeImmediate(((OPT_AddressConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempOffset();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand(((OPT_AddressConstantOperand)addr).value.toInt());
      }
    } 
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen, because is 64-bit unsafe
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegLong(OPT_Operand addr,
                                      OPT_Instruction s,
                                      OPT_IR ir, boolean signed) {
    if (VM.BuildFor64Addr && (addr instanceof OPT_LongConstantOperand)) {
      if (!canBeImmediate(((OPT_LongConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempLong();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand((int)((OPT_LongConstantOperand)addr).value);
      }
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegPolymorphic(OPT_Operand addr,
                                      OPT_Instruction s,
                                      OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_IntConstantOperand) {
      if (!canBeImmediate(((OPT_IntConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempInt();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      }
    } else if (addr instanceof OPT_AddressConstantOperand) {
      if (!canBeImmediate(((OPT_AddressConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempAddress();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand(((OPT_AddressConstantOperand)addr).value.toInt());
      }
    } else if (VM.BuildFor64Addr && (addr instanceof OPT_LongConstantOperand)) {
      if (!canBeImmediate(((OPT_LongConstantOperand)addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempLong();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand((int)((OPT_LongConstantOperand)addr).value);
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
  static OPT_Operand asRegInt(OPT_Operand addr,
                           OPT_Instruction s, 
                           OPT_IR ir) {
    if (addr instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempInt();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asRegLong(OPT_Operand addr,
                           OPT_Instruction s,
                           OPT_IR ir) {
    if (VM.BuildFor64Addr && (addr instanceof OPT_LongConstantOperand)) {
      OPT_RegisterOperand rop = ir.regpool.makeTempLong();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asRegAddress(OPT_Operand addr,
                           OPT_Instruction s,
                           OPT_IR ir) {
    if (addr instanceof OPT_AddressConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempAddress();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asRegOffset(OPT_Operand addr,
                           OPT_Instruction s,
                           OPT_IR ir) {
    if (addr instanceof OPT_AddressConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempOffset();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if (addr instanceof OPT_ConstantOperand) {
      VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asRegPolymorphic(OPT_Operand addr,
                           OPT_Instruction s, 
                           OPT_IR ir) {
    if (addr instanceof OPT_IntConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempInt();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if (addr instanceof OPT_AddressConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempAddress();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    else if ((VM.BuildFor64Addr) && (addr instanceof OPT_LongConstantOperand)) {
      OPT_RegisterOperand rop = ir.regpool.makeTempLong();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    // Operand was OK as is.
    return addr;
  }

}

