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
package test.org.jikesrvm.basic.bugs;

/**
 * Test code demonstrating bug [ 1644460 ] DaCapo lusearch fails when opt compiled.
 * <p/>
 * Should be tested with -X:aos:initial_compiler=opt in at least one test-configuration.
 */
public class R1644460_B {
  public static float b = 0.009765625f;
  public static float a = 10.57379f;

  public static float aa = 8.485281f;
  public static float ab = 1.2461331f;

  public static void main(String[] args) {
    float c = a * b;
    check(c);
    float d = aa * ab;
    d *= b;
    check(d);
    System.out.println(c);
    System.out.println(a * b);
  }

  @SuppressWarnings({"DoubleNegation"})
  private static void check(float c) {
    _assert((c == 0.10325967f));
    _assert(!(c != 0.10325967f));
    _assert((c <= 0.10325967f));
    _assert((c >= 0.10325967f));
    _assert(!(c < 0.10325967f));
    _assert(!(c > 0.10325967f));
  }

  private static void _assert(final boolean test) {
    if (!test) throw new AssertionError();
  }
}




