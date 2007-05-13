/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

class ExceptionTest5 {


  static int testa[] = new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      if (testa.length <= 3) 
         throw new IndexOutOfBoundsException("I am IndexOBE");
      testa[3] = 0;
    } catch (NullPointerException n) {
        System.out.println( n + ", but caught by NullPointCheckException");
    } catch (ArithmeticException a) {
        System.out.println( a + ", but caught by ArithMeticException");
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" IndexOutOfBoundsException caught");
    }
    System.out.println(" At End");

    return true;
  }

}
