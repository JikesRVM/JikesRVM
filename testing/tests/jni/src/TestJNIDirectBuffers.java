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

import java.nio.ByteBuffer;

public class TestJNIDirectBuffers {

  // set to true to get messages for each test
  static boolean verbose = true;
  static boolean allTestPass = true;

  public static void main(String[] args) throws Exception {
    System.loadLibrary("TestJNIDirectBuffers");

    if (args.length!=0) {
      if (args[0].equals("-quiet")) {
        verbose = false;
        setVerboseOff();
      }
    }
    int returnValue;

    ByteBuffer buffer = ByteBuffer.allocateDirect(1);

    returnValue = testBuffer(buffer);
    checkTest(returnValue, true, "testBuffer -- A");

    long address = getStaticNativeAddress();
    long capacity = getStaticNativeCapacity();
    ByteBuffer native_buffer = newByteBuffer(address, capacity);
    if (capacity != native_buffer.capacity()) {
      printVerbose("Wrong capacity: " + capacity + " != " + native_buffer.capacity());
      checkTest(0, false, "CheckCapacity");
    }
    long buffer_address = getAddress(native_buffer);
    if (address != buffer_address) {
      printVerbose("Wrong address: " + address + " != " + buffer_address);
      checkTest(0, false, "CheckAddress");
    }

    returnValue = testBuffer(native_buffer);
    checkTest(returnValue, true, "testBuffer -- B");

    returnValue = testBuffer2(native_buffer);
    checkTest(returnValue, true, "testBuffer2");

    if (allTestPass) {
      System.out.println("PASS: TestJNIDirectBuffers");
    } else {
      System.out.println("FAIL: TestJNIDirectBuffers");
    }
  }

  private static int testBuffer(ByteBuffer buffer) {
    byte MAGIC_BYTE = (byte)0xde;
    buffer.put(0, MAGIC_BYTE);
    byte b = getByte(buffer, 0);
    if (b != MAGIC_BYTE) {
      printVerbose("Failed to get correct byte from native side: " + b + " != " + MAGIC_BYTE);
      return 1;
    } else {
      return 0;
    }
  }

  private static int testBuffer2(ByteBuffer buffer) {
    byte MAGIC_BYTE = (byte)0xad;
    putByte(buffer, 0, MAGIC_BYTE);
    byte b = buffer.get(0);
    if (b != MAGIC_BYTE) {
      printVerbose("Failed to put correct byte from native side: " + b + " != " + MAGIC_BYTE);
      return 1;
    } else {
      return 0;
    }
  }


  private static native byte getByte(ByteBuffer buffer, int index);
  private static native void putByte(ByteBuffer buffer, int index, byte b);
  private static native long getAddress(ByteBuffer buffer);
  private static native long getStaticNativeAddress();
  private static native long getStaticNativeCapacity();
  private static native ByteBuffer newByteBuffer(long address, long capacity);

  public static native void setVerboseOff();

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
