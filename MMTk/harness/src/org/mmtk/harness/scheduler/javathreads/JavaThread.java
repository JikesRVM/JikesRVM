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
package org.mmtk.harness.scheduler.javathreads;


import org.mmtk.harness.MMTkThread;
import org.mmtk.harness.scheduler.Policy;
import org.mmtk.harness.scheduler.Scheduler;

/**
 * This class represents an MMTk thread (mutator or collector).
 */
public class JavaThread extends MMTkThread {

  /**
   * The command-line selected yield policy
   */
  private Policy yieldPolicy = Scheduler.yieldPolicy(this);

  /**
   * Create an MMTk thread.
   *
   * @param entryPoint The entryPoint.
   */
  protected JavaThread(Runnable entryPoint) {
    super(entryPoint);
  }

  /**
   * Create an MMTk thread.
   */
  protected JavaThread() {
  }

  boolean yieldPolicy() {
    return yieldPolicy.yieldNow();
  }
}
