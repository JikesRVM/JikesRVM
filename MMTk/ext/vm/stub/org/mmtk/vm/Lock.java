/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package org.mmtk.vm;

/**
 * Simple, fair locks with deadlock detection.
 *
 * The implementation mimics a deli-counter and consists of two values: 
 * the ticket dispenser and the now-serving display, both initially zero.
 * Acquiring a lock involves grabbing a ticket number from the dispenser
 * using a fetchAndIncrement and waiting until the ticket number equals
 * the now-serving display.  On release, the now-serving display is
 * also fetchAndIncremented.
 * 
 * This implementation relies on there being less than 1<<32 waiters.
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public class Lock {
  public static int verbose = 0; // show who is acquiring and releasing the locks

  public static void fullyBooted() {
  }

  public Lock(String str) { 
  }

  // Try to acquire a lock and spin-wait until acquired.
  // (1) The isync at the end is important to prevent hardware instruction re-ordering
  //       from floating instruction below the acquire above the point of acquisition.
  // (2) A deadlock is presumed to have occurred if the number of retries exceeds MAX_RETRY.
  // (3) When a lock is acquired, the time of acquistion and the identity of acquirer is recorded.
  //
  public void acquire() {
  }

  public void check (int w) {
  }

  // Release the lock by incrementing serving counter.
  // (1) The sync is needed to flush changes made while the lock is held and also prevent 
  //        instructions floating into the critical section.
  // (2) When verbose, the amount of time the lock is ehld is printed.
  //
  public void release() {
  }

}
