/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.*;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a "free list" virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>PAGE</code><p>
 *
 * Instances of this class respond to requests for virtual address
 * space by consuming the resource.  Consumers may also free resources
 * for subsequent resource.
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */

public final class FreeListVMResource extends VMResource implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  public FreeListVMResource(byte space, String vmName, Address vmStart, Extent bytes, byte status) {
    this(space, vmName, vmStart, bytes, status, 0);
  }
  public FreeListVMResource(byte space, String vmName, Address vmStart, Extent bytes, byte status, int metaDataPagesPerRegion) {
    super(space, vmName, vmStart, bytes, (byte) (VMResource.IN_VM | status));
    if (metaDataPagesPerRegion > 0) 
      freeList = new GenericFreeList(Conversions.bytesToPages(bytes), EmbeddedMetaData.PAGES_IN_REGION);
    else
      freeList = new GenericFreeList(Conversions.bytesToPages(bytes));
    reserveMetaData(metaDataPagesPerRegion);
    gcLock = new Lock("FreeListVMResrouce.gcLock");
    mutatorLock = new Lock("FreeListVMResrouce.mutatorLock");
  }

  /**
   * Reserve virtual address space for meta-data.
   *
   * @param metaDataPagesPerRegion The number of pages of meta data
   * required to be reserved for each region of allocated data.
   */
  private final void reserveMetaData(int metaDataPagesPerRegion) {
    highWaterMark = 0;
    if (metaDataPagesPerRegion > 0) {
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(start.toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toAddress().EQ(start));
      Extent size = end.diff(start).toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toExtent();
      Address cursor = start.add(size);
      while (cursor.GT(start)) {
        cursor = cursor.sub(EmbeddedMetaData.BYTES_IN_REGION);
        int unit = cursor.diff(start).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
        int tmp = freeList.alloc(metaDataPagesPerRegion, unit);
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(tmp == unit);
      }
    }
  }

 /**
   * Acquire a number of contigious pages from the virtual memory resource.
   *
   * @param request The number of pages requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public final Address acquire(int pages, MemoryResource mr) {
    return acquire(pages, mr, true);
  }
  static int totalmeta = 0;
  public final Address acquire(int pages, MemoryResource mr,
                                  boolean chargeMR) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(mr != null);
    if (chargeMR && !mr.acquire(pages))
      return Address.zero();
    lock();
    int startPage = freeList.alloc(pages);
    if (startPage > highWaterMark) {
      if ((startPage ^ highWaterMark) > EmbeddedMetaData.PAGES_IN_REGION) {
        int regions = 1 + ((startPage - highWaterMark)>>EmbeddedMetaData.LOG_PAGES_IN_REGION);
        int metapages = regions * metaDataPagesPerRegion;
	totalmeta += metapages;
	mr.consume(metapages);
	highWaterMark = startPage;
      }
    }
    if (startPage == -1) {
      unlock();
      if (chargeMR)
        mr.release(pages);
      VM_Interface.getPlan().poll(true, mr);
      return Address.zero();
    }
    pagetotal += pages;
    Address rtn = start.add(Conversions.pagesToBytes(startPage));
    unlock();
    LazyMmapper.ensureMapped(rtn, pages);
    acquireHelp(rtn, pages);
    return rtn;
    }

  public Address acquire(int request) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    return Address.zero();
  }

  public Address getHighWater() {
    return start.add(Extent.fromInt(highWaterMark<<LOG_BYTES_IN_PAGE));
  }

  public final void release(Address addr, MemoryResource mr) {
    release(addr, mr, true);
  }
  public final void release(Address addr, MemoryResource mr, byte tag) {
    release(addr, mr, tag, true);
  }
  public final void release(Address addr, MemoryResource mr, byte tag,
                            boolean chargeMR) {
    release(addr, mr, chargeMR);
  }
  public final void release(Address addr, MemoryResource mr, 
                            boolean chargeMR) {
    lock();
    int startPage = Conversions.bytesToPages(addr.diff(start).toWord().toExtent());
    int freedPages = freeList.free(startPage);
    pagetotal -= freedPages;
    if (chargeMR)
      mr.release(freedPages);
    if (VM_Interface.GCSPY) {
      Extent bytes =  Conversions.pagesToBytes(freedPages);
      Plan.releaseVMResource(addr, bytes);
    }
    unlock();
  }
  
  public final int getSize(Address addr) {
    int page = Conversions.bytesToPages(addr.diff(start).toWord().toExtent());
    return freeList.size(page);
  }

  /****************************************************************************
   *
   * Private fields and methods
   */
  /**
   * Acquire the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void lock() {
    if (Plan.gcInProgress())
      gcLock.acquire();
    else
      mutatorLock.acquire();
  }

  /**
   * Release the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void unlock() {
    if (Plan.gcInProgress())
      gcLock.release();
    else
      mutatorLock.release();
  }

  private int pagetotal;
  private int metaDataPagesPerRegion;
  private int highWaterMark;
  private GenericFreeList freeList;
  private Address cursor;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators
}
