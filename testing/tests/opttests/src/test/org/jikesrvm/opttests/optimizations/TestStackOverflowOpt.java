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
package test.org.jikesrvm.opttests.optimizations;

import java.util.Random;

import org.vmmagic.pragma.NoTailCallElimination;

/**
 * The IA32 opt compiler treats stack overflow checks differently for small and
 * big frames. Small frames are already tested in the basic test so this test
 * case only handles the case for big frames.
 */
class TestStackOverflowOpt {

  private static Random r = new Random(123456L);

  public static void main(String[] args) {
    try {
      methodWithBigFrame(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
          0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    } catch (StackOverflowError soe) {
      System.out.println("Caught stack overflow error for method with big frame");
    }
  }

  @NoTailCallElimination
  public static void methodWithBigFrame(long l0, long l1, long l2, long l3,
      long l4, long l5, long l6, long l7, long l8, long l9, long l10,
      long l11, long l12, long l13, long l14, long l15, long l16, long l17,
      long l18, long l19, long l20, long l21) {
    long nl0 = r.nextLong();
    long nl1 = r.nextLong();
    long nl2 = r.nextLong();
    long nl3 = r.nextLong();
    long nl4 = r.nextLong();
    long nl5 = r.nextLong();
    long nl6 = r.nextLong();
    long nl7 = r.nextLong();
    long nl8 = r.nextLong();
    long nl9 = r.nextLong();
    long nl10 = r.nextLong();
    long nl11 = r.nextLong();
    long nl12 = r.nextLong();
    long nl13 = r.nextLong();
    long nl14 = r.nextLong();
    long nl15 = r.nextLong();
    long nl16 = r.nextLong();
    long nl17 = r.nextLong();
    long nl18 = r.nextLong();
    long nl19 = r.nextLong();
    long nl20 = r.nextLong();
    long nl21 = r.nextLong();
    methodWithBigFrame(l0 + nl0, l1 + nl1, l2 + nl2, l3 + nl3, l4 + nl4,
        l5 + nl5, l6 + nl6, l7 + nl7, l8 + nl8, l9 + nl9, l10 + nl10,
        l11 + nl11, l12 + nl12, l13 + nl13, l14 + nl14, l15 + nl15,
        l16 + nl16, l17 + nl17, l18 + nl18, l19 + nl19, l20 + nl20, l21 + nl21);
  }

}
