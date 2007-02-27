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

class TestMath
   {
   public static void main(String args[])
      {
      run();
      }

   public static boolean run()
      {
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
