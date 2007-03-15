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
