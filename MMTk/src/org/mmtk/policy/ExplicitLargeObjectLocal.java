/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.policy;

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.LargeObjectAllocator;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.Constants;
import org.mmtk.utility.DoublyLinkedList;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Each instance of this class is intended to provide fast,
 * unsynchronized access to explicitly collected objects.  Therefore
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
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class ExplicitLargeObjectLocal extends LargeObjectAllocator
  implements Constants {

  /****************************************************************************
   * 
   * Class variables
   */

  /****************************************************************************
   * 
   * Instance variables
   */
  DoublyLinkedList cells;

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
  public ExplicitLargeObjectLocal(LargeObjectSpace space) {
    super(space);
    this.space = space;
    this.cells = new DoublyLinkedList(LOG_BYTES_IN_PAGE, true, this);
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
  protected final void postAlloc (Address cell) throws InlinePragma { 
    cells.add(DoublyLinkedList.payloadToNode(cell));
  };

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a collection. Nothing to be done here
   */
  public final void prepare() {}

  /**
   * Finish up after a collection. Nothing to be done
   */
  public void release() { }

  /**
   * Free an object
   * 
   * @param space The space the object is allocated in.
   * @param object The object to be freed.
   */
  public static final void free(LargeObjectSpace space, ObjectReference object)
    throws InlinePragma {
    Address cell = getSuperPage(VM.objectModel.refToAddress(object));
    ((ExplicitLargeObjectLocal)DoublyLinkedList.getOwner(cell)).cells.remove(cell);
    space.release(cell);
  }
  
  /**
   * Perform a linear scan through the objects allocated by this bump pointer.
   * 
   * @param scanner The scan object to delegate scanning to.
   */
  public void linearScan(LinearScan scanner) throws InlinePragma {
    Address cell = cells.getHead();
    while (!cell.isZero()) {
      ObjectReference current = VM.objectModel.getObjectFromStartAddress(cell.plus(superPageHeaderSize())); 
      scanner.scan(current);
      cell = cells.getNext(cell);
    }
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
   * @return The size of the per-superpage header required by this
   * system.
   */
  protected final int superPageHeaderSize() throws InlinePragma { 
    return DoublyLinkedList.headerSize(); 
  }

  /**
   * Return the size of the per-cell header for cells of a given class
   * size.
   * 
   * @return The size of the per-cell header for cells of a given class
   * size.
   */
  protected final int cellHeaderSize() throws InlinePragma { return 0; }
}
