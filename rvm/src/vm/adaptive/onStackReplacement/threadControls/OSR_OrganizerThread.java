/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.*;

import org.vmmagic.pragma.*;

/**
 * Organizer thread collects OSR requests and inserted in controller queue
 * The producers are application threads, and the consumer thread is the
 * organizer. The buffer is VM_Scheduler.threads array. The producer set
 * it is own flag "requesting_osr" and notify the consumer. The consumer
 * scans the threads array and collect requests. To work with concrrency,
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
 *
 * @author Feng Qian
 */

public class OSR_OrganizerThread extends VM_Thread {

  public String toString() {
    return "OSR_Organizer";
  }

  public OSR_OrganizerThread() {
        makeDaemon(true);
  }
  
  public boolean osr_flag = false;

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
  private int queueLock = 0;
  private VM_ThreadQueue tq = new VM_ThreadQueue();
  private void passivate() {
    boolean gainedLock = VM_Synchronization.testAndSet(this, 
                    VM_Entrypoints.osrOrganizerQueueLockField.getOffset(), 1);
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
  public void activate() throws UninterruptiblePragma {
    boolean gainedLock = VM_Synchronization.testAndSet(this,
         VM_Entrypoints.osrOrganizerQueueLockField.getOffset(), 1);
    if (gainedLock) {
      VM_Thread org = tq.dequeue();
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
    // scanning VM_Scheduler.threads
    for (int i=0, n=VM_Scheduler.threads.length; i<n; i++) {
      VM_Thread thread = VM_Scheduler.threads[i];
      if (thread != null) {
        if (thread.requesting_osr) {
          thread.requesting_osr = false;
          VM_Controller.controllerInputQueue.insert(5.0, thread.onStackReplacementEvent);
        }
      }
    }
  }
}
