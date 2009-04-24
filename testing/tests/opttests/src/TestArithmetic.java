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
class TestArithmetic {
  public static void main(String[] args){
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    boolean retSuccess = true;
    System.out.print("TestArithmetic");


    itest();
    if (!testSuccess) {
      System.out.print("\n-- itest --");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;


    ltest();
    if (!testSuccess) {
      System.out.print("\n-- ltest --");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;


    ftest();
    if (!testSuccess) {
      System.out.print("\n-- ftest --");
      System.out.println(" failed. ***************\n\n");
    }

    retSuccess &= testSuccess;
    testSuccess = true;

    dtest();
    if (!testSuccess) {
      System.out.print("\n-- dtest --");
      System.out.println(" failed. ***************\n\n");
    }

    if (retSuccess)
      System.out.println(" succeeded.");

    return retSuccess;
  }

  static void itest() {
    itest(3,2);
  }

  static void itest(int a,int b) {

    if ((a+1) != 4) {
      System.out.print("\n ****** want: 4\n got: ");
      System.out.println(a  +   1);  // iadd
      testSuccess = false;
    }

    if ((a-1) != 2) {
      System.out.print("\n ****** want: 2\n got: ");
      System.out.println(a  -   1);  // isub
      testSuccess = false;
    }

    if ((a*3) != 9) {
      System.out.print("\n ****** want: 9\n got: ");
      System.out.println(a  *   3);  // imul
      testSuccess = false;
    }

    if ((a/b) != 1) {
      System.out.print("\n ****** want: 1\n got: ");
      System.out.println(a  /   b);  // idiv
      testSuccess = false;
    }

    if ((a%b) != 1) {
      System.out.print("\n ****** want: 1\n got: ");
      System.out.println(a  %   b);  // irem
      testSuccess = false;
    }

    if ((-a) != -3) {
      System.out.print("\n ****** want: -3\n got: ");
      System.out.println(-a);  // ineg
      testSuccess = false;
    }

    int j = ++a;
    if (j != 4) {
      System.out.print("\n ****** want: 4\n got: ");
      System.out.println(j);  // iinc
      testSuccess = false;
    }

    itest1(0x00000011, 0x00000101);
  }
  static void itest1(int a, int b) {

    if ((a & b) != 1) {
      System.out.print("\n ****** want: 1\n got: ");
      System.out.println(a  &   b);  // iand
      testSuccess = false;
    }

    if ((a | b) != 273) {
      System.out.print("\n ****** want: 273\n got: ");
      System.out.println(a  |   b);  // ior
      testSuccess = false;
    }

    if ((a^b) != 272) {
      System.out.print("\n ****** want: 272\n got: ");
      System.out.println(a  ^   b);  // ixor
      testSuccess = false;
    }

    itest2(0xfffffffd); // -3
  }

  static void itest2(int a) {
    if ((a <<1) != -6) {
      System.out.print("\n ****** want: -6\n got: ");
      System.out.println(a  <<  1);  // ishl
      testSuccess = false;
    }

    if ((a >> 1) != -2) {
      System.out.print("\n ****** want: -2\n got: ");
      System.out.println(a  >>  1);  // ishr
      testSuccess = false;
    }

    if ((a >>> 1) != 2147483646) {
      System.out.print("\n ****** want: 2147483646\n got: ");
      System.out.println(a >>>  1);  // iushr
      testSuccess = false;
    }
  }

  static void ltest() {
    ltest(10000000000L, 2L);
    System.out.println(ldiv(10000000000L,2L));
    System.out.println(ldiv(-4L,2L));
    System.out.println(ldiv(4L,-2L));
    System.out.println(ldiv(4L,2L));
    System.out.println(ldiv(-40000000000L,2L));
    System.out.println(ldiv(40000000000L,-2L));
    System.out.println(ldiv(40000000000L,2L));
    System.out.println(ldiv(-40000000000L, 20000000000L));
    System.out.println(ldiv(40000000000L,-20000000000L));
    System.out.println(ldiv(40000000000L, 20000000000L));
  }

  static void ltest(long a, long b) {
    if ((a+b) != 10000000002L) {
      System.out.print("\n ****** want: 10000000002\n got: ");
      System.out.println(a +  b);  // ladd
      testSuccess = false;
    }

    if ((a-b) != 9999999998L) {
      System.out.print("\n ****** want: 9999999998\n got: ");
      System.out.println(a -  b);  // lsub
      testSuccess = false;
    }

    if ((a*b) != 20000000000L) {
      System.out.print("\n ****** want: 20000000000\n got: ");
      System.out.println(a *  b);  // lmul
      testSuccess = false;
    }

    if ((a/b) != 5000000000L) {
      System.out.print("\n ****** want: 5000000000\n got: ");
      System.out.println(a /  b);  // ldiv
      testSuccess = false;
    }

    if ((a%b) != 0) {
      System.out.print("\n ****** want: 0\n got: ");
      System.out.println(a %  b);  // lrem
      testSuccess = false;
    }

    if ((-b) != -2) {
      System.out.print("\n ****** want: -2\n got: ");
      System.out.println(-b);  // lneg
      testSuccess = false;
    }

    if ((-a) !=  -10000000000L) {
      System.out.print("\n ****** want: -10000000000\n got: ");
      System.out.println(-a);  // lneg
      testSuccess = false;
    }

    a = 0x0110000000000011L;
    b = 0x1010000000000101L;
    ltest1(a,b);
  }

  static long ldiv(long a, long b) {
     return a / b;
  }

  static long lrem(long a, long b) {
     return a % b;
  }

  static void ltest1(long a, long b) {

    if ((a & b) !=  4503599627370497L) {
      System.out.print("\n ****** want: 4503599627370497\n got: ");
      System.out.println(a &   b);  // land
      testSuccess = false;
    }

    if ((a | b) !=  1229482698272145681L) {
      System.out.print("\n ****** want: 1229482698272145681\n got: ");
      System.out.println(a |   b);  // lor
      testSuccess = false;
    }

    if ((a^b) != 1224979098644775184L) {
      System.out.print("\n ****** want: 1224979098644775184\n got: ");
      System.out.println(a ^   b);  // lxor
      testSuccess = false;
    }

    a = 0xfffffffffffffffdL; // -3
    ltest2(a);
  }

  static void ltest2(long a) {
    if ((a << 1) != -6) {
      System.out.print("\n ****** want: -6\n got: ");
      System.out.println(a <<  1);  // lshl
      testSuccess = false;
    }

    if ((a >> 1) != -2) {
      System.out.print("\n ****** want: -2\n got: ");
      System.out.println(a >>  1);  // lshr
      testSuccess = false;
    }

    if ((a >> 33) != -1) {
      System.out.print("\n ****** want: -1\n got: ");
      System.out.println(a >> 33);  // lshr, count > 32
      testSuccess = false;
    }

    if ((a>>>1) !=  9223372036854775806L) {
      System.out.print("\n ****** want: 9223372036854775806\n got: ");
      System.out.println(a >>> 1);  // lushr
      testSuccess = false;
    }
  }

  static void ftest() {
    float a = 1;
    float b = 2;
    ftest(a,b);
  }

  static void ftest(float a, float b) {
    if ((a + b) != 3.0) {
      System.out.print("\n ****** want: 3.0\n got: ");
      System.out.println(a + b);  // fadd
      testSuccess = false;
    }

    if ((a - b) != -1.0) {
      System.out.print("\n ****** want: -1.0\n got: ");
      System.out.println(a - b);  // fsub
      testSuccess = false;
    }

    if ((a*b) != 2.0) {
      System.out.print("\n ****** want: 2.0\n got: ");
      System.out.println(a * b);  // fmul
      testSuccess = false;
    }

    if ((a/b) != 0.5) {
      System.out.print("\n ****** want: 0.5\n got: ");
      System.out.println(a / b);  // fdiv
      testSuccess = false;
    }

    if ((-a) != -1.0) {
      System.out.print("\n ****** want: -1.0\n got: ");
      System.out.println(-a);  // fneg
      testSuccess = false;
    }

    a = 1.5F;
    b = 0.9F;
    ftest1(a,b);
  }
  static void ftest1(float a, float b) {
    float c = a%b;
    if (c != 0.6F) {
      System.out.print("\n ****** want: 0.6\n got: ");
      System.out.println(c);  // frem
      testSuccess = false;
    }
  }

  static void dtest() {
    double a = 1;
    double b = 2;
    dtest(a,b);
  }

  static void dtest(double a, double b) {
    if ((a+b) != 3.0) {
      System.out.print("\n ****** want: 3.0\n got: ");
      System.out.println(a + b);  // dadd
      testSuccess = false;
    }

    if ((a-b) != -1.0) {
      System.out.print("\n ****** want: -1.0\n got: ");
      System.out.println(a - b);  // dsub
      testSuccess = false;
    }

    if ((a*b) != 2.0) {
      System.out.print("\n ****** want: 2.0\n got: ");
      System.out.println(a * b);  // dmul
      testSuccess = false;
    }

    if ((a/b) != 0.5) {
      System.out.print("\n ****** want: 0.5\n got: ");
      System.out.println(a / b);  // ddiv
      testSuccess = false;
    }

    if ((-a) != -1.0) {
      System.out.print("\n ****** want: -1.0\n got: ");
      System.out.println(-a);  // dneg
      testSuccess = false;
    }

    a = 1.5;
    b = 0.9;
    dtest1(a,b);
  }

  static void dtest1(double a, double b) {
    if ((a%b) != 0.6) {
      System.out.print("\n ****** want: 0.6\n got: ");
      System.out.println(a % b);  // drem
      testSuccess = false;
    }
  }
}

