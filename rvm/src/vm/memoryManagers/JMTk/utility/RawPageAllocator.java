/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
/**
 * This class allows raw pages to be allocated.  Such pages are used
 * for untyped memory manager meta-data (eg sequential store buffers,
 * work queues etc) and live outside the normal heap.<p>
 *
 * Raw pages are accounted for via a memoryResource and are allocated
 * within the context of a VMResource.<p>
 *
 * This implmentation uses the GenericFreeList to manage pages on an
 * alloc-free basis.<p>
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 *
 */
final class RawPageAllocator implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$";
 
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   */
  RawPageAllocator(MonotoneVMResource vmr, MemoryResource mr) {
    memoryResource = mr;
    vmResource = vmr;
    blocks = vmResource.getBlocks();
    freeList = new GenericFreeList(Conversions.blocksToPages(blocks));
  }
  
  /**
   * Allocate <code>pages</code> pages of raw memory.  Return the
   * address of the page.
   *
   * @param pages  The number of pages to be allocated
   * @return The address of the first byte of the allocated pages
   */
  public VM_Address alloc(int pages) {
    memoryResource.acquire(pages);
    lock.acquire();
    if (base.isZero()) {
      base = vmResource.acquire(blocks, null);
    }
    int pageIndex = freeList.alloc(pages);
    lock.release();
    if (pageIndex == -1) {
      VM.sysWriteln("RawPageAllocator: unable to satisfy raw page allocation request");
      VM._assert(false);
    }
    return base.add(Conversions.pagesToBytes(pageIndex));
  }

  /**
   * Free previously allocated page or pages.
   *
   * @param start The address of the start of the allocated region
   * which is to be freed.
   * @return The number of pages freed.
   */
  public int free(VM_Address start) {
    lock.acquire();
    int freed = freeList.free(Conversions.bytesToPages(start.diff(base).toInt()));
    lock.release();
    memoryResource.release(Conversions.pagesToBlocks(freed));
    return freed;
  }

  /**
   * Return the size of the specified contiguously allocated region of
   * raw pages.
   *
   * @param start The address of the start of the allocated region.
   * @return The number of pages in the allocated region.
   */
  public int pages(VM_Address start) {
    return freeList.size(Conversions.bytesToPages(start.diff(base).toInt()));
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //
  private VM_Address base;
  private int blocks;
  private MemoryResource memoryResource;
  private MonotoneVMResource vmResource;
  private GenericFreeList freeList;
  private Lock lock = new Lock("RawPageAllocator");
}
