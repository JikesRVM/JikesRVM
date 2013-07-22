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

class TestVolatileStaticLongs extends XThread {

  public static void main(String[] args) {
    for (int i = 0; i < 5; i++) {
      TestVolatileStaticLongs tvsl = new TestVolatileStaticLongs(i);
      tvsl.start();
    }
    XThread.say("bye");
    XThread.outputMessages();
  }

  static volatile long vl = 0;
  static volatile int vi = 0;

  int n;
  long l;

  TestVolatileStaticLongs(int i) {
    super("VSL" + i);
    n = i;
    l = (((long) n) << 32) + n;
  }

  void performTask() {
    int errors = 0;
    for (int i = 0; i < 10000000; i++) {
      long tl = vl;
      vl = l;
      int n0 = (int) tl;
      int n1 = (int) (tl >> 32);
      if (n0 != n1) errors++;
      vi = n;
    }
    tsay(errors + " errors found");
  }
}
