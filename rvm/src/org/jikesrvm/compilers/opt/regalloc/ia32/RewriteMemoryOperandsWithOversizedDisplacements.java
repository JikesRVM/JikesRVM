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
package org.jikesrvm.compilers.opt.regalloc.ia32;

import static org.jikesrvm.compilers.opt.ir.IRTools.IC;
import static org.jikesrvm.compilers.opt.ir.IRTools.LC;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOVSXDQ;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOVSXQ__B;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOVSXQ__W;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_SHL;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IMMQ_MOV;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Move;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.operand.LocationOperand;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.util.Bits;

/**
 * Processes memory operands with a displacement that doesn't fit
 * into 32 bits. This is only necessary on x64.
 */
public class RewriteMemoryOperandsWithOversizedDisplacements extends CompilerPhase {

  @Override
  public final boolean shouldPerform(OptOptions options) {
    return VM.BuildFor64Addr;
  }

  @Override
  public String getName() {
    return "Rewrite MemoryOperands with 64-bit displacements";
  }

  @Override
  public void perform(IR ir) {
    for (Instruction inst = ir.firstInstructionInCodeOrder(); inst != null; inst =
        inst.nextInstructionInCodeOrder()) {
      for (int i = 0; i < inst.getNumberOfOperands(); i++) {
        Operand op = inst.getOperand(i);
        if (op instanceof MemoryOperand) {
          MemoryOperand mo = (MemoryOperand)op;
          disp64MemOperandConversion(ir, inst, mo);
        }
      }
    }
  }

  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  private static void disp64MemOperandConversion(IR ir, Instruction inst, MemoryOperand mo) {
    if (!mo.disp.isZero() && !Bits.fits(mo.disp, 32)) {
      RegisterOperand effectiveAddress = ir.regpool.makeTempLong();
      RegisterOperand temp = null;
      inst.insertBefore(MIR_Move.create(IMMQ_MOV, effectiveAddress, LC(mo.disp.toLong())));
      if (mo.index != null) {
         if (mo.scale != 0) {
           temp = ir.regpool.makeTempLong();
           TypeReference indexType = mo.index.getType();
           if (indexType.isLongType() || indexType.isWordLikeType()) {
             inst.insertBefore(MIR_Move.create(IA32_MOV, temp, mo.index.copy()));
           } else if (indexType.isIntType()) {
             inst.insertBefore(MIR_Unary.create(IA32_MOVSXDQ, temp, mo.index.copy()));
           } else if (indexType.isByteType()) {
             inst.insertBefore(MIR_Unary.create(IA32_MOVSXQ__B, temp, mo.index.copy()));
           } else if (indexType.isShortType() || indexType.isCharType()) {
             inst.insertBefore(MIR_Unary.create(IA32_MOVSXQ__W, temp, mo.index.copy()));
           } else {
             String msg = "Unhandled type: " + indexType;
             if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, msg);
           }
           inst.insertBefore(MIR_BinaryAcc.create(IA32_SHL, temp.copy(), IC(mo.scale)));
           inst.insertBefore(MIR_BinaryAcc.create(IA32_ADD, effectiveAddress.copy(), temp.copy()));
         } else {
           TypeReference indexType = mo.index.getType();
           if (indexType.isLongType() || indexType.isWordLikeType()) {
             inst.insertBefore(MIR_BinaryAcc.create(IA32_ADD, effectiveAddress.copy(), mo.index.copy()));
           } else {
             temp = ir.regpool.makeTempLong();
             if (indexType.isIntType()) {
               inst.insertBefore(MIR_Unary.create(IA32_MOVSXDQ, temp, mo.index.copy()));
             } else if (indexType.isByteType()) {
               inst.insertBefore(MIR_Unary.create(IA32_MOVSXQ__B, temp, mo.index.copy()));
             } else if (indexType.isShortType() || indexType.isCharType()) {
               inst.insertBefore(MIR_Unary.create(IA32_MOVSXQ__W, temp, mo.index.copy()));
             } else {
               String msg = "Unhandled type: " + indexType;
               if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED, msg);
             }
             inst.insertBefore(MIR_BinaryAcc.create(IA32_ADD, effectiveAddress.copy(), temp.copy()));
           }
         }
      }
      if (mo.base != null) {
        inst.insertBefore(MIR_BinaryAcc.create(IA32_ADD, effectiveAddress.copy(), mo.base.copy()));
      }
      MemoryOperand newMo = MemoryOperand.I(effectiveAddress.copy().asRegister(), mo.size, null != mo.loc ? (LocationOperand)mo.loc.copy() : null,
          mo.guard != null ? mo.guard.copy() : null);
      inst.replaceOperand(mo, newMo);
    }
  }

}
