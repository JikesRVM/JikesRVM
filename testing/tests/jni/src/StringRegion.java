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
class StringRegion {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /* Declare native method that will call the JNI 1.2 GetStringRegion
   * functions.  */
  static native int testStringRegion(String s); // 0 if OK
  static native int testStringCritical(String s); // 0 if OK

  public static void main(String[] args) {
    System.loadLibrary("StringRegion");

    if (args.length != 0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    // Reflected Methods

    // get some input for the string
    String inputStr = "Live Free or Die";
    int ret = testStringRegion(inputStr);
    checkTest(ret, true, "StringRegion1");

    ret = testStringCritical(inputStr);
    checkTest(ret, inputStr.equals("Free Java or Die"), "StringCritical");


    // Summarize

    if (allTestPass)
      System.out.println("PASS: StringRegion");
    else
      System.out.println("FAIL: StringRegion");
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
