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
class Mangled_Name_s_ {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;


  public static native void
    setVerboseOff();

  // method name for special case
  public static native int _underscore();
  public static native int with_underscore();
  public static native int overload();
  public static native int overload(int i);
  public static native int overload(boolean b, int i, String[] args);


  public static void main(String[] args) {
    int returnValue;
    boolean returnBoolean;

    System.loadLibrary("Mangled_Name_s_");
    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    returnValue = _underscore();
    checkTest(returnValue, "_underscore");

    returnValue = with_underscore();
    checkTest(returnValue, "with_underscore");

    returnValue = overload();
    checkTest(returnValue, "overload");

    returnValue = overload(1);
    checkTest(returnValue, "overload(int)");

    returnValue = overload(true, 1, args);
    checkTest(returnValue, "int overload(boolean,int,String[]");


    // Summarize

    if (allTestPass)
      System.out.println("PASS: Mangled_Name_s_");
    else
      System.out.println("FAIL: Mangled_Name_s_");

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
