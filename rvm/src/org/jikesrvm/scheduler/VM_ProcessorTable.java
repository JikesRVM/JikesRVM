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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * This class represents an instance of a table of processors
 */
public final class VM_ProcessorTable {

  /**
   * The backing data used during boot image writing.
   */
  private final VM_Processor[] data;

  /**
   * Private constructor. Can not create instances.
   */
  private VM_ProcessorTable(int size) {
    this.data = new VM_Processor[size];
  }

  /**
   * Create a new ITable of the specified size.
   *
   * @param size The size of the ITable
   * @return The created ITable instance.
   */
  public static VM_ProcessorTable allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_ProcessorTable(size);
  }

  /**
   * Return the backing array (for boot image writing)
   */
  public Object[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return data;
  }

  /**
   * Get an ITable entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Intrinsic
  @Uninterruptible
  public VM_Processor get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data[index];
  }

  /**
   * Set an ITable entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Intrinsic
  @UninterruptibleNoWarn
  public void set(int index, VM_Processor value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    data[index] = value;
  }

  /**
   * Return the length of the ITable
   */
  @Intrinsic
  @Uninterruptible
  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data.length;
  }
}
