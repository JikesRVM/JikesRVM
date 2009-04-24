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
