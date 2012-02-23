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

import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;

/**
 */
@Uninterruptible
@NonMoving
public abstract class SystemThread {

  protected final RVMThread rvmThread;

  protected SystemThread(String name) {
    rvmThread = new RVMThread(this, name);
    rvmThread.makeDaemon(true);
  }

  protected SystemThread(byte[] stack, String name) {
    rvmThread = new RVMThread(this, stack, name);
    rvmThread.makeDaemon(true);
  }

  public RVMThread getRVMThread() {
    return rvmThread;
  }

  @Interruptible
  public void start() {
    rvmThread.start();
  }

  public void stop(Throwable cause) {
    rvmThread.stop(cause);
  }

  @Interruptible
  public abstract void run();
}
