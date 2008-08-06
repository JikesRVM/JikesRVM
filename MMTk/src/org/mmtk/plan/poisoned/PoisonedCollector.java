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
package org.mmtk.plan.poisoned;

import org.mmtk.plan.marksweep.MSCollector;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements a poisoned collector, that is essentially a test
 * case for read and write barriers in the VM.
 */
@Uninterruptible
public class PoisonedCollector extends MSCollector {
  /****************************************************************************
   *
   * Collector read/write barriers.
   */

  /**
   * Store an object reference
   *
   * @param slot The location of the reference
   * @param value The value to store
   */
  @Inline
  public void storeObjectReference(Address slot, ObjectReference value) {
    slot.store(Poisoned.poison(value));
  }

  /**
   * Load an object reference
   *
   * @param slot The location of the reference
   * @return the object reference loaded from slot
   */
  @Inline
  public ObjectReference loadObjectReference(Address slot) {
    return Poisoned.depoison(slot.loadWord());
  }
}
