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

class TestVolatileStaticDoubles extends XThread {

  public static void main(String[] args) {
    for (int i = 0; i < 5; i++) {
      TestVolatileStaticDoubles tvsd = new TestVolatileStaticDoubles(doubleValues[i], i);
      tvsd.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static double[] doubleValues = { 0.1d, 5934093850936.32940348509376d, 123456.7891011d, -0.00000100023500008d, 1.37470092062392304E17d};

  static volatile double vd = doubleValues[0];

  double d;

  TestVolatileStaticDoubles(double d, int threadNumber) {
    super("VSD" + threadNumber);
    this.d = d;
  }

  void performTask() {
    int errors = 0;
    for (int i = 0; i < 10000000; i++) {
      double td = vd;
      vd = d;
      boolean acceptableValue = false;
      for (int j = 0; j < 5; j++) {
        acceptableValue = acceptableValue || (td == doubleValues[j]);
      }
      if (!acceptableValue) errors++;
    }
    tsay(errors + " errors found");
  }
}
