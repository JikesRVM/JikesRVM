/*
 * (C) Copyright IBM Corp. 2001
 */
// TestThrow
//
import java.io.*;

class MyTestErrorBase extends Throwable
   {
   }
   
class MyTestError extends MyTestErrorBase
   {
   }
   
class NotMyTestError extends Throwable
   {
   }
   
class TestThrow
   {
   public static void main(String args[])
      throws Throwable
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      throws Throwable
      {
      SystemOut.println("TestThrow");
      
      // test "user" exceptions
      try
         {
         int a = 1;
         int b = 2;
         int c = a + b * foo();
         SystemOut.println(c);
         }
      catch (MyTestErrorBase  e)
         {
         SystemOut.println("caught: " + e);
      // e.printStackTrace(System.out);     // !!TODO: fix backtrace so it omits <init> functions for throwables
         }
      
      // test "vm" exceptions
      try
         {
         FileInputStream s = new FileInputStream("xyzzy");
         SystemOut.println(s);
         }
      catch (IOException e)
         {
         SystemOut.println("caught: " + e);
      // e.printStackTrace(System.out);     // !!TODO: fix backtrace so it omits <init> functions for throwables
         }
      
      // test throw through synchronized block
      try
         {
         baz();
         }
      catch (Throwable e)
         {
         SystemOut.println("caught: " + e);
         }
      }

   static int foo()
      throws MyTestError,NotMyTestError
      {
      if (true ) throw new    MyTestError();
      else       throw new NotMyTestError();
      }

   static int bar()
      {
      int i = 1;
      int j = 0;
      return i / j;
      }

    static int baz()
       throws MyTestError, NotMyTestError
       {
       Object lock = new Object();
       synchronized (lock)
          {
          try
             {
             return foo();
             }
          catch (MyTestError e)
             {
             throw e;
             }
          catch (NotMyTestError e)
             {
             throw e;
             }
          }
       }
   }
