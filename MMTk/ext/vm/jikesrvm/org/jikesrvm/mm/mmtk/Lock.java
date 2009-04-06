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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.ThreadQueue;

/**
 * Adaptive mutex with a spinlock fast path.  Designed for good performance
 * on native threaded systems.  This implementation has the following specific
 * properties:
 * <ul>
 * <li>It behaves like a spinlock on the fast path (one CAS to lock, one CAS
 *     to unlock, if there is no contention).</li>
 * <li>It has the ability to spin for some predetermined number of cycles
 *     (see <code>SPIN_LIMIT</code>).</li>
 * <li>Adapts to contention by eventually placing contending threads on a
 *     queue and parking them.</li>
 * <li>Has a weak fairness guarantee: contenders follow queue discipline,
 *     except that new contenders may "beat" the thread at the head of the
 *     queue if they arrive just as the lock becomes available and the thread
 *     at the head of the queue just got scheduled.</li>
 * </ul>
 * @author Filip Pizlo
 */
@Uninterruptible public class Lock extends org.mmtk.vm.Lock {

  // Core Instance fields
  private String name;        // logical name of lock
  private final int id;       // lock id (based on a non-resetting counter)
  private static int lockCount;
  private static final int SPIN_LIMIT = 1000;
  /** Lock is not held and the queue is empty.  When the lock is in this
   * state, there <i>may</i> be a thread that just got dequeued and is
   * about to enter into contention on the lock. */
  private static final int CLEAR = 0;
  /** Lock is held and the queue is empty. */
  private static final int LOCKED = 1;
  /** Lock is not held but the queue is non-empty.  This state guarantees
   * that there is a thread that got dequeued and is about to contend on
   * the lock. */
  private static final int CLEAR_QUEUED = 2;
  /** Lock is held and the queue is non-empty. */
  private static final int LOCKED_QUEUED = 3;
  /** Some thread is currently engaged in an enqueue or dequeue operation,
   * and will return the lock to whatever it was in previously once that
   * operation is done.  During this states any lock/unlock attempts will
   * spin until the lock reverts to some other state. */
  private static final int QUEUEING = 4;
  private ThreadQueue queue;
  @Entrypoint
  private int state;

  // Diagnosis Instance fields
  @Untraced
  private RVMThread thread;   // if locked, who locked it?
  private int where = -1;     // how far along has the lock owner progressed?
  public Lock(String name) {
    this();
    this.name = name;
  }

  public Lock() {
    id = lockCount++;
    queue = new ThreadQueue();
    state = CLEAR;
  }

  public void setName(String str) {
    name = str;
  }
  public void acquire() {
    RVMThread me = RVMThread.getCurrentThread();
    Offset offset=Entrypoints.lockStateField.getOffset();
    boolean acquired=false;
    for (int i=0;i<SPIN_LIMIT;++i) {
      int oldState=Magic.prepareInt(this,offset);
      // NOTE: we could be smart here and break out of the spin if we see
      // that the state is CLEAR_QUEUED or LOCKED_QUEUED, or we could even
      // check the queue directly and see if there is anything on it; this
      // would make the lock slightly more fair.
      if ((oldState==CLEAR &&
           Magic.attemptInt(this,offset,CLEAR,LOCKED)) ||
          (oldState==CLEAR_QUEUED &&
           Magic.attemptInt(this,offset,CLEAR_QUEUED,LOCKED_QUEUED))) {
        acquired=true;
        break;
      }
    }
    if (!acquired) {
      for (;;) {
        int oldState=Magic.prepareInt(this,offset);
        if ((oldState==CLEAR &&
             Magic.attemptInt(this,offset,CLEAR,LOCKED)) ||
            (oldState==CLEAR_QUEUED &&
             Magic.attemptInt(this,offset,CLEAR_QUEUED,LOCKED_QUEUED))) {
          break;
        } else if ((oldState==LOCKED &&
                    Magic.attemptInt(this,offset,LOCKED,QUEUEING)) ||
                   (oldState==LOCKED_QUEUED &&
                    Magic.attemptInt(this,offset,LOCKED_QUEUED,QUEUEING))) {
          queue.enqueue(me);
          Magic.sync();
          state=LOCKED_QUEUED;
          me.monitor().lockNoHandshake();
          while (queue.isQueued(me)) {
            // use await instead of waitNicely because this is NOT a GC point!
            me.monitor().waitNoHandshake();
          }
          me.monitor().unlock();
        }
      }
    }
    thread = me;
    where = -1;
    Magic.isync();
  }

  public void check(int w) {
    if (VM.VerifyAssertions) VM._assert(RVMThread.getCurrentThread() == thread);
    where = w;
  }

  public void release() {
    where=-1;
    thread=null;
    Magic.sync();
    Offset offset=Entrypoints.lockStateField.getOffset();
    for (;;) {
      int oldState=Magic.prepareInt(this,offset);
      if (VM.VerifyAssertions) VM._assert(oldState==LOCKED ||
                                          oldState==LOCKED_QUEUED ||
                                          oldState==QUEUEING);
      if (oldState==LOCKED &&
          Magic.attemptInt(this,offset,LOCKED,CLEAR)) {
        break;
      } else if (oldState==LOCKED_QUEUED &&
                 Magic.attemptInt(this,offset,LOCKED_QUEUED,QUEUEING)) {
        RVMThread toAwaken=queue.dequeue();
        if (VM.VerifyAssertions) VM._assert(toAwaken!=null);
        boolean queueEmpty=queue.isEmpty();
        Magic.sync();
        if (queueEmpty) {
          state=CLEAR;
        } else {
          state=CLEAR_QUEUED;
        }
        toAwaken.monitor().lockedBroadcastNoHandshake();
        break;
      }
    }
  }
}
