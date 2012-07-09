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
 * BC_RefStore: {@code astore}, {@code astore_<i>}
 */
public class RefStore extends PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;

  public RefStore(int local) {
    this.lnum = local;

    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_astore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_astore, local);
    }
  }

  @Override
  public byte[] getBytes() {
    return codes;
  }

  @Override
  public int getSize() {
    return bsize;
  }

  @Override
  public int stackChanges() {
    return -1;
  }

  @Override
  public String toString() {
    return "astore " + this.lnum;
  }
}
