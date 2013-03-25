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
package test.org.jikesrvm.basic.core.threads;

class TestVolatileLongsUnresolved extends XThread {

  public static void main(String[] args) {
    TestVolatileLongsUnresolved[] tvlu_array = new TestVolatileLongsUnresolved[5];

    // Start the threads. They'll block immediately on the monitor. This ensures that
    // we get unresolved putfields and getfields in performTask() because the method
    // gets compiled before VolatileLongField has been resolved.
    Object monitor = new Object();
    for (int i = 0; i < 5; i++) {
      TestVolatileLongsUnresolved tvlu = new TestVolatileLongsUnresolved(i, monitor);
      tvlu_array[i] = tvlu;
      tvlu.start();
    }

    VolatileLongField vlf = new VolatileLongField();
    for (int i = 0; i < 5; i++) {
      tvlu_array[i].prepareThreadForWork(vlf);
    }
    synchronized (monitor) {
      monitor.notifyAll();
    }

    XThread.say("bye");
    XThread.outputMessages();
  }

  static volatile int vi = 0;

  int n;
  long l;
  VolatileLongField vlf;
  Object monitor;
  boolean block = true;

  TestVolatileLongsUnresolved(int i, Object monitor) {
    super("VLU" + i);
    n = i;
    l = (((long) n) << 32) + n;
    this.monitor = monitor;
  }

  void prepareThreadForWork(VolatileLongField vlf) {
    this.vlf = vlf;
    block = false;
  }

  void performTask() {
    synchronized (monitor) {
      try {
        while (block) {
          monitor.wait();
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    int errors = 0;
    for (int i = 0; i < 10000000; i++) {
      long tl = vlf.vl;
      vlf.vl = l;
      int n0 = (int) tl;
      int n1 = (int) (tl >> 32);
      if (n0 != n1) errors++;
      vi = n;
    }
    tsay(errors + " errors found");
  }

  private static class VolatileLongField {
    volatile long vl = 0;
  }

}
