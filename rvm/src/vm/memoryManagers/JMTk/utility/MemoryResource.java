/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) IBM Corp. 2002
 */
package org.mmtk.utility;

import org.mmtk.plan.Plan;
import org.mmtk.plan.BasePlan;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements a memory resource.  The unit of managment for
 * memory resources is the <code>PAGE</code><p>
 *
 * Instances of this class each manage some number of pages of
 * memory.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class MemoryResource implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  static private final int MAX_MEMORY_RESOURCES = 20;
  static private final MemoryResource [] allMR = new MemoryResource[MAX_MEMORY_RESOURCES];
  static private int allMRCount = 0;
  static private Lock classLock;
  static private long cumulativeCommitted = 0;

  /****************************************************************************
   *
   * Instance variables
   */
  public final String name;
  private int reserved;
  private int committed;
  private int pageBudget;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators

  /**
   * Class initializer
   */
  static {
    classLock = new Lock("MemoryResource.classLock");
  }

  /**
   * Constructor
   */
  public MemoryResource(String n) {
    this(n, 0);
  }

  /**
   * Constructor
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   */
  public MemoryResource(String n, int pageBudget) {
    name = n;
    gcLock = new Lock("MemoryResource.gcLock");
    mutatorLock = new Lock("MemoryResource.mutatorLock");
    this.pageBudget = pageBudget;
    allMR[allMRCount++] = this;
  }


  /**
   * Set the page budget
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   */
  public void setBudget(int pageBudget) {
    this.pageBudget = pageBudget;
  }

  /**
   * Reset this memory resource
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   */
  public void reset(int pageBudget) {
    lock();
    this.pageBudget = pageBudget;
    unlock();
    reset();
  }

  /**
   * Reset this memory resource
   */
  public void reset() {
    lock();
    reserved = 0;
    committed = 0;
    unlock();
  }

  /**
   * Acquire a number of pages from the memory resource.  Poll the
   * memory manager if the number of pages used exceeds the budget.
   * By default the budget is zero, in which case the memory manager
   * is polled every time a page is requested.
   *
   * @param pages The number of pages requested
   * @return success Whether the acquire succeeded.
   */
  public boolean acquire (int pages) {
    lock();
    reserved = committed + pages;
    if (reserved > pageBudget) {
      unlock();   // We cannot hold the lock across a GC point!
      if (VM_Interface.getPlan().poll(false, this)) {
        return false;
      }
      lock();
    }
    committed += pages;
    if (!Plan.gcInProgress())
      addToCommitted(pages);   // only count mutator pages
    unlock();
    return true;
  }

  /**
   * Release a given number of pages from the memory resource.
   *
   * @param pages The number of pages to be released.
   */
  public void release(int pages) {
    lock();
    committed -= pages;
    reserved = committed;
    unlock();
  }

  /**
   * Return the number of reserved pages
   *
   * @return The number of reserved pages.
   */
  public int reservedPages() {
    return reserved;
  }

  /**
   * Return the number of committed pages
   *
   * @return The number of committed pages.
   */
  public int committedPages() {
    return committed;
  }

  /**
   * Return the cumulative number of committed pages
   *
   * @return The cumulative number of committed pages.
   */
  public static long getCumulativeCommittedPages() {
    return cumulativeCommitted;
  }

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
  private void lock() {
    if (Plan.gcInProgress())
      gcLock.acquire();
    else
      mutatorLock.acquire();
  }

  /**
   * Release the appropriate lock depending on whether the context is
   * GC or mutator.
   */
  private void unlock() {
    if (Plan.gcInProgress())
      gcLock.release();
    else
      mutatorLock.release();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = 0;
    for (int i=0; i<allMRCount; i++) {
      MemoryResource mr = allMR[i];
      if (mr == null || mr == Plan.bootMR) continue;
      pages += mr.reservedPages();
    }
    return pages;
  }

  /**
   * Print out total memory usage and a breakdown by memory resources.
   * Excludes boot resource.
   */
  public static final void showUsage(int mode) {
    Log.write("used = ");
    BasePlan.writePages(getPagesUsed(), mode);
    boolean first = true;
    for (int i=0; i<allMRCount; i++) {
      MemoryResource mr = allMR[i];
      if (mr == null || mr == Plan.bootMR) continue;
      Log.write(first ? " = " : " + ");
      first = false;
      Log.write(mr.name); Log.write(" ");
      BasePlan.writePages(mr.reservedPages(), mode);
    }
    Log.writeln();
  }

}
