/*
 * (C) Copyright IBM Corp. 2001
 */
// TestThrow
//
import java.io.*;

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
      // e.printStackTrace(System.out);     // !!TODO: fix backtrace so it omits <init> functions for throwables
         }
      
      // test "vm" exceptions
      try
         {
         FileInputStream s = new FileInputStream("xyzzy");
         System.out.println(s);
         }
      catch (IOException e)
         {
         System.out.println("caught: " + e);
      // e.printStackTrace(System.out);     // !!TODO: fix backtrace so it omits <init> functions for throwables
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
