/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This class implements a "free list" virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>BLOCK</code><p>
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

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  FreeListVMResource(String vmName, VM_Address vmStart, EXTENT bytes, byte status) {
    super(vmName, vmStart, bytes, (byte) (VMResource.IN_VM | status));
    freeList = new GenericFreeList(Conversions.bytesToPages(bytes));
    gcLock = new Lock("NewFreeListVMResrouce.gcLock");
    mutatorLock = new Lock("NewFreeListVMResrouce.gcLock");
  }


 /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of blocks requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public final VM_Address acquire(int pages, MemoryResource mr) {

    if (VM.VerifyAssertions) VM._assert(mr != null);
    while (!mr.acquire(pages));

    lock();
    int page = freeList.alloc(pages);
    if (page == -1) {
      unlock();
      mr.release(pages);
      return VM_Address.zero();
    }
    pagetotal += pages;
    unlock();
    VM_Address rtn = start.add(Conversions.pagesToBytes(page));
    LazyMmapper.ensureMapped(rtn, Conversions.pagesToBlocks(pages));
    return rtn;
  }

  public VM_Address acquire(int request) {
    if (VM.VerifyAssertions) VM._assert(false);
    return VM_Address.zero();
  }

  public final void release(VM_Address addr, MemoryResource mr) {
    lock();
    int offset = addr.diff(start).toInt();
    int page = Conversions.bytesToPages(offset);
    int freedpages = freeList.free(page);
    pagetotal -= freedpages;
    mr.release(freedpages);
    unlock();
  }
  
  public final int getSize(VM_Address addr) {
    int offset = addr.diff(start).toInt();
    int page = Conversions.bytesToPages(offset);
    return freeList.size(page);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //
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
