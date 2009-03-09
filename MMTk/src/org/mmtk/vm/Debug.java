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
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

/**
 *
 */
@Uninterruptible
public abstract class Debug {
  /**
   * Global switch for debugging - if false the other methods of this
   * class are never called.
   * @return Whether debugging is enabled
   */
  public abstract boolean isEnabled();

  /**
   * A modbuf (object remembering barrier) entry has been
   * traced during collection.
   * @param object
   */
  public void modbufEntry(ObjectReference object) { }

  /**
   * A remset (slot remembering barrier) entry has been
   * traced during collection.
   * @param slot
   */
  public void remsetEntry(Address slot) { }

  /**
   * An array remset entry has been traced during collection.  Implicitly
   * the slots from start (inclusive) through to guard (non-inclusive)
   * are traced as remset entries
   * @param start
   * @param guard
   */
  public void arrayRemsetEntry(Address start, Address guard) { }

  /**
   * A global GC collection phase
   * @param phaseId The phase ID
   * @param before true at the start of the phase, false at the end
   */
  public void globalPhase(short phaseId, boolean before) { }

  /**
   * A per-collector GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The collector ID (within this collection)
   * @param before true at the start of the phase, false at the end
   */
  public void collectorPhase(short phaseId, int ordinal, boolean before) { }

  /**
   * A per-mutator GC collection phase
   * @param phaseId The phase ID
   * @param ordinal The mutator ID
   * @param before true at the start of the phase, false at the end
   */
  public void mutatorPhase(short phaseId, int ordinal, boolean before) { }

  /*
   * NOTE: These methods should not be called by anything other than the
   * reflective mechanisms in org.mmtk.vm.VM, and are not implemented by
   * subclasses.
   *
   * This hack exists only to allow us to declare the respective
   * methods as protected.
   */
  static final boolean isEnabledTrapdoor(Debug d) {
    return d.isEnabled();
  }

}
