/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
/**
 * This class allows raw pages to be allocated.  Such pages are used
 * for untyped memory manager meta-data (eg sequential store buffers,
 * work queues etc) and live outside the normal heap.<p>
 *
 * Raw pages are accounted for via a memoryResource and are lazily allocated
 * but never deallocated within the context of a VMResource.<p>
 *
 * This implmentation uses the GenericFreeList to manage pages on an
 * alloc-free basis.<p>
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 *
 */
public final class RawPageAllocator implements Constants, VM_Uninterruptible {
   public final static String Id = "$Id$";
 
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   */
  public RawPageAllocator(MonotoneVMResource vmr, MemoryResource mr) {
    memoryResource = mr;
    vmResource = vmr;
    totalPages = vmResource.getPages();
    freeList = new GenericFreeList(totalPages);
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
      base = vmResource.acquire(pages, null);
      top = base.add(Conversions.pagesToBytes(pages));
    }
    int pageIndex = freeList.alloc(pages);
    if (pageIndex == -1) {
      Log.writeln("RawPageAllocator: unable to satisfy raw page allocation request");
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    }
    VM_Address result = base.add(Conversions.pagesToBytes(pageIndex));
    VM_Address resultEnd = result.add(Conversions.pagesToBytes(pages));
    if (resultEnd.GT(top)) {
      int pagesNeeded = Conversions.bytesToPages(resultEnd.diff(result).toWord().toExtent()); // rounded up
      VM_Address tmp = vmResource.acquire(pagesNeeded, null);
      top = tmp.add(Conversions.pagesToBytes(pagesNeeded));
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(resultEnd.LE(top));
    }
    lock.release();
    return result;
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
    int freed = freeList.free(Conversions.bytesToPages(start.diff(base).toWord().toExtent()));
    lock.release();
    memoryResource.release(freed);
    return freed;
  }

  /**
   * Return the size of the specified contiguously allocated region of
   * raw pages.
   *
   * @param start The address of the start of the allocated region.
   * @return The number of pages in the allocated region.
   */
  public int pages (VM_Address start) {
    return freeList.size(Conversions.bytesToPages(start.diff(base).toWord().toExtent()));
  }

  /****************************************************************************
   *
   * Private fields and methods
   */
  private VM_Address base;       // beginning of available region
  private VM_Address top;        // end of available region
  private int totalPages;       // number of pages in entire VM region
  private MemoryResource memoryResource;
  private MonotoneVMResource vmResource;
  private GenericFreeList freeList;
  private Lock lock = new Lock("RawPageAllocator");
}
