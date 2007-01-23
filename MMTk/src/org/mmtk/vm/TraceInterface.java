/**
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003.
 *
 * $Id$
 */
package org.mmtk.vm;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning Objects or Arrays for references
 * during tracing, handling those references, and computing death times
 * 
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public abstract class TraceInterface {


  /***********************************************************************
   * 
   * Public Methods
   */

  /**
   * Returns if the VM is ready for a garbage collection.
   * 
   * @return True if the VM is ready for GC, false otherwise.
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
  public abstract void updateDeathTime(Object obj);
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
