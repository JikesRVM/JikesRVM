/*
 * (C) Copyright IBM Corp. 2001
 */

import java.io.*;

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
