/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestFinally
   {
   static int
   foo()
      {
      try 
         {
         int a = 1;
         int b = 0;
         return a / b;
         }

      catch(Exception e)
         {
         return 1;
         }

      finally 
         {
         return 2;
         }

      // not reached
      }
   
   public static void 
   main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestFinally");

      SystemOut.println(foo());
      
      try 
         {
         SystemOut.println("hi");      // jsr
         return;
         }

      finally 
         {
         SystemOut.println("bye");
         }                              // ret
      }
   }
