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
package org.mmtk.harness;

import org.mmtk.utility.Log;

public class MMTkThread extends Thread {

  /** The per-thread Log instance */
  protected final Log log = new Log();

  public MMTkThread() {
    super();
    trapUncaughtExceptions();
  }

  public MMTkThread(Runnable target, String name) {
    super(target, name);
    trapUncaughtExceptions();
  }

  public MMTkThread(Runnable target) {
    super(target);
    trapUncaughtExceptions();
  }

  public MMTkThread(String name) {
    super(name);
    trapUncaughtExceptions();
  }

  private void trapUncaughtExceptions() {
    setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        System.err.print("Unexpected exception: ");
        e.printStackTrace();
        System.exit(1);
      }
    });
  }

  public final Log getLog() {
    return log;
  }
}
