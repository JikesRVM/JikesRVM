/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) Copyright IBM Corp. 2002
 */

package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
import org.mmtk.utility.*;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.gcspy.AbstractDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public class MonotoneVMResource extends VMResource implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  public final static boolean ZERO_ON_RELEASE = false;

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  public MonotoneVMResource(byte space_, String vmName, MemoryResource mr, 
                     Address vmStart, Extent bytes, byte status) {
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
  public Address acquire(int pageRequest) {
    return acquire(pageRequest, memoryResource);
  }

  public Address acquire(int pageRequest, MemoryResource memoryResource) {
    if ((memoryResource != null) && !memoryResource.acquire(pageRequest)) {
      if (Options.verbose >= 5) Log.writeln("polling caused gc - returning gc and retry");
      return Address.zero();
    }
    lock();
    Extent bytes = Conversions.pagesToBytes(pageRequest);
    Address tmpCursor = cursor.add(bytes);
    if (tmpCursor.GT(sentinel)) {
      unlock();
      VM_Interface.getPlan().poll(true, memoryResource);
      return Address.zero();
    } else {
      Address oldCursor = cursor;
      cursor = tmpCursor;
      unlock();
      acquireHelp(oldCursor, pageRequest);
      LazyMmapper.ensureMapped(oldCursor, pageRequest);
      Memory.zero(oldCursor, bytes);
      // Memory.zeroPages(oldCursor, bytes);
      if (VM_Interface.GCSPY)
        Plan.acquireVMResource(start, cursor, bytes);
      return oldCursor;
    }
  }

  public void release() {
    // Unmapping is useful for being a "good citizen" and for debugging
    Extent bytes = cursor.diff(start).toWord().toExtent();
    int pages = Conversions.bytesToPages(bytes);
    if (ZERO_ON_RELEASE) 
        Memory.zero(start, bytes);
    if (Options.protectOnRelease)
      LazyMmapper.protect(start, pages);
    releaseHelp(start, pages);
    cursor = start;
    if (VM_Interface.GCSPY)
      Plan.releaseVMResource(start, bytes);
  }

  /**
   * GCSpy needs to know the extent of this resource
   * @return the cursor
   */
 public Address getCursor() { return cursor; }

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
    return Conversions.bytesToPages(cursor.diff(start).toWord().toExtent());
  }

  /**
   * Gather data for GCSpy
   * Until we can sweep through this space (either by laying out arrays and
   * scalars in the same direction, or by segregating scalars and arrays), all
   * we can report is the range of the space.
   * @param event The GCSpy event
   * @param driver the GCSpy driver for this space
   */
  public void gcspyGatherData(int event, AbstractDriver driver) {
    driver.setRange(event, getStart(), getCursor());
  }

  /****************************************************************************
   *
   * Private fields and methods
   */

  protected Address cursor;
  protected Address sentinel;
  public final MemoryResource memoryResource;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators

}
