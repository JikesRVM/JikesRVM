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
package org.mmtk.harness.vm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collection;

import org.mmtk.harness.Mutator;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.sanity.Sanity;
import org.mmtk.harness.scheduler.Scheduler;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;
import org.vmmagic.unboxed.harness.ArchitecturalWord;
import org.vmmagic.unboxed.harness.Clock;
import org.vmmagic.unboxed.harness.MemoryConstants;
import org.vmmagic.unboxed.harness.SimulatedMemory;

/**
 * MMTk Harness implementation of MMTk object model
 * <pre>
 *    GC header                              (determined by MMTk)
 *    Object id (age in allocations)         (int)
 *    Allocation site                        (int)
 *    The size of the data section in words. (int)
 *    The number of reference words.         (int)
 *    Status Word (includes GC)              (WORD)
 *    References
 *    Data
 * </pre>
 */
@Uninterruptible
public final class ObjectModel extends org.mmtk.vm.ObjectModel {

  private static final boolean IS_32_BIT = ArchitecturalWord.getModel().bitsInWord() == 32;

  /** The total header size (including any requested GC words) */
  public static final int HEADER_WORDS = (IS_32_BIT ? 5 : 3) + ActivePlan.constraints.gcHeaderWords();
  /** The number of bytes in the header */
  private static final int HEADER_SIZE = HEADER_WORDS << MemoryConstants.LOG_BYTES_IN_WORD;
  /** The number of bytes requested for GC in the header */
  private static final int GC_HEADER_BYTES = ActivePlan.constraints.gcHeaderWords() << MemoryConstants.LOG_BYTES_IN_WORD;

  /** The offset of the first GC header word */
  private static final Offset GC_OFFSET        = Offset.zero();
  /** The offset of the object ID */
  private static final Offset ID_OFFSET        = GC_OFFSET.plus(GC_HEADER_BYTES);
  /** The offset of the allocation site */
  private static final Offset SITE_OFFSET      = ID_OFFSET.plus(MemoryConstants.BYTES_IN_INT);
  /** The offset of the UInt32 storing the number of data fields */
  private static final Offset DATACOUNT_OFFSET = SITE_OFFSET.plus(MemoryConstants.BYTES_IN_INT);
  /** The offset of the UInt32 storing the number of reference fields */
  private static final Offset REFCOUNT_OFFSET  = DATACOUNT_OFFSET.plus(MemoryConstants.BYTES_IN_INT);
  /** The offset of the status word */
  private static final Offset STATUS_OFFSET    = REFCOUNT_OFFSET.plus(MemoryConstants.BYTES_IN_INT);
  /** The offset of the first reference field. */
  public  static final Offset REFS_OFFSET      = STATUS_OFFSET.plus(MemoryConstants.BYTES_IN_WORD);

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
  private static final int HASHED           = 0x1 << (3 * MemoryConstants.BITS_IN_BYTE);
  /** Has this object been moved since it was hashed? */
  private static final int HASHED_AND_MOVED = 0x3 << (3 * MemoryConstants.BITS_IN_BYTE);
  /** Is this object 8 byte aligned */
  private static final int DOUBLE_ALIGN     = 0x1 << (2 * MemoryConstants.BITS_IN_BYTE);

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

  /**
   * @return the last object ID allocated - for error reporting: NOT THREAD SAFE!!!
   */
  public static int lastObjectId() {
    return nextObjectId;
  }

  /**
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

  /**
   * Add the specified object (numbered in allocation order) to the watch set.
   * @param id Object ID
   */
  public static void watchObject(int id) {
    watchSet.add(id);
  }

  static {
    //printObjectLayout(System.out);     // Sometimes helps debug the object header layout
    assert REFS_OFFSET.EQ(Offset.fromIntSignExtend(HEADER_SIZE));
  }

  /**
   * Allocate an object and return the ObjectReference.
   *
   * @param context The MMTk MutatorContext to use.
   * @param refCount The number of reference fields.
   * @param dataCount The number of data fields.
   * @param doubleAlign Align the object at an 8 byte boundary?
   * @param site The allocation site
   * @return The new ObjectReference.
   */
  public static ObjectReference allocateObject(MutatorContext context, int refCount, int dataCount, boolean doubleAlign, int site) {
    int bytes = (HEADER_WORDS + refCount + dataCount) << MemoryConstants.LOG_BYTES_IN_WORD;
    int align = ArchitecturalWord.getModel().bitsInWord() == 64 ?
        MemoryConstants.BYTES_IN_WORD :
        (doubleAlign ? 2 : 1) * MemoryConstants.BYTES_IN_INT;
    int allocator = context.checkAllocator(bytes, align, refCount == 0 ? Plan.ALLOC_NON_REFERENCE : Plan.ALLOC_DEFAULT);

//    if (allocator == Plan.ALLOC_LOS) {
//      System.out.printf("Allocating %d bytes in LOS%n",bytes);
//    }

    // Allocate the raw memory
    Address region = context.alloc(bytes, align, 0, allocator, 0);

    // Create an object reference.
    ObjectReference ref = region.toObjectReference();

    Clock.stop();
    if (SimulatedMemory.isWatched(region,bytes)) {
      Trace.printf("%4d  alloc %s [%s-%s]%n", Thread.currentThread().getId(),
          region.toObjectReference(), region, region.plus(bytes));
    }
    Clock.start();
    if (doubleAlign) region.store(DOUBLE_ALIGN, STATUS_OFFSET);
    setId(ref, allocateObjectId());
    setSite(ref, site);
    setRefCount(ref, refCount);
    setDataCount(ref, dataCount);
    Clock.stop();
    Sanity.getObjectTable().alloc(region, bytes);

    if (isWatched(ref)) {
      System.err.printf("WATCH: Object %s created%n",objectIdString(ref));
    }
    Clock.start();

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

  public static boolean hasValidId(ObjectReference object) {
    return getId(object) > 0 && getId(object) < nextObjectId;
  }

  /**
   * Set the call site identifier.
   */
  private static void setSite(ObjectReference object, int site) {
    object.toAddress().store(site, SITE_OFFSET);
  }

  /**
   * Get the call site identifier.
   * @param object The object
   * @return The call site
   */
  public static int getSite(ObjectReference object) {
    Clock.assertStopped();
    return object.toAddress().loadInt(SITE_OFFSET);
  }

  /**
   * Get the number of data words in the object.
   * @param object The object
   * @return The data count
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
   * @param object The object
   * @return The object size in bytes
   */
  public static int getSize(ObjectReference object) {
    int refs = getRefs(object);
    int data = getDataCount(object);
    boolean includesHash = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED_AND_MOVED) == HASHED_AND_MOVED;

    return getSize(refs, data) + (includesHash ? MemoryConstants.BYTES_IN_WORD : 0);
  }

  /**
   * Get the size this object will require when copied.
   * @param object The object
   * @return The object size in bytes after copying
   */
  public static int getCopiedSize(ObjectReference object) {
    int refs = getRefs(object);
    int data = getDataCount(object);
    boolean needsHash = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED) == HASHED;

    return getSize(refs, data) + (needsHash ? MemoryConstants.BYTES_IN_WORD : 0);
  }

  /**
   * Return the address of a reference field in an object.
   *
   * @param object The object with the references.
   * @param index The reference index.
   * @return The address of the specified reference field
   */
  public static Address getRefSlot(ObjectReference object, int index) {
    return object.toAddress().plus(REFS_OFFSET).plus(index << MemoryConstants.LOG_BYTES_IN_WORD);
  }

  /**
   * Return the address of the specified data slot.
   *
   * @param object The object with the data slot.
   * @param index The data slot index.
   * @return The address of the specified data field
   */
  public static Address getDataSlot(ObjectReference object, int index) {
    return getRefSlot(object, index + getRefs(object));
  }

  /**
   * Calculate the size of an object.
   * @param refs Number of reference fields
   * @param data Number of data fields
   * @return Size in bytes of the object
   */
  public static int getSize(int refs, int data) {
    return (HEADER_WORDS + refs + data) << MemoryConstants.LOG_BYTES_IN_WORD;
  }


  /**
   * Return the next object id to be allocated.  For debugging/formatting
   * purposes - not thread safe.
   * @return The next object ID
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
    Sanity.assertValid(ref);
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
      return addr.loadInt(Offset.fromIntZeroExtend(getSize(ref) - MemoryConstants.BYTES_IN_WORD));
    }
    return addr.toInt() >>> MemoryConstants.LOG_BYTES_IN_WORD;
  }

  @Override
  public ObjectReference copy(ObjectReference from, int allocator) {
    int oldBytes = getSize(from);
    int newBytes = getCopiedSize(from);
    int align = getAlignWhenCopied(from);
    CollectorContext c = Scheduler.currentCollector();
    allocator = c.copyCheckAllocator(from, newBytes, align, allocator);
    Address toRegion = c.allocCopy(from, newBytes, align, getAlignOffsetWhenCopied(from), allocator);
    ObjectReference to = toRegion.toObjectReference();
    Clock.stop();
    if (isWatched(from) || Trace.isEnabled(Item.COLLECT)) {
      Trace.printf(Item.COLLECT,"Copying object %s from %s to %s%n", objectIdString(from),
          getString(from), getString(to));
    }
    Sanity.assertValid(from);
    Sanity.getObjectTable().copy(from, to);
    Clock.start();

    Address fromRegion = from.toAddress();
    for(int i=0; i < oldBytes; i += MemoryConstants.BYTES_IN_INT) {
      toRegion.plus(i).store(fromRegion.plus(i).loadInt());
    }

    int status = toRegion.loadInt(STATUS_OFFSET);
    if ((status & HASHED_AND_MOVED) == HASHED) {
      toRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
      toRegion.store(getHashCode(from), Offset.fromIntZeroExtend(oldBytes));
    }

    c.postCopy(to, null, newBytes, allocator);
    Clock.stop();
    if (isWatched(from)) {
      System.err.printf("WATCH: Object %d copied from %s to %s%n",getId(from),
          addressAndSpaceString(from),addressAndSpaceString(to));
      dumpObjectHeader("after copy: ", to);
    }
    Clock.start();

    return to;
  }

  @Override
  public Address copyTo(ObjectReference from, ObjectReference to, Address toRegion) {
    Clock.stop();
    boolean traceThisObject = Trace.isEnabled(Item.COLLECT) || isWatched(from);
    if (traceThisObject) {
      Trace.printf(Item.COLLECT,"Copying object %s explicitly from %s/%s to %s/%s, region %s%n", objectIdString(from),
        from,Space.getSpaceForObject(from).getName(),
        to,Space.getSpaceForObject(to).getName(),
        toRegion);
      dumpObjectHeader("Before copy: ", from);
    }
    Sanity.assertValid(from);
    Sanity.getObjectTable().copy(from, to);
    Clock.start();

    boolean doCopy = !from.equals(to);
    int bytes = getSize(from);

    if (doCopy) {
      Address srcRegion = from.toAddress();
      Address dstRegion = to.toAddress();
      for(int i=0; i < bytes; i += MemoryConstants.BYTES_IN_INT) {
        int before = srcRegion.plus(i).loadInt();
        dstRegion.plus(i).store(before);
        int after = dstRegion.plus(i).loadInt();
        Clock.stop();
        if (traceThisObject) {
          System.err.printf("copy %s/%08x -> %s/%08x%n",
              srcRegion.plus(i),before,dstRegion.plus(i),after);
        }
        Clock.start();
      }

      int status = dstRegion.loadInt(STATUS_OFFSET);
      if ((status & HASHED_AND_MOVED) == HASHED) {
        dstRegion.store(status | HASHED_AND_MOVED, STATUS_OFFSET);
        dstRegion.store(getHashCode(from), Offset.fromIntZeroExtend(bytes));
        bytes += MemoryConstants.BYTES_IN_WORD;
      }
      Allocator.fillAlignmentGap(toRegion, dstRegion);
    } else {
      if (traceThisObject) {
        Clock.stop();
        Trace.printf(Item.COLLECT,"%s: no copy required%n", getString(from));
        Clock.start();
      }
    }
    Address objectEndAddress = getObjectEndAddress(to);
    Clock.stop();
    if (traceThisObject) {
      dumpObjectHeader("After copy: ", to);
    }
    Sanity.assertValid(to);
    Clock.start();
    return objectEndAddress;
  }

  @Override
  public ObjectReference getReferenceWhenCopiedTo(ObjectReference from, Address to) {
    return to.toObjectReference();
  }


  @Override
  public int getSizeWhenCopied(ObjectReference object) {
    return getCopiedSize(object);
  }

  @Override
  public int getAlignWhenCopied(ObjectReference object) {
    boolean doubleAlign = (object.toAddress().loadInt(STATUS_OFFSET) & DOUBLE_ALIGN) == DOUBLE_ALIGN;
    return (doubleAlign ? 2 : 1) * MemoryConstants.BYTES_IN_WORD;
  }

  @Override
  public int getAlignOffsetWhenCopied(ObjectReference object) {
    return 0;
  }

  @Override
  public int getCurrentSize(ObjectReference object) {
    return getSize(object);
  }

  @Override
  public ObjectReference getNextObject(ObjectReference object) {
    Address nextAddress = object.toAddress().plus(getSize(object));
    if (nextAddress.loadInt() == ALIGNMENT_VALUE) {
      nextAddress = nextAddress.plus(MemoryConstants.BYTES_IN_WORD);
    }
    if (nextAddress.loadWord().isZero()) {
      return ObjectReference.nullReference();
    }
    return nextAddress.toObjectReference();
  }

  @Override
  public ObjectReference getObjectFromStartAddress(Address start) {
    if ((start.loadInt() & ALIGNMENT_VALUE) != 0) {
      start = start.plus(MemoryConstants.BYTES_IN_WORD);
    }
    return start.toObjectReference();
  }

  public static Address getStartAddressFromObject(ObjectReference object) {
    return object.toAddress();
  }

  @Override
  public Address getObjectEndAddress(ObjectReference object) {
    return object.toAddress().plus(getSize(object));
  }

  @Override
  public byte[] getTypeDescriptor(ObjectReference ref) {
    return getString(ref).getBytes();
  }

  @Override
  public boolean isArray(ObjectReference object) {
    Assert.notImplemented();
    return false;
  }

  @Override
  public boolean isPrimitiveArray(ObjectReference object) {
    Assert.notImplemented();
    return false;
  }

  @Override
  public int getArrayLength(ObjectReference object) {
    Assert.notImplemented();
    return 0;
  }

  @Override
  public boolean attemptAvailableBits(ObjectReference object, Word oldVal, Word newVal) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      Word actual = object.toAddress().loadWord(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.status:%s=%s ? ->%s%n", getString(object),actual,oldVal,newVal);
    }
    Clock.start();
    return object.toAddress().attempt(oldVal, newVal, STATUS_OFFSET);
  }

  @Override
  public Word prepareAvailableBits(ObjectReference object) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      Word old = object.toAddress().loadWord(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcword=%s (prepare)%n", getString(object),old);
    }
    Clock.start();
    return object.toAddress().prepareWord(STATUS_OFFSET);
  }

  @Override
  public void writeAvailableByte(ObjectReference object, byte val) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      byte old = object.toAddress().loadByte(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcbyte:%d->%d%n", getString(object),old,val);
    }
    Clock.start();
    object.toAddress().store(val, STATUS_OFFSET);
  }

  @Override
  public byte readAvailableByte(ObjectReference object) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      byte old = object.toAddress().loadByte(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcbyte=%d%n", getString(object),old);
    }
    Clock.start();
    return object.toAddress().loadByte(STATUS_OFFSET);
  }

  @Override
  public void writeAvailableBitsWord(ObjectReference object, Word val) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      Word old = object.toAddress().loadWord(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcword:%s->%s%n", getString(object),old,val);
    }
    Clock.start();
    object.toAddress().store(val, STATUS_OFFSET);
  }

  @Override
  public Word readAvailableBitsWord(ObjectReference object) {
    Clock.stop();
    if (Trace.isEnabled(Item.AVBYTE) || isWatched(object)) {
      Word old = object.toAddress().loadWord(STATUS_OFFSET);
      Trace.printf(Item.AVBYTE,"%s.gcword=%s%n", getString(object),old);
    }
    Clock.start();
    return object.toAddress().loadWord(STATUS_OFFSET);
  }

  @Override
  public Offset GC_HEADER_OFFSET() {
    return GC_OFFSET;
  }

  @Override
  public Address objectStartRef(ObjectReference object) {
    return object.toAddress();
  }

  @Override
  public Address refToAddress(ObjectReference object) {
    return object.toAddress();
  }

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

  @Override
  public void dumpObject(ObjectReference object) {
    Clock.stop();
    System.err.println("===================================");
    System.err.println(getString(object));
    System.err.println("===================================");
    SimulatedMemory.dumpMemory(object.toAddress(), 0, getSize(object));
    System.err.println("===================================");
    Clock.start();
  }

  /**
   * Format the object for dumping, and trim to a max width.
   * @param width Max output width
   * @param object The object to dump
   * @return The formatted object
   */
  public static String formatObject(int width, ObjectReference object) {
    String base = getString(object);
    return base.substring(Math.max(base.length() - width,0));
  }

  /**
   * Print an object's header
   * @param prefix TODO
   * @param object The object reference
   */
  public static void dumpObjectHeader(String prefix, ObjectReference object) {
    int gcWord = object.toAddress().loadInt(GC_OFFSET);
    int statusWord = object.toAddress().loadInt(STATUS_OFFSET);
    System.err.printf("%sObject %s[%d@%s]<%x,%x>%n",prefix,object,getId(object),Mutator.getSiteName(object),gcWord,statusWord);
  }

  /**
   * Dump (logical) information for an object.  Returns the non-null pointers
   * in the object.
   * @param width Output width
   * @param object The object whose information is to be dumped
   * @return The non-null references in the object
   */
  public static Collection<ObjectReference> dumpLogicalObject(int width, ObjectReference object) {
    int refCount = getRefs(object);
    int dataCount = getDataCount(object);
    boolean hashed = (object.toAddress().loadInt(STATUS_OFFSET) & HASHED) == HASHED;
    List<ObjectReference> pointers = new ArrayList<ObjectReference>(refCount);
    System.err.printf("  Object %s <%d %d %1s> [", ObjectModel.formatObject(width, object), refCount, dataCount, (hashed ? "H" : ""));
    if (refCount > 0) {
      for(int i=0; i < refCount; i++) {
        ObjectReference ref = ActivePlan.plan.loadObjectReference(getRefSlot(object, i));
        System.err.print(" ");
        System.err.print(ObjectModel.formatObject(width, ref));
        if (!ref.isNull()) {
          pointers.add(ref);
        }
      }
    }
    System.err.println(" ]");
    return pointers;
  }

  /**
   * String description of an object.
   *
   * @param ref address of the object
   * @return "Object[size b nR/mD]
   */
  public static String getString(ObjectReference ref) {
    if (ref.isNull()) return "<null>";
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
   * @param addr The object
   * @return "address/space"
   */
  public static String addressAndSpaceString(Address addr) {
    return String.format("%s/%s",addr, Space.getSpaceForAddress(addr).getName());
  }
}
