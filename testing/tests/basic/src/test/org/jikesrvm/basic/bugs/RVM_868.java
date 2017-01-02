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

interface A {
  void a();
}

abstract class B implements A {
}

abstract class C extends B {
}

class D extends C {
  @Override
  public void a() {
    System.out.println("D.a()");
  }
}

/**
 * RVM-868: Method resolution must also look for methods
 * implemented in interfaces that are implemented by a class.
 */
public class RVM_868 {
  public static void main(String[] args) {
    D d = new D();
    d.a();
    C c = new D();
    c.a();
  }
}
