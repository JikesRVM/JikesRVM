/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/bytecodeTests/src/TestInvoke.java 10684 2006-12-05T17:57:54.600932Z dgrove-oss  $
package test.org.jikesrvm.basic.core.bytecode;

import org.vmmagic.pragma.NoInline;

/*
* @author unascribed
*/
class TestInvoke {

  static interface MyInterface {
    void performMagic();
  }

  static class TypeA {
    TypeA() {System.out.println("TypeA.<init>()");}

    void f() { System.out.println("TypeA.f()"); }
  }

  static class TypeB extends TypeA {
    TypeB() {System.out.println("TypeB.<init>()");}

    //invokevirtual
    void f() { System.out.println("TypeB.f()"); }

    //invokestatic
    static int g(int value) { return 3 + value; }
  }

  static class TypeC extends TypeB implements MyInterface {
    TypeC() {System.out.println("TypeC.<init>()");}

    void test() {
      System.out.println("TypeC.test()");
      myPrivate();
    }

    //invokeinterface
    public void performMagic() {
      System.out.println("TypeC.performMagic()");
    }

    //invokespecial
    private void myPrivate() {
      System.out.println("TypeC.myPrivate()");
    }
  }

  public static void main(String[] args) {
    final TypeA a = new TypeA();
    final TypeB b = new TypeB();
    final TypeC c = new TypeC();

    callF(a);
    callF(b);
    callPerformMagic(c);

    c.test();

    System.out.print("invokestatic TypeB.g() Expected: 42 Actual: ");
    System.out.println(TypeB.g(39));

    System.out.print("invokestatic TypeC.g() Expected: 16 Actual: ");
    System.out.println(TypeC.g(13));
  }

  @NoInline
  private static void callF(TypeA a) {
    a.f();
  }

  @NoInline
  private static void callPerformMagic(MyInterface myInterface) {
    myInterface.performMagic();
  }
}
