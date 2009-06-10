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

import org.vmmagic.unboxed.Word;

/**
 * load a word constant on the stack
 */
public class LoadWordConst extends PseudoBytecode {
  private static final int bsize = 2 + BYTES_IN_ADDRESS;
  private final Word wbits;

  public LoadWordConst(Word bits) {
    this.wbits = bits;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_LoadWordConst);
    word2bytes(codes, 2, wbits);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    return +1;
  }

  public String toString() {
    return "LoadWord 0x" + Long.toHexString(wbits.toLong());
  }
}
