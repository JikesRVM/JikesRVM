/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * unresolved putstatic/getstatic test
 *
 * @author unascribed
 */
public class stPutGet {
  static boolean run() {
    int i = test(6000);
    System.out.println("stPutGet returned: " + i);
    return true;
  }

  public static int test(int n) {
    TestC2.tval += n;
    return TestC2.tval;
  }
}

class TestC2 {
  static int tval = 1000;
}
