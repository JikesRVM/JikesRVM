/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.heap.FreeListVMResource;
import org.mmtk.utility.heap.MemoryResource;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class RefCountLOSLocal extends LargeObjectAllocator
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  public RefCountLOSLocal(FreeListVMResource vmr, MemoryResource mr) {
    super(vmr, mr);
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
  protected final void postAlloc(Address cell) throws InlinePragma {};

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
    return 0;
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
}
