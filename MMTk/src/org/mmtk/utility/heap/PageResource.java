/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
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
 * freelist variants, which reflect monotonic and ad hoc space useage
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

  private static Lock classLock;
  private static long cumulativeCommitted = 0;


  /****************************************************************************
   *
   * Instance variables
   */

  // page budgeting
  protected int reserved;
  protected int committed;
  private int pageBudget;

  protected boolean contiguous = false;
  protected Address start; // only for contiguous

  // locking
  private Lock gcLock; // used during GC
  private Lock mutatorLock; // used by mutators

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
  PageResource(int pageBudget, Space space) {
    this.pageBudget = pageBudget;
    gcLock = VM.newLock(space.getName() + ".gcLock");
    mutatorLock = VM.newLock(space.getName() + ".mutatorLock");
  }

  /**
   * Constructor
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  PageResource(int pageBudget, Space space, Address start) {
    this(pageBudget, space);
    this.start = start;
    this.contiguous = true;
  }

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
    reserved = committed + adjustForMetaData(pages);
    boolean satisfied = reserved <= pageBudget;
    unlock();
    return satisfied;
  }

  abstract Address allocPages(int pages);

  /**
   * Adjust a page request to include metadata requirements, if any.
   *
   * @param pages The size of the pending allocation in pages
   * @return The number of required pages, inclusive of any metadata
   */
  public abstract int adjustForMetaData(int pages);

  /**
   * Adjust a page request to include metadata requirements, if any.
   *
   * @param pages The size of the pending allocation in pages
   * @param begin The address of the virtual memory region assigned to this
   * pending request
   * @return The number of required pages, inclusive of any metadata
   */
  public abstract int adjustForMetaData(int pages, Address begin);

  /**
   * Allocate pages in virtual memory, returning zero on failure.<p>
   *
   * If the request cannot be satisified, zero is returned and it
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
    Address rtn = allocPages(pages);
    if (!rtn.isZero()) commitPages(pages, rtn);
    return rtn;
  }

  /**
   * Commit pages to the page budget.  This is called after
   * successfully determining that the request can be satisfied by
   * both the page budget and virtual memory.  This simply accounts
   * for the descrepency between <code>committed</code> and
   * <code>reserved</code> while the request was pending.
   *
   * @param pages The number of pages to be committed
   * @param begin The start address of the allocated region
   */
  private void commitPages(int pages, Address begin) {
    lock();
    committed += adjustForMetaData(pages, begin);
    if (!Plan.gcInProgress())
      addToCommitted(pages); // only count mutator pages
    unlock();
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
   * Acquire the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  protected final void lock() {
    if (Plan.gcInProgress())
      gcLock.acquire();
    else
      mutatorLock.acquire();
  }

  /**
   * Release the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  protected final void unlock() {
    if (Plan.gcInProgress())
      gcLock.release();
    else
      mutatorLock.release();
  }
}
