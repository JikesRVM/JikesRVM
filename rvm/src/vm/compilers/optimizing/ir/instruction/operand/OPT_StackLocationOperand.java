/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a symbolic name for a stack location.
 * 
 * For now, this implementation names a stack location by its offset from
 * the frame pointer (ie., top of the stack).  TODO: consider reworking the 
 * code to use abstract names for stack locations.
 * 
 * @author Stephen Fink
 */
public final class OPT_StackLocationOperand extends OPT_Operand  {
  /**
   * The offset from the frame pointer (top of stack frame) corresponding
   * to this stack location.
   */
  private int fpOffset;

  /**
   * Size (in bytes) reserved for the value of this operand.
   */
  private byte size;

  /**
   * @param fpOffset the offset from the frame pointer (top of stack frame) 
   * corresponding to this stack location.
   * @param size Size (in bytes) reserved for the value of this operand.
   */
  OPT_StackLocationOperand(int fpOffset, byte size) {
    this.fpOffset = fpOffset;
    this.size = size;
  }

  /**
   * @return the offset from the frame pointer (top of stack frame) 
   * corresponding to this stack location.
   */
  int getOffset() {
    return fpOffset;
  }

  /** 
   * @return Size (in bytes) reserved for the value of this operand.
   */
  byte getSize() {
    return size;
  }

  public String toString() {
    return "<Frame offset " + getOffset() + "," + getSize() + ">";
  }

  boolean similar(OPT_Operand op) {
    if (op instanceof OPT_StackLocationOperand) {
      OPT_StackLocationOperand o2 = (OPT_StackLocationOperand)op;
      return ((o2.getOffset() == getOffset()) && (o2.getSize() == getSize()));
    } else {
      return false;
    }
  }

  OPT_Operand copy() {
    return new OPT_StackLocationOperand(getOffset(),getSize());
  }
}
