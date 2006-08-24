/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class TestClassInitializerA
   {
   static         { SystemOut.println("clinit called for TestClassInitializerA"); }
   static int f() { SystemOut.println("TestClassInitializerA.f called"); return 123; }
   static int i = f();
   }

class TestClassInitializerB
   {
   TestClassInitializerB()        { }
   static     { SystemOut.println("clinit called for TestClassInitializerB"); }
   int    f() { SystemOut.println("TestClassInitializerB.f called"); return 456; }
   }

class TestClassInitializerC
   {
   static     { SystemOut.println("clinit called for TestClassInitializerC"); }
   }
   
class TestClassInitializerD extends TestClassInitializerC
   {
   static     { SystemOut.println("clinit called for TestClassInitializerD"); }
   static int i = 123;
   }
   
class TestClassInitializer
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestClassInitializer");
      int                   i = TestClassInitializerA.i;     SystemOut.println(i);     // test initialization before first field reference
      TestClassInitializerB b = new TestClassInitializerB(); SystemOut.println(b.f()); // test initialization before first instance creation
      TestClassInitializerD d = new TestClassInitializerD(); SystemOut.println(d.i);   // test order of superclass initialization
      }
   }
