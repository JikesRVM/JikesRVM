/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package com.ibm.jikesrvm.osr;
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
