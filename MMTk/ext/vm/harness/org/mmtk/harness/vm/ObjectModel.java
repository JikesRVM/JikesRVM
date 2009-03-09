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

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import org.mmtk.harness.Collector;
import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

@Uninterruptible
public final class ObjectModel extends org.mmtk.vm.ObjectModel {

  /*
   * The object model for the harness stores:
   *
   *    Object id (age in allocations);        (Word)
   *    Allocation site                        (Word)
   *    The size of the data section in words. (UInt16)
   *    The number of reference words.         (UInt16)
   *    Status Word (includes GC)
   *    References
   *    Data
   */

  private static final boolean IS_32_BIT = ArchitecturalWord.getModel().bitsInWord() == 32;

  /** The total header size (including any requested GC words) */
  public static final int HEADER_WORDS = (IS_32_BIT ? 5 : 3) + ActivePlan.constraints.gcHeaderWords();
  /** The number of bytes in the header */
  private static final int HEADER_SIZE = HEADER_WORDS << SimulatedMemory.LOG_BYTES_IN_WORD;
  /** The number of bytes requested for GC in the header */
  private static final int GC_HEADER_BYTES = ActivePlan.constraints.gcHeaderWords() << SimulatedMemory.LOG_BYTES_IN_WORD;

  /** The offset of the first GC header word */
  private static final Offset GC_OFFSET        = Offset.zero();
  /** The offset of the object ID */
  private static final Offset ID_OFFSET        = GC_OFFSET.plus(GC_HEADER_BYTES);
  /** The offset of the allocation site */
  private static final Offset SITE_OFFSET      = ID_OFFSET.plus(SimulatedMemory.BYTES_IN_INT);
  /** The offset of the UInt16 storing the number of data fields */
  private static final Offset DATACOUNT_OFFSET = SITE_OFFSET.plus(SimulatedMemory.BYTES_IN_INT);
  /** The offset of the UInt16 storing the number of reference fields */
  private static final Offset REFCOUNT_OFFSET  = DATACOUNT_OFFSET.plus(SimulatedMemory.BYTES_IN_INT);
  /** The offset of the status word */
  private static final Offset STATUS_OFFSET    = REFCOUNT_OFFSET.plus(SimulatedMemory.BYTES_IN_INT);
  /** The offset of the first reference field. */
  public  static final Offset REFS_OFFSET      = STATUS_OFFSET.plus(SimulatedMemory.BYTES_IN_WORD);

  @SuppressWarnings("unused")
  private static void printObjectLayout(PrintStream wr) {
    wr.printf("GC_OFFSET=%s:%d, ID_OFFSET=%s, SITE_OFFSET=%s%n",
        GC_OFFSET, GC_HEADER_BYTES, ID_OFFSET, SITE_OFFSET);
    wr.printf("DATACOUNT_OFFSET=%s, REFCOUNT_OFFSET=%s, STATUS_OFFSET=%s%n",
        DATACOUNT_OFFSET, REFCOUNT_OFFSET, STATUS_OFFSET);
    wr.printf("REFS_OFFSET=%s, HEADER_SIZE=%d%n",
        REFS_OFFSET,HEADER_SIZE);
    wr.flush();
  }

  /** Max data fields in an object */
  public static final int MAX_DATA_FIELDS = Integer.MAX_VALUE;
  /** Max pointer fields in an object */
  public static final int MAX_REF_FIELDS = Integer.MAX_VALUE;

  /** Has this object been hashed? */
  private static final int HASHED           = 0x1 << (3 * SimulatedMemory.BITS_IN_BYTE);
  /** Has this object been moved since it was hashed? */
  private static final int HASHED_AND_MOVED = 0x3 << (3 * SimulatedMemory.BITS_IN_BYTE);
  /** Is this object 8 byte aligned */
  private static final int DOUBLE_ALIGN     = 0x1 << (2 * SimulatedMemory.BITS_IN_BYTE);

  /** The value placed in alignment holes */
  public static final int ALIGNMENT_VALUE = 1;

  /*
   * Object identifiers.  Objects are allocated a sequential identifier.
   */

  /** The next object id that will be allocated */
  private static int nextObjectId = 1;

  /** Allocate a new (sequential) object id */
  private static synchronized int allocateObjectId() {
    return nextObjectId++;
  }

  /** Return the last object ID allocated - for error reporting: NOT THREAD SAFE!!! */
  public static int lastObjectId() {
    return nextObjectId;
  }

  /*
   * Watchpoints by object identifier.
   */

  private static final Set<Integer> watchSet = new HashSet<Integer>();

  /**
   * @param object The object
   * @return True if this object is being watched
   */
  public static boolean isWatched(ObjectReference object) {
    return watchSet.contains(getId(object));
  }

  static {
    //printObjectLayout(System.out);
    assert REFS_OFFSET.EQ(Offset.fromIntSignExtend(HEADER_SIZE));
    //watchSet.add(4109);
    //watchSet.add(8205);
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
  public static ObjectReference allocateObject(MutatorContext context, int refCount, int dataCount, boolean doubleAlign, int site) {
    int bytes = (HEADER_WORDS + refCount + dataCount) << SimulatedMemory.LOG_BYTES_IN_WORD;
    int align = ArchitecturalWord.getModel().bitsInWord() == 64 ?
        SimulatedMemory.BYTES_IN_WORD :
        (doubleAlign ? 2 : 1) * SimulatedMemory.BYTES_IN_INT;
    int allocator = context.checkAllocator(bytes, align, Plan.ALLOC_DEFAULT);

    // Allocate the raw memory
    Address region = context.alloc(bytes, align, 0, allocator, 0);

    // Create an object reference.
    ObjectReference ref = region.toObjectReference();
    if (doubleAlign) region.store(DOUBLE_ALIGN, STATUS_OFFSET);
    setId(ref, allocateObjectId());
    setSite(ref, site);
    setRefCount(ref, refCount);
    setDataCount(ref, dataCount);

    if (isWatched(ref)) {
      System.err.printf("WATCH: Object %s created%n",objectIdString(ref));
    }

    // Call MMTk postAlloc
    context.postAlloc(ref, null, bytes, allocator);
    return ref;
  }

  /**
   * Get the number of references in an object.
   * @param object The object
   * @return number of reference fields in the object.
   */
  public static int getRefs(ObjectReference object) {
    return object.toAddress().loadInt(REFCOUNT_OFFSET);
  }

  /**
   * Get the object identifier.
   * @param object The object
   * @return The object identifier
   */
  public static int getId(ObjectReference object) {
    return object.toAddress().loadInt(ID_OFFSET) >>> 2;
  }

  /**
   * Set the object identifier.
   */
  private static void setId(ObjectReference object, int value) {
    object.toAddress().store(value << 2, ID_OFFSET);
  }

  /**
   * Set the call site identifier.
   */
  private static void setSite(ObjectReference object, int site) {
    object.toAddress().store(site, SITE_OFFSET);
  }

  /**
   * Get the call site identifier.
   */
  public static int getSite(ObjectReference object) {
    return object.toAddress().loadInt(SITE_OFFSET);
  }

  /**
   * Get the number of data words in the object.
   */
  public static int getDataCount(ObjectReference object) {
    return object.toAddress().loadInt(DATACOUNT_OFFSET);
  }

  /**
   * Set the number of data words in the object.
   */
  private static void setDataCount(ObjectReference object, int count) {
    assert count < MAX_DATA_FIELDS && count >= 0 : "Too many data fields, "+count;
    object.toAddress().store(count, DATACOUNT_OFFSET);
  }


  /**
   * Set the number of references in the object.
   */
  private static void setRefCount(ObjectReference object, int count) {
    assert count < MAX_REF_FIELDS && count >= 0 : "Too many reference fields, "+count;
    object.toAddress().store(count, REFCOUNT_OFFSET);
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
   * Return the next object id to be allocated.
   */
  public static int nextObjectId() {
    return nextObjectId;
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
  @Override
  public ObjectReference copy(ObjectReference from, int allocator) {
    int oldBytes = getSize(from);
    int newBytes = getCopiedSize(from);
    int align = getAlignWhenCopied(from);
    CollectorContext c = Collector.current().getContext();
    allocator = c.copyCheckAllocator(from, newBytes, align, allocator);
    Address toRegion = c.allocCopy(from, newBytes, align, getAlignOffsetWhenCopied(from), allocator);
    ObjectReference to = toRegion.toObjectReference();
    Trace.trace(Item.COLLECT,"Copying object %s from %s to %s", objectIdString(from),
        addressAndSpaceString(from),addressAndSpaceString(to));

    Address fromRegion = from.toAddress();
    for(int i=0; i < oldBytes; i += SimulatedMemory.BYTES_IN_INT) {
      toRegion.plus(i).store(fromRegion.plus(i).loadInt());
    }

    int status = toRegion.loadInt(STATUS_OFFSET);
    if ((status & HASHED_AND_MOVED) == HASHED) {
      toRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
      toRegion.store(getHashCode(from), Offset.fromIntZeroExtend(oldBytes));
    }

    c.postCopy(to, null, newBytes, allocator);
    if (isWatched(from)) {
      System.err.printf("WATCH: Object %d copied from %s to %s%n",getId(from),
          addressAndSpaceString(from),addressAndSpaceString(to));
      dumpObject(to);
    }

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
   * @param toRegion The start of the region that was reserved for this object
   * @return Address The address past the end of the copied object
   */
  @Override
  public Address copyTo(ObjectReference from, ObjectReference to, Address toRegion) {
    Trace.trace(Item.COLLECT,"Copying object %s from %s/%s to %s/%s", objectIdString(from),
        from,Space.getSpaceForObject(from).getName(),
        to,Space.getSpaceForObject(to).getName());
    if (Trace.isEnabled(Item.COLLECT)) {
      dumpObjectHeader(from);
    }
    int bytes = getSize(from);
    Address fromRegion = from.toAddress();
    for(int i=0; i < bytes; i += SimulatedMemory.BYTES_IN_WORD) {
      toRegion.plus(i).store(fromRegion.plus(i).loadInt());
    }

    int status = toRegion.loadInt(STATUS_OFFSET);
    if ((status & HASHED_AND_MOVED) == HASHED) {
      toRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
      toRegion.store(getHashCode(from), Offset.fromIntZeroExtend(bytes));
      bytes += SimulatedMemory.BYTES_IN_WORD;
    }

    if (Trace.isEnabled(Item.COLLECT)) {
      dumpObjectHeader(to);
    }
    if (isWatched(from)) {
      System.err.printf("WATCH: Object %d copied from %s to %s%n",objectIdString(from),
          addressAndSpaceString(from),addressAndSpaceString(to));
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
  @Override
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
  @Override
  public ObjectReference getObjectFromStartAddress(Address start) {
    if ((start.loadInt() & ALIGNMENT_VALUE) != 0) {
      start = start.plus(SimulatedMemory.BYTES_IN_WORD);
    }
    return start.toObjectReference();
  }

  /**
   * Gets a pointer to the address just past the end of the object.
   *
   * @param object The object.
   */
  @Override
  public Address getObjectEndAddress(ObjectReference object) {
    return object.toAddress().plus(getSize(object));
  }

  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  @Override
  public byte[] getTypeDescriptor(ObjectReference ref) {
    return getString(ref).getBytes();
  }

  /**
   * Is the passed object an array?
   *
   * @param object address of the object
   */
  @Override
  public boolean isArray(ObjectReference object) {
    Assert.notImplemented();
    return false;
  }

  /**
   * Is the passed object a primitive array?
   *
   * @param object address of the object
   */
  @Override
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
  @Override
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
  @Override
  public boolean attemptAvailableBits(ObjectReference object, Word oldVal, Word newVal) {
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      Trace.printf(Item.AVBYTE,"%s.status:%s->%s", object,oldVal,newVal);
    }
    return object.toAddress().attempt(oldVal, newVal, STATUS_OFFSET);
  }

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  @Override
  public Word prepareAvailableBits(ObjectReference object) {
    return object.toAddress().prepareWord(STATUS_OFFSET);
  }

  /**
   * Sets the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the byte
   */
  @Override
  public void writeAvailableByte(ObjectReference object, byte val) {
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      byte old = object.toAddress().loadByte(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcbyte:%d->%d", object,old,val);
    }
    object.toAddress().store(val, STATUS_OFFSET);
  }

  /**
   * Read the byte available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the byte
   */
  @Override
  public byte readAvailableByte(ObjectReference object) {
    return object.toAddress().loadByte(STATUS_OFFSET);
  }

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @param val the new value of the bits
   */
  @Override
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      byte old = object.toAddress().loadByte(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcbyte:%d->%d", object,old,val.and(Word.fromIntZeroExtend(0xFF)).toInt());
    }
    object.toAddress().store(val, STATUS_OFFSET);
  }

  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param object the address of the object
   * @return the value of the bits
   */
  @Override
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
  @Override
  public Offset GC_HEADER_OFFSET() {
    return GC_OFFSET;
  }

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  @Override
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
  @Override
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
  @Override
  public boolean isAcyclic(ObjectReference typeRef) {
    return false;
  }

  /** @return The offset from array reference to element zero */
  @Override
  protected Offset getArrayBaseOffset() {
    return REFS_OFFSET;
  }

  /*
   * String representations of objects and logging/dump methods
   */

  /**
   * Dump debugging information for an object.
   *
   * @param object The object whose information is to be dumped
   */
  @Override
  public void dumpObject(ObjectReference object) {
    System.err.println("===================================");
    System.err.println(getString(object));
    System.err.println("===================================");
    SimulatedMemory.dumpMemory(object.toAddress(), 0, getSize(object));
    System.err.println("===================================");
  }

  /**
   * Format the object for dumping, and trim to a max width.
   */
  public static String formatObject(int width, ObjectReference object) {
    String base = getString(object);
    return base.substring(base.length() - width);
  }

  /**
   * Print an object's header
   */
  public static void dumpObjectHeader(ObjectReference object) {
    int gcWord = object.toAddress().loadInt(GC_OFFSET);
    int statusWord = object.toAddress().loadInt(STATUS_OFFSET);
    System.err.printf("Object %s[%d@%s]<%x,%x>%n",object,getId(object),Mutator.getSiteName(object),gcWord,statusWord);
  }

  /**
   * Dump (logical) information for an object.
   * @param width Output width
   * @param object The object whose information is to be dumped
   */
  public static void dumpLogicalObject(int width, ObjectReference object, Stack<ObjectReference> workStack) {
    int refCount = getRefs(object);
    int dataCount = getDataCount(object);
    boolean hashed = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED) == HASHED;
    System.err.printf("  Object %s <%d %d %1s> [", ObjectModel.formatObject(width, object), refCount, dataCount, (hashed ? "H" : ""));
    if (refCount > 0) {
      for(int i=0; i < refCount; i++) {
        ObjectReference ref = getRefSlot(object, i).loadObjectReference();
        System.err.print(" ");
        System.err.print(ObjectModel.formatObject(width, ref));
        if (!ref.isNull()) {
          workStack.push(ref);
        }
      }
    }
    System.err.println(" ]");
  }

  /**
   * String description of an object.
   *
   * @param ref address of the object
   * @return "Object[size b nR/mD]
   */
  public static String getString(ObjectReference ref) {
    int refs = getRefs(ref);
    int data = getDataCount(ref);
    return addressAndSpaceString(ref)+objectIdString(ref)+refs + "R" + data + "D";
  }

  /**
   * Brief string description of an object
   * @param ref The object
   * @return "[id@line:col]"
   */
  private static String objectIdString(ObjectReference ref) {
    return String.format("[%d@%s]", getId(ref),Mutator.getSiteName(ref));
  }

  /**
   * Address and space name (eg 0x45678900/ms)
   * @param ref The object
   * @return "address/space"
   */
  public static String addressAndSpaceString(ObjectReference ref) {
    return String.format("%s/%s",ref, Space.getSpaceForObject(ref).getName());
  }

  /**
   * Address and space name (eg 0x45678900/ms)
   * @param ref The object
   * @return "address/space"
   */
  public static String addressAndSpaceString(Address addr) {
    return addressAndSpaceString(addr.toObjectReference());
  }
}
