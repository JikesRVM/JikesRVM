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
class A {
  public static void main(String[] args) {
    Y y = new Y();
    check(y);
    ifNonNullCheck(y);
    checkcast(y);
    nonNullCheckcast(y);
    C c = new C();
    check(c);
    ifNonNullCheck(c);
    checkcast(c);
    nonNullCheckcast(c);
    check(null);
    ifNonNullCheck(null);
    checkcast(null);
    nonNullCheckcast(null);
    System.out.println("A? "+new A().test1());
    System.out.println("B? "+new B().test1());
    System.out.println("C? "+new C().test1());
    System.out.println("E? "+new E().test1());
    System.out.println("G? "+new G().test1());
    System.out.println("Y? "+new Y().test1());
    System.out.println("A? "+new A().test1());
    System.out.println("B? "+new B().test1());
    System.out.println("C? "+new C().test1());
    System.out.println("E? "+new E().test1());
    System.out.println("G? "+new G().test1());
    System.out.println("Y? "+new Y().test1());

    System.out.println("A? "+new A().test2());
    System.out.println("B? "+new B().test2());
    System.out.println("C? "+new C().test2());
    System.out.println("E? "+new E().test2());
    System.out.println("G? "+new G().test2());
    System.out.println("Y? "+new Y().test2());
    System.out.println("A? "+new A().test2());
    System.out.println("B? "+new B().test2());
    System.out.println("C? "+new C().test2());
    System.out.println("E? "+new E().test2());
    System.out.println("G? "+new G().test2());
    System.out.println("Y? "+new Y().test2());
    System.out.println(test4(new A()));
    System.out.println(test4(new int[3]));

    arrayChecks(new int[10]);
    arrayChecks(new int[10][10]);
    arrayChecks(new A[10]);
    arrayChecks(new C[10]);
    arrayChecks(null);
    arrayChecks(new J[10]);
    arrayChecks(new Object[2][3][5]);

    checkStore(new int[10][10], new A());
    checkStore(new int[10][10], new int[5]);
    checkStore(new int[10][10], new float[5]);
    checkStore(new int[10][10], new int[5][3][5]);
    checkStore(new A[10], new C());
    checkStore(new C[10], new B());
    checkStore(new C[10], new C());
    checkStore(new J[10], new A());
    checkStore(new J[10], new E());
    checkStore(new Object[2], new A());
    checkStore(new Object[2], new A[3]);
    checkStore(new Object[2], new int[3]);
    checkStore(new Object[2], new A[3][2]);
    checkStore(new Object[2], new int[3][4][1]);
    checkStore(new Object[2][2][2], new int[3]);
    checkStore(new Object[2][2][2], new int[3][1][1]);
    checkStore(new Object[2][2][2], new int[3][1][1][1]);
    checkStore(new Object[2][2][2], new A[3]);
    checkStore(new Object[2][2][2], new A[3][1][1]);
    checkStore(new Object[2][2][2], new A[3][1][1][1]);
    checkStore(new Object[2][2][2], null);

    checkAStore(new A[3], new B());
    checkAStore(new A[3], new A());
    checkAStore(new B[3], new B());
    checkAStore(new B[3], new A());
    checkAStore(new B[3], new C());

    doit();
  }

  static void check(A a) {
    System.out.println(" is X? "+(a instanceof X));
    System.out.println(" is B? "+(a instanceof B));
    System.out.println(" is C? "+(a instanceof C));
    System.out.println(" is D? "+(a instanceof D));
  }

  static void ifNonNullCheck(A a) {
    if (a == null) return;
    if (a instanceof X) {
      System.out.println(" is X.");
    } else {
      System.out.println(" not X.");
    }

    if (a instanceof B) {
      System.out.println(" is B.");
    } else {
      System.out.println(" not B.");
    }

    if (a instanceof C) {
      System.out.println(" is C.");
    } else {
      System.out.println(" not C.");
    }

    if (a instanceof D) {
      System.out.println(" is D.");
    } else {
      System.out.println(" not D.");
    }

    ///
    if (!(a instanceof X)) {
      System.out.println(" not X.");
    } else {
      System.out.println(" is X.");
    }

    if (!(a instanceof B)) {
      System.out.println(" not B.");
    } else {
      System.out.println(" is B.");
    }

    if (!(a instanceof C)) {
      System.out.println(" not C.");
    } else {
      System.out.println(" is C.");
    }

    if (!(a instanceof D)) {
      System.out.println(" not D.");
    } else {
      System.out.println(" is D.");
    }

  }

  static void ifCheck(A a) {
    if (a instanceof X) {
      System.out.println(" is X.");
    } else {
      System.out.println(" not X.");
    }

    if (a instanceof B) {
      System.out.println(" is B.");
    } else {
      System.out.println(" not B.");
    }

    if (a instanceof C) {
      System.out.println(" is C.");
    } else {
      System.out.println(" not C.");
    }

    if (a instanceof D) {
      System.out.println(" is D.");
    } else {
      System.out.println(" not D.");
    }

    ///
    if (!(a instanceof X)) {
      System.out.println(" not X.");
    } else {
      System.out.println(" is X.");
    }

    if (!(a instanceof B)) {
      System.out.println(" not B.");
    } else {
      System.out.println(" is B.");
    }

    if (!(a instanceof C)) {
      System.out.println(" not C.");
    } else {
      System.out.println(" is C.");
    }

    if (!(a instanceof D)) {
      System.out.println(" not D.");
    } else {
      System.out.println(" is D.");
    }
  }


  static void nonNullCheckcast(A a) {
    if (a == null) return;
    try {
      X x = (X)a;
      System.out.println(" is X.");
    } catch (ClassCastException e) {
      System.out.println(" not X.");
    }

    try {
      B x = (B)a;
      System.out.println(" is B.");
    } catch (ClassCastException e) {
      System.out.println(" not B.");
    }

    try {
      C x = (C)a;
      System.out.println(" is C.");
    } catch (ClassCastException e) {
      System.out.println(" not C.");
    }

    try {
      D x = (D)a;
      System.out.println(" is D.");
    } catch (ClassCastException e) {
      System.out.println(" not D.");
    }
  }

  static void checkcast(A a) {
    try {
      X x = (X)a;
      System.out.println(" is X.");
    } catch (ClassCastException e) {
      System.out.println(" not X.");
    }

    try {
      B x = (B)a;
      System.out.println(" is B.");
    } catch (ClassCastException e) {
      System.out.println(" not B.");
    }

    try {
      C x = (C)a;
      System.out.println(" is C.");
    } catch (ClassCastException e) {
      System.out.println(" not C.");
    }

    try {
      D x = (D)a;
      System.out.println(" is D.");
    } catch (ClassCastException e) {
      System.out.println(" not D.");
    }
  }

  static void arrayChecks(Object a) {
    System.err.println("Testing "+a);
    System.out.println(" is X[]? "+(a instanceof X[]));
    System.out.println(" is B[]? "+(a instanceof B[]));
    System.out.println(" is C[]? "+(a instanceof C[]));
    System.out.println(" is D[]? "+(a instanceof D[]));
    System.out.println(" is A? "+(a instanceof A));
    System.out.println(" is I[]? "+(a instanceof I[]));
    System.out.println(" is J[]? "+(a instanceof J[]));
    System.out.println(" is A[][]? "+(a instanceof A[][]));
    System.out.println(" is Object[]? "+(a instanceof Object[]));
  }

  static void checkStore(Object[] xs, Object x) {
    try {
      System.err.println("Stored "+x+" into "+xs);
      xs[1] = x;
      System.out.println("Worked");
    } catch (ArrayStoreException e) {
      System.out.println("Failed");
    }
  }

  static void checkAStore(A[] xs, A x) {
    try {
      System.err.println("checkAStore: Stored "+x+" into "+xs);
      xs[1] = x;
      System.out.println("Worked");
    } catch (ArrayStoreException e) {
      System.out.println("Failed");
    }
  }

  String test1() {
    if (this instanceof I)
      return "true";
    else
      return "false";
  }

  String test2() {
    if (this instanceof J)
      return "true";
    else
      return "false";
  }

  boolean test3(Object o) {
    return o instanceof double[];
  }

  static int test4(Object o) {
    if (o instanceof int[]) {
      return 1;
    } else {
      return 22;
    }
  }

  void st1(Object[] xs, Object x) {
    xs[1] = x;
  }
  void st2(A[] xs, A x) {
    xs[1] = x;
  }
  void st3(A[][] xs, B x) {
    xs[1][0] = x;
  }
  void st4(J[] xs, D x) {
    xs[1] = x;
  }
  void st5(J[][][] xs, D x) {
    xs[1][1][0] = x;
  }
  void st6(J[][] xs, D x) {
    xs[1][1] = x;
  }
  void st7(A[][] xs, B[] x) {
    xs[1] = x;
  }

  static void doit() {
    System.out.println("A is [Object?"+instanceOfObjectArray(new A()));
    System.out.println("[A is [Object?"+instanceOfObjectArray(new A[5]));
    System.out.println("[[A is [Object?"+instanceOfObjectArray(new A[3][2]));
    System.out.println("[I is [Object?"+instanceOfObjectArray(new int[10]));
    System.out.println("[[I is [Object?"+instanceOfObjectArray(new int[10][2]));
    System.out.println("[[[I is [Object?"+instanceOfObjectArray(new int[10][2][1]));
    System.out.println("[L is [Object?"+instanceOfObjectArray(new L[3]));
    System.out.println("[[L is [Object?"+instanceOfObjectArray(new L[3][2]));
  }

  static boolean instanceOfObjectArray(Object x) {
    return x instanceof Object[];
  }

}

class B extends A implements I {}
class C extends B implements J {}
class D extends C implements L {}
class E extends D {}
class F extends E {}
class G extends F implements M {}
final class H extends G {}

class X extends A {}
class Y extends X implements K {}

interface I {}
interface J extends I {}
interface K extends I {}
interface L extends J {}
interface M extends K, L {}
