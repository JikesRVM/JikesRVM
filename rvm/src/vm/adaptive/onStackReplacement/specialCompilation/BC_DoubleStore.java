/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

/**
 * BC_DoubleStore: dstore, dstore_<l> 
 * 
 * @author Feng Qian
 */

public class BC_DoubleStore extends OSR_PseudoBytecode {
  private int bsize;
  private byte[] codes; 
  private int lnum;
  
  public BC_DoubleStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_dstore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_dstore, local);
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
    return "dstore "+lnum;
  }
}
