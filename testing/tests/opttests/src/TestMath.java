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
class TestMath {
   public static void main(String[] args) {
      run();
   }

   public static boolean run() {
      System.out.println("TestMath");

      System.out.println("-- Math.floor --");

      System.out.println("\nwant: 1.0 \ngot:  " + Math.floor(1.6));
      System.out.println("\nwant: 1.0 \ngot:  " + Math.floor(1.5));
      System.out.println("\nwant: 1.0 \ngot:  " + Math.floor(1.4));
      System.out.println("\nwant: 1.0 \ngot:  " + Math.floor(1.0));

      System.out.println("\nwant: -2.0 \ngot:  " + Math.floor(-2.0));
      System.out.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.6));
      System.out.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.5));
      System.out.println("\nwant: -2.0 \ngot:  " + Math.floor(-1.4));

      System.out.println("-- Math.ceil --");

      System.out.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.6));
      System.out.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.5));
      System.out.println("\nwant: 2.0 \ngot:  " + Math.ceil(1.4));
      System.out.println("\nwant: 1.0 \ngot:  " + Math.ceil(1.0));

      System.out.println("\nwant: -2.0 \ngot:  " + Math.ceil(-2.0));
      System.out.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.6));
      System.out.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.5));
      System.out.println("\nwant: -1.0 \ngot:  " + Math.ceil(-1.4));

      return true;
   }
}
