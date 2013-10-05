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

    runSQRTTest(4d, 2d);

    runSQRTTest(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    runSQRTTest(Double.NaN, Double.NaN);
    runSQRTTest(Double.NEGATIVE_INFINITY, Double.NaN);
    runSQRTTest(-1d, Double.NaN);
    runSQRTTest(-0.0d, -0.0d);
    runSQRTTest(0.0d, 0.0d);
  }

  private static void runCeilTest(final double value, final double expected) {
    System.out.println("Math.ceil(" + value + ") Expected: " + expected + " Actual: " + Math.ceil(value));
  }

  private static void runFloorTest(final double value, final double expected) {
    System.out.println("Math.floor(" + value + ") Expected: " + expected + " Actual: " + Math.floor(value));
  }

  private static void runSQRTTest(final double value, final double expected) {
    System.out.println("Math.sqrt(" + value + ") Expected: " + expected + " Actual: " + Math.sqrt(value));
  }
}
