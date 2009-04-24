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

public final class AddressArray {

  private Address[] data;

  private AddressArray(int size) {
    data = new Address[size];
    Address zero = Address.zero();
    for(int i=0; i<size;i++) {
      data[i] = zero;
    }
  }

  public static AddressArray create(int size) {
    return new AddressArray(size);
  }

  public Address get(int index) {
    return data[index];
  }

  public void set(int index, Address v) {
    data[index] = v;
  }

  public int length() {
    return data.length;
  }
}
