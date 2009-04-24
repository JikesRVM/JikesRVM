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

import org.jikesrvm.VM;
import static org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS;
import org.jikesrvm.classloader.UTF8Convert;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.util.StringUtilities;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

/**
 * Platform independent utility functions called from JNIFunctions
 * (cannot be placed in JNIFunctions because methods
 * there are specially compiled to be called from native).
 *
 * @see JNIFunctions
 */
public abstract class JNIGenericHelpers {

  /**
   * Compute the length of the given null-terminated string
   *
   * @param ptr address of string in memory
   * @return the length of the string in bytes
   */
  public static int strlen(Address ptr) {
    int length=0;
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
      if(!bytes.minus(onesToSubtract).and(maskToTestHighBits).isZero()) {
        if (VM.LittleEndian) {
          for(int byteOff=0; byteOff < BYTES_IN_ADDRESS; byteOff++) {
            if(bytes.and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + byteOff;
            }
            bytes = bytes.rshl(8);
          }
        } else {
          for(int byteOff=BYTES_IN_ADDRESS-1; byteOff >= 0; byteOff--) {
            if(bytes.rshl(byteOff*8).and(Word.fromIntZeroExtend(0xFF)).isZero()) {
              return length + (BYTES_IN_ADDRESS - 1 - byteOff);
            }
          }
        }
      }
      length += BYTES_IN_ADDRESS;
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

  /**
   * Create a string from the given charset decoder and bytebuffer
   */
  private static String createString(CharsetDecoder csd, ByteBuffer bbuf) throws CharacterCodingException {
    char[] v;
    int o;
    int c;
    CharBuffer cbuf = csd.decode(bbuf);
    if(cbuf.hasArray()) {
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
      } catch(Exception ex){
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
      } catch(Exception ex){
        // Any problems fall through to default encoding
      }
    }
    // Can't do real Char encoding until VM is fully booted.
    // All Strings encountered during booting must be ascii
    tmp = createByteArrayFromC(stringAddress);
    return StringUtilities.asciiBytesToString(tmp);
  }


  /**
   * Convert a String into a a malloced region
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
    copyBuffer.store((byte)0, Offset.fromIntZeroExtend(len-1));
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
}
