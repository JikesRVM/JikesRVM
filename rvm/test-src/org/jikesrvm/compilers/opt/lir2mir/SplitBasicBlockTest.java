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
package org.jikesrvm.compilers.opt.lir2mir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.jikesrvm.compilers.opt.ir.Operators.FENCE;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_IFCMP;
import static org.junit.Assert.assertThat;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Empty;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.RequiresOptCompiler;
import org.jikesrvm.junit.runners.VMRequirements;
import org.jikesrvm.tests.util.TestingTools;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category({RequiresBuiltJikesRVM.class, RequiresOptCompiler.class})
public class SplitBasicBlockTest {

  private SplitBasicBlock splitPhase;

  @Before
  public void createPhase() {
    splitPhase = new SplitBasicBlock();
  }

  @Test
  public void doesNotThrowExceptionsOnCFGWithoutInstructions() {
    int maxInstPerBlock = 0;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    splitPhase.perform(ir);
  }

  @Test
  public void doesNotAddInstructionsToEmptyCFG() {
    int maxInstPerBlock = 0;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    splitPhase.perform(ir);
    assertThatInstructionCountForEachBlockIsAtMost(ir, maxInstPerBlock);
  }

  @Test
  public void doesNotSplitEmptyCFG() {
    int maxInstPerBlock = 1;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    int nodeNumberBefore = ir.cfg.numberOfNodes();
    splitPhase.perform(ir);
    int nodeNumberAfter = ir.cfg.numberOfNodes();
    assertThat(nodeNumberAfter, is(nodeNumberBefore));
  }

  private void assertThatInstructionCountForEachBlockIsAtMost(IR ir, int maxInstrPerBlock) {
    Enumeration<BasicBlock> blocks = ir.getBasicBlocks();
    assertThat(blocks.hasMoreElements(), is(true));
    while (blocks.hasMoreElements()) {
      BasicBlock block = blocks.nextElement();

      // Note: This is slow but it's required to determine if the compiler phase
      // works correctly
      int numberOfRealInstructions = block.getNumberOfRealInstructions();

      assertThat(numberOfRealInstructions, lessThanOrEqualTo(maxInstrPerBlock));
    }
  }

  private IR createIRWithEmptyCFG(int maxInstPerBlock) {
    OptOptions opts = new OptOptions();
    IR ir = new IR(null, null, opts);
    opts.L2M_MAX_BLOCK_SIZE = maxInstPerBlock;
    TestingTools.addEmptyCFGToIR(ir);
    return ir;
  }

  @Test
  public void doesNotCreateNewBlocksWhenNumberOfInstructionsMatchesLimit() {
    int maxInstPerBlock = 2;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);

    int nodeNumberBefore = ir.cfg.numberOfNodes();

    addNumberOfInstructionsToBlock(ir, 2);

    splitPhase.perform(ir);

    int nodeNumberAfter = ir.cfg.numberOfNodes();
    assertThat(nodeNumberAfter, is(nodeNumberBefore));
    assertThatInstructionCountForEachBlockIsAtMost(ir, maxInstPerBlock);
  }

  @Test
  public void splitsAllBlocksThatHaveTooManyInstructions() {
    int maxInstPerBlock = 2;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    addNumberOfInstructionsToBlock(ir, 13);
    splitPhase.perform(ir);
    assertThatInstructionCountForEachBlockIsAtMost(ir, maxInstPerBlock);
  }

  @Test(timeout = 1000)
  public void worksCorrectlyForALimitOfOneInstructionPerBlock() {
    int maxInstPerBlock = 1;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    int nodeNumberBefore = ir.cfg.numberOfNodes();
    addNumberOfInstructionsToBlock(ir, 1);
    splitPhase.perform(ir);
    int nodeNumberAfter = ir.cfg.numberOfNodes();
    assertThat(nodeNumberAfter, is(nodeNumberBefore));
    assertThatInstructionCountForEachBlockIsAtMost(ir, maxInstPerBlock);
  }

  private void addNumberOfInstructionsToBlock(IR ir, int count) {
    Enumeration<BasicBlock> blocks = ir.getBasicBlocks();
    while (blocks.hasMoreElements()) {
      BasicBlock bb = blocks.nextElement();
      for (int currInstCount = 0; currInstCount < count; currInstCount++) {
        bb.appendInstruction(Empty.create(FENCE));
      }
    }
  }

  @Test(expected = OptimizingCompilerException.class)
  public void throwsOptimizingCompilerExceptionWhenLimitIsInvalid() {
    int maxInstPerBlock = 0;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    addNumberOfInstructionsToBlock(ir, 1);
    splitPhase.perform(ir);
  }

  @Test
  public void branchesAtTheEndOfTheBlockDoNotCountAsIntructions() {
    int maxInstPerBlock = 3;
    IR ir = createIRWithEmptyCFG(maxInstPerBlock);
    int nodeNumberBefore = ir.cfg.numberOfNodes();

    Enumeration<BasicBlock> blocks = ir.getBasicBlocks();

    while (blocks.hasMoreElements()) {
      BasicBlock bb = blocks.nextElement();
      bb.appendInstruction(Empty.create(FENCE));
      bb.appendInstruction(Empty.create(FENCE));
      Instruction i = IfCmp.create(INT_IFCMP, null, null, null, null, null, null);
      bb.appendInstruction(i);
    }

    splitPhase.perform(ir);

    int nodeNumberAfter = ir.cfg.numberOfNodes();
    assertThat(nodeNumberAfter, is(nodeNumberBefore));
    assertThatInstructionCountForEachBlockIsAtMost(ir, maxInstPerBlock);
  }

}
