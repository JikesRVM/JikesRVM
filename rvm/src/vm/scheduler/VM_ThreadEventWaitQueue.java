/*
 * (C) Copyright IBM Corp. 2002
 */
// $Id$

package com.ibm.JikesRVM;

/**
 * Queue of threads waiting for a specific kind of event to occur.
 * This class contains the high level functionality of enqueueing
 * and dequeueing threads and implementing timeouts.
 * Subclasses implement methods which determine when events
 * have occurred. Subclasses <em>must</em> directly implement the
 * {@link VM_Uninterruptible} interface.
 *
 * <p>This class was adapted from the original
 * <code>VM_ThreadIOQueue</code>, which is now a subclass.
 *
 * @author Derek Lieber (original <code>VM_ThreadIOQueue</code> class)
 * @author David Hovemeyer
 *
 * @see VM_ThreadIOQueue
 * @see VM_ThreadProcessWaitQueue
 * @see VM_ThreadEventConstants
 */
abstract class VM_ThreadEventWaitQueue extends VM_AbstractThreadQueue
  implements VM_Uninterruptible, VM_ThreadEventConstants {

  protected VM_Thread head, tail;

  // Number of queued threads.
  private int length;

  // Number of threads ready to run because their events occurred,
  // or their timeout expired.
  private int ready;

  /**
   * Is queue empty?
   */
  public boolean isEmpty() {
    return length == 0;
  }
   
  /**
   * Number of threads on queue.
   */
  public int length() {
    return length;
  }

  /**
   * Dump state for debugging.
   */
  void dump() throws VM_PragmaInterruptible {
    dump(" ");
  }
 
  /**
   * Dump state for debugging.
   */
  void dump(String prefix) throws VM_PragmaInterruptible {
    VM.sysWrite(prefix);
    for (VM_Thread t = head; t != null; t = t.next) {
      VM.sysWrite(t.getIndex());
      //VM.sysWrite(getWaitDescription(t));
      dumpWaitDescription(t);
    }
    VM.sysWrite("\n");
  }

  /** 
   * Dump description of what given thread is waiting for.
   * For debugging.
   */
  abstract void dumpWaitDescription(VM_Thread thread) throws VM_PragmaInterruptible;

  /**
   * Get string describing what given thread is waiting for.
   * This method must be interruptible!
   */
  abstract String getWaitDescription(VM_Thread thread) throws VM_PragmaInterruptible;

  /**
   * Check to see if any threads are ready to run, either because
   * their events occurred or their waits timed out.
   */
  public boolean isReady() {
    if (length == 0)
      return false; // no threads waiting

    if (VM.VerifyAssertions) VM._assert(ready >= 0); 

    if (ready == 0) {
      // No threads are ready, so try to find some that are...

      // Allow subclass to check for events
      if (!pollForEvents()) 
        return false; // possibly transient error; try again later

      VM_Thread thread = head;
      long currentCycle = VM_Time.cycles(); 

      // See if any threads have become ready to run
      while (thread != null) {
        VM_ThreadEventWaitData waitData = thread.waitData;
        long maxWaitCycle = waitData.maxWaitCycle;

        if (maxWaitCycle > 0 && maxWaitCycle < currentCycle) {
          // Wait timed out
          waitData.waitFlags = WAIT_FINISHED | WAIT_TIMEOUT;
          ++ready;
        } else if (isReady(thread)) { 
          // Subclass has decided that the thread is ready to schedule;
          // it should have updated waitFlags appropriately
          if (VM.VerifyAssertions)
            VM._assert((waitData.waitFlags & WAIT_FINISHED) != 0); 
          ++ready;
        } else {
          waitData.waitFlags &= ~(WAIT_FINISHED);
        }

        thread = thread.next;
      }
    }

    return ready != 0;
  }

  /**
   * Check to see if any events occurred.
   * Called prior to calling {@link #isReady(VM_Thread)} on
   * queued threads.
   * @return whether or not polling was successful
   */
  public abstract boolean pollForEvents();

  /**
   * Check to see if the event the given thread is waiting for
   * has occurred, or if it should be woken up for any other reason
   * (such as being interrupted).
   */
  public abstract boolean isReady(VM_Thread thread);

  /**
   * Place a thread on this queue.
   * Its {@link VM_Thread#waitData waitData} field should
   * be set to indicate the event that the thread is waiting for.
   * @param thread the thread to put on the queue
   */
  public void enqueue(VM_Thread thread) {
    if (VM.VerifyAssertions) {
      VM._assert(thread.waitData.waitFlags == WAIT_PENDING ||
                          thread.waitData.waitFlags == WAIT_NATIVE); 
      VM._assert(thread.next == null); 
    }

    // Add to queue
    if (head == null)
      head = thread;
    else
      tail.next = thread;
    tail = thread;
    ++length;
  }

  /**
   * Get a thread that has become ready to run.
   * @return the thread, or null if no threads from
   *   this queue are ready
   */
  public VM_Thread dequeue() {
    VM_Thread prev = null;
    VM_Thread thread = head;

    if (VM.VerifyAssertions) VM._assert(ready >= 0); 

    // See if a thread is finished waiting
    while (thread != null) {
      VM_ThreadEventWaitData waitData = thread.waitData;
      if ((waitData.waitFlags & WAIT_FINISHED) != 0)
        break;
      prev = thread;
      thread = thread.next;
    }

    // If we found one, take it off the queue
    if (thread != null) {
      if (prev == null)
        head = thread.next;
      else
        prev.next = thread.next;
      if (tail == thread)
        tail = prev;
      thread.next = null;
         
      --length;
      --ready;
    }
    else /* thread == null */ {
      if (VM.VerifyAssertions) VM._assert(ready == 0); 
    }

    return thread;
  }

   
  /**
   * Debugging.
   */
  boolean contains(VM_Thread x) {
    for (VM_Thread t = head; t != null; t = t.next)
      if (t == x) return true;
    return false;
  }
}
