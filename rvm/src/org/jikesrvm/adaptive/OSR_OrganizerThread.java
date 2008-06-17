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
package org.jikesrvm.adaptive;

import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.scheduler.Scheduler;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.scheduler.greenthreads.GreenThread;
import org.jikesrvm.scheduler.greenthreads.GreenThreadQueue;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Organizer thread collects OSR requests and inserted in controller queue
 * The producers are application threads, and the consumer thread is the
 * organizer. The buffer is Scheduler.threads array. The producer set
 * it is own flag "requesting_osr" and notify the consumer. The consumer
 * scans the threads array and collect requests. To work with concurrency,
 * we use following scheme:
 * P - producer, C - consumer
 * P1, P2:
 *   if (C.osr_flag == false) {
 *     C.osr_flag = true;
 *     C.activate();
 *   }
 *
 * C:
 *   while (true) {
 *     while (osr_flag == true) {
 *       osr_flag = false;
 *       scan threads array
 *     }
 *     // P may set osr_flag here, C is active now
 *     C.passivate();
 *   }
 *
 * // compensate the case C missed osr_flag
 * Other thread switching:
 *   if (C.osr_flag == true) {
 *     C.activate();
 *   }
 *
 * C.activate and passivate have to acquire a lock before dequeue and
 * enqueue.
 */

public final class OSR_OrganizerThread extends GreenThread {
  /** Constructor */
  public OSR_OrganizerThread() {
    super("OSR_Organizer");
    makeDaemon(true);
  }

  public boolean osr_flag = false;

  @Override
  public void run() {
    while (true) {
      while (this.osr_flag) {
        this.osr_flag = false;
        processOsrRequest();
      }
      // going to sleep, possible a osr request is set by producer
      passivate();
    }
  }

  // lock = 0, free , 1 owned by someone
  @SuppressWarnings("unused")
  // Accessed via EntryPoints
  private int queueLock = 0;
  private final GreenThreadQueue tq = new GreenThreadQueue();

  private void passivate() {
    boolean gainedLock = Synchronization.testAndSet(this, Entrypoints.osrOrganizerQueueLockField.getOffset(), 1);
    if (gainedLock) {

      // we cannot release lock before enqueue the organizer.
      // ideally, calling yield(q, l) is the solution, but
      // we donot want to use a lock
      //
      // this.beingDispatched = true;
      // tq.enqueue(this);
      // this.queueLock = 0;
      // morph(false);
      //
      // currently we go through following sequence which is incorrect
      //
      // this.queueLock = 0;
      // this.beingDispatched = true;
      // tq.enqueue(this);
      // morph(false);
      //
      this.queueLock = 0; // release lock
      yield(tq);     // sleep in tq
    }
    // if failed, just continue the loop again
  }

  /**
   * Activates organizer thread if it is sleeping in the queue.
   * Only one thread can access queue at one time
   */
  @Uninterruptible
  public void activate() {
    boolean gainedLock = Synchronization.testAndSet(this, Entrypoints.osrOrganizerQueueLockField.getOffset(), 1);
    if (gainedLock) {
      GreenThread org = tq.dequeue();
      // release lock
      this.queueLock = 0;

      if (org != null) {
        org.schedule();
      }
    }
    // otherwise, donot bother
  }

  // proces osr request
  private void processOsrRequest() {
    // scanning Scheduler.threads
    for (int i = 0, n = Scheduler.threads.length; i < n; i++) {
      GreenThread thread = (GreenThread)Scheduler.threads[i];
      if (thread != null) {
        if (thread.requesting_osr) {
          thread.requesting_osr = false;
          Controller.controllerInputQueue.insert(5.0, thread.onStackReplacementEvent);
        }
      }
    }
  }
}
