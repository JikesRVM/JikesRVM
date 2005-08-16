/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import java.util.Date;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;

import org.mmtk.plan.Plan;
import org.mmtk.plan.PlanLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.Barrier;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.gcspy.GCspy;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Memory;
import org.mmtk.utility.scan.MMType;
import org.mmtk.utility.statistics.Stats;
import org.mmtk.utility.options.*;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Lock;
import org.mmtk.vm.Options;
import org.mmtk.vm.ReferenceGlue;
import org.mmtk.vm.Scanning;
import org.mmtk.vm.SynchronizedCounter;
import org.mmtk.vm.Collection;

import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Method;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_CodeArray;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_HeapLayoutConstants;
import com.ibm.JikesRVM.VM_DynamicLibrary;
import com.ibm.JikesRVM.VM_JavaHeader;

import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;

/**
 * The interface that the JMTk memory manager presents to the Jikes
 * research virtual machine.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */  
public class MM_Interface implements VM_HeapLayoutConstants, Constants, Uninterruptible {

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * <code>true</code> if a write barrier is required.  Specifically,
   * if the memory manger requires that the virtual machine calls
   * putfieldWriteBarrier, arrayStoreWriteBarrier or modifyCheck when
   * the corresponding writes are done.
   * AJG: Is this correct?
   */
  public static final boolean NEEDS_WRITE_BARRIER 
    = SelectedPlanConstraints.get().needsWriteBarrier();

  /**
   * <code>true</code> if a write barrier for putstatic operations is
   * required.  Specifically, if the memory manger requires that the
   * virtual machine calls putstaticWriteBarrier, when a putstatic
   * operation is needed.
   * AJG: Is this correct?
   */
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER
    = SelectedPlanConstraints.get().needsStaticWriteBarrier();

  /* AJG: Not used. But will be. needed */
  /**
   * <code>true</code> if a write barrier for TIB store operations is
   * requried.  Note: This field is not used.
   */
  public static final boolean NEEDS_TIB_STORE_WRITE_BARRIER = false;

  /**
   * <code>true</code> if the memory manager moves objects. For
   * example, a copying collector will move objects.
   */
  public static final boolean MOVES_OBJECTS 
    = SelectedPlanConstraints.get().movesObjects();

  /**
   * <code>true</code> if the memory manager moves type information
   * blocks (TIBs).
   */
  public static final boolean MOVES_TIBS = false;

  /**
   * <code>true</code> if checking of allocated memory to ensure it is
   * zeroed is desired.
   */
  private static final boolean CHECK_MEMORY_IS_ZEROED = false;

  /**
   * <code>true</code> if the memory manager will generate a garbage
   * collection trace of the run.
   */
  public static final boolean GENERATE_GC_TRACE 
    = SelectedPlanConstraints.get().generateGCTrace(); 

  /** Used by mmtypes for arrays */
  private static final int [] zeroLengthIntArray = new int [0];
  
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
  public static final void init() throws InterruptiblePragma {
    VM_CollectorThread.init();
    Collection.init();
    Scanning.init();
  }

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   * @param theBootRecord the boot record. Contains information about
   * the heap size.
   */
  public static final void boot(VM_BootRecord theBootRecord)
    throws InterruptiblePragma {
    int pageSize = VM_Memory.getPagesize();  // Cannot be determined at init-time
    LazyMmapper.boot(BOOT_IMAGE_START, BOOT_IMAGE_SIZE);
    HeapGrowthManager.boot(theBootRecord.initialHeapSize, theBootRecord.maximumHeapSize);
    DebugUtil.boot(theBootRecord);
    ActivePlan.global().boot();
    SynchronizedCounter.boot();
    Monitor.boot();
  }

  /**
   * Sets up the fields of a <code>VM_Processor</code> object to
   * accommodate allocation and garbage collection running on that processor.
   * This may involve creating a remset array or a buffer for GC tracing.
   * 
   * This method is called from the constructor of VM_Processor. For the
   * PRIMORDIAL processor, which is allocated while the bootimage is being
   * built, this method is called a second time, from VM.boot, when the 
   * VM is starting.
   *
   * @param proc   The <code>VM_Processor</code> object.
   */
  public static final void setupProcessor(VM_Processor proc)
    throws InterruptiblePragma {
    // if (proc.mmPlan == null) proc.mmPlan = new Plan();
    // if (VM.VerifyAssertions) VM._assert(proc.mmPlan != null);
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  public static void postBoot() throws InterruptiblePragma {
    ActivePlan.global().postBoot();
  }

  /**
   * Notify the MM that the host VM is now fully booted.
   */
  public static void fullyBootedVM() throws InterruptiblePragma {
    ActivePlan.global().fullyBooted();
    Lock.fullyBooted();
    Barrier.fullyBooted();
  }

  /** 
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg)
    throws InterruptiblePragma {
      if (!Options.process(arg)) {
        VM.sysWriteln("Unrecognized command line argument: \"" + arg +"\"");
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
  public static void putfieldWriteBarrier(Object ref, Offset offset, Object value,
                                          int locationMetadata)
    throws InlinePragma {
    ObjectReference src = ObjectReference.fromObject(ref);
    SelectedPlanLocal.get().writeBarrier(src,
                                    src.toAddress().add(offset),
                                    ObjectReference.fromObject(value),
                                        offset,
                                        locationMetadata,
                                        PUTFIELD_WRITE_BARRIER);
  }
  
  /**
   * Write barrier for putstatic operations.
   *
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   */
  public static void putstaticWriteBarrier(Offset offset, Object value)
    throws InlinePragma { 
    // putstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
//     Address jtoc = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
//     VM_Interface.getPlan().writeBarrier(jtoc,
//                                         jtoc.add(offset),
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
  public static void arrayStoreWriteBarrier(Object ref, int index,
                                            Object value)
    throws InlinePragma {
    ObjectReference array = ObjectReference.fromObject(ref);
    Offset offset = Offset.fromIntZeroExtend(index<<LOG_BYTES_IN_ADDRESS);
    SelectedPlanLocal.get().writeBarrier(array,
                                    array.toAddress().add(offset),
                                    ObjectReference.fromObject(value),
                                        offset,
                                        0, // don't know metadata
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
  public static boolean arrayCopyWriteBarrier(Object src, Offset srcOffset,
                                              Object tgt, Offset tgtOffset,
                                              int bytes) 
    throws InlinePragma {
    return SelectedPlanLocal.get().writeBarrier(ObjectReference.fromObject(src),
                                               srcOffset,
                                           ObjectReference.fromObject(tgt),
                                               tgtOffset, bytes);
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
            VM.sysWrite(" type = "); VM.sysWriteln(t.getDescriptor());
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
  public static final int getCollectionCount()
    throws UninterruptiblePragma {
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
  public static final Extent freeMemory() {
    return Plan.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static final Extent totalMemory() {
    return Plan.totalMemory();
  }

  /**
   * Returns the maximum amount of memory VM will attempt to use.
   *
   * @return The maximum amount of memory VM will attempt to use.
   */
  public static final Extent maxMemory() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /**
   * External call to force a garbage collection.
   */
  public static final void gc() throws InterruptiblePragma {
    if (!org.mmtk.utility.options.Options.ignoreSystemGC.getValue())
      Collection.triggerCollection(Collection.EXTERNAL_GC_TRIGGER);
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
  public static void dumpRef(ObjectReference ref) throws UninterruptiblePragma {
    DebugUtil.dumpRef(ref);
  }

  /**
   * Checks if a reference is valid.
   *
   * @param ref the address to be checked
   * @return <code>true</code> if the reference is valid
   */
  public static boolean validRef(ObjectReference ref)
    throws UninterruptiblePragma, InlinePragma {
    return DebugUtil.validRef(ref);
  }

  /**
   * Checks if an address refers to an in-use area of memory.
   * 
   * @param address the address to be checked
   * @return <code>true</code> if the address refers to an in use area
   */
  public static boolean addressInVM(Address address)
    throws UninterruptiblePragma, InlinePragma {
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
  public static boolean objectInVM(ObjectReference object)
    throws UninterruptiblePragma, InlinePragma {
    return Space.isMappedObject(object);
  }

  /***********************************************************************
   *
   * Allocation
   */

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
  public static int pickAllocator(VM_Type type) throws InterruptiblePragma {
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
  private static boolean isPrefix(String a, byte [] b)
    throws InterruptiblePragma {
    int aLen = a.length();
    if (aLen > b.length)
      return false;
    for (int i = 0; i<aLen; i++) {
      if (a.charAt(i) != ((char) b[i]))
        return false;
    }
    return true;
  }

  public static int getDefaultAllocator() { return Plan.ALLOC_DEFAULT; }

  /**
   * Returns the appropriate allocation scheme/area for the given type
   * and given method requesting the allocation.
   * 
   * @param type the type of the object to be allocated
   * @param method the method requesting the allocation
   * @return the identifier of the appropriate allocator
   */
  public static int pickAllocator(VM_Type type, VM_Method method)
    throws InterruptiblePragma {

    if (method != null) {
     // We should strive to be allocation-free here.
      VM_Class cls = method.getDeclaringClass();
      byte[] clsBA = cls.getDescriptor().toByteArray();
      byte[] methBA = method.getDescriptor().toByteArray();
      if (SelectedPlanConstraints.get().withGCspy()) {
        if (isPrefix("Lorg/mmtk/vm/gcspy/",  clsBA) ||
            isPrefix("[Lorg/mmtk/vm/gcspy/", clsBA)) {
          return Plan.ALLOC_GCSPY;
        }
      }
      if (isPrefix("Lorg/mmtk/", clsBA) 
          || isPrefix("Lcom/ibm/JikesRVM/memoryManagers/mmInterface/VM_GCMapIteratorGroup", clsBA)) {
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
  private static int pickAllocatorForType(VM_Type type)
    throws InterruptiblePragma {
    int allocator = Plan.ALLOC_DEFAULT;
    byte[] typeBA = type.getDescriptor().toByteArray();
    if (SelectedPlanConstraints.get().withGCspy()) {
      if (isPrefix("Lorg/mmtk/vm/gcspy/",  typeBA) ||
               isPrefix("[Lorg/mmtk/vm/gcspy/", typeBA)) 
        allocator = Plan.ALLOC_GCSPY;
    }
    if (isPrefix("Lorg/mmtk/", typeBA) ||
        isPrefix("Lcom/ibm/JikesRVM/memoryManagers/", typeBA) ||
        isPrefix("Lcom/ibm/JikesRVM/VM_Processor;", typeBA) ||
        isPrefix("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", typeBA))
      allocator = Plan.ALLOC_IMMORTAL;
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
   * @return the initialized Object
   */
  public static Object allocateScalar(int size, Object [] tib, int allocator,
                                      int align, int offset)
    throws UninterruptiblePragma, InlinePragma {
    SelectedPlanLocal plan = SelectedPlanLocal.get();
    allocator = plan.checkAllocator(size, align, allocator);
    Address region = allocateSpace(plan, size, align, offset, allocator);
    Object result = VM_ObjectModel.initializeScalar(region, tib, size);
    plan.postAlloc(ObjectReference.fromObject(result), 
                   ObjectReference.fromObject(tib), size, allocator);
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
   * @return array object with header installed and all elements set 
   *         to zero/null
   * See also: bytecode 0xbc ("newarray") and 0xbd ("anewarray")
   */ 
  public static Object allocateArray(int numElements, int logElementSize, 
                                     int headerSize, Object [] tib,
                                     int allocator,
                                     int align, int offset) 
    throws UninterruptiblePragma, InlinePragma {
    SelectedPlanLocal plan = SelectedPlanLocal.get();

    int elemBytes = numElements << logElementSize;
    if ((elemBytes >>> logElementSize) != numElements) {
      // asked to allocate more than Integer.MAX_VALUE bytes
      Assert.failWithOutOfMemoryError();
    }
    int size = elemBytes + headerSize;
    allocator = plan.checkAllocator(size, align, allocator);
    Address region = allocateSpace(plan, size, align, offset, allocator);
    Object result = VM_ObjectModel.initializeArray(region, tib, numElements,
                                                   size);
    plan.postAlloc(ObjectReference.fromObject(result), 
                   ObjectReference.fromObject(tib), size, allocator);
    return result;
  }

  /** 
   * Allocate space for a new object
   *
   * @param plan The plan instance to be used for this allocation
   * @param bytes The size of the allocation in bytes
   * @param align The alignment requested; must be a power of 2.
   * @param offset The offset at which the alignment is desired.
   * @param allocator The MMTk allocator to be used for this allocation
   * @return The first byte of a suitably sized and aligned region of memory.
   */
  private static Address allocateSpace(SelectedPlanLocal plan, int bytes, 
                                       int align, int offset, int allocator)
    throws UninterruptiblePragma, InlinePragma {
    return allocateSpace(plan, bytes, align, offset, false, allocator, null);
  }

  /** 
   * Allocate space for copying an object
   *
   * @param plan The plan instance to be used for this allocation
   * @param bytes The size of the allocation in bytes
   * @param align The alignment requested; must be a power of 2.
   * @param offset The offset at which the alignment is desired.
   * @param from The source object from which this is to be copied
   * @return The first byte of a suitably sized and aligned region of memory.
   */
  public static Address allocateSpace(SelectedPlanLocal plan, int bytes, 
                                      int align, int offset, int allocator,
                                      ObjectReference from)
    throws UninterruptiblePragma, InlinePragma {
    return allocateSpace(plan, bytes, align, offset, true, allocator, from);
  }

  /** 
   * Allocate space for an object
   *
   * @param plan The plan instance to be used for this allocation
   * @param bytes The size of the allocation in bytes
   * @param align The alignment requested; must be a power of 2.
   * @param offset The offset at which the alignment is desired.
   * @param copy Is this object a copy (true) or a new instance (false)?
   * @param allocator The MMTk allocator to be used (if allocating)
   * @param from The source object from which to copy (if copying)
   * @return The first byte of a suitably sized and aligned region of memory.
   */
  private static Address allocateSpace(SelectedPlanLocal plan, int bytes, 
                                       int align, int offset, boolean copy,
                                       int allocator, ObjectReference from)
                                          
    throws UninterruptiblePragma, InlinePragma {
    // MMTk requests must be in multiples of MIN_ALIGNMENT
    bytes = VM_Memory.alignUp(bytes, MIN_ALIGNMENT);

    /*
     * Now make the request
     */
    Address region;
    if (copy) {
      region = plan.allocCopy(from, bytes, align, offset, allocator);
      /* TODO
      if (Stats.GATHER_MARK_CONS_STATS) Plan.mark.inc(bytes);
      */
    } else {
      region = plan.alloc(bytes, align, offset, allocator);
      /* TODO
      if (Stats.GATHER_MARK_CONS_STATS) Plan.cons.inc(bytes);
      */
    }
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
  public static final Offset alignAllocation(Offset initialOffset, int align,
                                          int offset)
    throws UninterruptiblePragma, InlinePragma {
    Address region = VM_Memory.alignUp(initialOffset.toWord().toAddress(),
                                       MIN_ALIGNMENT);
    return Allocator.alignAllocation(region, align, offset).toWord().toOffset();
  }

  /**
   * Allocate an array object, using the given array as an example of
   * the required type.
   *
   * @param array an array of the type to be allocated
   * @param allocator which allocation scheme/area JMTk should
   * allocation the memory from.
   * @param length the number of elements in the array to be allocated
   * @return the initialzed array object
   */
  public static Object cloneArray(Object [] array, int allocator, int length)
      throws UninterruptiblePragma {
    VM_Array type = VM_Magic.getObjectType(array).asArray();
    Object [] tib = type.getTypeInformationBlock();
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(type);
    int align = VM_ObjectModel.getAlignment(type);
    int offset = VM_ObjectModel.getOffsetForAlignment(type);
    return allocateArray(length, type.getLogElementSize(), headerSize,
                         tib, allocator, align, offset);
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
    int width  = type.getLogElementSize();
    Object [] tib = type.getTypeInformationBlock();
    int allocator = isHot ? Plan.ALLOC_HOT_CODE : Plan.ALLOC_COLD_CODE;
    
    return (VM_CodeArray) allocateArray(numInstrs, width, headerSize, tib,
                                        allocator, align, offset);
  }

  /**
   * Allocate a stack
   * @param bytes    The number of bytes to allocate
   * @param immortal  Is the stack immortal and non-moving?
   * @return The stack
   */ 
  public static byte[] newStack(int bytes, boolean immortal)
    throws InlinePragma, InterruptiblePragma {
    if (!VM.runningVM) {
      return new byte[bytes];
    } else {
      VM_Array stackType = VM_Array.ByteArray;
      int headerSize = VM_ObjectModel.computeArrayHeaderSize(stackType);
      int align = VM_ObjectModel.getAlignment(stackType);
      int offset = VM_ObjectModel.getOffsetForAlignment(stackType);
      int width  = stackType.getLogElementSize();
      Object [] stackTib = stackType.getTypeInformationBlock();

      return (byte[]) allocateArray(bytes, width, headerSize, stackTib,
                                    (immortal ? Plan.ALLOC_IMMORTAL_STACK : Plan.ALLOC_STACK),
                                    align, offset);
    }
  }

  /**
   * Allocate a new type information block (TIB).
   *
   * @param n the number of slots in the TIB to be allocated
   * @return the new TIB
   */
  public static Object[] newTIB (int n)
    throws InlinePragma, InterruptiblePragma {

    if (!VM.runningVM) 
      return new Object[n];

    VM_Array objectArrayType = VM_Type.JavaLangObjectArrayType;
    Object [] objectArrayTib = objectArrayType.getTypeInformationBlock();
    int align = VM_ObjectModel.getAlignment(objectArrayType);
    int offset = VM_ObjectModel.getOffsetForAlignment(objectArrayType);
    Object result = allocateArray(n, objectArrayType.getLogElementSize(),
                                  VM_ObjectModel.computeArrayHeaderSize(objectArrayType),
                                  objectArrayTib, Plan.ALLOC_IMMORTAL, align, offset);
    return (Object []) result;
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n)
    throws InlinePragma, InterruptiblePragma {
    return new VM_CompiledMethod[n];
  }

  /**
   * Allocate a contiguous VM_DynamicLibrary array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_DynamicLibrary[] newContiguousDynamicLibraryArray(int n)
    throws InlinePragma, InterruptiblePragma {
    return new VM_DynamicLibrary[n];
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
  public static void addFinalizer(Object object) throws InterruptiblePragma {
    Finalizer.addCandidate(ObjectReference.fromObject(object));
  }

  /**
   * Gets an object from the list of objects that are to be reclaimed
   * and need to have their <code>finalize</code> method called.
   *
   * @return the object needing to be finialized
   */
  public static Object getFinalizedObject() throws InterruptiblePragma {
    return Finalizer.get();
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
   public static void addSoftReference(SoftReference obj)
     throws InterruptiblePragma {
     ReferenceGlue.addSoftCandidate(obj);
   }
 
  /**
   * Add a weak reference to the list of weak references.
   *
   * @param obj the weak reference to be added to the list
   */
   public static void addWeakReference(WeakReference obj)
     throws InterruptiblePragma {
     ReferenceGlue.addWeakCandidate(obj);
   }
 
  /**
   * Add a phantom reference to the list of phantom references.
   *
   * @param obj the phantom reference to be added to the list
   */
   public static void addPhantomReference(PhantomReference obj)
     throws InterruptiblePragma {
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
   * Return the initial heap size in bytes (as set by -Xms).
   *
   * @return The initial heap size in bytes (as set by -Xms).
   */
  public static Extent getInitialHeapSize() {
    return HeapGrowthManager.getInitialHeapSize();
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
  public static void notifyClassResolved(VM_Type vmType) 
    throws InterruptiblePragma {
    MMType type;
    if (vmType.isArrayType()) {
      type = new MMType(false,
                        vmType.asArray().getElementType().isReferenceType(),
                        vmType.isAcyclicReference(),
                        pickAllocatorForType(vmType),
                        zeroLengthIntArray);
    } else {
      type = new MMType(false,
                        false,
                        vmType.isAcyclicReference(),
                        pickAllocatorForType(vmType),
                        vmType.asClass().getReferenceOffsets());
    }
    vmType.setMMType(type);
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions prior to the commencement of a
   * benchmark, such as a full heap collection, turning on
   * instrumentation, etc.
   */
  public static void harnessBegin() throws InterruptiblePragma {
    Plan.harnessBegin();
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions after the completion of a
   * benchmark, such as a full heap collection, turning off
   * instrumentation, etc.
   */
  public static void harnessEnd() {
    Plan.harnessEnd();
  }

  /**
   * Check if object might be a TIB.
   *
   * @param obj address of object to check
   * @return <code>false</code> if the object is in the wrong
   * allocation scheme/area for a TIB, <code>true</code> otherwise
   */
  public static boolean mightBeTIB(ObjectReference obj)
    throws InlinePragma, UninterruptiblePragma {
    return !obj.isNull() && Space.isImmortal(obj);
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static final boolean gcInProgress() throws UninterruptiblePragma {
    return Plan.gcInProgress();
  }

  /**
   * Start the GCspy server
   */
  public static void startGCspyServer() throws InterruptiblePragma {
    GCspy.startGCspyServer();
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
  public static int[] newContiguousIntArray(int n)
    throws InlinePragma, InterruptiblePragma {
    return new int[n];
  }

}

