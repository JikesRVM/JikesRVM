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

public class TestJNIGetFieldID {
  static {System.loadLibrary("TestJNIGetFieldID");}

  // set to true to get messages for each test
  static boolean verbose = true;
  static boolean allTestPass = true;

  static class A {
    public static int s = 1;
    public int a = 0;
  }

  interface I {
    int f = 1;
  }

  static class B extends A implements I {
    public int a = 1;
  }

  public static void main(String[] args) {
    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
      }
    }

    try {
      if (getInstanceFieldA(new B()) == 1) {
        if (verbose) System.out.println("instance_a: pass");
      } else {
        if (verbose) System.out.println("instance_a: fail");
        allTestPass = false;
      }
    } catch(Throwable e) {
      if (verbose) System.out.println("instance_a: fail");
      allTestPass = false;
    }

    try {
      if (getStaticFieldS(B.class) == 1) {
        if (verbose) System.out.println("static_s: pass");
      } else {
        if (verbose) System.out.println("static_s: fail");
        allTestPass = false;
      }
    } catch(Throwable e) {
      if (verbose) System.out.println("static_s: fail");
      allTestPass = false;
    }

    try {
      if (getStaticFinalF(B.class) == 1) {
        if (verbose) System.out.println("static_f: pass");
      } else {
        if (verbose) System.out.println("static_f: fail");
        allTestPass = false;
      }
    } catch(Throwable e) {
      if (verbose) System.out.println("static_f: fail");
      allTestPass = false;
    }

    try {
      if (getStaticFinalF(I.class) == 1) {
        if (verbose) System.out.println("static_I.f: pass");
      } else {
        if (verbose) System.out.println("static_I.f: fail");
        allTestPass = false;
      }
    } catch(Throwable e) {
      if (verbose) System.out.println("static_f: fail");
      allTestPass = false;
    }

    if (allTestPass) {
      System.out.println("PASS: TestJNIGetFieldID");
    } else {
      System.out.println("FAIL: TestJNIGetFieldID");
    }
  }

  private static native int getInstanceFieldA(B b);
  private static native int getStaticFieldS(Class c);
  private static native int getStaticFinalF(Class c);
}
