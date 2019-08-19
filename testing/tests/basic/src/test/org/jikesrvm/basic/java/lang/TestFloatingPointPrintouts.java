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
 *
 *  Alternatively, this file is licensed to You under the MIT License:
 *      http://opensource.org/licenses/MIT .
 */
package test.org.jikesrvm.basic.java.lang;

public class TestFloatingPointPrintouts {

  private static final float FIRST_FLOAT = 1.23456789E10f;
  private static final float SECOND_FLOAT = 0.23456789E10f;
  private static final float THIRD_FLOAT = 8.0005E10f;

  private static final double FIRST_DOUBLE = 1.23456789E100d;
  private static final double SECOND_DOUBLE = 0.23456789E100d;
  private static final double THIRD_DOUBLE = 7.0078E100d;

  public static void main(String[] args) {
    printOutFloats();
    printOutDoubles();
  }

  private static void printOutFloats() {
    printFloat(FIRST_FLOAT);
    printFloat(SECOND_FLOAT);
    printFloat(THIRD_FLOAT);
  }

  private static void printOutDoubles() {
    printDouble(FIRST_DOUBLE);
    printDouble(SECOND_DOUBLE);
    printDouble(THIRD_DOUBLE);
  }

  private static void printFloat(float floatVal) {
    System.out.println("Printout for float is " + Float.toString(floatVal) + " , raw bits: " + Float.floatToIntBits(floatVal));
  }

  private static void printDouble(double doubleVal) {
    System.out.println("Printout for double is " + Double.toString(doubleVal) + " , raw bits: " + Double.doubleToLongBits(doubleVal));
  }

}
