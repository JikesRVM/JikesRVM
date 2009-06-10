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
class TestClassInitializerA {
   static         { System.out.println("clinit called for TestClassInitializerA"); }
   static int f() { System.out.println("TestClassInitializerA.f called"); return 123; }
   static int i = f();
}

class TestClassInitializerB {
   TestClassInitializerB()        { }
   static     { System.out.println("clinit called for TestClassInitializerB"); }
   int    f() { System.out.println("TestClassInitializerB.f called"); return 456; }
}

class TestClassInitializerC {
   static     { System.out.println("clinit called for TestClassInitializerC"); }
}

class TestClassInitializerD extends TestClassInitializerC {
   static     { System.out.println("clinit called for TestClassInitializerD"); }
   static int i = 123;
}

class TestClassInitializer {
   public static void main(String[] args) {
      run();
   }

   public static void run() {
      System.out.println("TestClassInitializer");
      int                   i = TestClassInitializerA.i;     System.out.println(i);     // test initialization before first field reference
      TestClassInitializerB b = new TestClassInitializerB(); System.out.println(b.f()); // test initialization before first instance creation
      TestClassInitializerD d = new TestClassInitializerD(); System.out.println(d.i);   // test order of superclass initialization
   }
}
