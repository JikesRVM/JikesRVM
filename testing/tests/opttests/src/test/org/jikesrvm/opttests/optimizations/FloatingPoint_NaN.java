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
package test.org.jikesrvm.opttests.optimizations;

import org.vmmagic.pragma.Inline;

import java.util.Random;

public class FloatingPoint_NaN {

  private final Random random = new Random();
  private boolean success = true;

  public static void main(String[] args) {
    FloatingPoint_NaN notANumberTests = new FloatingPoint_NaN();
    notANumberTests.main();
  }

  private void main() {
    System.out.println("--- Tests for single-precision floating point (i.e. float) ---");
    System.out.println("-- Addition --");
    float randomFloat = getRandomFloat();
    floatAdd(randomFloat, Float.NaN);
    floatAdd(Float.NaN, randomFloat);
    floatAdd(Float.NaN, Float.NaN);
    System.out.println("-- Subtraction --");
    floatSub(randomFloat, Float.NaN);
    floatSub(Float.NaN, randomFloat);
    floatSub(Float.NaN, Float.NaN);
    System.out.println("-- Division --");
    floatDiv(randomFloat, Float.NaN);
    floatDiv(Float.NaN, randomFloat);
    floatDiv(Float.NaN, Float.NaN);
    System.out.println("-- Multiplication --");
    floatMul(randomFloat, Float.NaN);
    floatMul(Float.NaN, randomFloat);
    floatMul(Float.NaN, Float.NaN);
    System.out.println("-- Remainder --");
    floatRem(randomFloat, Float.NaN);
    floatRem(Float.NaN, randomFloat);
    floatRem(Float.NaN, Float.NaN);
    System.out.println("-- greater than --");
    floatGreater(randomFloat, Float.NaN);
    floatGreater(Float.NaN, randomFloat);
    floatGreater(1f, Float.NaN);
    floatGreater(Float.NaN, Float.NaN);
    System.out.println("-- less than --");
    floatLesser(randomFloat, Float.NaN);
    floatLesser(Float.NaN, randomFloat);
    floatLesser(1f, Float.NaN);
    floatLesser(Float.NaN, Float.NaN);
    System.out.println("--- Tests for double-precision floating point (i.e. double) ---");
    System.out.println("-- Addition --");
    double randomDouble = getRandomDouble();
    doubleAdd(randomDouble, Double.NaN);
    doubleAdd(Double.NaN, randomDouble);
    doubleAdd(Double.NaN, Double.NaN);
    System.out.println("-- Subtraction --");
    doubleSub(randomDouble, Double.NaN);
    doubleSub(Double.NaN, randomDouble);
    doubleSub(Double.NaN, Double.NaN);
    System.out.println("-- Division --");
    doubleDiv(randomDouble, Double.NaN);
    doubleDiv(Double.NaN, randomDouble);
    doubleDiv(Double.NaN, Double.NaN);
    System.out.println("-- Multiplication --");
    doubleMul(randomDouble, Double.NaN);
    doubleMul(Double.NaN, randomDouble);
    doubleMul(Double.NaN, Double.NaN);
    System.out.println("-- Remainder --");
    doubleRem(randomDouble, Double.NaN);
    doubleRem(Double.NaN, randomDouble);
    doubleRem(Double.NaN, Double.NaN);
    System.out.println("-- greater than --");
    doubleGreater(randomDouble, Double.NaN);
    doubleGreater(Double.NaN, randomDouble);
    doubleGreater(1d, Double.NaN);
    doubleGreater(Double.NaN, Double.NaN);
    System.out.println("-- less than --");
    doubleLesser(randomDouble, Double.NaN);
    doubleLesser(Double.NaN, randomDouble);
    doubleLesser(1d, Double.NaN);
    doubleLesser(Double.NaN, Double.NaN);

    System.out.println();
    if (success) {
      System.out.println("ALL TESTS PASSED");
    } else {
      System.out.println("SOME TESTS FAILED");
    }
  }

  private void determineTestStatusForFloat(float res) {
    if (Float.isNaN(res)) {
      System.out.print(" [OK]");
    } else {
      success = false;
      System.out.print(" [FAIL]");
    }
    System.out.println();
  }

  private void determineTestStatusForDouble(double res) {
    if (Double.isNaN(res)) {
      System.out.print(" [OK]");
    } else {
      success = false;
      System.out.print(" [FAIL]");
    }
    System.out.println();
  }

  private void determineTestStatusForBoolean(boolean res) {
    if (res == false) {
      System.out.print(" [OK]");
    } else {
      success = false;
      System.out.print(" [FAIL]");
    }
    System.out.println();
  }

  private void printFloatOperation(float op1, float op2, String operand, String result) {
    StringBuilder sb = new StringBuilder();
    sb.append(op1);
    sb.append(' ');
    sb.append(operand);
    sb.append(' ');
    sb.append(op2);
    sb.append(" = ");
    sb.append(result);
    System.out.print(sb.toString());
  }

  private void printDoubleOperation(double op1, double op2, String operand, String result) {
    StringBuilder sb = new StringBuilder();
    sb.append(op1);
    sb.append(' ');
    sb.append(operand);
    sb.append(' ');
    sb.append(op2);
    sb.append(" = ");
    sb.append(result);
    System.out.print(sb.toString());
  }

  // Value is not important, just need to get something that isn't constant
  private float getRandomFloat() {
    return random.nextFloat();
  }

  private double getRandomDouble() {
    return random.nextDouble();
  }

  @Inline // to enable optimizations by Simplifier
  private void floatAdd(float op1, float op2) {
    float result = op1 + op2;
    printFloatOperation(op1, op2, "+", Float.toString(result));
    determineTestStatusForFloat(result);
  }

  @Inline
  private void floatSub(float op1, float op2) {
    float result = op1 - op2;
    printFloatOperation(op1, op2, "-", Float.toString(result));
    determineTestStatusForFloat(result);
  }

  @Inline
  private void floatDiv(float op1, float op2) {
    float result = op1 / op2;
    printFloatOperation(op1, op2, "/", Float.toString(result));
    determineTestStatusForFloat(result);
  }

  @Inline
  private void floatMul(float op1, float op2) {
    float result = op1 * op2;
    printFloatOperation(op1, op2, "*", Float.toString(result));
    determineTestStatusForFloat(result);
  }

  @Inline
  private void floatRem(float op1, float op2) {
    float result = op1 % op2;
    printFloatOperation(op1, op2, "%", Float.toString(result));
    determineTestStatusForFloat(result);
  }

  @Inline
  private void floatGreater(float op1, float op2) {
    boolean result = op1 > op2;
    printFloatOperation(op1, op2, ">", Boolean.toString(result));
    determineTestStatusForBoolean(result);
  }

  @Inline
  private void floatLesser(float op1, float op2) {
    boolean result = op1 < op2;
    printFloatOperation(op1, op2, "<", Boolean.toString(result));
    determineTestStatusForBoolean(result);
  }

  @Inline
  private void doubleAdd(double op1, double op2) {
    double result = op1 + op2;
    printDoubleOperation(op1, op2, "+", Double.toString(result));
    determineTestStatusForDouble(result);
  }

  @Inline
  private void doubleSub(double op1, double op2) {
    double result = op1 - op2;
    printDoubleOperation(op1, op2, "-", Double.toString(result));
    determineTestStatusForDouble(result);
  }

  @Inline
  private void doubleDiv(double op1, double op2) {
    double result = op1 / op2;
    printDoubleOperation(op1, op2, "/", Double.toString(result));
    determineTestStatusForDouble(result);
  }

  @Inline
  private void doubleMul(double op1, double op2) {
    double result = op1 * op2;
    printDoubleOperation(op1, op2, "*", Double.toString(result));
    determineTestStatusForDouble(result);
  }

  @Inline
  private void doubleRem(double op1, double op2) {
    double result = op1 % op2;
    printDoubleOperation(op1, op2, "%", Double.toString(result));
    determineTestStatusForDouble(result);
  }

  @Inline
  private void doubleGreater(double op1, double op2) {
    boolean result = op1 > op2;
    printDoubleOperation(op1, op2, ">", Boolean.toString(result));
    determineTestStatusForBoolean(result);
  }

  @Inline
  private void doubleLesser(double op1, double op2) {
    boolean result = op1 < op2;
    printDoubleOperation(op1, op2, "<", Boolean.toString(result));
    determineTestStatusForBoolean(result);
  }

}
