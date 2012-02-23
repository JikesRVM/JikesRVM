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
package test.org.jikesrvm.basic.bugs;

public class RVM_915 {
  static class A {
    class B {
    }
    B newB() {
      return new B();
    }
  }

  static Object d = new Object(){};

  public static void main(String[] args) {
    A a = new A();
    A.B b = a.newB();
    Object c = new Object(){};
    System.out.println(a.getClass().isAnonymousClass());
    System.out.println(b.getClass().isAnonymousClass());
    System.out.println(c.getClass().isAnonymousClass());
    System.out.println(d.getClass().isAnonymousClass());
  }
}
