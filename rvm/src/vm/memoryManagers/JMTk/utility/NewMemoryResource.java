/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) IBM Corp. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Uninterruptible;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;


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
final class NewMemoryResource implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 


  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   */
  NewMemoryResource() {
    this(0);
  }

  /**
   * Constructor
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   */
  NewMemoryResource(int pageBudget) {
    gcLock = new Lock("MemoryResource.gcLock");
    mutatorLock = new Lock("MemoryResource.mutatorLock");
    this.pageBudget = pageBudget;
  }

  /**
   * Set the pageBudget
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
   */
  public void reset() {
    reset(0);
  }

  /**
   * Reset this memory resource
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   */
  public void reset(int pageBudget) {
    reserved = 0;
    committed = 0;
    this.pageBudget = pageBudget;
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
    reserved += pages;
    if ((committed + pages) > pageBudget) {
      unlock();   // We cannot hold the lock across a GC point!
      if (VM_Interface.getPlan().poll(false)) 
        return false;
      lock();
    }
    committed += pages;
    unlock();
    return true;
  }

  /**
   * Release all pages from the memory resource.
   */
  public void release() {
    release(reserved);
  }

  /**
   * Release a given number of pages from the memory resource.
   *
   * @param pages The number of pages to be released.
   */
  public void release(int pages) {
    lock();
    reserved -= pages;
    committed -= pages;
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

  /****************************************************************************
   *
   * Instance variables
   */
  private int reserved;
  private int committed;
  private int pageBudget;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators
}
