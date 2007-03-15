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
class TestArray {

   static int array[] = new int[10];
   static int temp;

   public static void main(String args[]) {
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
