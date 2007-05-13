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
 * artificial instruction, load a PC on the stack. 
 */

public class BC_LoadRetAddrConst extends OSR_PseudoBytecode {
  private static final int bsize = 6;
  private int bcindex;

  public BC_LoadRetAddrConst(int off) {
    this.bcindex = off;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadRetAddrConst);
    int2bytes(codes, 2, bcindex);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int getOffset() {
    return bcindex;
  }

  public int stackChanges() {
    return +1;
  }

  public void patch(int off) {
    this.bcindex = off;
  }

  public String toString() {
    return "LoadRetAddrConst " + bcindex;
  }
}
