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

class TestArithmetic {
  private static int int_3 = -3;
  private static int int1 = 1;
  private static int int3 = 3;
  private static int int5 = 5;
  private static int int33 = 33;
  private static int int65 = 65;
  private static long long_3 = -3;
  private static long long_1 = -1;
  private static long long0 = 0;
  private static long long2 = 2;
  private static long long10000000000 = 10000000000L;
  private static long long0x0110000000000011 = 0x0110000000000011L;
  private static long long0x1010000000000101 = 0x1010000000000101L;
  private static long long0xBEEFBEEFCAFEBABE = 0xBEEFBEEFCAFEBABEL;

  public static void main(String[] args) {
    System.out.println();
    System.out.println("-- itest --");
    itest();
    System.out.println();
    System.out.println("-- ltest --");
    ltest();
    System.out.println();
    System.out.println("-- ftest --");
    ftest();
    System.out.println();
    System.out.println("-- dtest --");
    dtest();
    System.out.println();
    System.out.println("-- nanTestFloat --");
    nanTestFloat();
    System.out.println();
    System.out.println("-- nanTestDouble --");
    nanTestDouble();
    System.out.println();
    System.out.println("-- remTest --");
    remTest();
  }

  private static void itest() {
    int a = int3;
    System.out.print("Expected: 4 Actual: ");
    System.out.println(a + 1);  // iadd
    System.out.print("Expected: 2 Actual: ");
    System.out.println(a - 1);  // isub
    System.out.print("Expected: 9 Actual: ");
    System.out.println(a * 3);  // imul
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a / 2);  // idiv
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a % 2);  // irem
    System.out.print("Expected: -3 Actual: ");
    System.out.println(-a);  // ineg
    System.out.print("Expected: 4 Actual: ");
    System.out.println(++a);  // iinc

    a = int_3;
    System.out.print("Expected: -2 Actual: ");
    System.out.println(a + 1);  // iadd
    System.out.print("Expected: -4 Actual: ");
    System.out.println(a - 1);  // isub
    System.out.print("Expected: -9 Actual: ");
    System.out.println(a * 3);  // imul
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a / 2);  // idiv
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a % 2);  // irem
    System.out.print("Expected: 3 Actual: ");
    System.out.println(-a);  // ineg
    System.out.print("Expected: -2 Actual: ");
    System.out.println(++a);  // iinc

    a = int3;     // 0x00000011
    int b = int5; // 0x00000101

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a & b);  // iand
    System.out.print("Expected: 7 Actual: ");
    System.out.println(a | b);  // ior
    System.out.print("Expected: 6 Actual: ");
    System.out.println(a ^ b);  // ixor

    a = int_3; // 0xfffffffd;

    System.out.print("Expected: -6 Actual: ");
    System.out.println(a << 1);  // ishl
    System.out.print("Expected: -2 Actual: ");
    System.out.println(a >> 1);  // ishr
    System.out.print("Expected: 2147483646 Actual: ");
    System.out.println(a >>> 1);  // iushr

    // funny Java shift cases
    a = int1;

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a << 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a << 16) << 16);
    System.out.print("Expected: 2 Actual: ");
    System.out.println(a << 33);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println(a << -1);
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a << -32);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println(a << -33);
    System.out.print("Expected: 2 Actual: ");
    System.out.println(1 << int33);

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >> 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a >> 16) >> 16);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> 33);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> -1);
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >> -32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >> -33);
    System.out.print("Expected: -2 Actual: ");
    System.out.println(-4 >> int33);

    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> 32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((a >>> 16) >>> 16);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> 33);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> -1);
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> -32);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a >>> -33);
    System.out.print("Expected: 2147483646 Actual: ");
    System.out.println(-4 >>> int33);

    // IA32 bit test patterns
    System.out.print("Expected: true Actual: ");
    System.out.println(((1 << int1) & int3) != 0);
    System.out.print("Expected: false Actual: ");
    System.out.println(((1 << int1) & int3) == 0);
    System.out.print("Expected: true Actual: ");
    System.out.println(((1 << int33) & int3) != 0);

    System.out.print("Expected: true Actual: ");
    System.out.println((int3 & (1 << int1)) != 0);
    System.out.print("Expected: false Actual: ");
    System.out.println((int3 & (1 << int1)) == 0);
    System.out.print("Expected: true Actual: ");
    System.out.println((int3 & (1 << int33)) != 0);
    System.out.print("Expected: taken Actual: ");
    if (((1 << int1) & int3) != 0) {
      System.out.println("taken");
    }

    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >> int1) & 1) == 1);
    System.out.print("Expected: false Actual: ");
    System.out.println(((int3 >> int1) & 1) != 1);
    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >> int1) & 1) != 0);
    System.out.print("Expected: false Actual: ");
    System.out.println(((int3 >> int1) & 1) == 0);
    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >> int33) & 1) == 1);
    System.out.print("Expected: taken Actual: ");
    if (((int3 >> int1) & 1) != 0) {
      System.out.println("taken");
    }

    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >>> int1) & 1) == 1);
    System.out.print("Expected: false Actual: ");
    System.out.println(((int3 >>> int1) & 1) != 1);
    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >>> int1) & 1) != 0);
    System.out.print("Expected: false Actual: ");
    System.out.println(((int3 >>> int1) & 1) == 0);
    System.out.print("Expected: true Actual: ");
    System.out.println(((int3 >>> int33) & 1) == 1);
    System.out.print("Expected: taken Actual: ");
    if (((int3 >>> int1) & 1) == 1) {
      System.out.println("taken");
    }

    // Rotate tests
    System.out.print("Expected: 10 Actual: ");
    System.out.println((int5 << 1)|(int5 >>> -1)); // Rotate left by 1
    System.out.print("Expected: 10 Actual: ");
    System.out.println((int5 >>> -1)|(int5 << 1)); // Rotate left by 1
    System.out.print("Expected: -2147483646 Actual: ");
    System.out.println((int5 << -1)|(int5 >>> 1)); // Rotate right by 1
    System.out.print("Expected: -2147483646 Actual: ");
    System.out.println((int5 >>> 1)|(int5 << -1)); // Rotate right by 1
    System.out.print("Expected: 10 Actual: ");
    System.out.println((int5 << int1)|(int5 >>> -int1)); // Rotate left by 1
    System.out.print("Expected: 10 Actual: ");
    System.out.println((int5 >>> -int1)|(int5 << int1)); // Rotate left by 1
    System.out.print("Expected: -2147483646 Actual: ");
    System.out.println((int5 << -int1)|(int5 >>> int1)); // Rotate right by 1
    System.out.print("Expected: -2147483646 Actual: ");
    System.out.println((int5 >>> int1)|(int5 << -int1)); // Rotate right by 1
  }

  private static void ltest() {
    long a = long10000000000;
    long b = long2;

    System.out.print("Expected: 10000000002 Actual: ");
    System.out.println(a + b);  // ladd
    System.out.print("Expected: 9999999998 Actual: ");
    System.out.println(a - b);  // lsub
    System.out.print("Expected: 20000000000 Actual: ");
    System.out.println(a * b);  // lmul
    System.out.print("Expected: 5000000000 Actual: ");
    System.out.println(a / b);  // ldiv
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a % b);  // lrem
    System.out.print("Expected: -2 Actual: ");
    System.out.println(-b);  // lneg
    System.out.print("Expected: -10000000000 Actual: ");
    System.out.println(-a);  // lneg

    a = long_3;

    System.out.print("Expected: -1 Actual: ");
    System.out.println(a + 2);  // ladd
    System.out.print("Expected: -5 Actual: ");
    System.out.println(a - 2);  // lsub
    System.out.print("Expected: -9 Actual: ");
    System.out.println(a * 3);  // lmul
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a / 2);  // ldiv
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a % 2);  // lrem
    System.out.print("Expected: 3 Actual: ");
    System.out.println(-a);  // lneg

    a = long0x0110000000000011;
    b = long0x1010000000000101;

    System.out.print("Expected: 4503599627370497 Actual: ");
    System.out.println(a & b);  // land
    System.out.print("Expected: 1229482698272145681 Actual: ");
    System.out.println(a | b);  // lor
    System.out.print("Expected: 1224979098644775184 Actual: ");
    System.out.println(a ^ b);  // lxor

    // bit patterns that can be optimized for certain operators if converting
    // long operators into int operators
    long long0x00000000FFFFFFFF = 0x00000000FFFFFFFFL;
    long long0xFFFFFFFF00000000 = 0xFFFFFFFF00000000L;
    long long0x00000001FFFFFFFF = 0x00000001FFFFFFFFL;
    long long0xFFFFFFFF00000001 = 0xFFFFFFFF00000001L;
    long long0x0000000100000000 = 0x0000000100000000L;
    long long0x0000000100000001 = 0x0000000100000001L;

    a = long_1;

    System.out.print("Expected: -4294967295 Actual: ");
    System.out.println(a * long0x00000000FFFFFFFF);
    System.out.print("Expected: 4294967296 Actual: ");
    System.out.println(a * long0xFFFFFFFF00000000);
    System.out.print("Expected: -8589934591 Actual: ");
    System.out.println(a * long0x00000001FFFFFFFF);
    System.out.print("Expected: 4294967295 Actual: ");
    System.out.println(a * long0xFFFFFFFF00000001);
    System.out.print("Expected: -4294967296 Actual: ");
    System.out.println(a * long0x0000000100000000);
    System.out.print("Expected: -4294967297 Actual: ");
    System.out.println(a * long0x0000000100000001);

    System.out.print("Expected: 4294967295 Actual: ");
    System.out.println(a & long0x00000000FFFFFFFF);
    System.out.print("Expected: -4294967296 Actual: ");
    System.out.println(a & long0xFFFFFFFF00000000);
    System.out.print("Expected: 8589934591 Actual: ");
    System.out.println(a & long0x00000001FFFFFFFF);
    System.out.print("Expected: -4294967295 Actual: ");
    System.out.println(a & long0xFFFFFFFF00000001);
    System.out.print("Expected: 4294967296 Actual: ");
    System.out.println(a & long0x0000000100000000);
    System.out.print("Expected: 4294967297 Actual: ");
    System.out.println(a & long0x0000000100000001);

    a = long0;

    System.out.print("Expected: 4294967295 Actual: ");
    System.out.println(a | long0x00000000FFFFFFFF);
    System.out.print("Expected: -4294967296 Actual: ");
    System.out.println(a | long0xFFFFFFFF00000000);
    System.out.print("Expected: 8589934591 Actual: ");
    System.out.println(a | long0x00000001FFFFFFFF);
    System.out.print("Expected: -4294967295 Actual: ");
    System.out.println(a | long0xFFFFFFFF00000001);
    System.out.print("Expected: 4294967297 Actual: ");
    System.out.println(a | long0x0000000100000001);

    System.out.print("Expected: 4294967295 Actual: ");
    System.out.println(a ^ long0x00000000FFFFFFFF);
    System.out.print("Expected: -4294967296 Actual: ");
    System.out.println(a ^ long0xFFFFFFFF00000000);
    System.out.print("Expected: 8589934591 Actual: ");
    System.out.println(a ^ long0x00000001FFFFFFFF);
    System.out.print("Expected: -4294967295 Actual: ");
    System.out.println(a ^ long0xFFFFFFFF00000001);
    System.out.print("Expected: 4294967297 Actual: ");
    System.out.println(a ^ long0x0000000100000001);

    a = long0xBEEFBEEFCAFEBABE;

    System.out.print("Expected: 9070106573795063164 Actual: ");
    System.out.println(a << 1);  // lshl
    System.out.print("Expected: -306530926119425288 Actual: ");
    System.out.println(a << 2);
    System.out.print("Expected: -613061852238850576 Actual: ");
    System.out.println(a << 3);
    System.out.print("Expected: -1226123704477701152 Actual: ");
    System.out.println(a << 4);
    System.out.print("Expected: -1171235197933666816 Actual: ");
    System.out.println(a << 8);
    System.out.print("Expected: -4688305491665879040 Actual: ");
    System.out.println(a << 16);
    System.out.print("Expected: -3819410108757049344 Actual: ");
    System.out.println(a << 32);
    System.out.print("Expected: -7638820217514098688 Actual: ");
    System.out.println(a << 33);
    System.out.print("Expected: 3169103638681354240 Actual: ");
    System.out.println(a << 34);
    System.out.print("Expected: 6338207277362708480 Actual: ");
    System.out.println(a << 35);
    System.out.print("Expected: -5770329518984134656 Actual: ");
    System.out.println(a << 36);
    System.out.print("Expected: -91551935198396416 Actual: ");
    System.out.println(a << 40);
    System.out.print("Expected: -4990551337079930880 Actual: ");
    System.out.println(a << 48);
    System.out.print("Expected: -4755801206503243776 Actual: ");
    System.out.println(a << 56);
    System.out.print("Expected: 0 Actual: ");
    System.out.println(a << 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a << 64);
    System.out.print("Expected: 9070106573795063164 Actual: ");
    System.out.println(a << 65);
    System.out.print("Expected: 9070106573795063164 Actual: ");
    System.out.println(a << int65);

    System.out.print("Expected: -2344159374978622113 Actual: ");
    System.out.println(a >> 1);  // lshr
    System.out.print("Expected: -1172079687489311057 Actual: ");
    System.out.println(a >> 2);
    System.out.print("Expected: -586039843744655529 Actual: ");
    System.out.println(a >> 3);
    System.out.print("Expected: -293019921872327765 Actual: ");
    System.out.println(a >> 4);
    System.out.print("Expected: -18313745117020486 Actual: ");
    System.out.println(a >> 8);
    System.out.print("Expected: -71538066863362 Actual: ");
    System.out.println(a >> 16);
    System.out.print("Expected: -1091584273 Actual: ");
    System.out.println(a >> 32);
    System.out.print("Expected: -545792137 Actual: ");
    System.out.println(a >> 33);
    System.out.print("Expected: -272896069 Actual: ");
    System.out.println(a >> 34);
    System.out.print("Expected: -136448035 Actual: ");
    System.out.println(a >> 35);
    System.out.print("Expected: -68224018 Actual: ");
    System.out.println(a >> 36);
    System.out.print("Expected: -4264002 Actual: ");
    System.out.println(a >> 40);
    System.out.print("Expected: -16657 Actual: ");
    System.out.println(a >> 48);
    System.out.print("Expected: -66 Actual: ");
    System.out.println(a >> 56);
    System.out.print("Expected: -1 Actual: ");
    System.out.println(a >> 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a >> 64);
    System.out.print("Expected: -2344159374978622113 Actual: ");
    System.out.println(a >> 65);
    System.out.print("Expected: -2344159374978622113 Actual: ");
    System.out.println(a >> int65);

    System.out.print("Expected: 6879212661876153695 Actual: ");
    System.out.println(a >>> 1);  // lushr
    System.out.print("Expected: 3439606330938076847 Actual: ");
    System.out.println(a >>> 2);
    System.out.print("Expected: 1719803165469038423 Actual: ");
    System.out.println(a >>> 3);
    System.out.print("Expected: 859901582734519211 Actual: ");
    System.out.println(a >>> 4);
    System.out.print("Expected: 53743848920907450 Actual: ");
    System.out.println(a >>> 8);
    System.out.print("Expected: 209936909847294 Actual: ");
    System.out.println(a >>> 16);
    System.out.print("Expected: 3203383023 Actual: ");
    System.out.println(a >>> 32);
    System.out.print("Expected: 1601691511 Actual: ");
    System.out.println(a >>> 33);
    System.out.print("Expected: 800845755 Actual: ");
    System.out.println(a >>> 34);
    System.out.print("Expected: 400422877 Actual: ");
    System.out.println(a >>> 35);
    System.out.print("Expected: 200211438 Actual: ");
    System.out.println(a >>> 36);
    System.out.print("Expected: 12513214 Actual: ");
    System.out.println(a >>> 40);
    System.out.print("Expected: 48879 Actual: ");
    System.out.println(a >>> 48);
    System.out.print("Expected: 190 Actual: ");
    System.out.println(a >>> 56);
    System.out.print("Expected: 1 Actual: ");
    System.out.println(a >>> 63);
    System.out.print("Expected: -4688318749957244226 Actual: ");
    System.out.println(a >>> 64);
    System.out.print("Expected: 6879212661876153695 Actual: ");
    System.out.println(a >>> 65);
    System.out.print("Expected: 6879212661876153695 Actual: ");
    System.out.println(a >>> int65);
  }

  private static float float0 = 0.0f;
  private static float float0_9 = 0.9f;
  private static float float1 = 1.0f;
  private static float float1_5 = 1.5f;
  private static float float2 = 2.0f;
  private static float float_maxint = (float)Integer.MAX_VALUE;
  private static float float_minint = (float)Integer.MIN_VALUE;
  private static double double0 = 0.0f;
  private static double double1 = 1.0f;
  private static double double2 = 2.0f;
  private static double double_maxint = (double)Integer.MAX_VALUE;
  private static double double_minint = (double)Integer.MIN_VALUE;
  private static float float_maxlong = (float)Long.MAX_VALUE;
  private static float float_minlong = (float)Long.MIN_VALUE;
  private static double double_maxlong = (double)Long.MAX_VALUE;
  private static double double_minlong = (double)Long.MIN_VALUE;

  private static void ftest() {
    // f2i, d2i, f2l and d2l tests
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)float0);
    System.out.print("Expected: 1 Actual: ");
    System.out.println((int)float1);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)double0);
    System.out.print("Expected: 1 Actual: ");
    System.out.println((int)double1);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)Float.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)-Float.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)Double.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)-Double.NaN);
    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println((int)Float.POSITIVE_INFINITY);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println((int)Float.NEGATIVE_INFINITY);
    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println((int)float_maxint);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println((int)float_minint);
    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println((int)double_maxint);
    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println((int)double_minint);

    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)float0);
    System.out.print("Expected: 1 Actual: ");
    System.out.println((long)float1);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)double0);
    System.out.print("Expected: 1 Actual: ");
    System.out.println((long)double1);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)Float.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)-Float.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)Double.NaN);
    System.out.print("Expected: 0 Actual: ");
    System.out.println((long)-Double.NaN);
    System.out.print("Expected: 9223372036854775807 Actual: ");
    System.out.println((long)Float.POSITIVE_INFINITY);
    System.out.print("Expected: -9223372036854775808 Actual: ");
    System.out.println((long)Float.NEGATIVE_INFINITY);
    System.out.print("Expected: 9223372036854775807 Actual: ");
    System.out.println((long)float_maxlong);
    System.out.print("Expected: -9223372036854775808 Actual: ");
    System.out.println((long)float_minlong);
    System.out.print("Expected: 9223372036854775807 Actual: ");
    System.out.println((long)double_maxlong);
    System.out.print("Expected: -9223372036854775808 Actual: ");
    System.out.println((long)double_minlong);

    float a = float1;
    float b = float2;

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(3.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a + b)));  // fadd

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(-1.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a - b)));  // fsub

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(2.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a * b)));  // fmul

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(0.5F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a / b)));  // fdiv

    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(-1.0F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(-a)));  // fneg

    a = float1_5;
    b = float0_9;
    System.out.print("Expected: " + Integer.toHexString(Float.floatToIntBits(0.6F)) + " Actual: ");
    System.out.println(Integer.toHexString(Float.floatToIntBits(a % b)));  // frem

    System.out.print("Expected: NaN Actual: ");
    System.out.println(float0/float0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Float.NaN + Float.NaN);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Float.NaN + float2);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Float.NaN * float2);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((float0 / 0.0) * Float.POSITIVE_INFINITY);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((float1 / 0.0) * 0.0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((float1 / 0.0) - (float1 / 0.0));

    System.out.print("Expected: NaN Actual: ");
    System.out.println((float1 / 0.0) / (float1 / 0.0));

    System.out.print("Expected: NaN Actual: ");
    System.out.println((-float1 / 0.0) * 0.0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((-float1 / 0.0) - (-float1 / 0.0));

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN > float1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN < float1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN == float1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN == -float1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN > Float.POSITIVE_INFINITY);

    System.out.print("Expected: false Actual: ");
    System.out.println(Float.NaN < Float.POSITIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(Float.POSITIVE_INFINITY == Float.POSITIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(-Float.POSITIVE_INFINITY == Float.NEGATIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(Float.NEGATIVE_INFINITY < float1);

    System.out.print("Expected: true Actual: ");
    System.out.println((-float1/0.0) == (-float1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println(float1/0.0);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((float1/0.0) + 2.0);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((float1/0.0) * 0.5);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((float1/0.0) + (float1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((float1/0.0) * (float1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println(Math.abs(-float1/0.0));

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println(-float1/0.0);

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println((-float1/0.0) + 2.0);

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println((-float1/0.0) * 0.5);

    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println((int)(float1/0.0));

    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println((int)(-float1/0.0));

    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)Float.NaN);
  }

  private static void dtest() {
    double a = 1;
    double b = 2;

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(3.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a + b)));  // dadd

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(-1.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a - b)));  // dsub

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(2.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a * b)));  // dmul

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(0.5D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a / b)));  // ddiv

    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(-1.0D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(-a)));  // dneg

    a = 1.5;
    b = 0.9;
    System.out.print("Expected: " + Long.toHexString(Double.doubleToLongBits(0.6D)) + " Actual: ");
    System.out.println(Long.toHexString(Double.doubleToLongBits(a % b)));  // drem

    System.out.print("Expected: NaN Actual: ");
    System.out.println(double0/double0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Double.NaN + Double.NaN);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Double.NaN + double2);

    System.out.print("Expected: NaN Actual: ");
    System.out.println(Double.NaN * double2);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((double0 / 0.0) * Double.POSITIVE_INFINITY);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((double1 / 0.0) * 0.0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((double1 / 0.0) - (double1 / 0.0));

    System.out.print("Expected: NaN Actual: ");
    System.out.println((double1 / 0.0) / (double1 / 0.0));

    System.out.print("Expected: NaN Actual: ");
    System.out.println((-double1 / 0.0) * 0.0);

    System.out.print("Expected: NaN Actual: ");
    System.out.println((-double1 / 0.0) - (-double1 / 0.0));

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN > double1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN < double1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN == double1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN == -double1);

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN > Double.POSITIVE_INFINITY);

    System.out.print("Expected: false Actual: ");
    System.out.println(Double.NaN < Double.POSITIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(Double.POSITIVE_INFINITY == Double.POSITIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(-Double.POSITIVE_INFINITY == Double.NEGATIVE_INFINITY);

    System.out.print("Expected: true Actual: ");
    System.out.println(Double.NEGATIVE_INFINITY < double1);

    System.out.print("Expected: true Actual: ");
    System.out.println((-double1/0.0) == (-double1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println(double1/0.0);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((double1/0.0) + 2.0);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((double1/0.0) * 0.5);

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((double1/0.0) + (double1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println((double1/0.0) * (double1/0.0));

    System.out.print("Expected: Infinity Actual: ");
    System.out.println(Math.abs(-double1/0.0));

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println(-double1/0.0);

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println((-double1/0.0) + 2.0);

    System.out.print("Expected: -Infinity Actual: ");
    System.out.println((-double1/0.0) * 0.5);

    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println((int)(double1/0.0));

    System.out.print("Expected: -2147483648 Actual: ");
    System.out.println((int)(-double1/0.0));

    System.out.print("Expected: 0 Actual: ");
    System.out.println((int)Double.NaN);
  }

  private static void nanTestFloat() {
    float zero = float0;
    float NaN = zero / zero;

    System.out.print("  expr     expected    got    \n");
    System.out.print("---------- -------- ----------\n");
    System.out.print("NaN <  NaN  false     " + (NaN < NaN) + "\n");
    System.out.print("NaN <= NaN  false     " + (NaN <= NaN) + "\n");
    System.out.print("NaN == NaN  false     " + (NaN == NaN) + "\n");
    System.out.print("NaN != NaN  true      " + (NaN != NaN) + "\n");
    System.out.print("NaN >= NaN  false     " + (NaN >= NaN) + "\n");
    System.out.print("NaN >  NaN  false     " + (NaN > NaN) + "\n");
  }

  private static void nanTestDouble() {
    double zero = 0;
    double NaN = zero / zero;

    System.out.println("  expr     expected    got    ");
    System.out.println("---------- -------- ----------");
    System.out.println("NaN <  NaN  false     " + (NaN < NaN));
    System.out.println("NaN <= NaN  false     " + (NaN <= NaN));
    System.out.println("NaN == NaN  false     " + (NaN == NaN));
    System.out.println("NaN != NaN  true      " + (NaN != NaN));
    System.out.println("NaN >= NaN  false     " + (NaN >= NaN));
    System.out.println("NaN >  NaN  false     " + (NaN > NaN));
  }

  private static void remTest() {
    rem(+2, +3);
    rem(+2, -3);
    rem(-2, +3);
    rem(-2, -3);
  }

  private static void rem(final double a, final double b) {
    System.out.println(a + "  /  " + b + "=" + Long.toHexString(Double.doubleToLongBits(a / b)));
    System.out.println(a + "  %  " + b + "=" + Long.toHexString(Double.doubleToLongBits(a % b)));
    System.out.println(a + " rem " + b + "=" + Long.toHexString(Double.doubleToLongBits(Math.IEEEremainder(a, b))));
    System.out.println();
  }
}
