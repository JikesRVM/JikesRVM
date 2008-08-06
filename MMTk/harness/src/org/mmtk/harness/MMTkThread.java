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
package org.mmtk.harness;


import org.mmtk.utility.Log;

/**
 * This class represents an MMTk thread (mutator or collector).
 */
public class MMTkThread extends Thread {
  /** The log associated with this thread */
  private final Log log = new Log();

  /**
   * Get the currently executing mutator.
   */
  public static MMTkThread current() {
    assert Thread.currentThread() instanceof MMTkThread  : "Current thread does is not an MMTk thread";
    return (MMTkThread)Thread.currentThread();
  }

  /**
   * Create an MMTk thread.
   *
   * @param entryPoint The entryPoint.
   */
  protected MMTkThread(Runnable entryPoint) {
    super(entryPoint);
    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        System.err.print("Unexpected exception: ");
        e.printStackTrace();
        System.exit(1);
      }
    });
  }

  /**
   * Create an MMTk thread.
   */
  protected MMTkThread() {
    this(null);
  }

  /**
   * Get the log for this MMTk thread (mutator or collector).
   */
  public final Log getLog() {
    return log;
  }
}
