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
class TestConversions {
  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    boolean retSuccess = true;

    System.out.print("TestConversions");

    i2b();

    if (!testSuccess) {
      System.out.print("\n-- i2b --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    i2c();

    if (!testSuccess) {
      System.out.print("\n-- i2c --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    i2s();

    if (!testSuccess) {
      System.out.print("\n-- i2s --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    i2l();

    if (!testSuccess) {
      System.out.print("\n-- i2l --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    l2i();

    if (!testSuccess) {
      System.out.print("\n-- l2i --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    i2f();

    if (!testSuccess) {
      System.out.print("\n-- i2f --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    i2d();

    if (!testSuccess) {
      System.out.print("\n-- i2d --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    l2f();

    if (!testSuccess) {
      System.out.print("\n-- l2f --");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    l2d();

    if (!testSuccess) {
      System.out.print("\n-- l2d --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    f2d();

    if (!testSuccess) {
      System.out.print("\n-- f2d --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    f2l();

    if (!testSuccess) {
      System.out.print("\n-- f2l --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    f2i();

    if (!testSuccess) {
      System.out.print("\n-- f2i --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    d2i();

    if (!testSuccess) {
      System.out.print("\n-- d2i --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    d2l();

    if (!testSuccess) {
      System.out.print("\n-- d2l --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;

    d2f();

    if (!testSuccess) {
      System.out.print("\n-- d2f --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;


    testFloatLimits(Float.NEGATIVE_INFINITY,   Float.POSITIVE_INFINITY);
    //    testFloatLimits( Float.MIN_VALUE,           Float.MAX_VALUE);
    //    testFloatLimits(-Float.MAX_VALUE,           Float.MAX_VALUE);
    //    testFloatLimits(-99999F,                    99999F);
    if (!testSuccess) {
      System.out.print("\n-- float_limits --");
      System.out.println(" failed. ***************\n\n");
    }
    retSuccess &= testSuccess;
    testSuccess = true;


    testDoubleLimits(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    //    testDoubleLimits( Double.MIN_VALUE,         Double.MAX_VALUE);
    //    testDoubleLimits(-Double.MAX_VALUE,         Double.MAX_VALUE);
    //    testDoubleLimits(-99999D,                   99999D);
    if (!testSuccess) {
      System.out.print("\n-- double_limits --");
      System.out.println(" failed. ***************\n\n");
    }

    if (retSuccess)
      System.out.println(" succeeded.");

    return retSuccess;
  }

  static void i2b() {
    byte x;
    x = i2b(0x0000007f);
    if (x != 127) {
      System.out.print("\nwant: 127\n got: ");
      System.out.println(i2b(0x0000007f));
      testSuccess = false;
    }

    x = i2b(0x000000ff);
    if (x != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(i2b(0x000000ff));
      testSuccess = false;
    }

    x = i2b(0xffffffff);
    if (x != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(i2b(0xffffffff));
      testSuccess = false;
    }
  }

  static byte i2b(int i) {
    return (byte)i;
  }

  static void i2c() {
    int c = (int)i2c(0x0000007f);
    if (c != 127) {
      System.out.print("\nwant: 127\n got: ");
      System.out.println((int)i2c(0x0000007f));
      testSuccess = false;
    }

    c = (int)i2c(0x000000ff);
    if (c != 255) {
      System.out.print("\nwant: 255\n got: ");
      System.out.println((int)i2c(0x000000ff));
      testSuccess = false;
    }

    c = (int)i2c(0xffffffff);
    if (c != 65535) {
      System.out.print("\nwant: 65535\n got: ");
      System.out.println((int)i2c(0xffffffff));
      testSuccess = false;
    }
  }

  static char i2c(int i) {
    return (char)i;
  }

  static void i2s() {
    short x;

    x = i2s(0x00007fff);
    if (x != 32767) {
      System.out.print("\nwant: 32767\n got: ");
      System.out.println(i2s(0x00007fff));
      testSuccess = false;
    }

    x = i2s(0x0000ffff);
    if (x != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(i2s(0x0000ffff));
      testSuccess = false;
    }
  }

  static short i2s(int i) {
    return (short)i;
  }

  static void i2l() {
    long l = i2l(0x7fffffff);
    if (l != 2147483647L) {
      System.out.print("\nwant: 2147483647\n got: ");
      System.out.println(i2l(0x7fffffff));
      testSuccess = false;
    }

    l = i2l(0xffffffff);
    if (l != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(i2l(0xffffffff));
      testSuccess = false;
    }
  }

  static long i2l(int i) {
    return (long)i;
  }

  static void l2i() {
    int i = l2i(0x000000007fffffffL);
    if (i != 2147483647) {
      System.out.print("\nwant: 2147483647\n got: ");
      System.out.println(l2i(0x000000007fffffffL));
      testSuccess = false;
    }

    i = l2i(0x00000000ffffffffL);
    if (i != -1) {
      System.out.print("\nwant: -1\n got: ");
      System.out.println(l2i(0x00000000ffffffffL));
      testSuccess = false;
    }
  }

  static int l2i(long i) {
    return (int)i;
  }

  static void i2f() {
    if (i2f(-2) != -2.0) {
      System.out.print("\nwant: -2.0\n got: ");
      System.out.println(i2f(-2));
      testSuccess = false;
    }
  }

  static float i2f(int i) {
    return (float)i;
  }

  static void l2f() {
    if (l2f(-2L) != -2.0) {
      System.out.print("\nwant: -2.0\n got: ");
      System.out.println(l2f(-2L));
      testSuccess = false;
    }
  }

  static float l2f(long i) {
    return (float)i;
  }

  static void l2d() {
    if (l2d(-2L) != -2.0) {
      System.out.print("\nwant: -2.0\n got: ");
      System.out.println(l2d(-2L));
      testSuccess = false;
    }
  }

  static double l2d(long i) {
    return (double)i;
  }

  static void i2d() {
    if (i2d(-2) != -2.0) {
      System.out.print("\nwant: -2.0\n got: ");
      System.out.println(i2d(-2));
      testSuccess = false;
    }
  }


  static double i2d(int i) {
    return (double)i;
  }

  static void f2d() {
    if (f2d(-2.6F) != -2.5999999046325684D) {
      System.out.print("\nwant: -2.5999999046325684\n got: ");
      System.out.println(f2d(-2.6F));
      testSuccess = false;
    }
  }

  static double f2d(float f) {
    return (double)f;
  }

  static void f2l() {
    if (f2l(-2.6F) != -2) {
      System.out.print("\nwant: -2\n got: ");
      System.out.println(f2l(-2.6F));
      testSuccess = false;
    }
  }

  static long f2l(float f) {
    return (long)f;
  }

  static void f2i() {
    if (f2i(-2.6F) != -2) {
      System.out.print("\nwant: -2\n got: ");
      System.out.println(f2i(-2.6F));
      testSuccess = false;
    }
  }

  static int f2i(float f) {
    return (int)f;
  }

  static void d2i() {
    if (d2i(-2.6) != -2) {
      System.out.print("\nwant: -2\n got: ");
      System.out.println(d2i(-2.6));
      testSuccess = false;
    }
  }

  static int d2i(double d) {
    return (int)d;
  }

  static void d2l() {
    if (d2l(-2.6) != -2) {
      System.out.print("\nwant: -2\n got: ");
      System.out.println(d2l(-2.6));
      testSuccess = false;
    }
  }

  static long d2l(double d) {
    return (long)d;
  }

  static void d2f() {
    if (d2f(-2.6) != -2.6F) {
      System.out.print("\nwant: -2.6\n got: ");
      System.out.println(d2f(-2.6));
      testSuccess = false;
    }
  }

  static float d2f(double d) {
    return (float)d;
  }

  static void testFloatLimits(float lo, float hi) {
    //    System.out.println();
    if ((!Float.toString(lo).equals("-Infinity")) ||
        (!Float.toString(hi).equals("Infinity"))) {
      System.out.println("\nfloat:  " +       lo + " .. " +       hi);
      testSuccess = false;
    }

    if ((!Long.toString((long)lo).equals("-9223372036854775808")) ||
        (!Long.toString((long)hi).equals("9223372036854775807"))) {
      System.out.println("\nlong:   " + (long)lo + " .. " + (long)hi);
      testSuccess = false;
    }

    if ((!Integer.toString((int)lo).equals("-2147483648")) ||
        (!Integer.toString((int)hi).equals("2147483647"))) {
      System.out.println("\nint:    " + (int)lo + " .. " + (int)hi);
      testSuccess = false;
    }
  }

  static void testDoubleLimits(double lo, double hi) {
    //    System.out.println();

    Double dbl1 = new Double(lo), dbl2 = new Double(hi);
    if ((!dbl1.toString().equals("-Infinity")) ||
        (!dbl2.toString().equals("Infinity"))) {
      System.out.println("\ndouble: " +       lo + " .. " +       hi);
      testSuccess = false;
    }

    if ((!Long.toString((long)lo).equals("-9223372036854775808")) ||
        (!Long.toString((long)hi).equals("9223372036854775807"))) {
      System.out.println("\nlong:   " + (long)lo + " .. " + (long)hi);
      testSuccess = false;
    }

    if ((!Integer.toString((int)lo).equals("-2147483648")) ||
        (!Integer.toString((int)hi).equals("2147483647"))) {
      System.out.println("\nint:    " + (int)lo + " .. " + (int)hi);
      testSuccess = false;
    }
  }
}

