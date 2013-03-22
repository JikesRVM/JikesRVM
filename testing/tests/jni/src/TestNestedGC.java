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

/**
 * Test GC with nested Native and Java frames on stack
 */
class TestNestedGC {

  static native void level0();

  public static void level1() {
    System.gc();
    System.out.println("First gc triggered.");
    level2();
  }

  static native void level2();

  public static void level3() {
    System.gc();
    System.out.println("PASS: Second gc triggered.");
  }

  public static void main(String[] args) {
    System.loadLibrary("TestNestedGC");
    level0();
  }
}
