/*
 * (C) Copyright IBM Corp. 2001
 */
// TestSpecialCall
//

class A
   {
   A() { System.out.print("A.init "); }
   }

class B extends A
   {
   B()        { System.out.print("B.init "); }
   void foo() { System.out.print("B.foo "); }
   }

class C extends B
   {
   void test()
      {
      new A();     // invokespecial - <init>   --> A.init
      super.foo(); // invokespecial - super    --> B.foo
      bar();       // invokespecial - private  --> C.bar
      foo();       // invokevirtual            --> C.foo
      }
      
   C()                { System.out.print("C.init "); }
           void foo() { System.out.print("C.foo "); }
   private void bar() { System.out.print("C.bar "); }
   }

class TestSpecialCall
   {
   public static void main(String args[])
      {
   // VM.boot();
      run();
      }

   public static boolean run()
      {
      System.out.println("TestSpecialCall");
      
      System.out.print("want: A.init B.init C.init A.init B.foo C.bar C.foo\n");
      System.out.print(" got: ");
      new C().test();
      System.out.println();
      return true;
      }
   }
