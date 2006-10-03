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

class TestMonitorUnwind
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestMonitorUnwind");

      try
         {
         new TestMonitorUnwind().foo(1,0);
         }
      catch (Exception e)
         {
         }

      try
         {
         error();
         }
      catch (Exception e)
         {
         }
      
      SystemOut.println("bye\n");
      }

   synchronized int foo(int a, int b)
      {
      return bar(a, b);
      }

   synchronized int bar(int a, int b)
      {
      int c = a / b; // throws exception: monitors must be unwound by vm during exception delivery
      return c;
      }

    static synchronized int error()
       throws Exception
       {
       throw new Exception("oops"); // likewise, but for static method (synchronize on class)
       }
   }
