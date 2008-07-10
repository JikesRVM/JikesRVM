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
package org.jikesrvm.osr.bytecodes;


/**
 * checkcast instruction
 */
public class BC_CheckCast extends OSR_PseudoBytecode {
  private static final int bsize = 6;
  private final int tid;

  public BC_CheckCast(int typeId) {
    this.tid = typeId;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_CheckCast);
    int2bytes(codes, 2, tid);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    return 0;
  }

  public String toString() {
    return "CheckCast " + this.tid;
  }
}
