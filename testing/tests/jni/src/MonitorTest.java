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
class MonitorTest {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;
  static volatile boolean startCounting = false;
  static int globalCount = 0;
  static String stringObject;


  public static native void setVerboseOff();

  /**
   * native methods that will call the JNI Monitor functions
   */
  static native int accessMonitorFromNative(Object lockObject);


  /**
   * called from native, increment a count protected by a lock on stringObject in native
   */
  static void accessCountUnderNativeLock(int increment) {

    // lock the same object again to test nested monitor enter
    synchronized(stringObject) {
      globalCount += increment;
    }
  }

  static synchronized void setFailFlag() {
    allTestPass = false;
  }

  /**
   * constructor initializes instance fields
   */
  public MonitorTest() {

  }

  public static void main(String[] args) {

    System.loadLibrary("MonitorTest");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    // Create an object to lock on
    stringObject = new String("Lock me");

    // Create 2 threads to contend for the lock and update a count
    printVerbose("Creating worker threads");
    Worker threadOne = new Worker("thread One", stringObject);
    Worker threadTwo = new Worker("thread Two", stringObject);

    printVerbose("Starting worker threads");
    threadOne.start();
    threadTwo.start();

    // wait for the threads to come up
    while (!threadOne.readyFlag || !threadTwo.readyFlag) {
    }

    printVerbose("Worker threads running, start counting");
    startCounting = true;

    // wait for the threads to finish
    while (!threadOne.doneFlag || !threadTwo.doneFlag) {
    }

    // check the count, should be 0 if the synchronization is correct
    printVerbose("Worker threads finish, check count");

    // get a copy so the checking can be protected in case some threads are still running
    int copyCount = globalCount;

    if (copyCount == 0 && allTestPass) {
      System.out.println("PASS: MonitorTest");
    } else {
      System.out.println("FAIL: MonitorTest");
      printVerbose("Expect globalCount = 0, get " + copyCount);
    }


  }


  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }

}


