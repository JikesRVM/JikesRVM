/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
interface InterfaceFoo {
   int one = 1;
   int foo();
}

interface InterfaceBar {
   int two = 2;
   int bar();
}

interface InterfaceBaz extends InterfaceFoo {
   int baz();
}

class TestInterfaceA implements InterfaceFoo, InterfaceBar {
   public int foo() { return 1; }
   public int bar() { return 2; }
}

class TestInterfaceB implements InterfaceBar, InterfaceFoo {
   public int bar() { return 3; }
   public int foo() { return 4; }
}

class TestInterfaceC extends TestInterfaceB implements InterfaceFoo {
}

class TestInterfaceD implements InterfaceBaz {
   public int foo() { return 5; }
   public int baz() { return 6; }
}

class TestInterfaceE extends TestInterfaceD {
}

class TestInterfaceCall {
  public static void main(String[] args) {
    run();
  }

  static boolean testSuccess = true;

  public static boolean run() {
    System.out.print("TestInterfaceCall");

    // test method invocation

    InterfaceFoo foo = null;
    foo = new TestInterfaceA();
    if (foo.foo() != 1) {
      testSuccess = false;
      System.out.println("\n Expected value: 1; Returned value: " + foo.foo());   // 1
    }

    foo = new TestInterfaceB();
    if (foo.foo() != 4) {
      testSuccess = false;
      System.out.println("\n Expected value: 4; Returned value: " + foo.foo());   // 4
    }


    InterfaceBar bar = null;
    bar = new TestInterfaceA();
    if (bar.bar() != 2) {
      //      System.out.println(bar.bar());   // 2
      testSuccess = false;
      System.out.println("\n Expected value: 2; Returned value: " + bar.bar());   // 2

    }

    bar = new TestInterfaceB();
    if (bar.bar() != 3) {
      //      System.out.println(bar.bar());   // 3
      testSuccess = false;
      System.out.println("\n Expected value: 3; Returned value: " + bar.bar());   // 3

    }

    foo = new TestInterfaceC();
    if (foo.foo() != 4) {
      //      System.out.println(foo.foo());   // 4
      testSuccess = false;
      System.out.println("\n Expected value: 4; Returned value: " + foo.foo());   // 4

    }

    // test type comparison

    TestInterfaceD tid = new TestInterfaceD();
    if (!((tid instanceof InterfaceBaz) &&
          (tid instanceof InterfaceFoo) &&
          (!(tid instanceof InterfaceBar)))) {
      testSuccess = false;
      System.out.print("\n Expected value: true, true, false;  Returned value: ");
      System.out.print((new TestInterfaceD() instanceof InterfaceBaz) + ", "); // true
      System.out.print((new TestInterfaceD() instanceof InterfaceFoo) + ", "); // true
      System.out.print(new TestInterfaceD() instanceof InterfaceBar); // false
    }

    TestInterfaceE tie = new TestInterfaceE();
    if (!((tie instanceof InterfaceBaz) &&
          (tie instanceof InterfaceFoo) &&
          (!(tie instanceof InterfaceBar)))) {
      testSuccess = false;
      System.out.print("\n Expected value: true, true, false;  Returned value: ");
      System.out.print((new TestInterfaceE() instanceof InterfaceBaz) + ", "); // true
      System.out.print((new TestInterfaceE() instanceof InterfaceFoo) + ", "); // true
      System.out.print(new TestInterfaceE() instanceof InterfaceBar); // false
    }

    if (testSuccess)
      System.out.println(" succeeded.");
    else
      System.out.println(" failed. ***************\n\n");

    return testSuccess;
  }
}
