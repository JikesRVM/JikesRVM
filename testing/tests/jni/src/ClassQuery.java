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
class ClassQuery extends ClassQuerySuper {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;
  int toTestConstructor = 1;

  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI Array Functions
   */

  static native Class testSuperClass(Class cls);
  static native boolean testAssignable(Class cls1, Class cls2);
  static native boolean testSameObject(Object obj1, Object obj2);
  static native Object testAllocObject(Class cls);
  static native Class testGetObjectClass(Object obj);

  /**
   * constructor
   */
  public ClassQuery() {
    toTestConstructor = 2;
  }


  public static void main(String[] args) {

    int returnValue;
    boolean returnFlag;
    Class supercls = null;
    Class subcls = null;
    Class returncls = null;

    System.loadLibrary("ClassQuery");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    /***********************************************
     * get the super class
     */
    try {
      subcls = Class.forName("MethodInvocationSub");
      supercls = Class.forName("MethodInvocation");
      returnValue = 0;
    } catch (ClassNotFoundException e) {
      returnValue = 1;
    }

    returncls = testSuperClass(subcls);
    checkTest(returnValue, (supercls==returncls), "GetSuperclass");


    /***********************************************
     * check if type is assignable
     */
    try {
      returnValue = 0;     // used to summarize all the tests for IsAssignableFrom
      // case 1:  assignable class
      subcls = Class.forName("ClassQuery");
      supercls = Class.forName("ClassQuerySuper");
      returnFlag = testAssignable(subcls, supercls);
      if (!returnFlag)             // should be true
        returnValue = 1;

      // case 2:  non-assignable class
      subcls = Class.forName("java.lang.String");
      returnFlag = testAssignable(subcls, supercls);
      if (returnFlag)             // should be false
        returnValue = 1;

      // case 3:  assignable primitive array
      int[] ary1 = new int[10];
      int[] ary2 = new int[10];
      subcls = ary1.getClass();
      supercls = ary2.getClass();
      returnFlag = testAssignable(subcls, supercls);
      if (!returnFlag)             // should be true
        returnValue = 1;

      // case 4:  non-assignable primitive array
      boolean[] bary2 = new boolean[10];
      supercls = bary2.getClass();
      returnFlag = testAssignable(subcls, supercls);
      if (returnFlag)             // should be false
        returnValue = 1;

      // case 5:  assignable object array
      String[] str1 = new String[7];
      String[] str2 = new String[7];
      subcls = str1.getClass();
      supercls = str2.getClass();
      returnFlag = testAssignable(subcls, supercls);
      if (!returnFlag)             // should be true
        returnValue = 1;

      // case 6:  non-assignable object array
      ClassQuery[] ObjArray = new ClassQuery[7];
      supercls = ObjArray.getClass();
      returnFlag = testAssignable(subcls, supercls);
      if (returnFlag)             // should be false
        returnValue = 1;


    } catch (ClassNotFoundException e) {
      returnValue = 1;
    }

    checkTest(returnValue, true, "IsAssignableFrom");


    /***********************************************
     * check for same object
     */
    String obj1 = new String("Thing one");
    String obj2 = new String("Thing two");
    String obj3 = obj1;
    returnFlag = testSameObject(obj1, obj2);
    if (returnFlag)             // should be false
      returnValue = 1;
    returnFlag = testSameObject(obj1, obj3);
    if (!returnFlag)             // should be true
      returnValue = 1;
    checkTest(returnValue, true, "IsSameObject");


    /***********************************************
     * test creating object without executing the constructor
     */
    try {
      subcls = Class.forName("ClassQuery");
      ClassQuery blankObj = (ClassQuery) testAllocObject(subcls);
      if (blankObj.toTestConstructor==2)    // shouldn't have been initialized
        returnValue = 1;
      else
        returnValue = 0;
      // blankObj = new ClassQuery();
      // System.out.println("the field is " + blankObj.toTestConstructor);
    } catch (ClassNotFoundException e) {
      returnValue = 1;
    }
    checkTest(returnValue, true, "AllocObject");


    /***********************************************
     * get the object class
     */
    try {
      returncls = testGetObjectClass(obj1);
      subcls = Class.forName("java.lang.String");
      checkTest(0, (returncls==subcls), "GetObjectClass");
    } catch (ClassNotFoundException e) {
      checkTest(0, false, "GetObjectClass");
    }


    /***********************************************
     * summarize
     */
    if (allTestPass)
      System.out.println("PASS: ClassQuery");
    else
      System.out.println("FAIL: ClassQuery");

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
