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

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;
import org.mmtk.utility.options.ProtectOnRelease;
import org.mmtk.utility.options.Options;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the allocation of pages for a space.  When a
 * page is requested by the space both a page budget and the use of
 * virtual address space are checked.  If the request for space can't
 * be satisfied (for either reason) a GC may be triggered.<p>
 *
 * This class is abstract, and is subclassed with monotone and
 * freelist variants, which reflect monotonic and ad hoc space usage
 * respectively.  Monotonic use is easier to manage, but is obviously
 * more restrictive (useful for copying collectors which allocate
 * monotonically before freeing the entire space and starting over).
 */
@Uninterruptible
public abstract class PageResource implements Constants {

  /****************************************************************************
   *
   * Class variables
   */
  protected static final boolean ZERO_ON_RELEASE = false; // debugging

  private static final Lock classLock;
  private static long cumulativeCommitted = 0;


  /****************************************************************************
   *
   * Instance variables
   */

  // page budgeting
  protected int reserved;
  protected int committed;

  protected final boolean contiguous;
  protected final Space space;
  protected Address start; // only for contiguous

  // locking
  private final Lock lock;

  // zeroing flags
  protected boolean zeroNT;

  /****************************************************************************
   *
   * Initialization
   */
  static {
    classLock = VM.newLock("PageResource");
    Options.protectOnRelease = new ProtectOnRelease();
  }

  /**
   * Constructor
   *
   * @param space The space to which this resource is attached
   */
  private PageResource(Space space, boolean contiguous) {
    this.contiguous = contiguous;
    this.space = space;
    lock = VM.newLock(space.getName() + ".lock");
  }

  /**
   * Constructor for discontiguous spaces
   *
   * @param space The space to which this resource is attached
   */
  PageResource(Space space) {
    this(space, false);
  }

  /**
   * Constructor for contiguous spaces
   *
   * @param space The space to which this resource is attached
   */
  PageResource(Space space, Address start) {
    this(space, true);
    this.start = start;
  }

  /**
   * Return the number of available physical pages for this resource.
   *
   * Note: This just considers physical pages (ie virtual memory pages
   * allocated for use by this resource). This calculation is orthogonal
   * to and does not consider any restrictions on the number of pages
   * this resource may actually use at any time (ie the number of
   * committed and reserved pages).<p>
   *
   * Note: The calculation is made on the assumption that all space that
   * could be assigned to this resource would be assigned to this resource
   * (ie the unused discontiguous space could just as likely be assigned
   * to another competing resource).
   *
   * @return The number of available physical pages for this resource.
   */
   public abstract int getAvailablePhysicalPages();

  /**
   * Reserve pages.<p>
   *
   * The role of reserving pages is that it allows the request to be
   * noted as pending (the difference between committed and reserved
   * indicates pending requests).  If the request would exceed the
   * page budget then the caller must poll in case a GC is necessary.
   *
   * @param pages The number of pages requested
   * @return The actual number of pages reserved (including metadata, etc.)
   */
  @Inline
  public final int reservePages(int pages) {
    lock();
    pages = adjustForMetaData(pages);
    reserved += pages;
    unlock();
    return pages;
  }

  /**
   * Remove a request to the space.
   *
   * @param pages The number of pages returned due to the request.
   */
  @Inline
  public final void clearRequest(int reservedPages) {
    lock();
    reserved -= reservedPages;
    unlock();
  }

  /**
   * Update the zeroing approach for this page resource.
   */
  public void updateZeroingApproach(boolean useNT) {
    this.zeroNT = useNT;
  }

  abstract Address allocPages(int reservedPages, int requiredPages, boolean zeroed);

  /**
   * Adjust a page request to include metadata requirements for a request
   * of the given size. This must be a pure function, that is it does not
   * depend on the state of the PageResource.
   *
   * @param pages The size of the pending allocation in pages
   * @return The number of required pages, inclusive of any metadata
   */
  public abstract int adjustForMetaData(int pages);

  /**
   * Allocate pages in virtual memory, returning zero on failure.<p>
   *
   * If the request cannot be satisfied, zero is returned and it
   * falls to the caller to trigger the GC.
   *
   * Call <code>allocPages</code> (subclass) to find the pages in
   * virtual memory.  If successful then commit the pending page
   * request and return the address of the first page.
   *
   * @param pagesReserved The number of pages reserved by the initial request
   * @param pages The number of pages requested
   * @param zeroed If true allocated pages are zeroed.
   * @return The address of the first of <code>pages</code> pages, or
   * zero on failure.
   */
  @Inline
  public final Address getNewPages(int pagesReserved, int pages, boolean zeroed) {
    return allocPages(pagesReserved, pages, zeroed);
  }

  /**
   * Commit pages to the page budget.  This is called after
   * successfully determining that the request can be satisfied by
   * both the page budget and virtual memory.  This simply accounts
   * for the discrepancy between <code>committed</code> and
   * <code>reserved</code> while the request was pending.
   *
   * This *MUST* be called by each PageResource during the
   * allocPages, and the caller must hold the lock.
   *
   * @param reservedPages The number of pages initially reserved due to this request
   * @param actualPages The number of pages actually allocated.
   */
  protected void commitPages(int reservedPages, int actualPages) {
    int delta = actualPages - reservedPages;
    reserved += delta;
    committed += actualPages;
    if (VM.activePlan.isMutator()) {
      // only count mutator pages
      addToCommitted(actualPages);
    }
  }

  /**
   * Return the number of reserved pages
   *
   * @return The number of reserved pages.
   */
  public final int reservedPages() { return reserved; }

  /**
   * Return the number of committed pages
   *
   * @return The number of committed pages.
   */
  public final int committedPages() { return committed; }

  /**
   * Return the cumulative number of committed pages
   *
   * @return The cumulative number of committed pages.
   */
  public static long cumulativeCommittedPages() { return cumulativeCommitted; }

  /**
   * Add to the total cumulative committed page count.
   *
   * @param pages The number of pages to be added.
   */
  private static void addToCommitted(int pages) {
    classLock.acquire();
    cumulativeCommitted += pages;
    classLock.release();
  }

  /**
   * Acquire the lock.
   */
  protected final void lock() {
    lock.acquire();
  }

  /**
   * Release the lock.
   */
  protected final void unlock() {
    lock.release();
  }
}
