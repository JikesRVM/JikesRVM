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
package org.jikesrvm.mm.mmtk;

import org.mmtk.utility.Constants;
import org.jikesrvm.runtime.VM_Time;
import org.jikesrvm.memorymanagers.mminterface.MM_Interface;

import org.vmmagic.pragma.*;

@Uninterruptible public final class Statistics extends org.mmtk.vm.Statistics implements Constants {
  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  @Uninterruptible
  public int getCollectionCount() {
    return MM_Interface.getCollectionCount();
  }

  /**
   * Read cycle counter
   */
  public long cycles() {
    return VM_Time.cycles();
  }

  /**
   * Convert cycles to milliseconds
   */
  public double cyclesToMillis(long c) {
    return VM_Time.cyclesToMillis(c);
  }

  /**
   * Convert cycles to seconds
   */
  public double cyclesToSecs(long c) {
    return VM_Time.cyclesToSecs(c);
  }

  /**
   * Convert milliseconds to cycles
   */
  public long millisToCycles(double t) {
    return VM_Time.millisToCycles(t);
  }

  /**
   * Convert seconds to cycles
   */
  public long secsToCycles(double t) {
    return VM_Time.secsToCycles(t);
  }
}
