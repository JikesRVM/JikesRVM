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
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.NonMoving;

/**
 * Organizer thread collects OSR requests and inserted in controller queue
 * The producers are application threads, and the consumer thread is the
 * organizer. The buffer is RVMThread.threads array. The producer set
 * it is own flag "requesting_osr" and notify the consumer. The consumer
 * scans the threads array and collect requests.
 */
@NonMoving
public final class OSROrganizerThread extends RVMThread {
  /** Constructor */
  public OSROrganizerThread() {
    super("OSR_Organizer");
    makeDaemon(true);
  }

  public boolean osr_flag = false;

  @Override
  public void run() {
    while (true) {
      monitor().lockNoHandshake();
      if (!this.osr_flag) {
        monitor().waitWithHandshake();
      }
      this.osr_flag=false; /* if we get another activation after here
                              then we should rescan the threads array */
      monitor().unlock();

      processOsrRequest();
    }
  }

  /**
   * Activates organizer thread if it is waiting.
   */
  @Uninterruptible
  public void activate() {
    monitor().lockNoHandshake();
    osr_flag=true;
    monitor().broadcast();
    monitor().unlock();
  }

  // proces osr request
  private void processOsrRequest() {
    // scan RVMThread.threads (scan down so we don't miss anything)
    for (int i=RVMThread.numThreads-1;i>=0;i--) {
      Magic.sync();
      RVMThread t=RVMThread.threads[i];
      if (t!=null) {
        boolean go=false;
        t.monitor().lockNoHandshake();
        // NOTE: if threads are being removed, we may see a thread twice
        if (t.requesting_osr) {
          t.requesting_osr=false;
          go=true;
        }
        t.monitor().unlock();
        if (go) {
          Controller.controllerInputQueue.insert(5.0, t.onStackReplacementEvent);
        }
      }
    }
  }
}
