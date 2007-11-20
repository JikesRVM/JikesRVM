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
package org.jikesrvm.compilers.opt.ia32;

import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_CodeConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LocationOperand;
import org.jikesrvm.compilers.opt.ir.OPT_NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_Operand;
import org.jikesrvm.compilers.opt.ir.OPT_Operators;
import org.jikesrvm.compilers.opt.ir.OPT_RegisterOperand;
import org.jikesrvm.compilers.opt.ir.OPT_StringConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_TIBConstantOperand;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.unboxed.Offset;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 */
public abstract class OPT_NormalizeConstants implements OPT_Operators {

  /**
   * Only thing we do for IA32 is to restrict the usage of
   * String, Float, and Double constants.  The rules are prepared
   * to deal with everything else.
   *
   * @param ir IR to normalize
   */
  public static void perform(OPT_IR ir) {
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {

      // Get 'large' constants into a form the the BURS rules are
      // prepared to deal with.
      // Constants can't appear as defs, so only scan the uses.
      //
      int numUses = s.getNumberOfUses();
      if (numUses > 0) {
        int numDefs = s.getNumberOfDefs();
        for (int idx = numDefs; idx < numUses + numDefs; idx++) {
          OPT_Operand use = s.getOperand(idx);
          if (use != null) {
            if (use instanceof OPT_ObjectConstantOperand) {
              OPT_ObjectConstantOperand oc = (OPT_ObjectConstantOperand) use;
              if(oc.isMovableObjectConstant()) {
                OPT_RegisterOperand rop = ir.regpool.makeTemp(use.getType());
                OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
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
                s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new OPT_IntConstantOperand(offset.toInt()), loc));
                s.putOperand(idx, rop.copyD2U());
              } else {
                s.putOperand(idx, new OPT_IntConstantOperand(VM_Magic.objectAsAddress(oc.value).toInt()));
              }
            } else if (use instanceof OPT_DoubleConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Double);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              OPT_DoubleConstantOperand dc = (OPT_DoubleConstantOperand) use.copy();
              if (dc.offset.isZero()) {
                dc.offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateLongSizeLiteral(Double.doubleToLongBits(dc.value)));
              }
              s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, dc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_FloatConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Float);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              OPT_FloatConstantOperand fc = (OPT_FloatConstantOperand) use.copy();
              if (fc.offset.isZero()) {
                fc.offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateIntSizeLiteral(Float.floatToIntBits(fc.value)));
              }
              s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, fc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_NullConstantOperand) {
              s.putOperand(idx, new OPT_IntConstantOperand(0));
            } else if (use instanceof OPT_AddressConstantOperand) {
              int v = ((OPT_AddressConstantOperand) use).value.toInt();
              s.putOperand(idx, new OPT_IntConstantOperand(v));
            } else if (use instanceof OPT_TIBConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.TIB);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((OPT_TIBConstantOperand) use).value.getTibOffset();
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new OPT_IntConstantOperand(offset.toInt()), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof OPT_CodeConstantOperand) {
              OPT_RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.CodeArray);
              OPT_Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((OPT_CodeConstantOperand) use).value.findOrCreateJtocOffset();
              OPT_LocationOperand loc = new OPT_LocationOperand(offset);
              s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new OPT_IntConstantOperand(offset.toInt()), loc));
              s.putOperand(idx, rop.copyD2U());
            }
          }
        }
      }
    }
  }

  /**
   * IA32 supports 32 bit int immediates, so nothing to do.
   */
  static OPT_Operand asImmediateOrReg(OPT_Operand addr, OPT_Instruction s, OPT_IR ir) {
    return addr;
  }

}
