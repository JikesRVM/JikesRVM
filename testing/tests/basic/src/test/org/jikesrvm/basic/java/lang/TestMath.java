/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.java.lang;

/*
 */
class TestMath {
  public static void main(String[] args) {
    runFloorTest(1.6, 1.0);
    runFloorTest(1.5, 1.0);
    runFloorTest(1.4, 1.0);
    runFloorTest(1.0, 1.0);

    runFloorTest(-2.0, -2.0);
    runFloorTest(-1.6, -2.0);
    runFloorTest(-1.5, -2.0);
    runFloorTest(-1.4, -2.0);

    runFloorTest(Double.NaN, Double.NaN);
    runFloorTest(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    runFloorTest(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    runFloorTest(-0, -0);
    runFloorTest(0, 0);

    runCeilTest(1.6, 2.0);
    runCeilTest(1.5, 2.0);
    runCeilTest(1.4, 2.0);
    runCeilTest(1.0, 0.0);

    runCeilTest(-2.0, -2.0);
    runCeilTest(-1.6, -1.0);
    runCeilTest(-1.5, -1.0);
    runCeilTest(-1.4, -1.0);

    runCeilTest(Double.NaN, Double.NaN);
    runCeilTest(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
    runCeilTest(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    runCeilTest(-0, -0);
    runCeilTest(0, 0);
  }

  private static void runCeilTest(final double value, final double expected) {
    System.out.println("Math.ceil(" + value + ") Expected: " + expected + " Actual: " + Math.ceil(value));
  }

  private static void runFloorTest(final double value, final double expected) {
    System.out.println("Math.floor(" + value + ") Expected: " + expected + " Actual: " + Math.floor(value));
  }
}
