/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.heap;

import org.mmtk.utility.*;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.Assert;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public final class RawPageAllocator implements Constants, Uninterruptible {
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
  public Address alloc(int pages) {
    memoryResource.acquire(pages);
    lock.acquire();
    if (base.isZero()) {
      base = vmResource.acquire(pages, null);
      top = base.add(Conversions.pagesToBytes(pages));
    }
    int pageIndex = freeList.alloc(pages);
    if (pageIndex == -1) {
      Log.writeln("RawPageAllocator: unable to satisfy raw page allocation request");
      Assert._assert(false);
    }
    Address result = base.add(Conversions.pagesToBytes(pageIndex));
    Address resultEnd = result.add(Conversions.pagesToBytes(pages));
    if (resultEnd.GT(top)) {
      int pagesNeeded = Conversions.bytesToPages(resultEnd.diff(result).toWord().toExtent()); // rounded up
      Address tmp = vmResource.acquire(pagesNeeded, null);
      top = tmp.add(Conversions.pagesToBytes(pagesNeeded));
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(resultEnd.LE(top));
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
  public int free(Address start) {
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
  public int pages (Address start) {
    return freeList.size(Conversions.bytesToPages(start.diff(base).toWord().toExtent()));
  }

  /****************************************************************************
   *
   * Private fields and methods
   */
  private Address base;       // beginning of available region
  private Address top;        // end of available region
  private int totalPages;       // number of pages in entire VM region
  private MemoryResource memoryResource;
  private MonotoneVMResource vmResource;
  private GenericFreeList freeList;
  private Lock lock = new Lock("RawPageAllocator");
}
