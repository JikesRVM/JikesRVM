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

class TestVolatileStaticLongsUnresolved extends XThread {

  public static void main(String[] args) {
    for (int i = 0; i < 5; i++) {
      TestVolatileStaticLongsUnresolved tvslu = new TestVolatileStaticLongsUnresolved(i);
      tvslu.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static volatile int vi = 0;

  int n;
  long l;

  TestVolatileStaticLongsUnresolved(int i) {
    super("VSLU" + i);
    n = i;
    l = (((long) n) << 32) + n;
  }

  void performTask() {
    int errors = 0;
    for (int i = 0; i < 10000000; i++) {
      long tl = StaticVolatileLongField.vl;
      StaticVolatileLongField.vl = l;
      int n0 = (int) tl;
      int n1 = (int) (tl >> 32);
      if (n0 != n1) errors++;
      vi = n;
    }
    tsay(errors + " errors found");
  }

  private static class StaticVolatileLongField {
    static volatile long vl = 0;
  }

}
