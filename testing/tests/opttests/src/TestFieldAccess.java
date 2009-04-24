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
class TestFieldAccess {
  static boolean s0 = true;
  static byte    s1 = -1;
  static char    s2 = 0x41;     // 'A'
  static short   s3 = -3;
  static int     s4 = -4;
  static long    s5 = -5;
  static float   s6 = -6;
  static double  s7 = -7;
  static Object  s8 = new TestFieldAccess();

  boolean x0 = true;
  byte    x1 = -1;
  char    x2 = 0x41;     // 'A'
  short   x3 = -3;
  int     x4 = -4;
  long    x5 = -5;
  float   x6 = -6;
  double  x7 = -7;
  Object  x8 = this;

  public String toString() { return "Instance of " + getClass().getName(); }

  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    System.out.print("TestFieldAccess");

    TestFieldAccess b = new TestFieldAccess();

    if (!TestFieldAccess.s0) {
      System.out.print("\nwant: true\n got: ");
      System.out.println(TestFieldAccess.s0);
      testSuccess = false;
    }

    if (TestFieldAccess.s1 != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(TestFieldAccess.s1);
      testSuccess = false;
    }

    if (TestFieldAccess.s2 != 'A') {
      System.out.print("\nwant: A\n got: ");
      System.out.println(TestFieldAccess.s2);
      testSuccess = false;
    }

    if (TestFieldAccess.s3 != -3) {
      System.out.print("\nwant: -3\n got: ");
      System.out.println(TestFieldAccess.s3);
      testSuccess = false;
    }

    if (TestFieldAccess.s4 != -4) {
      System.out.print("\nwant: -4\n got: ");
      System.out.println(TestFieldAccess.s4);
      testSuccess = false;
    }

    if (TestFieldAccess.s5 != -5) {
      System.out.print("\nwant: -5\n got: ");
      System.out.println(TestFieldAccess.s5);
      testSuccess = false;
    }

    if (TestFieldAccess.s6 != -6.0) {
      System.out.print("\nwant: -6.0\n got: ");
      System.out.println(TestFieldAccess.s6);
      testSuccess = false;
    }

    if (TestFieldAccess.s7 != -7.0D) {
      System.out.print("\nwant: -7.0\n got: ");
      System.out.println(TestFieldAccess.s7);
      testSuccess = false;
    }

    String str = TestFieldAccess.s8.toString();
    if (!str.equals("Instance of TestFieldAccess")) {
      System.out.print("\nwant: Instance of TestFieldAccess\n got: ");
      System.out.println(TestFieldAccess.s8);
      testSuccess = false;
    }

    if (!b.x0) {
      System.out.print("\nwant: true\n got: ");
      System.out.println(b.x0);
      testSuccess = false;
    }

    if (b.x1 != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(b.x1);
      testSuccess = false;
    }

    if (b.x2 != 'A') {
      System.out.print("\nwant: A\n got: ");
      System.out.println(b.x2);
      testSuccess = false;
    }

    if (b.x3 != -3) {
      System.out.print("\nwant: -3\n got: ");
      System.out.println(b.x3);
      testSuccess = false;
    }

    if (b.x4 != -4) {
      System.out.print("\nwant: -4\n got: ");
      System.out.println(b.x4);
      testSuccess = false;
    }

    if (b.x5 != -5) {
      System.out.print("\nwant: -5\n got: ");
      System.out.println(b.x5);
      testSuccess = false;
    }

    if (b.x6 != -6.0F) {
      System.out.print("\nwant: -6.0\n got: ");
      System.out.println(b.x6);
      testSuccess = false;
    }

    if (b.x7 != -7.0D) {
      System.out.print("\nwant: -7.0\n got: ");
      System.out.println(b.x7);
      testSuccess = false;
    }

    str = b.x8.toString();
    if (!str.equals("Instance of TestFieldAccess")) {
      System.out.print("\nwant: Instance of TestFieldAccess\n got: ");
      System.out.println(b.x8);
      testSuccess = false;
    }

    if (testSuccess)
      System.out.println(" succeeded.");
    else
      System.out.println(" failed. ***************\n\n");

    return testSuccess;
  }
}
