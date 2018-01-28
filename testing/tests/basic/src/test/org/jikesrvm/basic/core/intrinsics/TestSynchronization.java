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
package test.org.jikesrvm.basic.core.intrinsics;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.util.Services;
import org.vmmagic.pragma.NonMovingAllocation;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Provides a few test cases for methods in {@link Synchronization}.
 * The test is currently incomplete: it only tests tryCompareAndSwap(*)
 * methods.
 * <p>
 * Note: this test case only tests basic operation, it does NOT test
 * concurrent access.
 */
public class TestSynchronization {

  private static boolean success = true;

  private static class ClassWithObjectField {
    public Object obj;
  }

  private static class ClassWithObjectArrayField {
    public Object[] objArray;
  }

  private static class ClassWithIntField {
    public int intVal;
  }

  private static class ClassWithLongField {
    public long longVal;
  }

  private static class ClassWithAddressField {
    public Address addr;
  }

  private static class ClassWithWordField {
    public Word word;
  }


  /** Note: can't use SUCCESS because that's for overall success */
  private static final String TEST_SUCCESSFUL = " (OK)";

  public static void main(String[] args) throws SecurityException, NoSuchFieldException {
    testTryCompareAndSwapInt();
    testTryCompareAndSwapLong();
    testTryCompareAndSwapWord();
    testTryCompareAndSwapAddress();
    testTryCompareAndSwapObject();
    if (success) {
      System.out.println("ALL TESTS PASSED");
    } else {
      System.out.println("FAILURE");
    }
  }

  private static void testTryCompareAndSwapInt() throws SecurityException, NoSuchFieldException {
    System.out.println("--- testTryCompareAndSwapInt ----");

    ClassWithIntField to = createClassWithIntField();
    Offset off = java.lang.reflect.JikesRVMSupport.getFieldOf((ClassWithIntField.class.getField("intVal"))).getOffset();
    int firstInt = Integer.MIN_VALUE;

    boolean swap = Synchronization.tryCompareAndSwap(to, off, 0, firstInt);
    intTest("Synchronization.tryCompareAndSwap(to, off, 0, firstInt)", to.intVal, firstInt);
    booleanTest("return value", swap, true);

    to.intVal = firstInt;
    int secondInt = Integer.MAX_VALUE;
    swap = Synchronization.tryCompareAndSwap(to, off, firstInt, secondInt);
    intTest("Synchronization.tryCompareAndSwap(to, off, firstInt, secondInt)", to.intVal, secondInt);
    booleanTest("return value", swap, true);

    to.intVal = secondInt;
    swap = Synchronization.tryCompareAndSwap(to, off, secondInt, firstInt);
    intTest("Synchronization.tryCompareAndSwap(to, off, secondInt, firstInt)", to.intVal, firstInt);
    booleanTest("return value", swap, true);

    to.intVal = secondInt;
    swap = Synchronization.tryCompareAndSwap(to, off, secondInt, secondInt);
    intTest("Synchronization.tryCompareAndSwap(to, off, secondInt, secondInt)", to.intVal, secondInt);
    booleanTest("return value", swap, true);

    to.intVal = firstInt;
    swap = Synchronization.tryCompareAndSwap(to, off, secondInt, firstInt);
    intTest("Synchronization.tryCompareAndSwap(to, off, secondInt, firstInt)", to.intVal, firstInt);
    booleanTest("return value", swap, false);
  }

  private static void testTryCompareAndSwapLong() throws SecurityException, NoSuchFieldException {
    System.out.println("--- testTryCompareAndSwapLong ----");

    ClassWithLongField to = createClassWithLongField();
    Offset off = java.lang.reflect.JikesRVMSupport.getFieldOf((ClassWithLongField.class.getField("longVal"))).getOffset();
    long firstLong = Long.MIN_VALUE;

    boolean swap = Synchronization.tryCompareAndSwap(to, off, 0, firstLong);
    longTest("Synchronization.tryCompareAndSwap(to, off, 0, firstLong)", to.longVal, firstLong);
    booleanTest("return value", swap, true);

    to.longVal = firstLong;
    long secondLong = Long.MAX_VALUE;
    swap = Synchronization.tryCompareAndSwap(to, off, firstLong, secondLong);
    longTest("Synchronization.tryCompareAndSwap(to, off, firstLong, secondLong)", to.longVal, secondLong);
    booleanTest("return value", swap, true);

    to.longVal = secondLong;
    swap = Synchronization.tryCompareAndSwap(to, off, secondLong, firstLong);
    longTest("Synchronization.tryCompareAndSwap(to, off, secondLong, firstLong)", to.longVal, firstLong);
    booleanTest("return value", swap, true);

    to.longVal = secondLong;
    swap = Synchronization.tryCompareAndSwap(to, off, secondLong, secondLong);
    longTest("Synchronization.tryCompareAndSwap(to, off, secondLong, secondLong)", to.longVal, secondLong);
    booleanTest("return value", swap, true);

    to.longVal = firstLong;
    swap = Synchronization.tryCompareAndSwap(to, off, secondLong, firstLong);
    longTest("Synchronization.tryCompareAndSwap(to, off, secondLong, firstLong)", to.longVal, firstLong);
    booleanTest("return value", swap, false);
  }

  private static void testTryCompareAndSwapWord() throws SecurityException, NoSuchFieldException {
    System.out.println("--- testTryCompareAndSwapWord ----");

    ClassWithWordField to = createClassWithWordField();
    Offset off = java.lang.reflect.JikesRVMSupport.getFieldOf((ClassWithWordField.class.getField("word"))).getOffset();
    Word firstWord = VM.BuildFor64Addr ? Word.fromLong(Long.MIN_VALUE) : Word.fromIntSignExtend(Integer.MIN_VALUE);

    boolean swap = Synchronization.tryCompareAndSwap(to, off, Word.zero(), firstWord);
    wordTest("Synchronization.tryCompareAndSwap(to, off, Word.zero(), firstWord)", to.word, firstWord);
    booleanTest("return value", swap, true);

    to.word = firstWord;
    Word secondWord = Word.max();
    swap = Synchronization.tryCompareAndSwap(to, off, firstWord, secondWord);
    wordTest("Synchronization.tryCompareAndSwap(to, off, firstWord, secondWord)", to.word, secondWord);
    booleanTest("return value", swap, true);

    to.word = secondWord;
    swap = Synchronization.tryCompareAndSwap(to, off, secondWord, firstWord);
    wordTest("Synchronization.tryCompareAndSwap(to, off, secondWord, firstWord)", to.word, firstWord);
    booleanTest("return value", swap, true);

    to.word = secondWord;
    swap = Synchronization.tryCompareAndSwap(to, off, secondWord, secondWord);
    wordTest("Synchronization.tryCompareAndSwap(to, off, secondWord, secondWord)", to.word, secondWord);
    booleanTest("return value", swap, true);

    to.word = firstWord;
    swap = Synchronization.tryCompareAndSwap(to, off, secondWord, firstWord);
    wordTest("Synchronization.tryCompareAndSwap(to, off, secondWord, firstWord)", to.word, firstWord);
    booleanTest("return value", swap, false);
  }

  private static void testTryCompareAndSwapAddress() throws SecurityException, NoSuchFieldException {
    System.out.println("--- testTryCompareAndSwapAddress ----");

    ClassWithAddressField to = createClassWithAddressField();
    Offset off = java.lang.reflect.JikesRVMSupport.getFieldOf((ClassWithAddressField.class.getField("addr"))).getOffset();
    Address firstAddress = VM.BuildFor64Addr ? Address.fromLong(Long.MIN_VALUE) : Address.fromIntSignExtend(Integer.MIN_VALUE);

    boolean swap = Synchronization.tryCompareAndSwap(to, off, Address.zero(), firstAddress);
    addressTest("Synchronization.tryCompareAndSwap(to, off, Address.zero(), firstAddress)", to.addr, firstAddress);
    booleanTest("return value", swap, true);

    to.addr = firstAddress;
    Address secondAddress = Address.max();
    swap = Synchronization.tryCompareAndSwap(to, off, firstAddress, secondAddress);
    addressTest("Synchronization.tryCompareAndSwap(to, off, firstAddress, secondAddress)", to.addr, secondAddress);
    booleanTest("return value", swap, true);

    to.addr = secondAddress;
    swap = Synchronization.tryCompareAndSwap(to, off, secondAddress, firstAddress);
    addressTest("Synchronization.tryCompareAndSwap(to, off, secondAddress, firstAddress)", to.addr, firstAddress);
    booleanTest("return value", swap, true);

    to.addr = secondAddress;
    swap = Synchronization.tryCompareAndSwap(to, off, secondAddress, secondAddress);
    addressTest("Synchronization.tryCompareAndSwap(to, off, secondAddress, secondAddress)", to.addr, secondAddress);
    booleanTest("return value", swap, true);

    to.addr = firstAddress;
    swap = Synchronization.tryCompareAndSwap(to, off, secondAddress, firstAddress);
    addressTest("Synchronization.tryCompareAndSwap(to, off, secondAddress, firstAddress)", to.addr, firstAddress);
    booleanTest("return value", swap, false);
  }

  private static void testTryCompareAndSwapObject() throws SecurityException, NoSuchFieldException {
    System.out.println("--- testTryCompareAndSwapObject (object field) ----");

    ClassWithObjectField to = createClassWithObjectField();
    Offset off = java.lang.reflect.JikesRVMSupport.getFieldOf((ClassWithObjectField.class.getField("obj"))).getOffset();
    Object firstObj = createNewObject();

    boolean swap = Synchronization.tryCompareAndSwap(to, off, null, firstObj);
    objectTest("Synchronization.tryCompareAndSwap(to, off, null, firstObj)", to.obj, firstObj);
    booleanTest("return value", swap, true);

    to.obj = firstObj;
    Object secondObj = createNewObject();
    swap = Synchronization.tryCompareAndSwap(to, off, firstObj, secondObj);
    objectTest("Synchronization.tryCompareAndSwap(to, off, firstObj, secondObj)", to.obj, secondObj);
    booleanTest("return value", swap, true);

    to.obj = secondObj;
    swap = Synchronization.tryCompareAndSwap(to, off, secondObj, null);
    objectTest("Synchronization.tryCompareAndSwap(to, off, secondObj, null)", to.obj, null);
    booleanTest("return value", swap, true);

    to.obj = null;
    swap = Synchronization.tryCompareAndSwap(to, off, (Object)null, null);
    objectTest("Synchronization.tryCompareAndSwap(to, off, (Object)null, null)", to.obj, null);
    booleanTest("return value", swap, true);

    to.obj = firstObj;
    swap = Synchronization.tryCompareAndSwap(to, off, secondObj, firstObj);
    objectTest("Synchronization.tryCompareAndSwap(to, off, secondObj, firstObj)", to.obj, firstObj);
    booleanTest("return value", swap, false);

    System.out.println("--- testTryCompareAndSwapObject (object array element) ----");

    ClassWithObjectArrayField to2 = createClassWithObjectArrayField();
    to2.objArray = createNewObjectArray();
    Offset elementOffset = Offset.zero();

    boolean swapElement = Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, null, firstObj);
    objectTest("Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, null, firstObj)", to2.objArray[0], firstObj);
    booleanTest("return value", swapElement, true);

    to2.objArray[0] = firstObj;
    swapElement = Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, firstObj, secondObj);
    objectTest("Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, firstObj, secondObj)", to2.objArray[0], secondObj);
    booleanTest("return value", swapElement, true);

    to2.objArray[0] = secondObj;
    swapElement = Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, secondObj, null);
    objectTest("Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, secondObj, null)", to2.objArray[0], null);
    booleanTest("return value", swapElement, true);

    to2.objArray[0] = null;
    swapElement = Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, (Object)null, null);
    objectTest("Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, (Object)null, null)", to2.objArray[0], null);
    booleanTest("return value", swapElement, true);

    to2.objArray[0] = firstObj;
    swapElement = Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, secondObj, firstObj);
    objectTest("Synchronization.tryCompareAndSwap(to2.objArray, elementOffset, secondObj, firstObj)", to2.objArray[0], firstObj);
    booleanTest("return value", swapElement, false);
  }

  @NonMovingAllocation
  private static Object createNewObject() {
    return new Object();
  }

  @NonMovingAllocation
  private static Object[] createNewObjectArray() {
    return new Object[1];
  }

  @NonMovingAllocation
  public static ClassWithObjectField createClassWithObjectField() {
    return new ClassWithObjectField();
  }

  @NonMovingAllocation
  public static ClassWithObjectArrayField createClassWithObjectArrayField() {
    return new ClassWithObjectArrayField();
  }

  @NonMovingAllocation
  public static ClassWithIntField createClassWithIntField() {
    return new ClassWithIntField();
  }


  @NonMovingAllocation
  public static ClassWithLongField createClassWithLongField() {
    return new ClassWithLongField();
  }


  @NonMovingAllocation
  public static ClassWithAddressField createClassWithAddressField() {
    return new ClassWithAddressField();
  }


  @NonMovingAllocation
  public static ClassWithWordField createClassWithWordField() {
    return new ClassWithWordField();
  }

  private static void booleanTest(String msg, boolean value, boolean expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void intTest(String msg, int value, int expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void longTest(String msg, long value, long expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void wordTest(String msg, Word value, Word expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(Services.unboxedValueString(value));
    if (value.NE(expected)) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + Services.unboxedValueString(expected));
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void addressTest(String msg, Address value, Address expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(Services.unboxedValueString(value));
    if (value.NE(expected)) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + Services.unboxedValueString(expected));
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

  private static void objectTest(String msg, Object value, Object expected) {
    System.out.print(msg);
    System.out.print(": ");
    System.out.print(value);
    if (value != expected) {
      success = false;
      System.out.println(" (FAILED) Expected was: " + expected);
    } else {
      System.out.println(TEST_SUCCESSFUL);
    }
  }

}
