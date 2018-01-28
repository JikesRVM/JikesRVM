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
import org.jikesrvm.runtime.Magic;

/**
 * Contains a few simple tests for {@link Magic}.
 * <p>
 * Currently only has smoke tests for {@link Magic#getFrameSize()}.
 */
public class TestMagic {

  private static boolean success = true;

  /** Note: can't use SUCCESS because that's for overall success */
  private static final String TEST_SUCCESSFUL = " (OK)";

  public static void main(String[] args) {
    testMagicGetFrameSize();
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

  private static void testMagicGetFrameSize() {
    System.out.println("--- Tests for getFrameSize() ---");
    int frameSize = Magic.getFrameSize();
    System.out.println("Frame size was " + frameSize);
    booleanTest("Frame size > 0: ", frameSize > 0, true);
    // Holds for all our current architectures but it's really optional
    final int WORDSIZE = VM.BuildFor32Addr ? 4 : 8;
    System.out.println();
    booleanTest("Frame size was divisible by word size: ", frameSize % WORDSIZE == 0, true);
  }


}
