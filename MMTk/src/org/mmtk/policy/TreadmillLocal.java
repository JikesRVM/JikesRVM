/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.Treadmill;
import org.mmtk.utility.heap.VMResource;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.mmtk.utility.gcspy.TreadmillDriver;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to a treadmill.  Therefore instances must not
 * be shared across truely concurrent threads (CPUs).  Rather, one or
 * more instances of this class should be bound to each CPU.  The
 * shared VMResource used by each instance is the point of global
 * synchronization, and synchronization only occurs at the granularity
 * of aquiring (and releasing) chunks of memory from the VMResource.
 *
 * If there are C CPUs and T TreadmillSpaces, there must be C X T
 * instances of this class, one for each CPU, TreadmillSpace pair.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class TreadmillLocal extends LargeObjectAllocator
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private TreadmillSpace space;
  public final Treadmill treadmill;  // per-processor

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The treadmill space to which this thread instance is
   * bound.  The space's VMResource and MemoryResource are used.
   */
  public TreadmillLocal(TreadmillSpace space_) {
    super(space_.getVMResource(), space_.getMemoryResource());
    space = space_;
    treadmill = new Treadmill(VMResource.BYTES_IN_PAGE, true);
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   *  This is called each time a cell is alloced (i.e. if a cell is
   *  reused, this will be called each time it is reused in the
   *  lifetime of the cell, by contrast to initializeCell, which is
   *  called exactly once.).
   *
   * @param cell The newly allocated cell
   */
  protected final void postAlloc (Address cell) 
    throws InlinePragma {
    space.postAlloc(cell,  this);
  };

  /****************************************************************************
   *
   * Collection
   */

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
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(treadmill.toSpaceEmpty());
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
    while (true) {
      Address cell = treadmill.popFromSpace();
      if (cell.isZero()) break;
      free(cell);
    }
    treadmill.flip();
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(treadmill.toSpaceEmpty());
  }


  /****************************************************************************
   *
   * Miscellaneous size-related methods
   */
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
    throws InlinePragma {
    return Treadmill.headerSize();
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize()
    throws InlinePragma {
    return 0;
  }

  /**
   * Gather data for GCSpy
   * @param event the gc event
   * @param gcspyDriver the GCSpy space driver
   * @param tospace gather from tospace?
   */
  public void gcspyGatherData(int event, TreadmillDriver tmDriver, boolean tospace) {
    treadmill.gcspyGatherData(event, tmDriver, tospace);
  }
}
