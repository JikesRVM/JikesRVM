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
package org.jikesrvm.jni;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.RuntimeTable;
import org.vmmagic.Intrinsic;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.AddressArray;

/**
 * This class holds a triplet for each entry of the JNI function table.
 */
@NonMoving
public final class LinkageTripletTable implements RuntimeTable<AddressArray> {

  /**
   * The backing data used during boot image writing.
   */
  private final AddressArray[] data;

  /**
   * Private constructor. Can not create instances.
   */
  private LinkageTripletTable(int size) {
    this.data = new AddressArray[size];
  }

  /**
   * Create a new LinkageTripletTable of the specified size.
   *
   * @param size The size of the LinkageTripletTable
   * @return The created LinkageTripletTable instance.
   */
  public static LinkageTripletTable allocate(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return new LinkageTripletTable(size);
  }

  @Override
  public AddressArray[] getBacking() {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    return data;
  }

  /**
   * Get a LinkageTripletTable entry.
   *
   * @param index The index of the entry to get
   * @return The value of that entry
   */
  @Override
  @Intrinsic
  @Uninterruptible
  public AddressArray get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data[index];
  }

  /**
   * Set a LinkageTripletTable entry.
   *
   * @param index The index of the entry to set
   * @param value The value to set the entry to.
   */
  @Override
  @Intrinsic
  @UninterruptibleNoWarn("Interruptible code not reachable at runtime")
  public void set(int index, AddressArray value) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    data[index] = value;
  }

  /**
   * Return the length of the LinkageTripletTable
   */
  @Override
  @Intrinsic
  @Uninterruptible
  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);
    return data.length;
  }
}
