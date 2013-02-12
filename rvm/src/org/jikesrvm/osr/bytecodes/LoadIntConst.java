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
 * load an integer constant on the stack
 */
public class LoadIntConst extends PseudoBytecode {
  private static final int bsize = 6;
  private final int ibits;

  public LoadIntConst(int bits) {
    this.ibits = bits;
  }

  @Override
  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadIntConst);
    int2bytes(codes, 2, ibits);
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  @Override
  public int stackChanges() {
    return +1;
  }

  @Override
  public String toString() {
    return "LoadInt " + ibits;
  }
}
