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
package org.jikesrvm.compilers.opt.ir;

import static org.jikesrvm.compilers.opt.driver.OptConstants.MAYBE;
import static org.jikesrvm.compilers.opt.driver.OptConstants.NO;
import static org.jikesrvm.compilers.opt.driver.OptConstants.YES;

import java.util.Enumeration;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ClassLoaderProxy;
import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.liveness.LiveSet;

/**
 * A basic block that marks the start of an exception handler.
 * Exception Handler Basic Block; acronym EHBB.
 */
public final class ExceptionHandlerBasicBlock extends BasicBlock {

  /**
   * The RVMType(s) of the exception(s) caught by this block.
   */
  private TypeOperand[] exceptionTypes;

  /**
   * The liveness information at the beginning of this block.
   * <p>
   *  NOTE: If we decide to store this for all blocks, we should move
   *  this field to BasicBlock (the parent class)
   */
  private LiveSet liveSet;

  /**
   * Creates a new exception handler basic block at the specified location,
   * which catches the specified type of exception.
   *
   * @param loc   Bytecode index to create basic block at
   * @param position  The inline context for this basic block
   * @param type  The exception type
   * @param cfg   The ControlFlowGraph that will contain the basic block
   */
  public ExceptionHandlerBasicBlock(int loc, InlineSequence position, TypeOperand type, ControlFlowGraph cfg) {
    super(loc, position, cfg);
    exceptionTypes = new TypeOperand[1];
    exceptionTypes[0] = type;
    setExceptionHandlerBasicBlock();
    liveSet = null;
  }

  /**
   * Add a new exception type to an extant exception handler block.
   * Do filtering of duplicates internally for efficiency.
   * NOTE: this routine is only intended to be called by
   * {@link org.jikesrvm.compilers.opt.bc2ir.BC2IR}.
   *
   * @param et the exception type to be added
   */
  public void addCaughtException(TypeOperand et) {
    for (TypeOperand exceptionType : exceptionTypes) {
      if (exceptionType.similar(et)) return;
    }
    TypeOperand[] newets = new TypeOperand[exceptionTypes.length + 1];
    for (int i = 0; i < exceptionTypes.length; i++) {
      newets[i] = exceptionTypes[i];
    }
    newets[exceptionTypes.length] = et;
    exceptionTypes = newets;
  }

  /**
   * Return YES/NO/MAYBE values that answer the question is it possible for
   * this handler block to catch an exception of the type et.
   *
   * @param cand the TypeReference of the exception in question.
   * @return YES, NO, MAYBE
   */
  public byte mayCatchException(TypeReference cand) {
    boolean seenMaybe = false;
    byte t;
    for (TypeOperand exceptionType : exceptionTypes) {
      t = ClassLoaderProxy.includesType(exceptionType.getTypeRef(), cand);
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
      t = ClassLoaderProxy.includesType(cand, exceptionType.getTypeRef());
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
    }
    return seenMaybe ? MAYBE : NO;
  }

  /**
   * Return YES/NO/MAYBE values that answer the question is it guarenteed that
   * this handler block will catch an exception of type <code>cand</code>
   *
   * @param cand  the TypeReference of the exception in question.
   * @return YES, NO, MAYBE
   */
  public byte mustCatchException(TypeReference cand) {
    boolean seenMaybe = false;
    byte t;
    for (TypeOperand exceptionType : exceptionTypes) {
      t = ClassLoaderProxy.includesType(exceptionType.getTypeRef(), cand);
      if (t == YES) return YES;
      seenMaybe |= (t == MAYBE);
    }
    if (seenMaybe) {
      return MAYBE;
    } else {
      return NO;
    }
  }

  /**
   * Return an Enumeration of the caught exception types.
   * Mainly intended for creation of exception tables during
   * final assembly. Most other clients shouldn't care about this
   * level of detail.
   */
  public Enumeration<TypeOperand> getExceptionTypes() {
    return new Enumeration<TypeOperand>() {
      private int idx = 0;

      @Override
      public boolean hasMoreElements() {
        return idx != exceptionTypes.length;
      }

      @Override
      public TypeOperand nextElement() {
        try {
          return exceptionTypes[idx++];
        } catch (ArrayIndexOutOfBoundsException e) {
          throw new java.util.NoSuchElementException("ExceptionHandlerBasicBlock.getExceptionTypes");
        }
      }
    };
  }

  /**
   * Get how many table entries this EHBB needs.
   * Really only of interest during final assembly.
   *
   * @see org.jikesrvm.compilers.opt.runtimesupport.OptExceptionTable
   *
   * @return the number of table entries for this basic block
   */
  public int getNumberOfExceptionTableEntries() {
    return exceptionTypes.length;
  }

  /**
   * Returns the set of registers live before the first instruction of
   * this basic block
   *
   * @return the set of registers live before the first instruction of
   * this basic block
   */
  public LiveSet getLiveSet() {
    return liveSet;
  }

  /**
   * Set the set of registers live before the first instruction of
   * this basic block
   *
   * @param   liveSet The set of registers live before the first instruction of
   * this basic block
   */
  public void setLiveSet(LiveSet liveSet) {
    this.liveSet = liveSet;
  }

  /**
   * Return a string representation of the basic block
   * (augment {@link BasicBlock#toString} with
   * the exceptions caught by this handler block).
   *
   * @return a string representation of the block
   */
  @Override
  public String toString() {
    String exmsg = " (catches ";
    for (int i = 0; i < exceptionTypes.length - 1; i++) {
      exmsg = exmsg + exceptionTypes[i].toString() + ", ";
    }
    exmsg = exmsg + exceptionTypes[exceptionTypes.length - 1].toString();
    exmsg = exmsg + " for";
    Enumeration<BasicBlock> in = getIn();
    while (in.hasMoreElements()) {
      exmsg = exmsg + " " + in.nextElement().toString();
    }
    exmsg = exmsg + ")";

    return super.toString() + exmsg;
  }
}
