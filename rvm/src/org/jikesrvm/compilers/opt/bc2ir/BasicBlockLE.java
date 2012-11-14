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

import org.jikesrvm.compilers.opt.inlining.InlineSequence;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * This class is used as a 'wrapper' to a basic block to hold
 * information that is necessary only for IR generation.
 */
class BasicBlockLE {
  // Used by BBSet to maintain red/black tree of BBLE's during generation
  BasicBlockLE parent, left, right;

  /** Start bytecode of this BBLE */
  final int low;

  /** Current end bytecode of this BBLE */
  int high;

  /** Maximum possible bytecode of this BBLE (wrt exception ranges) */
  int max;

  /** Basic block that this BBLE refers to. */
  BasicBlock block;

  /** State of the stack at the start of this basic block. */
  OperandStack stackState;

  /** State of the local variables at the start of this basic block. */
  Operand[] localState;

  /**
   * The desired fallthrough (next in code order) BBLE (may be {@code null}).
   * NOTE: we may not always end up actually falling through
   * (see BBSet.finalPass).
   */
  BasicBlockLE fallThrough;

  /**
   * The exception handler BBLE's for this block (null if none)
   */
  HandlerBlockLE[] handlers;

  /**
   * Encoding of random boolean state
   */
  private byte flags;
  private static final byte STACK_KNOWN = 0x01;
  private static final byte LOCAL_KNOWN = 0x02;
  private static final byte SELF_REGEN = 0x04;
  private static final byte GENERATED = 0x08;
  private static final byte COLOR = 0x10;           //(Red = 0, Black = 1)
  private static final byte IN_CODE_ORDER = 0x20;

  final void setStackKnown() { flags |= STACK_KNOWN; }

  final void clearStackKnown() { flags &= ~STACK_KNOWN; }

  final boolean isStackKnown() { return (flags & STACK_KNOWN) != 0; }

  final void setLocalKnown() { flags |= LOCAL_KNOWN; }

  final void clearLocalKnown() { flags &= ~LOCAL_KNOWN; }

  final boolean isLocalKnown() { return (flags & LOCAL_KNOWN) != 0; }

  final void setSelfRegen() { flags |= SELF_REGEN; }

  final void clearSelfRegen() { flags &= ~SELF_REGEN; }

  final boolean isSelfRegen() { return (flags & SELF_REGEN) != 0; }

  final void setGenerated() { flags |= GENERATED; }

  final void clearGenerated() { flags &= ~GENERATED; }

  final boolean isGenerated() { return (flags & GENERATED) != 0; }

  final void setBlack() { flags |= COLOR; }

  final boolean isBlack() { return (flags & COLOR) != 0; }

  final void setRed() { flags &= ~COLOR; }

  final boolean isRed() { return (flags & COLOR) == 0; }

  final void setInCodeOrder() { flags |= IN_CODE_ORDER; }

  final void clearInCodeOrder() { flags &= ~IN_CODE_ORDER; }

  final boolean isInCodeOrder() { return (flags & IN_CODE_ORDER) != 0; }

  /**
   * Is the BBLE ready to generate?
   */
  final boolean isReadyToGenerate() {
    // (isStackKnown() && isLocalKnown && !isGenerated)
    byte READY_MASK = STACK_KNOWN | LOCAL_KNOWN | GENERATED;
    byte READY_VAL = STACK_KNOWN | LOCAL_KNOWN;
    return (flags & READY_MASK) == READY_VAL;
  }

  /**
   * Save a shallow copy of the given local variable state into this.
   * @param _localState local variable state to save
   */
  final void copyIntoLocalState(Operand[] _localState) {
    localState = new Operand[_localState.length];
    System.arraycopy(_localState, 0, localState, 0, _localState.length);
    setLocalKnown();
  }

  /**
   * Return a shallow copy of my local state.
   */
  final Operand[] copyLocalState() {
    Operand[] ls = new Operand[localState.length];
    System.arraycopy(localState, 0, ls, 0, localState.length);
    return ls;
  }

  /**
   * Add an exception handler BBLE to the handlers array.
   * NOTE: this isn't incredibly efficient, but empirically the expected
   * number of handlers per basic block is 0, with an observed
   * maximum across 10,000+ methods of 3.
   * Until this changes, we just don't care.
   */
  final void addHandler(HandlerBlockLE handler) {
    if (handlers == null) {
      handlers = new HandlerBlockLE[1];
      handlers[0] = handler;
    } else {
      for (HandlerBlockLE handler1 : handlers) {
        if (handler1 == handler) {
          return;             //already there (was in emap more than once)
        }
      }
      int n = handlers.length;
      HandlerBlockLE[] tmp = new HandlerBlockLE[n + 1];
      for (int i = 0; i < n; i++) {
        tmp[i] = handlers[i];
      }
      tmp[n] = handler;
      handlers = tmp;
    }
  }

  /**
   * Create a new BBLE (and basic block) for the specified bytecode index.
   *
   * @param loc bytecode index
   * @param position the inline sequence
   * @param cfg ControlFlowGraph into which the block
   *            will eventually be inserted
   */
  BasicBlockLE(int loc, InlineSequence position, ControlFlowGraph cfg) {
    block = new BasicBlock(loc, position, cfg);
    low = loc;
    high = loc;
  }

  // Only for use by subclasses to avoid above constructor.
  protected BasicBlockLE(int loc) { low = loc;  }

  /**
   * Returns a string representation of this BBLE.
   */
  @Override
  public String toString() {
    if (isGenerated()) {
      return "(" + low + "," + high + "," + max + ")";
    }
    if (isReadyToGenerate()) {
      return "{" + low + "," + max + "}";
    }
    return "[" + low + "," + max + "]";
  }

  /**
   * Returns a string representation of state that determines if the BBLE
   * is ready to be generated */
  public String genState() {
    return "(sk=" + isStackKnown() + ", lk=" + isLocalKnown() + ", gen=" + isGenerated() + ")";
  }
}
