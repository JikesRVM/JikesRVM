/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * BC_LoadDoubleConst: ldc2_w 
 * 
 * @author Feng Qian
 */
public class BC_LoadDoubleConst extends OSR_PseudoBytecode {
  private final static int bsize = 10;
  private final long dbits;

  public BC_LoadDoubleConst(long bits) {
    this.dbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadDoubleConst);
    long2bytes(codes, 2, dbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return +2;
  }

  public String toString() {
    return "LoadDouble 0x"+ Long.toHexString(dbits) + " : "+Double.longBitsToDouble(dbits);
  }
}

