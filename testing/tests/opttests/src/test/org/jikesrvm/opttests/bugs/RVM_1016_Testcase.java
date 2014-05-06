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

import org.vmmagic.pragma.NoInline;

/**
 * Testcase for emitting of ORC instructions on PPC.<p>
 *
 * Incorrect templates for the ORC and ORCr operators led to invalid
 * instructions being emitted. This test case tests only ORC.
 */
public class RVM_1016_Testcase {

  public static void main(String[] args) {
    orc(0, Integer.MIN_VALUE);
    orc(0, Integer.MAX_VALUE);

    orc(Integer.MIN_VALUE, -1);
    orc(Integer.MAX_VALUE, -1);

    orc(0, 0);
    orc(-1, -1);
    orc(-1, 0);
    orc(1, 0);
    orc(0, 1);
    orc(0, -1);
    orc(0, -2);

    orc(0, 2);

    orc(3, 5);
    orc(5, 3);

    orc(2, 1);

    orc(7, 13);
    orc(13, 7);
  }

  @NoInline // prevent removal of arithmetic operations by optimization
  private static void orc(int firstOp, int secondOp) {
    printOperation(firstOp, secondOp);
    int result = firstOp | ~secondOp;
    System.out.println(result);
  }

  @NoInline // inlining this will make the error disappear
  private static void printOperation(int firstOp, int secondOp) {
    System.out.print(firstOp + " | ~ " + secondOp + " : ");
  }

}
