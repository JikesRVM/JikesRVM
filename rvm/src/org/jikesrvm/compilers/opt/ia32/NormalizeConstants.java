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
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.Load;
import org.jikesrvm.compilers.opt.ir.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.CodeConstantOperand;
import org.jikesrvm.compilers.opt.ir.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.LocationOperand;
import org.jikesrvm.compilers.opt.ir.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.Operand;
import org.jikesrvm.compilers.opt.ir.Operators;
import org.jikesrvm.compilers.opt.ir.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.StringConstantOperand;
import org.jikesrvm.compilers.opt.ir.TIBConstantOperand;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.unboxed.Offset;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 */
public abstract class NormalizeConstants implements Operators {

  /**
   * Only thing we do for IA32 is to restrict the usage of
   * String, Float, and Double constants.  The rules are prepared
   * to deal with everything else.
   *
   * @param ir IR to normalize
   */
  public static void perform(IR ir) {
    for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {

      // Get 'large' constants into a form the the BURS rules are
      // prepared to deal with.
      // Constants can't appear as defs, so only scan the uses.
      //
      int numUses = s.getNumberOfUses();
      if (numUses > 0) {
        int numDefs = s.getNumberOfDefs();
        for (int idx = numDefs; idx < numUses + numDefs; idx++) {
          Operand use = s.getOperand(idx);
          if (use != null) {
            if (use instanceof ObjectConstantOperand) {
              ObjectConstantOperand oc = (ObjectConstantOperand) use;
              if(oc.isMovableObjectConstant()) {
                RegisterOperand rop = ir.regpool.makeTemp(use.getType());
                Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
                Offset offset = oc.offset;
                if (offset.isZero()) {
                  if (use instanceof StringConstantOperand) {
                    throw new OptimizingCompilerException("String constant w/o valid JTOC offset");
                  } else if (use instanceof ClassConstantOperand) {
                    throw new OptimizingCompilerException("Class constant w/o valid JTOC offset");
                  }
                  offset = Offset.fromIntSignExtend(VM_Statics.findOrCreateObjectLiteral(oc.value));
                }
                LocationOperand loc = new LocationOperand(offset);
                s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new IntConstantOperand(offset.toInt()), loc));
                s.putOperand(idx, rop.copyD2U());
              } else {
                s.putOperand(idx, new IntConstantOperand(VM_Magic.objectAsAddress(oc.value).toInt()));
              }
            } else if (use instanceof DoubleConstantOperand) {
              RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Double);
              Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              DoubleConstantOperand dc = (DoubleConstantOperand) use.copy();
              if (dc.offset.isZero()) {
                dc.offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateLongSizeLiteral(Double.doubleToLongBits(dc.value)));
              }
              s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, dc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof FloatConstantOperand) {
              RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.Float);
              Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              FloatConstantOperand fc = (FloatConstantOperand) use.copy();
              if (fc.offset.isZero()) {
                fc.offset =
                    Offset.fromIntSignExtend(VM_Statics.findOrCreateIntSizeLiteral(Float.floatToIntBits(fc.value)));
              }
              s.insertBefore(Binary.create(MATERIALIZE_FP_CONSTANT, rop, jtoc, fc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof NullConstantOperand) {
              s.putOperand(idx, new IntConstantOperand(0));
            } else if (use instanceof AddressConstantOperand) {
              int v = ((AddressConstantOperand) use).value.toInt();
              s.putOperand(idx, new IntConstantOperand(v));
            } else if (use instanceof TIBConstantOperand) {
              RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.TIB);
              Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((TIBConstantOperand) use).value.getTibOffset();
              LocationOperand loc = new LocationOperand(offset);
              s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new IntConstantOperand(offset.toInt()), loc));
              s.putOperand(idx, rop.copyD2U());
            } else if (use instanceof CodeConstantOperand) {
              RegisterOperand rop = ir.regpool.makeTemp(VM_TypeReference.CodeArray);
              Operand jtoc = ir.regpool.makeJTOCOp(ir, s);
              Offset offset = ((CodeConstantOperand) use).value.findOrCreateJtocOffset();
              LocationOperand loc = new LocationOperand(offset);
              s.insertBefore(Load.create(INT_LOAD, rop, jtoc, new IntConstantOperand(offset.toInt()), loc));
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
  static Operand asImmediateOrReg(Operand addr, Instruction s, IR ir) {
    return addr;
  }

}
