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
package org.jikesrvm.adaptive.util;

import org.jikesrvm.VM;
import org.jikesrvm.util.VM_PriorityQueue;

/**
 * This class extends VM_PriorityQueue to safely
 * support multiple producers/consumers where
 * the consumers are blocked if no objects are available
 * to consume.
 */
public class VM_BlockingPriorityQueue extends VM_PriorityQueue {

  /**
   * Used to notify consumers when about to wait and when notified
   * Default implementation does nothing, but can be overriden as needed by client.
   */
  public static class CallBack {
    void aboutToWait() {}

    void doneWaiting() {}
  }

  CallBack callback;

  /**
   * @param _cb the callback object
   */
  public VM_BlockingPriorityQueue(CallBack _cb) {
    super();
    callback = _cb;
  }

  public VM_BlockingPriorityQueue() {
    this(new CallBack());
  }

  /**
   * Insert the object passed with the priority value passed
   *
   * Notify any sleeping consumer threads that an object
   * is available for consumption.
   *
   * @param _priority  the priority to
   * @param _data the object to insert
   */
  public final synchronized void insert(double _priority, Object _data) {
    super.insert(_priority, _data);
    try {
      notifyAll();
    } catch (Exception e) {
      // TODO: should we exit or something more dramatic?
      VM.sysWrite("Exception occurred while notifying that element was inserted!\n");
    }
  }

  /**
   * Remove and return the front (minimum) object.  If the queue is currently
   * empty, then block until an object is available to be dequeued.
   *
   * @return the front (minimum) object.
   */
  public final synchronized Object deleteMin() {
    // While the queue is empty, sleep until notified that an object has been enqueued.
    while (isEmpty()) {
      try {
        callback.aboutToWait();
        wait();
        callback.doneWaiting();
      } catch (InterruptedException e) {
        // TODO: should we exit or something more dramatic?
        VM.sysWrite("Interrupted Exception occurred!\n");
      }
    }

    // When we get to here, we know the queue is non-empty, so dequeue an object and return it.
    return super.deleteMin();
  }
}
