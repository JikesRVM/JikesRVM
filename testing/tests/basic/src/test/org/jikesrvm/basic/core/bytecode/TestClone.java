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

class TestClone implements Cloneable {
  String id;

  TestClone(String id) {
    this.id = id;
  }

  public static void
  main(String[] args)
      throws CloneNotSupportedException {

    final TestClone a = new TestClone("a");
    final TestClone b = (TestClone) a.clone();
    runTest(a, b);
    a.id = "aa";
    System.out.println();
    System.out.println("Changing clones value.");
    runTest(a, b);

    System.out.println();
    System.out.println("Testing arrays.");
    System.out.println();


    TestClone[][] c = new TestClone[2][3];
    for (int i = 0; i < 2; ++i)
      for (int j = 0; j < 3; ++j)
        c[i][j] = new TestClone(i + "" + j);
    final TestClone[][] d = c.clone();

    runTest(c,d);

    System.out.println();
    System.out.println("after changing object value");
    c[1][1].id = "xx";
    runTest(c,d);

    System.out.println();
    System.out.println("after changing element");
    c[1][1] = new TestClone("zz");
    runTest(c,d);

    System.out.println();
    System.out.println("after changing row");
    c[1] = new TestClone[]{new TestClone("a"),new TestClone("b"),new TestClone("c")};
    runTest(c,d);
  }

  private static void output(final Object o) {
    if (o instanceof TestClone[][]) {
      final TestClone[][] c = (TestClone[][]) o;
      for (int i = 0; i < 2; ++i) {
        for (int j = 0; j < 3; ++j)
          System.out.print(c[i][j].id + " ");
        System.out.println();
      }
    } else {
      System.out.println(((TestClone)o).id);
    }
  }

  private static void runTest(final Object a, final Object b) {
    System.out.println("Original:");
    output(a);
    System.out.println("Clone:");
    output(b);
    System.out.println("a.equals(b) = " + a.equals(b));
    System.out.println("a == b      = " + (a == b));
  }
}
