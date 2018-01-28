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

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.SysCall;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.NoOptCompile;

/**
 * Tests that the stack alignment on the optimizing compiler for x64 works. This test is
 * fragile. It's currently also deactivated by default on everything except x64.
 */
class TestStackAlignment {

  private static Random r = new Random(123456L);

  private static boolean bigFrameOK;

  private static int bigFrame_frameSize;

  private static boolean bigFrame2OK;

  private static int bigFrame2_frameSize;

  @NoOptCompile // ensure same stack offset for both methods
  public static void main(String[] args) {
    if (!VM.BuildFor64Addr || !VM.BuildForIA32 || Magic.getCompilerLevel() < 0) {
      System.out.println("Test NYI or not applicable for current arch, skipping it");
      printTestSuccessMessage();
      return;
    }

    System.out.println("firstMethod call");
    firstMethod(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
        0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    System.out.println("firstMethod returned");

    System.out.println("secondMethod call");
    secondMethod(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
        0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    System.out.println("secondMethod returned");
    if (bigFrameOK && bigFrame2OK) {
      int frameSizeDifference = Math.abs(bigFrame2_frameSize - bigFrame_frameSize);
      if (frameSizeDifference != 8) {
        System.out.println("FAILURE: Frame sizes of test methods must differ " +
            "by 8 but they differ by " + frameSizeDifference);
      } else {
        printTestSuccessMessage();
      }
    }
  }

  @NoInline
  public static void firstMethod(long l0, long l1, long l2, long l3,
      long l4, long l5, long l6, long l7, long l8, long l9, long l10,
      long l11, long l12, long l13, long l14, long l15, long l16, long l17,
      long l18, long l19, long l20, long l21) {
    if (l0 % 2 == 1) {
      bigFrame_frameSize = Magic.getFrameSize();
      return;
    }
    bigFrameOK = false;
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
    firstMethod(l0 + nl0, l1 + nl1, l2 + nl2, l3 + nl3, l4 + nl4,
        l5 + nl5, l6 + nl6, l7 + nl7, l8 + nl8, l9 + nl9, l10 + nl10,
        l11 + nl11, l12 + nl12, l13 + nl13, l14 + nl14, l15 + nl15,
        l16 + nl16, l17 + nl17, l18 + nl18, l19 + nl19, l20 + nl20, l21 + nl21);
    SysCall.sysCall.sysStackAlignmentTest();
    bigFrameOK = true;
  }

  @NoInline
  public static void secondMethod(long l0, long l1, long l2, long l3,
      long l4, long l5, long l6, long l7, long l8, long l9, long l10,
      long l11, long l12, long l13, long l14, long l15, long l16, long l17,
      long l18, long l19, long l20, long l21, long l22) {
    if (l0 % 2 == 1) {
      bigFrame2_frameSize = Magic.getFrameSize();
      return;
    }
    bigFrame2OK = false;
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
    long nl22 = r.nextLong();
    secondMethod(l0 + nl0, l1 + nl1, l2 + nl2, l3 + nl3, l4 + nl4,
        l5 + nl5, l6 + nl6, l7 + nl7, l8 + nl8, l9 + nl9, l10 + nl10,
        l11 + nl11, l12 + nl12, l13 + nl13, l14 + nl14, l15 + nl15,
        l16 + nl16, l17 + nl17, l18 + nl18, l19 + nl19, l20 + nl20, l21 + nl21, nl22);
    SysCall.sysCall.sysStackAlignmentTest();
    bigFrame2OK = true;
  }

  private static void printTestSuccessMessage() {
    System.out.println("ALL TESTS PASSED");
  }

}
