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

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * Splits a large basic block into smaller ones with {@code size <=
 * OptOptions.L2M_MAX_BLOCK_SIZE}
 */
public final class SplitBasicBlock extends CompilerPhase {

  @Override
  public String getName() {
    return "SplitBasicBlock";
  }

  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  @Override
  public void perform(IR ir) {
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock bb = e.nextElement();

      if (!bb.isEmpty()) {
        while (bb != null) {
          bb = splitEachBlock(bb, ir);
        }
      }
    }
  }

  /**
   * Splits a basic block.
   *
   * @param bb the block to process
   * @param ir the IR that contains the block
   *
   * @return {@code null} if no splitting is done, returns the second block
   *  if splitting is done.
   */
  BasicBlock splitEachBlock(BasicBlock bb, IR ir) {
    if (ir.options.L2M_MAX_BLOCK_SIZE <= 0) {
      throw new OptimizingCompilerException("Maximum block size must be a" +
          " positive number but was " +
          ir.options.L2M_MAX_BLOCK_SIZE + "!", true);
    }

    int remainingInstCount = ir.options.L2M_MAX_BLOCK_SIZE;

    Enumeration<Instruction> instructions = bb.forwardRealInstrEnumerator();
    while (instructions.hasMoreElements()) {
      Instruction inst = instructions.nextElement();
      remainingInstCount--;
      if (remainingInstCount <= 0) {
        // no need to split because all the rests are just branches
        if (inst.isBranch()) {
          return null;
        }
        // no need to split because the basic block does not contain any more instructions
        if (!instructions.hasMoreElements()) {
          return null;
        }

        return bb.splitNodeWithLinksAt(inst, ir);
      }
    }

    return null;
  }

}
