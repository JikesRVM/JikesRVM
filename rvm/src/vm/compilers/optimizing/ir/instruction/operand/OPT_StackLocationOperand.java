/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException;

/**
 * Represents a symbolic name for a stack location.
 * 
 * The stack location is defined by an offset from either the framepointer
 * (top of stack frame) or stackpointer-home-location (bottom of frame).
 * 
 * @author Stephen Fink
 * @author Dave Grove
 */
public final class OPT_StackLocationOperand extends OPT_Operand  {
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
  public OPT_StackLocationOperand(boolean fromTop, int offset, byte size) {
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
  public OPT_StackLocationOperand(boolean fromTop, int offset, int size) {
    this.fromTop = fromTop;
    this.offset = offset;
    this.size = (byte)size;
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

  public String toString() {
    String s = "";
    switch (size) {
    case 1: s = ">B"; break;
    case 2: s = ">W"; break;
    case 4: s = ">DW"; break;
    case 8: s = ">QW"; break;
    default:
      OPT_OptimizingCompilerException.UNREACHABLE();
    }
    return "<"+(isFromTop()?"FrameTop":"FrameBottom")+
      (getOffset()<0?"":"+") + getOffset()+ s;
  }

  public boolean similar(OPT_Operand op) {
    if (op instanceof OPT_StackLocationOperand) {
      OPT_StackLocationOperand o2 = (OPT_StackLocationOperand)op;
      return ((o2.isFromTop() == isFromTop()) &&
              (o2.getOffset() == getOffset()) && 
              (o2.getSize() == getSize()));
    } else {
      return false;
    }
  }

  public OPT_Operand copy() {
    return new OPT_StackLocationOperand(isFromTop(), getOffset(), getSize());
  }
}
