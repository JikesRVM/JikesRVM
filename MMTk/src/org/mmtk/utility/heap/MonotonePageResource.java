/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Options;
import org.mmtk.utility.Memory;
import org.mmtk.utility.Log;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the allocation of pages for a space.  When a
 * page is requested by the space both a page budget and the use of
 * virtual address space are checked.  If the request for space can't
 * be satisfied (for either reason) a GC may be triggered.<p>
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class MonotonePageResource extends PageResource 
  implements Constants, Uninterruptible {

  /****************************************************************************
   *
   * Instance variables
   */
  private Address cursor;
  private Address sentinel;

  /**
   * Constructor
   *
   * Contiguous monotone resource.  The address range is pre-defined at
   * initializtion time and is immutable.
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   */
  public MonotonePageResource(int pageBudget, Space space, Address start, 
			      Extent bytes) {
    super(pageBudget, space, start);
    this.cursor = start;
    this.sentinel = start.add(bytes);
  }

  /**
   * Constructor
   *
   * Discontiguous monotone resource.  The address range is <i>not</i>
   * pre-defined at initializtion time and is dynamically defined to
   * be some set of pages, according to demand and availability.
   *
   *                  CURRENTLY UNIMPLEMENTED
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  public MonotonePageResource(int pageBudget, Space space) {
    super(pageBudget, space);
    /* unimplemented */
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(false); 
    this.contiguous = false;
    this.start = Address.zero();
    this.cursor = Address.zero();
    this.sentinel = Address.zero();
  }

  /** 
   * Allocate <code>pages</code> pages from this resource.  Simply
   * bump the cursor, and fail if we hit the sentinel.<p>
   *
   * If the request can be satisfied, then ensure the pages are
   * mmpapped and zeroed before returning the address of the start of
   * the region.  If the request cannot be satisified, return zero.
   *
   * @param pages The number of pages to be allocated.
   * @return The start of the first page if successful, zero on
   * failure.
   */
  protected final Address allocPages(int pages) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(contiguous);
    lock();
    Extent bytes = Conversions.pagesToBytes(pages);
    Address tmp = cursor.add(bytes);
    if (tmp.GT(sentinel)) {
      unlock();
      return Address.zero();
    } else {
      Address old = cursor;
      cursor = tmp;
      unlock();
      LazyMmapper.ensureMapped(old, pages);
      Memory.zero(old, bytes);
      return old;
    }
  }

  /**
   * Reset this page resource, freeing all pages and resetting
   * reserved and committed pages appropriately.
   */
  public final void reset() throws InlinePragma {
    lock();
    reserved = 0;
    committed = 0;
    releasePages();
    unlock();
  }

  /**
   * Release all pages associated with this page resource, optionally
   * zeroing on release and optionally memory protecting on release.
   */
  private final void releasePages() throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(contiguous);
    Extent bytes = cursor.diff(start).toWord().toExtent();
    releasePages(start, bytes);
    cursor = start;
  }

  /**
   * Release a range of pages associated with this page resource, optionally
   * zeroing on release and optionally memory protecting on release.
   */
  private final void releasePages(Address first, Extent bytes)
    throws InlinePragma {
    int pages = Conversions.bytesToPages(bytes);
    if (Assert.VERIFY_ASSERTIONS) 
      Assert._assert(bytes.EQ(Conversions.pagesToBytes(pages)));
    if (ZERO_ON_RELEASE) 
      Memory.zero(first, bytes);
    if (Options.protectOnRelease)
      LazyMmapper.protect(first, pages);
  }
}
