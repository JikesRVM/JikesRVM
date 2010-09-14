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
  private final int pageBudget;

  protected final boolean contiguous;
  protected final Space space;
  protected Address start; // only for contiguous

  // locking
  private final Lock lock; // used by mutators

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
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  private PageResource(int pageBudget, Space space, boolean contiguous) {
    this.pageBudget = pageBudget;
    this.contiguous = contiguous;
    this.space = space;
    lock = VM.newLock(space.getName() + ".lock");
  }

  /**
   * Constructor for discontiguous spaces
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  PageResource(int pageBudget, Space space) {
    this(pageBudget, space, false);
  }

  /**
   * Constructor for contiguous spaces
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  PageResource(int pageBudget, Space space, Address start) {
    this(pageBudget, space, true);
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
   * @return True if the page budget could satisfy the request.
   */
  @Inline
  public final boolean reservePages(int pages) {
    lock();
    reserved += adjustForMetaData(pages);
    boolean satisfied = reserved <= pageBudget;
    unlock();
    return satisfied;
  }

  /**
   * Remove a request to the space.
   *
   * @param pages The number of pages in the request.
   */
  @Inline
  public final void clearRequest(int pages) {
    lock();
    reserved -= adjustForMetaData(pages);
    unlock();
  }

  /**
   * Reserve pages unconditionally.<p>
   *
   * An example of where this is useful is in cases where it is
   * desirable to put some space aside as head-room.  By
   * unconditionally reserving the pages, the pages are counted
   * against the collectors budget.  When the space is actually
   * needed, the pages can be unconditionally released, freeing
   * the pages for other purposes.
   *
   * @param pages The number of pages to be unconditionally
   * reserved.
   */
  public final void unconditionallyReservePages(int pages) {
    lock();
    committed += pages;
    reserved += pages;
    unlock();
  }

  /**
   * Release pages unconditionally.<p>
   *
   * This call allows pages to be unconditionally removed from
   * the collectors page budget.
   *
   * @see #unconditionallyReservePages
   * @param pages The number of pages to be unconditionally
   * released.
   */
  public final void unconditionallyReleasePages(int pages) {
    lock();
    committed -= pages;
    reserved -= pages;
    unlock();
  }

  abstract Address allocPages(int pages);

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
   * @param pages The number of pages requested
   * @return The address of the first of <code>pages</code> pages, or
   * zero on failure.
   */
  @Inline
  public final Address getNewPages(int pages) {
    return allocPages(pages);
  }

  /**
   * Commit pages to the page budget.  This is called after
   * successfully determining that the request can be satisfied by
   * both the page budget and virtual memory.  This simply accounts
   * for the descrepency between <code>committed</code> and
   * <code>reserved</code> while the request was pending.
   *
   * This *MUST* be called by each PageResource during the
   * allocPages, and the caller must hold the lock.
   *
   * @param requestedPages The number of pages from this request
   * @param totalPages The number of pages
   */
  protected void commitPages(int requestedPages, int totalPages) {
    int predictedPages = adjustForMetaData(requestedPages);
    int delta = totalPages - predictedPages;
    reserved += delta;
    committed += totalPages;
    if (VM.activePlan.isMutator()) {
      // only count mutator pages
      addToCommitted(totalPages);
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
