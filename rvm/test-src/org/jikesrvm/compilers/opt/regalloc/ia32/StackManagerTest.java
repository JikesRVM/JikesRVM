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

import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_ADD;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FMOV;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_FLD;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_MOV;
import static org.jikesrvm.compilers.opt.ir.ia32.ArchOperators.IA32_PREFETCHNTA;
import static org.junit.Assert.*;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.OsrPoint;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_CacheOp;
import org.jikesrvm.compilers.opt.ir.ia32.MIR_Move;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresIA32;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresBuiltJikesRVM.class, RequiresIA32.class, RequiresOptCompiler.class})
public class StackManagerTest {

  private StackManager stackManager;

  private int registerNumber;

  @Before
  public void setupStackManager() {
    stackManager = new StackManager();
  }

  @Test
  public void noScratchNeededForFMOV() {
    RegisterOperand lhs = new RegisterOperand(createRegister(), TypeReference.Float);
    RegisterOperand rhs = new RegisterOperand(createRegister(), TypeReference.Float);
    Instruction fMov = MIR_Move.create(IA32_FMOV, lhs, rhs);
    assertFalse(stackManager.needScratch(lhs.getRegister(), fMov));
    assertFalse(stackManager.needScratch(rhs.getRegister(), fMov));
  }

  @Test
  public void osrPointsNeverNeedScratchRegisters() {
    Instruction osrPoint = OsrPoint.create(YIELDPOINT_OSR, null, 0);
    assertFalse(stackManager.needScratch(createRegister(), osrPoint));
  }

  private Register createRegister() {
    return new Register(registerNumber++);
  }

  @Test
  public void instructionsWithMemoryOperandsAlwaysNeedScratch() {
    RegisterOperand result = new RegisterOperand(createRegister(), TypeReference.Int);
    MemoryOperand value = MemoryOperand.B(new RegisterOperand(createRegister(), TypeReference.Int), (byte) 4, null, null);
    Instruction scratchFreeMove = MIR_Move.create(IA32_MOV, result, value);
    assertTrue(stackManager.needScratch(result.getRegister(), scratchFreeMove));
  }

  @Test
  public void instructionsMayNeedScratchDueToArchitectureRestrictions() {
    RegisterOperand addr = new RegisterOperand(createRegister(), TypeReference.Address);
    Instruction prefetch = MIR_CacheOp.create(IA32_PREFETCHNTA, addr);
    assertTrue(stackManager.needScratch(addr.getRegister(), prefetch));
  }

  @Test
  public void instructionsNeedAScratchIfOneRegisterAppearsMultipleTimes() {
    Register reg = createRegister();
    RegisterOperand result = new RegisterOperand(reg, TypeReference.Int);
    RegisterOperand value = new RegisterOperand(reg, TypeReference.Int);
    Instruction add = MIR_BinaryAcc.create(IA32_ADD, result, value);
    assertTrue(stackManager.needScratch(reg, add));
  }

  @Test
  public void normalInstructionsDoNotNeedScratch() {
    RegisterOperand result = new RegisterOperand(createRegister(), TypeReference.Float);
    RegisterOperand value = new RegisterOperand(createRegister(), TypeReference.Float);
    Instruction floatingPointMove = MIR_Move.create(IA32_FLD, result, value);
    assertFalse(stackManager.needScratch(result.getRegister(), floatingPointMove));
    assertFalse(stackManager.needScratch(value.getRegister(), floatingPointMove));
  }

}
