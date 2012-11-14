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
import java.lang.reflect.Field;
import java.lang.reflect.Method;


class JNI12 {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /* Declare native methods that will call the JNI 1.2 reflection functions */
  /** Should the same Method that it is passed, but tests FromReflectedMethod
   * and ToReflectedMethod.  */
  static native Method testReflectedMethods(Class cls, Method m);
  /** Should the same Field that it is passed, but tests FromReflectedField
   * and ToReflectedField.  */
  static native Field testReflectedFields(Class cls, Field m);

  /** Make sure we get the same data by using a weak ref that we do by
   * using a global ref. */
  static native Object testGlobalCreationAndReturn(Method m);
  static native int testGlobalPersistenceAndDestruction(Object m);

  static native Object testWeakCreationAndReturn(Method m);
  static native int testWeakPersistenceAndDestruction(Object m);


  public void dummyFunc() { }
  public int dummyFld;

  public static void main(String[] args) throws NoSuchMethodException,
                                                NoSuchFieldException {
    System.loadLibrary("JNI12");

    if (args.length != 0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    Object returnObj;
    // Reflected Methods

    Class<?> myClass =JNI12.class;

    Method dummyM = myClass.getMethod("dummyFunc", new Class[0]);
    returnObj = testReflectedMethods(myClass, dummyM);
    checkTest(0, (returnObj.equals(dummyM)), "ReflectedMethods");

    Field dummyF = myClass.getField("dummyFld");

    returnObj = testReflectedFields(myClass, dummyF);
    checkTest(0, (returnObj.equals(dummyF)), "ReflectedFields & NewLocalRef");

    Object heldGlobal = testGlobalCreationAndReturn(dummyM);
    if (heldGlobal == null) {
      if (verbose)
        System.err.println("testGlobalCreationAndReturn returned null!");
      heldGlobal = new Object();
    }
    if (verbose) {
      System.out.println("heldGlobal = " + heldGlobal.toString());
    }
    checkTest(0, (heldGlobal != null && heldGlobal.equals(dummyM)), "GlobalCreationAndReturn");

    int rint = testGlobalPersistenceAndDestruction(heldGlobal);
    checkTest(rint, true, "GlobalPersistenceAndDestruction");

    Object heldWeak = testWeakCreationAndReturn(dummyM);
    if (heldWeak == null) {
      if (verbose)
        System.err.println("testWeakCreationAndReturn returned null!");
      heldWeak = new Object();
    }
    if (verbose) {
      System.out.println("heldWeak = " + heldWeak.toString());
    }
    checkTest(0, (heldWeak != null && heldWeak.equals(dummyM)), "WeakCreationAndReturn");

    rint = testWeakPersistenceAndDestruction(heldWeak);
    checkTest(rint, true, "WeakPersistenceAndDestruction");


    // Summarize

    if (allTestPass)
      System.out.println("PASS: JNI_12");
    else
      System.out.println("FAIL: JNI_12");
  }


  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }
}
