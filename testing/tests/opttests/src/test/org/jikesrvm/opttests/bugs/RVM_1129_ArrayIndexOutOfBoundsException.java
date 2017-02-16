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
package test.org.jikesrvm.opttests.bugs;

/**
 * Testcase for elision of constructors of subclasses of {@link Throwable}
 * in stack traces. This test case makes sure that the stack trace for
 * {@link ArrayIndexOutOfBoundsException} is the same on all platforms.
 */
public class RVM_1129_ArrayIndexOutOfBoundsException {

  public static void main(String[] args) {
    causeArrayIndexOutOfBoundsException();
  }

  private static void causeArrayIndexOutOfBoundsException() {
    final int[] array = new int[4];
    System.out.println(array[5]);
  }

}
