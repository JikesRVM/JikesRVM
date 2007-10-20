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
package org.jikesrvm.scheduler.greenthreads;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Lock;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_ThinLock;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.LogicallyUninterruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

@Uninterruptible
public class VM_GreenLock extends VM_Lock {
  /**
   * A queue of threads contending for this lock (guarded by <code>mutex</code>).
   * For example, on the current thread if "synchronized(lockObject)" were
   * performed and the lock wasn't available, the thread would be queued here.
   */
  public final VM_ThreadQueue entering;
  /** A queue of (proxies for) threads awaiting notification on this object (guarded by <code>mutex</code>). */
  public final VM_ThreadProxyWaitingQueue waiting;

  /**
   * Should we give up or persist in the attempt to get a heavy-weight lock,
   * if its <code>mutex</code> microlock is held by another procesor.
   */
  private static final boolean tentativeMicrolocking = false;

  /**
   * A heavy weight lock to handle extreme contention and wait/notify
   * synchronization.
   */
  public VM_GreenLock() {
    entering = new VM_ThreadQueue();
    waiting = new VM_ThreadProxyWaitingQueue();
  }

  /**
   * Acquires this heavy-weight lock on the indicated object.
   *
   * @param o the object to be locked
   * @return true, if the lock succeeds; false, otherwise
   */
  @Override
  public final boolean lockHeavy(Object o) {
    if (tentativeMicrolocking) {
      if (!mutex.tryLock()) {
        return false;
      }
    } else {
      mutex.lock("lock heavy mutex");  // Note: thread switching is not allowed while mutex is held.
    }
    if (lockedObject != o) { // lock disappeared before we got here
      mutex.unlock(); // thread switching benign
      return false;
    }
    if (STATS) lockOperations++;
    VM_GreenProcessor mine = VM_GreenProcessor.getCurrentProcessor();
    int threadId = mine.threadId;
    if (ownerId == threadId) {
      recursionCount++;
    } else if (ownerId == 0) {
      ownerId = threadId;
      recursionCount = 1;
    } else if (mine.threadSwitchingEnabled()) {
      VM_GreenScheduler.getCurrentThread().block(entering, mutex);
      // when this thread next gets scheduled, it will be entitled to the lock,
      // but another thread might grab it first.
      return false; // caller will try again
    } else { // can't yield - must spin and let caller retry
      // potential deadlock if user thread is contending for a lock with thread switching disabled
      if (VM.VerifyAssertions) VM._assert(VM_Scheduler.getCurrentThread().isGCThread());
      mutex.unlock(); // thread-switching benign
      return false; // caller will try again
    }
    mutex.unlock(); // thread-switching benign
    return true;
  }

  /**
   * Releases this heavy-weight lock on the indicated object.
   *
   * @param o the object to be unlocked
   */
  @Override
  public final void unlockHeavy(Object o) {
    boolean deflated = false;
    mutex.lock("unlock heavy"); // Note: thread switching is not allowed while mutex is held.
    VM_Processor mine = VM_Processor.getCurrentProcessor();
    if (ownerId != mine.threadId) {
      mutex.unlock(); // thread-switching benign
      raiseIllegalMonitorStateException("heavy unlocking", o);
    }
    recursionCount--;
    if (0 < recursionCount) {
      mutex.unlock(); // thread-switching benign
      return;
    }
    if (STATS) unlockOperations++;
    ownerId = 0;
    VM_GreenThread t = entering.dequeue();
    if (t != null) {
      t.unblock();
    } else if (entering.isEmpty() && waiting.isEmpty()) { // heavy lock can be deflated
      // Possible project: decide on a heuristic to control when lock should be deflated
      Offset lockOffset = VM_Magic.getObjectType(o).getThinLockOffset();
      if (!lockOffset.isMax()) { // deflate heavy lock
        deflate(o, lockOffset);
        deflated = true;
      }
    }
    mutex.unlock(); // does a VM_Magic.sync();  (thread-switching benign)
    if (deflated && ((LOCK_ALLOCATION_UNIT_SIZE << 1) <= mine.freeLocks) && BALANCE_FREE_LOCKS) {
      globalizeFreeLocks(mine);
    }
  }

  /**
   * Disassociates this heavy-weight lock from the indicated object.
   * This lock is not held, nor are any threads on its queues.  Note:
   * the mutex for this lock is held when deflate is called.
   *
   * @param o the object from which this lock is to be disassociated
   */
  private void deflate(Object o, Offset lockOffset) {
    if (VM.VerifyAssertions) {
      VM._assert(lockedObject == o);
      VM._assert(recursionCount == 0);
      VM._assert(entering.isEmpty());
      VM._assert(waiting.isEmpty());
    }
    if (STATS) deflations++;
    VM_ThinLock.deflate(o, lockOffset, this);
    lockedObject = null;
    free(this);
  }

  @LogicallyUninterruptible
  private static void raiseIllegalMonitorStateException(String msg, Object o) {
    throw new IllegalMonitorStateException(msg + o);
  }

  /**
   * Is this lock blocking thread t?
   */
  @Override
  protected final boolean isBlocked(VM_Thread t) {
    return entering.contains(t);
  }

  /**
   * Is this thread t waiting on this lock?
   */
  @Override
  protected final boolean isWaiting(VM_Thread t) {
    return waiting.contains(t);
  }

  /**
   * Dump threads blocked trying to get this lock
   */
  @Override
  protected void dumpBlockedThreads() {
    VM.sysWrite(" entering: ");
    entering.dump();
  }
  /**
   * Dump threads waiting to be notified on this lock
   */
  @Override
  protected void dumpWaitingThreads() {
    VM.sysWrite(" waiting: ");
    waiting.dump();
  }
}
