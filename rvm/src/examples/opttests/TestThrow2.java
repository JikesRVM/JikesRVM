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
   
class TestThrow2
   {
   public static void main(String args[])
      throws Throwable
      {
      run();
      }

   public static void run()
      throws Throwable
      {
      System.out.println("TestThrow2");
      int a = 1;
      int b = 2; 
      // test "user" exceptions
      try
         {
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
         System.out.println("caught: " + e);
         }
      }

   static int foo()
      throws MyError,NotMyError
      {
      if (true ) throw new    MyError();
      else       throw new NotMyError();
      }

   }
