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
class CriticalCopy {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  static int[]     intArray     = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
  static boolean[] booleanArray = {true, true, false, false, true, true, false, false, true, true};
  static short[]   shortArray   = {1, 3, 5, 7, 9, 11, 13, 15, 17, 19};
  static byte[]    byteArray    = {2, 4, 6, 8, 10, 12, 14, 16, 18, 20};
  static char[]    charArray    = {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j'};
  static long[]    longArray    = {0x80001000, 0x80001000, 0x80001000, 0x80001000, 0x80001000,
                                   0x80001000, 0x80001000, 0x80001000, 0x80001000, 0x80001000};
  static double[]  doubleArray  = {115.1, 115.1, 115.1, 115.1, 115.1, 115.1, 115.1, 115.1, 115.1, 115.1};
  static float[]   floatArray   = {(float) 115.1, (float) 115.1, (float) 115.1, (float) 115.1, (float) 115.1,
                                   (float) 115.1, (float) 115.1, (float) 115.1, (float) 115.1, (float) 115.1};


  /**
   * Declare native methods that will call the JNI Array Functions
   */

  public static native int primitiveIntegerArray(int[] intArray);
  public static native int primitiveByteArray(byte[] byteArray);


  public static void main(String[] args) {

    int returnValue;

    System.loadLibrary("CriticalCopy");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }


    returnValue = primitiveIntegerArray(intArray);
    // check that the array has new values
    boolean checkFlag = true;
    if (verbose)
      System.out.println("Updated copy");

    for (int i=0; i<intArray.length; i++) {
      if (verbose)
        System.out.println("    " + i + " = " + intArray[i]);
      if (intArray[i]!=i)
        checkFlag = false;
    }
    checkTest(returnValue, checkFlag, "primitiveIntegerArray");

    // Summarize

    if (allTestPass)
      System.out.println("PASS: CriticalCopy");
    else
      System.out.println("FAIL: CriticalCopy");

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
