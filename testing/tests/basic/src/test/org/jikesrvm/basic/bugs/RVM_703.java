/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package test.org.jikesrvm.basic.bugs;

public class RVM_703 {

  public static void main(String a[]) {
    foo();
  }

  private static void foo() {
    bar();
  }

  private static void bar() {
    Throwable t = new Throwable();
    for (StackTraceElement l : t.getStackTrace()) {
      System.out.println(l.getClassName() + " " + l.getMethodName());
    }
  }
}
