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
class ExceptionTest {


  static void run2(int i) {



     try {
       System.out.println("does it work? " + i + "   ");
       if ((i < 0) || (i > 2)) {
         System.out.println(" IndexOutOfBoundsException with index = " + i);
         throw new IndexOutOfBoundsException();
       }
     } catch (IndexOutOfBoundsException e) {
       System.out.println(" NullPointerException caught.");
       System.out.println(" Will throw again");
       throw e;
     }
     /*
     try {
       Object[] n = new Integer[3];
       n[i] = new Integer(0);
     } catch (IndexOutOfBoundsException e) {
       System.out.println(" IndexOutOfBoundsException with index = " + i);
       System.out.println(" Will throw again");
       throw e;
     }
     */
  }


  public static void main(String[] args) {

    run();
  }

 public static boolean run() {
    try {
      ExceptionTest.run2(1);
      try {
        ExceptionTest.run2(2);
        try {
          ExceptionTest.run2(3);
        } catch (IndexOutOfBoundsException e1) {

          System.out.println(" so [0].");

          try {
            ExceptionTest.run2(4);


            try {
              ExceptionTest.run2(5);
            } catch (IndexOutOfBoundsException e2) {
              System.out.println(" so [1].");
            }


          } catch (IndexOutOfBoundsException e3) {
            System.out.println(" so [2].");
          }

        }
      } catch (IndexOutOfBoundsException e4) {
        System.out.println(" so [4].");
      }
    } catch (IndexOutOfBoundsException e5) {
      System.out.println(" so.");
    }
    System.out.println(" At End");

    return true;
  }

}
