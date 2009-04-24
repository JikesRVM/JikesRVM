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
package org.vmmagic.unboxed;

public final class OffsetArray {

  private Offset[] data;

  private OffsetArray(int size) {
    data = new Offset[size];
    Offset zero = Offset.zero();
    for(int i=0; i<size;i++) {
      data[i] = zero;
    }
  }

  public static OffsetArray create(int size) {
    return new OffsetArray(size);
  }

  public Offset get(int index) {
    return data[index];
  }

  public void set(int index, Offset v) {
    data[index] = v;
  }

  public int length() {
    return data.length;
  }
}
