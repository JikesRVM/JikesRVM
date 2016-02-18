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
package test.org.jikesrvm.opttests.burs;

import org.vmmagic.pragma.NoInline;

/**
 * Tests to cover some BURS patterns.
 * <p>
 * The current tests revealed bugs in the 32-bit version of the IA32 LONG_MUL
 * BURS rules after the 32-bit / 64-bit split for the IA32 rules.
 * <p>
 * If you want to delete (tests from) this class, please consider if
 * the tests can be turned into a generally useful optimizing compiler
 * test.
 */
public class BURSPatterns {

  public static void main(String[] args) {
    System.out.println("signed_32bit_32bit_to_64bit_integer_multiplication");
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(0, 0));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(0, 1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(1, 1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(1, -1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(-1, 1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(-1, Integer.MIN_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(-1, Integer.MAX_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(Integer.MAX_VALUE, Integer.MAX_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(3, Integer.MAX_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication(3, Integer.MIN_VALUE));

    System.out.println("signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant");
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(0));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(-1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(-2));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(2));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(Integer.MAX_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(Integer.MIN_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(-5));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(7));

    System.out.println("signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant");
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(0));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(-1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(1));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(-2));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(2));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(Integer.MAX_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(Integer.MIN_VALUE));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(-5));
    System.out.println(signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(7));
  }

  @NoInline
  private static long signed_32bit_32bit_to_64bit_integer_multiplication(int a, int b) {
    return a * b;
  }

  @NoInline
  private static long signed_32bit_32bit_to_64bit_integer_multiplication_pos_long_constant(int a) {
    return a * 0x00000000FFFFFFFFL;
  }

  @NoInline
  private static long signed_32bit_32bit_to_64bit_integer_multiplication_neg_long_constant(int a) {
    return a * 0xFFFFFFFF0000FDFDL;
  }

}
