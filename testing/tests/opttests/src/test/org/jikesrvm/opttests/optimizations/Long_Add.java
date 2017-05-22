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

import org.vmmagic.pragma.NoInline;

public class Long_Add {

  private static final long TwoToThePowerOf32 = 1L << 32;

  public static void main(String[] args) {
    Long_Add long_and = new Long_Add();
    long_and.main();
  }

  private void main() {
    System.out.println("--- Tests for long addition ---");
    System.out.println("--- Add zero ---");
    addZero(0L);
    addZero(-1L);
    addZero(1L);
    addZero(Long.MAX_VALUE);
    addZero(Long.MIN_VALUE);
    addZero(Integer.MAX_VALUE);
    addZero(Integer.MIN_VALUE);
    System.out.println("--- And own value  ---");
    addOwnValue(0L);
    addOwnValue(-1L);
    addOwnValue(1L);
    addOwnValue(Long.MAX_VALUE);
    addOwnValue(Long.MIN_VALUE);
    addOwnValue(Integer.MAX_VALUE);
    addOwnValue(Integer.MIN_VALUE);
    System.out.println("--- And negative value ---");
    addNegativeValue(0L);
    addNegativeValue(-1L);
    addNegativeValue(1L);
    addNegativeValue(Long.MAX_VALUE);
    addNegativeValue(Long.MIN_VALUE);
    addNegativeValue(Integer.MAX_VALUE);
    addNegativeValue(Integer.MIN_VALUE);
    System.out.println("--- Add one ---");
    addOne(0L);
    addOne(-1L);
    addOne(1L);
    addOne(Long.MAX_VALUE);
    addOne(Long.MIN_VALUE);
    addOne(Integer.MAX_VALUE);
    addOne(Integer.MIN_VALUE);
    addOne(2L);
    System.out.println("--- Add minus one ---");
    addMinusOne(0L);
    addMinusOne(-1L);
    addMinusOne(1L);
    addMinusOne(Long.MAX_VALUE);
    addMinusOne(Long.MIN_VALUE);
    addMinusOne(Integer.MAX_VALUE);
    addMinusOne(Integer.MIN_VALUE);
    addMinusOne(TwoToThePowerOf32);
    System.out.println("--- Add Long.MAX_VALUE ---");
    addLongMaxValue(0L);
    addLongMaxValue(-1L);
    addLongMaxValue(1L);
    addLongMaxValue(Long.MAX_VALUE);
    addLongMaxValue(Long.MIN_VALUE);
    addLongMaxValue(Integer.MAX_VALUE);
    addLongMaxValue(Integer.MIN_VALUE);
    addLongMaxValue(TwoToThePowerOf32);
    System.out.println("--- Add Long.MIN_VALUE ---");
    addLongMinValue(0L);
    addLongMinValue(-1L);
    addLongMinValue(1L);
    addLongMinValue(Long.MAX_VALUE);
    addLongMinValue(Long.MIN_VALUE);
    addLongMinValue(Integer.MAX_VALUE);
    addLongMinValue(Integer.MIN_VALUE);
    addLongMinValue(TwoToThePowerOf32);
  }

  @NoInline
  private static void addZero(long val) {
    long res = val + 0L;
    System.out.println(Long.toString(val) + " + 0L: " + Long.toString(res));
  }

  @NoInline
  private static void addOwnValue(long val) {
    long res = val + val;
    System.out.println(Long.toString(val) + " + " + Long.toString(val) + "L: " + Long.toString(res));
  }

  @NoInline
  private static void addNegativeValue(long val) {
    long res = val + -val;
    System.out.println(Long.toString(val) + " + -" + Long.toString(val) + "L: " + Long.toString(res));
  }

  @NoInline
  private static void addOne(long val) {
    long res = val + 1L;
    System.out.println(Long.toString(val) + " + 1L: " + Long.toString(res));
  }

  @NoInline
  private static void addMinusOne(long val) {
    long res = val + -1;
    System.out.println(Long.toString(val) + " + -1L: " + Long.toString(res));
  }

  @NoInline
  private static void addLongMaxValue(long val) {
    long res = val + Long.MAX_VALUE;
    System.out.println(Long.toString(val) + " + " + Long.MAX_VALUE + "L: " + Long.toString(res));
  }

  @NoInline
  private static void addLongMinValue(long val) {
    long res = val + Long.MIN_VALUE;
    System.out.println(Long.toString(val) + " + " + Long.MAX_VALUE + "L: " + Long.toString(res));

  }

}
