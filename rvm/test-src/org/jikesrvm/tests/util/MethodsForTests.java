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
package org.jikesrvm.tests.util;

import java.util.Random;

import org.vmmagic.pragma.BaselineSaveLSRegisters;
import org.vmmagic.pragma.NoBoundsCheck;
import org.vmmagic.pragma.NoCheckStore;
import org.vmmagic.pragma.NoNullCheck;
import org.vmmagic.pragma.NoTailCallElimination;
import org.vmmagic.pragma.Uninterruptible;

public class MethodsForTests {

  public static void emptyStaticMethodWithoutAnnotations() {}

  @NoBoundsCheck
  public static void emptyStaticMethodWithNoBoundCheckAnnotation() {}

  @NoCheckStore
  public static void emptyStaticMethodWithNoCheckStoreAnnotation() {}

  @NoNullCheck
  public static void emptyStaticMethodWithNoNullCheckAnnotation() {}

  @NoTailCallElimination
  public static void emptyStaticMethodWithNoTailCallEliminationAnnotation() {}

  @BaselineSaveLSRegisters
  public static void emptyStaticMethodWithBaselineSaveLSRegistersAnnotation() {}

  public static void emptyStaticMethodWithParams(long l, int i, Object o, double d) {}

  public static void emptyStaticMethodWithObjectParam(Object o) {}

  public static void emptyStaticMethodWithReferenceParam(MethodsForTests o) {}

  @Uninterruptible
  public static void emptyStaticUninterruptibleMethod() {}

  public static void printRandomInteger() {
    System.out.println(Integer.toString(new Random().nextInt()));
  }

  public static int multiplyByAFewPrimes(int num) {
    return num * 3 * 5 * 7 * 11;
  }

  public static void methodWithAFewLocals() {
    int a = 1 * 2 * 3;
    int b = 4 * 5;
    int c = a + b;
    System.out.println(c);
  }

  public static long staticMethodReturningLongMaxValue() {
    return Long.MAX_VALUE;
  }

  public static Object emptyStaticMethodWithObjectParamAndReturnValue(Object o) {
    return null;
  }

  public static void methodForInliningTests() {
    @SuppressWarnings("unused")
    Object o = emptyStaticMethodWithObjectParamAndReturnValue(new Object());
  }

  public static synchronized void emptySynchronizedStaticMethod() {}

  public static synchronized void emptySynchronizedStaticMethodForOSR() {}

  public void emptyInstanceMethodWithoutAnnotations() {}

  public void emptyInstanceMethodWithParams(Object o, double d, int i, long l) {}

  public synchronized void emptySynchronizedInstanceMethod() {}

}
