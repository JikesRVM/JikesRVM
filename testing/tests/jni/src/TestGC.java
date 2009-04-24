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

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;

/**
 * Test GC with Native frames on stack
 */
class TestGC {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call cause GC to occur
   */

  static native Object testgc(Object obj1, Object obj2);

  public static void main(String[] args) {

    boolean runningUnderJDK = true;

    String str1 = new String("string 1");
    String str2 = new String("string 2");

    System.loadLibrary("TestGC");

    if (args.length!=0) {
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-quiet")) {
                verbose = false;
                setVerboseOff();
            }
            if (args[i].equals("-jdk")) {
                runningUnderJDK = true;
            }
        }
    }

    Object returnobj;

    if (!runningUnderJDK) {

        Address oldAddress1 = Magic.objectAsAddress(str1);
        Address oldAddress2 = Magic.objectAsAddress(str2);
        printVerbose("  str1 address = " + VM.addressAsHexString(oldAddress1));
        printVerbose("  str2 address = " + VM.addressAsHexString(oldAddress2));

        returnobj = testgc(str1, str2);
        printVerbose("TestGC After native call:");

        Address newAddress1 = Magic.objectAsAddress(str1);
        Address newAddress2 = Magic.objectAsAddress(str2);
        if (oldAddress1!=newAddress1 && oldAddress2!=newAddress2) {
            printVerbose("Objects have been moved by GC:");
        } else {
            printVerbose("Objects have NOT been moved by GC:");
        }
        printVerbose("  str1 address = " + VM.addressAsHexString(newAddress1));
        printVerbose("  str2 address = " + VM.addressAsHexString(newAddress2));
        printVerbose("  returnobj address = " + VM.addressAsHexString(Magic.objectAsAddress(returnobj)));
    } else {
        returnobj = testgc(str1, str2);
        printVerbose("TestGC After native call:");
    }

    // if (copyingGC)
    //   checkTest( 0, (str1==returnobj), "GC with copying during native code" );
    // else
    //   checkTest( 0, (str1==returnobj), "GC without copying during native code" );
    checkTest(0, (str1==returnobj), "GC during native code");
  }

  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      System.out.println("PASS: " + testName);
    } else {
      allTestPass = false;
      System.out.println("FAIL: " + testName);
    }
  }

}

