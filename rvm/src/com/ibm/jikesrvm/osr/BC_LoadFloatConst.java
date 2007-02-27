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
 * BC_LoadFloatConst: ldc, ldc_w 
 *
 * @author Feng Qian
 */
public class BC_LoadFloatConst extends OSR_PseudoBytecode {
  private static final int bsize = 6;
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
