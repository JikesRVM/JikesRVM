/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.utility.alloc;

import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implements core functionality for a generic
 * large object allocator. The shared VMResource used by each instance
 * is the point of global synchronization, and synchronization only
 * occurs at the granularity of aquiring (and releasing) chunks of
 * memory from the VMResource.  Subclasses may require finer grained
 * synchronization during a marking phase, for example.<p>
 *
 * This is a first cut implementation, with plenty of room for
 * improvement...
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class LargeObjectAllocator extends Allocator implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 
  
  /****************************************************************************
   *
   * Class variables
   */
  protected static final Word PAGE_MASK = Word.fromIntSignExtend(~(BYTES_IN_PAGE - 1));

  /****************************************************************************
   *
   * Instance variables
   */
  protected LargeObjectSpace space;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The space with which this large object allocator
   * will be associated.
   */
  public LargeObjectAllocator(LargeObjectSpace space) {
    this.space = space;
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space for an object
   *
   * @param bytes The number of bytes allocated
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated cell Will
   * not return zero.
   */
  public final Address alloc(int bytes, int align, int offset) 
    throws NoInlinePragma {
    Address cell = allocSlow(bytes, align, offset, false);
    postAlloc(cell);
    return alignAllocation(cell, align, offset);
  }

  abstract protected void postAlloc(Address cell);
    
  /**
   * Allocate a large object.  Large objects are directly allocted and
   * freed in page-grained units via the vm resource.  This routine
   * returned zeroed memory.
   *
   * @param bytes The required size of this space in bytes.
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param inGC If true, this allocation is occuring with respect to
   * a space that is currently being collected.
   * @return The address of the start of the newly allocated region at
   * least <code>bytes</code> bytes in size.
   */
  final protected Address allocSlowOnce (int bytes, int align, int offset,
                                            boolean inGC) {
    int header = superPageHeaderSize() + cellHeaderSize();  //must be multiple of MIN_ALIGNMENT
    int maxbytes = getMaximumAlignedSize(bytes + header, align);
    int pages = (maxbytes + BYTES_IN_PAGE - 1) >>LOG_BYTES_IN_PAGE;
    Address sp = space.acquire(pages);
    if (sp.isZero()) return sp;
    Address cell = sp.add(header);
    return cell;
  }

  /****************************************************************************
   *
   * Freeing
   */

  /**
   * Free a cell.  If the cell is large (own superpage) then release
   * the superpage, if not add to the super page's free list and if
   * all cells on the superpage are free, then release the
   * superpage.
   *
   * @param cell The address of the first byte of the cell to be freed
   */
  public final void free(Address cell)
    throws InlinePragma {
    space.release(getSuperPage(cell));
  }

  /****************************************************************************
   *
   * Superpages
   */

  abstract protected int superPageHeaderSize();
  abstract protected int cellHeaderSize();

  /**
   * Return the superpage for a given cell.  If the cell is a small
   * cell then this is found by masking the cell address to find the
   * containing page.  Otherwise the first word of the cell contains
   * the address of the page.
   *
   * @param cell The address of the first word of the cell (exclusive
   * of any sub-class specific metadata).
   * @return The address of the first word of the superpage containing
   * <code>cell</code>.
   */
  public static final Address getSuperPage(Address cell)
    throws InlinePragma {
    return cell.toWord().and(PAGE_MASK).toAddress();
  }

  /****************************************************************************
   *
   * Miscellaneous
   */
  public void show() {
  }
}

