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

import test.org.jikesrvm.basic.core.bytecode.data.SubClassInDifferentPackage;

public class TestClassHierarchy {

  public interface Magic { void magic(); }

  protected static class A implements Magic {
    public void magic() { System.out.print("A"); }
  }

  protected static class B extends A {
    public void magic() { System.out.print("B"); }
  }

  protected static class C extends B {
    public void magic() { System.out.print("C"); }
  }

  protected static class D extends A {
    public void magic() { System.out.print("D"); }
  }

  protected class E extends D {
    public void magic() { System.out.print("E"); }
  }

  protected class F extends A {
    public void magic() { System.out.print("F"); }
  }

  protected class G extends F {
    public void magic() { System.out.print("G"); }
  }

  protected class H implements Magic {
    public void magic() { System.out.print("H"); }
  }

  protected class I extends H {
    public void magic() { System.out.print("I"); }
  }

  protected class J extends I {
    public void magic() { System.out.print("J"); }
  }

/*
In TestClassHierarchy:
    A
    B extends A
    C extends B
    D extends A
    E extends D
    F extends A
    G extends F
    H
    I extends H
    J extends I

In class SubClassInDifferentPackage extends TestClassHierarchy:

    P_B extends A
    P_C1 extends B
    P_C2 extends P_B
    P_D extends A
    P_E1 extends D
    P_E2 extends P_D
    P_F extends A
    P_G1 extends F
    P_G2 extends P_F
    P_H
    P_I1 extends H
    P_I2 extends P_H
    P_J1 extends I
    P_J2 extends P_I1
    P_J3

In class SubSubClass extends SubClassInDifferentPackage:
    O_C1 extends P_B
    O_C2 extends P_B
    O_E extends P_D
    O_G extends SubClassInDifferentPackage.P_F
    O_I1 extends H
    O_I2 extends P_H
    O_J1 extends P_I1
    O_J2 extends P_I2

*/

  static class SubSubClass extends test.org.jikesrvm.basic.core.bytecode.data.SubClassInDifferentPackage {

    static class O_C1 extends P_B {
      public void magic() { System.out.print("O_C1"); }
    }

    class O_C2 extends P_B {
      public void magic() { System.out.print("O_C2"); }
    }

    class O_E extends P_D {
      public void magic() { System.out.print("O_E"); }
    }

    class O_G extends SubClassInDifferentPackage.P_F {
      public void magic() { System.out.print("O_G"); }
    }

    class O_I1 extends H {
      public void magic() { System.out.print("O_I1"); }
    }

    class O_I2 extends P_H {
      public void magic() { System.out.print("O_I2"); }
    }

    class O_J1 extends P_I1 {
      public void magic() { System.out.print("O_J1"); }
    }

    class O_J2 extends P_I2 {
      public void magic() { System.out.print("O_J2"); }
    }

    private void runTests() {
      runTest("A", new A());
      runTest("B", new B());
      runTest("C", new C());
      runTest("D", new D());
      runTest("E", new E());
      runTest("F", new F());
      runTest("G", new G());
      runTest("H", new H());
      runTest("I", new I());
      runTest("J", new J());

      runTest("P_B", new P_B());
      runTest("P_C1", new P_C1());
      runTest("P_C2", new P_C2());
      runTest("P_D", new P_D());
      runTest("P_E1", new P_E1());
      runTest("P_E2", new P_E2());
      runTest("P_F", new P_F());
      runTest("P_G1", new P_G1());
      runTest("P_G2", new P_G2());
      runTest("P_H", new P_H());
      runTest("P_I1", new P_I1());
      runTest("P_I2", new P_I2());
      runTest("P_J1", new P_J1());
      runTest("P_J2", new P_J2());
      runTest("P_J3", new P_J3());

      runTest("O_C1", new O_C1());
      runTest("O_C2", new O_C2());
      runTest("O_E", new O_E());
      runTest("O_G", new O_G());
      runTest("O_I1", new O_I1());
      runTest("O_I2", new O_I2());
      runTest("O_J1", new O_J1());
      runTest("O_J2", new O_J2());
  }

  private static void runTest(final String name, final Magic x3) {
    System.out.print("Testing " + name + " instanceOf: ");
    testInstanceOf(x3);
    System.out.print(" casts: ");
    testCasts(x3);
    System.out.print(" magic: ");
    x3.magic();
    System.out.println();
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private static void testCasts(final Object x) {
    try { final A o = (A) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final B o = (B) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final C o = (C) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final D o = (D) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final E o = (E) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final F o = (F) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final G o = (G) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final H o = (H) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final I o = (I) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final J o = (J) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final P_B o = (P_B) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final P_C1 o = (P_C1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final P_C2 o = (P_C2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final P_D o = (P_D) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_E1 o = (SubClassInDifferentPackage.P_E1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_E2 o = (SubClassInDifferentPackage.P_E2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_F o = (SubClassInDifferentPackage.P_F) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_G1 o = (SubClassInDifferentPackage.P_G1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_G2 o = (SubClassInDifferentPackage.P_G2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_H o = (SubClassInDifferentPackage.P_H) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_I1 o = (SubClassInDifferentPackage.P_I1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_I2 o = (SubClassInDifferentPackage.P_I2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_J1 o = (SubClassInDifferentPackage.P_J1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_J2 o = (SubClassInDifferentPackage.P_J2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final SubClassInDifferentPackage.P_J3 o = (SubClassInDifferentPackage.P_J3) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_C1 o = (O_C1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_C2 o = (O_C2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_E o = (O_E) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_G o = (O_G) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_I1 o = (O_I1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_I2 o = (O_I2) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_J1 o = (O_J1) x; success(); } catch (final ClassCastException cce) { failure(); }
    try { final O_J2 o = (O_J2) x; success(); } catch (final ClassCastException cce) { failure(); }
  }

  private static void failure() {System.out.print("0");}

  private static void success() {System.out.print("1");}

  private static void testInstanceOf(final Object x) {
    io(A.class, (x instanceof A));
    io(B.class, (x instanceof B));
    io(C.class, (x instanceof C));
    io(D.class, (x instanceof D));
    io(E.class, (x instanceof E));
    io(F.class, (x instanceof F));
    io(G.class, (x instanceof G));
    io(H.class, (x instanceof H));
    io(I.class, (x instanceof I));
    io(J.class, (x instanceof J));
    io(P_B.class, (x instanceof P_B));
    io(P_C1.class, (x instanceof P_C1));
    io(P_C2.class, (x instanceof P_C2));
    io(P_D.class, (x instanceof P_D));
    io(SubClassInDifferentPackage.P_E1.class, (x instanceof SubClassInDifferentPackage.P_E1));
    io(SubClassInDifferentPackage.P_E2.class, (x instanceof SubClassInDifferentPackage.P_E2));
    io(SubClassInDifferentPackage.P_F.class, (x instanceof SubClassInDifferentPackage.P_F));
    io(SubClassInDifferentPackage.P_G1.class, (x instanceof SubClassInDifferentPackage.P_G1));
    io(SubClassInDifferentPackage.P_G2.class, (x instanceof SubClassInDifferentPackage.P_G2));
    io(SubClassInDifferentPackage.P_H.class, (x instanceof SubClassInDifferentPackage.P_H));
    io(SubClassInDifferentPackage.P_I1.class, (x instanceof SubClassInDifferentPackage.P_I1));
    io(SubClassInDifferentPackage.P_I2.class, (x instanceof SubClassInDifferentPackage.P_I2));
    io(SubClassInDifferentPackage.P_J1.class, (x instanceof SubClassInDifferentPackage.P_J1));
    io(SubClassInDifferentPackage.P_J2.class, (x instanceof SubClassInDifferentPackage.P_J2));
    io(SubClassInDifferentPackage.P_J3.class, (x instanceof SubClassInDifferentPackage.P_J3));
    io(O_C1.class, (x instanceof O_C1));
    io(O_C2.class, (x instanceof O_C2));
    io(O_E.class, (x instanceof O_E));
    io(O_G.class, (x instanceof O_G));
    io(O_I1.class, (x instanceof O_I1));
    io(O_I2.class, (x instanceof O_I2));
    io(O_J1.class, (x instanceof O_J1));
    io(O_J2.class, (x instanceof O_J2));
  }

  @SuppressWarnings({"UnusedDeclaration"})
  private static void io(final Class type, final boolean test) {
    System.out.print(test?"1":"0");
  }
  }

  private static void testInnerClass() {
    class Inner {
    }
    Class enclosing = Inner.class.getEnclosingClass();
    if (enclosing == null)
      System.out.println("Inner class has no enclosing class");
    else if (enclosing != TestClassHierarchy.class)
      System.out.println("Bad enclosing class");
    else
      System.out.println("Correct enclosing class");
    enclosing = SubSubClass.class.getEnclosingClass();
    if (enclosing == null)
      System.out.println("SubSubClass class has no enclosing class");
    else if (enclosing != TestClassHierarchy.class)
      System.out.println("Bad enclosing class");
    else
      System.out.println("Correct enclosing class");
  }

  public static void main(String[] args) {
    new SubSubClass().runTests();
    testInnerClass();
  }
}
