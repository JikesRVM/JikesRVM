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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * Represents a symbolic name for a stack location.
 *
 * The stack location is defined by an offset from either the framepointer
 * (top of stack frame) or stackpointer-home-location (bottom of frame).
 */
public final class StackLocationOperand extends Operand {
  /**
   * Is the offset from the top or bottom of stack frame?
   */
  private boolean fromTop;

  /**
   * The offset (top/bottom of stack frame) corresponding
   * to this stack location.
   */
  private int offset;

  /**
   * Size (in bytes) reserved for the value of this operand.
   */
  private byte size;

  /**
   * @param fromTop is the offset from the top of bottom of the frame?
   * @param offset  the offset of the stack location from the top/bottom
   *                of the frame
   * @param size    Size (in bytes) of the stack location.
   */
  public StackLocationOperand(boolean fromTop, int offset, byte size) {
    this.fromTop = fromTop;
    this.offset = offset;
    this.size = size;
  }

  /**
   * @param fromTop is the offset from the top of bottom of the frame?
   * @param offset  the offset of the stack location from the top/bottom
   *                of the frame
   * @param size    Size (in bytes) of the stack location.
   */
  public StackLocationOperand(boolean fromTop, int offset, int size) {
    this.fromTop = fromTop;
    this.offset = offset;
    this.size = (byte) size;
  }

  /**
   * @return <code>true</code> if the stack location uses the top of the
   *         frame as its base, <code>false</code> if it uses the bottom
   *         of the frame as its base.
   */
  public boolean isFromTop() {
    return fromTop;
  }

  /**
   * @return the offset from the frame pointer (top of stack frame)
   *         corresponding to this stack location.
   */
  public int getOffset() {
    return offset;
  }

  /**
   * @return Size (in bytes) of this stack location.
   */
  public byte getSize() {
    return size;
  }

  @Override
  public String toString() {
    String s = "";
    switch (size) {
      case 1:
        s = ">B";
        break;
      case 2:
        s = ">W";
        break;
      case 4:
        s = ">DW";
        break;
      case 8:
        s = ">QW";
        break;
      default:
        OptimizingCompilerException.UNREACHABLE();
    }
    return "<" + (isFromTop() ? "FrameTop" : "FrameBottom") + (getOffset() < 0 ? "" : "+") + getOffset() + s;
  }

  @Override
  public boolean similar(Operand op) {
    if (op instanceof StackLocationOperand) {
      StackLocationOperand o2 = (StackLocationOperand) op;
      return ((o2.isFromTop() == isFromTop()) && (o2.getOffset() == getOffset()) && (o2.getSize() == getSize()));
    } else {
      return false;
    }
  }

  @Override
  public Operand copy() {
    return new StackLocationOperand(isFromTop(), getOffset(), getSize());
  }
}
