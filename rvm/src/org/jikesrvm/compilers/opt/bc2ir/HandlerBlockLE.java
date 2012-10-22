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

import org.jikesrvm.ArchitectureSpecificOpt.RegisterPool;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Nullary;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;

/**
 * Extend BasicBlockLE for handler blocks
 */
final class HandlerBlockLE extends BasicBlockLE {
  /**
   * The RegisterOperand that code should use to access
   * the caught exception object
   */
  final RegisterOperand exceptionObject;

  /**
   * The synthetic entry basic block for this handler.
   * It contains the instruction sequence to get the caught exception object
   * into a "normal" register operand (exceptionObject);
   */
  final ExceptionHandlerBasicBlock entryBlock;

  /**
   * Create a new exception handler BBLE (and exception handler basic block)
   * for the specified bytecode index and exception type.
   *
   * @param loc bytecode index
   * @param position inline sequence
   * @param eType   exception type
   * @param temps the register pool to allocate exceptionObject from
   * @param exprStackSize max size of expression stack
   * @param cfg ControlFlowGraph into which the block
   *            will eventually be inserted
   */
  HandlerBlockLE(int loc, InlineSequence position, TypeOperand eType, RegisterPool temps,
                 int exprStackSize, ControlFlowGraph cfg) {
    super(loc);
    entryBlock = new ExceptionHandlerBasicBlock(BC2IR.SYNTH_CATCH_BCI, position, eType, cfg);
    block = new BasicBlock(loc, position, cfg);
    // NOTE: We intentionally use throwable rather than eType to avoid
    // having the complexity of having to regenerate the handler when a
    // new type of caught exception is added. Since we shouldn't care about
    // the performance of code in exception handling blocks, this
    // should be the right tradeoff.
    exceptionObject = temps.makeTemp(TypeReference.JavaLangThrowable);
    BC2IR.setGuard(exceptionObject, new TrueGuardOperand());    // know not null
    high = loc;
    // Set up expression stack on entry to have the caught exception operand.
    stackState = new OperandStack(exprStackSize);
    stackState.push(exceptionObject);
    setStackKnown();
    // entry block contains instructions to transfer the caught
    // exception object to exceptionObject.
    Instruction s = Nullary.create(BC2IR.GET_CAUGHT_EXCEPTION, exceptionObject.copyD2D());
    entryBlock.appendInstruction(s);
    s.bcIndex = BC2IR.SYNTH_CATCH_BCI;
    entryBlock.insertOut(block);
  }

  void addCaughtException(TypeOperand et) {
    entryBlock.addCaughtException(et);
  }

  byte mayCatchException(TypeReference et) {
    return entryBlock.mayCatchException(et);
  }

  byte mustCatchException(TypeReference et) {
    return entryBlock.mustCatchException(et);
  }
}
