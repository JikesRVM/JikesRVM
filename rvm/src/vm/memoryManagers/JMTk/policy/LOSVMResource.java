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
 *  A mark-sweep area to hold "large" objects (typically at least 2K).
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

    // Cycle through twice to make sure we covered everything
    // This is  bit wasteful...
    //
    for (int count=0; count < 2 ; count++) {

      int num_pages = (size + (pageSize - 1)) / pageSize;    // Number of pages needed
      int bytes = num_pages * pageSize;

      if (!memoryResource.acquire(Conversions.bytesToBlocks(bytes))) 
	return VM_Address.zero();

      LOSlock();
      int last_possible = totalPages - num_pages;
      LOScheckpoint(1);
      while (allocated[lastAllocated] != 0) 
	lastAllocated += allocated[lastAllocated];
      int first_free = lastAllocated;
      while (first_free <= last_possible) {
	// Now find contiguous pages for this object
	// first find the first available page
	// i points to an available page: remember it
	int i;
      LOScheckpoint(3);
	for (i = first_free + 1; i < first_free + num_pages ; i++) {
	  if (allocated[i] != 0) break;
	}
      LOScheckpoint(4);
	if (i == (first_free + num_pages )) {  

	  // successful: found num_pages contiguous pages
	  // mark the newly allocated pages
	  // mark the beginning of the range with num_pages
	  // mark the end of the range with -num_pages
	  // so that when marking (ref is input) will know which extreme 
	  // of the range the ref identifies, and then can find the other
	  
	  VM_Address result = start.add(pageSize * first_free);
	  VM_Address resultEnd = result.add(size);
      LOScheckpoint(5);
	  if (resultEnd.GT(cursor)) {
	    int neededBytes = resultEnd.diff(cursor).toInt();
	    int blocks = Conversions.bytesToBlocks(neededBytes);
	    VM_Address newArea = acquire(blocks);
      LOScheckpoint(6);
	    if (newArea.isZero()) {
      LOScheckpoint(7);
	      LOSunlock();
	      return VM_Address.zero();
	    }
	  }
      LOScheckpoint(10);
	  if (VM.VerifyAssertions) VM._assert(resultEnd.LE(cursor));
	  allocated[first_free + num_pages - 1] = (short)(-num_pages);
	  allocated[first_free] = (short)(num_pages);
	  pagesAllocated += num_pages;
      LOScheckpoint(11);
	  LOSunlock();
	  Memory.zero(result, resultEnd);
	  return result;
	} else {  
      LOScheckpoint(12);
	  // free area did not contain enough contig. pages
	  first_free = i + allocated[i]; 
	  while (allocated[first_free] != 0) 
	    first_free += allocated[first_free];
      LOScheckpoint(13);
	}
      }
      LOScheckpoint(14);
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
    if (temp == 1) 
      mark[page_num] = 1;
    else {
      // mark entries for both ends of the range of allocated pages
      if (temp > 0) {
	ij = page_num + temp -1;
	mark[ij] = (short)-temp;
	pagesMarked += temp;
      }
      else {
	ij = page_num + temp + 1;
	mark[ij] = (short)-temp;
	pagesMarked += (-temp);
      }
      mark[page_num] = (short)temp;
    }
    VM_Interface.getPlan().enqueue(ref);
    LOSunlock();
    return ref;
  }




}
