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
 * load an integer constant on the stack
 */
public class BC_LoadIntConst extends OSR_PseudoBytecode {
  private static final int bsize = 6;
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
    return "LoadInt " + ibits;
  }
}
