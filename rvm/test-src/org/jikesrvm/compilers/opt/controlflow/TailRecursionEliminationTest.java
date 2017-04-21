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
package org.jikesrvm.compilers.opt.controlflow;

import static org.jikesrvm.compilers.opt.ir.Operators.CALL;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.MethodsForTests;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresBuiltJikesRVM.class, RequiresOptCompiler.class})
public class TailRecursionEliminationTest {

  // TODO The test is currently incomplete. Ideas for tests:
  // - tail recursion elimination on CALL
  // - tail recursion elimination on SYSCALL
  // - normal calls aren't changed
  // - elimination of tail recursion requires a prologue instructions
  // - tail recursion with branch elimination

  private TailRecursionElimination tailRecursionElimination;

  @Before
  public void createPhase() {
    tailRecursionElimination = new TailRecursionElimination();
  }

  @Test
  public void willNotBePerformedOnOptLevel0() {
    OptOptions options = new OptOptions();
    options.setOptLevel(0);
    assertFalse(tailRecursionElimination.shouldPerform(options));
  }

  @Test
  public void willBePerformedOnOptLevel1() {
    OptOptions options = new OptOptions();
    options.setOptLevel(1);
    assertTrue(tailRecursionElimination.shouldPerform(options));
  }

  @Test
  public void willBePerformedOnOptLevel2() {
    OptOptions options = new OptOptions();
    options.setOptLevel(2);
    assertTrue(tailRecursionElimination.shouldPerform(options));
  }

  @Test
  public void doesNotThrowExceptionsOnCFGWithoutInstructions() {
    OptOptions opts = new OptOptions();
    IR ir = new IR(null, null, opts);
    opts.setOptLevel(1);
    TestingTools.addEmptyCFGToIR(ir);
    tailRecursionElimination.perform(ir);
  }

  @Test
  public void unannotatedCallsAreSubjectToOptimization() throws Exception {
    Class<?>[] types = {};
    RVMMethod m = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithoutAnnotations", types);
    MethodOperand methOp = MethodOperand.STATIC(m);
    Instruction call = Call.create(CALL, null, null, methOp, 0);
    assertTrue(tailRecursionElimination.allowedToOptimize(call));
  }

  @Test
  public void callsWithNoTailCallEliminationAnnotationArentSubjectToOptimization() throws Exception {
    Class<?>[] types = {};
    RVMMethod m = TestingTools.getNormalMethod(MethodsForTests.class, "emptyStaticMethodWithNoTailCallEliminationAnnotation", types);
    MethodOperand methOp = MethodOperand.STATIC(m);
    Instruction call = Call.create(CALL, null, null, methOp, 0);
    assertFalse(tailRecursionElimination.allowedToOptimize(call));
  }

}
