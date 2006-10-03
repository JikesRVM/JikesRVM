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

class A
   {
   A() { SystemOut.print("A.init "); }
   }

class B extends A
   {
   B()        { SystemOut.print("B.init "); }
   void foo() { SystemOut.print("B.foo "); }
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
      
   C()                { SystemOut.print("C.init "); }
           void foo() { SystemOut.print("C.foo "); }
   private void bar() { SystemOut.print("C.bar "); }
   }

class TestSpecialCall
   {
   public static void main(String args[])
      {
   // VM.boot();
      runTest();
      }

   public static void runTest()
      {
      SystemOut.println("TestSpecialCall");
      
      SystemOut.print("want: A.init B.init C.init A.init B.foo C.bar C.foo\n");
      SystemOut.print(" got: ");
      new C().test();
      SystemOut.println();
      }
   }
