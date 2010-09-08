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

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible
public abstract class ObjectModel {

  /**
   * Copy an object using a plan's allocCopy to get space and install
   * the forwarding pointer.  On entry, <code>from</code> must have
   * been reserved for copying by the caller.  This method calls the
   * plan's <code>getStatusForCopy()</code> method to establish a new
   * status word for the copied object and <code>postCopy()</code> to
   * allow the plan to perform any post copy actions.
   *
   * @param from the address of the object to be copied
   * @param allocator The allocator to use.
   * @return the address of the new object
   */
  public abstract ObjectReference copy(ObjectReference from, int allocator);

  /**
   * Copy an object to be pointer to by the to address. This is required
   * for delayed-copy collectors such as compacting collectors. During the
   * collection, MMTk reserves a region in the heap for an object as per
   * requirements found from ObjectModel and then asks ObjectModel to
   * determine what the object's reference will be post-copy.
   *
   * @param from the address of the object to be copied
   * @param to The target location.
   * @param region The start of the region that was reserved for this object
   * @return Address The address past the end of the copied object
   */
  public abstract Address copyTo(ObjectReference from, ObjectReference to, Address region);

  /**
   * Return the reference that an object will be refered to after it is copied
   * to the specified region. Used in delayed-copy collectors such as compacting
   * collectors.
   *
   * @param from The object to be copied.
   * @param to The region to be copied to.
   * @return The resulting reference.
   */
  public abstract ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to);


  /**
   * Return the size required to copy an object
   *
   * @param object The object whose size is to be queried
   * @return The size required to copy <code>obj</code>
   */
  public abstract int getSizeWhenCopied(ObjectReference object);

  /**
   * Return the alignment requirement for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment required for a copy of <code>obj</code>
   */
  public abstract int getAlignWhenCopied(ObjectReference object);

  /**
   * Return the alignment offset requirements for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment offset required for a copy of <code>obj</code>
   */
  public abstract int getAlignOffsetWhenCopied(ObjectReference object);


  /**
   * Return the size used by an object
   *
   * @param object The object whose size is to be queried
   * @return The size of <code>obj</code>
   */
  public abstract int getCurrentSize(ObjectReference object);

  /**
   * Return the next object in the heap under contiguous allocation
   */
  public abstract ObjectReference getNextObject(ObjectReference object);

  /**
   * Return an object reference from knowledge of the low order word
   */
  public abstract ObjectReference getObjectFromStartAddress(Address start);
  /**
   * Gets a pointer to the address just past the end of the object.
   *
   * @param object The objecty.
   */
  public abstract Address getObjectEndAddress(ObjectReference object);


  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public abstract byte[] getTypeDescriptor(ObjectReference ref);

  /**
   * Is the passed object an array?
   *
   * @param object address of the object
   */
  public abstract boolean isArray(ObjectReference object);

  /**
   * Is the passed object a primitive array?
   *
   * @param object address of the object
   */
  public abstract boolean isPrimitiveArray(ObjectReference object);

  /**
   * Get the length of an array object.
   *
   * @param object address of the object
   * @return The array length, in elements
   */
  public abstract int getArrayLength(ObjectReference object);

  /**
   * Attempts to set the bits available for memory manager use in an
   * object.  The attempt will only be successful if the current value
   * of the bits matches <code>oldVal</code>.  The comparison with the
   * current value and setting are atomic with respect to other
   * allocators.
   *
   * @param object the address of the object
   * @param oldVal the required current value of the bits
   * @param newVal the desired new value of the bits
   * @return <code>true</code> if the bits were set,
   * <code>false</code> otherwise
   */
  public abstract boolean attemptAvailableBits(ObjectReference object,
      Word oldVal, Word newVal);

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public abstract Word prepareAvailableBits(ObjectReference object);

  /**
   * Sets the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the byte
   */
  public abstract void writeAvailableByte(ObjectReference object, byte val);
  /**
   * Read the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the byte
   */
  public abstract byte readAvailableByte(ObjectReference object);

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the bits
   */
  public abstract void writeAvailableBitsWord(ObjectReference object, Word val);
  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public abstract Word readAvailableBitsWord(ObjectReference object);

  /**
   * Gets the offset of the memory management header from the object
   * reference address.  XXX The object model / memory manager
   * interface should be improved so that the memory manager does not
   * need to know this.
   *
   * @return the offset, relative the object reference address
   */
  public abstract Offset GC_HEADER_OFFSET();

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  public abstract Address objectStartRef(ObjectReference object);

  /**
   * Returns an address guaranteed to be inside the storage assocatied
   * with and object.
   *
   * @param object the reference address of the object
   * @return an address inside the object
   */
  public abstract Address refToAddress(ObjectReference object);

  /**
   * Checks if a reference of the given type in another object is
   * inherently acyclic.  The type is given as a TIB.
   *
   * @return <code>true</code> if a reference of the type is
   * inherently acyclic
   */
  public abstract boolean isAcyclic(ObjectReference typeRef);

  /**
   * Dump debugging information for an object.
   *
   * @param object The object whose information is to be dumped
   */
  public abstract void dumpObject(ObjectReference object);

  /*
   * NOTE: The following methods must be implemented by subclasses of this
   * class, but are internal to the VM<->MM interface glue, so are never
   * called by MMTk users.
   */
  /** @return The offset from array reference to element zero */
  protected abstract Offset getArrayBaseOffset();

  /*
   * NOTE: These methods should not be called by anything other than the
   * reflective mechanisms in org.mmtk.vm.VM, and are not implemented by
   * subclasses.
   *
   * This hack exists only to allow us to declare the respective
   * methods as protected.
   */
  static Offset arrayBaseOffsetTrapdoor(ObjectModel o) {
    return o.getArrayBaseOffset();
  }
}
