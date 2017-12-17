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

import static org.mmtk.utility.Conversions.*;

import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Log;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.heap.layout.HeapLayout;
import org.mmtk.utility.heap.layout.VMLayoutConstants;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class manages the allocation of pages for a space.  When a
 * page is requested by the space both a page budget and the use of
 * virtual address space are checked.  If the request for space can't
 * be satisfied (for either reason) a GC may be triggered.
 */
@Uninterruptible
public final class MonotonePageResource extends PageResource {

  /****************************************************************************
   *
   * Instance variables
   */

  /** Pointer to the next block to be allocated. */
  private Address cursor;

  /** The limit of the currently allocated address space. */
  private Address sentinel;

  /** Number of pages to reserve at the start of every allocation */
  private final int metaDataPagesPerRegion;

  /** Base address of the current chunk of addresses */
  private Address currentChunk;

  /** Current frontier of zeroing, in a separate zeroing thread */
  private volatile Address zeroingCursor;

  /** Current limit of zeroing.  If zeroingCursor < zeroingSentinel, zeroing is still happening. */
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
    this.currentChunk = Conversions.chunkAlign(start, true);
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
   * @param space The space to which this resource is attached
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public MonotonePageResource(Space space, int metaDataPagesPerRegion) {
    super(space);
    this.cursor = Address.zero();
    this.currentChunk = Address.zero();
    this.sentinel = Address.zero();
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
  }


  @Override
  public int getAvailablePhysicalPages() {
    int rtn = Conversions.bytesToPages(sentinel.diff(cursor));
    if (!contiguous)
      rtn += HeapLayout.vmMap.getAvailableDiscontiguousChunks() * VMLayoutConstants.PAGES_IN_CHUNK;
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

    if (VM.VERIFY_ASSERTIONS) {
      /*
       * Cursor should always be zero, or somewhere in the current chunk.  If we have just
       * allocated exactly enough pages to exhaust the current chunk, then cursor can point
       * to the next chunk.
       */
      if (currentChunk.GT(cursor) || (Conversions.chunkAlign(cursor, true).NE(currentChunk) && Conversions.chunkAlign(cursor, true).NE(currentChunk.plus(VMLayoutConstants.BYTES_IN_CHUNK)))) {
        logChunkFields("MonotonePageResource.allocPages:fail");
      }
      VM.assertions._assert(currentChunk.LE(cursor));
      VM.assertions._assert(cursor.isZero() ||
          Conversions.chunkAlign(cursor, true).EQ(currentChunk) ||
          Conversions.chunkAlign(cursor, true).EQ(currentChunk.plus(VMLayoutConstants.BYTES_IN_CHUNK)));
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
      currentChunk = space.growDiscontiguousSpace(requiredChunks); // Returns zero on failure
      cursor = currentChunk;
      sentinel = cursor.plus(currentChunk.isZero() ? 0 : requiredChunks << VMLayoutConstants.LOG_BYTES_IN_CHUNK);
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

      /* In a contiguous space we can bump along into the next chunk, so preserve the currentChunk invariant */
      if (contiguous && Conversions.chunkAlign(cursor, true).NE(currentChunk)) {
        currentChunk = Conversions.chunkAlign(cursor, true);
      }
      commitPages(reservedPages, requiredPages);
      space.growSpace(old, bytes, newChunk);
      unlock();
      HeapLayout.mmapper.ensureMapped(old, requiredPages);
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
    if (getRegionStart(begin).plus(pagesToBytes(metaDataPagesPerRegion)).EQ(begin)) {
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
    if (contiguous) {
      // TODO: We will perform unnecessary zeroing if the nursery size has decreased.
      if (zeroConcurrent) {
        // Wait for current zeroing to finish.
        while (zeroingCursor.LT(zeroingSentinel)) { }
      }
      // Reset zeroing region.
      if (cursor.GT(zeroingSentinel)) {
        zeroingSentinel = cursor;
      }
      zeroingCursor = start;
      cursor = start;
      currentChunk = Conversions.chunkAlign(start, true);
    } else { /* Not contiguous */
      if (!cursor.isZero()) {
        do {
          Extent bytes = cursor.diff(currentChunk).toWord().toExtent();
          releasePages(currentChunk, bytes);
        } while (moveToNextChunk());

        currentChunk = Address.zero();
        sentinel = Address.zero();
        cursor = Address.zero();
        space.releaseAllChunks();
      }
    }
  }

  /**
   * Adjust the currentChunk and cursor fields to point to the next chunk
   * in the linked list of chunks tied down by this page resource.
   *
   * @return {@code true} if we moved to the next chunk; {@code false} if we hit the
   * end of the linked list.
   */
  private boolean moveToNextChunk() {
    currentChunk = HeapLayout.vmMap.getNextContiguousRegion(currentChunk);
    if (currentChunk.isZero())
      return false;
    else {
      cursor = currentChunk.plus(HeapLayout.vmMap.getContiguousRegionSize(currentChunk));
      return true;
    }
  }

  /**
   * Releases a range of pages associated with this page resource, optionally
   * zeroing on release and optionally memory protecting on release.
   *
   * @param first start address of memory to be released
   * @param bytes number of bytes in the memory region
   */
  @Inline
  private void releasePages(Address first, Extent bytes) {
    int pages = Conversions.bytesToPages(bytes);
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(bytes.EQ(Conversions.pagesToBytes(pages)));
    if (VM.config.ZERO_PAGES_ON_RELEASE)
      VM.memory.zero(false, first, bytes);
    if (Options.protectOnRelease.getValue())
      HeapLayout.mmapper.protect(first, pages);
    VM.events.tracePageReleased(space, first, pages);
  }

  private static int CONCURRENT_ZEROING_BLOCKSIZE = 1 << 16;

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


  private void logChunkFields(String site) {
    Log.write("[");
    Log.write(space.getName());
    Log.write("]");
    Log.write(site);
    Log.write(": cursor=");
    Log.write(cursor);
    Log.write(", currentChunk=");
    Log.write(currentChunk);
    Log.write(", delta=");
    Log.writeln(cursor.diff(currentChunk));
  }
}

