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
package test.org.jikesrvm.basic.core.intrinsics;

import org.jikesrvm.VM;
import org.vmmagic.unboxed.Offset;

public class TestOffset {

  private static boolean success = true;

  public static void main(String[] args) {
    testIsZero();
    testOffsetZero();
    testOffsetMax();
    testOffsetFromInt();
    testPlusAndMinus();
    testSimplestCasesForComparisons();
    if (success) {
      System.out.println("SUCCESS");
    } else {
      System.out.println("FAILURE");
    }
  }

  private static void booleanTest(String msg, boolean value, boolean expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.println(value);
    if (value != expected) {
      success = false;
    }
  }

  private static void intTest(String msg, int value, int expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.println(value);
    if (value != expected) {
      success = false;
    }
  }

  private static void longTest(String msg, long value, long expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.println(value);
    if (value != expected) {
      success = false;
    }
  }

  private static void testIsZero() {
    System.out.println("--- Tests for isZero() ---");
    booleanTest("Offset.zero().isZero()", Offset.zero().isZero(), true);
    booleanTest("Offset.max().isZero()", Offset.max().isZero(), false);
    booleanTest("Offset.fromIntSignExtend(0).isZero()", Offset.fromIntSignExtend(0).isZero(), true);
    booleanTest("Offset.fromIntSignExtend(-0).isZero()", Offset.fromIntSignExtend(-0).isZero(), true);
    booleanTest("Offset.fromIntZeroExtend(0).isZero()", Offset.fromIntZeroExtend(0).isZero(), true);
    booleanTest("Offset.fromIntZeroExtend(-0).isZero()", Offset.fromIntZeroExtend(-0).isZero(), true);
  }

  private static void testOffsetZero() {
    System.out.println("--- Tests for zero() ---");
    booleanTest("Offset.zero().isZero()", Offset.zero().isZero(), true);
    booleanTest("Offset.zero().isMax()", Offset.zero().isMax(), false);
    intTest("Offset.zero().toInt()", Offset.zero().toInt(), 0);
    longTest("Offset.zero().toLong()", Offset.zero().toLong(), 0L);
  }

  private static void testOffsetMax() {
    System.out.println("--- Tests for max() ---");
    booleanTest("Offset.max().isZero()", Offset.max().isZero(), false);
    booleanTest("Offset.max().isMax()", Offset.max().isMax(), true);
    intTest("Offset.max().toInt() == -1", Offset.max().toInt(), -1);
    System.out.println("--- Tests for max() that depend on word size ---");
    if (VM.BuildFor32Addr) {
      longTest("Offset.max().toLong() == 4294967295 ((2^32)-1)", Offset.max().toLong(), 4294967295L);
    } else {
      longTest("Offset.max().toLong() == -1", Offset.max().toLong(), -1);
    }
  }

  private static void testOffsetFromInt() {
    System.out.println("--- Tests for fromIntSignExtend(int) and fromIntZeroExtend(int) ---");
    long integerMax = Integer.MAX_VALUE;
    long integerMaxPlusOne = integerMax + 1;
    long twoToThePowerOf32MinusOne = 4294967295L;

    intTest("Offset.fromIntSignExtend(Integer.MAX_VALUE).toInt()", Offset.fromIntSignExtend(Integer.MAX_VALUE).toInt(), Integer.MAX_VALUE);
    intTest("Offset.fromIntSignExtend(Integer.MIN_VALUE).toInt()", Offset.fromIntSignExtend(Integer.MIN_VALUE).toInt(), Integer.MIN_VALUE);
    intTest("Offset.fromIntZeroExtend(Integer.MAX_VALUE).toInt()", Offset.fromIntZeroExtend(Integer.MAX_VALUE).toInt(), Integer.MAX_VALUE);
    intTest("Offset.fromIntZeroExtend(Integer.MIN_VALUE).toInt()", Offset.fromIntZeroExtend(Integer.MIN_VALUE).toInt(), Integer.MIN_VALUE);
    intTest("Offset.fromIntSignExtend(-1).toInt()", Offset.fromIntSignExtend(-1).toInt(), -1);
    intTest("Offset.fromIntZeroExtend(-1).toInt()", Offset.fromIntZeroExtend(-1).toInt(), -1);

    longTest("Offset.fromIntSignExtend(Integer.MAX_VALUE).toLong()", Offset.fromIntSignExtend(Integer.MAX_VALUE).toLong(), integerMax);
    if (VM.BuildFor32Addr) {
      longTest("Offset.fromIntSignExtend(Integer.MIN_VALUE).toLong()", Offset.fromIntSignExtend(Integer.MIN_VALUE).toLong(), integerMaxPlusOne);
    } else {
      long minusIntegerMaxPlusOne = -1 * integerMaxPlusOne;
      longTest("Offset.fromIntSignExtend(Integer.MIN_VALUE).toLong()", Offset.fromIntSignExtend(Integer.MIN_VALUE).toLong(), minusIntegerMaxPlusOne);
    }
    longTest("Offset.fromIntZeroExtend(Integer.MAX_VALUE).toLong()", Offset.fromIntZeroExtend(Integer.MAX_VALUE).toLong(), integerMax);
    longTest("Offset.fromIntZeroExtend(Integer.MIN_VALUE).toLong()", Offset.fromIntZeroExtend(Integer.MIN_VALUE).toLong(), integerMaxPlusOne);
    if (VM.BuildFor32Addr) {
      longTest("Offset.fromIntSignExtend(-1).toLong()", Offset.fromIntSignExtend(-1).toLong(), twoToThePowerOf32MinusOne);
    } else {
      long minusIntegerMaxPlusOne = -1 * integerMaxPlusOne;
      longTest("Offset.fromIntSignExtend(-1).toLong()", Offset.fromIntSignExtend(-1).toLong(), -1L);
    }
    longTest("Offset.fromIntZeroExtend(-1).toLong()", Offset.fromIntZeroExtend(-1).toLong(), twoToThePowerOf32MinusOne);
  }

  private static void testPlusAndMinus() {
    System.out.println("--- Tests for plus(int) ---");
    booleanTest("Offset.zero().plus(0).isZero()", Offset.zero().plus(0).isZero(), true);
    intTest("Offset.zero().plus(1).toInt()", Offset.zero().plus(1).toInt(), 1);
    intTest("Offset.zero().plus(-1).toInt()", Offset.zero().plus(-1).toInt(), -1);
    intTest("Offset.zero().plus(1024).toInt()", Offset.zero().plus(1024).toInt(), 1024);
    booleanTest("Offset.max().plus(1).isZero()", Offset.max().plus(1).isZero(), true);

    System.out.println("--- Tests for minus(int) ---");
    booleanTest("Offset.zero().minus(0).isZero()", Offset.zero().minus(0).isZero(), true);
    intTest("Offset.zero().minus(1).toInt()", Offset.zero().minus(1).toInt(), -1);
    intTest("Offset.zero().minus(-1).toInt()", Offset.zero().minus(-1).toInt(), 1);
    intTest("Offset.zero().minus(1024).toInt()", Offset.zero().minus(1024).toInt(), -1024);

    System.out.println("--- Tests for minus(int) and plus(int) combined ---");
    booleanTest("Offset.zero().minus(0).plus(0).isZero()", Offset.zero().minus(0).plus(0).isZero(), true);
    booleanTest("Offset.zero().plus(0).minus(0).isZero()", Offset.zero().plus(0).minus(0).isZero(), true);
    booleanTest("Offset.zero().plus(1).minus(1).isZero()", Offset.zero().plus(1).minus(1).isZero(), true);
    booleanTest("Offset.zero().minus(1).plus(1).isZero()", Offset.zero().minus(1).plus(1).isZero(), true);

    System.out.println("--- Tests for minus(Offset) ---");
    booleanTest("Offset.max().minus(Offset.max()).isZero()", Offset.max().minus(Offset.max()).isZero(), true);
    booleanTest("Offset.max().minus(Offset.zero()).isMax()", Offset.max().minus(Offset.zero()).isMax(), true);

    System.out.println("--- Tests for plus(Offset) ---");
    booleanTest("Offset.zero().plus(Offset.max()).isMax()", Offset.zero().plus(Offset.max()).isMax(), true);
    booleanTest("Offset.max().plus(Offset.zero()).isMax()", Offset.max().plus(Offset.zero()).isMax(), true);
    booleanTest("Offset.max().plus(Offset.fromIntZeroExtend(1)).isZero()", Offset.max().plus(Offset.fromIntZeroExtend(1)).isZero(), true);

    System.out.println("--- Tests for minus(Offset) and plus(Offset) combined ---");
    booleanTest("Offset.zero().plus(Offset.max()).minus(Offset.max()).isZero()", Offset.zero().plus(Offset.max()).minus(Offset.max()).isZero(), true);
    booleanTest("Offset.zero().minus(Offset.max()).plus(Offset.max()).isZero()", Offset.zero().minus(Offset.max()).plus(Offset.max()).isZero(), true);
  }

  private static void testSimplestCasesForComparisons() {
    booleanTest("Offset.zero().EQ(Offset.zero())", Offset.zero().EQ(Offset.zero()), true);
    booleanTest("Offset.zero().NE(Offset.zero())", Offset.zero().NE(Offset.zero()), false);
    booleanTest("Offset.zero().sLE(Offset.zero())", Offset.zero().sLE(Offset.zero()), true);
    booleanTest("Offset.zero().sGE(Offset.zero())", Offset.zero().sGE(Offset.zero()), true);
    booleanTest("Offset.zero().sLT(Offset.zero())", Offset.zero().sLT(Offset.zero()), false);
    booleanTest("Offset.zero().sGT(Offset.zero())", Offset.zero().sGT(Offset.zero()), false);
  }

}
