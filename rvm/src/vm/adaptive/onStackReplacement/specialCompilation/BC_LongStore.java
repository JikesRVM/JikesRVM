/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * BC_LongStore: lstore, lstore_<n> 
 * 
 * @author Feng Qian
 */

public class BC_LongStore extends OSR_PseudoBytecode {
  private int bsize; 
  private byte[] codes; 
  private int lnum;

  public BC_LongStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_lstore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_lstore, local);
    }
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return -2;
  }

  public String toString() {
    return "lstore "+lnum;
  }
}
