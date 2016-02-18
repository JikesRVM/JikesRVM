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

class TestVolatileLongs extends XThread {

  public static void main(String[] args) {
    VolatileLongField vlf = new VolatileLongField();
    for (int i = 0; i < 5; i++) {
      TestVolatileLongs tvl = new TestVolatileLongs(i,vlf);
      tvl.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static volatile int vi = 0;

  int n;
  long l;
  VolatileLongField vlf;

  TestVolatileLongs(int i, VolatileLongField vlf) {
    super("VL" + i);
    n = i;
    l = (((long) n) << 32) + n;
    this.vlf = vlf;
  }

  void performTask() {
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
