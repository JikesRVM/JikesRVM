/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * artificial instruction, load a PC on the stack. 
 *
 * @author Feng Qian
 */

public class BC_LoadRetAddrConst extends OSR_PseudoBytecode {
  private final static int bsize = 6;
  private int bcindex;

  public BC_LoadRetAddrConst(int off) {
    this.bcindex = off;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadRetAddrConst);
    int2bytes(codes, 2, bcindex);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int getOffset() {
    return bcindex;
  }

  public int stackChanges() {
        return +1;
  }

  public void patch(int off) {
    this.bcindex = off;
  }

  public String toString() {
    return "LoadRetAddrConst "+bcindex;
  }
}
