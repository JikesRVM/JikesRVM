/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import java.util.Date;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.ref.PhantomReference;

import org.mmtk.plan.Plan;
import org.mmtk.utility.AllocAdvice;
import org.mmtk.utility.Barrier;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.HeapGrowthManager;
import org.mmtk.utility.Memory;
import org.mmtk.utility.MMType;
import org.mmtk.utility.Options;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.SynchronizedCounter;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Method;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_CodeArray;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_DynamicLibrary;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Uninterruptible;

import org.mmtk.vm.gcspy.GCSpy;

/**
 * The interface that the JMTk memory manager presents to the Jikes
 * research virtual machine.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */  
public class MM_Interface implements Constants, VM_Uninterruptible {

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
  public static final boolean NEEDS_WRITE_BARRIER = Plan.NEEDS_WRITE_BARRIER;

  /**
   * <code>true</code> if a write barrier for putstatic operations is
   * required.  Specifically, if the memory manger requires that the
   * virtual machine calls putstaticWriteBarrier, when a putstatic
   * operation is needed.
   * AJG: Is this correct?
   */
  public static final boolean NEEDS_PUTSTATIC_WRITE_BARRIER
    = Plan.NEEDS_PUTSTATIC_WRITE_BARRIER;

  /* AJG: Not used. But will be. needed */
  /**
   * <code>true</code> if a write barrier for TIB store operations is
   * requried.  Note: This field is not used.
   */
  public static final boolean NEEDS_TIB_STORE_WRITE_BARRIER
    = Plan.NEEDS_TIB_STORE_WRITE_BARRIER;

  /**
   * <code>true</code> if the memory manager moves objects. For
   * example, a copying collector will move objects.
   */
  public static final boolean MOVES_OBJECTS = Plan.MOVES_OBJECTS;

  /**
   * <code>true</code> if the memory manager moves type information
   * blocks (TIBs).
   */
  public static final boolean MOVES_TIBS = Plan.MOVES_TIBS;

  /**
   * <code>true</code> if checking of allocated memory to ensure it is
   * zeroed is desired.
   */
  private static final boolean CHECK_MEMORY_IS_ZEROED = false;

  /**
   * <code>true</code> if the memory manager will generate a garbage
   * collection trace of the run.
   */
  public static final boolean GENERATE_GC_TRACE = Plan.GENERATE_GC_TRACE;

  /** Used by mmtypes for arrays */
  private static final int [] zeroLengthIntArray = new int [0];
  
  /* AJG: Not used */
//   public static final boolean RC_CYCLE_DETECTION = Plan.REF_COUNT_CYCLE_DETECTION;

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
  public static final void init() throws VM_PragmaInterruptible {
    VM_CollectorThread.init();
    VM_Interface.init();
  }

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   * @param theBootRecord the boot record. Contains information about
   * the heap size.
   */
  public static final void boot (VM_BootRecord theBootRecord)
    throws VM_PragmaInterruptible {
    int pageSize = VM_Memory.getPagesize();  // Cannot be determined at init-time
    HeapGrowthManager.boot(theBootRecord.initialHeapSize, theBootRecord.maximumHeapSize);
    DebugUtil.boot(theBootRecord);
    Plan.boot();
    VMResource.boot();
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
   * @param p The <code>VM_Processor</code> object.
   */
  public static final void setupProcessor(VM_Processor proc)
    throws VM_PragmaInterruptible {
    // if (proc.mmPlan == null) proc.mmPlan = new Plan();
    // if (VM.VerifyAssertions) VM._assert(proc.mmPlan != null);
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  public static void postBoot() throws VM_PragmaInterruptible {
    Plan.postBoot();
  }

  /**
   * Notify the MM that the host VM is now fully booted.
   */
  public static void fullyBootedVM() throws VM_PragmaInterruptible {
    Plan.fullyBooted();
    Lock.fullyBooted();
    Barrier.fullyBooted();
  }

  /** 
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg)
    throws VM_PragmaInterruptible {
    Options.process(arg);
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
   */
  public static void putfieldWriteBarrier(Object ref, int offset, Object value)
    throws VM_PragmaInline {
    VM_Address src = VM_Magic.objectAsAddress(ref);
    VM_Interface.getPlan().writeBarrier(src,
                                        src.add(offset),
                                        VM_Magic.objectAsAddress(value),
                                        PUTFIELD_WRITE_BARRIER);
  }
  
  /**
   * Write barrier for putstatic operations.
   *
   * @param ref the object which is the subject of the putfield
   * @param offset the offset of the field to be modified
   * @param value the new value for the field
   */
  public static void putstaticWriteBarrier(int offset, Object value)
    throws VM_PragmaInline { 
    // putstatic barrier currently unimplemented
    if (VM.VerifyAssertions) VM._assert(false);
//     VM_Address jtoc = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
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
    throws VM_PragmaInline {
    VM_Address array = VM_Magic.objectAsAddress(ref);
    VM_Interface.getPlan().writeBarrier(array,
                                        array.add(index<<LOG_BYTES_IN_ADDRESS),
                                        VM_Magic.objectAsAddress(value),
                                        AASTORE_WRITE_BARRIER);
  }

  /**
   * Checks that if a garbage collection is in progress then the given
   * object is not movable.  If it is movable error messages are
   * logged and the system exits.
   *
   * @param obj the object to check
   */
  public static void modifyCheck(Object obj) {
    /* Make sure that during GC, we don't update on a possibly moving object. 
       Such updates are dangerous because they can be lost.
     */
    if (Plan.gcInProgressProper()) {
        VM_Address ref = VM_Magic.objectAsAddress(obj);
        if (VMResource.refIsMovable(ref)) {
            VM.sysWriteln("GC modifying a potentially moving object via Java (i.e. not magic)");
            VM.sysWriteln("  obj = ", ref);
            VM_Type t = VM_Magic.getObjectType(obj);
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
    throws VM_PragmaUninterruptible {
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
  public static final long freeMemory() {
    return Plan.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static final long totalMemory() {
    return Plan.totalMemory();
  }

  /**
   * Returns the maximum amount of memory VM will attempt to use.
   *
   * @return The maximum amount of memory VM will attempt to use.
   */
  public static final long maxMemory() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /**
   * External call to force a garbage collection.
   */
  public static final void gc() throws VM_PragmaInterruptible {
    if (!Options.ignoreSystemGC)
      VM_Interface.triggerCollection(VM_Interface.EXTERNAL_GC_TRIGGER);
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
  public static void dumpRef(VM_Address ref) throws VM_PragmaUninterruptible {
    DebugUtil.dumpRef(ref);
  }

  /**
   * Checks if a reference is valid.
   *
   * @param ref the address to be checked
   * @return <code>true</code> if the reference is valid
   */
  public static boolean validRef(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return DebugUtil.validRef(ref);
  }

  /**
   * Checks if an address refers to an in-use area of memory.
   * 
   * @param address the address to be checked
   * @return <code>true</code> if the address refers to an in use area
   */
  public static boolean addrInVM(VM_Address address)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.addrInVM(address);
  }

  /**
   * Checks if a reference refers to an object in an in-use area of
   * memory.
   *
   * <p>References may be addresses just outside the memory region
   * allocated to the object.
   *
   * @param ref the reference to be checked
   * @return <code>true</code> if the object refered to is in an
   * in-use area
   */
  public static boolean refInVM(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.refInVM(ref);
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
  public static int pickAllocator(VM_Type type) throws VM_PragmaInterruptible {
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
    throws VM_PragmaInterruptible {
    int aLen = a.length();
    if (aLen > b.length)
      return false;
    for (int i = 0; i<aLen; i++) {
      if (a.charAt(i) != ((char) b[i]))
        return false;
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
  public static int pickAllocator(VM_Type type, VM_Method method)
    throws VM_PragmaInterruptible {
    /*
     * We check the calling method so GC-data can go into special
     * places.  A better implementation would be call-site specific
     * which is strictly more refined.
     */
    if (method != null) {
     // We should strive to be allocation-free here.
      VM_Class cls = method.getDeclaringClass();
      byte[] clsBA = cls.getDescriptor().toByteArray();
      if (VM_Interface.GCSPY) {
        if (isPrefix("Lorg/mmtk/vm/gcspy/",  clsBA) ||
            isPrefix("[Lorg/mmtk/vm/gcspy/", clsBA)) {
	  return Plan.GCSPY_SPACE;
        }
      }
      if (isPrefix("Lorg/mmtk/", clsBA) 
	  || isPrefix("Lcom/ibm/JikesRVM/memoryManagers/mmInterface/VM_GCMapIteratorGroup", clsBA)) {
        return Plan.IMMORTAL_SPACE;
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
    throws VM_PragmaInterruptible {
    int allocator = Plan.DEFAULT_SPACE;
    byte[] typeBA = type.getDescriptor().toByteArray();
    if (VM_Interface.GCSPY) {
      if (isPrefix("Lorg/mmtk/vm/gcspy/",  typeBA) ||
	       isPrefix("[Lorg/mmtk/vm/gcspy/", typeBA)) 
	allocator = Plan.GCSPY_SPACE;
    }
    if (isPrefix("Lorg/mmtk/", typeBA) ||
	isPrefix("Lcom/ibm/JikesRVM/memoryManagers/", typeBA) ||
        isPrefix("Lcom/ibm/JikesRVM/VM_Processor;", typeBA) ||
        isPrefix("Lcom/ibm/JikesRVM/jni/VM_JNIEnvironment;", typeBA))
      allocator = Plan.IMMORTAL_SPACE;
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
    throws VM_PragmaUninterruptible, VM_PragmaInline {

    Plan plan = VM_Interface.getPlan();
    // JMTk requires sizes to be multiples of BYTES_IN_PARTICLE.
    // Jikes RVM currently already forces scalar sizes to be multiples
    // of BYTES_IN_INT. But, if BYTES_IN_PARTICLE > BYTES_IN_INT
    // then we have to do round up the allocation request.
    int alignedSize = size;
    if (BYTES_IN_PARTICLE > BYTES_IN_INT)
      alignedSize = VM_Memory.alignUp(size, BYTES_IN_PARTICLE);
    int rawSize = (align > BYTES_IN_INT) ? (alignedSize + align) : alignedSize;
    AllocAdvice advice = plan.getAllocAdvice(null, rawSize, null, null);
    VM_Address region = plan.alloc(rawSize, true, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, rawSize);
    if (align > BYTES_IN_INT) region = alignAllocation(region, align, offset);
    Object result = VM_ObjectModel.initializeScalar(region, tib, size);
    plan.postAlloc(VM_Magic.objectAsAddress(result), tib, rawSize, true,
                   allocator);
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
    throws VM_PragmaUninterruptible, VM_PragmaInline {

    int elemBytes = numElements << logElementSize;
    if ((elemBytes >>> logElementSize) != numElements) {
      // asked to allocate more than Integer.MAX_VALUE bytes
      VM_Interface.failWithOutOfMemoryError();
    }
    // JMTk requires sizes to be multiples of BYTES_IN_PARTICLES.
    // Jikes RVM does not ensure this for arrays, so we must round up here.
    int size = VM_Memory.alignUp(elemBytes + headerSize, BYTES_IN_PARTICLE);
    int rawSize = (align > BYTES_IN_INT) ? (size + align) : size;
    Plan plan = VM_Interface.getPlan();
    AllocAdvice advice = plan.getAllocAdvice(null, rawSize, null, null);
    VM_Address region = plan.alloc(rawSize, false, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) Memory.assertIsZeroed(region, rawSize);
    if (align > BYTES_IN_INT) region = alignAllocation(region, align, offset);
    Object result = VM_ObjectModel.initializeArray(region, tib, numElements,
                                                   size);
    plan.postAlloc(VM_Magic.objectAsAddress(result), tib, rawSize, false,
                   allocator);
    return result;
  }

  /**
   * Align an allocation using some modulo arithmetic to guarantee the
   * following property:<br>
   * <code>(region + offset) % alignment == 0</code>
   *
   * @param region The initial (unaligned) start value of the
   * allocated region of memory.
   * @param align The alignment requested, must be a power of two
   * @param offset The offset at which the alignment is desired
   * @return <code>region</code> plus some delta (possibly 0) such
   * that the return value is aligned according to the above
   * constraints.
   */
  public static final int alignAllocation(int initialOffset, int align,
                                          int offset)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return alignAllocation(VM_Address.fromInt(initialOffset), align, offset).toInt();
  }

  /**
   * Align an allocation using some modulo arithmetic to guarantee the
   * following property:<br>
   * <code>(region + offset) % alignment == 0</code>
   *
   * @param region The initial (unaligned) start value of the
   * allocated region of memory.
   * @param align The alignment requested, must be a power of two
   * @param offset The offset at which the alignment is desired
   * @return <code>region</code> plus some delta (possibly 0) such
   * that the return value is aligned according to the above
   * constraints.
   */
  private static final VM_Address alignAllocation(VM_Address region, int align,
                                                  int offset)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Word mask  = VM_Word.fromIntSignExtend(align-1);
    VM_Word negOff= VM_Word.fromIntSignExtend(-offset);
    VM_Offset delta = negOff.sub(region.toWord()).and(mask).toOffset();
    return region.add(delta);
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
      throws VM_PragmaUninterruptible {
    VM_Array type = VM_Magic.getObjectType(array).asArray();
    Object [] tib = type.getTypeInformationBlock();
    int headerSize = VM_ObjectModel.computeArrayHeaderSize(type);
    int align = VM_ObjectModel.getAlignment(type);
    int offset = VM_ObjectModel.getOffsetForAlignment(type);
    return allocateArray(length, type.getLogElementSize(), headerSize,
                         tib, allocator, align, offset);
  }


  /**
   * Allocate a VM_CodeArray
   * NOTE: We don't use this at all for Jikes RVM right now.
   *       It might be useful to think about expanding the interface
   *       to take hints on the likely hot/coldness of the code and
   *       other code placement hints.
   * @param n The number of instructions to allocate
   * @return The  array
   */
  public static VM_CodeArray newInstructions(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return VM_CodeArray.create(n);
  }

  /**
   * Allocate a stack
   * @param n The number of bytes to allocate
   * @param immortal  Is the stack immortal and non-moving?
   * @return The stack
   */ 
  public static byte[] newStack(int bytes, boolean immortal)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    if (!immortal || !VM.runningVM)
      return new byte[bytes];

    int logAlignment = 12;
    int alignment = 1 << logAlignment; // 4096
    VM_Array stackType = VM_Array.ByteArray;
    Object [] stackTib = stackType.getTypeInformationBlock();
    int offset = VM_JavaHeader.computeArrayHeaderSize(stackType);
    int arraySize = VM_Memory.alignUp(stackType.getInstanceSize(bytes), BYTES_IN_PARTICLE);
    int fullSize = arraySize + alignment;  // somewhat wasteful
    if (VM.VerifyAssertions) VM._assert(alignment > offset);
    AllocAdvice advice = VM_Interface.getPlan().getAllocAdvice(null, fullSize, null, null);
    VM_Address fullRegion = VM_Interface.getPlan().alloc(fullSize, false, Plan.IMMORTAL_SPACE, advice);
    VM_Address tmp = fullRegion.add(alignment);
    VM_Word mask = VM_Word.one().lsh(logAlignment).sub(VM_Word.one()).not();
    VM_Address region = tmp.toWord().and(mask).sub(VM_Word.fromIntSignExtend(offset)).toAddress();
    Object result = VM_ObjectModel.initializeArray(region, stackTib, bytes, arraySize);
    VM_Interface.getPlan().postAlloc(VM_Magic.objectAsAddress(result), stackTib, arraySize, false, Plan.IMMORTAL_SPACE);
    return (byte []) result;
  }

  /**
   * Allocate a new type information block (TIB).
   *
   * @param n the number of slots in the TIB to be allocated
   * @return the new TIB
   */
  public static Object[] newTIB (int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {

    if (!VM.runningVM) 
      return new Object[n];

    VM_Array objectArrayType = VM_Type.JavaLangObjectArrayType;
    Object [] objectArrayTib = objectArrayType.getTypeInformationBlock();
    int align = VM_ObjectModel.getAlignment(objectArrayType);
    int offset = VM_ObjectModel.getOffsetForAlignment(objectArrayType);
    Object result = allocateArray(n, objectArrayType.getLogElementSize(),
                                  VM_ObjectModel.computeArrayHeaderSize(objectArrayType),
                                  objectArrayTib, Plan.IMMORTAL_SPACE, align, offset);
    return (Object []) result;
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new VM_CompiledMethod[n];
  }

  /**
   * Allocate a contiguous VM_DynamicLibrary array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_DynamicLibrary[] newContiguousDynamicLibraryArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
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
   * @param obj the object to be added to the finalizer's list
   */
  public static void addFinalizer(Object obj) throws VM_PragmaInterruptible {
    Finalizer.addCandidate(obj);
  }

  /**
   * Gets an object from the list of objects that are to be reclaimed
   * and need to have their <code>finalize</code> method called.
   *
   * @return the object needing to be finialized
   */
  public static Object getFinalizedObject() throws VM_PragmaInterruptible {
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
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addSoftCandidate(obj);
   }
 
  /**
   * Add a weak reference to the list of weak references.
   *
   * @param obj the weak reference to be added to the list
   */
   public static void addWeakReference(WeakReference obj)
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addWeakCandidate(obj);
   }
 
  /**
   * Add a phantom reference to the list of phantom references.
   *
   * @param obj the phantom reference to be added to the list
   */
   public static void addPhantomReference(PhantomReference obj)
     throws VM_PragmaInterruptible {
     ReferenceProcessor.addPhantomCandidate(obj);
   }


  /***********************************************************************
   *
   * Tracing
   */

  /**
   * Processes an object during the tracing phase of a collection.
   *
   * @param object the address of the object to be processed
   * @return The address of the object after processing (it may have
   * been moved in the course of processing).
   */
  public static VM_Address processPtrValue(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline { 
    return Plan.traceObject(object);
  }

  /**
   * Processes an object during the tracing phase of a collection.
   *
   * @param location the address of a reference to the object to be
   * processed
   */
  public static void processPtrLocation(VM_Address location)
    throws VM_PragmaUninterruptible, VM_PragmaInline { 
    Plan.traceObjectLocation(location, false);
  }

  /***********************************************************************
   *
   * Heap size and heap growth
   */

  /**
   * Return the max heap size in bytes (as set by -Xmx).
   *
   * @return The max heap size in bytes (as set by -Xmx).
   */
  public static int getMaxHeapSize() {
    return HeapGrowthManager.getMaxHeapSize();
  }

  /**
   * Return the initial heap size in bytes (as set by -Xms).
   *
   * @return The initial heap size in bytes (as set by -Xms).
   */
  public static int getInitialHeapSize() {
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
    HeapGrowthManager.overrideGrowHeapSize(growSize);
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
    throws VM_PragmaInterruptible {
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
  public static void harnessBegin() throws VM_PragmaInterruptible {
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
  public static boolean mightBeTIB (VM_Address obj)
    throws VM_PragmaInline, VM_PragmaUninterruptible {
    return VMResource.refIsImmortal(obj);
  }

  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static final boolean gcInProgress() throws VM_PragmaUninterruptible {
    return Plan.gcInProgress();
  }

  /**
   * Start the GCSpy server
   */
  public static void startGCSpyServer() throws VM_PragmaInterruptible {
    GCSpy.startGCSpyServer();
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
    return VMResource.getMaxVMResource();
  }

  /**
   * Allocate a contiguous int array
   * @param n The number of ints
   * @return The contiguous int array
   */ 
  public static int[] newContiguousIntArray(int n)
    throws VM_PragmaInline, VM_PragmaInterruptible {
    return new int[n];
  }

}

