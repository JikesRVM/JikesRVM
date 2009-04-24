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
public class virtUnresolved {
  static boolean run() {
    int i = test(20);
    System.out.println("virtUnresolved returned: " + i);
    return true;
  }

  static int f1 = 0;

  public static int test(int n) {
    new virtUnresolved();
    virtTest vt = new virtTest();
    vt.ppp(n);
    vt.ppp();

    return f1;
  }

}

class virtTest {

  int tval = 0;
  void ppp(int tv) {
    tval = tv;
    virtUnresolved.f1 = tv + 100;
  }

  void ppp() {
  }
}
