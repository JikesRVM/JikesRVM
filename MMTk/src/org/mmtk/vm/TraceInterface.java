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
package org.mmtk.vm;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning Objects or Arrays for references
 * during tracing, handling those references, and computing death times
 */
@Uninterruptible public abstract class TraceInterface {


  /***********************************************************************
   *
   * Public Methods
   */

  /**
   * Returns {@code true} if the VM is ready for a garbage collection.
   *
   * @return {@code true} if the VM is ready for GC, {@code false} otherwise.
   */
  public abstract boolean gcEnabled();

  /**
   * This adjusts the offset into an object to reflect what it would look like
   * if the fields were laid out in memory space immediately after the object
   * pointer.
   *
   * @param isScalar If this is a pointer store to a scalar object
   * @param src The address of the source object
   * @param slot The address within <code>src</code> into which
   * the update will be stored
   * @return The easy to understand offset of the slot
   */
  public abstract Offset adjustSlotOffset(boolean isScalar,
                                              ObjectReference src,
                                              Address slot);

  /**
   * This skips over the frames added by the tracing algorithm, outputs
   * information identifying the method the containts the "new" call triggering
   * the allocation, and returns the address of the first non-trace, non-alloc
   * stack frame.
   *
   *@param typeRef The type reference (tib) of the object just allocated
   * @return The frame pointer address for the method that allocated the object
   */
  @Interruptible
  public abstract Address skipOwnFramesAndDump(ObjectReference typeRef);

  /***********************************************************************
  *
  * Wrapper methods
  */

  /**
   * Update an object's death time.
   * @param obj the object
   */
  public abstract void updateDeathTime(ObjectReference obj);
  public abstract void setDeathTime(ObjectReference ref, Word time_);
  public abstract void setLink(ObjectReference ref, ObjectReference link);
  public abstract void updateTime(Word time_);
  public abstract Word getOID(ObjectReference ref);
  public abstract Word getDeathTime(ObjectReference ref);
  public abstract ObjectReference getLink(ObjectReference ref);
  public abstract Address getBootImageLink();
  public abstract Word getOID();
  public abstract void setOID(Word oid);
  public abstract int getHeaderSize();
  public abstract int getHeaderEndOffset();
}
