/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * goto instruction
 *
 * @author Feng Qian
 */
public class BC_Goto extends OSR_PseudoBytecode {
  private int offset;
  private byte[] codes;
  private int bsize;
  
  public BC_Goto(int off) {
    this.offset = off;
    adjustFields();
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int getOffset() {
    return this.offset;
  }

  public int stackChanges() {
    return 0;
  }

  public void patch(int off) {
    this.offset = off;
    adjustFields();
  }

  private void adjustFields() {
    if ( (offset >= -32768)
        &&(offset <= 32767) ) {
      bsize = 3;
      codes = new byte[3];
      codes[0] = (byte)JBC_goto;
      codes[1] = (byte)(offset >> 8);
      codes[2] = (byte)(offset & 0xFF);
    } else {
      bsize = 5;
      codes = new byte[5];
      codes[0] = (byte)JBC_goto_w;
      int2bytes(codes, 1, offset);
    }
  }
 
  public String toString() {
    return "goto "+this.offset;
  }
}
