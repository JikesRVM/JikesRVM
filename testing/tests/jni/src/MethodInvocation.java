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
class MethodInvocation {

  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;
  static int testFlagForVoid = 0;        // flag to be updated by method returning void

  public static native void setVerboseOff();

  /************************************************************
   * Declare native methods that will call the JNI method invocation
   */
  static native int invokeStaticMethodA(Object objArg);
  static native int invokeStaticMethodV(Object objArg);
  static native int invokeStaticMethod(Object objArg);
  static native int invokeVirtualMethodA(MethodInvocation targetObj, Object objArg);
  static native int invokeVirtualMethodV(MethodInvocation targetObj, Object objArg);
  static native int invokeVirtualMethod(MethodInvocation targetObj, Object objArg);
  static native int invokeNonVirtualMethodA(MethodInvocationSub targetObj, Object objArg);
  static native int invokeNonVirtualMethodV(MethodInvocationSub targetObj, Object objArg);
  static native int invokeNonVirtualMethod(MethodInvocationSub targetObj, Object objArg);



  /************************************************************
   * Static methods serving as target to be invoked from native code
   * receive arguments of every type, return one of each type
   */

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Z        */
  public static boolean staticReturnBoolean(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return !val8;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)B        */
  public static byte staticReturnByte(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return (byte) (val0 + 3);
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)C        */
  public static char staticReturnChar(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    if (val1=='a')
      return 'b';
    else
      return 'c';    // didn't get the expected argument, force test to fail
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)S        */
  public static short staticReturnShort(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return (short) (val2 + 15);
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)I        */
  public static int staticReturnInt(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val3 + (int) val2;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)J        */
  public static long staticReturnLong(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val4 + (long) val3;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)F        */
  public static float staticReturnFloat(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val5 + (float) 100.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)D        */
  public static double staticReturnDouble(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val6 + (double) 100.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)         */
  public static void staticReturnVoid(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    testFlagForVoid = 789;     // update the flag to indicate success
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;        */
  public static Object staticReturnObject(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return new String("Year 2000");
  }


  /************************************************************
   * Virtual methods serving as target to be invoked from native code
   * receive arguments of every type, return one of each type
   */

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Z        */
  public boolean virtualReturnBoolean(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return !val8;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)B        */
  public byte virtualReturnByte(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return (byte) (val0 + 7);
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)C        */
  public char virtualReturnChar(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    if (val1=='x')
      return 'y';
    else
      return 'z';    // didn't get the expected argument, force test to fail
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)S        */
  public short virtualReturnShort(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return (short) (val2 + 23);
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)I        */
  public int virtualReturnInt(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val3 + (int) val0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)J        */
  public long virtualReturnLong(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val4 + (long) val2;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)F        */
  public float virtualReturnFloat(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val5 + (float) 32.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)D        */
  public double virtualReturnDouble(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return val6 + (double) 1000.0;
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)         */
  public void virtualReturnVoid(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    testFlagForVoid = 456;     // update the flag to indicate success
  }

  /* The signature is:     (BCSIJFDLjava/lang/Object;Z)Ljava/lang/Object;        */
  public Object virtualReturnObject(byte val0, char val1, short val2,
                                  int val3, long val4, float val5,
                                  double val6, Object val7, boolean val8) {
    return new String("RVM");
  }


  /************************************************************
   * Dummy constructor to get to the virtual methods
   */
  public MethodInvocation() {

  }

  /************************************************************
   * Main body of the test program
 */
  public static void main(String[] args) {
    int returnValue;
    String anObj = new String("Year of the Dragon");

    System.loadLibrary("MethodInvocation");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    /**
     *  Test the static method invocations
     */
    // pass arguments as an array of jvalue
    returnValue = invokeStaticMethodA(anObj);
    checkTest(returnValue, "callStatic<type>MethodA");

    // pass arguments as va_list pointer to a variable argument list
    testFlagForVoid = 0;        // reset flag to be updated by method returning void
    returnValue = invokeStaticMethodV(anObj);
    checkTest(returnValue, "callStatic<type>MethodV");


    // pass arguments as a variable argument list at the call site
    testFlagForVoid = 0;        // reset flag to be updated by method returning void
    returnValue = invokeStaticMethod(anObj);
    checkTest(returnValue, "callStatic<type>Method");



    /**
     *  Test the virtual method invocations
     */
    // pass arguments as an array of jvalue
    MethodInvocation targetObj = new MethodInvocation();
    returnValue = invokeVirtualMethodA(targetObj, anObj);
    checkTest(returnValue, "call<type>MethodA");

    // pass arguments as va_list pointer to a variable argument list
    testFlagForVoid = 0;        // reset flag to be updated by method returning void
    returnValue = invokeVirtualMethodV(targetObj, anObj);
    checkTest(returnValue, "call<type>MethodV");

    // pass arguments as a variable argument list at the call site
    testFlagForVoid = 0;        // reset flag to be updated by method returning void
    returnValue = invokeVirtualMethod(targetObj, anObj);
    checkTest(returnValue, "call<type>Method");



    /**
     * Test the nonvirtual method invocations
     */
    MethodInvocationSub subObj = new MethodInvocationSub();

    // reset flag to be updated by method returning void
    testFlagForVoid = 0;
    MethodInvocationSub.testFlagForVoid = 0;

    // pass arguments as an array of jvalue
    returnValue = invokeNonVirtualMethodA(subObj, anObj);
    checkTest(returnValue, "callNonVirtual<type>MethodA");


    // reset flag to be updated by method returning void
    testFlagForVoid = 0;
    MethodInvocationSub.testFlagForVoid = 0;

    // pass arguments as va_list pointer to a variable argument list
    returnValue = invokeNonVirtualMethodV(subObj, anObj);
    checkTest(returnValue, "callNonVirtual<type>MethodV");


    // reset flag to be updated by method returning void
    testFlagForVoid = 0;
    MethodInvocationSub.testFlagForVoid = 0;

    // pass arguments as a variable argument list at the call site
    returnValue = invokeNonVirtualMethod(subObj, anObj);
    checkTest(returnValue, "callNonVirtual<type>Method");


    // Summarize tests

    if (allTestPass)
      System.out.println("PASS: MethodInvocation");
    else
      System.out.println("FAIL: MethodInvocation");


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
