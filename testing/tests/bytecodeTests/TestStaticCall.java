/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/*
 * @author unascribed
 */

class TestStaticCall
   {
   public static void main(String args[])
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
