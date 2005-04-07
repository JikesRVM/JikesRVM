/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.Log;
import org.mmtk.utility.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.Plan;
import org.mmtk.utility.options.ProtectOnRelease;

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
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract public class PageResource implements Constants, Uninterruptible {
  
  /****************************************************************************
   *
   * Class variables
   */
  protected static final boolean ZERO_ON_RELEASE = false; // debugging

  static private Lock classLock;
  static private long cumulativeCommitted = 0;
  protected static ProtectOnRelease protectOnRelease;


  /****************************************************************************
   *
   * Instance variables
   */

  // page budgeting
  protected int reserved;
  protected int committed;
  private int pageBudget;

  protected boolean contiguous = false;
  protected Address start;   // only for contiguous

  // locking
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators

  /****************************************************************************
   *
   * Initialization
   */
  static {
    classLock = new Lock("PageResource");
    protectOnRelease = new ProtectOnRelease();
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
    gcLock = new Lock(space.getName() + ".gcLock");
    mutatorLock = new Lock(space.getName() + ".mutatorLock");
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
  public final boolean reservePages(int pages) throws InlinePragma {
    lock();
    reserved = committed + pages;
    boolean satisfied = reserved <= pageBudget;
    unlock();
    return satisfied;
  }

  abstract Address allocPages(int pages);

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
  public final Address getNewPages(int pages) throws InlinePragma {
    Address rtn = allocPages(pages);
    if (!rtn.isZero()) commitPages(pages);
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
   */
  private final void commitPages(int pages) { 
    lock(); 
    committed += pages;
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
  final private static void addToCommitted(int pages) {
    classLock.acquire();
    cumulativeCommitted += pages;
    classLock.release();
  }

  /**
   * Acquire the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  final protected void lock() {
    if (Plan.gcInProgress())
      gcLock.acquire();
    else
      mutatorLock.acquire();
  }

  /**
   * Release the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  final protected void unlock() {
    if (Plan.gcInProgress())
      gcLock.release();
    else
      mutatorLock.release();
  }
}
