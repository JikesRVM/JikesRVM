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
class NullIdentity {

  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();
  static native int nullFirst(Object a);
  static native int nullSecond(Object a, Object b);
  static native int nullForceSpill(Object a, Object b, Object c, Object d,
                                   Object e, Object f, Object g, Object h,
                                   Object i, Object j, Object k, Object l);

  /************************************************************
   * Main body of the test program
 */
  public static void main(String[] args) {
    int returnValue;
    String anObj = new String("Year of the Dragon");

    System.loadLibrary("NullIdentity");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    returnValue = nullFirst(null);
    checkTest(returnValue, "nullFirst");

    returnValue = nullSecond(anObj, null);
    checkTest(returnValue, "nullSecond");

    returnValue = nullForceSpill(anObj, anObj, anObj, anObj, anObj, anObj,
                                 anObj, anObj, anObj, anObj, anObj, null);
    checkTest(returnValue, "nullForceSpill");

    if (allTestPass)
      System.out.println("PASS: NullIdentity");
    else
      System.out.println("FAIL: NullIdentity");
  }

  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }

  static void checkTest(int returnValue, String testName) {
    if (returnValue==0) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }

}
