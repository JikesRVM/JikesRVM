/*
 * (C) Copyright IBM Corp. 2001
 */
// TestInterfaceCall
//
interface InterfaceFoo
   {
   int one = 1;
   int foo();
   }

interface InterfaceBar
   {
   int two = 2;
   int bar();
   }

interface InterfaceBaz extends InterfaceFoo
   {
   int baz();
   }

class TestInterfaceA implements InterfaceFoo, InterfaceBar
   {
   public int foo() { return 1; }
   public int bar() { return 2; }
   }

class TestInterfaceB implements InterfaceBar, InterfaceFoo
   {
   public int bar() { return 3; }
   public int foo() { return 4; }
   }

class TestInterfaceC extends TestInterfaceB implements InterfaceFoo
   {
   }

class TestInterfaceD implements InterfaceBaz
   {
   public int foo() { return 5; }
   public int baz() { return 6; }
   }

class TestInterfaceE extends TestInterfaceD
   {
   }

class TestInterfaceCall
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestInterfaceCall");

      // test method invocation
      
      InterfaceFoo foo = null;
      foo = new TestInterfaceA(); SystemOut.println(foo.foo());   // 1
      foo = new TestInterfaceB(); SystemOut.println(foo.foo());   // 4
      
      InterfaceBar bar = null;
      bar = new TestInterfaceA(); SystemOut.println(bar.bar());   // 2
      bar = new TestInterfaceB(); SystemOut.println(bar.bar());   // 3
      
      foo = new TestInterfaceC(); SystemOut.println(foo.foo());   // 4
      
      // test type comparison

      SystemOut.println(new TestInterfaceD() instanceof InterfaceBaz); // true
      SystemOut.println(new TestInterfaceD() instanceof InterfaceFoo); // true
      SystemOut.println(new TestInterfaceD() instanceof InterfaceBar); // false

      SystemOut.println(new TestInterfaceE() instanceof InterfaceBaz); // true
      SystemOut.println(new TestInterfaceE() instanceof InterfaceFoo); // true
      SystemOut.println(new TestInterfaceE() instanceof InterfaceBar); // false
      }
   }
