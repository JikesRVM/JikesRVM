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

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.NoOptCompile;
import org.vmmagic.pragma.NoInline;
import org.jikesrvm.VM;

/**
 * A heavy condition variable and lock that also disables interrupts while
 * the lock is held.  Extremely useful for any locks that may be acquired,
 * released, or waited upon in the process of performing a GC.
 * <p>
 * Note that calling any of the withHandshake methods on an instance of this
 * class is extremely dangerous.  These methods may cause you to block on
 * GC, which seemingly goes against the very intent of this being a "no
 * interrupts" condition variable and lock.  However, it makes a subtle
 * kind of sense to use these methods, <i>if you're calling them on the
 * instance of NoInterruptsCondLock that your thread will wait on when
 * blocking on GC</i>.  This idiom is used quite a bit.
 * <p>
 * To ensure that the withHandshake methods are used correctly - that is, that
 * they are only used by the thread that owns the lock - there are assertions
 * in place to ensure that the caller is the owner.
 */
@Uninterruptible
@NonMoving
public class NoYieldpointsMonitor extends Monitor {
  @Override
  @NoInline
  @NoOptCompile
  public void lockNoHandshake() {
    VM.disableYieldpoints();
    super.lockNoHandshake();
  }

  // This method is strange
  @Override
  @Unpreemptible
  @NoInline
  @NoOptCompile
  public void lockWithHandshake() {
    VM.disableYieldpoints();
    super.lockWithHandshake();
  }

  @Override
  @NoInline
  @NoOptCompile
  public void unlock() {
    super.unlock();
    VM.enableYieldpoints();
  }
}

