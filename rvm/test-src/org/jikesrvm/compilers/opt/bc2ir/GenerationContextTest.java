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
import org.jikesrvm.junit.runners.RequiresJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

@RunWith(VMRequirements.class)
@Category(RequiresJikesRVM.class)
public class GenerationContextTest {

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
    assertThat(gc.arguments.length, is(numberParams));
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
    assertNotNull(gc.temps);
  }

  private void assertThatChecksWontBeSkipped(GenerationContext gc) {
    assertThat(gc.noCheckStoreChecks(),is(false));
    assertThat(gc.noBoundsChecks(),is(false));
    assertThat(gc.noNullChecks(),is(false));
  }

  private void assertThatGCHasNoExceptionHandlers(GenerationContext gc) {
    assertNull(gc.enclosingHandlers);
    assertThat(gc.generatedExceptionHandlers, is(false));
  }

  private void assertThatReturnValueIsVoid(GenerationContext gc) {
    assertNull(gc.resultReg);
  }

  private void assertThatOriginalCompiledMethodWasSetCorrectly(CompiledMethod cm,
      GenerationContext gc) {
    assertThat(gc.original_cm, is(cm));
  }

  private void assertThatOriginalMethodWasSetCorrectly(NormalMethod nm,
      GenerationContext gc) {
    assertThat(gc.original_method, is(nm));
  }

  private void assertThatInlinePlanWasSetCorrectly(InlineOracle io,
      GenerationContext gc) {
    assertThat(gc.inlinePlan, is(io));
  }

  private void assertThatExitBlockIsSetCorrectly(GenerationContext gc) {
    assertThat(gc.exit, is(gc.cfg.exit()));
  }

  private void assertThatLastInstructionInEpilogueIsReturn(
      GenerationContext gc, BasicBlock epilogue) {
    assertThat(epilogue.lastRealInstruction().operator(), is(RETURN));
    assertThat(epilogue.lastRealInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertThat(epilogue.getNormalOut().nextElement(), is(gc.exit));
  }

  private void assertThatEpilogueBlockIsSetupCorrectly(GenerationContext gc,
      InlineSequence inlineSequence, BasicBlock epilogue) {
    assertThat(gc.cfg.lastInCodeOrder(), is(epilogue));
    assertThat(epilogue.firstInstruction().getBytecodeIndex(), is(EPILOGUE_BLOCK_BCI));
    assertTrue(epilogue.firstInstruction().position.equals(inlineSequence));
  }

  private void assertThatFirstInstructionInPrologueIsPrologue(
      InlineSequence inlineSequence, BasicBlock prologue) {
    assertThat(prologue.firstRealInstruction().getBytecodeIndex(),is(PROLOGUE_BCI));
    assertTrue(prologue.firstRealInstruction().position.equals(inlineSequence));
    assertThat(prologue.firstRealInstruction().operator(),is(IR_PROLOGUE));
  }

  private void assertThatPrologueBlockIsSetupCorrectly(GenerationContext gc,
      InlineSequence inlineSequence, BasicBlock prologue) {
    assertThat(gc.cfg.firstInCodeOrder(), is(prologue));
    assertThat(prologue.firstInstruction().getBytecodeIndex(), is(PROLOGUE_BLOCK_BCI));
    assertTrue(prologue.firstInstruction().position.equals(inlineSequence));
  }

  private void assertThatInlineSequenceWasSetCorrectly(GenerationContext gc,
      InlineSequence inlineSequence) {
    assertThat(gc.inlineSequence.equals(inlineSequence),is(true));
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

    BasicBlock prologue = gc.prologue;
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

    BasicBlock prologue = gc.prologue;
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatBlockOnlyHasOneRealInstruction(prologue);

    BasicBlock epilogue = gc.epilogue;
    assertThatEpilogueBlockIsSetupCorrectly(gc, inlineSequence, epilogue);
    assertThatLastInstructionInEpilogueIsReturn(gc, epilogue);
    RegisterOperand returnValue = Return.getVal(epilogue.lastRealInstruction()).asRegister();
    assertThat(returnValue.getType(),is(TypeReference.Long));

    assertThatExitBlockIsSetCorrectly(gc);

    assertThatDataIsSavedCorrectly(nm, cm, io, gc);

    assertThat(gc.resultReg.isLong(),is(true));
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
    RegisterOperand intRegOp = gc.arguments[1].asRegister();
    assertThat(intRegOp.isInt(), is(true));
    assertThatRegOpHasDeclaredType(intRegOp);
    RegisterOperand objectRegOp = gc.arguments[2].asRegister();
    assertThatRegOpHoldsClassType(objectRegOp);
    assertThatRegOpHasDeclaredType(objectRegOp);
    RegisterOperand doubleRegOp = gc.arguments[3].asRegister();
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

    BasicBlock prologue = gc.prologue;
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
    RegisterOperand objectRegOp = gc.arguments[1].asRegister();
    assertThatRegOpHoldsClassType(objectRegOp);
    assertThatRegOpHasDeclaredType(objectRegOp);
    RegisterOperand doubleRegOp = gc.arguments[2].asRegister();
    assertThat(doubleRegOp.isDouble(), is(true));
    assertThatRegOpHasDeclaredType(doubleRegOp);
    RegisterOperand intRegOp = gc.arguments[3].asRegister();
    assertThat(intRegOp.isInt(), is(true));
    assertThatRegOpHasDeclaredType(intRegOp);
    RegisterOperand longRegOp = gc.arguments[4].asRegister();
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


    BasicBlock prologue = gc.prologue;
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

    BasicBlock prologue = gc.prologue;
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
    BasicBlock epilogue = gc.epilogue;
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

    BasicBlock prologue = gc.prologue;
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

    BasicBlock epilogue = gc.epilogue;
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
    BasicBlock firstBlockInCodeOrder = gc.cfg.firstInCodeOrder();
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

    BasicBlock prologue = gc.prologue;
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

    BasicBlock epilogue = gc.epilogue;
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
    ExceptionHandlerBasicBlockBag ehbb = gc.enclosingHandlers;
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
      if (target == gc.cfg.exit()) {
        leadsToExit = true;
      }
    }
    assertTrue(leadsToExit);
    assertSame(rethrow, gc.unlockAndRethrow);

    ExceptionHandlerBasicBlockBag ehbbb = gc.enclosingHandlers;
    assertNull(ehbbb.getCaller());
    assertThatEnclosingHandlersContainRethrow(rethrow, ehbbb);
  }

  private void assertThatExceptionHandlersWereGenerated(GenerationContext gc) {
    assertThat(gc.generatedExceptionHandlers, is(true));
  }

  private void assertThatFirstInstructionEpilogueIsMonitorExit(
      InlineSequence inlineSequence, BasicBlock epilogue) {
    Instruction firstEpilogueInstruction = epilogue.firstRealInstruction();
    assertThat(firstEpilogueInstruction.getBytecodeIndex(),is(SYNCHRONIZED_MONITOREXIT_BCI));
    assertTrue(firstEpilogueInstruction.position.equals(inlineSequence));
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
    return gc.arguments[0].asRegister();
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
    assertTrue(secondInstruction.position.equals(inlineSequence));
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

    BasicBlock prologue = gc.prologue;
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

    BasicBlock prologue = gc.prologue;
    assertThatPrologueBlockIsSetupCorrectly(gc, inlineSequence, prologue);
    assertThatFirstInstructionInPrologueIsPrologue(inlineSequence, prologue);
    assertThatNumberOfRealInstructionsMatches(prologue, 1);

    BasicBlock epilogue = gc.epilogue;
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
    Register regMinus1 = new Register(-1);
    RegisterOperand result = new RegisterOperand(regMinus1, TypeReference.JavaLangObject);
    Instruction callInstr = Call.create(CALL, result, null, methOp, 1);
    Register regMinus2 = new Register(-2);
    RegisterOperand objectParam = new RegisterOperand(regMinus2, TypeReference.JavaLangObject);
    Call.setParam(callInstr, 0, objectParam);
    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(0, objectParam);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);
    RegisterOperand firstArg = child.arguments[0].asRegister();
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    assertSame(result.getRegister(), child.resultReg);
    assertTrue(child.resultReg.spansBasicBlock());

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();
    Instruction move = prologueRealInstr.nextElement();
    RegisterOperand objectParamInChild = objectParam.copy().asRegister();
    assertMoveOperationIsCorrect(callInstr, REF_MOVE,
        expectedLocalForObjectParam, objectParamInChild, move);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.epilogue.forwardRealInstrEnumerator();
    assertThatNoMoreInstructionsExist(epilogueRealInstr);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertMoveOperationIsCorrect(Instruction call,
      Operator moveOperator, RegisterOperand formal, RegisterOperand actual, Instruction move) {
    assertSame(call.position, move.position);
    assertThat(move.operator, is(moveOperator));
    assertThat(move.bcIndex, is(PROLOGUE_BCI));
    RegisterOperand moveResult = Move.getResult(move);
    assertTrue(moveResult.sameRegisterPropertiesAs(formal));
    RegisterOperand moveValue = Move.getVal(move).asRegister();
    assertTrue(moveValue.sameRegisterPropertiesAs(actual));
  }

  private void assertThatStateIsCopiedFromParentToChild(
      GenerationContext parent, NormalMethod callee, GenerationContext child, ExceptionHandlerBasicBlockBag ebag) {
    assertThat(child.method, is(callee));
    assertThat(child.original_method, is(parent.original_method));
    assertThat(child.original_cm, is(parent.original_cm));
    assertThat(child.options, is(parent.options));
    assertThat(child.temps, is(parent.temps));
    assertThat(child.exit, is(parent.exit));
    assertThat(child.inlinePlan, is(parent.inlinePlan));
    assertThat(child.enclosingHandlers, is(ebag));
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

    Register regIntMin = new Register(Integer.MIN_VALUE);
    RegisterOperand receiver = new RegisterOperand(regIntMin, TypeReference.JavaLangObject);
    assertFalse(receiver.isPreciseType());
    assertFalse(receiver.isDeclaredType());
    receiver.setPreciseType();
    Call.setParam(callInstr, 0, receiver);

    RegisterOperand objectParam = prepareCallWithObjectParam(callInstr);
    RegisterOperand doubleParam = prepareCallWithDoubleParam(callInstr);
    RegisterOperand intParam = prepareCallWithIntParam(callInstr);
    RegisterOperand longParam = prepareCallWithLongParam(callInstr);

    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand thisArg = child.arguments[0].asRegister();
    assertFalse(thisArg.isPreciseType());
    assertTrue(thisArg.isDeclaredType());
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertSame(thisArg.getType(), calleeClass);

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg);
    assertTrue(thisArg.sameRegisterPropertiesAs(expectedLocalForReceiverParam));

    RegisterOperand firstArg = child.arguments[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.arguments[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.arguments[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.arguments[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();

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

    BasicBlock epilogue = child.epilogue;
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
    assertThat(child.cfg.lastInCodeOrder(), is(epilogue));
    assertThat(epilogue.firstInstruction().getBytecodeIndex(), is(EPILOGUE_BCI));
    assertTrue(epilogue.firstInstruction().position.equals(inlineSequence));
  }

  private void assertThatNoRethrowBlockExists(GenerationContext child) {
    assertNull(child.unlockAndRethrow);
  }

  private void assertThatNoMoreInstructionsExist(
      Enumeration<Instruction> instrEnumeration) {
    assertFalse(instrEnumeration.hasMoreElements());
  }

  private void assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(
      ExceptionHandlerBasicBlockBag ebag, int nodeNumber,
      GenerationContext child) {
    assertThat(child.prologue.firstInstruction().bcIndex, is(PROLOGUE_BCI));
    assertThat(child.prologue.firstInstruction().position, is(child.inlineSequence));
    assertThat(child.prologue.getNumber(), is(nodeNumber));
    assertThat(child.prologue.exceptionHandlers, is(ebag));
    assertThat(child.epilogue.firstInstruction().bcIndex, is(EPILOGUE_BCI));
    assertThat(child.epilogue.firstInstruction().position, is(child.inlineSequence));
    assertThat(child.epilogue.getNumber(), is(nodeNumber + 1));
    assertThat(child.epilogue.exceptionHandlers, is(ebag));
    int newNodeNumber = nodeNumber + 2;
    assertThat(child.cfg.numberOfNodes(), is(newNodeNumber));
    assertThat(child.cfg.firstInCodeOrder(), is(child.prologue));
    assertThat(child.cfg.lastInCodeOrder(), is(child.epilogue));
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

    Register regIntMin = new Register(Integer.MIN_VALUE);
    RegisterOperand receiver = new RegisterOperand(regIntMin, callee.getDeclaringClass().getTypeRef());
    assertFalse(receiver.isPreciseType());
    assertFalse(receiver.isDeclaredType());
    receiver.setPreciseType();
    Call.setParam(callInstr, 0, receiver);

    RegisterOperand objectParam = prepareCallWithObjectParam(callInstr);
    RegisterOperand doubleParam = prepareCallWithDoubleParam(callInstr);
    RegisterOperand intParam = prepareCallWithIntParam(callInstr);
    RegisterOperand longParam = prepareCallWithLongParam(callInstr);

    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand thisArg = child.arguments[0].asRegister();
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertSame(thisArg.getType(), calleeClass);

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg);
    assertTrue(thisArg.sameRegisterPropertiesAs(expectedLocalForReceiverParam));

    RegisterOperand firstArg = child.arguments[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.arguments[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.arguments[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.arguments[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();

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

    BasicBlock epilogue = child.epilogue;
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private RegisterOperand prepareCallWithLongParam(Instruction callInstr) {
    Register regMinus5 = new Register(-5);
    RegisterOperand longParam = new RegisterOperand(regMinus5, TypeReference.Long);
    Call.setParam(callInstr, 4, longParam);
    return longParam;
  }

  private RegisterOperand prepareCallWithIntParam(Instruction callInstr) {
    Register regMinus4 = new Register(-4);
    RegisterOperand intParam = new RegisterOperand(regMinus4, TypeReference.Int);
    Call.setParam(callInstr, 3, intParam);
    return intParam;
  }

  private RegisterOperand prepareCallWithDoubleParam(Instruction callInstr) {
    Register regMinus3 = new Register(-3);
    RegisterOperand doubleParam = new RegisterOperand(regMinus3, TypeReference.Double);
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

    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    Operand thisArg = child.arguments[0];

    RegisterOperand expectedLocalForReceiverParam = child.makeLocal(0, thisArg.getType());
    expectedLocalForReceiverParam.setPreciseType();
    RegisterOperand expectedNullCheckGuard = child.makeNullCheckGuard(expectedLocalForReceiverParam.getRegister());
    BC2IR.setGuard(expectedLocalForReceiverParam, expectedNullCheckGuard);
    assertNotNull(expectedNullCheckGuard);

    RegisterOperand firstArg = child.arguments[1].asRegister();
    RegisterOperand expectedLocalForObjectParam = child.makeLocal(1, firstArg);
    assertTrue(firstArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    RegisterOperand secondArg = child.arguments[2].asRegister();
    RegisterOperand expectedLocalForDoubleParam = child.makeLocal(2, secondArg);
    assertTrue(secondArg.sameRegisterPropertiesAs(expectedLocalForDoubleParam));

    RegisterOperand thirdArg = child.arguments[3].asRegister();
    RegisterOperand expectedLocalForIntParam = child.makeLocal(4, thirdArg);
    assertTrue(thirdArg.sameRegisterPropertiesAs(expectedLocalForIntParam));

    RegisterOperand fourthArg = child.arguments[4].asRegister();
    RegisterOperand expectedLocalForLongParam = child.makeLocal(5, fourthArg);
    assertTrue(fourthArg.sameRegisterPropertiesAs(expectedLocalForLongParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();
    Instruction guardMove = prologueRealInstr.nextElement();
    assertThat(guardMove.operator, is(GUARD_MOVE));
    assertThat(guardMove.bcIndex, is(UNKNOWN_BCI));
    RegisterOperand moveResult = Move.getResult(guardMove);
    assertTrue(moveResult.sameRegisterPropertiesAs(expectedNullCheckGuard));
    Operand moveValue = Move.getVal(guardMove);
    assertTrue(moveValue.isTrueGuard());
    assertNull(guardMove.position);

    Instruction receiverMove = prologueRealInstr.nextElement();
    Operand expectedReceiver = receiver.copy();
    //TODO definite non-nullness of constant operand is not being verified
    assertMoveOperationIsCorrectForNonRegisterActual(callInstr, REF_MOVE, expectedLocalForReceiverParam, expectedReceiver, receiverMove);

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

    BasicBlock epilogue = child.epilogue;
    assertThatEpilogueLabelIsCorrectForInlinedMethod(child,
        expectedInlineSequence, epilogue);
    assertThatEpilogueIsEmpty(epilogue);

    assertThatNoRethrowBlockExists(child);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertMoveOperationIsCorrectForNonRegisterActual(Instruction call,
      Operator moveOperator, RegisterOperand formal, Operand actual, Instruction move) {
    assertSame(call.position, move.position);
    assertThat(move.operator, is(moveOperator));
    assertThat(move.bcIndex, is(PROLOGUE_BCI));
    RegisterOperand moveResult = Move.getResult(move);
    assertTrue(moveResult.sameRegisterPropertiesAsExceptForScratchObject(formal));
    Operand moveValue = Move.getVal(move);
    assertTrue(moveValue.similar(actual));
  }

  private RegisterOperand prepareCallWithObjectParam(Instruction callInstr) {
    Register regMinus2 = new Register(-2);
    RegisterOperand objectParam = new RegisterOperand(regMinus2, TypeReference.JavaLangObject);
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
    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    GenerationContext.createChildContext(gc, ebag, callee, callInstr);
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

    Register regIntMin = new Register(Integer.MIN_VALUE);
    RegisterOperand objectParam = new RegisterOperand(regIntMin, TypeReference.JavaLangObject);
    assertFalse(objectParam.isPreciseType());
    assertFalse(objectParam.isDeclaredType());
    objectParam.setPreciseType();
    Call.setParam(callInstr, 0, objectParam);
    RegisterOperand objectParamCopy = objectParam.copy().asRegister();

    callInstr.position = new InlineSequence(nm);
    ExceptionHandlerBasicBlockBag ebag = getMockEbag();

    int nodeNumber = 12345;
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    assertThatReturnValueIsVoid(child);

    RegisterOperand objectParamArg = child.arguments[0].asRegister();
    TypeReference calleeClass = callee.getDeclaringClass().getTypeRef();
    assertThatRegOpWasNarrowedToCalleeClass(objectParamArg, calleeClass);

    RegisterOperand expectedLocalForObjectParam = child.makeLocal(0, objectParamArg);
    assertTrue(objectParamArg.sameRegisterPropertiesAs(expectedLocalForObjectParam));

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();

    Instruction objectMove = prologueRealInstr.nextElement();
    narrowRegOpToCalleeClass(objectParamCopy, calleeClass);
    assertMoveOperationIsCorrect(callInstr, REF_MOVE, expectedLocalForObjectParam, objectParamCopy, objectMove);

    assertThatNoMoreInstructionsExist(prologueRealInstr);

    BasicBlock epilogue = child.epilogue;
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
    GenerationContext noBoundsContext = GenerationContext.createChildContext(gc, ebag, callee, noBoundsInstr);
    assertTrue(noBoundsContext.noBoundsChecks());
    assertFalse(noBoundsContext.noNullChecks());
    assertFalse(noBoundsContext.noCheckStoreChecks());

    callee = getNormalMethodForTest("emptyStaticMethodWithNoCheckStoreAnnotation");
    Instruction noCheckStoreInstr = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext noCheckStoreContext = GenerationContext.createChildContext(gc, ebag, callee, noCheckStoreInstr);
    assertFalse(noCheckStoreContext.noBoundsChecks());
    assertFalse(noCheckStoreContext.noNullChecks());
    assertTrue(noCheckStoreContext.noCheckStoreChecks());

    callee = getNormalMethodForTest("emptyStaticMethodWithNoNullCheckAnnotation");
    Instruction noNullChecks = buildCallInstructionForStaticMethodWithoutReturn(callee, nm);
    GenerationContext noNullCheckContext = GenerationContext.createChildContext(gc, ebag, callee, noNullChecks);
    assertFalse(noNullCheckContext.noBoundsChecks());
    assertTrue(noNullCheckContext.noNullChecks());
    assertFalse(noNullCheckContext.noCheckStoreChecks());
  }

  private Instruction buildCallInstructionForStaticMethodWithoutReturn(
      NormalMethod callee, NormalMethod caller) {
    MethodOperand methOp = MethodOperand.STATIC(callee);
    Instruction callInstr = Call.create(CALL, null, null, methOp, 0);
    callInstr.position = new InlineSequence(caller);

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
    ExceptionHandlerBasicBlockBag ebag = gc.enclosingHandlers;
    GenerationContext childContext = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatExceptionHandlersWereGenerated(childContext);

    Operand expectedLockObject = buildLockObjectForStaticMethod(callee);

    assertThatUnlockAndRethrowBlockIsCorrectForInlinedMethod(gc, childContext, childContext.inlineSequence,
        childContext.prologue, expectedLockObject, childContext.epilogue);

    assertThatChecksWontBeSkipped(gc);
  }

  private void assertThatUnlockAndRethrowBlockIsCorrectForInlinedMethod(GenerationContext parentContext,
      GenerationContext childContext, InlineSequence inlineSequence, BasicBlock prologue,
      Operand lockObject, BasicBlock epilogue) {
    ExceptionHandlerBasicBlockBag ehbb = childContext.enclosingHandlers;
    Enumeration<BasicBlock> enumerator = ehbb.enumerator();

    assertThat(childContext.unlockAndRethrow.exceptionHandlers, is(parentContext.enclosingHandlers));

    ExceptionHandlerBasicBlock rethrow = (ExceptionHandlerBasicBlock) enumerator.nextElement();
    assertSame(rethrow, childContext.unlockAndRethrow);
    assertThatRethrowBlockIsCorrect(inlineSequence, lockObject, rethrow);
    checkCodeOrderForRethrowBlock(childContext, prologue, epilogue, rethrow);

    ExceptionHandlerBasicBlockBag parentHandlers = parentContext.enclosingHandlers;
    Enumeration<BasicBlock> parentHandlerBBEnum = parentHandlers.enumerator();
    HashSet<BasicBlock> parentHandlerBBs = new HashSet<BasicBlock>();
    while (parentHandlerBBEnum.hasMoreElements()) {
      parentHandlerBBs.add(parentHandlerBBEnum.nextElement());
    }
    BasicBlock childRethrow = childContext.unlockAndRethrow;
    OutEdgeEnumeration outEdges = childRethrow.outEdges();
    boolean linkedToAllBlocksFromParentHandler = true;
    while (outEdges.hasMoreElements()) {
      BasicBlock target = (BasicBlock) outEdges.nextElement().to();
      if (!parentHandlerBBs.contains(target) && target != childContext.exit) {
        linkedToAllBlocksFromParentHandler = false;
        break;
      }
    }
    assertTrue(linkedToAllBlocksFromParentHandler);

    ExceptionHandlerBasicBlockBag ehbbb = childContext.enclosingHandlers;
    assertSame(parentContext.enclosingHandlers, ehbbb.getCaller());
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
    assertTrue(firstInstructionInRethrow.position.equals(inlineSequence));
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
    gc.cfg.setNumberOfNodes(nodeNumber);

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, callee, callInstr);

    assertThatStateIsCopiedFromParentToChild(gc, callee, child, ebag);

    InlineSequence expectedInlineSequence = new InlineSequence(callee, callInstr.position, callInstr);
    assertEquals(expectedInlineSequence, child.inlineSequence);

    assertThatPrologueAndEpilogueAreWiredCorrectlyForChildContext(ebag, nodeNumber, child);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();
    Instruction unintBegin = prologueRealInstr.nextElement();
    assertThatInstructionIsUnintMarker(unintBegin, UNINT_BEGIN);
    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.epilogue.forwardRealInstrEnumerator();
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

    GenerationContext child = GenerationContext.createChildContext(gc, ebag, interruptibleCallee, callInstr);

    Enumeration<Instruction> prologueRealInstr = child.prologue.forwardRealInstrEnumerator();
    assertThatNoMoreInstructionsExist(prologueRealInstr);

    Enumeration<Instruction> epilogueRealInstr = child.epilogue.forwardRealInstrEnumerator();
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

    GenerationContext child = GenerationContext.createChildContext(parent, ebag, interruptibleCallee, callInstr);
    setTransferableProperties(targetNumberOfNodes, child);

    GenerationContext.transferState(parent, child);

    assertThatStateWasTransferedToOtherContext(parent, targetNumberOfNodes);
  }

  private void setTransferableProperties(int targetNumberOfNodes,
      GenerationContext context) {
    context.allocFrame = true;
    context.generatedExceptionHandlers = true;
    context.cfg.setNumberOfNodes(targetNumberOfNodes);
  }

  private void assertThatStateWasTransferedToOtherContext(
      GenerationContext otherContext, int targetNumberOfNodes) {
    assertTrue(otherContext.allocFrame);
    assertTrue(otherContext.generatedExceptionHandlers);
    assertTrue(otherContext.cfg.numberOfNodes() == targetNumberOfNodes);
  }

  private void assertThatContextIsInExpectedState(GenerationContext parent,
      int targetNumberOfNodes) {
    assertFalse(parent.allocFrame);
    assertFalse(parent.generatedExceptionHandlers);
    assertFalse("Assumption in test case wrong, need to change test case", parent.cfg.numberOfNodes() == targetNumberOfNodes);
  }

  @Test
  public void transferStateDoesNotCheckThatChildIsReallyAChildOfParent() throws Exception {
    NormalMethod nm = getNormalMethodForTest("emptyStaticMethodWithoutAnnotations");
    CompiledMethod cm = new OptCompiledMethod(-1, nm);
    OptOptions opts = new OptOptions();
    InlineOracle io = new DefaultInlineOracle();
    GenerationContext parent = new GenerationContext(nm, null, cm, opts, io);
    int targetNumberOfNodes = 23456789;
    assertThatContextIsInExpectedState(parent, targetNumberOfNodes);

    GenerationContext nonChild = createMostlyEmptyContext("emptyStaticMethodWithoutAnnotations");
    setTransferableProperties(targetNumberOfNodes, nonChild);

    GenerationContext.transferState(parent, nonChild);

    assertThatStateWasTransferedToOtherContext(parent, targetNumberOfNodes);
  }

  @Test(expected = NullPointerException.class)
  public void contextReturnedByGetSynthethicContextContainsOnlyCFG() throws Exception {
    GenerationContext gc = createMostlyEmptyContext("methodForInliningTests");
    ExceptionHandlerBasicBlockBag mockEbag = getMockEbag();

    int parentCfgNodeNumber = gc.cfg.numberOfNodes();

    GenerationContext synthethicContext = GenerationContext.createSynthetic(gc, mockEbag);

    int synthethicNodeNumber = -100000;
    assertThat(synthethicContext.cfg.numberOfNodes(), is(synthethicNodeNumber));
    assertThat(synthethicContext.prologue.firstInstruction().bcIndex, is(PROLOGUE_BCI));
    assertThat(synthethicContext.prologue.firstInstruction().position, is(gc.inlineSequence));
    assertThat(synthethicContext.prologue.getNumber(), is(parentCfgNodeNumber));
    assertThat(synthethicContext.prologue.exceptionHandlers, is(mockEbag));
    assertThat(synthethicContext.epilogue.firstInstruction().bcIndex, is(EPILOGUE_BCI));
    assertThat(synthethicContext.epilogue.firstInstruction().position, is(gc.inlineSequence));
    assertThat(synthethicContext.epilogue.getNumber(), is(parentCfgNodeNumber + 1));
    assertThat(synthethicContext.epilogue.exceptionHandlers, is(mockEbag));
    assertThat(gc.cfg.numberOfNodes(), is(4));
    assertThat(synthethicContext.cfg.numberOfNodes(), is(synthethicNodeNumber));
    assertThat(synthethicContext.cfg.firstInCodeOrder(), is(synthethicContext.prologue));
    assertThat(synthethicContext.cfg.lastInCodeOrder(), is(synthethicContext.epilogue));

    assertFalse(synthethicContext.allocFrame);
    assertNull(synthethicContext.arguments);
    assertNull(synthethicContext.branchProfiles);
    assertNull(synthethicContext.enclosingHandlers);
    assertNull(synthethicContext.exit);
    assertFalse(synthethicContext.generatedExceptionHandlers);
    assertNull(synthethicContext.inlinePlan);
    assertNull(synthethicContext.inlineSequence);
    assertNull(synthethicContext.method);
    assertNull(synthethicContext.options);
    assertNull(synthethicContext.original_cm);
    assertNull(synthethicContext.original_method);
    assertNull(synthethicContext.result);
    assertNull(synthethicContext.resultReg);
    assertNull(synthethicContext.temps);
    assertNull(synthethicContext.unlockAndRethrow);

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
    regOp.scratchObject = guard;

    regOp.setParameter();
    regOp.setNonVolatile();
    regOp.setExtant();
    regOp.setDeclaredType();
    regOp.setPreciseType();
    regOp.setPositiveInt();

    RegisterOperand newRegOpWithInheritance = gc.makeLocal(localNumber, regOp);
    Operand scratchObject = (Operand) newRegOpWithInheritance.scratchObject;
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
    Register reg = new Register(-1);
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
    expectedNullCheckGuard.scratchObject = new TrueGuardOperand();

    RegisterOperand actual = gc.makeNullCheckGuard(reg);
    assertTrue(actual.sameRegisterPropertiesAsExceptForScratchObject(expectedNullCheckGuard));
    assertNull(actual.scratchObject);
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

    gc.temps.removeRegister(thisReg);

    gc.resync();

    RegisterOperand newNullCheckGuard = gc.makeNullCheckGuard(thisReg);
    assertFalse(newNullCheckGuard.sameRegisterPropertiesAsExceptForScratchObject(thisNullCheckGuard));
  }

  @Ignore("currently fails because resync deletes ALL null check guards." +
   "This is caused by the use of getValue() instead of getKey() when " +
   "checking whether to remove a mapping")
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
    assertTrue(newNullCheckGuard.sameRegisterPropertiesAsExceptForScratchObject(thisNullCheckGuard));
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

}
