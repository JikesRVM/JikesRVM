/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.options.Options;
import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

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
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class MonotonePageResource extends PageResource 
  implements Constants {

  /****************************************************************************
   * 
   * Instance variables
   */
  private Address cursor;
  private Address sentinel;
  private int metaDataPagesPerRegion;

  /**
   * Constructor
   * 
   * Contiguous monotone resource. The address range is pre-defined at
   * initializtion time and is immutable.
   * 
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public MonotonePageResource(int pageBudget, Space space, Address start,
      Extent bytes, int metaDataPagesPerRegion) {
    super(pageBudget, space, start);
    this.cursor = start;
    this.sentinel = start.plus(bytes);
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
  }

  /**
   * Constructor
   * 
   * Discontiguous monotone resource. The address range is <i>not</i>
   * pre-defined at initializtion time and is dynamically defined to
   * be some set of pages, according to demand and availability.
   * 
   * CURRENTLY UNIMPLEMENTED
   * 
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public MonotonePageResource(int pageBudget, Space space, int metaDataPagesPerRegion) {
    super(pageBudget, space);
    /* unimplemented */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false); 
    this.contiguous = false;
    this.start = Address.zero();
    this.cursor = Address.zero();
    this.sentinel = Address.zero();
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
  }

  /**
   * Allocate <code>pages</code> pages from this resource.  Simply
   * bump the cursor, and fail if we hit the sentinel.<p>
   *
   * If the request can be satisfied, then ensure the pages are
   * mmpapped and zeroed before returning the address of the start of
   * the region.  If the request cannot be satisified, return zero.
   *
   * @param requestPages The number of pages to be allocated.
   * @return The start of the first page if successful, zero on
   * failure.
   */
  @Inline
  protected final Address allocPages(int requestPages) { 
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(contiguous);
    int pages = requestPages;
    lock();
    Address rtn = cursor;
    if (metaDataPagesPerRegion != 0) {
      /* adjust allocation for metadata */
      Address regionStart = getRegionStart(cursor.plus(Conversions
          .pagesToBytes(pages)));
      Offset regionDelta = regionStart.diff(cursor);
      if (regionDelta.sGE(Offset.zero())) {
        /* start new region, so adjust pages and return address accordingly */
        pages += Conversions.bytesToPages(regionDelta) + metaDataPagesPerRegion;
        rtn = regionStart.plus(Conversions.pagesToBytes(metaDataPagesPerRegion));
      }
    }
    Extent bytes = Conversions.pagesToBytes(pages);
    Address tmp = cursor.plus(bytes);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(rtn.GE(cursor) && rtn.LT(cursor.plus(bytes)));
    if (tmp.GT(sentinel)) {
      unlock();
      return Address.zero();
    } else {
      Address old = cursor;
      cursor = tmp;
      unlock();
      Mmapper.ensureMapped(old, pages);
      VM.memory.zero(old, bytes);
      return rtn;
    }
    }

  /**
   * Adjust a page request to include metadata requirements, if any.<p>
   * 
   * Note that there could be a race here, with multiple threads each
   * adjusting their request on account of the same single metadata
   * region.  This should not be harmful, as the failing requests will
   * just retry, and if multiple requests succeed, only one of them
   * will actually have the metadata accounted against it, the others
   * will simply have more space than they originally requested.
   * 
   * @param pages The size of the pending allocation in pages
   * @return The number of required pages, inclusive of any metadata
   */
  public final int adjustForMetaData(int pages) { 
    Extent bytes = Conversions.pagesToBytes(pages);
    if (metaDataPagesPerRegion != 0) {
      if (cursor.LE(getRegionStart(cursor.plus(bytes))))
        pages += metaDataPagesPerRegion;
    }
    return pages;
   }

  /**
   * Adjust a page request to include metadata requirements, if any.<p>
   * 
   * Note that there could be a race here, with multiple threads each
   * adjusting their request on account of the same single metadata
   * region.  This should not be harmful, as the failing requests will
   * just retry, and if multiple requests succeed, only one of them
   * will actually have the metadata accounted against it, the others
   * will simply have more space than they originally requested.
   * 
   * @param pages The size of the pending allocation in pages
   * @param begin The start address of the region assigned to this pending
   * request
   * @return The number of required pages, inclusive of any metadata
   */
  public final int adjustForMetaData(int pages, Address begin) { 
    if (getRegionStart(begin).plus(metaDataPagesPerRegion<<LOG_BYTES_IN_PAGE).EQ(begin))
      pages += metaDataPagesPerRegion;
    return pages;
   }
  
  private static Address getRegionStart(Address addr) {
    return addr.toWord().and(Word.fromIntSignExtend(EmbeddedMetaData.BYTES_IN_REGION - 1).not()).toAddress();
  }

  /**
   * Reset this page resource, freeing all pages and resetting
   * reserved and committed pages appropriately.
   */
  @Inline
  public final void reset() { 
    lock();
    reserved = 0;
    committed = 0;
    releasePages();
    unlock();
  }

  /**
   * Notify that several pages are no longer in use.
   * 
   * @param pages The number of pages
   */
  public final void unusePages(int pages) {
    lock();
    reserved -= pages;
    committed -= pages;
    unlock();
  }

  /**
   * Notify that previously unused pages are in use again.
   * 
   * @param pages The number of pages
   */
  public final void reusePages(int pages) {
    lock();
    reserved += pages;
    committed += pages;
    unlock();
  }


  /**
   * Release all pages associated with this page resource, optionally
   * zeroing on release and optionally memory protecting on release.
   */
  @Inline
  private void releasePages() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(contiguous);
    Extent bytes = cursor.diff(start).toWord().toExtent();
    releasePages(start, bytes);
    cursor = start;
  }

  /**
   * Release a range of pages associated with this page resource, optionally
   * zeroing on release and optionally memory protecting on release.
   */
  @Inline
  private void releasePages(Address first, Extent bytes) {
    int pages = Conversions.bytesToPages(bytes);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(bytes.EQ(Conversions.pagesToBytes(pages)));
    if (ZERO_ON_RELEASE)
      VM.memory.zero(first, bytes);
    if (Options.protectOnRelease.getValue())
      Mmapper.protect(first, pages);
  }
}
