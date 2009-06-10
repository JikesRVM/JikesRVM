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
package org.jikesrvm.adaptive.util;

import org.jikesrvm.VM;
import org.jikesrvm.util.PriorityQueueRVM;

/**
 * This class extends PriorityQueueRVM to safely
 * support multiple producers/consumers where
 * the consumers are blocked if no objects are available
 * to consume.
 */
public class BlockingPriorityQueue extends PriorityQueueRVM {

  /**
   * Used to notify consumers when about to wait and when notified
   * Default implementation does nothing, but can be overriden as needed by client.
   */
  public static class CallBack {
    public void aboutToWait() {}

    public void doneWaiting() {}
  }

  CallBack callback;

  /**
   * @param _cb the callback object
   */
  public BlockingPriorityQueue(CallBack _cb) {
    super();
    callback = _cb;
  }

  public BlockingPriorityQueue() {
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
