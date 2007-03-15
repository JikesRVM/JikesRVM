/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */

class FinallyTest {


  static int testa[] = null; //new int[3];
  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    int i = 0, j = 0, k = 0;
    try {
      i = 1; j = 1; k = 1;
      foo();
    } catch (IndexOutOfBoundsException e5) {
      i = 1; j = 2; k = 1;
      // i = 10; j = 10; k = 10;
      // System.out.println(" IndexOutOfBoundsException caught");
      
    } catch (NullPointerException e) {
      i = 2; j = 1; k = 1;
      // i = 100; j = 100; k = 100;
      // System.out.println(" NullPointerException caught");
    } finally {
      i += 1000; j += 1000; k += 1000;

       // System.out.println(" Finally");
    }
    // System.out.println(" At End");

    // System.out.println(" i = " + i + "; j = " + j + "; k = " + k);
    i = i + j + k;
    return true;
  }

  public static void foo() {
      testa[4] = 0;
  }

}
