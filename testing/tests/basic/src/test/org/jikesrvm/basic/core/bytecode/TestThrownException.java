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
package test.org.jikesrvm.basic.core.bytecode;

import java.io.IOException;

class TestThrownException {
  private static void testHardwareException() {
    System.out.println("testHardwareException");
    int i = 1;
    int j = 0;
    int k = i / j;
    System.out.println(k);
  }

  private static void testSoftwareException() {
    System.out.println("testSoftwareException");
    Float f = Float.valueOf("abc");
    System.out.println(f);
  }

  private static void testUserException() throws IOException {
    System.out.println("testUserException");
    throw new IOException();
  }

  private static void testRethrownException() throws Exception {
    System.out.println("testRethrownException");
    try {
      throw new Exception();
    } catch (Exception e) {
      throw e;
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  private static void testNullException() {
    System.out.println("testNullException");
    Object foo = null;
    foo.hashCode();
  }

  private static void testReThrownThruSynchronizedSection() throws Exception {
    System.out.println("testReThrownThruSynchronizedSection");
    Object lock = new Object();
    synchronized (lock) {
      try {
        throw new RuntimeException("MyException");
      } catch (Exception e) {
        throw e;
      }
    }
  }

  static void trouble(int choice) throws Exception {
    if (choice == 1) testHardwareException();
    if (choice == 2) testSoftwareException();
    if (choice == 3) testUserException();
    if (choice == 4) testRethrownException();
    if (choice == 5) testNullException();
    if (choice == 6) testReThrownThruSynchronizedSection();
  }

  public static void main(String[] args) {
    for (int i = 1; i <= 6; ++i) {
      try {
        trouble(i);
        System.out.println("Error: no exception thrown!");
      } catch (Exception e) {
        System.out.println("caught " + e.getClass());
        checkTrace(e);
      }
    }
  }

  private static void checkTrace(final Throwable throwable) {
    final StackTraceElement[] elements = throwable.getStackTrace();
    boolean foundOurClass = false;
    for (StackTraceElement element : elements) {
      if (element.getClassName().indexOf("TestThrownException") >= 0) {
        System.out.println("Found our class in the stack trace");
        foundOurClass = true;
        break;
      }
    }
    if (!foundOurClass) {
      System.out.println("Error, our class wasn't found in the stack trace");
    }
  }
}
