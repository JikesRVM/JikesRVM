/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *  A mark-sweep area to hold "large" objects (typically at least 2K).
 *  The large space code is obtained by factoring out the code in various
 *  collectors.
 *
 *  @author Perry Cheng
 */

public class VM_LargeHeap extends VM_Heap 
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  // Internal management
  private VM_ImmortalHeap immortal;         // place where we allocate metadata
  private VM_ProcessorLock spaceLock;        // serializes access to large space
  private final int pageSize = 4096;         // large space allocated in 4K chunks
  private final int GC_LARGE_SIZES = 20;           // for statistics  
  private final int GC_INITIAL_LARGE_SPACE_PAGES = 200; // for early allocation of large objs
  private int           largeSpacePages;
  private int		large_last_allocated;   // where to start search for free space
  private short[]	largeSpaceAlloc;	// used to allocate in large space
  private short[]	largeSpaceMark;		// used to mark large objects
  private int[]	        countLargeAlloc;	//  - count sizes of large objects alloc'ed

  /**
   * Initialize for boot image - called from init of various collectors
   */
  VM_LargeHeap(VM_ImmortalHeap imm) {
    super("Large Object Heap");
    immortal        = imm;
    spaceLock       = new VM_ProcessorLock();      // serializes access to large space
    large_last_allocated = 0;
    largeSpacePages = GC_INITIAL_LARGE_SPACE_PAGES;
    countLargeAlloc = new int[GC_LARGE_SIZES];
  }


  /**
   * Initialize for execution.
   */
  public void attach (int size) {

    // setup large object space
    super.attach(size);
    largeSpacePages = size / VM_Memory.getPagesize();
    
    // Get the (full sized) arrays that control large object space
    largeSpaceAlloc = immortal.allocateShortArray(largeSpacePages + 1);
    largeSpaceMark  = immortal.allocateShortArray(largeSpacePages + 1);
  }

  /**
   * Get total amount of memory used by large space.
   *
   * @return the number of bytes
   */
  public int totalMemory () {
    return size;
  }

  /**
   * Allocate size bytes of zeroed memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory (int size) {
    int count = 0;
    while (true) {
      int num_pages = (size + (pageSize - 1)) / pageSize;    // Number of pages needed
      int last_possible = largeSpacePages - num_pages;

      spaceLock.lock();

      while (largeSpaceAlloc[large_last_allocated] != 0) {
	large_last_allocated += largeSpaceAlloc[large_last_allocated];
      }

      int first_free = large_last_allocated;
      while (first_free <= last_possible) {
	// Now find contiguous pages for this object
	// first find the first available page
	// i points to an available page: remember it
	int i;
	for (i = first_free + 1; i < first_free + num_pages ; i++) {
	  if (largeSpaceAlloc[i] != 0) break;
	}
	if (i == (first_free + num_pages )) {  
	  // successful: found num_pages contiguous pages
	  // mark the newly allocated pages
	  // mark the beginning of the range with num_pages
	  // mark the end of the range with -num_pages
	  // so that when marking (ref is input) will know which extreme 
	  // of the range the ref identifies, and then can find the other
	  
	  largeSpaceAlloc[first_free + num_pages - 1] = (short)(-num_pages);
	  largeSpaceAlloc[first_free] = (short)(num_pages);
	       
	  spaceLock.unlock();  //release lock *and synch changes*
	  VM_Address target = start.add(VM_Memory.getPagesize() * first_free);
	  VM_Memory.zero(target, target.add(size));  // zero space before return
	  return target;
	} else {  
	  // free area did not contain enough contig. pages
	  first_free = i + largeSpaceAlloc[i]; 
	  while (largeSpaceAlloc[first_free] != 0) 
	    first_free += largeSpaceAlloc[first_free];
	}
      }

      spaceLock.release();  //release lock: won't keep change to large_last_alloc'd

      // Couldn't find space; inform allocator (which will either trigger GC or 
      // throw out of memory exception)
      VM_Allocator.heapExhausted(this, size, count++);
    }
  }


  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  protected void postAllocationProcessing(Object newObj) { 
    if (VM_Collector.NEEDS_WRITE_BARRIER) {
      VM_ObjectModel.initializeAvailableByte(newObj); 
      VM_AllocatorHeader.setBarrierBit(newObj);
    } 
  }


  void startCollect() {
      VM_Memory.zero(VM_Magic.objectAsAddress(largeSpaceMark), 
		     VM_Magic.objectAsAddress(largeSpaceMark).add(2*largeSpaceMark.length));
  }

  void endCollect() {
      short[] temp    = largeSpaceAlloc;
      largeSpaceAlloc = largeSpaceMark;
      largeSpaceMark  = temp;
      large_last_allocated = 0;
  }

  boolean isLive (VM_Address ref) {
      VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(ref);
      if (VM.VerifyAssertions) VM.assert(refInHeap(ref));
      int page_num = addr.diff(start ) >> 12;
      return (largeSpaceMark[page_num] != 0);
  }

  boolean mark (VM_Address ref) {

    VM_Address tref = VM_ObjectModel.getPointerInMemoryRegion(ref);
    if (VM.VerifyAssertions) VM.assert(addrInHeap(tref));

    int ij;
    int page_num = (tref.diff(start)) >>> 12;
    boolean result = (largeSpaceMark[page_num] != 0);
    if (result) return true;	// fast, no synch case
    
    spaceLock.lock();		// get sysLock for large objects
    result = (largeSpaceMark[page_num] != 0);
    if (result) {	// need to recheck
      spaceLock.release();
      return true;	
    }
    int temp = largeSpaceAlloc[page_num];
    if (temp == 1) 
      largeSpaceMark[page_num] = 1;
    else {
      // mark entries for both ends of the range of allocated pages
      if (temp > 0) {
	ij = page_num + temp -1;
	largeSpaceMark[ij] = (short)-temp;
      }
      else {
	ij = page_num + temp + 1;
	largeSpaceMark[ij] = (short)-temp;
      }
      largeSpaceMark[page_num] = (short)temp;
    }

    spaceLock.unlock();	// INCLUDES sync()
    return false;
  }



  private void countObjects () {
    int i,num_pages,countLargeOld;
    int contiguousFreePages,maxContiguousFreePages;

    for (i =  0; i < GC_LARGE_SIZES; i++) countLargeAlloc[i] = 0;
    countLargeOld = contiguousFreePages = maxContiguousFreePages = 0;

    for (i =  0; i < largeSpacePages;) {
      num_pages = largeSpaceAlloc[i];
      if (num_pages == 0) {     // no large object found here
	countLargeAlloc[0]++;   // count free pages in entry[0]
	contiguousFreePages++;
	i++;
      }
      else {    // at beginning of a large object
	if (num_pages < GC_LARGE_SIZES-1) countLargeAlloc[num_pages]++;
	else countLargeAlloc[GC_LARGE_SIZES - 1]++;
	if ( contiguousFreePages > maxContiguousFreePages )
	  maxContiguousFreePages = contiguousFreePages;
	contiguousFreePages = 0;
	i = i + num_pages;       // skip to next object or free page
      }
    }
    if ( contiguousFreePages > maxContiguousFreePages )
      maxContiguousFreePages = contiguousFreePages;

    VM.sysWrite("Large Objects Allocated - by num pages\n");
    for (i = 0; i < GC_LARGE_SIZES-1; i++) {
      VM.sysWrite("pages ");
      VM.sysWrite(i);
      VM.sysWrite(" count ");
      VM.sysWrite(countLargeAlloc[i]);
      VM.sysWrite("\n");
    }
    VM.sysWrite(countLargeAlloc[GC_LARGE_SIZES-1]);
    VM.sysWrite(" large objects ");
    VM.sysWrite(GC_LARGE_SIZES-1);
    VM.sysWrite(" pages or more.\n");
    VM.sysWrite(countLargeAlloc[0]);
    VM.sysWrite(" Large Object Space pages are free.\n");
    VM.sysWrite(maxContiguousFreePages);
    VM.sysWrite(" is largest block of contiguous free pages.\n");
    VM.sysWrite(countLargeOld);
    VM.sysWrite(" large objects are old.\n");
    
  }  // countLargeObjects()

  public int freeSpace () {
    int total = 0;
    for (int i = 0 ; i < largeSpacePages;) {
      if (largeSpaceAlloc[i] == 0) {
	total++;
	i++;
      }
      else i = i + largeSpaceAlloc[i];
    }
    return (total * pageSize);       // number of bytes free in largespace
  }

}
