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
package org.jikesrvm.mm.mminterface;

import java.lang.ref.PhantomReference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import org.jikesrvm.ArchitectureSpecific.CodeArray;
import org.jikesrvm.VM;
import org.jikesrvm.HeapLayoutConstants;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.SpecializedMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.FinalizableProcessor;
import org.jikesrvm.mm.mmtk.ReferenceProcessor;
import org.jikesrvm.mm.mmtk.SynchronizedCounter;
import org.jikesrvm.objectmodel.BootImageInterface;
import org.jikesrvm.objectmodel.IMT;
import org.jikesrvm.objectmodel.ITable;
import org.jikesrvm.objectmodel.ITableArray;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.objectmodel.TIBLayoutConstants;
import org.jikesrvm.options.OptionSet;
import org.jikesrvm.runtime.BootRecord;
import org.jikesrvm.runtime.Magic;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Memory;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.utility.options.Options;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.UnpreemptibleNoWarn;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;
import org.vmmagic.unboxed.WordArray;

/**
 * The interface that the MMTk memory manager presents to Jikes RVM
 */
@Uninterruptible
public final class MemoryManager implements HeapLayoutConstants, Constants {

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * <code>true</code> if checking of allocated memory to ensure it is
   * zeroed is desired.
   */
  private static final boolean CHECK_MEMORY_IS_ZEROED = false;
  private static final boolean traceAllocator = false;

  /**
   * Hash the interface been booted yet?
   */
  private static boolean booted = false;

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Suppress default constructor to enforce noninstantiability.
   */
  private MemoryManager() {} // This constructor will never be invoked.

  /**
   * Initialization that occurs at <i>build</i> time.  The value of
   * statics as at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is the entry point for all build-time activity in the collector.
   */
  @Interruptible
  public static void init() {
    if (VM.VerifyAssertions) VM._assert(!Selected.Constraints.get().needsStaticReadBarrier());
    CollectorThread.init();
    org.jikesrvm.mm.mmtk.Collection.init();
  }

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   * @param theBootRecord the boot record. Contains information about
   * the heap size.
   */
  @Interruptible
  public static void boot(BootRecord theBootRecord) {
    Mmapper.markAsMapped(BOOT_IMAGE_DATA_START, BOOT_IMAGE_DATA_SIZE);
    Mmapper.markAsMapped(BOOT_IMAGE_CODE_START, BOOT_IMAGE_CODE_SIZE);
    HeapGrowthManager.boot(theBootRecord.initialHeapSize, theBootRecord.maximumHeapSize);
    DebugUtil.boot(theBootRecord);
    Selected.Plan.get().boot();
    SynchronizedCounter.boot();
    Monitor.boot();
    booted = true;
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  @Interruptible
  public static void postBoot() {
    Selected.Plan.get().postBoot();

    if (Options.noReferenceTypes.getValue()) {
      RVMType.JavaLangRefReferenceReferenceField.makeTraced();
    }

    if (VM.BuildWithGCSpy) {
      // start the GCSpy interpreter server
      MemoryManager.startGCspyServer();
    }
  }

  /**
   * Notify the MM that the host VM is now fully booted.
   */
  @Interruptible
  public static void fullyBootedVM() {
    Selected.Plan.get().fullyBooted();
  }

  /**
   *  Process GC parameters.
   */
  @Interruptible
  public static void processCommandLineArg(String arg) {
    if (!OptionSet.gc.process(arg)) {
      VM.sysWriteln("Unrecognized command line argument: \"" + arg + "\"");
      VM.sysExit(VM.EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
  }

  /***********************************************************************
   *
   * Write barriers
   */

  /**
   * Write barrier for putfield operations.
   *
   * @param ref the object which is the subject of the putfield
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void putfieldWriteBarrier(Object ref, Object value, Offset offset, int locationMetadata) {
    ObjectReference src = ObjectReference.fromObject(ref);
    Selected.Mutator.get().writeBarrier(src,
                                        src.toAddress().plus(offset),
                                        ObjectReference.fromObject(value),
                                        offset.toWord(),
                                        Word.fromIntZeroExtend(locationMetadata),
                                        PUTFIELD_WRITE_BARRIER);
  }

  /**
   * Write barrier for compare and exchange object field operations.
   *
   * @param ref the object which is the subject of the putfield
   * @param offset the offset of the field to be modified
   * @param old the old value to swap out
   * @param value the new value for the field
   */
  @Inline
  public static boolean tryCompareAndSwapWriteBarrier(Object ref, Offset offset, Object old, Object value) {
    ObjectReference src = ObjectReference.fromObject(ref);
    return Selected.Mutator.get().tryCompareAndSwapWriteBarrier(src,
                                        src.toAddress().plus(offset),
                                        ObjectReference.fromObject(old),
                                        ObjectReference.fromObject(value),
                                        offset.toWord(),
                                        Word.zero(), // do not have location metadata
                                        PUTFIELD_WRITE_BARRIER);
  }

  /**
   * Write barrier for putstatic operations.
   *
   * @param value the new value for the field
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  @Entrypoint
  public static void putstaticWriteBarrier(Object value, Offset offset, int locationMetadata) {
    ObjectReference src = ObjectReference.fromObject(Magic.getJTOC());
    Selected.Mutator.get().writeBarrier(src,
                                        src.toAddress().plus(offset),
                                        ObjectReference.fromObject(value),
                                        offset.toWord(),
                                        Word.fromIntZeroExtend(locationMetadata),
                                        PUTSTATIC_WRITE_BARRIER);
  }

  /**
   * Take appropriate write barrier actions for the creation of a new
   * reference by an aastore bytecode.
   *
   * @param ref the array containing the source of the new reference.
   * @param index the index into the array where the new reference
   * resides.  The index is the "natural" index into the array, for
   * example a[index].
   * @param value the object that is the target of the new reference.
   */
  @Inline
  @Entrypoint
  public static void arrayStoreWriteBarrier(Object ref, int index, Object value) {
    ObjectReference array = ObjectReference.fromObject(ref);
    Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS);
    Selected.Mutator.get().writeBarrier(array,
                                        array.toAddress().plus(offset),
                                        ObjectReference.fromObject(value),
                                        offset.toWord(),
                                        Word.zero(),
                                        // don't know metadata
                                        AASTORE_WRITE_BARRIER);
  }

  /**
   * Take appropriate write barrier actions when a series of
   * references are copied (i.e. in an array copy).
   *
   * @param src       The source object
   * @param srcOffset The offset of the first source address, in
   * bytes, relative to <code>src</code> (in principle, this could be
   * negative).
   * @param tgt        The target object
   * @param tgtOffset  The offset of the first target address, in bytes
   * relative to <code>tgt</code> (in principle, this could be
   * negative).
   * @param bytes The size of the region being copied, in bytes.
   * @return True if the update was performed by the barrier, false if
   * left to the caller (always false in this case).
   */
  @Inline
  public static boolean arrayCopyWriteBarrier(Object src, Offset srcOffset, Object tgt, Offset tgtOffset, int bytes) {
    return Selected.Mutator.get().writeBarrier(ObjectReference.fromObject(src),
                                               srcOffset,
                                               ObjectReference.fromObject(tgt),
                                               tgtOffset,
                                               bytes);
  }

  /***********************************************************************
  *
  * Read barriers
  */

  /**
   * Read barrier for getfield operations.
   *
   * @param ref the object which is the subject of the getfield
   * @param offset the offset of the field to be read
   * @param locationMetadata an int that encodes the source location being read
   * @return The value read from the field.
   */
  @Inline
  @Entrypoint
  public static Object getfieldReadBarrier(Object ref, Offset offset, int locationMetadata) {
    ObjectReference src = ObjectReference.fromObject(ref);
    return Selected.Mutator.get().readBarrier(src,
                                              src.toAddress().plus(offset),
                                              offset.toWord(),
                                              Word.fromIntZeroExtend(locationMetadata),
                                              GETFIELD_READ_BARRIER).toObject();
  }

  /**
   * Take appropriate read barrier actions for loading a reference
   * from an array.
   *
   * @param ref the array containing the reference.
   * @param index the index into the array were the reference resides.
   * @return the value read from the array
   */
  @Inline
  @Entrypoint
  public static Object arrayLoadReadBarrier(Object ref, int index) {
    ObjectReference array = ObjectReference.fromObject(ref);
    Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS);
    return Selected.Mutator.get().readBarrier(array,
                                              array.toAddress().plus(offset),
                                              offset.toWord(),
                                              Word.zero(), // don't know metadata
                                              AALOAD_READ_BARRIER).toObject();
  }

  /**
   * Read barrier for getstatic operations.
   *
   * @param offset the offset of the field to be modified
   * @param locationMetadata an int that encodes the source location being read
   * @return the value read from the field
   */
  @Inline
  @Entrypoint
  public static Object getstaticReadBarrier(Offset offset, int locationMetadata) {
    ObjectReference src = ObjectReference.fromObject(Magic.getJTOC());
    return Selected.Mutator.get().readBarrier(src,
                                              src.toAddress().plus(offset),
                                              offset.toWord(),
                                              Word.fromIntZeroExtend(locationMetadata),
                                              GETSTATIC_READ_BARRIER).toObject();
  }

  /**
   * A reference type is being read.
   *
   * @param obj The non-null referent about to be released to the mutator.
   * @return The object to release to the mutator.
   */
  public static Object referenceTypeReadBarrier(Object obj) {
    ObjectReference result = Selected.Mutator.get().referenceTypeReadBarrier(ObjectReference.fromObject(obj));
    return result.toObject();
  }

  /**
   * Checks that if a garbage collection is in progress then the given
   * object is not movable.  If it is movable error messages are
   * logged and the system exits.
   *
   * @param object the object to check
   */
  @Entrypoint
  public static void modifyCheck(Object object) {
    /* Make sure that during GC, we don't update on a possibly moving object.
       Such updates are dangerous because they can be lost.
     */
    if (Plan.gcInProgressProper()) {
      ObjectReference ref = ObjectReference.fromObject(object);
      if (Space.isMovable(ref)) {
        VM.sysWriteln("GC modifying a potentially moving object via Java (i.e. not magic)");
        VM.sysWriteln("  obj = ", ref);
        RVMType t = Magic.getObjectType(object);
        VM.sysWrite(" type = ");
        VM.sysWriteln(t.getDescriptor());
        VM.sysFail("GC modifying a potentially moving object via Java (i.e. not magic)");
      }
    }
  }

  /***********************************************************************
   *
   * Statistics
   */

  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public static int getCollectionCount() {
    return CollectorThread.collectionCount;
  }

  /***********************************************************************
   *
   * Application interface to memory manager
   */

  /**
   * Returns the amount of free memory.
   *
   * @return The amount of free memory.
   */
  public static Extent freeMemory() {
    return Plan.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static Extent totalMemory() {
    return Plan.totalMemory();
  }

  /**
   * Returns the maximum amount of memory VM will attempt to use.
   *
   * @return The maximum amount of memory VM will attempt to use.
   */
  public static Extent maxMemory() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /**
   * External call to force a garbage collection.
   */
  @Interruptible
  public static void gc() {
    if (!org.mmtk.utility.options.Options.ignoreSystemGC.getValue()) {
      Collection.triggerCollectionStatic(Collection.EXTERNAL_GC_TRIGGER);
    }
  }

  /****************************************************************************
   *
   * Check references, log information about references
   */

  /**
   * Logs information about a reference to the error output.
   *
   * @param ref the address to log information about
   */
  public static void dumpRef(ObjectReference ref) {
    DebugUtil.dumpRef(ref);
  }

  /**
   * Checks if a reference is valid.
   *
   * @param ref the address to be checked
   * @return <code>true</code> if the reference is valid
   */
  @Inline
  public static boolean validRef(ObjectReference ref) {
    if (booted) {
      return DebugUtil.validRef(ref);
    } else {
      return true;
    }
  }

  /**
   * Checks if an address refers to an in-use area of memory.
   *
   * @param address the address to be checked
   * @return <code>true</code> if the address refers to an in use area
   */
  @Inline
  public static boolean addressInVM(Address address) {
    return Space.isMappedAddress(address);
  }

  /**
   * Checks if a reference refers to an object in an in-use area of
   * memory.
   *
   * <p>References may be addresses just outside the memory region
   * allocated to the object.
   *
   * @param object the reference to be checked
   * @return <code>true</code> if the object refered to is in an
   * in-use area
   */
  @Inline
  public static boolean objectInVM(ObjectReference object) {
    return Space.isMappedObject(object);
  }

  /**
   * Return true if address is in a space which may contain stacks
   *
   * @param address The address to be checked
   * @return true if the address is within a space which may contain stacks
   */
  public static boolean mightBeFP(Address address) {
    // In general we don't know which spaces may hold allocated stacks.
    // If we want to be more specific than the space being mapped we
    // will need to add a check in Plan that can be overriden.
    return Space.isMappedAddress(address);
  }
  /***********************************************************************
   *
   * Allocation
   */

  /**
   * Return an allocation site upon request.  The request may be made
   * in the context of compilation.
   *
   * @param compileTime True if this request is being made in the
   * context of a compilation.
   * @return an allocation site
   */
  public static int getAllocationSite(boolean compileTime) {
    return Plan.getAllocationSite(compileTime);
  }

  /**
   * Returns the appropriate allocation scheme/area for the given
   * type.  This form is deprecated.  Without the RVMMethod argument,
   * it is possible that the wrong allocator is chosen which may
   * affect correctness. The prototypical example is that JMTk
   * meta-data must generally be in immortal or at least non-moving
   * space.
   *
   *
   * @param type the type of the object to be allocated
   * @return the identifier of the appropriate allocator
   */
  @Interruptible
  public static int pickAllocator(RVMType type) {
    return pickAllocator(type, null);
  }

  /**
   * Is string <code>a</code> a prefix of string
   * <code>b</code>. String <code>b</code> is encoded as an ASCII byte
   * array.
   *
   * @param a prefix string
   * @param b string which may contain prefix, encoded as an ASCII
   * byte array.
   * @return <code>true</code> if <code>a</code> is a prefix of
   * <code>b</code>
   */
  @Interruptible
  private static boolean isPrefix(String a, byte[] b) {
    int aLen = a.length();
    if (aLen > b.length) {
      return false;
    }
    for (int i = 0; i < aLen; i++) {
      if (a.charAt(i) != ((char) b[i])) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the appropriate allocation scheme/area for the given type
   * and given method requesting the allocation.
   *
   * @param type the type of the object to be allocated
   * @param method the method requesting the allocation
   * @return the identifier of the appropriate allocator
   */
  @Interruptible
  public static int pickAllocator(RVMType type, RVMMethod method) {
    if (traceAllocator) {
      VM.sysWrite("allocator for ");
      VM.sysWrite(type.getDescriptor());
      VM.sysWrite(": ");
    }
    if (method != null) {
      // We should strive to be allocation-free here.
      RVMClass cls = method.getDeclaringClass();
      byte[] clsBA = cls.getDescriptor().toByteArray();
      if (Selected.Constraints.get().withGCspy()) {
        if (isPrefix("Lorg/mmtk/vm/gcspy/", clsBA) || isPrefix("[Lorg/mmtk/vm/gcspy/", clsBA)) {
          if (traceAllocator) {
            VM.sysWriteln("GCSPY");
          }
          return Plan.ALLOC_GCSPY;
        }
      }
      if (isPrefix("Lorg/jikesrvm/mm/mmtk/ReferenceProcessor", clsBA)) {
        if (traceAllocator) {
          VM.sysWriteln("DEFAULT");
        }
        return Plan.ALLOC_DEFAULT;
      }
      if (isPrefix("Lorg/mmtk/", clsBA) || isPrefix("Lorg/jikesrvm/mm/", clsBA)) {
        if (traceAllocator) {
          VM.sysWriteln("NONMOVING");
        }
        return Plan.ALLOC_NON_MOVING;
      }
      if (method.isNonMovingAllocation()) {
        return Plan.ALLOC_NON_MOVING;
      }
    }
    if (traceAllocator) {
      VM.sysWriteln(type.getMMAllocator());
    }
    return type.getMMAllocator();
  }

  /**
   * Determine the default allocator to be used for a given type.
   *
   * @param type The type in question
   * @return The allocator to use for allocating instances of type
   * <code>type</code>.
   */
  @Interruptible
  private static int pickAllocatorForType(RVMType type) {
    int allocator = Plan.ALLOC_DEFAULT;
    if (type.isArrayType()) {
      RVMType elementType = type.asArray().getElementType();
      if (elementType.isPrimitiveType() || elementType.isUnboxedType()){
        allocator = Plan.ALLOC_NON_REFERENCE;
      }
    }
    if(type.isNonMoving()) {
      allocator = Plan.ALLOC_NON_MOVING;
    }
    byte[] typeBA = type.getDescriptor().toByteArray();
    if (Selected.Constraints.get().withGCspy()) {
      if (isPrefix("Lorg/mmtk/vm/gcspy/", typeBA) || isPrefix("[Lorg/mmtk/vm/gcspy/", typeBA)) {
        allocator = Plan.ALLOC_GCSPY;
      }
    }
    if (isPrefix("Lorg/mmtk/", typeBA) ||
        isPrefix("Lorg/jikesrvm/mm/", typeBA) ||
        isPrefix("Lorg/jikesrvm/jni/JNIEnvironment;", typeBA)) {
      allocator = Plan.ALLOC_NON_MOVING;
    }
    return allocator;
  }

  /***********************************************************************
   * These methods allocate memory.  Specialized versions are available for
   * particular object types.
   ***********************************************************************
   */

  /**
   * Allocate a scalar object.
   *
   * @param size Size in bytes of the object, including any headers
   * that need space.
   * @param tib  Type of the object (pointer to TIB).
   * @param allocator Specify which allocation scheme/area JMTk should
   * allocate the memory from.
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   * @param site allocation site.
   * @return the initialized Object
   */
  @Inline
  public static Object allocateScalar(int size, TIB tib, int allocator, int align, int offset, int site) {
    Selected.Mutator mutator = Selected.Mutator.get();
    allocator = mutator.checkAllocator(org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT), align, allocator);
    Address region = allocateSpace(mutator, size, align, offset, allocator, site);
    Object result = ObjectModel.initializeScalar(region, tib, size);
    mutator.postAlloc(ObjectReference.fromObject(result), ObjectReference.fromObject(tib), size, allocator);
    return result;
  }

  /**
   * Allocate an array object. This is the interruptible component, including throwing
   * an OutOfMemoryError for arrays that are too large.
   *
   * @param numElements number of array elements
   * @param logElementSize size in bytes of an array element, log base 2.
   * @param headerSize size in bytes of array header
   * @param tib type information block for array object
   * @param allocator int that encodes which allocator should be used
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   * @param site allocation site.
   * @return array object with header installed and all elements set
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */
  @Inline
  @Unpreemptible
  public static Object allocateArray(int numElements, int logElementSize, int headerSize, TIB tib, int allocator,
                                     int align, int offset, int site) {
    int elemBytes = numElements << logElementSize;
    if ((elemBytes >>> logElementSize) != numElements) {
      /* asked to allocate more than Integer.MAX_VALUE bytes */
      throwLargeArrayOutOfMemoryError();
    }
    int size = elemBytes + headerSize;
    return allocateArrayInternal(numElements, size, tib, allocator, align, offset, site);
  }


  /**
   * Throw an out of memory error due to an array allocation request that is
   * larger than the maximum allowed value. This is in a separate method
   * so it can be forced out of line.
   */
  @NoInline
  @UnpreemptibleNoWarn
  private static void throwLargeArrayOutOfMemoryError() {
    throw new OutOfMemoryError();
  }

  /**
   * Allocate an array object.
   *
   * @param numElements The number of element bytes
   * @param size size in bytes of array header
   * @param tib type information block for array object
   * @param allocator int that encodes which allocator should be used
   * @param align the alignment requested; must be a power of 2.
   * @param offset the offset at which the alignment is desired.
   * @param site allocation site.
   * @return array object with header installed and all elements set
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */
  @Inline
  private static Object allocateArrayInternal(int numElements, int size, TIB tib, int allocator,
                                              int align, int offset, int site) {
    Selected.Mutator mutator = Selected.Mutator.get();
    allocator = mutator.checkAllocator(org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT), align, allocator);
    Address region = allocateSpace(mutator, size, align, offset, allocator, site);
    Object result = ObjectModel.initializeArray(region, tib, numElements, size);
    mutator.postAlloc(ObjectReference.fromObject(result), ObjectReference.fromObject(tib), size, allocator);
    return result;
  }

  /**
   * Allocate space for runtime allocation of an object
   *
   * @param mutator The mutator instance to be used for this allocation
   * @param bytes The size of the allocation in bytes
   * @param align The alignment requested; must be a power of 2.
   * @param offset The offset at which the alignment is desired.
   * @param allocator The MMTk allocator to be used (if allocating)
   * @param site Allocation site.
   * @return The first byte of a suitably sized and aligned region of memory.
   */
  @Inline
  private static Address allocateSpace(Selected.Mutator mutator, int bytes, int align, int offset, int allocator,
                                       int site) {
    /* MMTk requests must be in multiples of MIN_ALIGNMENT */
    bytes = org.jikesrvm.runtime.Memory.alignUp(bytes, MIN_ALIGNMENT);

    /* Now make the request */
    Address region;
    region = mutator.alloc(bytes, align, offset, allocator, site);

    /* TODO: if (Stats.GATHER_MARK_CONS_STATS) Plan.cons.inc(bytes); */
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, bytes);

    return region;
  }

  /**
   * Allocate space for GC-time copying of an object
   *
   * @param collector The collector instance to be used for this allocation
   * @param bytes The size of the allocation in bytes
   * @param align The alignment requested; must be a power of 2.
   * @param offset The offset at which the alignment is desired.
   * @param from The source object from which this is to be copied
   * @return The first byte of a suitably sized and aligned region of memory.
   */
  @Inline
  public static Address allocateSpace(Selected.Collector collector, int bytes, int align, int offset, int allocator,
                                      ObjectReference from) {
    /* MMTk requests must be in multiples of MIN_ALIGNMENT */
    bytes = org.jikesrvm.runtime.Memory.alignUp(bytes, MIN_ALIGNMENT);

    /* Now make the request */
    Address region;
    region = collector.allocCopy(from, bytes, align, offset, allocator);

    /* TODO: if (Stats.GATHER_MARK_CONS_STATS) Plan.mark.inc(bytes); */
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, bytes);

    return region;
  }

  /**
   * Align an allocation using some modulo arithmetic to guarantee the
   * following property:<br>
   * <code>(region + offset) % alignment == 0</code>
   *
   * @param initialOffset The initial (unaligned) start value of the
   * allocated region of memory.
   * @param align The alignment requested, must be a power of two
   * @param offset The offset at which the alignment is desired
   * @return <code>initialOffset</code> plus some delta (possibly 0) such
   * that the return value is aligned according to the above
   * constraints.
   */
  @Inline
  public static Offset alignAllocation(Offset initialOffset, int align, int offset) {
    Address region = org.jikesrvm.runtime.Memory.alignUp(initialOffset.toWord().toAddress(), MIN_ALIGNMENT);
    return Allocator.alignAllocationNoFill(region, align, offset).toWord().toOffset();
  }

  /**
   * Allocate a CodeArray into a code space.
   * Currently the interface is fairly primitive;
   * just the number of instructions in the code array and a boolean
   * to indicate hot or cold code.
   * @param numInstrs number of instructions
   * @param isHot is this a request for hot code space allocation?
   * @return The  array
   */
  @Interruptible
  public static CodeArray allocateCode(int numInstrs, boolean isHot) {
    RVMArray type = RVMType.CodeArrayType;
    int headerSize = ObjectModel.computeArrayHeaderSize(type);
    int align = ObjectModel.getAlignment(type);
    int offset = ObjectModel.getOffsetForAlignment(type, false);
    int width = type.getLogElementSize();
    TIB tib = type.getTypeInformationBlock();
    int allocator = isHot ? Plan.ALLOC_HOT_CODE : Plan.ALLOC_COLD_CODE;

    return (CodeArray) allocateArray(numInstrs, width, headerSize, tib, allocator, align, offset, Plan.DEFAULT_SITE);
  }

  /**
   * Allocate a stack
   * @param bytes    The number of bytes to allocate
   * @return The stack
   */
  @Inline
  @Unpreemptible
  public static byte[] newStack(int bytes) {
    if (!VM.runningVM) {
      return new byte[bytes];
    } else {
      RVMArray stackType = RVMArray.ByteArray;
      int headerSize = ObjectModel.computeArrayHeaderSize(stackType);
      int align = ObjectModel.getAlignment(stackType);
      int offset = ObjectModel.getOffsetForAlignment(stackType, false);
      int width = stackType.getLogElementSize();
      TIB stackTib = stackType.getTypeInformationBlock();

      return (byte[]) allocateArray(bytes,
                                    width,
                                    headerSize,
                                    stackTib,
                                    Plan.ALLOC_STACK,
                                    align,
                                    offset,
                                    Plan.DEFAULT_SITE);
    }
  }

  /**
   * Allocate a non moving word array
   *
   * @param size The size of the array
   */
  @Inline
  @Interruptible
  public static WordArray newNonMovingWordArray(int size) {
    if (!VM.runningVM) {
      return WordArray.create(size);
    }

    RVMArray arrayType = RVMType.WordArrayType;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    return (WordArray) allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 Plan.ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

  }

  /**
   * Allocate a non moving double array
   *
   * @param size The size of the array
   */
  @Inline
  @Interruptible
  public static double[] newNonMovingDoubleArray(int size) {
    if (!VM.runningVM) {
      return new double[size];
    }

    RVMArray arrayType = RVMArray.DoubleArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    return (double[]) allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 Plan.ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

  }

  /**
   * Allocate a non moving int array
   *
   * @param size The size of the array
   */
  @Inline
  @Interruptible
  public static int[] newNonMovingIntArray(int size) {
    if (!VM.runningVM) {
      return new int[size];
    }

    RVMArray arrayType = RVMArray.IntArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    return (int[]) allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 Plan.ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

  }

  /**
   * Allocate a non moving int array
   *
   * @param size The size of the array
   */
  @Inline
  @Interruptible
  public static short[] newNonMovingShortArray(int size) {
    if (!VM.runningVM) {
      return new short[size];
    }

    RVMArray arrayType = RVMArray.ShortArray;
    int headerSize = ObjectModel.computeArrayHeaderSize(arrayType);
    int align = ObjectModel.getAlignment(arrayType);
    int offset = ObjectModel.getOffsetForAlignment(arrayType, false);
    int width = arrayType.getLogElementSize();
    TIB arrayTib = arrayType.getTypeInformationBlock();

    return (short[]) allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 Plan.ALLOC_NON_MOVING,
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

  }

  /**
   * Allocate a new type information block (TIB).
   *
   * @param numVirtualMethods the number of virtual method slots in the TIB
   * @return the new TIB
   */
  @Inline
  @Interruptible
  public static TIB newTIB(int numVirtualMethods) {
    int size = TIB.computeSize(numVirtualMethods);

    if (!VM.runningVM) {
      return TIB.allocate(size);
    }

    return (TIB)newRuntimeTable(size, RVMType.TIBType);
  }

  /**
   * Allocate a new interface method table (IMT).
   *
   * @return the new IMT
   */
  @Inline
  @Interruptible
  public static IMT newIMT() {
    if (!VM.runningVM) {
      return IMT.allocate();
    }

    return (IMT)newRuntimeTable(TIBLayoutConstants.IMT_METHOD_SLOTS, RVMType.IMTType);
  }

  /**
   * Allocate a new ITable
   *
   * @param size the number of slots in the ITable
   * @return the new ITable
   */
  @Inline
  @Interruptible
  public static ITable newITable(int size) {
    if (!VM.runningVM) {
      return ITable.allocate(size);
    }

    return (ITable)newRuntimeTable(size, RVMType.ITableType);
  }

  /**
   * Allocate a new ITableArray
   *
   * @param size the number of slots in the ITableArray
   * @return the new ITableArray
   */
  @Inline
  @Interruptible
  public static ITableArray newITableArray(int size) {
    if (!VM.runningVM) {
      return ITableArray.allocate(size);
    }

    return (ITableArray)newRuntimeTable(size, RVMType.ITableArrayType);
  }

  /**
   * Allocate a new runtime table (at runtime)
   *
   * @param size The size of the table.
   * @return the newly allocated table
   */
  @Inline
  @Interruptible
  public static Object newRuntimeTable(int size, RVMType type) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);

    TIB realTib = type.getTypeInformationBlock();
    RVMArray fakeType = RVMType.WordArrayType;
    TIB fakeTib = fakeType.getTypeInformationBlock();
    int headerSize = ObjectModel.computeArrayHeaderSize(fakeType);
    int align = ObjectModel.getAlignment(fakeType);
    int offset = ObjectModel.getOffsetForAlignment(fakeType, false);
    int width = fakeType.getLogElementSize();

    /* Allocate a word array */
    Object array = allocateArray(size,
                                 width,
                                 headerSize,
                                 fakeTib,
                                 type.getMMAllocator(),
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

    /* Now we replace the TIB */
    ObjectModel.setTIB(array, realTib);
    return array;
  }

  /**
   *  Will this object move (allows us to optimize some JNI calls)
   */
  @Pure
  public static boolean willNeverMove(Object obj) {
    return Selected.Plan.get().willNeverMove(ObjectReference.fromObject(obj));
  }

  /**
   *  Will this object move (allows us to optimize some JNI calls)
   */
  @Pure
  public static boolean isImmortal(Object obj) {
    return Space.isImmortal(ObjectReference.fromObject(obj));
  }

  /***********************************************************************
   *
   * Finalizers
   */

  /**
   * Adds an object to the list of objects to have their
   * <code>finalize</code> method called when they are reclaimed.
   *
   * @param object the object to be added to the finalizer's list
   */
  @Interruptible
  public static void addFinalizer(Object object) {
    FinalizableProcessor.addCandidate(object);
  }

  /**
   * Gets an object from the list of objects that are to be reclaimed
   * and need to have their <code>finalize</code> method called.
   *
   * @return the object needing to be finialized
   */
  @Unpreemptible("Non-preemptible but may yield if finalizable table is being grown")
  public static Object getFinalizedObject() {
    return FinalizableProcessor.getForFinalize();
  }

  /***********************************************************************
   *
   * References
   */

  /**
   * Add a soft reference to the list of soft references.
   *
   * @param obj the soft reference to be added to the list
   */
  @Interruptible
  public static void addSoftReference(SoftReference<?> obj, Object referent) {
    ReferenceProcessor.addSoftCandidate(obj,ObjectReference.fromObject(referent));
  }

  /**
   * Add a weak reference to the list of weak references.
   *
   * @param obj the weak reference to be added to the list
   */
  @Interruptible
  public static void addWeakReference(WeakReference<?> obj, Object referent) {
    ReferenceProcessor.addWeakCandidate(obj,ObjectReference.fromObject(referent));
  }

  /**
   * Add a phantom reference to the list of phantom references.
   *
   * @param obj the phantom reference to be added to the list
   */
  @Interruptible
  public static void addPhantomReference(PhantomReference<?> obj, Object referent) {
    ReferenceProcessor.addPhantomCandidate(obj,ObjectReference.fromObject(referent));
  }

  /***********************************************************************
   *
   * Tracing
   */

  /***********************************************************************
   *
   * Heap size and heap growth
   */

  /**
   * Return the max heap size in bytes (as set by -Xmx).
   *
   * @return The max heap size in bytes (as set by -Xmx).
   */
  public static Extent getMaxHeapSize() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /***********************************************************************
   *
   * Miscellaneous
   */

  /**
   * A new type has been resolved by the VM.  Create a new MM type to
   * reflect the VM type, and associate the MM type with the VM type.
   *
   * @param vmType The newly resolved type
   */
  @Interruptible
  public static void notifyClassResolved(RVMType vmType) {
    vmType.setMMAllocator(pickAllocatorForType(vmType));
  }

  /**
   * Check if object might be a TIB.
   *
   * @param obj address of object to check
   * @return <code>false</code> if the object is in the wrong
   * allocation scheme/area for a TIB, <code>true</code> otherwise
   */
  @Inline
  public static boolean mightBeTIB(ObjectReference obj) {
    return !obj.isNull() &&
           Space.isMappedObject(obj) &&
           Space.isImmortal(obj) &&
           Space.isMappedObject(ObjectReference.fromObject(ObjectModel.getTIB(obj)));
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static boolean gcInProgress() {
    return Plan.gcInProgress();
  }

  /**
   * Start the GCspy server
   */
  @Interruptible
  public static void startGCspyServer() {
    GCspy.startGCspyServer();
  }

  /**
   * Flush the mutator context.
   */
  public static void flushMutatorContext() {
    Selected.Mutator.get().flush();
  }

  /**
   * Return the number of specialized methods.
   */
  public static int numSpecializedMethods() {
    return SpecializedScanMethod.ENABLED ? Selected.Constraints.get().numSpecializedScans() : 0;
  }

  /**
   * Initialize a specified specialized method.
   *
   * @param id the specializedMethod
   */
  @Interruptible
  public static SpecializedMethod createSpecializedMethod(int id) {
    if (VM.VerifyAssertions) {
      VM._assert(SpecializedScanMethod.ENABLED);
      VM._assert(id < Selected.Constraints.get().numSpecializedScans());
    }

    /* What does the plan want us to specialize this to? */
    Class<?> traceClass = Selected.Plan.get().getSpecializedScanClass(id);

    /* Create the specialized method */
    return new SpecializedScanMethod(id, TypeReference.findOrCreate(traceClass));
  }

  /***********************************************************************
   *
   * Header initialization
   */

  /**
   * Override the boot-time initialization method here, so that
   * the core JMTk code doesn't need to know about the
   * BootImageInterface type.
   */
  @Interruptible
  public static void initializeHeader(BootImageInterface bootImage, Address ref, TIB tib, int size,
                                      boolean isScalar) {
    //    int status = JavaHeader.readAvailableBitsWord(bootImage, ref);
    Word status = Selected.Plan.get().setBootTimeGCBits(ref, ObjectReference.fromObject(tib), size, Word.zero());
    JavaHeader.writeAvailableBitsWord(bootImage, ref, status);
  }

  /**
   * Install a reference into the boot image.
   */
  @Interruptible
  public static Word bootTimeWriteBarrier(Word value) {
    return Selected.Plan.get().bootTimeWriteBarrier(value);
  }

  /***********************************************************************
   *
   * Deprecated and/or broken.  The following need to be expunged.
   */

  /**
   * Returns the maximum number of heaps that can be managed.
   *
   * @return the maximum number of heaps
   */
  public static int getMaxHeaps() {
    /*
     *  The boot record has a table of address ranges of the heaps,
     *  the maximum number of heaps is used to size the table.
     */
    return Space.MAX_SPACES;
  }

  /**
   * Allocate a contiguous int array
   * @param n The number of ints
   * @return The contiguous int array
   */
  @Inline
  @Interruptible
  public static int[] newContiguousIntArray(int n) {
    return new int[n];
  }

}

