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

/**
 * Test stack resize with native methods, various scenarios:
 *  -first entry to native code:  first resize
 *  -second nested entry to native code: no resize
 *  -fill up stack and make another entry to native code:  second resize
 */

class StackResize {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

  public static native boolean expectResize(int count);

  /* native method to be invoked when native frame exists on stack
   * Stack should not be resized
   */
  public static native boolean expectNoResize(int count);

  /* Java callback from native
   * From here we do another native call which should not cause the
   * stack to resize because there are native frames on the stack
   */
  public static boolean makeSecondNativeCall() {
    Thread th = Thread.getCurrentThread();
    int currentStackSize = Magic.getArrayLength(th.stack);

    // call another native method
    boolean resizeDidNotOccur = expectNoResize(currentStackSize);
    if (resizeDidNotOccur==false) {
      if (verbose)
        VM.sysWrite("> Unexpected stack resize with native frame present\n");
      return false;
    }
    return true;
  }

  public static boolean checkResizeOccurred(int previousStackSize) {
    Thread th = Thread.getCurrentThread();
    int currentStackSize = Magic.getArrayLength(th.stack);

    if (verbose) {
      VM.sysWrite("check resize: previous ");
      VM.sysWrite(previousStackSize); VM.sysWrite(", current ");
      VM.sysWrite(currentStackSize); VM.sysWrite("\n");
    }

    return !currentStackSize==previousStackSize;
  }


  /* recurse until the frame is within a few words of the
   * stack limit, then call a native method expecting the
   * stack to get resized.
   */
  @NoOptCompile
  public static boolean nativeWithStackAlmostFull() {
    Thread th = Thread.getCurrentThread();
    // VM.disableGC();   // holding frame pointer
    int fp = Magic.getFramePointer();
    int spaceLeft = fp - th.stackLimit;

    // debug printing:  OK until last frame, will cause stack overflow
    // because sysWrite will need many frames
    // VM.sysWrite("filling: left ");
    // VM.sysWrite(spaceLeft); VM.sysWrite("\n");
    // recursion to fill stack up to 3 words left
    if ((spaceLeft) > (500*4)) {
      // VM.enableGC();
      return nativeWithStackAlmostFull();
    } else {
      // VM.enableGC();
      int currentStackSize = Magic.getArrayLength(th.stack);
      boolean resizeOccurred = expectResize(currentStackSize);
      if (resizeOccurred) {
        return true;
      } else {
        if (verbose)
          VM.sysWrite("> Second stack resize did not occur\n");
        return false;
      }
    }

  }

  public static void main(String[] args) {
    boolean returnValue;
    FieldAccess tempObject;

    System.loadLibrary("StackResize");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        // for verbose native, have to edit the flag in StackResize.c
      }
    }

    if (verbose)
      VM.sysWrite("Checking stack size\n");
    // Test 1
    // First check if the current stack size is smaller than
    // required for native call
    Thread th = Thread.getCurrentThread();
    int currentStackSpace = Magic.getArrayLength(th.stack);
    if (currentStackSpace>VM.STACK_SIZE_JNINATIVE) {
      if (verbose)
        VM.sysWrite("StackResize:  normal stack size already exceeds native requirement, stack will not get resized.\n  Set up the system configuration for smaller normal stack:  StackFrameLayoutConstants.java\n");
      VM.sysWrite("FAIL: StackResize\n");
    }

    if (verbose)
      VM.sysWrite("Starting test 1\n");

    // proceed with resize test
    returnValue = expectResize(currentStackSpace);
    checkTest(0, returnValue, "first stack resize");

    // Test 2
    // After the stack has been resized once, fill up the stack
    // and call native again to force a second resize
    if (verbose)
      VM.sysWrite("Starting test 2\n");
    returnValue =  nativeWithStackAlmostFull();
    checkTest(0, returnValue, "second stack resize");


    if (allTestPass)
      System.out.println("PASS: StackResize");
    else
      System.out.println("FAIL: StackResize");
 }

  static void checkTest(int returnValue, boolean postCheck, String testName) {
    if (returnValue==0 && postCheck) {
      printVerbose("PASS: " + testName);
    } else {
      allTestPass = false;
      printVerbose("FAIL: " + testName);
    }
  }

  static void printVerbose(String str) {
    if (verbose)
      System.out.println(str);
  }
}
