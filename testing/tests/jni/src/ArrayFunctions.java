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
class ArrayFunctions {
  static boolean verbose = true;         // set to true to get messages for each test
  static boolean allTestPass = true;

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


  public static native void setVerboseOff();

  /**
   * Declare native methods that will call the JNI Array Functions
   */
  static native int[]     accessNewIntArray(int length);
  static native boolean[] accessNewBooleanArray(int length);
  static native short[]   accessNewShortArray(int length);
  static native byte[]    accessNewByteArray(int length);
  static native char[]    accessNewCharArray(int length);
  static native long[]    accessNewLongArray(int length);
  static native double[]  accessNewDoubleArray(int length);
  static native float[]   accessNewFloatArray(int length);
  static native Object[]  accessNewObjectArray(int length, Class<?> cls, Object initElement);

  static native int[]     testIntArrayRegion(int[] sourceArray);
  static native boolean[] testBooleanArrayRegion(boolean[] sourceArray);
  static native short[]   testShortArrayRegion(short[] sourceArray);
  static native byte[]    testByteArrayRegion(byte[] sourceArray);
  static native char[]    testCharArrayRegion(char[] sourceArray);
  static native long[]    testLongArrayRegion(long[] sourceArray);
  static native double[]  testDoubleArrayRegion(double[] sourceArray);
  static native float[]   testFloatArrayRegion(float[] sourceArray);

  static native int[]     testIntArrayElements(int[] sourceArray, int testMode);
  static native boolean[] testBooleanArrayElements(boolean[] sourceArray, int testMode);
  static native short[]   testShortArrayElements(short[] sourceArray, int testMode);
  static native byte[]    testByteArrayElements(byte[] sourceArray, int testMode);
  static native char[]    testCharArrayElements(char[] sourceArray, int testMode);
  static native long[]    testLongArrayElements(long[] sourceArray, int testMode);
  static native double[]  testDoubleArrayElements(double[] sourceArray, int testMode);
  static native float[]   testFloatArrayElements(float[] sourceArray, int testMode);
  static native Object    testObjectArrayElement(Object[] sourceArray, Object toAssign, int index);

  static native int testArrayLength(int[] sourceArray);
  static native boolean lastGetArrayElementsWasCopy();

  /*******************************************************/
  public static boolean testObjectArray() {

    String[] objectArray = new String[10];

    for (int i=0; i<objectArray.length; i++)
      objectArray[i] = new String("object " + i);

    String toAssign = new String("yet another one");

    Object returnObject = testObjectArrayElement(objectArray, toAssign, 7);

    return ((String)returnObject).equals("object 7") && objectArray[7]==toAssign;
  }

  /*******************************************************/
  public static boolean testBooleanArray() {
    boolean arrayFlag = true;
    boolean updateSucceeded, wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<booleanArray.length; i++)
      booleanArray[i] = false;
    boolean [] returnArray = testBooleanArrayElements(booleanArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose) System.out.println("INFO: GetBooleanArrayElements "+
          (wasCopied ? "copied" : "did not copy")+" the array");
    updateSucceeded = (returnArray[0] && !returnArray[1] && returnArray[2] && !returnArray[3] &&
           returnArray[4] && !returnArray[5] && returnArray[6] && !returnArray[7] &&
           returnArray[8] && !returnArray[9]);
    if (verbose) System.out.println("INFO: update(1) "+(updateSucceeded ? "succeeded" : "failed"));
    arrayFlag &= updateSucceeded;

    // second part: update and release the copy
    for (int i=0; i<booleanArray.length; i++)
      booleanArray[i] = false;
    returnArray = testBooleanArrayElements(booleanArray, 1);
    updateSucceeded = (!returnArray[0] && returnArray[1] && !returnArray[2] && returnArray[3] &&
        !returnArray[4] && returnArray[5] && !returnArray[6] && returnArray[7] &&
        !returnArray[8] && returnArray[9]);
    // Should always succeed
    if (verbose) System.out.println("INFO: update(2) "+(updateSucceeded ? "succeeded" : "failed"));
    arrayFlag &= updateSucceeded;

    // third part: release the copy with no update
    for (int i=0; i<booleanArray.length; i++)
      booleanArray[i] = true;
    returnArray = testBooleanArrayElements(booleanArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    updateSucceeded = !(returnArray[0] && returnArray[1] && returnArray[2] && returnArray[3] &&
           returnArray[4] && returnArray[5] && returnArray[6] && returnArray[7] &&
           returnArray[8] && returnArray[9]);
    if (verbose) System.out.println("INFO: update(3) "+(updateSucceeded ? "succeeded" : "failed"));
    arrayFlag &= updateSucceeded != wasCopied;

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testByteArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<byteArray.length; i++)
      byteArray[i] = (byte) i;
    byte [] returnArray = testByteArrayElements(byteArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose) System.out.println("INFO: GetByteArrayElements "+
          (wasCopied ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnArray.length; i++) {
      //if (verbose) System.out.println(" first:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((byte) (i+4)))
        arrayFlag = false;
    }

    // second part: update and release the copy
    /* If the array is copied, this will have no effect */
    for (int i=0; i<byteArray.length; i++)
      byteArray[i] = (byte) i;
    returnArray = testByteArrayElements(byteArray, 1);
    for (int i=0; i<returnArray.length; i++) {
      //if (verbose) System.out.println(" second:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((byte) (i+(wasCopied ? 9 : 5))))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<byteArray.length; i++)
      byteArray[i] = (byte) i;
    returnArray = testByteArrayElements(byteArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      //if (verbose) System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=(byte) i + (wasCopied ? 0 : 6))
        arrayFlag = false;
    }

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testIntArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<intArray.length; i++)
      intArray[i] = i;
    int [] returnIntArray = testIntArrayElements(intArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose) System.out.println("INFO: GetIntArrayElements "+
          (wasCopied ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnIntArray.length; i++) {
      // System.out.println(" first:  " + i + " = " + returnIntArray[i]);
      if (returnIntArray[i]!=i+1)
        arrayFlag = false;
    }

    // second part: update and release the copy
    for (int i=0; i<intArray.length; i++)
      intArray[i] = i;
    returnIntArray = testIntArrayElements(intArray, 1);
    for (int i=0; i<returnIntArray.length; i++) {
      // System.out.println(" second:  " + i + " = " + returnIntArray[i]);
      if (returnIntArray[i]!=i+(wasCopied ? 3 : 2))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<intArray.length; i++)
      intArray[i] = i;
    returnIntArray = testIntArrayElements(intArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnIntArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnIntArray[i]);
      if (returnIntArray[i]!= i + (wasCopied ? 0 : 3))
        arrayFlag = false;
    }

    return arrayFlag;
  }


  /*******************************************************/
  public static boolean testShortArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<shortArray.length; i++)
      shortArray[i] = (short) i;
    short [] returnArray = testShortArrayElements(shortArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose)
      System.out.println("INFO: GetShortArrayElements "+
          (lastGetArrayElementsWasCopy() ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" first:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=i+7)
        arrayFlag = false;
    }

    // second part: update and release the copy
    for (int i=0; i<shortArray.length; i++)
      shortArray[i] = (short) i;
    returnArray = testShortArrayElements(shortArray, 1);
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" second:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=i+(wasCopied ? 15 : 8))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<shortArray.length; i++)
      shortArray[i] = (short) i;
    returnArray = testShortArrayElements(shortArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=i + (wasCopied ? 0 : 9))
        arrayFlag = false;
    }

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testCharArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<charArray.length; i++)
      charArray[i] = 'a';
    char [] returnArray = testCharArrayElements(charArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose)
      System.out.println("INFO: GetCharArrayElements "+
          (lastGetArrayElementsWasCopy() ? "copied" : "did not copy")+" the array");
    if (returnArray[0]!='a' || returnArray[1]!='b' || returnArray[2]!='c' || returnArray[3]!='d' ||
        returnArray[4]!='e' || returnArray[5]!='f' || returnArray[6]!='g' || returnArray[7]!='h' ||
        returnArray[8]!='i' || returnArray[9]!='j')
      arrayFlag = false;

    // second part: update and release the copy
    for (int i=0; i<charArray.length; i++)
      charArray[i] = 'b';
    returnArray = testCharArrayElements(charArray, 1);
    if (returnArray[0]!='j' || returnArray[1]!='a' || returnArray[2]!='l' || returnArray[3]!='e' ||
        returnArray[4]!='p' || returnArray[5]!='e' || returnArray[6]!='n' || returnArray[7]!='o' ||
        returnArray[8]!='v' || returnArray[9]!='m')
      arrayFlag = false;

    // third part: release the copy with no update
    for (int i=0; i<charArray.length; i++)
      charArray[i] = 'c';
    returnArray = testCharArrayElements(charArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!= (wasCopied ? 'c' : 'x'))
        arrayFlag = false;
    }

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testLongArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<longArray.length; i++)
      longArray[i] = (long) i;
    long [] returnArray = testLongArrayElements(longArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose)
      System.out.println("INFO: GetLongArrayElements "+
          (lastGetArrayElementsWasCopy() ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" first:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((long) i + 10))
        arrayFlag = false;
    }

    // second part: update and release the copy
    for (int i=0; i<longArray.length; i++)
      longArray[i] = (long) i;
    returnArray = testLongArrayElements(longArray, 1);
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" second:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((long) i + (wasCopied ? 21 : 11)))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<longArray.length; i++)
      longArray[i] = (long) i;
    returnArray = testLongArrayElements(longArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((long) i) + (wasCopied ? 0 : 12))
        arrayFlag = false;
    }

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testFloatArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<floatArray.length; i++)
      floatArray[i] = i;
    float [] returnArray = testFloatArrayElements(floatArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose)
      System.out.println("INFO: GetFloatArrayElements "+
          (lastGetArrayElementsWasCopy() ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" first:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((float)i + 16.0f))
        arrayFlag = false;
    }

    // second part: update and release the copy
    for (int i=0; i<floatArray.length; i++)
      floatArray[i] = i;
    returnArray = testFloatArrayElements(floatArray, 1);
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" second:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((float)i + (wasCopied ? 33.0f : 17.0f)))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<floatArray.length; i++)
      floatArray[i] = i;
    returnArray = testFloatArrayElements(floatArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=(float) i + (wasCopied ? 0f : 18f))
        arrayFlag = false;
    }

    return arrayFlag;

  }

  /*******************************************************/
  public static boolean testDoubleArray() {
    boolean arrayFlag = true;
    boolean wasCopied;

    // first part: update but don't release the copy
    for (int i=0; i<doubleArray.length; i++)
      doubleArray[i] = (double) i;
    double [] returnArray = testDoubleArrayElements(doubleArray, 0);
    wasCopied = lastGetArrayElementsWasCopy();
    if (verbose)
      System.out.println("INFO: GetDoubleArrayElements "+
          (lastGetArrayElementsWasCopy() ? "copied" : "did not copy")+" the array");
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" first:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((double) i + 13.0))
        arrayFlag = false;
    }

    // second part: update and release the copy
    for (int i=0; i<doubleArray.length; i++)
      doubleArray[i] = (double) i;
    returnArray = testDoubleArrayElements(doubleArray, 1);
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" second:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!=((double) i + (wasCopied ? 27.0 : 14.0)))
        arrayFlag = false;
    }

    // third part: release the copy with no update
    for (int i=0; i<shortArray.length; i++)
      doubleArray[i] = (double) i;
    returnArray = testDoubleArrayElements(doubleArray, 2);
    wasCopied = lastGetArrayElementsWasCopy();
    for (int i=0; i<returnArray.length; i++) {
      // System.out.println(" third:  " + i + " = " + returnArray[i]);
      if (returnArray[i]!= (double) i + (wasCopied ? 0.0 : 15.0))
        arrayFlag = false;
    }

    return arrayFlag;

  }



  /**
   * constructor
   */
  public ArrayFunctions() {
  }

  public static void main(String[] args) {

    int returnValue;
    Object returnObject;

    System.loadLibrary("ArrayFunctions");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }

    /**
     * Test GetArrayLength
     */
    returnValue = testArrayLength(intArray);
    checkTest(0, (returnValue==intArray.length), "GetArrayLength");


    /**
     * Test New<type>Array:  creating Java array from native
     */
    returnObject = accessNewIntArray(31);
    // printVerbose("accessNewIntArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((int[]) returnObject).length==31) && returnObject.getClass().getName().equals("[I"),
              "NewIntArray");

    returnObject = accessNewBooleanArray(31);
    // printVerbose("accessNewBooleanArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((boolean[]) returnObject).length==31) && returnObject.getClass().getName().equals("[Z"),
              "NewBooleanArray");

    returnObject = accessNewShortArray(31);
    // printVerbose("accessNewShortArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((short[]) returnObject).length==31) && returnObject.getClass().getName().equals("[S"),
              "NewShortArray");

    returnObject = accessNewByteArray(31);
    // printVerbose("accessNewByteArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((byte[]) returnObject).length==31) && returnObject.getClass().getName().equals("[B"),
              "NewByteArray");

    returnObject = accessNewCharArray(31);
    // printVerbose("accessNewCharArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((char[]) returnObject).length==31) && returnObject.getClass().getName().equals("[C"),
              "NewCharArray");

    returnObject = accessNewLongArray(31);
    // printVerbose("accessNewLongArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((long[]) returnObject).length==31) && returnObject.getClass().getName().equals("[J"),
              "NewLongArray");

    returnObject = accessNewFloatArray(31);
    // printVerbose("accessNewFloatArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((float[]) returnObject).length==31) && returnObject.getClass().getName().equals("[F"),
              "NewFloatArray");

    returnObject = accessNewDoubleArray(31);
    // printVerbose("accessNewDoubleArray returns: " + returnObject.getClass().getName());
    checkTest(0, (((double[]) returnObject).length==31) && returnObject.getClass().getName().equals("[D"),
              "NewDoubleArray");


    try {
      returnObject = accessNewObjectArray(31, Class.forName("java.lang.String"), null);
      // printVerbose("accessNewObjectArray returns: " + returnObject.getClass().getName());
      checkTest(0, (((Object[]) returnObject).length==31) &&
                returnObject.getClass().getName().equals("[Ljava.lang.String;"),
                "NewObjectArray");
    } catch (ClassNotFoundException e) {
      System.out.println("Cannot run accessNewObjectArray");
    }


    /**
     * Test Set/Get<type>ArrayRegion:  access to array section
     */
    int [] returnIntArray = testIntArrayRegion(intArray);
    boolean arrayFlag = true;
    for (int i=0; i<returnIntArray.length; i++) {
      if (returnIntArray[i]!=i+2)
        arrayFlag = false;
    }
    checkTest(0, arrayFlag, "Get/SetIntArrayRegion");


    boolean [] returnBooleanArray = testBooleanArrayRegion(booleanArray);
    arrayFlag = true;
    if (returnBooleanArray[0] || returnBooleanArray[1] ||
        returnBooleanArray[4] || returnBooleanArray[5] ||
        returnBooleanArray[8] || returnBooleanArray[9])
      arrayFlag = false;
    if (!returnBooleanArray[2] || !returnBooleanArray[3] ||
        !returnBooleanArray[6] || !returnBooleanArray[7])
      arrayFlag = false;
    checkTest(0, arrayFlag, "Get/SetBooleanArrayRegion");


    short[]  returnShortArray = testShortArrayRegion(shortArray);
    arrayFlag = true;
    for (int i=0; i<returnShortArray.length; i++) {
      if (returnShortArray[i]!=((i+1)*2))
        arrayFlag = false;
    }
    checkTest(0, arrayFlag, "Get/SetShortArrayRegion");


    byte[]   returnByteArray = testByteArrayRegion(byteArray);
    arrayFlag = true;
    for (int i=0; i<returnByteArray.length; i++) {
      if (returnByteArray[i]!=(i*2+3))
        arrayFlag = false;
    }
    checkTest(0, arrayFlag, "Get/SetByteArrayRegion");


    char[]   returnCharArray = testCharArrayRegion(charArray);
    arrayFlag = true;
    if (returnCharArray[0]!='j' ||
        returnCharArray[1]!='a' ||
        returnCharArray[2]!='l' ||
        returnCharArray[3]!='a' ||
        returnCharArray[4]!='p' ||
        returnCharArray[5]!='e' ||
        returnCharArray[6]!='n' ||
        returnCharArray[7]!='o' ||
        returnCharArray[8]!='v' ||
        returnCharArray[9]!='m')
      arrayFlag = false;
    checkTest(0, arrayFlag, "Get/SetCharArrayRegion");


    long[] returnLongArray = testLongArrayRegion(longArray);
    arrayFlag = true;
    for (int i=0; i<returnLongArray.length; i++) {
      if (returnLongArray[i]!=0x80001000+i) {
        printVerbose("Get/SetLongArrayRegion returns: " + i + " = " +
                     Integer.toHexString((int) returnLongArray[i]>>32) + "  " +
                     Integer.toHexString((int) returnLongArray[i]));
        arrayFlag = false;
      }
    }
    checkTest(0, arrayFlag, "Get/SetLongArrayRegion");


    double[] returnDoubleArray = testDoubleArrayRegion(doubleArray);
    arrayFlag = true;
    for (int i=0; i<returnDoubleArray.length; i++) {
      if (returnDoubleArray[i]!=(115.1 + i)) {
        printVerbose("Get/SetDoubleArrayRegion returns: " + i + " = " +
                     returnDoubleArray[i]);
        arrayFlag = false;
      }
    }
    checkTest(0, arrayFlag, "Get/SetDoubleArrayRegion");


    float[]  returnFloatArray = testFloatArrayRegion(floatArray);
    arrayFlag = true;
    for (int i=0; i<returnFloatArray.length; i++) {
      if (returnFloatArray[i]!=((float) 115.1 + (float) i)) {
        printVerbose("Get/SetFloatArrayRegion returns: " + i + " = " +
                     returnFloatArray[i]);
        arrayFlag = false;
      }
    }
    checkTest(0, arrayFlag, "Get/SetFloatArrayRegion");


    /**
     * Test Set/Get<type>ArrayElements: access to entire array
     */
    boolean rc = testIntArray();
    checkTest(0, rc, "Get/SetIntArrayElements");

    rc = testBooleanArray();
    checkTest(0, rc, "Get/SetBooleanArrayElements");

    rc = testByteArray();
    checkTest(0, rc, "Get/SetByteArrayElements");

    rc = testShortArray();
    checkTest(0, rc, "Get/SetShortArrayElements");

    rc = testCharArray();
    checkTest(0, rc, "Get/SetCharArrayElements");

    rc = testLongArray();
    checkTest(0, rc, "Get/SetLongArrayElements");

    rc = testFloatArray();
    checkTest(0, rc, "Get/SetFloatArrayElements");

    rc = testDoubleArray();
    checkTest(0, rc, "Get/SetDoubleArrayElements");

    rc = testObjectArray();
    checkTest(0, rc, "Get/SetObjectArrayElement");

    // Summarize

    if (allTestPass)
      System.out.println("PASS: ArrayFunctions");
    else
      System.out.println("FAIL: ArrayFunctions");

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
