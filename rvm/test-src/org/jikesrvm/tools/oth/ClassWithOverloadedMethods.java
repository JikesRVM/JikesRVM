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
package org.jikesrvm.tools.oth;

/**
 * The solo purpose of this class is to be loaded via a test case for the
 * OptTestHarness.
 */
public class ClassWithOverloadedMethods {

  public static void print(String msg) {
    System.out.println(msg);
  }

  public static void print(int i) {
    System.out.println(Integer.toString(i));
  }

  public static void print(double d) {
    System.out.println(Double.toString(d));
  }

  public static void print(int[] numbers) {
    if (numbers == null) {
      return;
    }
    for (int i : numbers) {
      System.out.println(i);
    }
  }

  public static void print(String[] messages) {
    for (String s : messages)
    System.out.println(s);
  }

}
