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
package test.org.jikesrvm.basic.core.bytecode;

import org.vmmagic.pragma.NoTailCallElimination;

class TestStackOverflow {

  public static void main(String[] args) {
    try {
      unboundedRecursion();
    } catch (StackOverflowError soe) {
      System.out.println("Caught stack overflow error");
    }
  }

  @NoTailCallElimination
  public static void unboundedRecursion() {
    unboundedRecursion();
  }

}
