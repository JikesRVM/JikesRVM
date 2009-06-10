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
class TestArray {

   static int[] array = new int[10];
   static int temp;

   public static void main(String[] args) {
     run();
   }

   static boolean run() {
     try {
     temp = array[-1];
     } catch (Exception e) {
        System.out.println(e);
     }
     try {
        run1(11);
     } catch(Exception e) {
        System.out.println(e);
     }
     return true;
   }

   static void run1(int a) {
     temp = array[a];
   }
}
