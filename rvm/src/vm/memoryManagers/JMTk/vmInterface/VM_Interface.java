/*
 * (C) Copyright IBM Corp. 2001
 *
 * VM_Interface.java: methods that JMTk requires to interface with its 
 * enclosing run-time environment. 
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import java.util.Date;
import java.lang.ref.Reference;

import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressPairQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.SynchronizedCounter;
import com.ibm.JikesRVM.memoryManagers.JMTk.Finalizer;
import com.ibm.JikesRVM.memoryManagers.JMTk.ReferenceProcessor;
import com.ibm.JikesRVM.memoryManagers.JMTk.Options;
import com.ibm.JikesRVM.memoryManagers.JMTk.HeapGrowthManager;

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

/*
 * @author Perry Cheng  
 */  

public class VM_Interface implements VM_Constants, VM_Uninterruptible {

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The value of
   * statics as at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   */
  public static final void init () throws VM_PragmaInterruptible {
    collectorThreadAtom = VM_Atom.findOrCreateAsciiAtom(
      "Lcom/ibm/JikesRVM/memoryManagers/vmInterface/VM_CollectorThread;");
    runAtom = VM_Atom.findOrCreateAsciiAtom("run");
  }

  /***********************************************************************
   *
   * What we need to know about memory allocated by the outside world
   *
   * Basically where the boot image is, and how much memory is available.
   */

  public static VM_Address bootImageStart() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageStart;
  }

  public static VM_Address bootImageEnd() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageEnd;
  }

  public static final VM_Address bootImageAddress = 
    //-#if RVM_FOR_32_ADDR
    VM_Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    VM_Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_LOAD_ADDRESS
     );

  public static VM_Address MAXIMUM_MAPPABLE = 
    //-#if RVM_FOR_32_ADDR
    VM_Address.fromIntZeroExtend
    //-#elif RVM_FOR_64_ADDR
    VM_Address.fromLong
    //-#endif
    (
     //-#value MAXIMUM_MAPPABLE_ADDRESS
     );

  /***********************************************************************
   *
   * Manipulate raw memory
   */

  /* Returns errno 
   */
  public static int mmap(VM_Address start, int size) {
    VM_Address result = VM_Memory.mmap(start, size,
				       VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC, 
				       VM_Memory.MAP_PRIVATE | VM_Memory.MAP_FIXED | VM_Memory.MAP_ANONYMOUS);
    if (result.EQ(start)) return 0;
    if (result.GT(VM_Address.fromInt(127))) {
      VM.sysWrite("mmap with MAP_FIXED on ", start);
      VM.sysWriteln(" returned some other address", result);
      VM.sysFail("mmap with MAP_FIXED has unexpected behavior");
    }
    return result.toInt();
  }
  
  public static boolean mprotect(VM_Address start, int size) {
    return VM_Memory.mprotect(start, size,VM_Memory.PROT_NONE);
  }

  public static boolean munprotect(VM_Address start, int size) {
    return VM_Memory.mprotect(start, size,
			      VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC);
  }

  /**
   * Zero a region of memory.
   * Taken:    start of address range (inclusive)
   *           length in bytes of range to zero
   * Returned: nothing
   */
  public static void zero(VM_Address start, VM_Extent len) {
    VM_Memory.zero(start,len);
  }

  /**
   * Zero a range of pages of memory.
   * Taken:    start address       (must be a page address)
   *           number of bytes     (must be multiple of page size)
   * Returned: nothing
   */
  public static void zeroPages(VM_Address start, int len) {
    VM_Memory.zeroPages(start,len);
  }

  public static void dumpMemory(VM_Address start, int beforeBytes, int afterBytes) {
    VM_Memory.dumpMemory(start,beforeBytes,afterBytes);
  }

  /***********************************************************************
   *
   * Access to object model
   */

  /**
   * Call-throughs to VM_ObjectModel
   */

  public static boolean testAvailableBit(VM_Address o, int idx) {
    return VM_ObjectModel.testAvailableBit(VM_Magic.addressAsObject(o),idx);
  }
  public static void setAvailableBit(VM_Address o, int idx, boolean flag) {
    VM_ObjectModel.setAvailableBit(VM_Magic.addressAsObject(o),idx,flag);
  }
  public static boolean attemptAvailableBits(VM_Address o, int oldVal, int newVal) {
      return VM_ObjectModel.attemptAvailableBits(VM_Magic.addressAsObject(o),oldVal,newVal);
  }
  public static int prepareAvailableBits(VM_Address o) {
      return VM_ObjectModel.prepareAvailableBits(VM_Magic.addressAsObject(o));
  }
  public static void writeAvailableBitsWord(VM_Address o, int val) {
    VM_ObjectModel.writeAvailableBitsWord(VM_Magic.addressAsObject(o), val);
  }
  public static int readAvailableBitsWord(VM_Address o) {
      return VM_ObjectModel.readAvailableBitsWord(o);
  }
  public static int GC_HEADER_OFFSET() {
    return VM_ObjectModel.GC_HEADER_OFFSET;
  }
  public static VM_Address objectStartRef(VM_Address object) throws VM_PragmaInline {
    return VM_ObjectModel.objectStartRef(object);
  }
  public static VM_Address refToAddress(VM_Address obj) {
    return VM_ObjectModel.getPointerInMemoryRegion(obj);
  }


  /***********************************************************************
   *
   * Trigger collections
   */

  public static final int EXTERNALLY_TRIGGERED_GC = 0;
  public static final int RESOURCE_TRIGGERED_GC = 1;
  public static final int INTERNALLY_TRIGGERED = 2;
  public static final int TRIGGER_REASONS = 3;
  private static final String[] triggerReasons = {
    "external request",
    "resource exhaustion",
    "internal request"
  };

  public static final void triggerCollection(int why)
    throws VM_PragmaInterruptible {
    Plan.collectionInitiated();

    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    if (Options.verbose >= 4) {
      VM.sysWriteln("Entered VM_Interface.triggerCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }
    if ((Options.verbose == 1 || Options.verbose == 2) 
	&& (why == EXTERNALLY_TRIGGERED_GC)) {
      VM.sysWrite("[Forced GC]");
    }
    if (Options.verbose > 2) VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    double start = VM_Time.now();
    VM_CollectorThread.collect(VM_CollectorThread.handshake);
    double end = VM_Time.now();
    double gcTime = end - start;
    HeapGrowthManager.recordGCTime(gcTime);
    if (Options.verbose > 2) VM.sysWriteln("Collection finished (ms): ", VM_Time.toMilliSecs(gcTime));
    
    if (Plan.isLastGCFull()) {
      boolean heapSizeChanged = false;
      if (Options.variableSizeHeap && why != EXTERNALLY_TRIGGERED_GC) {
	// Don't consider changing the heap size if gc was forced by System.gc()
	heapSizeChanged = HeapGrowthManager.considerHeapSize();
      }
      HeapGrowthManager.reset();
      if (!heapSizeChanged) {
        double usage = Plan.reservedMemory() / ((double) Plan.totalMemory());
	if (usage > OUT_OF_MEMORY_THRESHOLD) {
	if (why == INTERNALLY_TRIGGERED) {
	  if (Options.verbose >= 2) {
	    VM.sysWriteln("OutOfMemoryError: usage = ", usage);
	    VM.sysWriteln("          reserved (kb) = ", (int) (Plan.reservedMemory() / 1024));
	    VM.sysWriteln("          total    (Kb) = ", (int) (Plan.totalMemory() / 1024));
	  }
	  if (VM.debugOOM || Options.verbose >= 5)
	    VM.sysWriteln("triggerCollection(): About to try \"new OutOfMemoryError()\"");
	  OutOfMemoryError oome = new OutOfMemoryError();
	  if (VM.debugOOM || Options.verbose >= 5)
	    VM.sysWriteln("triggerCollection(): Allocated the new OutOfMemoryError().");
	  throw oome;
	}
	ReferenceProcessor.setClearSoftReferences(true); // clear all possible reference objects
	triggerCollection(INTERNALLY_TRIGGERED);
	}
      }
    } // isLastGCFull
  }

  public static final void triggerAsyncCollection()
    throws VM_PragmaUninterruptible {
    if (Options.verbose >= 1) {
      VM.sysWrite("[Async GC]");
    }
    VM_CollectorThread.asyncCollect(VM_CollectorThread.handshake);
  }

  /***********************************************************************
   *
   * Miscellaneous
   */

  final public static double OUT_OF_MEMORY_THRESHOLD = 0.98;  // throw OutOfMemoryError when usage after a GC exceeds threshold

  public static void setHeapRange(int id, VM_Address start, VM_Address end) throws VM_PragmaUninterruptible {
    VM_BootRecord.the_boot_record.setHeapRange(id, start, end);
  }

  //
  // Only used within the vmInterface package
  static Plan getPlanFromProcessor(VM_Processor proc) throws VM_PragmaInline {
    //-#if RVM_WITH_JMTK_INLINE_PLAN
    return proc;
    //-#else
    return proc.mmPlan;
    //-#endif
  }


  public static Plan getPlan() throws VM_PragmaInline {
    return getPlanFromProcessor(VM_Processor.getCurrentProcessor());
  }

  // Schedule the finalizerThread, if there are objects to be finalized
  // and the finalizerThread is on its queue (ie. currently idle).
  // Should be called at the end of GC after moveToFinalizable has been called,
  // and before mutators are allowed to run.
  //
  public static void scheduleFinalizerThread () throws VM_PragmaUninterruptible {
    int finalizedCount = Finalizer.countToBeFinalized();
    boolean alreadyScheduled = VM_Scheduler.finalizerQueue.isEmpty();
    if (finalizedCount > 0 && !alreadyScheduled) {
      VM_Thread t = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(t);
    }
  }

  private static SynchronizedCounter threadCounter = new SynchronizedCounter();
  public static void resetComputeAllRoots() {
    threadCounter.reset();
  }
  public static void computeAllRoots(AddressQueue rootLocations,
				     AddressPairQueue interiorRootLocations) {
    AddressPairQueue codeLocations = MM_Interface.MOVES_OBJECTS ? interiorRootLocations : null;

    ScanStatics.scanStatics(rootLocations);
    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      VM_Thread th = VM_Scheduler.threads[threadIndex];
      if (th == null) continue;
      // See comment of ScanThread.scanThread
      //
      VM_Address thAddr = VM_Magic.objectAsAddress(th);
      VM_Thread th2 = VM_Magic.addressAsThread(Plan.traceObject(thAddr, true));
      if (VM_Magic.objectAsAddress(th2).EQ(thAddr))
	ScanObject.rootScan(th);
      ScanObject.rootScan(th.stack);
      if (th.jniEnv != null) {
	ScanObject.rootScan(th.jniEnv);
	ScanObject.rootScan(th.jniEnv.refsArray());
      }
      ScanObject.rootScan(th.contextRegisters);
      ScanObject.rootScan(th.contextRegisters.gprs);
      ScanObject.rootScan(th.hardwareExceptionRegisters);
      ScanObject.rootScan(th.hardwareExceptionRegisters.gprs);
      ScanThread.scanThread(th2, rootLocations, codeLocations);
    }
    ScanObject.rootScan(VM_Magic.objectAsAddress(VM_Scheduler.threads));
    VM_CollectorThread.gcBarrier.rendezvous();
  }

  public static boolean isNonParticipating (Plan plan) {
    VM_Processor vp = (VM_Processor) plan;
    int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
    return  ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT));
  }


  // The collector threads of processors currently running threads off in JNI-land cannot run.
  //
  public static void prepareNonParticipating (Plan p) {
    VM_Processor vp = (VM_Processor) p;
    int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
    if (VM.VerifyAssertions)
      VM._assert((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT));
    if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) { 
      // processor & its running thread are blocked in C for this GC.  
      // Its stack needs to be scanned, starting from the "top" java frame, which has
      // been saved in the running threads JNIEnv.  Put the saved frame pointer
      // into the threads saved context regs, which is where the stack scan starts.
      //
      VM_Thread t = vp.activeThread;
      t.contextRegisters.setInnermost(VM_Address.zero(), t.jniEnv.topJavaFP());
    }
  }

  private static VM_Atom collectorThreadAtom;
  private static VM_Atom runAtom;

    // Set a collector thread's so that a scan of its stack
    // will start at VM_CollectorThread.run
    //
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



  public static double now() {
    return VM_Time.now();
  }


  public static int getSizeWhenCopied(VM_Address obj) {
    VM_Type type = VM_Magic.objectAsType(VM_ObjectModel.getTIB(obj)[TIB_TYPE_INDEX]);
    if (type.isClassType())
      return VM_ObjectModel.bytesRequiredWhenCopied(obj, type.asClass());
    else
      return VM_ObjectModel.bytesRequiredWhenCopied(obj, type.asArray(), VM_Magic.getArrayLength(obj));
  }

  // Copy an object using a plan's allocCopy to get space and install the forwarding pointer.
  // On entry, "obj" must have been reserved for copying by the caller.
  //
  public static VM_Address copy (VM_Address fromObj, int forwardingPtr) throws VM_PragmaInline {

    Object[] tib = VM_ObjectModel.getTIB(fromObj);

    VM_Type type = VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]);
    Plan plan = getPlan();

    VM_Address toRef;
    if (type.isClassType()) {
      VM_Class classType = type.asClass();
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, classType);
      forwardingPtr = Plan.resetGCBitsForCopy(fromObj, forwardingPtr,numBytes);
      VM_Address region = plan.allocCopy(VM_Magic.objectAsAddress(fromObj), numBytes, true);
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      plan.postCopy(VM_Magic.objectAsAddress(toObj), tib, numBytes, true);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      forwardingPtr = Plan.resetGCBitsForCopy(fromObj, forwardingPtr,numBytes);
      VM_Address region = getPlan().allocCopy(VM_Magic.objectAsAddress(fromObj), numBytes, false);
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      plan.postCopy(VM_Magic.objectAsAddress(toObj), tib, numBytes, false);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.CodeArrayType) {
	// sync all moved code arrays to get icache and dcache in sync immediately.
	int dataSize = numBytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
	VM_Memory.sync(toRef, dataSize);
      }
    }
    return toRef;

  }

  /**
     Determine whether this reference has ever been enqueued.
     @param r the Reference object
     @return <code>true</codeif reference has ever been enqueued
   */
  public static final boolean referenceWasEverEnqueued(Reference r) {
    return r.wasEverEnqueued();
  }

  /**
     Put this Reference object on its ReferenceQueue (if it has one)
     when its referent is no longer sufficiently reachable. The
     definition of "reachable" is defined by the semantics of the
     particular subclass of Reference. The implementation of this
     routine is determined by the the implementation of
     java.lang.ref.ReferenceQueue in GNU classpath. It is in this
     class rather than the public Reference class to ensure that Jikes
     has a safe way of enqueueing the object, one that cannot be
     overridden by the application program.
     
     @see java.lang.ref.ReferenceQueue
     @param r the Reference object
     @return <code>true</codeif the reference was enqueued
   */
  public static final boolean enqueueReference(Reference r) {
    return r.enqueue();
  }

  /*
   * Utilities from VM
   */
  public static final boolean VerifyAssertions = VM.VerifyAssertions;
  public static void _assert(boolean cond) throws VM_PragmaInline {
    VM._assert(cond);
  }
  public static void sysFail(String message) { VM.sysFail(message); }

  public static void sysExit(int rc) throws VM_PragmaUninterruptible {
    VM.sysExit(rc);
  }

  /**
   * Copies characters from the string into the character array.
   *
   * @param src the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of charactes copied.
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
   * Get the long for an address
   *
   * @param addr the address
   * @return long for the address
   */
  public static long addressToLong(VM_Address addr)
  {
    //-#if RVM_FOR_32_ADDR
    return addr.toInt();
    //-#elif RVM_FOR_64_ADDR
    return addr.toLong();
    //-#endif
  }


  /**
   * Get the long for an offset
   *
   * @param offset the offset
   * @return long for the offset
   */
  public static long offsetToLong(VM_Offset offset)
  {
    //-#if RVM_FOR_32_ADDR
    return offset.toInt();
    //-#elif RVM_FOR_64_ADDR
    return offset.toLong();
    //-#endif
  }

  /*
   * This value changes, so call-through to VM must be a method
   */
  public static boolean runningVM() { return VM.runningVM; }

  public static boolean isAcyclic(Object[] tib) {
    return VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]).isAcyclicReference();
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
   * Rendezvous with all other processors, returning the rank,
   * ie the order this processor arrived at the barrier.
   */
  public static int rendezvous() throws VM_PragmaUninterruptible {
    return VM_CollectorThread.gcBarrier.rendezvous();
  }

}
