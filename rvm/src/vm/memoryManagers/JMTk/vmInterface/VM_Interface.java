/*
 * (C) Copyright IBM Corp. 2001, 2003
 *
 * VM_Interface.java: methods that JMTk requires to interface with its 
 * enclosing run-time environment. 
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import java.util.Date;
import java.lang.ref.Reference;

import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressDeque;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressPairDeque;
import com.ibm.JikesRVM.memoryManagers.JMTk.Finalizer;
import com.ibm.JikesRVM.memoryManagers.JMTk.ReferenceProcessor;
import com.ibm.JikesRVM.memoryManagers.JMTk.Options;
import com.ibm.JikesRVM.memoryManagers.JMTk.HeapGrowthManager;
import com.ibm.JikesRVM.memoryManagers.JMTk.Enumerate;
import com.ibm.JikesRVM.memoryManagers.JMTk.PreCopyEnumerator;
import com.ibm.JikesRVM.memoryManagers.JMTk.MMType;
import com.ibm.JikesRVM.memoryManagers.JMTk.Scan;

import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Member;
import com.ibm.JikesRVM.classloader.VM_MemberReference;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_Type;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_CommandLineArgs;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_DynamicLibrary;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Word;

/**
 * The interface that the Jikes research virtual machine presents to
 * the JMTk memory manager.
 *
 * @author Perry Cheng  
 * @version $Revision$
 * @date $Date$
 */  

public class VM_Interface implements VM_Constants, VM_Uninterruptible {

  /***********************************************************************
   *
   * Class variables
   */

  /**
   * The address of the start of the boot image.
   */
  public static final VM_Address bootImageAddress = 
    //-#if RVM_FOR_32_ADDR
    VM_Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    VM_Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_LOAD_ADDRESS
     );

  /**
   * The address in virtual memory that is the highest that can be mapped.
   */
  public static VM_Address MAXIMUM_MAPPABLE = 
    //-#if RVM_FOR_32_ADDR
    VM_Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    VM_Address.fromLong
    //-#endif
    (
     //-#value MAXIMUM_MAPPABLE_ADDRESS
     );

  /**
   * An unknown GC trigger reason.  Signals a logic bug.
   */ 
  public static final int UNKNOWN_GC_TRIGGER = 0;  
  /**
   * Externally triggered garbage collection.  For example, the
   * application called System.gc().
   */
  public static final int EXTERNAL_GC_TRIGGER = 1;
  /**
   * Resource triggered garbage collection.  For example, an
   * allocation request would take the number of pages in use beyond
   * the number available.
   */
  public static final int RESOURCE_GC_TRIGGER = 2;
  /**
   * Internally triggered garbage collection.  For example, the memory
   * manager attempting another collection after the first failed to
   * free space.
   */
  public static final int INTERNAL_GC_TRIGGER = 3;
  /**
   * The number of garbage collection trigger reasons.
   */
  public static final int TRIGGER_REASONS = 4;
  /**
   * Short descriptions of the garbage collection trigger reasons.
   */
  private static final String[] triggerReasons = {
    "unknown",
    "external request",
    "resource exhaustion",
    "internal request"
  };

  /**
   * <code>true</code> if assertions should be verified
   */
  public static final boolean VerifyAssertions = VM.VerifyAssertions;

  /**
   * The percentage threshold for throwing an OutOfMemoryError.  If,
   * after a garbage collection, the amount of memory used as a
   * percentage of the available heap memory exceeds this percentage
   * the memory manager will throw an OutOfMemoryError.
   */
  public static final double OUT_OF_MEMORY_THRESHOLD = 0.98;

  /**
   * Counter to track index into thread table for root tracing.
   */
  private static SynchronizedCounter threadCounter = new SynchronizedCounter();

  /**
   * The fully qualified name of the collector thread.
   */
  private static VM_Atom collectorThreadAtom;
  /**
   * The string "run".
   */
  private static VM_Atom runAtom;

  /**
   * An enumerator used to forward root objects
   */
  private static PreCopyEnumerator preCopyEnum;

  /**
   * <code>true</code> if built with GCSpy
   */
  public static final boolean GCSPY =
    //-#if RVM_WITH_GCSPY
    true;
    //-#else
    false;
    //-#endif

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The values of
   * statics at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is called from MM_Interface.
   */
  public static final void init() throws VM_PragmaInterruptible {
    collectorThreadAtom = VM_Atom.findOrCreateAsciiAtom(
      "Lcom/ibm/JikesRVM/memoryManagers/vmInterface/VM_CollectorThread;");
    runAtom = VM_Atom.findOrCreateAsciiAtom("run");
    preCopyEnum = new PreCopyEnumerator();
  }

  /***********************************************************************
   *
   * What we need to know about memory allocated by the outside world
   *<p>
   * Basically where the boot image is, and how much memory is available.
   */

  /**
   * Returns the start of the boot image.
   *
   * @return the address of the start of the boot image
   */
  public static VM_Address bootImageStart() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageStart;
  }

  /**
   * Return the end of the boot image.
   *
   * @return the address of the end of the boot image
   */
  public static VM_Address bootImageEnd() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageEnd;
  }

  /***********************************************************************
   *
   * Manipulate raw memory
   */

  /**
   * Maps an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return 0 if successful, otherwise the system errno
   */
  public static int mmap(VM_Address start, int size) {
    VM_Address result = VM_Memory.mmap(start, VM_Extent.fromIntZeroExtend(size),
                                       VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC, 
                                       VM_Memory.MAP_PRIVATE | VM_Memory.MAP_FIXED | VM_Memory.MAP_ANONYMOUS);
    if (result.EQ(start)) return 0;
    if (result.GT(VM_Address.fromIntZeroExtend(127))) {
      VM.sysWrite("mmap with MAP_FIXED on ", start);
      VM.sysWriteln(" returned some other address", result);
      VM.sysFail("mmap with MAP_FIXED has unexpected behavior");
    }
    return result.toInt();
  }
  
  /**
   * Protects access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean mprotect(VM_Address start, int size) {
    return VM_Memory.mprotect(start, VM_Extent.fromIntZeroExtend(size),
                              VM_Memory.PROT_NONE);
  }

  /**
   * Allows access to an area of virtual memory.
   *
   * @param start the address of the start of the area to be mapped
   * @param size the size, in bytes, of the area to be mapped
   * @return <code>true</code> if successful, otherwise
   * <code>false</code>
   */
  public static boolean munprotect(VM_Address start, int size) {
    return VM_Memory.mprotect(start, VM_Extent.fromIntZeroExtend(size),
                              VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC);
  }

  /**
   * Zero a region of memory.
   * @param start Start of address range (inclusive)
   * @param len Length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(VM_Address start, VM_Extent len) {
    VM_Memory.zero(start,len);
  }

  /**
   * Zero a range of pages of memory.
   * @param start Start of address range (must be a page address)
   * @param len Length in bytes of range (must be multiple of page size)
   */
  public static void zeroPages(VM_Address start, int len) {
      /* AJG: Add assertions to check conditions documented above. */
    VM_Memory.zeroPages(start,len);
  }

  /**
   * Logs the contents of an address and the surrounding memory to the
   * error output.
   *
   * @param start the address of the memory to be dumped
   * @param beforeBytes the number of bytes before the address to be
   * included
   * @param afterBytes the number of bytes after the address to be
   * included
   */
  public static void dumpMemory(VM_Address start, int beforeBytes,
                                int afterBytes) {
    VM_Memory.dumpMemory(start,beforeBytes,afterBytes);
  }

  /***********************************************************************
   *
   * Access to object model
   */

  /*
   * Call-throughs to VM_ObjectModel
   */

  /**
   * Tests a bit available for memory manager use in an object.
   *
   * @param o the address of the object
   * @param idx the index of the bit
   */
  public static boolean testAvailableBit(VM_Address o, int idx) {
    return VM_ObjectModel.testAvailableBit(VM_Magic.addressAsObject(o),idx);
  }

  /**
   * Sets a bit available for memory manager use in an object.
   *
   * @param o the address of the object
   * @param idx the index of the bit
   * @param flag <code>true</code> to set the bit to 1,
   * <code>false</code> to set it to 0
   */
  public static void setAvailableBit(VM_Address o, int idx, boolean flag) {
    VM_ObjectModel.setAvailableBit(VM_Magic.addressAsObject(o),idx,flag);
  }

  /**
   * Attempts to set the bits available for memory manager use in an
   * object.  The attempt will only be successful if the current value
   * of the bits matches <code>oldVal</code>.  The comparison with the
   * current value and setting are atomic with respect to other
   * allocators.
   *
   * @param o the address of the object
   * @param oldVal the required current value of the bits
   * @param newVal the desired new value of the bits
   * @return <code>true</code> if the bits were set,
   * <code>false</code> otherwise
   */
  public static boolean attemptAvailableBits(VM_Address o,
					     VM_Word oldVal, VM_Word newVal) {
    return VM_ObjectModel.attemptAvailableBits(VM_Magic.addressAsObject(o), oldVal, newVal);
  }

  /**
   * Gets the value of bits available for memory manager use in an
   * object, in preparation for setting those bits.
   *
   * @param o the address of the object
   * @return the value of the bits
   */
  public static VM_Word prepareAvailableBits(VM_Address o) {
    return VM_ObjectModel.prepareAvailableBits(VM_Magic.addressAsObject(o));
  }

  /**
   * Sets the bits available for memory manager use in an object.
   *
   * @param o the address of the object
   * @param val the new value of the bits
   */
  public static void writeAvailableBitsWord(VM_Address o, VM_Word val) {
    VM_ObjectModel.writeAvailableBitsWord(VM_Magic.addressAsObject(o),val);
  }

  /**
   * Read the bits available for memory manager use in an object.
   *
   * @param o the address of the object
   * @return the value of the bits
   */
  public static VM_Word readAvailableBitsWord(VM_Address o) {
    return VM_ObjectModel.readAvailableBitsWord(o);
  }

  /**
   * Gets the offset of the memory management header from the object
   * reference address.  XXX The object model / memory manager
   * interface should be improved so that the memory manager does not
   * need to know this.
   *
   * @return the offset, relative the object reference address
   */
  /* AJG: Should this be a variable rather than method? */
  public static int GC_HEADER_OFFSET() {
    return VM_ObjectModel.GC_HEADER_OFFSET;
  }

  /**
   * Returns the lowest address of the storage associated with an object.
   *
   * @param object the reference address of the object
   * @return the lowest address of the object
   */
  public static VM_Address objectStartRef(VM_Address object)
    throws VM_PragmaInline {
    return VM_ObjectModel.objectStartRef(object);
  }

  /**
   * Returns an address guaranteed to be inside the storage assocatied
   * with and object.
   *
   * @param obj the reference address of the object
   * @return an address inside the object
   */
  public static VM_Address refToAddress(VM_Address obj) {
    return VM_ObjectModel.getPointerInMemoryRegion(obj);
  }

  /**
   * Checks if a reference of the given type in another object is
   * inherently acyclic.  The type is given as a TIB.
   *
   * @return <code>true</code> if a reference of the type is
   * inherently acyclic
   */
  public static boolean isAcyclic(Object[] tib) {
    return VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]).isAcyclicReference();
  }
 
  /***********************************************************************
   *
   * Trigger collections
   */

  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  public static final void triggerCollection(int why)
    throws VM_PragmaInterruptible {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    Plan.collectionInitiated();

    if (Options.verbose >= 4) {
      VM.sysWriteln("Entered VM_Interface.triggerCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }
    if (why == EXTERNAL_GC_TRIGGER) {
      Plan.userTriggeredGC();
      if (Options.verbose == 1 || Options.verbose == 2) 
        VM.sysWrite("[Forced GC]");
    }
    if (Options.verbose > 2) VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    int sizeBeforeGC = HeapGrowthManager.getCurrentHeapSize();
    long start = VM_Time.cycles();
    VM_CollectorThread.collect(VM_CollectorThread.handshake, why);
    long end = VM_Time.cycles();
    double gcTime = VM_Time.cyclesToMillis(end - start);
    if (Options.verbose > 2) VM.sysWriteln("Collection finished (ms): ", gcTime);

    if (Plan.isLastGCFull() && 
        sizeBeforeGC == HeapGrowthManager.getCurrentHeapSize()) 
      checkForExhaustion(why, false);
    
    Plan.checkForAsyncCollection();
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  public static final void triggerAsyncCollection()
    throws VM_PragmaUninterruptible {
    checkForExhaustion(RESOURCE_GC_TRIGGER, true);
    Plan.collectionInitiated();
    if (Options.verbose >= 1) VM.sysWrite("[Async GC]");
    VM_CollectorThread.asyncCollect(VM_CollectorThread.handshake);
  }

  public static final void dumpStack () {
    VM_Scheduler.dumpStack();
  }

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
 public static final boolean noThreadsInGC() throws VM_PragmaUninterruptible {
   return VM_CollectorThread.noThreadsInGC(); 
 }

  /**
   * Check for memory exhaustion, possibly throwing an out of memory
   * exception and/or triggering another GC.
   *
   * @param why Why the collection was triggered
   * @param async True if this collection was asynchronously triggered.
   */
  private static final void checkForExhaustion(int why, boolean async)
    throws VM_PragmaLogicallyUninterruptible {
    double usage = Plan.reservedMemory() / ((double) Plan.totalMemory());
    
    //    if (Plan.totalMemory() - Plan.reservedMemory() < 64<<10) {
    if (usage > OUT_OF_MEMORY_THRESHOLD) {
      if (why == INTERNAL_GC_TRIGGER) {
        if (Options.verbose >= 2) {
          VM.sysWriteln("OutOfMemoryError: usage = ", usage);
          VM.sysWriteln("          reserved (kb) = ",(int)(Plan.reservedMemory() / 1024));
          VM.sysWriteln("          total    (Kb) = ",(int)(Plan.totalMemory() / 1024));
        }
        if (VM.debugOOM || Options.verbose >= 5)
          VM.sysWriteln("triggerCollection(): About to try \"new OutOfMemoryError()\"");
        MM_Interface.emergencyGrowHeap(512 * (1 << 10));  // 512K should be plenty to make an exn
        OutOfMemoryError oome = new OutOfMemoryError();
        MM_Interface.emergencyGrowHeap(- (512 * (1 << 10)));
        if (VM.debugOOM || Options.verbose >= 5)
          VM.sysWriteln("triggerCollection(): Allocated the new OutOfMemoryError().");
        throw oome;
      }
      /* clear all possible reference objects */
      ReferenceProcessor.setClearSoftReferences(true);
      if (!async)
        triggerCollection(INTERNAL_GC_TRIGGER);
    }
  }
   

  /***********************************************************************
   *
   * Finalizers
   */
  
  /**
   * Schedule the finalizerThread, if there are objects to be
   * finalized and the finalizerThread is on its queue (ie. currently
   * idle).  Should be called at the end of GC after moveToFinalizable
   * has been called, and before mutators are allowed to run.
   */
  public static void scheduleFinalizerThread ()
    throws VM_PragmaUninterruptible {

    int finalizedCount = Finalizer.countToBeFinalized();
    boolean alreadyScheduled = VM_Scheduler.finalizerQueue.isEmpty();
    if (finalizedCount > 0 && !alreadyScheduled) {
      VM_Thread t = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(t);
    }
  }

  /***********************************************************************
   *
   * Collection
   */

  /**
   * Checks if a plan instance is eligible to participate in a
   * collection.
   *
   * @param plan the plan to check
   * @return <code>true</code> if the plan is not participating,
   * <code>false</code> otherwise
   */
  public static boolean isNonParticipating(Plan plan) {
    VM_Processor vp = (VM_Processor)plan;
    int vpStatus = vp.vpStatus;
    return vpStatus == VM_Processor.BLOCKED_IN_NATIVE;
  }

  /**
   * Prepare a plan that is not participating in a collection.
   *
   * @param p the plan to prepare
   */
  public static void prepareNonParticipating(Plan p) {
    /*
     * The collector threads of processors currently running threads
     * off in JNI-land cannot run.
     */
    VM_Processor vp = (VM_Processor) p;
    int vpStatus = vp.vpStatus;
    if (VM.VerifyAssertions)
      VM._assert(vpStatus == VM_Processor.BLOCKED_IN_NATIVE);

    // processor & its running thread are blocked in C for this GC.  
    // Its stack needs to be scanned, starting from the "top" java frame, which has
    // been saved in the running threads JNIEnv.  Put the saved frame pointer
    // into the threads saved context regs, which is where the stack scan starts.
    //
    VM_Thread t = vp.activeThread;
    t.contextRegisters.setInnermost(VM_Address.zero(), t.jniEnv.topJavaFP());
  }

  public static int getArrayLength(VM_Address object) throws VM_PragmaInline {
    Object obj = VM_Magic.addressAsObject(object);
    return VM_Magic.getArrayLength(obj);
  }

  /**
   * Set a collector thread's so that a scan of its stack
   * will start at VM_CollectorThread.run
   *
   * @param p the plan to prepare
   */
  public static void prepareParticipating (Plan p) {
    VM_Processor vp = (VM_Processor) p;
    if (VM.VerifyAssertions) VM._assert(vp == VM_Processor.getCurrentProcessor());
    VM_Thread t = VM_Thread.getCurrentThread();
    VM_Address fp = VM_Magic.getFramePointer();
    while (true) {
      VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
      VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
      if (VM_Magic.getCallerFramePointer(caller_fp).EQ(STACKFRAME_SENTINEL_FP)) 
        VM.sysFail("prepareParticipating: Could not locate VM_CollectorThread.run");
      int compiledMethodId = VM_Magic.getCompiledMethodID(caller_fp);
      VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
      VM_Method method = compiledMethod.getMethod();
      VM_Atom cls = method.getDeclaringClass().getDescriptor();
      VM_Atom name = method.getName();
      if (name == runAtom && cls == collectorThreadAtom) {
        t.contextRegisters.setInnermost(caller_ip, caller_fp);
        break;
      }
      fp = caller_fp; 
    }

  }

  /***********************************************************************
   *
   * Tracing
   */

  /**
   * Return the type object for a give object
   *
   * @param object The object whose type is required
   * @return The type object for <code>object</code>
   */
  public static MMType getObjectType(VM_Address object) 
    throws VM_PragmaInline {
    Object obj = VM_Magic.addressAsObject(object);
    Object[] tib = VM_ObjectModel.getTIB(obj);
    if (VM.VerifyAssertions) {
      if (tib == null || VM_ObjectModel.getObjectType(tib) != VM_Type.JavaLangObjectArrayType) {
	VM.sysWriteln("getObjectType: objRef = ", object, "   tib = ", VM_Magic.objectAsAddress(tib));
	VM.sysWriteln("               tib's type is not Object[]");
        VM._assert(false);
      }
    }
    VM_Type vmType = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    if (VM.VerifyAssertions) {
      if (vmType == null) {
        VM.sysWriteln("getObjectType: null type for object = ", object);
        VM._assert(false);
      }
    }
    if (VM.VerifyAssertions) VM._assert(vmType.getMMType() != null);
    return (MMType) vmType.getMMType();
  }

  /**
   * Delegated scanning of a object, processing each pointer field
   * encountered. <b>Jikes RVM never delegates, so this is never
   * executed</b>.
   *
   * @param object The object to be scanned.
   */
  public static void scanObject(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }
  
  /**
   * Delegated enumeration of the pointers in an object, calling back
   * to a given plan for each pointer encountered. <b>Jikes RVM never
   * delegates, so this is never executed</b>.
   *
   * @param object The object to be scanned.
   * @param enum the Enumerate object through which the callback
   * is made
   */
  public static void enumeratePointers(VM_Address object, Enumerate enum) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    // Never reached
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Prepares for using the <code>computeAllRoots</code> method.  The
   * thread counter allows multiple GC threads to co-operatively
   * iterate through the thread data structure (if load balancing
   * parallel GC threads were not important, the thread counter could
   * simply be replaced by a for loop).
   */
  public static void resetThreadCounter() {
    threadCounter.reset();
  }

  /**
   * Pre-copy all potentially movable instances used in the course of
   * GC.  This includes the thread objects representing the GC threads
   * themselves.  It is crucial that these instances are forwarded
   * <i>prior</i> to the GC proper.  Since these instances <i>are
   * not</i> enqueued for scanning, it is important that when roots
   * are computed the same instances are explicitly scanned and
   * included in the set of roots.  The existence of this method
   * allows the actions of calculating roots and forwarding GC
   * instances to be decoupled. The <code>threadCounter</code> must be
   * reset so that load balancing parallel GC can share the work of
   * scanning threads.
   */
  public static void preCopyGCInstances() {
    /* pre-copy all thread objects in parallel */
    if (rendezvous(4201) == 1) /* one thread forwards the threads object */
      enumeratePointers(VM_Scheduler.threads, preCopyEnum);
    rendezvous(4202);
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread != null) {
        enumeratePointers(thread, preCopyEnum);
        enumeratePointers(thread.contextRegisters, preCopyEnum);
        enumeratePointers(thread.hardwareExceptionRegisters, preCopyEnum);
        if (thread.jniEnv != null) {
          // Right now, jniEnv are Java-visible objects (not C-visible)
          // if (VM.VerifyAssertions)
          //   VM._assert(Plan.willNotMove(VM_Magic.objectAsAddress(thread.jniEnv)));
          enumeratePointers(thread.jniEnv, preCopyEnum);
        }
      }
    }    
    rendezvous(4203);
  }
 
  /**
   * Enumerate the pointers in an object, calling back to a given plan
   * for each pointer encountered. <i>NOTE</i> that only the "real"
   * pointer fields are enumerated, not the TIB.
   *
   * @param object The object to be scanned.
   * @param enum the Enumerate object through which the callback
   * is made
   */
  private static void enumeratePointers(Object object, Enumerate enum) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Scan.enumeratePointers(VM_Magic.objectAsAddress(object), enum);
  }

 /**
   * Computes all roots.  This method establishes all roots for
   * collection and places them in the root values, root locations and
   * interior root locations queues.  This method should not have side
   * effects (such as copying or forwarding of objects).  There are a
   * number of important preconditions:
   *
   * <ul> 
   * <li> All objects used in the course of GC (such as the GC thread
   * objects) need to be "pre-copied" prior to calling this method.
   * <li> The <code>threadCounter</code> must be reset so that load
   * balancing parallel GC can share the work of scanning threads.
   * </ul>
   *
   * @param rootLocations set to store addresses containing roots
   * @param interiorRootLocations set to store addresses containing
   * return adddresses, or <code>null</code> if not required
   */
  public static void computeAllRoots(AddressDeque rootLocations,
                                     AddressPairDeque interiorRootLocations) {
    AddressPairDeque codeLocations = MM_Interface.MOVES_OBJECTS ? interiorRootLocations : null;
    
     /* scan statics */
    ScanStatics.scanStatics(rootLocations);
 
    /* scan all threads */
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      
      VM_Thread thread = VM_Scheduler.threads[threadIndex];
      if (thread == null) continue;
      
      /* scan the thread (stack etc.) */
      ScanThread.scanThread(thread, rootLocations, codeLocations);

      /* identify this thread as a root */
      rootLocations.push(VM_Magic.objectAsAddress(VM_Scheduler.threads).add(threadIndex<<LOG_BYTES_IN_ADDRESS));
    }
    rendezvous(4200);
  }

  /***********************************************************************
   *
   * Copying
   */

  /**
   * Copy an object using a plan's allocCopy to get space and install
   * the forwarding pointer.  On entry, <code>fromObj</code> must have
   * been reserved for copying by the caller.  This method calls the
   * plan's <code>PostCopy</code> method after making the copy.
   *
   * @param fromObj the address of the object to be copied
   * @param forwardingPtr the value the forwarding pointer in the copy
   * is to be set to.  This value is first modified by the plan's
   * <code>resetGCBitsForCopy</code> method.  AJG: Not sure why this
   * value passed in, it seems that it could be simply copied from the
   * old object.
   * @return the address of the new object
   */
  public static VM_Address copy(VM_Address fromObj, VM_Word forwardingPtr)
    throws VM_PragmaInline {
    Object[] tib = VM_ObjectModel.getTIB(fromObj);

    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    Plan plan = getPlan();

    VM_Address toRef;
    if (type.isClassType()) {
      VM_Class classType = type.asClass();
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, classType);
      int align = VM_ObjectModel.getAlignment(classType, fromObj);
      int offset = VM_ObjectModel.getOffsetForAlignment(classType, fromObj);
      int rawSize = (align != BYTES_IN_ADDRESS) ? (numBytes + align) : numBytes;
      forwardingPtr = Plan.resetGCBitsForCopy(fromObj, forwardingPtr,numBytes);
      VM_Address region = plan.allocCopy(VM_Magic.objectAsAddress(fromObj), rawSize, true);
      if (align != BYTES_IN_ADDRESS) {
        // This code is based on some fancy modulo artihmetic.
        // It ensures the property (region + offset) % alignment == 0
        VM_Word mask  = VM_Word.fromIntSignExtend(align-1);
        VM_Word negOff= VM_Word.fromIntSignExtend(-offset);
        VM_Offset delta = negOff.sub(region.toWord()).and(mask).toOffset();
        region = region.add(delta);
      }
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      plan.postCopy(VM_Magic.objectAsAddress(toObj), tib, rawSize, true);
      toRef = VM_Magic.objectAsAddress(toObj);
      ((MMType) type.getMMType()).profileCopy(numBytes);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      int align = VM_ObjectModel.getAlignment(arrayType, fromObj);
      int offset = VM_ObjectModel.getOffsetForAlignment(arrayType, fromObj);
      int rawSize = (align != BYTES_IN_ADDRESS) ? (numBytes + align) : numBytes;
      forwardingPtr = Plan.resetGCBitsForCopy(fromObj, forwardingPtr,numBytes);
      VM_Address region = getPlan().allocCopy(VM_Magic.objectAsAddress(fromObj), rawSize, false);
      if (align != BYTES_IN_ADDRESS) {
        // This code is based on some fancy modulo artihmetic.
        // It ensures the property (region + offset) % alignment == 0
        VM_Word mask  = VM_Word.fromIntSignExtend(align-1);
        VM_Word negOff= VM_Word.fromIntSignExtend(-offset);
        VM_Offset delta = negOff.sub(region.toWord()).and(mask).toOffset();
        region = region.add(delta);
      }
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      plan.postCopy(VM_Magic.objectAsAddress(toObj), tib, rawSize, false);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.CodeArrayType) {
        // sync all moved code arrays to get icache and dcache in sync immediately.
        int dataSize = numBytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
        VM_Memory.sync(toRef, dataSize);
      }
      ((MMType) type.getMMType()).profileCopy(numBytes);
    }
    return toRef;

  }

  /***********************************************************************
   *
   * References
   */

  /**
   * Determine whether this reference has ever been enqueued.
   *
   * @param r the Reference object
   * @return <code>true</code> if reference has ever been enqueued
   */
  public static final boolean referenceWasEverEnqueued(Reference r) {
    return r.wasEverEnqueued();
  }

  /**
   * Put this Reference object on its ReferenceQueue (if it has one)
   * when its referent is no longer sufficiently reachable. The
   * definition of "reachable" is defined by the semantics of the
   * particular subclass of Reference. The implementation of this
   * routine is determined by the the implementation of
   * java.lang.ref.ReferenceQueue in GNU classpath. It is in this
   * class rather than the public Reference class to ensure that Jikes
   * has a safe way of enqueueing the object, one that cannot be
   * overridden by the application program.
   * 
   * @see java.lang.ref.ReferenceQueue
   * @param r the Reference object
   * @return <code>true</code> if the reference was enqueued
   */
  public static final boolean enqueueReference(Reference r) {
    return r.enqueue();
  }

  /***********************************************************************
   *
   * Miscellaneous
   */

  /**
   * Sets the range of addresses associated with a heap.
   *
   * @param id the heap identifier
   * @param start the address of the start of the heap
   * @param end the address of the end of the heap
   */
  public static void setHeapRange(int id, VM_Address start, VM_Address end)
    throws VM_PragmaUninterruptible {
    VM_BootRecord.the_boot_record.setHeapRange(id, start, end);
  }

  /**
   * Gets the plan associated with a processor.  Only used within the
   * <code>vmInterface</code> package.
   *
   * @param proc the processor
   * @return the plan for the processor
   */
  static Plan getPlanFromProcessor(VM_Processor proc) throws VM_PragmaInline {
    //-#if RVM_WITH_JMTK_INLINE_PLAN
    return proc;
    //-#else
    return proc.mmPlan;
    //-#endif
  }

  /**
   * Gets the plan associated with the current processor.
   *
   * @return the plan for the current processor
   */
  public static Plan getPlan() throws VM_PragmaInline {
    return getPlanFromProcessor(VM_Processor.getCurrentProcessor());
  }

  /**
   * Read cycle counter
   */
  public static long cycles() {
    return VM_Time.cycles();
  }

  /**
   * Convert cycles to milliseconds
   */
  public static double cyclesToMillis(long c) {
    return VM_Time.cyclesToMillis(c);
  }

  /**
   * Convert cycles to seconds
   */
  public static double cyclesToSecs(long c) {
    return VM_Time.cyclesToSecs(c);
  }

  /**
   * Convert milliseconds to cycles
   */
  public static long millisToCycles(double t) {
    return VM_Time.millisToCycles(t);
  }

  /**
   * Convert seconds to cycles
   */
  public static long secsToCycles(double t) {
    return VM_Time.secsToCycles(t);
  }

  /**
   * Returnt the size required to copy an object
   *
   * @param obj The object whose size is to be queried
   * @return The size required to copy <code>obj</code>
   */
  public static int getSizeWhenCopied(VM_Address obj) {
    VM_Type type = VM_Magic.objectAsType(VM_ObjectModel.getTIB(obj)[TIB_TYPE_INDEX]);
    if (type.isClassType())
      return VM_ObjectModel.bytesRequiredWhenCopied(obj, type.asClass());
    else
      return VM_ObjectModel.bytesRequiredWhenCopied(obj, type.asArray(), VM_Magic.getArrayLength(obj));
  }
  
  /*
   * Utilities from the VM class
   */

  /**
   * Checks that the given condition is true.  If it is not, this
   * method does a traceback and exits.
   *
   * @param cond the condition to be checked
   */
  public static void _assert(boolean cond) throws VM_PragmaInline {
    VM._assert(cond);
  }


  public static void _assert(boolean cond, String s) throws VM_PragmaInline {
    if (!cond) VM.sysWriteln(s);
    VM._assert(cond);
  }

  /**
   * Checks if the virtual machine is running.  This value changes, so
   * the call-through to the VM must be a method.  In Jikes RVM, just
   * returns VM.runningVM.
   *
   * @return <code>true</code> if the virtual machine is running
   */
  public static boolean runningVM() { return VM.runningVM; }


  /***********************************************************************
   *
   * Logging
   */

  /**
   * Logs a message and traceback, then exits.
   *
   * @param message the string to log
   */
  public static void sysFail(String message) { VM.sysFail(message); }

  public static void sysExit(int rc) throws VM_PragmaUninterruptible {
    VM.sysExit(rc);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param src the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  public static int copyStringToChars(String src, char [] dst,
                                      int dstBegin, int dstEnd)
    throws VM_PragmaLogicallyUninterruptible {
    if (runningVM())
      VM_Processor.getCurrentProcessor().disableThreadSwitching();
    int len = src.length();
    int n = (dstBegin + len <= dstEnd) ? len : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++) 
      setArrayNoBarrier(dst, dstBegin + i, src.charAt(i));
    if (runningVM())
      VM_Processor.getCurrentProcessor().enableThreadSwitching();
    return n;
  }


  /**
   * Sets an element of a char array without invoking any write
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a write barrier operation.
   *
   * @param dst the destination array
   * @param index the index of the element to set
   * @param value the new value for the element
   */
  public static void setArrayNoBarrier(char [] dst, int index, char value) {
    if (runningVM())
      VM_Magic.setCharAtOffset(dst, index << LOG_BYTES_IN_CHAR, value);
    else
      dst[index] = value;
  }

  /**
   * Gets an element of a char array without invoking any read
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a read barrier operation.
   *
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public static char getArrayNoBarrier(char [] src, int index) {
    if (runningVM())
      return VM_Magic.getCharAtOffset(src, index << LOG_BYTES_IN_CHAR);
    else
      return src[index];
  }

  /**
   * Gets an element of a byte array without invoking any read
   * barrier.  This method is called by the Log method, as it will be
   * used during garbage collection and needs to manipulate character
   * arrays without causing a read barrier operation.
   *
   * @param src the source array
   * @param index the index of the element to get
   * @return the new value of element
   */
  public static byte getArrayNoBarrier(byte [] src, int index) {
    if (runningVM())
      return VM_Magic.getByteAtOffset(src, index);
    else
      return src[index];
  }

  /**
   * Log a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public static void sysWrite(char [] c, int len) {
    VM.sysWrite(c, len);
  }

  /**
   * Log a thread identifier and a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  public static void sysWriteThreadId(char [] c, int len) {
    VM.psysWrite(c, len);
  }

  /**
   * Get the type descriptor for an object.
   *
   * @param ref address of the object
   * @return byte array with the type descriptor
   */
  public static byte [] getTypeDescriptor(VM_Address ref) {
    VM_Atom descriptor = VM_Magic.getObjectType(ref).getDescriptor();
    return descriptor.toByteArray();
  }

  /**
   * Get the long for an offset
   *
   * @param offset the offset
   * @return long for the offset
   */
//TODO: Delete, add sToLong to VM_Offset (signed to long)
  public static long offsetToLong(VM_Offset offset)
  {
    //-#if RVM_FOR_32_ADDR
    return offset.toInt();
    //-#elif RVM_FOR_64_ADDR
    return offset.toLong();
    //-#endif
  }

  /* Used in processing weak references etc */

  public static VM_Address getReferent (VM_Address addr) {
    return VM_Magic.getMemoryAddress(addr.add(VM_Entrypoints.referenceReferentField.getOffset()));    
  }
  
  public static void setReferent (VM_Address addr, VM_Address referent) {
    VM_Magic.setMemoryAddress(addr.add(VM_Entrypoints.referenceReferentField.getOffset()), referent);    
  }
  
  public static VM_Address getNextReferenceAsAddress (VM_Address ref) {
    return VM_Magic.getMemoryAddress(ref.add(VM_Entrypoints.referenceNextAsAddressField.getOffset()));
    
  }
  
  public static void setNextReferenceAsAddress (VM_Address ref, VM_Address next) {
    VM_Magic.setMemoryAddress(ref.add(VM_Entrypoints.referenceNextAsAddressField.getOffset()),
                              next);
    
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public static int rendezvous(int where) throws VM_PragmaUninterruptible {
    return VM_CollectorThread.gcBarrier.rendezvous(where);
  }

  /**
   * Primitive parsing facilities for strings
   */
  public static int primitiveParseInt(String value) throws VM_PragmaInterruptible {
    return VM_CommandLineArgs.primitiveParseInt(value);
  }
  public static float primitiveParseFloat(String value) throws VM_PragmaInterruptible {
    return VM_CommandLineArgs.primitiveParseInt(value);
  }

  /**
   * Throw an out of memory exception.
   */
  public static void failWithOutOfMemoryError()
    throws VM_PragmaLogicallyUninterruptible, VM_PragmaNoInline {
    throw new OutOfMemoryError();
  }


}
