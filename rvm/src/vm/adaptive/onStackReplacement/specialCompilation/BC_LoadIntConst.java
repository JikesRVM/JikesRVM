/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * load an integer constant on the stack
 *
 * @author Feng Qian
 */
public class BC_LoadIntConst extends OSR_PseudoBytecode {
  private final static int bsize = 6;
  private final int ibits;
  
  public BC_LoadIntConst(int bits) {
    this.ibits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadIntConst);
    int2bytes(codes, 2, ibits);
    return codes;
  }

  public int getSize() {
    return bsize; 
  }
 
  public int stackChanges() {
        return +1;
  }

  public String toString() {
    return "LoadInt "+ibits;  
  }
}
