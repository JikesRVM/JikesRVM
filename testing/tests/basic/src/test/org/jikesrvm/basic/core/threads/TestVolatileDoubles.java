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

class TestVolatileDoubles extends XThread {

  public static void main(String[] args) {
    VolatileDoubleField vdf = new VolatileDoubleField();
    for (int i = 0; i < 5; i++) {
      TestVolatileDoubles tvd = new TestVolatileDoubles(vdf, doubleValues[i], i);
      tvd.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static double[] doubleValues = { 0.1d, 5934093850936.32940348509376d, 123456.7891011d, -0.00000100023500008d, 1.37470092062392304E17d};

  double d;
  VolatileDoubleField vdf;

  TestVolatileDoubles(VolatileDoubleField vdf, double d, int threadNumber) {
    super("VD" + threadNumber);
    this.vdf = vdf;
    this.d = d;
  }

  void performTask() {
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

  private static class VolatileDoubleField {
    volatile double vd = doubleValues[0];
  }

}
