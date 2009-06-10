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
class T {
  T(int x) {
    this.x = x;
  }

  int foo() {
    for (int i=0; i<1000; i++)
      ;
    return x;
  }

  private int x;
}


public class gctest {
  public static void main(String[] arg) {
    T o1 = new T(3);
    T o2 = new T(4);
    T o3 = new T(4);
    T o4 = new T(4);
    T o5 = new T(4);
    T o6 = new T(4);
    T o7 = new T(4);
    T o8 = new T(4);
    T o9 = new T(4);
    T o10 = new T(4);
    int x;

    System.gc();
    o1 = new T(3);
    System.gc();

    x = o1.foo() +       o2.foo() +      o3.foo() +
      o4.foo() +     o5.foo() +     o6.foo() +
      o7.foo() +     o8.foo() +     o9.foo() +
      o10.foo();
    System.gc();


    // allocate more storage
    o1 = new T(3);
    o2 = new T(4);
    o3 = new T(4);
    o4 = new T(4);
    o5 = new T(4);
    o6 = new T(4);
    o7 = new T(4);
    o8 = new T(4);
    o9 = new T(4);
    o10 = new T(4);

    System.gc();

    x = o1.foo() +       o2.foo() +      o3.foo() +
      o4.foo() +     o5.foo() +     o6.foo() +
      o7.foo() +     o8.foo() +     o9.foo() +
      o10.foo();

  }
}
