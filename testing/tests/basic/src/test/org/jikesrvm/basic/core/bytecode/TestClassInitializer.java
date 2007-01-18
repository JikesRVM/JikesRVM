/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/bytecodeTests/src/TestClassInitializer.java 10684 2006-12-05T17:57:54.600932Z dgrove-oss  $
package test.org.jikesrvm.basic.core.bytecode;

/**
 * @author unascribed
 */
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

  public static void main(final String args[]) {
    int i = TypeA.i;
    System.out.println(i);     // test initialization before first field reference
    System.out.println(new TypeB().f()); // test initialization before first instance creation
    System.out.println(TypeD.i);   // test order of superclass initialization
  }
}
