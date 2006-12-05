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

import java.io.FileInputStream;
import java.io.IOException;

class MyErrorBase extends Throwable
   {
   }
   
class MyError extends MyErrorBase
   {
   }
   
class NotMyError extends Throwable
   {
   }
   
class TestThrow
   {
   public static void main(String args[])
      throws Throwable
      {
   // VM.boot();
      run();
      }

   public static void run()
      throws Throwable
      {
      System.out.println("TestThrow");
      
      // test "user" exceptions
      try
         {
         int a = 1;
         int b = 2;
         int c = a + b * foo();
         System.out.println(c);
         }
      catch (MyErrorBase  e)
         {
         System.out.println("caught: " + e);
         }
      
      // test "vm" exceptions
      try
         {
         FileInputStream s = new FileInputStream("xyzzy");
         System.out.println(s);
         }
      catch (IOException e)
         {
         System.out.println("caught: " + e.getClass());
         }
      }

   static int foo()
      throws MyError,NotMyError
      {
      if (true ) throw new    MyError();
      else       throw new NotMyError();
      }

   static int bar()
      {
      int i = 1;
      int j = 0;
      return i / j;
      }
   }
