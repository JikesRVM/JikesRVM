/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_BootRecord;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.VM_ClassLoader;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_ProcessorLocalState;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Registers;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Entrypoints;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Synchronizer;
import com.ibm.JikesRVM.VM_EventLogger;
import com.ibm.JikesRVM.VM_Callbacks;

/**
 * Trivial implementation for illustrative purposes, not for production use.
 * Allocates all objects out of the immortal heap and exits with an OutOfMemoryError
 * when the immortal heap is exhausted. 
 * <p>
 * Ignores requests for garbage collection.
 * <p>
 * Useful for the initial port to a new architecture, otherwise fairly pointless.
 * <p>
 * @author Derek Lieber
 */
public class VM_Allocator implements VM_Constants, VM_GCConstants {
  private static final VM_BootHeap bootHeap = new VM_BootHeap();   
          static final VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();

  static int verbose = 0; // control chattering during progress of GC
  
  /**
   * Initialize for boot image.
   */
  static void init() {
    VM_CollectorThread.init();
  }
  
  /**
   * Initialize for execution.
   *
   * @param bootrecord  reference for the system VM_BootRecord
   */
  static void boot(VM_BootRecord bootrecord) {
    verbose = bootrecord.verboseGC;

    // attatch heaps.
    if (verbose >= 2) VM.sysWriteln("Attaching heaps");
    VM_Heap.boot(bootHeap, null, bootrecord);
    immortalHeap.attach(bootrecord.smallSpaceSize);

    VM_GCUtil.boot();
  }
  
  /**
   * Request a garbage collection cycle.
   */
  public static void gc() {
    // Tell gc thread to reclaim space, then wait for it to complete its work.
    // The gc thread will do its work by calling collect(), below.
    //
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }

  /**
   * Handle heap exhaustion.
   * 
   * @param size number of bytes requested in the failing allocation
   */
  public static void heapExhausted(VM_Heap heap, int size, int count) {
    VM.sysFail("999");
  }

  /**
   * return true if a garbage collection is in progress
   */
  public static boolean gcInProgress() {
    return false;
  }

  /**
   * Get total memory available, in bytes.
   *
   * @return the number of bytes
   */
  public static long totalMemory() {
    return immortalHeap.size;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   *
   * @return number of bytes available
   */
  public static long freeMemory() {
    return immortalHeap.freeMemory();
  }
  
  /**
   *  Includes freeMemory and per-processor local storage
   */
  public static long allSmallFreeMemory () {
    return freeMemory();
  }

  public static long allSmallUsableMemory () {
    return freeMemory();
  }


  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   * @param hasFinalizer hasFinalizer flag
   *
   * @return the reference for the allocated object
   */
  public static Object allocateScalar (int size, Object[] tib) throws OutOfMemoryError {
    return immortalHeap.allocateScalar(size, tib);
  }
  
  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   *
   * @return the reference for the allocated array object 
   */
  public static Object allocateArray (int numElements, int size, Object[] tib) throws OutOfMemoryError {
    return immortalHeap.allocateArray(numElements, size, tib);
  }
  
  /**
   * Reclaim unreferenced memory (ignored)
   */
  static void collect()  {
    // VM.sysWrite("VM_Allocator: gc unimplemented: nothing reclaimed\n");
  }

  // methods called from utility methods of VM_GCUtil, VM_ScanObject,
  // VM_ScanStack, VM_ScanStatics
  //
  static final void       processPtrField( VM_Address location ) { VM.assert(false); }
  static final VM_Address processPtrValue( VM_Address reference ) { VM.assert(false); return null; }
  static final void       processWriteBufferEntry( VM_Address discard ) { VM.assert(false); }

  // Other fields and methods referenced from common GC classes or elsewhere
  // in VM (ex. VM_Entrypoints)
  //
  static final boolean movesObjects = false;
  static final boolean writeBarrier = false;
  static boolean       gcInProgress;
  static void          gcSetup(int numProcessors) {}
  static void          setupProcessor(VM_Processor p) {}
  static boolean       processFinalizerListElement (VM_FinalizerListElement le) { return true; }
  static void          processWriteBufferEntry (int wbref) {}

  static int gcCount = 0;
}
