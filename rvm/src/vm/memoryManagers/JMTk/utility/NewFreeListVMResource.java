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
public final class NewFreeListVMResource extends VMResource implements Constants {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  NewFreeListVMResource(String vmName, VM_Address vmStart, EXTENT bytes, byte status) {
    super(vmName, vmStart, bytes, status);
    freeList = new GenericFreeList(Conversions.bytesToBlocks(bytes));
  }


 /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of blocks requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public final VM_Address acquire(int blocks, MemoryResource mr) {
    lock();
    mr.acquire(Conversions.blocksToPages(blocks));
    int blk = freeList.alloc(blocks);
    if (blk == -1) {
      // FIXME Is this really how we want to deal with failure?
      return VM_Address.zero();
    }
    unlock();
    return start.add(Conversions.blocksToBytes(blk));
  }

  public final void release(VM_Address addr, MemoryResource mr) {
    lock();
    int offset = addr.diff(start).toInt();
    int blk = Conversions.bytesToBlocks(offset);
    mr.release(freeList.free(blk));
    unlock();
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

  private GenericFreeList freeList;
  private VM_Address cursor;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators
}
