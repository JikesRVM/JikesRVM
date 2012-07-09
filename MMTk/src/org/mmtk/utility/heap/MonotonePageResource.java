/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
 */
@Uninterruptible
public final class MonotonePageResource extends PageResource
  implements Constants {

  /****************************************************************************
   *
   * Instance variables
   */

  /**
   *
   */
  private Address cursor;
  private Address sentinel;
  private final int metaDataPagesPerRegion;
  private Address currentChunk = Address.zero();
  private volatile Address zeroingCursor;
  private Address zeroingSentinel;

  /**
   * Constructor
   *
   * Contiguous monotone resource. The address range is pre-defined at
   * initialization time and is immutable.
   *
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public MonotonePageResource(Space space, Address start, Extent bytes, int metaDataPagesPerRegion) {
    super(space, start);
    this.cursor = start;
    this.sentinel = start.plus(bytes);
    this.zeroingCursor = this.sentinel;
    this.zeroingSentinel = start;
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
  }

  /**
   * Constructor
   *
   * Discontiguous monotone resource. The address range is <i>not</i>
   * pre-defined at initialization time and is dynamically defined to
   * be some set of pages, according to demand and availability.
   *
   * CURRENTLY UNIMPLEMENTED
   *
   * @param space The space to which this resource is attached
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public MonotonePageResource(Space space, int metaDataPagesPerRegion) {
    super(space);
    /* unimplemented */
    this.start = Address.zero();
    this.cursor = Address.zero();
    this.sentinel = Address.zero();
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
  }


  @Override
  public int getAvailablePhysicalPages() {
    int rtn = Conversions.bytesToPages(sentinel.diff(cursor));
    if (!contiguous)
      rtn += Map.getAvailableDiscontiguousChunks()*Space.PAGES_IN_CHUNK;
    return rtn;
  }

  /**
   * Allocate <code>pages</code> pages from this resource.  Simply
   * bump the cursor, and fail if we hit the sentinel.<p>
   *
   * If the request can be satisfied, then ensure the pages are
   * mmpapped and zeroed before returning the address of the start of
   * the region.  If the request cannot be satisfied, return zero.
   *
   * @param reservedPages The number of pages reserved due to the initial request.
   * @param requiredPages The number of pages required to be allocated.
   * @return The start of the first page if successful, zero on
   * failure.
   */
  @Override
  @Inline
  protected Address allocPages(int reservedPages, int requiredPages, boolean zeroed) {
    boolean newChunk = false;
    lock();
    Address rtn = cursor;
    if (Space.chunkAlign(rtn, true).NE(currentChunk)) {
      newChunk = true;
      currentChunk = Space.chunkAlign(rtn, true);
    }

    if (metaDataPagesPerRegion != 0) {
      /* adjust allocation for metadata */
      Address regionStart = getRegionStart(cursor.plus(Conversions.pagesToBytes(requiredPages)));
      Offset regionDelta = regionStart.diff(cursor);
      if (regionDelta.sGE(Offset.zero())) {
        /* start new region, so adjust pages and return address accordingly */
        requiredPages += Conversions.bytesToPages(regionDelta) + metaDataPagesPerRegion;
        rtn = regionStart.plus(Conversions.pagesToBytes(metaDataPagesPerRegion));
      }
    }
    Extent bytes = Conversions.pagesToBytes(requiredPages);
    Address tmp = cursor.plus(bytes);

    if (!contiguous && tmp.GT(sentinel)) {
      /* we're out of virtual memory within our discontiguous region, so ask for more */
      int requiredChunks = Space.requiredChunks(requiredPages);
      start = space.growDiscontiguousSpace(requiredChunks);
      cursor = start;
      sentinel = cursor.plus(start.isZero() ? 0 : requiredChunks<<Space.LOG_BYTES_IN_CHUNK);
      rtn = cursor;
      tmp = cursor.plus(bytes);
      newChunk = true;
    }
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(rtn.GE(cursor) && rtn.LT(cursor.plus(bytes)));
    if (tmp.GT(sentinel)) {
      unlock();
      return Address.zero();
    } else {
      Address old = cursor;
      cursor = tmp;
      commitPages(reservedPages, requiredPages);
      space.growSpace(old, bytes, newChunk);
      unlock();
      Mmapper.ensureMapped(old, requiredPages);
      if (zeroed) {
        if (!zeroConcurrent) {
          VM.memory.zero(zeroNT, old, bytes);
        } else {
          while (cursor.GT(zeroingCursor));
        }
      }
      VM.events.tracePageAcquired(space, rtn, requiredPages);
      return rtn;
    }
  }

  /**
   * {@inheritDoc}<p>
   *
   * In this case we simply report the expected page cost. We can't use
   * worst case here because we would exhaust our budget every time.
   */
  @Override
  public int adjustForMetaData(int pages) {
    return pages + ((pages + EmbeddedMetaData.PAGES_IN_REGION - 1) >> EmbeddedMetaData.LOG_PAGES_IN_REGION) * metaDataPagesPerRegion;
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
  public int adjustForMetaData(int pages, Address begin) {
    if (getRegionStart(begin).plus(metaDataPagesPerRegion<<LOG_BYTES_IN_PAGE).EQ(begin)) {
      pages += metaDataPagesPerRegion;
    }
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
  public void reset() {
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
  public void unusePages(int pages) {
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
  public void reusePages(int pages) {
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
    Address first = start;
    if (contiguous) {
      // TODO: We will perform unnecessary zeroing if the nursery size has decreased.
      if (zeroConcurrent) {
        // Wait for current zeroing to finish.
        while(zeroingCursor.LT(zeroingSentinel)) {}
      }
      // Reset zeroing region.
      if (cursor.GT(zeroingSentinel)) {
        zeroingSentinel = cursor;
      }
      zeroingCursor = start;
    }
    do {
      Extent bytes = cursor.diff(start).toWord().toExtent();
      releasePages(start, bytes);
      cursor = start;
    } while (!contiguous && moveToNextChunk());
    if (!contiguous) {
      sentinel = Address.zero();
      Map.freeAllChunks(first);
    }
  }

  /**
   * Adjust the start and cursor fields to point to the next chunk
   * in the linked list of chunks tied down by this page resource.
   *
   * @return {@code true} if we moved to the next chunk; {@code false} if we hit the
   * end of the linked list.
   */
  private boolean moveToNextChunk() {
    start = Map.getNextContiguousRegion(start);
    if (start.isZero())
      return false;
    else {
      cursor = start.plus(Map.getContiguousRegionSize(start));
      return true;
    }
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
      VM.memory.zero(false, first, bytes);
    if (Options.protectOnRelease.getValue())
      Mmapper.protect(first, pages);
    VM.events.tracePageReleased(space, first, pages);
  }

  private static int CONCURRENT_ZEROING_BLOCKSIZE = 1<<16;

  @Override
  public void concurrentZeroing() {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(zeroConcurrent);
    }
    Address first = start;
    while (first.LT(zeroingSentinel)) {
      Address last = first.plus(CONCURRENT_ZEROING_BLOCKSIZE);
      if (last.GT(zeroingSentinel)) last = zeroingSentinel;
      VM.memory.zero(zeroNT, first, Extent.fromIntSignExtend(last.diff(first).toInt()));
      zeroingCursor = last;
      first = first.plus(CONCURRENT_ZEROING_BLOCKSIZE);
    }
    zeroingCursor = sentinel;
  }
}
