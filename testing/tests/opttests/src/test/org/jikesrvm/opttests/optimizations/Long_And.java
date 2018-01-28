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

public class Long_And {

  private static final long TwoToThePowerOf32 = 1L << 32;

  public static void main(String[] args) {
    Long_And long_and = new Long_And();
    long_and.main();
  }

  private void main() {
    System.out.println("--- Tests for long and ---");
    System.out.println("--- Clear everything ---");
    clearEverything(0L);
    clearEverything(-1L);
    clearEverything(1L);
    clearEverything(Long.MAX_VALUE);
    clearEverything(Long.MIN_VALUE);
    clearEverything(Integer.MAX_VALUE);
    clearEverything(Integer.MIN_VALUE);
    System.out.println("--- Don't clear anything ---");
    dontClearAnything(0L);
    dontClearAnything(-1L);
    dontClearAnything(1L);
    dontClearAnything(Long.MAX_VALUE);
    dontClearAnything(Long.MIN_VALUE);
    dontClearAnything(Integer.MAX_VALUE);
    dontClearAnything(Integer.MIN_VALUE);
    System.out.println("--- And with self ---");
    andWithSelf(0L);
    andWithSelf(-1L);
    andWithSelf(1L);
    andWithSelf(Long.MAX_VALUE);
    andWithSelf(Long.MIN_VALUE);
    andWithSelf(Integer.MAX_VALUE);
    andWithSelf(Integer.MIN_VALUE);
    System.out.println("--- Reduce to 1 bit ---");
    reduceTo1Bit(0L);
    reduceTo1Bit(-1L);
    reduceTo1Bit(1L);
    reduceTo1Bit(Long.MAX_VALUE);
    reduceTo1Bit(Long.MIN_VALUE);
    reduceTo1Bit(Integer.MAX_VALUE);
    reduceTo1Bit(Integer.MIN_VALUE);
    reduceTo1Bit(2L);
    System.out.println("--- Clear upper 32 ---");
    clearUpper32(0L);
    clearUpper32(-1L);
    clearUpper32(1L);
    clearUpper32(Long.MAX_VALUE);
    clearUpper32(Long.MIN_VALUE);
    clearUpper32(Integer.MAX_VALUE);
    clearUpper32(Integer.MIN_VALUE);
    clearUpper32(TwoToThePowerOf32);
    System.out.println("--- Clear lower 32 ---");
    clearLower32(0L);
    clearLower32(-1L);
    clearLower32(1L);
    clearLower32(Long.MAX_VALUE);
    clearLower32(Long.MIN_VALUE);
    clearLower32(Integer.MAX_VALUE);
    clearLower32(Integer.MIN_VALUE);
    clearLower32(TwoToThePowerOf32);
    System.out.println("--- And with 2^32 ---");
    andWith2ToThePowerOf32(0L);
    andWith2ToThePowerOf32(-1L);
    andWith2ToThePowerOf32(1L);
    andWith2ToThePowerOf32(Long.MAX_VALUE);
    andWith2ToThePowerOf32(Long.MIN_VALUE);
    andWith2ToThePowerOf32(Integer.MAX_VALUE);
    andWith2ToThePowerOf32(Integer.MIN_VALUE);
    andWith2ToThePowerOf32(TwoToThePowerOf32);
  }

  @NoInline
  private static void clearEverything(long val) {
    long res = val & 0L;
    System.out.println(Long.toString(val) + " & 0L: " + Long.toString(res));
  }

  @NoInline
  private static void dontClearAnything(long val) {
    long res = val & -1L;
    System.out.println(Long.toString(val) + " & -1L: " + Long.toString(res));
  }

  @NoInline
  private static void andWithSelf(long val) {
    long res = val & val;
    System.out.println(Long.toString(val) + " & " + Long.toString(val) + "L: " + Long.toString(res));
  }

  @NoInline
  private static void reduceTo1Bit(long val) {
    long res = val & 1L;
    System.out.println(Long.toString(val) + " & 1L: " + Long.toString(res));
  }

  @NoInline
  private static void clearUpper32(long val) {
    long res = val & 0x00000000FFFFFFFFL;
    System.out.println(Long.toString(val) + " & 0x00000000FFFFFFFFL: " + Long.toString(res));
  }

  @NoInline
  private static void clearLower32(long val) {
    long res = val & 0xFFFFFFFF00000000L;
    System.out.println(Long.toString(val) + " & 0xFFFFFFFF00000000L: " + Long.toString(res));
  }

  @NoInline
  private static void andWith2ToThePowerOf32(long val) {
    long res = val & 0x00000001000000000L;
    System.out.println(Long.toString(val) + " & 0x00000001000000000L: " + Long.toString(res));
  }

}
