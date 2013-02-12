/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.osr.bytecodes;


/**
 * artificial instruction, load a PC on the stack.
 */
public class LoadRetAddrConst extends PseudoBytecode {
  private static final int bsize = 6;
  private int bcindex;

  public LoadRetAddrConst(int off) {
    this.bcindex = off;
  }

  @Override
  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadRetAddrConst);
    int2bytes(codes, 2, bcindex);
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  public int getOffset() {
    return bcindex;
  }

  @Override
  public int stackChanges() {
    return +1;
  }

  public void patch(int off) {
    this.bcindex = off;
  }

  @Override
  public String toString() {
    return "LoadRetAddrConst " + bcindex;
  }
}
