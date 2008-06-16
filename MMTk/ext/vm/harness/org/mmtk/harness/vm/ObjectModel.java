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
package org.mmtk.harness.vm;

import org.mmtk.harness.Collector;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible
public class ObjectModel extends org.mmtk.vm.ObjectModel {

  /*
   * The object model for the harness stores:
   *
   *    Object id (age in allocations);        (Word)
   *    The size of the data section in words. (UInt16)
   *    The number of reference words.         (UInt16)
   *    Status Word (includes GC)
   *    References
   *    Data
   *
   * Only tested using 32 bit and assumes at least 32 bits.
   */

  /** The total header size (including any requested GC words) */
  public static final int HEADER_WORDS = 3 + ActivePlan.constraints.gcHeaderWords();
  /** The number of bytes in the header */
  public static final int HEADER_SIZE = HEADER_WORDS << SimulatedMemory.LOG_BYTES_IN_WORD;
  /** The number of bytes requested for GC in the header */
  private static final int GC_HEADER_BYTES = ActivePlan.constraints.gcHeaderWords() << SimulatedMemory.LOG_BYTES_IN_WORD;

  /** The offset of the first GC header word */
  private static final Offset GC_OFFSET        = Offset.zero();
  /** The offset of the object ID */
  private static final Offset ID_OFFSET        = GC_OFFSET.plus(GC_HEADER_BYTES);
  /** The offset of the UInt16 storing the number of data fields */
  private static final Offset DATACOUNT_OFFSET = ID_OFFSET.plus(SimulatedMemory.BYTES_IN_WORD);
  /** The offset of the UInt16 storing the number of reference fields */
  private static final Offset REFCOUNT_OFFSET  = DATACOUNT_OFFSET.plus(SimulatedMemory.BYTES_IN_SHORT);
  /** The offset of the status word */
  private static final Offset STATUS_OFFSET    = DATACOUNT_OFFSET.plus(SimulatedMemory.BYTES_IN_WORD);
  /** The offset of the first reference field. */
  public  static final Offset REFS_OFFSET      = STATUS_OFFSET.plus(SimulatedMemory.BYTES_IN_WORD);

  /** Has this object been hashed? */
  private static final int HASHED           = 0x1 << (3 * SimulatedMemory.BITS_IN_BYTE);
  /** Has this object been moved since it was hashed? */
  private static final int HASHED_AND_MOVED = 0x3 << (3 * SimulatedMemory.BITS_IN_BYTE);
  /** Is this object 8 byte aligned */
  private static final int DOUBLE_ALIGN     = 0x1 << (2 * SimulatedMemory.BITS_IN_BYTE);

  /** The value placed in alignment holes */
  public static final int ALIGNMENT_VALUE = 1;

  /** The next object id that will be allocated */
  private static int nextObjectId = 1;

  /** Allocate a new (sequential) object id */
  private static synchronized int allocateObjectId() {
    return nextObjectId++;
  }

  /**
   * Allocate an object and return the ObjectReference.
   *
   * @param context The MMTk MutatorContext to use.
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   * @param doubleAlign Align the object at an 8 byte boundary?
   * @return The new ObjectReference.
   */
  public static ObjectReference allocateObject(MutatorContext context, int refCount, int dataCount, boolean doubleAlign) {
    int bytes = (HEADER_WORDS + refCount + dataCount) << SimulatedMemory.LOG_BYTES_IN_WORD;
    int align = (doubleAlign ? 2 : 1) * SimulatedMemory.BYTES_IN_WORD;
    int allocator = context.checkAllocator(bytes, SimulatedMemory.LOG_BYTES_IN_WORD, Plan.ALLOC_DEFAULT);

    // Allocate the raw memory
    Address region = context.alloc(bytes, align, 0, allocator, 0);

    // Create an object reference.
    ObjectReference ref = region.toObjectReference();
    if (doubleAlign) region.store(DOUBLE_ALIGN, STATUS_OFFSET);
    setId(ref, allocateObjectId());
    setRefCount(ref, refCount);
    setDataCount(ref, dataCount);

    // Call MMTk postAlloc
    context.postAlloc(ref, null, bytes, allocator);
    return ref;
  }

  /**
   * Get the number of references in the object.
   */
  public static int getRefs(ObjectReference object) {
    return object.toAddress().loadShort(REFCOUNT_OFFSET);
  }

  /**
   * Get the object identifier.
   */
  public static int getId(ObjectReference object) {
    return object.toAddress().loadInt(ID_OFFSET);
  }

  /**
   * Get the object identifier as a string.
   */
  public static String idString(ObjectReference object) {
    return Address.formatInt(object.toAddress().loadInt(ID_OFFSET));
  }

  /**
   * Set the object identifier.
   */
  public static void setId(ObjectReference object, int value) {
    object.toAddress().store(value, ID_OFFSET);
  }

  /**
   * Get the number of data words in the object.
   */
  public static int getDataCount(ObjectReference object) {
    return object.toAddress().loadShort(DATACOUNT_OFFSET);
  }

  /**
   * Set the number of data words in the object.
   */
  public static void setDataCount(ObjectReference object, int count) {
    object.toAddress().store((short)count, DATACOUNT_OFFSET);
  }


  /**
   * Set the number of references in the object.
   */
  public static void setRefCount(ObjectReference object, int count) {
    object.toAddress().store((short)count, REFCOUNT_OFFSET);
  }

  /**
   * Get the current size of an object.
   */
  public static int getSize(ObjectReference object) {
    int refs = getRefs(object);
    int data = getDataCount(object);
    boolean includesHash = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED_AND_MOVED) == HASHED_AND_MOVED;

    return getSize(refs, data) + (includesHash ? SimulatedMemory.BYTES_IN_WORD : 0);
  }

  /**
   * Get the size this object will require when copied.
   */
  public static int getCopiedSize(ObjectReference object) {
    int refs = getRefs(object);
    int data = getDataCount(object);
    boolean needsHash = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED) == HASHED;

    return getSize(refs, data) + (needsHash ? SimulatedMemory.BYTES_IN_WORD : 0);
  }

  /**
   * Return the address of the specified reference.
   *
   * @param object The object with the references.
   * @param index The reference index.
   */
  public static Address getRefSlot(ObjectReference object, int index) {
    return object.toAddress().plus(REFS_OFFSET).plus(index << SimulatedMemory.LOG_BYTES_IN_WORD);
  }

  /**
   * Return the address of the specified data slot.
   *
   * @param object The object with the data slot.
   * @param index The data slot index.
   */
  public static Address getDataSlot(ObjectReference object, int index) {
    return getRefSlot(object, index + getRefs(object));
  }

  /**
   * Calculate the size of an object.
   */
  public static int getSize(int refs, int data) {
    return (HEADER_WORDS + refs + data) << SimulatedMemory.LOG_BYTES_IN_WORD;
  }

  /**
   * Return the hash code for this object.
   *
   * @param ref The object.
   * @return The hash code
   */
  public static int getHashCode(ObjectReference ref) {
    Address addr = ref.toAddress();
    int status = addr.loadInt(STATUS_OFFSET) & HASHED_AND_MOVED;
    if (status == 0) {
      // Set status to be HASHED
      int old;
      do {
        old = addr.prepareInt(STATUS_OFFSET);
      } while (!addr.attempt(old, old | HASHED, STATUS_OFFSET));
    } else if (status == HASHED_AND_MOVED) {
      // Load stored hash code
      return addr.loadInt(Offset.fromIntZeroExtend(getSize(ref) - SimulatedMemory.BYTES_IN_WORD));
    }
    return addr.toInt() >>> SimulatedMemory.LOG_BYTES_IN_WORD;
  }

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
  public ObjectReference copy(ObjectReference from, int allocator) {
    int oldBytes = getSize(from);
    int newBytes = getCopiedSize(from);
    int align = getAlignWhenCopied(from);
    CollectorContext c = Collector.current().getContext();
    allocator = c.copyCheckAllocator(from, newBytes, align, allocator);
    Address toRegion = c.allocCopy(from, newBytes, align, getAlignOffsetWhenCopied(from), allocator);

    Address fromRegion = from.toAddress();
    for(int i=0; i < oldBytes; i += SimulatedMemory.BYTES_IN_WORD) {
      SimulatedMemory.setInt(toRegion.plus(i), SimulatedMemory.getInt(fromRegion.plus(i)));
    }

    int status = toRegion.loadInt(STATUS_OFFSET);
    if ((status & HASHED_AND_MOVED) == HASHED) {
      toRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
      toRegion.store(getHashCode(from), Offset.fromIntZeroExtend(oldBytes));
    }

    ObjectReference to = toRegion.toObjectReference();

    c.postCopy(to, null, newBytes, allocator);
    return to;
  }

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
  public Address copyTo(ObjectReference from, ObjectReference to, Address toRegion) {
    int bytes = getSize(from);
    Address fromRegion = from.toAddress();
    for(int i=0; i < (bytes >>> SimulatedMemory.LOG_BYTES_IN_WORD); i++) {
      SimulatedMemory.setInt(toRegion.plus(i), SimulatedMemory.getInt(fromRegion.plus(i)));
    }

    int status = toRegion.loadInt(STATUS_OFFSET);
    if ((status & HASHED_AND_MOVED) == HASHED) {
      toRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
      toRegion.store(getHashCode(from), Offset.fromIntZeroExtend(bytes));
      return toRegion.plus(bytes + SimulatedMemory.BYTES_IN_WORD);
    }

    return toRegion.plus(bytes);
  }

  /**
   * Return the reference that an object will be refered to after it is copied
   * to the specified region. Used in delayed-copy collectors such as compacting
   * collectors.
   *
   * @param from The object to be copied.
   * @param to The region to be copied to.
   * @return The resulting reference.
   */
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return to.toObjectReference();
  }


  /**
   * Return the size required to copy an object
   *
   * @param object The object whose size is to be queried
   * @return The size required to copy <code>obj</code>
   */
  public int getSizeWhenCopied(ObjectReference object) {
    return getCopiedSize(object);
  }

  /**
   * Return the alignment requirement for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment required for a copy of <code>obj</code>
   */
  public int getAlignWhenCopied(ObjectReference object) {
    boolean doubleAlign = (object.toAddress().loadInt(STATUS_OFFSET) & DOUBLE_ALIGN) == DOUBLE_ALIGN;
    return (doubleAlign ? 2 : 1) * SimulatedMemory.BYTES_IN_WORD;
  }

  /**
   * Return the alignment offset requirements for a copy of this object
   *
   * @param object The object whose size is to be queried
   * @return The alignment offset required for a copy of <code>obj</code>
   */
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    return 0;
  }

  /**
   * Return the size used by an object
   *
   * @param object The object whose size is to be queried
   * @return The size of <code>obj</code>
   */
  public int getCurrentSize(ObjectReference object) {
    return getSize(object);
  }

  /**
   * Return the next object in the heap under contiguous allocation
   */
  public ObjectReference getNextObject(ObjectReference object) {
    Address nextAddress = object.toAddress().plus(getSize(object));
    if (nextAddress.loadInt() == ALIGNMENT_VALUE) {
      nextAddress = nextAddress.plus(SimulatedMemory.BYTES_IN_WORD);
    }
    if (nextAddress.loadWord().isZero()) {
      return ObjectReference.nullReference();
    }
    return nextAddress.toObjectReference();
  }

  /**
   * Return an object reference from knowledge of the low order word
   */
  public ObjectReference getObjectFromStartAddress(Address start) {
    if (start.loadInt() == ALIGNMENT_VALUE) {
      start = start.plus(SimulatedMemory.BYTES_IN_WORD);
    }
    return start.toObjectReference();
  }

  /**
   * Gets a pointer to the address just past the end of the object.
   *
   * @param object The object.
   */
  public Address getObjectEndAddress(ObjectReference object) {
    return object.toAddress().plus(getSize(object));
  }

  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public byte[] getTypeDescriptor(ObjectReference ref) {
    return getString(ref).getBytes();
  }

  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public String getString(ObjectReference ref) {
    int refs = getRefs(ref);
    int data = getDataCount(ref);
    int size = getSize(ref);
    return ("Object[" + size + "bytes " + refs + "R / " + data + "D]");
  }

  /**
   * Is the passed object an array?
   *
   * @param object address of the object
   */
  public boolean isArray(ObjectReference object) {
    Assert.notImplemented();
    return false;
  }

  /**
   * Is the passed object a primitive array?
   *
   * @param object address of the object
   */
  public boolean isPrimitiveArray(ObjectReference object) {
    Assert.notImplemented();
    return false;
  }

  /**
   * Get the length of an array object.
   *
   * @param object address of the object
   * @return The array length, in elements
   */
  public int getArrayLength(ObjectReference object) {
    Assert.notImplemented();
    return 0;
  }

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
  public boolean attemptAvailableBits(ObjectReference object, Word oldVal, Word newVal) {
    return object.toAddress().attempt(oldVal, newVal, STATUS_OFFSET);
  }

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word prepareAvailableBits(ObjectReference object) {
    return object.toAddress().prepareWord(STATUS_OFFSET);
  }

  /**
   * Sets the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the byte
   */
  public void writeAvailableByte(ObjectReference object, byte val) {
    object.toAddress().store(val, STATUS_OFFSET);
  }

  /**
   * Read the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the byte
   */
  public byte readAvailableByte(ObjectReference object) {
    return object.toAddress().loadByte(STATUS_OFFSET);
  }

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the bits
   */
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    object.toAddress().store(val, STATUS_OFFSET);
  }

  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  public Word readAvailableBitsWord(ObjectReference object) {
    return object.toAddress().loadWord(STATUS_OFFSET);
  }

  /**
   * Gets the offset of the memory management header from the object
   * reference address.  XXX The object model / memory manager
   * interface should be improved so that the memory manager does not
   * need to know this.
   *
   * @return the offset, relative the object reference address
   */
  public Offset GC_HEADER_OFFSET() {
    return GC_OFFSET;
  }

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  public Address objectStartRef(ObjectReference object) {
    return object.toAddress();
  }

  /**
   * Returns an address guaranteed to be inside the storage assocatied
   * with and object.
   *
   * @param object the reference address of the object
   * @return an address inside the object
   */
  public Address refToAddress(ObjectReference object) {
    return object.toAddress();
  }

  /**
   * Checks if a reference of the given type in another object is
   * inherently acyclic.  The type is given as a TIB.
   *
   * @return <code>true</code> if a reference of the type is
   * inherently acyclic
   */
  public boolean isAcyclic(ObjectReference typeRef) {
    return false;
  }

  /**
   * Dump debugging information for an object.
   *
   * @param object The object whose information is to be dumped
   */
  public void dumpObject(ObjectReference object) {
    System.err.println("===================================");
    System.err.println(getString(object) + " @" + object.toAddress());
    System.err.println("===================================");
    SimulatedMemory.dumpMemory(object.toAddress(), 0, getSize(object));
    System.err.println("===================================");
  }

  /** @return The offset from array reference to element zero */
  protected Offset getArrayBaseOffset() {
    return REFS_OFFSET;
  }
}
