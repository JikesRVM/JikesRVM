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
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * This class represents an instance of an array of interface tables.
 */
public final class VM_ITableArray {

  /**
   * The backing data used during boot image writing.
   */
  private final VM_ITable[] backingData;

  /**
   * Private constructor. Can not create instances.
   */
  private VM_ITableArray(int size) {
    this.backingData = new VM_ITable[size];
  }

  /**
   * Return the backing array (for boot image writing)
   */
  public Object[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return backingData;
  }

  /**
   * Create a new TIB of the specified size.
   *
   * @param size The size of the TIB
   * @return The created TIB instance.
   */
  public static VM_ITableArray allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_ITableArray(size);
  }

  /**
   * Get a TIB entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Intrinsic
  @Uninterruptible
  public VM_ITable get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return backingData[index];
  }

  /**
   * Set a TIB entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Intrinsic
  @UninterruptibleNoWarn
  public void set(int index, VM_ITable value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    backingData[index] = value;
  }

  /**
   * Return the length of the TIB
   */
  @Intrinsic
  @Uninterruptible
  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return backingData.length;
  }
}
