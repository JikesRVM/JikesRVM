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
package org.jikesrvm.compilers.opt.regalloc;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ADD;
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;


@RunWith(VMRequirements.class)
@Category({RequiresOptCompiler.class})
public class ScratchMapTest {

  private static final int REGISTER_COUNT = 100;
  private RegisterAllocatorState regAllocState;
  private ScratchMap scratchMap;

  @Before
  public void createNewScratchMap() {
    regAllocState = new RegisterAllocatorState(REGISTER_COUNT);
    scratchMap = new ScratchMap(regAllocState);
  }

  @Test
  public void newMapIsEmpty() {
    ScratchMap scratchMap = new ScratchMap(regAllocState);
    assertThat(scratchMap.isEmpty(), is(true));
  }

  @Category(RequiresBuiltJikesRVM.class) // because of TypeReference
  @Test
  public void markDirtyMarksRegistersAsDirty() {
    Register resultReg = createRegister(0);
    RegisterOperand result = new RegisterOperand(resultReg, TypeReference.Int);
    Register op1Reg = new Register(1);
    Register op2Reg = new Register(2);
    RegisterOperand op1 = new RegisterOperand(op1Reg, TypeReference.Int);
    RegisterOperand op2 = new RegisterOperand(op2Reg, TypeReference.Int);
    Instruction add = Binary.create(INT_ADD, result, op1, op2);

    scratchMap.markDirty(add, op2Reg);
    assertThat(scratchMap.isDirty(add, op2Reg), is(true));
  }

  @Category(RequiresBuiltJikesRVM.class) // because of TypeReference
  @Test
  public void markingRegistersAsDirtyMoreThanOnceIsHarmless() {
    Register resultReg = createRegister(0);
    RegisterOperand result = new RegisterOperand(resultReg, TypeReference.Int);
    Register op1Reg = new Register(1);
    Register op2Reg = new Register(2);
    RegisterOperand op1 = new RegisterOperand(op1Reg, TypeReference.Int);
    RegisterOperand op2 = new RegisterOperand(op2Reg, TypeReference.Int);
    Instruction add = Binary.create(INT_ADD, result, op1, op2);

    scratchMap.markDirty(add, op2Reg);
    scratchMap.markDirty(add, op2Reg);
    assertThat(scratchMap.isDirty(add, op2Reg), is(true));
  }

  @Test
  public void markDirtyDoesNotCheckThatRegisterIsUsedInInstruction() {
    Register symb = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.markDirty(inst, symb);
    assertThat(scratchMap.isDirty(inst, symb), is(true));
  }

  @Test
  public void beginSymbolicIntervalCreatesANewInterval() {
    Register symb = createRegister(0);
    Register scratch = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.beginSymbolicInterval(symb, scratch, inst);
    assertThat(scratchMap.isEmpty(), is(false));
  }

  @Test(expected = NullPointerException.class)
  public void endSymbolicIntervalRemovesInformationAboutScratchRegisterFromPendingMap() {
    Register symb = createRegister(0);
    Register scratch = createRegister(1);
    Instruction begin = createInstruction();
    scratchMap.beginSymbolicInterval(symb, scratch, begin);
    Instruction end = createInstruction();
    scratchMap.endSymbolicInterval(symb, end);

    scratchMap.endSymbolicInterval(symb, end);
  }

  private Register createRegister(int regNum) {
    return new Register(regNum);
  }

  @Test
  public void beginSymbolicIntervalAllowsRegisterAndItsScratchRegisterToBeTheSame() {
    Register symb = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.beginSymbolicInterval(symb, symb, inst);
  }

  @Test(expected = NullPointerException.class)
  public void endSymbolicIntervalForNotStartedIntervalCausesNPE() {
    Register symb = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.endSymbolicInterval(symb, inst);
  }

  private Instruction createInstruction() {
    Instruction fence = Empty.create(FENCE);
    return fence;
  }

  @Test
  public void beginScratchIntervalCreatesANewInterval() {
    Register scratch = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.beginScratchInterval(scratch, inst);
    assertThat(scratchMap.isEmpty(), is(false));
  }

  @Test
  public void beginScratchIntervalSavesInformationAboutScratchRegister() {
    Register scratch = createRegister(0);
    Instruction begin = createInstruction();
    int instructionCount = 10;
    regAllocState.initializeDepthFirstNumbering(instructionCount);
    int instNumber = 2;
    regAllocState.setDFN(begin, instNumber);
    scratchMap.beginScratchInterval(scratch, begin);
    Instruction end = createInstruction();
    regAllocState.setDFN(end, instNumber + 1);
    scratchMap.endScratchInterval(scratch, end);

    Register scratchReg = scratchMap.getScratch(scratch, instNumber);
    assertThat(scratchReg, is(scratch));
    assertThat(scratchMap.isScratch(scratchReg, instNumber), is(true));
  }

  @Test(expected = NullPointerException.class)
  public void endScratchIntervalRemovesInformationAboutScratchRegisterFromPendingMap() {
    Register scratch = createRegister(0);
    Instruction begin = createInstruction();
    scratchMap.beginScratchInterval(scratch, begin);
    Instruction end = createInstruction();
    scratchMap.endScratchInterval(scratch, end);

    scratchMap.endScratchInterval(scratch, end);
  }

  @Test(expected = NullPointerException.class)
  public void endScratchIntervalForNotStartedIntervalCausesNPE() {
    Register scratch = createRegister(0);
    Instruction inst = createInstruction();
    scratchMap.endScratchInterval(scratch, inst);
  }


}
