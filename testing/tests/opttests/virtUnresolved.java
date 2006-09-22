/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */

import java.io.*;

public class virtUnresolved {
  static boolean run() {
    int i = test(20);
    System.out.println("virtUnresolved returned: " + i);
    return true;
  }

  static int f1 = 0;

  public static int test(int n) {

    virtUnresolved vur = new virtUnresolved();

    virtTest vt = new virtTest();

    vt.ppp(n);

    //    vt.tval = 1000;
    //    vt.ppp();

    //    vt.tval += 2000;
    vt.ppp();
 
    return f1;
  }

}

class virtTest {

  int tval = 0;
  void ppp(int tv) {
    
    //    System.out.println("  tv = " + tv);
    tval = tv;
    virtUnresolved.f1 = tv + 100;
  }

  void ppp() {
    //    System.out.println(" tval = " + tval);
  }

}
