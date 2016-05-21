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
package org.mmtk.harness.tests;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicReference;

public class BaseMMTkTest {

  protected void runMMTkThread(Thread t) throws Throwable {
    final AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();

    t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
      @Override
      public void uncaughtException(Thread t, Throwable e) {
        thrown.set(e);
      }
    });
    t.start();
    t.join();
    if (thrown.get() != null) {
      throw thrown.get();
    }
  }

}
