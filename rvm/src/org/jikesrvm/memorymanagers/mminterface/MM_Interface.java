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
package org.jikesrvm.memorymanagers.mminterface;

import java.lang.ref.PhantomReference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;
import org.jikesrvm.VM;
import org.jikesrvm.VM_HeapLayoutConstants;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.mm.mmtk.Assert;
import org.jikesrvm.mm.mmtk.Collection;
import org.jikesrvm.mm.mmtk.Lock;
import org.jikesrvm.mm.mmtk.Options;
import org.jikesrvm.mm.mmtk.ReferenceGlue;
import org.jikesrvm.mm.mmtk.SynchronizedCounter;
import org.jikesrvm.objectmodel.BootImageInterface;
import org.jikesrvm.objectmodel.VM_JavaHeader;
import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_BootRecord;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import org.mmtk.utility.Barrier;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.Memory;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.utility.scan.MMType;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * The interface that the JMTk memory manager presents to the Jikes
 * research virtual machine.
 */
@Uninterruptible
public final class MM_Interface implements VM_HeapLayoutConstants, Constants {

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * <code>true</code> if checking of allocated memory to ensure it is
   * zeroed is desired.
   */
  private static final boolean CHECK_MEMORY_IS_ZEROED = false;

  /** Used by mmtypes for arrays */
  private static final int[] zeroLengthIntArray = new int[0];

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Suppress default constructor to enforce noninstantiability.
   */
  private MM_Interface() {} // This constructor will never be invoked.

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
    VM_CollectorThread.init();
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
  public static void boot(VM_BootRecord theBootRecord) {
    Mmapper.markAsMapped(BOOT_IMAGE_DATA_START, BOOT_IMAGE_DATA_SIZE);
    Mmapper.markAsMapped(BOOT_IMAGE_CODE_START, BOOT_IMAGE_CODE_SIZE);
    HeapGrowthManager.boot(theBootRecord.initialHeapSize, theBootRecord.maximumHeapSize);
    DebugUtil.boot(theBootRecord);
    Selected.Plan.get().boot();
    SynchronizedCounter.boot();
    Monitor.boot();
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  @Interruptible
  public static void postBoot() {
    Selected.Plan.get().postBoot();
    if (VM.BuildWithGCSpy) {
      // start the GCSpy interpreter server
      MM_Interface.startGCspyServer();
    }

  }

  /**
   * Notify the MM that the host VM is now fully booted.
   */
  @Interruptible
  public static void fullyBootedVM() {
    Selected.Plan.get().fullyBooted();
    Lock.fullyBooted();
    Barrier.fullyBooted();
  }

  /**
   *  Process GC parameters.
   */
  @Interruptible
  public static void processCommandLineArg(String arg) {
    if (!Options.process(arg)) {
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
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   * @param locationMetadata an int that encodes the source location being modified
   */
  @Inline
  public static void putfieldWriteBarrier(Object ref, Offset offset, Object value, int locationMetadata) {
    ObjectReference src = ObjectReference.fromObject(ref);
    Selected.Mutator.get().writeBarrier(src,
                                        src.toAddress().plus(offset),
                                        ObjectReference.fromObject(value),
                                        offset,
                                        locationMetadata,
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
                                        offset,
                                        0, // do not have location metadata
                                        PUTFIELD_WRITE_BARRIER);
  }


  /**
   * Write barrier for putstatic operations.
   *
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   */
  @Inline
  public static void putstaticWriteBarrier(Offset offset, Object value) {
    // putstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
//     Address jtoc = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
//     VM_Interface.getPlan().writeBarrier(jtoc,
//                                         jtoc.plus(offset),
//                                         VM_Magic.objectAsAddress(value),
//                                         PUTSTATIC_WRITE_BARRIER);
  }

  /**
   * Take appropriate write barrier actions for the creation of a new
   * reference by an aastore bytecode.
   *
   * @param ref the array containing the source of the new reference.
   * @param index the index into the array were the new referece
   * resides.  The index is the "natural" index into the array, for
   * example a[index].
   * @param value the object that is the target of the new reference.
   */
  @Inline
  public static void arrayStoreWriteBarrier(Object ref, int index, Object value) {
    ObjectReference array = ObjectReference.fromObject(ref);
    Offset offset = Offset.fromIntZeroExtend(index << LOG_BYTES_IN_ADDRESS);
    Selected.Mutator.get().writeBarrier(array,
                                        array.toAddress().plus(offset),
                                        ObjectReference.fromObject(value),
                                        offset,
                                        0,
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

  /**
   * Checks that if a garbage collection is in progress then the given
   * object is not movable.  If it is movable error messages are
   * logged and the system exits.
   *
   * @param object the object to check
   */
  public static void modifyCheck(Object object) {
    /* Make sure that during GC, we don't update on a possibly moving object.
       Such updates are dangerous because they can be lost.
     */
    if (Plan.gcInProgressProper()) {
      ObjectReference ref = ObjectReference.fromObject(object);
      if (Space.isMovable(ref)) {
        VM.sysWriteln("GC modifying a potentially moving object via Java (i.e. not magic)");
        VM.sysWriteln("  obj = ", ref);
        VM_Type t = VM_Magic.getObjectType(object);
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
  @Uninterruptible
  public static int getCollectionCount() {
    return VM_CollectorThread.collectionCount;
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
  @Uninterruptible
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
  @Uninterruptible
  public static boolean validRef(ObjectReference ref) {
    return DebugUtil.validRef(ref);
  }

  /**
   * Checks if an address refers to an in-use area of memory.
   *
   * @param address the address to be checked
   * @return <code>true</code> if the address refers to an in use area
   */
  @Inline
  @Uninterruptible
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
  @Uninterruptible
  public static boolean objectInVM(ObjectReference object) {
    return Space.isMappedObject(object);
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
   * type.  This form is deprecated.  Without the VM_Method argument,
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
  public static int pickAllocator(VM_Type type) {
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
  public static int pickAllocator(VM_Type type, VM_Method method) {

    if (method != null) {
      // We should strive to be allocation-free here.
      VM_Class cls = method.getDeclaringClass();
      byte[] clsBA = cls.getDescriptor().toByteArray();
      if (Selected.Constraints.get().withGCspy()) {
        if (isPrefix("Lorg/mmtk/vm/gcspy/", clsBA) || isPrefix("[Lorg/mmtk/vm/gcspy/", clsBA)) {
          return Plan.ALLOC_GCSPY;
        }
      }
      if (isPrefix("Lorg/mmtk/", clsBA) ||
          isPrefix("Lorg/jikesrvm/mm/", clsBA) ||
          isPrefix("Lorg/jikesrvm/memorymanagers/mminterface/VM_GCMapIteratorGroup", clsBA)) {
        return Plan.ALLOC_IMMORTAL;
      }
    }
    MMType t = (MMType) type.getMMType();
    return t.getAllocator();
  }

  /**
   * Determine the default allocator to be used for a given type.
   *
   * @param type The type in question
   * @return The allocator to use for allocating instances of type
   * <code>type</code>.
   */
  @Interruptible
  private static int pickAllocatorForType(VM_Type type) {
    int allocator = Plan.ALLOC_DEFAULT;
    if (type.isArrayType() && type.asArray().getElementType().isPrimitiveType()) {
      allocator = Plan.ALLOC_NON_REFERENCE;
    }
    byte[] typeBA = type.getDescriptor().toByteArray();
    if (Selected.Constraints.get().withGCspy()) {
      if (isPrefix("Lorg/mmtk/vm/gcspy/", typeBA) || isPrefix("[Lorg/mmtk/vm/gcspy/", typeBA)) {
        allocator = Plan.ALLOC_GCSPY;
      }
    }
    if (isPrefix("Lorg/mmtk/", typeBA) ||
        isPrefix("Lorg/jikesrvm/mm/", typeBA) ||
        isPrefix("Lorg/jikesrvm/memorymanagers/", typeBA) ||
        isPrefix("Lorg/jikesrvm/scheduler/VM_Processor;", typeBA) ||
        isPrefix("Lorg/jikesrvm/jni/VM_JNIEnvironment;", typeBA)) {
      allocator = Plan.ALLOC_IMMORTAL;
    }
    if (Selected.Constraints.get().needsImmortalTypeInfo() &&
        (isPrefix("Lorg/jikesrvm/classloader/VM_Class", typeBA) ||
         isPrefix("Lorg/jikesrvm/classloader/VM_Array", typeBA))) {
      allocator = Plan.ALLOC_IMMORTAL;
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
  @Uninterruptible
  public static Object allocateScalar(int size, Object[] tib, int allocator, int align, int offset, int site) {
    Selected.Mutator mutator = Selected.Mutator.get();
    allocator = mutator.checkAllocator(VM_Memory.alignUp(size, MIN_ALIGNMENT), align, allocator);
    Address region = allocateSpace(mutator, size, align, offset, allocator, site);
    Object result = VM_ObjectModel.initializeScalar(region, tib, size);
    mutator.postAlloc(ObjectReference.fromObject(result), ObjectReference.fromObject(tib), size, allocator);
    return result;
  }

  /**
   * Allocate an array object.
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
  @Uninterruptible
  public static Object allocateArray(int numElements, int logElementSize, int headerSize, Object[] tib, int allocator,
                                     int align, int offset, int site) {
    Selected.Mutator mutator = Selected.Mutator.get();

    int elemBytes = numElements << logElementSize;
    if ((elemBytes >>> logElementSize) != numElements) {
      // asked to allocate more than Integer.MAX_VALUE bytes
      Assert.failWithOutOfMemoryErrorStatic();
    }
    int size = elemBytes + headerSize;
    allocator = mutator.checkAllocator(VM_Memory.alignUp(size, MIN_ALIGNMENT), align, allocator);
    Address region = allocateSpace(mutator, size, align, offset, allocator, site);
    Object result = VM_ObjectModel.initializeArray(region, tib, numElements, size);
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
  @Uninterruptible
  private static Address allocateSpace(Selected.Mutator mutator, int bytes, int align, int offset, int allocator,
                                       int site) {
    // MMTk requests must be in multiples of MIN_ALIGNMENT
    bytes = VM_Memory.alignUp(bytes, MIN_ALIGNMENT);

    /*
     * Now make the request
   */
    Address region;
    region = mutator.alloc(bytes, align, offset, allocator, site);
    /* TODO
       if (Stats.GATHER_MARK_CONS_STATS) Plan.cons.inc(bytes);
    */
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
  @Uninterruptible
  public static Address allocateSpace(Selected.Collector collector, int bytes, int align, int offset, int allocator,
                                      ObjectReference from) {
    // MMTk requests must be in multiples of MIN_ALIGNMENT
    bytes = VM_Memory.alignUp(bytes, MIN_ALIGNMENT);

    /*
     * Now make the request
     */
    Address region;
    region = collector.allocCopy(from, bytes, align, offset, allocator);
    /* TODO
    if (Stats.GATHER_MARK_CONS_STATS) Plan.mark.inc(bytes);
    */
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
  @Uninterruptible
  public static Offset alignAllocation(Offset initialOffset, int align, int offset) {
    Address region = VM_Memory.alignUp(initialOffset.toWord().toAddress(), MIN_ALIGNMENT);
    return Allocator.alignAllocationNoFill(region, align, offset).toWord().toOffset();
  }

  /**
   * Allocate a VM_CodeArray into a code space.
   * Currently the interface is fairly primitive;
   * just the number of instructions in the code array and a boolean
   * to indicate hot or cold code.
   * @param numInstrs number of instructions
   * @param isHot is this a request for hot code space allocation?
   * @return The  array
   */
  public static VM_CodeArray allocateCode(int numInstrs, boolean isHot) {
    VM_Array type = VM_Type.CodeArrayType;
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(type);
    int align = VM_ObjectModel.getAlignment(type);
    int offset = VM_ObjectModel.getOffsetForAlignment(type);
    int width = type.getLogElementSize();
    Object[] tib = type.getTypeInformationBlock();
    int allocator = isHot ? Plan.ALLOC_HOT_CODE : Plan.ALLOC_COLD_CODE;

    return (VM_CodeArray) allocateArray(numInstrs, width, headerSize, tib, allocator, align, offset, Plan.DEFAULT_SITE);
  }

  /**
   * Allocate a stack
   * @param bytes    The number of bytes to allocate
   * @param immortal  Is the stack immortal and non-moving?
   * @return The stack
   */
  @Inline
  @Interruptible
  public static byte[] newStack(int bytes, boolean immortal) {
    if (!VM.runningVM) {
      return new byte[bytes];
    } else {
      VM_Array stackType = VM_Array.ByteArray;
      int headerSize = VM_ObjectModel.computeArrayHeaderSize(stackType);
      int align = VM_ObjectModel.getAlignment(stackType);
      int offset = VM_ObjectModel.getOffsetForAlignment(stackType);
      int width = stackType.getLogElementSize();
      Object[] stackTib = stackType.getTypeInformationBlock();

      return (byte[]) allocateArray(bytes,
                                    width,
                                    headerSize,
                                    stackTib,
                                    (immortal ? Plan.ALLOC_IMMORTAL_STACK : Plan.ALLOC_STACK),
                                    align,
                                    offset,
                                    Plan.DEFAULT_SITE);
    }
  }

  /**
   * Allocate a reference offset array
   *
   * @param size The size of the array
   */
  @Inline
  @Interruptible
  public static int[] newReferenceOffsetArray(int size) {
    if (!VM.runningVM) {
      return new int[size];
    }

    VM_Array arrayType = VM_Array.IntArray;
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(arrayType);
    int align = VM_ObjectModel.getAlignment(arrayType);
    int offset = VM_ObjectModel.getOffsetForAlignment(arrayType);
    int width = arrayType.getLogElementSize();
    Object[] arrayTib = arrayType.getTypeInformationBlock();

    return (int[]) allocateArray(size,
                                 width,
                                 headerSize,
                                 arrayTib,
                                 (Selected.Constraints.get().needsImmortalTypeInfo() ? Plan.ALLOC_IMMORTAL : Plan.ALLOC_DEFAULT),
                                 align,
                                 offset,
                                 Plan.DEFAULT_SITE);

  }

  /**
   * Allocate a new type information block (TIB).
   *
   * @param n the number of slots in the TIB to be allocated
   * @return the new TIB
   */
  @Inline
  @Interruptible
  public static Object[] newTIB(int n) {

    if (!VM.runningVM) {
      return new Object[n];
    }

    VM_Array objectArrayType = VM_Type.JavaLangObjectArrayType;
    Object[] objectArrayTib = objectArrayType.getTypeInformationBlock();
    int align = VM_ObjectModel.getAlignment(objectArrayType);
    int offset = VM_ObjectModel.getOffsetForAlignment(objectArrayType);
    Object result =
        allocateArray(n,
                      objectArrayType.getLogElementSize(),
                      VM_ObjectModel.computeArrayHeaderSize(objectArrayType),
                      objectArrayTib,
                      Plan.ALLOC_IMMORTAL,
                      align,
                      offset,
                      Plan.DEFAULT_SITE);
    return (Object[]) result;
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */
  @Inline
  @Interruptible
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n) {
    return new VM_CompiledMethod[n];
  }

  /*
 *  Will this object move (allows us to optimize some JNI calls)
 *  */
  public static boolean objectCanMove(Object obj) {
    return Selected.Plan.get().objectCanMove(ObjectReference.fromObject(obj));
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
    Finalizer.addCandidate(ObjectReference.fromObject(object));
  }

  /**
   * Gets an object from the list of objects that are to be reclaimed
   * and need to have their <code>finalize</code> method called.
   *
   * @return the object needing to be finialized
   */
  @Interruptible
  public static Object getFinalizedObject() {
    return Finalizer.get().toObject();
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
  public static void addSoftReference(SoftReference<?> obj) {
    ReferenceGlue.addSoftCandidate(obj);
  }

  /**
   * Add a weak reference to the list of weak references.
   *
   * @param obj the weak reference to be added to the list
   */
  @Interruptible
  public static void addWeakReference(WeakReference<?> obj) {
    ReferenceGlue.addWeakCandidate(obj);
  }

  /**
   * Add a phantom reference to the list of phantom references.
   *
   * @param obj the phantom reference to be added to the list
   */
  @Interruptible
  public static void addPhantomReference(PhantomReference<?> obj) {
    ReferenceGlue.addPhantomCandidate(obj);
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

  /**
   * Increase heap size for an emergency, such as processing an out of
   * memory exception.
   *
   * @param growSize number of bytes to increase the heap size
   */
  public static void emergencyGrowHeap(int growSize) { // in bytes
    // This can be undoable if the current thread doesn't cause 'em
    // all to exit.
    // if (VM.VerifyAssertions && growSize < 0) VM._assert(false);
    HeapGrowthManager.overrideGrowHeapSize(Extent.fromIntSignExtend(growSize));
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
  public static void notifyClassResolved(VM_Type vmType) {
    MMType type;
    if (vmType.isArrayType()) {
      /* A reference array */
      if (vmType.asArray().getElementType().isReferenceType()) {
        type = MMType.createRefArray(vmType.isAcyclicReference(), pickAllocatorForType(vmType));
      } else {
        /* An array of primitive types */
        type = MMType.createPrimArray(pickAllocatorForType(vmType));
      }
    } else {
      type =
          MMType.createScalar(vmType.isAcyclicReference(),
                              pickAllocatorForType(vmType),
                              vmType.asClass().getReferenceOffsets());
    }
    vmType.setMMType(type);
  }

  /**
   * Check if object might be a TIB.
   *
   * @param obj address of object to check
   * @return <code>false</code> if the object is in the wrong
   * allocation scheme/area for a TIB, <code>true</code> otherwise
   */
  @Inline
  @Uninterruptible
  public static boolean mightBeTIB(ObjectReference obj) {
    return !obj.isNull() &&
           Space.isMappedObject(obj) &&
           Space.isImmortal(obj) &&
           Space.isMappedObject(ObjectReference.fromObject(VM_ObjectModel.getTIB(obj)));
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  @Uninterruptible
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
  public static void initializeHeader(BootImageInterface bootImage, Address ref, Object[] tib, int size,
                                      boolean isScalar) {
    //    int status = VM_JavaHeader.readAvailableBitsWord(bootImage, ref);
    Word status = Selected.Plan.get().setBootTimeGCBits(ref, ObjectReference.fromObject(tib), size, Word.zero());
    VM_JavaHeader.writeAvailableBitsWord(bootImage, ref, status);
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

