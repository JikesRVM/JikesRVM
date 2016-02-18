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

class TestVolatileDoublesUnresolved extends XThread {

  public static void main(String[] args) {
    TestVolatileDoublesUnresolved[] tvdu_array = new TestVolatileDoublesUnresolved[5];

    // Start the threads. They'll block immediately on the monitor. This ensures that
    // we get unresolved putfields and getfields in performTask() because the method
    // gets compiled before VolatileDoubleField has been resolved.
    Object monitor = new Object();
    for (int i = 0; i < 5; i++) {
      TestVolatileDoublesUnresolved tvdu = new TestVolatileDoublesUnresolved(doubleValues[i], i, monitor);
      tvdu_array[i] = tvdu;
      tvdu.start();
    }

    VolatileDoubleField vdf = new VolatileDoubleField();
    for (int i = 0; i < 5; i++) {
      tvdu_array[i].prepareThreadForWork(vdf);
    }
    synchronized (monitor) {
      monitor.notifyAll();
    }

    XThread.say("bye");
    XThread.outputMessages();
  }

  static double[] doubleValues = { 0.1d, 5934093850936.32940348509376d, 123456.7891011d, -0.00000100023500008d, 1.37470092062392304E17d};

  double d;
  VolatileDoubleField vdf;
  Object monitor;
  boolean block = true;

  TestVolatileDoublesUnresolved(double d, int threadNumber, Object monitor) {
    super("VDU" + threadNumber);
    this.vdf = vdf;
    this.d = d;
    this.monitor = monitor;
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
      double td = vdf.vd;
      vdf.vd = d;
      boolean acceptableValue = false;
      for (int j = 0; j < 5; j++) {
        acceptableValue = acceptableValue || (td == doubleValues[j]);
      }
      if (!acceptableValue) errors++;
    }
    tsay(errors + " errors found");
  }

  void prepareThreadForWork(VolatileDoubleField vdf) {
    this.vdf = vdf;
    block = false;
  }

  private static class VolatileDoubleField {
    volatile double vd = doubleValues[0];
  }

}
