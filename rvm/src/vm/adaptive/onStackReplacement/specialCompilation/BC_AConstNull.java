/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

/**
 * aconst_null
 *
 * @author Feng Qian
 */
public class BC_AConstNull extends OSR_PseudoBytecode {
  public byte[] getBytes() {
    byte[] codes = new byte[1];
    codes[0] = 1;
    return codes;
  }

  public int getSize() {
    return 1;
  }

  public int stackChanges() {
        return 1;
  }

  public String toString() {
    return "aconst_null";
  }
}
