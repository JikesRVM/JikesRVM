/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.vmInterface;

import com.ibm.JikesRVM.memoryManagers.watson.VM_Allocator;
import com.ibm.JikesRVM.memoryManagers.watson.VM_GCUtil;
import com.ibm.JikesRVM.memoryManagers.watson.VM_WriteBarrier;
import com.ibm.JikesRVM.memoryManagers.watson.VM_GCWorkQueue;
import com.ibm.JikesRVM.memoryManagers.watson.VM_GCStatistics;
import com.ibm.JikesRVM.memoryManagers.watson.VM_Heap;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_DynamicLibrary;


/*
 * @author Perry Cheng  
 */  

public class VM_Interface implements VM_Constants {

  public static void logGarbageCollection() throws VM_PragmaUninterruptible {
    if (VM.BuildForEventLogging && VM.EventLoggingEnabled)
      VM_EventLogger.logGarbageCollectionEvent();
  }

  public static VM_Class createScalarType(String descriptor) {
    VM_Atom atom = VM_Atom.findOrCreateAsciiAtom(descriptor);
    return VM_ClassLoader.findOrCreateType(atom, VM_SystemClassLoader.getVMClassLoader()).asClass();
  }

  public static VM_Array createArrayType(String descriptor) {
    VM_Atom atom = VM_Atom.findOrCreateAsciiAtom(descriptor);
    return VM_ClassLoader.findOrCreateType(atom, VM_SystemClassLoader.getVMClassLoader()).asArray();
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
    VM_Allocator.init();
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
    // get addresses of TIBs for VM_Array & VM_Class used for testing Type ptrs
    VM_Type t = VM_Array.getPrimitiveArrayType(10);
    tibForArrayType = VM_ObjectModel.getTIB(t);
    tibForPrimitiveType = VM_ObjectModel.getTIB(VM_Type.IntType);
    t = VM_Magic.getObjectType(VM_BootRecord.the_boot_record);
    tibForClassType = VM_ObjectModel.getTIB(t);
    VM_GCUtil.boot();
    VM_Allocator.boot();
    VM_GCStatistics.boot();
  }

  /**
   * Perform postBoot operations such as dealing with command line
   * options (this is called as soon as options have been parsed,
   * which is necessarily after the basic allocator boot).
   */
  public static void postBoot() throws VM_PragmaInterruptible {
  }

  /** 
   *  Process GC parameters.
   */
  public static void processCommandLineArg(String arg) {
      VM.sysWriteln("Unrecognized collection option: ", arg);
      VM.sysExit(1);
  }

  public static int numProcessors() throws VM_PragmaUninterruptible {
    return VM.sysCall0(VM_BootRecord.the_boot_record.sysNumProcessorsIP);
  }

  public static final void threadBoot(int numProcessors) throws VM_PragmaInterruptible {
    VM_Allocator.gcSetup(numProcessors);
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
    VM_BootRecord br = VM_BootRecord.the_boot_record;
    if (VM.VerifyAssertions) VM._assert(id < br.heapRanges.length - 2); 
    br.heapRanges[2 * id] = start.toInt();
    br.heapRanges[2 * id + 1] = end.toInt();
  }

  public static void resolvedPutfieldWriteBarrier(Object ref, int offset, Object value) {
    if (NEEDS_WRITE_BARRIER) 
      VM_WriteBarrier.resolvedPutfieldWriteBarrier(ref, offset, value);
  }

  public static void resolvedPutStaticWriteBarrier(int offset, Object value) { 
    if (NEEDS_WRITE_BARRIER) 
      VM_WriteBarrier.resolvedPutStaticWriteBarrier(offset, value);
  }

  public static void arrayCopyWriteBarrier(Object ref, int start, int end) {
    if (NEEDS_WRITE_BARRIER) 
      VM_WriteBarrier.arrayCopyWriteBarrier(ref, start, end);
  }

  public static void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
  }


  /**
   * Returns true if GC is in progress.
   *
   * @return True if GC is in progress.
   */
  public static final boolean gcInProgress() throws VM_PragmaUninterruptible {
    return VM_Allocator.gcInProgress();
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
  public static final long freeMemory() throws VM_PragmaInterruptible {
    return VM_Allocator.freeMemory();
  }

  /**
   * Returns the amount of total memory.
   *
   * @return The amount of total memory.
   */
  public static final long totalMemory() throws VM_PragmaInterruptible {
    return VM_Allocator.totalMemory();
  }

  /**
   * Forces a garbage collection.
   */
  public static final void gc() throws VM_PragmaInterruptible {
    VM_Allocator.gc();
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
  public static final void setupProcessor(VM_Processor p) throws VM_PragmaInterruptible {
    VM_Allocator.setupProcessor(p);
  }

  public static final boolean NEEDS_WRITE_BARRIER = VM_Allocator.writeBarrier;
  public static final boolean NEEDS_RC_WRITE_BARRIER = false;
  public static final boolean RC_CYCLE_DETECTION = false;
  public static final boolean MOVES_OBJECTS = VM_Allocator.movesObjects;
  public static boolean useMemoryController = false;


  public static void setWorkBufferSize (int size) {
    VM_GCWorkQueue.WORK_BUFFER_SIZE = 4 * size;
  }

  public static void dumpRef(VM_Address ref) throws VM_PragmaUninterruptible {
    VM_GCUtil.dumpRef(ref);
  }

  public static boolean addrInVM(VM_Address address) throws VM_PragmaUninterruptible {
    return VM_GCUtil.addrInVM(address);
  }

  public static boolean refInVM(VM_Address ref) throws VM_PragmaUninterruptible {
    return VM_GCUtil.refInVM(ref);
  }

  public static boolean validRef(VM_Address ref) {
    return VM_GCUtil.validRef(ref);
  }

  public static int getMaxHeaps() {
    return VM_Heap.MAX_HEAPS;
  }

  public static void addFinalizer(Object obj) {
    VM_Finalizer.addCandidate(obj);
  }

  public static void processPtrField (VM_Address location) throws VM_PragmaUninterruptible {
    VM_Allocator.processPtrField(location);
  }

  public static void processPtrField (VM_Address location, boolean root) throws VM_PragmaUninterruptible { 
    VM_Allocator.processPtrField(location);
  }

  public static Object allocateScalar(int size, Object [] tib, int allocator) {
    return VM_Allocator.allocateScalar(size, tib);
  }

  public static Object allocateArray(int numElements, int size, Object [] tib, int allocator) {
    return VM_Allocator.allocateArray(numElements, size, tib);
  }

  public static void collect() {
    VM_Allocator.collect();
  }






  /**
   * Allocate an array of instructions
   * @param n The number of instructions to allocate
   * @return The instruction array
   */ 
  public static INSTRUCTION[] newInstructions(int n) throws VM_PragmaInline {

    if (VM.BuildForRealtimeGC) {
      //-#if RVM_WITH_REALTIME_GC
      return VM_SegmentedArray.newInstructions(n);
      //-#endif
    }

    return new INSTRUCTION[n];
  }


  /**
   * Allocate a stack array
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newStack(int n) throws VM_PragmaInline {

    if (VM.BuildForRealtimeGC) {
      //-#if RVM_WITH_REALTIME_GC
      return VM_SegmentedArray.newStack(n);
      //-#endif
    }

    return new int[n];
  }


  /**
   * Allocate a stack array that will live forever and does not move
   * @param n The number of stack slots to allocate
   * @return The stack array
   */ 
  public static int[] newImmortalStack (int n) {

    if (VM.runningVM) {
      int[] stack = (int[]) VM_Allocator.immortalHeap.allocateAlignedArray(VM_Array.arrayOfIntType, n, 4096);
      return stack;
    }

    return new int[n];
  }

  /**
   * Allocate a contiguous int array
   * @param n The number of ints
   * @return The contiguous int array
   */ 
  public static int[] newContiguousIntArray(int n) throws VM_PragmaInline {

    if (VM.BuildForRealtimeGC) {
      //-#if RVM_WITH_REALTIME_GC
      return VM_SegmentedArray.newIntArray(n);
      //-#endif
    }

    return new int[n];
  }

  /**
   * Allocate a contiguous VM_CompiledMethod array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_CompiledMethod[] newContiguousCompiledMethodArray(int n) throws
    VM_PragmaInline {

      if (VM.BuildForRealtimeGC) {
        //-#if RVM_WITH_REALTIME_GC
        return VM_SegmentedArray.newContiguousCompiledMethodArray(n);
        //-#endif
      }

      return new VM_CompiledMethod[n];
    }

  /**
   * Allocate a contiguous VM_DynamicLibrary array
   * @param n The number of objects
   * @return The contiguous object array
   */ 
  public static VM_DynamicLibrary[] newContiguousDynamicLibraryArray(int n) throws VM_PragmaInline {

    if (VM.BuildForRealtimeGC) {
      //-#if RVM_WITH_REALTIME_GC
      return VM_SegmentedArray.newContiguousDynamicLibraryArray(n);
      //-#endif
    }

    return new VM_DynamicLibrary[n];
  }


  public static Object[] newTIB (int n) throws VM_PragmaInline {

    if (true) {
      //-#if RVM_WITH_COPYING_GC
      //-#if RVM_WITH_ONE_WORD_MASK_OBJECT_MODEL
      return VM_Allocator.newTIB(n);
      //-#endif
      //-#endif
    }

    return new Object[n];
  }

  public static int pickAllocator(VM_Type type) throws VM_PragmaInterruptible {
    return 0;
  }

  public static void checkBootImageAddress (int addr) {
  }

}
