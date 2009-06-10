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
final class inlineExcept {
  public static void main(String[] args) {
    run();
  }

  public static boolean run() {
    try {
      foo();
    } catch (IndexOutOfBoundsException e) {
      System.out.println("Caught IOOBE in foo");
    }
    return true;
  }

  static void foo() {
    bar();
  }

  static void bar() {
    throw new IndexOutOfBoundsException();
  }
}
