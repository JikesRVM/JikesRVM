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
 * load a long constant on the stack
 */
public class LoadLongConst extends PseudoBytecode {
  private static final int bsize = 10;
  private final long lbits;

  public LoadLongConst(long bits) {
    this.lbits = bits;
  }

  @Override
  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadLongConst);
    long2bytes(codes, 2, lbits);
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  @Override
  public int stackChanges() {
    return 2;
  }

  @Override
  public String toString() {
    return "LoadLong " + lbits;
  }
}
