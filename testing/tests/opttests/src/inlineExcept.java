/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * simple test of inlining & exception handling
 *
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
