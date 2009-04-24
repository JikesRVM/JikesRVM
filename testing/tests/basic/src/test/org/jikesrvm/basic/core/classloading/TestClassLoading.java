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
package test.org.jikesrvm.basic.core.classloading;

class TestClassLoading {
  public static void main(String[] args) {
    doTest("java.lang.String");
    doTest("java.lang.Number"); //Can't instantiate abstract classes
    doTest("java.lang.Integer"); //Can't instantiate as no default constructor

    doTest("[Ljava.lang.String;"); //Can't instantiate arrays
    doTest("[[Ljava.lang.String;"); //Can't instantiate arrays
    doTest("[I"); //Can't instantiate arrays
    doTest("[[I"); //Can't instantiate arrays

    doTest("I"); //Can not load classes for primitives
    doTest("NoExist"); //Non existent class
  }

  private static void doTest(final String classname) {
    System.out.print("Class.forName(" + classname + ") found? ");
    final Class c;
    try {
      c = Class.forName(classname);
      System.out.println("true");
    } catch (final ClassNotFoundException e) {
      System.out.println("false");
      return;
    }

    System.out.print("Class.forName(" + classname + ").newInstance() successful? ");
    try {
      c.newInstance();
      System.out.println("true");
    } catch (final Throwable throwable) {
      System.out.println("false due to " + throwable.getClass().getName());
    }
  }
}
