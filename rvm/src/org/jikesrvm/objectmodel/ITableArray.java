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
package org.jikesrvm.objectmodel;

import org.jikesrvm.VM;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * This class represents an instance of an array of interface tables.
 */
@NonMoving
public final class ITableArray implements RuntimeTable<ITable> {

  /**
   * The backing data used during boot image writing.
   */
  private final ITable[] backingData;

  /**
   * Private constructor. Can not create instances.
   */
  private ITableArray(int size) {
    this.backingData = new ITable[size];
  }

  /**
   * Return the backing array (for boot image writing)
   */
  @Override
  public ITable[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return backingData;
  }

  /**
   * Create a new TIB of the specified size.
   *
   * @param size The size of the TIB
   * @return The created TIB instance.
   */
  public static ITableArray allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new ITableArray(size);
  }

  /**
   * Get a TIB entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Override
  @Intrinsic
  @Uninterruptible
  public ITable get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return backingData[index];
  }

  /**
   * Set a TIB entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Override
  @Intrinsic
  @UninterruptibleNoWarn("Interruptible code not reachable at runtime")
  public void set(int index, ITable value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    backingData[index] = value;
  }

  /**
   * Return the length of the TIB
   */
  @Override
  @Intrinsic
  @Uninterruptible
  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return backingData.length;
  }
}
