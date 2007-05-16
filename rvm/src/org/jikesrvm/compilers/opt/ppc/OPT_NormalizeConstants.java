/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.OPT_Simplifier;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.BooleanCmp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.IfCmp2;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_CodeConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_IRTools;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADDR_2INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ADDR_2LONG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ATTEMPT_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.ATTEMPT_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOOLEAN_CMP_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BYTE_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.CALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.DOUBLE_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.FLOAT_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRSigExt_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2ADDRZerExt_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2DOUBLE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_2LONG;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_BITS_AS_FLOAT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP2_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_USHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.INT_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_2INT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_CMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_COND_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_DIV_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_IFCMP;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_LOAD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_MUL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_NEG_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_NOT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_REM_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_STORE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.LONG_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.NULL_CHECK_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PREPARE_ADDR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.PREPARE_INT_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ADD;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_ADD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_AND;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_AND_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_COND_MOVE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_IFCMP_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_NEG;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_NOT;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_OR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_OR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SHL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SUB;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_SUB_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_USHR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_XOR;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.REF_XOR_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.RETURN_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SHORT_STORE_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.SYSCALL_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.TRAP_IF_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.UBYTE_LOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.USHORT_LOAD_opcode;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_StringConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.Prepare;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.Store;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 */
public abstract class OPT_NormalizeConstants extends OPT_IRTools {
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
  public static void perform(OPT_IR ir) {

    // This code assumes that INT/LONG/ADDR constant folding in OPT_Simplifier is enabled.
    // This greatly reduces the number of cases we have to worry about below.
    if (!(OPT_Simplifier.CF_INT && OPT_Simplifier.CF_LONG && OPT_Simplifier.CF_ADDR)) {
      throw new OPT_OptimizingCompilerException("Unexpected config!");
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
            if (use instanceof OPT_ObjectConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(use.getType());
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir, s);
              OPT_ObjectConstantOperand oc = (OPT_ObjectConstantOperand) use;
              Offset offset = oc.offset;
              if (offset.isZero()) {
                if (use instanceof OPT_StringConstantOperand) {
                  throw new OPT_OptimizingCompilerException("String constant w/o valid JTOC offset");
                } else if (use instanceof OPT_ClassConstantOperand) {
                  throw new OPT_OptimizingCompilerException("Class constant w/o valid JTOC offset");
                }
                offset = Offset.fromIntSignExtend(VM_Statics.findOrCreateObjectLiteral(oc.value));
              }
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(VM.BuildFor32Addr ? INT_LOAD : LONG_LOAD,
                                         rop,
                                         jtoc,
                                         asImmediateOrRegOffset(AC(offset), s, ir, true),
                                         loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_DoubleConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Double);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir, s);
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand) use;
              Offset offset = dc.offset;
              if (offset.isZero()) {
                offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateLongSizeLiteral(Double.doubleToLongBits(dc.value)));
              }
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(DOUBLE_LOAD, rop, jtoc, asImmediateOrRegOffset(AC(offset), s, ir, true), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_FloatConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Float);
              OPT_RegisterOperand jtoc = ir.regpool.makeJTOCOp(ir, s);
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand) use;
              Offset offset = fc.offset;
              if (offset.isZero()) {
                offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateIntSizeLiteral(Float.floatToIntBits(fc.value)));
              }
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(FLOAT_LOAD, rop, jtoc, asImmediateOrRegOffset(AC(offset), s, ir, true), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_LongConstantOperand) {
              if (!VM.BuildFor64Addr) {
                if (s.getOpcode() != TRAP_IF_opcode) {
                  OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Long);
                  s.insertBefore(Move.create(LONG_MOVE, rop, use));
                  s.putOperand(idx, rop.copyD2U());
                }
              }
            } else if (use instanceof OPT_NullConstantOperand) {
              s.putOperand(idx, AC(Address.zero()));
            } else if (use instanceof OPT_TIBConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.JavaLangObjectArray);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((OPT_TIBConstantOperand) use).value.getTibOffset();
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(VM.BuildFor32Addr ? INT_LOAD : LONG_LOAD,
                                         rop,
                                         jtoc,
                                         asImmediateOrRegOffset(AC(offset), s, ir, true),
                                         loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_CodeConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.CodeArray);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((OPT_CodeConstantOperand) use).value.findOrCreateJtocOffset();
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(VM.BuildFor32Addr ? INT_LOAD : LONG_LOAD,
                                         rop,
                                         jtoc,
                                         asImmediateOrRegOffset(AC(offset), s, ir, true),
                                         loc));
              s.putOperand(idx, rop.copyD2U());
            }
          }
        }
      }

      // Calling OPT_Simplifier.simplify ensures that the instruction is
      // in normalized form. This reduces the number of cases we have to
      // worry about (and does last minute constant folding on the off chance
      // we've missed an opportunity...)
      OPT_Simplifier.simplify(ir.regpool, s);

      switch (s.getOpcode()) {
        //////////
        // LOAD/STORE
        //////////
        case REF_STORE_opcode:
          s.operator = VM.BuildFor32Addr ? INT_STORE : LONG_STORE;
          // On PowerPC, the value being stored must be in a register
          Store.setValue(s, asRegPolymorphic(Store.getClearValue(s), s, ir));
          // Supported addressing modes are quite limited.
          Store.setAddress(s, asRegAddress(Store.getClearAddress(s), s, ir));
          Store.setOffset(s, asImmediateOrRegOffset(Store.getClearOffset(s), s, ir, true));
          break;

        case BYTE_STORE_opcode:
        case SHORT_STORE_opcode:
        case INT_STORE_opcode:
        case LONG_STORE_opcode:
          // On PowerPC, the value being stored must be in a register
          Store.setValue(s, asRegPolymorphic(Store.getClearValue(s), s, ir));
          // Supported addressing modes are quite limited.
          Store.setAddress(s, asRegAddress(Store.getClearAddress(s), s, ir));
          Store.setOffset(s, asImmediateOrRegOffset(Store.getClearOffset(s), s, ir, true));
          break;

        case FLOAT_STORE_opcode:
        case DOUBLE_STORE_opcode:
          // Supported addressing modes are quite limited.
          Store.setAddress(s, asRegAddress(Store.getClearAddress(s), s, ir));
          Store.setOffset(s, asImmediateOrRegOffset(Store.getClearOffset(s), s, ir, true));
          break;

        case REF_LOAD_opcode:
          s.operator = VM.BuildFor32Addr ? INT_LOAD : LONG_LOAD;
          // Supported addressing modes are quite limited.
          Load.setAddress(s, asRegAddress(Load.getClearAddress(s), s, ir));
          Load.setOffset(s, asImmediateOrRegOffset(Load.getClearOffset(s), s, ir, true));
          break;

        case BYTE_LOAD_opcode:
        case UBYTE_LOAD_opcode:
        case SHORT_LOAD_opcode:
        case USHORT_LOAD_opcode:
        case INT_LOAD_opcode:
        case LONG_LOAD_opcode:
        case FLOAT_LOAD_opcode:
        case DOUBLE_LOAD_opcode:
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
          Attempt.setAddress(s, asRegAddress(Attempt.getClearAddress(s), s, ir));
          Attempt.setOffset(s, asRegOffset(Attempt.getClearOffset(s), s, ir));
          break;

        case PREPARE_INT_opcode:
        case PREPARE_ADDR_opcode:
          // Supported addressing modes are quite limited.
          Prepare.setAddress(s, asRegAddress(Prepare.getAddress(s), s, ir));
          Prepare.setOffset(s, asRegOffset(Prepare.getOffset(s), s, ir));
          break;

        case LONG_MOVE_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_MOVE;
          }
          break;

        case INT_MOVE_opcode:
          s.operator = REF_MOVE;
          break;

        case REF_COND_MOVE_opcode:
          s.operator = VM.BuildFor32Addr ? INT_COND_MOVE : LONG_COND_MOVE;
          break;

        case REF_IFCMP_opcode:
          s.operator = VM.BuildFor32Addr ? INT_IFCMP : LONG_IFCMP;
          // val1 can't be a constant, val2 must be small enough.
          IfCmp.setVal1(s, asRegPolymorphic(IfCmp.getClearVal1(s), s, ir));
          IfCmp.setVal2(s, asImmediateOrRegPolymorphic(IfCmp.getClearVal2(s), s, ir, true));

        case LONG_IFCMP_opcode:
          if (VM.BuildFor64Addr) {
            // val1 can't be a constant, val2 must be small enough.
            IfCmp.setVal1(s, asRegPolymorphic(IfCmp.getClearVal1(s), s, ir));
            IfCmp.setVal2(s, asImmediateOrRegPolymorphic(IfCmp.getClearVal2(s), s, ir, true));
          }
          break;

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
          BooleanCmp.setVal2(s,
                             asImmediateOrRegPolymorphic(BooleanCmp.getClearVal2(s),
                                                         s,
                                                         ir,
                                                         !BooleanCmp.getCond(s).isUNSIGNED()));
          break;

        case LONG_CMP_opcode:
          Binary.setVal1(s, asRegPolymorphic(Binary.getVal1(s), s, ir));
          Binary.setVal2(s, asRegPolymorphic(Binary.getVal2(s), s, ir));
          break;

          //////////
          // INT ALU OPS
          //////////

        case LONG_ADD_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_ADD;
            Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getVal2(s), s, ir, true));
          }
          break;

        case INT_ADD_opcode:
          s.operator = REF_ADD;
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getVal2(s), s, ir, true));
          break;

        case REF_ADD_opcode:
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getVal2(s), s, ir, true));
          break;

        case LONG_SUB_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_SUB;
            Binary.setVal1(s, asImmediateOrRegPolymorphic(Binary.getClearVal1(s), s, ir, true));
            // val2 isn't be constant (if it were, OPT_Simplifier would have
            // converted this into an ADD of -Val2).
          }
          break;

        case INT_SUB_opcode:
          s.operator = REF_SUB;
          Binary.setVal1(s, asImmediateOrRegPolymorphic(Binary.getClearVal1(s), s, ir, true));
          // val2 isn't be constant (if it were, OPT_Simplifier would have
          // converted this into an ADD of -Val2).
          break;

        case REF_SUB_opcode:
          Binary.setVal1(s, asImmediateOrRegPolymorphic(Binary.getClearVal1(s), s, ir, true));
          // val2 isn't be constant (if it were, OPT_Simplifier would have
          // converted this into an ADD of -Val2).
          break;

        case INT_MUL_opcode:
          Binary.setVal2(s, asImmediateOrRegInt(Binary.getVal2(s), s, ir, true));
          break;

        case LONG_MUL_opcode:
          if (VM.BuildFor64Addr) {
            Binary.setVal2(s, asImmediateOrRegLong(Binary.getVal2(s), s, ir, true));
          }
          break;

          // There are some instructions for which LIR2MIR.rules doesn't
          // seem to expect constant operands at all.
        case INT_REM_opcode:
        case INT_DIV_opcode:
          GuardedBinary.setVal1(s, asRegInt(GuardedBinary.getClearVal1(s), s, ir));
          GuardedBinary.setVal2(s, asRegInt(GuardedBinary.getClearVal2(s), s, ir));
          break;

        case LONG_REM_opcode:
        case LONG_DIV_opcode:
          if (VM.BuildFor64Addr) {
            GuardedBinary.setVal1(s, asRegLong(GuardedBinary.getClearVal1(s), s, ir));
            GuardedBinary.setVal2(s, asRegLong(GuardedBinary.getClearVal2(s), s, ir));
          }
          break;

        case LONG_NEG_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_NEG;
          }
          break;

        case INT_NEG_opcode:
          s.operator = REF_NEG;
          break;

        case LONG_NOT_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_NOT;
          }
          break;

        case INT_NOT_opcode:
          s.operator = REF_NOT;
          break;

        case LONG_AND_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_AND;
            Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          }
          break;

        case INT_AND_opcode:
          s.operator = REF_AND;
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case REF_AND_opcode:
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case LONG_OR_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_OR;
            Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          }
          break;

        case INT_OR_opcode:
          s.operator = REF_OR;
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case REF_OR_opcode:
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case LONG_XOR_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_XOR;
            Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          }
          break;

        case INT_XOR_opcode:
          s.operator = REF_XOR;
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case REF_XOR_opcode:
          Binary.setVal2(s, asImmediateOrRegPolymorphic(Binary.getClearVal2(s), s, ir, false)); //unsigned immediate
          break;

        case REF_SHL_opcode:
          s.operator = (VM.BuildFor32Addr ? INT_SHL : LONG_SHL);
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

        case LONG_SHL_opcode:
          if (VM.BuildFor64Addr) {
            // Val2 could be a constant, but Val1 apparently can't be.
            Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          }
          break;

        case INT_SHL_opcode:
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

        case REF_SHR_opcode:
          s.operator = (VM.BuildFor32Addr ? INT_SHR : LONG_SHR);
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

        case LONG_SHR_opcode:
          if (VM.BuildFor64Addr) {
            // Val2 could be a constant, but Val1 apparently can't be.
            Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          }
          break;

        case INT_SHR_opcode:
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

        case REF_USHR_opcode:
          s.operator = (VM.BuildFor32Addr ? INT_USHR : LONG_USHR);
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

        case LONG_USHR_opcode:
          if (VM.BuildFor64Addr) {
            // Val2 could be a constant, but Val1 apparently can't be.
            Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          }
          break;

        case INT_USHR_opcode:
          // Val2 could be a constant, but Val1 apparently can't be.
          Binary.setVal1(s, asRegPolymorphic(Binary.getClearVal1(s), s, ir));
          break;

          // Deal with OPT_Simplifier.CF_FLOAT or OPT_Simplifier.CF_DOUBLE being false
        case INT_2DOUBLE_opcode:
        case INT_2FLOAT_opcode:
        case INT_BITS_AS_FLOAT_opcode:
          Unary.setVal(s, asRegInt(Unary.getVal(s), s, ir));
          break;

        case ADDR_2INT_opcode:
          s.operator = (VM.BuildFor32Addr ? REF_MOVE : LONG_2INT);
          break;
        case ADDR_2LONG_opcode:
          s.operator = (VM.BuildFor32Addr ? INT_2LONG : REF_MOVE);
          break;
        case INT_2ADDRSigExt_opcode:
          s.operator = (VM.BuildFor32Addr ? REF_MOVE : INT_2LONG);
          break;

        case INT_2ADDRZerExt_opcode:
          if (VM.BuildFor32Addr) {
            s.operator = REF_MOVE;
          }
          break;

        case LONG_2ADDR_opcode:
          if (VM.BuildFor64Addr) {
            s.operator = REF_MOVE;
          }
          break;

        case NULL_CHECK_opcode:
          NullCheck.setRef(s, asRegAddress(NullCheck.getClearRef(s), s, ir));
          break;

          // Force all call parameters to be in registers
        case SYSCALL_opcode:
        case CALL_opcode: {
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
    if (signed) {
      return (val >= LOWER_IMMEDIATE) && (val <= UPPER_IMMEDIATE);
    } else {
      return (val >= 0) && (val <= UNSIGNED_UPPER_IMMEDIATE);
    }
  }

  public static boolean canBeImmediate(long val, boolean signed) {
    if (signed) {
      return (val >= (long) LOWER_IMMEDIATE) && (val <= (long) UPPER_IMMEDIATE);
    } else {
      return (val >= 0L) && (val <= (long) UNSIGNED_UPPER_IMMEDIATE);
    }
  }

  public static boolean canBeImmediate(Address val, boolean signed) { //KV: Address uses unsigned compares!!
    if (signed) {
      return (val.GE(Address.fromIntSignExtend(LOWER_IMMEDIATE)) || val.LE(Address.fromIntSignExtend(UPPER_IMMEDIATE)));
    } else {
      return val.LE(Address.fromIntZeroExtend(UNSIGNED_UPPER_IMMEDIATE));
    }
  }

  static OPT_Operand asImmediateOrRegInt(OPT_Operand addr,
                                         OPT_Instruction s,
                                         OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_IntConstantOperand) {
      if (!canBeImmediate(((OPT_IntConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempInt();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      }
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegOffset(OPT_Operand addr,
                                            OPT_Instruction s,
                                            OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_AddressConstantOperand) {
      if (!canBeImmediate(((OPT_AddressConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempOffset();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand(((OPT_AddressConstantOperand) addr).value.toInt());
      }
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen, because is 64-bit unsafe
    }
    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegLong(OPT_Operand addr,
                                          OPT_Instruction s,
                                          OPT_IR ir, boolean signed) {
    if (VM.BuildFor64Addr && (addr instanceof OPT_LongConstantOperand)) {
      if (!canBeImmediate(((OPT_LongConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempLong();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand((int) ((OPT_LongConstantOperand) addr).value);
      }
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
    }

    // Operand was OK as is.
    return addr;
  }

  static OPT_Operand asImmediateOrRegPolymorphic(OPT_Operand addr,
                                                 OPT_Instruction s,
                                                 OPT_IR ir, boolean signed) {
    if (addr instanceof OPT_IntConstantOperand) {
      if (!canBeImmediate(((OPT_IntConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempInt();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      }
    } else if (addr instanceof OPT_AddressConstantOperand) {
      if (!canBeImmediate(((OPT_AddressConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempAddress();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand(((OPT_AddressConstantOperand) addr).value.toInt());
      }
    } else if (VM.BuildFor64Addr && (addr instanceof OPT_LongConstantOperand)) {
      if (!canBeImmediate(((OPT_LongConstantOperand) addr).value, signed)) {
        OPT_RegisterOperand rop = ir.regpool.makeTempLong();
        s.insertBefore(Move.create(REF_MOVE, rop, addr));
        return rop.copyD2U();
      } else { // can be immediate --> convert to int
        return new OPT_IntConstantOperand((int) ((OPT_LongConstantOperand) addr).value);
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
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
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
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
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
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
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
    } else if (addr instanceof OPT_ConstantOperand) {
      if (VM.VerifyAssertions) VM._assert(false); //must not happen
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
    } else if (addr instanceof OPT_AddressConstantOperand) {
      OPT_RegisterOperand rop = ir.regpool.makeTempAddress();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    } else if ((VM.BuildFor64Addr) && (addr instanceof OPT_LongConstantOperand)) {
      OPT_RegisterOperand rop = ir.regpool.makeTempLong();
      s.insertBefore(Move.create(REF_MOVE, rop, addr));
      return rop.copyD2U();
    }
    // Operand was OK as is.
    return addr;
  }

}

