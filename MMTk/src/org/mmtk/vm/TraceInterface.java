/**
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003.
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
public final class TraceInterface {


  /***********************************************************************
   *
   * Public Methods
   */

  /**
   * Returns if the VM is ready for a garbage collection.
   *
   * @return True if the RVM is ready for GC, false otherwise.
   */
  public static final boolean gcEnabled() {
    return false;
  }


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
  public static final Offset adjustSlotOffset(boolean isScalar, 
					      ObjectReference src,
                                                 Address slot) {
    return null;
  }

  /**
   * This skips over the frames added by the tracing algorithm, outputs 
   * information identifying the method the containts the "new" call triggering
   * the allocation, and returns the address of the first non-trace, non-alloc
   * stack frame.
   *
   *@param typeRef The type reference (tib) of the object just allocated
   *@return The frame pointer address for the method that allocated the object
   */
  public static final Address skipOwnFramesAndDump(ObjectReference typeRef)
    {
    return null;
  }

  /***********************************************************************
   *
   * Wrapper methods
   */

  public static void updateDeathTime(Object obj) throws InlinePragma {
  }

  public static void setDeathTime(ObjectReference ref, Word time_) 
    {
  }

  public static void setLink(ObjectReference ref, ObjectReference link) 
{
  }

  public static void updateTime(Word time_) throws InlinePragma {
  }

  public static Word getOID(ObjectReference ref) throws InlinePragma {
    return null;
  }

  public static Word getDeathTime(ObjectReference ref) throws InlinePragma {
    return null;
  }

  public static ObjectReference getLink(ObjectReference ref){
    return null;
  }

  public static Address getBootImageLink() throws InlinePragma {
    return null;
  }

  public static Word getOID() throws InlinePragma {
    return null;
  }

  public static void setOID(Word oid) throws InlinePragma {
  }

  public static final int getHeaderSize() throws InlinePragma {
return 0;
  }

  public static final int getHeaderEndOffset() throws InlinePragma {
  return 0;
  }
}
