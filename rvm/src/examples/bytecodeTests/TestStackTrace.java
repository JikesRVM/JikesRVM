/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */

import java.io.*;
   
class TestStackTrace
   {
   TestStackTrace()
      throws Exception
      {
      try{
         testSoftwareException();
         }
      catch (Exception e)
         {
         throw e;
         }
      }
   
   static void
   testHardwareException()
      throws Exception
      {
      int i = 1; int j = 0; int k = i / j;
      SystemOut.println(k);
      }
   
   static void
   testSoftwareException()
      throws Exception
      {
      Float f = Float.valueOf("abc");
      SystemOut.println(f);
      }
   
   static void
   testUserException()
      throws Exception
      {
      throw new IOException();
      }
      
   static void
   testRethrownException()
      throws Exception
      {
      new TestStackTrace();
      }
   
   static void
   trouble(int choice)
      throws Exception
      {
      if (choice == 1) testHardwareException();
      if (choice == 2) testSoftwareException();
      if (choice == 3) testUserException();
      if (choice == 4) testRethrownException();
      }
      
   static void
   testAll()
      throws Exception
      {
      PrintWriter out = new PrintWriter ( 
                new FileOutputStream ("Exception.pw") );
      for (int i = 1; i <= 4; ++i)
         {
         SystemOut.println("test " + i);
         try{
            trouble(i);
            }
         catch (Exception e)
            {
            SystemOut.println("caught " + e);
            e.printStackTrace(System.out);
            e.printStackTrace(out);
            }
         }
      }
      
   public static void main(String args[])
      throws Exception
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      throws Exception
      {
      SystemOut.println("TestStackTrace");
      testAll();
      testAll();
      }
   }
