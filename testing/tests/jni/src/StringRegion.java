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

    // create a new String object to ensure that we have an unchanged
    // copy of the input if possible. Note that this is not guaranteed;
    // the class library implementation may just perform shallow
    // copies when creating new String objects (GNU Classpath does this).
    String originalInput = new String("Live Free or Die");

    // This is the output that the test writes into the backing array of the
    // String using JNI
    String oneAllowedOutput = "Free Java or Die";

    // get some input for the string.
    String inputStr = "Live Free or Die";
    int ret = testStringRegion(inputStr);
    checkTest(ret, true, "StringRegion1");

    ret = testStringCritical(inputStr);
    // This result is possible if the VM applies enough optimizations
    // (e.g. when this method is optimized on O1).
    boolean stringUnchanged = inputStr.equals(originalInput);
    // This result is possible if the VM actually
    // reads the String array after returning from JNI (e.g. when
    // the method is baseline compiled).
    boolean stringChangedToExpected = inputStr.equals(oneAllowedOutput);
    // Note that both variables may be true at the same time if the implementation
    // of new String(String) only performs a shallow copy.
    boolean outputOK = stringUnchanged || stringChangedToExpected;
    checkTest(ret, outputOK, "StringCritical");


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
    if (returnValue == 0 && postCheck) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }

}
