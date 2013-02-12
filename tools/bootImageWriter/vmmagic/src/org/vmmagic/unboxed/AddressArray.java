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

import org.vmmagic.pragma.*;
import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.RuntimeTable;

/**
 * The VM front end is not capable of correct handling an array of Address, Word, ....
 * In the boot image writer we provide special types to handle these situations.
 */
@Uninterruptible
public final class AddressArray implements RuntimeTable<Address> {

  private final Address[] data;

  @Interruptible
  public static AddressArray create(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    return new AddressArray(size);
  }

  private AddressArray(int size) {
    data = new Address[size];
    Address zero = Address.zero();
    for (int i=0; i<size; i++) {
      data[i] = zero;
    }
  }

  @Inline
  public Address get(int index) {
    if (VM.VerifyAssertions && (VM.runningVM || VM.writingImage)) VM._assert(VM.NOT_REACHED);  // should be hijacked
    return data[index];
  }

  @Inline
  public void set(int index, Address v) {
    if (VM.VerifyAssertions && (VM.runningVM || VM.writingImage)) VM._assert(VM.NOT_REACHED);
    data[index] = v;
  }

  @Inline
  public int length() {
    if (VM.VerifyAssertions && (VM.runningVM || VM.writingImage)) VM._assert(VM.NOT_REACHED);
    return data.length;
  }

  @Inline
  public Address[] getBacking() {
    if (!VM.writingImage)
      VM.sysFail("AddressArray.getBacking called when not writing boot image");
    return data;
  }
}
