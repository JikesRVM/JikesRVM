/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
