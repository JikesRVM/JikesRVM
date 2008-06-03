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
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * This class represents an instance of an interface table.
 */
@NonMoving
public final class VM_ITable {

  /**
   * The backing data used during boot image writing.
   */
  private final Object[] data;

  /**
   * Private constructor. Can not create instances.
   */
  private VM_ITable(int size) {
    this.data = new Object[size];
  }

  /**
   * Create a new ITable of the specified size.
   *
   * @param size The size of the ITable
   * @return The created ITable instance.
   */
  public static VM_ITable allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new VM_ITable(size);
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
  protected Object get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data[index];
  }

  /**
   * Does this ITable correspond to the given interface?
   *
   * @param I The interface
   * @return True if this ITable is for the given interface
   */
  @Inline
  @Uninterruptible
  public boolean isFor(VM_Type I) {
    return get(0) == I;
  }

  /**
   * Does this ITable correspond to the given interface?
   *
   * @param I The interface
   * @return True if this ITable is for the given interface
   */
  @Inline
  @Interruptible
  public VM_Class getInterfaceClass() {
    return (VM_Class)get(0);
  }


  /**
   * Get the code array at the given index.
   *
   * @param index The index
   * @return The code array
   */
  @Inline
  @Interruptible
  public CodeArray getCode(int index) {
    if (VM.VerifyAssertions) VM._assert(index < length());
    return (CodeArray)get(index);
  }
  /**
   * Set an ITable entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Intrinsic
  @UninterruptibleNoWarn
  public void set(int index, Object value) {
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
