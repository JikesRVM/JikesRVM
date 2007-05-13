/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 */

class ExceptionTest4 {


  static int testa[] = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    System.out.println(divide(1,0));
    return true;
  }

  static int divide(int a, int b) {
     try {
         return a/b;
     }
     catch(ArithmeticException e) {
         return a + 1;
     }
  }

}
