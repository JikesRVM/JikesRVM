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
class TestVirtualA
   {
   int f() { return 1; }
   }

class TestVirtualB extends TestVirtualA
   {
   int f() { return 2; }
   static int g() { return 3; }
   }

class TestVirtualC extends TestVirtualB
   {
   }

class TestVirtualCall
   {
   public static void main(String[] args)
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestVirtualCall");

      TestVirtualA a = new TestVirtualA();
      TestVirtualB b = new TestVirtualB();
      TestVirtualA t = a;

      SystemOut.print("\nwant: 12\n got: ");
      for (int i = 0; i < 2; ++i)
         {
         int j = t.f();
         SystemOut.print(j);
         t = b;
         }
      SystemOut.println();
      
      new TestVirtualC();
      SystemOut.print("\nwant: 3\n got: ");
      SystemOut.println(TestVirtualC.g());
      }
   }
