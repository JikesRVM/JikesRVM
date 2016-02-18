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
package org.jikesrvm.jni;

import static org.jikesrvm.runtime.JavaSizeConstants.BITS_IN_BYTE;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_LONG;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.classloader.UTF8Convert;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Platform independent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see JNIFunctions
 */
public abstract class JNIGenericHelpers {

  /**
   *  Whether to allow reads of undefined memory when computing strlen(..).
   *  This is enabled by default to improve performance. It will never cause
   *  undefined behaviour but it may turn up as a false positive when using
   *  tools such as Valgrind.
   */
  private static final boolean ALLOW_READS_OF_UNDEFINED_MEMORY = true;

  /**
   * Computes the length of the given null-terminated string.
   * <p>
   * <strong>NOTE: This method may read undefined memory if {@link #ALLOW_READS_OF_UNDEFINED_MEMORY}
   * is true. However, the behaviour of this method is always well-defined.</strong>
   *
   * @param ptr address of string in memory
   * @return the length of the string in bytes
   */
  public static int strlen(Address ptr) {
    int length = 0;
    // Read words at a time for better performance. This should be unproblematic unless
    // you're using Valgrind or a similar tool to check for errors. Memory protection
    // can't be a problem because the reads will be aligned and mprotect works at the
    // page level (or even coarser).
    if (ALLOW_READS_OF_UNDEFINED_MEMORY) {
      // align address to size of machine
      while (!ptr.toWord().and(Word.fromIntZeroExtend(BYTES_IN_ADDRESS - 1)).isZero()) {
        byte bits = ptr.loadByte(Offset.fromIntZeroExtend(length));
        if (bits == 0) {
          return length;
        }
        length++;
      }
      // Ascii characters are normally in the range 1 to 128, if we subtract 1
      // from each byte and look if the top bit of the byte is set then if it is
      // the chances are the byte's value is 0. Loop over words doing this quick
      // test and then do byte by byte tests when we think we have the 0
      Word onesToSubtract;
      Word maskToTestHighBits;
      if (VM.BuildFor32Addr) {
        onesToSubtract     = Word.fromIntZeroExtend(0x01010101);
        maskToTestHighBits = Word.fromIntZeroExtend(0x80808080);
      } else {
        onesToSubtract     = Word.fromLong(0x0101010101010101L);
        maskToTestHighBits = Word.fromLong(0x8080808080808080L);
      }
      while (true) {
        Word bytes = ptr.loadWord(Offset.fromIntZeroExtend(length));
        if (!bytes.minus(onesToSubtract).and(maskToTestHighBits).isZero()) {
          if (VM.LittleEndian) {
            for (int byteOff = 0; byteOff < BYTES_IN_ADDRESS; byteOff++) {
              if (bytes.and(Word.fromIntZeroExtend(0xFF)).isZero()) {
                return length + byteOff;
              }
              bytes = bytes.rshl(BITS_IN_BYTE);
            }
          } else {
            for (int byteOff = BYTES_IN_ADDRESS - 1; byteOff >= 0; byteOff--) {
              if (bytes.rshl(byteOff * 8).and(Word.fromIntZeroExtend(0xFF)).isZero()) {
                return length + (BYTES_IN_ADDRESS - 1 - byteOff);
              }
            }
          }
        }
        length += BYTES_IN_ADDRESS;
      }
    } else {
      // Avoid reads of undefined memory by proceeding one byte at a time
      Address currentAddress = ptr;
      byte currentByte = currentAddress.loadByte(Offset.fromIntSignExtend(length));
      while (currentByte != 0) {
        length++;
        currentByte = currentAddress.loadByte(Offset.fromIntSignExtend(length));
      }
      return length;
    }
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  public static byte[] createByteArrayFromC(Address stringAddress) {

    int length = strlen(stringAddress);
    byte[] contents = new byte[length];
    Memory.memcopy(Magic.objectAsAddress(contents), stringAddress, length);

    return contents;
  }

  private static String createString(CharsetDecoder csd, ByteBuffer bbuf) throws CharacterCodingException {
    char[] v;
    int o;
    int c;
    CharBuffer cbuf = csd.decode(bbuf);
    if (cbuf.hasArray()) {
      v = cbuf.array();
      o = cbuf.position();
      c = cbuf.remaining();
    } else {
      // Doubt this will happen. But just in case.
      v = new char[cbuf.remaining()];
      cbuf.get(v);
      o = 0;
      c = v.length;
    }
    return java.lang.JikesRVMSupport.newStringWithoutCopy(v, o, c);
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java String with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  public static String createStringFromC(Address stringAddress) {
    if (VM.fullyBooted) {
      try {
        String encoding = System.getProperty("file.encoding");
        CharsetDecoder csd = Charset.forName(encoding).newDecoder();
        csd.onMalformedInput(CodingErrorAction.REPLACE);
        csd.onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer bbuf =
          java.nio.JikesRVMSupport.newDirectByteBuffer(stringAddress,
                                                       strlen(stringAddress));
        return createString(csd, bbuf);
      } catch (Exception ex) {
        // Any problems fall through to default encoding
      }
    }
    // Can't do real Char encoding until VM is fully booted.
    // All Strings encountered during booting must be ascii
    byte[] tmp = createByteArrayFromC(stringAddress);
    return StringUtilities.asciiBytesToString(tmp);
  }
  /**
   * Given an address in C that points to a null-terminated string,
   * create a new UTF encoded Java String with a copy of the string.
   *
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  public static String createUTFStringFromC(Address stringAddress) {
    final boolean USE_LIBRARY_CODEC = false;
    byte[] tmp;
    ByteBuffer bbuf;
    if (VM.fullyBooted) {
      try {
        bbuf = java.nio.JikesRVMSupport.newDirectByteBuffer(stringAddress,
                                                            strlen(stringAddress));
        if (USE_LIBRARY_CODEC) {
          CharsetDecoder csd = Charset.forName("UTF8").newDecoder();
          return createString(csd, bbuf);
        } else {
          return UTF8Convert.fromUTF8(bbuf);
        }
      } catch (Exception ex) {
        // Any problems fall through to default encoding
      }
    }
    // Can't do real Char encoding until VM is fully booted.
    // All Strings encountered during booting must be ascii
    tmp = createByteArrayFromC(stringAddress);
    return StringUtilities.asciiBytesToString(tmp);
  }


  /**
   * Converts a String into a a malloced regions.
   *
   * @param str the string to convert
   * @param copyBuffer start address for a newly allocated buffer
   * @param len length of the buffer
   */
  public static void createUTFForCFromString(String str, Address copyBuffer, int len) {
    ByteBuffer bbuf =
      java.nio.JikesRVMSupport.newDirectByteBuffer(copyBuffer, len);

    final boolean USE_LIBRARY_CODEC = false;
    if (USE_LIBRARY_CODEC) {
      char[] strChars = java.lang.JikesRVMSupport.getBackingCharArray(str);
      int strOffset = java.lang.JikesRVMSupport.getStringOffset(str);
      int strLen = java.lang.JikesRVMSupport.getStringLength(str);
      CharBuffer cbuf = CharBuffer.wrap(strChars, strOffset, strLen);
      CharsetEncoder cse = Charset.forName("UTF8").newEncoder();
      cse.encode(cbuf, bbuf, true);
    } else {
      UTF8Convert.toUTF8(str, bbuf);
    }
    // store terminating zero
    copyBuffer.store((byte)0, Offset.fromIntZeroExtend(len - 1));
  }

  /**
   * A JNI helper function, to set the value pointed to by a C pointer
   * of type (jboolean *).
   * @param boolPtr Native pointer to a jboolean variable to be set.   May be
   *            the NULL pointer, in which case we do nothing.
   * @param val Value to set it to (usually TRUE)
   *
   */
  static void setBoolStar(Address boolPtr, boolean val) {
    if (boolPtr.isZero()) {
      return;
    }
    if (val) {
      boolPtr.store((byte)1);
    } else {
      boolPtr.store((byte)0);
    }
  }

  /**
   * Dispatch method call
   * @param obj this pointer for method to be invoked, or null if method is static
   * @param mr reference to method to be invoked
   * @param args argument array
   * @param expectedReturnType a type reference for the expected return type
   * @param nonVirtual should invocation be of the given method or should we use virtual dispatch on the object?
   * @return return value of the method (boxed if primitive)
   * @throws InvocationTargetException when the reflective method call fails
   */
  protected static Object callMethod(Object obj, MethodReference mr, Object[] args, TypeReference expectedReturnType, boolean nonVirtual) throws InvocationTargetException {
    RVMMethod targetMethod = mr.resolve();
    TypeReference returnType = targetMethod.getReturnType();

    if (JNIFunctions.traceJNI) {
      VM.sysWriteln("JNI CallXXXMethod: " + mr);
    }

    if (expectedReturnType == null) {   // for reference return type
      if (!returnType.isReferenceType()) {
        throw new IllegalArgumentException("Wrong return type for method (" + targetMethod + "): expected reference type instead of " + returnType);
      }
    } else { // for primitive return type
      if (!returnType.definitelySame(expectedReturnType)) {
        throw new IllegalArgumentException("Wrong return type for method (" + targetMethod + "): expected " + expectedReturnType + " instead of " + returnType);
      }
    }
    // invoke the method
    return Reflection.invoke(targetMethod, null, obj, args, nonVirtual);
  }


  /**
   * Dispatch method call, arguments in jvalue*
   * @param env the JNI environemnt for the thread
   * @param objJREF a JREF index for the object
   * @param methodID id of a MethodReference
   * @param argAddress address of an array of jvalues (jvalue*)
   * @param expectedReturnType a type reference for the expected return type
   * @param nonVirtual should invocation be of the given method or should we use virtual dispatch on the object?
   * @return return value of the method (boxed if primitive)
   * @throws InvocationTargetException when reflective invocation fails
   */
  protected static Object callMethodJValuePtr(JNIEnvironment env, int objJREF, int methodID, Address argAddress, TypeReference expectedReturnType, boolean nonVirtual) throws InvocationTargetException {
    RuntimeEntrypoints.checkJNICountDownToGC();
    try {
      Object obj = env.getJNIRef(objJREF);
      MethodReference mr = MemberReference.getMethodRef(methodID);
      Object[] args = packageParametersFromJValuePtr(mr, argAddress);
      return callMethod(obj, mr, args, expectedReturnType, nonVirtual);
    } catch (Throwable unexpected) {
      if (JNIFunctions.traceJNI) unexpected.printStackTrace(System.err);
      env.recordException(unexpected);
      return 0;
    }
  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic&lt;type&gt;MethodA
   * @param targetMethod the target {@link MethodReference}
   * @param argAddress an address into the C space for the array of jvalue unions
   * @return an Object array holding the arguments wrapped at Objects
   */
  protected static Object[] packageParametersFromJValuePtr(MethodReference targetMethod, Address argAddress) {
    TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the JNIEnvironment for this thread in case we need to dereference any object arg
    JNIEnvironment env = RVMThread.getCurrentThread().getJNIEnv();

    Address addr = argAddress;
    for (int i = 0; i < argCount; i++, addr = addr.plus(BYTES_IN_LONG)) {
      // convert and wrap the argument according to the expected type
      if (argTypes[i].isReferenceType()) {
        // Avoid endianness issues by loading the whole slot
        Word wholeSlot = addr.loadWord();
        // for object, the arg is a JREF index, dereference to get the real object
        int JREFindex = wholeSlot.toInt();
        argObjectArray[i] = env.getJNIRef(JREFindex);
      } else if (argTypes[i].isIntType()) {
        argObjectArray[i] = addr.loadInt();
      } else if (argTypes[i].isLongType()) {
        argObjectArray[i] = addr.loadLong();
      } else if (argTypes[i].isBooleanType()) {
        // the 0/1 bit is stored in the high byte
        argObjectArray[i] = addr.loadByte() != 0;
      } else if (argTypes[i].isByteType()) {
        // the target byte is stored in the high byte
        argObjectArray[i] = addr.loadByte();
      } else if (argTypes[i].isCharType()) {
        // char is stored in the high 2 bytes
        argObjectArray[i] = addr.loadChar();
      } else if (argTypes[i].isShortType()) {
        // short is stored in the high 2 bytes
        argObjectArray[i] = addr.loadShort();
      } else if (argTypes[i].isFloatType()) {
        argObjectArray[i] = addr.loadFloat();
      } else {
        if (VM.VerifyAssertions) VM._assert(argTypes[i].isDoubleType());
        argObjectArray[i] = addr.loadDouble();
      }
    }
    return argObjectArray;
  }

  /**
   * @param functionTableIndex slot in the JNI function table
   * @return {@code true} if the function is implemented in Java (i.e.
   *  its code is in org.jikesrvm.jni.JNIFunctions) and {@code false}
   *  if the function is implemented in C (i.e. its code is in the
   *  bootloader)
   */
  @Uninterruptible
  public static boolean implementedInJava(int functionTableIndex) {
    if (VM.BuildForPowerPC) {
      return true;
    }
    // Indexes for the JNI functions are fixed according to the JNI
    // specification and there is no need for links from functions
    // to their indexes for anything else, so they're hardcoded here.
    switch (functionTableIndex) {
      case 28:
      case 34: case 37: case 40: case 43:
      case 46: case 49: case 52: case 55:
      case 58: case 61: case 64: case 67:
      case 70: case 73: case 76: case 79:
      case 82: case 85: case 88: case 91:
      case 114: case 117: case 120: case 123:
      case 126: case 129: case 132: case 135:
      case 138: case 141:
        return false;
      default:
        return true;
    }
  }

}
