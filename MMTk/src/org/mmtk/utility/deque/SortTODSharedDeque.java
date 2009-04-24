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
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class specializes SortSharedQueue to sort objects according to
 * their time of death (TOD).
 */
@Uninterruptible
public final class SortTODSharedDeque extends SortSharedDeque {

  /**
   * Constructor
   *
   * @param rps The space from which the instance should obtain buffers.
   * @param arity The arity of the data to be enqueued
   */
  public SortTODSharedDeque(String name, RawPageSpace rps, int arity) {
    super(name, rps, arity);
  }

  /**
   * Return the sorting key for the object passed as a parameter.
   *
   * @param obj The address of the object whose key is wanted
   * @return The value of the sorting key for this object
   */
  protected Word getKey(Address obj) {
    return VM.traceInterface.getDeathTime(obj.toObjectReference());
  }
}
