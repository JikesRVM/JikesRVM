/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * BC_LoadFloatConst: ldc, ldc_w 
 *
 * @author Feng Qian
 */
public class BC_LoadFloatConst extends OSR_PseudoBytecode {
  private final static int bsize = 6;
  private final int fbits;

  public BC_LoadFloatConst(int bits) {
    this.fbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadFloatConst);
    int2bytes(codes, 2, fbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return +1;
  }
  
  public String toString() {
    return "LoadFloat "+Float.intBitsToFloat(fbits);
  }
}
