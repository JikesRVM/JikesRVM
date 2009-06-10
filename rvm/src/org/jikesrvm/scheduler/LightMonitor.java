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
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.Untraced;

/**
 * A light-weigh condition variable and lock, like Monitor, but this
 * one is movable and can be garbage collected.  Note that this lock is
 * heavier than an object monitor, but has the advantage of being usable
 * within GC (this lock never allocates in its methods, and never uses
 * read or write barriers, either).
 */
@Uninterruptible
public final class LightMonitor {
  ThreadQueue waiting;
  ThreadQueue entering;
  SpinLock mutex;
  @Untraced RVMThread holder;
  int recCount;

  public LightMonitor() {
    waiting=new ThreadQueue();
    entering=new ThreadQueue();
    mutex=new SpinLock();
  }

  @Unpreemptible
  public void lockWithHandshake() {
    RVMThread me=RVMThread.getCurrentThread();
    if (holder==me) {
      recCount++;
    } else {
      mutex.lock();
      while (holder!=null) {
        entering.enqueue(me);
        mutex.unlock();
        me.monitor().lockNoHandshake();
        while (entering.isQueued(me)) {
          me.monitor().waitWithHandshake();
        }
        me.monitor().unlock();
        mutex.lock();
      }
      holder=me;
      mutex.unlock();
      recCount=1;
    }
  }

  public void unlock() {
    if (recCount>1) {
      recCount--;
    } else {
      if (VM.VerifyAssertions) VM._assert(recCount==1);
      if (VM.VerifyAssertions) VM._assert(holder==RVMThread.getCurrentThread());
      mutex.lock();
      RVMThread toAwaken=entering.dequeue();
      holder=null;
      recCount=0;
      mutex.unlock();
      if (toAwaken!=null) {
        toAwaken.monitor().lockedBroadcastNoHandshake();
      }
    }
  }

  @Interruptible
  private void waitImpl(long whenAwake) {
    if (VM.VerifyAssertions) VM._assert(recCount>=1);
    if (VM.VerifyAssertions) VM._assert(holder==RVMThread.getCurrentThread());

    RVMThread me=RVMThread.getCurrentThread();

    boolean throwInterrupt = false;
    Throwable throwThis = null;

    mutex.lock();
    waiting.enqueue(me);
    mutex.unlock();
    int myRecCount=recCount;
    recCount=1;
    unlock();

    me.monitor().lockNoHandshake();
    while (waiting.isQueued(me) &&
           (whenAwake!=0 || sysCall.sysNanoTime() < whenAwake) &&
           !me.hasInterrupt && me.asyncThrowable == null) {
      if (whenAwake==0) {
        me.monitor().waitWithHandshake();
      } else {
        me.monitor().timedWaitAbsoluteWithHandshake(whenAwake);
      }
    }
    if (me.hasInterrupt) {
      throwInterrupt = true;
      me.hasInterrupt = false;
    }
    if (me.asyncThrowable != null) {
      throwThis = me.asyncThrowable;
      me.asyncThrowable = null;
    }
    me.monitor().unlock();

    mutex.lock();
    waiting.remove(me);
    mutex.unlock();

    lockWithHandshake();
    recCount=myRecCount;

    // check if we should exit in a special way
    if (throwThis != null) {
      RuntimeEntrypoints.athrow(throwThis);
    }
    if (throwInterrupt) {
      RuntimeEntrypoints.athrow(new InterruptedException("sleep interrupted"));
    }
  }

  @Interruptible
  public void waitInterruptibly() {
    waitImpl(0);
  }

  @Interruptible
  public void timedWaitAbsoluteInterruptibly(long whenAwakeNanos) {
    waitImpl(whenAwakeNanos);
  }

  @Interruptible
  public void timedWaitRelativeInterruptibly(long delayNanos) {
    waitImpl(sysCall.sysNanoTime()+delayNanos);
  }

  public void broadcast() {
    for (;;) {
      mutex.lock();
      RVMThread toAwaken=waiting.dequeue();
      mutex.unlock();
      if (toAwaken==null) break;
      toAwaken.monitor().lockedBroadcastNoHandshake();
    }
  }

  @Unpreemptible
  public void lockedBroadcastWithHandshake() {
    lockWithHandshake();
    broadcast();
    unlock();
  }
}


