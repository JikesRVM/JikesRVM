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
public class virtUnPutGet {
  static boolean run() {
    int i = test(6000);
    System.out.println("virtUnPutGet returned: " + i);
    return true;
  }

  static int f1 = 0;

  public static int test(int n) {

    vTest3 vt = new vTest3();

    vt.ppp();

    vt.tval += n;
    vt.ppp();

    return vt.tval;
  }

}

class vTest3 {

  int tval = 1000;

  void ppp() {
    //    System.out.println(" tval = " + tval);
  }

}
