/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truely concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of aquiring (and releasing) chunks of memory from the VMResource.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class ThreadmillThread extends LargeObjectAllocator
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private TreadmillSpace space;
  private Lock treadmillLock;
  public VM_Address treadmillFromHead;
  public VM_Address treadmillToHead;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Constructor
   *
   * @param space The treadmill space to which this thread instance is
   * bound.  The space's VMResource and MemoryResource are used.
   */
  TreadmillThread(TreadmillSpace space_) {
    super(space_.getVMResource(), space_.getMemoryResource());
    space = space_;
    treadmillLock = new Lock("TreadmillThread.treadmillLock");
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   */
  protected final void postAlloc(VM_Address cell) 
    throws VM_PragmaInline {
    space.postAlloc(cell,  this);
  };

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //

  /**
   * Prepare for a collection.  Clear the treadmill to-space head and
   * prepare the collector.  If paranoid, perform a sanity check.
   *
   * @param vm Unused
   * @param mr Unused
   */
  public final void prepare() {
//     if (PARANOID)
//       sanity();
    treadmillToHead = VM_Address.zero();
  }

  /**
   * Finish up after a collection.
   *
   * @param vm Unused
   * @param mr Unused
   */
  public void release() {
    // sweep the large objects
    sweepLargePages();
  }

  /**
   * Sweep through the large pages, releasing all superpages on the
   * "from space" treadmill.
   */
  public final void sweepLargePages() {
    VM_Address cell = treadmillFromHead;
    while (!cell.isZero()) {
      VM_Address next = TreadmillSpace.getNextTreadmill(cell);
      VM_Address sp =  getSuperPage(cell, false);
      free(cell, sp, LARGE_SIZE_CLASS);
      cell = next;
    }
    treadmillFromHead = treadmillToHead;
    treadmillToHead = VM_Address.zero();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Treadmill
  //

  /**
   * Set the head of the from-space threadmill
   *
   * @param cell The new head of the from-space treadmill
   */
  public final void setTreadmillFromHead(VM_Address cell)
    throws VM_PragmaInline {
    treadmillFromHead = cell;
  }

  /**
   * Get the head of the from-space treadmill
   *
   * @return The head of the from-space treadmill
   */
  public final VM_Address getTreadmillFromHead()
    throws VM_PragmaInline {
    return treadmillFromHead;
  }

  /**
   * Set the head of the to-space threadmill
   *
   * @param cell The new head of the to-space treadmill
   */
  public final void setTreadmillToHead(VM_Address cell)
    throws VM_PragmaInline {
    treadmillToHead = cell;
  }

  /**
   * Get the head of the to-space treadmill
   *
   * @return The head of the to-space treadmill
   */
  public final VM_Address getTreadmillToHead()
    throws VM_PragmaInline {
    return treadmillToHead;
  }

  /**
   * Lock the treadmills
   */
  public final void lockTreadmill()
    throws VM_PragmaInline {
    treadmillLock.acquire();
  }

  /**
   * Unlock the treadmills
   */
  public final void unlockTreadmill()
    throws VM_PragmaInline {
    treadmillLock.release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous size-related methods
  //
  /**
   * Return the size of the per-superpage header required by this
   * system.  In this case it is just the underlying superpage header
   * size.
   *
   * @param sizeClass The size class of the cells contained by this
   * superpage.
   * @return The size of the per-superpage header required by this
   * system.
   */
  protected final int superPageHeaderSize()
    throws VM_PragmaInline {
    return BASE_SP_HEADER_SIZE + TreadmillSpace.TREADMILL_HEADER_SIZE;
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @param sizeClass The size class in question.
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize(int sizeClass)
    throws VM_PragmaInline {
    return 0;
  }
}
