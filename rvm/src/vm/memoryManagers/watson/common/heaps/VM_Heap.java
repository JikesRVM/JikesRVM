/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers;

import VM;
import VM_BootRecord;
import VM_Constants;
import VM_Address;
import VM_Magic;
import VM_ObjectModel;
import VM_JavaHeader;
import VM_ClassLoader;
import VM_SystemClassLoader;
import VM_Atom;
import VM_Type;
import VM_Class;
import VM_Array;
import VM_Method;
import VM_PragmaInline;
import VM_PragmaNoInline;
import VM_PragmaInterruptible;
import VM_PragmaUninterruptible;
import VM_PragmaLogicallyUninterruptible;
import VM_Processor;
import VM_Scheduler;
import VM_Thread;
import VM_Memory;
import VM_Time;
import VM_Entrypoints;
import VM_Reflection;
import VM_Synchronization;
import VM_Synchronizer;
import VM_EventLogger;

/**
 * A Heap Abstraction.
 * 
 * @author Perry Cheng
 * @modified Dave Grove
 */
public abstract class VM_Heap 
  implements VM_Constants, VM_GCConstants {

  private static VM_BootRecord bootRecord;
  static VM_BootHeap bootHeap;
  protected static VM_MallocHeap mallocHeap;
  public static final int MAX_HEAPS = 10;
  public static final VM_Heap [] allHeaps = new VM_Heap[MAX_HEAPS];
  private static int heapCount = 0;

  /** Name of the heap */
  final String name;

  /** Amount of chattering during operation */
  int verbose = 0; 

  protected VM_Address start;         // INCLUSIVE-EXCLUSIVE range of memory belonging to this heap
  protected VM_Address end;
  protected int size;                 // end - start
  protected VM_Address minRef;        // INCLUSIVE range for legal object reference values
  protected VM_Address maxRef;

  private int id;          // instance's position within the allHeaps array


  /*
   * Static methods that manipulate all heaps
   */
  
  /**
   * Boot the heaps.  Must be called exactly once from VM_Allocator.boot.
   */
  public static void boot(VM_BootHeap bh, VM_MallocHeap mh, VM_BootRecord br) throws VM_PragmaUninterruptible {
    bootHeap = bh;
    mallocHeap = mh;
    bootRecord = br;
    bootHeap.start = bootRecord.bootImageStart;
    bootHeap.end = bootRecord.bootImageEnd;
    bootHeap.setAuxiliary();
    if (VM.VerifyAssertions) VM.assert(bootHeap.refInHeap(VM_Magic.objectAsAddress(bootHeap)));
  }

  /**
   * Return true if the given reference is to an 
   * object that is located within some heap
   */
  static public boolean refInAnyHeap(VM_Address ref) throws VM_PragmaUninterruptible {
    for (int i=0; i<heapCount; i++) {
      if (allHeaps[i].refInHeap(ref)) {
	return true;
      }
    }
    return false;
  }

  /**
   * Return true if the given addressis located within some heap
   */
  static public boolean addrInAnyHeap(VM_Address addr) throws VM_PragmaUninterruptible {
    for (int i=0; i<heapCount; i++)
      if (allHeaps[i].addrInHeap(addr))
	return true;
    return false;
  }

  /**
   * Ask all known heaps to show themselves
   */
  static public void showAllHeaps() throws VM_PragmaUninterruptible {
    VM.sysWriteln(heapCount, " heaps");
    for (int i=0; i<heapCount; i++) {
      VM.sysWrite("Heap ", i, ": "); 
      allHeaps[i].show(); 
    }
  }

  /**
   * Clobber the specified address range; useful for debugging
   */
  static public void clobber(VM_Address start, VM_Address end) throws VM_PragmaUninterruptible {
    VM.sysWrite("Zapping region ", start);
    VM.sysWrite(" .. ", end);
    VM.sysWriteln(" with 0xff****ff: ");
    int size = end.diff(start);
    for (int i=0; i<size; i+=4) {
      int pattern = 0xff0000ff;
      pattern |= i & 0x00ffff00;
      VM_Magic.setMemoryWord(start.add(i), pattern);
    }
  }

  
  /*
   * Heap instance methods
   */

  /**
   * For initial creation when no information is known at boot-image build time 
   */
  public VM_Heap(String n) throws VM_PragmaUninterruptible {        
    name = n;
    start = end = VM_Address.fromInt(0);
    minRef = maxRef = VM_Address.fromInt(0);
    size = 0;
    id = heapCount++;
    allHeaps[id] = this;
  }


  /**
   * Set minRef, maxRef, size and update bootRecord heap ranges
   */
  void setAuxiliary() throws VM_PragmaUninterruptible {     
    if (VM.VerifyAssertions) VM.assert(id < bootRecord.heapRanges.length - 2); 
    bootRecord.heapRanges[2 * id] = start.toInt();
    bootRecord.heapRanges[2 * id + 1] = end.toInt();
    minRef = VM_ObjectModel.minimumObjectRef(start);
    maxRef = VM_ObjectModel.maximumObjectRef(end);
    size = end.diff(start);
  }

  /**
   * Set the region that the heap is managing
   */
  public void setRegion(VM_Address s, VM_Address e) throws VM_PragmaUninterruptible {
    start = s;
    end = e;
    setAuxiliary();
  }

  /**
   * How big is the heap (in bytes)
   */
  public final int getSize() throws VM_PragmaUninterruptible {
    return size;
  }

  /**
   * Zero the entire heap
   */
  public final void zero() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(VM_Memory.roundDownPage(size) == size);
    VM_Memory.zeroPages(start, size);
  }

  /**
   * Zero the portion of the s ..e range of this heap that is assigned to the given processor
   */
  public final void zeroParallel(VM_Address s, VM_Address e) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(s.GE(start));
    if (VM.VerifyAssertions) VM.assert(e.LE(end));
    int np = VM_CollectorThread.numCollectors();
    VM_CollectorThread self = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    int which = self.gcOrdinal - 1;  // gcOrdinal starts at 1
    int chunk = VM_Memory.roundUpPage(e.diff(s) / np);
    VM_Address zeroBegin = s.add(which * chunk);
    VM_Address zeroEnd = zeroBegin.add(chunk);
    if (zeroEnd.GT(end)) zeroEnd = end;
    int size = zeroEnd.diff(zeroBegin);  // actual size to zero
    if (VM.VerifyAssertions) VM.assert(VM_Memory.roundUpPage(size) == size);
    VM_Memory.zeroPages(zeroBegin, size);

  }

  /**
   * size is specified in bytes and must be positive - automatically rounded up to the next page
   */
  public void attach(int size) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(bootRecord != null);
    if (size < 0) 
      VM.sysFail("VM_Heap.attach given negative size\n");
    if (this.size != 0)
      VM.sysFail("VM_Heap.attach called on already attached heap\n");
    size = VM_Memory.roundUpPage(size);
    // Let OS place region for now
    start = VM_Memory.mmap(VM_Address.fromInt(0), 
			   size, 
			   VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC,
			   VM_Memory.MAP_PRIVATE | VM_Memory.MAP_ANONYMOUS);
    if (start.GE(VM_Address.fromInt(0)) && 
	start.LT(VM_Address.fromInt(128))) {  // errno range
      VM.sysWrite("VM_Heap failed to mmap ", size / 1024);
      VM.sysWrite(" Kbytes with errno = "); VM.sysWrite(start); VM.sysWriteln();
      if (VM.VerifyAssertions) VM.assert(false);
    }
    end = start.add(size);
    if (verbose >= 1) {
      VM.sysWrite("VM_Heap sucessfully mmap ", size / 1024);
      VM.sysWrite(" Kbytes from ");
      VM.sysWrite(start); VM.sysWrite(" to " ); VM.sysWrite(end); VM.sysWrite("\n");
    }
    setAuxiliary();
  }

  public void grow(int sz) throws VM_PragmaUninterruptible {
    if (sz < size)
      VM.sysFail("VM_Heap.grow given smaller size than current size\n");
    sz = VM_Memory.roundUpPage(sz);
    VM_Address result = VM_Memory.mmap(end, sz - size,
				       VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC,
				       VM_Memory.MAP_PRIVATE | VM_Memory.MAP_ANONYMOUS | VM_Memory.MAP_FIXED);
    int status = result.toInt();
    if (status >= 0 && status < 128) {
      VM.sysWrite("VM_Heap.grow failed to mmap additional ", (sz - size) / 1024);
      VM.sysWrite(" Kbytes at ");
      VM.sysWrite(end);
      VM.sysWriteln(" with errno = ", status);
      if (VM.VerifyAssertions) VM.assert(false);
    }
    if (verbose >= 1) {
      VM.sysWrite("VM_Heap.grow successfully mmap additional ", (sz - size) / 1024);
      VM.sysWrite(" Kbytes at ");  VM.sysWrite(end); VM.sysWrite("\n");
    }
    // start not modified
    end = start.add(sz);
    setAuxiliary();
  }

  public void detach() throws VM_PragmaUninterruptible {
    if (this.size == 0)
      VM.sysFail("VM_Heap.detach called on unattached heap\n");
    int status = VM_Memory.munmap(start, size);
    if (status != 0) {
      VM.sysWriteln("VM_Heap.detach failed with errno = ", status);
      VM.sysFail("VM_Heap.detach failed\n");
    }
    if (verbose >= 1) {
      VM.sysWrite("VM_Heap successfully detached ", size / 1024); 
      VM.sysWrite(" Kbytes starting at ");
      VM.sysWrite(start); VM.sysWriteln();
    }
    start = end = VM_Address.fromInt(0);
    setAuxiliary();
  }

  public final void protect() throws VM_PragmaUninterruptible {
    VM_Memory.mprotect(start, size, VM_Memory.PROT_NONE);
  }
    
  public final void unprotect() throws VM_PragmaUninterruptible {
    VM_Memory.mprotect(start, size, VM_Memory.PROT_READ | VM_Memory.PROT_WRITE | VM_Memory.PROT_EXEC);
  }
    
  public final boolean refInHeap(VM_Address ref) throws VM_PragmaUninterruptible {
    return ref.GE(minRef) && ref.LE(maxRef);
  }

  public final boolean addrInHeap(VM_Address addr) throws VM_PragmaUninterruptible {
    return addr.GE(start) && addr.LT(end);
  }

  public void showRange() throws VM_PragmaUninterruptible {
    VM.sysWrite(start); VM.sysWrite(" .. "); VM.sysWrite(end);
  }


  public void show() throws VM_PragmaUninterruptible {
    show(true);
  }

  public void show(boolean newline) throws VM_PragmaUninterruptible {
    int tab = 26 - name.length();
    for (int i=0; i<tab; i++) VM.sysWrite(" ");
    VM.sysWrite(name, ": ");
    VM.sysWriteField(6, size / 1024); VM.sysWrite(" Kb  at  "); 
    showRange();
    if (newline) VM.sysWriteln();
  }

  public void touchPages() throws VM_PragmaUninterruptible {
    int ps = VM_Memory.getPagesize();
    for (int i = size - ps; i >= 0; i -= ps)
      VM_Magic.setMemoryWord(start.add(i), 0);
  }

  public final void clobber() throws VM_PragmaUninterruptible { clobber(start, end); }

  /**
   * Scan this heap for references to the target heap and report them
   * This is approximate since the scan is done without type information.
   */
  public final int paranoidScan(VM_Heap target, boolean show) throws VM_PragmaUninterruptible {
    int count = 0;
    VM.sysWrite("Checking heap "); showRange(); 
    VM.sysWrite(" for references to "); target.showRange(); VM.sysWriteln();
    for (VM_Address loc = start; loc.LT(end); loc = loc.add(4)) {
      VM_Address value = VM_Magic.getMemoryAddress(loc);
      int value2 = value.toInt();
      if (((value2 & 3) == 0) && target.refInHeap(value)) {
	count++;
	if (show) {
	  int oldVal = VM_Magic.getMemoryWord(value.sub(12));
	  VM.sysWrite("Warning:  GC ", VM_Allocator.gcCount);
	  VM.sysWrite(" # ", count);
	  VM.sysWrite("  loc ", loc); 
	  VM.sysWrite(" holds poss ref ", value);
	  VM.sysWriteln(" with value ", oldVal);
	}
      }
    }
    VM.sysWrite("\nThere were ", count, " suspicious references to ");
    target.showRange();
    VM.sysWriteln();
    return count;
  }


  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero. Assumes that type is already initialized.
   * 
   * @param type  VM_Class of type to be instantiated
   *
   * @return the reference for the allocated object
   */
  public final Object allocateScalar(VM_Class type) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(type.isInitialized());
    int size = type.getInstanceSize();
    Object[] tib = type.getTypeInformationBlock();
    return allocateScalar(size, tib);
  }

  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.  Assumes that type is already initialized.
   *
   * @param type  VM_Array of type to be instantiated
   * @param numElements  number of array elements
   *
   * @return the reference for the allocated array object 
   */
  public final Object allocateArray(VM_Array type, int numElements) throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM.assert(type.isInitialized());
    int size = type.getInstanceSize(numElements);
    Object[] tib = type.getTypeInformationBlock();
    return allocateArray(numElements, size, tib);
  }

  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   *
   * @return the reference for the allocated object
   */
  public final Object allocateScalar(int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaNoInline /* infrequent case allocation --dave */, VM_PragmaUninterruptible {
    VM_Address region = allocateZeroedMemory(size);
    VM_GCStatistics.profileAlloc(region, size, tib); // profile/debug; usually inlined away to nothing
    Object newObj = VM_ObjectModel.initializeScalar(region, tib, size);
    postAllocationProcessing(newObj);
    return newObj;
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
  public final Object allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError, VM_PragmaNoInline /* infrequent case allocation --dave */, VM_PragmaUninterruptible {
    // note: array size might not be a word multiple,
    //       must preserve alignment of future allocations
    size = VM_Memory.align(size, WORDSIZE);
    VM_Address region = allocateZeroedMemory(size);  
    VM_GCStatistics.profileAlloc(region, size, tib); // profile/debug: usually inlined away to nothing
    Object newObj = VM_ObjectModel.initializeArray(region, tib, numElements, size);
    postAllocationProcessing(newObj);
    return newObj;
  }


  /**
   * Allocate size bytes of zeroed memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected abstract VM_Address allocateZeroedMemory(int size) throws VM_PragmaUninterruptible;

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   * @param newObj the object just allocated in the heap.
   */
  protected abstract void postAllocationProcessing(Object newObj) throws VM_PragmaUninterruptible;

}
