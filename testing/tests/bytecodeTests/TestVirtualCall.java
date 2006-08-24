/*
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
   public static void main(String args[])
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
      
      TestVirtualC c = new TestVirtualC();
      SystemOut.print("\nwant: 3\n got: ");
      SystemOut.println(c.g());
      }
   }
