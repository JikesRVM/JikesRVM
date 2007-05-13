/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;

/**
 * BC_DoubleStore: dstore, dstore_<l> 
 * 
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
