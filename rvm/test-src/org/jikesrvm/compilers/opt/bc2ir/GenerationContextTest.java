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
package org.jikesrvm.compilers.opt.bc2ir;

import static org.hamcrest.CoreMatchers.is;
import static org.jikesrvm.compilers.opt.driver.OptConstants.EPILOGUE_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.EPILOGUE_BLOCK_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.PROLOGUE_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.PROLOGUE_BLOCK_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.SYNCHRONIZED_MONITORENTER_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.SYNCHRONIZED_MONITOREXIT_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.SYNTH_CATCH_BCI;
import static org.jikesrvm.compilers.opt.driver.OptConstants.UNKNOWN_BCI;
import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.GET_CAUGHT_EXCEPTION;
import static org.jikesrvm.compilers.opt.ir.Operators.GUARD_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITORENTER;
import static org.jikesrvm.compilers.opt.ir.Operators.MONITOREXIT;
import static org.jikesrvm.compilers.opt.ir.Operators.OSR_BARRIER;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_MOVE;
import static org.jikesrvm.compilers.opt.ir.Operators.RETURN;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_BEGIN;
import static org.jikesrvm.compilers.opt.ir.Operators.UNINT_END;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.inlining.DefaultInlineOracle;
import org.jikesrvm.compilers.opt.inlining.InlineOracle;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlockBag;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.OsrBarrier;
import org.jikesrvm.compilers.opt.ir.Prologue;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Return;
import org.jikesrvm.compilers.opt.ir.operand.ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.util.GraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode.OutEdgeEnumeration;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

@RunWith(VMRequirements.class)
@Category({RequiresBuiltJikesRVM.class, RequiresOptCompiler.class})
public class GenerationContextTest {

  private int currentRegisterNumber = -1;

  @Test
  public void constructorIsCorrectForTheSimplestMethods() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    assertThatStateIsCorrectForUnsynchronizedEmptyStaticMethod(nm, cm, io, gc);
  }

  private void assertThatNumberOfParametersIs(GenerationContext gc, int numberParams) {
    assertThat(gc.getArguments().length, is(numberParams));
  }

  private void assertThatDataIsSavedCorrectly(NormalMethod nm,
      CompiledMethod cm, InlineOracle io, GenerationContext gc) {
    assertThatInlinePlanWasSetCorrectly(io, gc);
    assertThatOriginalMethodWasSetCorrectly(nm, gc);
    assertThatOriginalCompiledMethodWasSetCorrectly(cm, gc);
  }

  private void assertThatReturnInstructionReturnsVoid(BasicBlock epilogue) {
    assertNull(Return.getVal(epilogue.lastRealInstruction()));
  }

  private void assertThatRegisterPoolExists(GenerationContext gc) {
    assertNotNull(gc.getTemps());
  }

  private void assertThatChecksWontBeSkipped(GenerationContext gc) {
    assertThat(gc.noCheckStoreChecks(),is(false));
    assertThat(gc.noBoundsChecks(),is(false));
    assertThat(gc.noNullChecks(),is(false));
  }

  private void assertThatGCHasNoExceptionHandlers(GenerationContext gc) {
    assertNull(gc.getEnclosingHandlers());
    assertThat(gc.generatedExceptionHandlers(), is(false));
  }

  private void assertThatReturnValueIsVoid(GenerationContext gc) {
    assertNull(gc.getResultReg());
  }

  private void assertThatOriginalCompiledMethodWasSetCorrectly(CompiledMethod cm,
      GenerationContext gc) {
    assertThat(gc.getOriginalCompiledMethod(), is(cm));
  }

  private void assertThatOriginalMethodWasSetCorrectly(NormalMethod nm,
      GenerationContext gc) {
    assertThat(gc.getOriginalMethod(), is(nm));
  }

  private void assertThatInlinePlanWasSetCorrectly(InlineOracle io,
      GenerationContext gc) {
    assertThat(gc.getInlinePlan(), is(io));
  }

  private void assertThatExitBlockIsSetCorrectly(GenerationContext gc) {
    assertThat(gc.getExit(), is(gc.getCfg().exit()));
  }

  private void assertThatLastInstructionInEpilogueIsReturn(
      GenerationContext gc, BasicBlock epilogue) {
    assertThat(epilogue.lastRealInstruction().operator(), is(RETURN));
    assertThat(epilogue.lastRealInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertThat(epilogue.getNormalOut().nextElement(), is(gc.getExit()));
  }

  private void assertThatEpilogueBlockIsSetupCorrectly(GenerationContext gc,
      InlineSequence inlineSequence, BasicBlock epilogue) {
    assertThat(gc.getCfg().lastInCodeOrder(), is(epilogue));
    assertThat(epilogue.firstInstruction().getBytecodeIndex(), is(EPILOGUE_BLOCK_BCI));
    assertTrue(epilogue.firstInstruction().position().equals(inlineSequence));
  }

  private void assertThatFirstInstructionInPrologueIsPrologue(
      InlineSequence inlineSequence, BasicBlock prologue) {
    assertThat(prologue.firstRealInstruction().getBytecodeIndex(),is(PROLOGUE_BCI));
    assertTrue(prologue.firstRealInstruction().position().equals(inlineSequence));
    assertThat(prologue.firstRealInstruction().operator(),is(IR_PROLOGUE));
  }

  private void assertThatPrologueBlockIsSetupCorrectly(GenerationContext gc,
      InlineSequence inlineSequence, BasicBlock prologue) {
    assertThat(gc.getCfg().firstInCodeOrder(), is(prologue));
    assertThat(prologue.firstInstruction().getBytecodeIndex(), is(PROLOGUE_BLOCK_BCI));
    assertTrue(prologue.firstInstruction().position().equals(inlineSequence));
  }

  private void assertThatInlineSequenceWasSetCorrectly(GenerationContext gc,
      InlineSequence inlineSequence) {
    assertThat(gc.getInlineSequence().equals(inlineSequence),is(true));
  }

  @Test
  public void checkMethodsQueryUnderlyingMethod() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyStaticMethodWithNoBoundCheckAnnotation");
    assertThat(gc.noBoundsChecks(),is(true));
    assertThat(gc.noCheckStoreChecks(),is(false));
    assertThat(gc.noNullChecks(),is(false));

    GenerationContext gc2 = createMostlyEmptyContext("emptyStaticMethodWithNoCheckStoreAnnotation");
    assertThat(gc2.noBoundsChecks(),is(false));
    assertThat(gc2.noCheckStoreChecks(),is(true));
    assertThat(gc2.noNullChecks(),is(false));

    GenerationContext gc3 = createMostlyEmptyContext("emptyStaticMethodWithNoNullCheckAnnotation");
    assertThat(gc3.noBoundsChecks(),is(false));
    assertThat(gc3.noCheckStoreChecks(),is(false));
    assertThat(gc3.noNullChecks(),is(true));
  }

  @Test
  public void instanceMethodsHaveMovesInPrologue() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyInstanceMethodWithoutAnnotations");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatLocalsForInstanceMethodWithoutParametersAreCorrect(nm, gc);
    RegisterOperand thisOperand = getThisOperand(gc);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, thisOperand, 0);
    Instruction lastPrologueInstruction = prologue.lastRealInstruction();
    assertThatInstructionIsGuardMoveForPrologue(lastPrologueInstruction, thisOperand, gc);

    assertThatEpilogueIsForVoidReturnMethod(gc, inlineSequence);

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatRegisterPoolExists(gc);

    assertThatChecksWontBeSkipped(gc);

    assertThatNoRethrowBlockExists(gc);
  }

  private void assertThatBlockOnlyHasOneRealInstruction(BasicBlock block) {
    assertThat(block.lastRealInstruction(),is(block.firstRealInstruction()));
  }

  @Test
  public void methodsWithReturnValueHaveReturnInstructionInEpilogue() throws Exception {
    NormalMethod nm = getNormalMethodForTest("staticMethodReturningLongMaxValue");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 0);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatBlockOnlyHasOneRealInstruction(prologue);

    BasicBlock epilogue = gc.getEpilogue();
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);
    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    RegisterOperand returnValue = Return.getVal(epilogue.lastRealInstruction()).asRegister();
    assertThat(returnValue.getType(),is(TypeReference.Long));

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThat(gc.getResultReg().isLong(),is(true));
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatRegisterPoolExists(gc);

    assertThatChecksWontBeSkipped(gc);
  }

  private static GenerationContext createMostlyEmptyContext(String methodName) throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, methodName);
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);
    return gc;
  }

  @Test
  public void constructorCreatesOperandsForStaticMethodWithParameters() throws Exception {
    Class<?>[] argumentTypes = {long.class, int.class, Object.class, double.class};
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithParams", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 4);
    RegisterOperand longRegOp = getThisOperand(gc);
    assertThat(longRegOp.isLong(),is(true));
    assertThatRegOpHasDeclaredType(longRegOp);
    RegisterOperand intRegOp = gc.getArguments()[1].asRegister();
    assertThat(intRegOp.isInt(), is(true));
    assertThatRegOpHasDeclaredType(intRegOp);
    RegisterOperand objectRegOp = gc.getArguments()[2].asRegister();
    assertThatRegOpHoldsClassType(objectRegOp);
    assertThatRegOpHasDeclaredType(objectRegOp);
    RegisterOperand doubleRegOp = gc.getArguments()[3].asRegister();
    assertThat(doubleRegOp.isDouble(), is(true));
    assertThatRegOpHasDeclaredType(doubleRegOp);

    Register longReg = gc.localReg(0, TypeReference.Long);
    assertThatRegOpIsLocalRegOfRegister(longRegOp, longReg);
    Register intReg = gc.localReg(2, TypeReference.Int);
    assertThatRegOpIsLocalRegOfRegister(intRegOp, intReg);
    Register objectReg = gc.localReg(3, TypeReference.JavaLangObject);
    assertThatRegOpIsLocalRegOfRegister(objectRegOp, objectReg);
    Register doubleReg = gc.localReg(4, TypeReference.Double);
    assertThatRegOpIsLocalRegOfRegister(doubleRegOp, doubleReg);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatBlockOnlyHasOneRealInstruction(prologue);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, longRegOp, 0);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, intRegOp, 1);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, objectRegOp, 2);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, doubleRegOp, 3);

    assertThatEpilogueIsForVoidReturnMethod(gc, inlineSequence);

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatRegisterPoolExists(gc);

    assertThatChecksWontBeSkipped(gc);

    assertThatNoRethrowBlockExists(gc);
  }

  private void assertThatPrologueOperandIsRegOpFromLocalNr(BasicBlock prologue,
      RegisterOperand regOp, int localNr) {
    RegisterOperand prologueOp = Prologue.getFormal(prologue.firstRealInstruction(),localNr);
    assertThat(prologueOp.sameRegisterPropertiesAs(regOp),is(true));
  }

  private void assertThatRegOpIsLocalRegOfRegister(RegisterOperand regOp,
      Register reg) {
    assertThat(regOp.getRegister(), is(reg));
    assertThat(reg.isLocal(), is(true));
  }

  private void assertThatRegOpHoldsClassType(RegisterOperand objectRegOp) {
    assertThat(objectRegOp.isRef(), is(true));
    assertThat(objectRegOp.isExtant(), is(true));
  }

  private void assertThatRegOpHasDeclaredType(RegisterOperand regOp) {
    assertThat(regOp.isDeclaredType(),is(true));
  }

  @Test
  public void constructorCreatesOperandsForInstanceMethods() throws Exception {
    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod nm = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 5);
    RegisterOperand thisOperand = getThisOperand(gc);
    assertThatRegOpHoldsClassType(thisOperand);
    assertThatRegOpHasDeclaredType(thisOperand);
    RegisterOperand objectRegOp = gc.getArguments()[1].asRegister();
    assertThatRegOpHoldsClassType(objectRegOp);
    assertThatRegOpHasDeclaredType(objectRegOp);
    RegisterOperand doubleRegOp = gc.getArguments()[2].asRegister();
    assertThat(doubleRegOp.isDouble(), is(true));
    assertThatRegOpHasDeclaredType(doubleRegOp);
    RegisterOperand intRegOp = gc.getArguments()[3].asRegister();
    assertThat(intRegOp.isInt(), is(true));
    assertThatRegOpHasDeclaredType(intRegOp);
    RegisterOperand longRegOp = gc.getArguments()[4].asRegister();
    assertThat(longRegOp.isLong(),is(true));
    assertThatRegOpHasDeclaredType(longRegOp);

    Register thisLocalReg = gc.localReg(0, nm.getDeclaringClass().getTypeRef());
    assertThatRegOpIsLocalRegOfRegister(thisOperand, thisLocalReg);
    Register objectReg = gc.localReg(1, TypeReference.JavaLangObject);
    assertThatRegOpIsLocalRegOfRegister(objectRegOp, objectReg);
    Register doubleReg = gc.localReg(2, TypeReference.Double);
    assertThatRegOpIsLocalRegOfRegister(doubleRegOp, doubleReg);
    Register intReg = gc.localReg(4, TypeReference.Int);
    assertThatRegOpIsLocalRegOfRegister(intRegOp, intReg);
    Register longReg = gc.localReg(5, TypeReference.Long);
    assertThatRegOpIsLocalRegOfRegister(longRegOp, longReg);


    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    Instruction lastPrologueInstruction = prologue.lastRealInstruction();
    assertThatInstructionIsGuardMoveForPrologue(lastPrologueInstruction, thisOperand, gc);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, thisOperand, 0);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, objectRegOp, 1);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, doubleRegOp, 2);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, intRegOp, 3);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, longRegOp, 4);

    assertThatEpilogueIsForVoidReturnMethod(gc, inlineSequence);

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatRegisterPoolExists(gc);

    assertThatChecksWontBeSkipped(gc);

    assertThatNoRethrowBlockExists(gc);
  }

  private void assertThatInstructionIsGuardMoveForPrologue(Instruction instruction, RegisterOperand thisOp, GenerationContext gc) {
    assertThat(instruction.operator(),is(GUARD_MOVE));
    assertThat(instruction.getBytecodeIndex(),is(PROLOGUE_BCI));
    assertTrue(Move.getVal(instruction).isTrueGuard());
    Operand o = Move.getResult(instruction);
    assertTrue(o.isRegister());
    RegisterOperand val = o.asRegister();
    RegisterOperand nullCheckGuard = gc.makeNullCheckGuard(thisOp.getRegister());
    assertTrue(val.sameRegisterPropertiesAs(nullCheckGuard));
  }

  @Test
  public void specializationOfParametersIsSupported() throws Exception {
    Class<?>[] argumentTypes = {Object.class};
    TypeReference typeRef = JikesRVMSupport.getTypeForClass(Uninterruptible.class).getTypeRef();
    TypeReference[] specializedArgumentTypes = {typeRef};
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithObjectParam", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, specializedArgumentTypes, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 1);
    RegisterOperand objectRegOp = getThisOperand(gc);
    assertThatRegOpHoldsClassType(objectRegOp);
    assertThatRegOpHasDeclaredType(objectRegOp);

    Register objectReg = gc.localReg(0, TypeReference.Uninterruptible);
    assertThatRegOpIsLocalRegOfRegister(objectRegOp, objectReg);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatBlockOnlyHasOneRealInstruction(prologue);
    assertThatPrologueOperandIsRegOpFromLocalNr(prologue, objectRegOp, 0);

    assertThatEpilogueIsForVoidReturnMethod(gc, inlineSequence);

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatRegisterPoolExists(gc);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertThatEpilogueIsForVoidReturnMethod(GenerationContext gc,
      InlineSequence inlineSequence) {
    BasicBlock epilogue = gc.getEpilogue();
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);
    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    assertThatReturnInstructionReturnsVoid(epilogue);
    assertThatBlockOnlyHasOneRealInstruction(epilogue);
  }

  @Test
  public void specializedParametersAreNotSanityChecked() throws Exception {
    Class<?>[] argumentTypes = {Object.class};
    TypeReference[] specializedArgumentTypes = {};
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithObjectParam", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, specializedArgumentTypes, cm, opts, io);
    assertThatNumberOfParametersIs(gc, 0);
  }

  @Test
  public void prologueAndEpilogueForSynchronizedStaticMethodHaveMonitorEnterAndExit() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptySynchronizedStaticMethod");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 0);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);

    Enumeration<Instruction> prologueInstructions = prologue.forwardRealInstrEnumerator();
    prologueInstructions.nextElement();

    Instruction secondInstruction = prologueInstructions.nextElement();
    assertInstructionIsMonitorEnterInPrologue(secondInstruction, inlineSequence);

    assertThatGuardIsCorrectForMonitorEnter(secondInstruction);
    Operand lockObject = MonitorOp.getRef(secondInstruction);

    Operand expectedLockObject = buildLockObjectForStaticMethod(nm);

    assertTrue(expectedLockObject.similar(lockObject));

    assertThatNumberOfRealInstructionsMatches(prologue, 2);

    BasicBlock epilogue = gc.getEpilogue();
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);

    assertThatFirstInstructionEpilogueIsMonitorExit(inlineSequence, epilogue);

    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    assertThatReturnInstructionReturnsVoid(epilogue);

    assertThatNumberOfRealInstructionsMatches(epilogue, 2);

    assertThatExitBlockIsSetCorrectly(gc);
    assertThatRegisterPoolExists(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);

    assertThatExceptionHandlersWereGenerated(gc);

    assertThatUnlockAndRethrowBlockIsCorrect(gc, inlineSequence, prologue,
        expectedLockObject, epilogue);

    assertThatChecksWontBeSkipped(gc);
  }

  private void checkCodeOrderForRethrowBlock(GenerationContext gc,
      BasicBlock prologue, BasicBlock epilogue,
      ExceptionHandlerBasicBlock rethrow) {
    BasicBlock firstBlockInCodeOrder = gc.getCfg().firstInCodeOrder();
    assertTrue(firstBlockInCodeOrder.equals(prologue));
    BasicBlock secondBlockInCodeOrder = firstBlockInCodeOrder.nextBasicBlockInCodeOrder();
    assertTrue(secondBlockInCodeOrder.equals(rethrow));
    BasicBlock lastBlockInCodeOrder = secondBlockInCodeOrder.nextBasicBlockInCodeOrder();
    assertTrue(lastBlockInCodeOrder.equals(epilogue));
    assertNull(lastBlockInCodeOrder.nextBasicBlockInCodeOrder());
  }

  private Operand buildLockObjectForStaticMethod(NormalMethod nm) {
    Class<?> klass = nm.getDeclaringClass().getClassForType();
    Offset offs = Offset.fromIntSignExtend(Statics.findOrCreateObjectLiteral(klass));
    Operand expectedLockObject = new ClassConstantOperand(klass, offs);
    return expectedLockObject;
  }

  private void assertThatNumberOfRealInstructionsMatches(BasicBlock b,
      int expected) {
    assertThat(b.getNumberOfRealInstructions(), is(expected));
  }

  @Test
  public void prologueAndEpilogueForSynchronizedInstanceMethodHaveMonitorEnterAndExit() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptySynchronizedInstanceMethod");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatLocalsForInstanceMethodWithoutParametersAreCorrect(nm, gc);
    RegisterOperand thisOperand = getThisOperand(gc);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);

    Enumeration<Instruction> prologueInstructions = prologue.forwardRealInstrEnumerator();
    prologueInstructions.nextElement();

    Instruction secondInstruction = prologueInstructions.nextElement();
    assertThatInstructionIsGuardMoveForPrologue(secondInstruction, thisOperand, gc);

    Instruction lastInstruction = prologueInstructions.nextElement();
    assertInstructionIsMonitorEnterInPrologue(lastInstruction, inlineSequence);

    assertThatGuardIsCorrectForMonitorEnter(lastInstruction);
    Operand lockObject = MonitorOp.getRef(lastInstruction);

    Operand expectedLockObject = buildLockObjectForInstanceMethod(nm, thisOperand, gc);
    assertTrue(expectedLockObject.similar(lockObject));

    assertThatNumberOfRealInstructionsMatches(prologue, 3);

    BasicBlock epilogue = gc.getEpilogue();
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);

    assertThatFirstInstructionEpilogueIsMonitorExit(inlineSequence, epilogue);

    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    assertThatReturnInstructionReturnsVoid(epilogue);

    assertThatNumberOfRealInstructionsMatches(epilogue, 2);

    assertThatExitBlockIsSetCorrectly(gc);
    assertThatRegisterPoolExists(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);

    assertThatExceptionHandlersWereGenerated(gc);

    assertThatUnlockAndRethrowBlockIsCorrect(gc, inlineSequence, prologue,
        expectedLockObject, epilogue);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertThatUnlockAndRethrowBlockIsCorrect(GenerationContext gc,
      InlineSequence inlineSequence, BasicBlock prologue, Operand lockObject,
      BasicBlock epilogue) {
    ExceptionHandlerBasicBlockBag ehbb = gc.getEnclosingHandlers();
    Enumeration<BasicBlock> enumerator = ehbb.enumerator();

    ExceptionHandlerBasicBlock rethrow = (ExceptionHandlerBasicBlock) enumerator.nextElement();
    assertThat(enumerator.hasMoreElements(), is(false));
    assertThatRethrowBlockIsCorrect(inlineSequence, lockObject, rethrow);

    checkCodeOrderForRethrowBlock(gc, prologue, epilogue, rethrow);

    assertTrue(rethrow.mayThrowUncaughtException());
    assertTrue(rethrow.canThrowExceptions());
    OutEdgeEnumeration outEdges = rethrow.outEdges();
    boolean leadsToExit = false;
    while (outEdges.hasMoreElements()) {
      GraphEdge nextElement = outEdges.nextElement();
      BasicBlock target = (BasicBlock) nextElement.to();
      if (target == gc.getCfg().exit()) {
        leadsToExit = true;
      }
    }
    assertTrue(leadsToExit);
    assertSame(rethrow, gc.getUnlockAndRethrow());

    ExceptionHandlerBasicBlockBag ehbbb = gc.getEnclosingHandlers();
    assertNull(ehbbb.getCaller());
    assertThatEnclosingHandlersContainRethrow(rethrow, ehbbb);
  }

  private void assertThatExceptionHandlersWereGenerated(GenerationContext gc) {
    assertThat(gc.generatedExceptionHandlers(), is(true));
  }

  private void assertThatFirstInstructionEpilogueIsMonitorExit(
      InlineSequence inlineSequence, BasicBlock epilogue) {
    Instruction firstEpilogueInstruction = epilogue.firstRealInstruction();
    assertThat(firstEpilogueInstruction.getBytecodeIndex(),is(SYNCHRONIZED_MONITOREXIT_BCI));
    assertTrue(firstEpilogueInstruction.position().equals(inlineSequence));
    assertThat(firstEpilogueInstruction.operator(),is(MONITOREXIT));
  }

  private void assertThatGuardIsCorrectForMonitorEnter(
      Instruction inst) {
    Operand guard = MonitorOp.getGuard(inst);
    assertTrue(guard.similar(new TrueGuardOperand()));
  }

  private RegisterOperand buildLockObjectForInstanceMethod(NormalMethod nm,
      RegisterOperand thisOp, GenerationContext gc) {
    return gc.makeLocal(0, thisOp.getType());
  }

  private RegisterOperand getThisOperand(GenerationContext gc) {
    return gc.getArguments()[0].asRegister();
  }

  private void assertThatLocalsForInstanceMethodWithoutParametersAreCorrect(NormalMethod nm,
      GenerationContext gc) {
    assertThatNumberOfParametersIs(gc, 1);
    RegisterOperand thisOperand = getThisOperand(gc);
    assertThatRegOpHoldsClassType(thisOperand);
    assertThatRegOpHasDeclaredType(thisOperand);

    Register thisLocalReg = gc.localReg(0, nm.getDeclaringClass().getTypeRef());
    assertThatRegOpIsLocalRegOfRegister(thisOperand, thisLocalReg);
  }

  private void assertInstructionIsMonitorEnterInPrologue(
      Instruction secondInstruction, InlineSequence inlineSequence) {
    assertThat(secondInstruction.operator(),is(MONITORENTER));
    assertTrue(secondInstruction.position().equals(inlineSequence));
    assertThat(secondInstruction.getBytecodeIndex(),is(SYNCHRONIZED_MONITORENTER_BCI));
  }

  private NormalMethod getNormalMethodForTest(String methodName) throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, methodName);
    return nm;
  }

  private NormalMethod getNormalMethodForTest(String methodName, Class<?>[] argumentTypes) throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, methodName, argumentTypes);
    return nm;
  }

  @Test
  public void threadLocalSynchronizedStaticMethodDoesNotHaveMonitorEnterAndMonitorExit() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptySynchronizedStaticMethod");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    opts.ESCAPE_INVOKEE_THREAD_LOCAL = true;
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    assertThatStateIsCorrectForUnsynchronizedEmptyStaticMethod(nm, cm, io, gc);

  }

  private void assertThatStateIsCorrectForUnsynchronizedEmptyStaticMethod(
      NormalMethod nm, CompiledMethod cm, InlineOracle io, GenerationContext gc) {
    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 0);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatBlockOnlyHasOneRealInstruction(prologue);

    assertThatEpilogueIsForVoidReturnMethod(gc, inlineSequence);

    assertThatExitBlockIsSetCorrectly(gc);
    assertThatRegisterPoolExists(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);
    assertThatGCHasNoExceptionHandlers(gc);

    assertThatChecksWontBeSkipped(gc);

    assertThatNoRethrowBlockExists(gc);
  }

  @Test
  public void osrSpecializedMethodsDoNotHaveMonitorEnterButHaveMonitorExit() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptySynchronizedStaticMethodForOSR");

    byte[] osrPrologue = {};
    nm.setForOsrSpecialization(osrPrologue, (short) 0);
    assertTrue(nm.isForOsrSpecialization());

    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);


    InlineSequence inlineSequence = new InlineSequence(nm);
    assertThatInlineSequenceWasSetCorrectly(gc, inlineSequence);

    assertThatNumberOfParametersIs(gc, 0);

    BasicBlock prologue = gc.getPrologue();
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatNumberOfRealInstructionsMatches(prologue, 1);

    BasicBlock epilogue = gc.getEpilogue();
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);

    assertThatFirstInstructionEpilogueIsMonitorExit(inlineSequence, epilogue);

    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    assertThatReturnInstructionReturnsVoid(epilogue);

    assertThatNumberOfRealInstructionsMatches(epilogue, 2);

    assertThatExitBlockIsSetCorrectly(gc);
    assertThatRegisterPoolExists(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThatReturnValueIsVoid(gc);

    assertThatExceptionHandlersWereGenerated(gc);

    Operand expectedLockObject = buildLockObjectForStaticMethod(nm);
    assertThatUnlockAndRethrowBlockIsCorrect(gc, inlineSequence, prologue,
        expectedLockObject, epilogue);

    assertThatChecksWontBeSkipped(gc);
  }

  private RegisterOperand createMockRegisterOperand(TypeReference tr) {
    Register r = new Register(currentRegisterNumber--);
    return new RegisterOperand(r, tr);
  }

  @Test
  public void basicChildContextsWorkCorrectly() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] classArgs = {Object.class};
    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithObjectParamAndReturnValue", classArgs);

    MethodOperand methOp = MethodOperand.STATIC(callee);
    RegisterOperand result = createMockRegisterOperand(TypeReference.JavaLangObject);
    Instruction callInstr = Call.create(CALL, result, null, methOp, 1);
    RegisterOperand objectParam = createMockRegisterOperand(TypeReference.JavaLangObject);
    Call.setParam(callInstr, 0, objectParam);
    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(0, objectParam);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());
    RegisterOperand firstArg = child.getArguments()[0].asRegister();
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    assertSame(result.getRegister(), child.getResultReg());
    assertTrue(child.getResultReg().spansBasicBlock());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();
    Instruction move = prologueRealInstr.nextElement();
    RegisterOperand objectParamInChild = objectParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE,
        expectedLocalForObjectParam, objectParamInChild, move);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.getEpilogue().forwardRealInstrEnumerator();
    assertThatNoMoreInstructionsExist(epilogueRealInstr);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertMoveOperationIsCorrect(Instruction call,
      Operator moveOperator, RegisterOperand formal, RegisterOperand actual, Instruction move) {
    assertSame(call.position(), move.position());
    assertThat(move.operator(), is(moveOperator));
    assertThat(move.getBytecodeIndex(), is(PROLOGUE_BCI));
    RegisterOperand moveResult = Move.getResult(move);
    assertTrue(moveResult.sameRegisterPropertiesAs(formal));
    RegisterOperand moveValue = Move.getVal(move).asRegister();
    assertTrue(moveValue.sameRegisterPropertiesAs(actual));
  }

  private void assertThatStateIsCopiedFromParentToChild(
      GenerationContext parent, NormalMethod callee, GenerationContext child, ExceptionHandlerBasicBlockBag ebag) {
    assertThat(child.getMethod(), is(callee));
    assertThat(child.getOriginalMethod(), is(parent.getOriginalMethod()));
    assertThat(child.getOriginalCompiledMethod(), is(parent.getOriginalCompiledMethod()));
    assertThat(child.getOptions(), is(parent.getOptions()));
    assertThat(child.getTemps(), is(parent.getTemps()));
    assertThat(child.getExit(), is(parent.getExit()));
    assertThat(child.getInlinePlan(), is(parent.getInlinePlan()));
    assertThat(child.getEnclosingHandlers(), is(ebag));
  }

  @Test
  public void getOriginalMethodReturnsTheOutermostMethod() throws Exception {
    GenerationContext outermostContext = createMostlyEmptyContext("emptyStaticMethodWithNoBoundCheckAnnotation");
    NormalMethod outermostCaller = getNormalMethodForTest("emptyStaticMethodWithNoBoundCheckAnnotation");

    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithNoCheckStoreAnnotation");
    Instruction noCheckStoreInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, outermostCaller);
    GenerationContext nextInnerContext = outermostContext.createChildContext(ebag, callee, noCheckStoreInstr);
    assertThat(nextInnerContext.getOriginalMethod(), is(outermostCaller));

    NormalMethod nextInnerCallee = getNormalMethodForTest("emptyStaticMethodWithNoNullCheckAnnotation");
    Instruction noNullCheckInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nextInnerCallee);
    GenerationContext innermostContext = nextInnerContext.createChildContext(ebag, nextInnerCallee, noNullCheckInstr);
    assertThat(innermostContext.getOriginalMethod(), is(outermostCaller));
  }

  @Test
  public void inliningInstanceMethodWithRegisterReceiver() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod callee = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);

    MethodOperand methOp = MethodOperand.VIRTUAL(callee.getMemberRef().asMethodReference(), callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 5);

    RegisterOperand receiver = createMockRegisterOperand(TypeReference.JavaLangObject);
    assertFalse(receiver.isPreciseType());
    assertFalse(receiver.isDeclaredType());
    receiver.setPreciseType();
    Call.setParam(callInstr, 0, receiver);

    RegisterOperand objectParam = prepareCallWithObjectParam(callInstr);
    RegisterOperand doubleParam = prepareCallWithDoubleParam(callInstr);
    RegisterOperand intParam = prepareCallWithIntParam(callInstr);
    RegisterOperand longParam = prepareCallWithLongParam(callInstr);

    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand thisArg = child.getArguments()[0].asRegister();
    assertFalse(thisArg.isPreciseType());
    assertTrue(thisArg.isDeclaredType());
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertSame(thisArg.getType(), calleeClass);

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg);
    assertTrue(thisArg.sameRegisterPropertiesAs(expectedLocalForReceiverParam));

    RegisterOperand firstArg = child.getArguments()[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.getArguments()[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.getArguments()[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.getArguments()[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();

    Instruction receiverMove = prologueRealInstr.nextElement();
    RegisterOperand expectedReceiver = receiver.copy().asRegister();
    narrowRegOpToCalleeClass(expectedReceiver, calleeClass);
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForReceiverParam, expectedReceiver, receiverMove);

    Instruction objectMove = prologueRealInstr.nextElement();
    RegisterOperand objectParamCopy = objectParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForObjectParam, objectParamCopy, objectMove);

    Instruction doubleMove = prologueRealInstr.nextElement();
    RegisterOperand doubleParamCopy = doubleParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, DOUBLE_MOVE, expectedLocalForDoubleParam, doubleParamCopy, doubleMove);

    Instruction intMove = prologueRealInstr.nextElement();
    RegisterOperand intParamCopy = intParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, INT_MOVE, expectedLocalForIntParam, intParamCopy, intMove);

    Instruction longMove = prologueRealInstr.nextElement();
    RegisterOperand longParamCopy = longParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, LONG_MOVE, expectedLocalForLongParam, longParamCopy, longMove);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    BasicBlock epilogue = child.getEpilogue();
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertThatEpilogueIsEmpty(BasicBlock epilogue) {
    assertTrue(epilogue.isEmpty());
  }

  private void assertThatEpilogueLabelIsCorrectForInlinedMethod(
      GenerationContext child, InlineSequence inlineSequence,
      BasicBlock epilogue) {
    assertThat(child.getCfg().lastInCodeOrder(), is(epilogue));
    assertThat(epilogue.firstInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertTrue(epilogue.firstInstruction().position().equals(inlineSequence));
  }

  private void assertThatNoRethrowBlockExists(GenerationContext child) {
    assertNull(child.getUnlockAndRethrow());
  }

  private void assertThatNoMoreInstructionsExist(
      Enumeration<Instruction> instrEnumeration) {
    assertFalse(instrEnumeration.hasMoreElements());
  }

  private void assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(
      ExceptionHandlerBasicBlockBag ebag, int nodeNumber,
      GenerationContext child) {
    assertThat(child.getPrologue().firstInstruction().getBytecodeIndex(), is(PROLOGUE_BCI));
    assertThat(child.getPrologue().firstInstruction().position(), is(child.getInlineSequence()));
    assertThat(child.getPrologue().getNumber(), is(nodeNumber));
    assertThat(child.getPrologue().exceptionHandlers(), is(ebag));
    assertThat(child.getEpilogue().firstInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertThat(child.getEpilogue().firstInstruction().position(), is(child.getInlineSequence()));
    assertThat(child.getEpilogue().getNumber(), is(nodeNumber + 1));
    assertThat(child.getEpilogue().exceptionHandlers(), is(ebag));
    int newNodeNumber = nodeNumber + 2;
    assertThat(child.getCfg().numberOfNodes(), is(newNodeNumber));
    assertThat(child.getCfg().firstInCodeOrder(), is(child.getPrologue()));
    assertThat(child.getCfg().lastInCodeOrder(), is(child.getEpilogue()));
  }

  @Test
  public void inliningInstanceMethodWithRegisterReceiverNoNarrowing() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod callee = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);

    MethodOperand methOp = MethodOperand.VIRTUAL(callee.getMemberRef().asMethodReference(), callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 5);

    RegisterOperand receiver = createMockRegisterOperand(callee.getDeclaringClass().getTypeRef());
    assertFalse(receiver.isPreciseType());
    assertFalse(receiver.isDeclaredType());
    receiver.setPreciseType();
    Call.setParam(callInstr, 0, receiver);

    RegisterOperand objectParam = prepareCallWithObjectParam(callInstr);
    RegisterOperand doubleParam = prepareCallWithDoubleParam(callInstr);
    RegisterOperand intParam = prepareCallWithIntParam(callInstr);
    RegisterOperand longParam = prepareCallWithLongParam(callInstr);

    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand thisArg = child.getArguments()[0].asRegister();
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertSame(thisArg.getType(), calleeClass);

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg);
    assertTrue(thisArg.sameRegisterPropertiesAs(expectedLocalForReceiverParam));

    RegisterOperand firstArg = child.getArguments()[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.getArguments()[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.getArguments()[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.getArguments()[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();

    Instruction receiverMove = prologueRealInstr.nextElement();
    RegisterOperand expectedReceiver = receiver.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForReceiverParam, expectedReceiver, receiverMove);

    Instruction objectMove = prologueRealInstr.nextElement();
    RegisterOperand objectParamCopy = objectParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForObjectParam, objectParamCopy, objectMove);

    Instruction doubleMove = prologueRealInstr.nextElement();
    RegisterOperand doubleParamCopy = doubleParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, DOUBLE_MOVE, expectedLocalForDoubleParam, doubleParamCopy, doubleMove);

    Instruction intMove = prologueRealInstr.nextElement();
    RegisterOperand intParamCopy = intParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, INT_MOVE, expectedLocalForIntParam, intParamCopy, intMove);

    Instruction longMove = prologueRealInstr.nextElement();
    RegisterOperand longParamCopy = longParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, LONG_MOVE, expectedLocalForLongParam, longParamCopy, longMove);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    BasicBlock epilogue = child.getEpilogue();
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private RegisterOperand prepareCallWithLongParam(Instruction callInstr) {
    RegisterOperand longParam = createMockRegisterOperand(TypeReference.Long);
    Call.setParam(callInstr, 4, longParam);
    return longParam;
  }

  private RegisterOperand prepareCallWithIntParam(Instruction callInstr) {
    RegisterOperand intParam = createMockRegisterOperand(TypeReference.Int);
    Call.setParam(callInstr, 3, intParam);
    return intParam;
  }

  private RegisterOperand prepareCallWithDoubleParam(Instruction callInstr) {
    RegisterOperand doubleParam = createMockRegisterOperand(TypeReference.Double);
    Call.setParam(callInstr, 2, doubleParam);
    return doubleParam;
  }

  @Test
  public void inliningMethodWithConstantReceiver() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod callee = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);

    MethodOperand methOp = MethodOperand.VIRTUAL(callee.getMemberRef().asMethodReference(), callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 5);

    ObjectConstantOperand receiver = new ObjectConstantOperand(new MethodsForTests(), null);
    Call.setParam(callInstr, 0, receiver);

    RegisterOperand objectParam = prepareCallWithObjectParam(callInstr);
    RegisterOperand doubleParam = prepareCallWithDoubleParam(callInstr);
    RegisterOperand intParam = prepareCallWithIntParam(callInstr);
    RegisterOperand longParam = prepareCallWithLongParam(callInstr);

    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    Operand thisArg = child.getArguments()[0];

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg.getType());
    expectedLocalForReceiverParam.setPreciseType();
    RegisterOperand expectedNullCheckGuard = child.makeNullCheckGuard(expectedLocalForReceiverParam.getRegister());
    BC2IR.setGuardForRegOp(expectedLocalForReceiverParam, expectedNullCheckGuard);
    assertNotNull(expectedNullCheckGuard);

    RegisterOperand firstArg = child.getArguments()[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.getArguments()[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.getArguments()[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.getArguments()[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();
    Instruction guardMove = prologueRealInstr.nextElement();
    assertThat(guardMove.operator(), is(GUARD_MOVE));
    assertThat(guardMove.getBytecodeIndex(), is(UNKNOWN_BCI));
    RegisterOperand guardMoveResult = Move.getResult(guardMove);
    assertTrue(guardMoveResult.sameRegisterPropertiesAs(expectedNullCheckGuard));
    Operand moveValue = Move.getVal(guardMove);
    assertTrue(moveValue.isTrueGuard());
    assertNull(guardMove.position());

    Instruction receiverMove = prologueRealInstr.nextElement();
    Operand expectedReceiver = receiver.copy();
    //TODO definite non-nullness of constant operand is not being verified
    assertSame(callInstr.position(), receiverMove.position());
    assertThat(receiverMove.operator(), is(REF_MOVE));
    assertThat(receiverMove.getBytecodeIndex(), is(PROLOGUE_BCI));
    RegisterOperand receiverMoveResult = Move.getResult(receiverMove);
    assertTrue(receiverMoveResult.sameRegisterPropertiesAsExceptForGuardWhichIsSimilar(expectedLocalForReceiverParam));
    Operand receiverMoveValue = Move.getVal(receiverMove);
    assertTrue(receiverMoveValue.similar(expectedReceiver));

    Instruction objectMove = prologueRealInstr.nextElement();
    RegisterOperand objectParamCopy = objectParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForObjectParam, objectParamCopy, objectMove);

    Instruction doubleMove = prologueRealInstr.nextElement();
    RegisterOperand doubleParamCopy = doubleParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, DOUBLE_MOVE, expectedLocalForDoubleParam, doubleParamCopy, doubleMove);

    Instruction intMove = prologueRealInstr.nextElement();
    RegisterOperand intParamCopy = intParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, INT_MOVE, expectedLocalForIntParam, intParamCopy, intMove);

    Instruction longMove = prologueRealInstr.nextElement();
    RegisterOperand longParamCopy = longParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, LONG_MOVE, expectedLocalForLongParam, longParamCopy, longMove);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    BasicBlock epilogue = child.getEpilogue();
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private RegisterOperand prepareCallWithObjectParam(Instruction callInstr) {
    RegisterOperand objectParam = createMockRegisterOperand(TypeReference.JavaLangObject);
    Call.setParam(callInstr, 1, objectParam);
    return objectParam;
  }

  @Test(expected = OptimizingCompilerException.class)
  public void invalidReceiverCausesException() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    NormalMethod callee = getNormalMethodForTest("emptyInstanceMethodWithoutAnnotations");

    MethodOperand methOp = MethodOperand.VIRTUAL(callee.getMemberRef().asMethodReference(), callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 1);

    Operand receiver = new InvalidReceiverOperand();
    Call.setParam(callInstr, 0, receiver);
    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    gc.createChildContext(ebag, callee, callInstr);
  }

  private static class InvalidReceiverOperand extends Operand {

    @Override
    public Operand copy() {
      return new InvalidReceiverOperand();
    }

    @Override
    public boolean similar(Operand op) {
      return op instanceof InvalidReceiverOperand;
    }

  }
  @Test
  public void inliningInstanceMethodWithNarrowingOfReferenceParam() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] argumentTypes = {MethodsForTests.class};
    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithReferenceParam", argumentTypes);

    MethodOperand methOp = MethodOperand.VIRTUAL(callee.getMemberRef().asMethodReference(), callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 1);

    RegisterOperand objectParam = createMockRegisterOperand(TypeReference.JavaLangObject);
    assertFalse(objectParam.isPreciseType());
    assertFalse(objectParam.isDeclaredType());
    objectParam.setPreciseType();
    Call.setParam(callInstr, 0, objectParam);
    RegisterOperand objectParamCopy = objectParam.copy().asRegister();

    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand objectParamArg = child.getArguments()[0].asRegister();
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertThatRegOpWasNarrowedToCalleeClass(objectParamArg, calleeClass);

    RegisterOperand expectedLocalForObjectParam = child.makeLocal(0, objectParamArg);
    assertTrue(objectParamArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();

    Instruction objectMove = prologueRealInstr.nextElement();
    narrowRegOpToCalleeClass(objectParamCopy, calleeClass);
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForObjectParam, objectParamCopy, objectMove);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    BasicBlock epilogue = child.getEpilogue();
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private void narrowRegOpToCalleeClass(RegisterOperand expectedObjectParam,
      TypeReference calleeClass) {
    expectedObjectParam.clearPreciseType();
    expectedObjectParam.setType(calleeClass);
    expectedObjectParam.setDeclaredType();
  }

  private void assertThatRegOpWasNarrowedToCalleeClass(RegisterOperand regOp,
      TypeReference calleeClass) {
    assertFalse(regOp.isPreciseType());
    assertTrue(regOp.isDeclaredType());
    assertSame(regOp.getType(), calleeClass);
  }

  @Test
  public void annotationsAreTreatedCorrectlyForInlinedMethods() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithNoBoundCheckAnnotation");
    Instruction noBoundsInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext noBoundsContext = gc.createChildContext(ebag, callee, noBoundsInstr);
    assertTrue(noBoundsContext.noBoundsChecks());
    assertFalse(noBoundsContext.noNullChecks());
    assertFalse(noBoundsContext.noCheckStoreChecks());

    callee = getNormalMethodForTest("emptyStaticMethodWithNoCheckStoreAnnotation");
    Instruction noCheckStoreInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext noCheckStoreContext = gc.createChildContext(ebag, callee, noCheckStoreInstr);
    assertFalse(noCheckStoreContext.noBoundsChecks());
    assertFalse(noCheckStoreContext.noNullChecks());
    assertTrue(noCheckStoreContext.noCheckStoreChecks());

    callee = getNormalMethodForTest("emptyStaticMethodWithNoNullCheckAnnotation");
    Instruction noNullChecks = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext noNullCheckContext = gc.createChildContext(ebag, callee, noNullChecks);
    assertFalse(noNullCheckContext.noBoundsChecks());
    assertTrue(noNullCheckContext.noNullChecks());
    assertFalse(noNullCheckContext.noCheckStoreChecks());
  }

  private Instruction buildCallInstructionForStaticMethodWithoutReturn(
      NormalMethod callee, NormalMethod caller) {
    MethodOperand methOp = MethodOperand.STATIC(callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 0);
    callInstr.setPosition(new InlineSequence(caller));

    return callInstr;
  }

  @Test
  public void rethrowBlockIsCorrectForSynchronizedMethodInlinedIntoSynchronizedMethod() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptySynchronizedStaticMethod");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    NormalMethod callee = getNormalMethodForTest("emptySynchronizedStaticMethod");
    Instruction callInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    ExceptionHandlerBasicBlockBag ebag = gc.getEnclosingHandlers();
    GenerationContext childContext = gc.createChildContext(ebag, callee, callInstr);

    assertThatExceptionHandlersWereGenerated(childContext);

    Operand expectedLockObject = buildLockObjectForStaticMethod(callee);

    assertThatUnlockAndRethrowBlockIsCorrectForInlinedMethod(gc, childContext, childContext.getInlineSequence(),
        childContext.getPrologue(), expectedLockObject, childContext.getEpilogue());

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertThatUnlockAndRethrowBlockIsCorrectForInlinedMethod(GenerationContext parentContext,
      GenerationContext childContext, InlineSequence inlineSequence, BasicBlock prologue,
      Operand lockObject, BasicBlock epilogue) {
    ExceptionHandlerBasicBlockBag ehbb = childContext.getEnclosingHandlers();
    Enumeration<BasicBlock> enumerator = ehbb.enumerator();

    assertThat(childContext.getUnlockAndRethrow().exceptionHandlers(), is(parentContext.getEnclosingHandlers()));

    ExceptionHandlerBasicBlock rethrow = (ExceptionHandlerBasicBlock) enumerator.nextElement();
    assertSame(rethrow, childContext.getUnlockAndRethrow());
    assertThatRethrowBlockIsCorrect(inlineSequence, lockObject, rethrow);
    checkCodeOrderForRethrowBlock(childContext, prologue, epilogue, rethrow);

    ExceptionHandlerBasicBlockBag parentHandlers = parentContext.getEnclosingHandlers();
    Enumeration<BasicBlock> parentHandlerBBEnum = parentHandlers.enumerator();
    HashSet<BasicBlock> parentHandlerBBs = new HashSet<BasicBlock>();
    while (parentHandlerBBEnum.hasMoreElements()) {
      parentHandlerBBs.add(parentHandlerBBEnum.nextElement());
    }
    BasicBlock childRethrow = childContext.getUnlockAndRethrow();
    OutEdgeEnumeration outEdges = childRethrow.outEdges();
    boolean linkedToAllBlocksFromParentHandler = true;
    while (outEdges.hasMoreElements()) {
      BasicBlock target = (BasicBlock) outEdges.nextElement().to();
      if (!parentHandlerBBs.contains(target) && target != childContext.getExit()) {
        linkedToAllBlocksFromParentHandler = false;
        break;
      }
    }
    assertTrue(linkedToAllBlocksFromParentHandler);

    ExceptionHandlerBasicBlockBag ehbbb = childContext.getEnclosingHandlers();
    assertSame(parentContext.getEnclosingHandlers(), ehbbb.getCaller());
    assertThatEnclosingHandlersContainRethrow(rethrow, ehbbb);
  }

  private void assertThatEnclosingHandlersContainRethrow(
      ExceptionHandlerBasicBlock rethrow, ExceptionHandlerBasicBlockBag ehbbb) {
    boolean rethrowFound = false;
    Enumeration<BasicBlock> exceptionHandlerBasicBlocks = ehbbb.enumerator();
    while (exceptionHandlerBasicBlocks.hasMoreElements()) {
      BasicBlock nextElement = exceptionHandlerBasicBlocks.nextElement();
      if (nextElement == rethrow) {
        rethrowFound = true;
      }
    }
    assertTrue(rethrowFound);
  }

  private void assertThatRethrowBlockIsCorrect(InlineSequence inlineSequence,
      Operand lockObject, ExceptionHandlerBasicBlock rethrow) {
    Enumeration<TypeOperand> exceptionTypes = rethrow.getExceptionTypes();
    TypeOperand firstHandledException = exceptionTypes.nextElement();
    assertThat(exceptionTypes.hasMoreElements(), is(false));
    assertTrue(firstHandledException.similar(new TypeOperand(RVMType.JavaLangThrowableType)));

    Enumeration<Instruction> rethrowInstructions = rethrow.forwardRealInstrEnumerator();
    Instruction firstInstructionInRethrow = rethrowInstructions.nextElement();

    assertThat(firstInstructionInRethrow.operator(),  is(GET_CAUGHT_EXCEPTION));
    assertThat(firstInstructionInRethrow.getBytecodeIndex(), is(SYNTH_CATCH_BCI));
    assertTrue(firstInstructionInRethrow.position().equals(inlineSequence));
    RegisterOperand catchExceptionObject = Nullary.getResult(firstInstructionInRethrow);
    assertThat(catchExceptionObject.getType(), is(TypeReference.JavaLangThrowable));

    Instruction secondInstructionInRethrow = rethrowInstructions.nextElement();
    assertThatNoMoreInstructionsExist(rethrowInstructions);
    assertThat(secondInstructionInRethrow.operator(), is(CALL));
    MethodOperand methodOp = Call.getMethod(secondInstructionInRethrow);
    assertTrue(methodOp.getTarget().equals(Entrypoints.unlockAndThrowMethod));
    assertTrue(methodOp.isNonReturningCall());
    Operand callAddress = Call.getAddress(secondInstructionInRethrow);
    Address actualAddress = callAddress.asAddressConstant().value;
    Address expectedAddress = Entrypoints.unlockAndThrowMethod.getOffset().toWord().toAddress();
    assertTrue(actualAddress.EQ(expectedAddress));
    Operand lockObjectFromRethrow = Call.getParam(secondInstructionInRethrow, 0);
    assertTrue(lockObjectFromRethrow.similar(lockObject));
    RegisterOperand catchOperand = Call.getParam(secondInstructionInRethrow, 1).asRegister();
    assertTrue(catchOperand.sameRegisterPropertiesAs(catchExceptionObject));

    assertTrue(rethrow.mayThrowUncaughtException());
    assertTrue(rethrow.canThrowExceptions());
  }

  @Test
  public void unintBeginAndUnintEndAreAddedWhenNecessary() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    assertThatStateIsCorrectForUnsynchronizedEmptyStaticMethod(nm, cm, io, gc);

    NormalMethod callee = getNormalMethodForTest("emptyStaticUninterruptibleMethod");

    Instruction callInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 23456;
    gc.getCfg().setNumberOfNodes(nodeNumber);

    GenerationContext child = gc.createChildContext(ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position(), callInstr);
    assertEquals(expectedInlineSequence, child.getInlineSequence());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();
    Instruction unintBegin = prologueRealInstr.nextElement();
    assertThatInstructionIsUnintMarker(unintBegin, UNINT_BEGIN);
    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.getEpilogue().forwardRealInstrEnumerator();
    Instruction unintEnd = epilogueRealInstr.nextElement();
    assertThatInstructionIsUnintMarker(unintEnd, UNINT_END);
    assertThatNoMoreInstructionsExist(epilogueRealInstr);
  }

  @Test
  public void unintBeginAndUnintEndAreNotAddedWhenDirectParentHasThem() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyStaticUninterruptibleMethod");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    assertThatStateIsCorrectForUnsynchronizedEmptyStaticMethod(nm, cm, io, gc);

    NormalMethod interruptibleCallee = getNormalMethodForTest("emptyStaticUninterruptibleMethod");
    Instruction callInstr = buildCallInstructionForStaticMethodWithoutReturn(interruptibleCallee, nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    GenerationContext child = gc.createChildContext(ebag, interruptibleCallee, callInstr);

    Enumeration<Instruction> prologueRealInstr = child.getPrologue().forwardRealInstrEnumerator();
    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.getEpilogue().forwardRealInstrEnumerator();
    assertThatNoMoreInstructionsExist(epilogueRealInstr);
  }

  private ExceptionHandlerBasicBlockBag getMockEbag() {
    ExceptionHandlerBasicBlockBag ebag = new ExceptionHandlerBasicBlockBag(null, null);
    return ebag;
  }

  private void assertThatInstructionIsUnintMarker(Instruction instr,
      Operator markerType) {
    assertTrue(Empty.conforms(instr));
    assertThat(instr.operator(), is(markerType));
  }

  @Test
  public void transferStateUpdatesFieldsInParentFromChild() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext parent = new GenerationContext(nm, null, cm, opts, io);
    int targetNumberOfNodes = 23456789;
    assertThatContextIsInExpectedState(parent, targetNumberOfNodes);

    NormalMethod interruptibleCallee = getNormalMethodForTest("emptyStaticMethodWithoutAnnotations");
    Instruction callInstr = buildCallInstructionForStaticMethodWithoutReturn(interruptibleCallee, nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    GenerationContext child = parent.createChildContext(ebag, interruptibleCallee, callInstr);
    setTransferableProperties(targetNumberOfNodes, child);

    child.transferStateToParent();

    assertThatStateWasTransferedToOtherContext(parent, targetNumberOfNodes);
  }

  private void setTransferableProperties(int targetNumberOfNodes,
      GenerationContext context) {
    context.forceFrameAllocation();
    context.markExceptionHandlersAsGenerated();
    context.getCfg().setNumberOfNodes(targetNumberOfNodes);
  }

  private void assertThatStateWasTransferedToOtherContext(
      GenerationContext otherContext, int targetNumberOfNodes) {
    assertTrue(otherContext.requiresStackFrame());
    assertTrue(otherContext.generatedExceptionHandlers());
    assertTrue(otherContext.getCfg().numberOfNodes() == targetNumberOfNodes);
  }

  private void assertThatContextIsInExpectedState(GenerationContext parent,
      int targetNumberOfNodes) {
    assertFalse(parent.requiresStackFrame());
    assertFalse(parent.generatedExceptionHandlers());
    assertFalse("Assumption in test case wrong, need to change test case", parent.getCfg().numberOfNodes() == targetNumberOfNodes);
  }

  @Test(expected = IllegalStateException.class)
  public void transferStateThrowsExceptionForContextsWithoutParents() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyStaticMethodWithNoBoundCheckAnnotation");
    gc.transferStateToParent();
  }

  @Test(expected = NullPointerException.class)
  public void contextReturnedByGetSynthethicContextContainsOnlyCFG() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("methodForInliningTests");
    ExceptionHandlerBasicBlockBag mockEbag = getMockEbag();

    int parentCfgNodeNumber = gc.getCfg().numberOfNodes();

    GenerationContext synthethicContext = GenerationContext.createSynthetic(gc, mockEbag);

    int synthethicNodeNumber = -100000;
    assertThat(synthethicContext.getCfg().numberOfNodes(), is(synthethicNodeNumber));
    assertThat(synthethicContext.getPrologue().firstInstruction().getBytecodeIndex(), is(PROLOGUE_BCI));
    assertThat(synthethicContext.getPrologue().firstInstruction().position(), is(gc.getInlineSequence()));
    assertThat(synthethicContext.getPrologue().getNumber(), is(parentCfgNodeNumber));
    assertThat(synthethicContext.getPrologue().exceptionHandlers(), is(mockEbag));
    assertThat(synthethicContext.getEpilogue().firstInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertThat(synthethicContext.getEpilogue().firstInstruction().position(), is(gc.getInlineSequence()));
    assertThat(synthethicContext.getEpilogue().getNumber(), is(parentCfgNodeNumber + 1));
    assertThat(synthethicContext.getEpilogue().exceptionHandlers(), is(mockEbag));
    assertThat(gc.getCfg().numberOfNodes(), is(4));
    assertThat(synthethicContext.getCfg().numberOfNodes(), is(synthethicNodeNumber));
    assertThat(synthethicContext.getCfg().firstInCodeOrder(), is(synthethicContext.getPrologue()));
    assertThat(synthethicContext.getCfg().lastInCodeOrder(), is(synthethicContext.getEpilogue()));

    assertFalse(synthethicContext.requiresStackFrame());
    assertNull(synthethicContext.getArguments());
    assertNull(synthethicContext.getBranchProfiles());
    assertNull(synthethicContext.getEnclosingHandlers());
    assertNull(synthethicContext.getExit());
    assertFalse(synthethicContext.generatedExceptionHandlers());
    assertNull(synthethicContext.getInlinePlan());
    assertNull(synthethicContext.getInlineSequence());
    assertNull(synthethicContext.getMethod());
    assertNull(synthethicContext.getOptions());
    assertNull(synthethicContext.getOriginalCompiledMethod());
    assertNull(synthethicContext.getOriginalMethod());
    assertNull(synthethicContext.getResult());
    assertNull(synthethicContext.getResultReg());
    assertNull(synthethicContext.getTemps());
    assertNull(synthethicContext.getUnlockAndRethrow());

    synthethicContext.resync(); // check that nc_guards are null
  }

  @Test
  public void localRegReturnsRegistersThatAreFlaggedAsLocals() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    Register localReg = gc.localReg(0, nm.getDeclaringClass().getTypeRef());
    assertNotNull(localReg);
    assertTrue(localReg.isLocal());
  }

  @Test
  public void localRegAlwaysReturnsSameRegisterForSameArguments() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    Register regExpected = gc.localReg(0, nm.getDeclaringClass().getTypeRef());
    Register regActual = gc.localReg(0, nm.getDeclaringClass().getTypeRef());
    assertSame(regActual, regExpected);
  }

  @Test(expected = ArrayIndexOutOfBoundsException.class)
  public void localRegInvalidLocalNumberCausesArrayOutOfBoundsException() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyInstanceMethodWithoutAnnotations");
    Register reg = gc.localReg(Integer.MAX_VALUE, TypeReference.Int);
    assertNotNull(reg);
  }

  @Test
  public void localRegDoesNotCheckTypeOfLocals() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyInstanceMethodWithoutAnnotations");
    Register reg = gc.localReg(0, TypeReference.Void);
    assertNotNull(reg);
  }

  @Test
  public void localRegAllowsDifferentTypesForSameLocalNumber() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyInstanceMethodWithoutAnnotations");

    Register longReg = gc.localReg(0, TypeReference.Long);
    assertNotNull(longReg);
    Register referenceReg = gc.localReg(0, TypeReference.JavaLangObject);
    assertNotNull(referenceReg);
    Register intReg = gc.localReg(0, TypeReference.Int);
    assertNotNull(intReg);
    Register doubleReg = gc.localReg(0, TypeReference.Double);
    assertNotNull(doubleReg);
    Register floatReg = gc.localReg(0, TypeReference.Float);
    assertNotNull(floatReg);

    if (VM.BuildForOptCompiler) { // Type not available when opt compiler not included
      Register validationReg = gc.localReg(0, TypeReference.VALIDATION_TYPE);
      assertNotNull(validationReg);
    }
  }

  @Test
  public void localRegistersAreSavedInDifferentPools() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyInstanceMethodWithoutAnnotations");

    Register longReg = gc.localReg(0, TypeReference.Long);

    Register referenceReg = gc.localReg(0, TypeReference.JavaLangObject);
    assertNotSame(longReg, referenceReg);

    Register intReg = gc.localReg(0, TypeReference.Int);
    assertNotSame(longReg, intReg);
    assertNotSame(referenceReg, intReg);

    Register doubleReg = gc.localReg(0, TypeReference.Double);
    assertNotSame(longReg, doubleReg);
    assertNotSame(referenceReg, doubleReg);
    assertNotSame(intReg, doubleReg);

    Register floatReg = gc.localReg(0, TypeReference.Float);
    assertNotSame(longReg, floatReg);
    assertNotSame(referenceReg, floatReg);
    assertNotSame(intReg, floatReg);
    assertNotSame(doubleReg, floatReg);
  }

  @Test
  public void makeLocalUsesLocalRegToDetermineRegisters() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    int localNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    Register expectedRegister = gc.localReg(localNumber, localType);

    RegisterOperand regOp = gc.makeLocal(localNumber, localType);
    assertSame(expectedRegister, regOp.getRegister());
  }

  @Test
  public void packagePrivateMakeLocalReturnsRegWithInheritedFlagsAndGuard() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    int localNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    RegisterOperand regOp = gc.makeLocal(localNumber, localType);
    TrueGuardOperand guard = new TrueGuardOperand();
    regOp.setGuard(guard);

    regOp.setParameter();
    regOp.setNonVolatile();
    regOp.setExtant();
    regOp.setDeclaredType();
    regOp.setPreciseType();
    regOp.setPositiveInt();

    RegisterOperand newRegOpWithInheritance = gc.makeLocal(localNumber, regOp);
    Operand scratchObject = newRegOpWithInheritance.getGuard();
    assertTrue(scratchObject.isTrueGuard());
    assertTrue(newRegOpWithInheritance.isParameter());
    assertTrue(newRegOpWithInheritance.isNonVolatile());
    assertTrue(newRegOpWithInheritance.isExtant());
    assertTrue(newRegOpWithInheritance.isDeclaredType());
    assertTrue(newRegOpWithInheritance.isPreciseType());
    assertTrue(newRegOpWithInheritance.isPositiveInt());
  }

  @Test
  public void getLocalNumberForRegisterReturnsMinusOneIfNotALocal() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyInstanceMethodWithoutAnnotations");
    Register reg = new Register(currentRegisterNumber--);
    reg.setLong();
    reg.setLocal();
    int localNumber = gc.getLocalNumberFor(reg, TypeReference.Long);
    assertThat(localNumber, is(-1));
  }

  @Test
  public void getLocalNumberReturnsLocalNumberForRegistersCreateViaMakeLocal() throws Exception {
    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod nm = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    int thisLocalNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    RegisterOperand thisRegOp = gc.makeLocal(thisLocalNumber, localType);
    Register thisReg = thisRegOp.getRegister();
    assertThat(gc.getLocalNumberFor(thisReg, localType), is(thisLocalNumber));
  }

  @Test
  public void isLocalReturnsTrueForRegistersCreatedViaMakeLocal() throws Exception {
    Class<?>[] argumentTypes = {Object.class, double.class, int.class, long.class};
    NormalMethod nm = getNormalMethodForTest("emptyInstanceMethodWithParams", argumentTypes);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    int thisLocalNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    RegisterOperand thisRegOp = gc.makeLocal(thisLocalNumber, localType);
    assertTrue(gc.isLocal(thisRegOp, thisLocalNumber, localType));
  }

  @Test
  public void makeNullCheckGuardReturnsSameGuardForSameRegister() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    int localNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    RegisterOperand regOp = gc.makeLocal(localNumber, localType);
    Register reg = regOp.getRegister();

    RegisterOperand expectedNullCheckGuard = gc.makeNullCheckGuard(reg);

    RegisterOperand actual = gc.makeNullCheckGuard(reg);
    assertTrue(actual.sameRegisterPropertiesAs(expectedNullCheckGuard));
  }

  @Test
  public void makeNullCheckGuardAlwaysReturnsCopies() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    int localNumber = 0;
    TypeReference localType = nm.getDeclaringClass().getTypeRef();
    RegisterOperand regOp = gc.makeLocal(localNumber, localType);
    Register reg = regOp.getRegister();

    RegisterOperand expectedNullCheckGuard = gc.makeNullCheckGuard(reg);
    RegisterOperand copiedGuard = expectedNullCheckGuard.copy().asRegister();
    expectedNullCheckGuard.setGuard(new TrueGuardOperand());

    RegisterOperand actual = gc.makeNullCheckGuard(reg);
    assertTrue(actual.sameRegisterPropertiesAs(copiedGuard));
    assertNull(actual.getGuard());
  }


  @Test
  public void resyncDeletesNullCheckGuardsThatMapToUnusedRegisters() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    RegisterOperand thisLocal = gc.makeLocal(0, nm.getDeclaringClass().getTypeRef());
    Register thisReg = thisLocal.getRegister();
    RegisterOperand thisNullCheckGuard = gc.makeNullCheckGuard(thisReg);
    assertNotNull(thisNullCheckGuard);

    gc.getTemps().removeRegister(thisReg);

    gc.resync();

    RegisterOperand newNullCheckGuard = gc.makeNullCheckGuard(thisReg);
    assertFalse(newNullCheckGuard.sameRegisterPropertiesAs(thisNullCheckGuard));
  }

  @Test
  public void resyncDoesNotDeleteNullCheckGuardsThatMapToUsedRegisters() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);

    RegisterOperand thisLocal = gc.makeLocal(0, nm.getDeclaringClass().getTypeRef());
    Register thisReg = thisLocal.getRegister();
    RegisterOperand thisNullCheckGuard = gc.makeNullCheckGuard(thisReg);
    assertNotNull(thisNullCheckGuard);

    gc.resync();

    RegisterOperand newNullCheckGuard = gc.makeNullCheckGuard(thisReg);
    assertTrue(newNullCheckGuard.sameRegisterPropertiesAs(thisNullCheckGuard));
  }

  @Test(expected = NullPointerException.class)
  public void noNullCheckGuardsCanBeCreatedAfterCloseWasCalled() throws Exception {
    NormalMethod nm = TestingTools.getNormalMethod(MethodsForTests.class, "emptyInstanceMethodWithoutAnnotations");
    OptOptions opts = new OptOptions();
    GenerationContext gc = new GenerationContext(nm, null, null, opts, null);
    RegisterOperand thisLocal = gc.makeLocal(0, nm.getDeclaringClass().getTypeRef());
    Register thisReg = thisLocal.getRegister();
    gc.close();
    gc.makeNullCheckGuard(thisReg);
  }

  @Test(expected = NullPointerException.class)
  public void resyncMustNotBeCalledAfterClose() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyStaticMethodWithoutAnnotations");
    gc.close();
    gc.resync();
  }

  @Test
  public void methodIsSelectedForDebuggingWithMethodToPrintReturnsTrueIfMethodOfContextMatches() throws Exception {
    String methodName = "emptyStaticMethodWithoutAnnotations";
    NormalMethod nm = getNormalMethodForTest(methodName);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = buildOptionsWithMethodToPrintOptionSet(methodName);
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);
    assertThat(gc.methodIsSelectedForDebuggingWithMethodToPrint(), is(true));
  }

  @Test
  public void methodIsSelectedForDebuggingWithMethodToPrintReturnsTrueIfOutermostParentMatches() throws Exception {
    String methodName = "emptyStaticMethodWithoutAnnotations";
    NormalMethod nm = getNormalMethodForTest(methodName);
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = buildOptionsWithMethodToPrintOptionSet(methodName);
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext gc = new GenerationContext(nm, null, cm, opts, io);

    ExceptionHandlerBasicBlockBag ebag = getMockEbag();
    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithNoCheckStoreAnnotation");
    Instruction noCheckStoreInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext nextInnerContext = gc.createChildContext(ebag, callee, noCheckStoreInstr);

    NormalMethod nextInnerCallee = getNormalMethodForTest("emptyStaticMethodWithNoNullCheckAnnotation");
    Instruction noNullCheckInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nextInnerCallee);
    GenerationContext innermostContext = nextInnerContext.createChildContext(ebag, nextInnerCallee, noNullCheckInstr);

    assertThat(innermostContext.methodIsSelectedForDebuggingWithMethodToPrint(), is(true));
  }

  private OptOptions buildOptionsWithMethodToPrintOptionSet(String methodName) {
    OptOptions opts = new OptOptions();
    opts.processAsOption("-X:opt:", "method_to_print=" + methodName);
    assertThat(opts.hasMETHOD_TO_PRINT(), is(true));
    Iterator<String> iterator = opts.getMETHOD_TO_PRINTs();
    String methodNameFromOpts = iterator.next();
    assertThat(methodNameFromOpts, is(methodName));
    return opts;
  }

  @Test
  public void canSaveInformationAboutOSRBarriers() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("emptyStaticMethodWithoutAnnotations");
    Instruction osrBarrier = createMockOSRBarrier();
    Instruction call = createMockCall();
    gc.saveOSRBarrierForInst(osrBarrier, call);
    Instruction barrier = gc.getOSRBarrierFromInst(call);
    assertThat(barrier, is(osrBarrier));
  }

  @Test
  public void childContextsSaveOSRBarrierInformationInOutermostParent() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext outermost = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] classArgs = {Object.class};
    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithObjectParamAndReturnValue", classArgs);

    MethodOperand methOp = MethodOperand.STATIC(callee);
    RegisterOperand result = createMockRegisterOperand(TypeReference.JavaLangObject);
    Instruction callInstr = Call.create(CALL, result, null, methOp, 1);
    RegisterOperand objectParam = createMockRegisterOperand(TypeReference.JavaLangObject);
    Call.setParam(callInstr, 0, objectParam);
    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    GenerationContext child = outermost.createChildContext(ebag, callee, callInstr);
    Instruction osrBarrier = createMockOSRBarrier();
    Instruction call = createMockCall();
    child.saveOSRBarrierForInst(osrBarrier, call);
    assertThat(outermost.getOSRBarrierFromInst(call), is(osrBarrier));

    GenerationContext child2 = child.createChildContext(ebag, callee, callInstr);
    Instruction osrBarrier2 = createMockOSRBarrier();
    Instruction call2 = createMockCall();
    child2.saveOSRBarrierForInst(osrBarrier2, call2);
    assertThat(outermost.getOSRBarrierFromInst(call2), is(osrBarrier2));
  }

  @Test
  public void childContextsQueryOSRBarrierInformationViaOutermostParent() throws Exception {
    NormalMethod nm = getNormalMethodForTest("methodForInliningTests");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext outermost = new GenerationContext(nm, null, cm, opts, io);

    Class<?>[] classArgs = {Object.class};
    NormalMethod callee = getNormalMethodForTest("emptyStaticMethodWithObjectParamAndReturnValue", classArgs);

    MethodOperand methOp = MethodOperand.STATIC(callee);
    RegisterOperand result = createMockRegisterOperand(TypeReference.JavaLangObject);
    Instruction callInstr = Call.create(CALL, result, null, methOp, 1);
    RegisterOperand objectParam = createMockRegisterOperand(TypeReference.JavaLangObject);
    Call.setParam(callInstr, 0, objectParam);
    callInstr.setPosition(new InlineSequence(nm));
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    GenerationContext child = outermost.createChildContext(ebag, callee, callInstr);
    Instruction osrBarrier = createMockOSRBarrier();
    Instruction call = createMockCall();
    child.saveOSRBarrierForInst(osrBarrier, call);
    assertThat(outermost.getOSRBarrierFromInst(call), is(osrBarrier));
    assertThat(child.getOSRBarrierFromInst(call), is(osrBarrier));

    GenerationContext child2 = outermost.createChildContext(ebag, callee, callInstr);
    assertThat(child2.getOSRBarrierFromInst(call), is(osrBarrier));
  }

  private Instruction createMockCall() {
    return Call.create(CALL, null, null, null, 0);
  }

  private Instruction createMockOSRBarrier() {
    return OsrBarrier.create(OSR_BARRIER, null, 0);
  }

  @Test(expected = NullPointerException.class)
  public void savingNoLongerWorksAfterOSRBarrierInformationWasDiscarded() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("methodForInliningTests");
    gc.discardOSRBarrierInformation();
    Instruction osrBarrier = createMockOSRBarrier();
    Instruction call = createMockCall();
    gc.saveOSRBarrierForInst(osrBarrier, call);
  }

  @Test(expected = NullPointerException.class)
  public void getOSRBarrierCausesNPEAfterOSRBarrierInformationWasDiscarded() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("methodForInliningTests");
    Instruction osrBarrier = createMockOSRBarrier();
    Instruction call = createMockCall();
    gc.saveOSRBarrierForInst(osrBarrier, call);
    gc.discardOSRBarrierInformation();
    gc.getOSRBarrierFromInst(call);
  }

}
