/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 * (C) IBM Corp. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;


import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;


/**
 * This class implements a memory resource.  The unit of managment for
 * memory resources is the <code>BLOCK</code><p>
 *
 * Instances of this class each manage some number of blocks of
 * memory.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class MemoryResource implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 


  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   */
  MemoryResource() {
    this(0);
  }

  /**
   * Constructor
   *
   * @param budget The budget of blocks available to this memory
   * manager before it must poll the collector.
   */
  MemoryResource(int budget) {
    gcLock = new Lock("MemoryResource.gcLock");
    mutatorLock = new Lock("MemoryResource.mutatorLock");
    this.budget = budget;
  }

  /**
   * Set the budget
   *
   * @param budget The budget of blocks available to this memory
   * manager before it must poll the collector.
   */
  public void setBudget(int budget) {
    this.budget = budget;
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
   * @param budget The budget of blocks available to this memory
   * manager before it must poll the collector.
   */
  public void reset(int budget) {
    reserved = 0;
    committed = 0;
    this.budget = budget;
  }

  /**
   * Acquire a number of blocks from the memory resource.  Poll the
   * memory manager if the number of blocks used exceeds the budget.
   * By default the budget is zero, in which case the memory manager
   * is polled every time a block is requested.
   *
   * @param blocks The number of blocks requested
   * @return success Whether the acquire succeeded.
   */
  public boolean acquire (int blocks) {
    lock();
    reserved += blocks;
    if ((committed + blocks) > budget) {
      unlock();   // We cannot hold the lock across a GC point!
      if (VM_Interface.getPlan().poll(false)) 
	return false;
      lock();
    }
    committed += blocks;
    unlock();
    return true;
  }

  /**
   * Release all blocks from the memory resource.
   */
  public void release() {
    release(reserved);
  }

  /**
   * Release a given number of blocks from the memory resource.
   *
   * @param blocks The number of blocks to be released.
   */
  public void release(int blocks) {
    lock();
    reserved -= blocks;
    committed -= blocks;
    unlock();
  }

  /**
   * Return the number of reserved blocks
   *
   * @return The number of reserved blocks.
   */
  public int reservedBlocks() {
    return reserved;
  }

  /**
   * Return the number of committed blocks
   *
   * @return The number of committed blocks.
   */
  public int committedBlocks() {
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

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private int reserved;
  private int committed;
  private int budget;
  private Lock gcLock;       // used during GC
  private Lock mutatorLock;  // used by mutators
}
