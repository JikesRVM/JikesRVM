/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */

class TestStaticCall
   {
   public static void main(String[] args)
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestStaticCall");

      int i = f(111);
      SystemOut.print("\nwant: 333\n got: "); SystemOut.println(i);
      }
      
   static int
   f(int arg)
      {
      return arg + 222;
      }
   }
