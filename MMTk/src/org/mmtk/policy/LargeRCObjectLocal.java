/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.gcspy.drivers.TreadmillDriver;
import org.mmtk.utility.Treadmill;
import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to reference-counted large object.  Therefore
 * instances must not be shared across truely concurrent threads
 * (CPUs).  Rather, one or more instances of this class should be
 * bound to each CPU.  The shared VMResource used by each instance is
 * the point of global synchronization, and synchronization only
 * occurs at the granularity of aquiring (and releasing) chunks of
 * memory from the VMResource.
 *
 * If there are C CPUs and T LargeObjectSpaces, there must be C X T
 * instances of this class, one for each CPU, LargeObjectSpace pair.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class LargeRCObjectLocal extends LargeObjectAllocator
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  /****************************************************************************
   *
   * Instance variables
   */
  private LargeObjectSpace space;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The large object space to which this thread instance
   * is bound.
   */
  public LargeRCObjectLocal(LargeObjectSpace space) {
    super(space);
    this.space = space;
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
  protected final void postAlloc (Address cell) throws InlinePragma { };

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. Nothing to be done here
   */
  public final void prepare() {}

  /**
   * Finish up after a collection.  Nothing to be done
   */
  public void release() { }

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
  protected final int superPageHeaderSize() throws InlinePragma { return 0; }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   *
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize() throws InlinePragma { return 0; }
}
