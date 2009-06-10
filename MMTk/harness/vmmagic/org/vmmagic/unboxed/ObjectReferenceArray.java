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

public final class ObjectReferenceArray {

  private ObjectReference[] data;

  private ObjectReferenceArray(int size) {
    data = new ObjectReference[size];
    ObjectReference nullRef = ObjectReference.nullReference();
    for(int i=0; i<size;i++) {
      data[i] = nullRef;
    }
  }

  public static ObjectReferenceArray create(int size) {
    return new ObjectReferenceArray(size);
  }

  public ObjectReference get(int index) {
    return data[index];
  }

  public void set(int index, ObjectReference v) {
    data[index] = v;
  }

  public int length() {
    return data.length;
  }
}
