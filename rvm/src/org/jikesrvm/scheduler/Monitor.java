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

import static org.jikesrvm.runtime.SysCall.sysCall;
import org.jikesrvm.VM;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.unboxed.Word;

/**
 * Implementation of a heavy lock and condition variable implemented using
 * the primitives available from the operating system.  Currently we use
 * a pthread_mutex_t and pthread_cond_it.  When instantiated, the mutex
 * and cond are allocated.  There is currently no way to destroy either
 * (thus, pool and reuse accordingly).
 * <p>
 * It is perfectly safe to use this throughout the VM for locking.  It is
 * meant to provide roughly the same functionality as Java monitors,
 * except:
 * <ul>
 * <li>This class provides a faster slow path than Java monitors.</li>
 * <li>This class provides a slower fast path than Java monitors.</li>
 * <li>This class does not have any interaction with Thread.interrupt()
 *     or Thread.stop().  Thus, if you block on a lock or a wait and the
 *     calling thread is stopped or interrupted, nothing will happen
 *     until the lock is acquired or the wait is notified.</li>
 * <li>This class will work in the inner guts of the RVM runtime because
 *     it gives you the ability to lock and unlock, as well as wait and
 *     notify, without using any other VM runtime functionality.</li>
 * <li>This class allows you to optionally block without letting the thread
 *     system know that you are blocked.  The benefit is that you can
 *     perform synchronization without depending on RVM thread subsystem functionality.
 *     However, most of the time, you should use the methods that inform
 *     the thread system that you are blocking.  Methods that have the
 *     "Nicely" suffix will inform the thread system if you are blocked,
 *     while methods that do not have the suffix will either not block
 *     (as is the case with unlock and broadcast) or will block without
 *     letting anyone know (like lock and wait).  Not letting the threading
 *     system know that you are blocked may cause things like GC to stall
 *     until you unblock.</li>
 * </ul>
 */
@Uninterruptible
@NonMoving
public class Monitor {
  Word monitor;
  int holderSlot=-1; // use the slot so that we're even more GC safe
  int recCount;
  public int acquireCount;
  /**
   * Allocate a heavy condition variable and lock.  This involves
   * allocating stuff in C that never gets deallocated.  Thus, don't
   * instantiate too many of these.
   */
  public Monitor() {
    monitor = sysCall.sysMonitorCreate();
  }
  /**
   * Wait until it is possible to acquire the lock and then acquire it.
   * There is no bound on how long you might wait, if someone else is
   * holding the lock and there is no bound on how long they will hold it.
   * As well, even if there is a bound on how long a thread might hold a
   * lock but there are multiple threads contending on its acquisition,
   * there will not necessarily be a bound on how long any particular
   * thread will wait until it gets its turn.
   * <p>
   * This blocking method method does not notify the threading subsystem
   * that it is blocking.  Thus, if someone (like, say, the GC) requests
   * that the thread is blocked then their request will block until this
   * method unblocks.  If this sounds like it might be undesirable, call
   * lockNicely instead.
   */
  @NoInline
  @NoOptCompile
  public void lockNoHandshake() {
    int mySlot = RVMThread.getCurrentThreadSlot();
    if (mySlot != holderSlot) {
      sysCall.sysMonitorEnter(monitor);
      if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
      if (VM.VerifyAssertions) VM._assert(recCount==0);
      holderSlot = mySlot;
    }
    recCount++;
    acquireCount++;
  }
  /**
   * Relock the mutex after using unlockCompletely.
   */
  @NoInline
  @NoOptCompile
  public void relockNoHandshake(int recCount) {
    sysCall.sysMonitorEnter(monitor);
    if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
    if (VM.VerifyAssertions) VM._assert(this.recCount==0);
    holderSlot=RVMThread.getCurrentThreadSlot();
    this.recCount=recCount;
    acquireCount++;
  }
  /**
   * Wait until it is possible to acquire the lock and then acquire it.
   * There is no bound on how long you might wait, if someone else is
   * holding the lock and there is no bound on how long they will hold it.
   * As well, even if there is a bound on how long a thread might hold a
   * lock but there are multiple threads contending on its acquisition,
   * there will not necessarily be a bound on how long any particular
   * thread will wait until it gets its turn.
   * <p>
   * This blocking method method notifies the threading subsystem that it
   * is blocking.  Thus, it may be safer than calling lock.  But,
   * its reliance on threading subsystem accounting methods may mean that
   * it cannot be used in certain contexts (say, the threading subsystem
   * itself).
   * <p>
   * This method will ensure that if it blocks, it does so with the
   * mutex not held.  This is useful for cases where the subsystem that
   * requested you to block needs to acquire the lock you were trying to
   * acquire when the blocking request came.
   * <p>
   * It is usually not necessary to call this method instead of lock(),
   * since most VM locks are held for short periods of time.
   */
  @Unpreemptible("If the lock cannot be acquired, this method will allow the thread to be asynchronously blocked")
  @NoInline
  @NoOptCompile
  public void lockWithHandshake() {
    int mySlot = RVMThread.getCurrentThreadSlot();
    if (mySlot != holderSlot) {
      lockWithHandshakeNoRec();
      if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
      if (VM.VerifyAssertions) VM._assert(recCount==0);
      holderSlot = mySlot;
    }
    recCount++;
    acquireCount++;
  }
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  private void lockWithHandshakeNoRec() {
    RVMThread.saveThreadState();
    lockWithHandshakeNoRecImpl();
  }
  @NoInline
  @Unpreemptible
  @NoOptCompile
  private void lockWithHandshakeNoRecImpl() {
    for (;;) {
      RVMThread.enterNative();
      sysCall.sysMonitorEnter(monitor);
      if (RVMThread.attemptLeaveNativeNoBlock()) {
        return;
      } else {
        sysCall.sysMonitorExit(monitor);
        RVMThread.leaveNative();
      }
    }
  }
  /**
   * Relock the mutex after using unlockCompletely, but do so "nicely".
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible("If the lock cannot be reacquired, this method may allow the thread to be asynchronously blocked")
  public void relockWithHandshake(int recCount) {
    RVMThread.saveThreadState();
    relockWithHandshakeImpl(recCount);
  }
  @NoInline
  @Unpreemptible
  @NoOptCompile
  private void relockWithHandshakeImpl(int recCount) {
    for (;;) {
      RVMThread.enterNative();
      sysCall.sysMonitorEnter(monitor);
      if (RVMThread.attemptLeaveNativeNoBlock()) {
        break;
      } else {
        sysCall.sysMonitorExit(monitor);
        RVMThread.leaveNative();
      }
    }
    if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
    if (VM.VerifyAssertions) VM._assert(this.recCount==0);
    holderSlot=RVMThread.getCurrentThreadSlot();
    this.recCount=recCount;
  }
  /**
   * Release the lock.  This method should (in principle) be non-blocking,
   * and, as such, it does not notify the threading subsystem that it is
   * blocking.
   */
  @NoInline
  @NoOptCompile
  public void unlock() {
    if (--recCount==0) {
      holderSlot=-1;
      sysCall.sysMonitorExit(monitor);
    }
  }
  /**
   * Completely release the lock, ignoring recursion.  Returns the
   * recursion count.
   */
  @NoInline
  @NoOptCompile
  public int unlockCompletely() {
    int result=recCount;
    recCount=0;
    holderSlot=-1;
    sysCall.sysMonitorExit(monitor);
    return result;
  }
  /**
   * Wait until someone calls broadcast.
   * <p>
   * This blocking method method does not notify the threading subsystem
   * that it is blocking.  Thus, if someone (like, say, the GC) requests
   * that the thread is blocked then their request will block until this
   * method unblocks.  If this sounds like it might be undesirable, call
   * waitNicely instead.
   */
  @NoInline
  @NoOptCompile
  public void waitNoHandshake() {
    int recCount=this.recCount;
    this.recCount=0;
    holderSlot=-1;
    sysCall.sysMonitorWait(monitor);
    if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
    if (VM.VerifyAssertions) VM._assert(this.recCount==0);
    this.recCount=recCount;
    holderSlot=RVMThread.getCurrentThreadSlot();
  }
  /**
   * Wait until someone calls broadcast, or until the clock reaches the
   * given time.
   * <p>
   * This blocking method method does not notify the threading subsystem
   * that it is blocking.  Thus, if someone (like, say, the GC) requests
   * that the thread is blocked then their request will block until this
   * method unblocks.  If this sounds like it might be undesirable, call
   * timedWaitAbsoluteNicely instead.
   */
  @NoInline
  @NoOptCompile
  public void timedWaitAbsoluteNoHandshake(long whenWakeupNanos) {
    int recCount=this.recCount;
    this.recCount=0;
    holderSlot=-1;
    sysCall.sysMonitorTimedWaitAbsolute(monitor, whenWakeupNanos);
    if (VM.VerifyAssertions) VM._assert(holderSlot==-1);
    if (VM.VerifyAssertions) VM._assert(this.recCount==0);
    this.recCount=recCount;
    holderSlot=RVMThread.getCurrentThreadSlot();
  }
  /**
   * Wait until someone calls broadcast, or until at least the given
   * number of nanoseconds pass.
   * <p>
   * This blocking method method does not notify the threading subsystem
   * that it is blocking.  Thus, if someone (like, say, the GC) requests
   * that the thread is blocked then their request will block until this
   * method unblocks.  If this sounds like it might be undesirable, call
   * timedWaitRelativeNicely instead.
   */
  @NoInline
  @NoOptCompile
  public void timedWaitRelativeNoHandshake(long delayNanos) {
    long now=sysCall.sysNanoTime();
    timedWaitAbsoluteNoHandshake(now+delayNanos);
  }
  /**
   * Wait until someone calls broadcast.
   * <p>
   * This blocking method notifies the threading subsystem that it
   * is blocking.  Thus, it is generally safer than calling wait.  But,
   * its reliance on threading subsystem accounting methods may mean that
   * it cannot be used in certain contexts (say, the threading subsystem
   * itself).
   * <p>
   * This method will ensure that if it blocks, it does so with the
   * mutex not held.  This is useful for cases where the subsystem that
   * requested you to block needs to acquire the lock you were trying to
   * acquire when the blocking request came.
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible("While the thread is waiting, this method may allow the thread to be asynchronously blocked")
  public void waitWithHandshake() {
    RVMThread.saveThreadState();
    waitWithHandshakeImpl();
  }
  @NoInline
  @Unpreemptible
  @NoOptCompile
  private void waitWithHandshakeImpl() {
    RVMThread.enterNative();
    waitNoHandshake();
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockWithHandshakeImpl(recCount);
  }
  /**
   * Wait until someone calls broadcast, or until the clock reaches the
   * given time.
   * <p>
   * This blocking method method notifies the threading subsystem that it
   * is blocking.  Thus, it is generally safer than calling
   * timedWaitAbsolute.  But, its reliance on threading subsystem accounting
   * methods may mean that it cannot be used in certain contexts (say, the
   * threading subsystem itself).
   * <p>
   * This method will ensure that if it blocks, it does so with the
   * mutex not held.  This is useful for cases where the subsystem that
   * requested you to block needs to acquire the lock you were trying to
   * acquire when the blocking request came.
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible("While the thread is waiting, this method may allow the thread to be asynchronously blocked")
  public void timedWaitAbsoluteWithHandshake(long whenWakeupNanos) {
    RVMThread.saveThreadState();
    timedWaitAbsoluteWithHandshakeImpl(whenWakeupNanos);
  }
  @NoInline
  @Unpreemptible
  @NoOptCompile
  private void timedWaitAbsoluteWithHandshakeImpl(long whenWakeupNanos) {
    RVMThread.enterNative();
    timedWaitAbsoluteNoHandshake(whenWakeupNanos);
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockWithHandshakeImpl(recCount);
  }
  /**
   * Wait until someone calls broadcast, or until at least the given
   * number of nanoseconds pass.
   * <p>
   * This blocking method method notifies the threading subsystem that it
   * is blocking.  Thus, it is generally safer than calling
   * timedWaitRelative.  But, its reliance on threading subsystem accounting
   * methods may mean that it cannot be used in certain contexts (say, the
   * threading subsystem itself).
   * <p>
   * This method will ensure that if it blocks, it does so with the
   * mutex not held.  This is useful for cases where the subsystem that
   * requested you to block needs to acquire the lock you were trying to
   * acquire when the blocking request came.
   */
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible("While the thread is waiting, this method may allow the thread to be asynchronously blocked")
  public void timedWaitRelativeWithHandshake(long delayNanos) {
    RVMThread.saveThreadState();
    timedWaitRelativeWithHandshakeImpl(delayNanos);
  }
  @NoInline
  @Unpreemptible
  @NoOptCompile
  private void timedWaitRelativeWithHandshakeImpl(long delayNanos) {
    RVMThread.enterNative();
    timedWaitRelativeNoHandshake(delayNanos);
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockWithHandshakeImpl(recCount);
  }

  /**
   * Send a broadcast, which should awaken anyone who is currently blocked
   * in any of the wait methods.  This method should (in principle) be
   * non-blocking, and, as such, it does not notify the threading subsystem
   * that it is blocking.
   */
  @NoInline
  @NoOptCompile
  public void broadcast() {
    sysCall.sysMonitorBroadcast(monitor);
  }
  /**
   * Send a broadcast after first acquiring the lock.  Release the lock
   * after sending the broadacst.  In most cases where you want to send
   * a broadcast but you don't need to acquire the lock to set the
   * condition that the other thread(s) are waiting on, you want to call
   * this method instead of <code>broadcast</code>.
   */
  @NoInline
  @NoOptCompile
  public void lockedBroadcastNoHandshake() {
    lockNoHandshake();
    broadcast();
    unlock();
  }

  @NoInline
  public static boolean lockNoHandshake(Monitor l) {
    if (l==null) {
      return false;
    } else {
      l.lockNoHandshake();
      return true;
    }
  }
  @NoInline
  public static void unlock(boolean b, Monitor l) {
    if (b) l.unlock();
  }

  @NoInline
  @NoOptCompile
  @Unpreemptible
  public static void lockWithHandshake(Monitor m1,Word priority1,
                                       Monitor m2,Word priority2) {
    if (priority1.LE(priority2)) {
      m1.lockWithHandshake();
      m2.lockWithHandshake();
    } else {
      m2.lockWithHandshake();
      m1.lockWithHandshake();
    }
  }
}

