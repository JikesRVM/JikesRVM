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
package org.jikesrvm.scheduler;

import static org.jikesrvm.runtime.SysCall.sysCall;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.Untraced;
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
public class HeavyCondLock {
  Word mutex;
  Word cond;
  @Untraced RVMThread holder;
  int recCount;
  public int acquireCount;
  /**
   * Allocate a heavy condition variable and lock.  This involves
   * allocating stuff in C that never gets deallocated.  Thus, don't
   * instantiate too many of these.
   */
  public HeavyCondLock() {
    mutex=sysCall.sysPthreadMutexCreate();
    cond=sysCall.sysPthreadCondCreate();
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
  public void lock() {
    RVMThread t = RVMThread.getCurrentThread();
    if (t != holder) {
      sysCall.sysPthreadMutexLock(mutex);
      holder = t;
    }
    recCount++;
    acquireCount++;
  }
  /**
   * Relock the mutex after using unlockCompletely.
   */
  public void relock(int recCount) {
    sysCall.sysPthreadMutexLock(mutex);
    holder=RVMThread.getCurrentThread();
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
  public void lockNicely() {
    RVMThread t = RVMThread.getCurrentThread();
    if (t != holder) {
      lockNicelyNoRec();
      holder = t;
    }
    recCount++;
    acquireCount++;
  }
  @NoInline
  @NoOptCompile
  @BaselineSaveLSRegisters
  @Unpreemptible
  private void lockNicelyNoRec() {
    RVMThread.saveThreadState();
    lockNicelyNoRecImpl();
  }
  @NoInline
  @Unpreemptible
  private void lockNicelyNoRecImpl() {
    for (;;) {
      RVMThread.enterNative();
      sysCall.sysPthreadMutexLock(mutex);
      if (RVMThread.attemptLeaveNativeNoBlock()) {
        return;
      } else {
        sysCall.sysPthreadMutexUnlock(mutex);
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
  public void relockNicely(int recCount) {
    RVMThread.saveThreadState();
    relockNicelyImpl(recCount);
  }
  @NoInline
  @Unpreemptible
  private void relockNicelyImpl(int recCount) {
    for (;;) {
      RVMThread.enterNative();
      sysCall.sysPthreadMutexLock(mutex);
      if (RVMThread.attemptLeaveNativeNoBlock()) {
        break;
      } else {
        sysCall.sysPthreadMutexUnlock(mutex);
        RVMThread.leaveNative();
      }
    }
    holder=RVMThread.getCurrentThread();
    this.recCount=recCount;
  }
  /**
   * Release the lock.  This method should (in principle) be non-blocking,
   * and, as such, it does not notify the threading subsystem that it is
   * blocking.
   */
  public void unlock() {
    if (--recCount==0) {
      holder=null;
      sysCall.sysPthreadMutexUnlock(mutex);
    }
  }
  /**
   * Completely release the lock, ignoring recursion.  Returns the
   * recursion count.
   */
  public int unlockCompletely() {
    int result=recCount;
    recCount=0;
    holder=null;
    sysCall.sysPthreadMutexUnlock(mutex);
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
  public void await() {
    int recCount=this.recCount;
    this.recCount=0;
    holder=null;
    sysCall.sysPthreadCondWait(cond,mutex);
    this.recCount=recCount;
    holder=RVMThread.getCurrentThread();
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
  public void timedWaitAbsolute(long whenWakeupNanos) {
    int recCount=this.recCount;
    this.recCount=0;
    holder=null;
    sysCall.sysPthreadCondTimedWait(cond,mutex,whenWakeupNanos);
    this.recCount=recCount;
    holder=RVMThread.getCurrentThread();
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
  public void timedWaitRelative(long delayNanos) {
    long now=sysCall.sysNanoTime();
    timedWaitAbsolute(now+delayNanos);
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
  public void waitNicely() {
    RVMThread.saveThreadState();
    waitNicelyImpl();
  }
  @NoInline
  @Unpreemptible
  private void waitNicelyImpl() {
    RVMThread.enterNative();
    await();
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockNicelyImpl(recCount);
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
  public void timedWaitAbsoluteNicely(long whenWakeupNanos) {
    RVMThread.saveThreadState();
    timedWaitAbsoluteNicelyImpl(whenWakeupNanos);
  }
  @NoInline
  @Unpreemptible
  private void timedWaitAbsoluteNicelyImpl(long whenWakeupNanos) {
    RVMThread.enterNative();
    timedWaitAbsolute(whenWakeupNanos);
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockNicelyImpl(recCount);
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
  public void timedWaitRelativeNicely(long delayNanos) {
    RVMThread.saveThreadState();
    timedWaitRelativeNicelyImpl(delayNanos);
  }
  @NoInline
  @Unpreemptible
  private void timedWaitRelativeNicelyImpl(long delayNanos) {
    RVMThread.enterNative();
    timedWaitRelative(delayNanos);
    int recCount=unlockCompletely();
    RVMThread.leaveNative();
    relockNicelyImpl(recCount);
  }

  /**
   * Send a broadcast, which should awaken anyone who is currently blocked
   * in any of the wait methods.  This method should (in principle) be
   * non-blocking, and, as such, it does not notify the threading subsystem
   * that it is blocking.
   */
  public void broadcast() {
    sysCall.sysPthreadCondBroadcast(cond);
  }
  /**
   * Send a broadcast after first acquiring the lock.  Release the lock
   * after sending the broadacst.  In most cases where you want to send
   * a broadcast but you don't need to acquire the lock to set the
   * condition that the other thread(s) are waiting on, you want to call
   * this method instead of <code>broadcast</code>.
   */
  public void lockedBroadcast() {
    lock();
    broadcast();
    unlock();
  }
  // NOTE: these methods below used to have a purpose but that purpose
  // disappeared as I was switching around designs.  I'm keeping these
  // methods here because they may potentially be useful again, but it
  // might be a good idea to ax them if they are truly without a use.
  @NoInline
  public static void lock(HeavyCondLock m1,Word priority1,
                          HeavyCondLock m2,Word priority2) {
    if (priority1.LE(priority2)) {
      m1.lock();
      m2.lock();
    } else {
      m2.lock();
      m1.lock();
    }
  }
  @NoInline
  public static void lock(HeavyCondLock m1,
                          HeavyCondLock m2) {
    lock(m1,m1.mutex,
         m2,m2.mutex);
  }
  @NoInline
  public static void lock(HeavyCondLock m1,Word priority1,
                          HeavyCondLock m2,Word priority2,
                          HeavyCondLock m3,Word priority3) {
    if (priority1.LE(priority2) &&
        priority1.LE(priority3)) {
      m1.lock();
      lock(m2,priority2,
           m3,priority3);
    } else if (priority2.LE(priority1) &&
               priority2.LE(priority3)) {
      m2.lock();
      lock(m1,priority1,
           m3,priority3);
    } else {
      m3.lock();
      lock(m1,priority1,
           m2,priority2);
    }
  }
  @NoInline
  public static void lock(HeavyCondLock m1,
                          HeavyCondLock m2,
                          HeavyCondLock m3) {
    lock(m1,m1.mutex,
         m2,m2.mutex,
         m3,m3.mutex);
  }
  @NoInline
  public static boolean lock(HeavyCondLock l) {
    if (l==null) {
      return false;
    } else {
      l.lock();
      return true;
    }
  }
  @NoInline
  public static void unlock(boolean b,HeavyCondLock l) {
    if (b) l.unlock();
  }
}

