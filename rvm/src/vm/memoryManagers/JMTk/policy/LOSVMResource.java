/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;

import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_ProcessorLock;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 *  A mark-sweep area to hold "large" objects (in 4K chunks)
 *  The large space code is obtained by factoring out the code in various
 *  collectors.
 *
 *  @author Perry Cheng
 */
public class LOSVMResource extends MonotoneVMResource implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  public void prepare (VMResource _vm, MemoryResource _mr) {
      VM_Memory.zero(VM_Magic.objectAsAddress(mark), 
		     VM_Magic.objectAsAddress(mark).add(2*mark.length));
  }

  public void release (VMResource _vm, MemoryResource _mr) {
    int blks = Conversions.bytesToBlocks(pageSize * (pagesAllocated - pagesMarked));
    memoryResource.release(blks);
    pagesAllocated = pagesMarked;
    pagesMarked = 0;
    short[] temp    = allocated;
    allocated = mark;
    mark  = temp;
    lastAllocated = 0;
  }
  
  // Overall configuration
  private final int pageSize = 4096;         // large space allocated in 4K chunks
  private final int GC_INITIAL_LARGE_SPACE_PAGES = 200; // for early allocation of large objs
  private int           totalPages;

  // Management of virtual address range
  private Lock LOSgcLock;       // used during GC
  private Lock LOSmutatorLock;  // used by mutators
  private int		lastAllocated;   // where to start search for free space
  private short[]	allocated;	// used to allocate in large space
  private short[]	mark;		// used to mark large objects

  // Memory usage
  private MemoryResource memoryResource;
  private int pagesAllocated = 0;
  private int pagesMarked = 0;
  private int pagesOverCommitted = 0;  // extra pages committed from memoryresource point of view

  /**
   * Initialize for boot image - called from init of various collectors
   */
  public LOSVMResource(String name, MemoryResource mr, VM_Address start, EXTENT size) throws VM_PragmaInterruptible {
    super(name, mr, start, size, VMResource.IN_VM);
    lastAllocated = 0;
    totalPages = 0;
    memoryResource = mr;
    LOSgcLock = new Lock("LOSVMResource.gcLock");
    LOSmutatorLock = new Lock("LOSVMResource.mutatorLock");
  }


  /**
   * Initialize for execution.  Meta-data created at boot time.
   */
  public void setup () throws VM_PragmaInterruptible {
    // Get the (full sized) arrays that control large object space
    totalPages = end.diff(start).toInt() / pageSize;
    allocated = VM_Interface.newImmortalShortArray(totalPages + 1);
    mark  = VM_Interface.newImmortalShortArray(totalPages + 1);
  }


  /**
   * Acquire the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void LOSlock() {
    if (Plan.gcInProgress())
      LOSgcLock.acquire();
    else
      LOSmutatorLock.acquire();
  }


  /*
   * Debug function for pinpointing where deadlock was caused.
   */
  private void LOScheckpoint(int where) {
    if (Plan.gcInProgress())
      LOSgcLock.checkpoint(where);
    else
      LOSmutatorLock.checkpoint(where);
  }

  /**
   * Release the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void LOSunlock() {
    if (Plan.gcInProgress())
      LOSgcLock.release();
    else
      LOSmutatorLock.release();
  }

  /**
   * Allocate size bytes of zeroed memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address alloc (boolean isScalar, int size) {

    if (VM.VerifyAssertions) VM._assert(allocated != null);

    int num_pages = (size + (pageSize - 1)) / pageSize;    // Number of pages needed
    int bytes = num_pages * pageSize;
    
    int bytesToCommit = bytes - pagesOverCommitted * pageSize;
    int blocks = 0;
    if (bytesToCommit < 0) {
      pagesOverCommitted -= bytes / pageSize;
    }
    else {
      blocks = Conversions.bytesToBlocks(bytesToCommit);
      int overcommitBytes = Conversions.blocksToBytes(blocks) - bytesToCommit;
	if (!memoryResource.acquire(blocks))
	  return VM_Address.zero();
	pagesOverCommitted = overcommitBytes / pageSize;
    }

    // Cycle through twice to make sure we covered everything
    // This is a bit wasteful since the second cycle does not need to go past the original starting position
    //
    int current = lastAllocated;  // could be initialized to any value not inside an allocated block or at the initial position
    int last_possible = totalPages - num_pages;
    for (int count=0; count < 2 ; count++) {

      LOSlock();
      while (current <= last_possible) {
	// Increment until first free block found
	while (allocated[current] != 0) {
	  if (VM.VerifyAssertions) VM._assert(allocated[current] > 0);
	  current += allocated[current];
	}
	// Look for needed number of free pages.  Search aborted if not found.
	int i;
	for (i = current; i < current + num_pages ; i++) {
	  if (allocated[i] != 0) break;
	}
	if (i == (current + num_pages )) {  

	  // successful: found num_pages contiguous pages
	  // mark the newly allocated pages
	  // mark the beginning of the range with num_pages
	  // mark the end of the range with -num_pages
	  // so that when marking (ref is input) will know which extreme 
	  // of the range the ref identifies, and then can find the other
	  
	  VM_Address result = start.add(pageSize * current);
	  VM_Address resultEnd = result.add(size);
	  if (resultEnd.GT(cursor)) {
	    int neededBytes = resultEnd.diff(cursor).toInt();
	    int neededBlocks = Conversions.bytesToBlocks(neededBytes);
	    VM_Address newArea = acquire(neededBlocks);
	    if (newArea.isZero()) {
	      LOSunlock();
	      return VM_Address.zero();
	    }
	  }
	  if (VM.VerifyAssertions) VM._assert(resultEnd.LE(cursor));
	  allocated[current] = (short)(num_pages);
	  allocated[current + num_pages - 1] = (short)(-num_pages);
	  pagesAllocated += num_pages;
	  lastAllocated = current + num_pages;
	  LOSunlock();
	  Memory.zero(result, resultEnd);
	  return result;
	} else {  
	  // free area did not contain enough contig. pages
	  if (VM.VerifyAssertions) VM._assert(allocated[i] > 0);
	  current = i + allocated[i]; 
	}
      }
      LOSunlock();
    } // for 

    VM_Interface.getPlan().poll(true);
    return VM_Address.zero();
  }


  boolean isLive (VM_Address ref) {
      VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(ref);
      if (VM.VerifyAssertions) VM._assert(start.LE(addr) && addr.LE(end));
      int page_num = addr.diff(start).toInt() >> 12;
      return (mark[page_num] != 0);
  }

  VM_Address traceObject (VM_Address ref) {

    VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
    // if (VM.VerifyAssertions) VM._assert(addrInHeap(tref));

    int ij;
    int page_num = tref.diff(start).toInt() >>> 12;
    boolean result = (mark[page_num] != 0);
    if (result) return ref;	// fast, no synch case
    
    LOSlock();		// get sysLock for large objects
    result = (mark[page_num] != 0);
    if (result) {	// need to recheck
      LOSunlock();
      return ref;
    }
    int temp = allocated[page_num];
    pagesMarked += (temp > 0) ? temp : -temp;
    if (Plan.verbose > 3)
      VM.sysWriteln("LOS.marking ", ref);
    if (temp == 1) 
      mark[page_num] = 1;
    else {
      // mark entries for both ends of the range of allocated pages
      if (temp > 0) {
	ij = page_num + temp -1;
	mark[ij] = (short)-temp;
      }
      else {
	ij = page_num + temp + 1;
	mark[ij] = (short)-temp;
      }
      mark[page_num] = (short)temp;
    }
    VM_Interface.getPlan().enqueue(ref);
    LOSunlock();
    return ref;
  }




}
