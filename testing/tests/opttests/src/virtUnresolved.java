/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
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
