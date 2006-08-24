/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * unresolved putstatic/getstatic test
 *
 * @author unascribed
 */
import java.io.*;

public class stPutGet {
  static boolean run() {
    int i = test(6000);
    System.out.println("stPutGet returned: " + i);
    return true;
  }

  public static int test(int n) {

    //    TestC2.ppp();

    int f1 = TestC2.tval;
    //    System.out.println(" In test: tval = " + f1);

    TestC2.tval += n;

    //    TestC2.ppp();

    f1 = TestC2.tval;
    //    System.out.println(" In test: tval = " + f1);

    return TestC2.tval;
  }

}

class TestC2 {

  static int tval = 1000;

  static void ppp() {
    //    System.out.println(" tval = " + tval);
  }
}
