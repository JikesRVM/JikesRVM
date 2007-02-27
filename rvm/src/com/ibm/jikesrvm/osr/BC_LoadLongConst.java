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
 * load a long constant on the stack
 *
 * @author Feng Qian
 */
public class BC_LoadLongConst extends OSR_PseudoBytecode {
  private static final int bsize = 10;
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
