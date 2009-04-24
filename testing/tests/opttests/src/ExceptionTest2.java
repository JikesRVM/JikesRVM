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
class ExceptionTest2 {


  static int[] testa = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      return run2();
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException: '" + e5 +"', but caught in run()!!!");
    }
    System.out.println(" At End");

    return true;
  }

  public static boolean run2() throws IndexOutOfBoundsException {
    return run3();
  }

  public static boolean run3() throws IndexOutOfBoundsException {
    return run4();
  }

  public static boolean run4() throws IndexOutOfBoundsException {
    return run5();
  }

  public static boolean run5() throws IndexOutOfBoundsException {
    return run6();
  }

  public static boolean run6() throws IndexOutOfBoundsException {
    return run7();
  }

  public static boolean run7() throws IndexOutOfBoundsException {
      throw new IndexOutOfBoundsException("I was thrwon in run7()!!!");
  }


}
