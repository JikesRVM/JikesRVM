/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import java.util.Date;

import com.ibm.JikesRVM.memoryManagers.JMTk.VMResource;
import com.ibm.JikesRVM.memoryManagers.JMTk.Plan;
import com.ibm.JikesRVM.memoryManagers.JMTk.Options;
import com.ibm.JikesRVM.memoryManagers.JMTk.Statistics;
// import com.ibm.JikesRVM.memoryManagers.JMTk.WorkQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.Memory;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.AddressPairQueue;
import com.ibm.JikesRVM.memoryManagers.JMTk.SynchronizedCounter;
import com.ibm.JikesRVM.memoryManagers.JMTk.Finalizer;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_StackframeLayoutConstants;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_DynamicLibrary;

/*
 * @author Perry Cheng  
 */  

public class VM_Interface implements VM_Constants, VM_Uninterruptible {

  final public static boolean CHECK_MEMORY_IS_ZEROED = false;
  private static int junk = 0;

  // time in seconds
  //
  public static void busyWait(double time) {
    double start = VM_Time.now();
    do {
      // Wait for abuot 50000 cycles (about 20 micro-seconds on a 1Ghz processor)
      // to avoid too many system calls.
      for (int i=0; i<10000; i++)
	junk++;
    } while ((VM_Time.now() - start) < time);
  }


  public static void logGarbageCollection() throws VM_PragmaUninterruptible {
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
  }

  public static VM_Address malloc(int size) throws VM_PragmaUninterruptible {
    return VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP, size));
  }

  public static void free(VM_Address addr) throws VM_PragmaUninterruptible {
    VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, addr.toInt());
  }



  /**
   * Initialization that occurs at <i>build</i> time.  The value of
   * statics as at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is the entry point for all build-time activity in the collector.
   */
  public static final void init () throws VM_PragmaInterruptible {
    VM_CollectorThread.init();
    collectorThreadAtom = VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/memoryManagers/vmInterface/VM_CollectorThread;");
    runAtom = VM_Atom.findOrCreateAsciiAtom("run");
  }


  public static Object[] tibForArrayType;
  public static Object[] tibForClassType;
  public static Object[] tibForPrimitiveType;

  /**
   * Initialization that occurs at <i>boot</i> time (runtime
   * initialization).  This is only executed by one processor (the
   * primordial thread).
   */
  public static final void boot (VM_BootRecord theBootRecord) throws VM_PragmaInterruptible {
    int pageSize = VM_Memory.getPagesize();  // Cannot be determined at init-time
    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType(10);
    tibForArrayType = VM_ObjectModel.getTIB(t);
    tibForPrimitiveType = VM_ObjectModel.getTIB(VM_Type.IntType);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_ObjectModel.getTIB(t);
    Plan.boot();
    VMResource.boot();
    Statistics.boot();
    synchronizedCounterOffset = VM_Entrypoints.synchronizedCounterField.getOffset();
    Monitor.boot();
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
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg) throws VM_PragmaInterruptible {
    Options.process(arg);
  }

  public static int numProcessors() throws VM_PragmaUninterruptible {
    return VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP);
  }

  public static int verbose() throws VM_PragmaUninterruptible {
    return VM_BootRecord.the_boot_record.verboseGC;
  }

  public static void lowYield() {
    VM.sysCall0(VM_BootRecord.the_boot_record.sysVirtualProcessorYieldIP);
  }

  public static int smallHeapSize() throws VM_PragmaUninterruptible {
    return VM_BootRecord.the_boot_record.smallSpaceSize;
  }

  public static int largeHeapSize() throws VM_PragmaUninterruptible {
    return VM_BootRecord.the_boot_record.largeSpaceSize;
  }

  public static int nurseryHeapSize() throws VM_PragmaUninterruptible {
    return VM_BootRecord.the_boot_record.nurserySize;
  }

  public static VM_Address bootImageStart() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageStart;
  }

  public static VM_Address bootImageEnd() throws VM_PragmaUninterruptible {
    return  VM_BootRecord.the_boot_record.bootImageEnd;
  }

  public static void setHeapRange(int id, VM_Address start, VM_Address end) throws VM_PragmaUninterruptible {
    VM_BootRecord.the_boot_record.setHeapRange(id, start, end);
  }

  public static Plan getPlan() throws VM_PragmaInline {
    return getPlanFromProcessor(VM_Processor.getCurrentProcessor());
  }

  public static Plan getPlanFromProcessor(VM_Processor proc) throws VM_PragmaInline {
    //-#if RVM_WITH_JMTK_INLINE_PLAN
    return proc;
    //-#else
    return proc.mmPlan;
    //-#endif
  }

  public static void putfieldWriteBarrier(Object ref, int offset, Object value)
    throws VM_PragmaInline {
    getPlan().putFieldWriteBarrier(VM_Magic.objectAsAddress(ref), offset,
				   VM_Magic.objectAsAddress(value));
  }
  
  public static void putstaticWriteBarrier(int offset, Object value)
    throws VM_PragmaInline { 
    VM_Address jtocSlot = VM_Magic.objectAsAddress(VM_Magic.getJTOC()).add(offset);
    getPlan().putStaticWriteBarrier(jtocSlot, VM_Magic.objectAsAddress(value));
  }

  public static void arrayStoreWriteBarrier(Object ref, int index,Object value)
    throws VM_PragmaInline {
    getPlan().arrayStoreWriteBarrier(VM_Magic.objectAsAddress(ref), index,
				     VM_Magic.objectAsAddress(value));
  }

  public static void arrayCopyWriteBarrier(Object ref, int startIndex,
					   int endIndex)
    throws VM_PragmaInline {
    getPlan().arrayCopyWriteBarrier(VM_Magic.objectAsAddress(ref), startIndex,
				    endIndex);
  }

  public static void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    getPlan().arrayCopyRefCountWriteBarrier(src, tgt);
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
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public static final int collectionCount() throws VM_PragmaUninterruptible {
    return VM_CollectorThread.collectionCount;
  }
  
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
   * External call to force a garbage collection.
   */
  public static final void gc() throws VM_PragmaInterruptible {
    Statistics.gcExternalCount++;
    triggerCollection(EXTERNALLY_TRIGGERED_GC);
  }

  public static final int EXTERNALLY_TRIGGERED_GC = 0;
  public static final int RESOURCE_TRIGGERED_GC = 1;
  public static final int TRIGGER_REASONS = 2;
  private static final String[] triggerReasons = {
    "external request",
    "resource exhaustion"
  };
  public static final void triggerCollection(int why)
    throws VM_PragmaInterruptible {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    // VM_Scheduler.dumpStack();
    if ((Plan.verbose == 1) && (why == EXTERNALLY_TRIGGERED_GC)) {
      VM.sysWrite("[Forced GC]");
    }
    if (Plan.verbose > 2) VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    long start = System.currentTimeMillis();
    VM_CollectorThread.collect(VM_CollectorThread.handshake);
    if (Plan.verbose > 2) VM.sysWriteln("Collection finished (ms): ", 
					(int) (System.currentTimeMillis() - start));
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
  public static final void setupProcessor(VM_Processor proc) throws VM_PragmaInterruptible {
    // if (proc.mmPlan == null) proc.mmPlan = new Plan();
    // if (VM.VerifyAssertions) VM._assert(proc.mmPlan != null);
  }

  public static final boolean NEEDS_WRITE_BARRIER = Plan.needsWriteBarrier;
  public static final boolean NEEDS_RC_WRITE_BARRIER = Plan.needsRefCountWriteBarrier;
  public static final boolean MOVES_OBJECTS = Plan.movesObjects;
  public static final boolean RC_CYCLE_DETECTION = Plan.refCountCycleDetection;
  public static boolean useMemoryController = false;

  public static final int bootImageAddress = 
  //-#value BOOTIMAGE_LOAD_ADDRESS
  ;

    /*
  public static void checkBootImageAddress (int addr) {
    if (bootImageAddress != addr) {
      VM.sysWriteln("checkBootImageAddress detected mismatch");
      VM.sysWriteln("  bootImageAddress = ", bootImageAddress);
      VM.sysWriteln("  addr             = ", addr);
      VM._assert(false);
    }
  }
    */
  public static void setWorkBufferSize (int size) {
      // WorkQueue.WORK_BUFFER_SIZE = 4 * size;
  }

  public static void dumpRef(VM_Address ref) throws VM_PragmaUninterruptible {
    Util.dumpRef(ref);
  }

  public static boolean validRef(VM_Address ref) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return Util.validRef(ref);
  }

  public static boolean addrInVM(VM_Address address) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.addrInVM(address);
  }

  public static boolean refInVM(VM_Address ref) throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VMResource.refInVM(ref);
  }

  public static int getMaxHeaps() {
    return VMResource.getMaxVMResource();
  }

  public static int pickAllocator(VM_Type type) throws VM_PragmaInterruptible {
    Type t = type.JMTKtype;
    if (t.initialized)
      return t.allocator;
    int allocator = Plan.DEFAULT_SPACE;
    String s = type.toString();
    if (s.startsWith("com.ibm.JikesRVM.memoryManagers.") ||
	s.equals("com.ibm.JikesRVM.VM_Processor"))
      allocator = Plan.IMMORTAL_SPACE;
    t.initialized = true;
    t.allocator = allocator;
    return allocator;
  }

  public static Object allocateScalar(int size, Object [] tib, int allocator) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Plan plan = getPlan();
    AllocAdvice advice = plan.getAllocAdvice(null, size, null, null);
    VM_Address region = plan.alloc(size, true, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) VM._assert(Memory.assertIsZeroed(region, size));
    Object result = VM_ObjectModel.initializeScalar(region, tib, size);
    plan.postAlloc(result, tib, size, true, allocator);
    return result;
  }

  public static Object allocateArray(int numElements, int size, Object [] tib, int allocator) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    size = (size + 3) & (~3);
    Plan plan = getPlan();
    AllocAdvice advice = plan.getAllocAdvice(null, size, null, null);
    VM_Address region = plan.alloc(size, false, allocator, advice);
    if (CHECK_MEMORY_IS_ZEROED) VM._assert(Memory.assertIsZeroed(region, size));
    Object result = VM_ObjectModel.initializeArray(region, tib, numElements, size);
    plan.postAlloc(result, tib, size, false, allocator);
    return result;
  }

  public static VM_Address allocateCopy(VM_Address object) throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM.sysWriteln("allocateCopy unimplmented");
    if (VM.VerifyAssertions) VM._assert(false); // unimplemented
    return VM_Address.zero();     // getPlan().allocCopy()...  FIXME
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

  public static void addFinalizer(Object obj) throws VM_PragmaInterruptible {
    Finalizer.addCandidate(obj);
  }

  public static Object getFinalizedObject () throws VM_PragmaInterruptible {
    return Finalizer.get();
  }

  public static VM_Address processPtrValue (VM_Address obj) throws VM_PragmaUninterruptible, VM_PragmaInline { 
    return Plan.traceObject(obj);
  }

  public static void processPtrField (VM_Address location) throws VM_PragmaUninterruptible, VM_PragmaInline { 
    VM.sysWriteln("processPtrField(LVM_Address;)V unimplmented");
    if (VM.VerifyAssertions) VM._assert(false); // unimplemented
  }

  public static void processPtrField (VM_Address location, boolean root) throws VM_PragmaUninterruptible, VM_PragmaInline { 
    Plan.traceObjectLocation(location, root);
  }

  public static boolean isLive(VM_Address obj) throws VM_PragmaInline {
    return Plan.isLive(obj);
  }

  public static void showPlans() {
    for (int i=0; i<VM_Scheduler.processors.length; i++) {
      VM_Processor p = VM_Scheduler.processors[i];
      if (p == null) continue;
      VM.sysWrite(i, ": ");
      getPlanFromProcessor(p).show();
    }
  }
  static SynchronizedCounter threadCounter = new SynchronizedCounter();
  public static void resetComputeAllRoots() {
    threadCounter.reset();
  }
  public static void computeAllRoots(AddressQueue rootLocations,
				     AddressPairQueue interiorRootLocations) {
    AddressPairQueue codeLocations = MOVES_OBJECTS ? interiorRootLocations : null;

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
	ScanObject.rootScan(thAddr);
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.stack));
      if (th.jniEnv != null) {
	ScanObject.rootScan(VM_Magic.objectAsAddress(th.jniEnv));
	ScanObject.rootScan(VM_Magic.objectAsAddress(th.jniEnv.JNIRefs));
      }
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.contextRegisters));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.contextRegisters.gprs));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.hardwareExceptionRegisters));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.hardwareExceptionRegisters.gprs));
      ScanThread.scanThread(th2, rootLocations, codeLocations);
    }
    ScanObject.rootScan(VM_Magic.objectAsAddress(VM_Scheduler.threads));
    VM_CollectorThread.gcBarrier.rendezvous();
  }

  // The collector threads of processors currently running threads off in JNI-land cannot run.
  //
  public static void prepareNonParticipating() {
    // include NativeDaemonProcessor in following loop over processors
    for (int i = 1; i <= VM_Scheduler.numProcessors+1; i++) {
      VM_Processor vp = VM_Scheduler.processors[i];
      if (vp == null) continue;   // the last VP (nativeDeamonProcessor) may be null
      int vpStatus = VM_Processor.vpStatus[vp.vpStatusIndex];
      if ((vpStatus == VM_Processor.BLOCKED_IN_NATIVE) || (vpStatus == VM_Processor.BLOCKED_IN_SIGWAIT)) {
	if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) { 
	  // processor & its running thread are blocked in C for this GC.  
	  // Its stack needs to be scanned, starting from the "top" java frame, which has
	  // been saved in the running threads JNIEnv.  Put the saved frame pointer
	  // into the threads saved context regs, which is where the stack scan starts.
	  //
	  VM_Thread t = vp.activeThread;
	  t.contextRegisters.setInnermost(VM_Address.zero(), t.jniEnv.JNITopJavaFP);
	}
	getPlanFromProcessor(vp).prepareNonParticipating();
      }
    }
  }

  public static boolean fullyInitialized() {
    return VM_Scheduler.allProcessorsInitialized;
  }

  private static VM_Atom collectorThreadAtom;
  private static VM_Atom runAtom;

    // Set a collector thread's so that a scan of its stack
    // will start at VM_CollectorThread.run
    //
  public static void prepareParticipating() {
    VM_Thread t = VM_Thread.getCurrentThread();
    VM_Address fp = VM_Magic.getFramePointer();
    while (true) {
      VM_Address caller_ip = VM_Magic.getReturnAddress(fp);
      VM_Address caller_fp = VM_Magic.getCallerFramePointer(fp);
      if (VM_Magic.getCallerFramePointer(caller_fp).EQ(VM_Address.fromInt(STACKFRAME_SENTINAL_FP))) 
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


  public static void collect () {
    getPlan().collect();
  }

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


  public static VM_Address refToAddress(VM_Address obj) {
    return VM_ObjectModel.getPointerInMemoryRegion(obj);
  }


  public static double now() {
    return VM_Time.now();
  }


  /**
   * Allocate an array of instructions
   * @param n The number of instructions to allocate
   * @return The instruction array
   */ 
  public static INSTRUCTION[] newInstructions(int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    return new INSTRUCTION[n];
  }


  /**
   * Allocate a stack array
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newStack(int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    return new int[n];
  }


  /**
   * Allocate an immortal short array that will live forever and does not move
   * @param n The number of elements
   * @return The short array
   */ 
  public static short[] newImmortalShortArray (int n) throws VM_PragmaInterruptible {

    if (VM.runningVM) {
      VM_Array shortArrayType = VM_Array.ShortArray;
      Object [] shortArrayTib = shortArrayType.getTypeInformationBlock();
      int offset = VM_JavaHeader.computeArrayHeaderSize(shortArrayType);
      int arraySize = shortArrayType.getInstanceSize(n);
      Object result = allocateArray(n, arraySize, shortArrayTib, Plan.IMMORTAL_SPACE);
      return (short []) result;
    }

    return new short[n];
  }


  /**
   * Allocate an aligned stack array that will live forever and does not move
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newImmortalStack (int n) throws VM_PragmaInterruptible {

    if (VM.runningVM) {
      int logAlignment = 12;
      int alignment = 1 << logAlignment; // 4096
      VM_Array stackType = VM_Array.IntArray;
      Object [] stackTib = stackType.getTypeInformationBlock();
      int offset = VM_JavaHeader.computeArrayHeaderSize(stackType);
      int arraySize = stackType.getInstanceSize(n);
      int fullSize = arraySize + alignment;  // somewhat wasteful
      if (VM.VerifyAssertions) VM._assert(alignment > offset);
      AllocAdvice advice = getPlan().getAllocAdvice(null, fullSize, null, null);
      VM_Address fullRegion = getPlan().alloc(fullSize, false, Plan.IMMORTAL_SPACE, advice);
      VM_Address tmp = fullRegion.add(alignment);
      int mask = ~((1 << logAlignment) - 1);
      VM_Address region = VM_Address.fromInt(tmp.toInt() & mask).sub(offset);
      Object result = VM_ObjectModel.initializeArray(region, stackTib, n, arraySize);
      getPlan().postAlloc(result, stackTib, arraySize, false, Plan.IMMORTAL_SPACE);
      return (int []) result;
    }

    return new int[n];
  }

  /**
   * Allocate a contiguous int array
   * @param n The number of ints
   * @return The contiguous int array
   */ 
  public static int[] newContiguousIntArray(int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    return new int[n];
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    return new VM_CompiledMethod[n];
  }

  /**
   * Allocate a contiguous VM_DynamicLibrary array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_DynamicLibrary[] newContiguousDynamicLibraryArray(int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    return new VM_DynamicLibrary[n];
  }


  public static Object[] newTIB (int n) throws VM_PragmaInline, VM_PragmaInterruptible {
    if (VM.runningVM) {
      VM_Array objectArrayType = VM_Type.JavaLangObjectArrayType;
      Object [] objectArrayTib = objectArrayType.getTypeInformationBlock();
      int arraySize = objectArrayType.getInstanceSize(n);
      Object result = allocateArray(n, arraySize, objectArrayTib, Plan.IMMORTAL_SPACE);
      return (Object []) result;
    } else
      return new Object[n];
  }

  public static boolean isScalar(VM_Address obj) {
    VM_Type type = VM_Magic.objectAsType(VM_ObjectModel.getTIB(obj)[TIB_TYPE_INDEX]);
    return type.isClassType();
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
      VM_Address region = plan.allocCopy(fromObj, numBytes, true);
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, classType, forwardingPtr);
      plan.postCopy(toObj, tib, numBytes, true);
      toRef = VM_Magic.objectAsAddress(toObj);
    } else {
      VM_Array arrayType = type.asArray();
      int numElements = VM_Magic.getArrayLength(fromObj);
      int numBytes = VM_ObjectModel.bytesRequiredWhenCopied(fromObj, arrayType, numElements);
      forwardingPtr = Plan.resetGCBitsForCopy(fromObj, forwardingPtr,numBytes);
      VM_Address region = getPlan().allocCopy(fromObj, numBytes, false);
      Object toObj = VM_ObjectModel.moveObject(region, fromObj, numBytes, arrayType, forwardingPtr);
      plan.postCopy(toObj, tib, numBytes, false);
      toRef = VM_Magic.objectAsAddress(toObj);
      if (arrayType == VM_Type.InstructionArrayType) {
	// sync all moved code arrays to get icache and dcache in sync immediately.
	int dataSize = numBytes - VM_ObjectModel.computeHeaderSize(VM_Magic.getObjectType(toObj));
	VM_Memory.sync(toRef, dataSize);
      }
    }
    return toRef;

  }

  public static int synchronizedCounterOffset = -1;


}
