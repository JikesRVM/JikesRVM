/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.core.bytecode;

class TestFieldAccess {
  static boolean s0 = true;
  static byte s1 = -1;
  static char s2 = 'A';
  static short s3 = -3;
  static int s4 = -4;
  static long s5 = -5;
  static float s6 = -6;
  static double s7 = -7;
  static Object s8 = new TestFieldAccess();

  boolean x0 = true;
  byte x1 = -1;
  char x2 = 'A';
  short x3 = -3;
  int x4 = -4;
  long x5 = -5;
  float x6 = -6;
  double x7 = -7;
  Object x8 = this;

  public String toString() { return "Instance of " + getClass().getName(); }

  public static void main(String[] args) {
    System.out.println(TestFieldAccess.s0);
    System.out.println(TestFieldAccess.s1);
    System.out.println(TestFieldAccess.s2);
    System.out.println(TestFieldAccess.s3);
    System.out.println(TestFieldAccess.s4);
    System.out.println(TestFieldAccess.s5);
    System.out.println(TestFieldAccess.s6);
    System.out.println(TestFieldAccess.s7);
    System.out.println(TestFieldAccess.s8);

    TestFieldAccess b = new TestFieldAccess();
    System.out.println(b.x0);
    System.out.println(b.x1);
    System.out.println(b.x2);
    System.out.println(b.x3);
    System.out.println(b.x4);
    System.out.println(b.x5);
    System.out.println(b.x6);
    System.out.println(b.x7);
    System.out.println(b.x8);
  }
}
