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
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;

public class TestExtent {

  private static boolean success = true;

  private static final long TWO_TO_THE_POWER_OF_32_MINUS_ONE = 4294967295L;
  private static final long INTEGER_MAX_AS_LONG = Integer.MAX_VALUE;

  /** Note: can't use SUCCESS because that's for overall success */
  private static final String TEST_SUCCESSFUL = " (OK)";

  public static void main(String[] args) {
    testExtentZero();
    testExtentOne();
    testExtentMax();
    testExtentFromInt();
    testExtentToWord();
    testPlusAndMinus();
    testSomeSimpleCasesForComparisons();
    if (success) {
      System.out.println("ALL TESTS PASSED");
    } else {
      System.out.println("FAILURE");
    }
  }

  private static void booleanTest(String msg, boolean value, boolean expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void intTest(String msg, int value, int expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void longTest(String msg, long value, long expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void testExtentZero() {
    System.out.println("--- Tests for zero() ---");
    intTest("Extent.zero().toInt()", Extent.zero().toInt(), 0);
    longTest("Extent.zero().toLong()", Extent.zero().toLong(), 0L);
  }

  private static void testExtentOne() {
    System.out.println("--- Tests for one() ---");
    intTest("Extent.zero().toInt()", Extent.one().toInt(), 1);
    longTest("Extent.zero().toLong()", Extent.one().toLong(), 1L);
  }

  private static void testExtentMax() {
    System.out.println("--- Tests for max() ---");
    intTest("Extent.max().toInt() == -1", Extent.max().toInt(), -1);
    System.out.println("--- Tests for max() that depend on word size ---");
    if (VM.BuildFor32Addr) {
      longTest("Extent.max().toLong() == 4294967295 ((2^32)-1)", Extent.max().toLong(), 4294967295L);
    } else {
      longTest("Extent.max().toLong() == -1", Extent.max().toLong(), -1);
    }
  }

  private static void testExtentFromInt() {
    System.out.println("--- Tests for fromIntSignExtend(int) and fromIntZeroExtend(int) ---");
    final long integerMaxPlusOne = INTEGER_MAX_AS_LONG + 1;

    intTest("Extent.fromIntZeroExtend(0).toInt()", Extent.fromIntZeroExtend(0).toInt(), 0);
    intTest("Extent.fromIntSignExtend(0).toInt()", Extent.fromIntSignExtend(0).toInt(), 0);
    longTest("Extent.fromIntZeroExtend(0).toLong()", Extent.fromIntZeroExtend(0).toLong(), 0);
    longTest("Extent.fromIntSignExtend(0).toLong()", Extent.fromIntSignExtend(0).toLong(), 0L);

    intTest("Extent.fromIntSignExtend(Integer.MAX_VALUE).toInt()", Extent.fromIntSignExtend(Integer.MAX_VALUE).toInt(), Integer.MAX_VALUE);
    intTest("Extent.fromIntSignExtend(Integer.MIN_VALUE).toInt()", Extent.fromIntSignExtend(Integer.MIN_VALUE).toInt(), Integer.MIN_VALUE);
    intTest("Extent.fromIntZeroExtend(Integer.MAX_VALUE).toInt()", Extent.fromIntZeroExtend(Integer.MAX_VALUE).toInt(), Integer.MAX_VALUE);
    intTest("Extent.fromIntZeroExtend(Integer.MIN_VALUE).toInt()", Extent.fromIntZeroExtend(Integer.MIN_VALUE).toInt(), Integer.MIN_VALUE);
    intTest("Extent.fromIntSignExtend(-1).toInt()", Extent.fromIntSignExtend(-1).toInt(), -1);
    intTest("Extent.fromIntZeroExtend(-1).toInt()", Extent.fromIntZeroExtend(-1).toInt(), -1);

    longTest("Extent.fromIntSignExtend(Integer.MAX_VALUE).toLong()", Extent.fromIntSignExtend(Integer.MAX_VALUE).toLong(), INTEGER_MAX_AS_LONG);
    if (VM.BuildFor32Addr) {
      longTest("Extent.fromIntSignExtend(Integer.MIN_VALUE).toLong()", Extent.fromIntSignExtend(Integer.MIN_VALUE).toLong(), integerMaxPlusOne);
    } else {
      long minusIntegerMaxPlusOne = -1 * integerMaxPlusOne;
      longTest("Extent.fromIntSignExtend(Integer.MIN_VALUE).toLong()", Extent.fromIntSignExtend(Integer.MIN_VALUE).toLong(), minusIntegerMaxPlusOne);
    }
    longTest("Extent.fromIntZeroExtend(Integer.MAX_VALUE).toLong()", Extent.fromIntZeroExtend(Integer.MAX_VALUE).toLong(), INTEGER_MAX_AS_LONG);
    longTest("Extent.fromIntZeroExtend(Integer.MIN_VALUE).toLong()", Extent.fromIntZeroExtend(Integer.MIN_VALUE).toLong(), integerMaxPlusOne);
    if (VM.BuildFor32Addr) {
      longTest("Extent.fromIntSignExtend(-1).toLong()", Extent.fromIntSignExtend(-1).toLong(), TWO_TO_THE_POWER_OF_32_MINUS_ONE);
    } else {
      longTest("Extent.fromIntSignExtend(-1).toLong()", Extent.fromIntSignExtend(-1).toLong(), -1L);
    }
    longTest("Extent.fromIntZeroExtend(-1).toLong()", Extent.fromIntZeroExtend(-1).toLong(), TWO_TO_THE_POWER_OF_32_MINUS_ONE);
  }

  private static void testExtentToWord() {
    System.out.println("--- Tests for toWord() ---");
    booleanTest("Extent.zero().toWord().EQ(Word.zero())", Extent.zero().toWord().EQ(Word.zero()), true);
    booleanTest("Extent.one().toWord().EQ(Word.one())", Extent.one().toWord().EQ(Word.one()), true);
    booleanTest("Extent.max().toWord().EQ(Word.max())", Extent.max().toWord().EQ(Word.max()), true);

  }

  private static void testPlusAndMinus() {
    System.out.println("--- Tests for plus(int) ---");
    intTest("Extent.zero().plus(0).toInt()", Extent.zero().plus(0).toInt(), 0);
    intTest("Extent.zero().plus(1).toInt()", Extent.zero().plus(1).toInt(), 1);
    intTest("Extent.zero().plus(-1).toInt()", Extent.zero().plus(-1).toInt(), -1);
    intTest("Extent.zero().plus(1024).toInt()", Extent.zero().plus(1024).toInt(), 1024);
    intTest("Extent.max().plus(1).toInt()", Extent.max().plus(1).toInt(), 0);

    System.out.println("--- Tests for minus(int) ---");
    intTest("Extent.zero().minus(0).toInt()", Extent.zero().minus(0).toInt(), 0);
    intTest("Extent.zero().minus(1).toInt()", Extent.zero().minus(1).toInt(), -1);
    intTest("Extent.zero().minus(-1).toInt()", Extent.zero().minus(-1).toInt(), 1);
    intTest("Extent.zero().minus(1024).toInt()", Extent.zero().minus(1024).toInt(), -1024);

    System.out.println("--- Tests for minus(int) and plus(int) combined ---");
    intTest("Extent.zero().minus(0).plus(0).toInt()", Extent.zero().minus(0).plus(0).toInt(), 0);
    intTest("Extent.zero().plus(0).minus(0).toInt()", Extent.zero().plus(0).minus(0).toInt(), 0);
    intTest("Extent.zero().plus(1).minus(1).toInt()", Extent.zero().plus(1).minus(1).toInt(), 0);
    intTest("Extent.zero().minus(1).plus(1).toInt()", Extent.zero().minus(1).plus(1).toInt(), 0);

    System.out.println("--- Tests for minus(Extent) ---");
    intTest("Extent.max().minus(Extent.max()).toInt()", Extent.max().minus(Extent.max()).toInt(), 0);
    intTest("Extent.one().minus(Extent.one()).toInt()", Extent.one().minus(Extent.one()).toInt(), 0);

    System.out.println("--- Tests for plus(Extent) ---");
    intTest("Extent.max().plus(Extent.one()).toInt()", Extent.max().plus(Extent.one()).toInt(), 0);

    System.out.println("--- Tests for minus(Extent) and plus(Extent) combined ---");
    intTest("Extent.zero().plus(Extent.max()).minus(Extent.max()).toInt()", Extent.zero().plus(Extent.max()).minus(Extent.max()).toInt(), 0);
    intTest("Extent.zero().minus(Extent.max()).plus(Extent.max()).toInt()", Extent.zero().minus(Extent.max()).plus(Extent.max()).toInt(), 0);
    intTest("Extent.zero().plus(Extent.one()).minus(Extent.one()).toInt()", Extent.zero().plus(Extent.one()).minus(Extent.one()).toInt(), 0);
    intTest("Extent.zero().minus(Extent.one()).plus(Extent.one()).toInt(), 0", Extent.zero().minus(Extent.one()).plus(Extent.one()).toInt(), 0);
  }

  private static void testSomeSimpleCasesForComparisons() {
    booleanTest("Extent.zero().EQ(Extent.zero())", Extent.zero().EQ(Extent.zero()), true);
    booleanTest("Extent.zero().NE(Extent.zero())", Extent.zero().NE(Extent.zero()), false);
    booleanTest("Extent.zero().LE(Extent.zero())", Extent.zero().LE(Extent.zero()), true);
    booleanTest("Extent.zero().GE(Extent.zero())", Extent.zero().GE(Extent.zero()), true);
    booleanTest("Extent.zero().LT(Extent.zero())", Extent.zero().LT(Extent.zero()), false);
    booleanTest("Extent.zero().GT(Extent.zero())", Extent.zero().GT(Extent.zero()), false);

    booleanTest("Extent.zero().EQ(Extent.one())", Extent.zero().EQ(Extent.one()), false);
    booleanTest("Extent.zero().NE(Extent.one())", Extent.zero().NE(Extent.one()), true);
    booleanTest("Extent.zero().LE(Extent.one())", Extent.zero().LE(Extent.one()), true);
    booleanTest("Extent.zero().LT(Extent.one())", Extent.zero().LT(Extent.one()), true);
    booleanTest("Extent.zero().GE(Extent.one())", Extent.zero().GE(Extent.one()), false);
    booleanTest("Extent.zero().GT(Extent.one())", Extent.zero().GT(Extent.one()), false);

    booleanTest("Extent.zero().EQ(Extent.max())", Extent.zero().EQ(Extent.max()), false);
    booleanTest("Extent.zero().NE(Extent.max())", Extent.zero().NE(Extent.max()), true);
    booleanTest("Extent.zero().LE(Extent.max())", Extent.zero().LE(Extent.max()), true);
    booleanTest("Extent.zero().LT(Extent.max())", Extent.zero().LT(Extent.max()), true);
    booleanTest("Extent.zero().GE(Extent.max())", Extent.zero().GE(Extent.max()), false);
    booleanTest("Extent.zero().GT(Extent.max())", Extent.zero().GT(Extent.max()), false);
  }

}
