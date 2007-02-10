/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Peter Donald. 2007
 */
package test.org.jikesrvm.basic.bugs;

/**
 * Test code demonstrating bug [ 1644460 ] DaCapo lusearch fails when opt compiled.
 * <p/>
 * Should be tested with -X:aos:initial_compiler=opt in at least one test-configuration.
 *
 * @author Peter Donald
 * @author Robin Garner
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
    System.out.println(((float) (a * b)));
  }

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




