/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * BC_FloatStore: fstore, fstore_<i> 
 * 
 * @author Feng Qian
 */
public class BC_FloatStore extends OSR_PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;
  
  public BC_FloatStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_fstore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_fstore, local);
    }
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return -1;
  }

  public String toString() {
    return "FloatStore "+lnum;
  }
}
