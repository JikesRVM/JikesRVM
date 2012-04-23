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
 * checkcast instruction
 */
public class CheckCast extends PseudoBytecode {
  private static final int bsize = 6;
  private final int tid;

  public CheckCast(int typeId) {
    this.tid = typeId;
  }

  @Override
  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_CheckCast);
    int2bytes(codes, 2, tid);
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  @Override
  public int stackChanges() {
    return 0;
  }

  @Override
  public String toString() {
    return "CheckCast " + this.tid;
  }
}
