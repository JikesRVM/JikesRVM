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
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
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
   *
   * This is called from MM_Interface, and would be just called init()
   * but for the pass-through methods at the bottom.
   *  // TODO: Rename to init() after the transition is over.
   */
  public static final void initVM_Interface () throws VM_PragmaInterruptible {
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
    VM_Address.fromInt
    //-#elif RVM_FOR_64_ADDR
    VM_Address.fromLong
    //-#endif
    (
     //-#value BOOTIMAGE_LOAD_ADDRESS
     );

  public static VM_Address MAXIMUM_MAPPABLE = 
    //-#if RVM_FOR_32_ADDR
    VM_Address.fromInt
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
  public static int JAVA_HEADER_END() {
    return VM_ObjectModel.JAVA_HEADER_END;
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
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    if (Plan.verbose >= 4) {
      VM.sysWriteln("Entered VM_Interface.triggerCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }
    if ((Plan.verbose == 1 || Plan.verbose == 2) 
	&& (why == EXTERNALLY_TRIGGERED_GC)) {
      VM.sysWrite("[Forced GC]");
    }
    if (Plan.verbose > 2) VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    long start = System.currentTimeMillis();
    VM_CollectorThread.collect(VM_CollectorThread.handshake);
    if (Plan.verbose > 2) VM.sysWriteln("Collection finished (ms): ", 
					(int) (System.currentTimeMillis() - start));

    if (Plan.isLastGCFull()) {
      int before = Options.getCurrentHeapSize(); 
      boolean heapGrew = Options.updateCurrentHeapSize((int) Plan.reservedMemory()); 
      if (heapGrew) { 
	if (Plan.verbose >= 2) { 
	  VM.sysWrite("Heap grew from ", (int) (before / 1024)); 
	  VM.sysWrite("KB to ", (int) (Options.getCurrentHeapSize() / 1024)); 
	  VM.sysWriteln("KB"); 
	} 
      } 
      else { 
        double usage = Plan.reservedMemory() / ((double) Plan.totalMemory());
	if (usage > OUT_OF_MEMORY_THRESHOLD) {
	if (why == INTERNALLY_TRIGGERED) {
	  if (Plan.verbose >= 2) {
	    VM.sysWriteln("Throwing OutOfMemoryError: usage = ", usage);
	    VM.sysWriteln("                           reserved (kb) = ", (int) (Plan.reservedMemory() / 1024));
	    VM.sysWriteln("                           total    (Kb) = ", (int) (Plan.totalMemory() / 1024));
	  }
	  if (VM.debugOOM || Plan.verbose >= 5)
	    VM.sysWriteln("triggerCollection(): About to try \"new OutOfMemoryError()\"");
	  OutOfMemoryError oome = new OutOfMemoryError();
	  if (VM.debugOOM || Plan.verbose >= 5)
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
    if (Plan.verbose >= 1) {
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

  public static int verbose() throws VM_PragmaUninterruptible {
    return VM_BootRecord.the_boot_record.verboseGC;
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
      if (arrayType == VM_Type.InstructionArrayType) {
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

  public static void sysWrite   (VM_Atom value)         { VM.sysWrite(value); }
  public static void sysWriteln (VM_Atom value)         { VM.sysWriteln(value); }
  public static void sysWrite   (VM_Word value)         { VM.sysWrite(value); }
  public static void sysWrite   (VM_Member m)           { VM.sysWrite(m);  }
  public static void sysWrite   (VM_MemberReference mr) { VM.sysWrite(mr);  }
  public static void sysWriteln ()                      { VM.sysWriteln(); }
  public static void sysWrite   (char c)                { VM.sysWrite(c);  }
  public static void sysWriteField(int w, int v)        { VM.sysWriteField(w,v); }
  public static void sysWriteField(int w, String s)     { VM.sysWriteField(w,s);  }
  public static void sysWriteHex(int value)             { VM.sysWriteHex(value);  }
  public static void sysWriteHex(VM_Address v)          { VM.sysWriteHex(v);  }
  public static void sysWriteInt(int v)                 { VM.sysWriteInt(v); }
  public static void sysWriteLong(long v)               { VM.sysWriteLong(v); }
  public static void sysWrite   (double d, int p)      { VM.sysWrite(d,p); }
  public static void sysWrite   (double d)             { VM.sysWrite(d); }
  public static void sysWrite   (String s)             { VM.sysWrite(s); }
  public static void sysWrite   (VM_Address addr)      { VM.sysWrite(addr); }
  public static void sysWriteln (VM_Address addr)      { VM.sysWriteln(addr); }
  public static void sysWrite   (boolean b)            { VM.sysWrite(b); }
  public static void sysWrite   (int i)                { VM.sysWrite(i); }
  public static void sysWriteln (int i)                { VM.sysWriteln(i); }
  public static void sysWriteln (double d)             { VM.sysWriteln(d); }
  public static void sysWriteln (long l)               { VM.sysWriteln(l); }
  public static void sysWriteln (boolean b)            { VM.sysWrite(b); }
  public static void sysWriteln (String s)             { VM.sysWriteln(s); }
  public static void sysWrite   (String s, int i)      { VM.sysWrite(s,i); }
  public static void sysWriteln (String s, int i)      { VM.sysWriteln(s,i); }
  public static void sysWrite   (String s, double d)   { VM.sysWrite(s,d); }
  public static void sysWriteln (String s, double d)   { VM.sysWriteln(s,d); }
  public static void sysWrite   (double d, String s)   { VM.sysWrite(d,s); }
  public static void sysWriteln (double d, String s)   { VM.sysWriteln(d,s); }
  public static void sysWrite   (String s, long i)     { VM.sysWrite(s); }
  public static void sysWriteln (String s, long i)     { VM.sysWriteln(s,i); }
  public static void sysWrite   (int i, String s)      { VM.sysWrite(i,s); }
  public static void sysWriteln (int i, String s)      { VM.sysWriteln(i,s); }
  public static void sysWrite   (String s1, String s2) { VM.sysWrite(s1,s2); }
  public static void sysWriteln (String s1, String s2) { VM.sysWriteln(s1,s2); }
  public static void sysWrite   (String s, VM_Address addr) { VM.sysWrite(s,addr);   }
  public static void sysWriteln (String s, VM_Address addr) { VM.sysWriteln(s,addr); }
  public static void sysWrite   (String s, VM_Offset addr) { VM.sysWrite(s,addr);   }
  public static void sysWriteln (String s, VM_Offset addr) { VM.sysWriteln(s,addr); }
  public static void sysWrite   (String s1, String s2, VM_Address a) { VM.sysWrite(s1,s2,a); }
  public static void sysWriteln (String s1, String s2, VM_Address a) { VM.sysWriteln(s1,s2,a); }
  public static void sysWrite   (String s1, String s2, int i)  { VM.sysWrite(s1,s2,i);}
  public static void sysWriteln (String s1, String s2, int i)  { VM.sysWriteln(s1,s2,i);  }
  public static void sysWrite   (String s1, int i, String s2)  { VM.sysWrite(s1,i,s2); }
  public static void sysWriteln (String s1, int i, String s2)  { VM.sysWriteln(s1,i,s2); }
  public static void sysWrite   (String s1, String s2, String s3)  { VM.sysWrite(s1,s2,s3); }
  public static void sysWriteln (String s1, String s2, String s3)  { VM.sysWriteln(s1,s2,s3); }
  public static void sysWrite   (int i1, String s, int i2)     { VM.sysWrite(i1,s,i2); }
  public static void sysWriteln (int i1, String s, int i2)     { VM.sysWriteln(i1,s,i2); }
  public static void sysWrite   (int i1, String s1, String s2) { VM.sysWrite(i1,s1,s2); }
  public static void sysWriteln (int i1, String s1, String s2) { VM.sysWriteln(i1,s1,s2); }
  public static void sysWrite   (String s1, int i1, String s2, int i2) { VM.sysWrite(s1,i1,s2,i2); }
  public static void sysWriteln (String s1, int i1, String s2, int i2) { VM.sysWriteln(s1,i1,s2,i2); }
  public static void sysWrite   (String s1, int i1, String s2, long l1) { VM.sysWrite(s1,i1,s2,l1); }
  public static void sysWriteln (String s1, int i1, String s2, long l1) { VM.sysWriteln(s1,i1,s2,l1); }
  public static void sysWrite   (String s1, double d, String s2)        { VM.sysWrite(s1,d,s2); }
  public static void sysWriteln (String s1, double d, String s2)        { VM.sysWriteln(s1,d,s2); }
  public static void sysWrite   (String s1, String s2, int i1, String s3) { VM.sysWrite(s1,s2,i1,s3); }
  public static void sysWriteln (String s1, String s2, int i1, String s3) { VM.sysWriteln(s1,s2,i1,s3); }
  public static void sysWrite   (String s1, String s2, String s3, int i1) { VM.sysWrite(s1,s2,s3,i1); }
  public static void sysWriteln (String s1, String s2, String s3, int i1) { VM.sysWriteln(s1,s2,s3,i1); }
  public static void sysWrite   (int i, String s1, double d, String s2) { VM.sysWrite(i,s1,d,s2); }
  public static void sysWriteln (int i, String s1, double d, String s2) { VM.sysWriteln(i,s1,d,s2); }
  public static void sysWrite   (String s1, String s2, String s3, int i1, String s4) { VM.sysWrite(s1,s2,s3,i1,s4);  }
  public static void sysWriteln (String s1, String s2, String s3, int i1, String s4) { VM.sysWriteln(s1,s2,s3,i1,s4);  }
  public static void sysWrite   (String s1, VM_Address a1, String s2, VM_Address a2) { VM.sysWrite(s1,a1,s2,a2);  }
  public static void sysWriteln (String s1, VM_Address a1, String s2, VM_Address a2) { VM.sysWriteln(s1,a1,s2,a2);  }



  public static void ptsysWriteln (String s) { 
    VM.ptsysWriteln(s);
  }
  public static void psysWriteln (VM_Address a) { 
    VM.psysWriteln(a); 
  }
  public static void psysWriteln (String s) { 
    VM.psysWriteln(s); 
  }
  public static void psysWriteln (String s, int i) { 
    VM.psysWriteln(s,i); 
  }
  public static void psysWriteln (String s, VM_Address a) { 
    VM.psysWriteln(s,a); 
  }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2)  { 
    VM.psysWriteln(s1,a1,s2,a2); 
  }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3) {  
    VM.psysWriteln(s1,a1,s2,a2,s3,a3); 
  }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4) { 
    VM.psysWriteln(s1,a1,s2,a2,s3,a3,s4,a4); 
  }
  public static void psysWriteln   (String s1, VM_Address a1, String s2, VM_Address a2, String s3, VM_Address a3, String s4, VM_Address a4, String s5, VM_Address a5) { 
    VM.psysWriteln(s1,a1,s2,a2,s3,a3,s4,a4,s5,a5); 
  }

  /*
   * This value changes, so call-through to VM must be a method
   */
  public static boolean runningVM() { return VM.runningVM; }

  public static boolean isAcyclic(Object[] tib) {
    return VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]).isAcyclicReference();
  }
 
}
