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
package org.mmtk.harness.scheduler;

import org.mmtk.utility.Log;

/**
 * Superclass of all threads in the MMTk harness - this is the public interface
 * to threads from outside the scheduler.
 */
public class MMTkThread extends Thread {

  /** The per-thread Log instance */
  protected final Log log = new Log();

  /**
   * The command-line selected yield policy
   */
  private final Policy yieldPolicy = Scheduler.yieldPolicy(this);

  /**
   * @return The MMTk Log object for this thread
   */
  public final Log getLog() {
    return log;
  }

  /**
   * @return True if the current yield policy requires a yield now.
   */
  public boolean yieldPolicy() {
    return yieldPolicy.yieldNow();
  }
}
