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
 * This class implements a monotone virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>BLOCK</code><p>
 *
 * Instances of this class respond to requests for virtual address
 * space by monotonically consuming the resource.
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class MonotoneVMResource extends VMResource implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  public final static boolean PROTECT_ON_RELEASE = false; // true;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  MonotoneVMResource(String vmName, MemoryResource mr, VM_Address vmStart, EXTENT bytes, byte status) {
    super(vmName, vmStart, bytes, (byte) (VMResource.IN_VM | status));
    cursor = start;
    sentinel = start.add(bytes);
    memoryResource = mr;
    gcLock = new Lock("MonotoneVMResrouce.gcLock");
    mutatorLock = new Lock("MonotoneVMResrouce.mutatorLock");
  }


 /**
   * Acquire a number of contigious blocks from the virtual memory resource.
   *
   * @param request The number of blocks requested
   * @return The address of the start of the virtual memory region, or
   * zero on failure.
   */
  public VM_Address acquire(int blockRequest) {
    return acquire(blockRequest, memoryResource);
  }

  public VM_Address acquire(int blockRequest, MemoryResource memoryResource) {

    if ((memoryResource != null) 
	&& (!memoryResource.acquire(Conversions.blocksToPages(blockRequest)))) 
      return VM_Address.zero();
    lock();
    int bytes = Conversions.blocksToBytes(blockRequest);
    VM_Address tmpCursor = cursor.add(bytes);
    if (tmpCursor.GT(sentinel)) {
      unlock();
      return VM_Address.zero();
    } else {
      VM_Address oldCursor = cursor;
      cursor = tmpCursor;
      unlock();
      LazyMmapper.ensureMapped(oldCursor, blockRequest);
      // Memory.zero(oldCursor, bytes);
      Memory.zeroPages(oldCursor, bytes);
      return oldCursor;
    }
  }

  public void release() {
    // Unmapping is useful for being a "good citizen" and for debugging
    if (PROTECT_ON_RELEASE)
      LazyMmapper.protect(start, Conversions.bytesToBlocks(cursor.diff(start).toInt()));
    cursor = start;
  }

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

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private fields and methods
  //

  protected VM_Address cursor;
  protected VM_Address sentinel;
  protected MemoryResource memoryResource;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators

}
