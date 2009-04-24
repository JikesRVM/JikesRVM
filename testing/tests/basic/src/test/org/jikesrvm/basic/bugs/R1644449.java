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
package test.org.jikesrvm.basic.bugs;

/**
 * [ 1644449 ] Floating point display breaks on IA32.
 */
public class R1644449 {
  public static float b = 0.009765625f;
  public static float a = 10.57379f;

  public static void main(String[] args) {
    System.out.println(a * b);
  }
}
