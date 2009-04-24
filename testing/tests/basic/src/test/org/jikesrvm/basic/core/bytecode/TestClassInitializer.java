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
package test.org.jikesrvm.basic.core.bytecode;

class TestClassInitializer {
  private static class TypeA {
    static { System.out.println("TypeA.<clinit>()"); }

    static int f() {
      System.out.println("TypeA.f()");
      return 123;
    }

    static int i = f();
  }

  private static class TypeB {
    static { System.out.println("TypeB.<clinit>()"); }

    int f() {
      System.out.println("TypeB.f()");
      return 456;
    }
  }

  private static class TypeC {
    static { System.out.println("TypeC.<clinit>()"); }
  }

  private static class TypeD extends TypeC {
    static { System.out.println("TypeD.<clinit>()"); }

    static int i = 123;
  }

  public static void main(final String[] args) {
    int i = TypeA.i;
    System.out.println(i);     // test initialization before first field reference
    System.out.println(new TypeB().f()); // test initialization before first instance creation
    System.out.println(TypeD.i);   // test order of superclass initialization
  }
}
