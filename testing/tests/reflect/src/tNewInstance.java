/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Test of whether we enforce member access with newInstance()
 *
 * @author Stephen Fink
 * @author Eugene Gluzberg
 */

class tNewInstance {
   public static void main(String args[]) {
      System.out.println("tNewInstance...");
      try {
         Class klass = Class.forName("OnlyPrivateConstructor");
         Object o = klass.newInstance(); 
      } catch (IllegalAccessException e2) {
         e2.printStackTrace();
         System.out.println("Test SUCCEEDED");
         return;
      } catch (Exception e) {
         e.printStackTrace();
      }
      System.out.println("Test FAILED");
   }
}

class OnlyPrivateConstructor{
   private OnlyPrivateConstructor() {}
}

