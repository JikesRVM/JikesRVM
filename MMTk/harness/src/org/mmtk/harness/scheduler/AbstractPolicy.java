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

import java.util.ArrayList;
import java.util.List;

import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Trace.Item;

/**
 * Abstract superclass of scheduling policies.
 */
public abstract class AbstractPolicy implements Policy {
  private static List<AbstractPolicy> policies = new ArrayList<AbstractPolicy>();

  public static void printStats() {
    for (AbstractPolicy p : policies) {
      System.out.printf(p.formatStats());
    }
  }

  /** Number of times we have yielded */
  private int yieldCount = 0;

  /** The thread whose pilicy we control */
  private final Thread thread;

  AbstractPolicy(Thread thread, String name) {
    Trace.trace(Item.SCHEDULER, "Creating scheduler policy %s", name);
    this.thread = thread;
    policies.add(this);
  }

  /**
   * Accumulate statistics
   */
  private void yieldTaken() {
    yieldCount++;
  }

  /**
   * The policy specific method
   * @return
   */
  protected abstract boolean taken();

  /**
   * The public method of the Policy interface
   * @return
   */
  @Override
  public final boolean yieldNow() {
    if (taken()) {
      yieldTaken();
      return true;
    }
    return false;
  }

  /**
   * Policy-specific formatting
   */
  protected String formatPolicy() {
    return "";
  }

  /**
   * Format yield statistics as a string
   */
  public String formatStats() {
    String policy = formatPolicy();
    if (!policy.equals("")) {
      policy = " ("+policy+")";
    }
    return String.format("%s, %d yields%s%n",thread.getName(),yieldCount,policy);
  }
}
