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
 * This class implements a monotone virtual memory resource.  The unit of
 * managment for virtual memory resources is the <code>PAGE</code><p>
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

  public final static boolean ZERO_ON_RELEASE = false;

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  MonotoneVMResource(byte space_, String vmName, MemoryResource mr, 
                     VM_Address vmStart, VM_Extent bytes, byte status) {
    super(space_, vmName, vmStart, bytes, (byte) (VMResource.IN_VM | status));
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
  public VM_Address acquire(int pageRequest) {
    return acquire(pageRequest, memoryResource);
  }

  public VM_Address acquire(int pageRequest, MemoryResource memoryResource) {
    if ((memoryResource != null) && !memoryResource.acquire(pageRequest)) {
      if (Options.verbose >= 5) Log.writeln("polling caused gc - returning gc and retry");
      return VM_Address.zero();
    }
    lock();
    int bytes = Conversions.pagesToBytes(pageRequest);
    VM_Address tmpCursor = cursor.add(bytes);
    if (tmpCursor.GT(sentinel)) {
      unlock();
      VM_Interface.getPlan().poll(true, memoryResource);
      return VM_Address.zero();
    } else {
      VM_Address oldCursor = cursor;
      cursor = tmpCursor;
      unlock();
      acquireHelp(oldCursor, pageRequest);
      LazyMmapper.ensureMapped(oldCursor, pageRequest);
      Memory.zero(oldCursor, VM_Extent.fromInt(bytes));
      // Memory.zeroPages(oldCursor, bytes);
      return oldCursor;
    }
  }

  public void release() {
    // Unmapping is useful for being a "good citizen" and for debugging
    int bytes = cursor.diff(start).toInt();
    int pages = Conversions.bytesToPages(bytes);
    if (ZERO_ON_RELEASE) 
        Memory.zero(start, VM_Extent.fromInt(bytes));
    if (Options.protectOnRelease)
      LazyMmapper.protect(start, pages);
    releaseHelp(start, pages);
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

  public int getUsedPages () {
    return Conversions.bytesToPages(cursor.diff(start).toInt());
  }

  /****************************************************************************
   *
   * Private fields and methods
   */

  protected VM_Address cursor;
  protected VM_Address sentinel;
  public final MemoryResource memoryResource;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators

}
