/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

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

public final class FreeListVMResource extends VMResource implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  FreeListVMResource(byte space_, String vmName, VM_Address vmStart, VM_Extent bytes, byte status) {
    super(space_, vmName, vmStart, bytes, (byte) (VMResource.IN_VM | status));
    freeList = new GenericFreeList(Conversions.bytesToPages(bytes.toInt()));
    gcLock = new Lock("NewFreeListVMResrouce.gcLock");
    mutatorLock = new Lock("NewFreeListVMResrouce.gcLock");
  }


 /**
   * Acquire a number of contigious pages from the virtual memory resource.
   *
   * @param request The number of pages requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public final VM_Address acquire(int pages, MemoryResource mr) {
    return acquire(pages, mr, true);
  }
  public final VM_Address acquire(int pages, MemoryResource mr, byte tag) {
    return acquire(pages, mr, tag, true);
  }
  public final VM_Address acquire(int pages, MemoryResource mr, byte tag,
                                  boolean chargeMR) {
    VM_Address rtn = acquire(pages, mr, chargeMR);
    setTag(rtn, pages, tag);
    return rtn;
  }
  public final VM_Address acquire(int pages, MemoryResource mr,
                                  boolean chargeMR) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(mr != null);
    if (chargeMR && !mr.acquire(pages))
      return VM_Address.zero();
    lock();
    int startPage = freeList.alloc(pages);
    if (startPage == -1) {
      unlock();
      if (chargeMR)
        mr.release(pages);
      VM_Interface.getPlan().poll(true, mr);
      return VM_Address.zero();
    }
    pagetotal += pages;
    unlock();
    VM_Address rtn = start.add(Conversions.pagesToBytes(startPage));
    LazyMmapper.ensureMapped(rtn, pages);
    acquireHelp(rtn, pages);
    return rtn;
  }

  public VM_Address acquire(int request) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    return VM_Address.zero();
  }

  public final void release(VM_Address addr, MemoryResource mr) {
    release(addr, mr, true);
  }
  public final void release(VM_Address addr, MemoryResource mr, byte tag) {
    release(addr, mr, tag, true);
  }
  public final void release(VM_Address addr, MemoryResource mr, byte tag,
                            boolean chargeMR) {
    clearTag(addr, getSize(addr), tag);
    release(addr, mr, chargeMR);
  }
  public final void release(VM_Address addr, MemoryResource mr, 
                            boolean chargeMR) {
    lock();
    int offset = addr.diff(start).toInt();
    int startPage = Conversions.bytesToPages(offset);
    int freedPages = freeList.free(startPage);
    pagetotal -= freedPages;
    if (chargeMR)
      mr.release(freedPages);
    //-if RVM_WITH_GCSPY
    if (VM_Interface.GCSPY) {
      int bytes =  Conversions.pagesToBytes(freedPages);
      Plan.releaseVMResource(addr, bytes);
    }
    //-endif
    unlock();
  }
  
  public final int getSize(VM_Address addr) {
    int offset = addr.diff(start).toInt();
    int page = Conversions.bytesToPages(offset);
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
  private GenericFreeList freeList;
  private VM_Address cursor;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators
}
