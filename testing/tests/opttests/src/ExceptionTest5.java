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
class ExceptionTest5 {


  static int[] testa = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      if (testa.length <= 3)
         throw new IndexOutOfBoundsException("I am IndexOBE");
      testa[3] = 0;
    } catch (NullPointerException n) {
        System.out.println(n + ", but caught by NullPointCheckException");
    } catch (ArithmeticException a) {
        System.out.println(a + ", but caught by ArithMeticException");
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException caught");
    }
    System.out.println(" At End");

    return true;
  }

}
