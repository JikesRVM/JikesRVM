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
package test.org.jikesrvm.basic.java.lang;

/**
 * Tests for java.lang.System.<p>
 *
 * Currently only covers {@link System#arraycopy(Object, int, Object, int, int)}.
 * <p>
 * Overflow of {@code srcPos + length} and {@code dstPos + length} is by design
 * not tested. Currently, the bootimage runner sets a maximum heap size if none is
 * given. That heap size is not enough for allocation of arrays in the order of
 * magnitude that would be required for a realistic test.
 **/
public class TestSystem {

  // Note that the test cases for exception throwing reuse arrays. A failure
  // in such a test may cause the array to be modified which might fail later
  // exception throwing tests.

  public static void main(String[] args) {
    checkSystemArrayCopy();
  }

  private static void checkSystemArrayCopy() {
    checkEasyErrorCases();
    checkArrayModificationIsPartialWhenExpected();
    checkBooleanArrays();
    checkByteArrays();
    checkCharArrays();
    checkDoubleArrays();
    checkFloatArrays();
    checkIntArrays();
    checkLongArrays();
    checkShortArrays();
    checkObjectArrays();
  }

  private static void checkEasyErrorCases() {
    final Object[] objects = { new Object(), new Object()};

    System.out.println("Checking for easy error cases.");
    System.out.println("Checking that NullPointerException (NPE) is thrown when expected.");
    System.out.println("src is null");
    try {
      System.arraycopy(null, 0, objects, 0, 1);
      printFailure();
    } catch (NullPointerException e) {
      printSuccess();
    }
    System.out.println("dest is null");
    try {
      System.arraycopy(objects, 0, null, 0, 1);
      printFailure();
    } catch (NullPointerException e) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("src is not an array");
    try {
      System.arraycopy(new Object(), 0, objects, 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
    System.out.println("dst is not an array");
    try {
      System.arraycopy(objects, 0, new Object(), 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
    System.out.println("primitive component types differ (using int and long)");
    final int[] intArray = {1, 2, 3};
    final long[] longArray = {4, 5, 6};
    try {
      System.arraycopy(intArray, 0, longArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
    System.out.println("src is reference and dst is primitive (int)");
    try {
      System.arraycopy(objects, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
    System.out.println("src is primitive (int) and dst is reference");
    try {
      System.arraycopy(intArray, 0, objects, 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
    final String[] stringArray = {"a", "b", "c"};
    System.out.println("src (String) and dst (Integer) have incompatible reference types");
    try {
      System.arraycopy(stringArray, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException e) {
      printSuccess();
    }
  }

  private static void checkArrayModificationIsPartialWhenExpected() {
    System.out.println("Checking that reference array is partially copied when ArrayStoreException is thrown while copying");
    Object[] firstArray = { Integer.valueOf(1), Integer.valueOf(2), "Invalid", Integer.valueOf(4)};
    Integer[] newIntArray = new Integer[4];
    newIntArray[2] = Integer.valueOf(20);
    newIntArray[3] = Integer.valueOf(40);
    try {
      System.arraycopy(firstArray, 0, newIntArray, 0, firstArray.length);
    } catch (ArrayStoreException ase) {
      boolean matchesExpectations = newIntArray[0].equals(Integer.valueOf(1)) &&
          newIntArray[1].equals(Integer.valueOf(2)) && newIntArray[2].equals(Integer.valueOf(20)) &&
          newIntArray[3].equals(Integer.valueOf(40));
      if (!matchesExpectations) {
        printFailure();
      } else {
        printSuccess();
      }
    }
  }

  private static void checkBooleanArrays() {
    System.out.println("Checking boolean[] arrays");
    final boolean[] booleanArray = {true, false, true, false };
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(booleanArray);
    checkDestPosIsNegative(booleanArray);
    checkLengthIsNegative(booleanArray);
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(booleanArray, 0, booleanArray, booleanArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from boolean array to int array");
    final int[] intArray = {1, 2, 3};
    try {
      System.arraycopy(booleanArray, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(booleanArray, 1, booleanArray, 0, 2);
    boolean copyDoesNotOverwriteGL = booleanArray[0] == false && booleanArray[1] == true &&
        booleanArray[2] == true && booleanArray[3] == false;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final boolean[] booleanArrayCopy = {true, false, true, false };
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(booleanArrayCopy, 0, booleanArrayCopy, 1, 2);
    boolean copyDoesNotOverwriteLG = booleanArrayCopy[0] == true && booleanArrayCopy[1] == true &&
        booleanArrayCopy[2] == false && booleanArrayCopy[3] == false;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkByteArrays() {
    System.out.println("Checking byte[] arrays");
    final byte[] byteArray = {-127, 0, 127, 1, 0, -1};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(byteArray);
    checkDestPosIsNegative(byteArray);
    checkLengthIsNegative(byteArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(byteArray, byteArray.length, byteArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(byteArray, 0, byteArray, byteArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from byte array to int array");
    final int[] intArray = {1, 2, 3};
    try {
      System.arraycopy(byteArray, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(byteArray, 1, byteArray, 0, 4);
    boolean copyDoesNotOverwriteGL = byteArray[0] == 0 && byteArray[1] == 127 &&
        byteArray[2] == 1 && byteArray[3] == 0 && byteArray[4] == 0 &&
        byteArray[5] == -1;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final byte[] byteArrayCopy = {-127, 0, 127, 1, 0, -1};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(byteArrayCopy, 0, byteArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = byteArrayCopy[0] == -127 && byteArrayCopy[1] == -127 &&
        byteArrayCopy[2] == 0 && byteArrayCopy[3] == 127 && byteArrayCopy[4] == 1 &&
        byteArrayCopy[5] == -1;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkCharArrays() {
    System.out.println("Checking char[] arrays");
    final byte[] charArray = {'&', 'a', '\r', 'z', '0', '*'};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(charArray);
    checkDestPosIsNegative(charArray);
    checkLengthIsNegative(charArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(charArray, charArray.length, charArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(charArray, 0, charArray, charArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from char array to int array");
    final int[] intArray = {1, 2, 3};
    try {
      System.arraycopy(charArray, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(charArray, 1, charArray, 0, 4);
    boolean copyDoesNotOverwrite = charArray[0] == 'a' && charArray[1] == '\r' &&
        charArray[2] == 'z' && charArray[3] == '0' && charArray[4] == '0' &&
        charArray[5] == '*';
    if (copyDoesNotOverwrite) {
      printSuccess();
    } else {
      printFailure();
    }
    final char[] charArrayCopy = {'&', 'a', '\r', 'z', '0', '*'};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(charArrayCopy, 0, charArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = charArrayCopy[0] == '&' && charArrayCopy[1] == '&' &&
        charArrayCopy[2] == 'a' && charArrayCopy[3] == '\r' && charArrayCopy[4] == 'z' &&
        charArrayCopy[5] == '*';
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkDoubleArrays() {
    System.out.println("Checking double[] arrays");
    final double[] doubleArray = {0.01d, 1245d, 2.45E06d, -1d, Double.MIN_VALUE, Double.MAX_VALUE};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(doubleArray);
    checkDestPosIsNegative(doubleArray);
    checkLengthIsNegative(doubleArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(doubleArray, doubleArray.length, doubleArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(doubleArray, 0, doubleArray, doubleArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from double array to long array");
    final long[] longArray = {1L,-2L, 3L};
    try {
      System.arraycopy(doubleArray, 0, longArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(doubleArray, 1, doubleArray, 0, 4);
    boolean copyDoesNotOverwriteGL = doubleArray[0] == 1245d && doubleArray[1] == 2.45E06d &&
        doubleArray[2] == -1d && doubleArray[3] == Double.MIN_VALUE && doubleArray[4] == Double.MIN_VALUE &&
        doubleArray[5] == Double.MAX_VALUE;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final double[] doubleArrayCopy = {0.01d, 1245d, 2.45E06d, -1d, Double.MIN_VALUE, Double.MAX_VALUE};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(doubleArrayCopy, 0, doubleArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = doubleArrayCopy[0] == 0.01d && doubleArrayCopy[1] == 0.01d &&
        doubleArrayCopy[2] == 1245d && doubleArrayCopy[3] == 2.45E06d && doubleArrayCopy[4] == -1d &&
        doubleArrayCopy[5] == Double.MAX_VALUE;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkFloatArrays() {
    System.out.println("Checking float[] arrays");
    final float[] floatArray = {0.01f, 1245f, 2.45E06f, -1f, Float.MIN_VALUE, Float.MAX_VALUE};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(floatArray);
    checkDestPosIsNegative(floatArray);
    checkLengthIsNegative(floatArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(floatArray, floatArray.length, floatArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(floatArray, 0, floatArray, floatArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from float array to double array");
    final double[] doubleArray = {1d,-2d, 3d};
    try {
      System.arraycopy(floatArray, 0, doubleArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(floatArray, 1, floatArray, 0, 4);
    boolean copyDoesNotOverwriteGL = floatArray[0] == 1245f && floatArray[1] == 2.45E06f &&
        floatArray[2] == -1f && floatArray[3] == Float.MIN_VALUE && floatArray[4] == Float.MIN_VALUE &&
        floatArray[5] == Float.MAX_VALUE;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final float[] floatArrayCopy = {0.01f, 1245f, 2.45E06f, -1f, Float.MIN_VALUE, Float.MAX_VALUE};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(floatArrayCopy, 0, floatArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = floatArrayCopy[0] == 0.01f && floatArrayCopy[1] == 0.01f &&
        floatArrayCopy[2] == 1245f && floatArrayCopy[3] == 2.45E06f && floatArrayCopy[4] == -1f &&
        floatArrayCopy[5] == Float.MAX_VALUE;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkIntArrays() {
    System.out.println("Checking int[] arrays");
    final int[] intArray = {-Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE-1, Integer.MAX_VALUE};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(intArray);
    checkDestPosIsNegative(intArray);
    checkLengthIsNegative(intArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(intArray, intArray.length, intArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(intArray, 0, intArray, intArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from int array to long array");
    final long[] longArray = {1L, 2L, 3L};
    try {
      System.arraycopy(intArray, 0, longArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(intArray, 1, intArray, 0, 4);
    boolean copyDoesNotOverwrite = intArray[0] == -1 && intArray[1] == 0 &&
        intArray[2] == 1 && intArray[3] == Integer.MAX_VALUE-1 && intArray[4] == Integer.MAX_VALUE-1 &&
        intArray[5] == Integer.MAX_VALUE;
    if (copyDoesNotOverwrite) {
      printSuccess();
    } else {
      printFailure();
    }
    final int[] intArrayCopy = {-Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE-1, Integer.MAX_VALUE};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(intArrayCopy, 0, intArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = intArrayCopy[0] == Integer.MIN_VALUE && intArrayCopy[1] == Integer.MIN_VALUE &&
        intArrayCopy[2] == -1 && intArrayCopy[3] == 0 && intArrayCopy[4] == 1 &&
        intArrayCopy[5] == Integer.MAX_VALUE;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkLongArrays() {
    final long[] longArray = {-Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE-1, Long.MAX_VALUE};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(longArray);
    checkDestPosIsNegative(longArray);
    checkLengthIsNegative(longArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(longArray, longArray.length, longArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(longArray, 0, longArray, longArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from long array to double array");
    final double[] doubleArray = {1d, 2d, 3d};
    try {
      System.arraycopy(longArray, 0, doubleArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(longArray, 1, longArray, 0, 4);
    boolean copyDoesNotOverwrite = longArray[0] == -1L && longArray[1] == 0L &&
        longArray[2] == 1L && longArray[3] == Long.MAX_VALUE-1 && longArray[4] == Long.MAX_VALUE-1 &&
        longArray[5] == Long.MAX_VALUE;
    if (copyDoesNotOverwrite) {
      printSuccess();
    } else {
      printFailure();
    }
    final long[] longArrayCopy = {-Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE-1, Long.MAX_VALUE};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(longArrayCopy, 0, longArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = longArrayCopy[0] == Long.MIN_VALUE && longArrayCopy[1] == Long.MIN_VALUE &&
        longArrayCopy[2] == -1 && longArrayCopy[3] == 0 && longArrayCopy[4] == 1 &&
        longArrayCopy[5] == Long.MAX_VALUE;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkShortArrays() {
    System.out.println("Checking short[] arrays");
    final short[] shortArray = {Short.MIN_VALUE, 0, Short.MAX_VALUE, 1, 0, -1};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(shortArray);
    checkDestPosIsNegative(shortArray);
    checkLengthIsNegative(shortArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(shortArray, shortArray.length, shortArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(shortArray, 0, shortArray, shortArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that ArrayStoreException is thrown when excpected.");
    System.out.println("Trying to copy from byte array to int array");
    final int[] intArray = {1, 2, 3};
    try {
      System.arraycopy(shortArray, 0, intArray, 0, 1);
      printFailure();
    } catch (ArrayStoreException ase) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(shortArray, 1, shortArray, 0, 4);
    boolean copyDoesNotOverwriteGL = shortArray[0] == 0 && shortArray[1] == Short.MAX_VALUE &&
        shortArray[2] == 1 && shortArray[3] == 0 && shortArray[4] == 0 &&
        shortArray[5] == -1;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final short[] shortArrayCopy = {Short.MIN_VALUE, 0, Short.MAX_VALUE, 1, 0, -1};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(shortArrayCopy, 0, shortArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = shortArrayCopy[0] == Short.MIN_VALUE && shortArrayCopy[1] == Short.MIN_VALUE &&
        shortArrayCopy[2] == 0 && shortArrayCopy[3] == Short.MAX_VALUE && shortArrayCopy[4] == 1 &&
        shortArrayCopy[5] == -1;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkObjectArrays() {
    System.out.println("Checking Object[] arrays");
    Object a = new Object();
    Object b = new Object();
    Object c = new Object();
    Object d = new Object();
    Object e = new Object();
    Object f = new Object();
    final Object[] objectArray = {a, b, c, d, e, f};
    System.out.println("Checking that IndexOutOfBoundsException is thrown when expected.");
    checkSrcPosIsNegative(objectArray);
    checkDestPosIsNegative(objectArray);
    checkLengthIsNegative(objectArray);
    System.out.println("srcPos+length > src.length");
    try {
      System.arraycopy(objectArray, objectArray.length, objectArray, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("dstPos+length > dst.length");
    try {
      System.arraycopy(objectArray, 0, objectArray, objectArray.length, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
    System.out.println("Checking that copying to the same array works with overlap (from greater position to lesser)");
    System.arraycopy(objectArray, 1, objectArray, 0, 4);
    boolean copyDoesNotOverwriteGL = objectArray[0] == b && objectArray[1] == c &&
        objectArray[2] == d && objectArray[3] == e && objectArray[4] == e &&
        objectArray[5] == f;
    if (copyDoesNotOverwriteGL) {
      printSuccess();
    } else {
      printFailure();
    }
    final Object[] objectArrayCopy = {a, b, c, d, e, f};
    System.out.println("Checking that copying to the same array works with overlap (from lesser position to greater)");
    System.arraycopy(objectArrayCopy, 0, objectArrayCopy, 1, 4);
    boolean copyDoesNotOverwriteLG = objectArrayCopy[0] == a && objectArrayCopy[1] == a &&
        objectArrayCopy[2] == b && objectArrayCopy[3] == c && objectArrayCopy[4] == d &&
        objectArrayCopy[5] == f;
    if (copyDoesNotOverwriteLG) {
      printSuccess();
    } else {
      printFailure();
    }
  }

  private static void checkSrcPosIsNegative(Object array) {
    System.out.println("srcPos is negative");
    try {
      System.arraycopy(array, -1, array, 0, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
  }

  private static void checkDestPosIsNegative(Object array) {
    System.out.println("destPos is negative");
    try {
      System.arraycopy(array, 0, array, -1, 1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
  }

  private static void checkLengthIsNegative(Object array) {
    System.out.println("length is negative");
    try {
      System.arraycopy(array, 0, array, 0, -1);
      printFailure();
    } catch (IndexOutOfBoundsException ioobe) {
      printSuccess();
    }
  }

  private static void printSuccess() {
    System.out.println("SUCCESS");
  }

  private static void printFailure() {
    System.out.println("FAILURE");
  }

}
