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
 * BC_IntStore : istore_<?>, istore
 *
 *      Local number            Instruction
 *      [0, 3]                  istore_<i>
 *      other                   istore, wide istore
 */
public class IntStore extends PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;

  public IntStore(int local) {
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_istore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_istore, local);
    }
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    return -1;
  }

  public String toString() {
    return "istore " + lnum;
  }
}
