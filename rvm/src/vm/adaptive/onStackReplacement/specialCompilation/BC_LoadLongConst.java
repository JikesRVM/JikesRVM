/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * load a long constant on the stack
 *
 * @author Feng Qian
 */
public class BC_LoadLongConst extends OSR_PseudoBytecode {
  private final static int bsize = 10;
  private final long lbits;
  
  public BC_LoadLongConst(long bits) {
    this.lbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadLongConst);
    long2bytes(codes, 2, lbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return 2;
  }

  public String toString() {
    return "LoadLong "+lbits;
  }
}
